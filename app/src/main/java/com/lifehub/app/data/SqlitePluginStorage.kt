package com.lifehub.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.lifehub.pluginapi.PluginDataMigration
import com.lifehub.pluginapi.PluginRecord
import com.lifehub.pluginapi.PluginStorage
import com.lifehub.pluginapi.PluginStorageLimits
import java.util.UUID

/**
 * 单个插件的 SQLite 私有存储。所有记录写入该插件独有的数据库文件。
 */
class SqlitePluginStorage(
    context: Context,
    pluginId: String,
    dataSchemaVersion: Int,
    migrations: Map<String, PluginDataMigration> = emptyMap(),
) : PluginStorage, PluginDataSchemaStore {

    private val helper = PluginDatabaseHelper(context, pluginId)
    private val schemaManager = PluginDataSchemaManager(this, this, migrations[pluginId])

    init {
        schemaManager.ensureVersion(pluginId, dataSchemaVersion)
    }

    override fun insert(collection: String, payload: String, id: String?): PluginRecord {
        PluginRecordRules.requireCollection(collection)
        PluginRecordRules.requirePayload(payload)
        val recordId = id ?: UUID.randomUUID().toString()
        PluginRecordRules.requireId(recordId)

        if (count(collection) >= PluginStorageLimits.MAX_RECORDS_PER_COLLECTION) {
            throw IllegalStateException(
                "集合 $collection 的记录数达到上限 ${PluginStorageLimits.MAX_RECORDS_PER_COLLECTION}",
            )
        }

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(PluginDatabaseHelper.COL_COLLECTION, collection)
            put(PluginDatabaseHelper.COL_ID, recordId)
            put(PluginDatabaseHelper.COL_PAYLOAD, payload)
            put(PluginDatabaseHelper.COL_CREATED_AT, now)
            put(PluginDatabaseHelper.COL_UPDATED_AT, now)
        }
        try {
            helper.writableDatabase.insertOrThrow(PluginDatabaseHelper.TABLE_RECORDS, null, values)
        } catch (error: SQLiteConstraintException) {
            throw IllegalArgumentException("记录已存在：$collection/$recordId", error)
        }
        return PluginRecord(collection, recordId, payload, now, now)
    }

    override fun update(collection: String, id: String, payload: String): Boolean {
        PluginRecordRules.requireCollection(collection)
        PluginRecordRules.requireId(id)
        PluginRecordRules.requirePayload(payload)

        val values = ContentValues().apply {
            put(PluginDatabaseHelper.COL_PAYLOAD, payload)
            put(PluginDatabaseHelper.COL_UPDATED_AT, System.currentTimeMillis())
        }
        val rows = helper.writableDatabase.update(
            PluginDatabaseHelper.TABLE_RECORDS,
            values,
            "${PluginDatabaseHelper.COL_COLLECTION} = ? AND ${PluginDatabaseHelper.COL_ID} = ?",
            arrayOf(collection, id),
        )
        return rows > 0
    }

    override fun delete(collection: String, id: String): Boolean {
        PluginRecordRules.requireCollection(collection)
        PluginRecordRules.requireId(id)
        val rows = helper.writableDatabase.delete(
            PluginDatabaseHelper.TABLE_RECORDS,
            "${PluginDatabaseHelper.COL_COLLECTION} = ? AND ${PluginDatabaseHelper.COL_ID} = ?",
            arrayOf(collection, id),
        )
        return rows > 0
    }

    override fun get(collection: String, id: String): PluginRecord? {
        PluginRecordRules.requireCollection(collection)
        PluginRecordRules.requireId(id)
        helper.readableDatabase.query(
            PluginDatabaseHelper.TABLE_RECORDS,
            null,
            "${PluginDatabaseHelper.COL_COLLECTION} = ? AND ${PluginDatabaseHelper.COL_ID} = ?",
            arrayOf(collection, id),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.readRecord() else null
        }
    }

    override fun list(collection: String, limit: Int, offset: Int): List<PluginRecord> {
        PluginRecordRules.requireCollection(collection)
        PluginRecordRules.requirePage(limit, offset)
        val records = mutableListOf<PluginRecord>()
        helper.readableDatabase.query(
            PluginDatabaseHelper.TABLE_RECORDS,
            null,
            "${PluginDatabaseHelper.COL_COLLECTION} = ?",
            arrayOf(collection),
            null,
            null,
            "${PluginDatabaseHelper.COL_UPDATED_AT} DESC, ${PluginDatabaseHelper.COL_ID} ASC",
            "$limit OFFSET $offset",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                records += cursor.readRecord()
            }
        }
        return records
    }

    override fun count(collection: String): Int {
        PluginRecordRules.requireCollection(collection)
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${PluginDatabaseHelper.TABLE_RECORDS} " +
                "WHERE ${PluginDatabaseHelper.COL_COLLECTION} = ?",
            arrayOf(collection),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    override fun collections(): List<String> {
        val collections = mutableListOf<String>()
        helper.readableDatabase.rawQuery(
            "SELECT DISTINCT ${PluginDatabaseHelper.COL_COLLECTION} " +
                "FROM ${PluginDatabaseHelper.TABLE_RECORDS} " +
                "ORDER BY ${PluginDatabaseHelper.COL_COLLECTION}",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                collections += cursor.getString(0)
            }
        }
        return collections
    }

    override fun clear() {
        helper.writableDatabase.delete(PluginDatabaseHelper.TABLE_RECORDS, null, null)
    }

    override fun readDataSchemaVersion(): Int? =
        helper.readableDatabase.query(
            PluginDatabaseHelper.TABLE_META,
            arrayOf(PluginDatabaseHelper.COL_META_VALUE),
            "${PluginDatabaseHelper.COL_META_KEY} = ?",
            arrayOf(PluginDatabaseHelper.META_DATA_SCHEMA_VERSION),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.toIntOrNull() else null
        }

    override fun writeDataSchemaVersion(version: Int) {
        require(version > 0) { "数据 schema 版本必须大于 0" }
        val values = ContentValues().apply {
            put(PluginDatabaseHelper.COL_META_KEY, PluginDatabaseHelper.META_DATA_SCHEMA_VERSION)
            put(PluginDatabaseHelper.COL_META_VALUE, version.toString())
        }
        helper.writableDatabase.insertWithOnConflict(
            PluginDatabaseHelper.TABLE_META,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun <T> transaction(block: () -> T): T {
        val database = helper.writableDatabase
        database.beginTransaction()
        return try {
            val result = block()
            database.setTransactionSuccessful()
            result
        } finally {
            database.endTransaction()
        }
    }

    private fun Cursor.readRecord(): PluginRecord = PluginRecord(
        collection = getString(getColumnIndexOrThrow(PluginDatabaseHelper.COL_COLLECTION)),
        id = getString(getColumnIndexOrThrow(PluginDatabaseHelper.COL_ID)),
        payload = getString(getColumnIndexOrThrow(PluginDatabaseHelper.COL_PAYLOAD)),
        createdAt = getLong(getColumnIndexOrThrow(PluginDatabaseHelper.COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(PluginDatabaseHelper.COL_UPDATED_AT)),
    )
}

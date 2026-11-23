package com.lifehub.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

/**
 * 每个插件一个独立数据库文件，文件名由 pluginId 的 SHA-256 摘要生成，
 * 插件无法传入路径或数据库名。
 */
internal class PluginDatabaseHelper(
    context: Context,
    pluginId: String,
) : SQLiteOpenHelper(context.applicationContext, databaseName(pluginId), null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_RECORDS)
        db.execSQL(CREATE_INDEX)
        db.execSQL(CREATE_META)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(CREATE_META)
        }
    }

    companion object {
        private const val VERSION = 2

        const val TABLE_RECORDS = "records"
        const val TABLE_META = "plugin_meta"
        const val COL_COLLECTION = "collection"
        const val COL_ID = "id"
        const val COL_PAYLOAD = "payload"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_META_KEY = "key"
        const val COL_META_VALUE = "value"
        const val META_DATA_SCHEMA_VERSION = "data_schema_version"

        private const val CREATE_RECORDS = """
            CREATE TABLE $TABLE_RECORDS (
                $COL_COLLECTION TEXT NOT NULL,
                $COL_ID TEXT NOT NULL,
                $COL_PAYLOAD TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                PRIMARY KEY ($COL_COLLECTION, $COL_ID)
            )
        """

        private const val CREATE_INDEX = """
            CREATE INDEX idx_records_collection_updated
            ON $TABLE_RECORDS ($COL_COLLECTION, $COL_UPDATED_AT)
        """

        private const val CREATE_META = """
            CREATE TABLE $TABLE_META (
                $COL_META_KEY TEXT PRIMARY KEY,
                $COL_META_VALUE TEXT NOT NULL
            )
        """

        private const val HEX = "0123456789abcdef"

        fun databaseName(pluginId: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(pluginId.toByteArray(Charsets.UTF_8))
            val hex = buildString(digest.size * 2) {
                digest.forEach { byte ->
                    val value = byte.toInt() and 0xFF
                    append(HEX[value ushr 4])
                    append(HEX[value and 0x0F])
                }
            }
            return "plugin_data_$hex.db"
        }
    }
}

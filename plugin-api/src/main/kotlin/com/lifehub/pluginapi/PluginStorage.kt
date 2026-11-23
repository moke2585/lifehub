package com.lifehub.pluginapi

/**
 * 插件私有存储的限额。宿主拒绝超出范围的集合名、ID、载荷和分页参数。
 */
object PluginStorageLimits {
    const val MAX_RECORD_BYTES: Int = 64 * 1024
    const val MAX_COLLECTION_NAME_LENGTH: Int = 64
    const val MAX_RECORD_ID_LENGTH: Int = 128
    const val DEFAULT_PAGE_SIZE: Int = 50
    const val MAX_PAGE_SIZE: Int = 200
    const val MAX_RECORDS_PER_COLLECTION: Int = 5_000
}

/**
 * 一条插件私有记录。payload 由插件自行编码（推荐 JSON），宿主不解析其内容。
 */
data class PluginRecord(
    val collection: String,
    val id: String,
    val payload: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 按插件隔离的存储接口。实现由宿主提供，插件不能指定数据库名、路径或执行 SQL。
 * 所有方法都是阻塞调用，宿主在后台线程执行 action 时使用。
 */
interface PluginStorage {
    fun insert(collection: String, payload: String, id: String? = null): PluginRecord

    fun update(collection: String, id: String, payload: String): Boolean

    fun delete(collection: String, id: String): Boolean

    fun get(collection: String, id: String): PluginRecord?

    fun list(
        collection: String,
        limit: Int = PluginStorageLimits.DEFAULT_PAGE_SIZE,
        offset: Int = 0,
    ): List<PluginRecord>

    fun count(collection: String): Int

    /** 当前插件已有的集合名，按字典序返回。用于备份导出。 */
    fun collections(): List<String>

    /** 清空该插件的所有记录，不删除数据库文件。 */
    fun clear()
}

/**
 * 插件数据版本迁移。宿主在事务中调用；抛异常即回滚并保留旧数据。
 */
fun interface PluginDataMigration {
    fun migrate(storage: PluginStorage, fromVersion: Int, toVersion: Int)
}

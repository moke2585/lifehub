package com.lifehub.app.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.lifehub.app.LifeHubApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LifeHubApplication
    private val manager = app.backupManager
    private val resolver = application.contentResolver

    fun suggestedFileName(): String = manager.suggestedFileName()

    suspend fun export(password: String, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            withPassword(password) { chars ->
                val bytes = manager.export(chars)
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                } ?: throw BackupException("无法写入所选文件")
            }
        }
    }

    suspend fun inspect(password: String, uri: Uri): Result<BackupPreview> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    readLimited(input)
                } ?: throw BackupException("无法读取所选文件")
                withPassword(password) { chars -> manager.preview(chars, bytes) }
            }
        }

    suspend fun apply(preview: BackupPreview): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { manager.apply(preview) }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > BackupLimits.MAX_ARCHIVE_BYTES + 4096) {
                throw BackupException("备份文件超过大小上限")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private inline fun <T> withPassword(password: String, block: (CharArray) -> T): T {
        val chars = password.toCharArray()
        return try {
            block(chars)
        } finally {
            chars.fill('\u0000')
        }
    }
}

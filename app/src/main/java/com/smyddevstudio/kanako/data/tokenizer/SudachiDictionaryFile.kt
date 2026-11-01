package com.smyddevstudio.kanako.data.tokenizer

import android.content.Context
import com.smyddevstudio.kanako.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Installs the public packaged dictionary, never OCR images or user text. Called on a worker. */
internal object SudachiDictionaryFile {
    @Synchronized
    fun prepare(context: Context): File {
        val directory = File(context.noBackupFilesDir, "sudachi")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create dictionary directory" }
        val target = File(directory, "system-${BuildConfig.SUDACHI_DICTIONARY_VERSION}.dic")
        if (target.isFile && target.length() == BuildConfig.SUDACHI_DICTIONARY_BYTES &&
            target.inputStream().buffered().use { sha256(it) } == BuildConfig.SUDACHI_DICTIONARY_SHA256
        ) return target

        val temporary = File(directory, target.name + ".tmp")
        try {
            context.assets.open("sudachi/system_full.dic").buffered().use { input ->
                FileOutputStream(temporary).use { output ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    var bytes = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Dictionary installation interrupted")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        bytes += count
                    }
                    check(bytes == BuildConfig.SUDACHI_DICTIONARY_BYTES &&
                        digest.digest().hex() == BuildConfig.SUDACHI_DICTIONARY_SHA256
                    ) { "Packaged Sudachi dictionary integrity check failed" }
                    output.fd.sync()
                }
            }
            Files.move(temporary.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return target
        } finally {
            temporary.delete()
        }
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Dictionary verification interrupted")
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().hex()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}

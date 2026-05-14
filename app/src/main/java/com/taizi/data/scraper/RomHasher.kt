package com.taizi.data.scraper

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * Computes ScreenScraper-friendly (crc32, size) tuples for ROM files.
 *
 * Mirrors what EmulationStation / Skraper / Skyscraper send to jeuInfos.php so
 * ScreenScraper can match against its hash database (much more accurate than
 * filename matching). For non-arcade ZIP files we read the stored CRC32 of the
 * largest entry — that's what ScreenScraper's DB indexes ROMs by, and the ZIP
 * central directory already has it so no decompression is required.
 */
object RomHasher {

    private const val MAX_HASH_BYTES = 1L shl 30 // 1 GB — skip hashing huge CD images

    private val ARCADE_SYSTEMS = setOf(
        "mame", "fbneo", "cps1", "cps2", "cps3",
        "naomi", "model2", "neogeo", "neogeocd", "daphne"
    )

    data class Hash(val crc32: String, val size: Long)

    fun hash(file: File, systemId: String): Hash? {
        if (!file.isFile) return null

        val name = file.name.lowercase()
        if (name.endsWith(".zip") && systemId !in ARCADE_SYSTEMS) {
            zipInnerHash(file)?.let { return it }
        }

        if (file.length() > MAX_HASH_BYTES) return null
        return fileHash(file)
    }

    private fun fileHash(file: File): Hash? = try {
        val crc = CRC32()
        file.inputStream().buffered(64 * 1024).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        Hash("%08X".format(crc.value), file.length())
    } catch (_: Exception) {
        null
    }

    private fun zipInnerHash(file: File): Hash? = try {
        ZipFile(file).use { zip ->
            val rom = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .maxByOrNull { it.size }
                ?: return null

            if (rom.crc >= 0 && rom.size >= 0) {
                Hash("%08X".format(rom.crc), rom.size)
            } else {
                val crc = CRC32()
                zip.getInputStream(rom).buffered(64 * 1024).use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        crc.update(buf, 0, n)
                    }
                }
                Hash("%08X".format(crc.value), if (rom.size >= 0) rom.size else file.length())
            }
        }
    } catch (_: Exception) {
        null
    }
}

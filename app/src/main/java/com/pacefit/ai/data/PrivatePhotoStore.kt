package com.pacefit.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

enum class PhotoDirectory(val folder: String) { PLOGGING("plogging"), REPORTS("reports") }

/** Only re-encoded JPEGs enter app-private storage; the source and its EXIF are never copied. */
class PrivatePhotoStore(
    private val context: Context,
    val root: File = File(context.filesDir, "pacefit"),
) {
    val eraseMarker: File get() = File(root, "erase.pending")

    fun directory(kind: PhotoDirectory): File = File(root, "photos/${kind.folder}")

    fun ensureDirectories() {
        PhotoDirectory.entries.forEach {
            val directory = directory(it)
            check(directory.isDirectory || directory.mkdirs()) { "No se pudo crear el directorio privado." }
        }
    }

    fun file(kind: PhotoDirectory, name: String): File {
        require(name.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.jpg"))) {
            "Nombre de foto no válido."
        }
        return File(directory(kind), name)
    }

    fun importPhoto(uri: Uri, kind: PhotoDirectory): String {
        ensureDirectories()
        val bytes = context.contentResolver.openInputStream(uri)?.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var size = 0
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                size += read
                require(size <= 20 * 1024 * 1024) { "La foto supera 20 MB." }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IOException("No se pudo abrir la foto.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..20_000 && bounds.outHeight in 1..20_000) { "Imagen no compatible." }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1600) inSampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("No se pudo decodificar la foto.")
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val name = "${UUID.randomUUID()}.jpg"
        val target = file(kind, name)
        val temporary = File(directory(kind), "$name.tmp")
        try {
            FileOutputStream(temporary).use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)) { "No se pudo comprimir la foto." }
                it.fd.sync()
            }
            check(temporary.renameTo(target)) { "No se pudo guardar la foto." }
            return name
        } finally {
            temporary.delete()
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }

    fun delete(kind: PhotoDirectory, name: String) {
        val file = file(kind, name)
        check(!file.exists() || file.delete()) { "No se pudo eliminar una foto privada." }
    }

    fun removeOrphans(kind: PhotoDirectory, referencedNames: Set<String>) {
        directory(kind).listFiles()?.forEach { file ->
            if (file.name !in referencedNames) deleteOwnedFile(kind, file)
        }
    }

    fun clearPhotos() {
        PhotoDirectory.entries.forEach { kind ->
            directory(kind).listFiles()?.forEach { deleteOwnedFile(kind, it) }
        }
    }

    private fun deleteOwnedFile(kind: PhotoDirectory, file: File) {
        check(file.canonicalFile.parentFile == directory(kind).canonicalFile && file.isFile)
        check(file.delete()) { "No se pudo eliminar un archivo privado." }
    }
}

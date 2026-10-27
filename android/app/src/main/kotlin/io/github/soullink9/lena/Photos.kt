package io.github.soullink9.lena

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** A photo to show Lena: a preview for the sheet, and the image itself, ready to send. */
class Photo(val preview: ImageBitmap, val dataUrl: String)

/**
 * Photos for the Ask sheet. Taken with the camera app (into Lena's private cache, shared only
 * with the camera for that one picture) or chosen with Android's photo picker, which needs no
 * permission and shows Lena only the photo you pick.
 */
object Photos {

    /** Big enough for the model to read a label, small enough to send quickly. */
    private const val LONGEST_SIDE = 1536

    private fun cameraFile(c: Context) = File(File(c.cacheDir, "photos").apply { mkdirs() }, "camera.jpg")

    /** Where the camera app should save the picture. */
    fun cameraUri(c: Context): Uri = FileProvider.getUriForFile(c, "${c.packageName}.photos", cameraFile(c))

    /** Shrinks, straightens (from the photo's orientation) and encodes the photo. Null if it can't be read. */
    suspend fun load(c: Context, uri: Uri): Photo? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, LONGEST_SIDE.toFloat() / max(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val jpeg = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
            Photo(bitmap.asImageBitmap(), "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP))
        }.getOrNull().also { cameraFile(c).delete() }
    }
}

package app.timeline.agentique.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import app.timeline.agentique.R
import java.io.File
import kotlin.math.max

/** Camera (system camera app, no CAMERA permission needed) and gallery, returning a downscaled bitmap. */
@Composable
fun ImageButtons(enabled: Boolean = true, onBitmap: (Bitmap) -> Unit) {
    val ctx = LocalContext.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { decodeBitmap(ctx, it) }?.let(onBitmap)
    }
    var pending by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pending?.let { decodeBitmap(ctx, it) }?.let(onBitmap)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = enabled, onClick = {
            val f = File(ctx.cacheDir, "images/cap_${System.currentTimeMillis()}.jpg").also { it.parentFile?.mkdirs() }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            pending = uri
            camera.launch(uri)
        }) { Text(stringResource(R.string.camera)) }
        OutlinedButton(enabled = enabled, onClick = { gallery.launch("image/*") }) {
            Text(stringResource(R.string.gallery))
        }
    }
}

fun decodeBitmap(ctx: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?: return null
    val rotation = ctx.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    if (rotation == 0) bmp
    else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
}.getOrNull()

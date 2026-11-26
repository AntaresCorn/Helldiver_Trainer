package cn.antares.helldiver_trainer.bridge

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 将内存中的图像字节数组（如 WebP）解码为 Compose [ImageBitmap]，解码失败时返回 null。
 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

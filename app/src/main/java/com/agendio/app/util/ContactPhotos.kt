package com.agendio.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.util.concurrent.Executors

/**
 * Carrega a foto salva no contato do aparelho.
 *
 * O Android não expõe as fotos de perfil do WhatsApp para outros aplicativos,
 * por isso usamos a foto do contato do celular. Quando não existe foto,
 * o monograma continua sendo exibido.
 */
object ContactPhotos {

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val executor = Executors.newFixedThreadPool(2)

    fun load(
        context: Context,
        uriString: String,
        imageView: ImageView,
        fallback: View
    ) {
        val key = uriString.trim()
        imageView.tag = key
        imageView.setImageDrawable(null)
        imageView.visibility = View.GONE
        fallback.visibility = View.VISIBLE
        if (key.isBlank()) return

        val cached = cache.get(key)
        if (cached != null) {
            show(imageView, fallback, cached)
            return
        }

        val appContext = context.applicationContext
        executor.execute {
            val bitmap = try {
                appContext.contentResolver.openInputStream(Uri.parse(key))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                null
            }
            if (bitmap != null) cache.put(key, bitmap)
            imageView.post {
                if (imageView.tag != key) return@post
                if (bitmap != null) {
                    show(imageView, fallback, bitmap)
                } else {
                    imageView.visibility = View.GONE
                    fallback.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun show(imageView: ImageView, fallback: View, bitmap: Bitmap) {
        imageView.setImageBitmap(bitmap)
        imageView.visibility = View.VISIBLE
        fallback.visibility = View.GONE
    }
}

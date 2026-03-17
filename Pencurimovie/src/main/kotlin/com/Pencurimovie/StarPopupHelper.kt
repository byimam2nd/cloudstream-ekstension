package com.Pencurimovie

import android.content.Context
import android.app.Activity
import android.os.Handler
import android.os.Looper

// StarPopupHelper - Versi sederhana tanpa Android UI imports
// Hanya menampilkan popup sekali untuk welcome message
object StarPopupHelper {

    private const val PREFS_NAME = "PencurimoviePrefs"
    private const val KEY_SHOWN_POPUP = "shown_welcome_popup"

    fun showStarPopupIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Jika sudah pernah tampil, jangan tampilkan lagi
        if (prefs.getBoolean(KEY_SHOWN_POPUP, false)) {
            return
        }

        // Simpan status sudah tampil
        prefs.edit().putBoolean(KEY_SHOWN_POPUP, true).apply()

        // Popup ditampilkan di main thread
        Handler(Looper.getMainLooper()).post {
            try {
                val activity = context as? Activity ?: return@post
                
                // Gunakan AlertDialog sederhana
                val builder = android.app.AlertDialog.Builder(activity)
                builder.setTitle("🎬 Selamat Menonton")
                .setMessage("Selamat menikmati film dan serial favorit Anda secara gratis.\n\nSemoga pengalaman menonton Anda menyenangkan! 🍿")
                .setPositiveButton("Mulai Menonton") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }
}


package com.example.tugashelloworld.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.widget.MediaController
import android.widget.Toast
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.DialogVideoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Dialog pemutar video sederhana untuk video lampiran laporan.
 */
object VideoPlayerDialog {

    fun show(context: Context, path: String) {
        if (path.isBlank() || !File(path).isFile) {
            Toast.makeText(
                context,
                "File video tidak ditemukan",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val binding = DialogVideoBinding.inflate(
            LayoutInflater.from(context)
        )

        val controller = MediaController(context).also {
            it.setAnchorView(binding.videoView)
        }

        binding.videoView.setMediaController(controller)
        binding.videoView.setVideoPath(path)

        binding.videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = false
            binding.videoView.start()
        }

        binding.videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(
                context,
                "Video gagal diputar",
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.close, null)
            .setOnDismissListener {
                binding.videoView.stopPlayback()
            }
            .show()
    }
}

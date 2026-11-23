package com.example.tugashelloworld.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.widget.MediaController
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.DialogVideoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Dialog pemutar video sederhana (dengan kontrol putar/jeda) untuk video lampiran laporan. */
object VideoPlayerDialog {

    fun show(context: Context, path: String) {
        val binding = DialogVideoBinding.inflate(LayoutInflater.from(context))
        binding.videoView.setMediaController(MediaController(context).also { it.setAnchorView(binding.videoView) })
        binding.videoView.setVideoPath(path)
        binding.videoView.setOnPreparedListener { it.start() }

        MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.close, null)
            .setOnDismissListener { binding.videoView.stopPlayback() }
            .show()
    }
}

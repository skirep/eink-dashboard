package com.eink.launcher.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.eink.launcher.R
import com.eink.launcher.util.SlideshowImageLoader

/**
 * SlideshowFragment – the launcher home screen (page 0).
 *
 * Displays a full-screen image from the device's slideshow folder.
 * The image advances automatically every [SLIDESHOW_INTERVAL_MS] milliseconds
 * (3 hours by default).  Navigation between images can also be driven
 * externally by calling [SlideshowImageLoader.advance].
 *
 * E-ink considerations:
 *  - No animation / cross-fade transitions (would look bad on e-ink).
 *  - The image is loaded synchronously on the UI thread from a pre-loaded
 *    bitmap cache managed by [SlideshowImageLoader] to avoid jank.
 */
class SlideshowFragment : Fragment() {

    companion object {
        /** 3-hour rotation interval. */
        const val SLIDESHOW_INTERVAL_MS: Long = 3 * 60 * 60 * 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ivSlideshow: ImageView
    private lateinit var tvNoImages: TextView

    private val advanceRunnable = object : Runnable {
        override fun run() {
            showNextImage()
            handler.postDelayed(this, SLIDESHOW_INTERVAL_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_slideshow, container, false)
        ivSlideshow = view.findViewById(R.id.ivSlideshow)
        tvNoImages = view.findViewById(R.id.tvNoImages)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showCurrentImage()
    }

    override fun onResume() {
        super.onResume()
        // Schedule the 3-hour rotation starting from the next interval.
        handler.postDelayed(advanceRunnable, SLIDESHOW_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(advanceRunnable)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun showCurrentImage() {
        val loader = SlideshowImageLoader.getInstance(requireContext())
        val bitmap = loader.currentBitmap()
        if (bitmap != null) {
            ivSlideshow.setImageBitmap(bitmap)
            ivSlideshow.visibility = View.VISIBLE
            tvNoImages.visibility = View.GONE
        } else {
            ivSlideshow.visibility = View.GONE
            tvNoImages.visibility = View.VISIBLE
        }
    }

    private fun showNextImage() {
        val loader = SlideshowImageLoader.getInstance(requireContext())
        loader.advance()
        showCurrentImage()
    }
}

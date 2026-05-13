package com.eink.launcher.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.eink.launcher.R

/**
 * WalkingPersonFragment – displays an animated walking character.
 * 
 * This fragment shows a simple stick figure that walks across the screen.
 * The animation is optimized for e-ink displays with periodic updates
 * rather than continuous frame-by-frame animation.
 */
class WalkingPersonFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_walking_person, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The WalkingPersonView in the layout handles the animation
    }
}

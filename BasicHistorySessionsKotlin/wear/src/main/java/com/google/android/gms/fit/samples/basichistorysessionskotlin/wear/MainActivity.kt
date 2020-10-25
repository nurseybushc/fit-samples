package com.google.android.gms.fit.samples.basichistorysessionskotlin.wear

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    var mTextView: TextView? = null
    var activityContext: Context? = null

    enum class AmbientType {
        EXIT, ENTER
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback =
            object : AmbientModeSupport.AmbientCallback() {

        /** If the display is low-bit in ambient mode. i.e. it requires anti-aliased fonts.  */
        private var mIsLowBitAmbient = false

        /**
         * If the display requires burn-in protection in ambient mode, rendered pixels need to be
         * intermittently offset to avoid screen burn-in.
         */
        private var mDoBurnInProtection = false

        override fun onEnterAmbient(ambientDetails: Bundle?) {
            // Handle entering ambient mode
            Log.d("wear/MainActivity.kt", "onEnterAmbient")

            mIsLowBitAmbient =
                    ambientDetails!!.getBoolean(AmbientModeSupport.EXTRA_LOWBIT_AMBIENT, false)
            mDoBurnInProtection =
                    ambientDetails.getBoolean(AmbientModeSupport.EXTRA_BURN_IN_PROTECTION, false)

            println("mIsLowBitAmbient $mIsLowBitAmbient, mDoBurnInProtection $mDoBurnInProtection")
            switchAmbient(AmbientType.ENTER)
        }

        override fun onExitAmbient() {
            // Handle exiting ambient mode
            Log.d("wear/MainActivity.kt", "onExitAmbient")
            switchAmbient(AmbientType.EXIT)
        }

        override fun onUpdateAmbient() {
            // Update the content
            Log.d("wear/MainActivity.kt", "onUpdateAmbient")
        }
    }

    private fun switchAmbient(at: AmbientType) {

        when(at) {
            AmbientType.ENTER -> {
                mTextView?.text = getString(R.string.ambient_enter)
                mTextView?.setTextColor(ContextCompat.getColor(applicationContext,
                        R.color.lightGrey))
            }
            AmbientType.EXIT -> {
                mTextView?.text = getString(R.string.ambient_exit)
                mTextView?.setTextColor(ContextCompat.getColor(applicationContext,
                        R.color.green))
            }
        }
    }

    /*
     * Declare an ambient mode controller, which will be used by
     * the activity to determine if the current mode is ambient.
     */
    private lateinit var ambientController: AmbientModeSupport.AmbientController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ambientController = AmbientModeSupport.attach(this)

        mTextView = findViewById(R.id.text)
        activityContext = this
    }
}


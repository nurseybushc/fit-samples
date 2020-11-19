package com.google.android.gms.fit.samples.basicrecordingapikotlin.wear

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType

const val TAG = "BasicRecordingApiWear"

enum class FitActionRequestCode {
    SUBSCRIBE,
    CANCEL_SUBSCRIPTION,
    DUMP_SUBSCRIPTIONS
}

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    var activityContext: Context? = null

    private var btnStart: Button? = null
    private var btnList: Button? = null
    private var btnStop: Button? = null

    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_WRITE)
                .build()
    }

    private fun checkPermissionsAndRun(fitActionRequestCode: FitActionRequestCode) {
        fitSignIn(fitActionRequestCode)
    }

    private fun fitSignIn(requestCode: FitActionRequestCode) {
        if (oAuthPermissionsApproved()) {
            performActionForRequestCode(requestCode)
        } else {
            requestCode.let {
                GoogleSignIn.requestPermissions(
                        this,
                        it.ordinal,
                        getGoogleAccount(), fitnessOptions)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (resultCode) {
            RESULT_OK -> {
                val postSignInAction = FitActionRequestCode.values()[requestCode]
                postSignInAction.let {
                    performActionForRequestCode(postSignInAction)
                }
            }
            else -> oAuthErrorMsg(requestCode, resultCode)
        }
    }

    private fun performActionForRequestCode(requestCode: FitActionRequestCode) = when (requestCode) {
        FitActionRequestCode.SUBSCRIBE -> subscribe()
        FitActionRequestCode.CANCEL_SUBSCRIPTION -> cancelSubscription()
        FitActionRequestCode.DUMP_SUBSCRIPTIONS -> dumpSubscriptionsList()
    }

    private fun oAuthErrorMsg(requestCode: Int, resultCode: Int) {
        val message = """
            There was an error signing into Fit. Check the troubleshooting section of the README
            for potential issues.
            Request code was: $requestCode
            Result code was: $resultCode
        """.trimIndent()
        Log.e(TAG, message)
    }

    private fun oAuthPermissionsApproved() = GoogleSignIn.hasPermissions(getGoogleAccount(), fitnessOptions)

    private fun getGoogleAccount() = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

    private fun subscribe() {
        // To create a subscription, invoke the Recording API. As soon as the subscription is
        // active, fitness data will start recording.
        // [START subscribe_to_datatype]
        Fitness.getRecordingClient(this, getGoogleAccount())
                .subscribe(DataType.TYPE_CALORIES_EXPENDED)
                .addOnSuccessListener { Log.i(TAG, "Successfully subscribed!") }
                .addOnFailureListener { e -> Log.e(TAG, "There was a problem subscribing. error: $e") }
        // [END subscribe_to_datatype]
    }

    private fun dumpSubscriptionsList() {
        // [START list_current_subscriptions]
        Fitness.getRecordingClient(this, getGoogleAccount())
                .listSubscriptions(DataType.TYPE_CALORIES_EXPENDED)
                .addOnSuccessListener { subscriptions ->
                    for (subscription in subscriptions) {
                        val dataType = subscription.dataType!!
                        Log.i(TAG, "Active subscription for data type: ${dataType.name}")
                    }

                    if (subscriptions.isEmpty()) {
                        Log.i(TAG, "No active subscriptions")
                    }
                }
                .addOnFailureListener { e -> Log.e(TAG, "There was a problem listing subscriptions. error: $e") }

        // [END list_current_subscriptions]
    }

    private fun cancelSubscription() {
        val dataTypeStr = DataType.TYPE_CALORIES_EXPENDED.toString()
        Log.i(TAG, "Unsubscribing from data type: $dataTypeStr")

        // Invoke the Recording API to unsubscribe from the data type and specify a callback that
        // will check the result.
        // [START unsubscribe_from_datatype]
        Fitness.getRecordingClient(this, getGoogleAccount())
                .unsubscribe(DataType.TYPE_CALORIES_EXPENDED)
                .addOnSuccessListener {
                    Log.i(TAG, "Successfully unsubscribed for data type: $dataTypeStr")
                }
                .addOnFailureListener {
                    // Subscription not removed
                    e -> Log.e(TAG, "Failed to unsubscribe for data type: $dataTypeStr. error: $e")
                }
        // [END unsubscribe_from_datatype]
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
                }

                override fun onExitAmbient() {
                    // Handle exiting ambient mode
                    Log.d("wear/MainActivity.kt", "onExitAmbient")
                }

                override fun onUpdateAmbient() {
                    // Update the content
                    Log.d("wear/MainActivity.kt", "onUpdateAmbient")
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

        activityContext = this

        btnStart = findViewById(R.id.btnStart)
        btnList = findViewById(R.id.btnList)
        btnStop = findViewById(R.id.btnStop)


        btnStart!!.setOnClickListener { checkPermissionsAndRun(FitActionRequestCode.SUBSCRIBE) }
        btnList!!.setOnClickListener { checkPermissionsAndRun(FitActionRequestCode.DUMP_SUBSCRIPTIONS) }
        btnStop!!.setOnClickListener { checkPermissionsAndRun(FitActionRequestCode.CANCEL_SUBSCRIPTION) }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        when {
            grantResults.isEmpty() -> {
                // If user interaction was interrupted, the permission request
                // is cancelled and you receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.")
            }
            grantResults[0] == PackageManager.PERMISSION_GRANTED -> {
                // Permission was granted.
                val fitActionRequestCode = FitActionRequestCode.values()[requestCode]
                fitSignIn(fitActionRequestCode)
            }
            else -> {
                // Permission denied

                Log.e(TAG, "Permission was denied, but is needed for core functionality.")
            }
        }
    }
}
package com.google.android.gms.fit.samples.basichistorysessionskotlin.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Session
import com.google.android.gms.fitness.data.Subscription
import com.google.android.gms.fitness.request.DataSourcesRequest
import java.util.*
import java.util.concurrent.TimeUnit

enum class FitActionRequestCode {
    INSERT_AND_VERIFY_SESSION
}

enum class RecordingType {
    SUBSCRIBE,
    UNSUBSCRIBE
}

enum class SessionType {
    START,
    STOP
}

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    var mTextView: TextView? = null
    var activityContext: Context? = null
    var running: Boolean? = false

    var btnStart: Button? = null
    var btnStop: Button? = null

    enum class AmbientType {
        EXIT, ENTER
    }
    
    private val TAG: String = "RUN_FIT_GAME_WEAR"

    var currentSession: Session? = null

    private fun performActionForRequestCode(requestCode: FitActionRequestCode) = when (requestCode) {
        FitActionRequestCode.INSERT_AND_VERIFY_SESSION -> startRecording()
    }

    private fun fitSignIn(requestCode: FitActionRequestCode) {
        Log.i(TAG, "fitSignIn")
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

    private fun oAuthPermissionsApproved() = GoogleSignIn.hasPermissions(getGoogleAccount(), fitnessOptions)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (resultCode) {
            AppCompatActivity.RESULT_OK -> {
                val postSignInAction = FitActionRequestCode.values()[requestCode]
                performActionForRequestCode(postSignInAction)
            }
            else -> oAuthErrorMsg(requestCode, resultCode)
        }
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

    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
                .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_WRITE) //comment to fix
                .addDataType(DataType.TYPE_STEP_COUNT_CADENCE, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_HEART_POINTS, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_MOVE_MINUTES, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_WRITE)
                .build()
    }

    private val dataSourceRequest = DataSourcesRequest.Builder()
            .setDataTypes(DataType.TYPE_ACTIVITY_SEGMENT,
                    DataType.TYPE_SPEED,
                    DataType.TYPE_HEART_RATE_BPM,
                    DataType.TYPE_STEP_COUNT_CADENCE,
                    DataType.TYPE_HEART_POINTS,
                    DataType.TYPE_MOVE_MINUTES,
                    DataType.TYPE_DISTANCE_DELTA,
                    DataType.TYPE_STEP_COUNT_DELTA,
                    DataType.TYPE_CALORIES_EXPENDED)
            .setDataSourceTypes(DataSource.TYPE_DERIVED) //should be used if any other data source is used in generating the data.
            .build()

    private fun getGoogleAccount() = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

    //Documentation:
    //--------------------------------------------------------------------------------------------------------//
    //Success Start Flow
    //start() -> startRecording() -> successRecording() -> startSession() -> successSession()

    //Success Stop Flow
    //stop() -> stopSession() -> successSession() -> stopRecording() -> successRecording()

    //Fail Start Recording Flow
    //start() -> startRecording() -> failRecording() -> stopRecording()

    //Fail Start Session Flow
    //start() -> startRecording() -> successRecording() -> startSession() -> failSession() -> stopRecording()

    //Fail Stop Session Flow
    //stop() -> stopSession() -> failSession() -> stopRecording()

    //Fail Stop Recording Flow
    //stop() -> stopSession() -> successSession() -> stopRecording()
    //--------------------------------------------------------------------------------------------------------//

    fun start(view: android.view.View){
        Log.i(TAG, "start()")

        checkPermissionsAndRun(FitActionRequestCode.INSERT_AND_VERIFY_SESSION)
    }

    fun stop(view: android.view.View){
        Log.i(TAG, "stop()")

        stopSession()
    }

    private fun failRecording(type: RecordingType, errs: List<Exception>){
        for (err in errs){
            Log.e(TAG, "failRecording ${type.name} ${err.message}")
        }

        //ignore error here to prevent infinite loop, list still recording
        listRecordingSubscriptions()
    }

    private fun successRecording(type: RecordingType, errs: List<Exception>){
        if(errs.isNotEmpty()){
            failRecording(type, errs)
        }

        Log.d(TAG, "successRecording ${type.name}")
        when (type) {
            RecordingType.SUBSCRIBE -> {
                startSession()
            }
            RecordingType.UNSUBSCRIBE -> {
                running = false
            }
        }
    }

    private fun failSession(type: SessionType, err: Exception){
        Log.e(TAG, "failSession ${type.name} ${err.message}")
        stopRecording()
    }

    private fun successSession(type: SessionType, session: Session){
        Log.e(TAG, "successSession ${type.name}")
        when (type) {
            SessionType.START -> {
                btnStart?.visibility = View.GONE
                btnStop?.visibility = View.VISIBLE
                running = true
                currentSession = session
            }
            SessionType.STOP -> {
                currentSession = null
                stopRecording()
            }
        }
    }

    private fun startRecording() {
        Log.i(TAG, "startRecording()")

        val returnExceptions = mutableListOf<Exception>()

        val dataTypeLen = dataSourceRequest.dataTypes.size
        for ((i, dt) in dataSourceRequest.dataTypes.withIndex()) {
            Fitness.getRecordingClient(this, getGoogleAccount())
                    .subscribe(dt)
                    .addOnSuccessListener {
                        Log.d(TAG, "i $i dataTypeLen $dataTypeLen dataType: $dt, subscribe success")
                        if(i >= dataTypeLen - 1){
                            successRecording(RecordingType.SUBSCRIBE, returnExceptions)
                        }
                     }
                    .addOnFailureListener { e: Exception? -> run {
                        val e2 = Exception("i $i dataTypeLen $dataTypeLen dataType: $dt, error ${e?.message}")
                        returnExceptions.add(e2)
                        if(i >= dataTypeLen - 1){
                            failRecording(RecordingType.SUBSCRIBE, returnExceptions)
                        }
                    } }
        }
    }

    private fun stopRecording() {
        Log.i(TAG, "stopRecording()")

        val returnExceptions = mutableListOf<Exception>()

        val dataTypeLen = dataSourceRequest.dataTypes.size
        for ((i, dt) in dataSourceRequest.dataTypes.withIndex()) {
            Fitness.getRecordingClient(this, getGoogleAccount())
                    .unsubscribe(dt)
                    .addOnSuccessListener {
                        Log.d(TAG, "i $i dataTypeLen $dataTypeLen dataType: $dt, unsubscribe success")
                        if(i >= dataTypeLen - 1){
                            successRecording(RecordingType.UNSUBSCRIBE, returnExceptions)
                        }
                    }
                    .addOnFailureListener { e: Exception? -> run {
                        val e2 = Exception("i $i dataTypeLen $dataTypeLen dataType: $dt, error ${e?.message}")
                        returnExceptions.add(e2)
                        if(i >= dataTypeLen - 1){
                            failRecording(RecordingType.UNSUBSCRIBE, returnExceptions)
                        }
                    } }
        }
    }

    private fun listRecordingSubscriptions(){
        Fitness.getRecordingClient(this, getGoogleAccount())
                .listSubscriptions()
                .addOnSuccessListener { subscriptions: List<Subscription> ->
                    for (sc in subscriptions) {
                        val dt = sc.dataType
                        Log.i(TAG, "Active subscription for data type: ${dt?.name}")
                    }
                }
                .addOnFailureListener { e: Exception? -> Log.e(TAG, "There was a problem listing subscriptions, error $e") }
    }

    private fun startSession() {
        Log.i(TAG, "startSession")

        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        val now = Date()
        cal.time = now

        val uuid = UUID.randomUUID()

        // 1. Subscribe to fitness data (see Recording Fitness Data)
        // 2. Create a session object
        // (provide a name, identifier, description, activity and start time)

        val sessionName = "RunFitGame session $now"
        val identifier = uuid.toString()
        val description = "RunFitGame RUNNING session $now"
        val startTime = cal.timeInMillis

        val session: Session = Session.Builder()
                .setName(sessionName)
                .setIdentifier(identifier)
                .setDescription(description)
                .setActivity(FitnessActivities.RUNNING)
                .setStartTime(startTime, TimeUnit.MILLISECONDS)
                .build()

        // 3. Use the Sessions client to start a session:
        Fitness.getSessionsClient(this, getGoogleAccount())
                .startSession(session)
                .addOnSuccessListener {
                    successSession(SessionType.START, session)
                }
                .addOnFailureListener { e: Exception? -> run {
                    failSession(SessionType.START, e!!)
                } }
    }

    private fun stopSession(){
        Log.i(TAG, "stopSession")

        // 1. Invoke the SessionsClient with the session identifier
                Fitness.getSessionsClient(this, getGoogleAccount())
                .stopSession(currentSession?.identifier)
                .addOnSuccessListener {
                    successSession(SessionType.STOP, currentSession!!)
                }
                .addOnFailureListener { e: Exception? -> run {
                    failSession(SessionType.STOP, e!!)
                } }
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

        when (at) {
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

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        btnStart?.visibility = View.VISIBLE
    }

    private fun checkPermissionsAndRun(fitActionRequestCode: FitActionRequestCode) {
        Log.i(TAG, "checkPermissionsAndRun")
        if (permissionApproved()) {
            fitSignIn(fitActionRequestCode)
        } else {
            requestRuntimePermissions(fitActionRequestCode)
        }
    }

    private val runningQOrLater =
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    private fun hasPermissions(context: Context, vararg permissions: String): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun permissionApproved(): Boolean {
        Log.i(TAG, "permissionApproved")
        return if (runningQOrLater) {
            hasPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.BODY_SENSORS)
        } else {
            Log.i(TAG, "runningQOrLater, permissionApproved true")
            true
        }
    }

    private fun requestRuntimePermissions(requestCode: FitActionRequestCode) {
        Log.i(TAG, "requestRuntimePermissions")
        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        requestCode.let {
                Log.i(TAG, "Requesting permission")
                // Request permission. It's possible this can be auto answered if device policy
                // sets the permission in a given state or the user denied the permission
                // previously and checked "Never ask again".
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.BODY_SENSORS),
                        requestCode.ordinal)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        Log.i(TAG, "onRequestPermissionsResult")
        when {
            grantResults.isEmpty() -> {
                // If user interaction was interrupted, the permission request
                // is cancelled and you receive empty arrays.
                Log.i(TAG, "onRequestPermissionsResult User interaction was cancelled.")
            }
            grantResults[0] == PackageManager.PERMISSION_GRANTED -> {
                // Permission was granted.
                val fitActionRequestCode = FitActionRequestCode.values()[requestCode]
                fitSignIn(fitActionRequestCode)
            }
            else -> {

                Log.i(TAG, "onRequestPermissionsResult Permission denied")
                // Permission denied.

                // In this Activity we've chosen to notify the user that they
                // have rejected a core permission for the app since it makes the Activity useless.
                // We're communicating this message in a Snackbar since this is a sample app, but
                // core permissions would typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
            }
        }
    }
}


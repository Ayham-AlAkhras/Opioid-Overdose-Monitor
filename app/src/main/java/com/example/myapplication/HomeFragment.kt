package com.example.myapplication

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.ncorti.slidetoact.SlideToActView
import com.ncorti.slidetoact.SlideToActView.OnSlideCompleteListener
import kotlinx.android.synthetic.main.fragment_home.*
import java.io.IOException


class HomeFragment : Fragment() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Request numbers for permissions
    val phoneRequestNumber = 101
    val smsRequestNumber = 102
    val locationRequestNumber = 103

    // Settings initial values
    var primaryNumber = ""
    var secondaryNumber = ""
    var smsCondition = false
    var callCondition = false
    var locationCondition = false
    var vibrationCondition = false
    var soundCondition = false
    var cdt = "5"

    // Monitor start condition
    var startAllowed = false

    // Start conditions
    var monitorStarted = false
    var emergencyStarted = false

    // Allows/prevents settings access
    var hideIndicator = 0

    // Vital signs
    var bloodOxygen = ""
    var breathRate = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Initialize location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Settings button
        fab_settings.setOnClickListener {
            when (hideIndicator) {
                0 -> {
                    findNavController().navigate(R.id.action_homeFragment_to_settingsFragment)
                }
                1 -> {
                    Toast.makeText(context as Activity, "Stop monitor to access settings", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    Toast.makeText(context as Activity, "Release monitor slider to access settings", Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    Toast.makeText(context as Activity, "Cancel emergency to access settings", Toast.LENGTH_SHORT).show()
                }
                4 -> {
                    Toast.makeText(context as Activity, "Release emergency slider to access settings", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fab_bluetooth.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_bluetoothFragment)
        }
        // Help button
        fab_help.setOnClickListener {
            val builder1 = AlertDialog.Builder(context as Activity)
            builder1.apply {
                setMessage("The Opioid Overdose Monitor companion application is designed to pair to the proprietary Mask Monitor™ breath " +
                        "and heart rate measurement device. The application main screen allows the user to:\n   1. Start/stop the monitoring device\n   2. Trigger/cancel an emergency\n   3. Navigate to the application settings\n" +
                        "The application settings screen allows the user to:\n   1. Customize emergency contacts\n   2. Customize emergency actions\n   3. Customize the emergency alarm\n" +
                        "Upon an emergency being triggered, either via the monitor or manual emergency slider, the application will perform the customizable emergency actions after a 5 second countdown. During the countdown alarm, " +
                        "the user can cancel the emergency using the same slider. If the call option is selected, the application will call the primary emergency contact. If the SMS option is selected, the application will " +
                        "send an SMS message to both emergency contacts containing the user's current vital readings and location.")
                setTitle("Help")
                setPositiveButton("OK"){ _, _ ->}
            }
            val dialog1 = builder1.create()
            dialog1.show()
        }

        // Load customizable settings
        loadSettings()

        // Monitor slider listener
        monitorSlider.onSlideCompleteListener = object : OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                // Check if call enabled
                if (callCondition) {
                    // Check call permissions
                    checkForPermission(android.Manifest.permission.CALL_PHONE, "phone", phoneRequestNumber)
                    when (PackageManager.PERMISSION_GRANTED) {
                        ContextCompat.checkSelfPermission(context as Activity, android.Manifest.permission.CALL_PHONE) -> {
                            startAllowed = true
                        }
                    }
                    when(PackageManager.PERMISSION_DENIED) {
                        ContextCompat.checkSelfPermission(context as Activity, android.Manifest.permission.CALL_PHONE) -> {
                            startAllowed = false
                            stopMonitor()
                        }
                    }
                }
                else {
                    startAllowed = true
                }

                // Check if SMS enabled
                if (smsCondition) {
                    // Check SMS permissions
                    checkForPermission(android.Manifest.permission.SEND_SMS, "sms", smsRequestNumber)
                    when(PackageManager.PERMISSION_DENIED) {
                        ContextCompat.checkSelfPermission(context as Activity, android.Manifest.permission.SEND_SMS) -> {
                            startAllowed = false
                            stopMonitor()
                        }
                    }
                    // Check location permissions
                    if (locationCondition) {
                        checkForPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, "location", locationRequestNumber)
                        when(PackageManager.PERMISSION_DENIED) {
                            ContextCompat.checkSelfPermission(context as Activity, android.Manifest.permission.ACCESS_FINE_LOCATION) -> {
                                startAllowed = false
                                stopMonitor()
                            }
                        }
                    }
                }

                // Start/stop monitor
                if (startAllowed)
                {
                    monitorStarted = if (!monitorStarted) {
                        startMonitor()
                        true
                    } else {
                        stopMonitor()
                        false
                    }
                }
            }
        }

        // Hide settings button on touch slider
        monitorSlider.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (hideIndicator == 0) {
                            hideIndicator = 2
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (hideIndicator == 2) {
                            hideIndicator = 0
                        }
                    }
                }
                return v?.onTouchEvent(event) ?: true
            }
        })
        monitorSlider.onSlideToActAnimationEventListener = object : SlideToActView.OnSlideToActAnimationEventListener {
            override fun onSlideCompleteAnimationStarted(view: SlideToActView, threshold: Float) {
                if (hideIndicator == 0) {
                    hideIndicator = 2
                }
            }
            override fun onSlideCompleteAnimationEnded(view: SlideToActView) {
                if (hideIndicator == 2) {
                    hideIndicator = 0
                }
            }
            override fun onSlideResetAnimationStarted(view: SlideToActView) {
                if (hideIndicator == 0) {
                    hideIndicator = 2
                }
            }
            override fun onSlideResetAnimationEnded(view: SlideToActView) {
                if (hideIndicator == 2) {
                    hideIndicator = 0
                }
            }
        }

        // Emergency slider listener
        emergencySlider.onSlideCompleteListener = object : OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {

                // Check if call enabled
                if (callCondition) {
                    // Check call permissions
                    checkForPermission(android.Manifest.permission.CALL_PHONE, "phone", phoneRequestNumber)
                    when (PackageManager.PERMISSION_GRANTED) {
                        ContextCompat.checkSelfPermission(context as Activity, android.Manifest.permission.CALL_PHONE) -> {
                            startAllowed = true
                        }
                    }
                    when(PackageManager.PERMISSION_DENIED) {
                        ContextCompat.checkSelfPermission(context as Activity, android.Manifest.permission.CALL_PHONE) -> {
                            startAllowed = false
                            stopEmergency()
                        }
                    }
                }
                else {
                    startAllowed = true
                }

                // Check if SMS enabled
                if (smsCondition) {
                    // Check SMS permissions
                    checkForPermission(android.Manifest.permission.SEND_SMS, "sms", smsRequestNumber)
                    when(PackageManager.PERMISSION_DENIED) {
                        ContextCompat.checkSelfPermission(context as Activity, android.Manifest.permission.SEND_SMS) -> {
                            startAllowed = false
                            stopEmergency()
                        }
                    }
                    // Check location permissions
                    if (locationCondition) {
                        checkForPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, "location", locationRequestNumber)
                        when(PackageManager.PERMISSION_DENIED) {
                            ContextCompat.checkSelfPermission(context as Activity, android.Manifest.permission.ACCESS_FINE_LOCATION) -> {
                                startAllowed = false
                                stopEmergency()
                            }
                        }
                    }
                }

                // Start/stop monitor
                if (startAllowed)
                {
                    emergencyStarted = if (!emergencyStarted) {
                        startEmergency()
                        true
                    } else {
                        stopEmergency()
                        false
                    }
                }
            }
        }

        // Hide settings while slider animation playing to prevent crash
        emergencySlider.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (hideIndicator == 0)
                        {
                            hideIndicator = 4
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (hideIndicator == 4) {
                            hideIndicator = 0
                        }
                    }
                }
                return v?.onTouchEvent(event) ?: true
            }
        })
        emergencySlider.onSlideToActAnimationEventListener = object : SlideToActView.OnSlideToActAnimationEventListener {
            override fun onSlideCompleteAnimationStarted(view: SlideToActView, threshold: Float) {
                if (hideIndicator == 0) {
                    hideIndicator = 4
                }
            }
            override fun onSlideCompleteAnimationEnded(view: SlideToActView) {
                if (hideIndicator == 4) {
                    hideIndicator = 0
                }
            }
            override fun onSlideResetAnimationStarted(view: SlideToActView) {
                if (hideIndicator == 0) {
                    hideIndicator = 4
                }
            }
            override fun onSlideResetAnimationEnded(view: SlideToActView) {
                if (hideIndicator == 4) {
                    hideIndicator = 0
                }
            }
        }
    }

    /*
    private fun readBlueToothDataFromMothership(bluetoothSocket: BluetoothSocket) {

        val bluetoothSocketInputStream = bluetoothSocket.inputStream
        val buffer = ByteArray(1024)
        var bytes: Int
        //Loop to listen for received bluetooth messages
        while (true) {
            try {
                bytes = bluetoothSocketInputStream.read(buffer)
                val readMessage = String(buffer, 0, bytes)
                textView2.text = readMessage
            } catch (e: IOException) {
                e.printStackTrace()
                break
            }
        }
    }*/

    // Load settings
    private fun loadSettings() {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val contact1 = sp.getString("contact1", "")
        val contact2 = sp.getString("contact2", "")
        val call = sp.getBoolean("call",false)
        val sms = sp.getBoolean("sms",false)
        val location = sp.getBoolean("location",false)
        val vibration = sp.getBoolean("vibration",false)
        val sound = sp.getBoolean("sound",false)
        // val countdown = sp.getString("time", "")

        if (contact1 != null) {
            primaryNumber = contact1
        }
        if (contact2 != null) {
            secondaryNumber = contact2
        }
        smsCondition = sms
        callCondition = call
        vibrationCondition = vibration
        soundCondition = sound
        locationCondition = location

        /*
                <ListPreference
            app:useSimpleSummaryProvider="true"
            app:defaultValue="1"
            app:key="time"
            app:title="Countdown timer"
            app:entries="@array/reply_entries"
            app:entryValues="@array/reply_entries"
            />

        if (countdown != null) {
            cdt = countdown
        }
         */

    }

    // Start/stop monitor
    private fun startMonitor() {
        progressBar.visibility = View.VISIBLE
        monitorSlider.isReversed = true
        monitorSlider.text = "Stop Monitor"
        monitorSlider.resetSlider()
        hideIndicator = 1
    }
    private fun stopMonitor() {
        progressBar.visibility = View.GONE
        monitorSlider.isReversed = false
        monitorSlider.text = "Start Monitor"
        monitorSlider.resetSlider()
        hideIndicator = 0
    }

    // Start/stop emergency
    private fun startEmergency() {
        // Reverse slider
        emergencySlider.isReversed = true
        emergencySlider.text = "CANCEL"
        emergencySlider.resetSlider()

        // Show emergency text
        progressBar.visibility = View.GONE
        infoTexts.visibility = View.GONE
        emergencyTexts.visibility = View.VISIBLE
        hideIndicator = 3

        // Start timer
        timer.start()
    }
    private fun stopEmergency() {
        // Stop timer
        timer.cancel()

        // Hide emergency text
        infoTexts.visibility = View.VISIBLE
        emergencyTexts.visibility = View.GONE
        if (monitorStarted) {progressBar.visibility = View.VISIBLE}
        hideIndicator = 0

        // Reverse slider
        emergencySlider.isReversed = false
        emergencySlider.text = "EMERGENCY"
        emergencySlider.resetSlider()
    }

    // Check permissions
    private fun checkForPermission(permission: String, name: String, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(context as Activity, permission) == PackageManager.PERMISSION_GRANTED -> {
                }
                shouldShowRequestPermissionRationale(permission) -> showDialog(permission, name, requestCode)
                else -> ActivityCompat.requestPermissions(context as Activity, arrayOf(permission), requestCode)
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        fun innerCheck(name: String) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(context as Activity, "$name permission refused", Toast.LENGTH_SHORT).show()
            } else{
                Toast.makeText(context as Activity, "$name permission granted", Toast.LENGTH_SHORT).show()
            }
        }
        when (requestCode) {
            phoneRequestNumber -> innerCheck("phone")
        }
    }
    private fun showDialog(permission: String, name: String, requestCode: Int) {
        val builder = AlertDialog.Builder(context as Activity)
        builder.apply {
            setMessage("Permission to access your $name is required to use this app.")
            setTitle("Permission required")
            setPositiveButton("OK"){ _, _ ->
                ActivityCompat.requestPermissions(context as Activity, arrayOf(permission),requestCode)
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    // Alarm functions
    fun vibratePhone() {
        val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(500)
        }
    }
    fun alarmStart() {
        val mp = MediaPlayer.create(context,R.raw.alarm)
        mp.start()
    }

    // Timer countdown
    // TODO:(Customizable timer)
    private val timer = object: CountDownTimer((cdt.toLong()+1)*1000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            emergencyTextView3.text = (millisUntilFinished/1000).toString()
            if (vibrationCondition){vibratePhone()}
            if (soundCondition){alarmStart()}
        }
        override fun onFinish() {
            Toast.makeText(context, "Contacting emergency contact", Toast.LENGTH_SHORT).show()
            if (callCondition) {
                // Check call permission
                checkForPermission(android.Manifest.permission.CALL_PHONE, "phone", phoneRequestNumber)
                when (PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.checkSelfPermission(context as Activity, android.Manifest.permission.CALL_PHONE) -> {
                        // Call
                        val callIntent = Intent(Intent.ACTION_CALL)
                        callIntent.data = Uri.parse("tel:$primaryNumber")
                        startActivity(callIntent)
                    }
                }
            }
            if (smsCondition) {
                // Check SMS permission
                checkForPermission(android.Manifest.permission.SEND_SMS, "SMS", smsRequestNumber)
                when (PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.checkSelfPermission(context as Activity, android.Manifest.permission.SEND_SMS) -> {
                        // Send SMS
                        val sms = SmsManager.getDefault()
                        sms.sendTextMessage(primaryNumber, "ME", "MY OPIOID OVERDOSE MONITOR HAS BEEN TRIGGERED, MY BLOOD OXYGEN IS $bloodOxygen% AND MY BREATHING RATE IS $breathRate BPM",null,null)
                        sms.sendTextMessage(secondaryNumber, "ME", "MY OPIOID OVERDOSE MONITOR HAS BEEN TRIGGERED, MY BLOOD OXYGEN IS $bloodOxygen% AND MY BREATHING RATE IS $breathRate BPM",null,null)
                        // Send location
                        if (locationCondition){
                            checkForPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, "location", locationRequestNumber)
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context as Activity, android.Manifest.permission.ACCESS_FINE_LOCATION) -> {
                                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                                        if (lastLocation != null) {
                                            val smsBody = "http://maps.google.com?q=" + lastLocation.latitude + "," + lastLocation.longitude
                                            sms.sendTextMessage(primaryNumber, "ME", smsBody, null, null)
                                            sms.sendTextMessage(secondaryNumber, "ME", smsBody, null, null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
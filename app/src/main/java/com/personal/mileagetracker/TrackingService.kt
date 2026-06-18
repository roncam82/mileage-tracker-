package com.personal.mileagetracker

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var db: AppDatabase

    private var currentTripLocations = mutableListOf<Location>()
    private var isTracking = false
    private var stillCounter = 0

    private val activityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ActivityRecognitionResult.hasResult(intent)) {
                val result = ActivityRecognitionResult.extractResult(intent)
                val activity = result.mostProbableActivity

                when (activity.type) {
                    DetectedActivity.IN_VEHICLE -> {
                        if (activity.confidence > 70 && !isTracking) {
                            startTripTracking()
                        }
                        stillCounter = 0
                    }
                    DetectedActivity.STILL -> {
                        if (isTracking) {
                            stillCounter++
                            if (stillCounter > 3) {
                                endTripTracking()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        activityRecognitionClient = ActivityRecognition.getClient(this)
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "trips.db").build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    if (isTracking) {
                        currentTripLocations.add(location)
                    }
                }
            }
        }

        registerReceiver(activityReceiver, IntentFilter("ACTIVITY_RECOGNITION_DATA"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification("Tracking enabled"))
        requestActivityUpdates()
        return START_STICKY
    }

    private fun requestActivityUpdates() {
        val intent = Intent("ACTIVITY_RECOGNITION_DATA")
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            activityRecognitionClient.requestActivityUpdates(60000, pendingIntent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startTripTracking() {
        isTracking = true
        currentTripLocations.clear()
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        updateNotification("Trip in progress...")
        Log.d("TrackingService", "Trip started")
    }

    private fun endTripTracking() {
        isTracking = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        updateNotification("Tracking enabled")
        Log.d("TrackingService", "Trip ended")

        if (currentTripLocations.size > 2) {
            saveTrip()
        }
        stillCounter = 0
    }

    private fun saveTrip() {
        val startLoc = currentTripLocations.first()
        val endLoc = currentTripLocations.last()
        
        var totalDistance = 0f
        for (i in 0 until currentTripLocations.size - 1) {
            totalDistance += currentTripLocations[i].distanceTo(currentTripLocations[i + 1])
        }
        val distanceMiles = (totalDistance / 1609.34 * 100.0).roundToInt() / 100.0

        val trip = Trip(
            startTime = startLoc.time,
            endTime = endLoc.time,
            startLat = startLoc.latitude,
            startLng = startLoc.longitude,
            endLat = endLoc.latitude,
            endLng = endLoc.longitude,
            distanceMiles = distanceMiles
        )

        CoroutineScope(Dispatchers.IO).launch {
            db.tripDao().insert(trip)
        }
    }

    private fun createNotification(text: String): Notification {
        val channelId = "mileage_tracking"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Mileage Tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Mileage Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, createNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(activityReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

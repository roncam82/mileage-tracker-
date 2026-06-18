package com.personal.mileagetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDatabase
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "trips.db").build()

        requestPermissions()
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent()
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val requestLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
        
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) requestLauncher.launch(toRequest.toTypedArray())
    }

    @Composable
    fun AppContent() {
        val trips by db.tripDao().getAllTrips().collectAsState(initial = emptyList())
        var exportPath by remember { mutableStateOf<String?>(null) }

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Mileage Tracker", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = {
                val serviceIntent = Intent(this@MainActivity, TrackingService::class.java)
                startForegroundService(serviceIntent)
            }) {
                Text("Start Background Tracking")
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                lifecycleScope.launch {
                    exportPath = CsvExporter.exportTrips(this@MainActivity, trips)
                }
            }) {
                Text("Export to IRS CSV")
            }
            
            if (exportPath != null) {
                Text("Saved to: $exportPath", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Recent Trips:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(trips) { trip ->
                    TripItem(trip = trip, onClassify = { newPurpose ->
                        lifecycleScope.launch {
                            db.tripDao().update(trip.copy(purpose = newPurpose))
                        }
                    })
                    HorizontalDivider()
                }
            }
        }
    }

    @Composable
    fun TripItem(trip: Trip, onClassify: (String) -> Unit) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Date: ${dateFormat.format(Date(trip.startTime))}")
            Text("Distance: ${trip.distanceMiles} miles")
            Text("Purpose: ${trip.purpose}", color = if (trip.purpose == "Business") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Button(onClick = { onClassify("Business") }, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Business")
                }
                Button(onClick = { onClassify("Personal") }) {
                    Text("Personal")
                }
            }
        }
    }
}

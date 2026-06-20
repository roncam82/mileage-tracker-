package com.personal.mileagetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDatabase
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "trips.db").fallbackToDestructiveMigration().build()

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
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
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
        var selectedTrip by remember { mutableStateOf<Trip?>(null) }
        var exportPath by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

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
            
            // The Map View
            if (selectedTrip != null && selectedTrip!!.routePoints.isNotEmpty()) {
                AndroidMap(trip = selectedTrip!!)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text("Recent Trips (Tap to view map):", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(trips) { trip ->
                    TripItem(
                        trip = trip, 
                        isSelected = selectedTrip?.id == trip.id,
                        onClassify = { newPurpose ->
                            lifecycleScope.launch {
                                db.tripDao().update(trip.copy(purpose = newPurpose))
                            }
                        },
                        onClick = { 
                            selectedTrip = if (selectedTrip?.id == trip.id) null else trip 
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    @Composable
    fun AndroidMap(trip: Trip) {
        val context = LocalContext.current
        AndroidView(
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                val mapView = MapView(ctx)
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.setMultiTouchControls(true)
                
                val points = trip.routePoints.split(";").mapNotNull { pointStr ->
                    val parts = pointStr.split(",")
                    if (parts.size == 2) {
                        GeoPoint(parts[0].toDouble(), parts[1].toDouble())
                    } else null
                }

                if (points.isNotEmpty()) {
                    val line = Polyline()
                    line.setPoints(points)
                    mapView.overlays.add(line)
                    
                    val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(points)
                    mapView.zoomToBoundingBox(bounds.increaseByScale(1.2f), false)
                }
                mapView
            },
            modifier = Modifier.fillMaxWidth().height(300.dp)
        )
    }

    @Composable
    fun TripItem(trip: Trip, isSelected: Boolean, onClassify: (String) -> Unit, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable { onClick() }
        ) {
            Text("Date: ${dateFormat.format(Date(trip.startTime))}", fontWeight = if(isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
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

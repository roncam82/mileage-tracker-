package com.personal.mileagetracker

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {
    
    fun exportTrips(context: Context, trips: List<Trip>): String? {
        val dateFormat = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US)
        val fileName = "MileageLog_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv"
        
        // Save to app-specific Downloads folder (No special permissions needed)
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir == null) return null
        val file = File(downloadDir, fileName)
        
        try {
            FileWriter(file).use { writer ->
                writer.append("Date,Starting Location,Destination,Business Purpose,Miles Driven\n")
                trips.forEach { trip ->
                    writer.append("\"${dateFormat.format(Date(trip.startTime))}\",")
                    writer.append("\"${String.format("%.4f,%.4f", trip.startLat, trip.startLng)}\",")
                    writer.append("\"${String.format("%.4f,%.4f", trip.endLat, trip.endLng)}\",")
                    writer.append("\"${trip.purpose}\",")
                    writer.append("\"${trip.distanceMiles}\"\n")
                }
                writer.flush()
            }
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

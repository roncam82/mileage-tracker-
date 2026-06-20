    private fun saveTrip() {
        val startLoc = currentTripLocations.first()
        val endLoc = currentTripLocations.last()
        
        var totalDistance = 0f
        for (i in 0 until currentTripLocations.size - 1) {
            totalDistance += currentTripLocations[i].distanceTo(currentTripLocations[i + 1])
        }
        val distanceMiles = (totalDistance / 1609.34 * 100.0).roundToInt() / 100.0

        // Convert the list of locations to a simple string: "lat,lng;lat,lng;..."
        val routeString = currentTripLocations.joinToString(";") { "${it.latitude},${it.longitude}" }

        val trip = Trip(
            startTime = startLoc.time,
            endTime = endLoc.time,
            startLat = startLoc.latitude,
            startLng = startLoc.longitude,
            endLat = endLoc.latitude,
            endLng = endLoc.longitude,
            distanceMiles = distanceMiles,
            routePoints = routeString // Save the route!
        )

        CoroutineScope(Dispatchers.IO).launch {
            db.tripDao().insert(trip)
        }
    }

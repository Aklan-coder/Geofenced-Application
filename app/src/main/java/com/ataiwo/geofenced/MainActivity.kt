package com.ataiwo.geofenced

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var locationManager: LocationManager

    private lateinit var radarContainer: FrameLayout
    private lateinit var zoneCircle: android.view.View
    private lateinit var centerDot: android.view.View
    private lateinit var userDot: android.view.View

    private lateinit var statusCard: LinearLayout
    private lateinit var distanceCard: LinearLayout
    private lateinit var radiusCard: LinearLayout

    private lateinit var statusText: TextView
    private lateinit var distanceText: TextView
    private lateinit var radiusText: TextView

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var setButton: Button

    private val geofenceRadiusMeters = 200f

    private val defaultCenter = Location("defaultCenter").apply {
        latitude = 35.22755
        longitude = -80.84298
    }

    private var geofenceCenter: Location = Location(defaultCenter)
    private var currentLocation: Location? = null
    private var tracking = false
    private var zoneSet = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startTracking()
            } else {
                Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        radarContainer = findViewById(R.id.radarContainer)
        zoneCircle = findViewById(R.id.zoneCircle)
        centerDot = findViewById(R.id.centerDot)
        userDot = findViewById(R.id.userDot)

        statusCard = findViewById(R.id.statusCard)
        distanceCard = findViewById(R.id.distanceCard)
        radiusCard = findViewById(R.id.radiusCard)

        statusText = findViewById(R.id.statusText)
        distanceText = findViewById(R.id.distanceText)
        radiusText = findViewById(R.id.radiusText)

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        setButton = findViewById(R.id.setButton)

        styleViews()

        radiusText.text = "${geofenceRadiusMeters.toInt()} m"
        updateUi()

        startButton.setOnClickListener { ensurePermissionAndStart() }
        stopButton.setOnClickListener { stopTracking() }
        setButton.setOnClickListener { setCenterToCurrentLocation() }
    }

    private fun styleViews() {
        zoneCircle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#1A1D9E75"))
            setStroke(6, Color.parseColor("#1D9E75"))
        }

        centerDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#0F6E56"))
        }

        userDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#378ADD"))
            setStroke(4, Color.WHITE)
        }

        statusCard.background = roundedCard("#F3EFE6")
        distanceCard.background = roundedCard("#F3EFE6")
        radiusCard.background = roundedCard("#F3EFE6")

        startButton.backgroundTintList =
            android.content.res.ColorStateList.valueOf(Color.parseColor("#1D9E75"))
        stopButton.backgroundTintList =
            android.content.res.ColorStateList.valueOf(Color.parseColor("#DADADA"))
        setButton.backgroundTintList =
            android.content.res.ColorStateList.valueOf(Color.parseColor("#DADADA"))

        startButton.setTextColor(Color.WHITE)
        stopButton.setTextColor(Color.parseColor("#3D3D3D"))
        setButton.setTextColor(Color.parseColor("#3D3D3D"))
    }

    private fun roundedCard(color: String): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 28f
            setColor(Color.parseColor(color))
        }
    }

    private fun ensurePermissionAndStart() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startTracking()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startTracking() {
        if (tracking) return
        tracking = true

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                this
            )
        } catch (_: SecurityException) {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show()
        }

        updateUi()
        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        if (!tracking) return
        tracking = false

        try {
            locationManager.removeUpdates(this)
        } catch (_: SecurityException) {
            // ignore
        }

        currentLocation = null
        zoneSet = false

        statusText.text = "Tracking stopped"
        distanceText.text = "-- m"

        statusCard.background = roundedCard("#F3EFE6")
        distanceCard.background = roundedCard("#F3EFE6")
        radiusCard.background = roundedCard("#F3EFE6")

        positionDotAtCenter()
        Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show()
    }

    private fun setCenterToCurrentLocation() {
        val location = currentLocation
        if (location == null) {
            Toast.makeText(this, "No GPS fix yet", Toast.LENGTH_SHORT).show()
            return
        }

        geofenceCenter = Location(location)
        zoneSet = true
        updateUi()
        Toast.makeText(this, "Center updated", Toast.LENGTH_SHORT).show()
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = Location(location)
        updateUi()
    }

    private fun updateUi() {
        val location = currentLocation
        if (location == null) {
            statusText.text = "Waiting for GPS..."
            distanceText.text = "-- m"
            statusCard.background = roundedCard("#F3EFE6")
            distanceCard.background = roundedCard("#F3EFE6")
            radiusCard.background = roundedCard("#F3EFE6")
            positionDotAtCenter()
            return
        }

        if (!zoneSet) {
            statusText.text = "Zone not set"
            distanceText.text = "-- m"
            statusCard.background = roundedCard("#F3EFE6")
            distanceCard.background = roundedCard("#F3EFE6")
            radiusCard.background = roundedCard("#F3EFE6")
            moveUserDot(location, geofenceCenter, 0f)
            return
        }

        val distance = geofenceCenter.distanceTo(location)
        distanceText.text = "${distance.toInt()} m"

        val inside = distance <= geofenceRadiusMeters
        if (inside) {
            statusText.text = "INSIDE ZONE"
            statusCard.background = roundedCard("#DFF5EE")
            distanceCard.background = roundedCard("#F3EFE6")
            radiusCard.background = roundedCard("#F3EFE6")
        } else {
            statusText.text = "OUTSIDE ZONE"
            statusCard.background = roundedCard("#FDE7E7")
            distanceCard.background = roundedCard("#FDE7E7")
            radiusCard.background = roundedCard("#FDE7E7")
        }

        moveUserDot(location, geofenceCenter, distance)
    }

    private fun positionDotAtCenter() {
        radarContainer.post {
            userDot.translationX = 0f
            userDot.translationY = 0f
        }
    }

    private fun moveUserDot(location: Location, center: Location, distanceMeters: Float) {
        radarContainer.post {
            val containerRadius = (radarContainer.width / 2f) - 24f
            val maxShowDistance = geofenceRadiusMeters

            val showDistance = if (distanceMeters > maxShowDistance) maxShowDistance else distanceMeters
            val scale = if (maxShowDistance == 0f) 0f else showDistance / maxShowDistance
            val displayDistance = scale * containerRadius

            val angle = bearingDegrees(center, location)
            val radians = Math.toRadians(angle)

            val dx = (sin(radians) * displayDistance).toFloat()
            val dy = (-cos(radians) * displayDistance).toFloat()

            userDot.translationX = dx
            userDot.translationY = dy
        }
    }

    private fun bearingDegrees(from: Location, to: Location): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationManager.removeUpdates(this)
        } catch (_: SecurityException) {
            // ignore
        }
    }
}
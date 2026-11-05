package app.timeline.agentique.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }
}

object DeviceLocation {
    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): GeoPoint? {
        val granted =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val best: Location? = lm.getProviders(true)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        return best?.let { GeoPoint(it.latitude, it.longitude) }
    }
}

object LocationResolver {
    /** Device location when allowed and available, otherwise the saved home. Never an invented default. */
    fun current(ctx: Context, s: AppSettings): GeoPoint? =
        (if (s.useDeviceLocation) DeviceLocation.lastKnown(ctx) else null) ?: s.home
}

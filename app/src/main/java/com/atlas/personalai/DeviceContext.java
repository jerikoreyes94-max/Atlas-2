package com.atlas.personalai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import org.json.JSONObject;

public final class DeviceContext {
    private DeviceContext() {}

    public static JSONObject snapshot(Context c) {
        JSONObject o = new JSONObject();
        try {
            BatteryManager bm = (BatteryManager)c.getSystemService(Context.BATTERY_SERVICE);
            o.put("batteryPercent", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            o.put("charging", bm.isCharging());
            o.put("network", network(c));
            o.put("timeMillis", System.currentTimeMillis());
            o.put("location", locationJson(c));
            o.put("screen", AtlasAccessibilityService.screenSnapshot());
        } catch (Exception ignored) {}
        return o;
    }

    public static String batterySummary(Context c) {
        try {
            BatteryManager bm = (BatteryManager)c.getSystemService(Context.BATTERY_SERVICE);
            int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return "Battery is at " + pct + " percent" + (bm.isCharging() ? " and charging." : ".");
        } catch (Exception e) { return "I couldn't read the battery right now."; }
    }

    private static String network(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) return "offline";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "cellular";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ethernet";
            return "connected";
        } catch (Exception e) { return "unknown"; }
    }

    public static JSONObject locationJson(Context c) {
        JSONObject o = new JSONObject();
        try {
            if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                o.put("available", false);
                return o;
            }
            LocationManager lm = (LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                try {
                    Location x = lm.getLastKnownLocation(p);
                    if (x != null && (best == null || x.getTime() > best.getTime())) best = x;
                } catch (Exception ignored) {}
            }
            if (best == null) { o.put("available", false); return o; }
            o.put("available", true);
            o.put("lat", best.getLatitude());
            o.put("lon", best.getLongitude());
            o.put("accuracyMeters", best.getAccuracy());
            o.put("ageMs", Math.max(0, System.currentTimeMillis() - best.getTime()));
        } catch (Exception e) {
            try { o.put("available", false); } catch (Exception ignored) {}
        }
        return o;
    }

    public static String locationSummary(Context c) {
        try {
            JSONObject o = locationJson(c);
            if (!o.optBoolean("available")) return "Location isn't available yet. Grant location access and try again.";
            return String.format(java.util.Locale.US,
                "Your last known location is %.5f, %.5f with about %.0f meter accuracy.",
                o.optDouble("lat"), o.optDouble("lon"), o.optDouble("accuracyMeters"));
        } catch (Exception e) { return "I couldn't read your location."; }
    }
}

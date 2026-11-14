package com.pulselab.huaweigt7;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Workout normalized from FIT/GPX/TCX to the existing Pulse Lab schemaVersion=1 contract. */
final class ActivityData {
    static final class Sample {
        long timeMs;
        Double latitude, longitude, altitude, speed, distance, cadence, power;
        Integer hr;

        JSONObject toJson() throws Exception {
            JSONObject j = new JSONObject().put("timeMs", timeMs);
            put(j, "latitude", latitude);
            put(j, "longitude", longitude);
            put(j, "altitudeMeters", altitude);
            put(j, "speedMetersPerSecond", speed);
            put(j, "distanceMeters", distance);
            put(j, "cadenceRpm", cadence);
            put(j, "powerWatts", power);
            if (hr != null && hr >= 30 && hr <= 240) j.put("heartRateBpm", hr);
            return j;
        }
        boolean hasGps() {
            return latitude != null && longitude != null && finite(latitude)
                && finite(longitude) && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
        }
    }

    long startTimeMs, endTimeMs;
    double durationSeconds, distanceMeters, caloriesKcal;
    int sportType = 0, avgHr, minHr, maxHr;
    String sportName = "Entraînement", format = "";
    final List<Sample> samples = new ArrayList<>();

    static boolean finite(Double d) { return d != null && !d.isNaN() && !d.isInfinite(); }

    static void put(JSONObject target, String name, Double value) throws Exception {
        if (finite(value)) target.put(name, value);
    }

    void normalize() {
        samples.removeIf(p -> p.timeMs < 946684800000L || p.timeMs > System.currentTimeMillis() + 86400000L);
        samples.sort(Comparator.comparingLong(p -> p.timeMs));
        if (!samples.isEmpty()) {
            long first = samples.get(0).timeMs, last = samples.get(samples.size() - 1).timeMs;
            if (startTimeMs == 0 || first < startTimeMs) startTimeMs = first;
            if (last > endTimeMs) endTimeMs = last;
        }
        if (startTimeMs <= 0 || startTimeMs > System.currentTimeMillis() + 86400000L)
            throw new IllegalArgumentException("Aucun horaire de séance valide dans ce fichier.");
        if (durationSeconds <= 0 && endTimeMs > startTimeMs)
            durationSeconds = (endTimeMs - startTimeMs) / 1000.0;
        if (endTimeMs <= 0 && durationSeconds > 0)
            endTimeMs = startTimeMs + (long)(durationSeconds * 1000);
        if (distanceMeters <= 0) {
            double lastDistance = 0;
            for (Sample p : samples)
                if (finite(p.distance) && p.distance > lastDistance) lastDistance = p.distance;
            if (lastDistance > 0) distanceMeters = lastDistance;
            else {
                // GPX does not necessarily contain distance. Estimate from consecutive positions.
                Sample previous = null;
                double estimated = 0;
                for (Sample p : samples) {
                    if (!p.hasGps()) continue;
                    if (previous != null) {
                        double segment = haversine(previous.latitude, previous.longitude, p.latitude, p.longitude);
                        if (segment < 500) estimated += segment; // exclude GPS jumps
                    }
                    previous = p;
                }
                distanceMeters = estimated;
            }
        }
        int count = 0, sum = 0, lo = 300, hi = 0;
        for (Sample p : samples) {
            if (p.hr != null && p.hr >= 30 && p.hr <= 240) {
                count++; sum += p.hr; lo = Math.min(lo, p.hr); hi = Math.max(hi, p.hr);
            }
        }
        if (count > 0) {
            if (avgHr <= 0) avgHr = Math.round((float)sum/count);
            if (minHr <= 0) minHr = lo;
            if (maxHr <= 0) maxHr = hi;
        }
    }

    private static double haversine(double a, double b, double c, double d) {
        double r = Math.PI / 180.0;
        double x = Math.sin((c-a)*r/2);
        double y = Math.sin((d-b)*r/2);
        double v = x*x + Math.cos(a*r)*Math.cos(c*r)*y*y;
        return 2 * 6371000.0 * Math.asin(Math.min(1, Math.sqrt(v)));
    }

    String fileName() { return "huawei-watch-gt7-" + startTimeMs + ".json"; }

    JSONObject toJson() throws Exception {
        normalize();
        JSONObject hr = new JSONObject();
        if (avgHr > 0) hr.put("averageBpm", avgHr);
        if (minHr > 0) hr.put("minimumBpm", minHr);
        if (maxHr > 0) hr.put("maximumBpm", maxHr);
        JSONObject activity = new JSONObject()
            .put("startTimeMs", startTimeMs)
            .put("endTimeMs", endTimeMs)
            .put("sportType", sportType)
            .put("sportName", sportName)
            .put("durationSeconds", durationSeconds)
            .put("distanceMeters", distanceMeters)
            .put("caloriesKcal", caloriesKcal)
            .put("heartRate", hr);
        JSONArray gps = new JSONArray(), heart = new JSONArray(), all = new JSONArray();
        for (Sample p : samples) {
            JSONObject one = p.toJson();
            all.put(one);
            if (p.hr != null && p.hr >= 30 && p.hr <= 240)
                heart.put(new JSONObject().put("timeMs", p.timeMs).put("bpm", p.hr));
            if (p.hasGps()) {
                JSONObject point = new JSONObject()
                    .put("timeMs", p.timeMs)
                    .put("latitude", p.latitude)
                    .put("longitude", p.longitude);
                put(point, "altitudeMeters", p.altitude);
                put(point, "speedMetersPerSecond", p.speed);
                if (p.hr != null && p.hr >= 30 && p.hr <= 240) point.put("heartRateBpm", p.hr);
                gps.put(point);
            }
        }
        return new JSONObject()
            .put("schemaVersion", 1)
            .put("id", "huawei-watch-gt7:" + startTimeMs)
            .put("source", new JSONObject()
                .put("deviceModel", "HUAWEI WATCH GT 7")
                .put("transport", "file-import")
                .put("adapter", "huawei-gt7-importer")
                .put("format", format))
            .put("activity", activity)
            .put("gpsPoints", gps)
            .put("heartRateSamples", heart)
            .put("samples", all);
    }

    static String sportFromFit(String fit) {
        if (fit == null) return "Entraînement";
        switch(fit.toUpperCase(Locale.ROOT)) {
            case "RUNNING": return "Course à pied";
            case "WALKING": return "Marche";
            case "HIKING": return "Randonnée";
            case "CYCLING": case "E_BIKING": return "Vélo";
            case "SWIMMING": return "Natation";
            case "ROWING": return "Rameur";
            case "FITNESS_EQUIPMENT": return "Fitness";
            case "HIIT": return "HIIT";
            case "TRAINING": return "Entraînement";
            case "TENNIS": return "Tennis";
            case "YOGA": return "Yoga";
            default: return fit.toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }
}

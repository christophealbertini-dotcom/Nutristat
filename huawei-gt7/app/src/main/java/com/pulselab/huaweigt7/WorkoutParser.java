package com.pulselab.huaweigt7;

import com.garmin.fit.Decode;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.RecordMesgListener;
import com.garmin.fit.SessionMesgListener;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.DateTime;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;

/** Parse FIT using official Garmin decoder; GPX/TCX through Android XMLPullParser. */
final class WorkoutParser {
    static ActivityData parse(byte[] bytes, String name) throws Exception {
        if (bytes == null || bytes.length < 12)
            throw new IllegalArgumentException("Fichier vide ou trop court.");
        // Content signature is authoritative; document-provider extensions are unreliable.
        if (bytes.length >= 14 && bytes[8] == '.' && bytes[9] == 'F'
                && bytes[10] == 'I' && bytes[11] == 'T')
            return parseFit(bytes);
        String prefix = new String(bytes, 0, Math.min(bytes.length, 1000),
                StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        if (prefix.startsWith("<") || prefix.startsWith("\uFEFF<")
                || prefix.startsWith("<?xml")) {
            String entire = new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (entire.contains("<!doctype") || entire.contains("<!entity"))
                throw new IllegalArgumentException("XML avec DTD/entité interdit pour la sécurité.");
            return parseXml(bytes);
        }
        throw new IllegalArgumentException("Format inconnu : sélectionner une activité FIT, GPX ou TCX.");
    }

    static ActivityData parseFit(byte[] bytes) throws Exception {
        ActivityData d = new ActivityData();
        d.format = "FIT";
        Decode decode = new Decode();
        MesgBroadcaster broadcaster = new MesgBroadcaster(decode);
        broadcaster.addListener((RecordMesgListener) record -> {
            DateTime time = record.getTimestamp();
            if (time == null) return;
            ActivityData.Sample point = new ActivityData.Sample();
            point.timeMs = time.getDate().getTime();
            Integer lat = record.getPositionLat(), lon = record.getPositionLong();
            if (lat != null && lon != null) {
                point.latitude = lat * (180.0 / 2147483648.0);
                point.longitude = lon * (180.0 / 2147483648.0);
            }
            Short heartRate = record.getHeartRate();
            if (heartRate != null && heartRate > 0) point.hr = heartRate.intValue();
            Float altitude = record.getEnhancedAltitude();
            if (altitude == null) altitude = record.getAltitude();
            if (altitude != null) point.altitude = altitude.doubleValue();
            Float speed = record.getEnhancedSpeed();
            if (speed == null) speed = record.getSpeed();
            if (speed != null && speed >= 0) point.speed = speed.doubleValue();
            Float distance = record.getDistance();
            if (distance != null && distance >= 0) point.distance = distance.doubleValue();
            Short cadence = record.getCadence();
            if (cadence != null && cadence >= 0) point.cadence = cadence.doubleValue();
            Integer power = record.getPower();
            if (power != null && power >= 0) point.power = power.doubleValue();
            d.samples.add(point);
        });
        broadcaster.addListener((SessionMesgListener) session -> {
            if (session.getStartTime() != null)
                d.startTimeMs = session.getStartTime().getDate().getTime();
            if (session.getTimestamp() != null)
                d.endTimeMs = session.getTimestamp().getDate().getTime();
            if (session.getSport() != null) {
                d.sportName = ActivityData.sportFromFit(session.getSport().name());
                d.sportType = session.getSport().getValue();
            }
            Float seconds = session.getTotalTimerTime();
            if (seconds == null || seconds <= 0) seconds = session.getTotalElapsedTime();
            if (seconds != null && seconds > 0) d.durationSeconds = seconds;
            Number distance = session.getTotalDistance();
            if (distance != null && distance.doubleValue() > 0)
                d.distanceMeters = distance.doubleValue();
            Number calories = session.getTotalCalories();
            if (calories != null && calories.doubleValue() > 0)
                d.caloriesKcal = calories.doubleValue();
            Number average = session.getAvgHeartRate();
            Number minimum = session.getMinHeartRate();
            Number maximum = session.getMaxHeartRate();
            if (average != null) d.avgHr = average.intValue();
            if (minimum != null) d.minHr = minimum.intValue();
            if (maximum != null) d.maxHr = maximum.intValue();
        });
        decode.read(new ByteArrayInputStream(bytes), broadcaster, broadcaster);
        d.normalize();
        if (d.samples.isEmpty() && d.durationSeconds == 0)
            throw new IllegalArgumentException("Aucun enregistrement sportif dans le FIT.");
        return d;
    }

    private static ActivityData parseXml(byte[] bytes) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser x = f.newPullParser();
        try { x.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false); }
        catch (Exception ignored) { }
        x.setInput(new ByteArrayInputStream(bytes), null);
        int event = x.getEventType();
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT)
            event = x.next();
        if (event != XmlPullParser.START_TAG)
            throw new IllegalArgumentException("Aucune racine XML.");
        String root = x.getName();
        if ("gpx".equalsIgnoreCase(root)) return parseGpx(x);
        if ("TrainingCenterDatabase".equalsIgnoreCase(root)) return parseTcx(x);
        throw new IllegalArgumentException("XML non pris en charge (attendu GPX ou TCX).");
    }

    private static ActivityData parseGpx(XmlPullParser x) throws Exception {
        ActivityData d = new ActivityData();
        d.format = "GPX";
        ActivityData.Sample point = null;
        while (x.next() != XmlPullParser.END_DOCUMENT) {
            int e = x.getEventType();
            String tag = x.getName();
            if (e == XmlPullParser.START_TAG) {
                if ("trkpt".equals(tag) || "rtept".equals(tag)) {
                    point = new ActivityData.Sample();
                    point.latitude = parseNumber(x.getAttributeValue(null, "lat"));
                    point.longitude = parseNumber(x.getAttributeValue(null, "lon"));
                } else if (point != null) {
                    if ("time".equals(tag)) point.timeMs = parseTime(x.nextText());
                    else if ("ele".equals(tag)) point.altitude = parseNumber(x.nextText());
                    else if ("hr".equalsIgnoreCase(tag) || "heartrate".equalsIgnoreCase(tag))
                        point.hr = parseInteger(x.nextText());
                    else if ("cad".equalsIgnoreCase(tag) || "cadence".equalsIgnoreCase(tag))
                        point.cadence = parseNumber(x.nextText());
                    else if ("speed".equalsIgnoreCase(tag)) point.speed = parseNumber(x.nextText());
                } else if ("name".equals(tag) && "Entraînement".equals(d.sportName)) {
                    d.sportName = x.nextText();
                }
            } else if (e == XmlPullParser.END_TAG && ("trkpt".equals(tag) || "rtept".equals(tag))) {
                if (point != null && point.timeMs > 0) d.samples.add(point);
                point = null;
            }
        }
        d.normalize();
        if (d.samples.isEmpty())
            throw new IllegalArgumentException("Parcours GPX sans points horodatés. Aucun JSON vide créé.");
        return d;
    }

    private static ActivityData parseTcx(XmlPullParser x) throws Exception {
        ActivityData d = new ActivityData();
        d.format = "TCX";
        ActivityData.Sample point = null;
        boolean inLap = false, inHr = false;
        while (x.next() != XmlPullParser.END_DOCUMENT) {
            int e = x.getEventType();
            String tag = x.getName();
            if (e == XmlPullParser.START_TAG) {
                switch(tag) {
                    case "Activity":
                        String sport = x.getAttributeValue(null, "Sport");
                        if (sport != null) d.sportName = ActivityData.sportFromFit(sport);
                        break;
                    case "Lap":
                        inLap = true;
                        long started = parseTime(x.getAttributeValue(null, "StartTime"));
                        if (started > 0 && (d.startTimeMs == 0 || started < d.startTimeMs))
                            d.startTimeMs = started;
                        break;
                    case "Trackpoint":
                        point = new ActivityData.Sample();
                        break;
                    case "HeartRateBpm":
                        inHr = true;
                        break;
                    case "Time":
                        if (point != null) point.timeMs = parseTime(x.nextText());
                        break;
                    case "LatitudeDegrees":
                        if (point != null) point.latitude = parseNumber(x.nextText());
                        break;
                    case "LongitudeDegrees":
                        if (point != null) point.longitude = parseNumber(x.nextText());
                        break;
                    case "AltitudeMeters":
                        if (point != null) point.altitude = parseNumber(x.nextText());
                        break;
                    case "DistanceMeters":
                        Double dist = parseNumber(x.nextText());
                        if (point != null) point.distance = dist;
                        else if (inLap && dist != null) d.distanceMeters += dist;
                        break;
                    case "Value":
                        if (inHr && point != null) point.hr = parseInteger(x.nextText());
                        break;
                    case "Cadence":
                    case "RunCadence":
                        if (point != null) point.cadence = parseNumber(x.nextText());
                        break;
                    case "Speed":
                        if (point != null) point.speed = parseNumber(x.nextText());
                        break;
                    case "TotalTimeSeconds":
                        if (inLap) {
                            Double v = parseNumber(x.nextText());
                            if (v != null && v > 0) d.durationSeconds += v;
                        }
                        break;
                    case "Calories":
                        if (inLap) {
                            Double v = parseNumber(x.nextText());
                            if (v != null && v > 0) d.caloriesKcal += v;
                        }
                        break;
                    default: break;
                }
            } else if (e == XmlPullParser.END_TAG) {
                if ("Trackpoint".equals(tag)) {
                    if (point != null && point.timeMs > 0) d.samples.add(point);
                    point = null;
                } else if ("HeartRateBpm".equals(tag)) inHr = false;
                else if ("Lap".equals(tag)) inLap = false;
            }
        }
        d.normalize();
        if (d.samples.isEmpty() && d.durationSeconds <= 0)
            throw new IllegalArgumentException("TCX sans points ni durée de séance.");
        return d;
    }

    private static Double parseNumber(String s) {
        if (s == null) return null;
        try {
            double d = Double.parseDouble(s.trim());
            return Double.isFinite(d) ? d : null;
        } catch (Exception ignored) { return null; }
    }

    private static Integer parseInteger(String s) {
        Double d = parseNumber(s);
        return d == null ? null : (int)Math.round(d);
    }

    private static long parseTime(String s) {
        if (s == null) return 0;
        try { return Instant.parse(s.trim()).toEpochMilli(); }
        catch (Exception ignored) { }
        try { return OffsetDateTime.parse(s.trim()).toInstant().toEpochMilli(); }
        catch (Exception ignored) { return 0; }
    }
}

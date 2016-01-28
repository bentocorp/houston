package org.bentocorp.houston.util;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.TimeZone;

public class TimeUtils {

    // 2016-01-28 13:30:25.500 -08:00
    public static SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z");

    // Convert LocalDate + LocalTime + TimeZone into millis (nanoseconds are truncated)
    public static long getMillis(LocalDate D, LocalTime T, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.set(
                D.getYear(),
                D.getMonth().ordinal(), // The month must be 0-based so use ordinal()
                D.getDayOfMonth(),
                T.getHour(),
                T.getMinute(),
                T.getSecond()
        );
        calendar.set(Calendar.MILLISECOND, T.getNano()/1000);
        String str = SIMPLE_DATE_FORMAT.format(calendar.getTime());
        System.out.println(String.format("TimeUtils.getMillis(%s, %s, %s) = %s", D, T, zone, str));
        return calendar.getTimeInMillis();
    }

    public static LocalDate getLocalDate(long millis, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(millis);
        return LocalDate.of(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1, // java.util.Calendar uses 0-based months
                calendar.get(Calendar.DAY_OF_MONTH)
        );
    }

    public static LocalTime getLocalTime(long millis, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(millis);
        return LocalTime.of(
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND),
                calendar.get(Calendar.MILLISECOND) * 1000 // nanosecond
        );
    }

    public static long epoch(String src, String formatStr, String zoneStr) throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(formatStr);
        if (zoneStr != null) {
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone(zoneStr));
        }
        return simpleDateFormat.parse(src).getTime();
    }

    public static Timestamp parseTimestamp(String src, String formatStr, String zoneStr) throws ParseException {
        return new Timestamp(epoch(src, formatStr, zoneStr));
    }
}

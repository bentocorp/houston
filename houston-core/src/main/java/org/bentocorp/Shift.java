package org.bentocorp;

import com.fasterxml.jackson.annotation.JsonValue;
import org.bentocorp.houston.util.TimeUtils;

import java.time.LocalTime;
import java.util.TimeZone;

public enum Shift {

    // Hours & minutes in PST (-08:00)
    LUNCH (LocalTime.of(10, 0), LocalTime.of(14, 0)),
    DINNER(LocalTime.of(17, 0), LocalTime.of(22, 0));

    // Shift.Type mainly used to describe what type of work a driver is scheduled for
    public enum Type {

        OFF_SHIFT, ON_DEMAND, ORDER_AHEAD;

        @JsonValue
        public int getOrdinal() {
            return this.ordinal();
        }
    }

    public LocalTime start;
    public LocalTime end;

    Shift(LocalTime start, LocalTime end) {
        this.start = start;
        this.end = end;
    }

    public static Shift parse(long millis) {
        // millis is Unix time (UTC) so extract hour & minute in PST (default)
        return Shift.parse(millis, TimeZone.getTimeZone("PST"));
    }

    public static Shift parse(long millis, TimeZone zone) {
        LocalTime T = TimeUtils.getLocalTime(millis, zone);
        Shift[] values = Shift.values();
        for (Shift shift: values) {
            // Note that both start and end are inclusive
            if ((T.equals(shift.start) || T.isAfter(shift.start)) && (T.equals(shift.end) || T.isBefore(shift.end))) {
                return shift;
            }
        }
        throw new RuntimeException("Shift.parse(" + millis + ", " + zone + ") - No matching enum");
    }

    // This method requires that there be at least one enum
    public static Shift getShiftEqualToOrGreaterThan(long millis) {
        LocalTime T = TimeUtils.getLocalTime(millis, TimeZone.getTimeZone("PST"));
        Shift[] values = Shift.values();
        int i, j;
        for (j = values.length - 1, i = j - 1; i >= 0; i--, j--) {
            LocalTime se = values[i].end; // Shift end
            if (/*se.equals(T) || */se.isBefore(T)) {
                break;
            }
        }
        return values[j];
    }

    // Tell Jackson to use this method when serializing
    @JsonValue
    public int getOrdinal() {
        return this.ordinal();
    }
}

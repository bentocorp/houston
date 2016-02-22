package org.bentocorp.houston.dispatch.routific;

import java.time.format.DateTimeFormatter;

public class Routific {

    public static final String LONG_VRP_URL = "https://api.routific.com/v1/vrp-long";

    // H - Hour of day (0-23)
    // m - Minute of hour (0-59)
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static final Location LOCATION_KITCHEN = new Location(37.762368F, -122.389061F, "Kitchen");

}

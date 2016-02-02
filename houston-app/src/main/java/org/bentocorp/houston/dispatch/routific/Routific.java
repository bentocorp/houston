package org.bentocorp.houston.dispatch.routific;

import java.time.format.DateTimeFormatter;

public class Routific {

    public static final String LONG_VRP_URL = "https://api.routific.com/v1/vrp-long";

    // Test account - marc@bentonow.com (max fleet size=19)
//    public static final String TEST_TOKEN = "bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJfaWQiOiI1NjkzZTc4ZjdlZjFiMTc5NmI1NWQ2MzEiLCJpYXQiOjE0NTI1MzM3ODJ9.9xvLj3232zCE_1pcoqeB0K5RhdnLDqnpq8XwRR8CKRk";

    public static final String TEST_TOKEN = "bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJfaWQiOiI1NmI4ZDM2MjlmMjIxZjA4NjM0NDJkODMiLCJpYXQiOjE0NTQ5NTMzMTR9.GnoYQ4BQiBr-xuPeHJIeSoJrUve7bRzwbXD3dGYJZKg";

    // H - Hour of day (0-23)
    // m - Minute of hour (0-59)
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static final Location LOCATION_KITCHEN = new Location(37.762368F, -122.389061F, "Kitchen");

}

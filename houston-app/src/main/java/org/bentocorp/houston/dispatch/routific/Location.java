package org.bentocorp.houston.dispatch.routific;

import com.fasterxml.jackson.annotation.JsonInclude;

// Tell Jackson not to serialize null values
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Location {

    public String name;

    public Float lat = null; // Since 0 and negatives are allowed, use null to indicate that
                             // this field has not been set

    public Float lng = null;

    public Location(Float lat, Float lng, String name) {
        this.lat = lat;
        this.lng = lng;
        this.name = name;
    }
}

package org.bentocorp.mapbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class WayPoint {
    public float lng;
    public float lat;
    public WayPoint(float lng, float lat) {
        this.lng = lng;
        this.lat = lat;
    }
    @Override
    public String toString() {
        return lng + "," + lat;
    }
}

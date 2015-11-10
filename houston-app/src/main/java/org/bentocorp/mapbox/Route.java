package org.bentocorp.mapbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Route {
    public int distance;
    public int duration;
    public String summary;
}

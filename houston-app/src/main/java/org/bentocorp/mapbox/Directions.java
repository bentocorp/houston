package org.bentocorp.mapbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Directions {
    public Route[] routes;
}

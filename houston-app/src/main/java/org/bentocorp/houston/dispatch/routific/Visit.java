package org.bentocorp.houston.dispatch.routific;

import com.fasterxml.jackson.annotation.*;
import org.bentocorp.Bento;
import org.bentocorp.Order;

import java.time.LocalTime;
import java.util.Calendar;
import java.util.TimeZone;

// This is a class-level annotation that tells Jackson to ignore any unrecognized properties in
// the JSON. Any class fields not found in the JSON, however, are left at their default values.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Visit {

    public Visit() {
        // Empty constructor so Jackson can deserialize
    }

  // Helper constructor to instantiate Visit from Order<Bento>
  public Visit(Order<Bento> order) {
    this.location = new Location(
        order.address.lat, order.address.lng, order.address.formatForMapbox()
    );

    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(order.scheduledTimeZone));

    calendar.setTimeInMillis(order.scheduledWindowStart);
    this.start = LocalTime.of(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));

    calendar.setTimeInMillis(order.scheduledWindowEnd);
    this.end = LocalTime.of(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));

    // Set default duration (of delivery) to 10 minutes
    this.durationInMinutes = 8;
  }

  /* The following fields are required for routing
   * "5678" -> {
   *   "location": {
   *     "name": "2017 Mission St, San Francisco, California // Address serialized in Mapbox format
   *     "lat":
   *     "lng":
   *   },
   *   "start":
   *   "end":
   *   "duration":
   * }
   */

  public Location location;

  @JsonIgnore
  public LocalTime start;

  @JsonGetter("start")
  public String getStartLocalTimeStr() {
    return (start != null) ? start.format(Routific.DATE_TIME_FORMATTER) : null;
  }

  @JsonIgnore
  public LocalTime end;

  @JsonGetter("end")
  public String getEndLocalTimeStr() {
    return (end != null) ? end.format(Routific.DATE_TIME_FORMATTER) : null;
  }

  // Use Integer instead of int so we can avoid having it unnecessarily serialized by initializing it with null
  @JsonProperty(value = "duration")
  public Integer durationInMinutes = null; // Estimated time to assemble and deliver order

  /* Served visit response */

  @JsonProperty("location_id")
  public String id; // This will be the key of Input#visits
                    // Since the API doesn't accept hyphenated keys, we've done "o-5678" -> "5678"

  @JsonProperty("location_name")
  public String name;

  @JsonIgnore
  public LocalTime arrivalTime;

  @JsonSetter("arrival_time")
  public void setArrivalTime(String str) {
    this.arrivalTime = LocalTime.parse(str, Routific.DATE_TIME_FORMATTER);
  }

  @JsonGetter("arrival_time")
  public String getArrivalTime() {
    return (arrivalTime != null) ? arrivalTime.format(Routific.DATE_TIME_FORMATTER) : null;
  }

  @JsonIgnore
  public LocalTime finishTime;

  @JsonSetter("finish_time")
  public void setFinishTime(String str) {
    this.finishTime = LocalTime.parse(str, Routific.DATE_TIME_FORMATTER);
  }

  @JsonGetter("finish_time")
  public String getFinishTime() {
    return (finishTime != null) ? finishTime.format(Routific.DATE_TIME_FORMATTER) : null;
  }
}

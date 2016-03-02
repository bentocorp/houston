package org.bentocorp.houston.dispatch.routific;

import com.fasterxml.jackson.annotation.*;
import org.bentocorp.Shift;
import org.bentocorp.dispatch.Driver.Speed;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Driver {

  // Only property required for routing
  @JsonProperty("start_location")
  public Location startLocation = Routific.LOCATION_KITCHEN;

  @JsonIgnore
  public Shift shift;

  @JsonGetter("shift_start")
  public String getShiftStart() {
    return (shift != null) ? shift.start.format(Routific.DATE_TIME_FORMATTER) : null;
  }

  @JsonGetter("shift_end")
  public String getShiftEnd() {
    return (shift != null) ? shift.end.format(Routific.DATE_TIME_FORMATTER) : null;
  }

  // Routific returns speed as a number
  // Default to normal speed
  public float speed = Speed.FASTER.value;

  public Driver() {
    // Empty constructor to allow Jackson to deserialize
  }

  // Use this constructor when we just want to find a rough approximation on minimum
  // number of drivers (or don't have individual driver information available)
  public Driver(Shift shift) {
    this.shift = shift;
  }

  public Driver(Shift shift, org.bentocorp.dispatch.Driver driver) {
    this.shift = shift;
    this.speed = driver.speed;
  }

}

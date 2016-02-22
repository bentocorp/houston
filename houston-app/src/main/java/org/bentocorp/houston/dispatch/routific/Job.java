package org.bentocorp.houston.dispatch.routific;

import com.fasterxml.jackson.annotation.*;
import org.bentocorp.Shift;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Job {

  public enum Status {

    PENDING, PROCESSING, FINISHED, ERROR;

    public static Status parse(String str) {
      for (Status s: Status.values()) {
        if (s.toString().equals(str)) {
          return s;
        }
      }
      throw new RuntimeException("\"" + str + "\" cannot be parsed into Job.Status");
    }

    @JsonValue
    @Override
    public String toString() {
      // name() is a final method and so is guaranteed not to be overridden
      return this.name().toLowerCase();
    }
  }

  // The JSON key for this field (returned by Routific) depends on the endpoint
  // See setId() and setJobId()
  @JsonProperty("jobId")
  public String jobId = null;

  // GET -> jobs/[someID]
  @JsonSetter("id")
  public void setId(String id) {
    this.jobId = id;
  }

  // POST -> v1/vrp-long
  @JsonSetter("job_id")
  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public Status status;

  @JsonSetter("status")
  public void setStatus(String statusStr) {
    this.status = Status.parse(statusStr);
  }

  public Input input;

  public Output output;

  /* Non-Routific properties */

  public String date;

  // Shift object in memory, but serializes to an integer in JSON
  public Shift shift;

  // For human readability in Atlas
  @JsonProperty("shift_string")
  public String getShiftString() {
    return (shift != null) ? shift.name().toUpperCase() : null;
  }

  @JsonSetter("shift")
  public void setShift(int shiftOrdinal) {
    this.shift = Shift.values()[shiftOrdinal];
  }

  public String time;

  public Integer minVehicles;

  /* Template engine for Freemarker requires getters (even for public fields) */

  // Must annotate this method with @JsonIgnore, otherwise will conflict with @JsonProperty("jobId") above
  @JsonIgnore
  public String getJobId()  { return jobId;  }

  public String getDate()   { return date;   }

  public Output getOutput() { return output; }
}

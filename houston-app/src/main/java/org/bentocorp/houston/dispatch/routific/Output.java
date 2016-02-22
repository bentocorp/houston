package org.bentocorp.houston.dispatch.routific;

import com.fasterxml.jackson.annotation.*;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Output {

  public enum Status {

      SUCCESS;

      public static Status parse(String str) {
          for (Status status: Status.values()) {
              if (status.name().toLowerCase().equals(str)) {
                  return status;
              }
          }
          throw new RuntimeException(str + " can't be parsed into Output.Status");
      }

      @JsonValue
      public String toStringLowerCase() {
          return this.name().toLowerCase();
      }
  }

    public Status status;

    @JsonSetter
    public void setStatus(String statusStr) {
        this.status = Status.parse(statusStr);
    }

    @JsonProperty("num_unserved")
    public int numUnservedVisits = 0;

    // visit -> reason why can't be served
    public Map<String, String> unserved = null;

    public Map<String, List<Visit>> solution = null;

    /* Template for Freemarker requires getters (even for public fields) */

    // This public getter is required to access field but must annotate with @JsonIgnore otherwise it
    // will conflict with @JsonProperty("num_unserved") above
    @JsonIgnore
    public int getNumUnservedVisits() { return numUnservedVisits; }

    public Map<String, String> getUnserved() { return unserved; }

}

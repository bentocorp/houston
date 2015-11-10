package org.bentocorp.mapbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapboxService {

  public final static Logger LOGGER = LoggerFactory.getLogger(MapboxService.class);

  private final static String ACCESS_TOKEN = "pk.eyJ1IjoiYmVudG9ub3ciLCJhIjoiNjI2ZmIwM2JkNzliMDZjYWEwYzkzOTc3YzdiYTQ2MmYifQ.LdRS3yQDx8_tWtEvY1bOjw";

  public static int getEta(WayPoint[] wayPoints) {
    try {
      if (wayPoints.length < 2) throw new Exception("There must be at least two (2) way points to get an ETA");
      String wayPointStr = wayPoints[0].toString();
      for (int i = 1; i < wayPoints.length; i++) {
        wayPointStr += ";" + wayPoints[i].toString();
      }
      String url = "https://api.mapbox.com/v4/directions/mapbox.driving/" + wayPointStr +
              ".json?alternatives=false&steps=false&geometry=false&access_token=" + ACCESS_TOKEN;
      HttpClient client = HttpClientBuilder.create().build();
      HttpGet httpGet = new HttpGet(url);
      HttpResponse res = client.execute(httpGet);
      int statusCode = res.getStatusLine().getStatusCode();
      if (statusCode != 200) {
        throw new Exception("HTTP GET request failed with status code " + statusCode);
      }
      String str = IOUtils.toString(res.getEntity().getContent());
      Directions directions = new ObjectMapper().readValue(str, Directions.class);
      Route[] routes = directions.routes;
      if (routes.length <= 0) {
        throw new Exception("Mapbox returned 0 routes!");
      }
      return Math.round(routes[0].duration/60f);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
      return -1;
    }
  }
}

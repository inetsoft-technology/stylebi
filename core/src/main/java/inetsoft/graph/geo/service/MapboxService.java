/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.geo.service;

import inetsoft.sree.SreeEnv;

import javax.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class MapboxService extends WebMapService {
   public MapboxService() {
      user = SreeEnv.getProperty("mapbox.user");
      token = SreeEnv.getProperty("mapbox.token");
      style = SreeEnv.getProperty("mapbox.style");
   }

   public MapboxService(boolean orgScoped) {
      user = SreeEnv.getProperty("mapbox.user", false, orgScoped);
      token = SreeEnv.getProperty("mapbox.token", false, orgScoped);
      style = SreeEnv.getProperty("mapbox.style", false, orgScoped);
   }

   public MapboxService(String user, String token, String style) {
      this.user = user;
      this.token = token;
      this.style = style;
   }

   /**
    * Load a static image using bbox.
    */
   @Override
   public Image loadImage(double lonMin, double lonMax, double latMin, double latMax,
                          int imgw, int imgh)
         throws IOException
   {
      // mapbox static image api:
      // https://api.mapbox.com
      // /styles/v1/{username}/{style_id}/static/{bbox}/{width}x{height}?access_token=...
      String urlstr = String.format("https://api.mapbox.com" +
                                    "/styles/v1/%s/%s/static/[%f,%f,%f,%f]/%dx%d?access_token=%s",
                                 user, style, lonMin, latMin, lonMax, latMax, imgw, imgh, token);
      return WebMapService.loadFromURL(urlstr);
   }

   /**
    * Get a list of all map styles for the user.
    */
   public List<MapboxStyle> getStyles(boolean withDefault) throws IOException {
      List<MapboxStyle> styles = new ArrayList<>();
      getStyles("https://api.mapbox.com/styles/v1/" + user + "?access_token=" + token, styles);

      if(withDefault) {
         styles.add(0, MapboxStyle.DEFAULT);
      }

      return styles;
   }

   private void getStyles(String urlstr, List<MapboxStyle> styles) throws IOException {
      URL url = new URL(urlstr);
      URLConnection conn = url.openConnection();

      try {
         String next = conn.getHeaderField("next");
         JsonReader parser = Json.createReader(conn.getInputStream());
         JsonArray arr = (JsonArray) parser.read();

         arr.stream().map(v -> new MapboxStyle(v)).forEach(v -> styles.add(v));

         if(next != null) {
            getStyles(next, styles);
         }
      }
      catch(IOException ex) {
         if(conn instanceof HttpsURLConnection) {
            if(((HttpsURLConnection) conn).getResponseCode() == 403) {
               throw new RuntimeException("Access token requires permission to STYLES:LIST secret scope");
            }
         }

         throw ex;
      }
   }

   /**
    * Get the tile size. One tile is required to show world at lowest zoom level.
    */
   @Override
   public int getTileSize() {
      return 512;
   }

   @Override
   public int getMaxSize() {
      return 1280;
   }

   @Override
   public boolean isDiscreteZoom() {
      return false;
   }

   private String user;
   private String token;
   private String style;
}

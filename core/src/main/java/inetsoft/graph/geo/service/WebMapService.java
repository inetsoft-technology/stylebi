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

import inetsoft.graph.coord.GeoCoord;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.graph.MapInfo;
import inetsoft.util.MessageException;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;

/**
 * This is the interface for web map services.
 */
public abstract class WebMapService implements Serializable {
   static BufferedImage loadFromURL(String urlstr) throws IOException {
      URLConnection conn = null;

      try {
         URL url = new URL(urlstr);
         conn = url.openConnection();
         return ImageIO.read(conn.getInputStream());
      }
      catch(IOException ex) {
         if(conn instanceof HttpsURLConnection) {
            int code = ((HttpsURLConnection) conn).getResponseCode();

            if(code == 402) {
               throw new WebMapLimitException();
            }
            else if(code == 403) {
               throw new MessageException("Web map access key is invalid: " + ex.getMessage());
            }
         }

         throw ex;
      }
   }

   /**
    * Get the current web map service.
    * @param mapboxStyle mapbox style or null to use the default.
    */
   public static WebMapService getWebMapService(String mapboxStyle) {
      String service = SreeEnv.getProperty("webmap.service");

      if(MapInfo.MAPBOX.equals(service)) {
         String mapboxUser = SreeEnv.getProperty("mapbox.user");
         String mapboxToken = SreeEnv.getProperty("mapbox.token");

         if(mapboxStyle == null) {
            mapboxStyle = SreeEnv.getProperty("mapbox.style");
         }

         return new MapboxService(mapboxUser, mapboxToken, mapboxStyle);
      }
      else if(MapInfo.GOOGLE.equals(service)) {
         return new GoogleMapsService();
      }

      return null;
   }

    /**
    * Load a static image using bbox.
    */
   public abstract Image loadImage(double lonMin, double lonMax, double latMin, double latMax,
                   int imgw, int imgh)
      throws IOException;

   /**
    * Get the tile size. One tile is required to show world at lowest zoom level.
    */
   public abstract int getTileSize();

   /**
    * Get the maximum size for the image.
    */
   public abstract int getMaxSize();

   /**
    * Check if this web map service only support discrete zoom levels or arbitrary zoom.
    */
   public abstract boolean isDiscreteZoom();

   /**
    * Get the maximum zoom level for discrete zoom.
    */
   public int getMaxZoomLevel() {
      return 22;
   }

   /**
    * Get the zoom level for the map. This may be explicitly set through setZoomLevel()
    * or calculated by prepareCoordinate().
    */
   public int getZoomLevel() {
      return zoomLevel;
   }

   /**
    * Set the zoom level for the map. This will be used for the map if this map service
    * requires discrete zoom level.
    */
   public void setZoomLevel(int zoomLevel) {
      this.zoomLevel = zoomLevel;
   }

   /**
    * Check and adjust latitude/longitude range if necessary.
    */
   public void prepareCoordinate(GeoCoord coord, int width, int height) {
      // no op
   }

   private int zoomLevel = -1;
}

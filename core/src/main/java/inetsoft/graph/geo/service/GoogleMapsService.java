/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.graph.geo.service;

import inetsoft.graph.coord.GeoCoord;
import inetsoft.graph.geo.GeoProjection;
import inetsoft.graph.scale.LinearScale;
import inetsoft.sree.SreeEnv;

import java.awt.*;
import java.io.IOException;
import java.util.stream.IntStream;

public class GoogleMapsService extends WebMapService {
   public GoogleMapsService() {
      token = SreeEnv.getProperty("google.maps.key");
   }

   public GoogleMapsService(String token) {
      this.token = token;
   }

   @Override
   public void prepareCoordinate(GeoCoord coord, int width, int height) {
      GeoProjection proj = coord.getProjection();
      LinearScale xscale = coord.getXScale();
      LinearScale yscale = coord.getYScale();
      double xmin = xscale.getMin();
      double xmax = xscale.getMax();
      double ymin = yscale.getMin();
      double ymax = yscale.getMax();
      xmin = proj.inverseX(xmin);
      xmax = proj.inverseX(xmax);
      ymin = proj.inverseY(ymin);
      ymax = proj.inverseY(ymax);

      this.centerLat = (ymin + ymax) / 2;
      this.centerLon = (xmin + xmax) / 2;
      int zoomLevel = getZoomLevel();

      if(zoomLevel < 0) {
         zoomLevel = getZoomToFit(ymin, ymax, xmin, xmax, width, height);
         setZoomLevel(zoomLevel);
      }

      MapBounds bounds = null;

      while(zoomLevel < getMaxZoomLevel()) {
         bounds = getStaticMapBounds(ymin, ymax, xmin, xmax, width, height, zoomLevel);

         if(bounds.getMaxLongitude() - bounds.getMinLongitude() <= 360 &&
            bounds.getMaxLatitude() - bounds.getMinLatitude() <= 180 &&
            bounds.getMaxLatitude() <= 90 && bounds.getMaxLatitude() >= -90)
         {
            break;
         }

         zoomLevel++;
         setZoomLevel(zoomLevel);
      }

      xscale.setMin(proj.projectX(bounds.getMinLongitude()));
      xscale.setMax(proj.projectX(bounds.getMaxLongitude()));
      yscale.setMin(proj.projectY(bounds.getMinLatitude()));
      yscale.setMax(proj.projectY(bounds.getMaxLatitude()));
   }

   /**
    * Load a static image using bbox.
    */
   @Override
   public Image loadImage(double lonMin, double lonMax, double latMin, double latMax,
                          int imgw, int imgh)
         throws IOException
   {
      // googleMaps static image api:
      // https://maps.googleapis.com
      // /maps/api/staticmap?center=lon,lat&zoom=2&size=600x300?key=key
      String url = String.format("https://maps.googleapis.com" +
                                    "/maps/api/staticmap?center=%f,%f&zoom=%d&size=%dx%d&key=%s",
                                 centerLat, centerLon, getZoomLevel(), imgw, imgh, token);
      return WebMapService.loadFromURL(url);
   }

   @Override
   public int getTileSize() {
      return 256;
   }

   @Override
   public int getMaxSize() {
      if(maxSize == 0) {
         maxSize = Integer.parseInt(SreeEnv.getProperty("googlemaps.max.size", "640"));
      }

      return maxSize;
   }

   @Override
   public boolean isDiscreteZoom() {
      return true;
   }

   /**
    * Returns the latitude/longitude bounds of the map image to use to set the chart bounds.
    *
    * @param minLatitude  the minimum latitude in the data.
    * @param maxLatitude  the maximum latitude in the data.
    * @param minLongitude the minimum longitude in the data.
    * @param maxLongitude the maximum longitude in the data.
    * @param widthPx      the width of the image.
    * @param heightPx     the height of the image.
    * @param zoom         the zoom of the image
    *
    * @return the latitude/longitude bounds of the image.
    */
   public static MapBounds getStaticMapBounds(double minLatitude, double maxLatitude,
                                               double minLongitude, double maxLongitude,
                                               int widthPx, int heightPx, int zoom)
   {
      final long scale = 1 << zoom;
      final LatitudeLongitude centerLatLng =
         new LatitudeLongitude((maxLatitude + minLatitude) / 2, (maxLongitude + minLongitude) / 2);
      final Point.Double centerPx = MercatorProjection.fromLatLngToPoint(centerLatLng);

      final Point.Double SWPoint = new Point.Double(
         centerPx.getX() - (widthPx / 2.0) / scale, centerPx.getY() + (heightPx / 2.0) / scale);
      final Point.Double NEPoint = new Point.Double(
         centerPx.getX() + (widthPx / 2.0) / scale, centerPx.getY() - (heightPx / 2.0) / scale);

      final LatitudeLongitude SWLatLng = MercatorProjection.fromPointToLatLng(SWPoint);
      final LatitudeLongitude NELatLng = MercatorProjection.fromPointToLatLng(NEPoint);

      final double minLatBound = Math.min(SWLatLng.getLatitude(), NELatLng.getLatitude());
      final double maxLatBound = Math.max(SWLatLng.getLatitude(), NELatLng.getLatitude());
      final double minLngBound = Math.min(SWLatLng.getLongitude(), NELatLng.getLongitude());
      final double maxLngBound = Math.max(SWLatLng.getLongitude(), NELatLng.getLongitude());

      return new MapBounds(minLatBound, maxLatBound, minLngBound, maxLngBound);
   }

   public static int getZoomToFit(double minLatitude, double maxLatitude,
                                  double minLongitude, double maxLongitude,
                                  int maxWidthPx, int maxHeightPx)
   {
      return getZoomToFit(minLatitude, maxLatitude, minLongitude, maxLongitude,
                          maxWidthPx, maxHeightPx, MAX_ZOOM);
   }

   private static int getZoomToFit(double minLatitude, double maxLatitude,
                                   double minLongitude, double maxLongitude,
                                   int maxWidthPx, int maxHeightPx, int maxZoom)
   {
      final int latZoom;
      final int lngZoom;

      if(minLatitude == maxLatitude) {
         latZoom = maxZoom;
      }
      else {
         double latFraction =
            (latitudeToRadians(maxLatitude) - latitudeToRadians(minLatitude)) / Math.PI;
         latZoom = zoom(maxHeightPx, GOOGLE_WORLD_HEIGHT, latFraction);
      }

      if(minLongitude == maxLongitude) {
         lngZoom = maxZoom;
      }
      else {
         double lngRange = maxLongitude - minLongitude;

         if(lngRange < 0) {
            lngRange += 360;
         }

         double lngFraction = lngRange / 360;
         lngZoom = zoom(maxWidthPx, GOOGLE_WORLD_WIDTH, lngFraction);
      }

      return IntStream.of(latZoom, lngZoom, MAX_ZOOM).min().getAsInt();
   }

   private static double latitudeToRadians(double latitude) {
      double sinLatitude = Math.sin(Math.toRadians(latitude));
      double radx2 = Math.log((1 + sinLatitude) / (1 - sinLatitude)) / 2.0;
      return Math.max(Math.min(radx2, Math.PI), -Math.PI) / 2.0;
   }

   private static int zoom(int mapPx, int worldPx, double fraction) {
      return (int) Math.floor(Math.log((double) mapPx / (double) worldPx / fraction) /
                                 Math.log(2.0));
   }

   private static class LatitudeLongitude {
      private LatitudeLongitude(double latitude, double longitude) {
         this.latLng = new Point.Double(longitude, latitude);
      }

      public double getLatitude() {
         return latLng.getY();
      }

      public double getLongitude() {
         return latLng.getX();
      }

      @Override
      public String toString() {
         return super.toString() + "[" + getLatitude() + "," + getLongitude() + "]";
      }

      private final Point.Double latLng;
   }

   public static class MapBounds {
      private MapBounds(double minLatitude, double maxLatitude,
                        double minLongitude, double maxLongitude)
      {
         this.minLatitude = minLatitude;
         this.maxLatitude = maxLatitude;
         this.minLongitude = minLongitude;
         this.maxLongitude = maxLongitude;
      }

      /**
       * @return the minimum latitude bound.
       */
      public double getMinLatitude() {
         return minLatitude;
      }

      /**
       * @return the maximum latitude bound.
       */
      public double getMaxLatitude() {
         return maxLatitude;
      }

      /**
       * @return the minimum longitude bound.
       */
      public double getMinLongitude() {
         return minLongitude;
      }

      /**
       * @return the maximum longitude bound.
       */
      public double getMaxLongitude() {
         return maxLongitude;
      }

      @Override
      public String toString() {
         return super.toString() + "[" + minLatitude + "," + maxLatitude +
            "," + minLongitude + "," + maxLongitude + "]";
      }

      private final double minLatitude;
      private final double maxLatitude;
      private final double minLongitude;
      private final double maxLongitude;
   }

   /**
    * Class for finding bounds of a static Google image. Primarily adapted from
    * <a href="https://stackoverflow.com/questions/12507274/how-to-get-bounds-of-a-google-static-map">this SO post.</a>
    */
   private static class MercatorProjection {
      static Point.Double fromLatLngToPoint(LatitudeLongitude latLng) {
         final Point.Double origin = pixelOrigin;
         final double x = origin.getX() + latLng.getLongitude() * pixelsPerLonDegree;

         // NOTE(appleton): Truncating to 0.9999 effectively limits latitude to
         // 89.189.  This is about a third of a tile past the edge of the world tile.
         final double siny =
            boundValue(Math.sin(Math.toRadians(latLng.getLatitude())), -0.9999, 0.9999);
         final double y =
            origin.getY() + 0.5 * Math.log((1 + siny) / (1 - siny)) * -pixelsPerLonRadian;

         return new Point.Double(x, y);
      }

      static LatitudeLongitude fromPointToLatLng(Point.Double point) {
         final Point.Double origin = pixelOrigin;
         final double lng = (point.getX() - origin.getX()) / pixelsPerLonDegree;
         final double latRadians = (point.getY() - origin.getY()) / -pixelsPerLonRadian;
         final double lat = Math.toDegrees(2 * Math.atan(Math.exp(latRadians)) - Math.PI / 2);
         return new LatitudeLongitude(lat, lng);
      }

      private static double boundValue(double value, double minValue, double maxValue) {
         value = Math.max(minValue, value);
         value = Math.min(maxValue, value);
         return value;
      }

      private static final int MERCATOR_RANGE = 256;
      private static final Point.Double pixelOrigin =
         new Point.Double(MERCATOR_RANGE / 2.0, MERCATOR_RANGE / 2.0);
      private static final double pixelsPerLonDegree = MERCATOR_RANGE / 360.0;
      private static final double pixelsPerLonRadian = MERCATOR_RANGE / (2 * Math.PI);
   }

   private static final int MAX_ZOOM = 17;
   /**
    * At zoom 0, the entire world map fits onto a single 256x256 tile.
    */
   private static final int GOOGLE_WORLD_WIDTH = 256;
   private static final int GOOGLE_WORLD_HEIGHT = 256;

   private String token;
   private int maxSize;
   private double centerLat, centerLon;
}

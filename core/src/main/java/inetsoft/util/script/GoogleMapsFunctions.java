/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.script;

import inetsoft.graph.*;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geo.service.GoogleMapsService;
import inetsoft.graph.scale.LinearScale;
import inetsoft.report.script.PropertyDescriptor;
import inetsoft.uql.script.XTableArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

public class GoogleMapsFunctions {
   /**
    * Setup the graph with a google maps background image and reasonable plot defaults.
    *
    * @param graph               the graph object.
    * @param urlPrefix           the google maps url prefix.
    * @param data                data object containing a table.
    * @param latitudeColumnName  the latitude column name in the data.
    * @param longitudeColumnName the longitude column name in the data.
    * @param maxWidthPx          the maximum width of the part of the image containing the data points.
    * @param maxHeightPx         the maximum height of the part of the image containing the data points.
    * @param widthPx             the actual width of the image.
    * @param heightPx            the actual height of the image.
    */
   public static void setupGoogleMapsPlot(
      Object graph, String urlPrefix, Object data,
      String latitudeColumnName, String longitudeColumnName,
      int maxWidthPx, int maxHeightPx, int widthPx, int heightPx)
   {
      if(!(graph instanceof EGraph)) {
         throw new IllegalArgumentException("Graph parameter is not an EGraph instance.");
      }

      final EGraph egraph = (EGraph) graph;
      final MapInfo mapInfo = getPlotInfo(data, latitudeColumnName, longitudeColumnName,
                                          maxWidthPx, maxHeightPx, widthPx, heightPx);

      final PlotSpec plotSpec = new PlotSpec();
      plotSpec.setLockAspect(true);

      final Image mapImage = JavaScriptEngine.getImage(urlPrefix + mapInfo.getRequestParams());
      plotSpec.setBackgroundImage(mapImage);

      final GoogleMapsService.MapBounds bounds = mapInfo.getBounds();
      plotSpec.setYMax(bounds.getMaxLatitude());
      plotSpec.setYMin(bounds.getMinLatitude());
      plotSpec.setXMax(bounds.getMaxLongitude());
      plotSpec.setXMin(bounds.getMinLongitude());

      final LinearScale latScale = new LinearScale(latitudeColumnName);
      final LinearScale lonScale = new LinearScale(longitudeColumnName);
      final AxisSpec axisSpec = new AxisSpec();
      axisSpec.setLabelVisible(false);
      latScale.setAxisSpec(axisSpec);
      lonScale.setAxisSpec(axisSpec);
      latScale.setScaleOption(0);
      lonScale.setScaleOption(0);
      latScale.setMax(plotSpec.getYMax());
      latScale.setMin(plotSpec.getYMin());
      lonScale.setMax(plotSpec.getXMax());
      lonScale.setMin(plotSpec.getXMin());

      final RectCoord coord = new RectCoord(lonScale, latScale);
      coord.setPlotSpec(plotSpec);

      egraph.setCoordinate(coord);
   }

   /**
    * @param data                data object containing a table.
    * @param latitudeColumnName  the latitude column name in the data.
    * @param longitudeColumnName the longitude column name in the data.
    * @param maxWidthPx          the maximum width of the image area containing the data points.
    * @param maxHeightPx         the maximum height of the image area containing the data points.
    * @param widthPx             the actual width of the image.
    * @param heightPx            the actual height of the image.
    *
    * @return the GoogleMapsInfo corresponding to these parameters.
    */
   private static MapInfo getPlotInfo(
      Object data, String latitudeColumnName, String longitudeColumnName,
      int maxWidthPx, int maxHeightPx, int widthPx, int heightPx)
   {
      double minLat = 90;
      double maxLat = -90;
      double minLng = 180;
      double maxLng = -180;

      if(data instanceof XTableArray) {
         data = ((XTableArray) data).unwrap();
      }

      final DataSet dataset = PropertyDescriptor.createDataSet(data);

      if(dataset != null) {
         final int latIdx = dataset.indexOfHeader(latitudeColumnName);
         final int lonIdx = dataset.indexOfHeader(longitudeColumnName);

         if(latIdx < 0) {
            throw new IndexOutOfBoundsException("Latitude column could not be found in the data.");
         }

         if(lonIdx < 0) {
            throw new IndexOutOfBoundsException("Longitude column could not be found in the data.");
         }

         for(int row = 0; row < dataset.getRowCount(); row++) {
            final double latitude = (double) dataset.getData(latIdx, row);
            final double longitude = (double) dataset.getData(lonIdx, row);

            minLat = Math.min(minLat, latitude);
            maxLat = Math.max(maxLat, latitude);
            minLng = Math.min(minLng, longitude);
            maxLng = Math.max(maxLng, longitude);
         }
      }
      else {
         throw new IllegalArgumentException("Data parameter type is not an acceptable type.");
      }

      return getMapInfo(minLat, maxLat, minLng, maxLng,
                        maxWidthPx, maxHeightPx, widthPx, heightPx);
   }

   private static MapInfo getMapInfo(
      double minLatitude, double maxLatitude, double minLongitude, double maxLongitude,
      int maxWidthPx, int maxHeightPx, int widthPx, int heightPx)
   {
      final int zoom = GoogleMapsService.getZoomToFit(minLatitude, maxLatitude, minLongitude, maxLongitude,
                                                      maxWidthPx, maxHeightPx);
      final GoogleMapsService.MapBounds bounds = GoogleMapsService.getStaticMapBounds(minLatitude, maxLatitude,
                                                                                      minLongitude, maxLongitude,
                                                                                      widthPx, heightPx, zoom);

      final double centerLat = (minLatitude + maxLatitude) / 2;
      final double centerLng = (minLongitude + maxLongitude) / 2;

      final String centerParam = String.format("&center=%f,%f", centerLat, centerLng);
      final String zoomParam = String.format("&zoom=%d", zoom);
      final String sizeParam = String.format("&size=%dx%d", widthPx, heightPx);
      final String requestParams = centerParam + zoomParam + sizeParam;

      return new MapInfo(requestParams, bounds);
   }

   private static class MapInfo {
      private MapInfo(String requestParams, GoogleMapsService.MapBounds bounds) {
         this.requestParams = requestParams;
         this.bounds = bounds;
      }

      /**
       * @return the request params to be appended to a google maps api call.
       */
      public String getRequestParams() {
         return requestParams;
      }

      /**
       * @return the longitude/latitude bounds of the map. These can be set for the plot and scale
       * to match the background google maps image.
       */
      public GoogleMapsService.MapBounds getBounds() {
         return bounds;
      }

      private final String requestParams;
      private final GoogleMapsService.MapBounds bounds;
   }

   private static final Logger LOG = LoggerFactory.getLogger(GoogleMapsFunctions.class);
}

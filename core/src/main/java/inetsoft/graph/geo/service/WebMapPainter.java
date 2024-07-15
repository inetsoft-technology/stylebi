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

import inetsoft.graph.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.GeoCoord;
import inetsoft.graph.geo.GeoProjection;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class WebMapPainter implements BackgroundPainter {
   public WebMapPainter() {
   }

   public WebMapPainter(WebMapService service) {
      this.service = service;
   }

   /**
    * Get the Web map service used by this painter.
    */
   public WebMapService getWebMapService() {
      return service;
   }

   @Override
   public void prepareCoordinate(GeoCoord coord, int width, int height) {
      service.prepareCoordinate(coord, width, height);
   }

   @Override
   public void paint(Graphics2D g, VGraph graph) throws IOException {
      Rectangle2D plot = graph.getPlotBounds();
      Coordinate coord = graph.getCoordinate();
      Image bg = getImage(graph);

      if(bg != null && coord instanceof GeoCoord) {
         double padding = getPadding((GeoCoord) coord);

         if(padding != 0) {
            BufferedImage img = (BufferedImage) bg;
            // fetch the sub image without the padding (which is used for panning).
            int subx = (int) (plot.getWidth() * padding);
            int suby = (int) (plot.getHeight() * padding);
            bg = img.getSubimage(subx, suby, (int) plot.getWidth(), (int) plot.getHeight());
         }
      }

      if(bg != null) {
         GraphTool.drawImage(g, bg, plot.getX(), plot.getY(), plot.getWidth(), plot.getHeight());
      }
   }

   /**
    * Get the image used for drawing background, including padding.
    */
   @Override
   public Image getImage(VGraph graph) throws IOException {
      Image bg = graph.getPlotBackground();

      if(bg == null && graph.getCoordinate() instanceof GeoCoord) {
         bg = loadImage(graph);
         graph.setPlotBackground(bg);
      }

      return bg;
   }

   private Image loadImage(VGraph graph) throws IOException {
      GeoCoord coord = (GeoCoord) graph.getCoordinate();
      GeoProjection proj = coord.getProjection();
      Rectangle2D bbox = coord.getBBox();
      Rectangle2D plot = coord.getVGraph().getPlotBounds();
      double plotw = plot.getWidth();
      double ploth = plot.getHeight();

      if(Double.isNaN(bbox.getWidth()) || Double.isNaN(bbox.getHeight())) {
         return null;
      }

      // adding padding to the image for panning.
      // mapbox uses bbox to calculate the zoom and center. if width/height is larger than
      // bbox, the image is expended on each size to fill in the space.
      // !!! lon/lat in web map is unprojected and x/y in coordinate (e.g. scale) are projected.

      double padding = getPadding(coord);
      int imgw = (int) (plotw * (1 + padding * 2));
      int imgh = (int) (ploth * (1 + padding * 2));
      // bbox corresponds to image (web)
      double xmin = bbox.getMinX() - bbox.getWidth() * padding;
      double xmax = bbox.getMaxX() + bbox.getWidth() * padding;
      double ymin = bbox.getMinY() - bbox.getHeight() * padding;
      double ymax = bbox.getMaxY() + bbox.getHeight() * padding;
      double yrange = ymax - ymin;
      double xrange = xmax - xmin;

      // empty space around the edge in case the lat/lon is out of range
      int topBlank = 0, bottomBlank = 0, leftBlank = 0, rightBlank = 0;
      // max for mapbox is 85.0511
      final double MAXY = proj.projectY(proj.getYMax());

      while(xmin < -360) {
         xmin += 360;
         xmax += 360;
      }

      while(xmax > 360) {
         xmin -= 360;
         xmax -= 360;
      }

      if(ymax > MAXY) {
         double diff = ymax - MAXY;
         topBlank = (int) (diff * imgh / yrange);
         ymax = MAXY;
      }

      if(ymin < -MAXY) {
         double diff = -MAXY - ymin;
         bottomBlank = (int) (diff * imgh / yrange);
         ymin = -MAXY;
      }

      if(xmin < -360) {
         double diff = -360 - xmin;
         leftBlank = (int) (diff * imgw / xrange);
         xmin = -360;

         // keep center at the same place if arbitrary bounds not supported.
         if(service.isDiscreteZoom()) {
            rightBlank = leftBlank;
         }
      }

      // if map doesn't support arbitrary bounds, we must maintain the same center for the
      // image. otherwise the map would not line up with the plot.
      if((topBlank > 0 || bottomBlank > 0) && service.isDiscreteZoom()) {
         topBlank = bottomBlank = Math.max(topBlank, bottomBlank);
      }

      double webw = imgw - leftBlank - rightBlank;
      double webh = imgh - topBlank - bottomBlank;
      int maxSize = service.getMaxSize();

      // if size > MAX_SIZE, reduce it on both sides to fit
      if(webw > maxSize) {
         double range = xmax - xmin;
         double adj = (webw - maxSize) / 2; // reduce on each side
         xmin += adj * range / webw;
         xmax -= adj * range / webw;
         webw = maxSize;
         leftBlank += adj;
      }

      if(webh > maxSize) {
         double range = ymax - ymin;
         double adj = (webh - maxSize) / 2; // reduce on each side
         ymin += adj * range / webh;
         ymax -= adj * range / webh;
         webh = maxSize;
         topBlank += adj;
      }

      double lonMin = proj.inverseX(xmin);
      double lonMax = proj.inverseX(xmax);
      double latMin = proj.inverseY(ymin);
      double latMax = proj.inverseY(ymax);

      Image img = service.loadImage(lonMin, lonMax, latMin, latMax, (int) webw, (int) webh);

      // fill in blank space at top and bottom
      if(topBlank > 0 || leftBlank > 0 || bottomBlank > 0 || rightBlank > 0) {
         BufferedImage img2 = new BufferedImage(imgw, imgh, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = (Graphics2D) img2.getGraphics();
         g.setColor(Color.WHITE);
         g.fillRect(0, 0, imgw, imgh);
         g.drawImage(img, leftBlank, topBlank, null);
         g.dispose();
         img = img2;
      }

      return img;
   }

   private WebMapService service;
}

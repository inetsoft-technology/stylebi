/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.graph.coord;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.geo.GeoDataSet;
import inetsoft.graph.geo.GeoMap;
import inetsoft.graph.geo.GeoProjection;
import inetsoft.graph.geo.GeoShape;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A fitted map frames its points, and a flagged map zooms out past the fit up to its layer.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GeoCoordZoomOutTest {
   // US cities, lon -122.4 to -74.0, lat 29.8 to 41.9
   private static final Object[][] ROWS = {
      { "Lon", "Lat", "Area" },
      { -122.4, 37.8, null },
      { -118.2, 34.1, null },
      { -87.6, 41.9, null },
      { -74.0, 40.7, null },
      { -95.4, 29.8, null },
   };
   private static final double LON_RANGE = 122.4 - 74.0;

   @Test
   void aFittedMapFramesItsPoints() {
      GeoCoord coord = layout(coord(false, 1), points());
      Rectangle2D box = coord.getBBox();

      for(int i = 1; i < ROWS.length; i++) {
         Point2D pt = coord.getProjection().project((Double) ROWS[i][0], (Double) ROWS[i][1]);
         assertTrue(box.contains(pt), pt + " in " + box);
      }

      assertTrue(box.getWidth() <= LON_RANGE * 1.3, "" + box);
   }

   @Test
   void withoutTheFlagZoomStaysAtLeastOne() {
      GeoCoord coord = coord(false, 0.5);
      layout(coord, points());

      assertEquals(1, coord.getZoom(), 0.0001);
   }

   @Test
   void withTheFlagZoomGoesBelowOne() {
      GeoCoord coord = coord(true, 0.5);
      Rectangle2D box = layout(coord, points()).getBBox();
      Rectangle2D fit = layout(coord(true, 1), points()).getBBox();

      assertEquals(0.5, coord.getZoom(), 0.0001);
      assertTrue(box.getWidth() > fit.getWidth(), box + " vs " + fit);
   }

   @Test
   void zoomOutStopsAtTheLayer() {
      GeoCoord coord = coord(true, 0.001);
      Rectangle2D box = layout(coord, points()).getBBox();
      GeoProjection proj = coord.getProjection();
      double layerW = proj.projectX(proj.getXMax()) - proj.projectX(proj.getXMin());

      assertTrue(coord.getZoom() > 0.001, "" + coord.getZoom());
      assertTrue(box.getWidth() <= layerW + 0.0001, box + " vs " + layerW);
      assertEquals(layerW, box.getWidth(), 0.0001, "the x extent binds for points in one country");
   }

   @Test
   void zoomOutStopsAtTheShapesLayer() {
      // a layer shape beyond the data rows, as a map's background layer is
      GeoCoord coord = coord(true, 0.001);
      Rectangle2D box = layout(coord, layer(-130, 20, -60, 55)).getBBox();

      assertEquals(70, box.getWidth(), 0.0001, "" + box);
   }

   @Test
   void aLayerInsideTheFitNeverZoomsIn() {
      GeoCoord coord = coord(true, 0.5);
      layout(coord, layer(-110, 32, -90, 40));

      assertEquals(1, coord.getZoom(), 0.0001);
   }

   @Test
   void theFlagTakesPartInEquality() {
      assertTrue(coord(true, 1).equalsContent(coord(true, 1)));
      assertTrue(coord(false, 1).equalsContent(coord(false, 1)));
      assertFalse(coord(true, 1).equalsContent(coord(false, 1)));
   }

   private static GeoCoord coord(boolean zoomOutToLayer, double zoom) {
      GeoCoord coord = new GeoCoord();
      coord.setXScale(new LinearScale("Lon"));
      coord.setYScale(new LinearScale("Lat"));
      coord.setFullMap(false);
      coord.setPadding(0.1);
      coord.setZoom(zoom);
      coord.setZoomOutToLayer(zoomOutToLayer);
      return coord;
   }

   private static GeoCoord layout(GeoCoord coord, DataSet data) {
      EGraph graph = new EGraph();
      graph.addElement(new PointElement("Lon", "Lat"));
      graph.setCoordinate(coord);

      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 500, 400);
      return (GeoCoord) vgraph.getCoordinate();
   }

   private static DataSet points() {
      return new DefaultDataSet(ROWS);
   }

   // the points with one layer shape that no data row names
   private static DataSet layer(double minX, double minY, double maxX, double maxY) {
      GeoShape shape = new GeoShape() {
         @Override
         protected Shape getShape() {
            return getBounds();
         }
      };
      Rectangle2D bounds = new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
      shape.setBounds(bounds);
      shape.setPrimaryAnchor(new Point2D.Double(bounds.getCenterX(), bounds.getCenterY()));

      GeoMap map = new GeoMap() {
         @Override
         public GeoShape getShape(String name) {
            return "L".equals(name) ? shape : null;
         }

         @Override
         public Collection<String> getNames() {
            return List.of("L");
         }
      };

      return new GeoDataSet(points(), map, "Area");
   }
}

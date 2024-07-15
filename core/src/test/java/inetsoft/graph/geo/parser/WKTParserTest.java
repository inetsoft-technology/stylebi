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
package inetsoft.graph.geo.parser;

import inetsoft.graph.geo.GeoShape;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.*;

class WKTParserTest {
   @Test
   void testParseIntegerPoint() throws Exception {
      testParsePoint("POINT (10 -20)", 10D, -20D);
   }

   @Test
   void testParseDecimalPoint() throws Exception {
      testParsePoint("POINT (10.1 -20.2)", 10.1, -20.2);
   }

   @Test
   void testParseScientificPoint() throws Exception {
      testParsePoint("POINT (1.1E1 -1.1E-1)", 11D, -0.11);
   }

   private void testParsePoint(String wkt, double x, double y) throws Exception {
      GeoShape shape = WKTParser.parse(wkt, false);
      assertNotNull(shape);
      assertThat(shape, instanceOf(GeoPoint.class));
      Point2D point = ((GeoPoint) shape).getPoint();
      assertNotNull(point);
      assertEquals(point.getX(), x, 0.000001);
      assertEquals(point.getY(), y, 0.000001);
   }

   @Test
   void testParseMultiPoint() throws Exception {
      String wkt = "MULTIPOINT (0 0,1 1,2 0,3 3)";
      GeoShape shape = WKTParser.parse(wkt, false);
      assertNotNull(shape);
      assertThat(shape, instanceOf(GeoMultiPoint.class));
      assertThrows(UnsupportedOperationException.class, ((GeoMultiPoint) shape)::getShape);
   }

   @Test
   void testParseLinestring() throws Exception {
      String wkt = "LINESTRING (0 0,1 1,2 0,3 3)";
      GeoShape shape = WKTParser.parse(wkt, false);
      assertNotNull(shape);
      assertThat(shape, instanceOf(GeoPolyline.class));
      Shape path = ((GeoPolyline) shape).getShape();
      int[] types = { 0, 1, 1, 1 };
      double[][] points = {
         { 0, 0 }, { 1, 1 }, { 2, 0 }, { 3, 3 }
      };
      validateShape(path, PathIterator.WIND_NON_ZERO, types, points);
   }

   @Test
   void testParseMultiLinestring() throws Exception {
      String wkt = "MULTILINESTRING ((0 0,1 1,2 0,3 3),(4 4,5 5,6 4,7 7))";
      GeoShape shape = WKTParser.parse(wkt, false);
      assertNotNull(shape);
      assertThat(shape, instanceOf(GeoPolyline.class));
      Shape path = ((GeoPolyline) shape).getShape();
      int[] types = {
         0, 1, 1, 1,
         0, 1, 1, 1
      };
      double[][] points = {
         { 0, 0 }, { 1, 1 }, { 2, 0 }, { 3, 3 },
         { 4, 4 }, { 5, 5 }, { 6, 4 }, { 7, 7 }
      };
      validateShape(path, PathIterator.WIND_NON_ZERO, types, points);
   }

   @Test
   void testParsePolygon() throws Exception {
      String wkt = "POLYGON ((0 0,0 3,3 3,3 0,0 0),(1 1,2 1,2 2,1 2,1 1))";
      GeoShape shape = WKTParser.parse(wkt, false);
      assertNotNull(shape);
      assertThat(shape, instanceOf(GeoPolygon.class));
      Shape path = ((GeoPolygon) shape).getShape();
      int[] types = {
         0, 1, 1, 1, 1, 4,
         0, 1, 1, 1, 1, 4
      };
      double[][] points = {
         { 0, 0 }, { 0, 3 }, { 3, 3 }, { 3, 0 }, { 0, 0 }, { 0, 0 },
         { 1, 1 }, { 2, 1 }, { 2, 2 }, { 1, 2 }, { 1, 1 }, { 1, 1 }
      };
      validateShape(path, PathIterator.WIND_EVEN_ODD, types, points);
   }

   @Test
   void testParseMultiPolygon() throws Exception {
      String wkt = "MULTIPOLYGON(((0 0,0 3,3 3,3 0,0 0),(1 1,2 1,2 2,1 2,1 1)),((4 4,4 7,7 7,7 4,4 4),(5 5,6 5,6 6,5 6,5 5)))";
      GeoShape shape = WKTParser.parse(wkt, false);
      assertNotNull(shape);
      assertThat(shape, instanceOf(GeoMultiPolygon.class));
      Shape path = ((GeoMultiPolygon) shape).getShape();
      int[] types = {
         0, 1, 1, 1, 1, 4,
         0, 1, 1, 1, 1, 4,
         0, 1, 1, 1, 1, 4,
         0, 1, 1, 1, 1, 4
      };
      double[][] points = {
         { 0, 0 }, { 0, 3 }, { 3, 3 }, { 3, 0 }, { 0, 0 }, { 0, 0 },
         { 1, 1 }, { 2, 1 }, { 2, 2 }, { 1, 2 }, { 1, 1 }, { 1, 1 },
         { 4, 4 }, { 4, 7 }, { 7, 7 }, { 7, 4 }, { 4, 4 }, { 4, 4 },
         { 5, 5 }, { 6, 5 }, { 6, 6 }, { 5, 6 }, { 5, 5 }, { 5, 5 }
      };
      validateShape(path, PathIterator.WIND_EVEN_ODD, types, points);
   }

   private void validateShape(Shape path, int windingRule, int[] types, double[][] points) {
      PathIterator iterator = path.getPathIterator(null);
      assertEquals(windingRule, iterator.getWindingRule(), "Incorrect winding rule");

      int counter = 0;
      double[] coords = new double[6];

      for(; !iterator.isDone(); iterator.next(), counter++) {
         assertThat("Too many points", counter, lessThan(points.length));
         int type = iterator.currentSegment(coords);
         assertEquals(types[counter], type, "Incorrect segment type for point " + counter);

         for(int i = 0; i < points[counter].length; i++) {
            assertEquals(points[counter][i], coords[i], 0.000001, "Incorrect coordinate " + i + " for point " + counter);
         }
      }

      assertEquals(points.length, counter, "Too few points");
   }
}

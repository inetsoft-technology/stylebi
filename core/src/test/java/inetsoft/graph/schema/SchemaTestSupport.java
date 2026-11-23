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
package inetsoft.graph.schema;

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.element.SchemaElement;
import inetsoft.graph.geometry.SchemaGeometry;
import inetsoft.graph.scale.Scale;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixture of the schema (candle, stock, box) painter, visual object and geometry tests: a
 * rectangular coordinate mock that maps a value {@code v} to {@code y = 10 v} (reversed:
 * {@code 1000 - 10 v}; pixel: {@code y = v}, x as the others) and a dimension value {@code t} to {@code x = 10 t} (zero
 * without an x scale), and a real {@link SchemaGeometry} over a real {@link SchemaElement} with one
 * dimension whose tuple is {@code [5, vars...]}.
 */
public final class SchemaTestSupport {
   public static final double EPS = 1e-6;

   /** How a value is mapped to a position. */
   public enum Mapping { SCALED, REVERSED, PIXEL }

   /** A painter initialised on the mock coordinate. */
   public static final class Fixture {
      public SchemaElement element;
      public SchemaGeometry geometry;
      public RectCoord coord;
      public SchemaPainter painter;
   }

   private SchemaTestSupport() {
   }

   /** A rectangular coordinate mock with the given mapping and slot width. */
   public static RectCoord coord(boolean hasX, Mapping mapping, double maxWidth) {
      RectCoord coord = mock(RectCoord.class);
      when(coord.getXScale()).thenReturn(hasX ? mock(Scale.class) : null);
      when(coord.getYScale()).thenReturn(mock(Scale.class));
      when(coord.getMaxWidth()).thenReturn(maxWidth);
      when(coord.getPosition(any(double[].class))).thenAnswer(inv -> {
         double[] t = inv.getArgument(0);
         double v = t[t.length - 1];
         double x = t.length > 1 ? t[0] : 0;

         switch(mapping) {
         case REVERSED:
            return new java.awt.geom.Point2D.Double(10 * x, 1000 - 10 * v);
         case PIXEL:
            return new java.awt.geom.Point2D.Double(10 * x, v);
         default:
            return new java.awt.geom.Point2D.Double(10 * x, 10 * v);
         }
      });

      // the coordinate transform of a shape adds (10, 20)
      when(coord.transformShape(any())).thenAnswer(inv -> {
         Shape shape = inv.getArgument(0);
         return java.awt.geom.AffineTransform.getTranslateInstance(10, 20)
            .createTransformedShape(shape);
      });
      return coord;
   }

   /** An element with one dimension and the painter. */
   public static SchemaElement element(SchemaPainter painter) {
      SchemaElement element = new SchemaElement(painter);
      element.addDim("x");
      return element;
   }

   /** A real geometry; the visual model returns green for every color and no size frame. */
   public static SchemaGeometry geometry(SchemaElement element, double[] tuple, String... vars) {
      VisualModel vmodel = mock(VisualModel.class);
      when(vmodel.getColor(any(), anyInt())).thenReturn(Color.GREEN);
      return new SchemaGeometry(element, mock(GGraph.class), vars, 0, vmodel, tuple);
   }

   /** The tuple {@code [5, vars...]}. */
   public static double[] tuple(double... vars) {
      double[] tuple = new double[vars.length + 1];
      tuple[0] = 5;
      System.arraycopy(vars, 0, tuple, 1, vars.length);
      return tuple;
   }

   /** Initialise the painter on a mock coordinate with the given values. */
   public static Fixture fixture(SchemaPainter painter, boolean hasX, Mapping mapping,
                                 double maxWidth, double... vars)
   {
      Fixture f = new Fixture();
      String[] names = new String[vars.length];

      for(int i = 0; i < names.length; i++) {
         names[i] = "v" + i;
      }

      f.painter = painter;
      f.element = element(painter);
      f.geometry = geometry(f.element, tuple(vars), names);
      f.coord = coord(hasX, mapping, maxWidth);
      painter.init(f.geometry, f.coord);
      return f;
   }

   /** The default fixture: x scale, scaled mapping, slot width 100. */
   public static Fixture fixture(SchemaPainter painter, double... vars) {
      return fixture(painter, true, Mapping.SCALED, 100, vars);
   }

   public static void assertLine(Shape shape, double x1, double y1, double x2, double y2) {
      assertInstanceOf(Line2D.class, shape);
      Line2D line = (Line2D) shape;
      assertEquals(x1, line.getX1(), EPS, "x1");
      assertEquals(y1, line.getY1(), EPS, "y1");
      assertEquals(x2, line.getX2(), EPS, "x2");
      assertEquals(y2, line.getY2(), EPS, "y2");
   }

   public static void assertBounds(Shape shape, double x, double y, double w, double h) {
      Rectangle2D b = shape.getBounds2D();
      assertEquals(x, b.getX(), EPS, "x");
      assertEquals(y, b.getY(), EPS, "y");
      assertEquals(w, b.getWidth(), EPS, "width");
      assertEquals(h, b.getHeight(), EPS, "height");
   }

   public static void assertBounds(Rectangle2D b, double x, double y, double w, double h) {
      assertEquals(x, b.getX(), EPS, "x");
      assertEquals(y, b.getY(), EPS, "y");
      assertEquals(w, b.getWidth(), EPS, "width");
      assertEquals(h, b.getHeight(), EPS, "height");
   }
}

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

import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.SchemaElement;
import inetsoft.graph.geometry.SchemaGeometry;
import inetsoft.graph.schema.SchemaTestSupport.Fixture;
import inetsoft.graph.schema.SchemaTestSupport.Mapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.Color;
import java.util.stream.Stream;

import static inetsoft.graph.schema.SchemaTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The helpers shared by the candle, stock and box painters: the horizontal size from the size
 * frame and the slot, the base width, the alpha hint, the tuple handed to the coordinate and the
 * coordinate transform of the shapes. The protected helpers are called from the same package.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SchemaPainterTest {
   private static Stream<SchemaPainter> painters() {
      return Stream.of(new CandlePainter(), new StockPainter(), new BoxPainter());
   }

   private static SizeFrame sizeFrame(double smallest, double largest, double max) {
      SizeFrame frame = mock(SizeFrame.class);
      when(frame.getSmallest()).thenReturn(smallest);
      when(frame.getLargest()).thenReturn(largest);
      when(frame.getMax()).thenReturn(max);
      return frame;
   }

   // ----------------------------------------------------------------------------- getSize

   @Test
   @DisplayName("without a size frame the width is 30 limited by the slot")
   void sizeWithoutFrame() {
      CandlePainter painter = new CandlePainter();

      assertEquals(30, painter.getSize(coord(true, Mapping.SCALED, 100), 7, null), EPS);
      assertEquals(20, painter.getSize(coord(true, Mapping.SCALED, 20), 7, null), EPS);
   }

   @Test
   @DisplayName("a constant size frame gives half the slot, at least 1")
   void sizeConstantFrame() {
      CandlePainter painter = new CandlePainter();
      SizeFrame frame = sizeFrame(5, 5, 5);

      assertEquals(50, painter.getSize(coord(true, Mapping.SCALED, 100), 5, frame), EPS);
      assertEquals(1, painter.getSize(coord(true, Mapping.SCALED, 1), 5, frame), EPS);
   }

   @ParameterizedTest
   @MethodSource("painters")
   @DisplayName("size grows with the value and stays between the base width and the slot")
   void sizeRange(SchemaPainter painter) {
      SizeFrame frame = sizeFrame(2, 10, 10);
      RectCoord coord = coord(true, Mapping.SCALED, 100);
      double previous = Double.NEGATIVE_INFINITY;

      for(int size = 0; size <= 10; size++) {
         double width = painter.getSize(coord, size, frame);

         assertTrue(width >= previous, "non-decreasing at " + size);
         assertTrue(width >= 5 - EPS && width <= 100 + EPS, "range at " + size);
         previous = width;
      }
   }

   @ParameterizedTest
   @MethodSource("painters")
   @DisplayName("the largest size fills exactly the slot")
   void sizeLargestFillsSlot(SchemaPainter painter) {
      assertEquals(100, painter.getSize(coord(true, Mapping.SCALED, 100), 10,
                                        sizeFrame(2, 10, 10)), 1e-9);
   }

   @ParameterizedTest
   @MethodSource("basePainters")
   @DisplayName("the size is relative to the frame maximum, not to the largest value")
   void sizeRelativeToFrameMax(SchemaPainter painter) {
      SizeFrame frame = sizeFrame(2, 10, 20);
      RectCoord coord = coord(true, Mapping.SCALED, 100);

      assertEquals(52.5, painter.getSize(coord, 10, frame), 1e-9);
      assertEquals(100, painter.getSize(coord, 20, frame), 1e-9);
   }

   private static Stream<SchemaPainter> basePainters() {
      return Stream.of(new CandlePainter(), new StockPainter());
   }

   @Test
   @DisplayName("base width: 5 for candle and stock, a third of the slot for box (pinned)")
   void baseWidth() {
      assertEquals(5, new CandlePainter().getBaseWidth(99), EPS);
      assertEquals(5, new StockPainter().getBaseWidth(99), EPS);
      assertEquals(33, new BoxPainter().getBaseWidth(99), EPS);
   }

   // ---------------------------------------------------------------------------- applyAlpha

   private static CandlePainter painterWithAlpha(Object alpha) {
      CandlePainter painter = new CandlePainter();
      SchemaElement element = element(painter);

      if(alpha != null) {
         element.setHint(GraphElement.HINT_ALPHA, alpha);
      }

      painter.geometry = geometry(element, tuple(1, 1, 1, 1), "a", "b", "c", "d");
      return painter;
   }

   @Test
   @DisplayName("alpha hint 0.5 keeps the rgb and halves the alpha")
   void applyAlpha() {
      Color result = painterWithAlpha(0.5).applyAlpha(new Color(10, 20, 30));

      assertEquals(10, result.getRed());
      assertEquals(20, result.getGreen());
      assertEquals(30, result.getBlue());
      // java.awt.Color(float r, g, b, a) rounds a * 255 + 0.5 down: 0.5f gives 128
      assertEquals(128, result.getAlpha());
   }

   @Test
   @DisplayName("alpha 1, a non-numeric hint, a null color and no hint leave the color alone")
   void applyAlphaControls() {
      Color color = new Color(10, 20, 30);

      assertSame(color, painterWithAlpha(1).applyAlpha(color));
      assertSame(color, painterWithAlpha("abc").applyAlpha(color));
      assertSame(color, painterWithAlpha(null).applyAlpha(color));
      assertNull(painterWithAlpha(0.5).applyAlpha(null));
   }

   // ---------------------------------------------------------------------------- getSubTuple

   private static CandlePainter painterWithDims(int dims) {
      CandlePainter painter = new CandlePainter();
      SchemaElement element = new SchemaElement(painter);

      for(int i = 0; i < dims; i++) {
         element.addDim("d" + i);
      }

      SchemaGeometry geometry = mock(SchemaGeometry.class);
      when(geometry.getElement()).thenReturn(element);
      painter.geometry = geometry;
      return painter;
   }

   @Test
   @DisplayName("with an x scale the dimension values lead the tuple")
   void subTupleWithX() {
      assertArrayEquals(new double[] { 3, 9 },
                        painterWithDims(1).getSubTuple(1, new double[] { 3 }, new double[] { 8, 9 },
                                                       true), EPS);
   }

   @Test
   @DisplayName("without an x scale the x slot stays 0 (52221)")
   void subTupleWithoutX() {
      assertArrayEquals(new double[] { 0, 9 },
                        painterWithDims(1).getSubTuple(1, new double[] { 3 }, new double[] { 8, 9 },
                                                       false), EPS);
   }

   @Test
   @DisplayName("with two dimensions and no x scale only the last dimension is dropped")
   void subTupleTwoDimsWithoutX() {
      assertArrayEquals(new double[] { 3, 0, 9 },
                        painterWithDims(2).getSubTuple(1, new double[] { 3, 4 },
                                                       new double[] { 8, 9 }, false), EPS);
   }

   @Test
   @DisplayName("without dimensions the tuple is the selected value")
   void subTupleNoDims() {
      assertArrayEquals(new double[] { 8 },
                        painterWithDims(0).getSubTuple(0, new double[0], new double[] { 8, 9 },
                                                       false), EPS);
   }

   @Test
   @DisplayName("a missing variable leaves the value slot at 0 without failing (pinned)")
   void subTupleMissingVar() {
      assertArrayEquals(new double[] { 3, 0 },
                        painterWithDims(1).getSubTuple(3, new double[] { 3 },
                                                       new double[] { 7, 8, 9 }, true), EPS);
   }

   // -------------------------------------------------------------------------- transformShape

   @Test
   @DisplayName("the coordinate transform replaces every shape and keeps the count")
   void transformShape() {
      Fixture f = fixture(new CandlePainter(), 80, 60, 40, 20);

      f.painter.transformShape(f.coord, null);

      assertEquals(2, f.painter.getShapeCount());
      assertBounds(f.painter.getShape(0), 60, 220, 0, 600);
      assertBounds(f.painter.getShape(1), 45, 420, 30, 200);
   }
}

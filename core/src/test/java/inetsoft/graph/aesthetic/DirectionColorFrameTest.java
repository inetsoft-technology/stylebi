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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.visual.ElementVO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Up when the close is at or above the open, or a waterfall step is not negative; down otherwise.
 */
@Tag("core")
class DirectionColorFrameTest {
   private static final Color UP = new Color(0x1C857B);
   private static final Color DOWN = new Color(0xAD6119);
   private static final String OPEN = "Sum(Open)";
   private static final String CLOSE = "Sum(Close)";
   private static final String HIGH = "Sum(High)";
   private static final String STEP = "Sum(V)";

   @Test
   void closeAboveOpenIsUp() {
      assertEquals(UP, candle().getColor(day(OPEN, CLOSE, 10.0, 12.0), HIGH, 0));
   }

   @Test
   void closeBelowOpenIsDown() {
      assertEquals(DOWN, candle().getColor(day(OPEN, CLOSE, 12.0, 10.0), HIGH, 0));
   }

   @Test
   void aFlatDayIsUp() {
      assertEquals(UP, candle().getColor(day(OPEN, CLOSE, 11.0, 11.0), HIGH, 0));
   }

   @Test
   void aMissingValueIsUp() {
      assertEquals(UP, candle().getColor(day(OPEN, CLOSE, null, 10.0), HIGH, 0));
      assertEquals(UP, candle().getColor(day(OPEN, CLOSE, 12.0, null), HIGH, 0));

      DataSet noOpen = new DefaultDataSet(new Object[][] { { CLOSE }, { 10.0 } });
      assertEquals(UP, candle().getColor(noOpen, HIGH, 0));
   }

   @Test
   void theBrushedCopyIsReadWhenThePlainColumnIsAbsent() {
      DataSet all = day(ElementVO.ALL_PREFIX + OPEN, ElementVO.ALL_PREFIX + CLOSE, 12.0, 10.0);
      assertEquals(DOWN, candle().getColor(all, ElementVO.ALL_PREFIX + HIGH, 0));
   }

   @Test
   void theBrushedCopyIsReadWhereThePlainColumnHasNoValue() {
      // a brushed chart's all-data rows hold no value under the plain name
      DataSet data = new DefaultDataSet(new Object[][] {
         { OPEN, CLOSE, ElementVO.ALL_PREFIX + OPEN, ElementVO.ALL_PREFIX + CLOSE },
         { 10.0, 12.0, null, null },
         { null, null, 12.0, 10.0 },
      });

      assertEquals(UP, candle().getColor(data, HIGH, 0));
      assertEquals(DOWN, candle().getColor(data, ElementVO.ALL_PREFIX + HIGH, 1));
   }

   @Test
   void aNegativeStepIsDown() {
      assertEquals(DOWN, waterfall().getColor(step(-3.0), STEP, 0));
   }

   @Test
   void aPositiveStepIsUp() {
      assertEquals(UP, waterfall().getColor(step(5.0), STEP, 0));
      assertEquals(UP, waterfall().getColor(step(0.0), STEP, 0), "a zero step");
      assertEquals(UP, waterfall().getColor(step(null), STEP, 0), "a missing step");
   }

   @Test
   void aCandleWithoutFieldNamesDoesNotReadTheStep() {
      DirectionColorFrame frame = new DirectionColorFrame(UP, DOWN, null, null);

      assertEquals(UP, frame.getColor(step(-3.0), STEP, 0));
      assertNotEquals(waterfall(), frame);
   }

   @Test
   void aValueWithoutARowIsUp() {
      assertEquals(UP, candle().getColor((Object) "Sum(High)"));
      assertEquals(UP, waterfall().getColor((Object) (-3.0)));
   }

   @Test
   void itShowsNoLegend() {
      assertFalse(candle().isVisible());
      assertFalse(waterfall().isVisible());
   }

   @Test
   void equalsAndCloneCoverTheColours() {
      DirectionColorFrame frame = candle();

      assertEquals(frame, candle());
      assertEquals(frame.hashCode(), candle().hashCode());
      assertNotEquals(frame, new DirectionColorFrame(Color.RED, DOWN, OPEN, CLOSE));
      assertNotEquals(frame, new DirectionColorFrame(UP, Color.RED, OPEN, CLOSE));
      assertNotEquals(frame, new DirectionColorFrame(UP, DOWN, "x", CLOSE));
      assertNotEquals(frame, new DirectionColorFrame(UP, DOWN, OPEN, "x"));
      assertNotEquals(frame, waterfall());
      assertNotEquals(frame, new StaticColorFrame(UP));

      DirectionColorFrame copy = (DirectionColorFrame) frame.clone();
      assertNotSame(frame, copy);
      assertEquals(frame, copy);
      assertEquals(UP, copy.getUpColor());
      assertEquals(DOWN, copy.getDownColor());
      assertEquals(DOWN, copy.getColor(day(OPEN, CLOSE, 12.0, 10.0), HIGH, 0));

      // the clone keeps the waterfall mode
      DirectionColorFrame steps = (DirectionColorFrame) waterfall().clone();
      assertEquals(waterfall(), steps);
      assertEquals(waterfall().hashCode(), steps.hashCode());
      assertEquals(DOWN, steps.getColor(step(-3.0), STEP, 0));
   }

   private static DirectionColorFrame candle() {
      return new DirectionColorFrame(UP, DOWN, OPEN, CLOSE);
   }

   private static DirectionColorFrame waterfall() {
      return DirectionColorFrame.forWaterfall(UP, DOWN);
   }

   private static DataSet day(String openCol, String closeCol, Double open, Double close) {
      return new DefaultDataSet(new Object[][] { { openCol, closeCol }, { open, close } });
   }

   private static DataSet step(Double value) {
      return new DefaultDataSet(new Object[][] { { STEP }, { value } });
   }
}

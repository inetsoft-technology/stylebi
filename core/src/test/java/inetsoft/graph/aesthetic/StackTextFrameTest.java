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

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StackTextFrame: one text value per stack (the total of the stack, shown on its top row).
 * The standard data is State/Quantity with rows (CA,10) (CA,5) (NY,7) (CA,-3) (NY,-2).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class StackTextFrameTest {
   private static DefaultDataSet quantities(Object[]... rows) {
      Object[][] all = new Object[rows.length + 1][];
      all[0] = new Object[] { "State", "Quantity" };
      System.arraycopy(rows, 0, all, 1, rows.length);
      return new DefaultDataSet(all);
   }

   private static DefaultDataSet standard() {
      return quantities(new Object[] { "CA", 10 }, new Object[] { "CA", 5 },
                        new Object[] { "NY", 7 }, new Object[] { "CA", -3 },
                        new Object[] { "NY", -2 });
   }

   private static StackTextFrame frame() {
      return new StackTextFrame(new IntervalElement("State", "Quantity"), "Quantity");
   }

   private static Object text(StackTextFrame frame, DefaultDataSet data, int row) {
      return frame.getText(data, "Quantity", row);
   }

   @Test
   void positiveStackTotalIsShownOnTheTopRowOfTheStack() {
      DefaultDataSet data = standard();
      StackTextFrame frame = frame();
      frame.init(data);

      assertNull(text(frame, data, 0));
      assertEquals(15.0, text(frame, data, 1));
      assertEquals(7.0, text(frame, data, 2));
   }

   @Test
   void negativeValuesStackSeparately() {
      DefaultDataSet data = standard();
      StackTextFrame frame = frame();
      frame.init(data);

      assertEquals(-3.0, text(frame, data, 3));
      assertEquals(-2.0, text(frame, data, 4));
   }

   @Test
   void withoutNegativeStackingAllValuesOfAGroupAreSummed() {
      DefaultDataSet data = standard();
      StackTextFrame frame = frame();
      frame.setStackNegative(false);
      frame.init(data);

      assertNull(text(frame, data, 0));
      assertNull(text(frame, data, 1));
      assertNull(text(frame, data, 2));
      assertEquals(12.0, text(frame, data, 3));
      assertEquals(5.0, text(frame, data, 4));
   }

   @Test
   void stackNegativeIsTakenFromTheElement() {
      assertTrue(frame().isStackNegative());

      IntervalElement elem = new IntervalElement("State", "Quantity");
      elem.setStackNegative(false);

      assertFalse(new StackTextFrame(elem, "Quantity").isStackNegative());
   }

   @Test
   void nullMeasureRowsAreIgnored() {
      DefaultDataSet data = quantities(new Object[] { "CA", 10 }, new Object[] { "CA", null });
      StackTextFrame frame = frame();
      frame.init(data);

      assertEquals(10.0, text(frame, data, 0));
      assertNull(text(frame, data, 1));
   }

   @Test
   void nonNumberMeasureCellsAreNotSummed() {
      DefaultDataSet data = quantities(new Object[] { "CA", 10 }, new Object[] { "CA", "x" });
      StackTextFrame frame = frame();
      frame.init(data);

      assertEquals(10.0, text(frame, data, 0));
      assertNull(text(frame, data, 1));
   }

   private static DefaultDataSet withPrice(Object[]... rows) {
      Object[][] all = new Object[rows.length + 1][];
      all[0] = new Object[] { "State", "Quantity", "Price" };
      System.arraycopy(rows, 0, all, 1, rows.length);
      return new DefaultDataSet(all);
   }

   private static StackTextFrame twoMeasures() {
      IntervalElement elem = new IntervalElement();
      elem.addDim("State");
      elem.addVar("Quantity");
      elem.addVar("Price");
      return new StackTextFrame(elem, "Quantity", "Price");
   }

   @Test
   void twoMeasuresShowTheGroupTotalUnderTheLastMeasure() {
      DefaultDataSet data = withPrice(new Object[] { "CA", 10, 1 }, new Object[] { "CA", 5, 2 },
                                      new Object[] { "NY", 7, 3 });
      StackTextFrame frame = twoMeasures();
      frame.init(data);

      assertEquals(18.0, frame.getText(data, "Price", 1));
      assertNull(frame.getText(data, "Quantity", 1));
      assertNull(frame.getText(data, "Price", 0));
      assertEquals(10.0, frame.getText(data, "Price", 2));
      assertNull(frame.getText(data, "Quantity", 2));
   }

   @Test
   void twoMeasuresShowTheTotalUnderTheLastMeasureThatHasAValue() {
      DefaultDataSet data = withPrice(new Object[] { "CA", 10, 1 },
                                      new Object[] { "CA", 5, null });
      StackTextFrame frame = twoMeasures();
      frame.init(data);

      assertEquals(16.0, frame.getText(data, "Quantity", 1));
      assertNull(frame.getText(data, "Price", 1));
   }

   @Test
   void twoDimensionsGroupByBothDimensions() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "State", "City", "Qty" },
         { "CA", "LA", 1 },
         { "CA", "SF", 2 },
         { "CA", "LA", 3 }
      });
      StackTextFrame frame = new StackTextFrame(new IntervalElement("State", "City", "Qty"), "Qty");
      frame.init(data);

      assertNull(frame.getText(data, "Qty", 0));
      assertEquals(2.0, frame.getText(data, "Qty", 1));
      assertEquals(4.0, frame.getText(data, "Qty", 2));
   }

   @Test
   void stackRowIndexesListTheRowsOfTheStackSorted() {
      DefaultDataSet data = standard();
      StackTextFrame frame = frame();
      frame.init(data);

      assertArrayEquals(new int[] { 0, 1 }, frame.getStackRowIndexes(1));
      assertArrayEquals(new int[] { 2 }, frame.getStackRowIndexes(2));
   }

   @Test
   void initializingTwiceDoesNotCountRowsTwice() {
      DefaultDataSet data = standard();
      StackTextFrame frame = frame();
      frame.init(data);
      frame.init(data);

      assertEquals(15.0, text(frame, data, 1));

      StackTextFrame lazy = frame();

      assertEquals(15.0, text(lazy, data, 1));
   }

   @Test
   void eachDataSetHasItsOwnTotals() {
      DefaultDataSet first = standard();
      DefaultDataSet second = quantities(new Object[] { "CA", 100 }, new Object[] { "CA", 1 });
      StackTextFrame frame = frame();
      frame.init(first);
      frame.init(second);

      assertEquals(15.0, text(frame, first, 1));
      assertEquals(101.0, text(frame, second, 1));
   }

   @Test
   void visibleFilterHidesRowsButKeepsTheTotals() {
      DefaultDataSet data = standard();
      StackTextFrame frame = frame();
      frame.setVisibleField("State");
      frame.setVisibleValues("NY");
      frame.init(data);

      assertNull(text(frame, data, 1));
      assertEquals(7.0, text(frame, data, 2));
      assertEquals(-2.0, text(frame, data, 4));
   }

   @Test
   void cloneDoesNotShareTotalsWithTheOriginal() {
      DefaultDataSet data = standard();
      StackTextFrame frame = frame();
      StackTextFrame copy = (StackTextFrame) frame.clone();
      copy.setStackNegative(false);
      copy.init(data);

      assertEquals(15.0, text(frame, data, 1));
   }

}

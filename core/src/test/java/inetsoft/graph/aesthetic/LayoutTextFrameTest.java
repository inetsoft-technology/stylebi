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
package inetsoft.graph.aesthetic;

import inetsoft.graph.CompositeLabel;
import inetsoft.graph.TextSegment;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.uql.viewsheet.graph.TextLayout;
import inetsoft.uql.viewsheet.graph.TextLayoutItem;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.TextLayoutRow;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.DecimalFormat;
import java.text.Format;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LayoutTextFrame}.
 * <p>
 * FIELD items are keyed by {@code fieldIndex}, which is a 0-based POSITION into the
 * frame's column array (the {@code String[]} passed to the constructor / returned by
 * {@link LayoutTextFrame#getFields()}). {@code getText} resolves each FIELD item's value
 * via {@code cols[item.getFieldIndex()]}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LayoutTextFrameTest {

   // ---------------------------------------------------------------------------
   // Helpers
   // ---------------------------------------------------------------------------

   private DefaultDataSet twoColumnDataset(Object nameVal, Object valueVal) {
      return new DefaultDataSet(new Object[][] {
         { "name",   "value"  },
         { nameVal,  valueVal },
      });
   }

   private DefaultDataSet threeColumnDataset(Object a, Object b, Object c) {
      return new DefaultDataSet(new Object[][] {
         { "a", "b", "c" },
         { a,   b,   c   },
      });
   }

   /** Each field gets its own row, keyed by the column POSITIONS provided. */
   private TextLayout verticalLayout(int... fieldIndexes) {
      TextLayout layout = new TextLayout();
      for(int fi : fieldIndexes) {
         TextLayoutRow row = new TextLayoutRow();
         row.addItem(TextLayoutItem.ofField(fi));
         layout.addRow(row);
      }
      return layout;
   }

   /** All fields in one row, separated by spacing items, keyed by the column POSITIONS provided. */
   private TextLayout horizontalLayout(double spacing, int... fieldIndexes) {
      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      for(int i = 0; i < fieldIndexes.length; i++) {
         if(i > 0) {
            row.addItem(TextLayoutItem.ofSpacing(spacing));
         }
         row.addItem(TextLayoutItem.ofField(fieldIndexes[i]));
      }
      layout.addRow(row);
      return layout;
   }

   // ---------------------------------------------------------------------------
   // 0. Task D1 — FIELD resolution by index against frame columns
   // ---------------------------------------------------------------------------

   @Test
   void resolvesFieldByIndexAgainstFrameColumns() {
      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      row.addItem(TextLayoutItem.ofField(0));
      row.addItem(TextLayoutItem.ofStatic(" - "));
      row.addItem(TextLayoutItem.ofField(1));
      layout.addRow(row);

      LayoutTextFrame frame = new LayoutTextFrame("state", "Sum(sales)");
      frame.setLayout(layout);

      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "state", "Sum(sales)" },
         { "CA", 100.0 }
      });

      Object text = frame.getText(ds, "Sum(sales)", 0);
      assertTrue(text instanceof CompositeLabel);
      CompositeLabel cl = (CompositeLabel) text;
      assertEquals("CA", cl.getSegments()[0].getText());
      assertEquals(" - ", cl.getSegments()[1].getText());
      assertEquals("100.0", cl.getSegments()[2].getText());
   }

   // ---------------------------------------------------------------------------
   // 1. Vertical orientation — fields joined with newline
   // ---------------------------------------------------------------------------

   @Test
   void vertical_twoFields_joinedWithNewline() {
      DefaultDataSet ds = twoColumnDataset("Alice", 42);
      LayoutTextFrame frame = new LayoutTextFrame("name", "value");
      frame.setLayout(verticalLayout(0, 1));

      Object result = frame.getText(ds, "name", 0);

      assertInstanceOf(CompositeLabel.class, result);
      TextSegment[] segments = ((CompositeLabel) result).getSegments();
      assertEquals(2, segments.length);
      assertEquals("Alice", segments[0].getText());
      assertEquals("42", segments[1].getText());
   }

   @Test
   void vertical_threeFields_joinedWithNewlines() {
      DefaultDataSet ds = threeColumnDataset("A", "B", "C");
      LayoutTextFrame frame = new LayoutTextFrame("a", "b", "c");
      frame.setLayout(verticalLayout(0, 1, 2));

      Object result = frame.getText(ds, "a", 0);

      assertInstanceOf(CompositeLabel.class, result);
      TextSegment[] segments = ((CompositeLabel) result).getSegments();
      assertEquals(3, segments.length);
      assertEquals("A", segments[0].getText());
      assertEquals("B", segments[1].getText());
      assertEquals("C", segments[2].getText());
   }

   // ---------------------------------------------------------------------------
   // 2. Horizontal orientation — fields with spacing between them
   // ---------------------------------------------------------------------------

   @Test
   void horizontal_spacingTwo_twoSpacesBetweenFields() {
      DefaultDataSet ds = twoColumnDataset("Hello", "World");
      LayoutTextFrame frame = new LayoutTextFrame("name", "value");
      frame.setLayout(horizontalLayout(2.0, 0, 1));

      Object result = frame.getText(ds, "name", 0);

      assertInstanceOf(CompositeLabel.class, result);
      TextSegment[] segments = ((CompositeLabel) result).getSegments();
      assertEquals(3, segments.length);
      assertEquals("Hello", segments[0].getText());
      assertTrue(segments[1].isSpacing());
      assertEquals(2.0, segments[1].getSpacingAmount(), 1e-9);
      assertEquals("World", segments[2].getText());
   }

   @Test
   void horizontal_spacingOne_oneSpaceBetweenFields() {
      DefaultDataSet ds = twoColumnDataset("Foo", "Bar");
      LayoutTextFrame frame = new LayoutTextFrame("name", "value");
      frame.setLayout(horizontalLayout(1.0, 0, 1));

      Object result = frame.getText(ds, "name", 0);

      assertInstanceOf(CompositeLabel.class, result);
      TextSegment[] segments = ((CompositeLabel) result).getSegments();
      assertEquals(3, segments.length);
      assertEquals("Foo", segments[0].getText());
      assertTrue(segments[1].isSpacing());
      assertEquals(1.0, segments[1].getSpacingAmount(), 1e-9);
      assertEquals("Bar", segments[2].getText());
   }

   @Test
   void horizontal_spacingZero_atLeastOneSpaceBetweenFields() {
      DefaultDataSet ds = twoColumnDataset("X", "Y");
      LayoutTextFrame frame = new LayoutTextFrame("name", "value");
      frame.setLayout(horizontalLayout(0.0, 0, 1));

      Object result = frame.getText(ds, "name", 0);

      assertInstanceOf(CompositeLabel.class, result);
      TextSegment[] segments = ((CompositeLabel) result).getSegments();
      assertEquals(3, segments.length);
      assertEquals("X", segments[0].getText());
      assertEquals(0.0, segments[1].getSpacingAmount(), 1e-9);
      assertEquals("Y", segments[2].getText());
   }

   // ---------------------------------------------------------------------------
   // 3. Null layout — delegates to super
   // ---------------------------------------------------------------------------

   @Test
   void nullLayout_delegatesToSuper() {
      DefaultDataSet ds = twoColumnDataset("Only", 7);
      LayoutTextFrame frame = new LayoutTextFrame("name");

      Object result = assertDoesNotThrow(() -> frame.getText(ds, "name", 0));
      assertNotNull(result);
   }

   // ---------------------------------------------------------------------------
   // 4. Static item interleaved correctly
   // ---------------------------------------------------------------------------

   @Test
   void staticItem_interleaved_appearsInOutput() {
      DefaultDataSet ds = twoColumnDataset("John", "Doe");
      LayoutTextFrame frame = new LayoutTextFrame("name", "value");

      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      row.addItem(TextLayoutItem.ofField(0));
      row.addItem(TextLayoutItem.ofStatic(":"));
      row.addItem(TextLayoutItem.ofField(1));
      layout.addRow(row);
      frame.setLayout(layout);

      Object result = frame.getText(ds, "name", 0);

      assertInstanceOf(CompositeLabel.class, result);
      TextSegment[] segments = ((CompositeLabel) result).getSegments();
      assertEquals(3, segments.length);
      assertEquals("John", segments[0].getText());
      assertEquals(":", segments[1].getText());
      assertEquals("Doe", segments[2].getText());
   }

   @Test
   void staticItem_vertical_separatedByNewlines() {
      DefaultDataSet ds = twoColumnDataset("Sales", 1000);
      LayoutTextFrame frame = new LayoutTextFrame("name", "value");

      TextLayout layout = new TextLayout();
      TextLayoutRow row0 = new TextLayoutRow();
      row0.addItem(TextLayoutItem.ofField(0));
      layout.addRow(row0);
      TextLayoutRow row1 = new TextLayoutRow();
      row1.addItem(TextLayoutItem.ofStatic("---"));
      layout.addRow(row1);
      TextLayoutRow row2 = new TextLayoutRow();
      row2.addItem(TextLayoutItem.ofField(1));
      layout.addRow(row2);
      frame.setLayout(layout);

      Object result = frame.getText(ds, "name", 0);

      assertInstanceOf(CompositeLabel.class, result);
      TextSegment[] segments = ((CompositeLabel) result).getSegments();
      assertEquals(3, segments.length);
      assertEquals("Sales", segments[0].getText());
      assertEquals("---", segments[1].getText());
      assertEquals("1000", segments[2].getText());
   }

   @Test
   void staticItem_leadingLabel_prependedWithoutSeparator() {
      DefaultDataSet ds = twoColumnDataset("Revenue", 500);
      LayoutTextFrame frame = new LayoutTextFrame("name", "value");

      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      row.addItem(TextLayoutItem.ofStatic("Label:"));
      row.addItem(TextLayoutItem.ofField(0));
      layout.addRow(row);
      frame.setLayout(layout);

      Object result = frame.getText(ds, "name", 0);

      assertInstanceOf(CompositeLabel.class, result);
      TextSegment[] segments = ((CompositeLabel) result).getSegments();
      assertEquals(2, segments.length);
      assertEquals("Label:", segments[0].getText());
      assertEquals("Revenue", segments[1].getText());
   }

   // ---------------------------------------------------------------------------
   // 5. equals() contract
   // ---------------------------------------------------------------------------

   @Test
   void equals_sameLayout_returnsTrue() {
      TextLayout layout = horizontalLayout(1.0, 0, 1);

      LayoutTextFrame a = new LayoutTextFrame("x", "y");
      a.setLayout(layout);

      LayoutTextFrame b = new LayoutTextFrame("x", "y");
      b.setLayout(layout);

      assertEquals(a, b);
   }

   @Test
   void equals_differentLayout_returnsFalse() {
      LayoutTextFrame a = new LayoutTextFrame("x", "y");
      a.setLayout(horizontalLayout(1.0, 0, 1));

      LayoutTextFrame b = new LayoutTextFrame("x", "y");
      b.setLayout(verticalLayout(0, 1));

      assertNotEquals(a, b);
   }

   @Test
   void equals_nullLayouts_returnsTrue() {
      LayoutTextFrame a = new LayoutTextFrame("x");
      LayoutTextFrame b = new LayoutTextFrame("x");

      assertEquals(a, b);
   }

   // ---------------------------------------------------------------------------
   // 6. Per-field formatting via setFieldFormats
   // ---------------------------------------------------------------------------

   @Test
   void setFieldFormats_firstFieldFormatted_outputContainsFormattedValue() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "qty",  "label"   },
         { 1234,   "Revenue" },
      });

      LayoutTextFrame frame = new LayoutTextFrame("qty", "label");

      // fieldFormats[0] applies to fields[0] = "qty"
      Format qtyFormat = new DecimalFormat("###,###");
      frame.setFieldFormats(new Format[] { qtyFormat, null });

      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      row.addItem(TextLayoutItem.ofField(0));
      row.addItem(TextLayoutItem.ofSpacing(1.0));
      row.addItem(TextLayoutItem.ofField(1));
      layout.addRow(row);
      frame.setLayout(layout);

      Object result = frame.getText(ds, "qty", 0);

      assertInstanceOf(CompositeLabel.class, result);
      TextSegment[] segments = ((CompositeLabel) result).getSegments();
      assertEquals(3, segments.length);
      assertEquals("1,234", segments[0].getText());
      assertTrue(segments[1].isSpacing());
      assertEquals("Revenue", segments[2].getText());
   }
}

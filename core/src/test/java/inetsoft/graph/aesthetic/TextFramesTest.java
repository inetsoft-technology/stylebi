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

import inetsoft.graph.TextSpec;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Format;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text frames: the default frame (value to alias), the visibility rules of the base class,
 * the multi field, value and current text frames. Number formats use US symbols.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class TextFramesTest {
   @AfterEach
   void clearCurrentField() {
      CurrentTextFrame.setCurrentField(null);
   }

   private static DefaultDataSet data(Object[]... rows) {
      return new DefaultDataSet(rows);
   }

   private static DefaultDataSet people() {
      return data(new Object[] { "name", "age" },
                  new Object[] { "Alice", 30 },
                  new Object[] { "Bob", 25 });
   }

   private static Format decimal(String pattern) {
      return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
   }

   // ---- DefaultTextFrame ----

   @Test
   void defaultTextFrameWithoutMappingReturnsTheSameObject() {
      DefaultTextFrame frame = new DefaultTextFrame();
      Integer five = 5;

      assertSame("A", frame.getText("A"));
      assertSame(five, frame.getText(five));
      assertNull(frame.getText((Object) null));
   }

   @Test
   void defaultTextFrameMapsAssignedValues() {
      DefaultTextFrame frame = new DefaultTextFrame();
      frame.setText("A", "Alpha");

      assertEquals("Alpha", frame.getText("A"));
      assertEquals("B", frame.getText("B"));
   }

   @Test
   void defaultTextFrameFindsNumberKeysByTheirTextForm() {
      DefaultTextFrame frame = new DefaultTextFrame();
      frame.setText(1, "One");

      assertEquals("One", frame.getText(1));
      assertEquals("One", frame.getText("1"));
      assertEquals("One", frame.getText(1L));
   }

   @Test
   void defaultTextFrameFindsDateKeysByEqualDateAndByFormattedText() {
      Date date = new Date(1700000000000L);
      DefaultTextFrame frame = new DefaultTextFrame();
      frame.setText(date, "D");

      assertEquals("D", frame.getText(new Date(date.getTime())));
      assertEquals("D", frame.getText(GTool.toString(date)));
   }

   @Test
   void defaultTextFrameRemovesAMappingWithNull() {
      DefaultTextFrame frame = new DefaultTextFrame();
      frame.setText("A", "Alpha");
      frame.setText("A", null);

      assertEquals("A", frame.getText("A"));
      assertFalse(frame.isStatic("A"));
      assertTrue(frame.getKeys().isEmpty());
   }

   @Test
   void defaultTextFrameCloneIsIndependentAndClearStaticEmpties() {
      DefaultTextFrame frame = new DefaultTextFrame();
      frame.setText("A", "a");
      DefaultTextFrame copy = (DefaultTextFrame) frame.clone();
      copy.setText("A", "x");

      assertEquals("a", frame.getText("A"));
      assertEquals("x", copy.getText("A"));

      frame.clearStatic();

      assertTrue(frame.getKeys().isEmpty());
      assertFalse(frame.isScripted("A"));
      assertEquals("A", frame.getText("A"));
      assertEquals("x", copy.getText("A"));
   }

   @Test
   void defaultTextFramesWithTheSameMappingAreEqual() {
      DefaultTextFrame a = new DefaultTextFrame("v");
      DefaultTextFrame b = new DefaultTextFrame("v");
      a.setText("A", "Alpha");
      b.setText("A", "Alpha");

      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
   }

   // ---- TextFrame base behavior ----

   @Test
   void textComesFromTheFieldOrTheColumn() {
      DefaultDataSet data = people();

      assertEquals(30, new DefaultTextFrame().getText(data, "age", 0));
      assertEquals("Alice", new DefaultTextFrame("name").getText(data, "age", 0));
      assertNull(new DefaultTextFrame().getText(data, null, 0));
   }

   @Test
   void visibleValuesFilterTheRows() {
      DefaultDataSet data = data(new Object[] { "g", "v" },
                                 new Object[] { "A", "t0" },
                                 new Object[] { "B", "t1" },
                                 new Object[] { null, "t2" });
      DefaultTextFrame frame = new DefaultTextFrame("v");
      frame.setVisibleField("g");
      frame.setVisibleValues("A");

      assertEquals("t0", frame.getText(data, null, 0));
      assertNull(frame.getText(data, null, 1));
      assertNull(frame.getText(data, null, 2));
   }

   private static DefaultDataSet rangeData(Object... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { "r", "t" };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { values[i], "t" + i };
      }

      return data(rows);
   }

   private static DefaultTextFrame ranged(Comparable lo, Comparable hi) {
      DefaultTextFrame frame = new DefaultTextFrame("t");
      frame.setVisibleField("r");
      frame.setVisibleRange(lo, hi);
      return frame;
   }

   @Test
   void visibleRangeIsInclusiveOnBothEnds() {
      DefaultDataSet data = rangeData(9, 10, 15, 20, 21);
      DefaultTextFrame frame = ranged(10, 20);

      assertNull(frame.getText(data, null, 0));
      assertEquals("t1", frame.getText(data, null, 1));
      assertEquals("t2", frame.getText(data, null, 2));
      assertEquals("t3", frame.getText(data, null, 3));
      assertNull(frame.getText(data, null, 4));
   }

   @Test
   void visibleRangeWithAnOpenEnd() {
      DefaultDataSet data = rangeData(5, 25);
      DefaultTextFrame upper = ranged(null, 20);
      DefaultTextFrame lower = ranged(10, null);

      assertEquals("t0", upper.getText(data, null, 0));
      assertNull(upper.getText(data, null, 1));
      assertNull(lower.getText(data, null, 0));
      assertEquals("t1", lower.getText(data, null, 1));
   }

   @Test
   void visibleRangeComparesMixedNumberTypesAsDoubles() {
      DefaultDataSet data = rangeData(20.0, 15L, 9.5);
      DefaultTextFrame frame = ranged(10, 20);

      assertEquals("t0", frame.getText(data, null, 0));
      assertEquals("t1", frame.getText(data, null, 1));
      assertNull(frame.getText(data, null, 2));
   }

   @Test
   void visibleRangeHidesANullValue() {
      DefaultDataSet data = rangeData(12, null);

      assertEquals("t0", ranged(10, 20).getText(data, null, 0));
      assertNull(ranged(10, 20).getText(data, null, 1));
   }

   // ---- MultiTextFrame ----

   private static DefaultDataSet multiData(Object name, Object age) {
      return data(new Object[] { "name", "age" },
                  new Object[] { "Zed", 1 },
                  new Object[] { name, age });
   }

   @Test
   void multiTextJoinsTheFieldsWithTheDelimiter() {
      DefaultDataSet data = people();
      MultiTextFrame frame = new MultiTextFrame("name", "age");

      assertEquals("Alice,30", frame.getText(data, null, 0));

      frame.setDelimiter(" - ");
      assertEquals("Alice - 30", frame.getText(data, null, 0));
   }

   @Test
   void multiTextShowsNullPartsAsEmpty() {
      MultiTextFrame frame = new MultiTextFrame("name", "age");

      assertEquals("Alice,", frame.getText(multiData("Alice", null), null, 1));
      assertEquals(",", frame.getText(multiData(null, null), null, 1));
   }

   @Test
   void multiTextWithOneFieldReturnsTheValueItself() {
      // pinned behavior: one field without formats gives the value, not a string
      Integer thirty = 30;
      DefaultDataSet data = data(new Object[] { "age" },
                                 new Object[] { thirty },
                                 new Object[] { null });
      MultiTextFrame frame = new MultiTextFrame("age");

      assertSame(thirty, frame.getText(data, null, 0));
      assertNull(frame.getText(data, null, 1));
   }

   @Test
   void multiTextAppliesTheValueFormatToEachValue() {
      MultiTextFrame frame = new MultiTextFrame("name", "age");
      frame.setValueFormat(decimal("0.0"));

      assertEquals("Alice,30.0", frame.getText(people(), null, 0));
   }

   @Test
   void multiTextAppliesTheMessageFormat() {
      MultiTextFrame frame = new MultiTextFrame("name", "age");
      frame.setMessageFormat(new MessageFormat("{0} is {1,number,0}", Locale.US));

      assertEquals("Alice is 30", frame.getText(people(), null, 0));
      assertEquals("Alice is 1234", frame.getText(multiData("Alice", 1234), null, 1));
   }

   @Test
   void multiTextFallsBackToTheJoinWhenTheMessageFormatCannotFormatANull() {
      MultiTextFrame frame = new MultiTextFrame("name", "age");
      frame.setMessageFormat(new MessageFormat("{0} is {1,number,0}", Locale.US));

      assertEquals("Alice,", frame.getText(multiData("Alice", null), null, 1));
   }

   @Test
   void multiTextFieldsDefaultToTheColumnAndSetFieldReplacesTheFirst() {
      DefaultDataSet data = people();

      assertEquals(30, new MultiTextFrame().getText(data, "age", 0));

      MultiTextFrame frame = new MultiTextFrame("a", "b");
      frame.setField("z");

      assertArrayEquals(new String[] { "z", "b" }, frame.getFields());
      assertEquals("z", frame.getField());
   }

   @Test
   void multiTextFramesWithDifferentFieldsOrFormatsAreNotEqual() {
      MultiTextFrame ab = new MultiTextFrame("a", "b");
      MultiTextFrame ac = new MultiTextFrame("a", "c");

      assertEquals(new MultiTextFrame("a", "b"), ab);
      assertNotEquals(ab, ac);

      MultiTextFrame withFormat = new MultiTextFrame("a", "b");
      withFormat.setMessageFormat(new MessageFormat("{0}-{1}"));
      MultiTextFrame otherFormat = new MultiTextFrame("a", "b");
      otherFormat.setMessageFormat(new MessageFormat("{0}+{1}"));

      assertNotEquals(ab, withFormat);
      assertNotEquals(withFormat, otherFormat);
   }

   // ---- ValueTextFrame ----

   @Test
   void valueTextReturnsTheTextAndValuePair() {
      ValueTextFrame frame = new ValueTextFrame(new DefaultTextFrame("name"),
                                                new DefaultTextFrame("age"), null, null);

      assertArrayEquals(new Object[] { "Alice", 30 }, (Object[]) frame.getText(people(), null, 0));
   }

   @Test
   void valueTextOfEmptyPartsIsNullOrKeepsTheNullPart() {
      assertNull(new ValueTextFrame(null, null, null, null).getText(people(), null, 0));

      DefaultDataSet nulls = data(new Object[] { "name", "age" },
                                  new Object[] { null, 1 },
                                  new Object[] { null, null });
      ValueTextFrame both = new ValueTextFrame(new DefaultTextFrame("name"),
                                               new DefaultTextFrame("age"), null, null);

      assertNull(both.getText(nulls, null, 1));

      ValueTextFrame textOnly = new ValueTextFrame(new DefaultTextFrame("name"), null, null, null);

      assertArrayEquals(new Object[] { "Alice", null },
                        (Object[]) textOnly.getText(people(), null, 0));
   }

   @Test
   void valueTextCombinedFormatFillsTheEmptySecondSlotWithTheValueFormat() {
      ValueTextFrame frame = new ValueTextFrame(
         new DefaultTextFrame("name"), new DefaultTextFrame("age"),
         new MessageFormat("{0}: {1}", Locale.US), decimal("0.0"));

      assertEquals("Alice: 30.0", frame.getText(people(), null, 0));
   }

   @Test
   void valueTextFormatFillsOnlyAnEmptySecondSlot() {
      // first slot has its own format, second is empty: the value format fills the second
      ValueTextFrame firstFormatted = new ValueTextFrame(
         new DefaultTextFrame("age"), new DefaultTextFrame("age"),
         new MessageFormat("{0,number,0}: {1}", Locale.US), decimal("0.0"));

      assertEquals("30: 30.0", firstFormatted.getText(people(), null, 0));

      // second slot already has a format: the value format does not override it
      ValueTextFrame secondFormatted = new ValueTextFrame(
         new DefaultTextFrame("name"), new DefaultTextFrame("age"),
         new MessageFormat("{0}: {1,number,0}", Locale.US), decimal("0.0"));

      assertEquals("Alice: 30", secondFormatted.getText(people(), null, 0));
   }

   @Test
   void valueTextIsCombinedOnlyForAMessageFormatWithTwoArguments() {
      Format value = decimal("0.0");

      assertFalse(new ValueTextFrame(null, null, null, value).isTextCombined());
      assertFalse(new ValueTextFrame(null, null, new MessageFormat("{0}"), value)
                     .isTextCombined());
      assertTrue(new ValueTextFrame(null, null, new MessageFormat("{0} {1}"), value)
                    .isTextCombined());
   }

   @Test
   void valueTextFindsItsStackTextFrame() {
      StackTextFrame stack = new StackTextFrame();
      StackTextFrame other = new StackTextFrame();
      DefaultTextFrame plain = new DefaultTextFrame("v");

      assertSame(stack, new ValueTextFrame(stack, plain, null, null).getStackTextFrame());
      assertSame(stack, new ValueTextFrame(plain, stack, null, null).getStackTextFrame());
      assertNull(new ValueTextFrame(plain, new DefaultTextFrame("w"), null, null)
                    .getStackTextFrame());
      assertSame(stack, new ValueTextFrame(stack, other, null, null).getStackTextFrame());
   }

   @Test
   void valueTextAppliesFormatsToAClonedTextSpec() {
      Format textFormat = new MessageFormat("{0}: {1}", Locale.US);
      Format valueFormat = decimal("0.0");
      ValueTextFrame frame = new ValueTextFrame(null, null, textFormat, valueFormat);
      TextSpec original = new TextSpec();

      TextSpec text = frame.applyTextFormat(original);
      TextSpec value = frame.applyValueFormat(original);
      TextSpec combined = frame.applyCombinedFormat(original);

      assertSame(textFormat, text.getFormat());
      assertSame(valueFormat, value.getFormat());
      assertTrue(combined.getFormat() instanceof MessageFormat);
      Format[] parts = ((MessageFormat) combined.getFormat()).getFormats();
      assertSame(textFormat, parts[0]);
      assertSame(valueFormat, parts[1]);

      assertNotSame(original, text);
      assertNotSame(original, value);
      assertNotSame(original, combined);
      assertNull(original.getFormat());
   }

   // ---- CurrentTextFrame ----

   private static DefaultDataSet tree() {
      return data(new Object[] { "a", "b", "c", "name", "age" },
                  new Object[] { 3, "x", "y", "Alice", 30 });
   }

   @Test
   void currentTextWithoutCurrentFieldUsesTheColumn() {
      assertEquals(30, new CurrentTextFrame().getText(tree(), "age", 0));
   }

   @Test
   void currentTextUsesTheCurrentFieldWhateverTheColumnIs() {
      CurrentTextFrame frame = new CurrentTextFrame();
      CurrentTextFrame.setCurrentField("name");

      assertEquals("Alice", frame.getText(tree(), "age", 0));

      CurrentTextFrame.setCurrentField(null);

      assertEquals(30, frame.getText(tree(), "age", 0));
   }

   @Test
   void currentTextWithParentsStopsAtTheCurrentDimension() {
      CurrentTextFrame frame = new CurrentTextFrame(List.of("a", "b", "c"));
      frame.setIncludeParents(true);
      CurrentTextFrame.setCurrentField("b");

      assertArrayEquals(new Object[] { 3, "x" }, (Object[]) frame.getText(tree(), "age", 0));
   }

   @Test
   void currentTextWithParentsFormatsEachValueUnlessTheCurrentFieldHasAMessageFormat() {
      CurrentTextFrame frame = new CurrentTextFrame(List.of("a", "b"));
      frame.setIncludeParents(true);
      frame.setFormat("a", decimal("0.0"));
      CurrentTextFrame.setCurrentField("b");

      assertArrayEquals(new Object[] { "3.0", "x" }, (Object[]) frame.getText(tree(), "age", 0));

      frame.setFormat("b", new MessageFormat("{0}", Locale.US));

      assertArrayEquals(new Object[] { 3, "x" }, (Object[]) frame.getText(tree(), "age", 0));
   }

   @Test
   void currentFieldIsPerThread() throws Exception {
      CurrentTextFrame frame = new CurrentTextFrame();
      DefaultDataSet data = tree();
      CurrentTextFrame.setCurrentField("name");
      AtomicReference<Object> other = new AtomicReference<>();
      Thread thread = new Thread(() -> other.set(frame.getText(data, "age", 0)));
      thread.start();
      thread.join(10000);

      assertEquals(30, other.get());
      assertEquals("Alice", frame.getText(data, "age", 0));
   }
}

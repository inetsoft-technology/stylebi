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
package inetsoft.graph.guide.form;

import inetsoft.report.filter.AverageFormula;
import inetsoft.report.filter.MaxFormula;
import inetsoft.test.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Target lines at constant or formula positions: ordering of lines against labels, and the
 * label text for numbers and dates.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DynamicLineStrategyTest {
   // 2024-01-15T00:00:00Z and one day later
   private static final double JAN_15 = 1705276800000.0;
   private static final double JAN_16 = 1705363200000.0;

   private static DynamicLineStrategy constants(double... values) {
      TargetParameter[] params = new TargetParameter[values.length];

      for(int i = 0; i < values.length; i++) {
         params[i] = new TargetParameter(values[i]);
      }

      return new DynamicLineStrategy(params);
   }

   private static MessageFormat[] formats(String... patterns) {
      MessageFormat[] formats = new MessageFormat[patterns.length];

      for(int i = 0; i < patterns.length; i++) {
         formats[i] = new MessageFormat(patterns[i]);
      }

      return formats;
   }

   private static String[] labels(DynamicLineStrategy strategy, double[] data, MessageFormat[] fmts,
                                  Format valueFormat, boolean date, boolean time)
   {
      double[] b = strategy.calculateBoundaries(data);
      return strategy.generateLabels(b, fmts, null, valueFormat, date, time);
   }

   private static String[] defaultLabels(DynamicLineStrategy strategy, double... data) {
      return labels(strategy, data, null, null, false, false);
   }

   private static SimpleDateFormat utcFormat(String pattern) {
      SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
      format.setTimeZone(TimeZone.getTimeZone("UTC"));
      return format;
   }

   private static String label(DynamicLineStrategy strategy, double[] data, String pattern,
                               Format valueFormat, boolean date, boolean time)
   {
      return labels(strategy, data, formats(pattern), valueFormat, date, time)[0];
   }

   // ---- ordering ----

   @Test
   @DisplayName("constant lines are sorted ascending and default labels show each constant")
   void constantLinesSorted() {
      DynamicLineStrategy strategy = constants(30, 10, 20);
      assertArrayEquals(new double[] { 10, 20, 30 }, strategy.calculateBoundaries(new double[0]));
      assertArrayEquals(new String[] { "10.0", "20.0", "30.0" }, defaultLabels(strategy));
   }

   @Test
   @DisplayName("equal lines keep their original order so each label format stays with its line")
   void equalLinesAreStable() {
      DynamicLineStrategy strategy = constants(5, 5, 1);
      String[] labels = labels(strategy, new double[0], formats("a", "b", "c"), null, false, false);
      assertArrayEquals(new double[] { 1, 5, 5 }, strategy.calculateBoundaries(new double[0]));
      assertArrayEquals(new String[] { "c", "a", "b" }, labels);
   }

   @Test
   @DisplayName("label formats are assigned to lines by original position, cycling")
   void labelFormatsFollowOriginalPosition() {
      DynamicLineStrategy strategy = constants(30, 10, 20);
      String[] labels = labels(strategy, new double[0], formats("L0 {0}", "L1 {0}"), null,
                               false, false);
      assertArrayEquals(new String[] { "L1 10", "L0 20", "L0 30" }, labels);
   }

   @Test
   @DisplayName("a NaN line sorts last")
   void nanLineSortsLast() {
      DynamicLineStrategy strategy = constants(Double.NaN, 1);
      double[] b = strategy.calculateBoundaries(new double[0]);
      assertEquals(1.0, b[0]);
      assertTrue(Double.isNaN(b[1]));
      assertArrayEquals(new String[] { "1.0", "NaN" }, strategy.generateLabels(b, null, null, null,
                                                                               false, false));
   }

   @Test
   @DisplayName("a formula line and a constant line sort by value and keep their own label")
   void formulaLineAndConstant() {
      DynamicLineStrategy strategy = new DynamicLineStrategy(
         new TargetParameter(new AverageFormula()), new TargetParameter(1));
      double[] data = { 2, 4, 6 };
      assertArrayEquals(new double[] { 1, 4 }, strategy.calculateBoundaries(data));
      assertArrayEquals(new String[] { "1.0", new AverageFormula().getDisplayName() },
                        defaultLabels(strategy, data));
   }

   @Test
   @DisplayName("a reused instance recalculates from the new data and the original line order")
   void reusedInstance() {
      DynamicLineStrategy strategy = new DynamicLineStrategy(
         new TargetParameter(new AverageFormula()), new TargetParameter(1));
      assertArrayEquals(new double[] { 1, 4 }, strategy.calculateBoundaries(new double[] { 2, 4, 6 }));
      assertArrayEquals(new double[] { 1, 15 }, strategy.calculateBoundaries(new double[] { 10, 20 }));
   }

   @Test
   @DisplayName("no parameters or null parameters: no lines and no labels; size skips nulls")
   void emptyAndNullParameters() {
      DynamicLineStrategy empty = new DynamicLineStrategy();
      assertEquals(0, empty.calculateBoundaries(new double[] { 1 }).length);
      assertEquals(0, defaultLabels(empty, 1).length);

      DynamicLineStrategy withNulls = new DynamicLineStrategy(new TargetParameter[] {
         null, new TargetParameter(1), null });
      assertEquals(1, withNulls.getSize());
      withNulls.addParameters(null, new TargetParameter(2));
      assertEquals(2, withNulls.getSize());
      withNulls.setParameters(new TargetParameter[] { null });
      assertEquals(0, withNulls.getSize());
   }

   @Test
   @DisplayName("pinned current behavior: a line at the null placeholder gets no label")
   void placeholderLineHasNoLabel() {
      DynamicLineStrategy strategy = constants(-Double.MAX_VALUE);
      String[] labels = labels(strategy, new double[0], formats("{0}"), null, false, false);
      assertEquals(1, labels.length);
      assertNull(labels[0]);
   }

   @Test
   @DisplayName("a single negative constant line")
   void singleNegativeConstant() {
      DynamicLineStrategy strategy = constants(-5);
      assertArrayEquals(new double[] { -5 }, strategy.calculateBoundaries(new double[0]));
      assertArrayEquals(new String[] { "-5.0" }, defaultLabels(strategy));
   }

   @Test
   @DisplayName("extreme constants sort without overflow")
   void extremeConstants() {
      assertArrayEquals(new double[] { -1e308, 0, 1e308 },
                        constants(1e308, -1e308, 0).calculateBoundaries(new double[0]));
   }

   @Test
   @DisplayName("without label formats the labels are the default labels")
   void nullFormatsGiveDefaultLabels() {
      DynamicLineStrategy strategy = constants(30, 10, 20);
      double[] b = strategy.calculateBoundaries(new double[0]);
      assertArrayEquals(strategy.generateDefaultLabels(b, false, false),
                        strategy.generateLabels(b, null, "f", null, false, false));
   }

   // ---- date and time labels ----

   @Test
   @DisplayName("date line {0} uses the value format")
   void dateValueFormat() {
      assertEquals("2024-01-15", label(constants(JAN_15), new double[0], "{0}",
                                       utcFormat("yyyy-MM-dd"), true, false));
   }

   @Test
   @DisplayName("date constant line {1} shows the constant formatted with the value format")
   void dateConstantFormattedInDefaultArgument() {
      assertEquals("2024-01-15", label(constants(JAN_15), new double[0], "{1}",
                                       utcFormat("yyyy-MM-dd"), true, false));
   }

   @Test
   @DisplayName("date formula line {1} shows the formula name")
   void dateFormulaLine() {
      DynamicLineStrategy strategy = new DynamicLineStrategy(new TargetParameter(new MaxFormula()));
      assertEquals("2024-01-16 / Max",
                   label(strategy, new double[] { JAN_15, JAN_16 }, "{0} / {1}",
                         utcFormat("yyyy-MM-dd"), true, false));
   }

   private static String localDate(double epoch, String pattern) {
      return Instant.ofEpochMilli((long) epoch).atZone(ZoneId.systemDefault())
         .format(DateTimeFormatter.ofPattern(pattern, Locale.US));
   }

   @ParameterizedTest(name = "time target {0}")
   @ValueSource(booleans = { false, true })
   @DisplayName("label date pattern {0,date,...} is rendered in the default time zone and wins over"
      + " the value format and the time format")
   void labelDatePatternWins(boolean timeTarget) {
      String expected = localDate(JAN_15, "yyyy/MM/dd");

      if("UTC".equals(ZoneId.systemDefault().getId())) {
         assertEquals("2024/01/15", expected);
      }
      else if("America/Los_Angeles".equals(ZoneId.systemDefault().getId())) {
         assertEquals("2024/01/14", expected);
      }

      assertEquals(expected, label(constants(JAN_15), new double[0], "{0,date,yyyy/MM/dd}",
                                   utcFormat("yyyy-MM-dd"), true, timeTarget));
      assertEquals(expected, label(constants(JAN_15), new double[0], "{0,date,yyyy/MM/dd}",
                                   null, true, timeTarget));
   }

   private static double localTime() {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(1970, Calendar.JANUARY, 1, 13, 30, 0);
      return cal.getTimeInMillis();
   }

   @Test
   @DisplayName("time target without value format shows the default HH:mm:ss time")
   void timeTargetDefaultFormat() {
      assertEquals("13:30:00", label(constants(localTime()), new double[0], "{0}", null, true, true));
   }

   @Test
   @DisplayName("time target with a label time pattern uses that pattern, not the time format")
   void timeTargetLabelPattern() {
      assertEquals("13:30", label(constants(localTime()), new double[0], "{0,date,HH:mm}", null,
                                  true, true));
   }

   @Test
   @DisplayName("time target with value format hh:mm a shows 01:30 PM")
   void timeTargetValueFormat() {
      assertEquals("01:30 PM", label(constants(localTime()), new double[0], "{0}",
                                     new SimpleDateFormat("hh:mm a", Locale.US), true, true));
   }

   @Test
   @DisplayName("a number format on a date line does not fail, shows the plain date and tells the user")
   void wrongValueFormatDoesNotFail() {
      Tool.clearUserMessage();
      Date date = new Date((long) JAN_15);
      String label = assertDoesNotThrow(() -> label(constants(JAN_15), new double[0], "{0}",
                                                    new DecimalFormat("#,##0"), true, false));
      assertEquals(CoreTool.toString(date), label);
      assertNotNull(Tool.getUserMessage());
      Tool.clearUserMessage();
   }

   @Test
   @DisplayName("date lines in reverse order: each label uses the format of its own line")
   void dateLinesKeepTheirFormats() {
      DynamicLineStrategy strategy = constants(JAN_16, JAN_15);
      String[] labels = labels(strategy, new double[0], formats("A {0}", "B {0}"),
                               utcFormat("yyyy-MM-dd"), true, false);
      assertArrayEquals(new String[] { "B 2024-01-15", "A 2024-01-16" }, labels);
   }

   @Test
   @DisplayName("pinned current behavior: date line without any format shows the raw epoch")
   void dateLineWithoutFormatsShowsEpoch() {
      DynamicLineStrategy strategy = constants(JAN_15);
      String[] labels = labels(strategy, new double[0], null, null, true, false);
      assertArrayEquals(new String[] { "1.7052768E12" }, labels);
   }

   // ---- number labels ----

   private static boolean defaultLanguageIs(String language) {
      return Locale.getDefault(Locale.Category.FORMAT).getLanguage().equals(language);
   }

   @Test
   @DisplayName("number line {0} follows the default locale grouping and decimal separator")
   void numberLabelDefaultLocale() {
      String actual = label(constants(1234.5), new double[0], "{0}", null, false, false);

      if(defaultLanguageIs("en")) {
         assertEquals("1,234.5", actual);
      }
      else if(defaultLanguageIs("de")) {
         assertEquals("1.234,5", actual);
      }
      else {
         Assumptions.abort("no expectation for locale " + Locale.getDefault());
      }
   }

   @Test
   @DisplayName("number line with value format #,##0.00 follows the default locale symbols")
   void numberLabelValueFormat() {
      String actual = label(constants(1234.5), new double[0], "{0}", new DecimalFormat("#,##0.00"),
                            false, false);

      if(defaultLanguageIs("en")) {
         assertEquals("1,234.50", actual);
      }
      else if(defaultLanguageIs("de")) {
         assertEquals("1.234,50", actual);
      }
      else {
         Assumptions.abort("no expectation for locale " + Locale.getDefault());
      }
   }

   @Test
   @DisplayName("{2} is the field name, empty when no field name is given")
   void fieldNameArgument() {
      DynamicLineStrategy strategy = constants(1);
      double[] b = strategy.calculateBoundaries(new double[0]);
      assertEquals("", strategy.generateLabels(b, formats("{2}"), null, null, false, false)[0]);
      assertEquals("Sales", strategy.generateLabels(b, formats("{2}"), "Sales", null, false,
                                                    false)[0]);
   }
}

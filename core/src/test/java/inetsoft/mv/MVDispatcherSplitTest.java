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
package inetsoft.mv;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How a multi-threaded MV creation splits the data between its dispatchers
 * ({@code MVDispatcher.processDispatch}): {@code mv.dispatcher.count} dispatchers, each reading
 * a range of the split column. A number column is cut into equal ranges below
 * {@code mv.dispatcher.number.maximum}, a date column into ranges of
 * {@code mv.dispatcher.interval} months back from now. The first dispatcher has no upper bound
 * and reads the null rows, the last has no lower bound (a number) or reaches back to 1970 (a
 * date).
 *
 * <p>The MV has no worksheet, so the dispatchers fail once they are started; the ranges are read
 * from the variables each dispatcher was given.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVDispatcherSplitTest {
   private static final String[] PROPERTIES = {
      "mv.dispatcher.count", "mv.dispatcher.number.maximum", "mv.dispatcher.interval" };

   @BeforeEach
   void setUp() {
      for(String name : PROPERTIES) {
         saved.put(name, SreeEnv.getProperty(name));
         SreeEnv.remove(name);
      }
   }

   @AfterEach
   void tearDown() {
      for(Map.Entry<String, String> entry : saved.entrySet()) {
         if(entry.getValue() == null) {
            SreeEnv.remove(entry.getKey());
         }
         else {
            SreeEnv.setProperty(entry.getKey(), entry.getValue());
         }
      }
   }

   @Test
   void numberColumnIsCutIntoEqualRangesBelowTheMaximum() throws Exception {
      SreeEnv.setProperty("mv.dispatcher.count", "4");
      SreeEnv.setProperty("mv.dispatcher.number.maximum", "1000");

      List<Range> ranges = dispatch(false);

      assertEquals(4, ranges.size());
      assertRange(ranges.get(0), true, 750, null);
      assertRange(ranges.get(1), false, 500, 750);
      assertRange(ranges.get(2), false, 250, 500);
      assertRange(ranges.get(3), false, null, 250);
   }

   @Test
   void anotherCountAndMaximum() throws Exception {
      SreeEnv.setProperty("mv.dispatcher.count", "2");
      SreeEnv.setProperty("mv.dispatcher.number.maximum", "400");

      List<Range> ranges = dispatch(false);

      assertEquals(2, ranges.size());
      assertRange(ranges.get(0), true, 200, null);
      assertRange(ranges.get(1), false, null, 200);
   }

   @Test
   void theDefaultMaximumIsOneMillion() throws Exception {
      SreeEnv.setProperty("mv.dispatcher.count", "2");

      List<Range> ranges = dispatch(false);

      assertRange(ranges.get(0), true, 500000, null);
      assertRange(ranges.get(1), false, null, 500000);
   }

   @Test
   void theDefaultCountIsTheNumberOfCores() throws Exception {
      assertEquals(Tool.getAvailableCPUCores(), dispatch(false).size());
   }

   @Test
   void aCountThatIsNotANumberKeepsTheDefault() throws Exception {
      SreeEnv.setProperty("mv.dispatcher.count", "many");
      assertEquals(Tool.getAvailableCPUCores(), dispatch(false).size());
   }

   @Test
   void dateColumnIsCutIntoRangesOfTheInterval() throws Exception {
      SreeEnv.setProperty("mv.dispatcher.count", "3");
      SreeEnv.setProperty("mv.dispatcher.interval", "2");
      long before = System.currentTimeMillis();

      List<Range> ranges = dispatch(true);

      assertEquals(3, ranges.size());
      Range first = ranges.get(0);
      Range second = ranges.get(1);
      Range last = ranges.get(2);
      assertTrue(first.first);
      assertFalse(second.first);
      assertFalse(last.first);

      // the first range reaches 100 years into the future (not the largest date, which some
      // databases reject) and starts two months back
      Date now = new Date(before);
      assertTime(monthsBefore(now, -100 * 12), first.end, 60000);
      assertTime(monthsBefore(now, 2), first.start, 60000);
      // each range ends where the next one starts, two months apart
      assertEquals(first.start, second.end);
      assertTime(monthsBefore((Date) second.end, 2), second.start, 0);
      assertEquals(second.start, last.end);
      // the last range reaches back to 1970
      Calendar calendar = Calendar.getInstance();
      calendar.setTime((Date) last.start);
      assertTrue(calendar.get(Calendar.YEAR) <= 1970 && calendar.get(Calendar.YEAR) >= 1969,
                 "last range starts at " + last.start);
   }

   @Test
   void theDefaultIntervalSharesTwelveMonths() throws Exception {
      SreeEnv.setProperty("mv.dispatcher.count", "3");

      List<Range> ranges = dispatch(true);

      // 12 months over 3 dispatchers: 4 months each
      assertTime(monthsBefore((Date) ranges.get(1).end, 4), ranges.get(1).start, 0);
   }

   @Test
   void theDefaultIntervalIsAtLeastOneMonth() throws Exception {
      SreeEnv.setProperty("mv.dispatcher.count", "20");

      List<Range> ranges = dispatch(true);

      assertTime(monthsBefore((Date) ranges.get(1).end, 1), ranges.get(1).start, 0);
   }

   private static void assertRange(Range range, boolean first, Integer start, Integer end) {
      assertEquals(first, range.first, "reads the null rows");
      assertEquals(start, range.start, "start");
      assertEquals(end, range.end, "end");
   }

   /** The same time, give or take {@code delta} ms. */
   private static void assertTime(Date expected, Object actual, long delta) {
      assertTrue(actual instanceof Date, "not a date: " + actual);
      long diff = Math.abs(expected.getTime() - ((Date) actual).getTime());
      assertTrue(diff <= delta, "expected " + expected + " but was " + actual);
   }

   /** The variables of one dispatcher: the range of the split column it reads. */
   private static final class Range {
      boolean first;
      Object start;
      Object end;
   }

   /**
    * Runs the split of the multi-threaded creation of an MV with no data behind it and reads the
    * range of each dispatcher it started.
    */
   private static List<Range> dispatch(boolean date) throws Exception {
      MVDispatcher dispatcher = new MVDispatcher(new MVDef());
      Field isDate = MVDispatcher.class.getDeclaredField("isDate");
      isDate.setAccessible(true);
      isDate.setBoolean(dispatcher, date);
      Field isDateTime = MVDispatcher.class.getDeclaredField("isDateTime");
      isDateTime.setAccessible(true);
      isDateTime.setBoolean(dispatcher, date);
      Method method = MVDispatcher.class.getDeclaredMethod("processDispatch", boolean.class);
      method.setAccessible(true);

      try {
         method.invoke(dispatcher, true);
      }
      catch(InvocationTargetException expected) {
         // the dispatchers have no worksheet to query, they fail after the split is made
      }

      Field field = MVDispatcher.class.getDeclaredField("dispatchers");
      field.setAccessible(true);
      MVDispatcher[] dispatchers = (MVDispatcher[]) field.get(dispatcher);
      List<Range> result = new ArrayList<>();

      for(MVDispatcher block : dispatchers) {
         Field vars = block.getClass().getDeclaredField("vars");
         vars.setAccessible(true);
         VariableTable table = (VariableTable) vars.get(block);
         Range range = new Range();
         range.first = table.contains("__FIRST__");
         range.start = value(table, "__START__");
         range.end = value(table, "__END__");
         result.add(range);
      }

      return result;
   }

   private static Object value(VariableTable table, String name) throws Exception {
      Object value = table.contains(name) ? table.get(name) : null;
      return value instanceof Number ? (Object) ((Number) value).intValue() : value;
   }

   private static Date monthsBefore(Date end, int months) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(end);
      calendar.add(Calendar.MONTH, -months);
      return calendar.getTime();
   }

   private final Map<String, String> saved = new HashMap<>();
}

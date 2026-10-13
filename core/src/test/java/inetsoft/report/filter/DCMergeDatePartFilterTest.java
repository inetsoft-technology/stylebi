/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.filter;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.TableLens;
import inetsoft.report.lens.DataSetTable;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.VSFieldValue;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static inetsoft.test.XTableUtil.date;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class DCMergeDatePartFilterTest {
   @Test
   public void testSerialize() throws Exception {
      DataSet dataSet = new DefaultDataSet(new Object[][]{
         { "col1", "col2", "col3" },
         { "a", date("2021-01-03"), 3 },
         { "a", date("2021-01-05"), 5 },
         { "b", date("2021-01-10"), 10 },
         { "b", date("2021-01-24"), 24 },
         { "c", date("2021-01-24"), 24 },
         });
      DataSetTable base = new DataSetTable(dataSet);
      VSDimensionRef col2Ref = new VSDimensionRef();
      col2Ref.setDataRef(new AttributeRef("col2"));
      VSDimensionRef col3Ref = new VSDimensionRef();
      col3Ref.setDataRef(new AttributeRef("col3"));
      DCMergeDatePartFilter originalTable = new DCMergeDatePartFilter(base, Collections.singletonList(col2Ref),
                                                                      col3Ref, null, null);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DCMergeDatePartFilter.class, deserializedTable.getClass());
   }

   // Bug #75351: for a WeekOfYear date-comparison part cell whose displayed week
   // maps to a different actual week across the period's year, VSDataSet must use
   // the equivalence cell so the drill-to-detail condition targets the same week
   // the axis/data represent (matching the crosstab path).
   @Test
   public void testWeekOfYearEquivalenceFieldValue() {
      // Cover many month/week combinations so at least one part value diverges
      // from its equivalence value (week 5/6 rolling into the next month under
      // minimalDaysInFirstWeek=7).
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[]{ "WeekOfYear(date)", "date" });

      for(int year = 2019; year <= 2022; year++) {
         for(int month = 1; month <= 12; month++) {
            for(int week = 4; week <= 6; week++) {
               String mm = month < 10 ? "0" + month : Integer.toString(month);
               rows.add(new Object[]{ month * 10 + week, date(year + "-" + mm + "-15") });
            }
         }
      }

      DataSet dataSet = new DefaultDataSet(rows.toArray(new Object[0][]));
      DataSetTable base = new DataSetTable(dataSet);

      VSDimensionRef partRef = new VSDimensionRef();
      partRef.setDataRef(new AttributeRef("WeekOfYear(date)"));
      VSDimensionRef dateGroupRef = new VSDimensionRef();
      dateGroupRef.setDataRef(new AttributeRef("date"));

      List<XDimensionRef> noExtraRefs = new ArrayList<>();
      DCMergeDatePartFilter filter =
         new DCMergeDatePartFilter(base, noExtraRefs, partRef, dateGroupRef, null);

      // Locate the first row whose part cell has a diverging equivalence cell.
      int divergingRow = -1;       // VSDataSet row index (header excluded)
      String expected = null;      // field value rendered from the equivalence cell
      String original = null;      // field value rendered from the original cell

      for(int r = base.getHeaderRowCount(); r < base.getRowCount(); r++) {
         Object cell = filter.getObject(r, 0);

         if(cell instanceof DCMergeDatePartFilter.MergePartCell) {
            DCMergeDatePartFilter.MergePartCell mpc = (DCMergeDatePartFilter.MergePartCell) cell;
            DCMergeDatePartFilter.MergePartCell equivalenceCell = mpc.getEquivalenceCell();

            if(equivalenceCell != null) {
               divergingRow = r - base.getHeaderRowCount();
               // Render the field value exactly as getFieldValue() would, so the
               // comparison is independent of the cell's string formatting.
               expected = new VSFieldValue("WeekOfYear(date)", equivalenceCell, true)
                  .getFieldValue().getValue();
               original = new VSFieldValue("WeekOfYear(date)", mpc, true)
                  .getFieldValue().getValue();
               break;
            }
         }
      }

      Assertions.assertTrue(divergingRow >= 0,
                            "expected at least one WeekOfYear row whose equivalence cell diverges");
      Assertions.assertNotEquals(original, expected,
                                 "equivalence cell should differ from the original part cell");

      VSDataSet vsDataSet = new VSDataSet(filter, new VSDataRef[]{ partRef, dateGroupRef });
      VSFieldValue[][] fieldValues = vsDataSet.getFieldValues(
         divergingRow, new String[]{ "WeekOfYear(date)" }, new String[]{ "WeekOfYear(date)" },
         false, false);

      String actual = null;

      for(VSFieldValue[] tuple : fieldValues) {
         for(VSFieldValue fv : tuple) {
            if("WeekOfYear(date)".equals(fv.getFieldName())) {
               actual = fv.getFieldValue().getValue();
            }
         }
      }

      Assertions.assertNotNull(actual, "field value for the WeekOfYear column should be present");
      Assertions.assertEquals(expected, actual,
                              "drill field value should use the equivalence week, not the displayed part value");
   }

   // Bug #75351: a merged WeekOfYear bucket carries one period's part value, which can map
   // to a different actual week in another period's year. getEquivalenceCell() must recompute
   // the part value from the cell's own date (matching JavaScriptEngine.datePart("wy")), not
   // from the stored part value. Deriving it from the stored value (the previous approach)
   // failed when the first day of week was not Sunday, leaving drill-to-detail one week off.
   @Test
   public void testWeekOfYearEquivalenceUsesCellDateNonSundayWeekStart() {
      WeekStartUtil.withWeekStart("monday", () -> {
         Assertions.assertEquals(Calendar.MONDAY, Tool.getFirstDayOfWeek(),
                                 "test requires a non-Sunday first day of week");

         // 2020-01-02 (Thu) belongs to the Dec 30 - Jan 5 week, i.e. December 2019's 5th
         // week, while the 2021 bar sharing its axis position falls in January's 1st week.
         Date cellDate = date("2020-01-02");
         // The bucket carries the comparison (other-year) period's week part value; here the
         // 2021 bar's week is shared with the 2020 bar at the same axis position.
         int storedPartValue = weekOfYearPart(date("2021-01-07"));
         int actualWeekPart = weekOfYearPart(cellDate);

         Assertions.assertNotEquals(actualWeekPart, storedPartValue,
            "scenario requires the stored week part to differ from the cell's actual week");

         DataSet dataSet = new DefaultDataSet(new Object[][]{
            { "WeekOfYear(date)", "date" },
            { storedPartValue, cellDate },
            });
         DataSetTable base = new DataSetTable(dataSet);

         VSDimensionRef partRef = new VSDimensionRef();
         partRef.setDataRef(new AttributeRef("WeekOfYear(date)"));
         VSDimensionRef dateGroupRef = new VSDimensionRef();
         dateGroupRef.setDataRef(new AttributeRef("date"));

         DCMergeDatePartFilter filter =
            new DCMergeDatePartFilter(base, new ArrayList<>(), partRef, dateGroupRef, null);

         Object cell = filter.getObject(base.getHeaderRowCount(), 0);
         Assertions.assertInstanceOf(DCMergeDatePartFilter.MergePartCell.class, cell);

         DCMergeDatePartFilter.MergePartCell mpc = (DCMergeDatePartFilter.MergePartCell) cell;
         Assertions.assertEquals(storedPartValue, ((Number) mpc.getValue(0)).intValue(),
                                 "cell should carry the stored (other-period) week part value");

         // The previous approach derived the equivalence value from the stored part value and,
         // for a non-Sunday week start, produced the stored value again -- so getEquivalenceCell
         // returned null and the drill stayed one week off. The fix derives it from the cell's
         // actual date, so a corrected cell is now returned with the cell's real week.
         DCMergeDatePartFilter.MergePartCell equivalenceCell = mpc.getEquivalenceCell();
         Assertions.assertNotNull(equivalenceCell,
            "equivalence cell expected when the stored week differs from the cell's actual week");
         Assertions.assertEquals(actualWeekPart, ((Number) equivalenceCell.getValue(0)).intValue(),
            "equivalence week must match the cell's actual date, not the stored part value");
      });
   }

   // Bug #75351: Same-Day comparison groups by the sequential week-of-year ('ww'), which already
   // aligns across periods, so getEquivalenceCell() must NOT remap it (the month*10 math would
   // corrupt a sequential value). A ref flagged dcSequentialWeek must skip the equivalence remap
   // even in the same non-Sunday boundary scenario where the legacy 'wy' encoding diverges.
   @Test
   public void testSequentialWeekSkipsEquivalence() {
      WeekStartUtil.withWeekStart("monday", () -> {
         Assertions.assertEquals(Calendar.MONDAY, Tool.getFirstDayOfWeek(),
                                 "test requires a non-Sunday first day of week");

         Date cellDate = date("2020-02-06");
         // A sequential week-of-year value (datePart 'ww'), already aligned across periods.
         int sequentialWeek = sequentialWeekOfYear(cellDate);

         DataSet dataSet = new DefaultDataSet(new Object[][]{
            { "WeekOfYear(date)", "date" },
            { sequentialWeek, cellDate },
            });
         DataSetTable base = new DataSetTable(dataSet);

         VSDimensionRef partRef = new VSDimensionRef();
         partRef.setDataRef(new AttributeRef("WeekOfYear(date)"));
         partRef.setDcSequentialWeek(true);
         VSDimensionRef dateGroupRef = new VSDimensionRef();
         dateGroupRef.setDataRef(new AttributeRef("date"));

         DCMergeDatePartFilter filter =
            new DCMergeDatePartFilter(base, new ArrayList<>(), partRef, dateGroupRef, null);

         Object cell = filter.getObject(base.getHeaderRowCount(), 0);
         Assertions.assertInstanceOf(DCMergeDatePartFilter.MergePartCell.class, cell);

         DCMergeDatePartFilter.MergePartCell mpc = (DCMergeDatePartFilter.MergePartCell) cell;
         Assertions.assertNull(mpc.getEquivalenceCell(),
            "sequential-week part must skip the equivalence remap (it is already aligned)");
      });
   }

   // Bug #77196: MergePartCell values reach crosstab LoadTableDataCommand.cellData, which is
   // forwarded to the websocket node as an object graph. A non-static inner cell dragged the
   // owning filter (this$0) and its whole table into that message. The cell must be a static
   // nested class with no outer-instance field.
   @Test
   public void testMergePartCellHasNoOuterInstance() {
      Class<?> cls = DCMergeDatePartFilter.MergePartCell.class;
      Assertions.assertTrue(Modifier.isStatic(cls.getModifiers()),
                            "MergePartCell must be a static nested class");
      Assertions.assertEquals("inetsoft.report.filter.DCMergeDatePartFilter$MergePartCell",
                              cls.getName(), "binary name must stay unchanged");

      for(Field field : cls.getDeclaredFields()) {
         Assertions.assertFalse(field.isSynthetic() || field.getName().startsWith("this$"),
                                "MergePartCell must not hold an outer instance: " + field);
      }
   }

   // Bug #77196: no TableLens/XTable (or the owning filter) may be reachable from a cell,
   // walking non-static, non-transient fields the way Ignite's BinaryMarshaller does.
   @Test
   public void testMergePartCellReachesNoTable() {
      MergePartCellFixture fx = new MergePartCellFixture();

      for(int r = fx.base.getHeaderRowCount(); r < fx.base.getRowCount(); r++) {
         Object cell = fx.filter.getObject(r, fx.partCol);
         Assertions.assertInstanceOf(DCMergeDatePartFilter.MergePartCell.class, cell);
         assertNoTableReachable(cell);

         DCMergeDatePartFilter.MergePartCell mpc = (DCMergeDatePartFilter.MergePartCell) cell;
         assertNoTableReachable(mpc.clone());
         assertNoTableReachable(mpc.copyCell(new Date()));
      }
   }

   // Bug #77196: the static cell must expose the same refs the filter was built with (shared
   // instances), for the cell itself, its clone/copy and its equivalence cell.
   @Test
   public void testMergePartCellGetterParity() {
      MergePartCellFixture fx = new MergePartCellFixture();
      Object cell = fx.filter.getObject(fx.base.getHeaderRowCount(), fx.partCol);
      Assertions.assertInstanceOf(DCMergeDatePartFilter.MergePartCell.class, cell);
      DCMergeDatePartFilter.MergePartCell mpc = (DCMergeDatePartFilter.MergePartCell) cell;

      assertRefParity(fx, mpc);
      assertRefParity(fx, mpc.clone());
      assertRefParity(fx, mpc.copyCell(mpc.getDateGroupValue()));
      Assertions.assertEquals("2021-" + mpc.getValue(1), mpc.toString());
      Assertions.assertEquals(fx.rawDate, mpc.getOriginalRawDate(),
                              "ignored dc temp ref should populate the original raw date");

      // Equivalence cell: find a WeekOfYear row whose equivalence value diverges.
      DCMergeDatePartFilter.MergePartCell equivalence = null;

      for(int r = fx.base.getHeaderRowCount(); r < fx.base.getRowCount() && equivalence == null;
          r++)
      {
         Object obj = fx.filter.getObject(r, fx.partCol);
         equivalence = ((DCMergeDatePartFilter.MergePartCell) obj).getEquivalenceCell();
      }

      Assertions.assertNotNull(equivalence, "expected at least one diverging equivalence cell");
      assertRefParity(fx, equivalence);
      assertNoTableReachable(equivalence);
   }

   private static void assertRefParity(MergePartCellFixture fx,
                                       DCMergeDatePartFilter.MergePartCell cell)
   {
      Assertions.assertSame(fx.partRef, cell.getPartRef());
      List<XDimensionRef> merged = cell.getMergedRefs();
      Assertions.assertEquals(2, merged.size(), "ignored dc temp ref must not be merged");
      Assertions.assertSame(fx.yearRef, merged.get(0));
      Assertions.assertSame(fx.partRef, merged.get(1));
   }

   private static void assertNoTableReachable(Object root) {
      Map<Object, Boolean> seen = new IdentityHashMap<>();
      List<Object> stack = new ArrayList<>();
      List<String> paths = new ArrayList<>();
      stack.add(root);
      paths.add(root.getClass().getSimpleName());

      while(!stack.isEmpty()) {
         Object obj = stack.remove(stack.size() - 1);
         String path = paths.remove(paths.size() - 1);

         if(obj == null || seen.put(obj, Boolean.TRUE) != null) {
            continue;
         }

         Assertions.assertFalse(obj instanceof XTable || obj instanceof TableLens ||
                                obj instanceof DCMergeDatePartFilter,
                                "table reachable from MergePartCell via " + path);
         Class<?> cls = obj.getClass();

         if(cls.isArray()) {
            if(!cls.getComponentType().isPrimitive()) {
               for(int i = 0; i < Array.getLength(obj); i++) {
                  stack.add(Array.get(obj, i));
                  paths.add(path + "[" + i + "]");
               }
            }

            continue;
         }

         if(obj instanceof Iterable<?> it && cls.getName().startsWith("java.")) {
            int i = 0;

            for(Object item : it) {
               stack.add(item);
               paths.add(path + "[" + i++ + "]");
            }

            continue;
         }

         if(obj instanceof Map<?, ?> map && cls.getName().startsWith("java.")) {
            for(Map.Entry<?, ?> e : map.entrySet()) {
               stack.add(e.getKey());
               paths.add(path + ".key");
               stack.add(e.getValue());
               paths.add(path + "[" + e.getKey() + "]");
            }

            continue;
         }

         if(cls.getName().startsWith("java.")) {
            continue; // JDK value types (String, Date, Number, ...)
         }

         for(Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for(Field field : c.getDeclaredFields()) {
               int mod = field.getModifiers();

               if(Modifier.isStatic(mod) || Modifier.isTransient(mod) ||
                  field.getType().isPrimitive())
               {
                  continue;
               }

               try {
                  field.setAccessible(true);
                  stack.add(field.get(obj));
                  paths.add(path + "." + field.getName());
               }
               catch(Exception ex) {
                  Assertions.fail("cannot inspect " + field + ": " + ex);
               }
            }
         }
      }
   }

   /**
    * A 2021 x WeekOfYear crosstab-shaped table: one visible dc extra ref (year), one
    * ignored dc temp ref (raw date), the part ref and the date group ref.
    */
   private static final class MergePartCellFixture {
      MergePartCellFixture() {
         List<Object[]> rows = new ArrayList<>();
         rows.add(new Object[]{ "Year(date)", "WeekOfYear(date)", "date", "raw" });
         rawDate = date("2021-01-01");

         for(int month = 1; month <= 12; month++) {
            for(int week = 4; week <= 6; week++) {
               String mm = month < 10 ? "0" + month : Integer.toString(month);
               rows.add(new Object[]{ 2021, month * 10 + week, date("2021-" + mm + "-15"),
                                      rawDate });
            }
         }

         base = new DataSetTable(new DefaultDataSet(rows.toArray(new Object[0][])));
         yearRef = new VSDimensionRef();
         yearRef.setDataRef(new AttributeRef("Year(date)"));
         VSDimensionRef rawRef = new VSDimensionRef();
         rawRef.setDataRef(new AttributeRef("raw"));
         rawRef.setIgnoreDcTemp(true);
         partRef = new VSDimensionRef();
         partRef.setDataRef(new AttributeRef("WeekOfYear(date)"));
         VSDimensionRef dateGroupRef = new VSDimensionRef();
         dateGroupRef.setDataRef(new AttributeRef("date"));
         List<XDimensionRef> extras = new ArrayList<>();
         extras.add(yearRef);
         extras.add(rawRef);
         filter = new DCMergeDatePartFilter(base, extras, partRef, dateGroupRef, null);
      }

      final DataSetTable base;
      final VSDimensionRef yearRef;
      final VSDimensionRef partRef;
      final DCMergeDatePartFilter filter;
      final Date rawDate;
      final int partCol = 1;
   }

   // Mirrors JavaScriptEngine.datePart("ww", date, true): the sequential week-of-year value
   // (read directly, with no week-start shift).
   private static int sequentialWeekOfYear(Date dt) {
      Calendar cal = new GregorianCalendar();
      cal.setFirstDayOfWeek(Tool.getFirstDayOfWeek());
      cal.setMinimalDaysInFirstWeek(7);
      cal.setTime(dt);
      return cal.get(Calendar.WEEK_OF_YEAR);
   }

   // Mirrors JavaScriptEngine.datePart("wy"): the week part value the data/query use.
   private static int weekOfYearPart(Date dt) {
      Calendar cal = new GregorianCalendar();
      cal.setFirstDayOfWeek(Tool.getFirstDayOfWeek());
      cal.setMinimalDaysInFirstWeek(7);
      cal.setTime(dt);
      DateComparisonUtil.moveToWeekStart(cal);
      return (cal.get(Calendar.MONTH) + 1) * 10 + cal.get(Calendar.WEEK_OF_MONTH);
   }
}

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

package inetsoft.report.composition.execution;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.SelectionVSAssembly;
import inetsoft.uql.viewsheet.internal.RangeCondition;
import inetsoft.uql.viewsheet.internal.SelectionSet;
import inetsoft.uql.xmla.MemberObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;

/**
 * A range slider on a cube must filter the other selection lists of the same hierarchy.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RealtimeCubeMetaDataTest {
   private static final String CAL = "[Date].[Calendar]";
   private static final String OTHER = "[Product].[Category]";

   private static MemberObject member(String uName, String hierarchy) throws Exception {
      MemberObject m = new MemberObject(uName);
      Field f = MemberObject.class.getDeclaredField("hierarchy");
      f.setAccessible(true);
      f.set(m, hierarchy);
      return m;
   }

   // Year, Month, Category: 2001 has 2 months, 2002 has 1 month
   private RealtimeCubeMetaData create() throws Exception {
      String[][] rows = { { "[Y].[2001]", "[M].[Jan]", "[C].[Bikes]" },
                          { "[Y].[2001]", "[M].[Feb]", "[C].[Bikes]" },
                          { "[Y].[2002]", "[M].[Mar]", "[C].[Parts]" } };
      DefaultTableLens table = new DefaultTableLens(rows.length + 1, 3);
      table.setObject(0, 0, "Year");
      table.setObject(0, 1, CAL + ".Month");
      table.setObject(0, 2, "Category");
      table.setHeaderRowCount(1);

      for(int r = 0; r < rows.length; r++) {
         table.setObject(r + 1, 0, member(rows[r][0], CAL));
         table.setObject(r + 1, 1, member(rows[r][1], CAL));
         table.setObject(r + 1, 2, member(rows[r][2], OTHER));
      }

      return new RealtimeCubeMetaData("cube", table, new String[] { "Year", "Month", "Category" },
                                      new ArrayList<>());
   }

   private static Object range(String id, String... cols) {
      ConditionList list = new ConditionList();
      DataRef ref = new AttributeRef(CAL, cols[0]);
      Condition gt = new Condition("string");
      gt.setOperation(XCondition.GREATER_THAN);
      gt.setEqual(true);
      gt.addValue("[Y].[2002]");
      Condition lt = new Condition("string");
      lt.setOperation(XCondition.LESS_THAN);
      lt.setEqual(true);
      lt.addValue("[Y].[2002]");
      list.append(new ConditionItem(ref, gt, 0));
      list.append(new JunctionOperator(JunctionOperator.AND, 0));
      list.append(new ConditionItem(ref, lt, 0));
      return RangeCondition.createRangeConditions(list, id).get(0);
   }

   private int monthCount(RealtimeCubeMetaData md, String key, Object cond) throws Exception {
      Map<String, Collection<Object>> selections = new HashMap<>();

      if(cond != null) {
         selections.put(key, new ArrayList<>(List.of(cond)));
      }

      SelectionSet set = md.getAssociatedValues(
         "Month", selections, new DataRef[] { new AttributeRef("Month") }, null,
         new SelectionMeasureAggregation(false));
      return set.size();
   }

   @Test
   void yearListSelectionFiltersMonthList() throws Exception {
      Assertions.assertEquals(1, monthCount(create(), "Year", new MemberObject("[Y].[2002]")));
   }

   @Test
   void singleRefRangeSliderFiltersMonthList() throws Exception {
      Assertions.assertEquals(
         1, monthCount(create(), SelectionVSAssembly.RANGE + "Year", range("s", "Year")));
   }

   @Test
   void compositeRangeSliderFiltersMonthList() throws Exception {
      Assertions.assertEquals(
         1, monthCount(create(), SelectionVSAssembly.RANGE + "Year^/^Month", range("c", "Year")));
   }

   @Test
   void rangeOnOtherHierarchyDoesNotFilterMonthList() throws Exception {
      Assertions.assertEquals(
         3, monthCount(create(), SelectionVSAssembly.RANGE + "Category", range("o", "Category")));
   }

   // ---- real-shaped members: uName + caption, months in chronological member order ----

   private static final String[] MONTHS = { "January", "February", "March", "April", "May", "June",
      "July", "August", "September", "October", "November", "December" };

   private static MemberObject captioned(String uName, String caption) throws Exception {
      MemberObject m = member(uName, CAL);
      Field f = MemberObject.class.getDeclaredField("caption");
      f.setAccessible(true);
      f.set(m, caption);
      return m;
   }

   // 38 months Jul 2001 .. Aug 2004
   private RealtimeCubeMetaData createReal() throws Exception {
      List<int[]> months = new ArrayList<>();

      for(int y = 2001; y <= 2004; y++) {
         for(int m = 1; m <= 12; m++) {
            if((y > 2001 || m >= 7) && (y < 2004 || m <= 8)) {
               months.add(new int[] { y, m });
            }
         }
      }

      DefaultTableLens table = new DefaultTableLens(months.size() + 1, 2);
      table.setObject(0, 0, CAL + ".Calendar Year");
      table.setObject(0, 1, CAL + ".Month");
      table.setHeaderRowCount(1);

      for(int r = 0; r < months.size(); r++) {
         int y = months.get(r)[0];
         int m = months.get(r)[1];
         table.setObject(r + 1, 0, captioned("[Date].[Calendar].[Calendar Year].&[" + y + "]", "CY " + y));
         table.setObject(r + 1, 1, captioned("[Date].[Calendar].[Month].&[" + y + "]&[" + m + "]",
                                          MONTHS[m - 1] + " " + y));
      }

      return new RealtimeCubeMetaData("cube", table, new String[] { CAL + ".Calendar Year", CAL + ".Month" },
                                      new ArrayList<>());
   }

   private static DataRef cubeRef(String name) {
      AttributeRef ref = new AttributeRef(CAL, name);
      ref.setRefType(DataRef.CUBE);
      return ref;
   }

   private static RangeCondition realRange(String[] names, Object[] mins, Object[] maxes)
      throws Exception
   {
      DataRef[] refs = new DataRef[names.length];

      for(int i = 0; i < refs.length; i++) {
         refs[i] = cubeRef(names[i]);
      }

      java.lang.reflect.Constructor<RangeCondition> c = RangeCondition.class.getDeclaredConstructor(
         Object[].class, Object[].class, DataRef[].class, boolean.class, boolean.class,
         boolean.class, String.class);
      c.setAccessible(true);
      return c.newInstance(mins, maxes, refs, true, true, false, "slider");
   }

   private Set<String> values(RealtimeCubeMetaData md, String key, RangeCondition cond, String col)
      throws Exception
   {
      Map<String, Collection<Object>> selections = new HashMap<>();
      selections.put(key, new ArrayList<>(List.of(cond)));
      SelectionSet set = md.getAssociatedValues(
         col, selections, new DataRef[] { cubeRef(col) }, null,
         new SelectionMeasureAggregation(false));
      Set<String> result = new TreeSet<>();

      for(Object obj : set) {
         Object v = obj instanceof SelectionSet.Tuple ? ((SelectionSet.Tuple) obj).get(0) : obj;
         result.add(((MemberObject) v).getCaption());
      }

      return result;
   }

   @Test
   void realCompositeRangeKeepsMonthsAndYearsInRange() throws Exception {
      RangeCondition cond = realRange(new String[] { CAL + ".Calendar Year", CAL + ".Month" },
                                      new Object[] { "CY 2002", "November 2002" },
                                      new Object[] { "CY 2003", "December 2003" });
      String key = SelectionVSAssembly.RANGE + "[Date].[Calendar].Calendar Year^/^[Date].[Calendar].Month";
      RealtimeCubeMetaData md = createReal();
      Set<String> months = values(md, key, cond, "Month");
      Assertions.assertEquals(14, months.size(), months.toString());
      Assertions.assertTrue(months.contains("November 2002"));
      Assertions.assertTrue(months.contains("December 2003"));
      Assertions.assertFalse(months.contains("October 2002"));
      Assertions.assertFalse(months.contains("January 2004"));
      Assertions.assertEquals(Set.of("CY 2002", "CY 2003"),
                              values(md, key, cond, "Calendar Year"));
   }

   @Test
   void realMonthOnlyRangeKeepsMonthsInRange() throws Exception {
      RangeCondition cond = realRange(new String[] { "Month" },
                                      new Object[] { "November 2002" },
                                      new Object[] { "December 2003" });
      String key = SelectionVSAssembly.RANGE + "[Date].[Calendar].Month";
      Assertions.assertEquals(14, values(createReal(), key, cond, "Month").size());
   }

   @Test
   void captionOrderDisagreeingWithChronologyUsesMemberOrder() throws Exception {
      // "March 2003" < "November 2002" as strings
      RangeCondition cond = realRange(new String[] { "Month" },
                                      new Object[] { "November 2002" },
                                      new Object[] { "March 2003" });
      String key = SelectionVSAssembly.RANGE + "[Date].[Calendar].Month";
      Set<String> months = values(createReal(), key, cond, "Month");
      Assertions.assertEquals(
         Set.of("November 2002", "December 2002", "January 2003", "February 2003", "March 2003"),
         months);
   }

   @Test
   void unresolvableBoundDoesNotApplyRange() throws Exception {
      RangeCondition cond = realRange(new String[] { "Month" },
                                      new Object[] { "Smarch 2002" },
                                      new Object[] { "December 2003" });
      String key = SelectionVSAssembly.RANGE + "[Date].[Calendar].Month";
      Assertions.assertEquals(38, values(createReal(), key, cond, "Month").size());
   }
}

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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.sql.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78123: {@code BaseTableShowDetailsService}'s own private {@code createTableConditions}
 * is a near-identical copy-paste of {@code TableConditionUtil.createTableConditions} (see
 * {@link inetsoft.uql.viewsheet.internal.TableConditionUtilWeekOfYearLocaleTest}), reached by
 * "Show Details" on a flat, worksheet-grouped {@code TableVSAssembly} (not a Crosstab - a
 * Crosstab's Show Details goes through {@code createCrosstabConditions} instead, which reuses
 * the cell's already-computed group value with no {@code Calendar} reconstruction at all). Its
 * {@code WEEK_OF_YEAR_DATE_GROUP} branch had the identical missing
 * {@code setMinimalDaysInFirstWeek}/{@code setFirstDayOfWeek} bug, fixed with the same
 * convention as bug #78112 (minimalDaysInFirstWeek forced to 1, firstDayOfWeek forced via
 * {@code Tool.getFirstDayOfWeek()}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BaseTableShowDetailsServiceWeekOfYearLocaleTest {
   /**
    * Same repro date as the {@code TableConditionUtil} test: 2025-12-28 is a Sunday that lands
    * in a different week of year depending on the JVM default locale's
    * {@code minimalDaysInFirstWeek}, unless forced to a fixed convention.
    */
   @Test
   void weekOfYearConditionIgnoresTheDefaultLocale() throws Exception {
      Locale old = Locale.getDefault();
      Date sunday = Date.valueOf("2025-12-28");

      try {
         int underEnUS = weekOfYearCondition(sunday, Locale.US);
         int underEnGB = weekOfYearCondition(sunday, Locale.UK);
         int underDeDE = weekOfYearCondition(sunday, Locale.GERMANY);
         int underZhCN = weekOfYearCondition(sunday, Locale.SIMPLIFIED_CHINESE);

         assertEquals(underEnUS, underEnGB,
                      "BaseTableShowDetailsService's week-of-year condition changed with the " +
                         "JVM default locale (en-GB)");
         assertEquals(underEnUS, underDeDE,
                      "BaseTableShowDetailsService's week-of-year condition changed with the " +
                         "JVM default locale (de-DE)");
         assertEquals(underEnUS, underZhCN,
                      "BaseTableShowDetailsService's week-of-year condition changed with the " +
                         "JVM default locale (zh-CN)");
      }
      finally {
         Locale.setDefault(old);
      }
   }

   /**
    * Matches #78112's convention: the week containing January 1st is week 1, under every
    * locale.
    */
   @Test
   void weekOfYearConditionJan1stIsWeekOneUnderAnyLocale() throws Exception {
      Locale old = Locale.getDefault();
      Date jan1 = Date.valueOf("2026-01-01");

      try {
         for(Locale locale : new Locale[] {
            Locale.US, Locale.UK, Locale.GERMANY, Locale.SIMPLIFIED_CHINESE })
         {
            assertEquals(1, weekOfYearCondition(jan1, locale),
                         "2026-01-01 should be week 1 under locale " + locale);
         }
      }
      finally {
         Locale.setDefault(old);
      }
   }

   /**
    * The fix also forces {@code firstDayOfWeek} via {@code Tool.getFirstDayOfWeek()} - see the
    * matching test and date-boundary reasoning in
    * {@link inetsoft.uql.viewsheet.internal.TableConditionUtilWeekOfYearLocaleTest
    * #weekOfYearConditionHonorsConfiguredWeekStart()}.
    */
   @Test
   void weekOfYearConditionHonorsConfiguredWeekStart() throws Exception {
      Date sunday = Date.valueOf("2026-01-04");

      int underSundayStart = WeekStartUtil.withWeekStart("sunday",
         () -> safeWeekOfYearCondition(sunday, Locale.US));
      int underMondayStart = WeekStartUtil.withWeekStart("monday",
         () -> safeWeekOfYearCondition(sunday, Locale.US));

      assertEquals(2, underSundayStart, "Sunday week start: 2026-01-04 should start week 2");
      assertEquals(1, underMondayStart, "Monday week start: 2026-01-04 should still be week 1");
   }

   private static int safeWeekOfYearCondition(Date date, Locale locale) {
      try {
         return weekOfYearCondition(date, locale);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   /**
    * Same fixture as {@code TableConditionUtilWeekOfYearLocaleTest}, but invokes {@code
    * BaseTableShowDetailsService}'s own private static {@code createTableConditions} via
    * reflection (no STOMP/sandbox bootstrap needed - the method only reaches the
    * {@code Worksheet}/{@code TableVSAssembly}/{@code TableLens} it is handed).
    */
   private static int weekOfYearCondition(Date date, Locale defaultLocale) throws Exception {
      Locale.setDefault(defaultLocale);

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "D");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.DATE }, new Object[][] { { "d" }, { date } }));
      ws.addAssembly(table);

      AggregateInfo ainfo = new AggregateInfo();
      GroupRef grp = new GroupRef(new AttributeRef(null, "d"));
      grp.setDateGroup(XConstants.WEEK_OF_YEAR_DATE_GROUP);
      ainfo.addGroup(grp);
      table.setAggregateInfo(ainfo);

      Viewsheet vs = new Viewsheet();
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, ws);

      TableVSAssembly tv = new TableVSAssembly(vs, "Table1");
      tv.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      ColumnSelection columns = new ColumnSelection();
      ColumnRef column = new ColumnRef(new AttributeRef(null, "d"));
      column.setDataType(XSchema.DATE);
      columns.addAttribute(column);
      tv.setColumnSelection(columns);
      vs.addAssembly(tv);

      TableLens lens = new DefaultTableLens(new Object[][] { { "d" }, { date } });

      Method create = BaseTableShowDetailsService.class.getDeclaredMethod(
         "createTableConditions", TableVSAssembly.class, int.class, int.class, TableLens.class,
         ConditionList.class);
      create.setAccessible(true);
      ConditionList result =
         (ConditionList) create.invoke(null, tv, 1, 0, lens, new ConditionList());

      return soleWeekOfYearValue(result);
   }

   /**
    * Pulls the single condition value out of the merged condition list, as an {@code int}. A
    * {@link ClassCastException} here (rather than a passing-but-meaningless comparison) means
    * the code fell through to a different branch than {@code WEEK_OF_YEAR_DATE_GROUP}.
    */
   private static int soleWeekOfYearValue(ConditionList conds) {
      for(int i = 0; i < conds.getSize(); i++) {
         Object item = conds.getItem(i);

         if(item instanceof ConditionItem citem) {
            Object value = citem.getCondition().getValue(0);
            assertInstanceOf(Number.class, value,
                             "condition value is not a week number: " + value);
            return ((Number) value).intValue();
         }
      }

      throw new AssertionError("no condition was built for the week-of-year group: " + conds);
   }
}

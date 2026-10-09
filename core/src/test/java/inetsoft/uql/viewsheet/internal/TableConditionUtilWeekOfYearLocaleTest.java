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
package inetsoft.uql.viewsheet.internal;

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
 * Bug #78123: {@code TableConditionUtil.createTableConditions}'s {@code WEEK_OF_YEAR_DATE_GROUP}
 * branch built a bare {@code new GregorianCalendar()} and read {@code Calendar.WEEK_OF_YEAR} off
 * it without ever forcing {@code minimalDaysInFirstWeek}/{@code firstDayOfWeek}, so the filter
 * condition built for the Flyover (hover tip) feature disagreed with the week number actually
 * shown in the UI whenever the server JVM's default locale used a {@code minimalDaysInFirstWeek}
 * other than 1 (en-GB/de-DE use 4; en-US/zh-CN use 1). This is the same convention bug #78112
 * fixed for {@code DateTimeProcessor}/{@code SortOrder}: force {@code minimalDaysInFirstWeek} to
 * 1 and {@code firstDayOfWeek} via {@code Tool.getFirstDayOfWeek()}, regardless of the JVM
 * default locale.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableConditionUtilWeekOfYearLocaleTest {
   /**
    * 2025-12-28 is a Sunday. Under {@code minimalDaysInFirstWeek=1} (en-US/zh-CN) it starts a
    * new week of year (week 1 of 2026); under {@code minimalDaysInFirstWeek=4} (en-GB/de-DE,
    * the pre-fix bug's symptom) it is still inside week 52 of 2025. The fix must make all four
    * locales agree.
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
                      "TableConditionUtil's week-of-year condition changed with the JVM " +
                         "default locale (en-GB)");
         assertEquals(underEnUS, underDeDE,
                      "TableConditionUtil's week-of-year condition changed with the JVM " +
                         "default locale (de-DE)");
         assertEquals(underEnUS, underZhCN,
                      "TableConditionUtil's week-of-year condition changed with the JVM " +
                         "default locale (zh-CN)");
      }
      finally {
         Locale.setDefault(old);
      }
   }

   /**
    * The reporter's own convention check (matching #78112's {@code CALC.weeknum}/
    * {@code DateTimeProcessor} convention): the week containing January 1st is week 1, under
    * every locale, not just the ones whose default {@code minimalDaysInFirstWeek} happens to
    * already be 1.
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
    * The fix also forces {@code firstDayOfWeek} via {@code Tool.getFirstDayOfWeek()} (the
    * product's locale-independent {@code week.start} setting), which the pre-fix code never
    * set at all. 2026-01-04 is a Sunday: with a Sunday week start it is the first day of week 2
    * of 2026 (week 1 = Dec 28-Jan 3); with a Monday week start it is the last day of week 1
    * (week 1 = Dec 29-Jan 4). A fix that only forced {@code minimalDaysInFirstWeek} and ignored
    * {@code week.start} would get this wrong.
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

   /** Wraps {@link #weekOfYearCondition} for use inside {@link WeekStartUtil}'s supplier. */
   private static int safeWeekOfYearCondition(Date date, Locale locale) {
      try {
         return weekOfYearCondition(date, locale);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   /**
    * Builds a flat {@code TableVSAssembly} bound to a worksheet table whose {@code
    * AggregateInfo} groups a DATE column {@code d} at {@code WEEK_OF_YEAR_DATE_GROUP}, then
    * calls {@code TableConditionUtil.createTableConditions} the same way {@code
    * BaseTableFlyoverService}/{@code VSUtil.sameCondition} do, and returns the single built
    * condition's {@code Calendar.WEEK_OF_YEAR} value.
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

      ConditionList result =
         TableConditionUtil.createTableConditions(tv, new ConditionList(), 1, 0, lens, false);

      return soleWeekOfYearValue(result);
   }

   /**
    * Pulls the single condition value out of the merged condition list, as an {@code int}. A
    * {@link ClassCastException} here (rather than a passing-but-meaningless comparison) means
    * the code fell through to a different branch than {@code WEEK_OF_YEAR_DATE_GROUP} - e.g.
    * one that stores the raw {@code java.sql.Date} instead of the computed week number.
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

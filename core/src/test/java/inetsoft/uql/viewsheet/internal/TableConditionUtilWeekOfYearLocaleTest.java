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
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.ThreadContext;
import inetsoft.util.script.graal.pool.PoolConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Date;
import java.security.Principal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78389: the conditions TableConditionUtil.createTableConditions builds for a flat table grouped by "Week of Year", fed
 * with real worksheet aggregation output (a {@code DateRangeRef WEEK_OF_YEAR_PART} column plus
 * {@code GroupRef}, as the Group and Aggregate dialog builds it). The group cell there is an
 * Integer week number, so the builder takes its plain EQUAL_TO path (the former date-part
 * branch, added by #78123 for Bug #78112's week numbering, was unreachable and was removed).
 * The week numbering itself is locale independent and is covered by
 * {@code DateRangeRefWeekOfYearLocaleTest}; this test checks it end to end through the builder.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  WeekOfYearGroupFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableConditionUtilWeekOfYearLocaleTest {
   private Principal oldPrincipal;

   @BeforeEach
   void setUp() {
      AssetDataCache.getCache().clearCache();
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      oldPrincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setPrincipal(oldPrincipal);
      SreeEnv.remove(PoolConfig.ENABLED);
      AssetDataCache.getCache().clearCache();
   }

   // 2025-12-28 is a Sunday; with minimalDaysInFirstWeek=1 and a Sunday week start it opens
   // week 1 of 2026 (under en-GB's own minimalDaysInFirstWeek=4 it would be week 52).
   private static final Date SUNDAY = Date.valueOf("2025-12-28");
   private static final Date SAME_WEEK = Date.valueOf("2025-12-30");
   private static final Date JAN_15 = Date.valueOf("2026-01-15");

   @Test
   void weekOfYearConditionSelectsOnlyThatWeekUnderEnGb() throws Exception {
      Locale old = Locale.getDefault();

      try {
         Locale.setDefault(Locale.UK);
         assertConditionsSelectOwnRowOnly(Locale.UK, "sunday");
      }
      finally {
         Locale.setDefault(old);
      }
   }

   @Test
   void weekOfYearConditionSelectsOnlyThatWeekUnderEnUs() throws Exception {
      Locale old = Locale.getDefault();

      try {
         Locale.setDefault(Locale.US);
         assertConditionsSelectOwnRowOnly(Locale.US, "sunday");
      }
      finally {
         Locale.setDefault(old);
      }
   }

   @Test
   void weekOfYearConditionHonorsConfiguredWeekStart() throws Exception {
      Locale old = Locale.getDefault();

      try {
         Locale.setDefault(Locale.US);
         // 2026-01-04 is a Sunday: week 2 with a Sunday week start, week 1 with a Monday one
         Date jan4 = Date.valueOf("2026-01-04");
         assertEquals(2, WeekStartUtil.withWeekStart("sunday", () -> weekValue(jan4)));
         assertEquals(1, WeekStartUtil.withWeekStart("monday", () -> weekValue(jan4)));
      }
      finally {
         Locale.setDefault(old);
      }
   }

   private void assertConditionsSelectOwnRowOnly(Locale locale, String weekStart)
      throws Exception
   {
      WeekOfYearGroupFixture fixture = WeekOfYearGroupFixture.create(SUNDAY, SAME_WEEK, JAN_15);
      TableLens lens = fixture.lens;
      int gcol = fixture.groupColumn();
      // 3 dates in 2 weeks: the worksheet really grouped by the week part
      assertEquals(2, fixture.dataRows(), "expected one row per week");
      assertEquals(Integer.class, lens.getObject(1, gcol).getClass(),
                   "the group cell must be the week number, not a Date");

      for(int row = 1; row <= fixture.dataRows(); row++) {
         ConditionList conds = create(fixture.table, lens, row, gcol);
         ConditionItem item = firstItem(conds);
         assertEquals(Condition.EQUAL_TO, item.getCondition().getOperation());
         assertEquals(lens.getObject(row, gcol), item.getCondition().getValue(0));

         for(int other = 1; other <= fixture.dataRows(); other++) {
            assertEquals(other == row, item.getCondition().evaluate(lens.getObject(other, gcol)),
                         "condition of row " + row + " vs row " + other + " under " + locale);
         }
      }

      assertEquals(1, lens.getObject(1, gcol), "2025-12-28 week under " + locale);
      assertEquals(3, lens.getObject(2, gcol), "2026-01-15 week under " + locale);
   }

   private int weekValue(Date date) {
      try {
         WeekOfYearGroupFixture fixture = WeekOfYearGroupFixture.create(date);
         ConditionItem item = firstItem(create(fixture.table, fixture.lens, 1, fixture.groupColumn()));
         return ((Number) item.getCondition().getValue(0)).intValue();
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private static ConditionItem firstItem(ConditionList conds) {
      for(int i = 0; i < conds.getSize(); i++) {
         if(conds.getItem(i) instanceof ConditionItem item) {
            return item;
         }
      }

      throw new AssertionError("no condition was built: " + conds);
   }

   private static ConditionList create(TableVSAssembly tv, TableLens lens, int row, int col)
      throws Exception
   {
      return TableConditionUtil.createTableConditions(tv, new ConditionList(), row, col, lens, false);
   }
}

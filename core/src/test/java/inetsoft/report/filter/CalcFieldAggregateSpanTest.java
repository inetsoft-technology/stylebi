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
package inetsoft.report.filter;

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.util.script.ScriptSpan;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.pool.PoolMetrics;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.SlotClaim;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76960, spec §14.3 (final review I1): a summary or crosstab aggregate of a calc field
 * runs one exec per group; in pool mode the whole aggregation must share one batch claim, so
 * it pays about one context clean, not one per group.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalcFieldAggregateSpanTest {
   @BeforeEach
   void setUp() {
      env = PoolTestSupport.env();
   }

   @AfterEach
   void tearDown() {
      env.retire();
      assertEquals(0, SlotClaim.openClaims());
   }

   @Test
   void summaryOfACalcFieldCleansOncePerAggregation() {
      SummaryFilter filter = new SummaryFilter(table(), new int[] { 0 }, new int[] { 1 },
         new Formula[] { calcField() }, null);
      filter.moreRows(TableLens.EOT);
      int values = 0;

      for(int r = filter.getHeaderRowCount(); filter.moreRows(r); r++) {
         if(filter.getObject(r, 1) != null) {
            values++;
         }
      }

      assertEquals(GROUPS, values);
      assertOneCleanForAllGroups();
   }

   @Test
   void crosstabOfACalcFieldCleansOncePerAggregation() {
      CrossTabFilter filter = new CrossTabFilter(table(), new int[] { 0 }, new int[0],
         new int[] { 1 }, new Formula[] { calcField() });
      filter.moreRows(TableLens.EOT);
      assertTrue(filter.getRowCount() > GROUPS);
      assertOneCleanForAllGroups();
   }

   @Test
   void aggregateSpanIsNoneWithoutAPooledEnv() {
      Formula[] formulas = { new SumFormula(), null };

      try(ScriptSpan span = CalcFieldFormula.openSpan(formulas, null)) {
         assertSame(ScriptSpan.NONE, span);
      }

      // the pool off: a plain env's span is NONE, so the aggregation runs as on main
      Formula[] plain = { new CalcFieldFormula("SUM", new String[] { "SUM" },
         new Formula[] { new SumFormula() }, new int[] { 1 }, new GraalJavaScriptEnv(),
         new PoolTestSupport.MapScope()) };

      try(ScriptSpan span = CalcFieldFormula.openSpan(plain)) {
         assertSame(ScriptSpan.NONE, span);
      }
   }

   private void assertOneCleanForAllGroups() {
      PoolMetrics metrics = env.getMetrics();
      assertTrue(metrics.getExecs() >= GROUPS, "execs " + metrics.getExecs());
      // one clean for the aggregation; a little slack for a grand total or a retry
      assertTrue(metrics.getCleans() <= 3,
                 "cleans " + metrics.getCleans() + " for " + metrics.getExecs() + " execs");
   }

   private CalcFieldFormula calcField() {
      return new CalcFieldFormula("SUM * 2", new String[] { "SUM" },
         new Formula[] { new SumFormula() }, new int[] { 1 }, env,
         new PoolTestSupport.MapScope());
   }

   private static TableLens table() {
      Object[][] data = new Object[GROUPS * 2 + 1][];
      data[0] = new Object[] { "g", "v" };

      for(int g = 0; g < GROUPS; g++) {
         data[g * 2 + 1] = new Object[] { g, 1.0 };
         data[g * 2 + 2] = new Object[] { g, 2.0 };
      }

      return new DefaultTableLens(data);
   }

   private static final int GROUPS = 50;
   private WorksheetScriptEnv env;
}

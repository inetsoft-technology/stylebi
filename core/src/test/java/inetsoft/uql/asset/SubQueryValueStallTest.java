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
package inetsoft.uql.asset;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.stall.LockStallException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A sub-query whose table stalls mid-read (bug #76967): the rows read so far are never cached
 * as the sub-query's values, so no evaluation matches against a partial list; the stall
 * reaches the caller, and once the table recovers the values are read again in full.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SubQueryValueStallTest {
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   public void stalledReadIsNeverCachedAsTheValues(boolean optimized) throws Exception {
      StallingTable sub = new StallingTable();
      AssetCondition condition = condition(sub, optimized);

      assertSame(sub.stall, assertThrows(LockStallException.class, () -> condition.evaluate(4)));
      // still stalled: a later evaluation fails again rather than match the rows read so far
      assertSame(sub.stall, assertThrows(LockStallException.class, () -> condition.evaluate(4)),
                 "a later evaluation must not use the partial values");
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   public void evaluationAfterTheStallReadsTheWholeSubQuery(boolean optimized)
      throws Exception
   {
      StallingTable sub = new StallingTable();
      AssetCondition condition = condition(sub, optimized);
      assertThrows(LockStallException.class, () -> condition.evaluate(4));

      sub.stalled = false;

      assertTrue(condition.evaluate(4), "4 is a value of the sub-query");
      assertTrue(condition.evaluate(1));
      assertFalse(condition.evaluate(9));
   }

   @Test
   public void nonStallFailureKeepsItsType() throws Exception {
      StallingTable sub = new StallingTable();
      sub.failure = new IllegalStateException("read failed");
      AssetCondition condition = condition(sub, false);

      assertSame(sub.failure, assertThrows(IllegalStateException.class,
                                           () -> condition.evaluate(4)));
   }

   private static AssetCondition condition(StallingTable sub, boolean optimized)
      throws Exception
   {
      SubQueryValue subQuery = new SubQueryValue();
      subQuery.setAttribute(new AttributeRef(null, "f1"));
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.ONE_OF);
      condition.setType(XSchema.INTEGER);
      condition.addValue(subQuery);
      condition.setOptimized(optimized);
      condition.init();
      condition.initSubTable(sub);
      condition.initMainTable(new DefaultTableLens(new Object[][] { { "v" }, { 1 } }), 0);
      return condition;
   }

   /**
    * Sub-query rows {@code f1 = 1..5}; while stalled, reading row 3 fails with a stall.
    */
   private static final class StallingTable extends DefaultTableLens {
      StallingTable() {
         super(new Object[][] { { "f1" }, { 1 }, { 2 }, { 3 }, { 4 }, { 5 } });
      }

      @Override
      public boolean moreRows(int row) {
         if(stalled && row >= 3) {
            throw failure != null ? failure : stall;
         }

         return super.moreRows(row);
      }

      final LockStallException stall =
         new LockStallException("nested.site", "worker", 1234, null);
      volatile boolean stalled = true;
      RuntimeException failure;
   }
}

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
package inetsoft.analytic.composition.event;

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.filter.CrossFilter;
import inetsoft.report.lens.DefaultTableLens;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #80146: a crosstab over a worksheet table cut by Max Rows has fewer rows than the limit,
 * so the warning must be decided from the capped source (the aggregation may be done by the
 * query on top of the limited table, the crosstab then only sees the aggregated rows).
 */
@Tag("core")
class VSEventUtilAggregateSourceCut80146Test {
   private static TableLens table(int dataRows) {
      DefaultTableLens table = new DefaultTableLens(dataRows + 1, 1);
      table.setHeaderRowCount(1);
      return table;
   }

   // a proxy, only getTable() is read
   @SuppressWarnings("unchecked")
   private static <T extends TableFilter> T filter(Class<T> type, TableLens base) {
      return (T) Proxy.newProxyInstance(
         type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if("getTable".equals(method.getName())) {
               return base;
            }

            throw new UnsupportedOperationException(method.getName());
         });
   }

   // the aggregation was pushed down: the crosstab sees 2 aggregated rows
   private static CrossFilter pushedDownCrosstab() {
      return filter(CrossFilter.class, table(2));
   }

   @Test
   void aggregateOverCappedSourceThatReachedTheLimitWarns() {
      assertTrue(VSEventUtil.isAggregateSourceCut(pushedDownCrosstab(), 50, () -> true));
   }

   @Test
   void aggregateOverSourceSmallerThanTheLimitStaysSilent() {
      assertFalse(VSEventUtil.isAggregateSourceCut(pushedDownCrosstab(), 50, () -> false));
   }

   @Test
   void aggregateOverInMemorySourceCutByAppliedMaxWarnsWithoutLookingAtTheWorksheet() {
      AtomicInteger lookups = new AtomicInteger();
      CrossFilter crosstab = filter(CrossFilter.class,
                                    new inetsoft.report.lens.MaxRowsTableLens(table(100), 51));

      assertTrue(VSEventUtil.isAggregateSourceCut(crosstab, 500, () -> {
         lookups.incrementAndGet();
         return false;
      }));
      assertEquals(0, lookups.get());
   }

   @Test
   void detailTableKeepsComparingItsOwnRowCount() {
      AtomicInteger lookups = new AtomicInteger();

      assertFalse(VSEventUtil.isAggregateSourceCut(table(80), 50, () -> {
         lookups.incrementAndGet();
         return true;
      }));
      assertEquals(0, lookups.get());
   }
}

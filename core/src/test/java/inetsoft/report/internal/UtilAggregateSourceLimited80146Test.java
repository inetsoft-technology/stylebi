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
package inetsoft.report.internal;

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.filter.CrossFilter;
import inetsoft.report.lens.DefaultTableLens;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #80146: the max rows warning compared the row count of an aggregate (a crosstab) with the
 * row limit of its source, so a crosstab with fewer groups than the limit never warned even
 * though its source was cut.
 */
@Tag("core")
class UtilAggregateSourceLimited80146Test {
   private static TableLens source(int dataRows) {
      DefaultTableLens table = new DefaultTableLens(dataRows + 1, 1);
      table.setHeaderRowCount(1);
      return table;
   }

   // a proxy, only getTable() is read by the check
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

   private static CrossFilter crosstab(TableLens base) {
      return filter(CrossFilter.class, base);
   }

   @Test
   void crosstabOverSourceThatReachedTheLimitIsLimited() {
      assertTrue(Util.isAggregateSourceLimited(crosstab(source(50)), 50));
   }

   @Test
   void crosstabOverSourceSmallerThanTheLimitIsNotLimited() {
      assertFalse(Util.isAggregateSourceLimited(crosstab(source(30)), 50));
   }

   @Test
   void crosstabBehindWrappingFiltersIsFound() {
      TableFilter wrapper = filter(TableFilter.class, crosstab(source(50)));

      assertTrue(Util.isAggregateSourceLimited(wrapper, 50));
   }

   @Test
   void detailTableIsNeverLimitedBySourceCheck() {
      assertFalse(Util.isAggregateSourceLimited(source(80), 50));
   }
}

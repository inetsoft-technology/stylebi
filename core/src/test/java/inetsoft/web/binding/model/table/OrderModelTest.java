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
package inetsoft.web.binding.model.table;

import inetsoft.report.internal.binding.OrderInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redmine #76892: {@code OrderModel(OrderInfo)} copied every other field off the real
 * {@code OrderInfo} but hard-reset {@code manualOrder} to a fresh empty list instead of reading
 * {@code info.getManualOrder()} -- so any read path that builds an {@code OrderModel} straight
 * from the stored {@code OrderInfo} (calc-table cell binding read, via
 * {@code CellBindingInfo(TableCellBinding)}; table/crosstab group-ref read, via
 * {@code GroupRefModel(GroupRef)}) always reported a manual sort's order as {@code []},
 * regardless of what was actually stored and actively rendering.
 */
@Tag("core")
class OrderModelTest {
   @Test
   void constructorCopiesManualOrderFromOrderInfo() {
      OrderInfo info = new OrderInfo();
      info.setOrder(OrderInfo.SORT_SPECIFIC);
      info.setManualOrder(List.of("West", "East", "Central"));

      OrderModel model = new OrderModel(info);

      assertEquals(List.of("West", "East", "Central"), model.getManualOrder());
   }

   @Test
   void constructorLeavesManualOrderEmptyWhenOrderInfoHasNone() {
      OrderInfo info = new OrderInfo();
      info.setOrder(OrderInfo.SORT_ASC);

      OrderModel model = new OrderModel(info);

      assertTrue(model.getManualOrder().isEmpty());
   }

   @Test
   void defaultConstructorLeavesManualOrderNull() {
      // OrderModel() -- the no-arg constructor used before a real OrderInfo is known (e.g.
      // CellBindingInfo's own field initializer) -- never touches manualOrder at all, unlike
      // OrderModel(OrderInfo) which the fix above always assigns a non-null list to. Documented
      // here so a future change to either constructor notices it changed this asymmetry.
      OrderModel model = new OrderModel();

      assertEquals(null, model.getManualOrder());
   }
}

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
package inetsoft.report.composition.execution;

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #79741: hidden-attribute check must be scoped to the column's own entity.
 */
@Tag("core")
class LMBoundQueryHiddenColumnTest {
   private static LMBoundQuery query(XLogicalModel lmodel) throws Exception {
      Field uf = Unsafe.class.getDeclaredField("theUnsafe");
      uf.setAccessible(true);
      Unsafe unsafe = (Unsafe) uf.get(null);
      LMBoundQuery query = (LMBoundQuery) unsafe.allocateInstance(LMBoundQuery.class);
      Field lf = LMBoundQuery.class.getDeclaredField("lmodel");
      lf.setAccessible(true);
      lf.set(query, lmodel);
      return query;
   }

   private static boolean hidden(LMBoundQuery query, String entity, String attr)
      throws Exception
   {
      Method m = LMBoundQuery.class.getDeclaredMethod("isHiddenColumn", ColumnRef.class);
      m.setAccessible(true);
      AttributeRef ref = entity == null ? new AttributeRef(attr) : new AttributeRef(entity, attr);
      return (Boolean) m.invoke(query, new ColumnRef(ref));
   }

   private static XLogicalModel model(boolean orderVisible) {
      XLogicalModel lmodel = new XLogicalModel("lm");
      XEntity customer = new XEntity("Customer");
      customer.setAttributeVisible("ID", false);
      XEntity order = new XEntity("Order");
      order.setVisible(orderVisible);
      lmodel.addEntity(customer);
      lmodel.addEntity(order);
      return lmodel;
   }

   @Test
   void sameNamedAttributeHiddenInOtherEntityDoesNotHideColumn() throws Exception {
      LMBoundQuery query = query(model(true));
      assertFalse(hidden(query, "Order", "ID"));
      assertTrue(hidden(query, "Customer", "ID"));
   }

   @Test
   void nullEntityKeepsAllEntitiesBehavior() throws Exception {
      LMBoundQuery query = query(model(true));
      assertTrue(hidden(query, null, "ID"));
   }

   @Test
   void invisibleOwnEntityIsNotHidden() throws Exception {
      LMBoundQuery query = query(model(false));
      assertFalse(hidden(query, "Order", "ID"));
   }

   @Test
   void nameHiddenNowhereIsNotHidden() throws Exception {
      LMBoundQuery query = query(model(true));
      assertFalse(hidden(query, "Order", "Name"));
      assertFalse(hidden(query, "Customer", "Name"));
   }
}

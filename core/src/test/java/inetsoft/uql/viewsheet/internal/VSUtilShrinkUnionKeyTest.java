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

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #79000: the fingerprint of the union of columns VSUtil.shrinkTable keeps on the shared bound
 * table of a logical model viewsheet. It must change when ANY assembly grows or shrinks the
 * union and stay put for changes that keep it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSUtilShrinkUnionKeyTest {
   @Test
   void isNullWhenTheViewsheetIsNotBoundToALogicalModel() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(table("A", "Order:Num"));
      assertNull(VSUtil.getShrinkUnionKey(vs));

      vs.setBaseEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                     "ws", null));
      assertNull(VSUtil.getShrinkUnionKey(vs));
      assertNull(VSUtil.getShrinkUnionKey(null));
   }

   @Test
   void changesWhenAnotherAssemblyGrowsOrShrinksTheUnion() {
      Viewsheet vs = lmViewsheet();
      vs.addAssembly(table("A", "Order:Num"));
      String before = VSUtil.getShrinkUnionKey(vs);
      assertNotNull(before);

      TableVSAssembly b = table("B", "Product:Total");
      vs.addAssembly(b);
      String grown = VSUtil.getShrinkUnionKey(vs);
      assertNotEquals(before, grown, "binding Product:Total in B did not change the union key");

      b.setColumnSelection(columns("Order:Num"));
      assertEquals(before, VSUtil.getShrinkUnionKey(vs),
                   "B no longer binds Product:Total, the union is back to the original one");

      vs.removeAssembly("B");
      assertEquals(before, VSUtil.getShrinkUnionKey(vs));
   }

   @Test
   void isTheSameForTheSameUnionWhoeverBindsIt() {
      Viewsheet one = lmViewsheet();
      one.addAssembly(table("A", "Order:Num"));
      one.addAssembly(table("B", "Product:Total"));

      Viewsheet two = lmViewsheet();
      two.addAssembly(table("X", "Product:Total"));
      two.addAssembly(table("Y", "Order:Num"));
      two.addAssembly(table("Z", "Order:Num"));

      assertEquals(VSUtil.getShrinkUnionKey(one), VSUtil.getShrinkUnionKey(two));
   }

   @Test
   void doesNotChangeForAChangeThatKeepsTheUnion() {
      Viewsheet vs = lmViewsheet();
      TableVSAssembly a = table("A", "Order:Num");
      vs.addAssembly(a);
      vs.addAssembly(table("B", "Product:Total"));
      String before = VSUtil.getShrinkUnionKey(vs);

      // format-only changes do not touch the bound columns
      a.getVSAssemblyInfo().setVisible(false);
      a.getVSAssemblyInfo().setVisible(true);
      assertEquals(before, VSUtil.getShrinkUnionKey(vs));

      // another assembly binding a column that is already in the union keeps it too
      vs.addAssembly(table("C", "Order:Num"));
      assertEquals(before, VSUtil.getShrinkUnionKey(vs));
   }

   private static Viewsheet lmViewsheet() {
      Viewsheet vs = new Viewsheet();
      vs.setBaseEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.LOGIC_MODEL,
                                     "ds/model", null));
      return vs;
   }

   private static TableVSAssembly table(String name, String... cols) {
      TableVSAssembly table = new TableVSAssembly(new Viewsheet(), name);
      table.setColumnSelection(columns(cols));
      return table;
   }

   private static ColumnSelection columns(String... names) {
      ColumnSelection cols = new ColumnSelection();

      for(String name : names) {
         int idx = name.indexOf(':');
         cols.addAttribute(new ColumnRef(
            new AttributeRef(name.substring(0, idx), name.substring(idx + 1))));
      }

      return cols;
   }
}

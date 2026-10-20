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
package inetsoft.web.composer.ws;

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78922 (WBS-146): the downstream dependency walk must map a source column into a
 * multi-source dependent by SOURCE (entity), not by bare attribute name. J1 = OD join EXT holds two
 * QUANTITY columns; the bare-name lookup picked the last one, so deleting OD.QUANTITY was refused
 * because a join two hops downstream is keyed on EXT's QUANTITY (QUANTITY_1).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class EntityAwareKeyMapping78922Test {
   /** J1 = OD join EXT on PRODUCT_ID, M1 = mirror(J1), J2 = M1 join BANDS on M1.QUANTITY_1. */
   private static final class Fixture {
      final Worksheet ws = new Worksheet();
      TableAssembly od;
      TableAssembly ext;
      ComposedTableAssembly j1;
   }

   private static Fixture build(boolean odFirst) {
      Fixture f = new Fixture();
      f.od = source(f.ws, "OD", "PRODUCT_ID", "QUANTITY");
      f.ext = source(f.ws, "EXT", "PRODUCT_ID", "QUANTITY");
      TableAssembly bands = source(f.ws, "BANDS", "QUANTITY_BAND", "LABEL");
      TableAssembly left = odFirst ? f.od : f.ext;
      TableAssembly right = odFirst ? f.ext : f.od;
      f.j1 = join(f.ws, "J1", left, right, "PRODUCT_ID", "PRODUCT_ID");
      MirrorTableAssembly m1 = new MirrorTableAssembly(f.ws, "M1", f.j1);
      m1.setColumnSelection(m1.getColumnSelection(false).clone(), true);
      f.ws.addAssembly(m1);
      join(f.ws, "J2", m1, bands, "QUANTITY_1", "QUANTITY_BAND");
      return f;
   }

   @Test
   void fixtureJoinColumnsCarryTheirSourceEntity() {
      Fixture f = build(true);
      ColumnRef odQty = (ColumnRef) f.j1.getColumnSelection(false).getAttribute(1);
      ColumnRef extQty = (ColumnRef) f.j1.getColumnSelection(false).getAttribute(3);

      assertEquals("OD", odQty.getEntity());
      assertEquals("QUANTITY", odQty.getAttribute());
      assertEquals("EXT", extQty.getEntity());
      assertEquals("QUANTITY_1", extQty.getAlias());
   }

   @Test
   void deletingTheNonKeyedSourceColumnIsAllowed() {
      Fixture f = build(true);

      assertTrue(WorksheetControllerService.allowsDeletion(f.ws, f.od, col(f.od, "QUANTITY")),
                 "OD.QUANTITY is not the join key; EXT.QUANTITY is");
   }

   @Test
   void deletingTheKeyedSourceColumnIsStillRefused() {
      Fixture f = build(true);

      assertFalse(WorksheetControllerService.allowsDeletion(f.ws, f.ext, col(f.ext, "QUANTITY")),
                  "EXT.QUANTITY feeds J2's key through J1 and M1");
   }

   @Test
   void reversedJoinOrderMapsTheOtherWay() {
      Fixture f = build(false);

      // J1 = EXT join OD, so now OD's QUANTITY is the aliased QUANTITY_1 and is the J2 key.
      assertFalse(WorksheetControllerService.allowsDeletion(f.ws, f.od, col(f.od, "QUANTITY")));
      assertTrue(WorksheetControllerService.allowsDeletion(f.ws, f.ext, col(f.ext, "QUANTITY")));
   }

   @Test
   void mapToDependentColumnPicksTheColumnOfTheNamedSource() {
      Fixture f = build(true);

      ColumnRef fromOd = WorksheetControllerService.mapToDependentColumn(
         f.od, f.j1, col(f.od, "QUANTITY"));
      ColumnRef fromExt = WorksheetControllerService.mapToDependentColumn(
         f.ext, f.j1, col(f.ext, "QUANTITY"));

      assertEquals("OD", fromOd.getEntity());
      assertEquals("EXT", fromExt.getEntity());
      assertEquals("QUANTITY_1", fromExt.getAlias());
   }

   @Test
   void concatNonFirstSourceFallsBackToTheBareNameMapping() {
      Worksheet ws = new Worksheet();
      TableAssembly a = source(ws, "A", "PRODUCT_ID", "QUANTITY");
      TableAssembly b = source(ws, "B", "PRODUCT_ID", "QUANTITY");
      TableAssembly bands = source(ws, "BANDS", "QUANTITY_BAND", "LABEL");
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.UNION);
      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);
      ConcatenatedTableAssembly c = new ConcatenatedTableAssembly(
         ws, "C", new TableAssembly[] { a, b }, new TableAssemblyOperator[] { top });
      ws.addAssembly(c);
      c.initDefaultColumnSelection();
      c.setColumnSelection(c.getColumnSelection(false).clone(), true);
      join(ws, "J2", c, bands, "QUANTITY", "QUANTITY_BAND");

      assertNotNull(WorksheetControllerService.mapToDependentColumn(b, c, col(b, "QUANTITY")));
      assertFalse(WorksheetControllerService.allowsDeletion(ws, b, col(b, "QUANTITY")),
                  "a concat is positional: the non-first source column still feeds C.QUANTITY");
      assertFalse(WorksheetControllerService.allowsDeletion(ws, a, col(a, "QUANTITY")));
   }

   private static ColumnRef col(TableAssembly table, String name) {
      ColumnSelection cs = table.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         ColumnRef c = (ColumnRef) cs.getAttribute(i);

         if(name.equals(c.getAttribute())) {
            return c;
         }
      }

      fail("no column " + name + " on " + table.getName());
      return null;
   }

   private static TableAssembly source(Worksheet ws, String name, String... cols) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();

      for(String col : cols) {
         ColumnRef ref = new ColumnRef(new AttributeRef(name, col));
         ref.setDataType("string");
         cs.addAttribute(ref);
      }

      table.setColumnSelection(cs, false);
      table.setColumnSelection(cs.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   /** Same shape as JoinQuery.getDefaultColumnSelection0: outer attribute per column, fixAlias. */
   private static ComposedTableAssembly join(Worksheet ws, String name, TableAssembly left,
                                             TableAssembly right, String lkey, String rkey)
   {
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable(left.getName());
      op.setRightTable(right.getName());
      op.setLeftAttribute(findKey(left, lkey));
      op.setRightAttribute(findKey(right, rkey));
      op.setOperation(TableAssemblyOperator.LEFT_JOIN);
      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);
      TableAssembly[] tables = { left, right };
      RelationalJoinTableAssembly j =
         new RelationalJoinTableAssembly(ws, name, tables, new TableAssemblyOperator[] { top });
      ColumnSelection columns = new ColumnSelection();

      for(TableAssembly table : tables) {
         ColumnSelection tcolumns = table.getColumnSelection(true);

         for(int i = 0; i < tcolumns.getAttributeCount(); i++) {
            ColumnRef tcolumn = (ColumnRef) tcolumns.getAttribute(i);
            ColumnRef column = new ColumnRef(AssetUtil.getOuterAttribute(table.getName(), tcolumn));
            column.setDataType(tcolumn.getDataType());
            columns.addAttribute(column);
         }

         AssetUtil.fixAlias(columns);
      }

      j.setColumnSelection(columns, false);
      j.setColumnSelection(columns.clone(), true);
      ws.addAssembly(j);
      return j;
   }

   private static ColumnRef findKey(TableAssembly table, String outName) {
      ColumnSelection cs = table.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         ColumnRef c = (ColumnRef) cs.getAttribute(i);

         if(outName.equals(c.getAlias() != null ? c.getAlias() : c.getAttribute())) {
            return c;
         }
      }

      fail("no key " + outName + " on " + table.getName());
      return null;
   }
}

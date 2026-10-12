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
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77005 / WBS-102: renaming a source column must not clobber a downstream mirror column that
 * carries its OWN alias. The alias is the mirror's output name, so it is kept (only the source
 * attribute is re-pointed) and the mirror's ranking and expression columns on that output name keep
 * resolving. Covers a source with and without an entity, and a mirror-of-mirror chain.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RenameColumnMirrorAliasTest {
   @ParameterizedTest
   @ValueSource(strings = { "", "T" })
   void aliasedMirrorColumnKeepsAliasRankingAndExpression(String entity) {
      Worksheet ws = new Worksheet();
      TableAssembly t = source(ws, "T", entity, "Region", "Amount");
      MirrorTableAssembly m = mirror(ws, "M", t);
      col(m, "Amount").setAlias("AmtM");
      WorksheetMutationSupport.addExpressionColumn(m, "Dbl", "field['AmtM']*2", "double", false);
      WorksheetMutationSupport.setRanking(
         m, new WorksheetMutationSupport.RankingSpec("AmtM", 2, "TOP_N", false));

      rename(ws, t, "Amount", "Amt");

      assertEquals(List.of("Region", "AmtM", "Dbl"), names(m));
      assertEquals("Amt", col(m, "AmtM").getAttribute());
      ConditionList ranking = (ConditionList) m.getRankingConditionList();
      assertEquals(1, ranking.getSize(), "the ranking on AmtM must survive");
      assertEquals("AmtM", ((ColumnRef) ranking.getConditionItem(0).getAttribute()).getName());
      assertEquals("field['AmtM']*2",
                   ((ExpressionRef) col(m, "Dbl").getDataRef()).getExpression());
   }

   @Test
   void aliasInTheMiddleOfAMirrorChainStopsTheCascade() {
      Worksheet ws = new Worksheet();
      TableAssembly t = source(ws, "T", "T", "Region", "Amount");
      MirrorTableAssembly m = mirror(ws, "M", t);
      col(m, "Amount").setAlias("AmtM");
      MirrorTableAssembly mm = mirror(ws, "MM", m);
      ColumnSelection mmCols = new ColumnSelection();

      for(String name : names(m)) {
         ColumnRef ref = new ColumnRef(new AttributeRef("M", name));
         ref.setDataType("string");
         mmCols.addAttribute(ref);
      }

      mm.setColumnSelection(mmCols, false);
      mm.setColumnSelection(mmCols.clone(), true);
      filter(mm, col(mm, "AmtM"));

      rename(ws, t, "Amount", "Amt");

      assertEquals(List.of("Region", "AmtM"), names(m));
      assertEquals(List.of("Region", "AmtM"), names(mm));
      assertEquals("AmtM", filterAttr(mm).getAttribute());
   }

   @Test
   void aliasOnTheSecondMirrorKeepsItsAliasWhileTheFirstFollows() {
      Worksheet ws = new Worksheet();
      TableAssembly t = source(ws, "T", "T", "Region", "Amount");
      MirrorTableAssembly m = mirror(ws, "M", t);
      MirrorTableAssembly mm = mirror(ws, "MM", m);
      col(mm, "Amount").setAlias("AmtMM");
      WorksheetMutationSupport.setRanking(
         mm, new WorksheetMutationSupport.RankingSpec("AmtMM", 2, "TOP_N", false));

      rename(ws, t, "Amount", "Amt");

      assertEquals(List.of("Region", "Amt"), names(m));
      assertEquals(List.of("Region", "AmtMM"), names(mm));
      assertEquals("Amt", col(mm, "AmtMM").getAttribute());
      assertEquals(1, ((ConditionList) mm.getRankingConditionList()).getSize());
   }

   @Test
   void unaliasedMirrorStillFollowsTheRename() {
      Worksheet ws = new Worksheet();
      TableAssembly t = source(ws, "T", "T", "Region", "Amount");
      MirrorTableAssembly m = mirror(ws, "M", t);
      WorksheetMutationSupport.setRanking(
         m, new WorksheetMutationSupport.RankingSpec("Amount", 2, "TOP_N", false));

      rename(ws, t, "Amount", "Amt");

      assertEquals(List.of("Region", "Amt"), names(m));
      ConditionList ranking = (ConditionList) m.getRankingConditionList();
      assertEquals(1, ranking.getSize());
      assertEquals("Amt", ranking.getConditionItem(0).getAttribute().getAttribute());
   }

   private static void rename(Worksheet ws, TableAssembly table, String column, String newName) {
      ColumnRef live = col(table, column);
      boolean failed = RenameColumnController.renameColumn(
         ws, mock(CommandDispatcher.class), table, live, newName);
      assertFalse(failed, "the rename itself must succeed");
   }

   private static TableAssembly source(Worksheet ws, String name, String entity, String... cols) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();

      for(String col : cols) {
         ColumnRef ref = new ColumnRef(new AttributeRef(entity.isEmpty() ? null : entity, col));
         ref.setDataType("string");
         cs.addAttribute(ref);
      }

      table.setColumnSelection(cs, false);
      table.setColumnSelection(cs.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   private static MirrorTableAssembly mirror(Worksheet ws, String name, TableAssembly base) {
      MirrorTableAssembly m = new MirrorTableAssembly(ws, name, base);
      m.setColumnSelection(m.getColumnSelection(false).clone(), true);
      ws.addAssembly(m);
      return m;
   }

   private static void filter(TableAssembly table, ColumnRef column) {
      Condition cond = new Condition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("A");
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(column, cond, 0));
      table.setPreConditionList(list);
   }

   private static DataRef filterAttr(TableAssembly table) {
      ConditionList list = (ConditionList) table.getPreConditionList();
      assertTrue(list.getSize() > 0, "the filter on " + table.getName() + " was dropped");
      return list.getConditionItem(0).getAttribute();
   }

   private static ColumnRef col(TableAssembly table, String outName) {
      ColumnSelection cs = table.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         ColumnRef c = (ColumnRef) cs.getAttribute(i);

         if(outName.equals(out(c))) {
            return c;
         }
      }

      fail("no column " + outName + " on " + table.getName() + ": " + names(table));
      return null;
   }

   private static List<String> names(TableAssembly table) {
      ColumnSelection cs = table.getColumnSelection(false);
      List<String> names = new ArrayList<>();

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         names.add(out((ColumnRef) cs.getAttribute(i)));
      }

      return names;
   }

   private static String out(ColumnRef c) {
      return c.getAlias() != null ? c.getAlias() : c.getAttribute();
   }
}

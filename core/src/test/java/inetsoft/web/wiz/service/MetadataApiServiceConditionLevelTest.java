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

package inetsoft.web.wiz.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.JunctionOperator;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.web.wiz.model.WorksheetStructure;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Condition leaves report the level of the junction preceding them, the level StyleBI itself splits
 * on (ConditionListHandler: OR junctions of the current level first, then AND, deeper runs are one
 * group at the next level), so A AND (B OR C) is not read back as A AND B OR C (turn-provenance
 * spec 3.3.4). Applies to worksheet, chart and SQL leaves.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MetadataApiServiceConditionLevelTest {
   private static ConditionItem item(String field, int level) {
      Condition condition = new Condition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.addValue("v");
      return new ConditionItem(new AttributeRef(null, field), condition, level);
   }

   /** A AND (B OR C): the AND junction at level 0, the OR junction at level 1. */
   private static ConditionList andOfOrGroup() {
      ConditionList list = new ConditionList();
      list.append(item("A", 0));
      list.append(new JunctionOperator(JunctionOperator.AND, 0));
      list.append(item("B", 1));
      list.append(new JunctionOperator(JunctionOperator.OR, 1));
      list.append(item("C", 1));
      return list;
   }

   /** (A OR B) AND C: the OR junction at level 1, the AND junction at level 0. */
   private static ConditionList orGroupThenAnd() {
      ConditionList list = new ConditionList();
      list.append(item("A", 1));
      list.append(new JunctionOperator(JunctionOperator.OR, 1));
      list.append(item("B", 1));
      list.append(new JunctionOperator(JunctionOperator.AND, 0));
      list.append(item("C", 0));
      return list;
   }

   private static List<WorksheetStructure.ConditionLeaf> collect(ConditionList list, String phase) {
      List<WorksheetStructure.ConditionLeaf> out = new ArrayList<>();
      MetadataApiService.collectStructureConditions(list, phase, out);
      return out;
   }

   private static void assertLeaf(WorksheetStructure.ConditionLeaf leaf, String field,
                                  String junction, Integer junctionLevel)
   {
      assertEquals(field, leaf.getField());
      assertEquals(junction, leaf.getJunction(), field);
      assertEquals(junctionLevel, leaf.getJunctionLevel(), field);
   }

   private static SQLBoundTableAssembly sqlTable(String text) {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t = new SQLBoundTableAssembly(ws, "sqlTable");
      t.setSourceInfo(new SourceInfo(XSourceInfo.PHYSICAL_TABLE, "ds", "ds"));
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(false);
      sql.setSQLString(text, false);
      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(sql);
      ((SQLBoundTableAssemblyInfo) t.getInfo()).setQuery(query);
      return t;
   }

   @Test
   void worksheetAndOfAnOrGroupCarriesJunctionLevels() {
      List<WorksheetStructure.ConditionLeaf> out = collect(andOfOrGroup(), "pre");

      assertEquals(3, out.size());
      assertLeaf(out.get(0), "A", null, null);
      assertLeaf(out.get(1), "B", "AND", 0);
      assertLeaf(out.get(2), "C", "OR", 1);
   }

   @Test
   void worksheetOrGroupThenAndCarriesJunctionLevels() {
      List<WorksheetStructure.ConditionLeaf> out = collect(orGroupThenAnd(), "post");

      assertEquals(3, out.size());
      assertLeaf(out.get(0), "A", null, null);
      assertLeaf(out.get(1), "B", "OR", 1);
      assertLeaf(out.get(2), "C", "AND", 0);
   }

   @Test
   void chartConditionListsCarryTheSameJunctionLevels() {
      // the shapes WizVsService.appendConditionNodes writes for a nested chart filter
      List<WorksheetStructure.ConditionLeaf> andOfOr = collect(andOfOrGroup(), "chart");
      List<WorksheetStructure.ConditionLeaf> orThenAnd = collect(orGroupThenAnd(), "chart");

      assertLeaf(andOfOr.get(0), "A", null, null);
      assertLeaf(andOfOr.get(1), "B", "AND", 0);
      assertLeaf(andOfOr.get(2), "C", "OR", 1);
      assertEquals("chart", andOfOr.get(2).getPhase());
      assertLeaf(orThenAnd.get(0), "A", null, null);
      assertLeaf(orThenAnd.get(1), "B", "OR", 1);
      assertLeaf(orThenAnd.get(2), "C", "AND", 0);
   }

   @Test
   void sqlLeavesCarryTheDepthOfTheSetTheirJunctionBelongsTo() {
      var r = MetadataApiService.extractStructureConditionsAndCoverage(
         sqlTable("SELECT a.a, a.b, a.c FROM T a WHERE a.a = 1 AND (a.b = 2 OR a.c = 3)"));

      assertEquals("full", r.coverage());
      assertEquals(3, r.leaves().size());
      assertLeaf(r.leaves().get(0), "a", null, null);
      // the first leaf of the nested group carries the OUTER junction, at the root set's depth
      assertLeaf(r.leaves().get(1), "b", "AND", 0);
      assertLeaf(r.leaves().get(2), "c", "OR", 1);
   }

   @Test
   void aSingleSqlLeafHasNoJunctionLevel() {
      var r = MetadataApiService.extractStructureConditionsAndCoverage(
         sqlTable("SELECT a.x FROM T a WHERE a.x = 1"));

      assertEquals(1, r.leaves().size());
      assertNull(r.leaves().get(0).getJunctionLevel());
   }
}

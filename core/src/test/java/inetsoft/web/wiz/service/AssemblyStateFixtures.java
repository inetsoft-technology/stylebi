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

import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.JunctionOperator;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;

/**
 * Real viewsheet objects for the assemblyState tests (callers must run with a Spring context:
 * Viewsheet and assembly construction reach SreeEnv).
 */
final class AssemblyStateFixtures {
   private AssemblyStateFixtures() {
   }

   static AssetEntry worksheetEntry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null);
   }

   static AssetEntry logicalModelEntry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.LOGIC_MODEL, "ds/model1", null);
   }

   static AssetEntry physicalTableEntry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.PHYSICAL_TABLE, "ds/T1", null);
   }

   static Viewsheet viewsheet(AssetEntry base) {
      return new Viewsheet(base, false, true, null, null);
   }

   /** A chart with one measure, Sum(Sales), on Y. */
   static ChartVSAssembly chart(Viewsheet vs, String name) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      VSChartAggregateRef measure = new VSChartAggregateRef();
      measure.setColumnValue("Sales");
      measure.setFormulaValue("Sum");
      chart.getVSChartInfo().addYField(measure);
      return chart;
   }

   static VSChartAggregateRef measure(ChartVSAssembly chart) {
      return (VSChartAggregateRef) chart.getVSChartInfo().getYFields()[0];
   }

   static ConditionList condition(String field, int operation, Object value) {
      Condition condition = new Condition();
      condition.setOperation(operation);
      condition.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, field), condition, 0));
      return list;
   }

   /** A AND (B OR C), as WizVsService.appendConditionNodes writes a nested group. */
   static ConditionList nested() {
      ConditionList list = new ConditionList();
      list.append(item("A", 0));
      list.append(new JunctionOperator(JunctionOperator.AND, 0));
      list.append(item("B", 1));
      list.append(new JunctionOperator(JunctionOperator.OR, 1));
      list.append(item("C", 1));
      return list;
   }

   static HighlightGroup group(String name, ConditionList conditions) {
      TextHighlight highlight = new TextHighlight();
      highlight.setName(name);
      highlight.setConditionGroup(conditions);
      HighlightGroup group = new HighlightGroup();
      group.addHighlight(name, highlight);
      return group;
   }

   private static ConditionItem item(String field, int level) {
      Condition condition = new Condition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.addValue(field.toLowerCase());
      return new ConditionItem(new AttributeRef(null, field), condition, level);
   }
}

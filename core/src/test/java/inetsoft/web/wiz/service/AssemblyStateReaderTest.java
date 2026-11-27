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

import inetsoft.report.TableDataPath;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.CandleVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.WorksheetStructure;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Reading assemblyState's parts straight from the assembly model (turn-provenance spec 3.3). */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssemblyStateReaderTest {
   @Test
   void chartSourceNamesTheWorksheetAndStripsTheOuterSuffix() {
      AssetEntry base = AssemblyStateFixtures.worksheetEntry();
      Viewsheet vs = AssemblyStateFixtures.viewsheet(base);
      ChartVSAssembly chart = AssemblyStateFixtures.chart(vs, "chart1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Orders_2_O"));

      CreateViewsheetResult.AssemblySource source = AssemblyStateReader.readSource(vs, chart);

      assertEquals("worksheet", source.kind());
      assertEquals(base.toIdentifier(), source.worksheet());
      assertEquals("Orders_2", source.table());
   }

   @Test
   void sourceKindNamesModelAndPhysicalBasesWithoutAWorksheet() {
      Viewsheet modelVs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.logicalModelEntry());
      Viewsheet physicalVs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.physicalTableEntry());

      CreateViewsheetResult.AssemblySource model =
         AssemblyStateReader.readSource(modelVs, AssemblyStateFixtures.chart(modelVs, "c1"));
      CreateViewsheetResult.AssemblySource physical =
         AssemblyStateReader.readSource(physicalVs, AssemblyStateFixtures.chart(physicalVs, "c2"));

      assertEquals("model", model.kind());
      assertNull(model.worksheet());
      assertEquals("physical", physical.kind());
      assertNull(physical.worksheet());
   }

   @Test
   void anUnboundOutputHasNoSourceTable() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());

      CreateViewsheetResult.AssemblySource source =
         AssemblyStateReader.readSource(vs, new GaugeVSAssembly(vs, "gauge1"));

      assertEquals("worksheet", source.kind());
      assertNull(source.table());
   }

   @Test
   void conditionsAreTheAssemblysOwnPreConditionsWithPhaseChartAndJunctionLevel() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      ChartVSAssembly chart = AssemblyStateFixtures.chart(vs, "chart1");
      chart.setPreConditionList(AssemblyStateFixtures.nested());

      List<WorksheetStructure.ConditionLeaf> leaves = AssemblyStateReader.readConditions(chart);

      assertEquals(3, leaves.size());
      assertEquals("A", leaves.get(0).getField());
      assertEquals("chart", leaves.get(0).getPhase());
      assertNull(leaves.get(0).getJunctionLevel());
      assertEquals("AND", leaves.get(1).getJunction());
      assertEquals(Integer.valueOf(0), leaves.get(1).getJunctionLevel());
      assertEquals("OR", leaves.get(2).getJunction());
      assertEquals(Integer.valueOf(1), leaves.get(2).getJunctionLevel());
   }

   @Test
   void outputConditionsAreReadToo() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "gauge1");
      gauge.setPreConditionList(AssemblyStateFixtures.condition("Category", XCondition.EQUAL_TO, "A"));

      List<WorksheetStructure.ConditionLeaf> leaves = AssemblyStateReader.readConditions(gauge);

      assertEquals(1, leaves.size());
      assertEquals("Category", leaves.get(0).getField());
      assertEquals(List.of("A"), leaves.get(0).getValues());
   }

   @Test
   void anAssemblyWithoutConditionsHasAnEmptyList() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());

      assertTrue(AssemblyStateReader.readConditions(AssemblyStateFixtures.chart(vs, "c1")).isEmpty());
      assertTrue(AssemblyStateReader.readConditions(null).isEmpty());
   }

   @Test
   void chartHighlightsListTheChartLevelGroupAndEveryRefGroup() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      ChartVSAssembly chart = AssemblyStateFixtures.chart(vs, "chart1");
      chart.getVSChartInfo().setHighlightGroup(AssemblyStateFixtures.group(
         "overall", AssemblyStateFixtures.condition("Region", XCondition.EQUAL_TO, "East")));
      AssemblyStateFixtures.measure(chart).setHighlightGroup(AssemblyStateFixtures.group(
         "big", AssemblyStateFixtures.condition("Sales", XCondition.GREATER_THAN, 100)));

      List<CreateViewsheetResult.AssemblyHighlight> highlights = AssemblyStateReader.readHighlights(chart);

      assertEquals(2, highlights.size());
      assertEquals("overall", highlights.get(0).name());
      assertNull(highlights.get(0).target());
      assertEquals(List.of("Region EQUAL_TO East"), highlights.get(0).clauses());
      assertEquals("big", highlights.get(1).name());
      assertEquals(AssemblyStateFixtures.measure(chart).getFullName(), highlights.get(1).target());
      assertEquals(List.of("Sales GREATER_THAN 100"), highlights.get(1).clauses());
   }

   @Test
   void multiStyleChartHighlightsIncludeEachFieldsOwnTextField() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      ChartVSAssembly chart = AssemblyStateFixtures.chart(vs, "chart1");
      VSChartAggregateRef label = new VSChartAggregateRef();
      label.setColumnValue("Profit");
      label.setFormulaValue("Sum");
      label.setTextHighlightGroup(AssemblyStateFixtures.group(
         "loss", AssemblyStateFixtures.condition("Profit", XCondition.LESS_THAN, 0)));
      VSAestheticRef text = new VSAestheticRef();
      text.setDataRef(label);
      AssemblyStateFixtures.measure(chart).setTextField(text);

      assertTrue(AssemblyStateReader.readHighlights(chart).isEmpty(),
                 "a per-field text field is not in effect on a single-style chart");

      chart.getVSChartInfo().setMultiStyles(true);
      List<CreateViewsheetResult.AssemblyHighlight> highlights = AssemblyStateReader.readHighlights(chart);

      assertEquals(1, highlights.size());
      assertEquals("loss", highlights.get(0).name());
      assertEquals(label.getFullName(), highlights.get(0).target());
      assertEquals(List.of("Profit LESS_THAN 0"), highlights.get(0).clauses());
   }

   @Test
   void candleChartHighlightsIncludeTheHighAndCloseFields() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      ChartVSAssembly chart = new ChartVSAssembly(vs, "candle1");
      CandleVSChartInfo info = new CandleVSChartInfo();
      VSChartAggregateRef high = new VSChartAggregateRef();
      high.setColumnValue("High");
      high.setFormulaValue("Max");
      high.setHighlightGroup(AssemblyStateFixtures.group(
         "peak", AssemblyStateFixtures.condition("High", XCondition.GREATER_THAN, 90)));
      VSChartAggregateRef close = new VSChartAggregateRef();
      close.setColumnValue("Close");
      close.setFormulaValue("Sum");
      close.setTextHighlightGroup(AssemblyStateFixtures.group(
         "drop", AssemblyStateFixtures.condition("Close", XCondition.LESS_THAN, 10)));
      info.setHighField(high);
      info.setCloseField(close);
      chart.setVSChartInfo(info);

      Map<String, CreateViewsheetResult.AssemblyHighlight> byName = new HashMap<>();

      for(CreateViewsheetResult.AssemblyHighlight highlight : AssemblyStateReader.readHighlights(chart)) {
         byName.put(highlight.name(), highlight);
      }

      assertEquals(2, byName.size());
      assertEquals(high.getFullName(), byName.get("peak").target());
      assertEquals(List.of("High GREATER_THAN 90"), byName.get("peak").clauses());
      assertEquals(close.getFullName(), byName.get("drop").target());
      assertEquals(List.of("Close LESS_THAN 10"), byName.get("drop").clauses());
   }

   @Test
   void markAndTextHighlightsWithTheSameNameOnOneMeasureAreBothListed() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      ChartVSAssembly chart = AssemblyStateFixtures.chart(vs, "chart1");
      VSChartAggregateRef measure = AssemblyStateFixtures.measure(chart);
      measure.setHighlightGroup(AssemblyStateFixtures.group(
         "big", AssemblyStateFixtures.condition("Sales", XCondition.GREATER_THAN, 100)));
      measure.setTextHighlightGroup(AssemblyStateFixtures.group(
         "big", AssemblyStateFixtures.condition("Sales", XCondition.GREATER_THAN, 500)));

      List<CreateViewsheetResult.AssemblyHighlight> highlights = AssemblyStateReader.readHighlights(chart);

      assertEquals(2, highlights.size());
      assertEquals(List.of("Sales GREATER_THAN 100"), highlights.get(0).clauses());
      assertEquals(List.of("Sales GREATER_THAN 500"), highlights.get(1).clauses());
      assertEquals(measure.getFullName(), highlights.get(1).target());
   }

   @Test
   void crosstabHighlightIsKeyedByItsCellPath() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "crosstab1");
      TableHighlightAttr attr = new TableHighlightAttr();
      attr.setHighlight(
         new TableDataPath(-1, TableDataPath.SUMMARY, XSchema.DOUBLE, new String[] { "Sum(Sales)" }),
         AssemblyStateFixtures.group("hot", AssemblyStateFixtures.condition("Sales", XCondition.GREATER_THAN, 5)));
      ((TableDataVSAssemblyInfo) crosstab.getVSAssemblyInfo()).setHighlightAttr(attr);

      List<CreateViewsheetResult.AssemblyHighlight> highlights = AssemblyStateReader.readHighlights(crosstab);

      assertEquals(1, highlights.size());
      assertEquals("hot", highlights.get(0).name());
      assertEquals("Sum(Sales)", highlights.get(0).target());
   }

   @Test
   void tableHighlightsIncludeColumnAndRowPaths() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      TableVSAssembly table = new TableVSAssembly(vs, "table1");
      TableHighlightAttr attr = new TableHighlightAttr();
      attr.setHighlight(
         new TableDataPath(-1, TableDataPath.DETAIL, XSchema.STRING, new String[] { "Region" }),
         AssemblyStateFixtures.group("east", AssemblyStateFixtures.condition("Region", XCondition.EQUAL_TO, "East")));
      attr.setHighlight(
         new TableDataPath(-1, TableDataPath.DETAIL, XSchema.STRING, new String[0], true, false),
         AssemblyStateFixtures.group("stripe", AssemblyStateFixtures.condition("Sales", XCondition.LESS_THAN, 0)));
      ((TableDataVSAssemblyInfo) table.getVSAssemblyInfo()).setHighlightAttr(attr);

      Map<String, String> targets = new HashMap<>();

      for(CreateViewsheetResult.AssemblyHighlight highlight : AssemblyStateReader.readHighlights(table)) {
         targets.put(highlight.name(), highlight.target());
      }

      assertEquals(Map.of("east", "Region", "stripe", "row"), targets);
   }

   @Test
   void textOutputHighlightHasNoTarget() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      TextVSAssembly text = new TextVSAssembly(vs, "text1");
      ((OutputVSAssemblyInfo) text.getVSAssemblyInfo()).setHighlightGroup(AssemblyStateFixtures.group(
         "low", AssemblyStateFixtures.condition("value", XCondition.LESS_THAN, 10)));

      List<CreateViewsheetResult.AssemblyHighlight> highlights = AssemblyStateReader.readHighlights(text);

      assertEquals(1, highlights.size());
      assertEquals("low", highlights.get(0).name());
      assertNull(highlights.get(0).target());
      assertEquals(List.of("value LESS_THAN 10"), highlights.get(0).clauses());
   }

   @Test
   void tableColumnsListOnlyTheVisibleColumns() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      TableVSAssembly table = new TableVSAssembly(vs, "table1");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "Region")));
      ColumnRef hidden = new ColumnRef(new AttributeRef(null, "Secret"));
      hidden.setVisible(false);
      columns.addAttribute(hidden);
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "Sales")));
      table.setColumnSelection(columns);

      CreateViewsheetResult.TableColumns tableColumns = AssemblyStateReader.readTableColumns(table);

      assertEquals(List.of("Region", "Sales"), tableColumns.columns());
   }

   @Test
   void tableColumnsAreNullForEveryOtherType() {
      Viewsheet vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());

      assertNull(AssemblyStateReader.readTableColumns(AssemblyStateFixtures.chart(vs, "c1")));
      assertNull(AssemblyStateReader.readTableColumns(new CrosstabVSAssembly(vs, "x1")));
      assertNull(AssemblyStateReader.readTableColumns(new GaugeVSAssembly(vs, "g1")));
   }

   @Test
   void clauseFormatCarriesJunctionNegationInclusiveBoundAndValues() {
      WorksheetStructure.ConditionLeaf leaf = new WorksheetStructure.ConditionLeaf();
      leaf.setJunction("AND");
      leaf.setNegated(true);
      leaf.setField("Sales");
      leaf.setOperation("GREATER_THAN");
      leaf.setEqual(true);
      leaf.setValues(List.of("100"));

      WorksheetStructure.ConditionLeaf isNull = new WorksheetStructure.ConditionLeaf();
      isNull.setField("Region");
      isNull.setOperation("NULL");

      assertEquals("AND NOT Sales GREATER_THAN_OR_EQUAL 100", AssemblyStateReader.formatClause(leaf));
      assertEquals("Region NULL", AssemblyStateReader.formatClause(isNull));
   }

   @Test
   void clauseFormatOmitsAMissingField() {
      WorksheetStructure.ConditionLeaf leaf = new WorksheetStructure.ConditionLeaf();
      leaf.setJunction("OR");
      leaf.setOperation("EQUAL_TO");
      leaf.setValues(List.of("East"));

      assertEquals("OR EQUAL_TO East", AssemblyStateReader.formatClause(leaf));
   }
}

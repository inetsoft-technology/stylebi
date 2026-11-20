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
package inetsoft.report.composition.graph;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.TealColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A composer draw records which colour fields draw the diverging ramp, for the colour pane; a
 * viewer draw leaves the record alone. TableView1 has 30 years and 2 storm types.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class DivergingColorFieldsViewsheetTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aComposerDrawRecordsAFieldThatCrossesZero() throws Exception {
      ViewsheetSandbox box = sandbox(composer);
      assertEquals(Viewsheet.SHEET_DESIGN_MODE, box.getMode(), "premise: a composer sheet");
      VSChartInfo cinfo = addBar(box, SIGNED).getVSChartInfo();
      cinfo.setDivergingColorFields(Set.of(STALE));

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(Set.of(SIGNED_SUM), cinfo.getDivergingColorFields());
   }

   @Test
   void aComposerDrawOfAPositiveMeasureRecordsNone() throws Exception {
      ViewsheetSandbox box = sandbox(composer);
      VSChartInfo cinfo = addBar(box, "wind").getVSChartInfo();
      cinfo.setDivergingColorFields(Set.of(STALE));

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(Set.of(), cinfo.getDivergingColorFields());
   }

   @Test
   void aViewerDrawLeavesTheRecordAlone() throws Exception {
      ViewsheetSandbox box = sandbox(viewer);
      assertEquals(Viewsheet.SHEET_RUNTIME_MODE, box.getMode(), "premise: a viewer sheet");
      VSChartInfo cinfo = addBar(box, SIGNED).getVSChartInfo();
      cinfo.setDivergingColorFields(Set.of(STALE));

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(Set.of(SIGNED_SUM), DivergingRamps.drawnFields(pair.getEGraph()),
                   "premise: the viewer draws the diverging ramp");
      assertEquals(Set.of(STALE), cinfo.getDivergingColorFields());
   }

   private static ViewsheetSandbox sandbox(RuntimeViewsheetExtension sheet) {
      return sheet.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
   }

   // a marked bar of Sum(wind) by year, with the sum of the color column on Color as Teal
   private static ChartVSAssemblyInfo addBar(ViewsheetSandbox box, String color) {
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      addSignedCalc(vs, source.getSource());

      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo(source);
      VSChartInfo cinfo = chart.getVSChartInfo();
      cinfo.addXField(dim("year"));
      cinfo.addYField(sum("wind"));

      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setDataRef(sum(color));
      colorRef.setVisualFrameWrapper(new TealColorFrameWrapper());
      cinfo.setColorField(colorRef);
      cinfo.setChartType(GraphTypes.CHART_BAR);

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.initDefaultFormat();
      vs.addAssembly(chart);
      return info;
   }

   // wind on even years, its negative on odd years, so the yearly sums cross zero
   private static void addSignedCalc(Viewsheet vs, String table) {
      CalculateRef calc = new CalculateRef(true);
      calc.setName(SIGNED);
      ExpressionRef expr = new ExpressionRef(null, SIGNED);
      expr.setExpression("field['year'] % 2 == 0 ? field['wind'] : -field['wind']");
      expr.setDataType(XSchema.DOUBLE);
      calc.setDataRef(expr);
      calc.setSQL(false);
      vs.addCalcField(table, calc);
   }

   private static VSChartAggregateRef sum(String col) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue(col);
      agg.setFormulaValue("Sum");
      return agg;
   }

   private static VSChartDimensionRef dim(String col) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, col)));
      dim.setGroupColumnValue(col);
      dim.setOrder(XConstants.SORT_ASC);
      return dim;
   }

   private static OpenViewsheetEvent openViewsheetEvent(boolean viewer) {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(viewer);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension composer = new RuntimeViewsheetExtension(openViewsheetEvent(false));

   @RegisterExtension
   RuntimeViewsheetExtension viewer = new RuntimeViewsheetExtension(openViewsheetEvent(true));

   private static final String TABLE = "TableView1";
   private static final String CHART = "ChartDivergingFields";
   private static final String SIGNED = "Signed";
   private static final String SIGNED_SUM = "Sum(Signed)";
   private static final String STALE = "Sum(Stale)";
}

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
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.util.LockRestoreException;
import inetsoft.util.MessageException;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78727: since #78220 a chart whose graph fails to initialize fails every request for its
 * graph pair instead of only the first. The export asks for the pair of every chart, hidden or
 * not, in {@code prepareSheet} (the EGraph-populate loop, and the match-layout loop), which had
 * no catch, so a hidden chart bound to a column its table lacks failed the whole export. A
 * chart that is not exported must not fail the export; an exported chart whose graph fails to
 * initialize still does (#77227). A chart with a script gets the failure as a "Script failed"
 * MessageException, which must not fail the export either. A chart on a tab that is not selected
 * is not exported either, and is prepared like a hidden one.
 *
 * <p>CSV is used because painting a chart needs batik, which core's test classpath lacks.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/graph/GraphRenderTest.zip")
@Tag("core")
@Tag("integration")
class AbstractVSExporterChartInitFailureTest {
   @ParameterizedTest(name = "match layout {0}, script {1}")
   @CsvSource({ "false,false", "true,false", "false,true", "true,true" })
   void hiddenChartWhoseGraphFailsDoesNotFailTheExport(boolean match, boolean script)
      throws Exception
   {
      ViewsheetSandbox box = getBox();
      ChartVSAssembly chart = bindMissingColumn(box, script);
      setVisible(chart, false);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVVSExporter exporter = new CSVVSExporter(out, null);
      exporter.setMatchLayout(match);

      assertDoesNotThrow(() -> {
         exporter.export(box, "Current View", 0, null);
         exporter.write();
      });
      assertTrue(out.size() > 0, "the export should be written");
   }

   // #77227: a chart that is exported and whose graph fails to initialize fails the export
   @ParameterizedTest(name = "match layout {0}, script {1}")
   @CsvSource({ "false,false", "true,false", "false,true", "true,true" })
   void exportedChartWhoseGraphFailsStillFailsTheExport(boolean match, boolean script)
      throws Exception
   {
      ViewsheetSandbox box = getBox();
      ChartVSAssembly chart = bindMissingColumn(box, script);
      setVisible(chart, true);
      CSVVSExporter exporter = new CSVVSExporter(new ByteArrayOutputStream(), null);
      exporter.setMatchLayout(match);

      Exception ex = assertThrows(Exception.class, () -> {
         exporter.export(box, "Current View", 0, null);
         exporter.write();
      });
      assertMissingColumn(ex);
   }

   // a chart on a tab that is not selected is not visible at runtime, so it is not exported and
   // must not fail the export; the chart on the selected tab is exported and does (#77227)
   @ParameterizedTest(name = "match layout {0}, chart tab selected {1}")
   @CsvSource({ "false,false", "true,false", "false,true", "true,true" })
   void chartOnATabWhoseGraphFailsFailsTheExportOnlyIfSelected(boolean match, boolean selected)
      throws Exception
   {
      ViewsheetSandbox box = getBox();
      Viewsheet vs = box.getViewsheet();
      ChartVSAssembly chart = bindMissingColumn(box, false);
      setVisible(chart, true);
      vs.addAssembly(new TextVSAssembly(vs, TEXT));
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab78727");
      tab.setAssemblies(new String[] { TEXT, CHART });
      tab.setLabelsValue(new String[] { "Other", "Chart" });
      tab.setSelectedValue(selected ? CHART : TEXT);
      vs.addAssembly(tab);
      assertEquals(selected, vs.isVisible(chart, AbstractSheet.SHEET_RUNTIME_MODE));
      CSVVSExporter exporter = new CSVVSExporter(new ByteArrayOutputStream(), null);
      exporter.setMatchLayout(match);
      Executable export = () -> {
         exporter.export(box, "Current View", 0, null);
         exporter.write();
      };

      if(selected) {
         assertMissingColumn(assertThrows(Exception.class, export));
      }
      else {
         assertDoesNotThrow(export);
      }
   }

   // what is not a failure of the chart is rethrown, also inside the "Script failed" wrapper
   @Test
   void lockLossConfirmAndCancelAreNotChartFailures() throws Exception {
      ChartVSAssembly chart = (ChartVSAssembly) getBox().getViewsheet().getAssembly(CHART);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      ConfirmException confirm = new ConfirmException("vs.mv.prepare", ConfirmException.PROGRESS);
      LockRestoreException lost = new LockRestoreException("lost");

      for(Exception ex : new Exception[] { confirm, lost }) {
         doThrow(new MessageException("Script failed", ex)).when(box).getVGraphPair(CHART);
         assertSame(ex, assertThrows(Exception.class,
                                     () -> AbstractVSExporter.getPreparedGraphPair(box, chart)));

         doThrow(ex).when(box).getVGraphPair(CHART);
         assertSame(ex, assertThrows(Exception.class,
                                     () -> AbstractVSExporter.getPreparedGraphPair(box, chart)));
      }

      // an invalid binding the graph generator reports with a message is a chart failure
      doThrow(new MessageException("em.common.graph.XmeaNotAllowed"))
         .when(box).getVGraphPair(CHART);
      assertNull(AbstractVSExporter.getPreparedGraphPair(box, chart));
   }

   /**
    * Bind the x axis of the chart to only a date column its table does not have, as the
    * reported chart's, so every init of the chart's graph fails: the chart query drops the
    * unknown ref, which leaves no runtime x field, and the graph generator then falls back to
    * the design x field and asks the data set for the column. With {@code script}, the chart
    * has a script, so the failure comes as a "Script failed" MessageException.
    */
   private static ChartVSAssembly bindMissingColumn(ViewsheetSandbox box, boolean script) {
      ChartVSAssembly chart = (ChartVSAssembly) box.getViewsheet().getAssembly(CHART);

      if(script) {
         chart.getVSAssemblyInfo().setScript("var b78727 = 1;");
         chart.getVSAssemblyInfo().setScriptEnabled(true);
      }

      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue(MISSING);
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(String.valueOf(DateRangeRef.MONTH_INTERVAL));
      dim.setTimeSeries(true);
      info.removeXFields();
      info.addXField(dim);

      // precondition: the graph of the chart cannot be built, on every request
      for(int i = 0; i < 2; i++) {
         Exception ex = assertThrows(Exception.class, () -> box.getVGraphPair(CHART));
         assertMissingColumn(ex);
         assertEquals(script, ex instanceof MessageException, String.valueOf(ex));
      }

      return chart;
   }

   private static void setVisible(ChartVSAssembly chart, boolean visible) {
      String value = String.valueOf(visible ? VSAssembly.ALWAYS_SHOW : VSAssembly.ALWAYS_HIDE);
      chart.getVSAssemblyInfo().setVisibleValue(value);
      chart.getVSAssemblyInfo().setVisible(value);
      assertEquals(visible, chart.isVisible());
   }

   private static void assertMissingColumn(Throwable ex) {
      for(Throwable t = ex; t != null; t = t.getCause()) {
         if(t instanceof ColumnNotFoundException && t.getMessage().contains(MISSING)) {
            return;
         }
      }

      fail("expected the missing column failure, got " + ex, ex);
   }

   private ViewsheetSandbox getBox() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      return rvs.getViewsheetSandbox().orElseThrow();
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String ASSET_ID = "1^128^__NULL__^TEST_GraphRender";
   private static final String CHART = "Chart1";
   private static final String MISSING = "No Such Column";
   private static final String TEXT = "Text78727";
}

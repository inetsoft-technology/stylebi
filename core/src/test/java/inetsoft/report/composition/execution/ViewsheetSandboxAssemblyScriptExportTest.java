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

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76765 (SSL-004b): one assembly whose script throws failed the whole export. The script
 * runs twice on the export path, in {@code prepareSheet} and, for a table, crosstab or calc
 * table, again when the write loop reads the table lens. Both must tolerate the failure, write
 * every other assembly and name the broken one on the document; a stopped script must still
 * propagate.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ViewsheetSandboxAssemblyScriptExportTest {
   private static final String THROWS = "throw 'ZZBOOM';";
   private static final String OK = "text = 'hello-ok';";

   @Test
   void throwingCrosstabScriptStillExportsEverythingElse() throws Exception {
      ViewsheetSandbox box = box();
      Viewsheet vs = box.getViewsheet();
      script(crosstab(vs, "BadCross"), THROWS);
      crosstab(vs, "GoodCross");
      script(text(vs, "OkText"), OK);

      Recorder rec = export(box);

      assertTrue(rec.crosstabs.contains("GoodCross"), () -> "written: " + rec.crosstabs);
      assertTrue(rec.crosstabs.contains("BadCross"), () -> "written: " + rec.crosstabs);
      assertTrue(rec.texts.contains("hello-ok"), () -> "texts: " + rec.texts);
      assertEquals(1, warnings(rec, "BadCross"), () -> "texts: " + rec.texts);
      assertEquals(0, warnings(rec, "GoodCross"));
      assertTrue(box.getExportFailedAssemblies().isEmpty(), "nothing is remembered past the export");
   }

   // the viewer shares the sandbox: after an export, a fixed script must run again
   @Test
   void throwingTimeSliderScriptStillExportsEverythingElse() throws Exception {
      ViewsheetSandbox box = box();
      Viewsheet vs = box.getViewsheet();
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "BadSlider");
      slider.setTableName("TableView1");
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "wind"));
      ref.setDataType(XSchema.DOUBLE);
      SingleTimeInfo tinfo = new SingleTimeInfo();
      tinfo.setDataRef(ref);
      tinfo.setRangeTypeValue(TimeInfo.NUMBER);
      slider.setTimeInfo(tinfo);
      vs.addAssembly(slider);
      script(slider, THROWS);
      script(text(vs, "OkText"), OK);

      Recorder rec = export(box);

      assertTrue(rec.texts.contains("hello-ok"), () -> "texts: " + rec.texts);
      assertEquals(1, warnings(rec, "BadSlider"), () -> "texts: " + rec.texts);
   }

   // direct: the query's own script run is reached (it throws outside an export) and tolerated
   // during one
   @Test
   void selectionAndTimeSliderQueryScriptsAreTolerantOnlyDuringExport() throws Exception {
      ViewsheetSandbox box = box();
      Viewsheet vs = box.getViewsheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "QList");
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) list.getInfo();
      info.setTableName("TableView1");
      info.setDataRef(new ColumnRef(new AttributeRef(null, "type")));
      vs.addAssembly(list);
      script(list, THROWS + " // query");
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "QSlider");
      slider.setTableName("TableView1");
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "wind"));
      ref.setDataType(XSchema.DOUBLE);
      SingleTimeInfo tinfo = new SingleTimeInfo();
      tinfo.setDataRef(ref);
      tinfo.setRangeTypeValue(TimeInfo.NUMBER);
      slider.setTimeInfo(tinfo);
      vs.addAssembly(slider);
      script(slider, THROWS);

      assertThrows(ScriptException.class, () -> new SelectionListVSAQuery(box, "QList").getData());
      assertThrows(ScriptException.class, () -> new TimeSliderVSAQuery(box, "QSlider").getData());

      box.beginExport();

      try {
         tolerated(() -> new SelectionListVSAQuery(box, "QList").getData());
         tolerated(() -> new TimeSliderVSAQuery(box, "QSlider").getData());
      }
      finally {
         box.endExport();
      }
   }

   private static void tolerated(ThrowingRunnable run) {
      try {
         run.run();
      }
      catch(ScriptException ex) {
         fail("the script error was not tolerated", ex);
      }
      catch(Throwable ignored) {
         // the query itself may fail on this minimal fixture, only the script matters here
      }
   }

   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   @Test
   void failedScriptRunsAgainAfterTheExport() throws Exception {
      ViewsheetSandbox box = box();
      Viewsheet vs = box.getViewsheet();
      script(crosstab(vs, "BadCross"), THROWS);
      export(box);

      script((VSAssembly) vs.getAssembly("BadCross"), "BadCross.title = 'FIXED';");
      box.resetDataMap("BadCross");
      assertNotNull(box.getVSTableLens("BadCross", false, 1));
      assertEquals("FIXED", ((inetsoft.uql.viewsheet.internal.TitledVSAssemblyInfo)
                         ((VSAssembly) vs.getAssembly("BadCross")).getVSAssemblyInfo()).getTitle(), "the script ran");

      // and a script that still fails still fails in the viewer, outside an export
      script((VSAssembly) vs.getAssembly("BadCross"), THROWS);
      box.resetDataMap("BadCross");
      assertThrows(ScriptException.class, () -> box.getVSTableLens("BadCross", false, 1));
   }

   @Test
   void throwingSelectionListScriptStillExportsEverythingElse() throws Exception {
      ViewsheetSandbox box = box();
      Viewsheet vs = box.getViewsheet();
      SelectionListVSAssembly selection = new SelectionListVSAssembly(vs, "BadList");
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) selection.getInfo();
      info.setTableName("TableView1");
      info.setDataRef(new ColumnRef(new AttributeRef(null, "type")));
      vs.addAssembly(selection);
      script(selection, THROWS + " // query");
      script(text(vs, "OkText"), OK);

      Recorder rec = export(box);

      assertTrue(rec.texts.contains("hello-ok"), () -> "texts: " + rec.texts);
      assertEquals(1, warnings(rec, "BadList"), () -> "texts: " + rec.texts);
   }

   @Test
   void throwingTableScriptStillExportsEverythingElse() throws Exception {
      ViewsheetSandbox box = box();
      Viewsheet vs = box.getViewsheet();
      script((VSAssembly) vs.getAssembly("TableView1"), THROWS);
      crosstab(vs, "GoodCross");

      Recorder rec = export(box);

      assertTrue(rec.crosstabs.contains("GoodCross"), () -> "written: " + rec.crosstabs);
      assertEquals(1, warnings(rec, "TableView1"), () -> "texts: " + rec.texts);
   }

   @Test
   void throwingChartScriptStillExportsEverythingElse() throws Exception {
      ViewsheetSandbox box = box();
      Viewsheet vs = box.getViewsheet();
      script(chart(vs, "BadChart"), THROWS);
      script(text(vs, "OkText"), OK);

      Recorder rec = export(box);

      assertTrue(rec.texts.contains("hello-ok"), () -> "texts: " + rec.texts);
      assertEquals(1, warnings(rec, "BadChart"), () -> "texts: " + rec.texts);
   }

   @Test
   void noFailureNoWarningAndSecondExportWarnsOnce() throws Exception {
      ViewsheetSandbox box = box();
      Viewsheet vs = box.getViewsheet();
      script(text(vs, "OkText"), OK);

      assertEquals(0, anyWarning(export(box)));

      script(text(vs, "BadText"), THROWS);
      assertEquals(1, warnings(export(box), "BadText"));
      assertEquals(1, warnings(export(box), "BadText"));
   }

   @Test
   void stoppedScriptStillPropagates() throws Exception {
      ViewsheetSandbox spy = Mockito.spy(box());
      Viewsheet vs = spy.getViewsheet();
      VSAssembly text = script(text(vs, "StoppedText"), OK);
      spy.beginExport();
      ScriptException stop = new ScriptException("timeout");
      stop.setStopped(true);
      Mockito.doThrow(stop).when(spy).executeScript(text);

      assertSame(stop, assertThrows(ScriptException.class, () -> spy.executeScriptForExport(text)));
      assertTrue(spy.getExportFailedAssemblies().isEmpty());
   }

   @Test
   void ordinaryScriptErrorIsRecordedNotThrown() throws Exception {
      ViewsheetSandbox spy = Mockito.spy(box());
      Viewsheet vs = spy.getViewsheet();
      VSAssembly text = script(text(vs, "FailedText"), OK);
      spy.beginExport();
      Mockito.doThrow(new ScriptException("boom")).when(spy).executeScript(text);

      assertEquals(VSAssembly.NONE_CHANGED, spy.executeScriptForExport(text));
      assertEquals(List.of("FailedText"), spy.getExportFailedAssemblies());
      spy.beginExport(); // a second, still running export
      spy.endExport();
      assertEquals(List.of("FailedText"), spy.getExportFailedAssemblies(), "nested export ended");
      spy.endExport();
      assertTrue(spy.getExportFailedAssemblies().isEmpty());
   }

   // outside an export (viewer, composer) a script error still propagates
   @Test
   void scriptErrorOutsideExportStillThrows() throws Exception {
      ViewsheetSandbox spy = Mockito.spy(box());
      VSAssembly text = script(text(spy.getViewsheet(), "ViewerText"), OK);
      Mockito.doThrow(new ScriptException("boom")).when(spy).executeScript(text);

      assertThrows(ScriptException.class, () -> spy.executeScriptForExport(text));
      assertTrue(spy.getExportFailedAssemblies().isEmpty());
   }

   private ViewsheetSandbox box() {
      return vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
   }

   private static Recorder export(ViewsheetSandbox box) throws Exception {
      Recorder exporter = new Recorder();
      exporter.export(box, "Current View", 0, null);
      return exporter;
   }

   private static long warnings(Recorder rec, String name) {
      String message = Catalog.getCatalog().getString("vs.export.assemblyScriptFailed", name);
      return rec.texts.stream().filter(t -> t != null && t.contains(message)).count();
   }

   private static long anyWarning(Recorder rec) {
      String message = Catalog.getCatalog().getString("vs.export.assemblyScriptFailed", "");
      String prefix = message.substring(0, message.indexOf("  "));
      return rec.texts.stream().filter(t -> t != null && t.contains(prefix)).count();
   }

   private static VSAssembly script(VSAssembly assembly, String script) {
      assembly.getVSAssemblyInfo().setScriptEnabled(true);
      assembly.getVSAssemblyInfo().setScript(script);
      return assembly;
   }

   private static TextVSAssembly text(Viewsheet vs, String name) {
      TextVSAssembly text = new TextVSAssembly(vs, name);
      vs.addAssembly(text);
      return text;
   }

   private static SourceInfo source() {
      return new SourceInfo(SourceInfo.VS_ASSEMBLY, null, Assembly.TABLE_VS_BOUND + "TableView1");
   }

   private static CrosstabVSAssembly crosstab(Viewsheet vs, String name) {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
      crosstab.setSourceInfo(source());
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      cinfo.setDesignRowHeaders(new DataRef[] { dim });
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      cinfo.setDesignAggregates(new DataRef[] { agg });
      vs.addAssembly(crosstab);
      return crosstab;
   }

   private static ChartVSAssembly chart(Viewsheet vs, String name) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo(source());
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);
      return chart;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private static final class Recorder extends CSVVSExporter {
      @Override
      protected void writeText(VSAssembly assembly, String txt) {
         texts.add(txt);
      }

      @Override
      protected void writeCrosstab(CrosstabVSAssembly assembly, VSTableLens lens) {
         crosstabs.add(assembly.getName());
      }

      @Override
      protected void writeTable(TableVSAssembly assembly, VSTableLens lens) {
         tables.add(assembly.getName());
      }

      private final List<String> texts = new ArrayList<>();
      private final List<String> crosstabs = new ArrayList<>();
      private final List<String> tables = new ArrayList<>();
   }
}

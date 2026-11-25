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

import inetsoft.report.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.uql.util.TableLoadException;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Timeout;
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81796: the failing component script of a data component (Table, Crosstab, CalcTable,
 * TimeSlider) must not fail the export either. The script runs again from the data path after
 * the loop in prepareSheet, so a catch at the loop alone leaves the export failing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/graph/GraphRenderTest.zip")
@Tag("slow")
@Tag("integration")
class AbstractVSExporterScriptFailureDataTest {
   private static final String BAD = "noSuchFunction123();";
   private static final String COUNTED =
      "var n = java.lang.System.getProperty('p3.n'); " +
      "java.lang.System.setProperty('p3.n', String((n == null ? 0 : parseInt(n)) + 1)); " +
      "noSuchFunction123();";

   /** Control: the same assemblies with no script must export (validates the harness). */
   @ParameterizedTest(name = "control {0}, match layout {1}")
   @CsvSource({
      "text,false", "table,false", "table,true", "crosstab,false", "crosstab,true",
      "calc,false", "calc,true", "timeslider,false"
   })
   void controlWithoutScriptExports(String kind, boolean match) throws Exception {
      ByteArrayOutputStream out = exportCsv(kind, "", match);
      assertTrue(out.size() > 0, "the export should be written");
      // a successful export is unchanged: nothing skipped, no warning on the document
      assertEquals(List.of(), skipWarnings(), "no script was skipped");
      assertNull(lastBox.getExportSkippedScript());
      assertNull(warningText(lastExporter), "no warning text on the document");
   }

   /** The bug: a failing script of the component must not fail the export. */
   @ParameterizedTest(name = "{0}, match layout {1}")
   @CsvSource({
      "text,false", "text,true", "table,false", "table,true", "crosstab,false", "crosstab,true",
      "calc,false", "calc,true", "timeslider,false", "timeslider,true"
   })
   void failingComponentScriptDoesNotFailTheExport(String kind, boolean match) throws Exception {
      ByteArrayOutputStream out = exportCsv(kind, BAD, match);
      assertTrue(out.size() > 0, "the export should be written");
      // the export went on because the failure was skipped (logged by assembly), and the
      // document says so
      List<String> skips = skipWarnings();
      assertFalse(skips.isEmpty(), "the skip should be logged");
      assertTrue(skips.get(0).contains(kind.equals("text") ? "Text81796" : "81796"), skips.get(0));
      assertTrue(skips.get(0).contains("noSuchFunction123"), skips.get(0));
      String warning = warningText(lastExporter);
      assertNotNull(warning, "the document should carry a warning");
      assertTrue(warning.contains("component script failed"), warning);
   }

   /**
    * The scheduler, the Portal and the email export read every table (CSVUtil.hasLargeDataTable,
    * errors ignored) before the export starts, outside any export scope. That failed read must
    * not leave a cached NULL that the export then trips over.
    */
   @ParameterizedTest(name = "pre-read {0}, match layout {1}")
   @CsvSource({
      "table,false", "table,true", "crosstab,false", "crosstab,true", "calc,false", "calc,true"
   })
   void failingScriptReadBeforeTheExportDoesNotBreakIt(String kind, boolean match)
      throws Exception
   {
      ViewsheetSandbox box = open(kind, BAD);
      // the real pre-read; it swallows the failure itself
      CSVUtil.hasLargeDataTable(viewsheetResource.getRuntimeViewsheet());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVVSExporter exporter = new CSVVSExporter(out, null);
      exporter.setMatchLayout(match);
      lastExporter = exporter;
      lastBox = box;

      try {
         exporter.export(box, "Current View", 0, null);
         exporter.write();
      }
      catch(Throwable ex) {
         System.out.println("P7-CAUGHT preread " + kind + "/" + match + " " +
                            ex.getClass().getName() + " <- " +
                            String.join(" <- ", topFrames(ex, 8)));
         throw ex;
      }

      assertTrue(out.size() > 0, "the export should be written");
      assertFalse(skipWarnings().isEmpty(), "the skip should be logged");
      String warning = warningText(exporter);
      assertNotNull(warning, "the document should carry a warning");
      assertTrue(warning.contains("component script failed"), warning);
   }

   /** A read that fails inside the scope is skipped and must not cache NULL for the next read. */
   @ParameterizedTest(name = "second read {0}")
   @CsvSource({ "table", "crosstab", "calc" })
   void failedReadInsideTheScopeIsNotCached(String kind) throws Exception {
      ViewsheetSandbox box = open(kind, BAD);
      String name = kind.equals("table") ? "Table81796" :
         kind.equals("crosstab") ? "Crosstab81796" : "Calc81796";

      try(ViewsheetSandbox.ExportScriptSkip skip = ViewsheetSandbox.skipScriptErrorsForExport()) {
         assertNotNull(box.getVSTableLens(name, false), "first read");
         assertNotNull(box.getVSTableLens(name, false), "second read");
      }
   }

   /** Outside an export scope the same failure still throws (the viewer is unchanged). */
   @ParameterizedTest(name = "outside export scope {0}")
   @CsvSource({ "text", "table", "crosstab", "calc", "timeslider" })
   void failingScriptOutsideAnExportStillThrows(String kind) throws Exception {
      ViewsheetSandbox box = open(kind, BAD);
      VSAssembly assembly = (VSAssembly) box.getViewsheet().getAssembly(
         kind.equals("text") ? "Text81796" : kind.equals("table") ? "Table81796" :
         kind.equals("crosstab") ? "Crosstab81796" : kind.equals("calc") ? "Calc81796" :
         "Slider81796");
      assertThrows(ScriptException.class, () -> box.executeScript(assembly));
   }

   /** The skip is scoped: after the scope closes the failure throws again. */
   @org.junit.jupiter.api.Test
   void skipScopeEndsWithTheExport() throws Exception {
      ViewsheetSandbox box = open("text", BAD);
      VSAssembly assembly = (VSAssembly) box.getViewsheet().getAssembly("Text81796");

      try(ViewsheetSandbox.ExportScriptSkip skip = ViewsheetSandbox.skipScriptErrorsForExport()) {
         assertDoesNotThrow(() -> box.executeScript(assembly));
      }

      assertThrows(ScriptException.class, () -> box.executeScript(assembly));
   }

   /**
    * Stop case: a script stopped by its timeout must still fail the export (guard test).
    * Run with a 1 second script timeout.
    */
   @ParameterizedTest(name = "stop {0}")
   @CsvSource({ "text", "table" })
   @Timeout(90)
   void timedOutScriptStillFailsTheExport(String kind) throws Exception {
      String previous = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "1");
      refreshTimeout();

      try {
         Throwable ex = assertThrows(Throwable.class,
            () -> exportCsv(kind, "while(true){}", false),
            "a timed out script must fail the export, not be skipped");
         assertInstanceOf(ScriptException.class, ex);
         assertTrue(ScriptTimeoutGuard.isStop(ex), "the failure is a stop: " + ex);
      }
      finally {
         if(previous == null) {
            SreeEnv.remove("script.execution.timeout");
         }
         else {
            SreeEnv.setProperty("script.execution.timeout", previous);
         }

         refreshTimeout();
      }
   }

   // the engine caches the property for 10 s
   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   /** Java object a script calls to get a TableLoadException, like a read of a failed table. */
   public static class Thrower {
      public void go() {
         throw new TableLoadException("p3 load failed", null);
      }
   }

   /**
    * A TableLoadException thrown from the script (stand-in for a script that reads a table whose
    * query failed, #78518/#78445) must still fail the export (guard test).
    */
   @ParameterizedTest(name = "table load failure {0}")
   @CsvSource({ "text", "table" })
   void tableLoadFailureStillFailsTheExport(String kind) throws Exception {
      ViewsheetSandbox box = open(kind, "p3thrower.go();");
      box.getScope().putMember("p3thrower", new Thrower());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVVSExporter exporter = new CSVVSExporter(out, null);

      Throwable ex = assertThrows(Throwable.class, () -> {
         exporter.export(box, "Current View", 0, null);
         exporter.write();
      }, "a failed table load must fail the export");
      System.out.println("P3-TLE " + kind + " " + ex.getClass().getName() + " tle=" +
                         (TableLoadException.find(ex) != null));
   }

   /** Probe: the data path of a time slider read directly (CSV does not write time sliders). */
   @ParameterizedTest(name = "slider data path {0}")
   @CsvSource({ "noSuchFunction123();" })
   void timeSliderDataPathDoesNotThrow(String script) throws Exception {
      ViewsheetSandbox box = open("timeslider", script);

      // a probe of the data path under the export scope, NOT an export: CSV does not write time
      // sliders, so this is the closest a core test gets to the TimeSliderVSAQuery re-run
      try(ViewsheetSandbox.ExportScriptSkip skip = ViewsheetSandbox.skipScriptErrorsForExport()) {
         box.getData("Slider81796");
      }
      catch(Throwable ex) {
         System.out.println("P3-CAUGHT slider-getData " + ex.getClass().getName() + " <- " +
                            String.join(" <- ", topFrames(ex, 12)));
         throw ex;
      }
   }

   private ByteArrayOutputStream exportCsv(String kind, String script, boolean match)
      throws Exception
   {
      ViewsheetSandbox box = open(kind, script);
      lastBox = box;
      lastExporter = null;
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVVSExporter exporter = new CSVVSExporter(out, null);
      exporter.setMatchLayout(match);
      lastExporter = exporter;

      try {
         exporter.export(box, "Current View", 0, null);
         exporter.write();
      }
      catch(Throwable ex) {
         System.out.println("P3-CAUGHT " + kind + "/" + match + "/" + script.length() + " " +
                            ex.getClass().getName() + " <- " +
                            String.join(" <- ", topFrames(ex, 12)));
         throw ex;
      }

      return out;
   }

   private ViewsheetSandbox lastBox;
   private AbstractVSExporter lastExporter;
   private ListAppender<ILoggingEvent> logs;

   @BeforeEach
   void captureLog() {
      logs = new ListAppender<>();
      logs.start();
      ((Logger) LoggerFactory.getLogger(ViewsheetSandbox.class)).addAppender(logs);
   }

   @AfterEach
   void releaseLog() {
      ((Logger) LoggerFactory.getLogger(ViewsheetSandbox.class)).detachAppender(logs);
   }

   private List<String> skipWarnings() {
      return logs.list.stream()
         .filter(e -> e.getLevel() == Level.WARN)
         .map(ILoggingEvent::getFormattedMessage)
         .filter(m -> m.startsWith("Skipped the failing script"))
         .toList();
   }

   // the exporter's own (cloned) viewsheet is where the warning text is written
   private static String warningText(AbstractVSExporter exporter) {
      TextVSAssembly text = exporter.viewsheet.getWarningTextAssembly(false);
      return text == null ? null : text.getText();
   }

   static String[] topFrames(Throwable ex, int n) {
      StackTraceElement[] st = ex.getStackTrace();
      String[] r = new String[Math.min(n, st.length)];

      for(int i = 0; i < r.length; i++) {
         r[i] = st[i].getClassName().replaceAll(".*\\.", "") + "." + st[i].getMethodName() + ":" +
            st[i].getLineNumber();
      }

      return r;
   }

   /** Add the assembly of {@code kind} carrying {@code script} to the opened viewsheet. */
   ViewsheetSandbox open(String kind, String script) throws Exception {
      ViewsheetSandbox box = viewsheetResource.getRuntimeViewsheet().getViewsheetSandbox()
         .orElseThrow();
      Viewsheet vs = box.getViewsheet();
      VSAssembly assembly = createAssembly(vs, kind);
      assembly.getVSAssemblyInfo().setScript(script);
      assembly.getVSAssemblyInfo().setScriptEnabled(true);
      vs.addAssembly(assembly);
      return box;
   }

   private static ColumnRef col(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType("string");
      return ref;
   }

   static VSAssembly createAssembly(Viewsheet vs, String kind) {
      SourceInfo source = new SourceInfo(SourceInfo.ASSET, null, "RegionalProductSales");

      switch(kind) {
      case "text":
         return new TextVSAssembly(vs, "Text81796");
      case "table": {
         TableVSAssembly table = new TableVSAssembly(vs, "Table81796");
         table.setSourceInfo(source);
         ColumnSelection cols = new ColumnSelection();
         cols.addAttribute(col("Region"));
         cols.addAttribute(col("Category"));
         ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).setColumnSelection(cols);
         return table;
      }
      case "crosstab": {
         CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab81796");
         crosstab.setSourceInfo(source);
         VSCrosstabInfo cinfo = new VSCrosstabInfo();
         cinfo.setDesignRowHeaders(new DataRef[] { new VSDimensionRef(col("Region")) });
         cinfo.setDesignColHeaders(new DataRef[] { new VSDimensionRef(col("Category")) });
         VSAggregateRef aggr = new VSAggregateRef();
         aggr.setDataRef(col("Total"));
         aggr.setFormula(AggregateFormula.COUNT_ALL);
         cinfo.setDesignAggregates(new DataRef[] { aggr });
         crosstab.setVSCrosstabInfo(cinfo);
         return crosstab;
      }
      case "calc": {
         CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc81796");
         calc.setSourceInfo(source);
         TableLayout layout = calc.getTableLayout();
         layout.setColCount(2);
         TableCellBinding region = TableCellBinding.getGroupBinding("Region");
         region.setExpansion(GroupableCellBinding.EXPAND_V);
         layout.setCellBinding(1, 0, region);
         TableCellBinding total = new TableCellBinding(CellBinding.BIND_COLUMN, "Total");
         total.setBType(CellBinding.SUMMARY);
         total.setFormula("Sum");
         total.setCellName("T");
         total.setRowGroup("Region");
         layout.setCellBinding(1, 1, total);
         return calc;
      }
      case "timeslider": {
         TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "Slider81796");
         slider.setTableName("RegionalProductSales");
         SingleTimeInfo tinfo = new SingleTimeInfo();
         tinfo.setDataRef(col("Region"));
         slider.setTimeInfo(tinfo);
         return slider;
      }
      default:
         throw new IllegalArgumentException(kind);
      }
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^TEST_GraphRender");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());
}

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
package inetsoft.web.wiz.dispatch;

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82316 on a real sandbox: an assemblyMain script of a Text and of a Chart that fails during
 * a viewsheet reset (what a render and a refresh both run) lands in the console buffer as INFO,
 * with no refresh warning collector in play.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ConsoleMessageCaptureIntegrationTest {
   @Test
   void aChartScriptFailureDuringAResetIsRecordedUnderTheScope() throws Exception {
      Viewsheet vs = box().getViewsheet();
      addScripted(new ChartVSAssembly(vs, "B5Chart"), vs, "var x = noSuchFunctionB7();");

      ConsoleMessageCapture.scope(runtimeId(), () -> {
         reset();
         return null;
      });

      ConsoleMessageBuffer.Snapshot snapshot =
         ConsoleMessageCapture.buffer(runtimeId()).read(null, 200, 0);
      assertEquals(1, snapshot.entries().size(), snapshot.entries().toString());
      ConsoleMessageBuffer.Entry entry = snapshot.entries().get(0);
      assertEquals("INFO", entry.type());
      assertEquals("B5Chart", entry.assembly());
      assertTrue(entry.message().contains("noSuchFunctionB7 is not defined"), entry.message());
   }

   /** Positive control: the same reset with no scope records nothing, the original symptom. */
   @Test
   void theSameResetWithoutAScopeRecordsNothing() throws Exception {
      Viewsheet vs = box().getViewsheet();
      addScripted(new ChartVSAssembly(vs, "B5Chart"), vs, "var x = noSuchFunctionB7();");

      try {
         reset();
      }
      finally {
         inetsoft.report.composition.WorksheetService.ASSET_EXCEPTIONS.remove();
      }

      assertEquals(0, ConsoleMessageCapture.buffer(runtimeId()).read(null, 200, 0).entries().size());
   }

   @Test
   void aTextScriptFailureAndANestedRefreshCollectorBothFeedTheBuffer() throws Exception {
      Viewsheet vs = box().getViewsheet();
      addScripted(new TextVSAssembly(vs, "T"), vs, "text = missingFn(value);");

      ConsoleMessageCapture.scope(runtimeId(), () ->
         AssetScriptFailureWarnings.collect(() -> {
            reset();
            return new java.util.ArrayList<>();
         }));

      ConsoleMessageBuffer.Snapshot snapshot =
         ConsoleMessageCapture.buffer(runtimeId()).read(null, 200, 0);
      assertEquals(1, snapshot.entries().size(), snapshot.entries().toString());
      assertEquals("T", snapshot.entries().get(0).assembly());
   }

   /**
    * F1 (live verification): a chart whose own assemblyMain script fails is rendered past it, and
    * the failure is reported by {@code VGraphPair} only as a user message, not through
    * ASSET_EXCEPTIONS, so a scope that watched only the exception list recorded nothing for
    * {@code get_viewsheet_image target=<chart>}. The graph pair is built the way
    * {@code getAssemblyImage} builds it (export, on the caller's thread), on a real chart.
    */
   @Test
   void aChartsOwnScriptFailureWhileItsGraphIsBuiltIsRecordedUnderTheScope() throws Exception {
      Viewsheet vs = box().getViewsheet();
      addChart(vs, "B5Chart");
      vs.getAssembly("B5Chart").getVSAssemblyInfo().setScript("var x = noSuchFunctionB7();");
      vs.getAssembly("B5Chart").getVSAssemblyInfo().setScriptEnabled(true);
      box().resetDataMap("B5Chart");

      ConsoleMessageCapture.scope(runtimeId(), () -> {
         var pair = box().getVGraphPair("B5Chart", false, null, true, 1);
         assertTrue(pair.isPlotted(), "the chart still renders past its script failure");
         return null;
      });

      ConsoleMessageBuffer.Snapshot snapshot =
         ConsoleMessageCapture.buffer(runtimeId()).read(null, 200, 0);
      assertEquals(1, snapshot.entries().size(), snapshot.entries().toString());
      ConsoleMessageBuffer.Entry entry = snapshot.entries().get(0);
      assertEquals("INFO", entry.type());
      assertEquals("B5Chart", entry.assembly());
      assertTrue(entry.message().startsWith("Script execution error in assembly: B5Chart"),
                 entry.message());
      assertTrue(entry.message().contains("noSuchFunctionB7 is not defined"), entry.message());
      inetsoft.util.Tool.clearUserMessage();
   }

   /** The same build outside a scope records nothing; messages pending beforehand are not rows. */
   @Test
   void aUserMessageThatWasAlreadyPendingWhenTheScopeStartedIsNotARow() throws Exception {
      inetsoft.util.Tool.addUserMessage("left over from earlier work");

      try {
         ConsoleMessageCapture.scope(runtimeId(), () -> null);
         assertEquals(0, ConsoleMessageCapture.buffer(runtimeId()).read(null, 50, 0).entries().size());
      }
      finally {
         inetsoft.util.Tool.clearUserMessage();
      }
   }

   private static void addChart(Viewsheet vs, String name) {
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly("TableView1");
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo((inetsoft.uql.asset.SourceInfo) table.getSourceInfo().clone());
      var info = chart.getVSChartInfo();
      var dim = new inetsoft.uql.viewsheet.graph.VSChartDimensionRef(
         new inetsoft.uql.asset.ColumnRef(new inetsoft.uql.erm.AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      info.addXField(dim);
      var agg = new inetsoft.uql.viewsheet.graph.VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);
   }

   /** The sandbox sets a list on the test thread when none is scoped; leave none for the next test. */
   @org.junit.jupiter.api.AfterEach
   void clearThreadLocals() {
      inetsoft.report.composition.WorksheetService.ASSET_EXCEPTIONS.remove();
      inetsoft.util.Tool.clearUserMessage();
   }

   private void reset() throws Exception {
      ViewsheetSandbox box = box();
      box.reset(null, box.getViewsheet().getAssemblies(), new ChangedAssemblyList(), true, true,
                null);
   }

   private String runtimeId() {
      return vsResource.getRuntimeViewsheet().getID();
   }

   private ViewsheetSandbox box() {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      return rvs.getViewsheetSandbox().orElseThrow();
   }

   private static void addScripted(VSAssembly assembly, Viewsheet vs, String script) {
      assembly.getVSAssemblyInfo().setScript(script);
      assembly.getVSAssemblyInfo().setScriptEnabled(true);
      vs.addAssembly(assembly);
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());
}

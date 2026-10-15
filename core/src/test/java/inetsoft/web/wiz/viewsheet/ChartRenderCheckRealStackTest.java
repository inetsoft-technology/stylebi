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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.XCondition;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78439: a wiz chart write returned a clean success while the chart's data fetch threw a
 * script error, because nothing in the write builds the chart. {@link ChartRenderCheck} builds it
 * once after the write and reports the {@code ScriptException} as a warning. Runs on the real
 * stack (EmbeddedVS1, a chart whose JS pre-condition throws, the real {@code CoreLifecycleService}
 * and capturing dispatcher), inside the same capturing scope {@code mutate} uses.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ChartRenderCheckRealStackTest {
   /** The lifecycle execute a data-changing write performs does not report it; the check does. */
   @Test
   void invalidatingWriteOnABrokenChartWarnsOnce() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true);

      List<String> withoutCheck = capture(rvs, (box, d) ->
         lifecycle.execute(rvs, CHART, "", VSAssembly.BINDING_CHANGED, d));
      assertEquals(List.of(), withoutCheck, "the write alone is silent (the bug)");

      List<String> warnings = capture(rvs, (box, d) -> {
         lifecycle.execute(rvs, CHART, "", VSAssembly.BINDING_CHANGED, d);
         ChartRenderCheck.warnIfScriptFails(rvs, CHART, d);
      });

      assertWarned(warnings);
   }

   /** A write that leaves the data alone, after a render already used up the one-shot failure. */
   @Test
   void nonInvalidatingWriteAfterTheOneShotFailureStillWarns() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      assertThrows(Exception.class, () -> box.getVGraphPair(CHART, true));
      assertDoesNotThrow(() -> box.getVGraphPair(CHART, true), "silent no-data after the one-shot");

      List<String> warnings = capture(rvs, (b, d) -> {
         b.clearGraph(CHART);
         b.resetDataMap(CHART);
         ChartRenderCheck.warnIfScriptFails(rvs, CHART, d);
      });

      assertWarned(warnings);
   }

   /** A repeated write on a still-broken chart warns again: the check re-armed the failure. */
   @Test
   void repeatedWriteOnAStillBrokenChartWarnsEachTime() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true);

      for(int i = 0; i < 3; i++) {
         assertWarned(capture(rvs, (box, d) -> ChartRenderCheck.warnIfScriptFails(rvs, CHART, d)));
      }
   }

   /** The native chart-areas request that follows the write still throws, so it shows its error. */
   @Test
   void theFailureIsStillThereForTheNativeRenderAfterTheWarning() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true);

      assertWarned(capture(rvs, (box, d) -> ChartRenderCheck.warnIfScriptFails(rvs, CHART, d)));

      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Exception failure = assertThrows(Exception.class, () -> box.getVGraphPair(CHART, true));
      Throwable root = rootOf(failure);
      assertInstanceOf(ScriptException.class, root, failure.toString());
      assertTrue(String.valueOf(root.getMessage()).contains("undefinedFn"), root.toString());
   }

   /**
    * The Composer renders between two wiz writes (a broadcast follows every write) and uses up the
    * re-armed failure; later non-invalidating writes must still warn.
    */
   @Test
   void aNativeRenderBetweenWritesDoesNotSilenceTheNextWrites() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();

      assertWarned(capture(rvs, (b, d) -> ChartRenderCheck.warnIfScriptFails(rvs, CHART, d)));

      for(int i = 0; i < 2; i++) {
         assertThrows(Exception.class, () -> box.getVGraphPair(CHART, true),
                      "the native render still shows the error");
         assertWarned(capture(rvs, (b, d) -> ChartRenderCheck.warnIfScriptFails(rvs, CHART, d)));
      }

      assertTrue(ChartRenderCheck.hasFailed(box, CHART));
   }

   /** Once the script is fixed the next write is silent and the failure record is gone. */
   @Test
   void fixingTheScriptEndsTheWarningsAndClearsTheRecord() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      assertWarned(capture(rvs, (b, d) -> ChartRenderCheck.warnIfScriptFails(rvs, CHART, d)));
      assertThrows(Exception.class, () -> box.getVGraphPair(CHART, true));
      assertTrue(ChartRenderCheck.hasFailed(box, CHART));

      ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly(CHART);
      chart.setPreConditionList(new ConditionList());
      box.resetDataMap(CHART);

      assertEquals(List.of(), capture(rvs, (b, d) ->
         ChartRenderCheck.warnIfScriptFails(rvs, CHART, d)));
      assertFalse(ChartRenderCheck.hasFailed(box, CHART));
   }

   /** A chart that is merely empty is never rebuilt: no record, no second build, no warning. */
   @Test
   void anEmptyHealthyChartIsNeitherRebuiltNorWarned() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, false);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly(CHART);
      TableVSAssembly table = (TableVSAssembly) rvs.getViewsheet().getAssembly(TABLE);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setType(XSchema.STRING);
      cond.addValue("no such type");
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(table.getColumnSelection().getAttribute("type"), cond, 0));
      chart.setPreConditionList(list);
      box.resetDataMap(CHART);

      assertEquals(List.of(), capture(rvs, (b, d) ->
         ChartRenderCheck.warnIfScriptFails(rvs, CHART, d)));
      VGraphPair first = box.getVGraphPair(CHART, true);
      assertEquals(List.of(), capture(rvs, (b, d) ->
         ChartRenderCheck.warnIfScriptFails(rvs, CHART, d)));
      assertSame(first, box.getVGraphPair(CHART, true), "no rebuild for a chart with no failure");
      assertFalse(ChartRenderCheck.hasFailed(box, CHART));
   }

   /** A healthy chart yields no warning and the pair it built is the one the next consumer gets. */
   @Test
   void healthyChartIsSilentAndItsPairIsReused() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, false);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();

      List<String> warnings =
         capture(rvs, (b, d) -> ChartRenderCheck.warnIfScriptFails(rvs, CHART, d));

      assertEquals(List.of(), warnings);
      VGraphPair first = box.getVGraphPair(CHART, true);
      assertSame(first, box.getVGraphPair(CHART, true), "the pair is cached, not rebuilt");
   }

   /**
    * A chart that still builds (a viewsheet calc field that throws is swallowed by the engine, and
    * the chart plots) is not reported: there is nothing to catch at build time.
    */
   @Test
   void aChartThatStillBuildsDoesNotWarn() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, false);
      VSChartInfo info = ((ChartVSAssembly) rvs.getViewsheet().getAssembly(CHART)).getVSChartInfo();
      assertEquals(1, info.getYFields().length);

      assertEquals(List.of(), capture(rvs, (box, d) ->
         ChartRenderCheck.warnIfChartScriptFails(rvs, CHART, d)));
   }

   /** The chart-only entry point leaves a non-chart assembly alone. */
   @Test
   void chartOnlyEntryPointSkipsATable() throws Exception {
      RuntimeViewsheet rvs = open();

      assertEquals(List.of(), capture(rvs, (box, d) ->
         ChartRenderCheck.warnIfChartScriptFails(rvs, TABLE, d)));
   }

   @Test
   void warningIsRecognisedByItsPrefix() {
      assertTrue(ChartRenderCheck.isWarningFor("Chart 'Chart1' cannot be generated: x", "Chart1"));
      assertFalse(ChartRenderCheck.isWarningFor("Chart 'Chart1' cannot be generated: x", "Chart2"));
      assertFalse(ChartRenderCheck.isWarningFor(null, "Chart1"));
   }

   private static void assertWarned(List<String> warnings) {
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(ChartRenderCheck.isWarningFor(warnings.get(0), CHART), warnings.get(0));
      assertTrue(warnings.get(0).contains("undefinedFn"), warnings.get(0));
   }

   private static Throwable rootOf(Throwable e) {
      while(e instanceof RuntimeException && e.getCause() != null) {
         e = e.getCause();
      }

      return e;
   }

   /** Runs {@code body} under the capturing dispatcher, as {@code mutate} does. */
   private List<String> capture(RuntimeViewsheet rvs, Body body) throws Exception {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      return CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
         body.run(box, dispatcher);
         return dispatcher.getWarnings();
      });
   }

   private static void addChart(RuntimeViewsheet rvs, boolean failing) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(TABLE);
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo((SourceInfo) table.getSourceInfo().clone());
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

      if(failing) {
         DataRef type = table.getColumnSelection().getAttribute("type");
         ExpressionValue expression = new ExpressionValue();
         expression.setExpression("undefinedFn(1)");
         expression.setType(ExpressionValue.JAVASCRIPT);
         AssetCondition cond = new AssetCondition();
         cond.setOperation(XCondition.EQUAL_TO);
         cond.setType(XSchema.STRING);
         cond.addValue(expression);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(type, cond, 0));
         chart.setPreConditionList(list);
      }

      box.resetDataMap(CHART);
   }

   private RuntimeViewsheet open() {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      user = rvs.getUser();
      return rvs;
   }

   private XPrincipal principal() {
      return (XPrincipal) user;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @FunctionalInterface
   private interface Body {
      void run(ViewsheetSandbox box, CapturingCommandDispatcher dispatcher) throws Exception;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired CoreLifecycleService lifecycle;

   private Principal user;
   private static final String TABLE = "TableView1";
   private static final String CHART = "Chart1";
}

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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class ScriptReadServiceTest {
   private final ScriptReadService service = new ScriptReadService();

   private static ScriptTargetInfo find(List<ScriptTargetInfo> all, String kind, String assembly) {
      return all.stream()
         .filter(t -> t.kind().equals(kind))
         .filter(t -> assembly == null ? t.assembly() == null : assembly.equals(t.assembly()))
         .findFirst()
         .orElseThrow(() -> new AssertionError(
            "no target for kind=" + kind + " assembly=" + assembly + " in " + all));
   }

   /** A viewsheet with init/load text, a chart (main script only) and a text (main + onClick). */
   private static RuntimeViewsheet runtime() {
      ViewsheetInfo vsInfo = new ViewsheetInfo();
      vsInfo.setOnInit("a = 1");
      vsInfo.setOnLoad("");
      vsInfo.setScriptEnabled(true);

      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      ChartVSAssemblyInfo chartInfo = new ChartVSAssemblyInfo();
      chartInfo.setScript("chart()");
      chartInfo.setScriptEnabled(true);
      when(chart.getName()).thenReturn("Chart1");
      when(chart.getVSAssemblyInfo()).thenReturn(chartInfo);

      TextVSAssembly text = mock(TextVSAssembly.class);
      TextVSAssemblyInfo textInfo = new TextVSAssemblyInfo();
      textInfo.setScript("");
      textInfo.setOnClick("go()");
      textInfo.setScriptEnabled(false);
      when(text.getName()).thenReturn("Text1");
      when(text.getVSAssemblyInfo()).thenReturn(textInfo);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getViewsheetInfo()).thenReturn(vsInfo);
      when(vs.getAssemblies()).thenReturn(new Assembly[]{ chart, text });

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   @Test
   void everyTargetCarriesAnIdThatDecodesBackToItself() throws Exception {
      for(ScriptTargetInfo info : service.list(runtime())) {
         ScriptTarget back = ScriptTarget.fromId(info.id());
         assertEquals(info.kind(), back.kind().wireName(), info.toString());
         assertEquals(info.assembly(), back.assemblyName(), info.toString());
      }
   }

   @Test
   void namesTheKindsRatherThanTheDelimitedString() {
      List<ScriptTargetInfo> all = service.list(runtime());

      assertEquals("viewsheetOnInit", find(all, "viewsheetOnInit", null).kind());
      assertEquals("assembly:Chart1", find(all, "assemblyMain", "Chart1").target(),
                   "the v1 string stays, so an old plugin still reads what it expects");
   }

   @Test
   void reportsRunsWhenSoTheCallerNeedNotGuess() {
      List<ScriptTargetInfo> all = service.list(runtime());

      assertEquals("once, at viewsheet initialization",
                   find(all, "viewsheetOnInit", null).runsWhen());
      assertEquals("on every refresh", find(all, "viewsheetOnLoad", null).runsWhen());
      assertEquals("each time the assembly renders",
                   find(all, "assemblyMain", "Chart1").runsWhen());
      assertEquals("on user click", find(all, "assemblyOnClick", "Text1").runsWhen());
   }

   /**
    * The shared-enable-flag footgun, made explicit. onInit and onLoad share one viewsheet flag;
    * an assembly's main and onClick scripts share one assembly flag. Disabling "the onClick"
    * silently disables the assembly's main script too.
    */
   @Test
   void reportsWhichEnableFlagEachTargetShares() {
      List<ScriptTargetInfo> all = service.list(runtime());

      assertEquals("viewsheet", find(all, "viewsheetOnInit", null).enableScope());
      assertEquals("viewsheet", find(all, "viewsheetOnLoad", null).enableScope());
      assertEquals("assembly:Text1", find(all, "assemblyOnClick", "Text1").enableScope());
      assertEquals("assembly:Text1", find(all, "assemblyMain", "Text1").enableScope());
   }

   @Test
   void distinguishesHasScriptFromEnabled() {
      List<ScriptTargetInfo> all = service.list(runtime());

      assertTrue(find(all, "viewsheetOnInit", null).hasScript());
      assertFalse(find(all, "viewsheetOnLoad", null).hasScript(), "onLoad text is empty");
      assertTrue(find(all, "assemblyOnClick", "Text1").hasScript());
      assertFalse(find(all, "assemblyOnClick", "Text1").enabled(), "Text1's flag is off");
   }

   @Test
   void omitsOnClickForAssembliesThatCannotHaveOne() {
      List<ScriptTargetInfo> all = service.list(runtime());

      assertTrue(all.stream().noneMatch(
                    t -> "assemblyOnClick".equals(t.kind()) && "Chart1".equals(t.assembly())),
                 "charts have no onClick: " + all);
   }

   @Test
   void everyTargetIsHostedOnTheViewsheet() {
      assertTrue(service.list(runtime()).stream().allMatch(t -> "viewsheet".equals(t.hostSheet())));
   }

   @Test
   void advertisesOnlyTheKindsThisServerCanActuallyServe() {
      assertEquals(2, ScriptGrammar.VERSION);
      assertEquals(List.of("viewsheetOnInit", "viewsheetOnLoad", "assemblyMain", "assemblyOnClick",
                           "calcField"),
                   ScriptGrammar.supportedKinds());
      assertFalse(ScriptGrammar.supportedKinds().contains("worksheetExpression"),
                  "a reserved kind must not be advertised as servable");
   }

   private static CalculateRef calc(String name, String expression, boolean sql,
                                    boolean baseOnDetail)
   {
      ExpressionRef inner = new ExpressionRef();
      inner.setName(name);
      inner.setExpression(expression);
      // baseOnDetail is constructor-only; CalculateRef exposes no setter for it.
      CalculateRef ref = new CalculateRef(baseOnDetail);
      ref.setDataRef(inner);
      ref.setSQL(sql);
      return ref;
   }

   /** A viewsheet whose Query1 carries one JS calc field. */
   private static RuntimeViewsheet runtimeWithCalcField() {
      CalculateRef calc = calc("Margin", "field['PRICE'] - field['COST']", false, true);

      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      info.setScript("");
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getName()).thenReturn("Chart1");
      when(chart.getVSAssemblyInfo()).thenReturn(info);

      ViewsheetInfo vsInfo = new ViewsheetInfo();
      vsInfo.setScriptEnabled(true);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getViewsheetInfo()).thenReturn(vsInfo);
      when(vs.getAssemblies()).thenReturn(new Assembly[]{ chart });
      when(vs.getCalcFields("Query1")).thenReturn(new CalculateRef[]{ calc });

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   @Test
   void listsCalcFieldsAlongsideTheScriptTargets() {
      ScriptTargetInfo calc = service.list(runtimeWithCalcField()).stream()
         .filter(t -> "calcField".equals(t.kind()))
         .findFirst()
         .orElseThrow(() -> new AssertionError("no calcField target emitted"));

      assertEquals("Query1", calc.assembly(), "the TABLE, for this kind");
      assertEquals("Margin", calc.name());
      assertEquals("per row, when the field is evaluated", calc.runsWhen());
      assertTrue(calc.hasScript());
      assertNull(calc.target(), "a calc field has no legacy string form");
   }

   @Test
   void aCalcFieldsLabelSaysItIsAFieldOnATableNotAnAssemblyScript() {
      ScriptTargetInfo calc = service.list(runtimeWithCalcField()).stream()
         .filter(t -> "calcField".equals(t.kind())).findFirst().orElseThrow();

      assertTrue(calc.label().contains("Margin") && calc.label().contains("Query1"),
                 "label should name both: " + calc.label());
   }

   @Test
   void everyNonCalcTargetStillReportsANullName() {
      assertTrue(service.list(runtimeWithCalcField()).stream()
                    .filter(t -> !"calcField".equals(t.kind()))
                    .allMatch(t -> t.name() == null));
   }

   @Test
   void calcFieldIsAdvertisedAsServableWithoutEditingScriptGrammar() {
      assertTrue(ScriptGrammar.supportedKinds().contains("calcField"));
      assertFalse(ScriptGrammar.supportedKinds().contains("worksheetExpression"),
                  "reserved kinds still must not be advertised");
   }

   /**
    * Two calc fields on one table that differ in BOTH reported flags: Margin is JavaScript over
    * detail rows, TaxRate is SQL over aggregated rows. A fixture where both fields agree could not
    * tell "reported correctly" from "hardcoded".
    */
   private static RuntimeViewsheet runtimeWithSqlAndJsCalcFields() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      info.setScript("");
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getName()).thenReturn("Chart1");
      when(chart.getVSAssemblyInfo()).thenReturn(info);

      ViewsheetInfo vsInfo = new ViewsheetInfo();
      vsInfo.setScriptEnabled(true);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getViewsheetInfo()).thenReturn(vsInfo);
      when(vs.getAssemblies()).thenReturn(new Assembly[]{ chart });
      when(vs.getCalcFields("Query1")).thenReturn(new CalculateRef[]{
         calc("Margin", "field['PRICE'] - field['COST']", false, true),
         calc("TaxRate", "PRICE * 0.2", true, false),
      });

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static ScriptTargetInfo calcNamed(List<ScriptTargetInfo> all, String name) {
      return all.stream()
         .filter(t -> "calcField".equals(t.kind()) && name.equals(t.name()))
         .findFirst()
         .orElseThrow(() -> new AssertionError("no calcField named " + name + " in " + all));
   }

   /**
    * The flag that stops an agent rewriting a SQL expression as JavaScript. {@code PRICE * 0.2}
    * and {@code field['PRICE'] - field['COST']} are told apart by this field, not by reading the
    * text — and {@code update_script} writes whatever it is handed, verbatim.
    */
   @Test
   void reportsWhetherACalcFieldIsSqlOrJavaScript() {
      List<ScriptTargetInfo> all = service.list(runtimeWithSqlAndJsCalcFields());

      assertEquals(Boolean.FALSE, calcNamed(all, "Margin").sql(), "Margin is JavaScript");
      assertEquals(Boolean.TRUE, calcNamed(all, "TaxRate").sql(), "TaxRate is SQL");
   }

   @Test
   void reportsWhetherACalcFieldEvaluatesOverDetailRows() {
      List<ScriptTargetInfo> all = service.list(runtimeWithSqlAndJsCalcFields());

      assertEquals(Boolean.TRUE, calcNamed(all, "Margin").baseOnDetail(), "Margin is per-detail");
      assertEquals(Boolean.FALSE, calcNamed(all, "TaxRate").baseOnDetail(),
                   "TaxRate evaluates over aggregated rows");
   }

   /**
    * Neither flag is meaningful off a calc field, so the other four kinds report null rather than
    * a default that reads as an answer — {@code sql=false} on an onInit script would claim the
    * viewsheet's initializer is "JavaScript, not SQL", which is not a distinction it has.
    */
   @Test
   void everyNonCalcTargetReportsNeitherFlag() {
      List<ScriptTargetInfo> others = service.list(runtimeWithSqlAndJsCalcFields()).stream()
         .filter(t -> !"calcField".equals(t.kind()))
         .toList();

      assertFalse(others.isEmpty(), "the fixture must carry non-calc targets to discriminate");
      assertTrue(others.stream().allMatch(t -> t.sql() == null), "sql must be absent: " + others);
      assertTrue(others.stream().allMatch(t -> t.baseOnDetail() == null),
                 "baseOnDetail must be absent: " + others);
   }
}

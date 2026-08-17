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

   /** A viewsheet whose Query1 carries one JS calc field. */
   private static RuntimeViewsheet runtimeWithCalcField() {
      ExpressionRef inner = new ExpressionRef();
      inner.setName("Margin");
      inner.setExpression("field['PRICE'] - field['COST']");
      CalculateRef calc = new CalculateRef(true);
      calc.setDataRef(inner);

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
}

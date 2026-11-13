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

import inetsoft.util.script.ScriptEnv;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
import inetsoft.web.wiz.viewsheet.PropertyAliases;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class DynamicValueTargetsTest {
   private static final UnaryOperator<String> PARSES = text -> null;
   private static final UnaryOperator<String> ENDS_IN_PLUS =
      text -> text.trim().endsWith("+") ? "unexpected end of input" : null;

   private static ScriptTarget target(String assembly, String name) throws Exception {
      return ScriptTarget.of(ScriptTarget.Kind.DYNAMIC_VALUE, assembly, name);
   }

   private static ScriptTargetInfo info(String assembly, String name, String type) {
      return DynamicValueTargets.targetInfo(
         new EditorContext("dynamicValue", assembly, name, null), type);
   }

   // ---- targetInfo / describe -------------------------------------------------------------

   @Test
   void theSyntheticTargetIsPendingAndRoundTripsItsId() throws Exception {
      ScriptTargetInfo info = info("Text1", "visible", "text");

      assertEquals("dynamicValue", info.kind());
      assertEquals("Text1", info.assembly());
      assertEquals("visible", info.name());
      assertEquals(Boolean.TRUE, info.pending());
      assertFalse(info.hasScript());
      assertTrue(info.enabled());
      assertEquals("viewsheet", info.hostSheet());
      assertEquals("dynamicValue:Text1:visible", info.enableScope());
      assertEquals("each time 'Text1' is refreshed, once the user applies the property",
                   info.runsWhen());
      assertEquals(
         "Expression of the visible property of Text 'Text1' (set_assembly_properties key " +
         "'visible'; draft-only: the value is in the user's open dialog)", info.label());

      ScriptTarget back = ScriptTarget.fromId(info.id());
      assertEquals(ScriptTarget.Kind.DYNAMIC_VALUE, back.kind());
      assertEquals("visible", back.name());
   }

   @Test
   void anUnparsableGrantYieldsNoTarget() {
      for(String bad : new String[] { "prop:visible", "a b", "format:x", "", null }) {
         assertNull(info("Text1", bad, "text"), String.valueOf(bad));
      }

      assertNull(info("", "visible", "text"));
      assertNull(info(null, "visible", "text"));
   }

   @Test
   void aCaptionReplacesTheKeyAndAnUnknownTypeIsOmitted() {
      assertTrue(info("Gauge1", "nthOperand", "gauge").label().contains(
         "the N/P property of Gauge 'Gauge1'"), info("Gauge1", "nthOperand", "gauge").label());
      assertTrue(info("X", "visible", null).label().startsWith(
         "Expression of the visible property of 'X' ("), info("X", "visible", null).label());
      assertTrue(info("List1", "formula", "selectionlist").label().contains(
         "Selection List 'List1'"), info("List1", "formula", "selectionlist").label());
   }

   @Test
   void aRawPathIsReportedAsTheAliasTheTypeHas() {
      String path = "dataInputPaneModel.columnValue";

      assertTrue(info("Slider1", path, "slider").label().contains("set_assembly_properties key " +
         "'columnValue'"), info("Slider1", path, "slider").label());
      assertTrue(info("Combo1", path, "combobox").label().contains("set_assembly_properties key " +
         "'writeBackColumn'"), info("Combo1", path, "combobox").label());
      assertTrue(info("Radio1", path, "radiobutton").label().contains("key 'writeBackColumn'"),
                 info("Radio1", path, "radiobutton").label());
      // No alias on this type: the raw path itself is what the plugin accepts.
      assertTrue(info("Check1", path, "checkbox").label().contains("key '" + path + "'"),
                 info("Check1", path, "checkbox").label());
      assertTrue(info("Img1", "imageAdvancedPaneModel.dynamicImagePaneModel.dynamicImageValue",
                      "image").label().contains(
         "key 'imageAdvancedPaneModel.dynamicImagePaneModel.dynamicImageValue'"));
   }

   @Test
   void everyNamespaceNamesTheToolAndKeyTheNameEquals() {
      assertWhere("source:measure", "selectionlist",
                  "set_selection_source parameter 'measure'");
      assertWhere("tableOption:rowTotals", "crosstab", "set_table_options key 'rowTotals'");
      assertWhere("dateComparison:periodsBack", "chart",
                  "set_date_comparison parameter 'periodsBack'");
      assertWhere("dateComparison:customPeriods[1].end", "chart",
                  "set_date_comparison parameter 'customPeriods[1].end'");
      assertWhere("binding:x[2].sort.sortByField", "chart",
                  "the binding editor field, fieldConfigs key 'sort.sortByField' of shelf 'x' " +
                  "position 2");
      assertWhere("chartTarget:line.value", "chart",
                  "line target, add_chart_target_line parameter 'value' (no edit tool for an " +
                  "existing target)");
      assertWhere("chartTarget:band.fromLabel", "chart",
                  "band target, add_chart_target parameter 'fromLabel' (no edit tool for an " +
                  "existing target)");
      assertWhere("chartTarget:statistics.percentageOf", "chart",
                  "statistics target, add_chart_target parameter 'percentageOf' (no edit tool " +
                  "for an existing target)");
      assertWhere("chartRegion:title/y2/title", "chart",
                  "set_chart_region_properties, region title, target y2, properties.title");
      assertWhere("chartRegion:legend/1/title", "chart",
                  "set_chart_region_properties, region legend, target 1, properties.title");
      assertWhere("rangeValues[3]", "gauge", "set_assembly_properties key 'rangeValues[3]'");
   }

   private static void assertWhere(String name, String type, String where) {
      ScriptTargetInfo i = info("A1", name, type);

      assertNotNull(i, name);
      assertTrue(i.label().contains(where), name + " -> " + i.label());
      assertTrue(i.label().endsWith("; draft-only: the value is in the user's open dialog)"),
                 i.label());
   }

   // ---- aliasFor ---------------------------------------------------------------------------

   @Test
   void aliasForIsAReverseLookupOnTheTypesOwnRegistry() {
      assertEquals("columnValue",
                   PropertyAliases.aliasFor("slider", "dataInputPaneModel.columnValue"));
      assertEquals("writeBackColumn",
                   PropertyAliases.aliasFor("combobox", "dataInputPaneModel.columnValue"));
      assertEquals("writeBackRow",
                   PropertyAliases.aliasFor("radiobutton", "dataInputPaneModel.rowValue"));
      assertEquals("columnValue",
                   PropertyAliases.aliasFor("slider", "DataInputPaneModel.columnValue"));
      assertNull(PropertyAliases.aliasFor("checkbox", "dataInputPaneModel.columnValue"));
      assertNull(PropertyAliases.aliasFor("text", "dataInputPaneModel.columnValue"));
      assertNull(PropertyAliases.aliasFor("cube", "dataInputPaneModel.columnValue"));
      assertNull(PropertyAliases.aliasFor("slider", null));
   }

   // ---- read / note ------------------------------------------------------------------------

   @Test
   void readIsAnEmptyPendingDraftWithTheDesignNote() throws Exception {
      ScriptInfo read = DynamicValueTargets.read(target("Text1", "visible"));

      assertEquals("", read.text());
      assertEquals(Boolean.TRUE, read.pending());
      assertEquals("dynamicValue:Text1:visible", read.target());
      assertTrue(read.note().startsWith("Draft-only location: the expression of ONE dynamic " +
         "value in the user's open dialog (the visible property of 'Text1' (set_assembly_" +
         "properties key 'visible')"), read.note());
      assertTrue(read.note().contains("the empty text is NOT an empty expression and NOT a " +
         "failed update_script"), read.note());
      assertTrue(read.note().contains("Nothing is applied until the user clicks OK in the " +
         "formula dialog and then OK/Apply in the dialog that holds the field"), read.note());
      assertTrue(read.note().contains("get_assembly_properties from a toolbar session " +
         "(connect_sheet with a toolbar code)"), read.note());
   }

   @Test
   void theReadToolIsTheOneThatReadsBackTheSameNamespace() throws Exception {
      assertReadTool("visible", "get_assembly_properties");
      assertReadTool("source:measure", "get_assembly_properties");
      assertReadTool("binding:x[0].column", "get_binding");
      assertReadTool("tableOption:rowTotals", "list_table_options");
      assertReadTool("dateComparison:level", "get_date_comparison");
      assertReadTool("chartTarget:line.value", "list_chart_target_lines");
      assertReadTool("chartRegion:title/x/title", "list_chart_region_properties");
   }

   private static void assertReadTool(String name, String tool) throws Exception {
      assertTrue(DynamicValueTargets.note(target("A1", name), false).contains(tool + " from a " +
         "toolbar session"), name);
      assertTrue(DynamicValueTargets.note(target("A1", name), true).contains(tool + " from a " +
         "toolbar session"), name);
   }

   @Test
   void theWriteNoteSaysNothingIsAppliedAndNotToRetry() throws Exception {
      String note = DynamicValueTargets.note(target("Text1", "visible"), true);

      assertTrue(note.startsWith("Pushed to the open formula dialog for the visible property of " +
         "'Text1'"), note);
      assertTrue(note.contains("as a draft. Nothing is applied: the user must click OK in the " +
         "formula dialog and then OK/Apply in the dialog that holds the field; Cancel anywhere " +
         "discards it."), note);
      assertTrue(note.contains("the draft waits behind the dialog's reload prompt"), note);
      assertTrue(note.contains("Do not retry or read back to verify"), note);
   }

   // ---- prepareWrite -----------------------------------------------------------------------

   private static DynamicValueTargets.Prepared prepare(String text) throws PairingException {
      return DynamicValueTargets.prepareWrite(text, PARSES);
   }

   private static String refusal(String text, UnaryOperator<String> syntax) {
      PairingException ex = assertThrows(PairingException.class,
         () -> DynamicValueTargets.prepareWrite(text, syntax), String.valueOf(text));
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind());
      return ex.getMessage();
   }

   @Test
   void aPlainExpressionIsKeptVerbatim() throws Exception {
      DynamicValueTargets.Prepared p = prepare("parameter.x > 1");

      assertEquals("parameter.x > 1", p.text());
      assertFalse(p.strippedEquals());
   }

   @Test
   void oneLeadingEqualsIsStrippedAndReported() throws Exception {
      for(String in : new String[] { "=2 > 1", "= 2 > 1", "  = 2 > 1", "\n=2 > 1" }) {
         DynamicValueTargets.Prepared p = prepare(in);

         assertTrue(p.strippedEquals(), in);
         assertEquals("2 > 1", p.text(), in);
      }
   }

   @Test
   void aDoubleEqualsIsNotMangled() throws Exception {
      DynamicValueTargets.Prepared p = prepare("==x");

      assertEquals("==x", p.text());
      assertFalse(p.strippedEquals());

      // 'a == b' starts with a letter and keeps both signs.
      assertEquals("a == b", prepare("a == b").text());
   }

   @Test
   void onlyTheFirstOfTwoEqualsIsStrippedWhenTheSecondIsAnotherCharacter() throws Exception {
      // "= =x" strips ONE '=' and leaves "=x" -- which is a '=' expression the user typed.
      DynamicValueTargets.Prepared p = prepare("= =x");

      assertTrue(p.strippedEquals());
      assertEquals("=x", p.text().strip());
   }

   @Test
   void nullBlankAndABareEqualsAreRefused() {
      assertTrue(refusal(null, PARSES).contains("text is required."));

      for(String blank : new String[] { "", "   ", "=", " = ", "=  \n" }) {
         String message = refusal(blank, PARSES);

         assertTrue(message.contains("The expression is empty: it would store a bare '=' and " +
            "leave the field with no value. Nothing was pushed."), blank + " -> " + message);
      }
   }

   @Test
   void aBareVariableReferenceIsRefused() throws Exception {
      for(String ref : new String[] { "$(Foo)", " $(Foo) ", "=$(Foo)", "$(my var)", "$(a.b)" }) {
         String message = refusal(ref, PARSES);

         assertTrue(message.contains("That text is a variable reference, not an expression"),
                    ref + " -> " + message);
         assertTrue(message.contains("Ask the user to choose the Variable type"), message);
         assertTrue(message.contains("Nothing was pushed."), message);
      }

      // A reference that is part of a bigger expression is a real expression.
      assertEquals("$(a)+1", prepare("$(a)+1").text());
      assertEquals("$(a) + $(b)", prepare("$(a) + $(b)").text());
      assertEquals("f($(a))", prepare("f($(a))").text());
      assertEquals("'$(a)'", prepare("'$(a)'").text());
   }

   @Test
   void aSyntaxProblemIsRefusedAndNamed() {
      String message = refusal("1 +", ENDS_IN_PLUS);

      assertEquals("The expression does not parse as a JavaScript expression (unexpected end " +
                   "of input). Nothing was pushed.", message);
   }

   @Test
   void theSyntaxCheckSeesTheNormalizedText() throws Exception {
      String[] seen = new String[1];

      DynamicValueTargets.prepareWrite("= 2 > 1", text -> {
         seen[0] = text;
         return null;
      });

      assertEquals("2 > 1", seen[0].strip());
      assertFalse(seen[0].stripLeading().startsWith("="));
   }

   /** Parse only: the injected engine sees checkFunction and nothing else. */
   @Test
   void theEngineParsesAndNeverExecutes() throws Exception {
      ScriptEnv env = mock(ScriptEnv.class);
      doThrow(new Exception("Syntax error: unexpected end"))
         .when(env).checkFunction("viewsheet condition", "1 +");
      UnaryOperator<String> syntax = text -> VsConditionValueTargets.syntaxProblem(env, text);

      assertEquals("parameter.x > 1", DynamicValueTargets.prepareWrite("parameter.x > 1",
                                                                       syntax).text());
      assertTrue(refusal("1 +", syntax).contains("Syntax error"));
      verify(env).checkFunction("viewsheet condition", "parameter.x > 1");
      verify(env).checkFunction("viewsheet condition", "1 +");
      verifyNoMoreInteractions(env);
   }

   /**
    * Pins what the shared syntax check lets through (review F6). prepareWrite adds NO statement or
    * `return` filter of its own: whether `return x` or a bare statement list is accepted is
    * decided entirely by the engine's parse-only checkFunction, which parses the text as a
    * program (GraalJavaScriptEngine.checkFunction). The runtime evaluates a dynamic value as the
    * same kind of program (ViewsheetSandbox.executeDynamicValue runs getScope().execute(script)
    * on the text after the '='), and the dialog the user confirms in runs its own check, so a
    * draft is advisory either way. The real engine is not loadable in a unit test, so a stub that
    * accepts every text stands for "the engine parses it" and a stub that rejects `return`
    * stands for "the engine refuses it".
    */
   @Test
   void statementTextsAreAcceptedExactlyWhenTheEngineParsesThem() throws Exception {
      UnaryOperator<String> engineRejectsTopLevelReturn =
         text -> text.stripLeading().startsWith("return ") ? "Illegal return statement" : null;

      for(String statement : new String[] { "x = 1; y = 2", "if(a) b", "var q = 3; q + 1" }) {
         assertEquals(statement, DynamicValueTargets.prepareWrite(
            statement, engineRejectsTopLevelReturn).text());
      }

      assertTrue(refusal("return x", engineRejectsTopLevelReturn).contains("Illegal return"));
      assertTrue(refusal("= return x", engineRejectsTopLevelReturn).contains("Illegal return"));

      // an engine that parses it is trusted: no filter of prepareWrite's own stops it
      assertEquals("return x", DynamicValueTargets.prepareWrite("return x", PARSES).text());
   }
}

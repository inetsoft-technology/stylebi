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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.binding.BindableFieldsService;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PaneState;
import inetsoft.web.wiz.pairing.SheetType;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.AssemblyContext;
import inetsoft.web.wiz.script.model.PaneContext;
import inetsoft.web.wiz.script.model.ScriptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81445: one read-only context query for every pane kind. Pins what it lists per location,
 * that a failing source degrades to a note instead of failing the call, and that it carries no
 * identifier another endpoint would accept.
 */
@WizAgentTestSupport
class PaneContextServiceTest {
   private static final String RUNTIME_ID = "Viewsheet/secret-runtime";
   private static final String TOKEN = "TOK-SECRET";

   private ScriptContextService contextService;
   private ScriptApiService apiService;
   private BindableFieldsService fields;
   private PaneContextService service;
   private final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
   private final Principal user = () -> "admin";

   private static BindableTable table(String name, Boolean current, String... columns) {
      return new BindableTable(name, current,
         Arrays.stream(columns).map(c -> new BindableField(c, "string", null)).toList());
   }

   private static JoinSession session(EditorContext ctx) {
      return new JoinSession(TOKEN, RUNTIME_ID, "admin", SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
         JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   @BeforeEach
   void setUp() throws Exception {
      contextService = mock(ScriptContextService.class);
      when(contextService.context(rvs, null)).thenReturn(new ScriptContext(
         List.of(
            new AssemblyContext("RadioButton1", "RadioButtonVSAssembly", true, List.of(), null),
            new AssemblyContext("Text1", "TextVSAssembly", true, List.of(), null)),
         List.of("parameter")));
      apiService = mock(ScriptApiService.class);
      when(apiService.functionNames()).thenReturn(List.of("dateAdd", "runQuery"));

      fields = mock(BindableFieldsService.class);
      when(fields.list(eq(RUNTIME_ID), any(), eq(user))).thenReturn(
         List.of(table("Orders", null, "id", "total"), table("Customers", null, "name")));

      service = new PaneContextService(
         contextService, apiService, mock(ViewsheetService.class), fields);
   }

   /** A viewsheet that declares {@code region}, holding a RadioButton1 component. */
   private static Viewsheet viewsheetDeclaring(String... variables) {
      Viewsheet vs = new Viewsheet() {
         @Override
         public synchronized UserVariable[] getAllVariables() {
            return Arrays.stream(variables).map(UserVariable::new).toArray(UserVariable[]::new);
         }
      };
      vs.addAssembly(new TextVSAssembly(vs, "RadioButton1"));
      return vs;
   }

   /** A runtime whose variable table holds the given names (a URL/viewer/tool-set parameter). */
   private RuntimeViewsheet runtimeWith(Viewsheet vs, String... runtimeKeys) {
      VariableTable table = new VariableTable();

      for(String key : runtimeKeys) {
         table.put(key, "x");
      }

      VariableScriptable scriptable = mock(VariableScriptable.class);
      when(scriptable.unwrap()).thenReturn(table);
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.getVariableScriptable()).thenReturn(scriptable);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getScope()).thenReturn(scope);
      RuntimeViewsheet runtime = mock(RuntimeViewsheet.class);
      when(runtime.getViewsheet()).thenReturn(vs);
      when(runtime.getViewsheetSandbox()).thenReturn(Optional.of(box));
      return runtime;
   }

   @Test
   void customParametersAreListedWithTheStandardOnesAndNoScriptableNoise() throws Exception {
      Viewsheet vs = viewsheetDeclaring("region");
      // what the editor's "parameter" scriptable reported in the live check: a component's value
      // slot and the JavaScript object's own members next to the real parameters
      RuntimeViewsheet runtime = runtimeWith(vs, "cutoff", "RadioButton1", "length",
                                             "parameterNames", "__principal__", "_USER_");
      when(contextService.context(runtime, null)).thenReturn(new ScriptContext(List.of(), List.of()));

      PaneContext ctx = service.paneContext(
         runtime, session(new EditorContext("dynamicValue", "Text1", "visible", null)), user, PaneState.DEFAULT);

      assertTrue(ctx.parameters().containsAll(List.of("cutoff", "region")), ctx.parameters().toString());
      assertTrue(ctx.parameters().containsAll(List.of("_USER_", "_ROLES_", "_GROUPS_")));
      assertFalse(ctx.parameters().contains("RadioButton1"), "a component is not a parameter");
      assertFalse(ctx.parameters().contains("__principal__"));
      assertEquals(ctx.parameters().stream().sorted().toList(), ctx.parameters());
   }

   @Test
   void aDeclaredVariableThatSharesAComponentNameIsStillAParameter() throws Exception {
      Viewsheet vs = viewsheetDeclaring("RadioButton1");
      RuntimeViewsheet runtime = runtimeWith(vs);
      when(contextService.context(runtime, null)).thenReturn(new ScriptContext(List.of(), List.of()));

      PaneContext ctx = service.paneContext(runtime, session(null), user, PaneState.DEFAULT);

      assertTrue(ctx.parameters().contains("RadioButton1"), ctx.parameters().toString());
   }

   @Test
   void mergeDropsInternalsBuiltinsBlanksAndComponentSlots() {
      assertEquals(List.of("_GROUPS_", "_ROLES_", "_USER_", "a", "b"),
         PaneContextService.mergeParameterNames(
            List.of("b", "a", "", "__x__"), List.of("_TODAY", "Chart1", "a", "!type"),
            List.of("Chart1")));
      assertEquals(List.of("_GROUPS_", "_ROLES_", "_USER_"),
         PaneContextService.mergeParameterNames(List.of(), List.of(), List.of()));
   }

   @Test
   void aWholeSheetSessionListsEveryTable() throws Exception {
      PaneContext ctx = service.paneContext(rvs, session(null), user, PaneState.DEFAULT);

      assertEquals("sheet", ctx.kind());
      assertNull(ctx.editorContext());
      assertEquals(List.of("Orders", "Customers"),
                   ctx.columns().stream().map(PaneContext.Table::name).toList());
      verify(fields).list(RUNTIME_ID, null, user);
   }

   @Test
   void componentsParametersAndFunctionsAreNamesOnly() throws Exception {
      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("dynamicValue", "Text1", "visible", null)), user, PaneState.DEFAULT);

      assertEquals("dynamicValue", ctx.kind());
      assertEquals(List.of("RadioButton1", "Text1"),
                   ctx.components().stream().map(PaneContext.Component::name).toList());
      assertEquals("RadioButtonVSAssembly", ctx.components().get(0).type());
      // no viewsheet declares anything here: only the standard user variables, sorted
      assertEquals(List.of("_GROUPS_", "_ROLES_", "_USER_"), ctx.parameters());
      assertEquals(List.of("dateAdd", "runQuery"), ctx.functions());
      assertTrue(ctx.notes().isEmpty(), ctx.notes().toString());
   }

   @Test
   void aComponentLocationListsOnlyTheTableItIsBoundTo() throws Exception {
      when(fields.list(RUNTIME_ID, "Text1", user)).thenReturn(List.of(
         table("Orders", Boolean.TRUE, "id", "total"), table("Customers", Boolean.FALSE, "name")));

      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("dynamicValue", "Text1", "visible", null)), user, PaneState.DEFAULT);

      assertEquals(1, ctx.columns().size());
      assertEquals("Orders", ctx.columns().get(0).name());
      assertEquals(List.of("id", "total"),
                   ctx.columns().get(0).fields().stream().map(PaneContext.Field::column).toList());
      assertEquals(Boolean.TRUE, ctx.columns().get(0).current());
   }

   @Test
   void aComponentWithNoBoundTableListsEverySourceItOffers() throws Exception {
      when(fields.list(RUNTIME_ID, "Text1", user)).thenReturn(List.of(
         table("Orders", Boolean.FALSE, "id"), table("Customers", Boolean.FALSE, "name")));

      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("assemblyMain", "Text1", null, null)), user, PaneState.DEFAULT);

      assertEquals(2, ctx.columns().size());
   }

   @Test
   void aCalcFieldListsTheColumnsOfItsOwnTable() throws Exception {
      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("calcField", "Orders", "Margin", null)), user, PaneState.DEFAULT);

      assertEquals(List.of("Orders"), ctx.columns().stream().map(PaneContext.Table::name).toList());
      assertTrue(ctx.syntax().note().contains("field['<column>']"), ctx.syntax().note());
      verify(fields, never()).list(eq(RUNTIME_ID), eq("Orders"), any());
   }

   @Test
   void aCalcFieldOnAnUnknownTableSaysSoAndListsEverything() throws Exception {
      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("calcField", "Gone", "Margin", null)), user, PaneState.DEFAULT);

      assertEquals(2, ctx.columns().size());
      assertTrue(ctx.notes().get(0).contains("'Gone'"), ctx.notes().toString());
   }

   @Test
   void aCalcFieldAddressedByTableKeyIsAlsoUnderstood() throws Exception {
      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("calcField", null, "Margin", "Customers")), user, PaneState.DEFAULT);

      assertEquals(List.of("Customers"),
                   ctx.columns().stream().map(PaneContext.Table::name).toList());
   }

   @Test
   void aViewsheetLevelScriptListsEveryTable() throws Exception {
      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("viewsheetOnLoad", null, null, null)), user, PaneState.DEFAULT);

      assertEquals(2, ctx.columns().size());
      verify(fields).list(RUNTIME_ID, null, user);
   }

   @Test
   void aCalcTableCellIsServedAndItsSyntaxNamesTheNamedCellReference() throws Exception {
      when(fields.list(RUNTIME_ID, "Calc1", user))
         .thenReturn(List.of(table("Orders", Boolean.TRUE, "id")));

      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("calcTableCell", "Calc1", "2,1", null)), user, PaneState.DEFAULT);

      assertEquals("calcTableCell", ctx.kind());
      assertEquals(1, ctx.columns().size());
      assertTrue(ctx.syntax().note().contains("$<name>"), ctx.syntax().note());
   }

   @Test
   void aFailingSourceBecomesANoteAndTheRestStillComesBack() throws Exception {
      when(rvs.getViewsheet()).thenThrow(new IllegalStateException("boom"));
      when(fields.list(eq(RUNTIME_ID), any(), eq(user)))
         .thenThrow(new IllegalArgumentException("nope"));

      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("dynamicValue", "Text1", "visible", null)), user, PaneState.DEFAULT);

      // the standard user variables survive a failed read
      assertEquals(List.of("_GROUPS_", "_ROLES_", "_USER_"), ctx.parameters());
      assertTrue(ctx.columns().isEmpty());
      assertEquals(2, ctx.notes().size());
      assertTrue(ctx.notes().get(0).contains("boom"), ctx.notes().toString());
      assertTrue(ctx.notes().get(1).contains("nope"), ctx.notes().toString());
      assertEquals(2, ctx.components().size());
      assertEquals(2, ctx.functions().size());
   }

   @Test
   void theAnswerCarriesNoRuntimeIdOrSessionToken() throws Exception {
      for(EditorContext grant : new EditorContext[] {
         null,
         new EditorContext("dynamicValue", "Text1", "visible", null),
         new EditorContext("calcTableCell", "Calc1", "0,0", null),
         new EditorContext("calcField", "Orders", "Margin", null)})
      {
         String json = new ObjectMapper().writeValueAsString(
            service.paneContext(rvs, session(grant), user, PaneState.DEFAULT));

         assertFalse(json.contains(RUNTIME_ID), json);
         assertFalse(json.contains(TOKEN), json);
         assertFalse(json.contains("apiTree"), json);
      }
   }

   @Test
   void theVsConditionValueSyntaxSaysDollarIsNotDefined() {
      PaneContext.Syntax syntax = PaneContextService.syntaxOf(
         new EditorContext("vsConditionValue", "Table1", "filter", null), false);

      assertTrue(syntax.note().contains("$ is not defined"), syntax.note());
      assertTrue(syntax.note().contains("parameter.<name>"), syntax.note());
      assertTrue(syntax.note().contains("SQL"), syntax.note());
   }

   @Test
   void theDynamicValueSyntaxExplainsTheReportedFailure() {
      PaneContext.Syntax syntax = PaneContextService.syntaxOf(
         new EditorContext("dynamicValue", "Text1", "visible", null), false);

      assertEquals("javascript", syntax.language());
      assertTrue(syntax.note().contains("ReferenceError: $ is not defined"), syntax.note());
      assertTrue(syntax.note().contains("Variable"), syntax.note());
   }

   @Test
   void everyLocationKindHasASyntaxStatement() {
      for(String kind : new String[] {
         "viewsheetOnInit", "viewsheetOnLoad", "assemblyMain", "assemblyOnClick", "calcField",
         "vsConditionValue", "dynamicValue", "calcTableCell"})
      {
         PaneContext.Syntax syntax =
            PaneContextService.syntaxOf(new EditorContext(kind, "A", "n", null), false);

         assertEquals("javascript", syntax.language(), kind);
         assertFalse(syntax.note().isBlank(), kind);
      }

      assertFalse(PaneContextService.syntaxOf(null, false).note().isBlank());
   }

   /**
    * Every {@link ScriptTarget.Kind} gets its own statement, and none carries the retired "not
    * served by this viewsheet context" text (Bug #81557 serves the worksheet kinds). The worksheet
    * SQL kind is reported as {@code sql}, never JavaScript.
    */
   @Test
   void noKindIsMislabelledAsNotServed() {
      for(ScriptTarget.Kind kind : ScriptTarget.Kind.values()) {
         PaneContext.Syntax syntax = PaneContextService.syntaxOf(
            new EditorContext(kind.wireName(), "A", "n", null), false);

         assertFalse(syntax.note().contains("not served by this viewsheet context"),
                     kind.wireName() + ": " + syntax.note());
         assertFalse(syntax.note().isBlank(), kind.wireName());
         assertEquals(kind == ScriptTarget.Kind.WORKSHEET_SQL_QUERY ? "sql" : "javascript",
                      syntax.language(), kind.wireName());
      }
   }

   /** Bug #81557: a worksheet expression and a condition value follow the dialog's SQL switch. */
   @Test
   void aWorksheetExpressionAndConditionValueFollowTheSqlSwitch() {
      for(String kind : new String[] {"worksheetExpression", "worksheetConditionValue"}) {
         EditorContext grant = new EditorContext(kind, "Orders", "Margin", null);
         PaneContext.Syntax script = PaneContextService.syntaxOf(grant, false);
         PaneContext.Syntax sql = PaneContextService.syntaxOf(grant, true);

         assertEquals("javascript", script.language(), kind);
         assertTrue(script.note().contains("field['<column>']"), script.note());
         assertTrue(script.note().contains("SQL mode"), script.note());
         assertEquals("sql", sql.language(), kind);
         assertTrue(sql.note().contains("SQL value expression"), sql.note());
         assertTrue(PaneContextService.isSqlMode(grant, new PaneState(true)), kind);
         assertFalse(PaneContextService.isSqlMode(grant, new PaneState(false)), kind);
      }

      // A whole worksheet condition has no switch: its reported state is ignored.
      EditorContext condition = new EditorContext("worksheetCondition", "Orders", "Margin", null);
      assertFalse(PaneContextService.isSqlMode(condition, new PaneState(true)));
      assertEquals("javascript", PaneContextService.syntaxOf(condition, true).language());
   }

   @Test
   void theHyperlinkExpressionSyntaxIsJavaScriptWithTheLeadingEqualsAndTheMessageForm() {
      PaneContext.Syntax syntax = PaneContextService.syntaxOf(
         new EditorContext("vsHyperlinkExpression", "Table1", "cell[2,1]", null), false);

      assertEquals("javascript", syntax.language());
      assertTrue(syntax.note().contains("Web Link Expression of one hyperlink"), syntax.note());
      assertTrue(syntax.note().contains("leading '='"), syntax.note());
      assertTrue(syntax.note().contains("=\"message:...\""), syntax.note());
      assertTrue(syntax.note().contains("parameter.<name>"), syntax.note());
      assertFalse(syntax.note().contains("not served by this viewsheet context"), syntax.note());
   }

   @Test
   void theWorksheetKindsAreLabelledForWhatTheyAre() {
      PaneContext.Syntax variable = PaneContextService.syntaxOf(
         new EditorContext("worksheetVariableDefault", "Var1", null, null), false);
      PaneContext.Syntax sql = PaneContextService.syntaxOf(
         new EditorContext("worksheetSqlQuery", "SQL Query1", null, null), false);

      assertEquals("javascript", variable.language());
      assertTrue(variable.note().contains("worksheet variable"), variable.note());
      assertTrue(variable.note().contains("worksheet editor tools"), variable.note());

      assertEquals("sql", sql.language());
      assertTrue(sql.note().contains("run verbatim with the user's own permissions"), sql.note());
      assertTrue(sql.note().contains("$(var) placeholders are not checked"), sql.note());
      assertTrue(sql.note().contains("worksheet editor tools"), sql.note());
      assertFalse(sql.note().contains("not served by this viewsheet context"), sql.note());
   }

   @Test
   void aHyperlinkLocationListsTheBoundTableOfTheOwningComponent() throws Exception {
      when(fields.list(RUNTIME_ID, "Table1", user)).thenReturn(List.of(
         table("Orders", Boolean.TRUE, "id", "total"), table("Customers", Boolean.FALSE, "name")));

      PaneContext ctx = service.paneContext(rvs,
         session(new EditorContext("vsHyperlinkExpression", "Table1", "cell[2,1]", null)), user, PaneState.DEFAULT);

      assertEquals("vsHyperlinkExpression", ctx.kind());
      assertEquals("javascript", ctx.syntax().language());
      assertEquals(List.of("Orders"), ctx.columns().stream().map(PaneContext.Table::name).toList());
      assertEquals(List.of("id", "total"),
                   ctx.columns().get(0).fields().stream().map(PaneContext.Field::column).toList());
      assertEquals("Table1", PaneContextService.ownerOf(
         new EditorContext("vsHyperlinkExpression", "Table1", "cell[2,1]", null)));
   }

   @Test
   void anEmbeddedChildThatCannotBeLookedUpBecomesANoteAndTheRestComesBack() throws Exception {
      when(fields.list(RUNTIME_ID, "Viewsheet1.Table1", user))
         .thenThrow(new IllegalArgumentException("no such assembly"));

      PaneContext ctx = service.paneContext(rvs,
         session(new EditorContext("vsHyperlinkExpression", "Viewsheet1.Table1", "cell[0,0]",
                                   null)), user, PaneState.DEFAULT);

      assertTrue(ctx.columns().isEmpty());
      assertTrue(ctx.notes().stream().anyMatch(n -> n.contains("no such assembly")),
                 ctx.notes().toString());
      assertEquals("javascript", ctx.syntax().language());
      assertEquals(2, ctx.components().size());
   }

   @Test
   void aWorksheetSqlSessionIsReportedAsSqlEvenThoughThisEndpointDoesNotServeIt() throws Exception {
      PaneContext ctx = service.paneContext(rvs,
         session(new EditorContext("worksheetSqlQuery", "SQL Query1", null, null)), user, PaneState.DEFAULT);

      assertEquals("worksheetSqlQuery", ctx.kind());
      assertEquals("sql", ctx.syntax().language());
   }

   @Test
   void aCalcFieldOnTheSqlSwitchIsDescribedAsSqlNotJavaScript() throws Exception {
      EditorContext calcField = new EditorContext("calcField", "Orders", "Margin", null);

      PaneContext js = service.paneContext(rvs, session(calcField), user, PaneState.DEFAULT);
      PaneContext sql = service.paneContext(rvs, session(calcField), user, new PaneState(true));

      assertEquals("javascript", js.syntax().language());
      assertTrue(js.syntax().note().contains("parameter.<name>"), js.syntax().note());
      assertEquals(List.of("dateAdd", "runQuery"), js.functions());

      assertEquals("sql", sql.syntax().language());
      assertEquals(PaneContextService.CALC_FIELD_SQL_NOTE, sql.syntax().note());
      assertTrue(sql.syntax().note().contains("field['<column>']"), sql.syntax().note());
      assertTrue(sql.syntax().note().contains("$(<name>)"), sql.syntax().note());
      assertFalse(sql.syntax().note().contains("evaluated per row: read"), sql.syntax().note());
      // the dialog's own SQL function and operator lists are served, not the script functions
      assertEquals(List.of("SUM", "COUNT", "AVG", "MIN", "MAX"), sql.functions());
      assertEquals(List.of("+", "-", "*", "/", "<", ">", "<=", ">=", "=", "<>"), sql.operators());
      assertTrue(sql.notes().stream().noneMatch(n -> n.contains("SQL mode")), sql.notes().toString());
      assertTrue(js.operators().isEmpty(), js.operators().toString());
      // the location and the column scope are the same in both modes
      assertEquals(js.editorContext(), sql.editorContext());
      assertEquals(js.columns(), sql.columns());
   }

   @Test
   void theSqlStateIsIgnoredForEveryKindWithoutTheSwitch() throws Exception {
      for(ScriptTarget.Kind kind : ScriptTarget.Kind.values()) {
         // Bug #81557: the worksheet expression and condition value have the switch too.
         if(kind == ScriptTarget.Kind.CALC_FIELD || kind == ScriptTarget.Kind.WORKSHEET_EXPRESSION ||
            kind == ScriptTarget.Kind.WORKSHEET_CONDITION_VALUE)
         {
            continue;
         }

         EditorContext ctx = new EditorContext(kind.wireName(), "A", "n", null);

         assertEquals(PaneContextService.syntaxOf(ctx, false),
                      PaneContextService.syntaxOf(ctx, true), kind.wireName());
         assertFalse(PaneContextService.isSqlMode(ctx, new PaneState(true)), kind.wireName());
      }

      assertFalse(PaneContextService.isSqlMode(null, new PaneState(true)));
      assertFalse(PaneContextService.isSqlMode(
         new EditorContext("calcField", "A", "n", null), null));
      assertTrue(PaneContextService.isSqlMode(
         new EditorContext("calcField", "A", "n", null), new PaneState(true)));
   }
}

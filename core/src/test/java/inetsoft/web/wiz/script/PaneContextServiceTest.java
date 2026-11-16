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
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.binding.VSScriptableService;
import inetsoft.web.wiz.binding.BindableFieldsService;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.SheetType;
import inetsoft.web.wiz.script.model.AssemblyContext;
import inetsoft.web.wiz.script.model.PaneContext;
import inetsoft.web.wiz.script.model.ScriptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81445: one read-only context query for every pane kind. Pins what it lists per location,
 * that a failing source degrades to a note instead of failing the call, and that it carries no
 * identifier another endpoint would accept.
 */
@Tag("core")
class PaneContextServiceTest {
   private static final String RUNTIME_ID = "Viewsheet/secret-runtime";
   private static final String TOKEN = "TOK-SECRET";

   private ScriptContextService contextService;
   private ScriptApiService apiService;
   private VSScriptableService scriptable;
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

      ObjectNode root = new ObjectMapper().createObjectNode();
      ObjectNode parameter = root.putObject("parameter");
      parameter.putObject("region");
      parameter.putObject("__principal__");
      parameter.putObject("_USER_");
      parameter.putObject("!type");
      scriptable = mock(VSScriptableService.class);
      when(scriptable.getScriptDefinition(rvs, null, null, false)).thenReturn(root);

      fields = mock(BindableFieldsService.class);
      when(fields.list(eq(RUNTIME_ID), any(), eq(user))).thenReturn(
         List.of(table("Orders", null, "id", "total"), table("Customers", null, "name")));

      service = new PaneContextService(contextService, apiService, scriptable, fields);
   }

   @Test
   void aWholeSheetSessionListsEveryTable() throws Exception {
      PaneContext ctx = service.paneContext(rvs, session(null), user);

      assertEquals("sheet", ctx.kind());
      assertNull(ctx.editorContext());
      assertEquals(List.of("Orders", "Customers"),
                   ctx.columns().stream().map(PaneContext.Table::name).toList());
      verify(fields).list(RUNTIME_ID, null, user);
   }

   @Test
   void componentsParametersAndFunctionsAreNamesOnly() throws Exception {
      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("dynamicValue", "Text1", "visible", null)), user);

      assertEquals("dynamicValue", ctx.kind());
      assertEquals(List.of("RadioButton1", "Text1"),
                   ctx.components().stream().map(PaneContext.Component::name).toList());
      assertEquals("RadioButtonVSAssembly", ctx.components().get(0).type());
      // sorted; the runtime's own slot and metadata keys are not parameters a script can name
      assertEquals(List.of("_USER_", "region"), ctx.parameters());
      assertEquals(List.of("dateAdd", "runQuery"), ctx.functions());
      assertTrue(ctx.notes().isEmpty(), ctx.notes().toString());
   }

   @Test
   void aComponentLocationListsOnlyTheTableItIsBoundTo() throws Exception {
      when(fields.list(RUNTIME_ID, "Text1", user)).thenReturn(List.of(
         table("Orders", Boolean.TRUE, "id", "total"), table("Customers", Boolean.FALSE, "name")));

      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("dynamicValue", "Text1", "visible", null)), user);

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
         rvs, session(new EditorContext("assemblyMain", "Text1", null, null)), user);

      assertEquals(2, ctx.columns().size());
   }

   @Test
   void aCalcFieldListsTheColumnsOfItsOwnTable() throws Exception {
      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("calcField", "Orders", "Margin", null)), user);

      assertEquals(List.of("Orders"), ctx.columns().stream().map(PaneContext.Table::name).toList());
      assertTrue(ctx.syntax().note().contains("field['<column>']"), ctx.syntax().note());
      verify(fields, never()).list(eq(RUNTIME_ID), eq("Orders"), any());
   }

   @Test
   void aCalcFieldOnAnUnknownTableSaysSoAndListsEverything() throws Exception {
      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("calcField", "Gone", "Margin", null)), user);

      assertEquals(2, ctx.columns().size());
      assertTrue(ctx.notes().get(0).contains("'Gone'"), ctx.notes().toString());
   }

   @Test
   void aCalcFieldAddressedByTableKeyIsAlsoUnderstood() throws Exception {
      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("calcField", null, "Margin", "Customers")), user);

      assertEquals(List.of("Customers"),
                   ctx.columns().stream().map(PaneContext.Table::name).toList());
   }

   @Test
   void aViewsheetLevelScriptListsEveryTable() throws Exception {
      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("viewsheetOnLoad", null, null, null)), user);

      assertEquals(2, ctx.columns().size());
      verify(fields).list(RUNTIME_ID, null, user);
   }

   @Test
   void aCalcTableCellIsServedAndItsSyntaxNamesTheNamedCellReference() throws Exception {
      when(fields.list(RUNTIME_ID, "Calc1", user))
         .thenReturn(List.of(table("Orders", Boolean.TRUE, "id")));

      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("calcTableCell", "Calc1", "2,1", null)), user);

      assertEquals("calcTableCell", ctx.kind());
      assertEquals(1, ctx.columns().size());
      assertTrue(ctx.syntax().note().contains("$<name>"), ctx.syntax().note());
   }

   @Test
   void aFailingSourceBecomesANoteAndTheRestStillComesBack() throws Exception {
      when(scriptable.getScriptDefinition(rvs, null, null, false))
         .thenThrow(new IllegalStateException("boom"));
      when(fields.list(eq(RUNTIME_ID), any(), eq(user)))
         .thenThrow(new IllegalArgumentException("nope"));

      PaneContext ctx = service.paneContext(
         rvs, session(new EditorContext("dynamicValue", "Text1", "visible", null)), user);

      assertTrue(ctx.parameters().isEmpty());
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
            service.paneContext(rvs, session(grant), user));

         assertFalse(json.contains(RUNTIME_ID), json);
         assertFalse(json.contains(TOKEN), json);
         assertFalse(json.contains("apiTree"), json);
      }
   }

   @Test
   void theDynamicValueSyntaxExplainsTheReportedFailure() {
      PaneContext.Syntax syntax = PaneContextService.syntaxOf(
         new EditorContext("dynamicValue", "Text1", "visible", null));

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
            PaneContextService.syntaxOf(new EditorContext(kind, "A", "n", null));

         assertEquals("javascript", syntax.language(), kind);
         assertFalse(syntax.note().isBlank(), kind);
      }

      assertFalse(PaneContextService.syntaxOf(null).note().isBlank());
   }
}

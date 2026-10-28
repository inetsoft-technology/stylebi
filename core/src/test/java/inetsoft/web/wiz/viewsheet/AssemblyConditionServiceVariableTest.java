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
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.IntegerType;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.vs.VSConditionDialogModel;
import inetsoft.web.composer.vs.dialog.VSConditionDialogService;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.wiz.service.WizVsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79535: a {@code variable} operand was stored without any check of the value it holds.
 * The built-in date ranges the {@code date_in} check reads come from the server's data space, so
 * this runs against a real SreeHome.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssemblyConditionServiceVariableTest {
   private static final String DATE = "Order:Date";
   private static final String NUM = "Order:Num";

   private static DataRefModel field(String name, String type) {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn(name);
      when(field.getDataType()).thenReturn(type);
      return field;
   }

   private static VSConditionDialogModel model() {
      VSConditionDialogModel model = new VSConditionDialogModel();
      model.setTableName("Orders");
      model.setFields(new DataRefModel[]{ field(DATE, "timeInstant"), field(NUM, "integer") });
      return model;
   }

   private static ConditionVocabulary.Clause variableClause(String field, String operator,
                                                            String variable)
   {
      return new ConditionVocabulary.Clause(
         field, operator, List.of(Map.of("type", "variable", "name", variable)), null, false,
         false, 0);
   }

   private static VariableTable variables(Object... pairs) {
      VariableTable vars = new VariableTable();

      for(int i = 0; i < pairs.length; i += 2) {
         vars.put((String) pairs[i], pairs[i + 1]);
      }

      return vars;
   }

   private static XNodeMetaTable failedQuery(String cause) {
      XTypeNode root = new XTypeNode("table");
      root.addChild(new IntegerType("c"));
      XNodeMetaTable meta = new XNodeMetaTable(root, true);
      meta.setFailedQueryMessage(cause);
      return meta;
   }

   private record Harness(AssemblyConditionService service, VSConditionDialogService conditions,
                          ViewsheetSandbox box) {}

   private static Harness harness(VSConditionDialogModel model, VariableTable vars) {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getAllVariables()).thenReturn(vars);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.getID()).thenReturn("rt1");
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      VSConditionDialogService conditions = mock(VSConditionDialogService.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(conditions.getModel(anyString(), anyString(), any(Principal.class)))
            .thenReturn(model);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(
         new AssemblyConditionService(sessions, conditions, mock(VSRefreshService.class),
                                   (exp, b) -> null), conditions, box);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static void neverStored(Harness h) throws Exception {
      verify(h.conditions, never()).setModel(anyString(), anyString(), any(), anyString(),
                                             any(Principal.class), any());
   }

   // ── date_in ───────────────────────────────────────────────────────────────

   @Test
   void refusesADateInVariableHoldingFreeTextAndStoresNothing() throws Exception {
      Harness h = harness(model(), variables("PeriodInput", "Last fortnight"));

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "T", List.of(
            variableClause(DATE, "date_in", "PeriodInput")), ""));

      String message = thrown.getMessage();
      assertTrue(message.contains("PeriodInput"), message);
      assertTrue(message.contains("Last fortnight"), message);
      assertTrue(message.contains(DATE), message);
      assertTrue(message.contains("date_in"), message);
      assertTrue(message.contains("Nothing was changed"), message);
      neverStored(h);
   }

   @Test
   void refusesADateInVariableHoldingANumber() throws Exception {
      Harness h = harness(model(), variables("Qty", 5));

      assertThrows(IllegalArgumentException.class,
                   () -> h.service.set("tok", principal(), "T", List.of(
                      variableClause(DATE, "date_in", "Qty")), ""));
      neverStored(h);
   }

   @Test
   void acceptsADateInVariableHoldingABuiltInRangeInAnyCase() throws Exception {
      Harness h = harness(model(), variables("PeriodInput", "this year"));

      AssemblyConditionService.SetResult result = h.service.setWithWarnings(
         "tok", principal(), "T", List.of(variableClause(DATE, "date_in", "PeriodInput")), "");

      assertEquals(1, result.applied());
      assertTrue(result.warnings().isEmpty(), result.warnings().toString());
   }

   // ── other type clashes warn ───────────────────────────────────────────────

   @Test
   void warnsWhenANumericVariableIsComparedWithADateFieldButStillStoresIt() throws Exception {
      Harness h = harness(model(), variables("QtySlider", 5));

      AssemblyConditionService.SetResult result = h.service.setWithWarnings(
         "tok", principal(), "T", List.of(variableClause(DATE, "greater_than", "QtySlider")), "");

      assertEquals(1, result.applied());
      assertEquals(1, result.warnings().size(), result.warnings().toString());
      String warning = result.warnings().get(0);
      assertTrue(warning.contains("QtySlider"), warning);
      assertTrue(warning.contains("5"), warning);
      assertTrue(warning.contains(DATE), warning);
      assertTrue(warning.contains("greater_than"), warning);
      assertTrue(warning.contains("number"), warning);
   }

   @Test
   void warnsWhenATextVariableIsComparedWithANumericField() throws Exception {
      Harness h = harness(model(), variables("TxtIn", "abc"));

      AssemblyConditionService.SetResult result = h.service.setWithWarnings(
         "tok", principal(), "T", List.of(variableClause(NUM, "greater_than", "TxtIn")), "");

      assertEquals(1, result.warnings().size(), result.warnings().toString());
      assertTrue(result.warnings().get(0).contains("TxtIn"));
      assertTrue(result.warnings().get(0).contains("'abc'"));
   }

   @Test
   void aFittingVariableValueIsNotWarnedAbout() throws Exception {
      Harness h = harness(model(), variables("Min", 5));

      AssemblyConditionService.SetResult result = h.service.setWithWarnings(
         "tok", principal(), "T", List.of(variableClause(NUM, "greater_than", "Min")), "");

      assertTrue(result.warnings().isEmpty(), result.warnings().toString());
   }

   @Test
   void warnsThatAnUnknownVariableHasNothingToCompareAgainst() throws Exception {
      Harness h = harness(model(), variables("Other", 1));

      AssemblyConditionService.SetResult result = h.service.setWithWarnings(
         "tok", principal(), "T", List.of(variableClause(NUM, "greater_than", "NoSuchVar")), "");

      assertEquals(1, result.applied());
      assertEquals(1, result.warnings().size(), result.warnings().toString());
      assertTrue(result.warnings().get(0).contains("NoSuchVar"));
      assertTrue(result.warnings().get(0).contains("not defined"));
   }

   @Test
   void warnsThatAVariableWithNoValueYetHasNothingToCompareAgainst() throws Exception {
      Harness h = harness(model(), variables("Unanswered", null));

      AssemblyConditionService.SetResult result = h.service.setWithWarnings(
         "tok", principal(), "T", List.of(variableClause(DATE, "date_in", "Unanswered")), "");

      assertEquals(1, result.applied());
      assertEquals(1, result.warnings().size(), result.warnings().toString());
      assertTrue(result.warnings().get(0).contains("Unanswered"));
      assertTrue(result.warnings().get(0).contains("no value yet"));
   }

   // ── a query that fails because of the variable ───────────────────────────

   /** The first read is the table before the write, the second the table after it. */
   @Test
   void undoesAWriteWhoseVariableMakesTheQueryFailAndSaysWhy() throws Exception {
      VSConditionDialogModel existing = model();
      Object[] before = existing.getConditionList();
      Harness h = harness(existing, variables("QtySlider", 5));
      when(h.box.getData("T")).thenReturn(new DefaultTableLens(1, 1),
         new DefaultTableFilter(failedQuery("int into TIMESTAMP")));

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "T", List.of(
            variableClause(DATE, "greater_than", "QtySlider")), ""));

      String message = thrown.getMessage();
      assertTrue(message.contains("int into TIMESTAMP"), message);
      assertTrue(message.contains("QtySlider = '5'"), message);
      assertTrue(message.contains(DATE + " greater_than"), message);
      assertTrue(message.contains("Nothing was changed"), message);
      assertSame(before, existing.getConditionList());
      verify(h.conditions, times(2)).setModel(anyString(), anyString(), any(), anyString(),
                                              any(Principal.class), any());
   }

   @Test
   void keepsAWriteWhenTheTableWasAlreadyFailingBeforeIt() throws Exception {
      Harness h = harness(model(), variables("Min", 5));
      when(h.box.getData("T")).thenReturn(failedQuery("source down"));

      AssemblyConditionService.SetResult result = h.service.setWithWarnings(
         "tok", principal(), "T", List.of(variableClause(NUM, "greater_than", "Min")), "");

      assertEquals(1, result.applied());
      verify(h.conditions, times(1)).setModel(anyString(), anyString(), any(), anyString(),
                                              any(Principal.class), any());
   }

   @Test
   void doesNotReadTheTableForAWriteWithoutAVariableOperand() throws Exception {
      Harness h = harness(model(), variables());

      h.service.set("tok", principal(), "T", List.of(new ConditionVocabulary.Clause(
         NUM, "greater_than", List.of(3), null, false, false, 0)), "");

      verify(h.box, never()).getData(anyString());
   }

   @Test
   void aScriptFailureWhileReadingTheTableIsNotMistakenForAFailedQuery() throws Exception {
      Harness h = harness(model(), variables("Min", 5));
      when(h.box.getData("T")).thenThrow(new RuntimeException("script failed"));

      AssemblyConditionService.SetResult result = h.service.setWithWarnings(
         "tok", principal(), "T", List.of(variableClause(NUM, "greater_than", "Min")), "");

      assertEquals(1, result.applied());
   }

   // ── the failed-query marker is seen through filter wrappers ──────────────

   @Test
   void findsTheFailedQueryDefaultBehindATableFilter() {
      assertEquals("boom", WizVsService.findFailedQueryCause(
         new DefaultTableFilter(new DefaultTableFilter(failedQuery("boom")))));
   }

   @Test
   void findsNothingInARealTable() {
      assertNull(WizVsService.findFailedQueryCause(new DefaultTableFilter(
         new DefaultTableLens(1, 1))));
   }
}

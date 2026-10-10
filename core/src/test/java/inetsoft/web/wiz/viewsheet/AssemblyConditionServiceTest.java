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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.condition.JunctionOperatorModel;
import inetsoft.web.composer.model.vs.VSConditionDialogModel;
import inetsoft.web.composer.vs.dialog.VSConditionDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AssemblyConditionServiceTest {
   private static DataRefModel field(String name) {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn(name);
      return field;
   }

   private static VSConditionDialogModel model() {
      VSConditionDialogModel model = new VSConditionDialogModel();
      model.setTableName("Orders");
      model.setFields(new DataRefModel[]{ field("Region"), field("Revenue") });
      return model;
   }

   private static ConditionVocabulary.Clause clause(String f, String op, List<Object> v,
                                                    String junction)
   {
      return new ConditionVocabulary.Clause(f, op, v, junction, false, false, 0);
   }

   @Test
   void writesTheAlternatingArrayIntoTheModelItRead() throws Exception {
      Harness h = harness(model());

      int applied = h.service.set("tok", principal(), "Chart1",
                                  List.of(clause("Region", "one_of", List.of("East"), "and"),
                                          clause("Revenue", ">", List.of(1000), null)), "");

      assertEquals(2, applied);
      VSConditionDialogModel posted = capture(h.conditions);
      assertEquals(3, posted.getConditionList().length);
      assertInstanceOf(ConditionModel.class, posted.getConditionList()[0]);
      assertInstanceOf(JunctionOperatorModel.class, posted.getConditionList()[1]);
   }

   /** tableName and fields are model state no caller supplies; losing them breaks the dialog. */
   @Test
   void preservesTheModelStateTheCallerNeverSupplies() throws Exception {
      Harness h = harness(model());

      h.service.set("tok", principal(), "Chart1",
                    List.of(clause("Region", "equals", List.of("East"), null)), "");

      VSConditionDialogModel posted = capture(h.conditions);
      assertEquals("Orders", posted.getTableName());
      assertEquals(2, posted.getFields().length);
   }

   @Test
   void eachWriteIsExactlyOneCheckpoint() throws Exception {
      Harness h = harness(model());

      h.service.set("tok", principal(), "Chart1",
                    List.of(clause("Region", "equals", List.of("East"), null)), "");

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void clearingWritesAnEmptyArray() throws Exception {
      Harness h = harness(model());

      h.service.clear("tok", principal(), "Chart1", "");

      assertEquals(0, capture(h.conditions).getConditionList().length);
   }

   @Test
   void validatesFieldsAgainstTheModelSoAnUnknownColumnNeverReachesTheBackend() {
      Harness h = harness(model());

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Chart1",
                             List.of(clause("Profit", "equals", List.of(1), null)), ""));

      assertTrue(thrown.getMessage().contains("Profit"));
      assertTrue(thrown.getMessage().contains("Region"));
   }

   @Test
   void refusesAnAssemblyWithNoConditionDialog() {
      Harness h = harness(null);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Text1",
                             List.of(clause("Region", "equals", List.of(1), null)), ""));

      assertTrue(thrown.getMessage().contains("Text1"));
      assertTrue(thrown.getMessage().contains("filtered"));
   }

   // ── the read side ─────────────────────────────────────────────────────────

   @Test
   void readsConditionsBackInTheFlatVocabularyWithACount() throws Exception {
      VSConditionDialogModel existing = model();
      existing.setConditionList(ConditionVocabulary.toConditionList(
         List.of(clause("Region", "one_of", List.of("East"), "and"),
                 clause("Revenue", ">", List.of(1), null)),
         existing.getFields()));
      Harness h = harness(existing);

      Map<String, Object> read = h.service.read("tok", principal(), "Chart1");

      assertEquals(2, read.get("conditionCount"));
      assertEquals(List.of("Region", "Revenue"), read.get("fields"));
      verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void readsAnAssemblyWithNoDialogAsUnfiltered() throws Exception {
      Map<String, Object> read = harness(null).service.read("tok", principal(), "Text1");

      assertEquals(List.of(), read.get("conditions"));
   }

   // ── value browsing ────────────────────────────────────────────────────────

   @Test
   void resolvesTheColumnToItsRefBeforeBrowsing() throws Exception {
      Harness h = harness(model());

      h.service.browseValues("tok", principal(), "Chart1", "Region");

      ArgumentCaptor<DataRefModel> captor = ArgumentCaptor.forClass(DataRefModel.class);
      verify(h.conditions).browseData(eq("rt1"), eq("Orders"), eq("Chart1"), eq(false),
                                      captor.capture(), any(Principal.class));
      assertEquals("Region", captor.getValue().getName());
   }

   /**
    * Browsing an unknown column returns nothing, which reads as "this column has no values"
    * rather than "you named it wrong".
    */
   @Test
   void refusesToBrowseAnUnknownColumn() throws Exception {
      Harness h = harness(model());

      Exception thrown = assertThrows(
         Exception.class, () -> h.service.browseValues("tok", principal(), "Chart1", "Profit"));

      assertTrue(thrown.getMessage().contains("Profit"));
      assertTrue(thrown.getMessage().contains("Region"));
      verify(h.conditions, never()).browseData(anyString(), any(), anyString(), any(), any(),
                                               any(Principal.class));
   }

   @Test
   void refusesToBrowseWithNoColumn() {
      Harness h = harness(model());

      assertThrows(Exception.class,
                   () -> h.service.browseValues("tok", principal(), "Chart1", "  "));
   }

   @Test
   void vocabularyComesFromTheSharedConditionVocabulary() {
      assertEquals(ConditionVocabulary.vocabulary().get("operators"),
                   harness(model()).service.vocabulary().get("operators"));
   }

   // ── expression operands (bug #78260) ──────────────────────────────────────

   private static ConditionVocabulary.Clause expression(String text, String language) {
      return clause("Revenue", ">",
                    List.of(Map.of("type", "expression", "expression", text,
                                   "language", language)), null);
   }

   @Test
   void refusesAnExpressionThatDoesNotCompileAndCommitsNothing() throws Exception {
      Harness h = harness(model(), (exp, box) -> { throw new Exception("SyntaxError: " + exp); });

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Table1", List.of(expression("1 +* 2", "js")), ""));

      assertTrue(thrown.getMessage().contains("does not compile"));
      assertTrue(thrown.getMessage().contains("Nothing was changed"));
      verify(h.conditions, never()).setModel(anyString(), anyString(), any(), anyString(),
                                             any(Principal.class), any());
   }

   /** Not rejected: the same expression is valid once, say, a selection list has a selection. */
   @Test
   void acceptsAnExpressionThatThrowsAtRuntimeButWarns() throws Exception {
      Harness h = harness(model(), (exp, box) -> "ReferenceError: undefinedFn is not defined");

      AssemblyConditionService.SetResult result = h.service.setWithWarnings(
         "tok", principal(), "Table1", List.of(expression("undefinedFn(1)", "js")), "");

      assertEquals(1, result.applied());
      assertEquals(1, result.warnings().size());
      assertTrue(result.warnings().get(0).contains("undefinedFn is not defined"));
      assertTrue(result.warnings().get(0).contains("no rows"));
      assertEquals(1, capture(h.conditions).getConditionList().length);
   }

   @Test
   void acceptsAnSqlExpressionThatEvaluates() throws Exception {
      Harness h = harness(model(), (exp, box) -> null);

      AssemblyConditionService.SetResult result = h.service.setWithWarnings(
         "tok", principal(), "Table1", List.of(expression("10 + 1", "sql")), "");

      assertEquals(1, result.applied());
      assertTrue(result.warnings().isEmpty());
   }

   @Test
   void refusesAnSqlExpressionThatFailsWithTheJavaScriptNote() throws Exception {
      Harness h = harness(model(), (exp, box) -> "ReferenceError: NOSUCHFN is not defined");

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Table1",
                             List.of(expression("NOSUCHFN(REVENUE)", "sql")), ""));

      assertTrue(thrown.getMessage().contains(
         "sql-language expressions are evaluated as JavaScript on viewsheet assemblies"));
      verify(h.conditions, never()).setModel(anyString(), anyString(), any(), anyString(),
                                             any(Principal.class), any());
   }

   @Test
   void restoresThePreviousConditionsWhenTheRefreshFailsAfterTheCommit() throws Exception {
      VSConditionDialogModel existing = model();
      Object[] before = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals", List.of("East"), null)), existing.getFields());
      existing.setConditionList(before);
      Harness h = harness(existing, (exp, box) -> null);
      doThrow(new RuntimeException("refresh failed")).doNothing().when(h.conditions)
         .setModel(anyString(), anyString(), any(), anyString(), any(Principal.class), any());

      assertThrows(RuntimeException.class,
                   () -> h.service.set("tok", principal(), "Table1",
                                       List.of(expression("1", "js")), ""));

      assertSame(before, existing.getConditionList());
      verify(h.conditions, times(2)).setModel(anyString(), anyString(), any(), anyString(),
                                              any(Principal.class), any());
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private record Harness(AssemblyConditionService service, ViewsheetSessionService sessions,
                          VSConditionDialogService conditions) {}

   private static VSConditionDialogModel capture(VSConditionDialogService conditions)
      throws Exception
   {
      ArgumentCaptor<VSConditionDialogModel> captor =
         ArgumentCaptor.forClass(VSConditionDialogModel.class);
      verify(conditions).setModel(eq("rt1"), anyString(), captor.capture(), anyString(),
                                  any(Principal.class), any());
      return captor.getValue();
   }

   private static Harness harness(VSConditionDialogModel model) {
      return harness(model, null);
   }

   private static Harness harness(VSConditionDialogModel model,
                                  AssemblyConditionService.ExpressionChecker checker)
   {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
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

      AssemblyConditionService service = checker == null
         ? new AssemblyConditionService(sessions, conditions, (exp, box) -> null)
         : new AssemblyConditionService(sessions, conditions, checker);
      return new Harness(service, sessions, conditions);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}

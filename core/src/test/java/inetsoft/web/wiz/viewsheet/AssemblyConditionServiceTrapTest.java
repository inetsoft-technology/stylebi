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
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.vs.VSConditionDialogModel;
import inetsoft.web.composer.vs.dialog.VSConditionDialogService;
import inetsoft.web.composer.ws.assembly.ConditionTrapModel;
import inetsoft.web.composer.ws.assembly.ConditionTrapValidator;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80476: the Composer's condition dialog asks to confirm a condition that introduces a
 * data-model trap. {@code set_condition} now asks the same {@code checkConditionTrap} before the
 * write and adds a warning; the write is applied either way.
 */
@Tag("core")
class AssemblyConditionServiceTrapTest {
   @Test
   void aNewTrapWarnsAndTheWriteStillHappens() throws Exception {
      Fixture f = new Fixture(ConditionTrapValidator.builder().showTrap(true)
                                 .trapFields(new DataRefModel[0]).build());

      AssemblyConditionService.SetResult result = f.service.setWithWarnings(
         "tok", principal(), "Table1", List.of(clause()), "");

      assertEquals(1, result.warnings().size(), result.warnings().toString());
      assertTrue(result.warnings().get(0).startsWith("Data-model trap: "),
                 result.warnings().get(0));
      assertTrue(result.warnings().get(0).contains("The write was applied"));
      assertEquals(1, result.applied());
      verify(f.conditions).setModel(eq("rt1"), eq("Table1"), any(), anyString(),
                                    any(Principal.class), any());

      ArgumentCaptor<ConditionTrapModel> asked = ArgumentCaptor.forClass(ConditionTrapModel.class);
      verify(f.conditions).checkConditionTrap(eq("rt1"), asked.capture(), any(Principal.class));
      assertEquals("Table1", asked.getValue().tableName());
      assertEquals(0, asked.getValue().oldConditionList().length);
      assertEquals(1, asked.getValue().newConditionList().length);
   }

   @Test
   void noTrapIsSilent() throws Exception {
      Fixture f = new Fixture(ConditionTrapValidator.builder().showTrap(false)
                                 .trapFields(new DataRefModel[0]).build());

      AssemblyConditionService.SetResult result = f.service.setWithWarnings(
         "tok", principal(), "Table1", List.of(clause()), "");

      assertTrue(result.warnings().isEmpty(), result.warnings().toString());
      verify(f.conditions).setModel(eq("rt1"), eq("Table1"), any(), anyString(),
                                    any(Principal.class), any());
   }

   /** No cardinality in the model: the native check returns null. */
   @Test
   void aModelThatIsNotCheckedIsSilent() throws Exception {
      Fixture f = new Fixture(null);

      assertTrue(f.service.setWithWarnings("tok", principal(), "Table1", List.of(clause()), "")
                    .warnings().isEmpty());
   }

   /** A failing check must not fail or block the write. */
   @Test
   void aFailingCheckDoesNotBlockTheWrite() throws Exception {
      Fixture f = new Fixture(null);
      when(f.conditions.checkConditionTrap(anyString(), any(), any(Principal.class)))
         .thenThrow(new IllegalStateException("boom"));

      AssemblyConditionService.SetResult result = f.service.setWithWarnings(
         "tok", principal(), "Table1", List.of(clause()), "");

      assertTrue(result.warnings().isEmpty(), result.warnings().toString());
      verify(f.conditions).setModel(eq("rt1"), eq("Table1"), any(), anyString(),
                                    any(Principal.class), any());
   }

   private static ConditionVocabulary.Clause clause() {
      return new ConditionVocabulary.Clause("Region", "equals", List.of("East"), null, false,
                                            false, 0);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static final class Fixture {
      Fixture(ConditionTrapValidator validator) throws Exception {
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
         when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
         when(rvs.getID()).thenReturn("rt1");

         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());

         DataRefModel region = mock(DataRefModel.class);
         when(region.getName()).thenReturn("Region");
         VSConditionDialogModel model = new VSConditionDialogModel();
         model.setTableName("Table1");
         model.setFields(new DataRefModel[]{ region });

         conditions = mock(VSConditionDialogService.class);
         when(conditions.getModel(anyString(), anyString(), any(Principal.class)))
            .thenReturn(model);
         when(conditions.checkConditionTrap(anyString(), any(), any(Principal.class)))
            .thenReturn(validator);
         service = new AssemblyConditionService(
            sessions, conditions, mock(VSRefreshService.class),
            (exp, box) -> new VSAQuery.ExpressionResult(null, null));
      }

      final VSConditionDialogService conditions;
      final AssemblyConditionService service;
   }
}

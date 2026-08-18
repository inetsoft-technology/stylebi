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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import inetsoft.web.wiz.script.model.ScriptInfo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class ScriptExecuteServiceTest {

   /**
    * Builds a RuntimeViewsheet with real onInit script text (via a real Viewsheet +
    * ViewsheetInfo, mirroring the "real domain object" pattern in WorksheetEditServiceTest)
    * and a mocked sandbox/scope so execute() doesn't need a real GraalJS engine.
    */
   private RuntimeViewsheet viewsheetWithScript(String script, ViewsheetScope scope) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setOnInit(script);
      vs.getViewsheetInfo().setScriptEnabled(true);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getScope()).thenReturn(scope);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      return rvs;
   }

   @Test
   void dryRunRefusesAndDoesNotExecuteWhenScriptReferencesADestructiveGlobal() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      RuntimeViewsheet rvs = viewsheetWithScript("runQuery('ds', {})", scope);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.dryRun(rvs, ScriptTarget.parse("vs-init"));

      assertFalse(result.ok());
      assertTrue(result.requiresConfirmation());
      assertNotNull(result.confirmationReason());
      assertTrue(result.confirmationReason().contains("runQuery"));
      verify(scope, never()).execute(anyString(), nullable(String.class));
   }

   @Test
   void runLiveRefusesAndDoesNotExecuteWithoutConfirmation() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      RuntimeViewsheet rvs = viewsheetWithScript("setCellValue('T', 0, 0, 1)", scope);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, ScriptTarget.parse("vs-init"), false);

      assertFalse(result.ok());
      assertTrue(result.requiresConfirmation());
      verify(scope, never()).execute(anyString(), nullable(String.class));
   }

   @Test
   void runLiveExecutesOnceConfirmed() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq("setCellValue('T', 0, 0, 1)"), nullable(String.class))).thenReturn(true);
      RuntimeViewsheet rvs = viewsheetWithScript("setCellValue('T', 0, 0, 1)", scope);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, ScriptTarget.parse("vs-init"), true);

      assertTrue(result.ok());
      assertFalse(result.requiresConfirmation());
      assertEquals(true, result.value());
      verify(scope).execute(eq("setCellValue('T', 0, 0, 1)"), nullable(String.class));
   }

   @Test
   void dryRunExecutesNonDestructiveScriptsAgainstTheLiveScope() throws Exception {
      // Documents the known, accepted limitation: "dry run" has no isolated clone to run
      // against, so any non-destructive-named mutation runs for real. See the class javadoc.
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq("1 + 1"), nullable(String.class))).thenReturn(2.0);
      RuntimeViewsheet rvs = viewsheetWithScript("1 + 1", scope);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.dryRun(rvs, ScriptTarget.parse("vs-init"));

      assertTrue(result.ok());
      assertEquals(2.0, result.value());
      assertEquals(java.util.List.of("vs-init"), result.changed());
      verify(scope).execute(eq("1 + 1"), nullable(String.class));
   }

   @Test
   void executeReturnsOkNoOpWhenThereIsNoScript() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      RuntimeViewsheet rvs = viewsheetWithScript(null, scope);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.dryRun(rvs, ScriptTarget.parse("vs-init"));

      assertTrue(result.ok());
      assertEquals(java.util.List.of(), result.changed());
      verify(scope, never()).execute(anyString(), nullable(String.class));
   }

   /**
    * Mocks ScriptReadService (rather than using the real one, as every other test here does) so
    * execution reaches ScriptExecuteService's OWN switch(target.location()) instead of being
    * refused earlier by ScriptReadService's unrelated "Unsupported target" default case. That
    * switch is a switch EXPRESSION with no default, so a fifth Location value made it fail to
    * compile at all until CALC_FIELD was added — this proves the added case both compiles and
    * refuses with the intended, permanent message (a calc field is an expression, not a script).
    */
   @Test
   void dryRunRefusesACalcFieldTargetWithASpecificMessage() throws Exception {
      ScriptReadService readService = mock(ScriptReadService.class);
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "Margin");
      when(readService.read(any(), eq(target))).thenReturn(new ScriptInfo(null, "irrelevant", true));

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getScope()).thenReturn(mock(ViewsheetScope.class));
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      ScriptExecuteService svc = new ScriptExecuteService(readService);

      PairingException ex = assertThrows(PairingException.class, () -> svc.dryRun(rvs, target));
      assertTrue(ex.getMessage().contains("not a runnable script"),
                 "must explain WHY, not just refuse: " + ex.getMessage());
   }

   @Test
   void runLiveRefusesACalcFieldTargetWithASpecificMessage() throws Exception {
      ScriptReadService readService = mock(ScriptReadService.class);
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "Margin");
      when(readService.read(any(), eq(target))).thenReturn(new ScriptInfo(null, "irrelevant", true));

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getScope()).thenReturn(mock(ViewsheetScope.class));
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      ScriptExecuteService svc = new ScriptExecuteService(readService);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.runLive(rvs, target, true));
      assertTrue(ex.getMessage().contains("not a runnable script"),
                 "must explain WHY, not just refuse: " + ex.getMessage());
   }
}

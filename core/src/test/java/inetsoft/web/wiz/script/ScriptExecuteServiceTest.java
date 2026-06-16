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
import inetsoft.report.script.viewsheet.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ScriptExecuteService}.
 *
 * <p>Dry-run and live-run tests mock the {@link ViewsheetSandbox} and
 * {@link ViewsheetScope} — full integration tests live in
 * {@link inetsoft.report.script.viewsheet.ViewsheetScopeStructuredExecuteTest}.</p>
 */
@WizAgentTestSupport
class ScriptExecuteServiceTest {

   private final ScriptExecuteService service = new ScriptExecuteService();

   // =========================================================================
   // detectDestructiveGlobals()
   // =========================================================================

   @Test
   void detectsKnownDestructiveGlobals() {
      List<String> found = service.detectDestructiveGlobals("runQuery('q1'); saveWorksheet();");
      assertTrue(found.contains("runQuery"));
      assertTrue(found.contains("saveWorksheet"));
   }

   @Test
   void emptyScriptHasNoDestructiveGlobals() {
      assertTrue(service.detectDestructiveGlobals("").isEmpty());
      assertTrue(service.detectDestructiveGlobals(null).isEmpty());
   }

   @Test
   void harmlessScriptHasNoDestructiveGlobals() {
      assertTrue(service.detectDestructiveGlobals("var x = 1 + 2;").isEmpty());
   }

   // =========================================================================
   // dryRun() — sandbox unavailable
   // =========================================================================

   @Test
   void dryRunThrowsWhenNoSandbox() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());

      assertThrows(inetsoft.web.wiz.pairing.PairingException.class,
         () -> service.dryRun(rvs, ScriptTarget.parse("vs-init")));
   }

   // =========================================================================
   // dryRun() — empty script
   // =========================================================================

   @Test
   void dryRunEmptyScriptReturnsSuccess() throws Exception {
      Viewsheet vs = new Viewsheet();
      // vs-init is empty by default
      RuntimeViewsheet rvs = mockRvsWithSandboxScope(vs);

      ScriptExecResult result = service.dryRun(rvs, ScriptTarget.parse("vs-init"));
      assertTrue(result.ok());
      assertNull(result.error());
   }

   // =========================================================================
   // dryRun() — assembly script, restores state
   // =========================================================================

   @Test
   void dryRunAssemblyRestoresOriginalState() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "chart1");
      chart.getVSAssemblyInfo().setScript("/* script */");
      chart.getVSAssemblyInfo().setScriptEnabled(true);
      // Record original script text — the script we mock will not change it in reality,
      // but the copyInfo restore should bring it back even if something changed.
      String originalScript = chart.getVSAssemblyInfo().getScript();
      vs.addAssembly(chart);

      ViewsheetScope scope = mock(ViewsheetScope.class);
      VSAScriptable scriptable = mock(VSAScriptable.class);
      when(scope.getVSAScriptable("chart1")).thenReturn(scriptable);

      // Simulate a successful script execution that mutates the assembly's script text.
      when(scope.executeStructured(anyString(), eq(scriptable)))
         .thenAnswer(inv -> {
            chart.getVSAssemblyInfo().setScript("/* mutated by script */");
            return ScriptExecResult.success(null);
         });

      RuntimeViewsheet rvs = mockRvsWithScope(vs, scope);

      ScriptExecResult result = service.dryRun(rvs, ScriptTarget.parse("assembly:chart1"));

      assertTrue(result.ok(), "dry-run should succeed");
      // The assembly's script text must be restored to the original.
      assertEquals(originalScript, chart.getVSAssemblyInfo().getScript(),
         "dry-run must restore the assembly's original state");
   }

   // =========================================================================
   // runLive() — destructive global guardrail
   // =========================================================================

   @Test
   void runLiveRequiresConfirmationForDestructiveGlobals() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setOnInit("runQuery('q1');");
      RuntimeViewsheet rvs = mockRvsWithSandboxScope(vs);

      ScriptExecResult result = service.runLive(rvs, ScriptTarget.parse("vs-init"), false);

      assertFalse(result.ok());
      assertTrue(result.requiresConfirmation());
      assertNotNull(result.error());
   }

   @Test
   void runLiveWithConfirmedBypassesGuardrail() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setOnInit("runQuery('q1');");

      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.executeStructured(anyString(), isNull()))
         .thenReturn(ScriptExecResult.success(null));

      RuntimeViewsheet rvs = mockRvsWithScope(vs, scope);

      // confirmed=true bypasses the guardrail
      ScriptExecResult result = service.runLive(rvs, ScriptTarget.parse("vs-init"), true);
      assertTrue(result.ok());
   }

   // =========================================================================
   // Helpers
   // =========================================================================

   /**
    * Creates a mocked {@link RuntimeViewsheet} whose sandbox returns the given scope
    * and whose viewsheet returns the given {@link Viewsheet}.
    */
   private RuntimeViewsheet mockRvsWithScope(Viewsheet vs, ViewsheetScope scope) {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getScope()).thenReturn(scope);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      return rvs;
   }

   /**
    * Creates a mocked {@link RuntimeViewsheet} with a minimal scope (no-op execute).
    */
   private RuntimeViewsheet mockRvsWithSandboxScope(Viewsheet vs) {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.getVSAScriptable(anyString())).thenReturn(mock(VSAScriptable.class));
      when(scope.executeStructured(anyString(), any()))
         .thenReturn(ScriptExecResult.success(null));
      return mockRvsWithScope(vs, scope);
   }
}

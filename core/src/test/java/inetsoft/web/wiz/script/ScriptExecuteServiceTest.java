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
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.viewsheet.SubmitVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SubmitVSAssemblyInfo;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import inetsoft.web.wiz.script.model.ScriptInfo;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

   /**
    * Builds a RuntimeViewsheet with a real Submit1 assembly (so {@code getViewsheet().getAssembly}
    * resolves) and a mocked scope/scriptable, for exercising the ASSEMBLY-location
    * unrecognized-write reporting in {@code execute()}.
    */
   private RuntimeViewsheet viewsheetWithAssemblyScript(String script, ViewsheetScope scope,
                                                        VSAScriptable scriptable)
   {
      Viewsheet vs = new Viewsheet();
      SubmitVSAssembly submit = new SubmitVSAssembly();
      SubmitVSAssemblyInfo info = (SubmitVSAssemblyInfo) submit.getVSAssemblyInfo();
      info.setName("Submit1");
      info.setScript(script);
      info.setScriptEnabled(true);
      vs.addAssembly(submit);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getScope()).thenReturn(scope);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(scope.getVSAScriptable("Submit1")).thenReturn(scriptable);

      return rvs;
   }

   @Test
   void executeExcludesTargetFromChangedAndReportsUnrecognizedPropertyWrite() throws Exception {
      String script = "Submit1.label = 'ClauseTest42';";
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), eq("Submit1"))).thenReturn("ClauseTest42");

      VSAScriptable scriptable = mock(VSAScriptable.class);
      when(scriptable.getUnrecognizedWrites()).thenReturn(List.of("label"));

      RuntimeViewsheet rvs = viewsheetWithAssemblyScript(script, scope, scriptable);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Submit1"), false);

      assertTrue(result.ok());
      assertEquals(List.of(), result.changed());
      assertEquals(List.of("label"), result.unrecognizedProperties());
      assertTrue(result.summary().contains("label"), result.summary());
      assertTrue(result.summary().contains("SubmitVSAssembly"), result.summary());
      verify(scriptable).resetUnrecognizedWrites();
   }

   @Test
   void executeReportsRealPropertyWriteNormallyWithNoUnrecognizedProperties() throws Exception {
      String script = "Submit1.enabled = false;";
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), eq("Submit1"))).thenReturn(false);

      VSAScriptable scriptable = mock(VSAScriptable.class);
      when(scriptable.getUnrecognizedWrites()).thenReturn(List.of());

      RuntimeViewsheet rvs = viewsheetWithAssemblyScript(script, scope, scriptable);
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Submit1");
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, target, false);

      assertTrue(result.ok());
      assertEquals(List.of(target.toString()), result.changed());
      assertNull(result.unrecognizedProperties());
      verify(scriptable).resetUnrecognizedWrites();
   }

   /**
    * Builds a RuntimeViewsheet with a real onInit (or onLoad) script and one real
    * {@code SubmitVSAssembly} per entry in {@code scriptablesByAssembly}, each wired to the
    * given mock scriptable via {@code scope.getVSAScriptable(name)} -- for exercising the
    * VS_INIT/VS_LOAD-location unrecognized-write reporting, which (unlike
    * ASSEMBLY/ASSEMBLY_ONCLICK) runs with {@code assemblyName == null} and can write to any
    * assembly in the sheet by name, not just a single "current" one.
    */
   private RuntimeViewsheet viewsheetWithVsScript(boolean onLoad, String script,
                                                   ViewsheetScope scope,
                                                   Map<String, VSAScriptable> scriptablesByAssembly)
   {
      Viewsheet vs = new Viewsheet();

      if(onLoad) {
         vs.getViewsheetInfo().setOnLoad(script);
      }
      else {
         vs.getViewsheetInfo().setOnInit(script);
      }

      vs.getViewsheetInfo().setScriptEnabled(true);

      for(String name : scriptablesByAssembly.keySet()) {
         SubmitVSAssembly submit = new SubmitVSAssembly();
         ((SubmitVSAssemblyInfo) submit.getVSAssemblyInfo()).setName(name);
         vs.addAssembly(submit);
      }

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getScope()).thenReturn(scope);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      for(Map.Entry<String, VSAScriptable> entry : scriptablesByAssembly.entrySet()) {
         when(scope.getVSAScriptable(entry.getKey())).thenReturn(entry.getValue());
      }

      return rvs;
   }

   @Test
   void executeQualifiesAndReportsAnUnrecognizedWriteFromAnAssemblyTouchedByVsInit()
      throws Exception
   {
      String script = "Submit1.label = 'ClauseTest42';";
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), nullable(String.class))).thenReturn(null);

      VSAScriptable submit1 = mock(VSAScriptable.class);
      when(submit1.getUnrecognizedWrites()).thenReturn(List.of("label"));

      RuntimeViewsheet rvs =
         viewsheetWithVsScript(false, script, scope, Map.of("Submit1", submit1));
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, ScriptTarget.parse("vs-init"), false);

      assertTrue(result.ok());
      assertEquals(List.of(), result.changed());
      // Qualified with the assembly name, unlike the single-assembly ASSEMBLY/ASSEMBLY_ONCLICK
      // path -- VS_INIT/VS_LOAD have no single "the assembly" the message can name.
      assertEquals(List.of("Submit1.label"), result.unrecognizedProperties());
      assertTrue(result.summary().contains("Submit1.label"), result.summary());
      verify(submit1).resetUnrecognizedWrites();
   }

   /**
    * VS_INIT can touch several assemblies; each one's scriptable must be reset/checked, but
    * only the one that actually wrote an unrecognized name should show up in the result -- an
    * untouched assembly's scriptable reporting an empty list must not leak into the aggregate.
    */
   @Test
   void executeOnlyReportsUnrecognizedWritesFromTheAssembliesActuallyTouchedByVsInit()
      throws Exception
   {
      String script = "Submit2.label = 'ClauseTest42';";
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), nullable(String.class))).thenReturn(null);

      VSAScriptable submit1 = mock(VSAScriptable.class);
      when(submit1.getUnrecognizedWrites()).thenReturn(List.of());
      VSAScriptable submit2 = mock(VSAScriptable.class);
      when(submit2.getUnrecognizedWrites()).thenReturn(List.of("label"));

      Map<String, VSAScriptable> scriptables = new LinkedHashMap<>();
      scriptables.put("Submit1", submit1);
      scriptables.put("Submit2", submit2);
      RuntimeViewsheet rvs = viewsheetWithVsScript(false, script, scope, scriptables);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, ScriptTarget.parse("vs-init"), false);

      assertTrue(result.ok());
      assertEquals(List.of(), result.changed());
      assertEquals(List.of("Submit2.label"), result.unrecognizedProperties());
      verify(submit1).resetUnrecognizedWrites();
      verify(submit2).resetUnrecognizedWrites();
   }

   @Test
   void executeReportsARealPropertyWriteFromVsInitNormallyWithNoUnrecognizedProperties()
      throws Exception
   {
      String script = "Submit1.enabled = false;";
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), nullable(String.class))).thenReturn(false);

      VSAScriptable submit1 = mock(VSAScriptable.class);
      when(submit1.getUnrecognizedWrites()).thenReturn(List.of());

      RuntimeViewsheet rvs =
         viewsheetWithVsScript(false, script, scope, Map.of("Submit1", submit1));
      ScriptTarget target = ScriptTarget.parse("vs-init");
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, target, false);

      assertTrue(result.ok());
      assertEquals(List.of(target.toString()), result.changed());
      assertNull(result.unrecognizedProperties());
      verify(submit1).resetUnrecognizedWrites();
   }

   /** Same mechanism, the other location that runs with {@code assemblyName == null}. */
   @Test
   void executeTracksUnrecognizedWritesForVsLoadTheSameWayAsVsInit() throws Exception {
      String script = "Submit1.label = 'ClauseTest42';";
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), nullable(String.class))).thenReturn(null);

      VSAScriptable submit1 = mock(VSAScriptable.class);
      when(submit1.getUnrecognizedWrites()).thenReturn(List.of("label"));

      RuntimeViewsheet rvs =
         viewsheetWithVsScript(true, script, scope, Map.of("Submit1", submit1));
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, ScriptTarget.parse("vs-load"), false);

      assertTrue(result.ok());
      assertEquals(List.of(), result.changed());
      assertEquals(List.of("Submit1.label"), result.unrecognizedProperties());
      verify(submit1).resetUnrecognizedWrites();
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

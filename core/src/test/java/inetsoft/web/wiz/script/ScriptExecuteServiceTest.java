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

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.SubmitVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SubmitVSAssemblyInfo;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import inetsoft.web.wiz.script.model.ScriptInfo;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

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

   /**
    * Bug #76853 (VSD-010): {@code maxDate} collides (case-insensitively -- see
    * BindingRootProxy's Calc builtin scope) with the registered Calc scripting function
    * {@code CalcDateTime.maxDate}, so a real GraalJS run routes its writes through
    * VSAScriptable.putMember into unrecognizedWrites even though it is an ordinary script-local
    * declared with {@code var}. Reproduces that reported shape (the exact repro script, and
    * {@code getUnrecognizedWrites()} mocked to the same 4-entries-per-loop the real engine
    * produces) and confirms {@code execute()}'s declared-locals filter excludes it.
    */
   @Test
   void executeExcludesADeclaredLocalFromUnrecognizedPropertiesEvenIfItCollidesWithACalcFunctionName()
      throws Exception
   {
      String script = "var maxVal = -1;\nvar maxDate = null;\n" +
         "for(var i = 0; i < 3; i++) {\n  maxVal = i;\n  maxDate = \"x\" + i;\n}";
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), eq("Submit1"))).thenReturn(null);

      VSAScriptable scriptable = mock(VSAScriptable.class);
      when(scriptable.getUnrecognizedWrites())
         .thenReturn(List.of("maxDate", "maxDate", "maxDate", "maxDate"));

      RuntimeViewsheet rvs = viewsheetWithAssemblyScript(script, scope, scriptable);
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Submit1");
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, target, false);

      assertTrue(result.ok());
      assertEquals(List.of(target.toString()), result.changed());
      assertNull(result.unrecognizedProperties());
   }

   /**
    * Per the refuter's correction to the VSD-010 diagnosis, the Calc-function-name collision
    * itself is case-insensitive, but the declared-locals filter must not rely on that -- it
    * works purely off "was this exact identifier declared as a script-local," so a case-variant
    * collision (which a live engine also flags, per the refuter's experiment) must be excluded
    * too, driven only by the exact-name match against the script's own {@code var} declaration.
    */
   @Test
   void executeExcludesADeclaredLocalFromUnrecognizedPropertiesForACaseVariantCollisionToo()
      throws Exception
   {
      String script = "var MAXVAL = -1;\nvar MAXDATE = null;\nMAXDATE = \"x\";";
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), eq("Submit1"))).thenReturn(null);

      VSAScriptable scriptable = mock(VSAScriptable.class);
      when(scriptable.getUnrecognizedWrites()).thenReturn(List.of("MAXDATE", "MAXDATE"));

      RuntimeViewsheet rvs = viewsheetWithAssemblyScript(script, scope, scriptable);
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Submit1");
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, target, false);

      assertTrue(result.ok());
      assertEquals(List.of(target.toString()), result.changed());
      assertNull(result.unrecognizedProperties());
   }

   /**
    * Bug #76104: "position" IS a registered scriptable property (unlike "label" above), so it
    * must not be reported as "not a recognized scriptable property" -- but its setter silently
    * declined to apply the value (Composer design-mode session, not a live viewer runtime), so
    * execute() must still exclude the target from changed via the new rejected-writes tracking,
    * with wording that doesn't misrepresent the write as unrecognized.
    */
   @Test
   void executeExcludesTargetFromChangedAndDoesNotClaimUnrecognizedForARejectedPositionWrite()
      throws Exception
   {
      String script = "Submit1.position = new Point(180, 100);";
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), eq("Submit1"))).thenReturn(null);

      VSAScriptable scriptable = mock(VSAScriptable.class);
      when(scriptable.getUnrecognizedWrites()).thenReturn(List.of());
      when(scriptable.getRejectedWrites()).thenReturn(List.of("position"));

      RuntimeViewsheet rvs = viewsheetWithAssemblyScript(script, scope, scriptable);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Submit1"), false);

      assertTrue(result.ok());
      assertEquals(List.of(), result.changed());
      assertEquals(List.of("position"), result.unrecognizedProperties());
      assertTrue(result.summary().contains("position"), result.summary());
      assertFalse(result.summary().contains("not a recognized scriptable property"), result.summary());
      assertFalse(result.summary().contains("not recognized scriptable properties"), result.summary());
      verify(scriptable).resetUnrecognizedWrites();
      verify(scriptable).resetRejectedWrites();
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

   /**
    * Round-1 review Finding 1 on bug #76853 (VSD-010): the declared-locals filter must NOT
    * apply across assemblies for VS_INIT/VS_LOAD. Submit2 gets a genuinely wrong dotted write
    * ("label" isn't a real Submit property, same as the pre-existing
    * {@code executeExcludesTargetFromChangedAndReportsUnrecognizedPropertyWrite} case), and the
    * script separately declares an unrelated {@code var label} used only for Submit1 -- a
    * bare-name coincidence that has nothing to do with the Calc-collision/with-scope mechanism
    * this bug's filter targets. The write must still be reported, proving the filter (correctly
    * scoped to the single-assembly ASSEMBLY/ASSEMBLY_ONCLICK case above) does not also swallow
    * it here.
    */
   @Test
   void executeDoesNotFilterADeclaredLocalNameCollisionAcrossDifferentAssembliesInVsInit()
      throws Exception
   {
      String script = "var label = 'helper text for Submit1';\n" +
         "Submit1.toolTip = label;\n" +
         "Submit2.label = 'helper text for Submit1';";
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
      assertEquals(List.of("Submit2.label"), result.unrecognizedProperties());
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

   /** Bug #78812: service whose Script Library is the given entry-name -> source map. */
   private ScriptExecuteService serviceWithLibrary(Map<String, String> library) {
      ScriptExecuteService svc = spy(new ScriptExecuteService(new ScriptReadService()));
      doReturn(library).when(svc).libraryScripts(any());
      return svc;
   }

   private ScriptExecResult runLiveWithLibrary(String script, Map<String, String> library,
                                               boolean confirmed, ViewsheetScope scope)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetWithScript(script, scope);
      return serviceWithLibrary(library).runLive(rvs, ScriptTarget.parse("vs-init"), confirmed);
   }

   @Test
   void libraryFunctionCallingDestructiveGlobalRequiresConfirmation() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      Map<String, String> lib = Map.of("getOrderCount",
         "function getOrderCount() { return runQuery('ws:global:x'); }");

      ScriptExecResult result =
         runLiveWithLibrary("'Total: ' + getOrderCount()", lib, false, scope);

      assertFalse(result.ok());
      assertTrue(result.requiresConfirmation());
      assertTrue(result.confirmationReason().contains("\"runQuery\""));
      assertTrue(result.confirmationReason().contains("\"getOrderCount\""));
      verify(scope, never()).execute(anyString(), nullable(String.class));
   }

   @Test
   void nestedLibraryCallIsFollowed() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      Map<String, String> lib = new LinkedHashMap<>();
      lib.put("outer", "function outer() { return middle(); }");
      lib.put("middle", "function middle() { return inner(); }");
      lib.put("inner", "function inner() { return runQuery('q'); }");

      ScriptExecResult result = runLiveWithLibrary("outer()", lib, false, scope);

      assertTrue(result.requiresConfirmation());
      assertTrue(result.confirmationReason().contains("\"runQuery\""));
   }

   @Test
   void cyclicLibraryCallsTerminate() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), nullable(String.class))).thenReturn(1);
      Map<String, String> lib = new LinkedHashMap<>();
      lib.put("a", "function a() { return b(); }");
      lib.put("b", "function b() { return a(); }");

      ScriptExecResult result = runLiveWithLibrary("a()", lib, false, scope);

      assertTrue(result.ok());
      assertFalse(result.requiresConfirmation());
   }

   @Test
   void entryNameDifferentFromDeclaredFunctionNameIsFollowed() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      Map<String, String> lib = Map.of("orderHelpers",
         "function getOrderCount() { return runQuery('q'); }");

      ScriptExecResult result = runLiveWithLibrary("getOrderCount()", lib, false, scope);

      assertTrue(result.requiresConfirmation());
      assertTrue(result.confirmationReason().contains("\"getOrderCount\" (entry \"orderHelpers\")"));
   }

   @Test
   void multiFunctionEntryIsFollowedThroughAnyDeclaredFunction() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      Map<String, String> lib = Map.of("helpers",
         "function safe() { return 1; }\nvar load = function() { return runQuery('q'); };\n" +
         "const twice = (x) => x * 2;");

      assertTrue(runLiveWithLibrary("load()", lib, false, scope).requiresConfirmation());
      assertTrue(runLiveWithLibrary("safe()", lib, false, scope).requiresConfirmation());
   }

   /** Bug #78998: entry name "lib" never equals the exposed namespace, so only root indexing helps. */
   private void assertGatedViaNamespace(String libSource, String script) throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      ScriptExecResult result = runLiveWithLibrary(script, Map.of("lib", libSource), false, scope);

      assertTrue(result.requiresConfirmation(), libSource);
      assertTrue(result.confirmationReason().contains("\"runQuery\""));
      assertTrue(result.confirmationReason().contains("(entry \"lib\")"));
      verify(scope, never()).execute(anyString(), nullable(String.class));
   }

   @Test
   void objectLiteralMemberIsGated() throws Exception {
      assertGatedViaNamespace("var Ns = { count: function() { return runQuery('q').length; } };",
                              "'n=' + Ns.count()");
   }

   @Test
   void assignedMemberIsGated() throws Exception {
      assertGatedViaNamespace("var Ns = {};\nNs.count = function() { return runQuery('q'); };",
                              "Ns.count()");
      assertGatedViaNamespace("Ns = {};\nNs.count = function() { return runQuery('q'); };",
                              "Ns.count()");
   }

   @Test
   void nestedNamespaceIsGated() throws Exception {
      assertGatedViaNamespace("var A = A || {};\nA.B = {};\nA.B.count = function() { return runQuery('q'); };",
                              "A.B.count()");
   }

   @Test
   void factoryAndIifeNamespacesAreGated() throws Exception {
      assertGatedViaNamespace(
         "var Ns = (function() { return { count: function() { return runQuery('q'); } }; })();",
         "Ns.count()");
      assertGatedViaNamespace(
         "var Ns = make();\nfunction make() { return { count: function() { return runQuery('q'); } }; }",
         "Ns.count()");
   }

   @Test
   void objectAssignAndGlobalQualifiedNamespacesAreGated() throws Exception {
      assertGatedViaNamespace(
         "var Ns = {};\nObject.assign(Ns, { count: function() { return runQuery('q'); } });",
         "Ns.count()");
      assertGatedViaNamespace("window.Ns = { count: function() { return runQuery('q'); } };",
                              "Ns.count()");
      assertGatedViaNamespace("this.Ns = { count: function() { return runQuery('q'); } };",
                              "Ns.count()");
      assertGatedViaNamespace("Ns = { count: function() { return runQuery('q'); } };",
                              "Ns['count']()");
   }

   @Test
   void unrelatedMemberCallIsNotGated() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), nullable(String.class))).thenReturn(1);
      Map<String, String> lib = Map.of("lib",
         "var Ns = { count: function() { return runQuery('q'); } };");

      ScriptExecResult result = runLiveWithLibrary("other.count()", lib, false, scope);

      assertTrue(result.ok());
      assertFalse(result.requiresConfirmation());
   }

   @Test
   void functionLocalNamesAreNotIndexedAsRoots() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), nullable(String.class))).thenReturn(1);
      Map<String, String> lib = Map.of("lib",
         "var Ns = { count: function() { var result = runQuery('q'); var i = 0; return result; } };");

      // 'result' and 'i' are locals of the library function, not exposed globals
      ScriptExecResult result =
         runLiveWithLibrary("var result = 2; for(var i = 0; i < 3; i++) { result += i; } result",
                            lib, false, scope);

      assertTrue(result.ok());
      assertFalse(result.requiresConfirmation());
   }

   @Test
   void globalObjectNamesAreNotIndexedAsRoots() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), nullable(String.class))).thenReturn(1);
      Map<String, String> lib = Map.of("lib",
         "window.Ns = { count: function() { return runQuery('q'); } };\nthis.x = 1;");

      ScriptExecResult result = runLiveWithLibrary("window.foo = 1; this.bar = 2; 3", lib, false, scope);

      assertTrue(result.ok());
      assertFalse(result.requiresConfirmation());
   }

   /** Bug #79322: runs {@code script} against a one-entry library and expects NO confirmation. */
   private void assertNotGated(String libSource, String script) throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), nullable(String.class))).thenReturn(1);

      ScriptExecResult result = runLiveWithLibrary(script, Map.of("lib", libSource), false, scope);

      assertTrue(result.ok(), libSource);
      assertFalse(result.requiresConfirmation(), libSource);
   }

   @Test
   void namespaceExportedThroughAnIifeGlobalAliasIsGated() throws Exception {
      String member = "{ count: function() { return runQuery('q').length; } }";

      assertGatedViaNamespace("(function(g){ g.Ns = " + member + "; })(this);", "Ns.count()");
      assertGatedViaNamespace("(function(g){ g.Ns = " + member + "; }(this));", "Ns.count()");
      assertGatedViaNamespace("(function(g){ g.Ns = " + member + "; })(globalThis);", "Ns.count()");
      assertGatedViaNamespace("((g) => { g.Ns = " + member + "; })(this);", "Ns.count()");
      assertGatedViaNamespace("(function(a, g){ g.Ns = " + member + "; })(1, this);",
                              "Ns.count()");
      assertGatedViaNamespace("(function(g){ if(g) { g.Ns = " + member + "; } })(this);",
                              "Ns.count()");
   }

   @Test
   void namespaceExportedThroughAnIifeThisOrGlobalQualifierIsGated() throws Exception {
      String member = "{ count: function() { return runQuery('q').length; } }";

      assertGatedViaNamespace("(function(){ this.Ns = " + member + "; })();", "Ns.count()");
      assertGatedViaNamespace("(function(){ this.Ns = " + member + "; }).call(this);",
                              "Ns.count()");
      assertGatedViaNamespace("(function(){ window.Ns = " + member + "; })();", "Ns.count()");
      assertGatedViaNamespace("(function(g){ g.Ns = {}; g.Ns.count = function() { return runQuery('q'); }; })(this);",
                              "Ns.count()");
   }

   @Test
   void undeclaredNamespaceLeakedFromAnIifeIsGated() throws Exception {
      assertGatedViaNamespace("(function(){ Ns = { count: function() { return runQuery('q'); } }; })();",
                              "Ns.count()");
      // declared inside the IIFE and re-exported through the alias
      assertGatedViaNamespace(
         "(function(g){ var impl = { count: function() { return runQuery('q'); } }; g.Ns = impl; })(this);",
         "Ns.count()");
   }

   @Test
   void namespaceAssignedInsideAPlainBlockIsGated() throws Exception {
      String member = "{ count: function() { return runQuery('q').length; } }";

      assertGatedViaNamespace("var Ns; if (true) { Ns = " + member + "; }", "Ns.count()");
      assertGatedViaNamespace("var Ns; if (false) { } else { Ns = " + member + "; }", "Ns.count()");
      assertGatedViaNamespace("var Ns; try { Ns = " + member + "; } catch(e) { }", "Ns.count()");
      assertGatedViaNamespace("var Ns = {}; for(var i = 0; i < 1; i++) { Ns.count = function() { return runQuery('q'); }; }",
                              "Ns.count()");
      assertGatedViaNamespace("var Ns; { Ns = " + member + "; }", "Ns.count()");
   }

   @Test
   void localsOfAnIifeOrAFunctionBlockAreNotIndexedAsRoots() throws Exception {
      // a local of the IIFE is not a global, even though the IIFE runs at load time
      assertNotGated("(function(g){ var result = runQuery('q'); g.Ns = { a: 1 }; })(this);",
                     "var result = 2; result");
      assertNotGated("(function(g){ var Ns = { count: function() { return runQuery('q'); } }; })(this);",
                     "Ns.count()");
      assertNotGated("(function(g, Ns){ Ns = { count: function() { return runQuery('q'); } }; })(this, 1);",
                     "Ns.count()");
      // a block inside an ordinary function body does not make its assignments global
      assertNotGated("var Ns = { count: function() { if(true) { result = runQuery('q'); } return result; } };",
                     "var result = 2; result");
      assertNotGated("(function(g){ g.Ns = { count: function() { if(true) { var result = runQuery('q'); } } }; })(this);",
                     "var result = 2; result");
   }

   @Test
   void iifeParameterNotBoundToAGlobalObjectIsNotAnAlias() throws Exception {
      assertNotGated("(function(g){ g.Ns = { count: function() { return runQuery('q'); } }; })(other);",
                     "Ns.count()");
      // an ordinary (non-IIFE) function is not run at load, so its parameter is no alias
      assertNotGated("function install(g) { g.Ns = { count: function() { return runQuery('q'); } }; }",
                     "Ns.count()");
      assertNotGated("var f = function(g){ g.Ns = { count: function() { return runQuery('q'); } }; };",
                     "Ns.count()");
   }

   @Test
   void nonDestructiveNamespaceIsNotGated() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), nullable(String.class))).thenReturn(1);
      Map<String, String> lib = Map.of("lib", "var Ns = { count: function() { return 1; } };");

      ScriptExecResult result = runLiveWithLibrary("Ns.count()", lib, false, scope);

      assertTrue(result.ok());
      assertFalse(result.requiresConfirmation());
   }

   @Test
   void unreferencedLibraryFunctionDoesNotRequireConfirmation() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), nullable(String.class))).thenReturn(2);
      Map<String, String> lib = Map.of("danger", "function danger() { return runQuery('q'); }");

      ScriptExecResult result = runLiveWithLibrary("1 + 1", lib, false, scope);

      assertTrue(result.ok());
      assertFalse(result.requiresConfirmation());
   }

   @Test
   void memberAccessNamedLikeLibraryFunctionIsNotACall() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), nullable(String.class))).thenReturn(2);
      Map<String, String> lib = Map.of("danger", "function danger() { return runQuery('q'); }");

      ScriptExecResult result = runLiveWithLibrary("obj.danger", lib, false, scope);

      assertFalse(result.requiresConfirmation());
   }

   @Test
   void writeGlobalThroughLibraryRequiresConfirmation() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      Map<String, String> lib = Map.of("fill", "function fill() { setCellValue('T', 0, 0, 1); }");

      ScriptExecResult result = runLiveWithLibrary("fill()", lib, false, scope);

      assertTrue(result.requiresConfirmation());
      assertTrue(result.confirmationReason().contains("\"setCellValue\""));
   }

   @Test
   void dryRunRefusesLibraryDestructiveCallAndDoesNotExecute() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      RuntimeViewsheet rvs = viewsheetWithScript("getOrderCount()", scope);
      ScriptExecuteService svc = serviceWithLibrary(Map.of("getOrderCount",
         "function getOrderCount() { return runQuery('q'); }"));

      ScriptExecResult result = svc.dryRun(rvs, ScriptTarget.parse("vs-init"));

      assertFalse(result.ok());
      assertTrue(result.requiresConfirmation());
      assertTrue(result.confirmationReason().contains("runQuery"));
      verify(scope, never()).execute(anyString(), nullable(String.class));
   }

   @Test
   void runLiveExecutesLibraryDestructiveCallOnceConfirmed() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq("getOrderCount()"), nullable(String.class))).thenReturn(7);
      Map<String, String> lib = Map.of("getOrderCount",
         "function getOrderCount() { return runQuery('q'); }");

      ScriptExecResult result = runLiveWithLibrary("getOrderCount()", lib, true, scope);

      assertTrue(result.ok());
      assertFalse(result.requiresConfirmation());
      verify(scope).execute(eq("getOrderCount()"), nullable(String.class));
   }

   @Test
   void unavailableLibraryFallsBackToDirectScanOnly() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), nullable(String.class))).thenReturn(1);

      ScriptExecResult clean = runLiveWithLibrary("getOrderCount()", null, false, scope);
      assertTrue(clean.ok());

      ScriptExecResult direct = runLiveWithLibrary("runQuery('q')", null, false, scope);
      assertTrue(direct.requiresConfirmation());
   }

   @Test
   void templateLiteralInterpolationIsScanned() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      Map<String, String> lib = Map.of("getOrderCount",
         "function getOrderCount() { return runQuery('q'); }");

      ScriptExecResult result =
         runLiveWithLibrary("`Total ${getOrderCount()}`", lib, false, scope);

      assertTrue(result.requiresConfirmation());
      assertTrue(result.confirmationReason().contains("\"getOrderCount\""));
      verify(scope, never()).execute(anyString(), nullable(String.class));
   }

   @Test
   void templateLiteralTextOutsideInterpolationIsNotScanned() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), nullable(String.class))).thenReturn(1);
      Map<String, String> lib = Map.of("getOrderCount",
         "function getOrderCount() { return runQuery('q'); }");

      assertTrue(runLiveWithLibrary("`getOrderCount ${1 + 1}`", lib, false, scope).ok());
   }

   @Test
   void globalQualifiedLibraryCallIsStillScanned() throws Exception {
      Map<String, String> lib = Map.of("getOrderCount",
         "function getOrderCount() { return runQuery('q'); }");

      for(String qualifier : List.of("this", "globalThis", "window", "self", "this .", "this?",
         "globalThis ?")) {
         ViewsheetScope scope = mock(ViewsheetScope.class);
         String call = qualifier.endsWith(".") ? qualifier + " getOrderCount()" :
            qualifier + ".getOrderCount()";
         ScriptExecResult result = runLiveWithLibrary("'T: ' + " + call, lib, false, scope);

         assertTrue(result.requiresConfirmation(), call);
      }
   }

   @Test
   void spreadOfLibraryCallIsStillScanned() throws Exception {
      Map<String, String> lib = Map.of("getOrderCount",
         "function getOrderCount() { return runQuery('q'); }");

      for(String script : List.of("[...getOrderCount()]", "Math.max(...getOrderCount())")) {
         ViewsheetScope scope = mock(ViewsheetScope.class);
         assertTrue(runLiveWithLibrary(script, lib, false, scope).requiresConfirmation(), script);
      }
   }

   @Test
   void plainMemberOfNonGlobalIsStillSkipped() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), nullable(String.class))).thenReturn(1);
      Map<String, String> lib = Map.of("getOrderCount",
         "function getOrderCount() { return runQuery('q'); }");

      for(String script : List.of("obj.getOrderCount", "obj?.getOrderCount", "f().getOrderCount",
         "a[0].getOrderCount"))
      {
         assertTrue(runLiveWithLibrary(script, lib, false, scope).ok(), script);
      }
   }

   @Test
   void unicodeLibraryFunctionNameIsScanned() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      Map<String, String> lib = Map.of("获取订单数",
         "function 获取订单数() { return runQuery('q'); }");

      ScriptExecResult result = runLiveWithLibrary("获取订单数()", lib, false, scope);

      assertTrue(result.requiresConfirmation());
      assertTrue(result.confirmationReason().contains("获取订单数"));
   }

   @Test
   void unicodeDeclaredNameDiffersFromEntryName() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      Map<String, String> lib = Map.of("entry",
         "var 订单 = function() { return runQuery('q'); };");

      assertTrue(runLiveWithLibrary("订单()", lib, false, scope).requiresConfirmation());
   }

   @Test
   void absentLibraryProviderBeanFallsBackToDirectScan() {
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      try(MockedStatic<LibManagerProvider> provider = mockStatic(LibManagerProvider.class)) {
         provider.when(LibManagerProvider::getInstance)
            .thenThrow(new NoSuchBeanDefinitionException(LibManagerProvider.class));

         assertNull(svc.libraryScripts(new XPrincipal(new IdentityID("u", "org"))));
      }
   }

   @Test
   void libraryFailureOtherThanAbsentBeanPropagates() {
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());
      LibManagerProvider mgrProvider = mock(LibManagerProvider.class);
      when(mgrProvider.getManager(any(java.security.Principal.class)))
         .thenThrow(new IllegalStateException("storage down"));

      try(MockedStatic<LibManagerProvider> provider = mockStatic(LibManagerProvider.class)) {
         provider.when(LibManagerProvider::getInstance).thenReturn(mgrProvider);

         assertThrows(IllegalStateException.class,
                      () -> svc.libraryScripts(new XPrincipal(new IdentityID("u", "org"))));
      }
   }

   @Test
   void realLibraryPathUsesRuntimeUsersManagerAndSkipsNullSources() throws Exception {
      XPrincipal user = new XPrincipal(new IdentityID("u", "org"));
      LibManager mgr = mock(LibManager.class);
      when(mgr.getScripts()).thenReturn(
         java.util.Collections.enumeration(List.of("getOrderCount", "empty")));
      when(mgr.getScript("getOrderCount"))
         .thenReturn("function getOrderCount() { return runQuery('q'); }");
      when(mgr.getScript("empty")).thenReturn(null);
      LibManagerProvider mgrProvider = mock(LibManagerProvider.class);
      when(mgrProvider.getManager(user)).thenReturn(mgr);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());
      ViewsheetScope scope = mock(ViewsheetScope.class);
      RuntimeViewsheet rvs = viewsheetWithScript("getOrderCount()", scope);
      when(rvs.getUser()).thenReturn(user);

      try(MockedStatic<LibManagerProvider> provider = mockStatic(LibManagerProvider.class)) {
         provider.when(LibManagerProvider::getInstance).thenReturn(mgrProvider);

         ScriptExecResult result = svc.runLive(rvs, ScriptTarget.parse("vs-init"), false);

         assertTrue(result.requiresConfirmation());
      }

      verify(mgrProvider).getManager(user);
      verify(mgrProvider, never()).getManager();
   }

   /** Bug #78662: a script run that changes the live runtime must read as changed since the last save. */
   @Test
   void runLiveAddsAnUndoCheckpointAndBumpsTheWriteRevisionWhenTheScriptChangesTheViewsheet()
      throws Exception
   {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      RuntimeViewsheet rvs = viewsheetWithScript("vs.desc = 'changed'", scope);
      when(scope.execute(eq("vs.desc = 'changed'"), nullable(String.class))).thenAnswer(inv -> {
         rvs.getViewsheet().getViewsheetInfo().setOnLoad("changed by the script");
         return null;
      });
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      svc.runLive(rvs, ScriptTarget.parse("vs-init"), true);

      verify(rvs).addCheckpoint(any());
      verify(rvs).bumpWriteRevision();
   }

   /** A script that throws after changing the viewsheet still reads as changed. */
   @Test
   void runLiveAddsAnUndoCheckpointWhenTheScriptThrowsAfterChangingTheViewsheet() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      RuntimeViewsheet rvs = viewsheetWithScript("vs.desc = 'changed'", scope);
      when(scope.execute(eq("vs.desc = 'changed'"), nullable(String.class))).thenAnswer(inv -> {
         rvs.getViewsheet().getViewsheetInfo().setOnLoad("changed by the script");
         throw new IllegalStateException("fails after the write");
      });
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      try {
         svc.runLive(rvs, ScriptTarget.parse("vs-init"), true);
      }
      catch(Exception ignore) {
      }

      verify(rvs).addCheckpoint(any());
      verify(rvs).bumpWriteRevision();
   }

   /** Bug #79051: a read-only script changes nothing, so it adds no undo step and keeps redo. */
   @Test
   void runLiveAddsNoCheckpointWhenTheScriptChangesNothing() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq("1 + 1"), nullable(String.class))).thenReturn(2.0);
      RuntimeViewsheet rvs = viewsheetWithScript("1 + 1", scope);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      svc.runLive(rvs, ScriptTarget.parse("vs-init"), true);
      svc.dryRun(rvs, ScriptTarget.parse("vs-init"));

      verify(rvs, never()).addCheckpoint(any());
      verify(rvs, never()).bumpWriteRevision();
   }

   /** If the viewsheet cannot be serialized before the run, the script still runs and the run counts as a change. */
   @Test
   void runLiveStillRunsAndCheckpointsWhenSerializingBeforeTheRunFails() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq("1 + 1"), nullable(String.class))).thenReturn(2.0);
      RuntimeViewsheet rvs = viewsheetWithScript("1 + 1", scope);
      Viewsheet vs = spy(rvs.getViewsheet());
      when(rvs.getViewsheet()).thenReturn(vs);
      doThrow(new IllegalStateException("cannot serialize")).doCallRealMethod()
         .when(vs).prepareCheckpoint();
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, ScriptTarget.parse("vs-init"), true);

      assertEquals(2.0, result.value());
      verify(rvs).addCheckpoint(any());
      verify(rvs).bumpWriteRevision();
   }

   /** If serializing fails after a script that threw, the script's own failure is still reported and the run still checkpoints. */
   @Test
   void runLiveReportsTheScriptFailureAndCheckpointsWhenSerializingAfterTheRunFails() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      IllegalStateException failure = new IllegalStateException("script failed");
      when(scope.execute(eq("1 + 1"), nullable(String.class))).thenThrow(failure);
      RuntimeViewsheet rvs = viewsheetWithScript("1 + 1", scope);
      Viewsheet vs = spy(rvs.getViewsheet());
      when(rvs.getViewsheet()).thenReturn(vs);
      doCallRealMethod().doThrow(new IllegalStateException("cannot serialize")).doCallRealMethod()
         .when(vs).prepareCheckpoint();
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      ScriptExecResult result = svc.runLive(rvs, ScriptTarget.parse("vs-init"), true);

      assertFalse(result.ok());
      verify(rvs).addCheckpoint(any());
      verify(rvs).bumpWriteRevision();
   }

   /** A run refused for needing confirmation executes nothing, so it adds no checkpoint. */
   @Test
   void aRunRefusedForNeedingConfirmationAddsNoCheckpoint() throws Exception {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      RuntimeViewsheet rvs = viewsheetWithScript("setCellValue('T', 0, 0, 1)", scope);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      svc.runLive(rvs, ScriptTarget.parse("vs-init"), false);

      verify(rvs, never()).addCheckpoint(any());
      verify(rvs, never()).bumpWriteRevision();
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

   private static XPrincipal callerPrincipal(String name, String org) {
      XPrincipal user = mock(XPrincipal.class);
      when(user.getOrgId()).thenReturn(org);
      when(user.getName()).thenReturn(name + "~;~" + org);
      when(user.getIdentityID()).thenReturn(new IdentityID(name, org));
      return user;
   }

   /**
    * Bug #76661: runQuery('ws:<token>:<path>') is scope-sensitive -- a literal "global" token
    * addresses GLOBAL_SCOPE, any other token (here, a username) addresses only that user's
    * USER_SCOPE. Getting this wrong used to resolve to an AssetEntry that legitimately has
    * nothing at it, so the shared runtime returned an empty result with no error and no
    * UI-visible warning. This confirms the wiz-agent-only diagnostic added to execute() catches
    * exactly that case -- requested (USER_SCOPE) entry absent, GLOBAL_SCOPE sibling present --
    * and folds an actionable explanation into the result summary without touching value/ok.
    */
   @Test
   void runLiveAppendsWsScopeMismatchNoteWhenTheAddressedScopeIsEmptyButTheOtherScopeHasTheAsset()
      throws Exception
   {
      String script = "runQuery('ws:admin:ws')";
      ViewsheetScope scope = scopeObserving(script, 0.0, "ws:admin:ws", XUtil.RunQueryOutcome.NOT_FOUND);

      RuntimeViewsheet rvs = viewsheetWithScript(script, scope);
      XPrincipal user = callerPrincipal("admin", "host-org");
      when(rvs.getUser()).thenReturn(user);

      AssetEntry requested = AssetEntry.createAssetEntry("ws:admin:ws", "host-org");
      AssetEntry sibling = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                          "ws", null, "host-org");
      AssetRepository repo = mock(AssetRepository.class);
      when(repo.containsEntry(requested)).thenReturn(false);
      when(repo.containsEntry(sibling)).thenReturn(true);

      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repo);

         ScriptExecResult result = svc.runLive(rvs, ScriptTarget.parse("vs-init"), true);

         assertTrue(result.ok());
         assertEquals(0.0, result.value());
         assertTrue(result.summary().contains("ws:admin:ws"), result.summary());
         assertTrue(result.summary().contains("user scope"), result.summary());
         assertTrue(result.summary().contains("global scope"), result.summary());
         assertTrue(result.summary().contains("ws:global:ws"), result.summary());
      }
   }

   /**
    * The reverse direction of the test above: a literal "global" token requested, but the asset
    * actually exists only at the CURRENT calling user's own USER_SCOPE. Exercises the other half
    * of the sibling-lookup branch, and specifically that the fix wording says "use ws:<user>:X"
    * (the asset already exists there), not "save the worksheet ..." (which would wrongly imply
    * it still needs to be created/moved) -- a wording bug a prior version of this fix had here,
    * caught by review because this direction had no test asserting the exact message.
    */
   @Test
   void runLiveAppendsWsScopeMismatchNoteForTheReverseDirectionWithCorrectFixWording()
      throws Exception
   {
      String script = "runQuery('ws:global:ws')";
      ViewsheetScope scope = scopeObserving(script, 0.0, "ws:global:ws", XUtil.RunQueryOutcome.NOT_FOUND);

      RuntimeViewsheet rvs = viewsheetWithScript(script, scope);
      XPrincipal user = callerPrincipal("admin", "host-org");
      when(rvs.getUser()).thenReturn(user);

      AssetEntry requested = AssetEntry.createAssetEntry("ws:global:ws", "host-org");
      AssetEntry sibling = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                                          "ws", new IdentityID("admin", "host-org"), "host-org");
      AssetRepository repo = mock(AssetRepository.class);
      when(repo.containsEntry(requested)).thenReturn(false);
      when(repo.containsEntry(sibling)).thenReturn(true);

      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repo);

         ScriptExecResult result = svc.runLive(rvs, ScriptTarget.parse("vs-init"), true);

         assertTrue(result.ok());
         assertEquals(0.0, result.value());
         assertTrue(result.summary().contains("ws:global:ws"), result.summary());
         assertTrue(result.summary().contains("global scope"), result.summary());
         assertTrue(result.summary().contains("user scope"), result.summary());
         assertTrue(result.summary().contains("Use \"ws:admin:ws\" to reach it."), result.summary());
         assertFalse(result.summary().contains("Save the worksheet"), result.summary());
      }
   }

   /** Control for the test above: the token matches where the asset actually is -- no note. */
   @Test
   void runLiveAddsNoWsScopeMismatchNoteWhenTheAddressedScopeActuallyHasTheAsset() throws Exception {
      String script = "runQuery('ws:global:ws')";
      ViewsheetScope scope = scopeObserving(script, 36.0, "ws:global:ws", XUtil.RunQueryOutcome.FOUND);

      RuntimeViewsheet rvs = viewsheetWithScript(script, scope);
      XPrincipal user = callerPrincipal("admin", "host-org");
      when(rvs.getUser()).thenReturn(user);

      AssetEntry requested = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                            "ws", null, "host-org");
      AssetRepository repo = mock(AssetRepository.class);
      when(repo.containsEntry(requested)).thenReturn(true);

      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repo);

         ScriptExecResult result = svc.runLive(rvs, ScriptTarget.parse("vs-init"), true);

         assertTrue(result.ok());
         assertEquals(36.0, result.value());
         assertFalse(result.summary().contains("scope, but nothing exists there"), result.summary());
      }
   }

   /**
    * Bug #78285: a worksheet missing at the addressed scope AND the sibling used to produce no
    * note at all. It now gets one generic note (the call was observed executing and resolved
    * nothing). Rewrites the former "no note when absent at both scopes" expectation.
    */
   @Test
   void runLiveAddsGenericNoteWhenTheAssetDoesNotExistAtEitherScope() throws Exception {
      ScriptExecResult result = runObserved("runQuery('ws:admin:doesNotExist')", repoWithNothing(),
                                            "ws:admin:doesNotExist", XUtil.RunQueryOutcome.NOT_FOUND);

      assertTrue(result.ok());
      assertTrue(result.summary().contains("runQuery('ws:admin:doesNotExist') returned no data"),
                 result.summary());
      assertFalse(result.summary().contains("nothing exists there"), result.summary());
   }

   @Test
   void runLiveWsNoteWordingIsIdenticalForDeniedNotFoundAndError() throws Exception {
      String a = runObserved("runQuery('ws:bob:p')", repoWithNothing(), "ws:bob:p",
                             XUtil.RunQueryOutcome.NOT_FOUND).summary();
      String b = runObserved("runQuery('ws:bob:p')", repoWithNothing(), "ws:bob:p",
                             XUtil.RunQueryOutcome.DENIED).summary();
      String c = runObserved("runQuery('ws:bob:p')", repoWithNothing(), "ws:bob:p",
                             XUtil.RunQueryOutcome.ERROR).summary();

      assertEquals(a, b);
      assertEquals(a, c);
      assertFalse(a.toLowerCase().contains("permission"), a);
      assertFalse(a.contains("exists"), a);
   }

   @Test
   void runLiveGenericNoteDoesNotClaimTheWorksheetDoesNotExist() throws Exception {
      for(XUtil.RunQueryOutcome o : XUtil.RunQueryOutcome.values()) {
         if(o == XUtil.RunQueryOutcome.FOUND) {
            continue;
         }

         String summary = runObserved("runQuery('ws:global:ws')", repoWithNothing(),
                                      "ws:global:ws", o).summary();

         assertFalse(summary.contains("was found"), summary);
         assertFalse(summary.contains("does not exist"), summary);
         assertTrue(summary.contains("could not be found, accessed or run"), summary);
         assertTrue(summary.contains("check the worksheet itself"), summary);
      }
   }

   /** Another user's scope never probes the repository, so the note can't reveal existence. */
   @Test
   void runLiveOtherUsersScopeNeverUsesTheSiblingMismatchText() throws Exception {
      AssetRepository repo = mock(AssetRepository.class);
      when(repo.containsEntry(any(AssetEntry.class))).thenReturn(true);

      String summary = runObserved("runQuery('ws:bob:p')", repo, "ws:bob:p",
                                   XUtil.RunQueryOutcome.DENIED).summary();

      assertTrue(summary.contains("returned no data"), summary);
      assertFalse(summary.contains("ws:global:p"), summary);
      verify(repo, never()).containsEntry(any(AssetEntry.class));
   }

   @Test
   void runLiveAddsNoNoteForCommentedOrStringMentionedLiterals() throws Exception {
      // nothing executed -> the observer is never invoked, whatever the text says
      for(String script : new String[] {
         "// runQuery('ws:admin:ws')\nvar x = 1;",
         "/* runQuery('ws:admin:ws') */ var x = 1;",
         "var s = \"see runQuery('ws:admin:ws')\";",
         "if(false) { runQuery('ws:global:__none__'); }"})
      {
         ScriptExecResult result = runObserved(script, repoWithGlobalSibling(), null, null);

         assertTrue(result.ok());
         assertFalse(String.valueOf(result.summary()).contains("runQuery("), result.summary());
      }
   }

   @Test
   void runLiveReportsOnlyTheExecutedCallNotADeadOne() throws Exception {
      ScriptExecResult result = runObserved(
         "runQuery('ws:global:live'); // runQuery('ws:global:dead')", repoWithNothing(),
         "ws:global:live", XUtil.RunQueryOutcome.NOT_FOUND);

      assertTrue(result.summary().contains("ws:global:live"), result.summary());
      assertFalse(result.summary().contains("dead"), result.summary());
   }

   @Test
   void runLiveDedupesAndBoundsTheNotes() throws Exception {
      String script = "many";
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), nullable(String.class))).thenAnswer(inv -> {
         for(int i = 0; i < 10; i++) {
            notifyObserver("ws:global:w" + (i % 5), XUtil.RunQueryOutcome.NOT_FOUND);
            notifyObserver("ws:global:w" + (i % 5), XUtil.RunQueryOutcome.NOT_FOUND);
         }

         return null;
      });
      ScriptExecResult result = runLiveWith(script, scope, repoWithNothing());
      String summary = result.summary();

      assertEquals(3, summary.split("returned no data", -1).length - 1, summary);
      assertEquals(1, summary.split("ws:global:w0", -1).length - 1, summary);
   }

   @Test
   void runLiveClearsTheObserverAfterExecuteIncludingOnException() throws Exception {
      assertNull(currentObserver());

      runObserved("runQuery('ws:global:ws')", repoWithNothing(), "ws:global:ws",
                  XUtil.RunQueryOutcome.FOUND);
      assertNull(currentObserver());

      String script = "boom";
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), nullable(String.class))).thenAnswer(inv -> {
         assertNotNull(currentObserver());
         throw new RuntimeException("boom");
      });
      ScriptExecResult result = runLiveWith(script, scope, repoWithNothing());

      assertFalse(result.ok());
      assertNull(currentObserver());
   }

   /**
    * Bug #78583: a scope-less runQuery('ws:<path>') parses to REPORT_SCOPE, which no StyleBI
    * runtime can resolve, and used to produce no note at all. The note names the missing scope
    * token and, since the worksheet exists at global scope, the address that reaches it. The
    * REPORT_SCOPE entry itself is never probed.
    */
   @Test
   void runLiveNotesAScopeLessWsAddressAndNamesTheGlobalWorksheet() throws Exception {
      AssetRepository repo = repoWithGlobalSibling();
      String summary = runObserved("runQuery('ws:Examples/AllSales')", repo,
                                   "ws:Examples/AllSales", XUtil.RunQueryOutcome.ERROR).summary();

      assertNotNull(summary);
      assertTrue(summary.contains("runQuery('ws:Examples/AllSales')"), summary);
      assertTrue(summary.contains("no scope token"), summary);
      assertTrue(summary.contains("Use \"ws:global:Examples/AllSales\""), summary);
      verify(repo, never()).containsEntry(
         argThat(e -> e != null && e.getScope() == AssetRepository.REPORT_SCOPE));
   }

   /** Bug #78583: the caller's own user scope is the other sibling probed. */
   @Test
   void runLiveNotesAScopeLessWsAddressAndNamesTheCallersUserWorksheet() throws Exception {
      AssetRepository repo = mock(AssetRepository.class);
      when(repo.containsEntry(any(AssetEntry.class))).thenAnswer(inv -> {
         AssetEntry e = inv.getArgument(0);
         return e.getScope() == AssetRepository.USER_SCOPE &&
            new IdentityID("admin", "host-org").equals(e.getUser());
      });

      String summary = runObserved("runQuery('ws:mine')", repo, "ws:mine",
                                   XUtil.RunQueryOutcome.ERROR).summary();

      assertTrue(summary.contains("no scope token"), summary);
      assertTrue(summary.contains("Use \"ws:admin:mine\""), summary);
      assertFalse(summary.contains("ws:global:mine\" to reach"), summary);
   }

   /** Bug #78583: no worksheet at either sibling -> one generic note naming both forms. */
   @Test
   void runLiveNotesAScopeLessWsAddressWithNoMatchingWorksheet() throws Exception {
      String summary = runObserved("runQuery('ws:missing')", repoWithNothing(), "ws:missing",
                                   XUtil.RunQueryOutcome.NOT_FOUND).summary();

      assertTrue(summary.contains("runQuery('ws:missing')"), summary);
      assertTrue(summary.contains("no scope token"), summary);
      assertTrue(summary.contains("ws:global:missing"), summary);
      assertTrue(summary.contains("ws:admin:missing"), summary);
      assertFalse(summary.contains("exists at"), summary);
   }

   /** Bug #78583: a bare 'ws:' names no worksheet; no sibling (root folder) entry is probed. */
   @Test
   void runLiveNotesABareWsAddressWithoutProbing() throws Exception {
      AssetRepository repo = repoWithGlobalSibling();
      String summary = runObserved("runQuery('ws:')", repo, "ws:",
                                   XUtil.RunQueryOutcome.ERROR).summary();

      assertTrue(summary.contains("runQuery('ws:')"), summary);
      assertTrue(summary.contains("names no worksheet"), summary);
      verify(repo, never()).containsEntry(any(AssetEntry.class));
   }

   // ---- Bug #78285 helpers: drive XUtil's observer from inside the mocked scope.execute ----

   private ViewsheetScope scopeObserving(String script, Object value, String literal,
                                         XUtil.RunQueryOutcome outcome) throws Exception
   {
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(eq(script), nullable(String.class))).thenAnswer(inv -> {
         if(literal != null) {
            notifyObserver(literal, outcome);
         }

         return value;
      });
      return scope;
   }

   private ScriptExecResult runObserved(String script, AssetRepository repo, String literal,
                                        XUtil.RunQueryOutcome outcome) throws Exception
   {
      return runLiveWith(script, scopeObserving(script, null, literal, outcome), repo);
   }

   private ScriptExecResult runLiveWith(String script, ViewsheetScope scope, AssetRepository repo)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetWithScript(script, scope);
      XPrincipal caller = callerPrincipal("admin", "host-org");
      when(rvs.getUser()).thenReturn(caller);
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repo);
         return svc.runLive(rvs, ScriptTarget.parse("vs-init"), true);
      }
   }

   private static AssetRepository repoWithNothing() throws Exception {
      AssetRepository repo = mock(AssetRepository.class);
      when(repo.containsEntry(any(AssetEntry.class))).thenReturn(false);
      return repo;
   }

   private static AssetRepository repoWithGlobalSibling() throws Exception {
      AssetRepository repo = mock(AssetRepository.class);
      when(repo.containsEntry(any(AssetEntry.class))).thenAnswer(
         inv -> ((AssetEntry) inv.getArgument(0)).getScope() == AssetRepository.GLOBAL_SCOPE);
      return repo;
   }

   private static void notifyObserver(String literal, XUtil.RunQueryOutcome outcome)
      throws Exception
   {
      java.lang.reflect.Method m = XUtil.class.getDeclaredMethod(
         "notifyRunQueryObserver", AssetEntry.class, XUtil.RunQueryOutcome.class);
      m.setAccessible(true);
      m.invoke(null, AssetEntry.createAssetEntry(literal, "host-org"), outcome);
   }

   private static Object currentObserver() throws Exception {
      java.lang.reflect.Field f = XUtil.class.getDeclaredField("RUN_QUERY_OBSERVER");
      f.setAccessible(true);
      return ((ThreadLocal<?>) f.get(null)).get();
   }
}

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
package inetsoft.web.wiz.controller;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.DependencyTransformer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the two dependency-tracking correctness issues code review found in
 * {@code ScriptLibraryController}: {@code delete()}'s safety check was built against the wrong
 * {@code AssetEntry} scope (so it could never find a real dependent), and {@code update()} never
 * refreshed the dependency graph at all.
 *
 * <p>Also covers the Redmine #76765 SSL-001 mitigation: a Script Library function is installed as
 * a global JS binding once when a viewsheet session's script runtime is built, and nothing
 * currently rebuilds that for an already-open session when LibManager changes -- see
 * docs/teams/2026-09-18-bugs-76765-script-library/bug-ssl-001/03-fix.md. Until that staleness is
 * fixed at the runtime level, create/update/delete surface a {@code resyncWarning} so a caller
 * isn't left to discover the staleness by getting a stale/ReferenceError result elsewhere.
 */
@Tag("core")
class ScriptLibraryControllerTest {
   @Test
   void create_returnsAResyncWarning() throws Exception {
      Fixture fixture = new Fixture();

      ScriptLibraryController.ScriptLibraryFunctionDetail result = fixture.controller.create(
         new ScriptLibraryController.CreateScriptLibraryFunctionRequest(
            "helper", "return 1;", null),
         fixture.principal);

      assertNotNull(result.resyncWarning());
   }

   @Test
   void update_returnsAResyncWarning() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getScript("helper")).thenReturn("return 1;");

      DependencyHandler dependencyHandler = mock(DependencyHandler.class);

      try(MockedStatic<DependencyHandler> handler = mockStatic(DependencyHandler.class)) {
         handler.when(DependencyHandler::getInstance).thenReturn(dependencyHandler);

         ScriptLibraryController.ScriptLibraryFunctionDetail result = fixture.controller.update(
            "helper",
            new ScriptLibraryController.UpdateScriptLibraryFunctionRequest("return 2;", null),
            fixture.principal);

         assertNotNull(result.resyncWarning());
      }
   }

   @Test
   void delete_returnsAResyncWarning() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getScript("helper")).thenReturn("return 1;");

      try(MockedStatic<DependencyTransformer> transformer = mockStatic(DependencyTransformer.class)) {
         transformer.when(() -> DependencyTransformer.getDependencies(anyString()))
            .thenReturn(List.of());

         ScriptLibraryController.DeleteScriptLibraryFunctionResult result =
            fixture.controller.delete("helper", false, fixture.principal);

         assertNotNull(result.resyncWarning());
      }
   }

   @Test
   void read_doesNotReturnAResyncWarning() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getScript("helper")).thenReturn("return 1;");

      ScriptLibraryController.ScriptLibraryFunctionDetail result =
         fixture.controller.read("helper", fixture.principal);

      assertNull(result.resyncWarning());
   }

   @Test
   void delete_refusesWhenAComponentScopedDependentExists() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getScript("helper")).thenReturn("return 1;");
      AssetEntry dependent = new AssetEntry(
         AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.VIEWSHEET, "report", null);

      try(MockedStatic<DependencyTransformer> transformer = mockStatic(DependencyTransformer.class)) {
         transformer.when(() -> DependencyTransformer.getDependencies(componentScopedId("helper")))
            .thenReturn(List.<AssetObject>of(dependent));

         assertThrows(IllegalArgumentException.class,
                      () -> fixture.controller.delete("helper", false, fixture.principal));
      }

      verify(fixture.lib, never()).removeScript(anyString());
   }

   @Test
   void delete_looksUpDependentsUnderComponentScopeNotGlobalScope() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getScript("helper")).thenReturn("return 1;");

      // GLOBAL_SCOPE is the scope RemoveAssetController.checkScriptRemoveable's own (buggy) lookup
      // used, and the bug this test guards against: a lookup keyed there never matches a real
      // dependency record, so force=false would silently allow an unsafe delete.
      try(MockedStatic<DependencyTransformer> transformer = mockStatic(DependencyTransformer.class)) {
         transformer.when(() -> DependencyTransformer.getDependencies(anyString()))
            .thenReturn(List.of());

         assertDoesNotThrow(() -> fixture.controller.delete("helper", false, fixture.principal));

         transformer.verify(() -> DependencyTransformer.getDependencies(componentScopedId("helper")));
         transformer.verify(() -> DependencyTransformer.getDependencies(globalScopedId("helper")),
                            never());
      }

      verify(fixture.lib).removeScript("helper");
   }

   @Test
   void delete_allowsRemovalWhenNoDependentsExist() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getScript("helper")).thenReturn("return 1;");

      try(MockedStatic<DependencyTransformer> transformer = mockStatic(DependencyTransformer.class)) {
         transformer.when(() -> DependencyTransformer.getDependencies(anyString()))
            .thenReturn(List.of());

         assertDoesNotThrow(() -> fixture.controller.delete("helper", false, fixture.principal));
      }

      verify(fixture.lib).removeScript("helper");
      verify(fixture.lib).save();
   }

   @Test
   void update_refreshesTheOutgoingDependencyGraph() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getScript("helper")).thenReturn("return other();");

      DependencyHandler dependencyHandler = mock(DependencyHandler.class);

      try(MockedStatic<DependencyHandler> handler = mockStatic(DependencyHandler.class)) {
         handler.when(DependencyHandler::getInstance).thenReturn(dependencyHandler);

         fixture.controller.update(
            "helper",
            new ScriptLibraryController.UpdateScriptLibraryFunctionRequest("return 1;", null),
            fixture.principal);

         AssetEntry expectedEntry = new AssetEntry(
            AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.SCRIPT, "helper", null);
         verify(dependencyHandler).updateScriptDependencies(
            eq("return other();"), eq("return 1;"), eq(expectedEntry));
      }

      verify(fixture.lib).setScript("helper", "return 1;");
   }

   @Test
   void create_rejectsBlankText() throws Exception {
      Fixture fixture = new Fixture();

      assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new ScriptLibraryController.CreateScriptLibraryFunctionRequest("newFn", "   ", null),
         fixture.principal));

      verify(fixture.lib, never()).setScript(anyString(), anyString());
   }

   @Test
   void create_rejectsNullText() throws Exception {
      Fixture fixture = new Fixture();

      assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new ScriptLibraryController.CreateScriptLibraryFunctionRequest("newFn", null, null),
         fixture.principal));

      verify(fixture.lib, never()).setScript(anyString(), anyString());
   }

   @Test
   void update_rejectsBlankText() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getScript("helper")).thenReturn("return 1;");

      assertThrows(IllegalArgumentException.class, () -> fixture.controller.update(
         "helper",
         new ScriptLibraryController.UpdateScriptLibraryFunctionRequest("", null),
         fixture.principal));

      verify(fixture.lib, never()).setScript(anyString(), anyString());
   }

   @Test
   void rename_rejectsWhenNewNameAlreadyExists() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getScript("oldFn")).thenReturn("function oldFn(x) { return x; }");
      when(fixture.lib.getScript("existingFn")).thenReturn("function existingFn(x) { return x; }");

      assertThrows(IllegalArgumentException.class, () -> fixture.controller.rename(
         "oldFn",
         new ScriptLibraryController.RenameScriptLibraryFunctionRequest("existingFn"),
         fixture.principal));

      verify(fixture.lib, never()).renameScript(anyString(), anyString());
      // the pre-existing function under newName must not be clobbered
      assertEquals("function existingFn(x) { return x; }", fixture.lib.getScript("existingFn"));
   }

   @Test
   void rename_rejectsWhenOldNameDoesNotExist() throws Exception {
      Fixture fixture = new Fixture();

      assertThrows(IllegalArgumentException.class, () -> fixture.controller.rename(
         "missingFn",
         new ScriptLibraryController.RenameScriptLibraryFunctionRequest("newFn"),
         fixture.principal));

      verify(fixture.lib, never()).renameScript(anyString(), anyString());
   }

   @Test
   void rename_rejectsBlankNewName() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getScript("oldFn")).thenReturn("function oldFn(x) { return x; }");

      assertThrows(IllegalArgumentException.class, () -> fixture.controller.rename(
         "oldFn",
         new ScriptLibraryController.RenameScriptLibraryFunctionRequest("   "),
         fixture.principal));

      verify(fixture.lib, never()).renameScript(anyString(), anyString());
   }

   /**
    * The regression this fix is actually about: renameScript() alone only moves the registry key
    * and patches the derived signature (ScriptLogicalLibrary.renameEntry) -- it never touches the
    * function's own declaration text, which is what GraalJavaScriptEngine.installLibraryFunctions
    * actually binds as the JS global. Without the follow-up setScript() rewriting the declaration
    * itself, the renamed entry would still read "function oldFn(...)" and every caller (already
    * rewritten by RenameTransformHandler to call newFn(...)) would break with a ReferenceError.
    *
    * <p>Note: Util.renameScriptDepended is a dot/bracket-bounded substring replace, not an
    * identifier-exact-match rename -- this test's fixture avoids any substring-collision (e.g. a
    * sibling local variable containing "oldFn" as a substring) since that's a known pre-existing
    * limitation of the shared utility, not something this fix is expected to solve.
    */
   @Test
   void rename_rewritesTheFunctionsOwnDeclarationText() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getScript("oldFn")).thenReturn("function oldFn(x) { return x * 2; }");

      fixture.controller.rename(
         "oldFn",
         new ScriptLibraryController.RenameScriptLibraryFunctionRequest("newFn"),
         fixture.principal);

      verify(fixture.lib).renameScript("oldFn", "newFn");
      verify(fixture.lib).setScript("newFn", "function newFn(x) { return x * 2; }");
      verify(fixture.lib).save();
   }

   private static String componentScopedId(String name) {
      return new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.SCRIPT, name, null)
         .toIdentifier();
   }

   private static String globalScopedId(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCRIPT, name, null)
         .toIdentifier();
   }

   /** The controller with every collaborator mocked, and every permission granted by default. */
   private static final class Fixture {
      Fixture() throws Exception {
         when(securityEngine.checkPermission(any(), eq(ResourceType.SCRIPT), anyString(),
                                             any(ResourceAction.class))).thenReturn(true);
         when(libManagerProvider.getManager(any(Principal.class))).thenReturn(lib);
         controller = new ScriptLibraryController(libManagerProvider, securityEngine);
      }

      final LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
      final SecurityEngine securityEngine = mock(SecurityEngine.class);
      final LibManager lib = mock(LibManager.class);
      final Principal principal = mock(Principal.class);
      final ScriptLibraryController controller;
   }
}

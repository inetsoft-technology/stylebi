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
package inetsoft.web.admin.ai.scriptlibrary;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.security.auth.MissingResourceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link ScriptLibraryChangePlanService#resolve} for all four verbs: request-shape
 * validation, the risk classification each verb gets (create/rename/update always
 * {@code RISK_LOW}, delete always {@code RISK_HIGH}), the dependency-check refuse/force-allow
 * gate on delete, and -- the round-1 review finding this file exists to regression-test -- that
 * {@code create} now enforces the same {@code ADMIN} permission check {@code rename}/
 * {@code update}/{@code delete} already enforced (a caller denied {@code ADMIN} on the Script
 * Library resource must be refused on {@code create} exactly as it already is on the other three
 * verbs). Mocking follows {@code ScriptLibraryControllerTest}'s own precedent for this package's
 * collaborators: {@link LibManager}/{@link LibManagerProvider}/{@link SecurityEngine} are plain
 * Mockito mocks, and {@link DependencyTransformer} is mocked statically per-test.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScriptLibraryChangePlanServiceTest {
   @Mock private LibManagerProvider libManagerProvider;
   @Mock private LibManager lib;
   @Mock private SecurityEngine securityEngine;
   @Mock private Principal user;

   private ScriptLibraryService scriptLibraryService;
   private ScriptLibraryChangePlanService service;
   private MockedStatic<Tool> tool;

   @BeforeEach
   void setUp() throws Exception {
      lenient().when(libManagerProvider.getManager(any(Principal.class))).thenReturn(lib);
      scriptLibraryService = new ScriptLibraryService(libManagerProvider, securityEngine);
      service = new ScriptLibraryChangePlanService(scriptLibraryService);

      // Default every caller to holding ADMIN on every script name -- individual permission tests
      // override this to specific denials.
      lenient().when(securityEngine.checkPermission(
         eq(user), eq(ResourceType.SCRIPT), anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);

      // ResolvedPlan.taskToken() is issued via TaskAuditToken.issue -> Tool.encryptPassword, which
      // needs a live Spring-managed keystore outside this unit test -- stub it, same as every other
      // area's own ChangePlanServiceTest (e.g. ShapeChangePlanServiceTest/StoredAssetChangePlanServiceTest).
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach
   void tearDown() {
      tool.close();
   }

   private static ScriptLibraryChangePlanRequest request(ScriptLibraryChangeRequest... changes) {
      ScriptLibraryChangePlanRequest req = new ScriptLibraryChangePlanRequest();
      req.setTask("test task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static ScriptLibraryChangeRequest change(String verb, String name) {
      ScriptLibraryChangeRequest c = new ScriptLibraryChangeRequest();
      c.setVerb(verb);
      c.setName(name);
      return c;
   }

   // -------------------------------------------------------------------------
   // request-shape validation
   // -------------------------------------------------------------------------

   @Test void resolveRejectsBlankTask() {
      ScriptLibraryChangePlanRequest req = request(
         change(ScriptLibraryChangeRequest.VERB_CREATE, "a"));
      req.setTask("  ");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("task"), ex.getMessage());
   }

   @Test void resolveRejectsEmptyChangeList() {
      ScriptLibraryChangePlanRequest req = request();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("changes"), ex.getMessage());
   }

   @Test void resolveRejectsAnUnrecognizedVerb() {
      ScriptLibraryChangeRequest c = change("destroy", "a");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request(c), user));
      assertTrue(ex.getMessage().contains("verb"), ex.getMessage());
   }

   @Test void resolveAcceptsRemoveAsAnAliasForDelete() throws Exception {
      when(lib.getScript("a")).thenReturn("return 1;");

      try(MockedStatic<DependencyTransformer> dep = mockStatic(DependencyTransformer.class)) {
         dep.when(() -> DependencyTransformer.getDependencies(anyString())).thenReturn(List.of());

         ScriptLibraryChangeRequest c = change("remove", "a");
         ResolvedPlan plan = service.resolve(request(c), user);

         assertEquals(1, plan.changes().size());
      }
   }

   // -------------------------------------------------------------------------
   // create -- risk classification, the round-1 permission gap, name collision
   // -------------------------------------------------------------------------

   @Test void resolveCreateProducesARiskLowChangeForANewName() throws Exception {
      when(lib.getScript("newScript")).thenReturn(null);
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_CREATE, "newScript");
      c.setDescription("does a thing");
      c.setText("return 1;");

      ResolvedPlan plan = service.resolve(request(c), user);

      PlanChange pc = plan.changes().get(0);
      assertEquals("scriptlibrary:newScript", pc.property());
      assertNull(pc.currentValue());
      assertEquals(inetsoft.util.audit.AdminChangeRecord.RISK_LOW, pc.risk());
      assertFalse(plan.requiresAgentSignoff());
   }

   @Test void resolveCreateRefusesACollidingName() {
      when(lib.getScript("existing")).thenReturn("return 1;");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_CREATE, "existing");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request(c), user));
      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
   }

   /**
    * Regression test for round-1 review finding 1: {@code resolveCreate} did not call
    * {@code scriptLibraryService.requirePermission(name, user, ResourceAction.ADMIN)}, unlike
    * {@code resolveRename}/{@code resolveUpdate}/{@code resolveDelete} -- a caller explicitly
    * denied {@code ADMIN} on the Script Library resource could create new scripts while being
    * correctly blocked from every other verb. Fails without the fix (create would resolve
    * successfully despite the denial) and passes with it.
    */
   @Test void resolveCreateRefusesWhenCallerLacksAdminPermission() throws Exception {
      when(securityEngine.checkPermission(
         eq(user), eq(ResourceType.SCRIPT), eq("newScript"), eq(ResourceAction.ADMIN)))
         .thenReturn(false);
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_CREATE, "newScript");

      assertThrows(SecurityException.class, () -> service.resolve(request(c), user));
      // The permission gate must fire before the (more expensive/informative) existence check --
      // a denied caller should not learn whether a name is already taken.
      verify(lib, never()).getScript("newScript");
   }

   // -------------------------------------------------------------------------
   // rename / update -- risk classification, permission (pre-existing, still enforced)
   // -------------------------------------------------------------------------

   @Test void resolveRenameProducesARiskLowChangeAndRequiresPermission() throws Exception {
      when(lib.getScript("old")).thenReturn("return 1;");
      when(lib.getScriptComment("old")).thenReturn("desc");
      when(lib.getScript("new")).thenReturn(null);
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_RENAME, "old");
      c.setNewName("new");

      ResolvedPlan plan = service.resolve(request(c), user);

      assertEquals(inetsoft.util.audit.AdminChangeRecord.RISK_LOW, plan.changes().get(0).risk());
      // Checked twice: once via requireEntry -> getEntry -> isVisible, and again via resolveRename's
      // own explicit requirePermission call right after it -- both against the SAME ADMIN action.
      verify(securityEngine, times(2)).checkPermission(
         eq(user), eq(ResourceType.SCRIPT), eq("old"), eq(ResourceAction.ADMIN));
   }

   /**
    * Unlike {@code create} (below), a denied caller never reaches {@code resolveRename}'s own
    * explicit {@code requirePermission} call at all: {@code requireEntry} -> {@code getEntry} ->
    * {@code isVisible} already gates on the SAME {@code ResourceAction.ADMIN} check, and
    * {@code getEntry} folds "does not exist" and "not visible" into one
    * {@link MissingResourceException} (documented, matching {@code RecycleBinService.getEntry}'s
    * own posture) -- so a permission denial here surfaces as "no script library entry named ...",
    * not as a raw {@code SecurityException}. Verified real behavior, not a bug.
    */
   @Test void resolveRenameRefusesWhenCallerLacksAdminPermission() throws Exception {
      // The name must actually exist so control reaches isVisible's own checkPermission call --
      // otherwise the earlier "lib.getScript(name) == null" short-circuit in getEntry would throw
      // MissingResourceException for the wrong reason (not found, rather than not permitted).
      when(lib.getScript("old")).thenReturn("return 1;");
      when(securityEngine.checkPermission(
         eq(user), eq(ResourceType.SCRIPT), eq("old"), eq(ResourceAction.ADMIN)))
         .thenReturn(false);
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_RENAME, "old");
      c.setNewName("new");

      assertThrows(MissingResourceException.class, () -> service.resolve(request(c), user));
   }

   @Test void resolveUpdateProducesARiskLowChange() throws Exception {
      when(lib.getScript("a")).thenReturn("return 1;");
      when(lib.getScriptComment("a")).thenReturn("old desc");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_UPDATE, "a");
      c.setDescription("new desc");

      ResolvedPlan plan = service.resolve(request(c), user);

      PlanChange pc = plan.changes().get(0);
      assertEquals(inetsoft.util.audit.AdminChangeRecord.RISK_LOW, pc.risk());
      assertTrue(pc.proposedValue().contains("new desc"));
   }

   @Test void resolveUpdateRequiresADescription() {
      // The blank-description check fires before requireEntry, so the entry lookup is never
      // reached -- no LibManager stubbing needed here.
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_UPDATE, "a");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request(c), user));
      assertTrue(ex.getMessage().contains("nothing to change"), ex.getMessage());
   }

   // -------------------------------------------------------------------------
   // delete -- always high risk, dependency refuse/force-allow gate
   // -------------------------------------------------------------------------

   @Test void resolveDeleteIsAlwaysHighRiskWithNoDependents() throws Exception {
      when(lib.getScript("a")).thenReturn("return 1;");
      when(lib.getScriptComment("a")).thenReturn("desc");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_DELETE, "a");

      try(MockedStatic<DependencyTransformer> dep = mockStatic(DependencyTransformer.class)) {
         dep.when(() -> DependencyTransformer.getDependencies(anyString())).thenReturn(List.of());

         ResolvedPlan plan = service.resolve(request(c), user);

         PlanChange pc = plan.changes().get(0);
         assertEquals(inetsoft.util.audit.AdminChangeRecord.RISK_HIGH, pc.risk());
         assertTrue(plan.requiresAgentSignoff());
         assertNull(pc.proposedValue());
      }
   }

   @Test void resolveDeleteRefusesWhenDependentsExistAndForceIsNotSet() throws Exception {
      when(lib.getScript("a")).thenReturn("return 1;");
      when(lib.getScriptComment("a")).thenReturn("desc");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_DELETE, "a");

      try(MockedStatic<DependencyTransformer> dep = mockStatic(DependencyTransformer.class)) {
         AssetObject dependent = mock(AssetObject.class);
         dep.when(() -> DependencyTransformer.getDependencies(anyString()))
            .thenReturn(List.of(dependent));

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resolve(request(c), user));
         assertTrue(ex.getMessage().contains("force"), ex.getMessage());
      }
   }

   @Test void resolveDeleteAllowsDependentsWhenForceIsSet() throws Exception {
      when(lib.getScript("a")).thenReturn("return 1;");
      when(lib.getScriptComment("a")).thenReturn("desc");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_DELETE, "a");
      c.setForce(true);

      try(MockedStatic<DependencyTransformer> dep = mockStatic(DependencyTransformer.class)) {
         AssetObject dependent = mock(AssetObject.class);
         dep.when(() -> DependencyTransformer.getDependencies(anyString()))
            .thenReturn(List.of(dependent));

         ResolvedPlan plan = service.resolve(request(c), user);

         PlanChange pc = plan.changes().get(0);
         assertEquals(inetsoft.util.audit.AdminChangeRecord.RISK_HIGH, pc.risk());
         assertTrue(pc.description().contains("force: true"), pc.description());
      }
   }

   /** Same "gated by requireEntry's own isVisible check first" shape as rename -- see the javadoc
    * on {@link #resolveRenameRefusesWhenCallerLacksAdminPermission()}. */
   @Test void resolveDeleteRequiresAdminPermission() throws Exception {
      when(securityEngine.checkPermission(
         eq(user), eq(ResourceType.SCRIPT), eq("a"), eq(ResourceAction.ADMIN)))
         .thenReturn(false);
      when(lib.getScript("a")).thenReturn("return 1;");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_DELETE, "a");

      assertThrows(MissingResourceException.class, () -> service.resolve(request(c), user));
   }

   // -------------------------------------------------------------------------
   // MissingResourceException surfaced through requireEntry
   // -------------------------------------------------------------------------

   @Test void resolveRenameFailsWithMissingResourceExceptionWhenNameDoesNotExist() {
      when(lib.getScript("ghost")).thenReturn(null);
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_RENAME, "ghost");
      c.setNewName("new");

      assertThrows(MissingResourceException.class,
         () -> service.resolve(request(c), user));
   }
}

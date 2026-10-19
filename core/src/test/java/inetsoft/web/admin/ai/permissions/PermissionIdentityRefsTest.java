/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.permissions;

import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.security.PermissionGrant;
import inetsoft.web.admin.security.ResourcePermission;
import inetsoft.web.admin.security.SecurityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78857: every identity-reference form on the REAL {@code PermissionChangePlanService},
 * {@code PermissionChangesetApplyService} and {@code AdminPermissionController}, over a Mockito
 * spy of the REAL {@code SecurityService} -- so {@code getPermissionGrant}, {@code
 * create/update/deletePermissionGrant} and {@code checkIdType} are the production bodies. Only
 * {@code SecurityService.getPermission/setPermission} (an in-memory grant list) and the provider's
 * identity/org/permission reads are stubbed.
 *
 * <p>Uses only the public plan/apply API, so the class also runs against the code before the fix.
 */
@Tag("core")
class PermissionIdentityRefsTest {
   private static final String PATH = "Examples/Census";
   private static final String ORG = "host-org";

   private SecurityEngine securityEngine;
   private SecurityProvider provider;
   private SecurityService securityService;
   private PermissionChangePlanService planService;
   private PermissionChangesetApplyService applyService;
   private Principal user;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<Tool> tool;
   private MockedStatic<Audit> auditStatic;
   private final List<PermissionGrant> stored = new ArrayList<>();
   private boolean written;
   private final Set<IdentityID> users = new HashSet<>();

   @BeforeEach void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      provider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      users.add(new IdentityID("admin", ORG));
      User someUser = mock(User.class);
      when(provider.getUser(any()))
         .thenAnswer(inv -> users.contains(inv.<IdentityID>getArgument(0)) ? someUser : null);
      Organization someOrg = mock(Organization.class);
      when(provider.getOrganization(any())).thenAnswer(
         inv -> Set.of(ORG, "other-org").contains(inv.<String>getArgument(0)) ? someOrg : null);
      when(provider.getPermission(eq(ResourceType.ASSET), eq(PATH), nullable(String.class)))
         .thenAnswer(inv -> rawPermission());

      securityService = spy(new SecurityService(securityEngine, null, null, null, null, null,
                                                null, null));
      doAnswer(inv -> resourcePermission()).when(securityService)
         .getPermission(eq(PATH), eq("ASSET"), any());
      doAnswer(inv -> {
         ResourcePermission model = inv.getArgument(2);
         stored.clear();
         stored.addAll(model.getPermissionGrants());
         written = true;
         return null;
      }).when(securityService).setPermission(eq(PATH), eq("ASSET"), any(), any());

      planService = new PermissionChangePlanService(securityService, securityEngine);
      applyService = new PermissionChangesetApplyService(planService, securityService, securityEngine);

      user = mock(Principal.class);
      when(user.getName()).thenReturn(new IdentityID("caller", ORG).convertToKey());
      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      when(orgManager.getCurrentOrgID(any())).thenReturn(ORG);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().lenient());
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);

      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> inv.<String>getArgument(0).substring(4));
      tool.when(Tool::getHost).thenReturn("test-host");
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      securityEngineStatic.close();
      tool.close();
      auditStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   // ------------------------------------------------------------------ create, every form

   /** Bare and same-org {@code :} plans are byte-identical to the plans before the fix. */
   @Test void bareAndSameOrgColonPlansKeepTheirPinnedHash() throws Exception {
      for(String ref : List.of("admin", "admin:host-org", " admin ")) {
         ResolvedPlan plan = preview(create(ref));

         assertEquals("ASSET|" + PATH + "|USER|admin~;~host-org", plan.changes().get(0).property());
         assertEquals(PINNED_CREATE_ADMIN_HASH, plan.planHash(), ref);
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "admin~;~host-org", "admin^host-org" })
   void keyAndCaretFormsCreateTheGrantOfTheRealUser(String ref) throws Exception {
      ResolvedPlan plan = preview(create(ref));

      assertEquals("ASSET|" + PATH + "|USER|admin~;~host-org", plan.changes().get(0).property());
      assertEquals(PINNED_CREATE_ADMIN_HASH, plan.planHash());

      ApplyResult result = apply(plan, create(ref));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, result.results().get(0).status());
      assertEquals(1, stored.size());
      assertEquals(new IdentityID("admin", ORG), stored.get(0).getIdentityID());
   }

   @Test void caretWithAnUnknownOrgIsRefused() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> preview(create("regression-author^SELF")));

      assertTrue(ex.getMessage().contains("no organization \"SELF\""), ex.getMessage());
      assertFalse(written);
   }

   @ParameterizedTest
   @ValueSource(strings = { "admin^other-org", "admin~;~other-org", "admin:other-org" })
   void anotherOrgIsRefusedInEveryForm(String ref) {
      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> preview(create(ref)));

      assertTrue(ex.getMessage().contains("targets org \"other-org\""), ex.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = { "admin^__GLOBAL__", "admin~;~__GLOBAL__", "admin:__GLOBAL__" })
   void aGlobalIdentityIsRefusedInEveryForm(String ref) {
      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> preview(create(ref)));

      assertTrue(ex.getMessage().contains("global"), ex.getMessage());
   }

   @Test void aRealUserLiterallyNamedWithACaretIsUsedWhenOnlyTheLiteralExists() throws Exception {
      users.add(new IdentityID("j^host-org", ORG));
      users.add(new IdentityID("m^SELF", ORG));

      assertEquals("ASSET|" + PATH + "|USER|j^host-org~;~host-org",
                   preview(create("j^host-org")).changes().get(0).property());
      assertEquals("ASSET|" + PATH + "|USER|m^SELF~;~host-org",
                   preview(create("m^SELF")).changes().get(0).property());
   }

   @Test void aCaretReferenceBothReadingsResolveIsRefusedAsAmbiguous() {
      users.add(new IdentityID("k^host-org", ORG));
      users.add(new IdentityID("k", ORG));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> preview(create("k^host-org")));

      assertTrue(ex.getMessage().contains("ambiguous"), ex.getMessage());
      assertTrue(ex.getMessage().contains(
         "use \"k:host-org\" to name the second, or \"k^host-org:host-org\" to name the first"),
         ex.getMessage());
   }

   @Test void createForAnIdentityThatDoesNotExistIsRefused() {
      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> preview(create("user0")));

      assertTrue(ex.getMessage().contains("no USER \"user0\""), ex.getMessage());
   }

   @Test void aWrongKindStillGetsTheDidYouMeanRefusal() {
      PermissionChangeRequest change = create("admin");
      change.setIdentityType("GROUP");

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> preview(change));

      assertTrue(ex.getMessage().contains("Did you mean identityType=USER"), ex.getMessage());
   }

   @Test void blankPartsOfAnExplicitFormAreRefused() {
      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> preview(create("admin:")));

      assertTrue(ex.getMessage().contains("non-blank"), ex.getMessage());
   }

   // ------------------------------------------------------------------ update / delete

   @Test void updateWithTheKeyFormAppliesInsteadOfFailingRollback() throws Exception {
      seedGrant("admin", "READ");
      PermissionChangeRequest change = create("admin~;~host-org");
      change.setVerb(PermissionChangeRequest.VERB_UPDATE);
      change.setActions(List.of("WRITE"));

      ResolvedPlan plan = preview(change);
      ApplyResult result = apply(plan, change);

      // before the fix: rollback-failed ("state unknown ... admin~;~host-org~;~host-org")
      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("ASSET|" + PATH + "|USER|admin~;~host-org", plan.changes().get(0).property());
      assertEquals(List.of("WRITE"), stored.get(0).getActions());
      assertEquals(new IdentityID("admin", ORG), stored.get(0).getIdentityID());
   }

   @Test void deleteWithTheKeyFormAppliesInsteadOfFailingRollback() throws Exception {
      seedGrant("admin", "READ");
      PermissionChangeRequest change = delete("admin~;~host-org");

      ApplyResult result = apply(preview(change), change);

      // before the fix: rollback-failed, and the grant was left in place
      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertTrue(stored.isEmpty());
   }

   @Test void deleteOfAGrantWhoseIdentityIsGoneIsAllowedWithAWarning() throws Exception {
      seedGrant("ghost", "READ");
      PermissionChangeRequest change = delete("ghost");

      ResolvedPlan plan = preview(change);
      PlanChange planned = plan.changes().get(0);
      ApplyResult result = apply(plan, change);

      assertTrue(planned.description().startsWith("WARNING: identity not found"),
                 planned.description());
      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertTrue(stored.isEmpty());
   }

   // ------------------------------------------------------------------ get_permission_grant

   @ParameterizedTest
   @ValueSource(strings = { "admin", "admin^host-org", "admin~;~host-org" })
   void getGrantFindsTheGrantInEveryForm(String ref) throws Exception {
      seedGrant("admin", "READ");
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
      AdminPermissionController controller =
         new AdminPermissionController(securityService, planService, applyService, null);

      Map<String, Object> grant = controller.getGrant("ASSET", PATH, "USER", ref, user);

      assertEquals(true, grant.get("found"), String.valueOf(grant));
   }

   // ------------------------------------------------------------------ helpers

   private void seedGrant(String name, String action) {
      PermissionGrant grant = new PermissionGrant();
      grant.setIdentityID(new IdentityID(name, ORG));
      grant.setType("USER");
      grant.setActions(List.of(action));
      stored.add(grant);
      written = true;
   }

   private ResourcePermission resourcePermission() {
      ResourcePermission permission = new ResourcePermission();
      List<PermissionGrant> copy = new ArrayList<>();

      for(PermissionGrant g : stored) {
         PermissionGrant c = new PermissionGrant();
         c.setIdentityID(g.getIdentityID());
         c.setType(g.getType());
         c.setActions(new ArrayList<>(g.getActions()));
         copy.add(c);
      }

      permission.setPermissionGrants(copy);
      return permission;
   }

   private Permission rawPermission() {
      if(!written) {
         return null;
      }

      Map<ResourceAction, Set<String>> byAction = new HashMap<>();

      for(PermissionGrant g : stored) {
         for(String a : g.getActions()) {
            byAction.computeIfAbsent(ResourceAction.valueOf(a), k -> new HashSet<>())
               .add(g.getIdentityID().getName());
         }
      }

      Permission permission = new Permission();
      byAction.forEach((a, names) -> permission.setUserGrantsForOrg(a, names, ORG));
      return permission;
   }

   private ResolvedPlan preview(PermissionChangeRequest change) throws Exception {
      PermissionChangePlanRequest req = new PermissionChangePlanRequest();
      req.setTask("grant");
      req.setChanges(List.of(change));
      return planService.resolve(req, user);
   }

   private ApplyResult apply(ResolvedPlan plan, PermissionChangeRequest change) throws Exception {
      PermissionApplyRequest req = new PermissionApplyRequest();
      req.setTask("grant");
      req.setChanges(List.of(change));
      req.setPlanHash(plan.planHash());
      req.setTaskToken(plan.taskToken());
      req.setReviewOutcome("approved");
      return applyService.apply(req, user);
   }

   private static PermissionChangeRequest create(String identityId) {
      PermissionChangeRequest change = new PermissionChangeRequest();
      change.setVerb(PermissionChangeRequest.VERB_CREATE);
      change.setResourceType("ASSET");
      change.setResourcePath(PATH);
      change.setIdentityType("USER");
      change.setIdentityId(identityId);
      change.setActions(List.of("READ"));
      return change;
   }

   private static PermissionChangeRequest delete(String identityId) {
      PermissionChangeRequest change = create(identityId);
      change.setVerb(PermissionChangeRequest.VERB_DELETE);
      change.setActions(null);
      return change;
   }

   /** The planHash of a bare {@code admin} create on this fixture, taken from the code before
    * Bug #78857's fix. */
   private static final String PINNED_CREATE_ADMIN_HASH =
      "85e0c16926a9993ca1cafe1d460af0255ad2428852bc3eb8e4244b269bfec6d2";
}

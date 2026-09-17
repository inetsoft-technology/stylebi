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

import inetsoft.web.admin.security.PermissionGrant;
import inetsoft.web.admin.security.ResourcePermission;
import inetsoft.web.admin.security.SecurityService;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.action.ActionTreeNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Same gate shape as {@code AdminScheduleControllerTest}/{@code AdminChangesetControllerTest} --
 * the bearer-token backstop and site-admin check are copied verbatim, so these tests mirror that
 * coverage. Also covers the {@code {found: false}} shape for {@code getGrant} (spec section 3),
 * unique to this area. */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminPermissionControllerTest {
   @Mock private SecurityService securityService;
   @Mock private PermissionChangePlanService planService;
   @Mock private PermissionChangesetApplyService applyService;
   @Mock private ActionPermissionService actionPermissionService;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;
   private AdminPermissionController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach void setup() {
      controller = new AdminPermissionController(securityService, planService, applyService,
         actionPermissionService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   @Test void listGrantsThrowsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.listGrants("ASSET", "Examples/Census", principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(securityService);
   }

   @Test void listGrantsThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.listGrants("ASSET", "Examples/Census", principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(securityService);
   }

   @Test void listGrantsThrowsOnExcludedResourceType() throws Exception {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> controller.listGrants("SCHEDULE_TASK", "t1", principal));
      assertTrue(ex.getMessage().contains("resourceType"));
      verifyNoInteractions(securityService);
   }

   @Test void listGrantsDelegatesToSecurityApiService() throws Exception {
      ResourcePermission permission = new ResourcePermission();
      when(securityService.getPermission("Examples/Census", "ASSET", principal))
         .thenReturn(permission);

      ResourcePermission result = controller.listGrants("ASSET", "Examples/Census", principal);

      assertSame(permission, result);
   }

   @Test void listGrantableActionsThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.listGrantableActions(principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(actionPermissionService);
   }

   @Test void listGrantableActionsFlattensOnlyAllowedLeavesWithCategory() {
      ActionTreeNode aiAssistant = ActionTreeNode.builder()
         .label("AI Assistant").resource("*").folder(false).type(ResourceType.AI_ASSISTANT)
         .actions(EnumSet.of(ResourceAction.ACCESS)).build();
      ActionTreeNode emComponent = ActionTreeNode.builder()
         .label("Properties").resource("settings/properties").folder(false)
         .type(ResourceType.EM_COMPONENT).actions(EnumSet.of(ResourceAction.ACCESS)).build();
      ActionTreeNode bookmarkFolder = ActionTreeNode.builder()
         .label("Bookmark").folder(true).actions(EnumSet.noneOf(ResourceAction.class))
         .addChildren(ActionTreeNode.builder()
            .label("Open Bookmark").resource("OpenBookmark").folder(false)
            .type(ResourceType.VIEWSHEET_ACTION).actions(EnumSet.of(ResourceAction.READ)).build())
         .build();
      ActionTreeNode root = ActionTreeNode.builder()
         .label("").folder(true).actions(EnumSet.noneOf(ResourceAction.class))
         .addAllChildren(List.of(aiAssistant, emComponent, bookmarkFolder))
         .build();
      when(actionPermissionService.getActionTree(principal)).thenReturn(root);

      List<Map<String, Object>> leaves = controller.listGrantableActions(principal);

      assertEquals(2, leaves.size());
      Map<String, Object> ai = leaves.stream()
         .filter(l -> "AI_ASSISTANT".equals(l.get("resourceType"))).findFirst().orElseThrow();
      assertEquals("*", ai.get("resourcePath"));
      assertEquals("AI Assistant", ai.get("label"));
      Map<String, Object> bookmark = leaves.stream()
         .filter(l -> "VIEWSHEET_ACTION".equals(l.get("resourceType"))).findFirst().orElseThrow();
      assertEquals("OpenBookmark", bookmark.get("resourcePath"));
      assertEquals("Bookmark", bookmark.get("category"));
      assertTrue(leaves.stream().noneMatch(l -> "EM_COMPONENT".equals(l.get("resourceType"))));
   }

   @Test void getGrantReturnsFoundFalseWhenNoGrantExists() throws Exception {
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "alice", "USER",
         principal)).thenReturn(null);

      Map<String, Object> result =
         controller.getGrant("ASSET", "Examples/Census", "USER", "alice", principal);

      assertEquals(false, result.get("found"));
      assertEquals("alice", result.get("identityId"));
   }

   @Test void getGrantReturnsFoundTrueWithActionsWhenGrantExists() throws Exception {
      PermissionGrant grant = new PermissionGrant();
      grant.setActions(List.of("READ", "WRITE"));
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "alice", "USER",
         principal)).thenReturn(grant);

      Map<String, Object> result =
         controller.getGrant("ASSET", "Examples/Census", "USER", "alice", principal);

      assertEquals(true, result.get("found"));
      assertEquals(List.of("READ", "WRITE"), result.get("actions"));
   }

   @Test void previewThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.preview(new PermissionChangePlanRequest(), principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(planService);
   }

   @Test void previewDelegatesToPlanService() throws Exception {
      PermissionChangePlanRequest req = new PermissionChangePlanRequest();
      ResolvedPlan plan = new ResolvedPlan("t", Collections.emptyList(), false, false, "hash", "token");
      when(planService.resolve(req, principal)).thenReturn(plan);

      ResolvedPlan result = controller.preview(req, principal);

      assertSame(plan, result);
   }

   @Test void applyThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.apply(new PermissionApplyRequest(), principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(applyService);
   }

   @Test void applyDelegatesToApplyService() throws Exception {
      PermissionApplyRequest req = new PermissionApplyRequest();
      ApplyResult applied = new ApplyResult("tx-1", AdminChangesetApplyService.STATUS_APPLIED,
                                            null, Collections.emptyList(), null);
      when(applyService.apply(req, principal)).thenReturn(applied);

      ApplyResult result = controller.apply(req, principal);

      assertSame(applied, result);
   }

   @Test void handleIllegalArgumentReturnsFailedStatus() {
      var body = controller.handleIllegalArgument(new IllegalArgumentException("bad input"));

      assertEquals("failed", body.get("status"));
      assertEquals("bad input", body.get("error"));
   }

   @Test void handlePlanHashMismatchReturnsConflictWithFreshPlan() {
      ResolvedPlan fresh = new ResolvedPlan("t", Collections.emptyList(), false, false, "new-hash", "new-token");
      var body = controller.handlePlanHashMismatch(
         new AdminChangesetApplyService.PlanHashMismatchException(fresh));

      assertEquals("conflict", body.get("status"));
      ResolvedPlan returnedPlan = (ResolvedPlan) body.get("plan");
      assertEquals(fresh.task(), returnedPlan.task());
      assertEquals(fresh.planHash(), returnedPlan.planHash());
      assertNull(returnedPlan.taskToken());
   }

   // TaskTokenMismatchException's constructor (like PlanHashMismatchException's) never hands back
   // the taskToken it was given, so the returned plan is equal to, but not the same instance as,
   // `fresh`.
   @Test void handleTaskTokenMismatchReturnsConflictWithFreshPlan() {
      ResolvedPlan fresh = new ResolvedPlan("t", Collections.emptyList(), false, false, "new-hash", "new-token");
      var body = controller.handleTaskTokenMismatch(
         new AdminChangesetApplyService.TaskTokenMismatchException(fresh, "taskToken: mismatch"));

      assertEquals("conflict", body.get("status"));
      ResolvedPlan returned = (ResolvedPlan) body.get("plan");
      assertEquals(fresh.task(), returned.task());
      assertEquals(fresh.planHash(), returned.planHash());
      assertNull(returned.taskToken());
   }
}

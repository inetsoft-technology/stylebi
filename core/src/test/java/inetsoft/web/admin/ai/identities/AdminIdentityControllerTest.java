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
package inetsoft.web.admin.ai.identities;

import inetsoft.web.admin.security.SecurityService;
import inetsoft.web.admin.security.SecurityUser;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.security.auth.MissingResourceException;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Same gate shape as every prior area's controller test -- the bearer-token backstop and
 * site-admin check are copied verbatim. Also covers the structured 404-shaped response for an
 * unrecognized read id (spec section 3), unique in shape to this area (worded "not found or not
 * permitted" since a checkPermission denial and a real not-found are indistinguishable, spec
 * section 2). */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminIdentityControllerTest {
   @Mock private SecurityService securityService;
   @Mock private IdentityChangePlanService planService;
   @Mock private IdentityChangesetApplyService applyService;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;
   private AdminIdentityController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach void setup() {
      controller = new AdminIdentityController(securityService, planService, applyService);

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

   @Test void listUsersThrowsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.listUsers(principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(securityService);
   }

   @Test void listUsersThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.listUsers(principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(securityService);
   }

   @Test void getUserDelegatesToSecurityApiService() throws Exception {
      SecurityUser expected = new SecurityUser();
      when(securityService.getUser(any(), eq(principal))).thenReturn(expected);

      SecurityUser result = controller.getUser("bob", principal);

      assertSame(expected, result);
   }

   @Test void previewThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.preview(new IdentityChangePlanRequest(), principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(planService);
   }

   @Test void previewDelegatesToPlanService() throws Exception {
      IdentityChangePlanRequest req = new IdentityChangePlanRequest();
      ResolvedPlan plan = new ResolvedPlan("t", Collections.emptyList(), true, true, "hash", "token");
      when(planService.resolve(req, principal)).thenReturn(plan);

      ResolvedPlan result = controller.preview(req, principal);

      assertSame(plan, result);
   }

   @Test void applyThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.apply(new IdentityApplyRequest(), principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(applyService);
   }

   @Test void applyDelegatesToApplyService() throws Exception {
      IdentityApplyRequest req = new IdentityApplyRequest();
      IdentityApplyResult applied = new IdentityApplyResult("tx-1",
         AdminChangesetApplyService.STATUS_APPLIED, "ref", Collections.emptyList(), null);
      when(applyService.apply(req, principal)).thenReturn(applied);

      IdentityApplyResult result = controller.apply(req, principal);

      assertSame(applied, result);
   }

   @Test void handleIllegalArgumentReturnsFailedStatus() {
      var body = controller.handleIllegalArgument(new IllegalArgumentException("bad input"));

      assertEquals("failed", body.get("status"));
      assertEquals("bad input", body.get("error"));
   }

   @Test void handleMissingResourceReturnsNotFoundOrNotPermitted() {
      var body = controller.handleMissingResource(new MissingResourceException("bob"));

      assertEquals("failed", body.get("status"));
      assertTrue(body.get("error").contains("not found or not permitted"));
   }

   @Test void handlePlanHashMismatchReturnsConflictWithFreshPlan() {
      ResolvedPlan fresh = new ResolvedPlan("t", Collections.emptyList(), true, true, "new-hash", "new-token");
      var body = controller.handlePlanHashMismatch(
         new AdminChangesetApplyService.PlanHashMismatchException(fresh));

      assertEquals("conflict", body.get("status"));
      ResolvedPlan returnedPlan = (ResolvedPlan) body.get("plan");
      assertEquals(fresh.task(), returnedPlan.task());
      assertEquals(fresh.planHash(), returnedPlan.planHash());
      assertNull(returnedPlan.taskToken());
   }

   @Test void handleTaskTokenMismatchReturnsConflictWithFreshPlan() {
      ResolvedPlan fresh = new ResolvedPlan("t", Collections.emptyList(), true, true, "new-hash", "new-token");
      var body = controller.handleTaskTokenMismatch(
         new AdminChangesetApplyService.TaskTokenMismatchException(fresh,
            "taskToken: does not match the current plan; re-review before applying"));

      assertEquals("conflict", body.get("status"));
      ResolvedPlan returnedPlan = (ResolvedPlan) body.get("plan");
      assertEquals(fresh.task(), returnedPlan.task());
      assertEquals(fresh.planHash(), returnedPlan.planHash());
      assertNull(returnedPlan.taskToken());
   }
}

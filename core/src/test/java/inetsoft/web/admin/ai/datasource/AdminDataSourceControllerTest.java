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
package inetsoft.web.admin.ai.datasource;

import inetsoft.web.admin.datasource.*;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.security.ConnectionStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Same gate shape as {@code AdminScheduleControllerTest} -- the bearer-token backstop and
 * site-admin check are copied verbatim, so these tests mirror that file's coverage of them. */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminDataSourceControllerTest {
   @Mock private DataSourceService dataSourceService;
   @Mock private DataSourceChangePlanService planService;
   @Mock private DataSourceChangesetApplyService applyService;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;
   private AdminDataSourceController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach void setup() {
      controller = new AdminDataSourceController(dataSourceService, planService, applyService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   @Test void listDataSourcesThrowsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.listDataSources(null, principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(dataSourceService);
   }

   @Test void listDataSourcesThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.listDataSources(null, principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(dataSourceService);
   }

   @Test void listDataSourcesDelegatesToDataSourceService() throws Exception {
      DataSourceList list = new DataSourceList();
      when(dataSourceService.getDataSources(isNull(), eq(principal)))
         .thenReturn(list);

      DataSourceList result = controller.listDataSources(null, principal);

      assertSame(list, result);
   }

   @Test void getDataSourceThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.getDataSource("id1", principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(dataSourceService);
   }

   @Test void getDataSourceDelegatesToDataSourceService() throws Exception {
      TabularDataSourceProperties properties = new TabularDataSourceProperties();
      when(dataSourceService.getDataSource("id1", principal)).thenReturn(properties);

      DataSourceProperties result = controller.getDataSource("id1", principal);

      assertSame(properties, result);
   }

   @Test void previewThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.preview(new DataSourceChangePlanRequest(), principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(planService);
   }

   @Test void previewDelegatesToPlanService() throws Exception {
      DataSourceChangePlanRequest req = new DataSourceChangePlanRequest();
      ResolvedPlan plan = new ResolvedPlan("t", Collections.emptyList(), false, false, "hash", "token");
      when(planService.resolve(req, principal)).thenReturn(plan);

      ResolvedPlan result = controller.preview(req, principal);

      assertSame(plan, result);
   }

   @Test void testConnectionThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.testConnection(new DataSourceTestConnectionRequest(), principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(planService);
   }

   @Test void testConnectionDelegatesToPlanService() throws Exception {
      DataSourceTestConnectionRequest req = new DataSourceTestConnectionRequest();
      ConnectionStatus status = new ConnectionStatus("ok", true);
      when(planService.testConnection(req, principal)).thenReturn(status);

      ConnectionStatus result = controller.testConnection(req, principal);

      assertSame(status, result);
   }

   @Test void applyThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.apply(new DataSourceApplyRequest(), principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(applyService);
   }

   @Test void applyDelegatesToApplyService() throws Exception {
      DataSourceApplyRequest req = new DataSourceApplyRequest();
      DataSourceApplyResult applied = new DataSourceApplyResult("tx-1",
         AdminChangesetApplyService.STATUS_APPLIED, null, Collections.emptyList(), null);
      when(applyService.apply(req, principal)).thenReturn(applied);

      DataSourceApplyResult result = controller.apply(req, principal);

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

   @Test void handleTaskTokenMismatchReturnsConflictStatusWithCurrentPlan() {
      ResolvedPlan current = new ResolvedPlan("update url", Collections.emptyList(), true, true,
                                              "hash456", "token456");
      AdminChangesetApplyService.TaskTokenMismatchException ex =
         new AdminChangesetApplyService.TaskTokenMismatchException(current,
            "taskToken: does not match the current plan; re-review before applying");

      var body = controller.handleTaskTokenMismatch(ex);

      assertEquals("conflict", body.get("status"));
      assertEquals(ex.getMessage(), body.get("error"));
      ResolvedPlan returnedPlan = (ResolvedPlan) body.get("plan");
      assertEquals(current.task(), returnedPlan.task());
      assertEquals(current.planHash(), returnedPlan.planHash());
      // The 409 body must never hand back a fresh, still-unverified taskToken a caller could
      // replay without re-review (TaskTokenMismatchException scrubs it).
      assertNull(returnedPlan.taskToken());
   }

   @Test void handleTaskTokenMismatchIsAnnotatedConflict() throws NoSuchMethodException {
      ResponseStatus annotation = AdminDataSourceController.class
         .getMethod("handleTaskTokenMismatch",
                    AdminChangesetApplyService.TaskTokenMismatchException.class)
         .getAnnotation(ResponseStatus.class);

      assertNotNull(annotation, "handleTaskTokenMismatch must be annotated @ResponseStatus");
      assertEquals(HttpStatus.CONFLICT, annotation.value());
   }
}

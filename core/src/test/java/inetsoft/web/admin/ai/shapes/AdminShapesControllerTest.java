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
package inetsoft.web.admin.ai.shapes;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.content.dataspace.DataSpaceContentSettingsService;
import inetsoft.web.admin.content.dataspace.model.DataSpaceTreeModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Every endpoint's own {@code requireSiteAdmin} gate, {@link #list}'s own scope/path resolution
 * (never forwarding a caller-supplied absolute path -- 01-design.md section 2.2), and delegation to
 * {@link ShapeChangePlanService}/{@link ShapeChangesetApplyService}.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminShapesControllerTest {
   private static final String GLOBAL_DIR = "portal/shapes";
   private static final String ORG_DIR = "portal/myorg/shapes";

   @Mock private DataSpaceContentSettingsService dataSpaceContentSettingsService;
   @Mock private ShapeChangePlanService planService;
   @Mock private ShapeChangesetApplyService applyService;
   @Mock private OrganizationManager orgManager;
   @Mock private Principal principal;

   private AdminShapesController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ImageShapes> imageShapes;

   @BeforeEach
   void setup() {
      controller = new AdminShapesController(dataSpaceContentSettingsService, planService, applyService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      imageShapes = mockStatic(ImageShapes.class, withSettings().lenient());
      imageShapes.when(ImageShapes::getGlobalShapesDirectory).thenReturn(GLOBAL_DIR);
      imageShapes.when(ImageShapes::getShapesDirectory).thenReturn(ORG_DIR);

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      imageShapes.close();
      RequestContextHolder.resetRequestAttributes();
   }

   private void asNonBearerRequest() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));
   }

   // -------------------------------------------------------------------------
   // requireSiteAdmin gate
   // -------------------------------------------------------------------------

   @Test void listThrowsForbiddenWithoutBearerToken() {
      asNonBearerRequest();

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.list("global", null, principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(dataSpaceContentSettingsService);
   }

   @Test void listThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.list("global", null, principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(dataSpaceContentSettingsService, planService);
   }

   @Test void previewThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      ShapeChangePlanRequest req = new ShapeChangePlanRequest();

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.preview(req, principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(planService);
   }

   @Test void applyThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      ShapeApplyRequest req = new ShapeApplyRequest();

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.apply(req, principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(applyService);
   }

   // -------------------------------------------------------------------------
   // list -- scope/path resolution, never forwarding an unresolved caller path
   // -------------------------------------------------------------------------

   @Test void listResolvesTheGlobalRootAndChecksPermissionBeforeReadingTheTree() throws Exception {
      DataSpaceTreeModel expected = DataSpaceTreeModel.builder().nodes(List.of()).build();
      when(dataSpaceContentSettingsService.getTree(GLOBAL_DIR)).thenReturn(expected);

      assertSame(expected, controller.list("global", null, principal));

      verify(planService).requireShapesPermission(principal, GLOBAL_DIR);
      verify(dataSpaceContentSettingsService).getTree(GLOBAL_DIR);
   }

   @Test void listAppendsAGivenSubPathOntoTheResolvedRoot() throws Exception {
      DataSpaceTreeModel expected = DataSpaceTreeModel.builder().nodes(List.of()).build();
      when(dataSpaceContentSettingsService.getTree(ORG_DIR + "/icons")).thenReturn(expected);

      assertSame(expected, controller.list("organization", "icons", principal));

      verify(planService).requireShapesPermission(principal, ORG_DIR);
      verify(dataSpaceContentSettingsService).getTree(ORG_DIR + "/icons");
   }

   @Test void listRejectsATraversalSubPathBeforeReadingTheTree() throws Exception {
      assertThrows(IllegalArgumentException.class,
         () -> controller.list("global", "../../etc", principal));

      verify(planService).requireShapesPermission(principal, GLOBAL_DIR);
      verifyNoInteractions(dataSpaceContentSettingsService);
   }

   @Test void listRejectsAnInvalidScopeBeforeCheckingPermission() throws Exception {
      assertThrows(IllegalArgumentException.class,
         () -> controller.list("worldwide", null, principal));

      verifyNoInteractions(planService, dataSpaceContentSettingsService);
   }

   @Test void listPropagatesForbiddenFromRequireShapesPermissionWithoutReadingTheTree()
      throws Exception
   {
      doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "nope"))
         .when(planService).requireShapesPermission(principal, GLOBAL_DIR);

      assertThrows(ResponseStatusException.class,
         () -> controller.list("global", null, principal));

      verifyNoInteractions(dataSpaceContentSettingsService);
   }

   // -------------------------------------------------------------------------
   // preview / apply delegation
   // -------------------------------------------------------------------------

   @Test void previewDelegatesToPlanService() throws Exception {
      ShapeChangePlanRequest req = new ShapeChangePlanRequest();
      ResolvedPlan expected = new ResolvedPlan("task", List.of(), true, true, "hash", "token");
      when(planService.resolve(req, principal)).thenReturn(expected);

      assertSame(expected, controller.preview(req, principal));
      verify(planService).resolve(req, principal);
   }

   @Test void applyDelegatesToApplyService() throws Exception {
      ShapeApplyRequest req = new ShapeApplyRequest();
      ApplyResult expected = new ApplyResult(
         "shapes-1", AdminChangesetApplyService.STATUS_APPLIED, "backup/ref", List.of(), null);
      when(applyService.apply(req, principal)).thenReturn(expected);

      assertSame(expected, controller.apply(req, principal));
      verify(applyService).apply(req, principal);
   }

   // -------------------------------------------------------------------------
   // exception handlers
   // -------------------------------------------------------------------------

   @Test void handleIllegalArgumentReturnsFailedStatusWithMessage() {
      Map<String, String> actual =
         controller.handleIllegalArgument(new IllegalArgumentException("name: invalid name"));

      assertEquals("failed", actual.get("status"));
      assertEquals("name: invalid name", actual.get("error"));

      ResponseStatus annotation = assertDoesNotThrow(() -> AdminShapesController.class
         .getMethod("handleIllegalArgument", IllegalArgumentException.class)
         .getAnnotation(ResponseStatus.class));
      assertEquals(HttpStatus.BAD_REQUEST, annotation.value());
   }

   @Test void handleSecurityExceptionReturnsForbidden() throws NoSuchMethodException {
      Map<String, String> actual = controller.handleSecurityException(
         new inetsoft.sree.security.SecurityException("not logged in"));

      assertEquals("failed", actual.get("status"));
      assertEquals("not logged in", actual.get("error"));

      ResponseStatus annotation = AdminShapesController.class
         .getMethod("handleSecurityException", inetsoft.sree.security.SecurityException.class)
         .getAnnotation(ResponseStatus.class);
      assertEquals(HttpStatus.FORBIDDEN, annotation.value());
   }

   @Test void handlePlanHashMismatchReturnsConflictWithTheCurrentPlan() throws NoSuchMethodException {
      ResolvedPlan current = new ResolvedPlan("task",
         List.of(new PlanChange("portal/shapes/a.svg", null, "exists=false", "exists=true", "high",
                                "storage", true, "upload shape \"a.svg\"")),
         true, true, "current-hash", null);
      AdminChangesetApplyService.PlanHashMismatchException ex =
         new AdminChangesetApplyService.PlanHashMismatchException(current);

      Map<String, Object> actual = controller.handlePlanHashMismatch(ex);

      assertEquals("conflict", actual.get("status"));
      assertEquals(current, actual.get("plan"));

      ResponseStatus annotation = AdminShapesController.class
         .getMethod("handlePlanHashMismatch", AdminChangesetApplyService.PlanHashMismatchException.class)
         .getAnnotation(ResponseStatus.class);
      assertEquals(HttpStatus.CONFLICT, annotation.value());
   }

   @Test void handleTaskTokenMismatchReturnsConflictWithTheCurrentPlan() throws NoSuchMethodException {
      ResolvedPlan current = new ResolvedPlan("task", List.of(), true, true, "current-hash", null);
      AdminChangesetApplyService.TaskTokenMismatchException ex =
         new AdminChangesetApplyService.TaskTokenMismatchException(current, "taskToken: required");

      Map<String, Object> actual = controller.handleTaskTokenMismatch(ex);

      assertEquals("conflict", actual.get("status"));
      assertEquals("taskToken: required", actual.get("error"));
      assertEquals(current, actual.get("plan"));

      ResponseStatus annotation = AdminShapesController.class
         .getMethod("handleTaskTokenMismatch", AdminChangesetApplyService.TaskTokenMismatchException.class)
         .getAnnotation(ResponseStatus.class);
      assertEquals(HttpStatus.CONFLICT, annotation.value());
   }
}

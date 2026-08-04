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
package inetsoft.web.admin.ai;

import inetsoft.sree.security.OrganizationManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminAiControllerTest {
   @Mock private AdminBackupService backupService;
   @Mock private AdminChangePlanService planService;
   @Mock private AdminChangesetApplyService applyService;
   @Mock private OrganizationManager orgManager;
   @Mock private Principal principal;
   private AdminAiController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setup() {
      controller = new AdminAiController(backupService, planService, applyService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // default to a site-admin caller so the existing delegation tests exercise delegation;
      // individual tests override this to false to cover the FORBIDDEN gate
      lenient().when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      // Every endpoint also requires a bearer-authenticated request (AdminAiCallerGuard); bind a
      // request carrying one so these tests exercise the site-admin gate rather than that guard.
      // AdminAiCallerGuardTest covers the guard itself.
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   // -------------------------------------------------------------------------
   // bearer-token gate (CSRF backstop on the /api/wiz/** prefix)
   // -------------------------------------------------------------------------

   @Test void backupThrowsForbiddenWithoutBearerToken() {
      // A session-authenticated site admin (e.g. a cross-site request riding their session
      // cookie) must still be rejected: /api/wiz/** is CSRF-exempt.
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.backup(Map.of("transactionId", "chg-1"), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(backupService);
   }

   @Test void previewThrowsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.preview(new PlanRequest(), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(planService);
   }

   @Test void applyThrowsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.apply(new ApplyRequest(), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(applyService);
   }

   // -------------------------------------------------------------------------
   // site-admin gate (#5)
   // -------------------------------------------------------------------------

   @Test void backupThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.backup(Map.of("transactionId", "chg-1"), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(backupService);
   }

   @Test void previewThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.preview(new PlanRequest(), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(planService);
   }

   @Test void applyThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.apply(new ApplyRequest(), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(applyService);
   }

   // -------------------------------------------------------------------------
   // delegation (site-admin path)
   // -------------------------------------------------------------------------

   @Test void backupDelegatesToService() throws Exception {
      when(backupService.backup("chg-1")).thenReturn("admin-chg-1-123.zip");

      Map<String, String> actual =
         controller.backup(Map.of("transactionId", "chg-1"), principal);

      assertEquals("admin-chg-1-123.zip", actual.get("backupRef"));
      verify(backupService).backup("chg-1");
   }

   @Test void previewDelegatesToService() {
      PlanRequest req = new PlanRequest();
      req.setTask("raise max rows");
      ResolvedPlan expected =
         new ResolvedPlan("raise max rows", List.of(), false, false, "hash123");
      when(planService.resolve(req)).thenReturn(expected);

      ResolvedPlan actual = controller.preview(req, principal);

      assertEquals(expected, actual);
      verify(planService).resolve(req);
   }

   @Test void applyDelegatesToService() throws Exception {
      ApplyRequest req = new ApplyRequest();
      req.setTask("raise max rows");
      req.setPlanHash("hash123");
      ApplyResult expected = new ApplyResult(
         "chg-1", AdminChangesetApplyService.STATUS_APPLIED, null, List.of(), null);
      when(applyService.apply(req, principal)).thenReturn(expected);

      ApplyResult actual = controller.apply(req, principal);

      assertEquals(expected, actual);
      verify(applyService).apply(req, principal);
   }

   // -------------------------------------------------------------------------
   // error contract (#3): endpoints do not swallow exceptions; a scoped
   // @ExceptionHandler maps IllegalArgumentException to 400 instead
   // -------------------------------------------------------------------------

   @Test void backupPropagatesExceptionOnFailure() throws Exception {
      // AdminBackupService.backup throws when the snapshot did not happen; that must surface as an
      // error rather than a 200 with a bogus backupRef.
      doThrow(new IOException("snapshot failed")).when(backupService).backup("chg-1");

      IOException ex = assertThrows(IOException.class,
         () -> controller.backup(Map.of("transactionId", "chg-1"), principal));

      assertEquals("snapshot failed", ex.getMessage());
   }

   @Test void handleIllegalArgumentReturnsFailedStatusWithMessage() {
      Map<String, String> actual =
         controller.handleIllegalArgument(new IllegalArgumentException("property: must not be blank"));

      assertEquals("failed", actual.get("status"));
      assertEquals("property: must not be blank", actual.get("error"));
   }

   @Test void handleIllegalArgumentIsAnnotatedBadRequest() throws NoSuchMethodException {
      ResponseStatus annotation = AdminAiController.class
         .getMethod("handleIllegalArgument", IllegalArgumentException.class)
         .getAnnotation(ResponseStatus.class);

      assertNotNull(annotation, "handleIllegalArgument must be annotated @ResponseStatus");
      assertEquals(HttpStatus.BAD_REQUEST, annotation.value());
   }

   // -------------------------------------------------------------------------
   // error contract: a stale/missing planHash maps to 409, distinct from validation's 400 and
   // an unhandled failure's 500 (see AdminAiController.handlePlanHashMismatch javadoc)
   // -------------------------------------------------------------------------

   @Test void handlePlanHashMismatchReturnsConflictStatusWithCurrentPlan() {
      ResolvedPlan current = new ResolvedPlan("raise max rows", List.of(), false, false, "hash456");
      AdminChangesetApplyService.PlanHashMismatchException ex =
         new AdminChangesetApplyService.PlanHashMismatchException(current);

      Map<String, Object> actual = controller.handlePlanHashMismatch(ex);

      assertEquals("conflict", actual.get("status"));
      assertEquals(ex.getMessage(), actual.get("error"));
      assertSame(current, actual.get("plan"));
   }

   @Test void handlePlanHashMismatchIsAnnotatedConflict() throws NoSuchMethodException {
      ResponseStatus annotation = AdminAiController.class
         .getMethod("handlePlanHashMismatch",
                    AdminChangesetApplyService.PlanHashMismatchException.class)
         .getAnnotation(ResponseStatus.class);

      assertNotNull(annotation, "handlePlanHashMismatch must be annotated @ResponseStatus");
      assertEquals(HttpStatus.CONFLICT, annotation.value());
   }
}

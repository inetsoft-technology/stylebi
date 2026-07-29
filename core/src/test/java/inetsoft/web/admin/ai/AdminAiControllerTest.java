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

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminAiControllerTest {
   @Mock private AdminChangeService changeService;
   @Mock private AdminBackupService backupService;
   @Mock private OrganizationManager orgManager;
   @Mock private Principal principal;
   private AdminAiController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setup() {
      controller = new AdminAiController(changeService, backupService);

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

   @Test void changeThrowsForbiddenWithoutBearerToken() {
      // A session-authenticated site admin (e.g. a cross-site request riding their session
      // cookie) must still be rejected: /api/wiz/** is CSRF-exempt.
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));
      AdminChangeRequest req = new AdminChangeRequest();
      req.setProperty("max.rows");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.change(req, principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(changeService);
   }

   @Test void backupThrowsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.backup(Map.of("transactionId", "chg-1"), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(backupService);
   }

   @Test void restoreThrowsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.restore(Map.of("backupRef", "admin-chg-1-123.zip"), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(backupService);
   }

   // -------------------------------------------------------------------------
   // site-admin gate (#5)
   // -------------------------------------------------------------------------

   @Test void changeThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      AdminChangeRequest req = new AdminChangeRequest();
      req.setProperty("max.rows");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.change(req, principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(changeService);
   }

   @Test void backupThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.backup(Map.of("transactionId", "chg-1"), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(backupService);
   }

   @Test void restoreThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.restore(Map.of("backupRef", "admin-chg-1-123.zip"), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(backupService);
   }

   // -------------------------------------------------------------------------
   // delegation (site-admin path)
   // -------------------------------------------------------------------------

   @Test void changeDelegatesToService() {
      AdminChangeRequest req = new AdminChangeRequest();
      req.setProperty("max.rows");
      AdminChangeResult expected = new AdminChangeResult();
      expected.setStatus("verified");
      when(changeService.applyChange(req, principal)).thenReturn(expected);

      AdminChangeResult actual = controller.change(req, principal);

      assertEquals("verified", actual.getStatus());
      verify(changeService).applyChange(req, principal);
   }

   @Test void backupDelegatesToService() throws Exception {
      when(backupService.backup("chg-1")).thenReturn("admin-chg-1-123.zip");

      Map<String, String> actual =
         controller.backup(Map.of("transactionId", "chg-1"), principal);

      assertEquals("admin-chg-1-123.zip", actual.get("backupRef"));
      verify(backupService).backup("chg-1");
   }

   @Test void restoreReturnsRestoredStatusOnSuccess() throws Exception {
      Map<String, String> actual =
         controller.restore(Map.of("backupRef", "admin-chg-1-123.zip"), principal);

      assertEquals("restored", actual.get("status"));
      verify(backupService).restore("admin-chg-1-123.zip");
   }

   // -------------------------------------------------------------------------
   // error contract (#3): restore no longer swallows exceptions; a scoped
   // @ExceptionHandler maps IllegalArgumentException to 400 instead
   // -------------------------------------------------------------------------

   @Test void restorePropagatesExceptionOnFailure() throws Exception {
      doThrow(new IllegalStateException("no such backup"))
         .when(backupService).restore("missing.zip");

      IllegalStateException ex = assertThrows(IllegalStateException.class,
         () -> controller.restore(Map.of("backupRef", "missing.zip"), principal));

      assertEquals("no such backup", ex.getMessage());
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
}

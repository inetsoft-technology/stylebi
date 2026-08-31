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
package inetsoft.web.admin.ai.file;

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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminFileBackupControllerTest {
   @Mock private AdminFileBackupService backupService;
   @Mock private OrganizationManager orgManager;
   @Mock private Principal principal;
   private AdminFileBackupController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setup() {
      controller = new AdminFileBackupController(backupService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // default to a site-admin caller so the delegation tests exercise delegation; individual
      // tests override this to false to cover the FORBIDDEN gate
      lenient().when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      // Every endpoint also requires a bearer-authenticated request (AdminAiCallerGuard); bind a
      // request carrying one so these tests exercise the site-admin gate rather than that guard.
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   @Test void backupThrowsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.backup(Map.of("task", "nightly-export"), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(backupService);
   }

   @Test void backupThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.backup(Map.of("task", "nightly-export"), principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(backupService);
   }

   @Test void backupDelegatesToService() throws Exception {
      when(backupService.backup("nightly-export")).thenReturn("backup/wiz-manual-nightly-export-123.zip");

      Map<String, String> actual =
         controller.backup(Map.of("task", "nightly-export"), principal);

      assertEquals("backup/wiz-manual-nightly-export-123.zip", actual.get("backupPath"));
      verify(backupService).backup("nightly-export");
   }

   @Test void backupPropagatesExceptionOnFailure() throws Exception {
      doThrow(new IOException("backup failed")).when(backupService).backup("nightly-export");

      IOException ex = assertThrows(IOException.class,
         () -> controller.backup(Map.of("task", "nightly-export"), principal));

      assertEquals("backup failed", ex.getMessage());
   }

   @Test void handleIllegalArgumentReturnsFailedStatusWithMessage() {
      Map<String, String> actual =
         controller.handleIllegalArgument(new IllegalArgumentException("task: invalid task description"));

      assertEquals("failed", actual.get("status"));
      assertEquals("task: invalid task description", actual.get("error"));
   }

   @Test void handleIllegalArgumentIsAnnotatedBadRequest() throws NoSuchMethodException {
      ResponseStatus annotation = AdminFileBackupController.class
         .getMethod("handleIllegalArgument", IllegalArgumentException.class)
         .getAnnotation(ResponseStatus.class);

      assertNotNull(annotation, "handleIllegalArgument must be annotated @ResponseStatus");
      assertEquals(HttpStatus.BAD_REQUEST, annotation.value());
   }
}

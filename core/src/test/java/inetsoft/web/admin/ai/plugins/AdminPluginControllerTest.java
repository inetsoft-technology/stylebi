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
package inetsoft.web.admin.ai.plugins;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.web.admin.content.plugins.model.PluginsModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminPluginControllerTest {
   @Mock private AdminPluginService pluginService;
   @Mock private OrganizationManager orgManager;
   @Mock private Principal principal;
   private AdminPluginController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setup() {
      controller = new AdminPluginController(pluginService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   @Test void listThrowsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex =
         assertThrows(ResponseStatusException.class, () -> controller.list(principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(pluginService);
   }

   @Test void listThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex =
         assertThrows(ResponseStatusException.class, () -> controller.list(principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(pluginService);
   }

   @Test void listDelegatesToService() throws Exception {
      PluginsModel model = PluginsModel.builder().plugins(List.of()).build();
      when(pluginService.list(principal)).thenReturn(model);

      assertSame(model, controller.list(principal));
   }

   @Test void uploadDelegatesToService() throws Exception {
      MockMultipartFile file =
         new MockMultipartFile("file", "driver.jar", "application/java-archive", new byte[]{1});
      when(pluginService.upload(file, principal))
         .thenReturn(Map.of("uploadId", "upload-123", "fileName", "driver.jar"));

      Map<String, String> result = controller.upload(file, principal);

      assertEquals("upload-123", result.get("uploadId"));
      verify(pluginService).upload(file, principal);
   }

   @Test void uploadThrowsForbiddenWithoutBearerTokenBeforeReachingService() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));
      MockMultipartFile file =
         new MockMultipartFile("file", "driver.jar", "application/java-archive", new byte[]{1});

      assertThrows(ResponseStatusException.class, () -> controller.upload(file, principal));
      verifyNoInteractions(pluginService);
   }

   @Test void scanDelegatesToService() throws Exception {
      when(pluginService.scan("upload-123", principal))
         .thenReturn(inetsoft.web.admin.content.plugins.model.DriverList.builder()
                        .drivers(List.of("org.postgresql.Driver")).build());

      var result = controller.scan("upload-123", principal);

      assertEquals(List.of("org.postgresql.Driver"), result.drivers());
   }

   @Test void installDelegatesToService() throws Exception {
      AdminInstallDriverOrPluginRequest request = new AdminInstallDriverOrPluginRequest();
      PluginsModel model = PluginsModel.builder().plugins(List.of()).build();
      when(pluginService.install(request, principal)).thenReturn(model);

      assertSame(model, controller.install(request, principal));
   }

   @Test void installThrowsForbiddenForNonSiteAdminBeforeReachingService() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      AdminInstallDriverOrPluginRequest request = new AdminInstallDriverOrPluginRequest();

      assertThrows(ResponseStatusException.class, () -> controller.install(request, principal));
      verifyNoInteractions(pluginService);
   }

   @Test void removeDelegatesToService() throws Exception {
      AdminRemoveDriverOrPluginRequest request = new AdminRemoveDriverOrPluginRequest();
      PluginsModel model = PluginsModel.builder().plugins(List.of()).build();
      when(pluginService.remove(request, principal)).thenReturn(model);

      assertSame(model, controller.remove(request, principal));
   }

   @Test void removeThrowsForbiddenForNonSiteAdminBeforeReachingService() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      AdminRemoveDriverOrPluginRequest request = new AdminRemoveDriverOrPluginRequest();

      assertThrows(ResponseStatusException.class, () -> controller.remove(request, principal));
      verifyNoInteractions(pluginService);
   }

   @Test void handleIllegalArgumentReturnsFailedStatusWithMessage() {
      Map<String, String> actual = controller.handleIllegalArgument(
         new IllegalArgumentException("uploadId: required"));

      assertEquals("failed", actual.get("status"));
      assertEquals("uploadId: required", actual.get("error"));
   }
}

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
package inetsoft.web.admin.logviewer;

/*
 * Test strategy
 *
 * LogMonitoringController has the following testable behaviors:
 *
 *   REST endpoints (pure delegation, @Secured handled by framework):
 *     getLogs           — delegates to logMonitoringService.getLogs()
 *     refreshLogViewer  — delegates to logMonitoringService.getLog(node, file, offset, len)
 *     rotateLogFile     — delegates to logMonitoringService.rotateLogFile(node, file)
 *     downloadLogs      — delegates to logMonitoringService.downloadLogs(); wraps any
 *                         checked exception as RuntimeException
 *     getLogLinks       — delegates to logMonitoringService.getLinks(principal)
 *     getAuditLinks     — delegates to logMonitoringService.getLinks(principal)
 *
 *   STOMP subscriber (subscribeToLogRefresh) — permission guard:
 *     permission denied → SecurityException; addSubscriber not called
 *     permission granted → addSubscriber invoked
 *
 * Behavioral guarantees covered:
 *
 * [G1] getLogs delegates and returns the LogMonitoringModel.
 * [G2] refreshLogViewer forwards all four path variables to the service.
 * [G3] rotateLogFile delegates clusterNode and logFileName to the service.
 * [G4] downloadLogs delegates response and clusterNode; service exception → RuntimeException.
 * [G5] getLogLinks delegates to service with principal.
 * [G6] getAuditLinks delegates to service with principal.
 * [G7] STOMP subscribe: permission denied → SecurityException; addSubscriber not reached.
 * [G8] STOMP subscribe: permission granted → addSubscriber called.
 *
 * Bug #77068 — file endpoints are site-admin only in multi-tenant mode. The monitoring/log
 * permission is granted to org admins when fluentd logging with log.fluentd.orgAdminAccess is on
 * (intended only for the org-scoped external log viewer link), so the file endpoints must check
 * site admin themselves:
 *
 * [G9]  multi-tenant org admin → java.lang.SecurityException on all-logs, refresh, rotate,
 *       download; the service is never reached.
 * [G10] multi-tenant org admin → STOMP subscribe denied even though monitoring/log passes.
 * [G11] multi-tenant site admin → all file endpoints and STOMP subscribe pass.
 * [G12] multi-tenant org admin → log/links and audit/links are unaffected.
 * [G13] HTTP level: AdminExceptionHandler maps the denial to a sanitized 403 and the service
 *       is not reached; a site admin gets 200.
 * Single-tenant callers are covered by [G1]-[G8], which run with isMultiTenant() == false.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.AdminExceptionHandler;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class LogMonitoringControllerTest {

   @Mock private LogMonitoringService logMonitoringService;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private HttpServletResponse response;
   @Mock private StompHeaderAccessor stompHeaderAccessor;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;

   private LogMonitoringController controller;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setUp() {
      controller = new LogMonitoringController(
         logMonitoringService, monitoringDataService, securityEngine);

      // default: single-tenant, so [G1]-[G8] exercise the unchanged single-tenant behavior
      sUtilStatic = mockStatic(SUtil.class, withSettings().lenient());
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      orgManagerStatic.close();
   }

   private void multiTenant(boolean siteAdmin) {
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      lenient().when(orgManager.isSiteAdmin(principal)).thenReturn(siteAdmin);
   }

   private void grantMonitoringLog(boolean granted) {
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM_COMPONENT),
            eq("monitoring/log"), eq(ResourceAction.ACCESS)))
         .thenReturn(granted);
   }

   // [G1] getLogs returns the LogMonitoringModel from the service
   @Test
   void getLogs_delegatesToService() {
      LogMonitoringModel model = new LogMonitoringModel(null, List.of(), false, false, 100);
      when(logMonitoringService.getLogs()).thenReturn(model);

      LogMonitoringModel result = controller.getLogs(principal);

      assertSame(model, result);
   }

   // [G2] refreshLogViewer forwards all four path variables
   @Test
   void refreshLogViewer_forwardsAllParams() {
      List<String> lines = List.of("line1", "line2");
      when(logMonitoringService.getLog("node1", "server.log", 0, 100)).thenReturn(lines);

      List<String> result = controller.refreshLogViewer("node1", "server.log", 0, 100, principal);

      assertSame(lines, result);
   }

   // [G3] rotateLogFile delegates clusterNode and logFileName
   @Test
   void rotateLogFile_delegatesParams() throws Exception {
      LogMonitoringModel model = new LogMonitoringModel(null, List.of(), false, true, 100);
      when(logMonitoringService.rotateLogFile("node1", "server.log")).thenReturn(model);

      LogMonitoringModel result = controller.rotateLogFile("node1", "server.log", principal);

      assertSame(model, result);
   }

   // [G4a] downloadLogs delegates response and clusterNode to the service
   @Test
   void downloadLogs_delegatesToService() throws Exception {
      controller.downloadLogs(response, "node1", principal);

      verify(logMonitoringService).downloadLogs(response, "node1");
   }

   // [G4b] downloadLogs wraps any exception from service as RuntimeException
   @Test
   void downloadLogs_serviceThrows_wrappedAsRuntimeException() {
      doThrow(new RuntimeException("disk full")).when(logMonitoringService).downloadLogs(any(), any());

      assertThrows(RuntimeException.class,
         () -> controller.downloadLogs(response, null, principal));
   }

   // [G5] getLogLinks delegates to service with principal
   @Test
   void getLogLinks_delegatesWithPrincipal() {
      LogViewLinks links = mock(LogViewLinks.class);
      when(logMonitoringService.getLinks(principal)).thenReturn(links);

      LogViewLinks result = controller.getLogLinks(principal);

      assertSame(links, result);
   }

   // [G6] getAuditLinks delegates to service with principal
   @Test
   void getAuditLinks_delegatesWithPrincipal() {
      LogViewLinks links = mock(LogViewLinks.class);
      when(logMonitoringService.getLinks(principal)).thenReturn(links);

      LogViewLinks result = controller.getAuditLinks(principal);

      assertSame(links, result);
   }

   // [G7] STOMP permission denied → SecurityException; addSubscriber never called
   @Test
   void subscribeToLogRefresh_permissionDenied_throwsSecurityException() {
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM_COMPONENT),
            eq("monitoring/log"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      assertThrows(SecurityException.class,
         () -> controller.subscribeToLogRefresh(
            stompHeaderAccessor, "node1", "server.log", 0, 100, principal));

      verifyNoInteractions(monitoringDataService);
   }

   // [G8] STOMP permission granted → addSubscriber invoked
   @Test
   void subscribeToLogRefresh_permissionGranted_delegatesToMonitoringDataService()
      throws Exception
   {
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM_COMPONENT),
            eq("monitoring/log"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(monitoringDataService.addSubscriber(same(stompHeaderAccessor), any())).thenReturn(null);

      controller.subscribeToLogRefresh(stompHeaderAccessor, "node1", "server.log", 0, 100, principal);

      verify(monitoringDataService).addSubscriber(same(stompHeaderAccessor), any());
   }
   // [G9] multi-tenant org admin is denied on every file endpoint; the service is not reached
   @Test
   void fileEndpoints_multiTenantOrgAdmin_throwJavaLangSecurityException() {
      multiTenant(false);

      assertThrows(java.lang.SecurityException.class, () -> controller.getLogs(principal));
      assertThrows(java.lang.SecurityException.class,
         () -> controller.refreshLogViewer("node1", "sree-10.0.0.1.log", 0, 100, principal));
      assertThrows(java.lang.SecurityException.class,
         () -> controller.rotateLogFile("node1", "sree-10.0.0.1.log", principal));
      assertThrows(java.lang.SecurityException.class,
         () -> controller.downloadLogs(response, "node1", principal));

      verifyNoInteractions(logMonitoringService);
   }

   // [G10] multi-tenant org admin: STOMP subscribe denied even when monitoring/log passes
   @Test
   void subscribeToLogRefresh_multiTenantOrgAdminWithPermission_throwsSecurityException() {
      multiTenant(false);
      grantMonitoringLog(true);

      assertThrows(SecurityException.class,
         () -> controller.subscribeToLogRefresh(
            stompHeaderAccessor, "node1", "sree-10.0.0.1.log", 0, 100, principal));

      verifyNoInteractions(monitoringDataService, logMonitoringService);
   }

   // [G11] multi-tenant site admin passes on every file endpoint and on STOMP subscribe
   @Test
   void fileEndpoints_multiTenantSiteAdmin_delegateToService() throws Exception {
      multiTenant(true);
      grantMonitoringLog(true);
      LogMonitoringModel model = new LogMonitoringModel(null, List.of(), false, true, 100);
      List<String> lines = List.of("line1");
      when(logMonitoringService.getLogs()).thenReturn(model);
      when(logMonitoringService.getLog("node1", "sree-10.0.0.1.log", 0, 100)).thenReturn(lines);
      when(logMonitoringService.rotateLogFile("node1", "sree-10.0.0.1.log")).thenReturn(model);

      assertSame(model, controller.getLogs(principal));
      assertSame(lines, controller.refreshLogViewer("node1", "sree-10.0.0.1.log", 0, 100, principal));
      assertSame(model, controller.rotateLogFile("node1", "sree-10.0.0.1.log", principal));
      controller.downloadLogs(response, "node1", principal);
      controller.subscribeToLogRefresh(
         stompHeaderAccessor, "node1", "sree-10.0.0.1.log", 0, 100, principal);

      verify(logMonitoringService).downloadLogs(response, "node1");
      verify(monitoringDataService).addSubscriber(same(stompHeaderAccessor), any());
   }

   // [G12] multi-tenant org admin: the link endpoints are unaffected
   @Test
   void linkEndpoints_multiTenantOrgAdmin_stillDelegate() {
      multiTenant(false);
      LogViewLinks links = mock(LogViewLinks.class);
      when(logMonitoringService.getLinks(principal)).thenReturn(links);

      assertSame(links, controller.getLogLinks(principal));
      assertSame(links, controller.getAuditLinks(principal));
   }

   // [G13] HTTP level: org admin gets a sanitized 403 from AdminExceptionHandler, site admin 200
   @Test
   void allLogs_http_multiTenantOrgAdminForbidden_siteAdminOk() throws Exception {
      MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
         .setControllerAdvice(new AdminExceptionHandler(mock(LogManager.class)))
         .build();
      lenient().when(principal.getName()).thenReturn("orgAdmin~;~orgA");

      multiTenant(false);
      mvc.perform(get("/em/monitoring/logviewer/all-logs").principal(principal)
                     .accept(MediaType.APPLICATION_JSON))
         .andExpect(status().isForbidden())
         .andExpect(jsonPath("$.type").value("SecurityException"))
         .andExpect(jsonPath("$.message", not(containsString("orgAdmin"))));
      mvc.perform(get("/em/monitoring/logviewer/download").principal(principal))
         .andExpect(status().isForbidden());
      verifyNoInteractions(logMonitoringService);

      multiTenant(true);
      when(logMonitoringService.getLogs())
         .thenReturn(new LogMonitoringModel(null, List.of(), false, true, 100));
      mvc.perform(get("/em/monitoring/logviewer/all-logs").principal(principal)
                     .accept(MediaType.APPLICATION_JSON))
         .andExpect(status().isOk());
   }
}

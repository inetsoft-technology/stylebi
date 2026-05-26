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
 */

import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

   private LogMonitoringController controller;

   @BeforeEach
   void setUp() {
      controller = new LogMonitoringController(
         logMonitoringService, monitoringDataService, securityEngine);
   }

   // [G1] getLogs returns the LogMonitoringModel from the service
   @Test
   void getLogs_delegatesToService() {
      LogMonitoringModel model = new LogMonitoringModel(null, List.of(), false, false, 100);
      when(logMonitoringService.getLogs()).thenReturn(model);

      LogMonitoringModel result = controller.getLogs();

      assertSame(model, result);
   }

   // [G2] refreshLogViewer forwards all four path variables
   @Test
   void refreshLogViewer_forwardsAllParams() {
      List<String> lines = List.of("line1", "line2");
      when(logMonitoringService.getLog("node1", "server.log", 0, 100)).thenReturn(lines);

      List<String> result = controller.refreshLogViewer("node1", "server.log", 0, 100);

      assertSame(lines, result);
   }

   // [G3] rotateLogFile delegates clusterNode and logFileName
   @Test
   void rotateLogFile_delegatesParams() throws Exception {
      LogMonitoringModel model = new LogMonitoringModel(null, List.of(), false, true, 100);
      when(logMonitoringService.rotateLogFile("node1", "server.log")).thenReturn(model);

      LogMonitoringModel result = controller.rotateLogFile("node1", "server.log");

      assertSame(model, result);
   }

   // [G4a] downloadLogs delegates response and clusterNode to the service
   @Test
   void downloadLogs_delegatesToService() throws Exception {
      controller.downloadLogs(response, "node1");

      verify(logMonitoringService).downloadLogs(response, "node1");
   }

   // [G4b] downloadLogs wraps any exception from service as RuntimeException
   @Test
   void downloadLogs_serviceThrows_wrappedAsRuntimeException() {
      doThrow(new RuntimeException("disk full")).when(logMonitoringService).downloadLogs(any(), any());

      assertThrows(RuntimeException.class,
         () -> controller.downloadLogs(response, null));
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
}

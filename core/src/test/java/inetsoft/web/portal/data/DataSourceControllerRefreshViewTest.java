/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.portal.data;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.CoreTool;
import inetsoft.web.notifications.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77110 regression coverage.
 *
 * POST /api/portal/data/datasources/refreshView read the thread-local user message without
 * clearing it first, so a message left on the pooled servlet thread by an earlier request was
 * picked up, and it sent whatever it found through NotificationService.sendNotification(String),
 * which publishes to the shared /notifications topic that every connected client subscribes to.
 * The endpoint must now clear stale messages before the refresh and deliver the refresh's own
 * message only to the requesting user.
 */
@Tag("core")
class DataSourceControllerRefreshViewTest {
   @BeforeEach
   void setUp() {
      CoreTool.clearUserMessage();
      datasourcesService = mock(DatasourcesService.class);
      notificationService = mock(NotificationService.class);
      controller = createController(notificationService);

      request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      when(session.getId()).thenReturn("session-77110");
      when(request.getSession()).thenReturn(session);

      principal = mock(Principal.class);
      when(principal.getName()).thenReturn("requester");
   }

   @AfterEach
   void tearDown() {
      CoreTool.clearUserMessage();
   }

   @Test
   void refreshMessageGoesOnlyToRequesterAndStaleMessageIsDropped() throws Exception {
      CoreTool.addUserMessage("STALE-FROM-EARLIER-REQUEST");
      refreshAddsMessage("OWN-REFRESH-MESSAGE");

      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setSequenceNumber(7);
      DataSourceDefinition result = controller.refreshTabularView(definition, request, principal);

      assertEquals(7, result.getSequenceNumber());
      verify(notificationService).sendNotificationToUser("OWN-REFRESH-MESSAGE", principal);
      verify(notificationService, never()).sendNotification(anyString());
      verify(notificationService, never())
         .sendNotificationToUser(contains("STALE"), any());
   }

   @Test
   void staleMessageIsNotSentWhenRefreshAddsNone() throws Exception {
      CoreTool.addUserMessage("STALE-FROM-EARLIER-REQUEST");
      when(datasourcesService.refreshTabularView(any())).thenReturn(new DataSourceDefinition());

      controller.refreshTabularView(new DataSourceDefinition(), request, principal);

      verify(notificationService, never()).sendNotification(anyString());
      verify(notificationService, never()).sendNotificationToUser(anyString(), any());
   }

   @Test
   void nullPrincipalDoesNotThrowOrBroadcast() throws Exception {
      // real service: convertAndSendToUser rejects a null user destination
      Cluster cluster = mock(Cluster.class);
      NotificationService realService = new NotificationService(
         new SimpMessagingTemplate(mock(MessageChannel.class)), cluster);
      controller = createController(realService);
      refreshAddsMessage("OWN-REFRESH-MESSAGE");

      assertDoesNotThrow(
         () -> controller.refreshTabularView(new DataSourceDefinition(), request, null));
      verify(cluster, never()).sendMessage(any());
   }

   private void refreshAddsMessage(String message) {
      when(datasourcesService.refreshTabularView(any())).thenAnswer(invocation -> {
         CoreTool.addUserMessage(message);
         return new DataSourceDefinition();
      });
   }

   private DataSourceController createController(NotificationService service) {
      DataSourceController result = new DataSourceController(
         datasourcesService, null, null, null, null, null);
      ReflectionTestUtils.setField(result, "notificationService", service);
      return result;
   }

   private DatasourcesService datasourcesService;
   private NotificationService notificationService;
   private DataSourceController controller;
   private HttpServletRequest request;
   private Principal principal;
}

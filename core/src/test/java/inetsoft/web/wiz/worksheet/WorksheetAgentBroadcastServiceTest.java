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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.DataCacheSweeper;
import inetsoft.uql.util.XSessionService;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WorksheetAgentBroadcastServiceTest {
   // Building an SRPrincipal (via TestPrincipals.user) resolves an XSessionService Spring
   // bean; a minimal application context supplying it must be present.
   private static GenericApplicationContext appContext;

   @BeforeAll
   static void initContext() {
      appContext = new GenericApplicationContext();
      appContext.getBeanFactory().registerSingleton(
         "dataCacheSweeper", mock(DataCacheSweeper.class));
      appContext.getBeanFactory().registerSingleton(
         "xSessionService", new XSessionService());
      appContext.refresh();
      ConfigurationContext.getContext().setApplicationContext(appContext);
   }

   @AfterAll
   static void tearDownContext() {
      ConfigurationContext.getContext().setApplicationContext(null);

      if(appContext != null) {
         appContext.close();
      }
   }

   @Test
   void broadcastsRefreshToOwnerSocketSessionWithoutClientId() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      WorksheetAgentBroadcastService svc = new WorksheetAgentBroadcastService(dispatcher);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getSocketSessionId()).thenReturn("stomp-1");
      when(rws.getSocketUserName()).thenReturn("browser-user");
      Principal owner = TestPrincipals.user("alice", "host-org");

      svc.broadcastRefresh(rws, "Worksheet/foo-7", owner);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> headers =
         ArgumentCaptor.forClass(Map.class);
      // Must target the browser's recorded socket user, NOT the agent owner's name.
      verify(dispatcher).convertAndSendToUser(eq("browser-user"), eq("/commands"), any(),
                                              headers.capture());
      assertEquals("stomp-1", SimpMessageHeaderAccessor.getSessionId(headers.getValue()));
      assertNull(headers.getValue().get("inetsoftClientId"),
                 "broadcast must omit inetsoftClientId so the browser accepts it");
   }

   @Test
   void fallsBackToOwnerWhenNoSocketUser() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      WorksheetAgentBroadcastService svc = new WorksheetAgentBroadcastService(dispatcher);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getSocketSessionId()).thenReturn("stomp-1");
      when(rws.getSocketUserName()).thenReturn(null);
      Principal owner = TestPrincipals.user("alice", "host-org");

      svc.broadcastRefresh(rws, "Worksheet/foo-7", owner);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> headers =
         ArgumentCaptor.forClass(Map.class);
      // No recorded socket user -> fall back to the agent owner's name.
      verify(dispatcher).convertAndSendToUser(eq("alice~;~host-org"), eq("/commands"), any(),
                                              headers.capture());
      assertEquals("stomp-1", SimpMessageHeaderAccessor.getSessionId(headers.getValue()));
      assertNull(headers.getValue().get("inetsoftClientId"),
                 "broadcast must omit inetsoftClientId so the browser accepts it");
   }

   @Test
   void noBroadcastWhenNoSocketSession() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      WorksheetAgentBroadcastService svc = new WorksheetAgentBroadcastService(dispatcher);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getSocketSessionId()).thenReturn(null);
      svc.broadcastRefresh(rws, "Worksheet/foo-7", TestPrincipals.user("alice", "host-org"));
      verifyNoInteractions(dispatcher);
   }
}

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
package inetsoft.web.admin.monitoring;

/*
 * Test strategy
 *
 * MonitorLevelController exposes two entry points:
 *
 *   REST GET /api/em/monitoring/level — calls the static MonitorLevelService.getMonitorLevel()
 *     and returns the integer result. Any exception from that call is caught and re-thrown as
 *     RuntimeException.
 *
 *   STOMP @SubscribeMapping /monitoring/monitor-level — first checks the caller's EM permission;
 *     denied → SecurityException; granted → delegates to MonitoringDataService.addSubscriber.
 *
 * Behavioral guarantees covered:
 *
 * [G1] REST getMonitoringLevel returns the value from MonitorLevelService.getMonitorLevel().
 * [G2] REST getMonitoringLevel wraps any exception from MonitorLevelService as RuntimeException.
 * [G3] STOMP getMonitoringLevel: permission denied → SecurityException; addSubscriber not called.
 * [G4] STOMP getMonitoringLevel: permission granted → addSubscriber invoked.
 */

import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class MonitorLevelControllerTest {

   @Mock private MonitoringDataService monitoringDataService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private StompHeaderAccessor stompHeaderAccessor;
   @Mock private Principal principal;

   private MonitorLevelController controller;
   private MockedStatic<MonitorLevelService> monitorLevelServiceStatic;

   @BeforeEach
   void setUp() {
      controller = new MonitorLevelController(monitoringDataService, securityEngine);
      monitorLevelServiceStatic = mockStatic(MonitorLevelService.class, withSettings().lenient());
      monitorLevelServiceStatic.when(MonitorLevelService::getMonitorLevel).thenReturn(1);
   }

   @AfterEach
   void tearDown() {
      monitorLevelServiceStatic.close();
   }

   // [G1] REST endpoint returns value from MonitorLevelService.getMonitorLevel()
   @Test
   void getMonitoringLevel_rest_returnsServiceLevel() {
      monitorLevelServiceStatic.when(MonitorLevelService::getMonitorLevel).thenReturn(2);

      int result = controller.getMonitoringLevel();

      assertEquals(2, result);
   }

   // [G2] any exception from MonitorLevelService is re-thrown as RuntimeException
   @Test
   void getMonitoringLevel_rest_serviceThrows_wrappedAsRuntimeException() {
      monitorLevelServiceStatic.when(MonitorLevelService::getMonitorLevel)
         .thenThrow(new RuntimeException("config error"));

      assertThrows(RuntimeException.class, () -> controller.getMonitoringLevel());
   }

   // [G3] STOMP subscribe: permission denied → SecurityException; addSubscriber not reached
   @Test
   void getMonitoringLevel_stomp_permissionDenied_throwsSecurityException() {
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      assertThrows(SecurityException.class,
         () -> controller.getMonitoringLevel(stompHeaderAccessor, principal));

      verifyNoInteractions(monitoringDataService);
   }

   // [G4] STOMP subscribe: permission granted → addSubscriber invoked
   @Test
   void getMonitoringLevel_stomp_permissionGranted_delegatesToMonitoringDataService()
      throws Exception
   {
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(monitoringDataService.addSubscriber(same(stompHeaderAccessor), any())).thenReturn(0);

      controller.getMonitoringLevel(stompHeaderAccessor, principal);

      verify(monitoringDataService).addSubscriber(same(stompHeaderAccessor), any());
   }
}

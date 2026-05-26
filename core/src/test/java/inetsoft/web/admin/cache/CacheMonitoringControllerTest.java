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
package inetsoft.web.admin.cache;

/*
 * Test strategy
 *
 * CacheMonitoringController has a single STOMP subscribe handler — subscribeDataGrid — that
 * enforces an inline permission check before delegating to MonitoringDataService.
 * No REST endpoints exist in this controller.
 *
 * The permission check is:
 *   securityEngine.getSecurityProvider().checkPermission(
 *       principal, EM_COMPONENT, "monitoring/cache", ACCESS)
 *
 * Behavioral guarantees covered:
 *
 * [G1] When checkPermission returns false → SecurityException is thrown, addSubscriber
 *      is never called.
 * [G2] When checkPermission returns true → monitoringDataService.addSubscriber is called
 *      with the provided StompHeaderAccessor.
 */

import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class CacheMonitoringControllerTest {

   @Mock private CacheService cacheService;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private StompHeaderAccessor stompHeaderAccessor;
   @Mock private Principal principal;

   private CacheMonitoringController controller() {
      return new CacheMonitoringController(cacheService, monitoringDataService, securityEngine);
   }

   private void stubPermission(boolean granted) {
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM_COMPONENT),
            eq("monitoring/cache"), eq(ResourceAction.ACCESS)))
         .thenReturn(granted);
   }

   // [G1] permission denied → SecurityException; addSubscriber never reached
   @Test
   void subscribeDataGrid_permissionDenied_throwsSecurityException() {
      stubPermission(false);

      assertThrows(SecurityException.class,
         () -> controller().subscribeDataGrid(stompHeaderAccessor, Optional.empty(), principal));

      verifyNoInteractions(monitoringDataService);
   }

   // [G2] permission granted → addSubscriber is invoked with the header accessor
   @Test
   void subscribeDataGrid_permissionGranted_delegatesToMonitoringDataService() throws Exception {
      stubPermission(true);
      when(monitoringDataService.addSubscriber(same(stompHeaderAccessor), any())).thenReturn(null);

      controller().subscribeDataGrid(stompHeaderAccessor, Optional.empty(), principal);

      verify(monitoringDataService).addSubscriber(same(stompHeaderAccessor), any());
   }
}

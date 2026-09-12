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
package inetsoft.web.admin.query;

/*
 * Test strategy
 *
 * QueryMonitoringController has two entry points:
 *
 *   STOMP @SubscribeMapping — inline permission check before delegating to
 *     MonitoringDataService.addSubscriber.
 *
 *   REST POST /api/em/monitoring/queries/remove/** — delegates to
 *     queryService.destroyClusterQueries(address, ids). Any exception is caught,
 *     logged, and NOT rethrown (silent failure).
 *
 * The REST endpoint inherits getServerClusterNode() from AbstractMonitoringController.
 * For a blank/null server path, getServerClusterNode returns null, which is passed as the
 * address. For non-trivial cluster-node resolution (SUtil.computeServerClusterNode), the
 * behavior is outside this controller's scope and is not tested here.
 *
 * Behavioral guarantees covered:
 *
 * [G1] STOMP subscribe: permission denied → SecurityException; addSubscriber not called.
 * [G2] STOMP subscribe: permission granted → addSubscriber invoked.
 * [G3] REST remove: delegates ids and resolved address to queryService.destroyClusterQueries.
 * [G4] REST remove: exception from queryService is swallowed (no rethrow).
 */

import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import org.junit.jupiter.api.BeforeEach;
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
class QueryMonitoringControllerTest {

   @Mock private QueryService queryService;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private StompHeaderAccessor stompHeaderAccessor;
   @Mock private Principal principal;

   private QueryMonitoringController controller;

   @BeforeEach
   void setUp() {
      controller = new QueryMonitoringController(
         queryService, monitoringDataService, securityEngine);
   }

   // [G1] STOMP permission denied → SecurityException; addSubscriber not called
   @Test
   void subscribe_permissionDenied_throwsSecurityException() {
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM_COMPONENT),
            eq("monitoring/queries/executing"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      assertThrows(SecurityException.class,
         () -> controller.subscribe(stompHeaderAccessor, Optional.empty(), principal));

      verifyNoInteractions(monitoringDataService);
   }

   // [G2] STOMP permission granted → addSubscriber invoked
   @Test
   void subscribe_permissionGranted_delegatesToMonitoringDataService() throws Exception {
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM_COMPONENT),
            eq("monitoring/queries/executing"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(monitoringDataService.addSubscriber(same(stompHeaderAccessor), any())).thenReturn(null);

      controller.subscribe(stompHeaderAccessor, Optional.empty(), principal);

      verify(monitoringDataService).addSubscriber(same(stompHeaderAccessor), any());
   }

   // [G3] REST remove with blank server → null address forwarded to destroyClusterQueries
   @Test
   void remove_blankServer_passesNullAddressToService() throws Exception {
      String[] ids = { "q1", "q2" };

      // blank server path → getServerClusterNode returns null
      controller.remove(ids, "/");

      verify(queryService).destroyClusterQueries(isNull(), eq(ids));
   }

   // [G4] exception from queryService is swallowed; no exception propagates to caller
   @Test
   void remove_serviceThrows_exceptionSwallowed() throws Exception {
      String[] ids = { "q1" };
      doThrow(new RuntimeException("connection lost"))
         .when(queryService).destroyClusterQueries(any(), any());

      assertDoesNotThrow(() -> controller.remove(ids, "/"));
   }
}

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
package inetsoft.web.admin.user;

/*
 * Test strategy
 *
 * UserStatusController has three STOMP subscribers and one REST endpoint.
 *
 *   Three STOMP subscribers (subscribeSessionModel, subscribeFailedLoginGrid,
 *   subscribeTop5UsersGrid) each perform an inline permission check before delegating to
 *   MonitoringDataService.addSubscriber. Different resources are guarded:
 *     subscribeSessionModel / subscribeFailedLoginGrid → "monitoring/users"
 *     subscribeTop5UsersGrid                           → "monitoring/summary"
 *
 *   REST POST /api/em/monitor/user/logout (logout) — delegates session IDs and resolved
 *   cluster address to userService.logoutSession().
 *
 * Behavioral guarantees covered:
 *
 * [G1] subscribeSessionModel: permission denied → SecurityException; addSubscriber not called.
 * [G2] subscribeFailedLoginGrid: permission denied → SecurityException.
 * [G3] subscribeTop5UsersGrid: permission denied (monitoring/summary) → SecurityException.
 * [G4] logout delegates session IDs and null address (blank server) to userService.
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
class UserStatusControllerTest {

   @Mock private UserService userService;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private StompHeaderAccessor stompHeaderAccessor;
   @Mock private Principal principal;

   private UserStatusController controller;

   @BeforeEach
   void setUp() {
      controller = new UserStatusController(userService, monitoringDataService, securityEngine);
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
   }

   private void stubPermission(String resource, boolean granted) {
      lenient().when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM_COMPONENT),
            eq(resource), eq(ResourceAction.ACCESS)))
         .thenReturn(granted);
   }

   // [G1] session model subscribe: permission denied → SecurityException
   @Test
   void subscribeSessionModel_permissionDenied_throwsSecurityException() {
      stubPermission("monitoring/users", false);

      assertThrows(SecurityException.class,
         () -> controller.subscribeSessionModel(stompHeaderAccessor, Optional.empty(), principal));

      verifyNoInteractions(monitoringDataService);
   }

   // [G2] failed login subscribe: permission denied → SecurityException
   @Test
   void subscribeFailedLoginGrid_permissionDenied_throwsSecurityException() {
      stubPermission("monitoring/users", false);

      assertThrows(SecurityException.class,
         () -> controller.subscribeFailedLoginGrid(stompHeaderAccessor, Optional.empty(), principal));

      verifyNoInteractions(monitoringDataService);
   }

   // [G3] top-5-users subscribe: denied on "monitoring/summary" → SecurityException
   @Test
   void subscribeTop5UsersGrid_permissionDenied_throwsSecurityException() {
      stubPermission("monitoring/summary", false);

      assertThrows(SecurityException.class,
         () -> controller.subscribeTop5UsersGrid(stompHeaderAccessor, Optional.empty(), principal));

      verifyNoInteractions(monitoringDataService);
   }

   // [G4] logout delegates sessionIds and null address (blank server path) to userService
   @Test
   void logout_blankServer_passesNullAddressToService() {
      String[] sessionIds = { "s1", "s2" };

      controller.logout(sessionIds, "/");

      verify(userService).logoutSession(isNull(), eq(sessionIds));
   }
}

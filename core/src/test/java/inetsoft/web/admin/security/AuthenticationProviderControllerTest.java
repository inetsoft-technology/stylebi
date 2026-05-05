/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.admin.security;

/*
 * Test strategy
 *
 * Class type: thin orchestration controller — nearly all logic lives in
 * AuthenticationProviderService; in-controller code is limited to three areas:
 *   1. removeAuthenticationProvider()  — fetches chain, resolves provider name at index,
 *                                        then delegates removal.
 *   2. getConnectionStatus()           — wraps the raw status string in ConnectionStatus.
 *   3. All other endpoints             — pure pass-through delegation.
 *
 * Coverage scope:
 *   [remove: chain present]      chain Optional present → get name at index → delegates to service
 *   [remove: chain absent]       chain Optional.empty() → orElseThrow → Exception propagates
 *   [getProviders: delegation]   delegates to service, returns its result unchanged
 *   [add: delegation]            delegates to service with model + principal
 *   [connectionStatus: wrapping] wraps String status from service into ConnectionStatus
 *
 * All other endpoints (getAuthenticationProvider, editAuthenticationProvider,
 * reorderAuthenticationProviders, clearCache, copyProvider, get-users/roles/groups, etc.)
 * are pure delegation with no in-controller guards — skipped.
 */

import inetsoft.sree.security.*;
import inetsoft.web.admin.general.DatabaseSettingsService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AuthenticationProviderControllerTest {

   @Mock private AuthenticationProviderService authenticationProviderService;
   @Mock private DatabaseSettingsService databaseSettingsService;
   @Mock private AuthenticationChain authenticationChain;
   @Mock private AuthenticationProvider authenticationProvider;
   @Mock private Principal principal;

   private AuthenticationProviderController controller;

   @BeforeEach
   void setUp() {
      controller = new AuthenticationProviderController(authenticationProviderService,
                                                        databaseSettingsService);
   }

   // -------------------------------------------------------------------------
   // getConfiguredAuthenticationProviders()
   // -------------------------------------------------------------------------

   @Test
   void getConfiguredAuthenticationProviders_delegatesToService() {
      SecurityProviderStatusList expected = SecurityProviderStatusList.builder().build();
      when(authenticationProviderService.getProviderListModel()).thenReturn(expected);

      SecurityProviderStatusList result = controller.getConfiguredAuthenticationProviders();

      assertSame(expected, result);
      verify(authenticationProviderService).getProviderListModel();
   }

   // -------------------------------------------------------------------------
   // addAuthenticationProvider()
   // -------------------------------------------------------------------------

   @Test
   void addAuthenticationProvider_delegatesToService() throws Exception {
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("myProvider")
         .providerType(SecurityProviderType.FILE)
         .build();

      controller.addAuthenticationProvider(model, principal);

      verify(authenticationProviderService).addAuthenticationProvider(model, "myProvider", principal);
   }

   // -------------------------------------------------------------------------
   // removeAuthenticationProvider()
   // -------------------------------------------------------------------------

   // [remove: chain present] chain present → service called with correct name
   @Test
   void removeAuthenticationProvider_chainPresent_delegatesWithCorrectName() throws Exception {
      when(authenticationProviderService.getAuthenticationChain())
         .thenReturn(Optional.of(authenticationChain));
      when(authenticationChain.getProviders()).thenReturn(List.of(authenticationProvider));
      when(authenticationProvider.getProviderName()).thenReturn("fileProvider");

      controller.removeAuthenticationProvider(0, principal);

      verify(authenticationProviderService).removeAuthenticationProvider(0, "fileProvider", principal);
   }

   // [remove: chain absent] chain Optional.empty() → Exception propagates before service is called
   @Test
   void removeAuthenticationProvider_chainAbsent_throwsException() throws Exception {
      when(authenticationProviderService.getAuthenticationChain()).thenReturn(Optional.empty());

      assertThrows(Exception.class, () -> controller.removeAuthenticationProvider(0, principal));
      verify(authenticationProviderService, never())
         .removeAuthenticationProvider(anyInt(), anyString(), any(Principal.class));
   }

   // -------------------------------------------------------------------------
   // getConnectionStatus()
   // -------------------------------------------------------------------------

   // [connectionStatus] wraps the raw status string from service into ConnectionStatus
   @Test
   void getConnectionStatus_wrapsServiceResult() throws Exception {
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("p")
         .providerType(SecurityProviderType.FILE)
         .build();
      when(authenticationProviderService.testConnection(model)).thenReturn("OK");

      ConnectionStatus result = controller.getConnectionStatus(model);

      assertEquals("OK", result.getStatus());
   }

   // [connectionStatus: service throws] exception propagates to caller
   @Test
   void getConnectionStatus_serviceThrows_propagatesException() throws Exception {
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("p")
         .providerType(SecurityProviderType.LDAP)
         .build();
      when(authenticationProviderService.testConnection(model))
         .thenThrow(new RuntimeException("connection refused"));

      assertThrows(Exception.class, () -> controller.getConnectionStatus(model));
   }
}

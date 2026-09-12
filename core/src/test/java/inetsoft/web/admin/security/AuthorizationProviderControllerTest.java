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
 * Class type: thin orchestration controller — mirrors AuthenticationProviderController;
 * all logic is in AuthorizationProviderService.  In-controller code:
 *   1. removeAuthorizationProvider() — fetches chain, resolves provider name at index, delegates.
 *   2. All other endpoints           — pure pass-through delegation.
 *
 * Coverage scope:
 *   [remove: chain present]    chain Optional present → get name at index → delegates to service
 *   [remove: chain absent]     chain Optional.empty() → orElseThrow → Exception propagates
 *   [getProviders: delegation] delegates to service, returns result unchanged
 *   [add: delegation]          delegates with model + principal
 */

import inetsoft.sree.security.*;
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
class AuthorizationProviderControllerTest {

   @Mock private AuthorizationProviderService authorizationProviderService;
   @Mock private AuthorizationChain authorizationChain;
   @Mock private AuthorizationProvider authorizationProvider;
   @Mock private Principal principal;

   private AuthorizationProviderController controller;

   @BeforeEach
   void setUp() {
      controller = new AuthorizationProviderController(authorizationProviderService);
   }

   // -------------------------------------------------------------------------
   // getConfiguredAuthorizationProviders()
   // -------------------------------------------------------------------------

   @Test
   void getConfiguredAuthorizationProviders_delegatesToService() {
      SecurityProviderStatusList expected = SecurityProviderStatusList.builder().build();
      when(authorizationProviderService.getProviderListModel()).thenReturn(expected);

      SecurityProviderStatusList result = controller.getConfiguredAuthorizationProviders();

      assertSame(expected, result);
      verify(authorizationProviderService).getProviderListModel();
   }

   // -------------------------------------------------------------------------
   // addAuthorizationProvider()
   // -------------------------------------------------------------------------

   @Test
   void addAuthorizationProvider_delegatesToService() throws Exception {
      AuthorizationProviderModel model = AuthorizationProviderModel.builder()
         .providerName("myAuthzProvider")
         .providerType(SecurityProviderType.FILE)
         .build();

      controller.addAuthorizationProvider(model, principal);

      verify(authorizationProviderService)
         .addAuthorizationProvider(model, "myAuthzProvider", principal);
   }

   // -------------------------------------------------------------------------
   // removeAuthorizationProvider()
   // -------------------------------------------------------------------------

   // [remove: chain present] chain present → service called with name resolved from chain
   @Test
   void removeAuthorizationProvider_chainPresent_delegatesWithCorrectName() throws Exception {
      when(authorizationProviderService.getAuthorizationChain())
         .thenReturn(Optional.of(authorizationChain));
      when(authorizationChain.getProviders()).thenReturn(List.of(authorizationProvider));
      when(authorizationProvider.getProviderName()).thenReturn("fileAuthz");

      controller.removeAuthorizationProvider(0, principal);

      verify(authorizationProviderService).removeAuthorizationProvider(0, "fileAuthz", principal);
   }

   // [remove: chain absent] Optional.empty() → orElseThrow fires; service never called
   @Test
   void removeAuthorizationProvider_chainAbsent_throwsException() throws Exception {
      when(authorizationProviderService.getAuthorizationChain()).thenReturn(Optional.empty());

      assertThrows(Exception.class, () -> controller.removeAuthorizationProvider(0, principal));
      verify(authorizationProviderService, never())
         .removeAuthorizationProvider(anyInt(), anyString(), any(Principal.class));
   }
}

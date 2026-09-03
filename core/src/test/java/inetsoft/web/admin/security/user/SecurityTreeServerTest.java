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
package inetsoft.web.admin.security.user;

/*
 * Test strategy
 *
 * Class type: request-scoped orchestration service — SecurityTreeServer resolves the
 * requested authentication provider and then delegates the per-identity-type tree building
 * to UserTreeService.
 *
 * Coverage scope:
 *   [unknown provider name]  getProviderByName() returns null -> MessageException, not NPE
 *   [no default provider]    provider name omitted and the chain has none -> same guard
 *
 * Regression context: the EM Users tab sends whatever provider name the client has cached.
 * When that provider has been renamed or removed, getProviderByName() returns null and the
 * old code dereferenced it at provider.getOrganizationIDs(), producing a 500
 * "Cannot invoke ...getOrganizationIDs() because \"provider\" is null" with no usable message.
 *
 * Static singletons (SUtil, Catalog) are intercepted with Mockito.mockStatic() using lenient().
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.server.ServerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class SecurityTreeServerTest {
   @Mock private AuthenticationProviderService authenticationProviderService;
   @Mock private ServerService serverService;
   @Mock private SecurityEngine securityEngine;
   @Mock private UserTreeService userTreeService;
   @Mock private SecurityProvider securityProvider;
   @Mock private Catalog catalog;
   @Mock private Principal principal;

   private SecurityTreeServer server;

   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<Catalog> catalogStatic;

   @BeforeEach
   void setUp() {
      when(serverService.getLicenseInfos()).thenReturn(List.of());
      server = new SecurityTreeServer(
         authenticationProviderService, serverService, securityEngine, userTreeService);

      sutilStatic = mockStatic(SUtil.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());

      sutilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      // any(Object.class) rather than any(): it pins the getString(String, Object...) overload
      // instead of letting javac pick getString(String, boolean)
      lenient().when(catalog.getString(anyString(), any(Object.class)))
         .thenReturn("provider does not exist");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
   }

   @AfterEach
   void tearDown() {
      sutilStatic.close();
      catalogStatic.close();
   }

   // [Scenario: unknown provider name] the client asked for a provider that is no longer in
   // this node's authentication chain (renamed/removed, or a node that has not reloaded
   // authc-chain.json yet). Must report the missing provider instead of NPEing.
   @Test
   void getSecurityTree_unknownProviderName_throwsMessageExceptionNotNpe() {
      when(authenticationProviderService.getProviderByName("gone")).thenReturn(null);

      MessageException ex = assertThrows(MessageException.class,
         () -> server.getSecurityTree("gone", principal, false, false));

      assertEquals("provider does not exist", ex.getMessage());
      verify(catalog).getString("em.security.provider.notFound", "gone");
      verifyNoInteractions(userTreeService);
   }

   // [Scenario: no default provider] with the provider name omitted the chain's own
   // authentication provider is used; the same guard must cover a null there.
   @Test
   void getSecurityTree_noDefaultProvider_throwsMessageExceptionNotNpe() {
      when(securityProvider.getAuthenticationProvider()).thenReturn(null);

      assertThrows(MessageException.class,
         () -> server.getSecurityTree(null, principal, false, false));

      verifyNoInteractions(userTreeService);
   }
}

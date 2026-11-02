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
package inetsoft.sree.security;

import inetsoft.sree.security.ldap.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Bug #79234: SecurityEngine.getLdapAuthenticationProviderNames() reports the authentication
 * providers that are LDAP or Active Directory, which cannot be used with multi-tenancy.
 *
 *  [no chain]      security not initialized -> empty
 *  [empty chain]   -> empty
 *  [mixed chain]   File + Generic + AD -> the two LDAP names in chain order
 */
@Tag("core")
class SecurityEngineLdapProviderNamesTest {
   // real method on a mock: the accessor under test needs no Spring context or constructor
   private final SecurityEngine engine = mock(SecurityEngine.class, CALLS_REAL_METHODS);

   @Test
   void noChainGivesEmptyList() {
      doReturn(Optional.empty()).when(engine).getAuthenticationChain();

      assertEquals(List.of(), engine.getLdapAuthenticationProviderNames());
   }

   @Test
   void emptyChainGivesEmptyList() {
      useChain();

      assertEquals(List.of(), engine.getLdapAuthenticationProviderNames());
   }

   @Test
   void mixedChainGivesLdapAndAdNamesInOrder() {
      useChain(provider(FileAuthenticationProvider.class, "file"),
               provider(GenericLdapAuthenticationProvider.class, "corp-ldap"),
               provider(ADAuthenticationProvider.class, "corp-ad"));

      assertEquals(List.of("corp-ldap", "corp-ad"), engine.getLdapAuthenticationProviderNames());
   }

   private void useChain(AuthenticationProvider... providers) {
      AuthenticationChain chain = mock(AuthenticationChain.class);
      when(chain.getProviders()).thenReturn(List.of(providers));
      doReturn(Optional.of(chain)).when(engine).getAuthenticationChain();
   }

   private static AuthenticationProvider provider(Class<? extends AuthenticationProvider> type,
                                                  String name) {
      AuthenticationProvider provider = mock(type);
      when(provider.getProviderName()).thenReturn(name);
      return provider;
   }
}

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AuthenticationChain business scenarios:
 *  - the first provider containing a user owns authentication
 *  - later providers are consulted only when earlier providers do not contain the user
 *  - aggregate queries merge provider results and remove duplicates
 *  - provider exceptions are propagated instead of being swallowed
 *  - an empty chain denies authentication
 */
@Tag("core")
class AuthenticationChainTest {
   private static final IdentityID USER_ID = new IdentityID("alice", "org1");
   private static final User USER = new User(USER_ID);

   // [Scenario: first hit] the first provider containing the user authenticates the request
   @Test
   void authenticate_firstProviderContainsUser_returnsItsResult() {
      AuthenticationProvider first = mock(AuthenticationProvider.class);
      AuthenticationProvider second = mock(AuthenticationProvider.class);
      AuthenticationChain chain = chainOf(first, second);

      when(first.getUser(USER_ID)).thenReturn(USER);
      when(first.authenticate(USER_ID, "secret")).thenReturn(true);

      boolean authenticated = chain.authenticate(USER_ID, "secret");

      assertTrue(authenticated);
      verify(first).getUser(USER_ID);
      verify(first).authenticate(USER_ID, "secret");
      verify(second, never()).getUser(any());
      verify(second, never()).authenticate(any(), any());
   }

   // [Scenario: fallback] the chain skips providers that do not contain the user
   @Test
   void authenticate_firstProviderMissesUser_fallsBackToNextProvider() {
      AuthenticationProvider first = mock(AuthenticationProvider.class);
      AuthenticationProvider second = mock(AuthenticationProvider.class);
      AuthenticationChain chain = chainOf(first, second);

      when(first.getUser(USER_ID)).thenReturn(null);
      when(second.getUser(USER_ID)).thenReturn(USER);
      when(second.authenticate(USER_ID, "secret")).thenReturn(true);

      boolean authenticated = chain.authenticate(USER_ID, "secret");

      assertTrue(authenticated);
      verify(first).getUser(USER_ID);
      verify(first, never()).authenticate(any(), any());
      verify(second).getUser(USER_ID);
      verify(second).authenticate(USER_ID, "secret");
   }

   // [Scenario: first hit denied] the first provider containing the user can reject authentication
   @Test
   void authenticate_firstProviderContainsUserAndRejects_returnsFalse() {
      AuthenticationProvider first = mock(AuthenticationProvider.class);
      AuthenticationProvider second = mock(AuthenticationProvider.class);
      AuthenticationChain chain = chainOf(first, second);

      when(first.getUser(USER_ID)).thenReturn(USER);
      when(first.authenticate(USER_ID, "secret")).thenReturn(false);

      boolean authenticated = chain.authenticate(USER_ID, "secret");

      assertFalse(authenticated);
      verify(first).authenticate(USER_ID, "secret");
      verify(second, never()).getUser(any());
      verify(second, never()).authenticate(any(), any());
   }

   // [Scenario: union] getUsers merges provider results and removes duplicates
   @Test
   void getUsers_multipleProviders_mergesDistinctResults() {
      AuthenticationProvider first = mock(AuthenticationProvider.class);
      AuthenticationProvider second = mock(AuthenticationProvider.class);
      AuthenticationChain chain = chainOf(first, second);
      IdentityID bob = new IdentityID("bob", "org1");

      when(first.getUsers()).thenReturn(new IdentityID[] { USER_ID, bob });
      when(second.getUsers()).thenReturn(new IdentityID[] { bob, USER_ID });

      IdentityID[] users = chain.getUsers();

      assertEquals(Set.of(USER_ID, bob), new HashSet<>(Arrays.asList(users)));
      assertEquals(2, users.length);
   }

   // [Scenario: exception] an exception from the selected provider is propagated
   @Test
   void authenticate_selectedProviderThrows_propagatesException() {
      AuthenticationProvider first = mock(AuthenticationProvider.class);
      AuthenticationProvider second = mock(AuthenticationProvider.class);
      AuthenticationChain chain = chainOf(first, second);
      RuntimeException expected = new RuntimeException("authentication failed");

      when(first.getUser(USER_ID)).thenReturn(USER);
      when(first.authenticate(USER_ID, "secret")).thenThrow(expected);

      RuntimeException actual =
         assertThrows(RuntimeException.class, () -> chain.authenticate(USER_ID, "secret"));

      assertSame(expected, actual);
      verify(second, never()).getUser(any());
      verify(second, never()).authenticate(any(), any());
   }

   // [Scenario: empty chain] authentication is denied when no provider exists
   @Test
   void authenticate_emptyChain_returnsFalse() {
      AuthenticationChain chain = chainOf();

      assertFalse(chain.authenticate(USER_ID, "secret"));
   }

   private static AuthenticationChain chainOf(AuthenticationProvider... providers) {
      AuthenticationChain chain = new TestAuthenticationChain();
      chain.getProviderList().addAll(List.of(providers));
      return chain;
   }

   private static final class TestAuthenticationChain extends AuthenticationChain {
      @Override
      void initialize() {
         // Keep the test isolated from DataSpace and Spring-managed lifecycle state.
      }
   }
}

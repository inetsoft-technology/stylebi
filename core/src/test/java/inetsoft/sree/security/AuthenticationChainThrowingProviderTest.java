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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79268: a provider whose getUser throws must not hide users owned by other providers, but
 * a failure must never be reported as "user not found".
 */
@Tag("core")
class AuthenticationChainThrowingProviderTest {
   private static final IdentityID ADMIN = new IdentityID("admin", "host-org");
   private static final IdentityID PRV = new IdentityID("prv-basic", "host-org");
   private static final IdentityID NOBODY = new IdentityID("nobody", "host-org");
   private static final User ADMIN_USER = new User(ADMIN);
   private static final User PRV_USER = new User(PRV);

   @Test
   void getUserReturnsUserOwnedByProviderAfterThrowingProvider() {
      AuthenticationProvider owner = provider();
      AuthenticationChain chain = chainOf(throwing(), owner);
      when(owner.getUser(PRV)).thenReturn(PRV_USER);

      assertSame(PRV_USER, chain.getUser(PRV));
   }

   // guard against reporting a failure as "absent"
   @Test
   void getUserOfUnknownIdentityThrowsWhenAProviderFails() {
      AuthenticationChain chain = chainOf(provider(), throwing(), provider());

      assertThrows(RuntimeException.class, () -> chain.getUser(NOBODY));
   }

   @Test
   void getUserDoesNotAskThrowingProviderAfterAnEarlierProviderOwnsTheUser() {
      AuthenticationProvider first = provider();
      AuthenticationProvider throwing = throwing();
      AuthenticationChain chain = chainOf(first, throwing);
      when(first.getUser(ADMIN)).thenReturn(ADMIN_USER);

      assertSame(ADMIN_USER, chain.getUser(ADMIN));
      verify(throwing, never()).getUser(any());
   }

   // the owner itself fails and a later provider does not own the name: still a failure
   @Test
   void getUserThrowsWhenTheOwnerFailsAndLaterProviderDoesNotOwnTheUser() {
      AuthenticationChain chain = chainOf(throwing(), provider());

      assertThrows(RuntimeException.class, () -> chain.getUser(PRV));
   }

   // documents the accepted caveat: a later provider owning the same identity answers
   @Test
   void getUserReturnsLaterProvidersUserWithTheSameIdentityWhenEarlierThrows() {
      AuthenticationProvider later = provider();
      AuthenticationChain chain = chainOf(throwing(), later);
      when(later.getUser(PRV)).thenReturn(PRV_USER);

      assertSame(PRV_USER, chain.getUser(PRV));
   }

   @Test
   void authenticateUsesOwnerAfterThrowingProvider() {
      AuthenticationProvider owner = provider();
      AuthenticationChain chain = chainOf(throwing(), owner);
      when(owner.getUser(PRV)).thenReturn(PRV_USER);
      when(owner.authenticate(PRV, "pw")).thenReturn(true);

      assertTrue(chain.authenticate(PRV, "pw"));
      assertFalse(chain.authenticate(NOBODY, "pw"));
   }

   @Test
   void findUserOwnerSkipsThrowingProvider() {
      AuthenticationProvider owner = provider();
      AuthenticationChain chain = chainOf(throwing(), owner);
      when(owner.getUser(PRV)).thenReturn(PRV_USER);

      AuthenticationChain.UserOwner found = chain.findUserOwner(PRV);

      assertNotNull(found);
      assertSame(owner, found.provider());
      assertSame(PRV_USER, found.user());
      assertNull(chain.findUserOwner(NOBODY));
   }

   // two providers failing with the identical instance must not replace the original failure
   @Test
   void getUserRethrowsOriginalFailureWhenProvidersThrowTheSameInstance() {
      RuntimeException shared = new RuntimeException("shared failure");
      AuthenticationProvider first = provider();
      AuthenticationProvider second = provider();
      when(first.getUser(any())).thenThrow(shared);
      when(second.getUser(any())).thenThrow(shared);
      AuthenticationChain chain = chainOf(first, second);

      RuntimeException thrown = assertThrows(RuntimeException.class, () -> chain.getUser(PRV));

      assertSame(shared, thrown);
   }

   @Test
   void skippedProviderWithoutNameDoesNotFailTheLookup() {
      AuthenticationProvider unnamed = throwing();
      AuthenticationProvider owner = provider();
      when(unnamed.getProviderName()).thenReturn(null);
      when(owner.getUser(PRV)).thenReturn(PRV_USER);
      AuthenticationChain chain = chainOf(unnamed, owner);

      assertSame(PRV_USER, chain.getUser(PRV));
   }

   private static AuthenticationProvider provider() {
      AuthenticationProvider p = mock(AuthenticationProvider.class);
      when(p.getProviderName()).thenReturn("p" + System.identityHashCode(p));
      return p;
   }

   private static AuthenticationProvider throwing() {
      AuthenticationProvider p = provider();
      when(p.getUser(any())).thenThrow(new RuntimeException("provider down"));
      return p;
   }

   private static AuthenticationChain chainOf(AuthenticationProvider... providers) {
      AuthenticationChain chain = new AuthenticationChain() {
         @Override
         void initialize() {
         }
      };
      chain.getProviderList().addAll(List.of(providers));
      return chain;
   }
}

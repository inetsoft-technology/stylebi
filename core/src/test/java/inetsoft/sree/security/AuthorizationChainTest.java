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

/*
 * AuthorizationChain scenario table
 *
 * [Lookup: resource-hit]        first provider misses, later provider has permission      -> return first non-null permission
 * [Lookup: resource-all-null]   every provider returns null for resource                  -> return null
 * [Lookup: identity-hit]        first provider misses, later provider has permission      -> return first non-null permission
 * [Lookup: identity-all-null]   every provider returns null for identity                  -> return null
 * [Set: resource-first-hit]     first provider already owns the permission                -> update first provider only, never read second
 * [Set: resource-existing]      matching permission exists in provider N (N > 0)          -> update provider N only
 * [Set: resource-new]           no provider has the permission                            -> write to first provider
 * [Set: resource-empty-chain]   no providers configured                                   -> throws IndexOutOfBoundsException
 * [Set: identity-new]           no provider has the identity permission                   -> write to first provider
 * [Set: identity-empty-chain]   no providers configured                                   -> throws IndexOutOfBoundsException
 * [Remove: broadcast]           multiple providers configured                             -> remove propagated to all providers
 * [List: fallback]              earlier provider throws UnsupportedOperationException     -> use first later provider that supports listing
 * [List: none-supported]        every provider throws UnsupportedOperationException       -> throw UnsupportedOperationException
 * [Aggregate: any-true]         support/cache/loading mixed across providers              -> true if any provider returns true
 * [Aggregate: all-false]        all providers return false for supportGroupPermission     -> return false
 * [Aggregate: max-age]          cache ages differ across providers                        -> return max cache age
 * [Lifecycle: teardown]         multiple providers configured                             -> tearDown delegated to all providers
 * [Event: auth-change]          multiple providers configured                             -> authenticationChanged propagated to all providers
 * [OrgClean: delegate]          mixed provider types in chain                             -> only FileAuthorizationProvider instances receive the call
 *
 * Intent vs implementation suspects
 *
 * [Suspect 1] setPermission(type, identityID, perm, orgID) on a contentInConfig provider
 *             intent: persist in-memory provider changes into the chain configuration, same as the String overload
 *             actual: updates the provider but never calls saveConfiguration()
 * [Suspect 2] removePermission(...) on a contentInConfig provider
 *             intent: persist removal from configuration-backed providers
 *             actual: removes from providers in memory but never calls saveConfiguration()
 */

import inetsoft.uql.util.Identity;
import inetsoft.util.Tuple4;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AuthorizationChainTest {
   private static final String ORG_A = "orgA";

   // [Lookup: resource-hit] first non-null permission in provider order wins
   @Test
   void getPermission_resourceReturnsFirstNonNullPermission() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      Permission permission = new Permission();
      TestAuthorizationChain chain = newChain(first, second);

      when(first.getPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A)).thenReturn(null);
      when(second.getPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A)).thenReturn(permission);

      assertSame(permission, chain.getPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A));
   }

   // [Lookup: resource-all-null] every provider returns null -> chain propagates null to caller
   @Test
   void getPermission_resourceAllProvidersReturnNullYieldsNull() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      TestAuthorizationChain chain = newChain(first, second);

      // Mockito default for object return types is null, so no stubbing needed
      assertNull(chain.getPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A));
   }

   // [Lookup: identity-hit] first non-null identity permission in provider order wins
   @Test
   void getPermission_identityReturnsFirstNonNullPermission() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      Permission permission = new Permission();
      IdentityID identity = new IdentityID("alice", ORG_A);
      TestAuthorizationChain chain = newChain(first, second);

      when(first.getPermission(ResourceType.SECURITY_USER, identity, ORG_A)).thenReturn(null);
      when(second.getPermission(ResourceType.SECURITY_USER, identity, ORG_A)).thenReturn(permission);

      assertSame(permission, chain.getPermission(ResourceType.SECURITY_USER, identity, ORG_A));
   }

   // [Lookup: identity-all-null] every provider returns null -> chain propagates null to caller
   @Test
   void getPermission_identityAllProvidersReturnNullYieldsNull() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      IdentityID identity = new IdentityID("alice", ORG_A);
      TestAuthorizationChain chain = newChain(first, second);

      assertNull(chain.getPermission(ResourceType.SECURITY_USER, identity, ORG_A));
   }

   // [Set: resource-first-hit] first provider already owns the permission -> update first, never read or write second
   @Test
   void setPermission_resourceExistingInFirstProviderUpdatesFirstProviderOnly() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      Permission existing = new Permission();
      Permission updated = new Permission();
      TestAuthorizationChain chain = newChain(first, second);

      when(first.getPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A)).thenReturn(existing);
      when(first.contentInConfig()).thenReturn(false);

      chain.setPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", updated, ORG_A);

      verify(first).setPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", updated, ORG_A);
      verify(second, never()).setPermission(any(), anyString(), any(), anyString());
      verify(second, never()).getPermission(any(ResourceType.class), anyString(), anyString());
   }

   // [Set: resource-existing] existing permission is updated on the provider that already owns it
   @Test
   void setPermission_resourceExistingPermissionUpdatesOwningProviderAndSavesConfiguration()
      throws Exception
   {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      Permission existing = new Permission();
      Permission updated = new Permission();
      TestAuthorizationChain chain = newChain(first, second);

      when(first.getPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A)).thenReturn(null);
      when(second.getPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A)).thenReturn(existing);
      when(second.contentInConfig()).thenReturn(true);

      chain.setPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", updated, ORG_A);

      verify(first, never()).setPermission(any(), anyString(), any(), anyString());
      verify(second).setPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", updated, ORG_A);
      assertEquals(1, chain.saveCalls);
   }

   // [Set: resource-new] missing permission is written to the first provider in the chain
   @Test
   void setPermission_resourceMissingEverywhereWritesToFirstProvider() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      Permission updated = new Permission();
      TestAuthorizationChain chain = newChain(first, second);

      when(first.getPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A)).thenReturn(null);
      when(second.getPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A)).thenReturn(null);
      when(first.contentInConfig()).thenReturn(false);

      chain.setPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", updated, ORG_A);

      verify(first).setPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", updated, ORG_A);
      verify(second, never()).setPermission(any(), anyString(), any(), anyString());
      assertEquals(0, chain.saveCalls);
   }

   // [Set: resource-empty-chain] empty chains fail when asked to create a new permission
   @Test
   void setPermission_resourceWithNoProvidersThrowsIndexOutOfBoundsException() {
      TestAuthorizationChain chain = newChain();

      assertThrows(IndexOutOfBoundsException.class,
                   () -> chain.setPermission(ResourceType.VIEWSHEET, "/viewsheets/sales",
                                             new Permission(), ORG_A));
   }

   // [Set: resource-save-failure] saveConfiguration I/O failures are wrapped in RuntimeException
   @Test
   void setPermission_resourceSaveFailureWrapsIOException() {
      AuthorizationProvider provider = mockProvider();
      Permission existing = new Permission();
      Permission updated = new Permission();
      TestAuthorizationChain chain = newChain(provider);
      IOException failure = new IOException("disk full");

      when(provider.getPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A)).thenReturn(existing);
      when(provider.contentInConfig()).thenReturn(true);
      chain.saveFailure = failure;

      RuntimeException thrown = assertThrows(RuntimeException.class,
         () -> chain.setPermission(ResourceType.VIEWSHEET, "/viewsheets/sales", updated, ORG_A));

      assertSame(failure, thrown.getCause());
   }

   // [Set: identity-new] no provider has the identity permission -> write to first provider
   @Test
   void setPermission_identityMissingEverywhereWritesToFirstProvider() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      IdentityID identity = new IdentityID("alice", ORG_A);
      Permission updated = new Permission();
      TestAuthorizationChain chain = newChain(first, second);

      // both providers return null by default (Mockito default for object return types)
      chain.setPermission(ResourceType.SECURITY_USER, identity, updated, ORG_A);

      verify(first).setPermission(ResourceType.SECURITY_USER, identity, updated, ORG_A);
      verify(second, never()).setPermission(any(), any(IdentityID.class), any(), anyString());
   }

   // [Set: identity-empty-chain] empty chain fails when asked to create a new identity permission
   @Test
   void setPermission_identityWithNoProvidersThrowsIndexOutOfBoundsException() {
      TestAuthorizationChain chain = newChain();
      IdentityID identity = new IdentityID("alice", ORG_A);

      assertThrows(IndexOutOfBoundsException.class,
                   () -> chain.setPermission(ResourceType.SECURITY_USER, identity,
                                             new Permission(), ORG_A));
   }

   // [Remove: broadcast] resource removals are propagated to every provider in the chain
   @Test
   void removePermission_resourceBroadcastsToAllProviders() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      TestAuthorizationChain chain = newChain(first, second);

      chain.removePermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A);

      verify(first).removePermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A);
      verify(second).removePermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A);
   }

   // [Remove: broadcast] identity removals are propagated to every provider in the chain
   @Test
   void removePermission_identityBroadcastsToAllProviders() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      IdentityID identity = new IdentityID("alice", ORG_A);
      TestAuthorizationChain chain = newChain(first, second);

      chain.removePermission(ResourceType.SECURITY_USER, identity, ORG_A);

      verify(first).removePermission(ResourceType.SECURITY_USER, identity, ORG_A);
      verify(second).removePermission(ResourceType.SECURITY_USER, identity, ORG_A);
   }

   // [List: fallback] providers that do not support listing are skipped until one succeeds
   @Test
   void getPermissions_skipsUnsupportedProvidersAndReturnsFirstSupportedList() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      List<Tuple4<ResourceType, String, String, Permission>> permissions =
         List.of(new Tuple4<>(ResourceType.VIEWSHEET, ORG_A, "/viewsheets/sales", new Permission()));
      TestAuthorizationChain chain = newChain(first, second);

      when(first.getPermissions()).thenThrow(new UnsupportedOperationException());
      when(second.getPermissions()).thenReturn(permissions);

      assertSame(permissions, chain.getPermissions());
   }

   // [List: none-supported] if no provider supports listing, the chain also rejects listing
   @Test
   void getPermissions_allProvidersUnsupportedThrowsUnsupportedOperationException() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      TestAuthorizationChain chain = newChain(first, second);

      when(first.getPermissions()).thenThrow(new UnsupportedOperationException());
      when(second.getPermissions()).thenThrow(new UnsupportedOperationException());

      assertThrows(UnsupportedOperationException.class, chain::getPermissions);
   }

   // [Aggregate: any-true] supportGroupPermission returns true when any provider supports it
   @Test
   void supportGroupPermission_returnsTrueWhenAnyProviderSupportsIt() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      TestAuthorizationChain chain = newChain(first, second);

      when(first.supportGroupPermission()).thenReturn(false);
      when(second.supportGroupPermission()).thenReturn(true);

      assertTrue(chain.supportGroupPermission());
   }

   // [Aggregate: all-false] supportGroupPermission returns false when no provider supports it
   @Test
   void supportGroupPermission_returnsFalseWhenNoProviderSupportsIt() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      TestAuthorizationChain chain = newChain(first, second);

      when(first.supportGroupPermission()).thenReturn(false);
      when(second.supportGroupPermission()).thenReturn(false);

      assertFalse(chain.supportGroupPermission());
   }

   // [Aggregate: any-true] cache flags are aggregated with anyMatch semantics and max-age semantics
   @Test
   void cacheStateMethodsAggregateAcrossProviders() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      TestAuthorizationChain chain = newChain(first, second);

      when(first.isCacheEnabled()).thenReturn(false);
      when(second.isCacheEnabled()).thenReturn(true);
      when(first.isLoading()).thenReturn(false);
      when(second.isLoading()).thenReturn(true);
      when(first.getCacheAge()).thenReturn(100L);
      when(second.getCacheAge()).thenReturn(250L);

      assertTrue(chain.isCacheEnabled());
      assertTrue(chain.isLoading());
      assertEquals(250L, chain.getCacheAge());
   }

   // [Aggregate: broadcast] clearCache is delegated to every provider
   @Test
   void clearCache_broadcastsToAllProviders() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      TestAuthorizationChain chain = newChain(first, second);

      chain.clearCache();

      verify(first).clearCache();
      verify(second).clearCache();
   }

   // [Lifecycle: teardown] chain teardown disposes every provider via provider.tearDown()
   @Test
   void tearDown_disposesAllProviders() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      TestAuthorizationChain chain = newChain(first, second);

      chain.tearDown();

      verify(first).tearDown();
      verify(second).tearDown();
   }

   // [Event: auth-change] authentication changes are propagated to every provider
   @Test
   void authenticationChanged_broadcastsToAllProviders() {
      AuthorizationProvider first = mockProvider();
      AuthorizationProvider second = mockProvider();
      AuthenticationChangeEvent event = new AuthenticationChangeEvent(
         this, new IdentityID("alice", ORG_A), new IdentityID("alice2", ORG_A),
         ORG_A, ORG_A, Identity.USER, false);
      TestAuthorizationChain chain = newChain(first, second);

      chain.authenticationChanged(event);

      verify(first).authenticationChanged(event);
      verify(second).authenticationChanged(event);
   }

   // [OrgClean: delegate] only FileAuthorizationProvider instances in the chain receive the call;
   //                      plain AuthorizationProvider mocks are skipped
   @Test
   void cleanOrganizationFromPermissions_delegatesToFileAuthorizationProvidersOnly() {
      FileAuthorizationProvider fileProvider = mock(FileAuthorizationProvider.class);
      AuthorizationProvider otherProvider = mockProvider();
      TestAuthorizationChain chain = newChain(fileProvider, otherProvider);

      chain.cleanOrganizationFromPermissions(ORG_A);

      verify(fileProvider).cleanOrganizationFromPermissions(ORG_A);
      // otherProvider is not a FileAuthorizationProvider, so it must not receive the call
      verifyNoInteractions(otherProvider);
   }

   // [Suspect 1] contentInConfig-backed identity updates should persist chain configuration too
   // BROKEN: the IdentityID overload mutates the provider but never calls saveConfiguration().
   // Bug #74662
   @Test
   @Disabled("Bug: setPermission(ResourceType, IdentityID, Permission, String) never saves contentInConfig providers")
   void setPermission_identityOnConfigBackedProvider_savesConfiguration() {
      AuthorizationProvider provider = mockProvider();
      IdentityID identity = new IdentityID("alice", ORG_A);
      Permission existing = new Permission();
      Permission updated = new Permission();
      TestAuthorizationChain chain = newChain(provider);

      when(provider.getPermission(ResourceType.SECURITY_USER, identity, ORG_A)).thenReturn(existing);
      when(provider.contentInConfig()).thenReturn(true);

      chain.setPermission(ResourceType.SECURITY_USER, identity, updated, ORG_A);

      assertEquals(1, chain.saveCalls);
   }

   // [Suspect 2] contentInConfig-backed removals should persist chain configuration too
   // BROKEN: removePermission only delegates to providers and never calls saveConfiguration().
   // Bug #74662
   @Test
   @Disabled("Bug: removePermission does not save configuration for contentInConfig providers")
   void removePermission_onConfigBackedProvider_savesConfiguration() {
      AuthorizationProvider provider = mockProvider();
      TestAuthorizationChain chain = newChain(provider);

      when(provider.contentInConfig()).thenReturn(true);

      chain.removePermission(ResourceType.VIEWSHEET, "/viewsheets/sales", ORG_A);

      assertEquals(1, chain.saveCalls);
   }

   private static AuthorizationProvider mockProvider() {
      return mock(AuthorizationProvider.class);
   }

   private static TestAuthorizationChain newChain(AuthorizationProvider... providers) {
      TestAuthorizationChain chain = new TestAuthorizationChain();
      chain.setProviderList(List.of(providers));
      return chain;
   }

   private static final class TestAuthorizationChain extends AuthorizationChain {
      TestAuthorizationChain() {
         super(true);
      }

      @Override
      void initialize() {
         // skip DataSpace/Spring lookup — tests inject providers directly via setProviderList()
      }

      @Override
      void dispose() {
         // skip DataSpace change-listener removal — no listener registered in tests
         clear();
      }

      @Override
      public void saveConfiguration() throws IOException {
         saveCalls++;

         if(saveFailure != null) {
            throw saveFailure;
         }
      }

      private int saveCalls;
      private IOException saveFailure;
   }
}

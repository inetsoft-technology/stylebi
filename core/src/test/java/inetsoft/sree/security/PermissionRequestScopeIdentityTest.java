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
package inetsoft.sree.security;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.AfterEach;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The identity lookups a permission check repeats for every entry of a listing (chain
 * getUser/getOrganization, principal session properties) are done once per listing inside a
 * PermissionRequestScope, independent of the number of entries, and exactly as before without one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class PermissionRequestScopeIdentityTest {
   private static final IdentityID USER_ID = new IdentityID("alice", "org1");
   private static final String SESSION = "session-1";

   @AfterEach
   void clear() {
      PermissionRequestScope.clear();
   }

   // ---- AuthenticationChain

   @Test
   void chain_userAndOrganizationLookupsAreIndependentOfEntryCount() {
      for(int entries : new int[] { 10, 200 }) {
         AuthenticationProvider provider = mock(AuthenticationProvider.class);
         when(provider.getUser(USER_ID)).thenReturn(new User(USER_ID));
         when(provider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
         AuthenticationChain chain = chainOf(provider);

         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            for(int i = 0; i < entries; i++) {
               assertNotNull(chain.getUser(USER_ID));
               assertNotNull(chain.getOrganization("org1"));
            }
         }

         verify(provider, times(1)).getUser(USER_ID);
         verify(provider, times(1)).getOrganization("org1");
      }
   }

   @Test
   void chain_withoutScopeCountsGrowWithEntries() {
      for(int entries : new int[] { 10, 200 }) {
         AuthenticationProvider provider = mock(AuthenticationProvider.class);
         when(provider.getUser(USER_ID)).thenReturn(new User(USER_ID));
         when(provider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
         AuthenticationChain chain = chainOf(provider);

         for(int i = 0; i < entries; i++) {
            chain.getUser(USER_ID);
            chain.getOrganization("org1");
         }

         verify(provider, times(entries)).getUser(USER_ID);
         verify(provider, times(entries)).getOrganization("org1");
      }
   }

   @Test
   void chain_missingUserIsMemoizedAndFallsThroughAllProviders() {
      AuthenticationProvider first = mock(AuthenticationProvider.class);
      AuthenticationProvider second = mock(AuthenticationProvider.class);
      AuthenticationChain chain = chainOf(first, second);

      try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
         for(int i = 0; i < 20; i++) {
            assertNull(chain.getUser(USER_ID));
            assertNull(chain.getOrganization("none"));
         }
      }

      verify(first, times(1)).getUser(USER_ID);
      verify(second, times(1)).getUser(USER_ID);
      verify(first, times(1)).getOrganization("none");
      verify(second, times(1)).getOrganization("none");
   }

   @Test
   void chain_providerFailureIsNotMemoized() {
      AuthenticationProvider provider = mock(AuthenticationProvider.class);
      when(provider.getUser(USER_ID)).thenThrow(new IllegalStateException("down"))
         .thenReturn(new User(USER_ID));
      AuthenticationChain chain = chainOf(provider);

      try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
         assertThrows(IllegalStateException.class, () -> chain.getUser(USER_ID));
         assertNotNull(chain.getUser(USER_ID));
      }
   }

   @Test
   void chain_differentChainsDoNotShareAnswers() {
      AuthenticationProvider p1 = mock(AuthenticationProvider.class);
      AuthenticationProvider p2 = mock(AuthenticationProvider.class);
      User u1 = new User(USER_ID);
      User u2 = new User(USER_ID);
      when(p1.getUser(USER_ID)).thenReturn(u1);
      when(p2.getUser(USER_ID)).thenReturn(u2);
      AuthenticationChain c1 = chainOf(p1);
      AuthenticationChain c2 = chainOf(p2);

      try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
         assertSame(u1, c1.getUser(USER_ID));
         assertSame(u2, c2.getUser(USER_ID));
         assertSame(u1, c1.getUser(USER_ID));
      }
   }

   // ---- DestinationUserNameProviderPrincipal.getProperty

   @Test
   void property_sessionReadsAreIndependentOfEntryCount() {
      for(int entries : new int[] { 10, 200 }) {
         SessionFixture fixture = new SessionFixture();

         try(MockedStatic<IgniteSessionRepository> repo = fixture.mockRepository()) {
            DestinationUserNameProviderPrincipal principal = fixture.principal();
            fixture.resetCounts();

            try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
               for(int i = 0; i < entries; i++) {
                  assertEquals("true", principal.getProperty("virtual"));
                  assertEquals("group", principal.getProperty("identity_type"));
               }
            }

            assertEquals(2, fixture.mapFetches.get());
            assertEquals(2, fixture.gets.get());
         }
      }
   }

   @Test
   void property_withoutScopeEveryReadHitsSession() {
      SessionFixture fixture = new SessionFixture();

      try(MockedStatic<IgniteSessionRepository> repo = fixture.mockRepository()) {
         DestinationUserNameProviderPrincipal principal = fixture.principal();
         fixture.resetCounts();

         for(int i = 0; i < 10; i++) {
            principal.getProperty("virtual");
         }

         assertEquals(10, fixture.mapFetches.get());
         assertEquals(10, fixture.gets.get());
      }
   }

   @Test
   void property_setPropertyInvalidatesThatPrincipalOnly() {
      SessionFixture fixture = new SessionFixture();

      try(MockedStatic<IgniteSessionRepository> repo = fixture.mockRepository()) {
         DestinationUserNameProviderPrincipal principal = fixture.principal();
         DestinationUserNameProviderPrincipal other = fixture.principal();

         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            assertEquals("true", principal.getProperty("virtual"));
            assertEquals("true", other.getProperty("virtual"));

            principal.setProperty("virtual", "false");
            assertEquals("false", principal.getProperty("virtual"));

            // the shared session was rewritten, but the other instance keeps its snapshot
            assertEquals("true", other.getProperty("virtual"));
         }
      }
   }

   @Test
   void property_samePropertyNameOnTwoInstancesIsNotShared() {
      SessionFixture a = new SessionFixture();
      SessionFixture b = new SessionFixture();

      try(MockedStatic<IgniteSessionRepository> repo = Mockito.mockStatic(
         IgniteSessionRepository.class))
      {
         repo.when(() -> IgniteSessionRepository.getSessionAttributeMap("a"))
            .thenAnswer(inv -> a.proxy);
         repo.when(() -> IgniteSessionRepository.getSessionAttributeMap("b"))
            .thenAnswer(inv -> b.proxy);
         DestinationUserNameProviderPrincipal pa = new DestinationUserNameProviderPrincipal();
         pa.setHttpSessionId("a");
         DestinationUserNameProviderPrincipal pb = new DestinationUserNameProviderPrincipal();
         pb.setHttpSessionId("b");
         pa.setProperty("virtual", "true");
         pb.setProperty("virtual", "other");

         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            assertEquals("true", pa.getProperty("virtual"));
            assertEquals("other", pb.getProperty("virtual"));
         }
      }
   }

   @Test
   void property_earlyReturnsAreUnchanged() {
      SessionFixture fixture = new SessionFixture();

      try(MockedStatic<IgniteSessionRepository> repo = fixture.mockRepository()) {
         DestinationUserNameProviderPrincipal principal = fixture.principal();
         principal.setProperty(SUtil.EM_USER, "true");
         fixture.resetCounts();

         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            for(int i = 0; i < 5; i++) {
               assertEquals("true", principal.getProperty(SUtil.EM_USER));
            }
         }

         assertEquals(0, fixture.mapFetches.get());
      }

      SessionFixture plain = new SessionFixture();

      try(MockedStatic<IgniteSessionRepository> repo = plain.mockRepository()) {
         DestinationUserNameProviderPrincipal principal = plain.principal();
         principal.setProperty("curr_org_id", "org9");
         plain.resetCounts();

         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            assertNull(principal.getProperty("curr_org_id"));
            assertNull(principal.getProperty("curr_org_id"));
         }

         assertEquals(0, plain.mapFetches.get());
      }
   }

   @Test
   void property_unboundPrincipalReadsLocalValueEveryTime() {
      DestinationUserNameProviderPrincipal principal = new DestinationUserNameProviderPrincipal();
      principal.setProperty("k", "v1");

      try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
         assertEquals("v1", principal.getProperty("k"));
         principal.setProperty("k", "v2");
         assertEquals("v2", principal.getProperty("k"));
      }
   }

   @Test
   void property_missingValueIsMemoized() {
      SessionFixture fixture = new SessionFixture();

      try(MockedStatic<IgniteSessionRepository> repo = fixture.mockRepository()) {
         DestinationUserNameProviderPrincipal principal = fixture.principal();
         fixture.resetCounts();

         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            for(int i = 0; i < 20; i++) {
               assertNull(principal.getProperty("absent"));
            }
         }

         assertEquals(1, fixture.mapFetches.get());
      }
   }

   @Test
   void property_rolesGroupsAndOrgIdAreNotPropertyReads() {
      SessionFixture fixture = new SessionFixture();

      try(MockedStatic<IgniteSessionRepository> repo = fixture.mockRepository()) {
         DestinationUserNameProviderPrincipal principal = fixture.principal();

         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            assertEquals("true", principal.getProperty("virtual"));
            principal.setRoles(new IdentityID[] { new IdentityID("r", "org1") });
            principal.setGroups(new String[] { "g" });
            principal.setOrgId("org2");
            fixture.resetCounts();

            assertEquals("true", principal.getProperty("virtual"));
            assertEquals(0, fixture.mapFetches.get());
         }
      }
   }

   @Test
   void property_bindingASessionDropsTheSnapshot() {
      SessionFixture fixture = new SessionFixture();

      try(MockedStatic<IgniteSessionRepository> repo = fixture.mockRepository()) {
         DestinationUserNameProviderPrincipal principal = new DestinationUserNameProviderPrincipal();

         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            principal.setProperty("virtual", "local");
            assertEquals("local", principal.getProperty("virtual"));
            fixture.session.put("DestinationUserNameProviderPrincipal.PROP.virtual", "bound");
            principal.bindHttpSession(SESSION);
            assertEquals("local", principal.getProperty("virtual"));
         }
      }
   }

   // ---- helpers

   private static AuthenticationChain chainOf(AuthenticationProvider... providers) {
      AuthenticationChain chain = new AuthenticationChain() {
         @Override
         void initialize() {
         }
      };
      chain.getProviderList().addAll(List.of(providers));
      return chain;
   }

   /**
    * A session attribute map backed by a HashMap that counts how many times the map is fetched
    * from the repository (two Ignite reads each) and how many values are read from it.
    */
   private static final class SessionFixture {
      private final Map<String, Object> session = new HashMap<>();
      private final AtomicInteger mapFetches = new AtomicInteger();
      private final AtomicInteger gets = new AtomicInteger();
      @SuppressWarnings("unchecked")
      private final DistributedMap<String, Object> proxy = (DistributedMap<String, Object>)
         Proxy.newProxyInstance(
            DistributedMap.class.getClassLoader(), new Class<?>[] { DistributedMap.class },
            (p, method, args) -> {
               if("get".equals(method.getName())) {
                  gets.incrementAndGet();
               }

               return method.invoke(session, args);
            });

      private MockedStatic<IgniteSessionRepository> mockRepository() {
         MockedStatic<IgniteSessionRepository> repo =
            Mockito.mockStatic(IgniteSessionRepository.class);
         repo.when(() -> IgniteSessionRepository.getSessionAttributeMap(anyString()))
            .thenAnswer(inv -> {
               mapFetches.incrementAndGet();
               return proxy;
            });
         return repo;
      }

      private DestinationUserNameProviderPrincipal principal() {
         DestinationUserNameProviderPrincipal principal = new DestinationUserNameProviderPrincipal();
         principal.setHttpSessionId(SESSION);
         principal.setProperty("virtual", "true");
         principal.setProperty("identity_type", "group");
         return principal;
      }

      private void resetCounts() {
         mapFetches.set(0);
         gets.set(0);
      }
   }
}

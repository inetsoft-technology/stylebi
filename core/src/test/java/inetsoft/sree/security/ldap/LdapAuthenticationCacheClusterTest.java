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

package inetsoft.sree.security.ldap;

import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.sree.internal.cluster.ignite.IgniteClusterTestUtils;
import inetsoft.test.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79077, the LDAP authentication cache could not load in a cluster. Ignite refuses the
 * no-argument removeAll() of a cache inside a transaction, and loadInternal() cleared its maps
 * that way inside Cluster.startTx(), so every load failed, the cache never became initialized and
 * every request that reached the provider started another full directory load. MockCluster does
 * not refuse the call, so this runs the real LdapAuthenticationCache on a real Ignite node, with
 * a mocked directory client.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, LdapAuthenticationCacheClusterTest.TestConfiguration.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class LdapAuthenticationCacheClusterTest {
   @BeforeAll
   static void startNode() throws Exception {
      clusterDir = Files.createTempDirectory("cluster-79077");
      node = IgniteClusterTestUtils.getIgniteCluster(
         "c79077-" + UUID.randomUUID(), new TcpDiscoveryVmIpFinder(true), clusterDir);
   }

   @AfterAll
   static void stopNode() throws Exception {
      try {
         if(node != null) {
            node.close();
         }
      }
      finally {
         node = null;
      }
   }

   @Test
   void loadInsideClusterTransactionInitializesTheCache() throws Exception {
      LdapAuthenticationClient client = mock(LdapAuthenticationClient.class);
      when(client.getIndividualUsers()).thenReturn(new String[] { "a" });
      when(client.getIndividualEmailAddresses()).thenReturn(new String[] { "a@x.com" });
      when(client.getUsers()).thenReturn(Map.of("a", "cn=a", "b", "cn=b"));
      when(client.getGroups()).thenReturn(Map.of("g", "cn=g"));
      when(client.getRoles()).thenReturn(Map.of("r", "cn=r"));

      LdapAuthenticationProvider provider = mock(LdapAuthenticationProvider.class);
      when(provider.getProviderName()).thenReturn("ldap79077");
      when(provider.getClient()).thenReturn(client);
      when(provider.getCacheRefreshDelay()).thenReturn(3600000L);

      LdapAuthenticationCache cache = new LdapAuthenticationCache(provider);

      try {
         assertFalse(cache.isInitialized());
         cache.load(true).get();
         assertTrue(cache.isInitialized());
         assertEquals(Set.of("a", "b"), new HashSet<>(Arrays.asList(cache.getUsers())));
         assertEquals("cn=g", cache.getGroupDn("g"));
         assertEquals("cn=r", cache.getRoleDn("r"));

         // a second load replaces the maps, including entries that are gone from the directory
         when(client.getUsers()).thenReturn(Map.of("c", "cn=c"));
         cache.load(true).get();
         assertEquals(Set.of("c"), new HashSet<>(Arrays.asList(cache.getUsers())));
      }
      finally {
         cache.close();
      }
   }

   @Configuration
   static class TestConfiguration {
      @Bean
      public Cluster cluster() {
         // only the LDAP cache goes to the Ignite node, the rest of the context keeps the mock
         return new MockCluster() {
            @Override
            public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
               return name.startsWith(PREFIX) ? node.getReplicatedMap(name) :
                  super.getReplicatedMap(name);
            }

            @Override
            public DistributedLong getLong(String name) {
               return name.startsWith(PREFIX) ? node.getLong(name) : super.getLong(name);
            }

            @Override
            public DistributedTransaction startTx() {
               return node.startTx();
            }
         };
      }
   }

   private static final String PREFIX = "LdapSecurity:";
   private static Path clusterDir;
   private static IgniteCluster node;
}

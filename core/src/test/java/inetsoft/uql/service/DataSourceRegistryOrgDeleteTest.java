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
package inetsoft.uql.service;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.util.Config;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import inetsoft.storage.BlobStorageManager;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79558: an organization deleted on one node and re-created with the same id listed the
 * old organization's data sources on the other nodes.
 *
 * <p>Another node is modelled as follows: the registry here has the org loaded, the org's store
 * is dropped and an empty root is written to the new store by something other than this
 * registry's {@code init()} (which would hide the defect through {@code setRoot}).</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DataSourceRegistryOrgDeleteTest.RegistryConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceRegistryOrgDeleteTest {
   @Configuration
   static class RegistryConfig {
      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager, cluster, eventPublisher);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public IndexedStorage indexedStorage(BlobStorageManager blobStorageManager) {
         return new BlobIndexedStorage(blobStorageManager);
      }

      @Bean
      public DataSourceRegistry dataSourceRegistry(IndexedStorage indexedStorage, Config config,
                                                   Cluster cluster) throws Exception
      {
         return new DataSourceRegistry(indexedStorage, config, cluster);
      }
   }

   @BeforeEach
   void savePrincipal() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void restorePrincipal() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   /** The node that did not run the delete receives the cluster message. */
   @Test
   void otherNodeDropsDeletedOrgOnClusterMessage() throws Exception {
      String org = "org79558a";
      seedAndList(org);
      recreateEmptyOrgElsewhere(org);

      // the new store is empty but this node still serves the old root (the defect)
      indexedStorage.setInitialized(org);
      cluster.sendMessage(new ClearDataSourceCacheEvent(org, true));

      assertArrayEquals(new String[0], registry.getDataSourceFullNames("/", org));
      assertFalse(indexedStorage.isInitialized(org),
                  "init() must run again on this node for the re-created org");
   }

   /** The node that runs the delete clears its own cache and notifies the cluster. */
   @Test
   void deleteNodeClearsOwnCacheAndNotifiesCluster() throws Exception {
      String org = "org79558b";
      seedAndList(org);
      recreateEmptyOrgElsewhere(org);
      indexedStorage.setInitialized(org);

      registry.orgDeleted(org);

      assertArrayEquals(new String[0], registry.getDataSourceFullNames("/", org));
      assertFalse(indexedStorage.isInitialized(org));
   }

   /** clearCache(orgId) must drop the org's cached objects, not only the name indexes. */
   @Test
   void clearCacheByOrgDropsCachedRoot() throws Exception {
      String org = "org79558c";
      seedAndList(org);
      recreateEmptyOrgElsewhere(org);

      registry.clearCache(org);

      assertArrayEquals(new String[0], registry.getDataSourceFullNames("/", org));
   }

   /** The deletion of one org must not evict another org's cached state. */
   @Test
   void otherOrgIsUntouched() throws Exception {
      String org = "org79558d";
      String other = "org79558e";
      seedAndList(org);
      seedAndList(other);
      recreateEmptyOrgElsewhere(org);

      cluster.sendMessage(new ClearDataSourceCacheEvent(org, true));

      assertArrayEquals(new String[] { "Orders" }, registry.getDataSourceFullNames("/", other));
   }

   /** A failed cluster send must not abort the caller, and the local clear must still happen. */
   @Test
   void failedBroadcastDoesNotPropagate() throws Exception {
      String org = "org79558f";
      seedAndList(org);
      recreateEmptyOrgElsewhere(org);
      indexedStorage.setInitialized(org);
      MessageListener failing = event -> {
         throw new IllegalStateException("send failed");
      };
      cluster.addMessageListener(failing);

      try {
         assertDoesNotThrow(() -> registry.orgDeleted(org));
      }
      finally {
         cluster.removeMessageListener(failing);
      }

      assertArrayEquals(new String[0], registry.getDataSourceFullNames("/", org));
      assertFalse(indexedStorage.isInitialized(org));
   }

   /** An old-format event (no org-deleted flag) still does the global clear. */
   @Test
   void oldFormatEventStillClearsGlobally() throws Exception {
      String org = "org79558g";
      seedAndList(org);
      recreateEmptyOrgElsewhere(org);
      indexedStorage.setInitialized(org);

      cluster.sendMessage(new ClearDataSourceCacheEvent(org));

      assertArrayEquals(new String[0], registry.getDataSourceFullNames("/", org));
      assertTrue(indexedStorage.isInitialized(org), "an ordinary event must not reset the flag");
   }

   /** init() after resetInitialized leaves an existing root intact. */
   @Test
   void initAfterResetKeepsExistingRoot() throws Exception {
      String org = "org79558h";
      seedAndList(org);
      indexedStorage.setInitialized(org);

      indexedStorage.resetInitialized(org);
      registry.init();

      assertFalse(indexedStorage.isInitialized(org));
      assertArrayEquals(new String[] { "Orders" }, registry.getDataSourceFullNames("/", org));
   }

   private void seedAndList(String org) throws Exception {
      ThreadContext.setContextPrincipal(principal(org));
      AssetFolder root = new AssetFolder();
      root.addEntry(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                   "Orders", null, org));
      indexedStorage.putXMLSerializable(rootId(org), root);
      // the login of a user of the org: sets the registry's last modified timestamp for the org
      registry.init();
      assertArrayEquals(new String[] { "Orders" }, registry.getDataSourceFullNames("/", org),
                        "setup: the org's data source must be listed and cached");
   }

   private void recreateEmptyOrgElsewhere(String org) throws Exception {
      indexedStorage.removeStorage(org);
      ThreadContext.setContextPrincipal(principal(org));
      indexedStorage.putXMLSerializable(rootId(org), new AssetFolder());
   }

   private static String rootId(String org) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER,
                            "/", null, org).toIdentifier();
   }

   private static Principal principal(String org) {
      return new SRPrincipal(new IdentityID("admin", org), new IdentityID[0], new String[0], org, 0L);
   }

   @Autowired Cluster cluster;
   @Autowired IndexedStorage indexedStorage;
   @Autowired DataSourceRegistry registry;
   private Principal savedPrincipal;
}

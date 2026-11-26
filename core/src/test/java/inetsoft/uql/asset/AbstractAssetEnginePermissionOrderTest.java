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
package inetsoft.uql.asset;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #82406: the cross-org and private-repository guards of checkAssetPermission0 ran the costly
 * site admin lookup for every entry. The cheap comparison now runs first; the decisions must be
 * the same as before and the lookup must not be made for entries of the callers own org.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  AbstractAssetEnginePermissionOrderTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AbstractAssetEnginePermissionOrderTest {
   private static final String ORG1 = "org1";
   private static final String ORG2 = "org2";

   @Test
   void sameOrgEntriesNeverLookUpSiteAdmin() throws Exception {
      for(int entries : new int[] { 10, 200 }) {
         try(Fixture f = new Fixture(false)) {
            for(int i = 0; i < entries; i++) {
               assertTrue(f.check(user("alice", ORG1), global("ws" + i, ORG1), false));
            }

            verify(f.manager, never()).isSiteAdmin(any(Principal.class));
         }
      }
   }

   @Test
   void crossOrgEntryIsRejectedForNonAdmin() throws Exception {
      try(Fixture f = new Fixture(false)) {
         assertFalse(f.check(user("alice", ORG1), global("ws", ORG2), false));
         verify(f.manager, times(1)).isSiteAdmin(any(Principal.class));
      }
   }

   @Test
   void crossOrgEntryIsAllowedForSiteAdmin() throws Exception {
      try(Fixture f = new Fixture(true)) {
         assertTrue(f.check(user("root", ORG1), global("ws", ORG2), false));
      }
   }

   @Test
   void siteAdminWithoutOrgIdDoesNotFail() throws Exception {
      try(Fixture f = new Fixture(true)) {
         assertTrue(f.check(user("root", null), global("ws", ORG1), false));
      }
   }

   @Test
   void nonAdminWithoutOrgIdIsRejected() throws Exception {
      try(Fixture f = new Fixture(false)) {
         assertFalse(f.check(user("alice", null), global("ws", ORG1), false));
      }
   }

   @Test
   void otherUsersPrivateEntryIsRejectedForNonAdminOnly() throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                                        "ws", new IdentityID("bob", ORG1), ORG1);

      try(Fixture f = new Fixture(false)) {
         assertFalse(f.check(user("alice", ORG1), entry, true));
      }

      try(Fixture f = new Fixture(true)) {
         assertTrue(f.check(user("alice", ORG1), entry, true));
      }
   }

   @Test
   void ownPrivateEntryNeedsNoSiteAdminLookup() throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                                        "ws", new IdentityID("alice", ORG1), ORG1);

      try(Fixture f = new Fixture(false)) {
         assertTrue(f.check(user("alice", ORG1), entry, true));
         verify(f.manager, never()).isSiteAdmin(any(Principal.class));
      }
   }

   private static AssetEntry global(String path, String org) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null,
                            org);
   }

   private static XPrincipal user(String name, String org) {
      XPrincipal user = mock(XPrincipal.class);
      when(user.getName()).thenReturn(new IdentityID(name, ORG1).convertToKey());
      when(user.getOrgId()).thenReturn(org);
      return user;
   }

   private final class Fixture implements AutoCloseable {
      Fixture(boolean siteAdmin) {
         manager = mock(OrganizationManager.class);
         when(manager.isSiteAdmin(any(Principal.class))).thenReturn(siteAdmin);
         statics = Mockito.mockStatic(OrganizationManager.class);
         statics.when(OrganizationManager::getInstance).thenReturn(manager);
         engine = new Engine(new BlobIndexedStorage(blobStorageManager));
      }

      boolean check(Principal user, AssetEntry entry, boolean checkUserAsset) throws Exception {
         try {
            engine.checkAssetPermission(user, entry, ResourceAction.READ, checkUserAsset);
            return true;
         }
         catch(MessageException ex) {
            return false;
         }
      }

      @Override
      public void close() {
         statics.close();
      }

      final OrganizationManager manager;
      private final MockedStatic<OrganizationManager> statics;
      private final Engine engine;
   }

   private static final class Engine extends AbstractAssetEngine {
      Engine(IndexedStorage storage) {
         super((LibManagerProvider) null, Cluster.getInstance());
         istore = storage;
         scopes = new int[] { GLOBAL_SCOPE, REPORT_SCOPE, USER_SCOPE };
         Arrays.sort(scopes);
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                     EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type,
                                     IdentityID resource, EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      protected boolean checkDataModelFolderPermission(String folder, String source,
                                                       Principal user)
      {
         return true;
      }

      @Override
      protected boolean checkQueryFolderPermission(String folder, String source, Principal user) {
         return true;
      }

      @Override
      protected boolean checkQueryPermission(String query, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourcePermission(String dname, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourceFolderPermission(String folder, Principal user) {
         return true;
      }
   }

   @Configuration
   static class Beans {
      @Bean
      ScheduleManager scheduleManager() {
         return mock(ScheduleManager.class);
      }

      @Bean
      MVManager mvManager() {
         MVManager manager = mock(MVManager.class);
         when(manager.list(anyBoolean(), any(MVManager.MVFilter.class))).thenReturn(new MVDef[0]);
         return manager;
      }
   }

   @Autowired
   private BlobStorageManager blobStorageManager;
}

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
package inetsoft.uql.asset;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80473: finding the owners of a dashboard bookmarks scans every key of the org store, and
 * deleting a folder did that scan once per child dashboard. The scan must return the same owners
 * as before (dash1 must not match dash10), and a folder delete must scan once, whatever the number
 * of dashboards in it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  AbstractAssetEngineBookmarkScanTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AbstractAssetEngineBookmarkScanTest {
   @BeforeEach
   void setUp(TestInfo info) throws Exception {
      principal = ThreadContext.getContextPrincipal();
      orgId = Organization.getDefaultOrganizationID();
      prefix = "b80473" + info.getTestMethod().orElseThrow().getName();
      storage = new CountingStorage(blobStorageManager);
      engine = new Engine(storage);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(principal);
   }

   @Test
   void bookmarkUsersOfOneDashboardDoNotIncludeASiblingWithTheSamePrefix() throws Exception {
      AssetEntry dash1 = addSheet(null, prefix + "dash1");
      AssetEntry dash10 = addSheet(null, prefix + "dash10");
      IdentityID alice = id("alice");
      IdentityID bob = id("bob");
      IdentityID ghost = id("ssoUserNotInProvider");
      addBookmark(dash1, alice);
      addBookmark(dash1, bob);
      addBookmark(dash1, ghost);
      addBookmark(dash10, id("carol"));
      addBookmark(dash10, alice);

      List<IdentityID> expected = new ArrayList<>(List.of(alice, bob, ghost));
      Collections.sort(expected);
      List<IdentityID> expected10 = new ArrayList<>(List.of(alice, id("carol")));
      Collections.sort(expected10);

      assertEquals(expected, engine.getBookmarkUsers(dash1));
      assertEquals(expected10, engine.getBookmarkUsers(dash10));
   }

   @Test
   void bookmarkEntriesOfOneDashboardAreTheOnesOfThatDashboard() throws Exception {
      AssetEntry dash1 = addSheet(null, prefix + "dash1");
      AssetEntry dash10 = addSheet(null, prefix + "dash10");
      addBookmark(dash1, id("alice"));
      addBookmark(dash10, id("bob"));
      addBookmark(dash10, id("alice"));

      List<AssetEntry> entries = engine.getVSBookmarkEntries(dash1);

      assertEquals(1, entries.size());
      assertEquals(id("alice"), entries.get(0).getUser());
   }

   @Test
   void clearingOneDashboardKeepsTheSiblingBookmarks() throws Exception {
      AssetEntry dash1 = addSheet(null, prefix + "dash1");
      AssetEntry dash10 = addSheet(null, prefix + "dash10");
      addBookmark(dash1, id("alice"));
      addBookmark(dash10, id("alice"));

      engine.clearVSBookmark(dash1);

      assertTrue(engine.getBookmarkUsers(dash1).isEmpty());
      assertEquals(List.of(id("alice")), engine.getBookmarkUsers(dash10));
   }

   @Test
   void removingOneSheetScansOnceAndClearsItsBookmarks() throws Exception {
      AssetEntry dash = addSheet(null, prefix + "single");
      addBookmark(dash, id("alice"));
      addBookmark(dash, id("bob"));
      storage.scans.set(0);

      engine.removeSheet0(dash, storage, false);

      assertEquals(1, storage.scans.get());
      assertTrue(engine.getBookmarkUsers(dash).isEmpty());
   }

   @Test
   void removingAFolderScansOnceForAnyNumberOfDashboards() throws Exception {
      AssetEntry folder = addFolder(prefix + "folder");
      AssetEntry sub = addFolder(prefix + "folder/sub");
      List<AssetEntry> dashboards = new ArrayList<>();

      for(int i = 0; i < 12; i++) {
         AssetEntry dash = addSheet(i < 8 ? folder : sub, prefix + "d" + i);
         dashboards.add(dash);
         addBookmark(dash, id("alice"));
         addBookmark(dash, id("bob" + i));
      }

      AssetEntry outside = addSheet(null, prefix + "outside");
      addBookmark(outside, id("alice"));
      storage.scans.set(0);

      engine.removeFolder0(folder, storage, true);

      assertEquals(1, storage.scans.get(), "bookmark key scans for a folder delete");

      for(AssetEntry dash : dashboards) {
         assertTrue(engine.getBookmarkUsers(dash).isEmpty(), "bookmarks left for " + dash);
      }

      assertEquals(List.of(id("alice")), engine.getBookmarkUsers(outside));
   }

   @Test
   void aFolderDeleteThatFailsMidwayDoesNotLeaveItsIndexOnTheThread() throws Exception {
      AssetEntry folder = addFolder(prefix + "folder");
      AssetEntry dash1 = addSheet(folder, prefix + "d1");
      AssetEntry dash2 = addSheet(folder, prefix + "d2");
      addBookmark(dash1, id("alice"));
      addBookmark(dash2, id("alice"));
      AssetEntry outside = addSheet(null, prefix + "outside");
      addBookmark(outside, id("alice"));
      engine.failOn = dash2.getPath();

      assertThrows(Exception.class, () -> engine.removeFolder0(folder, storage, true));

      // a bookmark added after the failed delete must be seen by the next lookup, which
      // happens only if the thread-local index was dropped
      addBookmark(outside, id("bob"));
      storage.scans.set(0);
      List<IdentityID> expected = new ArrayList<>(List.of(id("alice"), id("bob")));
      Collections.sort(expected);

      assertEquals(expected, engine.getBookmarkUsers(outside));
      assertEquals(1, storage.scans.get());

      // and the next folder delete builds a fresh index
      engine.failOn = null;
      addBookmark(dash2, id("carol"));
      engine.removeFolder0(folder, storage, true);
      assertTrue(engine.getBookmarkUsers(dash2).isEmpty());
   }

   private IdentityID id(String name) {
      return new IdentityID(name, orgId);
   }

   private AssetEntry addFolder(String path) throws Exception {
      AssetEntry folder = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
                                         path, null, orgId);
      AssetEntry parent = folder.getParent();
      storage.putXMLSerializable(folder.toIdentifier(), new AssetFolder());
      AssetFolder parentFolder = engine.getParentFolder(folder, storage);
      assertNotNull(parentFolder, "no parent folder for " + folder);
      parentFolder.addEntry(folder);
      storage.putXMLSerializable(parent.toIdentifier(), parentFolder);
      return folder;
   }

   private AssetEntry addSheet(AssetEntry folder, String name) throws Exception {
      String path = folder == null ? name : folder.getPath() + "/" + name;
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        path, null, orgId);
      AssetEntry parent = entry.getParent();
      storage.putXMLSerializable(entry.toIdentifier(), new Viewsheet());
      AssetFolder parentFolder = engine.getParentFolder(entry, storage);
      assertNotNull(parentFolder, "no folder " + parent);
      parentFolder.addEntry(entry);
      storage.putXMLSerializable(parent.toIdentifier(), parentFolder);
      return entry;
   }

   private void addBookmark(AssetEntry sheet, IdentityID user) throws Exception {
      AssetEntry bookmark = engine.getVSBookmarkEntry(sheet, user, true);
      storage.putXMLSerializable(bookmark.toIdentifier(), new VSBookmark());
   }

   /** Counts the scans of the keys, which take a filter. */
   private static final class CountingStorage extends BlobIndexedStorage {
      CountingStorage(BlobStorageManager manager) {
         super(manager);
      }

      @Override
      public Set<String> getKeys(Filter filter, String orgID) {
         if(filter != null) {
            scans.incrementAndGet();
         }

         return super.getKeys(filter, orgID);
      }

      final AtomicInteger scans = new AtomicInteger();
   }

   private static final class Engine extends AbstractAssetEngine {
      Engine(IndexedStorage storage) {
         super((LibManagerProvider) null, Cluster.getInstance());
         istore = storage;
         scopes = new int[] { GLOBAL_SCOPE, REPORT_SCOPE, USER_SCOPE };
         Arrays.sort(scopes);
      }

      @Override
      protected void removeSheet0(AssetEntry entry, IndexedStorage storage, boolean isFolder)
         throws Exception
      {
         if(failOn != null && failOn.equals(entry.getPath())) {
            throw new Exception("injected failure");
         }

         super.removeSheet0(entry, storage, isFolder);
      }

      volatile String failOn;

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

   private Principal principal;
   private String orgId;
   private String prefix;
   private CountingStorage storage;
   private Engine engine;
}

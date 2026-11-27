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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.XMLSerializable;
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
 * Bug #82457: removing a folder rewrote the parent folder once per removed child, and the parent
 * of the children of the Recycle Bin is the Recycle Bin itself, so clearing a large bin was
 * quadratic. The parent of a folder that is being removed is removed too, so its blob must not
 * be read or written per child.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  AbstractAssetEngineFolderRemoveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AbstractAssetEngineFolderRemoveTest {
   @BeforeEach
   void setUp(TestInfo info) throws Exception {
      principal = ThreadContext.getContextPrincipal();
      orgId = Organization.getDefaultOrganizationID();
      prefix = "b82457" + info.getTestMethod().orElseThrow().getName();
      storage = new CountingStorage(blobStorageManager);
      engine = new Engine(storage);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(principal);
   }

   @Test
   void childRemovalDoesNotRewriteTheFolderBeingRemoved() throws Exception {
      int small = writesWhileRemoving(prefix + "s", 10);
      int large = writesWhileRemoving(prefix + "l", 100);

      assertEquals(small, large, "parent writes must not depend on the number of children");
      assertEquals(0, large);
   }

   @Test
   void nestedFoldersAreRemovedWithoutWritingTheFoldersBeingRemoved() throws Exception {
      AssetEntry folder = addFolder(null, prefix + "top");
      AssetEntry sub = addFolder(folder, "sub");
      List<AssetEntry> all = new ArrayList<>();

      for(int i = 0; i < 5; i++) {
         all.add(addSheet(folder, "a" + i));
         all.add(addSheet(sub, "b" + i));
      }

      storage.parentWrites.clear();
      engine.removeFolder0(folder, storage, true);

      assertFalse(storage.parentWrites.contains(folder.toIdentifier()));
      assertFalse(storage.parentWrites.contains(sub.toIdentifier()));
      assertFalse(storage.contains(folder.toIdentifier()));
      assertFalse(storage.contains(sub.toIdentifier()));

      for(AssetEntry sheet : all) {
         assertFalse(storage.contains(sheet.toIdentifier()), "left: " + sheet);
      }
   }

   @Test
   void aFailureKeepsTheRemovalsAlreadyDoneAndWritesTheFolderOnce() throws Exception {
      AssetEntry folder = addFolder(null, prefix + "top");
      List<AssetEntry> sheets = new ArrayList<>();

      for(int i = 0; i < 6; i++) {
         sheets.add(addSheet(folder, "s" + i));
      }

      AssetEntry failing = sheets.get(3);
      engine.failOn = failing.getPath();
      AssetEntry[] order = ((AssetFolder) storage.getXMLSerializable(
         folder.toIdentifier(), null)).getEntries();
      storage.parentWrites.clear();

      assertThrows(Exception.class, () -> engine.removeFolder0(folder, storage, true));

      AssetFolder left = (AssetFolder) storage.getXMLSerializable(folder.toIdentifier(), null);
      assertNotNull(left, "the folder must survive a failed removal");
      assertEquals(1, storage.parentWrites.stream()
         .filter(folder.toIdentifier()::equals).count());

      for(AssetEntry sheet : order) {
         boolean stored = storage.contains(sheet.toIdentifier());
         assertEquals(stored, left.containsEntry(sheet),
                      "folder listing disagrees with storage for " + sheet);
      }

      assertTrue(left.containsEntry(failing));
   }

   @Test
   void aChildThatFailsAfterItsBlobIsGoneIsNotListedAgain() throws Exception {
      AssetEntry folder = addFolder(null, prefix + "top");
      List<AssetEntry> sheets = new ArrayList<>();

      for(int i = 0; i < 4; i++) {
         sheets.add(addSheet(folder, "s" + i));
      }

      AssetEntry failing = sheets.get(2);
      engine.failAfter = failing.getPath();
      storage.parentWrites.clear();

      assertThrows(Exception.class, () -> engine.removeFolder0(folder, storage, true));

      AssetFolder left = (AssetFolder) storage.getXMLSerializable(folder.toIdentifier(), null);
      assertNotNull(left);
      assertEquals(1, storage.parentWrites.stream()
         .filter(folder.toIdentifier()::equals).count());
      assertFalse(storage.contains(failing.toIdentifier()));
      assertFalse(left.containsEntry(failing), "deleted child is still listed");

      for(AssetEntry sheet : sheets) {
         assertEquals(storage.contains(sheet.toIdentifier()), left.containsEntry(sheet),
                      "folder listing disagrees with storage for " + sheet);
      }
   }

   private int writesWhileRemoving(String name, int count) throws Exception {
      AssetEntry folder = addFolder(null, name);

      for(int i = 0; i < count; i++) {
         addSheet(folder, "s" + i);
      }

      storage.parentWrites.clear();
      engine.removeFolder0(folder, storage, true);
      return (int) storage.parentWrites.stream().filter(folder.toIdentifier()::equals).count();
   }

   private AssetEntry addFolder(AssetEntry parent, String name) throws Exception {
      String path = parent == null ? name : parent.getPath() + "/" + name;
      AssetEntry folder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                         AssetEntry.Type.REPOSITORY_FOLDER, path, null, orgId);
      storage.putXMLSerializable(folder.toIdentifier(), new AssetFolder());
      AssetFolder parentFolder = engine.getParentFolder(folder, storage);
      assertNotNull(parentFolder, "no parent folder for " + folder);
      parentFolder.addEntry(folder);
      storage.putXMLSerializable(folder.getParent().toIdentifier(), parentFolder);
      return folder;
   }

   private AssetEntry addSheet(AssetEntry folder, String name) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        folder.getPath() + "/" + name, null, orgId);
      storage.putXMLSerializable(entry.toIdentifier(), new Viewsheet());
      AssetFolder parentFolder = engine.getParentFolder(entry, storage);
      assertNotNull(parentFolder, "no folder " + folder);
      parentFolder.addEntry(entry);
      storage.putXMLSerializable(folder.toIdentifier(), parentFolder);
      return entry;
   }

   /** Records the identifiers of the folders written. */
   private static final class CountingStorage extends BlobIndexedStorage {
      CountingStorage(BlobStorageManager manager) {
         super(manager);
      }

      @Override
      public void putXMLSerializable(String key, XMLSerializable obj) throws Exception {
         if(obj instanceof AssetFolder) {
            parentWrites.add(key);
         }

         super.putXMLSerializable(key, obj);
      }

      final List<String> parentWrites = Collections.synchronizedList(new ArrayList<>());
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

         if(failAfter != null && failAfter.equals(entry.getPath())) {
            throw new Exception("injected late failure");
         }
      }

      volatile String failAfter;

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

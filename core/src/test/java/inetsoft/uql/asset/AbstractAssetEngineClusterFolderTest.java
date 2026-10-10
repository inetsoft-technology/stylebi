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
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78230: assets deleted at the same moment on two cluster nodes must all be moved to the
 * Recycle Bin. An asset folder is read, changed and written back as a whole, and each node has its
 * own engine with its own write lock, so the engine also takes a cluster lock for the change.
 *
 * <p>Two engines over one shared storage play the two nodes. They share the cluster of the test
 * context, whose locks are shared by name as Ignite's are across nodes. A single engine would pass
 * without the cluster lock, as its own write lock serializes the changes.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  AbstractAssetEngineClusterFolderTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AbstractAssetEngineClusterFolderTest {
   @BeforeEach
   void setUp(TestInfo info) throws Exception {
      principal = ThreadContext.getContextPrincipal();
      orgId = Organization.getDefaultOrganizationID();
      prefix = "b78230" + info.getTestMethod().orElseThrow().getName();
      storage = new BlobIndexedStorage(blobStorageManager);
      nodeA = new NodeEngine(storage);
      nodeB = new NodeEngine(new BlobIndexedStorage(blobStorageManager));

      if(!nodeA.containsEntry(recycleBin())) {
         nodeA.addFolder(recycleBin(), null);
      }
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(principal);
      AssetUtil.setAssetRepository(false, null);
   }

   /**
    * Two nodes move a sheet each to the Recycle Bin, both having read the Recycle Bin folder
    * before either wrote it. Neither move may be lost from the folders.
    */
   @Test
   void concurrentMovesOnTwoNodesKeepBothEntries() throws Exception {
      AssetEntry sheetA = addSheet(prefix + "A");
      AssetEntry sheetB = addSheet(prefix + "B");
      AssetEntry recycledA = recycled(prefix + "RA");
      AssetEntry recycledB = recycled(prefix + "RB");
      CountDownLatch bothRead = new CountDownLatch(2);
      nodeA.pauseAfterRead = bothRead;
      nodeB.pauseAfterRead = bothRead;
      ExecutorService executor = Executors.newFixedThreadPool(2);

      try {
         Future<?> moveA = executor.submit(() -> move(nodeA, sheetA, recycledA));
         Future<?> moveB = executor.submit(() -> move(nodeB, sheetB, recycledB));
         moveA.get(1, TimeUnit.MINUTES);
         moveB.get(1, TimeUnit.MINUTES);
      }
      finally {
         executor.shutdownNow();
         assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));
      }

      AssetFolder recycleFolder = nodeA.getParentFolder(recycledA, storage);
      assertTrue(recycleFolder.containsEntry(recycledA), "lost from the Recycle Bin: " + recycledA);
      assertTrue(recycleFolder.containsEntry(recycledB), "lost from the Recycle Bin: " + recycledB);

      AssetFolder root = nodeA.getParentFolder(sheetA, storage);
      assertFalse(root.containsEntry(sheetA), "deleted sheet is still listed: " + sheetA);
      assertFalse(root.containsEntry(sheetB), "deleted sheet is still listed: " + sheetB);
   }

   /**
    * A folder entry whose sheet is no longer stored is only removed from its folder by a delete,
    * so no recycle bin record may be added for it.
    */
   @Test
   void deletingAMissingSheetAddsNoRecycleBinRecord() throws Exception {
      AssetEntry missing = addSheet(prefix + "Missing");
      storage.remove(missing.toIdentifier());
      RecycleBin recycleBin = mock(RecycleBin.class);
      AssetUtil.setAssetRepository(false, nodeA);

      RecycleUtils.moveSheetToRecycleBin(missing, null, recycleBin, true);

      verify(recycleBin, never()).addEntry(anyString(), anyString(), anyString(), any(),
                                           anyInt(), anyInt(), any());
      assertFalse(nodeA.getParentFolder(missing, storage).containsEntry(missing));
   }

   @Test
   void deletingAStoredSheetAddsARecycleBinRecord() throws Exception {
      AssetEntry sheet = addSheet(prefix + "Stored");
      RecycleBin recycleBin = mock(RecycleBin.class);
      AssetUtil.setAssetRepository(false, nodeA);

      RecycleUtils.moveSheetToRecycleBin(sheet, null, recycleBin, true);

      verify(recycleBin).addEntry(startsWith(RecycleUtils.RECYCLE_BIN_FOLDER + "/"),
                                  eq(sheet.getPath()), eq(sheet.getName()), any(), anyInt(),
                                  eq(AssetRepository.GLOBAL_SCOPE), isNull());
      assertFalse(nodeA.getParentFolder(sheet, storage).containsEntry(sheet));
   }

   /**
    * A caller that found no Recycle Bin before another node created it and moved a sheet into it
    * must not replace that folder with an empty one.
    */
   @Test
   void addingAStoredFolderKeepsItsEntries() throws Exception {
      AssetEntry sheet = addSheet(prefix + "Kept");
      AssetEntry recycled = recycled(prefix + "Kept");
      move(nodeA, sheet, recycled);

      nodeB.addFolder(recycleBin(), null);

      assertTrue(nodeA.getParentFolder(recycled, storage).containsEntry(recycled));
   }

   private void move(NodeEngine node, AssetEntry from, AssetEntry to) {
      try {
         node.changeSheet(from, to, null, true, true, true);
      }
      catch(Exception e) {
         throw new CompletionException(e);
      }
   }

   private AssetEntry addSheet(String name) throws Exception {
      AssetEntry entry =
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, name, null, orgId);
      AssetEntry parent = entry.getParent();
      storage.putXMLSerializable(entry.toIdentifier(), new Viewsheet());
      AssetFolder folder = nodeA.getParentFolder(entry, storage);
      folder.addEntry(entry);
      storage.putXMLSerializable(parent.toIdentifier(), folder);
      return entry;
   }

   private AssetEntry recycled(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            RecycleUtils.getRecycleBinPath(name), null, orgId);
   }

   private AssetEntry recycleBin() {
      return recycled("x").getParent();
   }

   /**
    * The asset engine of one cluster node. It can hold a move after it read the destination
    * folder until the other node read it too, or until a second passes when the other node
    * waits for the cluster lock.
    */
   private static final class NodeEngine extends AbstractAssetEngine {
      NodeEngine(IndexedStorage storage) {
         super((LibManagerProvider) null, Cluster.getInstance());
         istore = storage;
         scopes = new int[] { GLOBAL_SCOPE, REPORT_SCOPE, USER_SCOPE };
         Arrays.sort(scopes);
      }

      @Override
      protected AssetFolder getParentFolder(AssetEntry entry, IndexedStorage storage) {
         AssetFolder folder = super.getParentFolder(entry, storage);
         CountDownLatch latch = pauseAfterRead;

         if(latch != null && entry.isSheet() && RecycleUtils.isInRecycleBin(entry.getPath())) {
            pauseAfterRead = null;
            latch.countDown();

            try {
               latch.await(1, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }

         return folder;
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

      private volatile CountDownLatch pauseAfterRead;
   }

   // the recycle bin delete removes the materialized views of the sheet
   @Configuration
   static class Beans {
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
   private IndexedStorage storage;
   private NodeEngine nodeA;
   private NodeEngine nodeB;
}

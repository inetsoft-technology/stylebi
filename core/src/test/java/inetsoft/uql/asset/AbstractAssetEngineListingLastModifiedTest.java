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

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79419: listing a folder of N viewsheets read the storage timestamp (which resolves every
 * organization) once per sheet. It must be read once per listing, and the node-local sheet cache
 * must still be dropped when the timestamp changed (the only cross-node invalidation).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AbstractAssetEngineListingLastModifiedTest {
   @BeforeEach
   void setUp(TestInfo info) {
      principal = ThreadContext.getContextPrincipal();
      orgId = Organization.getDefaultOrganizationID();
      prefix = "b79419" + info.getTestMethod().orElseThrow().getName();
      storage = new CountingStorage(blobStorageManager);
      engine = new NodeEngine(storage);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(principal);
   }

   @Test
   void listingReadsTheTimestampOncePerListingNotPerSheet() throws Exception {
      addSheets(2, true);
      assertEquals(2, list().length);
      int small = storage.reads.get();
      addSheets(8, true);
      assertEquals(10, list().length);
      int large = storage.reads.get() - small;

      assertTrue(small >= 1);
      // listing 10 sheets costs the same number of reads as listing the 2 sheets before
      assertEquals(small, large, "timestamp reads must not grow with the number of sheets");
   }

   @Test
   void unchangedTimestampKeepsCachedSheetsAndChangedTimestampDropsThem() throws Exception {
      AssetEntry sheet = addSheets(1, true)[0];
      assertEquals("true", list(sheet));

      // another node saves the sheet: only the timestamp tells this node
      storeSheet(sheet, false);
      assertEquals("true", list(sheet), "still cached while the timestamp is unchanged");

      storage.timestamp++;
      assertEquals("false", list(sheet), "cache must be dropped when the timestamp changed");
   }

   private AssetEntry[] addSheets(int count, boolean onReport) throws Exception {
      AssetEntry[] result = new AssetEntry[count];

      for(int i = 0; i < count; i++) {
         AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.VIEWSHEET, prefix + System.nanoTime() + "_" + i, null, orgId);
         AssetEntry parent = entry.getParent();
         parentEntry = parent;
         storeSheet(entry, onReport);
         AssetFolder folder = engine.getParentFolder(entry, storage);
         folder.addEntry(entry);
         storage.putXMLSerializable(parent.toIdentifier(), folder);
         result[i] = entry;
      }

      return result;
   }

   private void storeSheet(AssetEntry entry, boolean onReport) throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setOnReport(onReport);
      storage.putXMLSerializable(entry.toIdentifier(), vs);
   }

   private AssetEntry[] list() {
      AssetEntry root = parentEntry;
      return engine.getWorksheetEntries(root, principal, ResourceAction.READ,
                                        new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET));
   }

   private String list(AssetEntry sheet) {
      AssetEntry[] listed = list();
      assertTrue(listed.length > 0, "nothing listed");

      for(AssetEntry e : listed) {
         if(e.toIdentifier().equals(sheet.toIdentifier())) {
            return e.getProperty("onReport");
         }
      }

      return null;
   }

   private static final class CountingStorage extends BlobIndexedStorage {
      CountingStorage(BlobStorageManager manager) {
         super(manager);
      }

      @Override
      public long lastModified() {
         reads.incrementAndGet();
         // controlled by the test, the real timestamp moves with every write here
         return timestamp;
      }

      final AtomicInteger reads = new AtomicInteger();
      volatile long timestamp = 0;
   }

   private static final class NodeEngine extends AbstractAssetEngine {
      NodeEngine(IndexedStorage storage) {
         super((LibManagerProvider) null, Cluster.getInstance());
         istore = storage;
         scopes = new int[] { GLOBAL_SCOPE, REPORT_SCOPE, USER_SCOPE };
         Arrays.sort(scopes);
      }

      @Override
      protected AssetFolder getParentFolder(AssetEntry entry, IndexedStorage storage) {
         return super.getParentFolder(entry, storage);
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

   @Autowired
   private BlobStorageManager blobStorageManager;

   private Principal principal;
   private String orgId;
   private String prefix;
   private AssetEntry parentEntry;
   private CountingStorage storage;
   private NodeEngine engine;
}

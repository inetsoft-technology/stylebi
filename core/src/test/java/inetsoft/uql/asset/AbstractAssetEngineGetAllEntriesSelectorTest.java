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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.ResourceAction;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.util.IndexedStorage;
import inetsoft.util.StorageRefreshListener;
import inetsoft.util.TransformListener;
import inetsoft.util.XMLSerializable;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.beans.PropertyChangeListener;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Regression for Redmine #76587: AbstractAssetEngine.getAllEntries gates candidate entries with
 * AssetEntry.Selector.isEqual(Type...) (whole-bitset equality) instead of Selector.matches(Type...)
 * (membership). A Selector built from more than one Type -- e.g.
 * new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET, AssetEntry.Type.WORKSHEET), exactly what
 * ViewsheetChangePlanService.findFolderContents constructs -- can never satisfy isEqual against any
 * single-type candidate entry, so getAllEntries always returned zero entries for such a selector,
 * regardless of what a folder actually contained. This made resolveFolderDelete report any folder,
 * however full, as empty/low-risk. See docs/teams/2026-09-11-bugs-selector-condition-session/
 * bug-76587/01-diagnosis.md and 02-refute.md.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               AbstractAssetEngineGetAllEntriesSelectorTest.XRepositoryConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractAssetEngineGetAllEntriesSelectorTest {
   @Configuration
   static class XRepositoryConfig {
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @Autowired
   private Cluster cluster;

   private InMemoryIndexedStorage storage;
   private TestAssetEngine engine;

   @BeforeEach
   void setUp() {
      storage = new InMemoryIndexedStorage();
      engine = new TestAssetEngine(cluster);
      engine.istore = storage;
      AssetRepository.IGNORE_PERM.set(true);
   }

   @AfterEach
   void tearDown() {
      AssetRepository.IGNORE_PERM.remove();
   }

   @Test
   void getAllEntries_multiTypeSelector_returnsBothViewsheetAndWorksheetEntries() throws Exception {
      AssetEntry folder = folderEntry("F");
      AssetEntry viewsheet = viewsheetEntry("F/V1");
      AssetEntry worksheet = worksheetEntry("F/W1");
      seedFolderAndSheet(folder, viewsheet, new Viewsheet());
      seedFolderAndSheet(folder, worksheet, new Worksheet());

      AssetEntry.Selector selector =
         new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET, AssetEntry.Type.WORKSHEET);

      AssetEntry[] result =
         engine.getAllEntries(folder, () -> "testuser", ResourceAction.READ, selector);

      assertEquals(2, result.length,
         "a multi-type Selector(VIEWSHEET, WORKSHEET) must find both the real viewsheet and the "
         + "real worksheet in the folder -- before the fix, isEqual made this always return zero "
         + "entries regardless of actual folder contents");
      List<AssetEntry> found = List.of(result);
      assertTrue(found.contains(viewsheet), "expected the viewsheet entry to be returned");
      assertTrue(found.contains(worksheet), "expected the worksheet entry to be returned");
   }

   private void seedFolderAndSheet(AssetEntry folder, AssetEntry sheetEntry,
                                    XMLSerializable sheet) throws Exception
   {
      AssetFolder assetFolder =
         (AssetFolder) storage.getXMLSerializable(folder.toIdentifier(), null);

      if(assetFolder == null) {
         assetFolder = new AssetFolder();
         storage.putXMLSerializable(folder.toIdentifier(), assetFolder);
      }

      assetFolder.addEntry(sheetEntry);
      storage.putXMLSerializable(sheetEntry.toIdentifier(), sheet);
   }

   private static AssetEntry folderEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, path, null);
   }

   private static AssetEntry viewsheetEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null);
   }

   private static AssetEntry worksheetEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null);
   }

   private static final class TestAssetEngine extends AbstractAssetEngine {
      TestAssetEngine(Cluster cluster) {
         super(null, cluster);
         scopes = new int[] { AssetRepository.GLOBAL_SCOPE };
      }

      @Override
      protected boolean checkDataModelFolderPermission(String folder, String source,
                                                        Principal user)
      {
         return false;
      }

      @Override
      protected boolean checkQueryFolderPermission(String folder, String source, Principal user) {
         return false;
      }

      @Override
      protected boolean checkQueryPermission(String query, Principal user) {
         return false;
      }

      @Override
      protected boolean checkDataSourcePermission(String dname, Principal user) {
         return false;
      }

      @Override
      protected boolean checkDataSourceFolderPermission(String folder, Principal user) {
         return false;
      }
   }

   /**
    * A real (non-mocked) IndexedStorage backed by an in-memory map, whose getKeys(Filter, orgID)
    * actually iterates stored keys and invokes the filter -- unlike AssetRepository mocks used by
    * ViewsheetChangePlanServiceTest, this exercises AbstractAssetEngine.getAllEntries's real filter
    * lambda (and therefore the real Selector.isEqual/matches defect) end to end.
    */
   private static final class InMemoryIndexedStorage implements IndexedStorage {
      private final Map<String, XMLSerializable> data = new HashMap<>();

      @Override
      public boolean clear() {
         data.clear();
         return true;
      }

      @Override
      public void dispose() {
      }

      @Override
      public boolean contains(String key) {
         return data.containsKey(key);
      }

      @Override
      public boolean contains(String key, String orgID) {
         return contains(key);
      }

      @Override
      public XMLSerializable getXMLSerializable(String key, TransformListener trans) {
         return data.get(key);
      }

      @Override
      public XMLSerializable getXMLSerializable(String key, TransformListener trans,
                                                 String orgID)
      {
         return getXMLSerializable(key, trans);
      }

      @Override
      public void putXMLSerializable(String key, XMLSerializable value) {
         data.put(key, value);
      }

      @Override
      public void putDocument(String key, Document doc, String className, String orgID) {
         throw new UnsupportedOperationException();
      }

      @Override
      public Document getDocument(String key, String orgID) {
         return null;
      }

      @Override
      public Object getSerializable(String key) {
         throw new UnsupportedOperationException();
      }

      @Override
      public long getDataLength(String key) {
         return 0;
      }

      @Override
      public void putSerializable(String key, java.io.Serializable value) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean remove(String key) {
         return data.remove(key) != null;
      }

      @Override
      public boolean rename(String okey, String nkey, boolean overwrite) {
         throw new UnsupportedOperationException();
      }

      @Override
      public long size() {
         return data.size();
      }

      @Override
      public void addRefreshedListener(PropertyChangeListener listener) {
      }

      @Override
      public void removeRefreshedListener(PropertyChangeListener listener) {
      }

      @Override
      public void addStorageRefreshListener(StorageRefreshListener l) {
      }

      @Override
      public void removeStorageRefreshListener(StorageRefreshListener l) {
      }

      @Override
      public void addTransformListener(TransformListener listener) {
      }

      @Override
      public void removeTransformListener(TransformListener listener) {
      }

      @Override
      public long lastModified() {
         return 0;
      }

      @Override
      public long lastModified(String key) {
         return 0;
      }

      @Override
      public long lastModified(String key, String orgID) {
         return 0;
      }

      @Override
      public long lastModified(Filter filter) {
         return 0;
      }

      @Override
      public Map<String, Long> getTimestamps(Filter filter) {
         return new HashMap<>();
      }

      @Override
      public Map<String, Long> getTimestamps(Filter filter, long from) {
         return new HashMap<>();
      }

      @Override
      public Set<String> getKeys(Filter filter) {
         return getKeys(filter, null);
      }

      @Override
      public Set<String> getKeys(Filter filter, String orgID) {
         Set<String> result = new HashSet<>();

         for(String key : new ArrayList<>(data.keySet())) {
            if(filter.accept(key)) {
               result.add(key);
            }
         }

         return result;
      }

      @Override
      public boolean isInitialized(String orgID) {
         return true;
      }

      @Override
      public void setInitialized(String orgID) {
      }

      @Override
      public void migrateStorageData(AbstractIdentity oorg, AbstractIdentity norg) {
      }

      @Override
      public void migrateStorageData(String oname, String nname) {
      }

      @Override
      public void copyStorageData(Organization oOrg, Organization nOrg, boolean rename) {
      }

      @Override
      public void removeStorage(String orgID) {
      }
   }
}

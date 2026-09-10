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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.DependenciesInfo;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.uql.asset.sync.RenameTransformObject;
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
import inetsoft.util.StorageRefreshListener;
import inetsoft.util.TransformListener;
import inetsoft.util.XMLSerializable;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression for Redmine #76545 item 2/3: AbstractAssetEngine.checkSheetRemoveable
 * (via getSheetDependencies) reads only the stale legacy per-sheet field
 * (AbstractSheet.getOuterDependencies()) and never consults the canonical
 * DependencyStorageService / DependencyTool.getDependencies reverse-dependency
 * index. A worksheet with a real dependent recorded only in the canonical index (the legacy
 * field never populated -- there is no defined sync mechanism between the two stores) is
 * wrongly reported as removable, for both the direct-delete path (real principal) and the
 * folder-delete-cascade path (hardcoded null principal). See
 * docs/teams/2026-09-10-bug-76545-checksheetremoveable-fixsafety/02-root-cause.md.
 *
 * Also covers guards A, B and C (the entry-validity/QUERY_SCOPE check, the storage-null check,
 * and the parent-folder-containment check in getSheetDependencies) as correctness invariants a
 * fix must not silently break -- per that root-cause document, each of these guards producing a
 * clear MessageException for a malformed entry is a correctness concern, not a security boundary
 * (three independent investigations found no caller population for whom guard A's absence
 * changes what they can access), but a naive fix that bypasses getSheetDependencies entirely
 * must not turn any of these loud errors into a silent removable-no-dependents answer.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               CheckSheetRemoveableStaleStoreTest.XRepositoryConfig.class,
               CheckSheetRemoveableStaleStoreTest.DependencyStorageConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CheckSheetRemoveableStaleStoreTest {
   @Configuration
   static class XRepositoryConfig {
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   /*
    * DependencyStorageService is a @Service picked up by component scan in production, so it is
    * not in this tests context (see LocalDependencyHandlerTests identical note). Provide a
    * fake backed by a real in-memory map (not a bare no-op mock) so seedCanonicalDependents
    * put() is genuinely retrievable -- this tests whole point is that a real, resolvable
    * canonical-index record exists and checkSheetRemoveable still misses it.
    */
   @Configuration
   static class DependencyStorageConfig {
      @Bean
      public DependencyStorageService dependencyStorageService() throws Exception {
         DependencyStorageService service = mock(DependencyStorageService.class);
         Map<String, RenameTransformObject> backing = new ConcurrentHashMap<>();

         doAnswer(invocation -> {
            backing.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
         }).when(service).put(anyString(), any());

         when(service.getWithOrg(anyString(), anyString()))
            .thenAnswer(invocation -> backing.get((String) invocation.getArgument(0)));

         doAnswer(invocation -> backing.remove((String) invocation.getArgument(0)) != null)
            .when(service).remove(anyString());

         return service;
      }
   }

   @Autowired
   private Cluster cluster;

   private InMemoryIndexedStorage storage;
   private TestAssetEngine engine;
   private final List<String> seededCanonicalKeys = new ArrayList<>();

   @BeforeEach
   void setUp() {
      storage = new InMemoryIndexedStorage();
      engine = new TestAssetEngine(cluster);
      engine.istore = storage;
      AssetRepository.IGNORE_PERM.set(true);
   }

   @AfterEach
   void tearDown() throws Exception {
      AssetRepository.IGNORE_PERM.remove();

      for(String key : seededCanonicalKeys) {
         DependencyStorageService.getInstance().remove(key);
      }

      seededCanonicalKeys.clear();
   }

   // Case 1 (primary): dependent recorded only in the canonical index, legacy field stale/empty.

   @Test
   void directDelete_realPrincipal_missesDependentRecordedOnlyInCanonicalIndex() throws Exception {
      AssetEntry entryW = worksheetEntry("F/W1");
      seedFolderAndSheet("F", entryW);
      seedCanonicalDependent(entryW, worksheetEntry("F/D1"));

      Principal realUser = () -> "testuser";

      assertThrows(DependencyException.class,
         () -> engine.checkSheetRemoveable(entryW, realUser),
         "W has a real dependent (D1) recorded in the canonical DependencyStorageService index; "
         + "checkSheetRemoveable must reject removal, but todays code only consults the stale "
         + "legacy per-sheet field and wrongly reports removable");
   }

   @Test
   void folderCascade_nullPrincipal_missesDependentRecordedOnlyInCanonicalIndex() throws Exception {
      AssetEntry entryW = worksheetEntry("F/W2");
      seedFolderAndSheet("F", entryW);
      seedCanonicalDependent(entryW, worksheetEntry("F/D2"));

      assertThrows(DependencyException.class,
         () -> engine.checkSheetRemoveable(entryW, null),
         "Folder-cascade delete (hardcoded null principal) must also reject removal of W2, which "
         + "has a real dependent (D2) recorded only in the canonical index");
   }

   // Negative control: no dependents in either store -- correctly removable.

   @Test
   void noDependentsAnywhere_correctlyReportsRemovable() throws Exception {
      AssetEntry entryW = worksheetEntry("F/W3");
      seedFolderAndSheet("F", entryW);

      assertDoesNotThrow(() -> engine.checkSheetRemoveable(entryW, () -> "testuser"),
         "A worksheet with no dependents in either store must be reported removable -- guards "
         + "against a fix that over-corrects and always throws");
   }

   // Case 2 (secondary): guard A must keep rejecting a malformed QUERY_SCOPE-forced entry.

   @Test
   void guardA_malformedQueryScopeWorksheetEntry_stillThrowsInvalidEntry() {
      AssetEntry malformed =
         new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.WORKSHEET, "F/W4", null);

      assertThrows(MessageException.class,
         () -> engine.checkSheetRemoveable(malformed, () -> "testuser"),
         "Guard A (entry-validity/QUERY_SCOPE check) must reject this malformed entry with a "
         + "clear error today -- a fix that bypasses getSheetDependencies entirely must not turn "
         + "this into a silent removable-no-dependents answer");
   }

   // Guard B: storage == null must still be rejected with a clear error, not silently treated
   // as "no dependents".

   @Test
   void guardB_nullStorage_stillThrowsInvalidStorage() {
      AssetEntry entryW = worksheetEntry("F/W5");
      engine.istore = null;

      assertThrows(MessageException.class,
         () -> engine.checkSheetRemoveable(entryW, () -> "testuser"),
         "Guard B (storage == null) must reject this entry with a clear error today -- a fix "
         + "that bypasses getSheetDependencies entirely must not turn this into a silent "
         + "removable-no-dependents answer");
   }

   // Guard C: an entry whose parent folder does not actually contain it must still be rejected
   // with a clear error, not silently treated as "no dependents".

   @Test
   void guardC_entryNotContainedInParentFolder_stillThrowsNotContainedEntry() throws Exception {
      AssetEntry entryW = worksheetEntry("F/W6");
      seedFolderWithoutEntry("F");

      assertThrows(MessageException.class,
         () -> engine.checkSheetRemoveable(entryW, () -> "testuser"),
         "Guard C (parent-folder-containment check) must reject this entry with a clear error "
         + "today -- a fix that bypasses getSheetDependencies entirely must not turn this into a "
         + "silent removable-no-dependents answer");
   }

   private void seedFolderWithoutEntry(String folderPath) throws Exception {
      AssetEntry folder = folderEntry(folderPath);
      AssetFolder assetFolder =
         (AssetFolder) storage.getXMLSerializable(folder.toIdentifier(), null);

      if(assetFolder == null) {
         assetFolder = new AssetFolder();
         storage.putXMLSerializable(folder.toIdentifier(), assetFolder);
      }
   }

   private void seedFolderAndSheet(String folderPath, AssetEntry wsEntry) throws Exception {
      AssetEntry folder = folderEntry(folderPath);
      AssetFolder assetFolder =
         (AssetFolder) storage.getXMLSerializable(folder.toIdentifier(), null);

      if(assetFolder == null) {
         assetFolder = new AssetFolder();
         storage.putXMLSerializable(folder.toIdentifier(), assetFolder);
      }

      assetFolder.addEntry(wsEntry);
      storage.putXMLSerializable(wsEntry.toIdentifier(), new Worksheet());
   }

   private void seedCanonicalDependent(AssetEntry ws, AssetEntry dependent) throws Exception {
      String key = ws.toIdentifier(true);
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(new ArrayList<>(List.of((AssetObject) dependent)));
      DependencyStorageService.getInstance().put(key, info);
      seededCanonicalKeys.add(key);

      // Sanity check: this is a real, resolvable canonical-index record (not a no-op stub) --
      // the same check investigator-unreachable ran live before drawing any conclusion from it.
      assertFalse(DependencyTool.getDependencies(key).isEmpty(),
         "seeded canonical dependency record must itself be resolvable via DependencyTool"
         + ".getDependencies() -- otherwise this test would not distinguish a real staleness"
         + " bug from a broken test fixture");
   }

   private static AssetEntry worksheetEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null);
   }

   private static AssetEntry folderEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, path, null);
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
         return new HashSet<>();
      }

      @Override
      public Set<String> getKeys(Filter filter, String orgID) {
         return new HashSet<>();
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

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
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.util.IndexedStorage;
import inetsoft.util.StorageRefreshListener;
import inetsoft.util.TransformListener;
import inetsoft.util.XMLSerializable;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Regression for Redmine #76545 item 1: {@code AbstractAssetEngine.changeSheetDependents} only
 * propagated a rename into sheets an renamed sheet embeds ({@code getOuterDependents()}), never
 * the reverse -- sheets that embed the renamed sheet ({@code getOuterDependencies()}, the field
 * {@code checkSheetRemoveable}/{@code MVManager}/etc. read). When a folder-rename cascade
 * processes the embedder before the embedded sheet in both the away and back half of a
 * rename-away-then-back round trip, the embedded sheet's {@code getOuterDependencies()} is left
 * pointing at the sheet's stale, away-half location instead of its true final location.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               ChangeSheetDependentsRenameSilentDropTest.XRepositoryConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChangeSheetDependentsRenameSilentDropTest {
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
   }

   /**
    * Folder F contains worksheet A (no outer embeds) and worksheet B (an outer
    * {@code MirrorTableAssembly} pointing at A). The folder-rename cascade processes B (the
    * embedder) before A (the embedded sheet) in both the away (F -> F2) and back (F2 -> F)
    * halves of a round trip -- the ordering the diagnosis confirmed reproduces the defect.
    */
   @Test
   void renameRoundTripKeepsOuterDependenciesCurrent() throws Exception {
      AssetEntry entryFA = worksheetEntry("F/A");
      AssetEntry entryFB = worksheetEntry("F/B");
      AssetEntry entryF2A = worksheetEntry("F2/A");
      AssetEntry entryF2B = worksheetEntry("F2/B");

      Worksheet wsA = new Worksheet();
      EmbeddedTableAssembly tableA = new EmbeddedTableAssembly(wsA, "t1");
      wsA.addAssembly(tableA);
      wsA.setPrimaryAssembly(tableA);

      Worksheet wsB = new Worksheet();
      EmbeddedTableAssembly mirrorBase = new EmbeddedTableAssembly(new Worksheet(), "t1");
      MirrorTableAssembly mirror =
         new MirrorTableAssembly(wsB, "m1", entryFA, true, mirrorBase);
      wsB.addAssembly(mirror);
      wsB.setPrimaryAssembly(mirror);

      AssetFolder folderF = new AssetFolder();
      folderF.addEntry(entryFA);
      folderF.addEntry(entryFB);
      AssetFolder folderF2 = new AssetFolder();

      storage.putXMLSerializable(folderEntry("F").toIdentifier(), folderF);
      storage.putXMLSerializable(folderEntry("F2").toIdentifier(), folderF2);
      storage.putXMLSerializable(entryFA.toIdentifier(), wsA);
      storage.putXMLSerializable(entryFB.toIdentifier(), wsB);

      // Away half (F -> F2), embedder (B) before embedded (A).
      engine.changeSheet0(entryFB, storage, entryF2B, storage, true, false);
      engine.changeSheet0(entryFA, storage, entryF2A, storage, true, false);

      // Back half (F2 -> F), same embedder-first ordering.
      engine.changeSheet0(entryF2B, storage, entryFB, storage, true, false);
      engine.changeSheet0(entryF2A, storage, entryFA, storage, true, false);

      Worksheet finalA = (Worksheet) storage.getXMLSerializable(entryFA.toIdentifier(), null);
      Worksheet finalB = (Worksheet) storage.getXMLSerializable(entryFB.toIdentifier(), null);
      MirrorTableAssembly finalMirror = (MirrorTableAssembly) finalB.getPrimaryAssembly();

      // Fixed behavior: A's reverse-dependency index round-trips back to {F/B}, with no
      // dangling F2/B identifier left over from the away half.
      assertArrayEquals(new AssetEntry[] { entryFB }, finalA.getOuterDependencies());
      // B's own embed pointer also round-trips back to F/A.
      assertEquals(entryFA, finalMirror.getEntry());
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
    * Minimal in-memory {@link IndexedStorage} that stores live objects directly (no XML
    * encode/decode round trip) -- sufficient to exercise {@code changeSheet0}/
    * {@code changeSheetDependents}'s dependency bookkeeping, which is orthogonal to
    * serialization.
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

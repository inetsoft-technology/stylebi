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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XRepository;
import inetsoft.util.IndexedStorage;
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

import java.security.Principal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78662 (P-E): getSheet reads the stored XML and then puts it into the sheet cache without
 * any lock. A reader that loaded the OLD sheet before a save wrote the new one, and puts it into
 * the cache after the save cleared the cache, left the pre-save sheet cached until the next save
 * (a viewsheet then kept rendering a worksheet table that was deleted and saved). The cache insert
 * must be refused when the entry was cleared since the reader started loading.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               AbstractAssetEngineSheetCacheRaceTest.XRepositoryConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractAssetEngineSheetCacheRaceTest {
   @Configuration
   static class XRepositoryConfig {
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @Autowired
   private Cluster cluster;

   private ExecutorService executor;

   @BeforeEach
   void setUp() {
      executor = Executors.newSingleThreadExecutor();
      AssetRepository.IGNORE_PERM.set(true);
   }

   @AfterEach
   void tearDown() {
      executor.shutdownNow();
      AssetRepository.IGNORE_PERM.remove();
   }

   @Test
   void readerLoadedBeforeSave_doesNotCacheStaleSheetAfterClear() throws Exception {
      AssetEntry entry =
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "F/W", null);
      String id = entry.toIdentifier();
      AtomicReference<Worksheet> stored = new AtomicReference<>(sheet(1L));
      CountDownLatch loaded = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      // only the first (racing) read parks after it has loaded the old sheet
      AtomicReference<Boolean> first = new AtomicReference<>(true);

      IndexedStorage storage = mock(IndexedStorage.class);
      when(storage.getXMLSerializable(anyString(), any(TransformListener.class), anyString()))
         .thenAnswer(inv -> {
            XMLSerializable result = stored.get();

            if(first.getAndSet(false)) {
               loaded.countDown();
               release.await(10, TimeUnit.SECONDS);
            }

            return result;
         });
      when(storage.contains(anyString())).thenReturn(true);

      TestEngine engine = new TestEngine(cluster);
      engine.istore = storage;

      Principal user = () -> "u";
      Future<AbstractSheet> reader = executor.submit(
         () -> engine.getSheet(entry, user, false, AssetContent.ALL));

      // the reader has read the old XML but not yet cached it
      loaded.await(10, TimeUnit.SECONDS);
      // the writer saves a new sheet and clears the cache
      stored.set(sheet(2L));
      engine.clearCache(entry);
      release.countDown();
      reader.get(10, TimeUnit.SECONDS);

      // a later reader must see the saved sheet, not the one the slow reader loaded earlier
      AbstractSheet after = engine.getSheet(entry, user, false, AssetContent.ALL);
      assertEquals(2L, after.getLastModified(),
                   "the pre-save sheet was left in the sheet cache after the save");
   }

   private static Worksheet sheet(long modified) {
      Worksheet ws = new Worksheet();
      ws.setLastModified(modified);
      return ws;
   }

   private static final class TestEngine extends AbstractAssetEngine {
      TestEngine(Cluster cluster) {
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
}

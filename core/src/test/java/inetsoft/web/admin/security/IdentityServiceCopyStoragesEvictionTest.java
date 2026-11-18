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
package inetsoft.web.admin.security;

import inetsoft.mv.MVManager;
import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.Organization;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.BlobTransaction;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.util.IndexedStorage;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.RecycleBin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81573, with more stores in use than the cache of the storage manager holds, the copy of an
 * organization's storages must still copy every entry, and a clone whose copy fails must fail,
 * as an id change does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityServiceCopyStoragesEvictionTest {
   private static final int FILLERS = 60;
   private static final int ENTRIES = 12;

   @Autowired
   private BlobStorageManager blobStorageManager;

   private IdentityService service;
   private DashboardManager dashboardManager;

   @BeforeEach
   void setUp() {
      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      dashboardManager = mock(DashboardManager.class);
      ReflectionTestUtils.setField(service, "blobStorageManager", blobStorageManager);
      ReflectionTestUtils.setField(service, "dashboardManager", dashboardManager);
      ReflectionTestUtils.setField(service, "dependencyStorageService",
                                   mock(DependencyStorageService.class));
      ReflectionTestUtils.setField(service, "recycleBin", mock(RecycleBin.class));
      ReflectionTestUtils.setField(service, "indexedStorage", mock(IndexedStorage.class));
      ReflectionTestUtils.setField(service, "mvManager", mock(MVManager.class));
      ReflectionTestUtils.setField(service, "scheduleManager", mock(ScheduleManager.class));
      ReflectionTestUtils.setField(service, "libManagerProvider",
                                   mock(LibManagerProvider.class));
      ReflectionTestUtils.setField(service, "LOG",
                                   LoggerFactory.getLogger(IdentityService.class));
   }

   @Test
   void copyStoragesCopiesEveryEntryWhenTheStoreCacheIsFull() throws Exception {
      for(int trial = 0; trial < 5; trial++) {
         String from = "copyevict" + trial + "from";
         String to = "copyevict" + trial + "to";
         Set<String> autoSave = new TreeSet<>();
         Set<String> library = new TreeSet<>();

         for(int i = 0; i < ENTRIES; i++) {
            autoSave.add("auto" + i);
            library.add("lib" + i);
            seed(from.toLowerCase() + "__autoSave", "auto" + i, new AutoSaveUtils.Metadata());
            seed(from.toLowerCase() + "__library", "lib" + i, new LibManager.Metadata());
         }

         // the cache is full when the copy starts, every store it fetches evicts another one
         for(int k = 0; k < 3; k++) {
            for(int i = 0; i < FILLERS; i++) {
               blobStorageManager.getStorage("copyevictfill" + i + "__indexedStorage", true);
            }
         }

         service.copyStorages(new Organization(from), new Organization(to), trial % 2 == 0);

         assertEquals(autoSave, paths(to.toLowerCase() + "__autoSave"), "trial " + trial + " autosave");
         assertEquals(library, paths(to.toLowerCase() + "__library"), "trial " + trial + " library");
      }
   }

   @Test
   void cloneFailsWhenTheCopyFails() throws Exception {
      doThrow(new IllegalStateException("boom")).when(dashboardManager)
         .copyStorageData(anyString(), anyString());

      IllegalStateException cloneFailure = assertThrows(
         IllegalStateException.class,
         () -> service.copyStorages(new Organization("clonefailfrom"),
                                    new Organization("clonefailto"), false));
      assertTrue(cloneFailure.getMessage().contains("clonefailfrom"));
      assertEquals("boom", cloneFailure.getCause().getMessage());

      // the id change fails the same way
      assertThrows(IllegalStateException.class,
                   () -> service.copyStorages(new Organization("renamefailfrom"),
                                              new Organization("renamefailto"), true));
   }

   private <T extends java.io.Serializable> void seed(String store, String path, T metadata)
      throws Exception
   {
      BlobStorage<T> storage = blobStorageManager.getStorage(store, false);

      try(BlobTransaction<T> tx = storage.beginTransaction();
          OutputStream out = tx.newStream(path, metadata))
      {
         out.write(path.getBytes());
         tx.commit();
      }
   }

   private Set<String> paths(String store) {
      return blobStorageManager.getStorage(store, false).paths()
         .collect(Collectors.toCollection(TreeSet::new));
   }
}

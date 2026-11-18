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
package inetsoft.util;

/*
 * Bug #79879: a timestamp query of the blob indexed storage must not open the storage of every
 * organization. Opening blocks on a cluster load and the storage caches are bounded, so scanning
 * all organizations per event thrashed the caches and piled up event threads.
 */

import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BlobIndexedStorageLastModifiedScopeTest {
   private static final int ORG_COUNT = 120;

   private MockedStatic<SecurityEngine> securityStatic;
   private BlobStorageManager manager;
   private Principal oldPrincipal;
   private final List<String> opened = new ArrayList<>();
   private final Map<String, Long> stamps = new HashMap<>();

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getPrincipal();
      String[] ids = new String[ORG_COUNT];

      for(int i = 0; i < ORG_COUNT; i++) {
         ids[i] = "org" + i;
      }

      ids[0] = Organization.getDefaultOrganizationID();
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.getOrganizationIDs()).thenReturn(ids);
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.getSecurityProvider()).thenReturn(provider);
      securityStatic = mockStatic(SecurityEngine.class);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(engine);

      manager = mock(BlobStorageManager.class);
      when(manager.getStorage(anyString(), anyBoolean(), any())).thenAnswer(inv -> {
         String id = inv.getArgument(0);
         opened.add(id);
         BlobStorage storage = mock(BlobStorage.class);
         long ts = stamps.getOrDefault(id, 0L);
         when(storage.getLastModified()).thenReturn(Instant.ofEpochMilli(ts));
         when(storage.stream()).thenAnswer(i2 -> Stream.empty());
         return storage;
      });
   }

   @AfterEach
   void tearDown() {
      securityStatic.close();
      ThreadContext.setPrincipal(oldPrincipal);
   }

   private void actAsOrg(String orgID) {
      ThreadContext.setPrincipal(
         new XPrincipal(new IdentityID(XPrincipal.SYSTEM, orgID), new IdentityID[0],
                        new String[0], orgID));
   }

   @Test
   void lastModifiedOpensOnlyCurrentAndHostOrgStores() {
      actAsOrg("org77");
      stamps.put("org77__indexedStorage", 500L);
      stamps.put("org5__indexedStorage", 900L); // unrelated org, must not be consulted
      BlobIndexedStorage storage = new BlobIndexedStorage(manager);

      long ts = storage.lastModified();

      assertEquals(500L, ts);
      assertEquals(2, opened.size(), "opened: " + opened);
      assertTrue(opened.contains("org77__indexedStorage"));
      assertTrue(opened.contains(Organization.getDefaultOrganizationID().toLowerCase()
                                 + "__indexedStorage"));
   }

   @Test
   void filteredLastModifiedIsAlsoScoped() {
      actAsOrg("org42");
      BlobIndexedStorage storage = new BlobIndexedStorage(manager);

      storage.lastModified(path -> true);
      storage.lastModified(path -> true);

      assertEquals(4, opened.size(), "two storages per query, never ORG_COUNT: " + opened);
      assertFalse(opened.contains("org5__indexedStorage"));
   }

   @Test
   void hostOrgChangeIsStillSeenFromAnOrg() {
      actAsOrg("org42");
      String host = Organization.getDefaultOrganizationID().toLowerCase();
      stamps.put(host + "__indexedStorage", 700L);
      BlobIndexedStorage storage = new BlobIndexedStorage(manager);

      assertEquals(700L, storage.lastModified());
   }

   /**
    * The asset/repository engines compare lastModified() with the previous value and clear their
    * node-wide caches on any difference. Interleaved requests of different orgs must not flip it.
    */
   @Test
   void interleavedOrgsSeeAStableValue() {
      stamps.put("org1__indexedStorage", 300L);
      stamps.put("org2__indexedStorage", 800L);
      BlobIndexedStorage storage = new BlobIndexedStorage(manager);
      Set<Long> seen = new HashSet<>();
      long previous = -1;
      int changes = 0;

      for(int i = 0; i < 10; i++) {
         actAsOrg(i % 2 == 0 ? "org1" : "org2");
         long ts = storage.lastModified();
         seen.add(ts);

         if(previous != -1 && ts != previous) {
            changes++;
         }

         previous = ts;
      }

      // the first org1 call yields 300, the first org2 call 800; never flips back afterwards
      assertEquals(1, changes, "values seen: " + seen);
   }

   @Test
   void realChangeStillAdvancesTheValue() {
      stamps.put("org1__indexedStorage", 300L);
      BlobIndexedStorage storage = new BlobIndexedStorage(manager);
      actAsOrg("org1");
      assertEquals(300L, storage.lastModified());

      stamps.put("org1__indexedStorage", 400L);
      assertEquals(400L, storage.lastModified());
      assertEquals(400L, storage.lastModified());
   }

   @Test
   void unknownOrgIsNotOpened() {
      actAsOrg("deleted-org");
      BlobIndexedStorage storage = new BlobIndexedStorage(manager);

      storage.lastModified();

      assertFalse(opened.contains("deleted-org__indexedStorage"), "opened: " + opened);
   }
}

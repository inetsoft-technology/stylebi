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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.sree.internal.SUtil;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82422: any storage write cleared the whole CONTEXT sheet cache, so the next folder listing
 * re-read and DOM-parsed every child. CONTEXT entries are now kept across a storage change and
 * validated by the content digest of their key.
 *
 * Axis: cache validity varies with the content digest per key per org (checked on a hit) and the
 * generation per storage write (decides whether the digest is checked at all).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AbstractAssetEngineContextCacheTest.CountingStorageConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AbstractAssetEngineContextCacheTest {
   private static final AtomicInteger PARSES = new AtomicInteger();
   private static final AtomicInteger SEQ = new AtomicInteger();

   @Configuration
   static class CountingStorageConfig {
      @Bean
      IndexedStorage indexedStorage(BlobStorageManager manager) {
         return new BlobIndexedStorage(manager) {
            @Override
            public XMLSerializable getXMLSerializable(
               String key, TransformListener trans, String orgID) throws Exception
            {
               XMLSerializable value = super.getXMLSerializable(key, trans, orgID);

               if(value instanceof Viewsheet) {
                  PARSES.incrementAndGet();
               }

               return value;
            }
         };
      }
   }

   @Autowired AnalyticRepository analytic;
   private AssetRepository rep;

   private Principal oldPrincipal;
   private Principal admin;
   private String folderPath;
   private AssetEntry folder;

   @BeforeEach
   void setUp() throws Exception {
      rep = analytic.unwrap(AssetRepository.class);
      oldPrincipal = ThreadContext.getPrincipal();
      admin = SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false);
      ThreadContext.setPrincipal(admin);
      folderPath = "b82422f" + SEQ.incrementAndGet();
      folder = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, folderPath, null);
      rep.addFolder(folder, admin);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setPrincipal(oldPrincipal);
   }

   private AssetEntry child(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            folderPath + "/" + name, null);
   }

   private Viewsheet vs(String description) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setDescription(description);
      return vs;
   }

   private AssetEntry listed(String name) throws Exception {
      AssetEntry[] entries = rep.getEntries(folder, admin, inetsoft.sree.security.ResourceAction.READ);
      return Arrays.stream(entries).filter(e -> e.getName().equals(name)).findFirst().orElse(null);
   }

   private void touchStorage() throws Exception {
      Thread.sleep(20);
      rep.addFolder(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
                                   "b82422other" + SEQ.incrementAndGet(), null), admin);
   }

   @Test
   void unrelatedWriteDoesNotReparseChildren() throws Exception {
      for(int i = 0; i < 3; i++) {
         rep.setSheet(child("vs" + i), vs("d" + i), admin, true);
      }

      listed("vs0");
      int warm = PARSES.get();
      listed("vs0");
      assertEquals(warm, PARSES.get(), "second listing must be served from the cache");

      touchStorage();
      AssetEntry e = listed("vs1");

      assertEquals(warm, PARSES.get(), "an unrelated folder add must not re-parse children");
      assertEquals("d1", e.getProperty("Tooltip"));
   }

   @Test
   void contentChangedThroughStorageIsSeenByNextListing() throws Exception {
      rep.setSheet(child("vs0"), vs("old"), admin, true);
      rep.setSheet(child("vs1"), vs("same"), admin, true);
      assertEquals("old", listed("vs0").getProperty("Tooltip"));
      int warm = PARSES.get();

      // write straight into the storage so the engine's own clearCache is not involved
      Thread.sleep(20);
      IndexedStorage.getIndexedStorage().putXMLSerializable(child("vs0").toIdentifier(), vs("new"));

      assertEquals("new", listed("vs0").getProperty("Tooltip"));
      assertEquals("same", listed("vs1").getProperty("Tooltip"));
      assertEquals(warm + 1, PARSES.get(), "only the changed child is re-parsed");
   }

   @Test
   void childDeletedThroughStorageIsNotServedFromCache() throws Exception {
      AssetEntry c = child("vs0");
      rep.setSheet(c, vs("x"), admin, true);
      assertNotNull(rep.getSheet(c, admin, false, AssetContent.CONTEXT));

      Thread.sleep(20);
      assertTrue(IndexedStorage.getIndexedStorage().remove(c.toIdentifier()));
      touchStorage();

      assertNull(rep.getSheet(c, admin, false, AssetContent.CONTEXT));
   }

   @Test
   void digestIsScopedToTheOrganization() throws Exception {
      AssetEntry c = child("vs0");
      rep.setSheet(c, vs("x"), admin, true);
      IndexedStorage storage = IndexedStorage.getIndexedStorage();

      assertNotNull(storage.getDigest(c.toIdentifier(), "host-org"));
      assertNull(storage.getDigest(c.toIdentifier(), "other-org"));
   }
}

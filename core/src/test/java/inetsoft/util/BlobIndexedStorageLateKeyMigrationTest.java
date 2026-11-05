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
 * Bug #79856 (row S4 / part A): an organization id change copies the old org's indexed
 * storage from a key snapshot (migrateStorageData -> getKeys) and then drops the old store. A key
 * written to the old org after the snapshot was acknowledged but is neither copied nor kept.
 */

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BlobIndexedStorageLateKeyMigrationTest {
   @Autowired
   private BlobStorageManager blobStorageManager;

   /** Storage whose key snapshot of the old org is followed by one late write into that org. */
   private static final class LateWriteStorage extends BlobIndexedStorage {
      LateWriteStorage(BlobStorageManager manager, String oldOrg, String lateKey) {
         super(manager);
         this.oldOrg = oldOrg;
         this.lateKey = lateKey;
      }

      @Override
      public Set<String> getKeys(Filter filter, String orgID) {
         Set<String> keys = super.getKeys(filter, orgID);

         if(!fired && oldOrg.equals(orgID)) {
            fired = true;

            try {
               // the acknowledged write of a concurrent import, after the snapshot was taken
               putXMLSerializable(lateKey, new AssetFolder());
               lateWriteReturned = true;
            }
            catch(Exception e) {
               throw new RuntimeException(e);
            }
         }

         return keys;
      }

      private final String oldOrg;
      private final String lateKey;
      private boolean fired;
      private boolean lateWriteReturned;
   }

   private static String folderKey(String org, String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "/" + name,
                            (IdentityID) null, org).toIdentifier();
   }

   /** S4: a key written after the getKeys snapshot must not vanish from the renamed org. */
   @Test
   void keyWrittenAfterSnapshotReachesNewOrg() throws Exception {
      String oldOrg = "bug79856a";
      String newOrg = "bug79856b";
      String kKey = folderKey(oldOrg, "early");
      String xKey = folderKey(oldOrg, "late");
      LateWriteStorage storage = new LateWriteStorage(blobStorageManager, oldOrg, xKey);
      storage.putXMLSerializable(kKey, new AssetFolder());
      assertTrue(storage.contains(kKey, oldOrg), "setup");

      storage.copyStorageData(new Organization(oldOrg), new Organization(newOrg), true);
      assertTrue(storage.lateWriteReturned, "setup: the late put must have returned normally");
      assertTrue(storage.contains(folderKey(newOrg, "early"), newOrg),
                 "control: the key of the snapshot is copied");
      assertTrue(storage.contains(xKey, oldOrg), "the late key is in the old store before removal");

      storage.removeStorage(oldOrg);

      assertTrue(storage.contains(folderKey(newOrg, "late"), newOrg),
                 "the acknowledged late write is absent from the new org's store");
   }
}

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
package inetsoft.sree.security;

/*
 * Bug #79856: while an organization's id is being changed, writes into the old organization are
 * refused instead of being acknowledged and lost, and a late registry save can't recreate the
 * old organization's repository.xml.
 */

import inetsoft.sree.RepletRegistry;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OrgIdChangeGuardTest {
   @Autowired
   private BlobStorageManager blobStorageManager;

   private static String folderKey(String org, String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "/" + name,
                            (IdentityID) null, org).toIdentifier();
   }

   /** Runs the write on another thread, as a request on another node/thread would. */
   private static Throwable writeFromOtherThread(BlobIndexedStorage storage, String key)
      throws Exception
   {
      AtomicReference<Throwable> error = new AtomicReference<>();
      Thread t = new Thread(() -> {
         try {
            storage.putXMLSerializable(key, new AssetFolder());
         }
         catch(Throwable e) {
            error.set(e);
         }
      });
      t.start();
      t.join();
      return error.get();
   }

   @Test
   void indexedStorageWriteIsRefusedWhileOrgIdChanges() throws Exception {
      String org = "bug79856g1";
      BlobIndexedStorage storage = new BlobIndexedStorage(blobStorageManager);
      storage.putXMLSerializable(folderKey(org, "before"), new AssetFolder());

      OrgIdChangeGuard.begin(org);

      try {
         Throwable e = writeFromOtherThread(storage, folderKey(org, "late"));
         assertInstanceOf(OrgIdChangeGuard.OrganizationChangingException.class, e);
         assertFalse(storage.contains(folderKey(org, "late"), org), "refused write must not land");
         AtomicReference<Throwable> removeError = new AtomicReference<>();
         Thread t = new Thread(() -> {
            try {
               storage.remove(folderKey(org, "before"));
            }
            catch(Throwable ex) {
               removeError.set(ex);
            }
         });
         t.start();
         t.join();
         assertInstanceOf(OrgIdChangeGuard.OrganizationChangingException.class, removeError.get());
         assertTrue(storage.contains(folderKey(org, "before"), org));
         // the thread running the change is exempt
         storage.putXMLSerializable(folderKey(org, "owner"), new AssetFolder());
      }
      finally {
         OrgIdChangeGuard.end(org, false);
      }

      // a failed change leaves the organization writable
      assertNull(writeFromOtherThread(storage, folderKey(org, "after")));
   }

   @Test
   void completedChangeKeepsOldIdRefusedUntilItIsUsedAgain() throws Exception {
      String org = "bug79856g2";
      BlobIndexedStorage storage = new BlobIndexedStorage(blobStorageManager);
      OrgIdChangeGuard.begin(org);
      OrgIdChangeGuard.end(org, true);

      assertInstanceOf(OrgIdChangeGuard.OrganizationChangingException.class,
                       writeFromOtherThread(storage, folderKey(org, "late")));

      OrgIdChangeGuard.clear(org);
      assertNull(writeFromOtherThread(storage, folderKey(org, "reused")));
   }

   @Test
   void lateRegistrySaveDoesNotRecreateRepositoryFileOfChangingOrg() throws Exception {
      String org = "bug79856g3";
      String file = org + "/repository.xml";
      DataSpace space = DataSpace.getDataSpace();
      RepletRegistry registry = new RepletRegistry(org);
      registry.addFolder("early");
      registry.save();
      assertTrue(space.exists(null, file), "setup");

      OrgIdChangeGuard.begin(org);

      try {
         // copyDataSpace moves the old registry file away
         space.delete(null, file);
         registry.addFolder("late");
         AtomicReference<Throwable> error = new AtomicReference<>();
         Thread t = new Thread(() -> {
            try {
               registry.save();
            }
            catch(Throwable e) {
               error.set(e);
            }
         });
         t.start();
         t.join();
         assertInstanceOf(OrgIdChangeGuard.OrganizationChangingException.class, error.get());
         assertFalse(space.exists(null, file), "late save recreated " + file);
      }
      finally {
         OrgIdChangeGuard.end(org, true);
         OrgIdChangeGuard.clear(org);
         registry.shutdown();
      }
   }
}

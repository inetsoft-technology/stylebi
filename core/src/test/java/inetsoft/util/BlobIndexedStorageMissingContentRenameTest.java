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
 * Bug #82141: renaming a user threw a NullPointerException when the storage index listed a key
 * of the user whose content could not be read, because null was written back to the storage.
 */

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BlobIndexedStorageMissingContentRenameTest {
   @Autowired
   private IndexedStorage indexedStorage;

   @Test
   void userRename_skipsKeyWithMissingContent() throws Exception {
      IdentityID oldID = new IdentityID("ren82141", ORG);
      String key = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER, "f82141",
                                  oldID, ORG).toIdentifier(true);
      AssetFolder folder = new AssetFolder();
      indexedStorage.putXMLSerializable(key, folder);

      try {
         // the key is listed but its content cannot be read, getXMLSerializable returns null
         IndexedStorage spied = Mockito.spy(indexedStorage);
         Mockito.doReturn(null).when(spied).getXMLSerializable(
            Mockito.eq(key), Mockito.any(), Mockito.any());

         assertDoesNotThrow(() -> spied.migrateStorageData(
            oldID, new IdentityID("ren82141b", ORG), Identity.USER));
      }
      finally {
         indexedStorage.remove(key);
         indexedStorage.remove(new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER,
            "f82141", new IdentityID("ren82141b", ORG), ORG).toIdentifier(true));
      }
   }

   private static final String ORG = "host-org";
}

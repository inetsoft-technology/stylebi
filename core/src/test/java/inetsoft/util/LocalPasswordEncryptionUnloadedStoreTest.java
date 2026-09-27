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

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.KeyValueEngine;
import inetsoft.storage.UnloadedPropertiesTestConfiguration;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.storage.UnloadedPropertiesTestConfiguration.STORE;
import static inetsoft.storage.UnloadedPropertiesTestConfiguration.STORED_ENCRYPTION_KEY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76975: after the startup load of {@code sreeProperties} timed out, the property store is
 * empty but readable, so {@code getSecretKey} takes the missing {@code password.encryption.key}
 * as confirmed absent and generates a new one. Its save is applied to the stored properties,
 * replacing the key that every stored password is encrypted with.
 *
 * <p>See {@link UnloadedPropertiesTestConfiguration} for how the timed-out load is set up.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, UnloadedPropertiesTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LocalPasswordEncryptionUnloadedStoreTest {
   @Test
   void encryptionWithUnloadedPropertiesDoesNotReplaceStoredKey() throws Exception {
      assertTrue(UnloadedPropertiesTestConfiguration.firstLoadTimedOut(cluster),
                 "the startup load of " + STORE + " did not time out");
      // the early-loaded properties outlive a context in the test JVM and can still hold the
      // properties another test loaded, so load the empty store into fresh ones
      PropertiesEngine.getInstance().clear();
      PropertiesEngine.getInstance().init();
      RuntimeException refused = null;

      try {
         passwordEncryption.encryptPassword("secret");
      }
      catch(RuntimeException e) {
         refused = e;
      }

      UnloadedPropertiesTestConfiguration.flush(cluster);
      String refusal = String.valueOf(refused);
      assertEquals(STORED_ENCRYPTION_KEY, keyValueEngine.get(STORE, "password.encryption.key"),
                   () -> "a new password.encryption.key was saved over the stored key while " +
                      "the properties were not loaded (encryption refused: " + refusal + ")");
   }

   @Autowired
   private PasswordEncryption passwordEncryption;
   @Autowired
   private Cluster cluster;
   @Autowired
   private KeyValueEngine keyValueEngine;
}

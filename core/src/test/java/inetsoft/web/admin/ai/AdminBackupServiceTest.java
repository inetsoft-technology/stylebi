/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai;

import inetsoft.setup.StorageService;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.KeyValueConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code new StorageService(dir)} loads (or, absent a config file, defaults to) a "mapdb"
 * key-value engine, whose implementation lives in the separate {@code inetsoft-storage-mapdb}
 * module - not a dependency of {@code community/core} (that module is only pulled in downstream,
 * e.g. by {@code community/server}). To exercise a real {@code StorageService} round trip from
 * within {@code core}'s own test suite, this test writes an {@code inetsoft.yaml} into the temp
 * dir that selects the "test" key-value engine (`inetsoft.test.TestKeyValueEngine`, registered
 * via {@code @AutoService} in {@code core}'s test sources) instead of "mapdb" - the same
 * override used by {@code inetsoft.test.BaseTestConfiguration#inetsoftConfig}. The blob engine
 * stays at its default ("local" / filesystem), which core does ship. This is a real, ServiceLoader
 * -resolved {@link StorageService} backed by real engines - only the key-value backend differs
 * from a production "mapdb" deployment.
 */
@Tag("core")
class AdminBackupServiceTest {
   @Test
   void backupProducesRestorableRefRoundTrip(@TempDir Path dir) throws Exception {
      Path configFile = dir.resolve("inetsoft.yaml");
      InetsoftConfig config = InetsoftConfig.createDefault(dir);
      KeyValueConfig keyValue = new KeyValueConfig();
      keyValue.setType("test");
      config.setKeyValue(keyValue);
      InetsoftConfig.save(config, configFile);

      // Inject a StorageService pointed at a temp config dir (test ctor from Step 3).
      StorageService storage = new StorageService(dir.toString());

      try {
         AdminBackupService service = new AdminBackupService(storage, dir.toFile());

         String ref = service.backup("chg-1");
         assertNotNull(ref);
         File backupFile = service.resolve(ref);
         assertTrue(backupFile.exists(), "backup zip should exist at resolved ref");

         // restore should not throw for a ref we just produced
         assertDoesNotThrow(() -> service.restore(ref));
      }
      finally {
         storage.close();
      }
   }
}

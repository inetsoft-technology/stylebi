/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A thread that reads the cache directory while another thread is still switching it to a
 * new replet.cache.directory value must not get the old directory (bug #77533).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FileSystemServiceCacheDirectoryTest {
   @BeforeEach
   void saveProperty() {
      saved = SreeEnv.getProperty("replet.cache.directory");
   }

   @AfterEach
   void restoreProperty() {
      if(saved == null) {
         SreeEnv.remove("replet.cache.directory");
      }
      else {
         SreeEnv.setProperty("replet.cache.directory", saved);
      }
   }

   @Test
   void readerDuringSwitchDoesNotGetOldDirectory(@TempDir Path tempDir) throws Exception {
      String oldDir = tempDir.resolve("old").toString();
      String newDir = tempDir.resolve("new").toString();
      CountDownLatch switching = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Thread[] switcher = new Thread[1];

      // pause the switching thread while it resolves the new directory
      FileSystemService service = new FileSystemService(null, null) {
         @Override
         public Path getPath(String fileName, String... more) {
            if(Thread.currentThread() == switcher[0] && newDir.equals(fileName)) {
               switching.countDown();

               try {
                  release.await(10, TimeUnit.SECONDS);
               }
               catch(InterruptedException e) {
                  Thread.currentThread().interrupt();
               }
            }

            return super.getPath(fileName, more);
         }
      };

      SreeEnv.setProperty("replet.cache.directory", oldDir);
      assertEquals(oldDir, service.getCacheDirectory());
      SreeEnv.setProperty("replet.cache.directory", newDir);

      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         Future<String> switched = executor.submit(() -> {
            switcher[0] = Thread.currentThread();
            return service.getCacheDirectory();
         });

         assertTrue(switching.await(10, TimeUnit.SECONDS), "switch did not start");
         String read;

         try {
            read = service.getCacheDirectory();
         }
         finally {
            release.countDown();
         }

         assertEquals(newDir, read);
         assertEquals(newDir, switched.get(10, TimeUnit.SECONDS));
         assertEquals(newDir, service.getCacheDirectory());
      }
      finally {
         executor.shutdownNow();
      }
   }

   private String saved;
}

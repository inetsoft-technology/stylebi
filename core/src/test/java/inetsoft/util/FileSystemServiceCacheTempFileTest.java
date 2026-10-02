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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cache temp files get a name that is never reused in the JVM, so threads that create and
 * delete files with the same prefix never collide on a name that is still being deleted
 * (bug #77469).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FileSystemServiceCacheTempFileTest {
   @Test
   void namesAreNotReusedAfterDelete() throws Exception {
      FileSystemService service = FileSystemService.getInstance();
      Set<String> names = new HashSet<>();

      for(int i = 0; i < 2000; i++) {
         File file = service.getCacheTempFile("tempFileReuse", "dat");
         assertNotNull(file, "temp file " + i);
         assertTrue(names.add(file.getName()), "name reused: " + file.getName());
         Files.delete(file.toPath());
      }
   }

   // Tool.getCacheTempFile delegates to FileSystemService, so it has the same guarantee
   // (bug #77525)
   @Test
   void toolNamesAreNotReusedAfterDelete() throws Exception {
      Set<String> names = new HashSet<>();

      for(int i = 0; i < 2000; i++) {
         File file = Tool.getCacheTempFile("toolTempFileReuse", "dat");
         assertNotNull(file, "temp file " + i);
         assertTrue(names.add(file.getName()), "name reused: " + file.getName());
         Files.delete(file.toPath());
      }
   }

   @Test
   void concurrentCreateAndDeleteNeverReturnsNull() throws Exception {
      FileSystemService service = FileSystemService.getInstance();
      int threads = 4;
      long end = System.currentTimeMillis() + 3000;
      AtomicInteger nulls = new AtomicInteger();
      AtomicInteger created = new AtomicInteger();
      Set<String> names = ConcurrentHashMap.newKeySet();
      ExecutorService executor = Executors.newFixedThreadPool(threads);

      try {
         List<Future<?>> futures = new ArrayList<>();

         for(int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
               while(System.currentTimeMillis() < end) {
                  File file = service.getCacheTempFile("tempFileRace", "dat");

                  if(file == null) {
                     nulls.incrementAndGet();
                     continue;
                  }

                  created.incrementAndGet();
                  names.add(file.getName());
                  Files.write(file.toPath(), new byte[] { 1, 2, 3 });
                  Files.delete(file.toPath());
               }

               return null;
            }));
         }

         for(Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
         }
      }
      finally {
         executor.shutdownNow();
      }

      assertEquals(0, nulls.get(), "null temp files out of " + created.get());
      assertEquals(created.get(), names.size(), "distinct names");
   }
}

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
package inetsoft.report.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import inetsoft.web.json.ThirdPartySupportModule;
import inetsoft.web.json.TypedPropertyMapWrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77227: the runtime sheet properties (e.g. __EXPORTING__) are read and written by
 * concurrent requests of the same sheet without the sheet monitor, so the map must be safe for
 * concurrent use while keeping the "null value removes the property" contract.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RuntimeSheetPropTest {
   @Test
   void nullValueRemovesAndNullKeyIsIgnored() {
      RuntimeViewsheet rvs = new RuntimeViewsheet();
      rvs.setProperty("__EXPORTING__", "true");
      assertEquals("true", rvs.getProperty("__EXPORTING__"));

      rvs.setProperty("__EXPORTING__", null);
      assertNull(rvs.getProperty("__EXPORTING__"));

      assertDoesNotThrow(() -> rvs.setProperty(null, "x"));
      assertDoesNotThrow(() -> rvs.setProperty(null, null));
      assertNull(rvs.getProperty(null));
   }

   @Test
   void loadedPropertiesSkipNullValues() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new ThirdPartySupportModule());
      Map<String, Object> saved = new HashMap<>();
      saved.put("kept", "value");
      saved.put("dropped", null);
      String json = mapper.writeValueAsString(new TypedPropertyMapWrapper(saved));

      Method load = RuntimeSheet.class.getDeclaredMethod(
         "loadPropMap", String.class, ObjectMapper.class);
      load.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Object> loaded = (Map<String, Object>) load.invoke(new RuntimeViewsheet(), json, mapper);

      assertInstanceOf(ConcurrentMap.class, loaded);
      assertEquals("value", loaded.get("kept"));
      assertFalse(loaded.containsKey("dropped"));

      Map<?, ?> empty = (Map<?, ?>) load.invoke(new RuntimeViewsheet(), null, mapper);
      assertInstanceOf(ConcurrentMap.class, empty);
   }

   /** Saving the properties while other requests change them must not fail. */
   @Test
   void saveWhileOtherThreadsWrite() throws Exception {
      RuntimeViewsheet rvs = new RuntimeViewsheet();
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new ThirdPartySupportModule());
      Method save = RuntimeSheet.class.getDeclaredMethod(
         "savePropMap", Map.class, ObjectMapper.class);
      save.setAccessible(true);
      Field propField = RuntimeSheet.class.getDeclaredField("prop");
      propField.setAccessible(true);
      Map<?, ?> prop = (Map<?, ?>) propField.get(rvs);
      // never empty, so a null result can only mean the save failed (it logs and returns null)
      rvs.setProperty("stable", "x");

      ExecutorService pool = Executors.newFixedThreadPool(2);
      CountDownLatch stop = new CountDownLatch(1);

      try {
         List<Future<?>> writers = new ArrayList<>();

         for(int t = 0; t < 2; t++) {
            int id = t;
            writers.add(pool.submit(() -> {
               for(int i = 0; stop.getCount() > 0; i++) {
                  rvs.setProperty("k" + id + "_" + (i % 64), i % 2 == 0 ? "v" : null);
                  rvs.getProperty("__EXPORTING__");
               }

               return null;
            }));
         }

         for(int i = 0; i < 200; i++) {
            assertNotNull(save.invoke(rvs, prop, mapper), "saving the properties failed");
         }

         stop.countDown();

         for(Future<?> writer : writers) {
            writer.get(10, TimeUnit.SECONDS);
         }
      }
      finally {
         stop.countDown();
         pool.shutdownNow();
      }
   }
}

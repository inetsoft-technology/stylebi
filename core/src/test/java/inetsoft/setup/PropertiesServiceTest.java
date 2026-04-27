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
package inetsoft.setup;

import inetsoft.storage.KeyValueEngine;
import inetsoft.test.TestKeyValueEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PropertiesService}.
 *
 * <p>Because {@code PropertiesService} reads an {@code inetsoft.yaml} config file via
 * {@code AbstractStorageService}, we write a minimal YAML that sets the key-value engine
 * type to "test" (backed by {@link TestKeyValueEngine}) into a temporary directory, then
 * construct the service from that directory. We also inject the {@link TestKeyValueEngine}
 * directly via reflection as a fallback to ensure tests always run against the in-memory engine.
 */
class PropertiesServiceTest {

   @TempDir
   Path tempDir;

   private PropertiesService service;
   private TestKeyValueEngine engine;

   @BeforeEach
   void setUp() throws Exception {
      // Write a minimal inetsoft.yaml that declares key-value type "test".
      // InetsoftConfig setters require non-null cluster, keyValue, and blob — all must be
      // present in the YAML when Jackson deserialises it (Objects.requireNonNull in setters).
      String blobDir = tempDir.resolve("blob").toFile().getAbsolutePath();
      File yaml = tempDir.resolve("inetsoft.yaml").toFile();
      try(PrintWriter writer = new PrintWriter(yaml)) {
         writer.println("keyValue:");
         writer.println("  type: test");
         writer.println("cluster:");
         writer.println("  multicastEnabled: false");
         writer.println("blob:");
         writer.println("  type: local");
         writer.println("  filesystem:");
         writer.println("    directory: \"" + blobDir.replace("\\", "/") + "\"");
      }

      service = new PropertiesService(tempDir.toFile().getAbsolutePath());

      // Retrieve the actual engine instance from the service via reflection so tests can
      // inspect / pre-populate state if needed.
      Field field = PropertiesService.class.getDeclaredField("keyValueEngine");
      field.setAccessible(true);
      KeyValueEngine kv = (KeyValueEngine) field.get(service);

      // If the engine is not a TestKeyValueEngine (e.g., ServiceLoader found something
      // else first), replace it with a fresh TestKeyValueEngine so tests are deterministic.
      if(!(kv instanceof TestKeyValueEngine)) {
         engine = new TestKeyValueEngine();
         field.set(service, engine);
      }
      else {
         engine = (TestKeyValueEngine) kv;
      }
   }

   @AfterEach
   void tearDown() throws Exception {
      service.close();
   }

   // ---- get ----

   @Test
   void get_existingKey_returnsValue() {
      engine.put("sreeProperties", "color", "blue");
      assertEquals("blue", service.get("color"));
   }

   @Test
   void get_missingKey_returnsNull() {
      assertNull(service.get("nonexistent"));
   }

   // ---- put then get ----

   @Test
   void put_thenGet_retrievesStoredValue() {
      service.put("timeout", "30");
      assertEquals("30", service.get("timeout"));
   }

   @Test
   void put_overwritesExistingValue() {
      service.put("key", "old");
      service.put("key", "new");
      assertEquals("new", service.get("key"));
   }

   // ---- remove ----

   @Test
   void remove_existingKey_keyNoLongerAccessible() {
      service.put("prop", "value");
      service.remove("prop");
      assertNull(service.get("prop"));
   }

   @Test
   void remove_missingKey_idempotent() {
      // Removing a key that was never set should not throw
      service.remove("neverSet");
      assertNull(service.get("neverSet"));
   }

   @Test
   void remove_calledTwice_noException() {
      service.put("key", "v");
      service.remove("key");
      service.remove("key"); // second call should be safe
      assertNull(service.get("key"));
   }

   // ---- putIfAbsent ----

   @Test
   void putIfAbsent_absentKey_storesValue() {
      service.putIfAbsent("newKey", "hello");
      assertEquals("hello", service.get("newKey"));
   }

   @Test
   void putIfAbsent_presentNonEmptyKey_skips() {
      service.put("existingKey", "existing");
      service.putIfAbsent("existingKey", "shouldNotOverwrite");
      assertEquals("existing", service.get("existingKey"));
   }

   @Test
   void putIfAbsent_presentEmptyValue_overwrites() {
      service.put("emptyKey", "");
      service.putIfAbsent("emptyKey", "replacement");
      assertEquals("replacement", service.get("emptyKey"));
   }

   // ---- putNonnullIfAbsent ----

   @Test
   void putNonnullIfAbsent_nullValue_skips() {
      service.putNonnullIfAbsent("key", null);
      assertNull(service.get("key"));
   }

   @Test
   void putNonnullIfAbsent_emptyStringValue_skips() {
      service.putNonnullIfAbsent("key", "");
      assertNull(service.get("key"));
   }

   @Test
   void putNonnullIfAbsent_validValueAbsent_stores() {
      service.putNonnullIfAbsent("key", "value");
      assertEquals("value", service.get("key"));
   }

   @Test
   void putNonnullIfAbsent_validValuePresent_skips() {
      service.put("key", "original");
      service.putNonnullIfAbsent("key", "newValue");
      assertEquals("original", service.get("key"));
   }

   @Test
   void putNonnullIfAbsent_validValuePresentButEmpty_overwrites() {
      service.put("key", "");
      service.putNonnullIfAbsent("key", "filled");
      assertEquals("filled", service.get("key"));
   }
}

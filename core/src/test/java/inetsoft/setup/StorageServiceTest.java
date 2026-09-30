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

import inetsoft.storage.Blob;
import inetsoft.storage.fs.FilesystemBlobEngine;
import inetsoft.test.TestKeyValueEngine;
import inetsoft.util.DataSpace;
import inetsoft.util.config.InetsoftConfig;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StorageService} writes to the data space, which must leave every ancestor
 * folder of a written file as a directory marker (a key with a {@code null} digest).
 */
@Tag("core")
class StorageServiceTest {
   @TempDir
   Path tempDir;

   private TestKeyValueEngine keyValueEngine;
   private FilesystemBlobEngine blobEngine;
   private StorageService service;

   @BeforeEach
   void setUp() {
      keyValueEngine = new TestKeyValueEngine();
      blobEngine = new FilesystemBlobEngine(tempDir.resolve("blob"));
      service = new StorageService(new InetsoftConfig(), keyValueEngine, blobEngine);
   }

   @Test
   void nestedFileCreatesMarkerForEveryAncestor() throws Exception {
      service.write("a/b/c/f.txt", file("f.txt", "f"));

      assertDirectory("a");
      assertDirectory("a/b");
      assertDirectory("a/b/c");
      assertFile("a/b/c/f.txt");
      assertEquals(Set.of("a", "a/b", "a/b/c", "a/b/c/f.txt"), keys());
   }

   @Test
   void rootFileCreatesNoMarker() throws Exception {
      service.write("root.txt", file("root.txt", "root"));

      assertFile("root.txt");
      assertEquals(Set.of("root.txt"), keys());
   }

   @Test
   void existingAncestorMarkerIsNotRewritten() throws Exception {
      service.createDirectory("a");
      Blob<DataSpace.Metadata> marker = keyValueEngine.get("dataSpace", "a");

      service.write("a/f.txt", file("f.txt", "f"));
      service.write("a/b/g.txt", file("g.txt", "g"));
      Blob<DataSpace.Metadata> subMarker = keyValueEngine.get("dataSpace", "a/b");
      service.write("a/b/h.txt", file("h.txt", "h"));

      assertSame(marker, keyValueEngine.get("dataSpace", "a"));
      assertSame(subMarker, keyValueEngine.get("dataSpace", "a/b"));
      assertEquals(Set.of("a", "a/f.txt", "a/b", "a/b/g.txt", "a/b/h.txt"), keys());
   }

   @Test
   void fileAncestorIsRejectedWithoutWritingAnything() throws Exception {
      service.write("x", file("x", "x"));
      Blob<DataSpace.Metadata> existing = keyValueEngine.get("dataSpace", "x");

      IOException e = assertThrows(
         IOException.class, () -> service.write("x/y/z.txt", file("z.txt", "z")));

      assertTrue(e.getMessage().contains("x is a file"), e.getMessage());
      assertSame(existing, keyValueEngine.get("dataSpace", "x"));
      assertNotNull(existing.getDigest());
      assertEquals(Set.of("x"), keys());
      assertFalse(blobEngine.exists("dataSpace", DigestUtils.md5Hex("z")));
   }

   @Test
   void backslashPathCreatesMarkers() throws Exception {
      service.write("portal\\theme\\x.jar", file("x.jar", "jar"));

      assertDirectory("portal");
      assertDirectory("portal/theme");
      assertFile("portal/theme/x.jar");
      assertEquals(Set.of("portal", "portal/theme", "portal/theme/x.jar"), keys());
   }

   @Test
   void emptySegmentsDoNotCreateMarkers() throws Exception {
      service.write("/lead/f.txt", file("f.txt", "f"));
      service.write("a//b/g.txt", file("g.txt", "g"));
      service.write("./c/h.txt", file("h.txt", "h"));
      service.write("", file("empty.txt", "empty"));

      assertDirectory("lead");
      assertDirectory("a");
      assertDirectory("a/b");
      assertDirectory("c");
      // the file keys themselves are written as given
      assertEquals(
         Set.of("/lead/f.txt", "lead", "a//b/g.txt", "a", "a/b", "./c/h.txt", "c", ""), keys());
   }

   @Test
   void importFilesCreatesMarkersForNestedFolders() throws Exception {
      Path root = tempDir.resolve("files");
      Files.createDirectories(root.resolve("portal/theme"));
      Files.writeString(root.resolve("portal/theme/x.jar"), "jar");
      Files.writeString(root.resolve("portal/y.txt"), "y");
      Files.writeString(root.resolve("root.txt"), "root");

      Method method = StorageInitializer.class.getDeclaredMethod(
         "importFiles", StorageService.class, File.class, File.class);
      method.setAccessible(true);
      method.invoke(new StorageInitializer(), service, root.toFile(), root.toFile());

      assertDirectory("portal");
      assertDirectory("portal/theme");
      assertFile("portal/theme/x.jar");
      assertFile("portal/y.txt");
      assertFile("root.txt");
      assertEquals(
         Set.of("portal", "portal/theme", "portal/theme/x.jar", "portal/y.txt", "root.txt"),
         keys());
   }

   private File file(String name, String content) throws IOException {
      Path dir = Files.createTempDirectory(tempDir, "src");
      Path file = dir.resolve(name);
      Files.writeString(file, content, StandardCharsets.UTF_8);
      return file.toFile();
   }

   private Set<String> keys() {
      return keyValueEngine.<Blob<DataSpace.Metadata>>stream("dataSpace")
         .map(pair -> pair.getKey())
         .collect(Collectors.toSet());
   }

   private void assertDirectory(String path) {
      Blob<DataSpace.Metadata> blob = keyValueEngine.get("dataSpace", path);
      assertNotNull(blob, path + " does not exist");
      assertNull(blob.getDigest(), path + " is not a directory");
      assertEquals(path, blob.getPath());
      assertInstanceOf(DataSpace.Metadata.class, blob.getMetadata());
   }

   private void assertFile(String path) {
      Blob<DataSpace.Metadata> blob = keyValueEngine.get("dataSpace", path);
      assertNotNull(blob, path + " does not exist");
      assertNotNull(blob.getDigest(), path + " is not a file");
      assertTrue(blobEngine.exists("dataSpace", blob.getDigest()), path + " content is missing");
   }
}

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
package inetsoft.storage;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BlobCacheTest {
   @TempDir
   Path tempDir;

   private BlobCache cache;
   private Blob<Serializable> directoryBlob;

   @BeforeEach
   void setUp() {
      BlobEngine engine = mock(BlobEngine.class);
      cache = new BlobCache(tempDir, engine);
      directoryBlob = new Blob<>("some/dir", null, 0L, Instant.now(), null);
   }

   /**
    * Directory blobs have a null digest. Verify that calling remove(storeId, String) with a
    * null digest returns cleanly rather than throwing a NullPointerException on
    * digest.substring(0, 2).
    */
   @Test
   void remove_withNullDigest_returnsCleanlyWithoutNPE() {
      assertDoesNotThrow(() -> cache.remove("test-store", (String) null));
   }

   /**
    * Directory blobs have a null digest. Verify that calling remove(storeId, Blob) with a
    * directory blob also returns cleanly — consistent with the String overload — rather than
    * throwing an IOException from the internal getPath() helper.
    */
   @Test
   void remove_withDirectoryBlob_returnsCleanlyWithoutException() {
      assertDoesNotThrow(() -> cache.remove("test-store", directoryBlob));
   }

   /**
    * Directory blobs have no binary data. Verify that put(storeId, Blob, Path) throws an
    * IOException rather than passing a null digest down to the engine.
    */
   @Test
   void put_throwsIOException_forDirectoryBlob() throws Exception {
      Path tempFile = Files.createTempFile(tempDir, "blob", ".dat");
      IOException thrown = assertThrows(IOException.class,
                                        () -> cache.put("test-store", directoryBlob, tempFile));
      assertTrue(thrown.getMessage().contains("directory blob"),
                 "Expected message to mention 'directory blob', got: " + thrown.getMessage());
   }

   /**
    * Directory blobs have a null digest. Verify that get(storeId, Blob) throws an IOException
    * with a meaningful message rather than causing an NPE on digest.substring().
    */
   @Test
   void get_throwsIOException_forDirectoryBlob() {
      IOException thrown = assertThrows(IOException.class,
                                        () -> cache.get("test-store", directoryBlob));
      assertTrue(thrown.getMessage().contains("directory blob"),
                 "Expected message to mention 'directory blob', got: " + thrown.getMessage());
   }
}

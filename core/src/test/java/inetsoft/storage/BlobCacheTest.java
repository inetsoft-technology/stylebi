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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class BlobCacheTest {
   @TempDir
   Path tempDir;

   /**
    * Directory blobs have a null digest. Verify that calling remove(storeId, String) with a
    * null digest returns cleanly rather than throwing a NullPointerException on
    * digest.substring(0, 2).
    */
   @Test
   void remove_withNullDigest_returnsCleanlyWithoutNPE() {
      BlobEngine engine = mock(BlobEngine.class);
      BlobCache cache = new BlobCache(tempDir, engine);
      assertDoesNotThrow(() -> cache.remove("test-store", (String) null));
   }

   /**
    * Directory blobs have a null digest. Verify that calling remove(storeId, Blob) with a
    * directory blob also returns cleanly — consistent with the String overload — rather than
    * throwing an IOException from the internal getPath() helper.
    */
   @Test
   void remove_withDirectoryBlob_returnsCleanlyWithoutException() {
      BlobEngine engine = mock(BlobEngine.class);
      BlobCache cache = new BlobCache(tempDir, engine);
      Blob<Serializable> directoryBlob = new Blob<>("some/dir", null, 0L, Instant.now(), null);
      assertDoesNotThrow(() -> cache.remove("test-store", directoryBlob));
   }
}

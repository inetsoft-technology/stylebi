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
package inetsoft.storage.fs;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FilesystemBlobEngine}, in particular the idempotent-delete behavior needed to
 * tolerate two concurrent "last reference removed" decisions for the same content-hash digest
 * (Bug #77298).
 */
@Tag("core")
class FilesystemBlobEngineTest {
   @TempDir
   Path tempDir;

   /**
    * The refcount bookkeeping in {@code PutBlobTask}/{@code DeleteBlobTask} can, under a
    * cluster-singleton-service redeployment race, hand out two independent "this digest's last
    * reference was just removed" decisions for the same digest. The second physical delete must
    * be a no-op rather than throwing, exactly like the analogous {@code BlobCache.remove()}.
    */
   @Test
   void delete_whenFileAlreadyRemoved_doesNotThrow() {
      FilesystemBlobEngine engine = new FilesystemBlobEngine(tempDir);
      String digest = "0123456789abcdef0123456789abcdef";

      // first delete of a digest that was never written is already a no-op (file never existed).
      assertDoesNotThrow(() -> engine.delete("test-store", digest));

      // simulate: write the blob, then have two independent callers both decide to delete it.
      assertDoesNotThrow(() -> {
         Path source = Files.createTempFile(tempDir, "src", ".dat");
         Files.writeString(source, "content");
         engine.write("test-store", digest, source);
      });

      assertTrue(engine.exists("test-store", digest));
      assertDoesNotThrow(() -> engine.delete("test-store", digest));
      assertFalse(engine.exists("test-store", digest));

      // second, independent delete decision for the same (now-missing) digest must not throw.
      assertDoesNotThrow(() -> engine.delete("test-store", digest));
   }
}

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
package inetsoft.storage;

import inetsoft.storage.fs.FilesystemBlobEngine;
import inetsoft.test.TestKeyValueEngine;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trips a backup through {@link DirectStorageTransfer} (export then import into fresh engines)
 * to verify the non-mapdb restore path used by {@code StorageTransfer.create}. Unlike
 * {@code ClusterStorageTransfer}, the direct path writes straight to the engines and never fires the
 * live change-listeners, so the import cannot NPE on a not-yet-imported parent folder.
 */
@Tag("core")
class DirectStorageTransferRoundTripTest {
   @TempDir
   Path tempDir;

   @Test
   void keyValueRoundTrip() throws Exception {
      KeyValueEngine srcKv = new TestKeyValueEngine();
      BlobEngine srcBlob = new FilesystemBlobEngine(tempDir.resolve("src-blob"));
      srcKv.put("sreeProperties", "a.prop", "valueA");
      srcKv.put("sreeProperties", "b.prop", "valueB");
      srcKv.put("otherStore", "x", "1");

      Path zip = exportTo("kv.zip", srcKv, srcBlob);

      KeyValueEngine tgtKv = new TestKeyValueEngine();
      BlobEngine tgtBlob = new FilesystemBlobEngine(tempDir.resolve("tgt-blob"));
      new DirectStorageTransfer(tgtKv, tgtBlob).importContents(zip);

      assertEquals("valueA", tgtKv.get("sreeProperties", "a.prop"));
      assertEquals("valueB", tgtKv.get("sreeProperties", "b.prop"));
      assertEquals("1", tgtKv.get("otherStore", "x"));

      // no spurious entries beyond what was exported
      assertEquals(2, tgtKv.size("sreeProperties"));
      assertEquals(1, tgtKv.size("otherStore"));
   }

   @Test
   void blobRoundTrip() throws Exception {
      KeyValueEngine srcKv = new TestKeyValueEngine();
      BlobEngine srcBlob = new FilesystemBlobEngine(tempDir.resolve("src-blob"));
      byte[] content = "blob-bytes-1234567890".getBytes(StandardCharsets.UTF_8);
      Path srcFile = tempDir.resolve("content.dat");
      Files.write(srcFile, content);
      String digest = DigestUtils.md5Hex(content);
      String storeId = "blobStore";
      srcBlob.write(storeId, digest, srcFile);
      Blob<Serializable> blob =
         new Blob<>("path/to/file", digest, content.length, Instant.now(), null);
      srcKv.put(storeId, "the-key", blob);

      Path zip = exportTo("blob.zip", srcKv, srcBlob);

      KeyValueEngine tgtKv = new TestKeyValueEngine();
      BlobEngine tgtBlob = new FilesystemBlobEngine(tempDir.resolve("tgt-blob"));
      new DirectStorageTransfer(tgtKv, tgtBlob).importContents(zip);

      Blob<?> imported = tgtKv.get(storeId, "the-key");
      assertNotNull(imported, "blob entry should be imported into the key-value store");
      assertEquals(digest, imported.getDigest());
      assertTrue(tgtBlob.exists(storeId, digest), "blob bytes should be written to the blob engine");

      Path readBack = tempDir.resolve("readback.dat");
      tgtBlob.read(storeId, digest, readBack);
      assertArrayEquals(content, Files.readAllBytes(readBack));
   }

   private Path exportTo(String name, KeyValueEngine kv, BlobEngine blob) throws Exception {
      Path zip = tempDir.resolve(name);

      try(OutputStream out = Files.newOutputStream(zip)) {
         new DirectStorageTransfer(kv, blob).exportContents(out);
      }

      return zip;
   }
}

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
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.BlobTransaction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Serializable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A backup that only exists on the node that produced it is not a reversibility mechanism: in a
 * cluster the load balancer may route the later restore anywhere. These tests pin that the ZIP is
 * published to cluster-visible BlobStorage and fetched back from it, so backupRef is node-agnostic.
 *
 * The internal rollback inside a single apply is unaffected - it runs in the same request on the
 * same node and uses beforeValue, not the ZIP. Only the separate restore call needs this.
 */
@Tag("core")
class AdminBackupServiceClusterTest {
   @Test
   void publishesTheBackupToBlobStorage(@TempDir Path dir) throws Exception {
      StorageService storage = mock(StorageService.class);
      BlobStorage<Serializable> blobs = mock(BlobStorage.class);
      BlobStorageManager manager = mock(BlobStorageManager.class);
      when(manager.<Serializable>getStorage(anyString(), anyBoolean())).thenReturn(blobs);
      // Writes go through a BlobTransaction; there is no write(path, InputStream).
      BlobTransaction<Serializable> tx = mock(BlobTransaction.class);
      when(blobs.beginTransaction()).thenReturn(tx);
      when(tx.newStream(anyString(), any())).thenReturn(new ByteArrayOutputStream());
      // StorageService.backup(File) writes the ZIP locally; capture that it happened.
      doAnswer(inv -> {
         Files.writeString(((File) inv.getArgument(0)).toPath(), "zip-bytes");
         return null;
      }).when(storage).backup(any(File.class));

      AdminBackupService service =
         new AdminBackupService(storage, manager, dir.toFile());
      String ref = service.backup("chg-1");

      assertTrue(ref.startsWith("admin-chg-1-"));
      assertTrue(ref.endsWith(".zip"));
      verify(storage).backup(any(File.class));
      // Published under a namespaced path so it cannot collide with other blob data.
      verify(tx).newStream(contains(ref), isNull());
      verify(tx).commit();
   }

   @Test
   void restoreFetchesTheBackupFromBlobStorageWhenAbsentLocally(@TempDir Path dir)
      throws Exception
   {
      StorageService storage = mock(StorageService.class);
      BlobStorage<Serializable> blobs = mock(BlobStorage.class);
      BlobStorageManager manager = mock(BlobStorageManager.class);
      when(manager.<Serializable>getStorage(anyString(), anyBoolean())).thenReturn(blobs);
      when(blobs.exists(anyString())).thenReturn(true);
      when(blobs.getInputStream(anyString()))
         .thenReturn(new ByteArrayInputStream("zip-bytes".getBytes(StandardCharsets.UTF_8)));

      AdminBackupService service = new AdminBackupService(storage, manager, dir.toFile());
      service.restore("admin-chg-1-123.zip");

      verify(blobs).getInputStream(contains("admin-chg-1-123.zip"));
      verify(storage).restore(any(File.class));
   }

   @Test
   void restoreFailsLoudlyWhenTheBackupIsNowhere(@TempDir Path dir) {
      StorageService storage = mock(StorageService.class);
      BlobStorage<Serializable> blobs = mock(BlobStorage.class);
      BlobStorageManager manager = mock(BlobStorageManager.class);
      when(manager.<Serializable>getStorage(anyString(), anyBoolean())).thenReturn(blobs);
      when(blobs.exists(anyString())).thenReturn(false);

      AdminBackupService service = new AdminBackupService(storage, manager, dir.toFile());

      // Silently succeeding here would leave an operator believing a rollback path exists.
      assertTrue(assertThrows(Exception.class,
         () -> service.restore("admin-missing-1.zip")).getMessage().contains("admin-missing-1.zip"));
   }

   @Test
   void stillRejectsAnUnsafeBackupRef(@TempDir Path dir) {
      StorageService storage = mock(StorageService.class);
      BlobStorageManager manager = mock(BlobStorageManager.class);
      AdminBackupService service = new AdminBackupService(storage, manager, dir.toFile());

      assertThrows(IllegalArgumentException.class, () -> service.restore("../escape.zip"));
      assertThrows(IllegalArgumentException.class, () -> service.backup("../escape"));
   }
}

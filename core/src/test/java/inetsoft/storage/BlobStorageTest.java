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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedLong;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.*;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BlobStorageTest {
   private Cluster mockCluster;
   private KeyValueStorage<Blob<Serializable>> mockStorage;
   private Blob<Serializable> directoryBlob;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      mockCluster = mock(Cluster.class);
      DistributedLong mockLong = mock(DistributedLong.class);
      when(mockCluster.getLong(anyString())).thenReturn(mockLong);
      when(mockCluster.getLocalMember()).thenReturn("localhost:1234");

      mockStorage = mock(KeyValueStorage.class);
      directoryBlob = new Blob<>("some/dir", null, 0L, Instant.now(), null);
      when(mockStorage.get("some/dir")).thenReturn(directoryBlob);
   }

   /**
    * A directory blob has a null digest. Verify that getInputStream(String) throws an
    * IOException with an appropriate message rather than passing a null digest down to the
    * underlying cache or engine and causing an NPE.
    */
   @Test
   void getInputStream_throwsIOException_forDirectoryBlob() {
      try (MockedStatic<Cluster> clusterStatic = Mockito.mockStatic(Cluster.class)) {
         clusterStatic.when(Cluster::getInstance).thenReturn(mockCluster);

         BlobStorage<Serializable> storage = new TestBlobStorage("test-store", mockStorage);

         IOException thrown = assertThrows(IOException.class,
                                           () -> storage.getInputStream("some/dir"));
         assertTrue(thrown.getMessage().contains("directory"),
                    "Expected message to mention 'directory', got: " + thrown.getMessage());
      }
   }

   /**
    * Same guard exists on getReadChannel. Verify it throws consistently.
    */
   @Test
   void getReadChannel_throwsIOException_forDirectoryBlob() {
      try (MockedStatic<Cluster> clusterStatic = Mockito.mockStatic(Cluster.class)) {
         clusterStatic.when(Cluster::getInstance).thenReturn(mockCluster);

         BlobStorage<Serializable> storage = new TestBlobStorage("test-store", mockStorage);

         IOException thrown = assertThrows(IOException.class,
                                           () -> storage.getReadChannel("some/dir"));
         assertTrue(thrown.getMessage().contains("directory"),
                    "Expected message to mention 'directory', got: " + thrown.getMessage());
      }
   }

   /** Minimal concrete subclass of BlobStorage used only for testing. */
   private static final class TestBlobStorage extends BlobStorage<Serializable> {
      TestBlobStorage(String id, KeyValueStorage<Blob<Serializable>> storage) {
         super(id, storage);
      }

      @Override
      protected InputStream getInputStream(Blob<Serializable> blob) {
         throw new UnsupportedOperationException("should not reach here for a directory blob");
      }

      @Override
      protected BlobChannel getReadChannel(Blob<Serializable> blob) {
         throw new UnsupportedOperationException("should not reach here for a directory blob");
      }

      @Override
      protected Path copyToTemp(Blob<Serializable> blob) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected void commit(Blob<Serializable> blob, Path tempFile) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected void delete(Blob<Serializable> blob) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected Path createTempFile(String prefix, String suffix) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected boolean isLocal() {
         return false;
      }
   }
}

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
import inetsoft.test.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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

         BlobStorage<Serializable> storage = new TestBlobStorage("test-store", mockStorage, mockCluster);

         IOException thrown = assertThrows(IOException.class,
                                           () -> storage.getInputStream("some/dir"));
         assertTrue(thrown.getMessage().contains("directory"),
                    "Expected message to mention 'directory', got: " + thrown.getMessage());
         verify(mockCluster).unlockRead(anyString());
      }
   }

   /**
    * Same guard exists on getReadChannel. Verify it throws consistently.
    */
   @Test
   void getReadChannel_throwsIOException_forDirectoryBlob() {
      try (MockedStatic<Cluster> clusterStatic = Mockito.mockStatic(Cluster.class)) {
         clusterStatic.when(Cluster::getInstance).thenReturn(mockCluster);

         BlobStorage<Serializable> storage = new TestBlobStorage("test-store", mockStorage, mockCluster);

         IOException thrown = assertThrows(IOException.class,
                                           () -> storage.getReadChannel("some/dir"));
         assertTrue(thrown.getMessage().contains("directory"),
                    "Expected message to mention 'directory', got: " + thrown.getMessage());
         verify(mockCluster).unlockRead(anyString());
      }
   }

   /**
    * Bugs #77759, #77760. close() only shuts the event executor down, so an event that is queued
    * when the storage is closed still runs later. awaitClosedEventExecutors() waits for it.
    */
   @Test
   void awaitClosedEventExecutors_waitsForAnEventQueuedAtClose() throws Exception {
      InMemoryKeyValueStorage<Blob<Serializable>> keyValueStorage = new InMemoryKeyValueStorage<>();
      BlobStorage<Serializable> storage =
         new TestBlobStorage("test-store", keyValueStorage, mockCluster);
      AtomicBoolean ran = new AtomicBoolean();
      storage.addListener(new RunListener(() -> {
         try {
            Thread.sleep(200L);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }

         ran.set(true);
      }));

      // delivered to the blob storage's listener synchronously, which queues it
      keyValueStorage.remotePut("some/dir", directoryBlob, true);
      storage.close();

      assertTrue(BlobStorage.awaitClosedEventExecutors(10L, TimeUnit.SECONDS));
      assertTrue(ran.get(), "the event queued at close did not run");
   }

   /**
    * An event that does not finish in time is reported once, and is not waited for again.
    */
   @Test
   void awaitClosedEventExecutors_timesOutForAnEventThatDoesNotFinish() throws Exception {
      InMemoryKeyValueStorage<Blob<Serializable>> keyValueStorage = new InMemoryKeyValueStorage<>();
      BlobStorage<Serializable> storage =
         new TestBlobStorage("test-store", keyValueStorage, mockCluster);
      CountDownLatch release = new CountDownLatch(1);
      storage.addListener(new RunListener(() -> {
         try {
            release.await(10L, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }));

      try {
         keyValueStorage.remotePut("some/dir", directoryBlob, true);
         storage.close();

         assertFalse(BlobStorage.awaitClosedEventExecutors(200L, TimeUnit.MILLISECONDS));
         assertTrue(BlobStorage.awaitClosedEventExecutors(0L, TimeUnit.MILLISECONDS),
                    "an executor that timed out must not be waited for again");
      }
      finally {
         release.countDown();
      }
   }

   private record RunListener(Runnable action) implements BlobStorage.Listener<Serializable> {
      @Override
      public void blobAdded(BlobStorage.Event<Serializable> event) {
         action.run();
      }

      @Override
      public void blobUpdated(BlobStorage.Event<Serializable> event) {
         action.run();
      }

      @Override
      public void blobRemoved(BlobStorage.Event<Serializable> event) {
         action.run();
      }
   }

   /** Minimal concrete subclass of BlobStorage used only for testing. */
   private static final class TestBlobStorage extends BlobStorage<Serializable> {
      TestBlobStorage(String id, KeyValueStorage<Blob<Serializable>> storage, Cluster cluster) {
         super(id, storage, cluster);
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
      protected void deleteByDigest(String digest) throws IOException {
         throw new UnsupportedOperationException();
      }

      @Override
      protected Path createTempFile(String prefix, String suffix) {
         throw new UnsupportedOperationException();
      }

      protected boolean isLocal() {
         return false;
      }
   }
}

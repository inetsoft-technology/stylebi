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

package inetsoft.util.cachefs;

import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
import inetsoft.test.SreeHome;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SreeHome
public class CacheFileSystemTests {
   @Test
   void getPathCreatesCachePath() {
      Path path = getRoot();
      assertNotNull(path);
      assertThat(path, instanceOf(CachePath.class));

      CachePath cachePath = (CachePath) path;
      CacheFileSystem fs = cachePath.getCacheFileSystem();
      assertNotNull(fs);
      assertEquals("testCache", fs.getStoreId());
      assertNotNull(fs.getBlobStorage());
   }

   @Test
   void readAttributesShouldReturnResult() throws Exception {
      Path path = getRoot();
      BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
      assertNotNull(attrs);

      long now = System.currentTimeMillis();
      long error = TimeUnit.MINUTES.toMillis(1L);

      assertInRange(now, attrs.creationTime(), error);
      assertInRange(now, attrs.lastModifiedTime(), error);
      assertInRange(now, attrs.lastAccessTime(), error);
      assertTrue(attrs.isDirectory());
      assertFalse(attrs.isRegularFile());
      assertFalse(attrs.isSymbolicLink());
      assertFalse(attrs.isOther());
      assertEquals(0L, attrs.size());
      assertEquals("/", attrs.fileKey());
   }

   @Test
   void newInputStreamShouldReadFile() throws Exception {
      long now = System.currentTimeMillis();
      String key = "/newInputStream.txt";
      String text =  """
         Lorem ipsum dolor sit amet, consectetur adipiscing elit.
         Donec in ante sed quam porttitor dapibus.
         Praesent dui nibh, cursus at tristique in, malesuada vel ligula.""";
      BlobStorage<CacheMetadata> storage = getStorage();

      try {

         try(BlobTransaction<CacheMetadata> tx = storage.beginTransaction()) {
            CacheMetadata metadata = new CacheMetadata(now);

            try(OutputStream out = tx.newStream(key, metadata)) {
               out.write(text.getBytes(StandardCharsets.UTF_8));
            }

            tx.commit();
         }

         Path path = CacheFS.getPath("testCache", key);
         String actual;

         try(InputStream in = Files.newInputStream(path)) {
            actual = IOUtils.toString(in, StandardCharsets.UTF_8);
         }

         assertEquals(text, actual);
      }
      finally {
         storage.delete(key);
      }
   }

   @Test
   void newByteChannelShouldModifyFile() throws Exception {
      long now = System.currentTimeMillis();
      String key = "/newByteChannel.dat";
      byte[] data = { 1, 2, 3, 4, 5, 7, 8, 9, 10 };
      byte[] reversed = Arrays.reverse(data);
      BlobStorage<CacheMetadata> storage = getStorage();
      Path path = CacheFS.getPath("testCache", key);

      try {
         try(BlobTransaction<CacheMetadata> tx = storage.beginTransaction()) {
            CacheMetadata metadata = new CacheMetadata(now);

            try(OutputStream out = tx.newStream(key, metadata)) {
               IOUtils.write(data, out);
            }

            tx.commit();
         }

         try(SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.WRITE)) {
            byte[] actual = new byte[data.length];
            ByteBuffer buffer = ByteBuffer.wrap(actual);
            channel.read(buffer);

            assertArrayEquals(data, actual);

            channel.position(0L);
            buffer = ByteBuffer.wrap(reversed);
            channel.write(buffer);
         }

         byte[] actual;

         try(InputStream in = Files.newInputStream(path)) {
            actual = IOUtils.toByteArray(in);
         }

         assertArrayEquals(reversed, actual);
      }
      finally {
         Files.deleteIfExists(path);
      }
   }

   @Test
   void newOutputStreamShouldWriteFile() throws Exception {
      String key = "/newOutputStream.txt";
      String text =  """
         Lorem ipsum dolor sit amet, consectetur adipiscing elit.
         Donec in ante sed quam porttitor dapibus.
         Praesent dui nibh, cursus at tristique in, malesuada vel ligula.""";
      BlobStorage<CacheMetadata> storage = getStorage();
      Path path = CacheFS.getPath("testCache", key);
      long now = System.currentTimeMillis();
      long error = TimeUnit.SECONDS.toMillis(15L);

      try {
         try(OutputStream out = Files.newOutputStream(path)) {
            IOUtils.write(text, out, StandardCharsets.UTF_8);
         }

         String actual;

         try(InputStream in = storage.getInputStream(key)) {
            actual = IOUtils.toString(in, StandardCharsets.UTF_8);
         }

         assertEquals(text, actual);

         BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
         assertNotNull(attrs);
         assertInRange(now, attrs.creationTime(), error);
         assertInRange(now, attrs.lastModifiedTime(), error);
         assertInRange(now, attrs.lastAccessTime(), error);
         assertFalse(attrs.isDirectory());
         assertTrue(attrs.isRegularFile());
         assertFalse(attrs.isSymbolicLink());
         assertFalse(attrs.isOther());
         assertEquals(163L, attrs.size());
         assertEquals(key, attrs.fileKey());

         boolean found = false;

         try(DirectoryStream<Path> stream = Files.newDirectoryStream(getRoot())) {
            for(Path p : stream) {
               if(p.equals(path)) {
                  found = true;
                  break;
               }
            }
         }

         assertTrue(found);
      }
      finally {
         Files.deleteIfExists(path);
      }
   }

   @Test
   void createDirectoryShouldCreateNewDirectory() throws Exception {
      Path path = CacheFS.getPath("testCache", "/newDirectory");

      try {
         Files.createDirectory(path);
         assertTrue(Files.exists(path));
         assertTrue(Files.isDirectory(path));

         boolean found = false;

         try(DirectoryStream<Path> stream = Files.newDirectoryStream(getRoot())) {
            for(Path p : stream) {
               if(p.equals(path)) {
                  found = true;
                  break;
               }
            }
         }

         assertTrue(found);
      }
      finally {
         Files.deleteIfExists(path);
      }
   }

   @Test
   void existsShouldReturnTrueWhenFileExists() throws Exception {
      String key = "/fileExists.txt";
      String text =  """
         Lorem ipsum dolor sit amet, consectetur adipiscing elit.
         Donec in ante sed quam porttitor dapibus.
         Praesent dui nibh, cursus at tristique in, malesuada vel ligula.""";
      BlobStorage<CacheMetadata> storage = getStorage();
      Path path = CacheFS.getPath("testCache", key);

      try {
         try(OutputStream out = Files.newOutputStream(path)) {
            IOUtils.write(text, out, StandardCharsets.UTF_8);
         }

         assertTrue(Files.exists(path));
      }
      finally {
         Files.deleteIfExists(path);
      }
   }

   @Test
   void existsShouldReturnFalseWhenFileDoesNotExist() throws Exception {
      String key = "/fileMissing.txt";
      Path path = CacheFS.getPath("testCache", key);
      assertFalse(Files.exists(path));
   }

   @Test
   void deleteShouldRemoveFile() throws Exception {
      String key = "/toBeDeleted.txt";
      String text =  """
         Lorem ipsum dolor sit amet, consectetur adipiscing elit.
         Donec in ante sed quam porttitor dapibus.
         Praesent dui nibh, cursus at tristique in, malesuada vel ligula.""";
      BlobStorage<CacheMetadata> storage = getStorage();
      Path path = CacheFS.getPath("testCache", key);

      try {
         try(OutputStream out = Files.newOutputStream(path)) {
            IOUtils.write(text, out, StandardCharsets.UTF_8);
         }

         Files.delete(path);
         assertFalse(Files.exists(path));

         boolean found = false;

         try(DirectoryStream<Path> stream = Files.newDirectoryStream(getRoot())) {
            for(Path p : stream) {
               if(p.equals(path)) {
                  found = true;
                  break;
               }
            }
         }

         assertFalse(found);
      }
      finally {
         Files.deleteIfExists(path);
      }
   }

   @Test
   void copyShouldCreateFile() throws Exception {
      String sourceKey = "/copySource.txt";
      String targetKey = "/copyTarget.txt";
      String text =  """
         Lorem ipsum dolor sit amet, consectetur adipiscing elit.
         Donec in ante sed quam porttitor dapibus.
         Praesent dui nibh, cursus at tristique in, malesuada vel ligula.""";
      Path source = CacheFS.getPath("testCache", sourceKey);
      Path target = CacheFS.getPath("testCache", targetKey);

      long now = System.currentTimeMillis();
      long error = TimeUnit.SECONDS.toMillis(15L);

      try {
         try(OutputStream out = Files.newOutputStream(source)) {
            IOUtils.write(text, out, StandardCharsets.UTF_8);
         }

         Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
         assertTrue(Files.exists(source));
         assertTrue(Files.exists(target));
         String actual;

         try(InputStream in = Files.newInputStream(target)) {
            actual = IOUtils.toString(in, StandardCharsets.UTF_8);
         }

         assertEquals(text, actual);

         BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
         assertNotNull(attrs);
         assertInRange(now, attrs.creationTime(), error);
         assertInRange(now, attrs.lastModifiedTime(), error);
         assertInRange(now, attrs.lastAccessTime(), error);
         assertFalse(attrs.isDirectory());
         assertTrue(attrs.isRegularFile());
         assertFalse(attrs.isSymbolicLink());
         assertFalse(attrs.isOther());
         assertEquals(163L, attrs.size());
         assertEquals(targetKey, attrs.fileKey());

         boolean sourceFound = false;
         boolean targetFound = false;

         try(DirectoryStream<Path> stream = Files.newDirectoryStream(getRoot())) {
            for(Path p : stream) {
               sourceFound = sourceFound || p.equals(source);
               targetFound = targetFound || p.equals(target);

               if(sourceFound && targetFound) {
                  break;
               }
            }
         }

         assertTrue(sourceFound);
         assertTrue(targetFound);
      }
      finally {
         Files.deleteIfExists(source);
         Files.deleteIfExists(target);
      }
   }

   @Test
   void moveShouldRenameFile() throws Exception {
      String sourceKey = "/moveSource.txt";
      String targetKey = "/moveTarget.txt";
      String text =  """
         Lorem ipsum dolor sit amet, consectetur adipiscing elit.
         Donec in ante sed quam porttitor dapibus.
         Praesent dui nibh, cursus at tristique in, malesuada vel ligula.""";
      Path source = CacheFS.getPath("testCache", sourceKey);
      Path target = CacheFS.getPath("testCache", targetKey);

      long now = System.currentTimeMillis();
      long error = TimeUnit.SECONDS.toMillis(15L);

      try {
         try(OutputStream out = Files.newOutputStream(source)) {
            IOUtils.write(text, out, StandardCharsets.UTF_8);
         }

         Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
         assertFalse(Files.exists(source));
         assertTrue(Files.exists(target));
         String actual;

         try(InputStream in = Files.newInputStream(target)) {
            actual = IOUtils.toString(in, StandardCharsets.UTF_8);
         }

         assertEquals(text, actual);

         BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
         assertNotNull(attrs);
         assertInRange(now, attrs.creationTime(), error);
         assertInRange(now, attrs.lastModifiedTime(), error);
         assertInRange(now, attrs.lastAccessTime(), error);
         assertFalse(attrs.isDirectory());
         assertTrue(attrs.isRegularFile());
         assertFalse(attrs.isSymbolicLink());
         assertFalse(attrs.isOther());
         assertEquals(163L, attrs.size());
         assertEquals(targetKey, attrs.fileKey());

         boolean sourceFound = false;
         boolean targetFound = false;

         try(DirectoryStream<Path> stream = Files.newDirectoryStream(getRoot())) {
            for(Path p : stream) {
               sourceFound = sourceFound || p.equals(source);
               targetFound = targetFound || p.equals(target);

               if(sourceFound && targetFound) {
                  break;
               }
            }
         }

         assertFalse(sourceFound);
         assertTrue(targetFound);
      }
      finally {
         Files.deleteIfExists(source);
         Files.deleteIfExists(target);
      }
   }

   private void assertInRange(long expected, FileTime actual, long error) {
      assertNotNull(actual);
      assertInRange(expected, actual.toMillis(), error);
   }

   private void assertInRange(long expected, long actual, long error) {
      assertThat(actual, greaterThan(expected - error));
      assertThat(actual, lessThan(expected + error));
   }

   private Path getRoot() {
      return CacheFS.getPath("testCache", "/");
   }

   private BlobStorage<CacheMetadata> getStorage() {
      return ((CachePath) getRoot()).getCacheFileSystem().getBlobStorage();
   }
}
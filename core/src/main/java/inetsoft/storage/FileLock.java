/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.storage;

import inetsoft.util.Cleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@code FileLock} provides a named mutex across processes <i>on the same machine</i>. This class
 * is not thread-safe. It should be created where it will be used and discarded when done.
 */
public class FileLock {
   public FileLock(Path lockDir, String name) {
      this.path = lockDir.resolve(name);
      this.path.toFile().deleteOnExit();
      Cleaner.add(new LockReference(this));
   }

   public void lock() throws InterruptedException {
      acquireLock(-1L);
   }

   public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
      return acquireLock(TimeUnit.MILLISECONDS.convert(time, unit));
   }

   public void unlock() {
      unlock(file, channel, lock, path);
   }

   private boolean acquireLock(long timeout) throws InterruptedException {
      if(lock.get() != null) {
         throw new IllegalStateException(
            "FileLock is not thread safe and cannot be shared between threads.");
      }

      try {
         file.set(new RandomAccessFile(path.toFile(), "rw"));
         channel.set(file.get().getChannel());
         long end = timeout <= 0L ? -1 : System.currentTimeMillis() + timeout;

         while(lock.get() == null && (end < 0L || System.currentTimeMillis() < end)) {
            try {
               lock.set(channel.get().tryLock());
            }
            catch(OverlappingFileLockException ignore) {
            }

            if(lock.get() != null) {
               break;
            }

            Thread.sleep(250);
         }
      }
      catch(IOException e) {
         unlock(); // release any resources
         throw new RuntimeException(e);
      }

      if(lock.get() == null) {
         unlock(); // make sure resources are cleaned up
         return false;
      }

      return true;
   }

   private static void unlock(AtomicReference<RandomAccessFile> file,
                              AtomicReference<FileChannel> channel,
                              AtomicReference<java.nio.channels.FileLock> lock,
                              Path path)
   {
      if(lock.get() != null) {
         try {
            lock.get().release();
         }
         catch(Exception e) {
            LOG.warn("Failed to release file lock", e);
         }
         finally {
            lock.set(null);
         }
      }

      if(channel.get() != null) {
         try {
            channel.get().close();
         }
         catch(Exception e) {
            LOG.warn("Failed to close file channel", e);
         }
         finally {
            channel.set(null);
         }
      }

      if(file.get() != null) {
         try {
            file.get().close();
         }
         catch(Exception e) {
            LOG.warn("Failed to close file", e);
         }
         finally {
            file.set(null);
         }
      }

      path.toFile().delete();
   }

   private final Path path;
   private final AtomicReference<RandomAccessFile> file = new AtomicReference<>();
   private final AtomicReference<FileChannel> channel = new AtomicReference<>();
   private final AtomicReference<java.nio.channels.FileLock> lock = new AtomicReference<>();
   private static final Logger LOG = LoggerFactory.getLogger(FileLock.class);

   private static final class LockReference extends Cleaner.Reference<FileLock> {
      public LockReference(FileLock referent) {
         super(referent);
         this.file = referent.file;
         this.channel = referent.channel;
         this.lock = referent.lock;
         this.path = referent.path;
      }

      @Override
      public void close() throws Exception {
         unlock(file, channel, lock, path);
      }

      private final AtomicReference<RandomAccessFile> file;
      private final AtomicReference<FileChannel> channel;
      private final AtomicReference<java.nio.channels.FileLock> lock;
      private final Path path;
   }
}

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
package inetsoft.util.swap;

import inetsoft.test.*;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.channels.FileLock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77652. {@code XIntFragment}/{@code XObjectFragment}'s {@code swap0()} used to drop the
 * in-memory array (via {@code invalidate()}) before the durable {@code channel.write()} had
 * actually returned without throwing, so a write failure (disk full, lock contention, I/O error)
 * silently and permanently lost the data instead of leaving it recoverable in memory. Each test
 * here forces a genuine {@code IOException} out of the fragment's own write by taking an
 * OS-level exclusive {@link FileLock} on the same swap file from a second handle, timed to land
 * after the fragment has opened its own file/channel but before it writes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class XFragmentSwapFileWriteFailureTest {
   @Test
   void intFragmentSurvivesFailedSwapWrite() throws Exception {
      int[] values = { 100, 101, 102, 103, 104 };
      XIntFragment fragment = new XIntFragment(values);
      File swapFile = FileSystemService.getInstance().getCacheFile(fragment.prefix + ".tdat");
      XSwapper swapper = spy(XSwapper.getSwapper());

      try(LockedFile locked = lockDuringNextWaitForMemory(swapper, swapFile)) {
         fragment.swapper = swapper;
         assertTrue(fragment.swap(), "swap() should report success even though the write failed");
      }

      assertFalse(fragment.isValid(), "fragment should still be marked invalid after a failed write");
      assertEquals(100, fragment.getSafely(0), "data was lost after a failed swap write");
      assertEquals(104, fragment.getSafely(4), "data was lost after a failed swap write");

      fragment.dispose();
   }

   @Test
   void objectFragmentSurvivesFailedSwapWrite() throws Exception {
      // XObjectFragment's swap0() creates (and so would create, via a naive lock-ahead-of-time)
      // the swap file itself before any write is attempted, and swap0() treats a pre-existing
      // file as "already swapped" and skips writing entirely - so unlike the Int/String
      // fragments, the lock here can't be taken from a waitForMemory() hook ahead of time.
      // Instead, a blocking object in the array pauses serialization (which runs after the
      // fragment's own file/channel are already open) so the lock can be acquired deterministically
      // in between, with no need to race the fragment's own thread.
      CountDownLatch readyToLock = new CountDownLatch(1);
      CountDownLatch acquiredLock = new CountDownLatch(1);
      XObjectFragment<String> fragment = new XObjectFragment<>((char) 10, (char) 100, null);
      fragment.add(new BlockingDuringSerialization(readyToLock, acquiredLock));
      fragment.add("alpha");
      fragment.add("beta");
      fragment.complete();

      File swapFile = FileSystemService.getInstance().getCacheFile(fragment.prefix + "_0.tdat");
      Thread swapThread = new Thread(fragment::swap, "object-fragment-swap-test");

      try(LockedFile locked = new LockedFile()) {
         swapThread.start();
         assertTrue(readyToLock.await(10, TimeUnit.SECONDS),
                    "serialization never reached the blocking object");
         locked.acquire(swapFile);
         acquiredLock.countDown();
         swapThread.join(10_000);
      }

      assertFalse(fragment.isValid(), "fragment should still be marked invalid after a failed write");
      assertEquals("alpha", fragment.getSafely(1), "data was lost after a failed swap write");
      assertEquals("beta", fragment.getSafely(2), "data was lost after a failed swap write");

      fragment.dispose();
   }

   @Test
   void stringFragmentSurvivesFailedSwapWriteAndRecoversCleanly() throws Exception {
      String original = "the quick brown fox";
      XStringFragment fragment = new XStringFragment(original);
      fragment.complete();
      File swapFile = FileSystemService.getInstance().getCacheFile(fragment.prefix + ".tdat");
      XSwapper swapper = spy(XSwapper.getSwapper());

      try(LockedFile locked = lockDuringNextWaitForMemory(swapper, swapFile)) {
         fragment.swapper = swapper;
         assertTrue(fragment.swap(), "swap() should report success even though the write failed");
      }

      assertEquals(original, fragment.getCurrentData(),
                   "in-memory value was dropped even though the write failed");

      // detach from the locking spy before any further waitForMemory() calls
      fragment.swapper = XSwapper.getSwapper();

      assertEquals(original, fragment.getData(),
                   "a failed write followed by an ordinary read must not silently return empty content");
      assertFalse(swapFile.exists(),
                  "the corrupt/empty stub left by the failed write should be cleaned up on recovery");

      // the fragment should swap normally afterward instead of treating a leftover stub as an
      // already-durable copy and discarding the in-memory value for nothing
      assertTrue(fragment.swap());
      assertFalse(fragment.isValid());
      assertEquals(original, fragment.getData(), "fragment did not recover cleanly after the failed write");

      fragment.dispose();
   }

   /**
    * A payload whose serialization pauses mid-way, so a test can deterministically lock the swap
    * file (already created and open by the fragment at this point) before serialization - and so
    * the fragment's write - resumes.
    */
   private static final class BlockingDuringSerialization implements Serializable {
      BlockingDuringSerialization(CountDownLatch readyToLock, CountDownLatch acquiredLock) {
         this.readyToLock = readyToLock;
         this.acquiredLock = acquiredLock;
      }

      private void writeObject(ObjectOutputStream out) throws IOException {
         readyToLock.countDown();

         try {
            if(!acquiredLock.await(10, TimeUnit.SECONDS)) {
               throw new IOException("timed out waiting for the test lock to be acquired");
            }
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(ex);
         }
      }

      private final transient CountDownLatch readyToLock;
      private final transient CountDownLatch acquiredLock;
   }

   private static LockedFile lockDuringNextWaitForMemory(XSwapper swapper, File file) {
      LockedFile locked = new LockedFile();
      doAnswer(invocation -> {
         locked.acquire(file);
         return null;
      }).when(swapper).waitForMemory();
      return locked;
   }

   /**
    * Holds an OS-level exclusive lock on a file, acquired from a second handle so the fragment's
    * own write into the same file fails with a genuine {@code IOException}.
    */
   private static final class LockedFile implements AutoCloseable {
      void acquire(File file) throws IOException {
         raf = new RandomAccessFile(file, "rw");
         lock = raf.getChannel().lock();
      }

      @Override
      public void close() throws IOException {
         if(lock != null) {
            lock.release();
            lock = null;
         }

         if(raf != null) {
            raf.close();
            raf = null;
         }
      }

      private RandomAccessFile raf;
      private FileLock lock;
   }
}

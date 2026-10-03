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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
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

   @Test
   void stringFragmentRewritesStubEvenWhenDeleteFails() throws Exception {
      // Review round 1, finding 1: the recovery path's stub.delete() is a best-effort cleanup,
      // not a correctness requirement. If it fails (e.g. a transient external handle on the
      // file), swap0() must still rewrite the file on the next swap instead of treating the
      // still-existing stub as an already-durable copy and silently discarding value again.
      String original = "the quick brown fox";
      XStringFragment fragment = new XStringFragment(original);
      fragment.complete();
      File swapFile = FileSystemService.getInstance().getCacheFile(fragment.prefix + ".tdat");
      XSwapper swapper = spy(XSwapper.getSwapper());

      try(LockedFile locked = lockDuringNextWaitForMemory(swapper, swapFile)) {
         fragment.swapper = swapper;
         assertTrue(fragment.swap(), "swap() should report success even though the write failed");
      }

      fragment.swapper = XSwapper.getSwapper();

      // hold the stub open (without an exclusive lock) so File.delete() fails on Windows,
      // without preventing a later handle from opening the same path for read/write
      try(RandomAccessFile blocker = new RandomAccessFile(swapFile, "rw")) {
         assertEquals(original, fragment.getData(),
                      "recovery must still return the in-memory value even if the stub can't be deleted");
         assertTrue(swapFile.exists(), "test setup invalid: stub delete should have failed while open");

         assertTrue(fragment.swap(), "fragment must still be swappable after a failed stub delete");
         assertFalse(fragment.isValid());
         assertEquals(original, fragment.getData(),
                      "stub must be rewritten (not skipped) even though it still physically exists");
      }

      fragment.dispose();
   }

   @Test
   void intFragmentValidateDoesNotDesyncPosFromArrOnReadFailure() throws Exception {
      // Review round 1, finding 2: a read-back failure partway through validate() must not leave
      // this.pos updated to a new value while this.arr (now preserved by the swap0() reorder)
      // stays at the old, differently-sized array.
      int[] values = { 100, 101, 102, 103, 104 };
      XIntFragment fragment = new XIntFragment(values);

      Method validate = XIntFragment.class.getDeclaredMethod("validate", ByteBuffer.class);
      validate.setAccessible(true);
      Field posField = XIntFragment.class.getDeclaredField("pos");
      posField.setAccessible(true);
      Field arrField = XIntFragment.class.getDeclaredField("arr");
      arrField.setAccessible(true);

      char posBefore = (char) posField.get(fragment);
      int[] arrBefore = (int[]) arrField.get(fragment);

      // claims 10 ints follow (a new pos of 10) but only supplies 2, so XSwapUtil.readInt()
      // throws BufferUnderflowException partway through the array-read loop, after the field
      // would previously have already been overwritten with the claimed new pos
      ByteBuffer buf = ByteBuffer.allocate(64);
      XSwapUtil.writeChar(buf, (char) 10);
      XSwapUtil.writeInt(buf, 999);
      XSwapUtil.writeInt(buf, 999);
      buf.flip();

      InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                                                        () -> validate.invoke(fragment, buf));
      assertInstanceOf(java.nio.BufferUnderflowException.class, thrown.getCause());

      assertEquals(posBefore, (char) posField.get(fragment),
                   "pos must not be mutated by a read-back that failed partway through");
      assertArrayEquals(arrBefore, (int[]) arrField.get(fragment),
                         "arr must not be mutated by a read-back that failed partway through");

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

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
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77652. {@code XIntFragment}/{@code XObjectFragment}'s {@code swap0()} used to drop the
 * in-memory array (via {@code invalidate()}) before the durable {@code channel.write()} had
 * actually returned without throwing, so a write failure (disk full, lock contention, I/O error)
 * silently and permanently lost the data instead of leaving it recoverable in memory.
 *
 * <p>An earlier version of this test forced the write failure with an OS-level exclusive
 * {@code FileLock} taken from a second handle on the same swap file. That works on Windows
 * (mandatory, handle-level locking) but not on Linux (POSIX advisory locks only block other
 * *lock* attempts, not a different file descriptor's plain read/write calls - this project's CI
 * runs on {@code ubuntu-latest}), so it silently exercised the "write succeeded" path instead of
 * the failure path there - confirmed by an actual CI failure on
 * {@code stringFragmentSurvivesFailedSwapWriteAndRecoversCleanly}. A later attempt to replace it
 * with "pre-create the swap file's path as a directory" also doesn't generalize: for a fragment's
 * first/only chunk, that path is gated behind {@code if(!file.exists())}, so making the path
 * "exist" (as a directory) routes into the unrelated "already swapped, nothing to write" fast
 * path instead of ever reaching a write attempt - confirmed by running the resulting tests
 * against the pre-fix code and finding they passed when they should have failed.
 *
 * <p>Every test here instead uses {@code testBeforeWrite}, a package-private, test-only
 * {@code Runnable} hook added to each fragment class specifically for this purpose: it is invoked
 * immediately before the real durable write (after the file/channel are already open and the
 * buffer is already serialized - exactly mirroring where a real {@code IOException} would occur),
 * and is a no-op (field stays {@code null}) in production. This forces the failure
 * deterministically, identically, on every platform, with no OS-level locking or permissions
 * involved at all.
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
      preventBackgroundSwapping(fragment);
      fragment.testBeforeWrite = XFragmentSwapFileWriteFailureTest::throwSimulatedWriteFailure;

      assertTrue(fragment.swap(), "swap() should report success even though the write failed");
      assertFalse(fragment.isValid(), "fragment should still be marked invalid after a failed write");
      assertEquals(100, fragment.getSafely(0), "data was lost after a failed swap write");
      assertEquals(104, fragment.getSafely(4), "data was lost after a failed swap write");

      fragment.dispose();
   }

   @Test
   void intFragmentRecoversAndRewritesOnNextSwap() throws Exception {
      // Review round 2 (combined): a recovered fragment must actually rewrite its stub on the
      // next swap, not skip the write again (via the "already exists" fast path) and lose the
      // data a second time - this is what rewriteRequired now forces.
      int[] values = { 100, 101, 102, 103, 104 };
      XIntFragment fragment = new XIntFragment(values);
      preventBackgroundSwapping(fragment);
      fragment.testBeforeWrite = XFragmentSwapFileWriteFailureTest::throwSimulatedWriteFailure;

      assertTrue(fragment.swap());
      assertEquals(100, fragment.getSafely(0), "data was lost after a failed swap write");

      fragment.testBeforeWrite = null;
      assertTrue(fragment.swap(), "a recovered fragment must still be swappable");
      assertFalse(fragment.isValid());
      assertEquals(100, fragment.getSafely(0),
                   "stub was not actually rewritten on the next swap - data lost a second time");
      assertEquals(104, fragment.getSafely(4),
                   "stub was not actually rewritten on the next swap - data lost a second time");

      fragment.dispose();
   }

   @Test
   void objectFragmentSurvivesFailedSwapWrite() throws Exception {
      XObjectFragment<String> fragment = new XObjectFragment<>((char) 10, (char) 100, null);
      fragment.add("alpha");
      fragment.add("beta");
      preventBackgroundSwapping(fragment);
      fragment.testBeforeWrite = XFragmentSwapFileWriteFailureTest::throwSimulatedWriteFailure;

      assertTrue(fragment.swap(), "swap() should report success even though the write failed");
      assertFalse(fragment.isValid(), "fragment should still be marked invalid after a failed write");
      assertEquals("alpha", fragment.getSafely(0), "data was lost after a failed swap write");
      assertEquals("beta", fragment.getSafely(1), "data was lost after a failed swap write");

      fragment.dispose();
   }

   @Test
   void objectFragmentRecoversAndRewritesOnNextSwap() throws Exception {
      // Review round 2 (combined): same rewriteRequired-style gap as XIntFragment, for
      // XObjectFragment's "if(file.exists()) { invalidate(null); clear(); return; }" fast path.
      XObjectFragment<String> fragment = new XObjectFragment<>((char) 10, (char) 100, null);
      fragment.add("alpha");
      fragment.add("beta");
      preventBackgroundSwapping(fragment);
      fragment.testBeforeWrite = XFragmentSwapFileWriteFailureTest::throwSimulatedWriteFailure;

      assertTrue(fragment.swap());
      assertEquals("alpha", fragment.getSafely(0), "data was lost after a failed swap write");

      fragment.testBeforeWrite = null;
      assertTrue(fragment.swap(), "a recovered fragment must still be swappable");
      assertFalse(fragment.isValid());
      assertEquals("alpha", fragment.getSafely(0),
                   "stub was not actually rewritten on the next swap - data lost a second time");
      assertEquals("beta", fragment.getSafely(1),
                   "stub was not actually rewritten on the next swap - data lost a second time");

      fragment.dispose();
   }

   @Test
   void objectFragmentSurvivesFailedWriteOnANonFirstChunk() throws Exception {
      // Tester-found gap (post round-1): a fragment large enough to span multiple swap-file
      // chunks ("_0.tdat", "_1.tdat", ...) can have an EARLIER chunk's write succeed, durably,
      // while a LATER chunk's write fails. validate(ByteBuffer, ObjectArrayHolder)'s own internal
      // catch swallows a read failure on that later, corrupt/0-byte chunk and returns -1 from its
      // tail - indistinguishable, to validate0()'s while loop, from a legitimate full read - so
      // without the holder.complete guard, the next read-back would silently overwrite the fully
      // intact in-memory array with a partial reconstruction (only the earlier chunk's elements).
      //
      // testBeforeWrite fires once per write call site reached (one per chunk); failing only on
      // the 2nd invocation forces exactly "chunk 0 durably succeeds, chunk 1 fails" - the precise
      // shape this gap needs, without any thread/lock/directory trickery.
      int count = 2200;
      String base = "x".repeat(140);
      XObjectFragment<String> fragment = new XObjectFragment<>((char) 10, (char) 20000, null);

      for(int i = 0; i < count; i++) {
         fragment.add(base + i);
      }

      preventBackgroundSwapping(fragment);

      File chunk0 = FileSystemService.getInstance().getCacheFile(fragment.prefix + "_0.tdat");
      AtomicInteger writeCount = new AtomicInteger();
      fragment.testBeforeWrite = () -> {
         if(writeCount.incrementAndGet() == 2) {
            throwSimulatedWriteFailure();
         }
      };

      assertTrue(fragment.swap(), "swap() should report success even though chunk 1's write failed");
      assertTrue(writeCount.get() >= 2,
                 "test setup: expected a multi-chunk swap (at least 2 write attempts) - adjust "
                    + "count/base length if XObjectFragment's chunk-sizing changes");
      assertTrue(chunk0.length() > 0, "test setup: chunk 0 should have been durably written");
      assertFalse(fragment.isValid(), "fragment should still be marked invalid after a failed write");

      assertEquals(base + 0, fragment.getSafely(0),
                   "an earlier, durably-written chunk's data was lost");
      assertEquals(base + (count - 1), fragment.getSafely(count - 1),
                   "data was lost - the intact in-memory array was clobbered by a partial "
                      + "reconstruction from the chunk whose write failed");
      // not just the array contents - pos itself (exposed via available()) must also stay at
      // its original, pre-failure value, not some smaller count read back from the partial
      // reconstruction (mirrors the pos/arr consistency check from round 1's Int fragment test)
      assertEquals(count, fragment.available(),
                   "pos was desynced from the preserved array by the failed read-back");

      fragment.dispose();
   }

   @Test
   void stringFragmentSurvivesFailedSwapWriteAndRecoversCleanly() throws Exception {
      String original = "the quick brown fox";
      XStringFragment fragment = new XStringFragment(original);
      preventBackgroundSwapping(fragment);
      fragment.testBeforeWrite = XFragmentSwapFileWriteFailureTest::throwSimulatedWriteFailure;

      assertTrue(fragment.swap(), "swap() should report success even though the write failed");
      assertEquals(original, fragment.getCurrentData(),
                   "in-memory value was dropped even though the write failed");

      assertEquals(original, fragment.getData(),
                   "a failed write followed by an ordinary read must not silently return empty content");

      // the fragment should swap normally afterward instead of treating a leftover stub as an
      // already-durable copy and discarding the in-memory value for nothing
      fragment.testBeforeWrite = null;
      assertTrue(fragment.swap());
      assertFalse(fragment.isValid());
      assertEquals(original, fragment.getData(), "fragment did not recover cleanly after the failed write");

      fragment.dispose();
   }

   @Test
   void stringFragmentRewritesStubEvenWhenDeleteFails() throws Exception {
      // Review round 1, finding 1: the recovery path's stub.delete() is a best-effort cleanup,
      // not a correctness requirement - swap0() must still rewrite the file on the next swap
      // instead of treating a leftover stub as an already-durable copy and silently discarding
      // value again. A real File.delete() failure isn't reliably producible across platforms/CI
      // permission models (e.g. POSIX delete is governed by the *directory's* write permission,
      // not the file's own, and containers commonly run as root, which bypasses permission checks
      // entirely) - so this exercises the actual contract directly: with rewriteRequired set and
      // a stub physically present, swap0() must still rewrite rather than skip.
      String original = "the quick brown fox";
      XStringFragment fragment = new XStringFragment(original);
      preventBackgroundSwapping(fragment);
      File swapFile = FileSystemService.getInstance().getCacheFile(fragment.prefix + ".tdat");
      Files.write(swapFile.toPath(), new byte[0]);

      Field rewriteRequiredField = XStringFragment.class.getDeclaredField("rewriteRequired");
      rewriteRequiredField.setAccessible(true);
      rewriteRequiredField.setBoolean(fragment, true);

      assertTrue(swapFile.exists(), "test setup: stub file should exist");
      assertTrue(fragment.swap(), "fragment must still be swappable with a leftover stub present");
      assertFalse(fragment.isValid());
      assertEquals(original, fragment.getData(),
                   "stub must be rewritten (not skipped) when rewriteRequired is set, even though "
                      + "it still physically exists");

      fragment.dispose();
   }

   @Test
   void stringFragmentStaysValidAfterUnrelatedSwapFileDeletion() throws Exception {
      // review-and-merge-prs finding (post-merge, independent review of this PR): before this
      // PR's changes, access0() began with an unconditional "valid = true" before attempting
      // anything, so even a swap file that became unreadable for a reason unrelated to a failed
      // write (externally deleted, disk corruption, a sweep bug) still left the fragment
      // settled (valid, data lost) after the first failed read. This PR's restructuring moved
      // "valid = true" into only the value-!=null recovery branch and the successful-read
      // branch, so a fragment whose swap file goes missing for any OTHER reason got stuck at
      // isValid() == false forever: every later access()/getData() call would re-enter
      // access0(), retry the same failing RandomAccessFile open, and fail the same way again,
      // indefinitely. Fixed by moving "valid = true" into a finally block covering the whole
      // read attempt, mirroring XIntFragment.validate0()/XObjectFragment.validate0().
      String original = "the quick brown fox";
      XStringFragment fragment = new XStringFragment(original);
      preventBackgroundSwapping(fragment);

      // a normal, successful swap - no testBeforeWrite, so the write genuinely completes and
      // access0() will take the disk-read branch (not the value != null recovery branch) below
      assertTrue(fragment.swap());
      File swapFile = FileSystemService.getInstance().getCacheFile(fragment.prefix + ".tdat");
      assertTrue(swapFile.exists(), "test setup: swap file should have been durably written");

      // simulate the swap file going missing for a reason unrelated to a failed write
      assertTrue(swapFile.delete(), "test setup: failed to delete swap file");

      assertNull(fragment.getData(),
                 "data cannot be recovered once the swap file is genuinely gone");
      assertTrue(fragment.isValid(),
                 "fragment must settle (valid) after a read failure unrelated to a write "
                    + "failure, not retry the same failing read forever");

      // a second access() must not re-enter access0() at all now that the fragment is valid -
      // if it did, isValid() would still read true here anyway, but the fragment would never
      // actually stop retrying in real usage, which is exactly the bug this test guards against
      assertNull(fragment.getData());
      assertTrue(fragment.isValid());

      fragment.dispose();
   }

   @Test
   void intFragmentValidateDoesNotDesyncPosFromArrOnReadFailure() throws Exception {
      // Review round 1, finding 2: a read-back failure partway through validate() must not leave
      // this.pos updated to a new value while this.arr (now preserved by the swap0() reorder)
      // stays at the old, differently-sized array. Platform-independent by construction - no
      // file I/O at all, just a hand-built buffer fed directly to the method via reflection.
      int[] values = { 100, 101, 102, 103, 104 };
      XIntFragment fragment = new XIntFragment(values);
      preventBackgroundSwapping(fragment);

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
    * complete() registers the fragment with the real, shared XSwapper background thread pool,
    * which can act on it independently (e.g. under real memory pressure, or because another test
    * class sharing this JVM triggers a GC/memory-pressure sweep) - confirmed to actually happen:
    * an earlier version of this test file, without this call, was intermittently flaky when run
    * in the same JVM as XSwapperGCThrottleTest/XSwapperPeriodicGCTest/XSwapperCriticalWaitTest.
    * Deregistering immediately after completing keeps completed/getSwapPriority() working for
    * this test's own explicit swap() calls, while guaranteeing only this test's thread ever
    * touches the fragment.
    */
   private static void preventBackgroundSwapping(XSwappable fragment) {
      fragment.complete();
      XSwapper.getSwapper().deregister(fragment);
   }

   private static void throwSimulatedWriteFailure() {
      // wrap a real IOException - the same type channel.write()/fout.write() actually declare -
      // so the forced failure looks as close as possible to a genuine one, even though
      // Runnable.run() can't declare a checked exception
      throw new UncheckedIOException(new IOException("simulated write failure"));
   }
}

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
package inetsoft.util.script.graal.pool;

import inetsoft.util.script.graal.GraalJavaScriptEngine;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #77123: a caller's cancel (an interrupt of the thread) that reaches a lens-owned
 * object hand-off snapshot is kept. At its next guest safepoint Graal turns a set interrupt
 * flag into "Thread was interrupted." and clears the flag; the snapshot used to take any
 * interrupt for its own time bound, so the cancel was swallowed and the query went on. An
 * interrupt of the snapshot's own time bound, or of the timeout of an exec the snapshot runs
 * in, is still a time-out and never left on the thread.
 */
@Tag("core")
class OwnedValueCodecCancelTest {
   @BeforeEach
   @AfterEach
   void clearFlag() {
      Thread.interrupted();
   }

   @AfterEach
   void closeSlots() {
      // slots of the worker thread are closed by it
      for(Slot slot : slots) {
         closeHeld(slot);
      }

      slots.clear();
      executor.shutdownNow();
   }

   /** A cancel set before the snapshot is kept, and does not stop the snapshot. */
   @Test
   void aCancelBeforeTheSnapshotIsKeptAndTheValuesAreSaved() throws Exception {
      Slot slot = newSlot(PoolConfig.defaults());
      Value v = eval(slot, "var o = {a: [1, 2], d: new Date(5)}; o");
      Thread.currentThread().interrupt();
      Object[] nodes = OwnedValueCodec.of(slot).snapshotTree(List.of(v));
      assertTrue(Thread.interrupted(), "the snapshot lost the cancel: " + describe(nodes));
      assertInstanceOf(OwnedValueCodec.TreeRef.class, nodes[0], describe(nodes));

      // and a rebuild on another context on a cancelled thread keeps it too
      Slot other = newSlot(PoolConfig.defaults());
      Thread.currentThread().interrupt();
      Value built = OwnedValueCodec.of(other).rebuild(nodes[0], new IdentityHashMap<>());
      assertTrue(Thread.interrupted(), "the rebuild lost the cancel");
      assertEquals(2, built.getMember("a").getArraySize());
      assertEquals(5L, built.getMember("d").asInstant().toEpochMilli());
   }

   /**
    * A cancel that lands while the snapshot runs: the flag is set afterwards, the values are
    * lost (never read stale), and the query stops at its next script.
    */
   @Test
   void aCancelDuringTheSnapshotIsKeptAndTheQueryStops() throws Exception {
      Future<String> run = executor.submit(() -> {
         Slot slot = newSlot(bigConfig());

         try {
            Value big = bigRoot(slot);
            worker = Thread.currentThread();
            snapping.countDown();
            long t0 = System.nanoTime();
            Object[] nodes = OwnedValueCodec.of(slot).snapshotTree(List.of(big));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            boolean kept = Thread.currentThread().isInterrupted();
            String outcome = "snapshot " + ms + " ms, " + describe(nodes);

            if(!kept) {
               return "the snapshot lost the cancel: " + outcome;
            }

            if(nodes[0] != OwnedValueCodec.UNREADABLE) {
               return "the snapshot was not stopped by the cancel: " + outcome;
            }

            try {
               Object r = eval(slot, "var s = 0; for(var i = 0; i < 2e9; i++) { s += i; } s");
               return "the next script ran on: " + r;
            }
            catch(Exception ex) {
               return "ok " + outcome + ", next script: " + ex;
            }
         }
         finally {
            Thread.interrupted();
            closeHeld(slot);
         }
      });

      assertTrue(snapping.await(60, TimeUnit.SECONDS));
      interruptInSnapshot(worker);
      String result = run.get(120, TimeUnit.SECONDS);
      System.out.println("CODEC-CANCEL " + result);
      assertTrue(result.startsWith("ok "), result);
   }

   /**
    * A hand-off snapshot inside an exec whose own timeout guard fires while the snapshot
    * runs: that interrupt is the exec's time-out, not a cancel, so it is not kept on the
    * thread. The snapshot's own guard opens and closes inside the fired outer guard, so the
    * outer guard must still count as the thread's.
    */
   @Test
   void aTimeoutOfAnOuterExecDuringTheSnapshotIsNoCancel() throws Exception {
      Future<String> run = executor.submit(() -> {
         Slot slot = newSlot(bigConfig());
         Value big = bigRoot(slot);
         OwnedValueCodec codec = OwnedValueCodec.of(slot);
         AtomicReference<String> inner = new AtomicReference<>("the snapshot did not run");
         OuterEngine outer = new OuterEngine();
         outer.init(new HashMap<>());

         try {
            outer.bindings().putMember("snap", (ProxyExecutable) args -> {
               long t0 = System.nanoTime();
               Object[] nodes = codec.snapshotTree(List.of(big));
               long ms = (System.nanoTime() - t0) / 1_000_000;
               inner.set((Thread.currentThread().isInterrupted() ? "flag set" : "flag clear") +
                         ", snapshot " + ms + " ms, " + describe(nodes));
               return 1;
            });

            String exec;

            try {
               exec = "returned " + outer.exec(outer.compile(
                  "snap(); var s = 0; for(var i = 0; i < 2e9; i++) { s += i; } s"), null, null);
            }
            catch(Exception ex) {
               exec = "threw " + ex;
            }

            return inner.get() + "; exec " + exec + "; flag after " +
               Thread.currentThread().isInterrupted();
         }
         finally {
            outer.close();
            closeHeld(slot);
         }
      });

      String result = run.get(120, TimeUnit.SECONDS);
      System.out.println("CODEC-CANCEL nested " + result);
      // the snapshot counts it as a time-out (a cancel would lose the values unreadable and
      // re-assert the flag); the flag the outer interrupt sent while it was in progress is
      // Graal's, cleared when the exec leaves the context
      assertTrue(result.contains("took longer than"),
                 "the outer timeout was not taken for a time-out: " + result);
      assertTrue(result.contains("exec threw"), "the outer exec was not stopped: " + result);
      assertTrue(result.endsWith("flag after false"),
                 "the outer timeout was kept as a cancel: " + result);
   }

   /** A cancel set before the batch-end Date snapshot is kept, and the Date is saved. */
   @Test
   void aCancelBeforeADateSnapshotIsKept() throws Exception {
      Slot slot = newSlot(PoolConfig.defaults());
      Value valid = eval(slot, "new Date(7)");
      Value invalid = eval(slot, "new Date(NaN)");
      OwnedValueCodec codec = OwnedValueCodec.of(slot);
      Thread.currentThread().interrupt();
      Object a = codec.snapshot(valid, new HashMap<>());
      Object b = codec.snapshot(invalid, new HashMap<>());
      assertTrue(Thread.interrupted(), "the Date snapshot lost the cancel");
      assertInstanceOf(OwnedValueCodec.DateNode.class, a);
      assertInstanceOf(OwnedValueCodec.DateNode.class, b);
   }

   // wait until the worker runs the cloner, then interrupt it
   private static void interruptInSnapshot(Thread t) throws InterruptedException {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);

      while(System.nanoTime() - end < 0) {
         StackTraceElement[] stack = t.getStackTrace();
         boolean inSnap = false;
         boolean inGuest = false;

         for(StackTraceElement e : stack) {
            if(e.getClassName().endsWith("OwnedValueCodec") &&
               e.getMethodName().equals("snapshotTree"))
            {
               inSnap = true;
               break;
            }

            if(e.getClassName().startsWith("com.oracle.truffle") ||
               e.getClassName().startsWith("org.graalvm"))
            {
               inGuest = true;
            }
         }

         if(inSnap && inGuest) {
            Thread.sleep(50);
            t.interrupt();
            return;
         }

         Thread.sleep(2);
      }

      fail("the snapshot never ran");
   }

   // about 2M entries, a snapshot of a second or more
   private static Value bigRoot(Slot slot) throws Exception {
      return eval(slot, "var big = []; for(var i = 0; i < 1000000; i++) { big.push({n: i}); } big");
   }

   private static PoolConfig bigConfig() {
      PoolConfig d = PoolConfig.defaults();
      return new PoolConfig(d.idleMillis(), d.cleanThreshold(), d.warnSlotsPerSandbox(),
                            d.warnSlotsPerNode(), d.batchRows(), d.maxBatchRows(),
                            d.maxHomes(), d.maxHomesPerNode(), 120_000L, 50_000_000);
   }

   private static Value eval(Slot slot, String js) throws Exception {
      WsEngine engine = slot.engine();
      engine.exec(engine.compile("var __r = eval(" + quote(js) + ");"), null, null);
      return engine.context().getBindings("js").getMember("__r");
   }

   private static String quote(String js) {
      return "'" + js.replace("\\", "\\\\").replace("'", "\\'") + "'";
   }

   private static String describe(Object[] nodes) {
      StringBuilder buf = new StringBuilder();

      for(Object n : nodes) {
         buf.append(n instanceof OwnedValueCodec.Lost lost ? "lost: " + lost.kind()
                       : n == null ? "null" : n.getClass().getSimpleName()).append("; ");
      }

      return buf.toString();
   }

   private static void closeHeld(Slot slot) {
      try {
         if(!slot.isClosed()) {
            slot.close();
            slot.unlock();
         }
      }
      catch(IllegalStateException ex) {
         // held by another thread
      }
   }

   private Slot newSlot(PoolConfig config) throws Exception {
      EnvState state = new EnvState();
      Slot slot = Slot.create(new InitSnapshot("org0", Map.of()), state.snapshot(), 0L, false,
                              Collections.synchronizedMap(new WeakHashMap<>()),
                              new PoolMetrics());
      slot.setConfig(config);
      slots.add(slot);
      return slot;
   }

   /** A plain engine whose script timeout is 1 s. */
   static final class OuterEngine extends GraalJavaScriptEngine {
      @Override
      protected Duration currentTimeout() {
         return Duration.ofSeconds(1);
      }

      Value bindings() {
         return context.getBindings("js");
      }
   }

   private final List<Slot> slots = new CopyOnWriteArrayList<>();
   private final ExecutorService executor = Executors.newSingleThreadExecutor();
   private final CountDownLatch snapping = new CountDownLatch(1);
   private volatile Thread worker;
}

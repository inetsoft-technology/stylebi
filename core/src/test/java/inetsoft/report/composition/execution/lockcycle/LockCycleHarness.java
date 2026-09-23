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
package inetsoft.report.composition.execution.lockcycle;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.Condition;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.ScriptScope;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared harness of the lock-cycle regression suite (bug #76966). It builds lens pipelines
 * from real classes (the real {@code ConditionFilter2} via {@link PostProcessor#filter}, real
 * lenses, a real {@link FormulaTableLens} and a real GraalJS env and execution lock) and runs
 * them on daemon threads with bounded waits, so a lock cycle fails its test with a thread
 * dump instead of hanging the build.
 *
 * <p>Since bug #76935 a condition filter only takes the engine lock when its base chain
 * reaches a {@code FormulaTableLens} (or one of the async lenses), so every pipeline that
 * needs a lock holder has a formula lens where production has an expression column.
 *
 * <p>Use one harness per test and close it in {@code @AfterEach}. Closing never blocks for
 * long: lenses are cancelled on a daemon thread with a bound (a deadlocked
 * {@code JoinTableLens.cancel()} never returns, bug #76960), and an engine is closed only if
 * its lock is free.
 */
public final class LockCycleHarness implements AutoCloseable {
   /**
    * Per-future cap for cases of cycles that are fixed on main.
    */
   public static final long ACTIVE_CAP = 30;

   /**
    * Per-future cap for known-deadlock cases, which are expected to time out on main.
    */
   public static final long KNOWN_CAP = 10;

   public LockCycleHarness() {
      HARNESS_THREAD.set(true);

      // threads of earlier cases (e.g. left deadlocked by a known case) are not dumped
      for(Thread thread : Thread.getAllStackTraces().keySet()) {
         preexisting.add(thread.getId());
      }

      pool = Executors.newCachedThreadPool(r -> {
         Thread thread = new Thread(() -> {
            HARNESS_THREAD.set(true);
            r.run();
         }, "lockcycle-" + SEQ.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      });
   }

   /**
    * Create a sandbox whose condition filters take the lock of a real, initialized GraalJS
    * env, the one its formula lenses run on.
    */
   public Sandbox sandbox() {
      return sandbox(true);
   }

   /**
    * Create a sandbox with a real env whose condition filters take no lock (the box is
    * {@code null}), to compute the expected rows of a pipeline.
    */
   public Sandbox control() {
      return sandbox(false);
   }

   private Sandbox sandbox(boolean locking) {
      Sandbox sandbox = new Sandbox(locking);
      sandboxes.add(sandbox);
      return sandbox;
   }

   /**
    * Make {@code box} hand its condition filters {@code env}, whose execution lock they take.
    * This is the only place that knows how {@code ConditionFilter2} finds the lock (since
    * PR #5548, by capturing {@code peekScriptEnv()} at construction), so a change of that API
    * changes only this line.
    */
   static void stubScriptLock(AssetQuerySandbox box, ScriptEnv env) {
      when(box.peekScriptEnv()).thenReturn(env);
   }

   /**
    * Run {@code task} on a daemon harness thread.
    */
   public <T> Future<T> submit(Callable<T> task) {
      return pool.submit(task);
   }

   /**
    * Wait at most {@code capSeconds} for {@code future}. On timeout, print a thread dump of
    * every thread started since this harness was created that runs inetsoft code, and the
    * state of every sandbox lock, and fail.
    */
   public <T> T await(Future<T> future, long capSeconds, String what) throws Exception {
      try {
         return future.get(capSeconds, TimeUnit.SECONDS);
      }
      catch(TimeoutException ex) {
         String dump = dump();
         System.err.println("LOCKCYCLE TIMEOUT: " + what + "\n" + dump);
         fail(what + " did not finish within " + capSeconds + " s (lock cycle?)\n" + dump);
         return null;
      }
      catch(ExecutionException ex) {
         if(ex.getCause() instanceof Exception) {
            throw (Exception) ex.getCause();
         }

         throw ex;
      }
   }

   /**
    * Wait until {@code thread} is parked in {@code LendableReentrantLock.lock()}, or until
    * {@code capSeconds} passed.
    */
   public static boolean awaitWaitingOnLock(Thread thread, long capSeconds)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(capSeconds);

      while(System.currentTimeMillis() < deadline) {
         if(thread != null && thread.getState() == Thread.State.WAITING) {
            for(StackTraceElement element : thread.getStackTrace()) {
               if(element.getClassName().equals(LendableReentrantLock.class.getName()) &&
                  element.getMethodName().equals("lock"))
               {
                  return true;
               }
            }
         }

         Thread.sleep(5);
      }

      return false;
   }

   /**
    * Submit {@code task} and publish the thread that runs it, so a case can wait for that
    * thread to reach a frame before starting the next thread.
    */
   public <T> Started<T> start(Callable<T> task) {
      Started<T> started = new Started<>();
      started.future = submit(() -> {
         started.thread = Thread.currentThread();
         return task.call();
      });
      return started;
   }

   /**
    * {@link #start} a task whose thread owns {@code gate}.
    */
   public <T> Started<T> startGated(Gate gate, Callable<T> task) {
      return start(() -> {
         gate.own();
         return task.call();
      });
   }

   /**
    * Let {@code gate}'s owner, parked at the gate, go on once {@code next} has started and
    * parked (or finished): the owner was provably first, and {@code next} provably arrived
    * while the owner was still inside its frame.
    */
   public static void releaseAfter(Gate gate, Started<?> next, long capSeconds)
      throws InterruptedException
   {
      awaitParked(next, capSeconds);
      gate.release();
   }

   /**
    * Create a gate: a {@link SlowTable} built with it parks the gate's owner thread at its
    * first data read, inside whatever monitor that thread holds at that point, until
    * {@link Gate#release()}. The owner publishes that it is parked, so a case knows the
    * owner is inside the frame without polling its stack, and a starved test thread cannot
    * miss it or flip the order.
    */
   public Gate gate() {
      Gate gate = new Gate();
      gates.add(gate);
      return gate;
   }

   /**
    * Wait until {@code started}'s thread is parked (blocked or waiting), or has finished, or
    * {@code capSeconds} passed. Used after a gated owner is parked, to let the next thread
    * reach the point where it has to wait for the owner before the owner is released.
    */
   public static void awaitParked(Started<?> started, long capSeconds)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(capSeconds);

      while(System.currentTimeMillis() < deadline && !started.future.isDone()) {
         Thread thread = started.thread;
         Thread.State state = thread == null ? null : thread.getState();

         if(state == Thread.State.BLOCKED || state == Thread.State.WAITING ||
            state == Thread.State.TIMED_WAITING)
         {
            return;
         }

         Thread.sleep(2);
      }
   }

   /**
    * Parks its owner at the first data read of a gated {@link SlowTable}. See {@link #gate()}.
    */
   public static final class Gate {
      /**
       * Make the calling thread the owner. Call it first in the owner's task.
       */
      public void own() {
         owner = Thread.currentThread();
      }

      /**
       * Wait until the owner is parked at the gate.
       *
       * @return {@code false} if it did not get there within {@code capSeconds}.
       */
      public boolean awaitEntered(long capSeconds) throws InterruptedException {
         return entered.await(capSeconds, TimeUnit.SECONDS);
      }

      public void release() {
         released.countDown();
      }

      void onRead() {
         if(Thread.currentThread() == owner && entered.getCount() > 0) {
            entered.countDown();

            try {
               // bounded, so a case that never releases cannot park the owner forever
               released.await(3 * KNOWN_CAP, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      }

      private volatile Thread owner;
      private final CountDownLatch entered = new CountDownLatch(1);
      private final CountDownLatch released = new CountDownLatch(1);
   }

   /**
    * A task submitted with {@link #start}.
    */
   public static final class Started<T> {
      public Future<T> future;
      public volatile Thread thread;
   }

   /**
    * Cancel {@code lenses} when the harness is closed.
    */
   public <T extends TableLens> T track(T lens) {
      tracked.add(lens);
      return lens;
   }

   /**
    * Make {@code JoinTableLens} pick {@code HashJoinTable} regardless of memory state, until
    * the harness is closed.
    */
   public void forceHashJoin() {
      if(!forcedHash) {
         forcedHash = true;
         oldForceHash = SreeEnv.getProperty("join.table.forceHash");
      }

      SreeEnv.setProperty("join.table.forceHash", "true");
   }

   @Override
   public void close() throws Exception {
      gates.forEach(Gate::release);
      Thread canceller = new Thread(() -> {
         for(TableLens lens : tracked) {
            if(lens instanceof CancellableTableLens) {
               try {
                  ((CancellableTableLens) lens).cancel();
               }
               catch(Throwable ignore) {
               }
            }
         }
      }, "lockcycle-cancel");
      canceller.setDaemon(true);
      canceller.start();
      canceller.join(3000);
      tracked.clear();
      pool.shutdownNow();

      if(forcedHash) {
         if(oldForceHash == null) {
            SreeEnv.remove("join.table.forceHash");
         }
         else {
            SreeEnv.setProperty("join.table.forceHash", oldForceHash);
         }
      }

      // closing takes the lock, which a deadlocked case leaves held forever
      for(Sandbox sandbox : sandboxes) {
         if(sandbox.lock.tryLock()) {
            try {
               sandbox.engine.close();
            }
            finally {
               sandbox.lock.unlock();
            }
         }
      }

      sandboxes.clear();
      HARNESS_THREAD.remove();
   }

   /**
    * @return {@code true} on the test's own threads, {@code false} on lens workers.
    */
   public static boolean isHarnessThread() {
      return HARNESS_THREAD.get();
   }

   /**
    * A condition filter keeping every row ({@code col 1 > -1}), built by the real factory.
    */
   public static TableLens cf2(TableLens base, AssetQuerySandbox box) {
      return PostProcessor.filter(base, allRows(), box);
   }

   public static ConditionGroup allRows() {
      Condition condition = new Condition();
      condition.setOperation(Condition.GREATER_THAN);
      condition.addValue(-1);
      condition.setType(XSchema.INTEGER);
      ConditionGroup group = new ConditionGroup();
      group.addCondition(1, condition, 0);
      return group;
   }

   /**
    * Read every cell of {@code table}.
    */
   public static List<List<Object>> drain(TableLens table) {
      List<List<Object>> rows = new ArrayList<>();

      for(int r = 0; table.moreRows(r); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < table.getColCount(); c++) {
            row.add(table.getObject(r, c));
         }

         rows.add(row);
      }

      return rows;
   }

   private String dump() {
      StringBuilder buf = new StringBuilder();

      for(Sandbox sandbox : sandboxes) {
         buf.append("sandbox lock ").append(System.identityHashCode(sandbox.lock))
            .append(" locked=").append(sandbox.lock.isLocked())
            .append(" lent=").append(sandbox.lock.isLent()).append('\n');
      }

      for(ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
         StackTraceElement[] stack = info.getStackTrace();

         if(preexisting.contains(info.getThreadId()) ||
            Arrays.stream(stack).noneMatch(e -> e.getClassName().startsWith("inetsoft.")))
         {
            continue;
         }

         buf.append('"').append(info.getThreadName()).append("\" ")
            .append(info.getThreadState());

         if(info.getLockName() != null) {
            buf.append(" on ").append(info.getLockName());
         }

         if(info.getLockOwnerName() != null) {
            buf.append(" owned by \"").append(info.getLockOwnerName()).append('"');
         }

         buf.append('\n');

         for(int i = 0; i < Math.min(stack.length, 24); i++) {
            buf.append("      at ").append(stack[i]).append('\n');
         }
      }

      return buf.toString();
   }

   /**
    * A sandbox: a real GraalJS env, its engine and execution lock, and a mocked
    * {@code AssetQuerySandbox} whose condition filters take that lock.
    */
   public static final class Sandbox {
      Sandbox(boolean locking) {
         CapturingEnv env = new CapturingEnv();
         env.init();
         this.env = env;
         engine = env.created;
         lock = engine.getExecutionLock();

         if(locking) {
            box = mock(AssetQuerySandbox.class);
            stubScriptLock(box, env);
         }
         else {
            box = null;
         }

         try {
            script = engine.compile("1 + 1");
         }
         catch(Exception ex) {
            throw new IllegalStateException(ex);
         }
      }

      /**
       * A real formula lens adding a column {@code f1 = 1}, run on this sandbox's env: the
       * shape of a worksheet table with an expression column.
       */
      public FormulaTableLens formula(TableLens base) {
         return formula(base, "f1", "1");
      }

      /**
       * A real formula lens adding a column {@code header = expr}, run on this sandbox's env.
       * An expression like {@code field['value'] + 1} reads the base while it computes, like a
       * calc field.
       */
      public FormulaTableLens formula(TableLens base, String header, String expr) {
         return new FormulaTableLens(base, new String[] {header}, new String[] {expr}, env, null);
      }

      /**
       * Run {@code task} as a guest script thread of this sandbox: holding its engine lock
       * inside {@code exec}, as a worksheet or viewsheet script reading a table does.
       */
      public <T> T asGuest(Callable<T> task) throws Exception {
         lock.lock();
         JavaScriptEngine.pushExecScriptable(guestScope);

         try {
            return task.call();
         }
         finally {
            JavaScriptEngine.popExecScriptable();
            lock.unlock();
         }
      }

      /**
       * A condition filter of this sandbox over a formula lens over {@code base}: a sub-table
       * with an expression column and a post-processed condition.
       */
      public TableLens filteredFormula(TableLens base) {
         return cf2(formula(base), box);
      }

      /**
       * A table whose column 1 is computed per cell by {@code exec()} on this sandbox's
       * engine, like a calc field or a script join key.
       */
      public TableLens execTable(int rows, Slow slow) {
         return new ExecTable(rows, slow, engine, script);
      }

      public final GraalJavaScriptEnv env;
      public final GraalJavaScriptEngine engine;
      public final LendableReentrantLock lock;
      /** {@code null} for a control sandbox. */
      public final AssetQuerySandbox box;
      private final Object script;
      private final ScriptScope guestScope = mock(ScriptScope.class);
   }

   /**
    * Where a {@link SlowTable} is slow.
    */
   public enum Slow {
      /** Never. */
      NONE,
      /** On lens worker threads only, so the worker is still running when a holder arrives. */
      WORKERS,
      /** On every thread, so a thread is still inside a lens monitor when another arrives. */
      EVERYWHERE,
      /** On lens worker threads, and only past the 10000-row JoinTable pre-drain. */
      WORKERS_PAST_PREDRAIN
   }

   /**
    * A table of {@code rows} rows ({@code group, value, id}): {@code group} and {@code value} repeat, {@code id} is unique, whose
    * column 1 costs 1 ms per read where {@code slow} says.
    */
   public static class SlowTable extends DefaultTableLens {
      public SlowTable(int rows, Slow slow) {
         this(rows, slow, null);
      }

      /**
       * @param gate parks its owner at the owner's first data read of this table.
       */
      public SlowTable(int rows, Slow slow, Gate gate) {
         super(data(rows));
         this.slow = slow;
         this.gate = gate;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r > 0 && gate != null) {
            gate.onRead();
         }

         if(r > 0 && c == 1 && isSlow(r)) {
            try {
               Thread.sleep(1);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.getObject(r, c);
      }

      private boolean isSlow(int r) {
         switch(slow) {
         case WORKERS:
            return !isHarnessThread();
         case EVERYWHERE:
            return true;
         case WORKERS_PAST_PREDRAIN:
            return r > 10000 && !isHarnessThread();
         default:
            return false;
         }
      }

      private static Object[][] data(int rows) {
         Object[][] data = new Object[rows + 1][];
         data[0] = new Object[] {"group", "value", "id"};

         for(int i = 1; i <= rows; i++) {
            data[i] = new Object[] {"k" + (i % 6), i % 30, i};
         }

         return data;
      }

      private final Slow slow;
      private final Gate gate;
   }

   /**
    * A slow table whose column 1 is computed by running a script on the engine; the script
    * returns 2 and leaves the value unchanged.
    */
   private static final class ExecTable extends SlowTable {
      ExecTable(int rows, Slow slow, GraalJavaScriptEngine engine, Object script) {
         super(rows, slow);
         this.engine = engine;
         this.script = script;
      }

      @Override
      public Object getObject(int r, int c) {
         Object value = super.getObject(r, c);

         if(r > 0 && c == 1) {
            try {
               int two = ((Number) engine.exec(script, null, null)).intValue();
               return (Integer) value + two - 2;
            }
            catch(Exception ex) {
               throw new RuntimeException(ex);
            }
         }

         return value;
      }

      private final GraalJavaScriptEngine engine;
      private final Object script;
   }

   /**
    * A real env that remembers the engine it creates, so the harness can reach its lock and
    * close it.
    */
   private static final class CapturingEnv extends GraalJavaScriptEnv {
      @Override
      protected GraalJavaScriptEngine createScriptEngine() {
         created = super.createScriptEngine();
         return created;
      }

      private GraalJavaScriptEngine created;
   }

   private static final ThreadLocal<Boolean> HARNESS_THREAD = ThreadLocal.withInitial(() -> false);
   private static final java.util.concurrent.atomic.AtomicInteger SEQ =
      new java.util.concurrent.atomic.AtomicInteger();

   private final ExecutorService pool;
   private final Set<Long> preexisting = new HashSet<>();
   private final List<Sandbox> sandboxes = new CopyOnWriteArrayList<>();
   private final List<TableLens> tracked = new CopyOnWriteArrayList<>();
   private final List<Gate> gates = new CopyOnWriteArrayList<>();
   private boolean forcedHash;
   private String oldForceHash;
}

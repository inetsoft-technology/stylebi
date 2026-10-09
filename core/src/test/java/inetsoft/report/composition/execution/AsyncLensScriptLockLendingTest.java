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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.filter.*;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.lens.*;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for bug #76938: MV creation hung forever because a thread holding the
 * sandbox's script engine lock (taken by {@code PostProcessor$ConditionFilter2.moreRows()},
 * the #76918 fix) waited in {@code SummaryFilter.moreRows()} for the background worker
 * populating the summary, while that worker blocked taking the same lock to read an inner
 * condition filter.
 *
 * <p>Runs the real {@code ConditionFilter2} (via {@link PostProcessor#filter}), the real
 * async lenses ({@link SummaryFilter}, {@link DistinctTableLens}, {@link SelfJoinTableLens},
 * {@link UnionTableLens}) and the real {@link GraalJavaScriptEngine} execution lock, in the
 * shape {@code outer CF2 -> async lens -> inner CF2 -> base}. The sandbox is a mock that
 * only hands out that lock. The base table is slow on the worker thread only, and the tests
 * that assert a loan hold the worker at the last base row until the lock is lent (see
 * {@link #loanGate}), so the worker is still running when the lock holder arrives.
 *
 * <p>The inner condition filter over a plain base table no longer takes the lock itself
 * (only filters that can reach script do, see {@code ConditionFilter2}), so in those
 * pipelines the worker does not need the loan and the tests only check that the holder
 * lends. The {@code execWorker...} tests cover a worker that needs the lent lock.
 *
 * <p>Every step runs on daemon threads with bounded waits and the lenses are cancelled
 * afterwards, so a regression fails with a timeout instead of hanging the build.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class AsyncLensScriptLockLendingTest {
   @BeforeEach
   public void setUp() {
      FAST.set(true);
      engine = new GraalJavaScriptEngine();
      lock = engine.getExecutionLock();
      // stubbed before any condition filter is built: the filter captures the env then
      ScriptEnv senv = mock(ScriptEnv.class);
      when(senv.getExecutionLock()).thenReturn(lock);
      box = mock(AssetQuerySandbox.class);
      when(box.peekScriptEnv()).thenReturn(senv);
      pool = Executors.newCachedThreadPool(r -> {
         Thread thread = new Thread(() -> {
            FAST.set(true);
            r.run();
         });
         thread.setDaemon(true);
         return thread;
      });
      lentSeen = new AtomicBoolean();
      watching = true;
      watcher = new Thread(() -> {
         while(watching) {
            if(lock.isLent()) {
               lentSeen.set(true);
            }

            try {
               Thread.sleep(1);
            }
            catch(InterruptedException ex) {
               return;
            }
         }
      });
      watcher.setDaemon(true);
      watcher.start();
   }

   @AfterEach
   public void tearDown() throws Exception {
      watching = false;
      watcher.join(5000);

      for(TableLens lens : created) {
         if(lens instanceof CancellableTableLens) {
            ((CancellableTableLens) lens).cancel();
         }
      }

      created.clear();
      pool.shutdownNow();

      // closing takes the lock, which a regression could leave held forever
      if(lock.tryLock()) {
         lock.unlock();
         engine.close();
      }
   }

   /**
    * The condition filter must take the sandbox env's lock, or the tests below would
    * pass without exercising the lock at all. The lock holder is the first to touch the
    * summary, so it reads the base on its own thread.
    */
   @Test
   public void conditionFilterTakesSandboxLock() throws Exception {
      AtomicBoolean held = new AtomicBoolean();
      TableLens base = new SlowTable() {
         @Override
         public Object getObject(int r, int c) {
            if(r > 0 && lock.isHeldByCurrentThread()) {
               held.set(true);
            }

            return super.getObject(r, c);
         }
      };
      TableLens summary = new SummaryFilter(base, new int[] {0}, new int[] {1}, new SumFormula(), null);
      TableLens filter = PostProcessor.filter(summary, allRows(), box);
      created.add(summary);
      created.add(filter);

      assertTrue(pool.submit(() -> filter.moreRows(1)).get(TIMEOUT, TimeUnit.SECONDS));
      assertTrue(held.get(), "the condition filter did not take the sandbox lock");
      assertFalse(lock.isLocked());
   }

   /**
    * The MV case: the worker is started at query-build time by {@code getRowCount()} on a
    * thread holding no lock ({@code AssetQuery.validateDataTypes}), then another thread
    * reads through the outer condition filter and waits for it while holding the lock.
    */
   @ParameterizedTest
   @EnumSource(Kind.class)
   public void workerStartedByGetRowCountThenAwaitedUnderLock(Kind kind) throws Exception {
      Pipeline pipeline = build(kind, box, loanGate(kind));
      pool.submit(() -> pipeline.lens.getRowCount()).get(TIMEOUT, TimeUnit.SECONDS);

      Future<List<List<Object>>> holder = pool.submit(() -> drain(pipeline.outer));

      assertEquals(control(kind).outerRows, holder.get(TIMEOUT, TimeUnit.SECONDS));
      assertLent(kind);
      assertFalse(lock.isLocked());
   }

   /**
    * Same, but the worker is started by an unlocked {@code moreRows(1)}
    * ({@code AssetQuery.getRuntimeTableLens}), still waiting when the holder arrives.
    */
   @ParameterizedTest
   @EnumSource(Kind.class)
   public void workerStartedByMoreRowsThenAwaitedUnderLock(Kind kind) throws Exception {
      Pipeline pipeline = build(kind, box);
      Future<Boolean> starter = pool.submit(() -> pipeline.lens.moreRows(1));
      Thread.sleep(20);

      Future<List<List<Object>>> holder = pool.submit(() -> drain(pipeline.outer));

      assertEquals(control(kind).outerRows, holder.get(TIMEOUT, TimeUnit.SECONDS));
      assertTrue(starter.get(TIMEOUT, TimeUnit.SECONDS));
      assertFalse(lock.isLocked());
   }

   /**
    * The lock holder is the first to touch the lens: it processes it on its own thread.
    * (Reading a header cell first would not do: the condition filter's getObject() does
    * not take the lock, so that read may start the worker unlocked.)
    */
   @ParameterizedTest
   @EnumSource(Kind.class)
   public void firstTouchUnderLock(Kind kind) throws Exception {
      Pipeline pipeline = build(kind, box);
      Future<List<List<Object>>> holder = pool.submit(() -> {
         assertTrue(pipeline.outer.moreRows(1));
         return drain(pipeline.outer);
      });

      assertEquals(control(kind).outerRows, holder.get(TIMEOUT, TimeUnit.SECONDS));
      assertFalse(lentSeen.get(), "a synchronously processed lens should not need a loan");
      assertFalse(lock.isLocked());
   }

   /**
    * A lens shared by two readers (e.g. through AssetDataCache): one reads it directly
    * without any lock and starts the worker, the other waits for the same worker while
    * holding the lock.
    */
   @ParameterizedTest
   @EnumSource(Kind.class)
   public void sharedLensAwaitedByLockHolder(Kind kind) throws Exception {
      Pipeline pipeline = build(kind, box);
      Future<List<List<Object>>> reader = pool.submit(() -> {
         pipeline.lens.getObject(1, 0);
         return drain(pipeline.lens);
      });
      Thread.sleep(20);

      Future<List<List<Object>>> holder = pool.submit(() -> drain(pipeline.outer));
      Pipeline control = control(kind);

      assertEquals(control.outerRows, holder.get(TIMEOUT, TimeUnit.SECONDS));
      assertEquals(control.lensRows, reader.get(TIMEOUT, TimeUnit.SECONDS));
      assertFalse(lock.isLocked());
   }

   /**
    * {@code SummaryFilter.getObject()} waits for the row inside {@code XSwappableTable}
    * rather than in {@code moreRows()}; a lock holder must lend there too.
    */
   @Test
   public void summaryGetObjectAwaitedUnderLock() throws Exception {
      Pipeline pipeline = build(Kind.SUMMARY, box, loanGate(Kind.SUMMARY));
      pool.submit(() -> pipeline.lens.getRowCount()).get(TIMEOUT, TimeUnit.SECONDS);

      Future<Object> holder = pool.submit(() -> {
         lock.lock();
         JavaScriptEngine.pushHeldScriptLock(lock);

         try {
            return pipeline.lens.getObject(1, 1);
         }
         finally {
            JavaScriptEngine.popHeldScriptLock();
            lock.unlock();
         }
      });

      assertEquals(control(Kind.SUMMARY).lensRows.get(1).get(1), holder.get(TIMEOUT, TimeUnit.SECONDS));
      assertTrue(lentSeen.get(), "the lock holder never lent the lock to the worker");
   }

   /**
    * The contended case, and the #76918 guard. Thread B is the #76918 script thread: it
    * takes the engine lock and then the outer condition filter's monitor. B queues on
    * the lock while the holder has it, before the holder lends it, and the worker blocks
    * on the lock too. The worker must get in ahead of B, B must not get the lock while
    * it is lent, and everything must complete.
    */
   @ParameterizedTest
   @EnumSource(Kind.class)
   public void scriptThreadQueuedBeforeLoan(Kind kind) throws Exception {
      Pipeline pipeline = build(kind, box, loanGate(kind));
      CountDownLatch holderLocked = new CountDownLatch(1);
      AtomicReference<Thread> scriptThread = new AtomicReference<>();
      AtomicBoolean scriptLocking = new AtomicBoolean();
      AtomicBoolean holderDone = new AtomicBoolean();
      pool.submit(() -> pipeline.lens.getRowCount()).get(TIMEOUT, TimeUnit.SECONDS);

      Future<List<List<Object>>> holder = pool.submit(() -> {
         lock.lock();
         JavaScriptEngine.pushHeldScriptLock(lock);

         try {
            holderLocked.countDown();
            awaitQueued(scriptThread, scriptLocking);
            // let the worker reach the lock and block on it as well
            Thread.sleep(100);
            List<List<Object>> rows = drain(pipeline.outer);
            holderDone.set(true);
            return rows;
         }
         finally {
            JavaScriptEngine.popHeldScriptLock();
            lock.unlock();
         }
      });

      Future<List<List<Object>>> script = pool.submit(() -> {
         scriptThread.set(Thread.currentThread());
         assertTrue(holderLocked.await(TIMEOUT, TimeUnit.SECONDS));
         scriptLocking.set(true);
         lock.lock();

         try {
            assertTrue(holderDone.get(), "script thread got the lock while it was lent");
            return drain(pipeline.outer);
         }
         finally {
            lock.unlock();
         }
      });

      Pipeline control = control(kind);
      assertEquals(control.outerRows, holder.get(TIMEOUT, TimeUnit.SECONDS));
      assertEquals(control.outerRows, script.get(TIMEOUT, TimeUnit.SECONDS));
      assertLent(kind);
      assertFalse(lock.isLocked());
   }

   /**
    * Stacked async lenses with no condition filter between them, in the MV (build-time)
    * ordering: an unlocked getRowCount() starts the upper lens's worker W1, which touches
    * the lower lens and starts its worker W2 while holding nothing. The lock holder then
    * lends the lock to W1, and W1 must lend it on to W2, which needs it for the inner
    * condition filter.
    */
   @ParameterizedTest
   @EnumSource(StackedKind.class)
   public void stackedWorkersStartedAtBuildTime(StackedKind kind) throws Exception {
      Pipeline pipeline = buildStacked(kind, box, loanGate(Kind.SUMMARY));
      pool.submit(() -> pipeline.lens.getRowCount()).get(TIMEOUT, TimeUnit.SECONDS);

      Future<List<List<Object>>> holder = pool.submit(() -> drain(pipeline.outer));

      assertEquals(stackedControl(kind), holder.get(TIMEOUT, TimeUnit.SECONDS));
      assertTrue(lentSeen.get(), "the lock holder never lent the lock to the worker");
      assertFalse(lock.isLocked());
   }

   private List<List<Object>> stackedControl(StackedKind kind) {
      return STACKED_CONTROLS.computeIfAbsent(kind, k -> {
         List<List<Object>> rows = drain(buildStacked(k, null, null).outer);
         assertTrue(rows.size() > 2, "control pipeline is empty");
         return rows;
      });
   }

   private Pipeline buildStacked(StackedKind kind, AssetQuerySandbox box, Runnable gate) {
      TableLens inner = gated(PostProcessor.filter(new SlowTable(), allRows(), box), gate);
      TableLens lower = new SummaryFilter(inner, new int[] {0, 1}, new int[] {2}, new SumFormula(), null);
      TableLens upper;

      switch(kind) {
      case SUMMARY_OVER_SUMMARY:
         upper = new SummaryFilter(lower, new int[] {0}, new int[] {1}, new SumFormula(), null);
         break;
      case DISTINCT_OVER_SUMMARY:
         upper = new DistinctTableLens(lower);
         break;
      default:
         throw new IllegalArgumentException(kind.name());
      }

      Pipeline pipeline = new Pipeline();
      pipeline.lens = upper;
      pipeline.outer = PostProcessor.filter(upper, allRows(), box);
      created.add(lower);
      created.add(upper);
      created.add(pipeline.outer);
      return pipeline;
   }

   /**
    * Check that the lock holder lent the lock, i.e. that the worker was still running
    * when the holder waited for it. SetTableLens builds its merge tree on the calling
    * thread and its worker only walks that tree, so its worker is done too quickly and
    * never needs the lock.
    */
   private void assertLent(Kind kind) {
      if(kind != Kind.UNION) {
         assertTrue(lentSeen.get(), "the lock holder never lent the lock to the worker");
      }
   }

   /**
    * The rows of the same pipeline built without a sandbox, so without any locking.
    */
   private Pipeline control(Kind kind) {
      return CONTROLS.computeIfAbsent(kind, k -> {
         Pipeline pipeline = build(k, null);
         pipeline.outerRows = drain(pipeline.outer);
         pipeline.lensRows = drain(pipeline.lens);
         assertTrue(pipeline.outerRows.size() > 2, "control pipeline is empty");
         return pipeline;
      });
   }

   /**
    * The worker itself runs script: the lens reads a base table whose values are
    * computed by GraalJavaScriptEngine.exec() on the engine whose lock the sandbox
    * hands out, like a calc field (the #76937 jstack shows a SummaryFilter worker
    * parked in exec() on the engine lock while the holder waits in
    * SummaryFilter.moreRows()). No condition filter below the lens. The worker is
    * started by an unlocked getRowCount() at build time.
    */
   @ParameterizedTest
   @EnumSource(value = Kind.class, names = { "SUMMARY", "DISTINCT", "SELF_JOIN" })
   public void execWorkerStartedByGetRowCountThenAwaitedUnderLock(Kind kind) throws Exception {
      List<List<Object>> expected = execControl(kind);
      Object script = initEngine(engine);
      Pipeline pipeline = buildExec(kind, box, engine, script, loanGate(kind));
      pool.submit(() -> pipeline.lens.getRowCount()).get(TIMEOUT, TimeUnit.SECONDS);

      Future<List<List<Object>>> holder = pool.submit(() -> drain(pipeline.outer));

      assertEquals(expected, holder.get(TIMEOUT, TimeUnit.SECONDS));
      assertTrue(lentSeen.get(), "the lock holder never lent the lock to the worker");
      assertFalse(lock.isLocked());
   }

   /**
    * Same, but the condition filter lock holder is the first to touch the lens.
    */
   @ParameterizedTest
   @EnumSource(value = Kind.class, names = { "SUMMARY", "DISTINCT", "SELF_JOIN" })
   public void execWorkerFirstTouchUnderLock(Kind kind) throws Exception {
      List<List<Object>> expected = execControl(kind);
      Object script = initEngine(engine);
      Pipeline pipeline = buildExec(kind, box, engine, script, null);
      Future<List<List<Object>>> holder = pool.submit(() -> {
         assertTrue(pipeline.outer.moreRows(1));
         return drain(pipeline.outer);
      });

      assertEquals(expected, holder.get(TIMEOUT, TimeUnit.SECONDS));
      assertFalse(lock.isLocked());
   }

   private static Object initEngine(GraalJavaScriptEngine engine) throws Exception {
      engine.init(new HashMap<>());
      return engine.compile("1 + 1");
   }

   /**
    * The rows of the same pipeline without a sandbox, on an engine of its own so it
    * can't contend with a deadlocked pipeline.
    */
   private List<List<Object>> execControl(Kind kind) throws Exception {
      GraalJavaScriptEngine controlEngine = new GraalJavaScriptEngine();

      try {
         Object script = initEngine(controlEngine);
         List<List<Object>> rows = drain(buildExec(kind, null, controlEngine, script, null).outer);
         assertTrue(rows.size() > 2, "control pipeline is empty");
         // exec() leaves the values unchanged, so the rows equal the plain pipeline's
         assertEquals(control(kind).outerRows, rows);
         return rows;
      }
      finally {
         controlEngine.close();
      }
   }

   private Pipeline buildExec(Kind kind, AssetQuerySandbox box, GraalJavaScriptEngine engine,
                              Object script, Runnable gate)
   {
      return build(kind, box, new ExecTable(engine, script, gate));
   }

   /**
    * A gate for the worker's read of the last base row (bug #78147), for the tests that
    * assert a loan. Without it the holder and the worker race: a holder slowed down by
    * CPU starvation (cold first test, busy CI runner) reached the lens after the worker
    * had finished, found nothing to wait for and correctly lent nothing, or lent only for
    * a few milliseconds that the sampling watcher missed. The gate holds the worker,
    * outside the lock, until the holder has lent it the lock, and records the loan it
    * sees. It is bounded below TIMEOUT, so a holder that never lends still fails: on the
    * loan assertion where the worker does not need the lock, and with a timeout in the
    * exec pipeline, where the worker blocks on the unlent lock before it gets here. The
    * worker must hold no monitor the holder needs while it waits, so it
    * runs only on reads made without the lock, in ExecTable or in a GateTable above the
    * inner condition filter (never inside it: a SELF_JOIN holder reads the inner filter
    * under its monitor). None for UNION, whose worker never makes the holder wait.
    */
   private Runnable loanGate(Kind kind) {
      if(kind == Kind.UNION) {
         return null;
      }

      AtomicBoolean armed = new AtomicBoolean(true);

      return () -> {
         if(lock.isHeldByCurrentThread() || !armed.compareAndSet(true, false)) {
            return;
         }

         long deadline = System.currentTimeMillis() + GATE_TIMEOUT * 1000L;

         while(!lock.isLent() && System.currentTimeMillis() < deadline) {
            try {
               Thread.sleep(1);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
               return;
            }
         }

         if(lock.isLent()) {
            lentSeen.set(true);
         }
      };
   }

   private Pipeline build(Kind kind, AssetQuerySandbox box) {
      return build(kind, box, (Runnable) null);
   }

   private Pipeline build(Kind kind, AssetQuerySandbox box, Runnable gate) {
      return build(kind, box, gated(PostProcessor.filter(new SlowTable(), allRows(), box), gate));
   }

   private static TableLens gated(TableLens inner, Runnable gate) {
      return gate == null ? inner : new GateTable(inner, gate);
   }

   private Pipeline build(Kind kind, AssetQuerySandbox box, TableLens inner) {
      TableLens lens;

      switch(kind) {
      case SUMMARY:
         lens = new SummaryFilter(inner, new int[] {0}, new int[] {1}, new SumFormula(), null);
         break;
      case DISTINCT:
         lens = new DistinctTableLens(inner);
         break;
      case SELF_JOIN:
         SelfJoinTableLens join = new SelfJoinTableLens(inner);
         join.addJoin(1, XConstants.GREATER_EQUAL_JOIN, 2);
         lens = join;
         break;
      case UNION:
         lens = new UnionTableLens(inner, PostProcessor.filter(new SlowTable(), allRows(), box));
         break;
      default:
         throw new IllegalArgumentException(kind.name());
      }

      Pipeline pipeline = new Pipeline();
      pipeline.lens = lens;
      pipeline.outer = PostProcessor.filter(lens, allRows(), box);
      created.add(lens);
      created.add(pipeline.outer);
      return pipeline;
   }

   private static ConditionGroup allRows() {
      Condition condition = new Condition();
      condition.setOperation(Condition.GREATER_THAN);
      condition.addValue(-1);
      condition.setType(XSchema.INTEGER);
      ConditionGroup group = new ConditionGroup();
      group.addCondition(1, condition, 0);
      return group;
   }

   private static List<List<Object>> drain(TableLens table) {
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

   private static void awaitQueued(AtomicReference<Thread> ref, AtomicBoolean locking)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + TIMEOUT * 1000L;

      // the only wait after the flag is set is the lock's, which waits in slices for the
      // lock-stall watchdog (bug #76967)
      while(!locking.get() || !isParked(ref.get())) {
         assertTrue(System.currentTimeMillis() < deadline, "script thread never queued on the lock");
         Thread.sleep(5);
      }
   }

   private static boolean isParked(Thread thread) {
      Thread.State state = thread.getState();
      return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING;
   }

   public enum Kind {
      SUMMARY, DISTINCT, SELF_JOIN, UNION
   }

   public enum StackedKind {
      SUMMARY_OVER_SUMMARY, DISTINCT_OVER_SUMMARY
   }

   private static final class Pipeline {
      TableLens lens;
      TableLens outer;
      List<List<Object>> outerRows;
      List<List<Object>> lensRows;
   }

   /**
    * A base table with duplicate rows that is slow to read on any thread other than the
    * test's own threads, i.e. on the lens workers.
    */
   private static class SlowTable extends DefaultTableLens {
      SlowTable() {
         super(data());
      }

      @Override
      public Object getObject(int r, int c) {
         if(r > 0 && c == 1 && !FAST.get()) {
            try {
               Thread.sleep(1);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.getObject(r, c);
      }

      private static Object[][] data() {
         Object[][] data = new Object[ROWS + 1][];
         data[0] = new Object[] {"group", "value", "zero"};

         for(int i = 1; i <= ROWS; i++) {
            data[i] = new Object[] {"k" + (i % 6), i % 30, 0};
         }

         return data;
      }
   }

   /**
    * Runs the loan gate when a lens worker reads the last row of the inner condition
    * filter, before the read enters the filter and its monitor.
    */
   private static final class GateTable extends DefaultTableFilter {
      GateTable(TableLens table, Runnable gate) {
         super(table);
         this.gate = gate;
      }

      @Override
      public boolean moreRows(int row) {
         gate(row);
         return super.moreRows(row);
      }

      @Override
      public Object getObject(int r, int c) {
         gate(r);
         return super.getObject(r, c);
      }

      @Override
      public Object getData(int r, int c) {
         gate(r);
         return super.getData(r, c);
      }

      private void gate(int r) {
         if(r >= ROWS && !FAST.get()) {
            gate.run();
         }
      }

      private final Runnable gate;
   }

   /**
    * A slow base table whose values are computed by running a script on the engine,
    * like a calc field. The script returns 2 and leaves the value unchanged.
    */
   private static final class ExecTable extends SlowTable {
      ExecTable(GraalJavaScriptEngine engine, Object script, Runnable gate) {
         this.engine = engine;
         this.script = script;
         this.gate = gate;
      }

      @Override
      public Object getObject(int r, int c) {
         if(gate != null && r == ROWS && c == 1 && !FAST.get()) {
            gate.run();
         }

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
      private final Runnable gate;
   }

   private static final int ROWS = 120;
   private static final long TIMEOUT = 30;
   private static final long GATE_TIMEOUT = 10;
   private static final ThreadLocal<Boolean> FAST = ThreadLocal.withInitial(() -> false);
   private static final Map<Kind, Pipeline> CONTROLS = new ConcurrentHashMap<>();
   private static final Map<StackedKind, List<List<Object>>> STACKED_CONTROLS = new ConcurrentHashMap<>();

   private final List<TableLens> created = new ArrayList<>();
   private GraalJavaScriptEngine engine;
   private LendableReentrantLock lock;
   private AssetQuerySandbox box;
   private ExecutorService pool;
   private AtomicBoolean lentSeen;
   private volatile boolean watching;
   private Thread watcher;
}

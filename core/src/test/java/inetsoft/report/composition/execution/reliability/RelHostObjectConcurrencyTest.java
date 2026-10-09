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
package inetsoft.report.composition.execution.reliability;

import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.*;
import inetsoft.report.script.TableArray;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A3b of the worksheet script context pool reliability report (Testing #77123): host objects
 * of one sandbox that pooled contexts now reach at the same time, where the single engine lock
 * used to serialize them. Each probe runs 12 threads on a real {@link AssetQuerySandbox}, pool
 * on, and the same with the pool off as a control, and compares every value read with the
 * value computed in Java.
 * <ul>
 * <li>{@link #tableArrayReads}: {@code T.length}, {@code T.size}, {@code T[i][col]},
 * {@code T[i]['name']} over one embedded table, with sequential, backward, strided, ping-pong
 * and random row orders so the 100-row window of {@link TableArray} slides on every read. The
 * table array is the shared scope's (one object for every context), a query view's shared by
 * every thread, the shared one reached through the {@code worksheet} env global from
 * condition-style per-evaluation scopes, or a per-evaluation one.</li>
 * <li>{@link #sharedWindowIsDetected}: the same reads over a table array that keeps one shared
 * window (no per-slot window, as before bug #76960) find wrong rows, so the probe detects the
 * race the per-slot window prevents.</li>
 * <li>{@link #formulaTablesReadTableByName}: the production path. Eight worksheet tables, each
 * with an expression column that reads another table by name, are read at the same time.</li>
 * <li>{@link #parameterWritesPoolOff}, {@link #tableArrayMemberWritesPoolOff}: writes to the
 * {@code parameter} scriptable (the sandbox's {@link VariableTable}) and to a table array's own
 * members. Both lost writes with the pool on until they were locked (A3b findings):
 * {@link #parameterWritesPoolOn} and {@link #tableArrayMemberWritesPoolOn}.</li>
 * </ul>
 * About 30-45 s with the Spring context, so tagged slow: run it with
 * {@code -Dsurefire.groups=slow -Dtest=RelHostObjectConcurrencyTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  RelHostObjectConcurrencyTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
public class RelHostObjectConcurrencyTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @AfterEach
   public void tearDown() {
      for(AssetQuerySandbox box : boxes) {
         box.dispose();
      }

      boxes.clear();
      SreeEnv.remove(PoolConfig.ENABLED);
   }

   /**
    * Which table array the threads read, and through which name.
    */
   enum Access {
      // the sandbox's shared scope: one TableAssemblyScriptable for every context
      SHARED_SCOPE("T1"),
      // condition-style per-evaluation scopes reaching the shared scope's table through the
      // worksheet env global
      WORKSHEET_GLOBAL("worksheet['T1']"),
      // one query view used by every thread
      SHARED_VIEW("T1"),
      // a per-evaluation scope, as a JS condition value: a table array of its own each exec
      FRESH_SCOPE("T1");

      Access(String ref) {
         this.ref = ref;
      }

      final String ref;
   }

   static Stream<Arguments> accessModes() {
      return Stream.of(true, false).flatMap(
         pool -> Arrays.stream(Access.values()).map(a -> Arguments.of(pool, a)));
   }

   @ParameterizedTest(name = "pool={0} {1}")
   @MethodSource("accessModes")
   public void tableArrayReads(boolean pool, Access access) throws Exception {
      AssetQuerySandbox box = sandbox(pool, 0);
      ScriptEnv env = box.getScriptEnv();
      AssetQueryScope shared = box.getScope(); // also puts the worksheet env global
      AssetQueryScope view = shared.queryView(box.getVariableTable(),
                                              AssetQuerySandbox.RUNTIME_MODE);
      Probe probe = new Probe();
      env.put("probe", probe);

      Supplier<Object> scope = switch(access) {
         case SHARED_SCOPE -> () -> shared;
         case SHARED_VIEW -> () -> view;
         case WORKSHEET_GLOBAL, FRESH_SCOPE -> box::createAssetQueryScope;
      };

      Result result = runReaders(env, access.ref, scope, probe);
      report("tableArrayReads pool=" + pool + " " + access, result, probe);
      assertEquals(List.of(), result.mismatches, "wrong values read concurrently");
      assertEquals(THREADS * EXECS, result.execs.get(), "execs");

      if(pool) {
         assertTrue(probe.maxInFlight.get() >= 2,
                    "the pooled execs never overlapped: " + probe.maxInFlight.get());
      }
   }

   /**
    * Sensitivity of {@link #tableArrayReads}: a table array that keeps one window for every
    * context hands one context another's rows, which the probe reports. The same reads over a
    * per-slot window array find none.
    */
   @Test
   public void sharedWindowIsDetected() throws Exception {
      AssetQuerySandbox box = sandbox(true, 0);
      ScriptEnv env = box.getScriptEnv();
      TableLens lens = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE);
      lens.moreRows(TableLens.EOT);
      Probe probe = new Probe();
      env.put("probe", probe);
      env.put("sharedWin", new TableArray(lens));
      env.put("slotWin", new TableArray(lens) {
         @Override
         protected boolean usePerSlotWindows() {
            return true;
         }
      });

      Result perSlot = runReaders(env, "slotWin", () -> null, probe);
      report("sharedWindowIsDetected per-slot", perSlot, probe);
      assertEquals(List.of(), perSlot.mismatches, "per-slot windows read wrong rows");

      int found = 0;

      for(int round = 0; round < 10 && found == 0; round++) {
         Result sharedWin = runReaders(env, "sharedWin", () -> null, probe);
         report("sharedWindowIsDetected shared round " + round, sharedWin, probe);
         found = sharedWin.mismatchCount.get();
      }

      assertTrue(found > 0, "a shared row window raced by pooled contexts was not detected; " +
         "the probe cannot see the race the per-slot window prevents");
   }

   /**
    * The production path: eight tables, each with an expression column reading T1 by name
    * ({@code T1.length}, {@code T1[i]['name']}, {@code T1[i][3]}), built and read at the same
    * time, three rounds with a reset between.
    */
   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { true, false })
   public void formulaTablesReadTableByName(boolean pool) throws Exception {
      int tables = 8;
      AssetQuerySandbox box = sandbox(pool, tables);
      ExecutorService exec = Executors.newFixedThreadPool(tables);
      List<String> mismatches = Collections.synchronizedList(new ArrayList<>());
      AtomicInteger cells = new AtomicInteger();
      long start = System.nanoTime();

      try {
         for(int round = 0; round < 3; round++) {
            CyclicBarrier barrier = new CyclicBarrier(tables);
            List<Future<?>> futures = new ArrayList<>();

            for(int t = 0; t < tables; t++) {
               int k = t;
               boolean reset = round > 0;
               futures.add(exec.submit(() -> {
                  barrier.await(60, TimeUnit.SECONDS);
                  String name = "F" + k;

                  if(reset) {
                     box.resetTableLens(name);
                  }

                  TableLens lens = box.getTableLens(name, AssetQuerySandbox.RUNTIME_MODE);
                  lens.moreRows(TableLens.EOT);
                  int col = col(lens, "look");
                  int n = 0;

                  for(int r = 1; lens.moreRows(r); r++) {
                     Object v = lens.getObject(r, col);
                     String expected = lookup(r, k);
                     cells.incrementAndGet();

                     if(!expected.equals(v)) {
                        mismatches.add(name + " row " + r + ": " + v + " expected " + expected);
                     }

                     n++;
                  }

                  if(n != F_ROWS) {
                     mismatches.add(name + " has " + n + " rows");
                  }

                  return null;
               }));
            }

            for(Future<?> f : futures) {
               f.get(120, TimeUnit.SECONDS);
            }
         }
      }
      finally {
         exec.shutdownNow();
      }

      System.out.printf("RelHostObjectConcurrencyTest formulaTablesReadTableByName pool=%s: " +
                        "%d cells, %d mismatches, %d ms%n", pool, cells.get(), mismatches.size(),
                        (System.nanoTime() - start) / 1_000_000);
      assertEquals(List.of(), mismatches.subList(0, Math.min(10, mismatches.size())));
      assertEquals(3 * tables * F_ROWS, cells.get());
   }

   /**
    * {@code parameter} of a condition-style scope (and of the shared scope, and of the views
    * that {@code AssetDataCache}, {@code VSAQuery}, {@code MVAssetQuery} and the crosstab
    * queries make) is the sandbox's one {@link VariableTable}. Readers read a fixed parameter
    * while writers add new ones from pooled contexts at the same time; every read must see
    * the fixed value, and every write must land with its as-is flag
    * ({@code VariableScriptable.putMember} sets both). Checked pool off here, where the engine
    * lock serializes the scripts; pool on, see {@link #parameterWritesPoolOn}.
    */
   @Test
   public void parameterWritesPoolOff() throws Exception {
      ParameterOutcome outcome = parameterWrites0(false);
      assertEquals(0, outcome.badReads, "a read of an existing parameter missed");
      assertEquals(List.of(), head(outcome.lostValues),
                   outcome.lostValues.size() + " parameter writes lost");
      assertEquals(List.of(), head(outcome.lostFlags),
                   outcome.lostFlags.size() + " as-is flags lost");
   }

   /**
    * A3b finding (Testing #77123), fixed by locking {@code get0}, {@code contains},
    * {@code setAsIs} and {@code isAsIs} on the vartable monitor: the sandbox's
    * {@link VariableTable} was not safe for scripts of pooled contexts that write parameters
    * at the same time. Every condition-style
    * per-evaluation scope ({@code AssetQuerySandbox.createAssetQueryScope}, e.g. a JS condition
    * value in {@code ConditionGroup}) holds it as {@code parameter}, as do the shared scope and
    * the views {@code AssetDataCache}, {@code VSAQuery}, {@code MVAssetQuery} and the crosstab
    * queries make. With the pool off the engine lock serialized these scripts.
    * <ul>
    * <li>{@code setAsIs} creates and fills its {@code asIs} HashSet without a lock (only
    * {@code put} holds the vartable monitor): as-is flags are lost (observed 0-65 of 2400 per
    * round), so the value is parsed again (e.g. split into an array) where the script meant it
    * as-is.</li>
    * <li>{@code get0} reads the vartable HashMap without the monitor that {@code put} holds:
    * a read of an existing parameter during a concurrent resize returns null (observed 1 of
    * 12000 reads in one round).</li>
    * </ul>
    */
   @Test
   public void parameterWritesPoolOn() throws Exception {
      for(int round = 0; round < 10; round++) {
         ParameterOutcome outcome = parameterWrites0(true);
         assertEquals(0, outcome.badReads,
                      "a read of an existing parameter missed in round " + round);
         assertEquals(List.of(), head(outcome.lostValues),
                      outcome.lostValues.size() + " parameter writes lost in round " + round);
         assertEquals(List.of(), head(outcome.lostFlags),
                      outcome.lostFlags.size() + " as-is flags lost in round " + round);
         tearDown();
      }
   }

   private ParameterOutcome parameterWrites0(boolean pool) throws Exception {
      AssetQuerySandbox box = sandbox(pool, 0);
      ScriptEnv env = box.getScriptEnv();
      VariableTable vars = box.getVariableTable();
      vars.put("fixed", 42);
      int writers = THREADS / 2;
      int keys = 400;
      Object read = env.compile("(function() { var bad = 0; for(var k = 0; k < 2000; k++) { " +
                                "if(parameter.fixed != 42) bad++; } return bad; })()");
      List<Object> writes = new ArrayList<>();

      for(int t = 0; t < writers; t++) {
         writes.add(env.compile("(function() { for(var k = 0; k < " + keys + "; k++) { " +
                                "parameter['w_" + t + "_' + k] = k; } return 0; })()"));
      }

      ExecutorService exec = Executors.newFixedThreadPool(THREADS);
      CountDownLatch go = new CountDownLatch(1);
      List<Future<Object>> futures = new ArrayList<>();
      long start = System.nanoTime();

      try {
         for(int t = 0; t < THREADS; t++) {
            Object script = t < writers ? writes.get(t) : read;
            futures.add(exec.submit(() -> {
               go.await();
               return env.exec(script, box.createAssetQueryScope(), null, null);
            }));
         }

         go.countDown();
         int badReads = 0;

         for(Future<Object> f : futures) {
            badReads += ((Number) f.get(120, TimeUnit.SECONDS)).intValue();
         }

         ParameterOutcome outcome = new ParameterOutcome();
         outcome.badReads = badReads;

         for(int t = 0; t < writers; t++) {
            for(int k = 0; k < keys; k++) {
               String name = "w_" + t + "_" + k;
               Object v = vars.get(name);

               if(!(v instanceof Number) || ((Number) v).intValue() != k) {
                  outcome.lostValues.add(name + "=" + v);
               }
               else if(!vars.isAsIs(name)) {
                  outcome.lostFlags.add(name + " lost its as-is flag");
               }
            }
         }

         System.out.printf("RelHostObjectConcurrencyTest parameterWrites pool=%s: %d bad reads, " +
                           "%d lost writes, %d lost as-is flags of %d, %d ms%n", pool, badReads,
                           outcome.lostValues.size(), outcome.lostFlags.size(), writers * keys,
                           (System.nanoTime() - start) / 1_000_000);
         return outcome;
      }
      finally {
         exec.shutdownNow();
      }
   }

   /**
    * A script can set its own members on a table array ({@code worksheet['T1'].tag = 1},
    * {@link TableArray#putMember}); the shared scope's table array is one object for every
    * pooled context. Every write must land: checked pool off here (the engine lock
    * serializes the writes) and pool on by {@link #tableArrayMemberWritesPoolOn}.
    */
   @Test
   public void tableArrayMemberWritesPoolOff() throws Exception {
      List<String> lost = tableArrayMemberWrites(false);
      assertEquals(List.of(), head(lost), lost.size() + " table array member writes lost");
   }

   /**
    * A3b finding (Testing #77123), fixed by a synchronized {@code TableArray.members}: it was
    * a plain LinkedHashMap.
    * {@code worksheet['T1']} from a script on a non-view scope (a JS condition value's
    * per-evaluation scope) is the shared scope's {@code TableAssemblyScriptable}, one object
    * for every pooled context of the sandbox, so concurrent {@code worksheet['T1'].x = v}
    * writes race in {@code putMember} and entries are lost (and {@code hasMember}'s
    * {@code containsKey} reads the map unlocked). Observed 0-54 of 4800 writes lost per round
    * (0 with the pool off). Writing own members on a table array is rare in real content.
    */
   @Test
   public void tableArrayMemberWritesPoolOn() throws Exception {
      for(int round = 0; round < 10; round++) {
         List<String> lost = tableArrayMemberWrites(true);
         assertEquals(List.of(), head(lost),
                      lost.size() + " table array member writes lost in round " + round);
         tearDown();
      }
   }

   private List<String> tableArrayMemberWrites(boolean pool) throws Exception {
      AssetQuerySandbox box = sandbox(pool, 0);
      ScriptEnv env = box.getScriptEnv();
      AssetQueryScope shared = box.getScope();
      int keys = 400;
      List<Object> scripts = new ArrayList<>();

      for(int t = 0; t < THREADS; t++) {
         scripts.add(env.compile("(function() { var t = worksheet['T1']; for(var k = 0; k < " +
                                 keys + "; k++) { t['tag_" + t + "_' + k] = k; } " +
                                 "return t.length; })()"));
      }

      ExecutorService exec = Executors.newFixedThreadPool(THREADS);
      CountDownLatch go = new CountDownLatch(1);
      List<Future<Object>> futures = new ArrayList<>();
      long start = System.nanoTime();

      try {
         for(int t = 0; t < THREADS; t++) {
            Object script = scripts.get(t);
            futures.add(exec.submit(() -> {
               go.await();
               return env.exec(script, box.createAssetQueryScope(), null, null);
            }));
         }

         go.countDown();

         for(Future<Object> f : futures) {
            assertEquals(T1_ROWS + 1, ((Number) f.get(120, TimeUnit.SECONDS)).intValue());
         }

         Object array = shared.getMember("T1");
         Field field = TableArray.class.getDeclaredField("members");
         field.setAccessible(true);
         Map<?, ?> members = (Map<?, ?>) field.get(array);
         List<String> lost = new ArrayList<>();

         for(int t = 0; t < THREADS; t++) {
            for(int k = 0; k < keys; k++) {
               Object v = members.get("tag_" + t + "_" + k);

               if(!(v instanceof Number) || ((Number) v).intValue() != k) {
                  lost.add("tag_" + t + "_" + k + "=" + v);
               }
            }
         }

         System.out.printf("RelHostObjectConcurrencyTest tableArrayMemberWrites pool=%s: " +
                           "%d members, %d lost of %d, %d ms%n", pool, members.size(),
                           lost.size(), THREADS * keys, (System.nanoTime() - start) / 1_000_000);
         return lost;
      }
      finally {
         exec.shutdownNow();
      }
   }

   // ---------------------------------------------------------------------------------------

   /**
    * THREADS threads, EXECS execs each, every exec reading READS rows of {@code ref} in one of
    * the row orders and returning them as text, compared with the text computed in Java.
    */
   private static Result runReaders(ScriptEnv env, String ref, Supplier<Object> scope,
                                    Probe probe) throws Exception
   {
      probe.maxInFlight.set(0);
      List<int[]> orders = new ArrayList<>();
      List<Object> scripts = new ArrayList<>();
      Random random = new Random(76960);

      for(int p = 0; p < PATTERNS; p++) {
         int[] rows = order(p, random);
         orders.add(rows);
         scripts.add(env.compile(readScript(ref, rows)));
      }

      Result result = new Result();
      ExecutorService exec = Executors.newFixedThreadPool(THREADS);
      CountDownLatch go = new CountDownLatch(1);
      List<Future<?>> futures = new ArrayList<>();
      long start = System.nanoTime();

      try {
         for(int t = 0; t < THREADS; t++) {
            int id = t;
            futures.add(exec.submit(() -> {
               go.await();

               for(int e = 0; e < EXECS; e++) {
                  int p = (id + e) % PATTERNS;
                  String expected = expected(orders.get(p));
                  Object actual;

                  try {
                     actual = env.exec(scripts.get(p), scope.get(), null, null);
                  }
                  catch(Exception ex) {
                     actual = ex.toString();
                  }

                  result.execs.incrementAndGet();

                  if(!expected.equals(actual)) {
                     result.mismatch("thread " + id + " pattern " + p + ": " +
                                     firstDifference(expected, String.valueOf(actual)));
                  }
               }

               return null;
            }));
         }

         go.countDown();

         for(Future<?> f : futures) {
            f.get(180, TimeUnit.SECONDS);
         }
      }
      finally {
         exec.shutdownNow();
      }

      result.millis = (System.nanoTime() - start) / 1_000_000;
      return result;
   }

   /**
    * Row order {@code p}: sequential, backward, strided past the window, ping-pong between
    * two rows half the table apart, or random.
    */
   private static int[] order(int p, Random random) {
      int[] rows = new int[READS];
      int start = random.nextInt(T1_ROWS);

      for(int k = 0; k < READS; k++) {
         rows[k] = 1 + switch(p % 5) {
            case 0 -> (start + k) % T1_ROWS;
            case 1 -> T1_ROWS - 1 - (start + k) % T1_ROWS;
            case 2 -> (start + k * 137) % T1_ROWS;
            case 3 -> (start + k / 2 + (k % 2) * (T1_ROWS / 2)) % T1_ROWS;
            default -> random.nextInt(T1_ROWS);
         };
      }

      return rows;
   }

   private static String readScript(String ref, int[] rows) {
      StringBuilder idx = new StringBuilder();

      for(int r : rows) {
         idx.append(idx.length() == 0 ? "" : ",").append(r);
      }

      return "(function() { probe.enter(); var t = " + ref + "; var rows = [" + idx + "]; " +
         "var out = [t.length, t.size]; " +
         "for(var k = 0; k < rows.length; k++) { var i = rows[k]; var r = t[i]; " +
         "out.push(i + '=' + r['name'] + '/' + r[3] + '/' + t[i]['id'] + '/' + t[i][2]); } " +
         "probe.leave(); return out.join(','); })()";
   }

   private static String expected(int[] rows) {
      StringBuilder str = new StringBuilder().append(T1_ROWS + 1).append(',').append(4);

      for(int i : rows) {
         str.append(',').append(i).append("=n").append(i).append('/').append(i * 3)
            .append('/').append(i).append('/').append(i % 7);
      }

      return str.toString();
   }

   private static String firstDifference(String expected, String actual) {
      String[] e = expected.split(",");
      String[] a = actual.split(",");

      for(int i = 0; i < Math.min(e.length, a.length); i++) {
         if(!e[i].equals(a[i])) {
            return "item " + i + " read " + a[i] + " expected " + e[i];
         }
      }

      return e.length != a.length ? "length " + a.length + " expected " + e.length + ": " +
         (actual.length() > 200 ? actual.substring(0, 200) : actual) : "?";
   }

   // the value of F<k>'s look column on row r
   private static String lookup(int r, int k) {
      int i = (r * 37 + k * 101) % T1_ROWS + 1;
      return "n" + i + "/" + (i * 3) + "/" + (T1_ROWS + 1);
   }

   private AssetQuerySandbox sandbox(boolean pool, int formulaTables) {
      SreeEnv.setProperty(PoolConfig.ENABLED, String.valueOf(pool));
      Worksheet ws = new Worksheet();
      Object[][] data = new Object[T1_ROWS + 1][];
      data[0] = new Object[] { "id", "name", "grp", "value" };

      for(int i = 1; i <= T1_ROWS; i++) {
         data[i] = new Object[] { i, "n" + i, i % 7, i * 3 };
      }

      EmbeddedTableAssembly t1 = new EmbeddedTableAssembly(ws, "T1");
      t1.setEmbeddedData(new XEmbeddedTable(new String[] {
         XSchema.INTEGER, XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER }, data));
      t1.setProperty("no_cache", "true");
      ws.addAssembly(t1);

      for(int k = 0; k < formulaTables; k++) {
         EmbeddedTableAssembly f = new EmbeddedTableAssembly(ws, "F" + k);
         Object[][] fdata = new Object[F_ROWS + 1][];
         fdata[0] = new Object[] { "id" };

         for(int r = 1; r <= F_ROWS; r++) {
            fdata[r] = new Object[] { r };
         }

         f.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER }, fdata));
         f.setProperty("no_cache", "true");
         ws.addAssembly(f);
         ColumnSelection columns = f.getColumnSelection(false);
         String i = "((field['id'] * 37 + " + (k * 101) + ") % " + T1_ROWS + " + 1)";
         ExpressionRef exp = new ExpressionRef(null, "look");
         exp.setExpression("T1[" + i + "]['name'] + '/' + T1[" + i + "][3] + '/' + T1.length");
         ColumnRef column = new ColumnRef(exp);
         column.setDataType(XSchema.STRING);
         columns.addAttribute(column);
         f.setColumnSelection(columns, false);
      }

      AssetDataCache.getCache().clearCache();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      boxes.add(box);
      assertEquals(pool, box.isScriptPoolMode());
      assertEquals(pool, box.getScriptEnv() instanceof WorksheetScriptEnv);
      return box;
   }

   private static int col(TableLens lens, String name) {
      for(int c = 0; c < lens.getColCount(); c++) {
         if(name.equals(String.valueOf(lens.getObject(0, c)))) {
            return c;
         }
      }

      throw new AssertionError("no column " + name);
   }

   private static void report(String name, Result result, Probe probe) {
      System.out.printf("RelHostObjectConcurrencyTest %s: %d execs, %d mismatches, " +
                        "max in flight %d, %d ms%s%n", name, result.execs.get(),
                        result.mismatchCount.get(), probe.maxInFlight.get(), result.millis,
                        result.mismatches.isEmpty() ? "" : ", first: " + result.mismatches.get(0));
   }

   /**
    * Counts execs in flight, called from inside the scripts.
    */
   public static final class Probe {
      public void enter() {
         int n = inFlight.incrementAndGet();
         maxInFlight.accumulateAndGet(n, Math::max);
      }

      public void leave() {
         inFlight.decrementAndGet();
      }

      final AtomicInteger inFlight = new AtomicInteger();
      final AtomicInteger maxInFlight = new AtomicInteger();
   }

   private static List<String> head(List<String> list) {
      return list.subList(0, Math.min(10, list.size()));
   }

   private static final class ParameterOutcome {
      int badReads;
      final List<String> lostValues = new ArrayList<>();
      final List<String> lostFlags = new ArrayList<>();
   }

   private static final class Result {
      void mismatch(String text) {
         if(mismatchCount.incrementAndGet() <= 5) {
            mismatches.add(text);
         }
      }

      final AtomicInteger execs = new AtomicInteger();
      final AtomicInteger mismatchCount = new AtomicInteger();
      final List<String> mismatches = Collections.synchronizedList(new ArrayList<>());
      long millis;
   }

   private static final int THREADS = 12;
   private static final int EXECS = 20;
   private static final int READS = 250;
   private static final int PATTERNS = 10;
   private static final int T1_ROWS = 1000;
   private static final int F_ROWS = 300;
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
}

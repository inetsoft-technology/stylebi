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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.TableLens;
import inetsoft.report.script.TableRowScope;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testing #77123 (B1 residual) end to end: a worksheet embedded table whose expression column
 * keeps a Date in a top-level var, run by a real {@link AssetQuerySandbox} with the context
 * pool on, while some of its batches run on another pooled context (another thread holds the
 * usual one). The Date gives what the pool off gives, whether the table is read row by row,
 * in 100-row pages, from a deep row first, at once, or through a post condition (a sort reads
 * its formula table in one batch as the table opens, so there it only matches the pool off);
 * two vars holding one Date still hold one object; a Date that loops in every method, or a
 * Proxy of one, does not hang the batch end; a batch ended by the script timeout keeps its
 * Date. The table is Date-only, so it takes the batch-end Date path (no home); the pool's
 * count of owned-var reads made on another context (CrossReads) shows that each read really
 * ran batches on another context. The tree modes add a second column that keeps a counter
 * closure in a var (a function is not kept across a hand-off, so it restarts there): the
 * table then keeps its objects on a home (B1 residual part 2), with no exclusive home, so the
 * other thread's claim takes that home over after a hand-off (the Dates are saved in the same
 * tree) and the table's next batch runs on another context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, PooledWorksheetDateVarTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledWorksheetDateVarTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   // one calendar day per row, from 2020-01-02 on row 1
   static final String START =
      "var start = start || new Date(2020, 0, 1); start.setDate(start.getDate() + 1); " +
      "start.getTime()";
   static final String ALIAS =
      "var a = a || new Date(0); var b = b || a; b.setTime(b.getTime() + 1000); " +
      "(a === b ? 1 : -1) * a.getTime() / 1000";
   // not kept across a hand-off, so it restarts there: proof that a read crossed
   static final String WITNESS =
      "var w = w || (function() { var n = 0; return function() { return ++n; }; })(); w()";

   @BeforeEach
   void capture() {
      logger = (Logger) LoggerFactory.getLogger(TableRowScope.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      SreeEnv.remove(PoolConfig.ENABLED);
      SreeEnv.remove(MAX_HOMES);

      for(AssetQuerySandbox box : boxes) {
         if(box.peekScriptEnv() instanceof WorksheetScriptEnv env) {
            env.retire();
         }
      }

      boxes.clear();
   }

   /**
    * A Date accumulator and two vars holding one Date give, with the pool on and batches on
    * another context, exactly what the pool off gives, however the table is read.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "seq", "pages", "jump", "eot", "openHeld", "cf2", "sort",
                            "tree pages", "tree openHeld" })
   void aDateVarGivesWhatThePoolOffGives(String mode) throws Exception {
      for(String f : new String[] { START, ALIAS }) {
         Result off = run(false, mode, f);
         Result on = run(true, mode, f);

         if(f == START) {
            for(int id = 2; id <= ROWS; id++) {
               double d = off.out[id] - off.out[id - 1];
               assertTrue(d >= 23 * HOUR && d <= 25 * HOUR, "pool off, id " + id + ": " + d);
            }
         }
         else if(!mode.equals("sort")) {
            for(int id = 1; id <= ROWS; id++) {
               assertEquals(id, off.out[id], "pool off alias, id " + id);
            }
         }

         List<String> bad = new ArrayList<>();

         for(int id = 1; id <= ROWS; id++) {
            if(on.out[id] != off.out[id]) {
               bad.add(id + ": " + on.out[id] + " != " + off.out[id]);
            }
         }

         assertTrue(bad.isEmpty(), () -> mode + " " + (f == START ? "start" : "alias") + ": " +
            bad.size() + " rows differ from pool off, first " +
            bad.subList(0, Math.min(5, bad.size())));
         // a sort reads its formula table in one batch when the table opens: nothing to cross
         if(mode.startsWith("tree")) {
            assertFalse(Arrays.equals(on.wit, off.wit),
                        mode + ": no batch ran on another context");
         }
         else if(!mode.equals("sort")) {
            assertTrue(on.crossed(), mode + ": no batch ran on another context");
            assertEquals(0, on.homes, mode + ": a Date-only table has no home");
         }
      }
   }

   /**
    * A Date whose methods and an enumerable getter loop forever, a Date behind a looping
    * Proxy prototype, and a Proxy of a Date with looping traps: no batch end hangs, the Dates
    * continue, and the Proxy reads as undefined on another context with one warning.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "hostile", "protoProxy", "proxy" })
   void aHostileDateDoesNotHangTheBatchEnd(String what) {
      String loop = "var loop = function() { while(true) {} }; ";
      String f = switch(what) {
      case "hostile" -> "var d = d || (function() { " + loop + "var x = new Date(0); " +
         "x.valueOf = loop; x.toString = loop; x.toJSON = loop; x[Symbol.toPrimitive] = loop; " +
         "Object.defineProperty(x, 'g', { get: loop, enumerable: true }); " +
         "Object.defineProperty(x, 'constructor', { get: loop, enumerable: true }); " +
         "return x; })(); " + STEP;
      case "protoProxy" -> "var d = d || (function() { " + loop + "var x = new Date(0); " +
         "Object.setPrototypeOf(x, new Proxy(Date.prototype, { get: loop, has: loop, " +
         "ownKeys: loop, getPrototypeOf: loop, getOwnPropertyDescriptor: loop })); " +
         "return x; })(); " + STEP;
      default -> "var made = made || 0; var p = p || (made++, new Proxy(new Date(0), { " +
         "ownKeys() { while(true) {} }, get() { while(true) {} }, " +
         "getPrototypeOf() { while(true) {} }, getOwnPropertyDescriptor() { while(true) {} }, " +
         "has() { while(true) {} } })); made";
      };

      assertTimeoutPreemptively(Duration.ofSeconds(120), () -> {
         // a Proxy is no Date: that table keeps it on a home, taken over by the other thread
         Result on = run(true, what.equals("proxy") ? "tree pages" : "pages", f, false);
         assertTrue(on.crossed(), "no batch ran on another context");

         if(what.equals("proxy")) {
            assertTrue(on.out[ROWS] > 1, "the Proxy is not kept on another context");
            assertTrue(warnings().stream().anyMatch(e -> e.getFormattedMessage().contains(
               "\"p\" holds a Proxy object")), () -> "" + warnings());
         }
         else {
            for(int id = 1; id <= ROWS; id++) {
               assertEquals(id, on.out[id], what + ", id " + id);
            }
         }
      });
   }

   /**
    * A batch that the script timeout ends: the next batch, on another context, continues
    * from its Date.
    */
   @Test
   void aBatchEndedByTheScriptTimeoutKeepsItsDate() throws Exception {
      String previous = SreeEnv.getProperty("script.execution.timeout");
      // a batch of a thousand rows must finish well within it on a loaded machine
      SreeEnv.setProperty("script.execution.timeout", "5");
      refreshTimeout();

      try {
         assertTimeoutPreemptively(Duration.ofSeconds(120), () -> {
            String f = "var hit = hit || 0; var d = d || new Date(0); " +
               "if(field['id'] == 2300 && !hit) { hit = 1; while(true) {} } " + STEP;
            AssetQuerySandbox box = box(ws(f, false), true, false);
            WorksheetScriptEnv env = (WorksheetScriptEnv) box.getScriptEnv();
            TableLens t = box.getTableLens("A", RUNTIME);
            Result res = new Result(t);
            int failedAt = -1;

            for(int r = 1; r <= ROWS; r++) {
               try {
                  read(t, res, r, r);
               }
               catch(Throwable ex) {
                  failedAt = r;
                  break;
               }
            }

            assertTrue(failedAt > 0 && failedAt <= 2300, "the timeout ended a batch: " +
               failedAt);
            int from = failedAt;
            PoolTestSupport.whileHeldElsewhere(env, () -> {
               read(t, res, from, 2299);
               // the timed-out row has no value: its read fails with the stop, every time,
               // without running into the timeout again (bug #77949)
               assertTrue(t.moreRows(2300));
               assertEquals(2300, (int) num(t.getObject(2300, res.cid)));

               for(int i = 0; i < 2; i++) {
                  long start = System.currentTimeMillis();
                  Throwable stop = assertThrows(Throwable.class, () -> t.getObject(2300, res.cout));
                  assertTrue(ScriptTimeoutGuard.isStop(stop), "a stop: " + stop);
                  assertTrue(System.currentTimeMillis() - start < 2000, "not run again");
               }

               res.out[2300] = Double.NaN;
               read(t, res, 2301, 2700);
            });
            read(t, res, 2701, ROWS);

            // it stopped before its step, so the rows after it are one second behind their
            // id, as with the pool off
            for(int id = 1; id <= ROWS; id++) {
               double expected = id < 2300 ? id : id == 2300 ? Double.NaN : id - 1;
               assertEquals(expected, res.out[id],
                            "id " + id + " (timeout at row " + failedAt + ")");
            }
         });
      }
      finally {
         if(previous == null) {
            SreeEnv.remove("script.execution.timeout");
         }
         else {
            SreeEnv.setProperty("script.execution.timeout", previous);
         }

         refreshTimeout();
      }
   }

   /**
    * An array or object in a var is kept on another context, as with the pool off, with no
    * warning (B1 residual part 2); a function reads as undefined there, with one warning, and
    * the idiom creates it again, so the count is kept.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "array", "object", "function" })
   void anArrayOrObjectVarIsKeptAndAFunctionIsPerBatchWithOneWarning(String kind)
      throws Exception
   {
      String f = switch(kind) {
      case "array" -> "var q = q || []; q.push(1); q.length";
      case "object" -> "var q = q || { n: 0 }; q.n++; q.n";
      default -> "var q = q || function() { return 1; }; var k = (k || 0) + q(); k";
      };

      Result off = run(false, "pages", f);

      for(int id = 1; id <= ROWS; id++) {
         assertEquals(id, off.out[id], "pool off, id " + id);
      }

      assertEquals(0, warnings().size(), () -> "pool off: " + warnings());

      // the other thread's claim takes the table's home over (tree), with no witness column
      Result on = run(true, "tree pages", f, false);
      assertTrue(on.crossed(), "no batch ran on another context");
      List<String> mine = warnings().stream().map(ILoggingEvent::getFormattedMessage)
         .filter(m -> m.contains("\"q\"")).toList();

      for(int id = 1; id <= ROWS; id++) {
         assertEquals(id, on.out[id], kind + ", id " + id);
      }

      if(kind.equals("function")) {
         assertEquals(1, mine.size(), () -> "one warning for q: " + mine);
         assertTrue(mine.get(0).contains("\"q\" holds a function"), mine.get(0));
      }
      else {
         assertEquals(0, mine.size(), () -> "no warning for q: " + mine);
      }
   }

   // --- helpers ---

   // a step of one second that calls no method of the Date itself
   private static final String STEP =
      "Date.prototype.setTime.call(d, Date.prototype.getTime.call(d) + 1000); " +
      "Date.prototype.getTime.call(d) / 1000";

   /** out and witness values by the row's id. */
   private static final class Result {
      Result() {
      }

      Result(TableLens t) {
         bind(t);
      }

      void bind(TableLens t) {
         cid = col(t, "id");
         cout = col(t, "out");
         cwit = has(t, "wit") ? col(t, "wit") : -1;
      }

      // a read of an owned var's object on another context was counted
      boolean crossed() {
         return crossReads > 0;
      }

      final double[] out = new double[ROWS + 1];
      final double[] wit = new double[ROWS + 1];
      int cid, cout, cwit = -1;
      long crossReads;
      int homes;
   }

   /**
    * Read the table in {@code mode}; with the pool on, part of it while another thread holds
    * the usual context, so those batches run on another one.
    */
   private Result run(boolean pool, String mode, String f) throws Exception {
      return run(pool, mode, f, true);
   }

   /**
    * {@code mode} "tree X" reads as X with no exclusive home, and with the witness column if
    * {@code witness}.
    */
   private Result run(boolean pool, String mode, String f, boolean witness) throws Exception {
      boolean tree = mode.startsWith("tree ");
      mode = tree ? mode.substring(5) : mode;
      Worksheet ws = ws(f, tree && witness);
      EmbeddedTableAssembly a = (EmbeddedTableAssembly) ws.getAssembly("A");

      if(mode.equals("cf2")) {
         // a post condition on the formula column that keeps every row
         AssetCondition cond = new AssetCondition();
         cond.setOperation(XCondition.GREATER_THAN);
         cond.setType(XSchema.DOUBLE);
         cond.addValue(-1e18);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(a.getColumnSelection(false).getAttribute("out"), cond, 0));
         a.setPostConditionList(list);
      }
      else if(mode.equals("sort")) {
         SortInfo sort = new SortInfo();
         SortRef ref = new SortRef(a.getColumnSelection(false).getAttribute("id"));
         ref.setOrder(XConstants.SORT_DESC);
         sort.addSort(ref);
         a.setSortInfo(sort);
      }

      AssetQuerySandbox box = box(ws, pool, tree);
      WorksheetScriptEnv env = pool ? (WorksheetScriptEnv) box.getScriptEnv() : null;
      Result res = new Result();
      TableLens[] t = new TableLens[1];
      PoolTestSupport.ThrowingRunnable open = () -> {
         t[0] = box.getTableLens("A", RUNTIME);
         res.bind(t[0]);
      };

      switch(mode) {
      case "seq", "cf2" -> {
         open.run();
         read(t[0], res, 1, 1000);
         held(env, () -> read(t[0], res, 1001, 2200));
         read(t[0], res, 2201, ROWS);
      }
      case "pages" -> {
         open.run();
         pages(t[0], res, 1, 1000);
         held(env, () -> pages(t[0], res, 1001, 2200));
         pages(t[0], res, 2201, ROWS);
      }
      case "jump" -> {
         open.run();
         held(env, () -> t[0].moreRows(2500));
         read(t[0], res, 1, ROWS);
      }
      case "eot" -> {
         open.run();
         read(t[0], res, 1, 500);
         held(env, () -> t[0].moreRows(TableLens.EOT));
         read(t[0], res, 1, ROWS);
      }
      case "openHeld" -> {
         held(env, open);

         // a tree table stays on the context it opened on, its home: hand it off, so the
         // reads below run on another context
         if(env != null && tree) {
            PoolTestSupport.handOffIdleHomes(env);
         }

         read(t[0], res, 1, ROWS);
      }
      case "sort" -> {
         // the sort reads the whole formula table in one batch as the table opens, so no
         // batch of it can run on another context: pool on still gives what pool off gives
         held(env, open);
         pages(t[0], res, 1, ROWS);
      }
      default -> fail(mode);
      }

      if(env != null) {
         res.crossReads = PoolTestSupport.metric(env, "CrossReads");
         res.homes = PoolTestSupport.homes(env);
      }

      return res;
   }

   private static void held(WorksheetScriptEnv env, PoolTestSupport.ThrowingRunnable body)
      throws Exception
   {
      if(env == null) {
         body.run();
      }
      else {
         PoolTestSupport.whileHeldElsewhere(env, body);
      }
   }

   private static void read(TableLens t, Result res, int from, int to) {
      for(int r = from; r <= to; r++) {
         assertTrue(t.moreRows(r), "row " + r);
         store(t, res, r);
      }
   }

   private static void pages(TableLens t, Result res, int from, int to) {
      for(int s = from; s <= to; s += 100) {
         int e = Math.min(to, s + 99);
         t.moreRows(e);

         for(int r = s; r <= e; r++) {
            store(t, res, r);
         }
      }
   }

   private static void store(TableLens t, Result res, int r) {
      int id = (int) num(t.getObject(r, res.cid));
      res.out[id] = num(t.getObject(r, res.cout));
      res.wit[id] = res.cwit < 0 ? id : num(t.getObject(r, res.cwit));
   }

   private AssetQuerySandbox box(Worksheet ws, boolean pool, boolean tree) {
      SreeEnv.setProperty(PoolConfig.ENABLED, Boolean.toString(pool));

      // a tree table: no exclusive home (see the class comment)
      if(tree) {
         SreeEnv.setProperty(MAX_HOMES, "0");
      }
      else {
         SreeEnv.remove(MAX_HOMES);
      }

      // the data keys are content based: without this a run could read another's rows
      AssetDataCache.getCache().clearCache();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      boxes.add(box);
      assertEquals(pool, box.isScriptPoolMode());
      return box;
   }

   private static Worksheet ws(String formula, boolean witness) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "A");
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "value", "id" };

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] { 1, i };
      }

      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(table);
      ColumnSelection columns = table.getColumnSelection(false);

      String[][] cols = witness ? new String[][] { { "out", formula }, { "wit", WITNESS } }
         : new String[][] { { "out", formula } };

      for(String[] c : cols) {
         ExpressionRef exp = new ExpressionRef(null, c[0]);
         exp.setExpression(c[1]);
         ColumnRef column = new ColumnRef(exp);
         column.setDataType(XSchema.DOUBLE);
         columns.addAttribute(column);
      }

      table.setColumnSelection(columns, false);
      return ws;
   }

   private static boolean has(TableLens t, String name) {
      t.moreRows(0);

      for(int c = 0; c < t.getColCount(); c++) {
         if(name.equals(String.valueOf(t.getObject(0, c)))) {
            return true;
         }
      }

      return false;
   }

   private static int col(TableLens t, String name) {
      t.moreRows(0);

      for(int c = 0; c < t.getColCount(); c++) {
         if(name.equals(String.valueOf(t.getObject(0, c)))) {
            return c;
         }
      }

      throw new AssertionError("no column " + name);
   }

   private static double num(Object o) {
      return o instanceof Number ? ((Number) o).doubleValue() : Double.NaN;
   }

   private List<ILoggingEvent> warnings() {
      return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
   }

   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   // more rows than a worksheet query computes up front (about 1800 with the pool on), so
   // later reads run further batches
   private static final int ROWS = 3000;
   private static final String MAX_HOMES = "script.ws.contextPool.maxHomes";
   private static final double HOUR = 3600_000;
   private static final int RUNTIME = AssetQuerySandbox.RUNTIME_MODE;
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}

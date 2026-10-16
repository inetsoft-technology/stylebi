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
package inetsoft.report.lens;

import inetsoft.mv.MVManager;
import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.TableFilter2;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapLostTestSupport.LostTable;
import inetsoft.util.swap.SwapReadInterruptedException;
import inetsoft.util.swap.XIntFragment;
import inetsoft.util.swap.XSwapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static inetsoft.util.swap.SwapLostTestSupport.swapLost;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78100: a script timeout that interrupts a swap read of the base while a distinct, self
 * join or join table is computed inline in the exec leaves the table failing every later read
 * with the interrupted read it keeps. The table then reports itself stopped, so the caches
 * treat it, and every table over it, as not cached, and the next reader computes it again.
 * A lost swap file is still kept as before (bug #77651) and is not a stop. The timeout is
 * real: the base loops on a swap read past a 1 s timeout, once.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, SwapInterruptStoppedLensTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SwapInterruptStoppedLensTest {
   @Configuration
   static class TestConfig {
      // a TableFilter2 checks its table against its materialized view (none here)
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }


   @BeforeEach
   void setUp() throws Exception {
      previousTimeout = ScriptStopTestSupport.setTimeout("1");
      previousForceHash = SreeEnv.getProperty(FORCE_HASH);
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      int[] values = new int[100];

      for(int i = 0; i < values.length; i++) {
         values[i] = 1000 + i;
      }

      fragment = new XIntFragment(values);
   }

   @AfterEach
   void tearDown() throws Exception {
      Thread.interrupted();
      fragment.dispose();
      engine.close();
      ScriptStopTestSupport.setTimeout(previousTimeout);
      memoryState(null);

      if(previousForceHash == null) {
         SreeEnv.remove(FORCE_HASH);
      }
      else {
         SreeEnv.setProperty(FORCE_HASH, previousForceHash);
      }
   }

   @Test
   void hashDistinctInterruptedByTimeoutIsStopped() throws Exception {
      assertStoppedByTimeout(t -> new DistinctTableLens(t, new int[] { 0 }, false), 6);
   }

   @Test
   void sortDistinctInterruptedByTimeoutIsStopped() throws Exception {
      // two columns, not stable: sortDistinct, which keeps the failure instead of throwing it
      assertStoppedByTimeout(t -> new DistinctTableLens(t, new int[] { 0, 1 }, false), 6);
   }

   @Test
   void selfJoinInterruptedByTimeoutIsStopped() throws Exception {
      assertStoppedByTimeout(SwapInterruptStoppedLensTest::selfJoin, 11);
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void joinInterruptedByTimeoutIsStopped(boolean hash) throws Exception {
      // set outside the exec, the delegate is created in it
      SreeEnv.setProperty(FORCE_HASH, Boolean.toString(hash));
      memoryState(hash ? null : XSwapper.LOW_MEM);
      TableLens lens = assertStoppedByTimeout(SwapInterruptStoppedLensTest::join, 21);
      assertEquals(hash ? HashJoinTable.class : MergeJoinTable.class, delegate(lens).getClass());
   }

   @Test
   void lostSwapFileIsNotAStop() throws Exception {
      SwapFileReadException lost = swapLost();
      assertLostIsNotAStop(new DistinctTableLens(lostTable(lost), new int[] { 0 }, false),
                           "baseFailure");
      assertLostIsNotAStop(new DistinctTableLens(lostTable(lost), new int[] { 0, 1 }, false),
                           "baseFailure");
      assertLostIsNotAStop(selfJoin(lostTable(lost)), "swapFailure");

      for(boolean hash : new boolean[] { false, true }) {
         SreeEnv.setProperty(FORCE_HASH, Boolean.toString(hash));
         memoryState(hash ? null : XSwapper.LOW_MEM);
         TableLens join = inline(() -> join(lostTable(lost)));
         assertEquals(hash ? HashJoinTable.class : MergeJoinTable.class, delegate(join).getClass());
         assertLostIsNotAStop(join, null);
      }
   }

   /**
    * Build and read the lens inside an exec that times out during a swap read of its base, and
    * check that the caches drop it.
    */
   private TableLens assertStoppedByTimeout(Function<TableLens, TableLens> build, int control)
      throws Exception
   {
      assertEquals(control, rows(build.apply(new DefaultTableLens(data()))));
      Host host = new Host(build);
      engine.put("host", host);
      Throwable first = null;

      try {
         engine.exec(engine.compile("host.run()"), null, null);
      }
      catch(Throwable ex) {
         first = ex;
      }

      Thread.interrupted();
      assertTrue(host.holdsLock, "computed inline in the exec");
      assertInstanceOf(SwapReadInterruptedException.class, host.failure,
                       "the timeout interrupted the swap read of the base");
      assertNotNull(first, "the exec failed");
      assertTrue(ScriptTimeoutGuard.isStop(first), "the exec's caller gets a stop: " + first);
      // the swap file is intact
      assertEquals(1005, fragment.getSafely(5));

      TableLens lens = host.lens;
      assertTrue(isStopped(lens), "the lens is stopped");
      // the shape a cache holds and hands out, and a table over it
      TableFilter2 cached = new TableFilter2(lens);
      assertTrue(AssetDataCache.isStopped(cached));
      assertTrue(AssetDataCache.isStopped(cached.clone()));
      assertTrue(AssetDataCache.isStopped(
         new TableFilter2(new ColumnMapFilter(lens, new int[] { 1, 0 }))));

      // the instance still fails loudly, never with the rows read before the interrupt
      assertInstanceOf(SwapReadInterruptedException.class,
                       SwapFileReadException.find(assertThrows(Throwable.class, () -> rows(lens))));
      assertInstanceOf(SwapReadInterruptedException.class,
                       SwapFileReadException.find(
                          assertThrows(Throwable.class, () -> rows(cached.clone()))));

      // invalidated, it is computed again (a join from its new delegate) and not stopped
      invalidate(lens);
      assertFalse(isStopped(lens), "the invalidated lens is not stopped");
      assertFalse(AssetDataCache.isStopped(new TableFilter2(lens)));
      assertEquals(control, rows(lens));
      return lens;
   }

   /**
    * A lens whose base swap file is lost fails the read and keeps the plain loss, which is not
    * a stop (bug #77651).
    */
   private void assertLostIsNotAStop(TableLens lens, String field) throws Exception {
      SwapFileReadException read = SwapFileReadException.find(
         assertThrows(Throwable.class, () -> inline(() -> rows(lens))));
      assertEquals(SwapFileReadException.class, read == null ? null : read.getClass());

      Object kept = field == null ? keptByJoin(lens) : get(lens, lens.getClass(), field);
      assertNotNull(kept, "the lens keeps the lost swap file");
      assertEquals(SwapFileReadException.class, kept.getClass(), "kept a plain lost swap file");
      assertFalse(isStopped(lens));
      assertFalse(AssetDataCache.isStopped(new TableFilter2(lens).clone()));
      // still fails
      assertNotNull(SwapFileReadException.find(assertThrows(Throwable.class, () -> rows(lens))));
   }

   private static boolean isStopped(TableLens lens) {
      if(lens instanceof DistinctTableLens distinct) {
         return distinct.isStopped();
      }
      else if(lens instanceof SelfJoinTableLens selfJoin) {
         return selfJoin.isStopped();
      }

      return ((JoinTableLens) lens).isStopped();
   }

   /**
    * Pin the memory state the swapper reports (null to read it again), as a join table is a
    * merge join only while memory is not good.
    */
   private static void memoryState(Integer state) throws Exception {
      XSwapper swapper = XSwapper.getSwapper();

      if(state != null) {
         set(swapper, "cachedState", state);
      }

      // the state is read again 200 ms after this time
      set(swapper, "stateTS", state != null ? Long.MAX_VALUE / 2 : 0L);
   }

   private static void set(Object obj, String name, Object value) throws Exception {
      Field field = obj.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(obj, value);
   }

   private static void invalidate(TableLens lens) {
      if(lens instanceof JoinTableLens join) {
         join.invalidate();
      }
      else {
         ((TableFilter) lens).invalidate();
      }
   }

   private static TableLens selfJoin(TableLens base) {
      SelfJoinTableLens lens = new SelfJoinTableLens(base);
      lens.addJoin(0, SelfJoinTableLens.INNER_JOIN, 1);
      return lens;
   }

   private static TableLens join(TableLens left) {
      return new JoinTableLens(left, new DefaultTableLens(data()), new int[] { 0 },
                               new int[] { 0 });
   }

   private static JoinTable delegate(TableLens join) throws Exception {
      return (JoinTable) get(join, JoinTableLens.class, "delegate");
   }

   private static Object keptByJoin(TableLens join) throws Exception {
      return get(delegate(join), JoinTable.class, "baseFailure");
   }

   private static Object get(Object obj, Class<?> cls, String name) throws Exception {
      Field field = cls.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(obj);
   }

   /**
    * Run on this thread as if it held the script lock, so the lens computes inline.
    */
   private static <T> T inline(java.util.function.Supplier<T> task) {
      JavaScriptEngine.pushHeldScriptLock(new ReentrantLock());

      try {
         return task.get();
      }
      finally {
         JavaScriptEngine.popHeldScriptLock();
      }
   }

   private static int rows(TableLens lens) {
      lens.moreRows(XTable.EOT);

      // read every cell, as a reader of the rows does
      for(int r = 0; r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            lens.getObject(r, c);
         }
      }

      return lens.getRowCount();
   }

   private static LostTable lostTable(SwapFileReadException lost) {
      return new LostTable(data(), 3, lost);
   }

   // 10 rows of v, w, both i % 5
   private static Object[][] data() {
      Object[][] data = new Object[11][];
      data[0] = new Object[] { "v", "w" };

      for(int i = 1; i <= 10; i++) {
         data[i] = new Object[] { i % 5, i % 5 };
      }

      return data;
   }

   public final class Host {
      Host(Function<TableLens, TableLens> build) {
         this.build = build;
      }

      /**
       * Build and read the lens in the exec. The first read of a base row from row 3 on loops
       * on a swap read until the timeout interrupts it.
       */
      public Object run() {
         holdsLock = JavaScriptEngine.holdsScriptLock();
         boolean[] armed = { true };
         TableLens base = new DefaultTableLens(data()) {
            @Override
            public Object getObject(int row, int col) {
               if(row >= 3 && armed[0]) {
                  armed[0] = false;
                  long end = System.currentTimeMillis() + 10000;

                  try {
                     while(System.currentTimeMillis() < end) {
                        fragment.swap();
                        fragment.getSafely(5);
                     }
                  }
                  catch(RuntimeException ex) {
                     failure = ex;
                     throw ex;
                  }
               }

               return super.getObject(row, col);
            }
         };

         lens = build.apply(base);
         return rows(lens);
      }

      private final Function<TableLens, TableLens> build;
      private volatile boolean holdsLock;
      private volatile RuntimeException failure;
      private volatile TableLens lens;
   }

   private static final String FORCE_HASH = "join.table.forceHash";
   private String previousTimeout;
   private String previousForceHash;
   private GraalJavaScriptEngine engine;
   private XIntFragment fragment;
}

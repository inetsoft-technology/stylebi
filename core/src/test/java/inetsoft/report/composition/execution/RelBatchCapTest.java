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

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.SlotClaim;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finding F1's invariant under a concurrent reader (bug #77123, seat mut, mutant 22): a pooled
 * formula lens reads under its lens lock only base rows that were read before it took that
 * lock. A batch predicts its rows before the lock (peekPoolBatch); when another thread's batch
 * runs in between, the batch it actually computes is larger than predicted, and it must stop
 * at the predicted rows (the maxr cap) instead of reading further base rows under the lock,
 * which could deadlock against a thread holding the base's monitor (finding F1).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RelBatchCapTest {
   @Test
   public void batchAfterAConcurrentBatchReadsNoUnloadedBaseRowUnderTheLensLock()
      throws Exception
   {
      // the row the second batch starts at: the first row not computed once a row-by-row warm
      // up and one more sequential batch ran, learned from a lens read the same way, alone
      Chain control = new Chain();
      int start = warmUp(control);
      control.lens.moreRows(start);
      int after = processed(control.lens);
      assertTrue(after > start + 1, "the sequential batch computes more than one row");
      control.env.retire();

      Chain chain = new Chain();
      assertEquals(start, warmUp(chain));
      ExecutorService ex = Executors.newFixedThreadPool(2);

      try {
         // B: a sequential batch from start, parked in its script at row start + 1 while it
         // holds the lens lock
         chain.gate.pauseAt = start + 1;
         Future<Boolean> b = ex.submit(() -> chain.lens.moreRows(start));
         assertTrue(chain.gate.paused.await(30, TimeUnit.SECONDS), "B reached its gate");

         // A: the row right after B's batch. It predicts its batch while B runs (B's rows are
         // not computed yet, so no sequential batch), loads its base rows and waits for the lock
         Future<Boolean> a = ex.submit(() -> chain.lens.moreRows(after));
         long deadline = System.currentTimeMillis() + 30000;

         while(chain.loadedOutside.get() < after && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
         }

         assertTrue(chain.loadedOutside.get() >= after, "A loaded its rows before the lock");
         // B ends its batch; A then finds its row next in sequence and doubles the batch
         chain.gate.resume.countDown();
         assertTrue(b.get(30, TimeUnit.SECONDS));
         assertTrue(a.get(30, TimeUnit.SECONDS));
      }
      finally {
         chain.gate.resume.countDown();
         ex.shutdownNow();
         chain.env.retire();
      }

      assertEquals(Set.of(), chain.unloadedUnderLock,
                   "base rows first read under the lens lock");
      assertEquals(0, SlotClaim.openClaims());
   }

   /**
    * Read rows 1..100 one by one, so the pooled batches have doubled a few times.
    *
    * @return the first row not computed.
    */
   private static int warmUp(Chain chain) throws Exception {
      for(int r = 1; r <= 100; r++) {
         assertTrue(chain.lens.moreRows(r));
      }

      return processed(chain.lens);
   }

   private static int processed(FormulaTableLens lens) throws Exception {
      Method m = FormulaTableLens.class.getDeclaredMethod("getProcessedRowCount");
      m.setAccessible(true);
      return (Integer) m.invoke(lens) + lens.getHeaderRowCount();
   }

   private static boolean lensLocked(FormulaTableLens lens) {
      try {
         Field f = FormulaTableLens.class.getDeclaredField("lock");
         f.setAccessible(true);
         return ((ReentrantLock) f.get(lens)).isHeldByCurrentThread();
      }
      catch(ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }
   }

   /**
    * A pooled formula lens over a base that records the rows first read under the lens lock.
    */
   private static final class Chain {
      Chain() {
         env.put("gate", gate);
         DefaultTableLens base = new DefaultTableLens(PooledBatchClaimTest.table(ROWS)) {
            @Override
            public boolean moreRows(int row) {
               FormulaTableLens owner = lens;

               if(row >= 0 && owner != null) {
                  if(lensLocked(owner)) {
                     if(row > loadedOutside.get()) {
                        unloadedUnderLock.add(row);
                     }
                  }
                  else {
                     loadedOutside.accumulateAndGet(row, Math::max);
                  }
               }

               return super.moreRows(row);
            }
         };
         lens = new FormulaTableLens(base, new String[] {"f"},
                                     new String[] {"gate.at(field['id']); field['value'] + 1"},
                                     env, null);
      }

      final WorksheetScriptEnv env = PoolTestSupport.env();
      final Gate gate = new Gate();
      final AtomicInteger loadedOutside = new AtomicInteger();
      final Set<Integer> unloadedUnderLock = ConcurrentHashMap.newKeySet();
      final FormulaTableLens lens;
   }

   /**
    * A host object a formula calls per row; parks the batch that reaches row pauseAt.
    */
   public static final class Gate {
      public Object at(Object id) throws InterruptedException {
         if(id instanceof Number n && n.intValue() == pauseAt) {
            paused.countDown();
            assertTrue(resume.await(30, TimeUnit.SECONDS));
         }

         return id;
      }

      volatile int pauseAt = -1;
      final CountDownLatch paused = new CountDownLatch(1);
      final CountDownLatch resume = new CountDownLatch(1);
   }

   private static final int ROWS = 5000;
}

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

import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.*;

import static inetsoft.report.composition.execution.PooledBatchClaimTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pre-enable check for the worksheet script context pool (bug #76960): a batch claim is held
 * per thread, so a formula lens read on another thread, or a script that makes another thread
 * read a lens partway through its batch, must still keep a script global for the whole batch
 * and clean it only at the batch's end.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CrossThreadBatchClaimTest {
   /**
    * A lens populated on a worker thread (a join or summary worker) batches exactly as on the
    * reader's own thread: the same batch lengths, the accumulator counting through each.
    */
   @Test
   public void formulaReadOnAWorkerThreadKeepsItsBatch() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      ExecutorService worker = Executors.newSingleThreadExecutor();

      try {
         List<Integer> own = runs(formula(table(1000), env, ACCUMULATOR), 1, 1000);
         FormulaTableLens formula = formula(table(1000), env, ACCUMULATOR);
         List<Integer> runs =
            worker.submit(() -> runs(formula, 1, 1000)).get(60, TimeUnit.SECONDS);

         assertEquals(List.of(11, 21, 41, 81), runs.subList(0, 4));
         assertEquals(own, runs);
      }
      finally {
         worker.shutdownNow();
         env.retire();
      }
   }

   /**
    * The outer formula's script makes another thread read a second lens of the same env and
    * waits for it, as a script reading a table populated by a worker does. The other thread
    * takes its own claim (a different slot, the outer one is held) and cleans only that slot,
    * so the outer accumulator keeps counting across its batch.
    */
   @Test
   public void crossThreadReadInsideABatchKeepsTheOuterBatch() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      ExecutorService worker = Executors.newSingleThreadExecutor();

      try {
         FormulaTableLens inner = formula(table(1000), env, "field['id'] * 2");
         env.put("other", new OtherThreadReader(inner, worker));
         FormulaTableLens outer = formula(
            table(1000), env, "other.read(field['id']); " + ACCUMULATOR);

         List<Integer> runs = runs(outer, 1, 1000);

         assertEquals(List.of(11, 21, 41, 81), runs.subList(0, 4));
         assertEquals(2000.0, ((Number) inner.getObject(1000, 3)).doubleValue(),
                      "the inner lens gets its own values");
      }
      finally {
         worker.shutdownNow();
         env.retire();
      }
   }

   /**
    * A host object whose read runs on another thread and waits for it.
    */
   public static final class OtherThreadReader {
      OtherThreadReader(FormulaTableLens lens, ExecutorService thread) {
         this.lens = lens;
         this.thread = thread;
      }

      public Object read(int row) throws Exception {
         return thread.submit(() -> lens.moreRows(row) ? lens.getObject(row, 3) : null)
            .get(30, TimeUnit.SECONDS);
      }

      private final FormulaTableLens lens;
      private final ExecutorService thread;
   }
}

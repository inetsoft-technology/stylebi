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
package inetsoft.mv;

import inetsoft.mv.MVDef.MVContainer;
import inetsoft.mv.data.MV;
import inetsoft.mv.data.MVBuilder;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77247: the dispatchers of a parallel MV build share one {@code MVDef}, and each
 * {@code MVBuilder} constructor prunes the def columns missing from its data (in the
 * constructor loop and in {@code MVCreatorUtil.expand} for dynamic columns). Builders that
 * run at the same time on one def must remove each missing column exactly once, keep the
 * real columns, and each build its MV over all the real columns.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVBuilderConcurrentRemoveColumnTest {
   /**
    * Plain columns missing from the data are pruned by the MVBuilder constructor loop.
    */
   @Test
   void concurrentBuildersPruneMissingColumnsOnce() throws Exception {
      runConcurrentBuilders(MVBuilderConcurrentRemoveColumnTest::newLoopDef, "miss");
   }

   /**
    * Dynamic columns whose base is missing from the data are pruned by MVCreatorUtil.expand.
    */
   @Test
   void concurrentBuildersPruneMissingDynamicColumnsOnce() throws Exception {
      runConcurrentBuilders(MVBuilderConcurrentRemoveColumnTest::newExpandDef, "rng");
   }

   private static void runConcurrentBuilders(Supplier<MVDef> defs, String removedPrefix)
      throws Exception
   {
      ExecutorService pool = Executors.newFixedThreadPool(THREADS);
      List<String> expectedRemoved = new ArrayList<>();

      for(int i = 0; i < MISSING; i++) {
         expectedRemoved.add(removedPrefix + i);
      }

      long start = System.currentTimeMillis();

      try {
         for(int run = 0; run < RUNS && System.currentTimeMillis() - start < TIME_CAP_MS; run++) {
            MVDef def = defs.get();
            Set<MVColumn> resetColumns =
               Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
            CountDownLatch go = new CountDownLatch(1);
            List<Future<MVBuilder>> futures = new ArrayList<>();

            for(int t = 0; t < THREADS; t++) {
               futures.add(pool.submit(() -> {
                  go.await();
                  return new MVBuilder(newTable(), def, false, null, resetColumns);
               }));
            }

            go.countDown();

            for(Future<MVBuilder> future : futures) {
               MV mv = future.get(30, TimeUnit.SECONDS).getMV();
               assertEquals(2, mv.getDimCount() + mv.getMeasureCount(),
                            "run " + run + ": builder mv columns");
            }

            assertEquals(List.of("dim", "num"), names(def.getColumns()),
                         "run " + run + ": final def columns");
            List<String> removed = names(def.container.rcolumns);
            Collections.sort(removed);
            assertEquals(expectedRemoved, removed, "run " + run + ": removed columns");
         }
      }
      finally {
         pool.shutdownNow();
      }
   }

   private static List<String> names(List<MVColumn> cols) {
      return cols.stream()
         .map(c -> c == null ? "<null>" : c.getColumn().getName())
         .collect(Collectors.toList());
   }

   private static DefaultTableLens newTable() {
      return new DefaultTableLens(new Object[][] {
         { "dim", "num" },
         { "a", 1.0 },
         { "b", 2.0 },
         { "c", 3.0 }
      });
   }

   private static ColumnRef ref(String name, String type) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(type);
      return ref;
   }

   // real columns interleaved with plain columns missing from the data
   private static MVDef newLoopDef() {
      List<MVColumn> columns = new ArrayList<>();
      columns.add(new MVColumn(ref("dim", XSchema.STRING), true));

      for(int i = 0; i < MISSING; i++) {
         columns.add(new MVColumn(ref("miss" + i, XSchema.STRING), true));

         if(i == MISSING / 2) {
            columns.add(new MVColumn(ref("num", XSchema.DOUBLE), false));
         }
      }

      return newDef(columns);
   }

   // real columns interleaved with dynamic columns whose base is missing from the data
   private static MVDef newExpandDef() {
      List<MVColumn> columns = new ArrayList<>();
      columns.add(new MVColumn(ref("dim", XSchema.STRING), true));

      for(int i = 0; i < MISSING; i++) {
         MVColumn base = new MVColumn(ref("base" + i, XSchema.DOUBLE), false);
         columns.add(new RangeMVColumn(base, ref("rng" + i, XSchema.DOUBLE), false));

         if(i == MISSING / 2) {
            columns.add(new MVColumn(ref("num", XSchema.DOUBLE), false));
         }
      }

      return newDef(columns);
   }

   private static MVDef newDef(List<MVColumn> columns) {
      MVDef def = new MVDef();
      def.container = new MVContainer(columns, null);
      return def;
   }

   private static final int THREADS = 8;
   private static final int MISSING = 8;
   private static final int RUNS = 300;
   private static final long TIME_CAP_MS = 10000;
}

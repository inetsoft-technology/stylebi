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
package inetsoft.report.composition.execution.reliability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Many open sandboxes at once (Testing #77123, R4; the status doc's G10 / D7 memory risk):
 * {@code -Drel.sandboxes} (500) sandboxes are built and each runs one 1200-row formula lens,
 * 16 at a time, and all stay open; then the node's pooled contexts and the heap after GC are
 * read, again after the idle eviction has had time to run, and after every sandbox is disposed.
 * Pool off first, as the memory reference, then pool on. Pass (pool on): every result equals the
 * pool-off oracle, no claim is open or leaked, the open but idle sandboxes keep no more contexts
 * after the idle eviction than before (each keeps its primary), disposing them leaves no pooled
 * context, and the heap returns to within 64 MB of where it started. Skipped unless {@code -Drel.long=true}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RelManySandboxesTest {
   @Test
   public void manyOpenSandboxes() throws Exception {
      Assumptions.assumeTrue(Boolean.getBoolean("rel.long"), "needs -Drel.long=true");
      Logger logger = (Logger) LoggerFactory.getLogger("inetsoft");
      Level level = logger.getLevel();
      logger.setLevel(Level.ERROR);

      try {
         List<String> oracle = RelPipeline.run(SCRIPT, Shape.FTL, RelConfig.off(),
                                               ReadPattern.SEQUENTIAL);
         StringBuilder report = new StringBuilder("RelManySandboxesTest (sandboxes=" + N + ")\n");
         Phase off = phase(RelConfig.off(), oracle, report);
         Phase on = phase(RelConfig.on(), oracle, report);
         report.append(String.format(Locale.ROOT, "  heap per open sandbox: off %.3f MB, on %.3f MB%n",
            (off.open - off.base) / N, (on.open - on.base) / N));
         System.out.println(report);

         assertEquals(0, on.mismatches, "pool-on results differ from the oracle");
         assertEquals(0, off.mismatches, "pool-off results differ from the oracle");
         assertEquals(0, on.openClaims, "open claims");
         assertEquals(0, on.leaked, "leaked claims");
         assertTrue(on.slotsOpen > 0, "the pool-on sandboxes had no pooled context");
         // a sandbox keeps its primary context while it is open; the eviction closes the others
         assertTrue(on.slotsIdle <= on.slotsOpen, on.slotsOpen + " -> " + on.slotsIdle);
         assertEquals(0, on.slotsDisposed, "pooled contexts left after dispose");
         assertTrue(on.disposed - on.base < 64, "heap after dispose " + on.disposed +
            " MB vs " + on.base + " MB at the start");
      }
      finally {
         logger.setLevel(level);
      }
   }

   private static Phase phase(RelConfig cfg, List<String> oracle, StringBuilder report)
      throws Exception
   {
      Phase p = new Phase();
      p.base = heap();
      long leaked = PoolMetrics.nodeLeakedClaims();
      List<AssetQuerySandbox> boxes = Collections.synchronizedList(new ArrayList<>());
      ExecutorService executor = Executors.newFixedThreadPool(16);
      long start = System.currentTimeMillis();

      try {
         List<Future<Boolean>> runs = new ArrayList<>();

         for(int i = 0; i < N; i++) {
            runs.add(executor.submit(() -> {
               AssetQuerySandbox box = RelPipeline.sandbox(cfg);
               boxes.add(box);
               return RelPipeline.run(SCRIPT, Shape.FTL, box, ReadPattern.SEQUENTIAL)
                  .equals(oracle);
            }));
         }

         for(Future<Boolean> run : runs) {
            if(!run.get(10, TimeUnit.MINUTES)) {
               p.mismatches++;
            }
         }

         long millis = System.currentTimeMillis() - start;
         p.openClaims = SlotClaim.openClaims();
         p.slotsOpen = PoolMetrics.nodeSlots();
         p.open = heap();
         // the idle eviction runs on its own schedule once a context has been idle idleMillis
         // (60 s by default)
         Thread.sleep(IDLE_WAIT_MILLIS);
         p.slotsIdle = PoolMetrics.nodeSlots();
         p.idle = heap();
         report.append(String.format(Locale.ROOT,
            "  %s: %d runs in %d ms, mismatches %d; heap MB base %.1f, all open %.1f, " +
            "open after %d s idle %.1f; node slots open %d, after idle %d; open claims %d; %s%n",
            cfg, N, millis, p.mismatches, p.base, p.open, IDLE_WAIT_MILLIS / 1000, p.idle,
            p.slotsOpen, p.slotsIdle, p.openClaims, PoolMetrics.nodeSummary()));
      }
      finally {
         executor.shutdownNow();
         boxes.forEach(AssetQuerySandbox::dispose);
      }

      p.slotsDisposed = PoolMetrics.nodeSlots();
      p.disposed = heap();
      p.leaked = PoolMetrics.nodeLeakedClaims() - leaked;
      report.append(String.format(Locale.ROOT,
         "  %s: after dispose heap %.1f MB, node slots %d, leaked claims %d%n", cfg, p.disposed,
         p.slotsDisposed, p.leaked));
      return p;
   }

   private static double heap() throws InterruptedException {
      System.gc();
      Thread.sleep(200);
      System.gc();
      return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1048576.0;
   }

   private static final class Phase {
      double base;
      double open;
      double idle;
      double disposed;
      int slotsOpen;
      int slotsIdle;
      int slotsDisposed;
      int openClaims;
      long leaked;
      int mismatches;
   }

   private static final String SCRIPT = "var acc=(acc||0)+field['value'];acc";
   private static final int N = Integer.getInteger("rel.sandboxes", 500);
   private static final long IDLE_WAIT_MILLIS = Long.getLong("rel.idleWaitMillis", 150_000);
}

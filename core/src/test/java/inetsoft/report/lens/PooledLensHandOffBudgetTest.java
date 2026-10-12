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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.script.TableRowScope;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static inetsoft.report.lens.FormulaTableLensVarTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #77123: a lens-owned object hand-off snapshot stopped by its own time bound's
 * interrupt (the backstop guard past the cloner's own time checks) still loses the value
 * with one warning, and leaves no interrupt on the thread: the fix that keeps a caller's
 * cancel during a snapshot must not take the hand-off's own interrupt for one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
class PooledLensHandOffBudgetTest {
   @BeforeEach
   void capture() {
      Thread.interrupted();
      logger = (Logger) LoggerFactory.getLogger(TableRowScope.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void retire() {
      Thread.interrupted();
      logger.detachAppender(appender);
      SreeEnv.remove(HAND_OFF_MILLIS);

      if(env != null) {
         env.retire();
      }
   }

   /**
    * The var holds a BigInt whose decimal digits take the cloner one uninterruptible step of
    * about 1.6 s, so the cloner's own 1 ms time check (every 2048 entries) never runs, and
    * the hand-off's guard (1 ms + 1 s) interrupts it in the 1500 entries after the BigInt.
    */
   @Test
   void aSnapshotStoppedByItsOwnTimeBoundLosesTheValueWithOneWarningAndNoInterrupt()
      throws Exception
   {
      long bits = calibrate();
      SreeEnv.setProperty(HAND_OFF_MILLIS, "1");
      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      env = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), "var c = c || {b: BigInt(2) ** BigInt(" + bits +
         "), t: Array.from({length: 1500}, function(x, i) { return i; }), n: 0}; c.n++; c.n", "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 20);
      long t0 = System.nanoTime();
      PoolTestSupport.handOffIdleHomes(env);
      long ms = (System.nanoTime() - t0) / 1_000_000;
      System.out.println("CODEC-CANCEL budget hand-off " + ms + " ms, bits " + bits);
      assertFalse(Thread.currentThread().isInterrupted(),
                  "the hand-off's own interrupt was left on the thread");
      assertTrue(ms >= 900, "the hand-off's guard must have stopped it: " + ms + " ms");
      read(t, v, 21, ROWS);
      int restart = 0;

      for(int r = 1; r <= ROWS && restart == 0; r++) {
         if(v[r] != r) {
            restart = r;
         }
      }

      // rows past 20 the first batch read ahead were computed before the hand-off
      assertTrue(restart > 20, "c was lost: it is created again after the hand-off, row " +
         restart);
      assertEquals(1, v[restart], "c is created again at row " + restart);
      List<String> warns = appender.list.stream().filter(e -> e.getLevel() == Level.WARN)
         .map(ILoggingEvent::getFormattedMessage).toList();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      assertTrue(warns.get(0).contains("\"c\" holds a value that took longer than 1 ms"),
                 warns.get(0));
   }

   // the bits of a power of two whose decimal digits take about 1.6 s (1.0 s .. 2.6 s: the
   // guard fires at 1 s and its interrupt gives up 2 s later). The first sample is thrown
   // away: it warms up the context and the host's BigInteger code, and under load it takes
   // several times as long as the warm digits in the pooled context. A size is kept only
   // when two samples of it in a row take 1.3 s .. 2 s; if none settles, the test is
   // aborted, since the hand-off would then show nothing about the guard
   private static long calibrate() {
      try(Context ctx = Context.create("js")) {
         long bits = 1L << 20;
         time(ctx, bits);
         long ms = time(ctx, bits);

         while(ms < 300) {
            bits *= 2;
            ms = time(ctx, bits);
         }

         for(int i = 0; i < 12; i++) {
            if(ms >= 1300 && ms <= 2000) {
               long again = time(ctx, bits);

               if(again >= 1300 && again <= 2000) {
                  return bits;
               }

               // load only adds time, so the faster of the two is the closer one
               ms = Math.min(ms, again);
            }

            bits = (long) (bits * Math.pow(1600.0 / ms, 1 / 1.5));
            ms = time(ctx, bits);
         }

         return Assumptions.abort("the calibration did not settle: " + ms + " ms, bits " +
            bits);
      }
   }

   private static long time(Context ctx, long bits) {
      long t0 = System.nanoTime();
      ctx.eval("js", "String(BigInt(2) ** BigInt(" + bits + ")).length");
      return (System.nanoTime() - t0) / 1_000_000;
   }

   private static void read(TableLens t, double[] v, int from, int to) {
      for(int r = from; r <= to; r++) {
         assertTrue(t.moreRows(r), "row " + r);
         v[r] = num(t.getObject(r, 2));
      }
   }

   private static final String HAND_OFF_MILLIS = "script.ws.contextPool.handOffMillis";
   private static final int ROWS = 200;
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
   private WorksheetScriptEnv env;
}

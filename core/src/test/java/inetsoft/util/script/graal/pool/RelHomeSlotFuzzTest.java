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
package inetsoft.util.script.graal.pool;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.TableRowScope;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.RelCleanFuzzTest.Outcome;
import inetsoft.util.script.graal.pool.RelScriptGenerator.Block;
import inetsoft.util.script.graal.pool.RelScriptGenerator.Category;
import inetsoft.util.script.graal.pool.RelScriptGenerator.Gen;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Clean fuzz around a home (Testing #77123, round 2): a formula table whose owned vars hold an
 * object and an array keeps them on its home context between its batches, while the clean
 * fuzz's polluters and probes run on the same sandbox's env between those batches.
 * <ul>
 *    <li>exclusive (maxHomes default): the other claims skip the idle home, so the polluters
 *    and probes run on another context and never on the home; the table's values are exact and
 *    nothing is handed off except when a retire closes the home. The home's own cleanliness
 *    then rests on paranoia's verify, which (paranoid cases) must have run at the release of
 *    each of the table's batches;</li>
 *    <li>soft (maxHomes 0): the first polluter claim after each table batch takes the home
 *    over after the table handed its values off (exactly one take-over per batch), so that
 *    polluter, its clean and its probe run on the table's context, which the probe proves by
 *    seeing the table's formula vars as cleaned leftovers.</li>
 * </ul>
 * The table's values live host side (the table's scope holds the guest values; see
 * {@code TableRowScope.enrollHome}), never on the global, so the probe must see exactly what a
 * fresh context sees, the only extra entries being the table formula's own declared-and-cleaned
 * vars (the spec §14.1 leftover shape: undefined, non-configurable). No other allowance is made
 * for a home, and paranoia runs its usual verify. Every row the table reads either continues
 * each var's count or restarts it with a warning naming the var (a lost value reads as
 * undefined); a silent wrong count is a failure. Plain objects and arrays survive a hand-off, so
 * losses are asserted 0 in both modes, as are discards the clean fuzz's model does not
 * explain.
 *
 * <p>Seeds: {@code -Drel.fuzz.seed} (base), {@code -Drel.home.chunks} (table batches, 3 seeds
 * each; default 24, {@code -Drel.long=true} 700).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
class RelHomeSlotFuzzTest {
   @BeforeAll
   static void timeouts() throws Exception {
      previousTimeout = SreeEnv.getProperty("script.execution.timeout");
      RelCleanFuzzTest.setTimeout(RelCleanFuzzTest.TIMEOUT_SECONDS);
   }

   @AfterAll
   static void restoreTimeout() throws Exception {
      RelCleanFuzzTest.setTimeout(previousTimeout);
   }

   @BeforeEach
   void setUp() {
      forcedBefore = PoolParanoia.forced;
      logger = (Logger) LoggerFactory.getLogger(TableRowScope.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      SreeEnv.setProperty(PoolConfig.BATCH_ROWS, String.valueOf(BATCH));
      SreeEnv.setProperty(PoolConfig.MAX_BATCH_ROWS, String.valueOf(BATCH));
   }

   @AfterEach
   void tearDown() {
      PoolParanoia.forced = forcedBefore;
      logger.detachAppender(appender);
      SreeEnv.remove(PoolConfig.BATCH_ROWS);
      SreeEnv.remove(PoolConfig.MAX_BATCH_ROWS);
      SreeEnv.remove(PoolConfig.MAX_HOMES);

      for(WorksheetScriptEnv env : envs) {
         env.retire();
      }

      envs.clear();
   }

   @ParameterizedTest(name = "{0} paranoid={1}")
   @CsvSource({ "exclusive, false", "soft, false", "exclusive, true", "soft, true" })
   void homeSlotStaysPristineAndKeepsItsTableValues(String mode, boolean paranoid)
      throws Exception
   {
      boolean soft = mode.equals("soft");

      if(soft) {
         SreeEnv.setProperty(PoolConfig.MAX_HOMES, "0");
      }

      PoolParanoia.forced = paranoid;
      long offset = (soft ? 1_000_000L : 0L) + (paranoid ? 2_000_000L : 0L);
      Result r = fuzzAroundAHome(soft, paranoid, Long.getLong("rel.fuzz.seed", 77123L) + 9_000_000L + offset,
                                 Integer.getInteger("rel.home.chunks", LONG ? 700 : 24));
      System.out.println("[rel-home] " + mode + " paranoid=" + paranoid + ": " + r);

      assertEquals(0, r.violations, () -> String.join("\n", r.reports));
      assertEquals(0, r.silentMismatches, () -> String.join("\n", r.reports));
      assertEquals(0, r.paranoiaViolations, "paranoia accepts every cleaned home");
      assertEquals(0, r.losses, () -> "no value is lost: " + r.warnings);
      assertEquals(0, r.unexplainedDiscards, () -> String.join("\n", r.reports));
      assertEquals(1, r.refCreations, "the reference env's context was never replaced");
      assertTrue(r.homeBatches > 0, "the table ran batches on its home");

      if(paranoid) {
         // the check ran at the release of each table batch, on its home
         assertEquals(0, r.unverifiedBatches, "table batches whose release was not verified");
      }

      if(soft) {
         // the first polluter claim after each batch took the home over after a hand-off,
         // and its probe ran on the table's context
         assertEquals(r.homeBatches, r.takeOvers, "one take-over per table batch");
         assertEquals(0, r.takeOverMisses, () -> String.join("\n", r.reports));
         assertEquals(0, r.homeIdentityMisses, () -> String.join("\n", r.reports));
      }
      else {
         assertEquals(0, r.takeOvers, "an exclusive home is never taken over");
      }
   }

   private Result fuzzAroundAHome(boolean soft, boolean paranoid, long base, int chunks)
      throws Exception
   {
      Result result = new Result();
      AssetQuerySandbox box = poolBox(true);
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      envs.add(w);
      AssetQuerySandbox refBox = poolBox(true);
      WorksheetScriptEnv ref = (WorksheetScriptEnv) refBox.getScriptEnv();
      envs.add(ref);

      // the formula scope is the "worksheet" host var: the reference env gets one too
      refBox.getScope();

      for(WorksheetScriptEnv e : List.of(w, ref)) {
         e.put("zhost", "hv");
         run(e, "1");
      }

      long ref0 = ref.getMetrics().getCreations();
      long paranoia0 = PoolParanoia.violations();
      long verifies0 = PoolParanoia.VERIFIES.get();
      long inconclusive0 = PoolParanoia.inconclusive();
      long takeOvers0 = metric(w, "TakeOvers");
      int rows = chunks * BATCH;
      TableLens t = PostProcessor.formula(base(rows), new String[] { "out" },
                                          new String[] { FORMULA }, w, box.getScope(), null,
                                          "T", null, List.of(Double.class),
                                          new boolean[] { false });
      Set<String> slotLeftovers = new HashSet<>();
      int[] expected = { 1, 1 }; // the next count of hc, ha
      long seed = base;

      for(int chunk = 0; chunk < chunks; chunk++) {
         int warns0 = appender.list.size();
         long cleans0 = w.getMetrics().getCleans();
         long verifies1 = PoolParanoia.VERIFIES.get();

         for(int row = chunk * BATCH + 1; row <= (chunk + 1) * BATCH; row++) {
            assertTrue(t.moreRows(row), "row " + row);
            double v = ((Number) t.getObject(row, 2)).doubleValue();
            int[] got = { (int) (v / 100_000), (int) (v % 100_000) };
            List<String> newWarns = warnings(warns0);

            for(int i = 0; i < 2; i++) {
               String var = i == 0 ? "hc" : "ha";

               if(got[i] == expected[i]) {
                  expected[i]++;
               }
               else if(got[i] == 1 &&
                  newWarns.stream().anyMatch(m -> m.contains("\"" + var + "\"")))
               {
                  // lost with a warning: the var restarted
                  result.losses++;
                  expected[i] = 2;
               }
               else {
                  result.silentMismatches++;
                  result.reports.add("row " + row + " " + var + "=" + got[i] + " expected " +
                                     expected[i] + " (warnings " + newWarns + ")");
                  expected[i] = got[i] + 1;
               }
            }
         }

         // only the table's claims ran while it read the rows: one batch, on its home
         boolean batch = w.getMetrics().getCleans() > cleans0;
         result.homeBatches += batch ? 1 : 0;

         if(batch && paranoid && PoolParanoia.VERIFIES.get() == verifies1) {
            result.unverifiedBatches++;
         }

         if(soft) {
            // the batch ran on the context the next polluter takes over: its formula vars
            // are declared there, and are that context's leftovers until it is closed
            slotLeftovers.addAll(TABLE_VARS);
         }
         result.warnings.addAll(warnings(warns0));

         for(int k = 0; k < SEEDS_PER_CHUNK; k++, seed++) {
            long takeOvers1 = metric(w, "TakeOvers");
            Outcome o = fuzzOne(w, ref, seed, soft, slotLeftovers, result);
            long took = metric(w, "TakeOvers") - takeOvers1;
            boolean first = k == 0 && batch;

            if(soft && took != (first ? 1 : 0)) {
               result.takeOverMisses++;
               result.reports.add("seed " + seed + " (chunk " + chunk + ", seed " + k +
                                  ") took over " + took + " homes");
            }

            // the probe ran on the table's context: it saw the table's vars as leftovers
            if(soft && first && !o.discarded() &&
               !o.observedLeftovers().containsAll(TABLE_VARS))
            {
               result.homeIdentityMisses++;
               result.reports.add("seed " + seed + " (chunk " + chunk + "): the probe after " +
                                  "the take-over did not see " + TABLE_VARS + " as leftovers");
            }
         }
      }

      result.refCreations = ref.getMetrics().getCreations() - ref0 + 1;
      result.paranoiaViolations = PoolParanoia.violations() - paranoia0;
      result.paranoiaVerifies = PoolParanoia.VERIFIES.get() - verifies0;
      result.paranoiaInconclusive = PoolParanoia.inconclusive() - inconclusive0;
      result.takeOvers = metric(w, "TakeOvers") - takeOvers0;
      return result;
   }

   // one polluter and probe of the clean fuzz, judged by its oracle, between two batches
   private static Outcome fuzzOne(WorksheetScriptEnv w, WorksheetScriptEnv ref, long seed,
                                  boolean soft, Set<String> slotLeftovers, Result result)
      throws Exception
   {
      RelScriptGenerator gen = new RelScriptGenerator(seed).avoid(new HashSet<>(slotLeftovers));
      List<Block> blocks = gen.blocks();
      Gen p = RelScriptGenerator.combine(blocks);
      String probe = gen.probe();
      Outcome o = RelCleanFuzzTest.check(w, ref, blocks, p, probe, slotLeftovers);
      result.seeds++;
      result.probedOnHome += soft && o.observedLeftovers().stream()
         .anyMatch(TABLE_VARS::contains) ? 1 : 0;
      result.discards += o.discarded() ? 1 : 0;

      // as in the clean fuzz: a polluter that ran to its end is discarded only as modelled
      if(o.discarded() && !o.expectDiscard() && !o.unexpectedThrow()) {
         result.unexplainedDiscards++;
         result.reports.add("seed " + seed + ": unexplained discard, form " + o.form() +
                            ": " + p.source().replace('\n', ' '));
      }

      if(o.violation() != null) {
         result.violations++;

         if(result.reports.size() < 10) {
            result.reports.add("seed " + seed + ": " + o.violation() + "\n  polluter: " +
                               p.source().replace('\n', ' '));
         }
      }

      if(o.discarded()) {
         slotLeftovers.clear();
      }
      else {
         slotLeftovers.addAll(o.observedLeftovers());
      }

      if(!o.discarded() && blocks.stream().anyMatch(
         b -> b.category() == Category.B7_PROTOTYPE ||
            b.category() == Category.B8_BASELINE_MUTATION))
      {
         // a documented drift stays on its context: retire it (the home hands off first)
         w.retire();
         slotLeftovers.clear();
         run(w, "1");
      }

      return o;
   }

   private List<String> warnings(int from) {
      List<String> list = new ArrayList<>();

      for(ILoggingEvent e : new ArrayList<>(appender.list.subList(from, appender.list.size()))) {
         if(e.getLevel() == Level.WARN) {
            list.add(e.getFormattedMessage());
         }
      }

      return list;
   }

   private static DefaultTableLens base(int rows) {
      Object[][] d = new Object[rows + 1][];
      d[0] = new Object[] { "value", "id" };

      for(int i = 1; i <= rows; i++) {
         d[i] = new Object[] { 1, i };
      }

      return new DefaultTableLens(d);
   }

   static final class Result {
      @Override
      public String toString() {
         return "seeds=" + seeds + " violations=" + violations + " silentMismatches=" +
            silentMismatches + " losses=" + losses + " discards=" + discards +
            " (unexplained " + unexplainedDiscards + ") homeBatches=" + homeBatches +
            " unverifiedBatches=" + unverifiedBatches + " takeOvers=" + takeOvers +
            " takeOverMisses=" + takeOverMisses + " homeIdentityMisses=" + homeIdentityMisses +
            " probedOnHome=" + probedOnHome +
            " paranoiaViolations=" + paranoiaViolations + " verifies=" + paranoiaVerifies +
            " inconclusive=" + paranoiaInconclusive + " refCreations=" + refCreations +
            (warnings.isEmpty() ? "" : " warnings=" + warnings.size() + " first=" +
               warnings.get(0));
      }

      int seeds, violations, silentMismatches, losses, homeBatches, probedOnHome, discards,
         unexplainedDiscards, unverifiedBatches, takeOverMisses, homeIdentityMisses;
      long takeOvers, paranoiaViolations, paranoiaVerifies, paranoiaInconclusive, refCreations;
      final List<String> reports = new ArrayList<>();
      final List<String> warnings = new ArrayList<>();
   }

   // hc counts the rows in an object, ha in an array; the cell is hc * 100000 + ha
   private static final String FORMULA = "var hc = hc || {n: 0}; hc['k' + field['id']] = 1; " +
      "hc.n++; var ha = ha || []; ha.push(field['id']); hc.n * 100000 + ha.length";
   private static final Set<String> TABLE_VARS = Set.of("hc", "ha");
   private static final int BATCH = 40;
   private static final int SEEDS_PER_CHUNK = 3;
   private static final boolean LONG = Boolean.getBoolean("rel.long");
   private static String previousTimeout;
   private final List<WorksheetScriptEnv> envs = new ArrayList<>();
   private Boolean forcedBefore;
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}

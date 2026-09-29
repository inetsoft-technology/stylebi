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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.pool.RelScriptGenerator.Block;
import inetsoft.util.script.graal.pool.RelScriptGenerator.Category;
import inetsoft.util.script.graal.pool.RelScriptGenerator.Gen;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Clean fuzz (bug #77123, reliability task 3): a random polluter runs on a pooled env, then a
 * read-only probe; the probe must see exactly what it sees on a fresh env, except for the
 * documented drifts the polluter's blocks declare (B7 prototype patches, B8 baseline object
 * mutation, and the non-configurable leftovers of spec §14.1, which stay declared holding
 * undefined). A slot the clean must fail is closed, and the probe then runs on a new one.
 *
 * <p>Chained runs reuse one env for many seeds (the primary is cleaned again and again, as in
 * production; a B7/B8 seed retires it); fresh runs build a new env per seed. A violation is
 * reproduced on fresh envs and shrunk block by block before it is reported.
 *
 * <p>Seeds: {@code -Drel.fuzz.seed} (base, default 77123), {@code -Drel.fuzz.seeds},
 * {@code -Drel.fuzz.freshSeeds}. The defaults keep the class near 2 min; {@code -Drel.long=true}
 * runs 200k chained, 20k paranoid, 20k prototype and 5k fresh seeds.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RelCleanFuzzTest {
   @BeforeAll
   static void shortTimeout() throws Exception {
      previousTimeout = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "1");
      refreshTimeout();
   }

   @AfterAll
   static void restoreTimeout() throws Exception {
      SreeEnv.setProperty("script.execution.timeout", previousTimeout);
      refreshTimeout();
   }

   @AfterEach
   void unforce() {
      PoolParanoia.forced = forcedBefore;
   }

   @BeforeEach
   void rememberForced() {
      forcedBefore = PoolParanoia.forced;
   }

   @Test
   void chainedCleanFuzz() throws Exception {
      Stats stats = fuzz(seeds(350, 200_000), true, Set.of());
      stats.print("chained");
      stats.assertClean();
   }

   @Test
   void freshEnvCleanFuzz() throws Exception {
      Stats stats = fuzz(Long.getLong("rel.fuzz.freshSeeds", LONG ? 5_000 : 60), false,
                         Set.of());
      stats.print("fresh");
      stats.assertClean();
   }

   /**
    * With the paranoid check on, no kept slot of any category differs from its baseline.
    */
   @Test
   void chainedCleanFuzzWithParanoia() throws Exception {
      PoolParanoia.forced = true;
      Stats stats = fuzz(seeds(200, 20_000), true, Set.of());
      stats.print("chained+paranoid");
      stats.assertClean();
      assertEquals(0, stats.paranoiaViolations, stats.paranoiaKinds::toString);
   }


   /**
    * FZ1, minimal: a re-parented global gets its baseline prototype back at the clean, so the
    * next claim does not see the names the foreign prototype provided.
    */
   @Test
   void globalPrototypeIsRestoredOrTheSlotClosed() throws Exception {
      WorksheetScriptEnv env = newEnv();
      run(env, "Object.setPrototypeOf(globalThis, {zql: 1}); 1");
      assertEquals("undefined", run(env, "typeof zql"));
      run(env, "Object.setPrototypeOf(globalThis, null); 1");
      assertEquals("function", run(env, "typeof hasOwnProperty"));
      assertEquals(1, env.getMetrics().getCreations(), "a restored prototype keeps the slot");
   }

   /**
    * FZ1: a global that was re-parented and then frozen cannot get its prototype back, so the
    * slot is closed and the next claim runs on a new one.
    */
   @Test
   void frozenGlobalWithAChangedPrototypeClosesTheSlot() throws Exception {
      WorksheetScriptEnv env = newEnv();
      long created = env.getMetrics().getCreations();
      run(env, "Object.setPrototypeOf(globalThis, {zql: 1}); Object.freeze(globalThis); 1");
      assertEquals("undefined", run(env, "typeof zql"));
      assertEquals(true, run(env, "Object.getPrototypeOf(globalThis) === Object.prototype"));
      assertEquals(created + 1, env.getMetrics().getCreations());
   }

   /**
    * FZ1 in the fuzz: other seeds (base + 500000), with the prototype blocks included as in
    * every fuzz here.
    */
   @Test
   void chainedCleanFuzzWithGlobalPrototypeBlocks() throws Exception {
      Stats stats = fuzz(seeds(200, 20_000), true, Set.of(), 500_000L);
      stats.print("chained+FZ1");
      stats.assertClean();
   }

   private static long seeds(long def, long longDef) {
      return Long.getLong("rel.fuzz.seeds", LONG ? longDef : def);
   }

   Stats fuzz(long count, boolean chained, Set<String> excluded) throws Exception {
      return fuzz(count, chained, excluded, 0L);
   }

   Stats fuzz(long count, boolean chained, Set<String> excluded, long offset) throws Exception {
      long base = Long.getLong("rel.fuzz.seed", 77123L) + offset;
      Stats stats = new Stats();
      WorksheetScriptEnv ref = newEnv();
      WorksheetScriptEnv env = chained ? newEnv() : null;
      Set<String> slotLeftovers = new HashSet<>();
      Deque<String> recent = new ArrayDeque<>();
      long start = System.nanoTime();
      int probes0 = PROBES.get(), retries0 = PROBE_RETRIES.get();

      for(long i = 0; i < count; i++) {
         long seed = base + i;
         if(!chained || i % 1000 == 0) {
            env = newEnv();
            slotLeftovers.clear();
         }

         RelScriptGenerator gen = new RelScriptGenerator(seed).exclude(excluded)
            .avoid(new HashSet<>(slotLeftovers));
         List<Block> blocks = gen.blocks();
         Gen p = RelScriptGenerator.combine(blocks);
         String probe = gen.probe();

         long violationsBefore = PoolParanoia.violations();
         Outcome o = check(env, ref, blocks, p, probe, slotLeftovers);
         long paranoid = PoolParanoia.violations() - violationsBefore;
         stats.count(p.category(), o, blocks);

         if(paranoid > 0) {
            stats.paranoiaViolations += paranoid;
            blocks.forEach(b -> stats.paranoiaKinds.merge(b.kind(), 1, Integer::sum));

            if(blocks.stream().noneMatch(b -> RelScriptGenerator.F_PROTO.equals(b.finding()))) {
               stats.paranoiaWithoutPrototype++;
            }
         }

         recent.addLast("seed " + seed + ": " + p.source().replace('\n', ' '));

         if(recent.size() > 5) {
            recent.removeFirst();
         }

         if(o.violation() != null) {
            stats.report(seed, blocks, probe, o, recent, excluded);
         }

         if(o.discarded()) {
            slotLeftovers.clear();
         }
         else {
            // the leftovers the probe saw: whether a throwing script declared its globals
            // depends on where the engine's declaration hoist ran
            slotLeftovers.addAll(o.observedLeftovers());
         }

         // by block, not category: a polluter that should have been discarded but threw
         // early may still have patched a prototype
         if(chained && !o.discarded() && blocks.stream().anyMatch(
            b -> b.category() == Category.B7_PROTOTYPE ||
               b.category() == Category.B8_BASELINE_MUTATION))
         {
            // a documented drift stays on its slot: start the next seed on a new one
            env.retire();
            slotLeftovers.clear();
            // create the next primary now, so the next seed does not count it as a discard
            run(env, "1");
         }
      }

      stats.millis = (System.nanoTime() - start) / 1_000_000L;
      stats.probes = PROBES.get() - probes0;
      stats.probeRetries = PROBE_RETRIES.get() - retries0;
      stats.seeds = count;
      stats.refCreations = ref.getMetrics().getCreations();
      return stats;
   }

   /**
    * Run one polluter and probe and judge the result.
    */
   private static Outcome check(WorksheetScriptEnv env, WorksheetScriptEnv ref, List<Block> blocks,
                                Gen p, String probe, Set<String> slotLeftovers)
      throws Exception
   {
      long c0 = env.getMetrics().getCreations();
      boolean threw = false, unexpectedThrow = false;

      try {
         run(env, p.source());
      }
      catch(Exception ex) {
         threw = true;
         // the generator's own throw or loop ends the script after every other block ran;
         // any other throw stopped it early (a block collided with an earlier seed's
         // leftover, or a B7 patch of Object.prototype.get/value made a later descriptor
         // invalid), so the blocks after it never ran
         Block lastBlock = blocks.get(blocks.size() - 1);
         unexpectedThrow = !String.valueOf(ex.getMessage()).contains("zq partial") &&
            !"loop".equals(lastBlock.kind());
      }

      String r1;
      long t0 = System.nanoTime();

      try {
         r1 = probe(env, probe, "loop".equals(blocks.get(blocks.size() - 1).kind()));
      }
      catch(Exception ex) {
         // the probe is read-only and fast: a throw here is judged, not a test error
         return new Outcome("probe threw after " + (System.nanoTime() - t0) / 1_000_000L +
                            " ms: " + ex.getMessage(), env.getMetrics().getCreations() > c0,
                            false, threw, unexpectedThrow, false, 0, Set.of());
      }

      boolean discarded = env.getMetrics().getCreations() > c0;
      String r2 = probe(ref, probe, false);

      Set<String> leftovers = new HashSet<>(slotLeftovers);
      leftovers.addAll(RelScriptGenerator.leftovers(blocks));
      // the engine declares a script's var/function globals after its body (its declaration
      // hoist), so a script that throws or times out leaves no leftovers
      boolean expectDiscard = !unexpectedThrow &&
         (blocks.stream().anyMatch(b -> b.category() == Category.DISCARDS_SLOT) ||
          blocks.stream().mapToInt(Block::foreign).sum() > PoolConfig.MAX_FOREIGN_DELETES ||
          !threw && leftovers.size() > PoolConfig.defaults().cleanThreshold());
      Set<String> allowed = discarded ? Set.of() : RelScriptGenerator.patched(blocks);
      Map<String, String> m1 = parse(r1), m2 = parse(r2);
      Set<String> bad = new TreeSet<>();
      Set<String> drift = new TreeSet<>();
      int leftoverDiffs = 0;
      Set<String> observed = new HashSet<>();
      Set<String> keys = new TreeSet<>(m1.keySet());
      keys.addAll(m2.keySet());

      for(String k : keys) {
         String v1 = m1.get(k), v2 = m2.get(k);

         if(Objects.equals(v1, v2)) {
            continue;
         }

         if(!discarded && v2 == null && isLeftover(k, v1, leftovers)) {
            leftoverDiffs++;
            observed.add(k.substring(4));
         }
         else if(allowed.contains(k)) {
            drift.add(k);
         }
         else {
            bad.add(k + ": fresh=" + v2 + " pooled=" + v1);
         }
      }

      String violation = null;

      if(!bad.isEmpty()) {
         violation = "probe differs: " + bad;
      }
      else if(expectDiscard && !discarded) {
         violation = "the slot was kept though the clean must fail";
      }

      return new Outcome(violation, discarded, expectDiscard, threw, unexpectedThrow,
                         !drift.isEmpty(), leftoverDiffs, observed);
   }

   /**
    * Run a probe. The probe runs under the 1 s script timeout the loop blocks need, and a
    * cold JVM on a loaded machine can take longer (seen once, 1484 ms, on a run's first
    * seed); it is read-only, so a probe that really ran out its timeout is run again, at most
    * twice. An interrupt that stops a probe early, or any interrupt of the probe right after
    * a loop block, could be the loop's interrupt leaking onto the next exec, so it is never
    * retried: it becomes a violation.
    */
   private static String probe(WorksheetScriptEnv env, String probe, boolean afterLoop)
      throws Exception
   {
      for(int attempt = 0; ; attempt++) {
         PROBES.incrementAndGet();
         long start = System.nanoTime();

         try {
            return String.valueOf(run(env, probe));
         }
         catch(Exception ex) {
            long ms = (System.nanoTime() - start) / 1_000_000L;

            if(afterLoop || attempt >= 2 || ms < 900 ||
               !String.valueOf(ex.getMessage()).contains("interrupted"))
            {
               throw ex;
            }

            PROBE_RETRIES.incrementAndGet();
         }
      }
   }

   // a leftover the clean left on purpose: declared by this slot's polluters, still declared
   // (non-configurable), holding undefined
   private static boolean isLeftover(String key, String pooled, Set<String> leftovers) {
      return pooled != null && key.startsWith("own:") &&
         leftovers.contains(key.substring(4)) && pooled.startsWith("undefined ") &&
         pooled.endsWith(" c0");
   }

   private static Map<String, String> parse(String r) {
      Map<String, String> map = new HashMap<>();

      for(String line : r.split("\n")) {
         int eq = line.indexOf('=');
         map.put(eq < 0 ? line : line.substring(0, eq), eq < 0 ? "" : line.substring(eq + 1));
      }

      return map;
   }

   /**
    * Whether these blocks still violate on fresh envs (the polluter's own claim first).
    */
   private static String violationOnFresh(List<Block> blocks, String probe) throws Exception {
      WorksheetScriptEnv env = newEnv();
      WorksheetScriptEnv ref = newEnv();

      try {
         return check(env, ref, blocks, RelScriptGenerator.combine(blocks), probe,
                      new HashSet<>()).violation();
      }
      finally {
         env.retire();
         ref.retire();
      }
   }

   static List<Block> shrink(List<Block> blocks, String probe) throws Exception {
      List<Block> current = new ArrayList<>(blocks);
      boolean progress = true;

      while(progress && current.size() > 1) {
         progress = false;

         for(int i = 0; i < current.size(); i++) {
            List<Block> fewer = new ArrayList<>(current);
            fewer.remove(i);

            if(violationOnFresh(fewer, probe) != null) {
               current = fewer;
               progress = true;
               break;
            }
         }
      }

      return current;
   }

   static WorksheetScriptEnv newEnv() throws Exception {
      WorksheetScriptEnv env = env();
      env.put("zhost", "hv");
      run(env, "1");
      return env;
   }

   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   record Outcome(String violation, boolean discarded, boolean expectDiscard, boolean threw,
                  boolean unexpectedThrow, boolean drift, int leftoverDiffs,
                  Set<String> observedLeftovers)
   {
   }

   static final class Stats {
      void count(Category category, Outcome o, List<Block> blocks) {
         byCategory.merge(category, 1, Integer::sum);
         discards += o.discarded() ? 1 : 0;
         expectedDiscards += o.expectDiscard() ? 1 : 0;
         threw += o.threw() ? 1 : 0;
         unexpectedThrows += o.unexpectedThrow() ? 1 : 0;
         driftSeen += o.drift() ? 1 : 0;
         leftoverDiffs += o.leftoverDiffs();

         if(o.discarded() && !o.expectDiscard()) {
            blocks.forEach(b -> unexpectedDiscardKinds.merge(b.kind().split(" ")[0], 1,
                                                             Integer::sum));
            unexpectedDiscards++;
         }

         blocks.forEach(b -> kinds.merge(b.kind().split(" ")[0], 1, Integer::sum));
      }

      void report(long seed, List<Block> blocks, String probe, Outcome o, Deque<String> recent,
                  Set<String> excluded) throws Exception
      {
         violations++;

         if(reports.size() >= 10) {
            return;
         }

         StringBuilder sb = new StringBuilder("seed ").append(seed).append(": ")
            .append(o.violation());
         String fresh = violationOnFresh(blocks, probe);

         if(fresh == null) {
            sb.append("\n  not reproduced on a fresh env; last seeds on the slot:\n    ")
               .append(String.join("\n    ", recent));
         }
         else {
            List<Block> min = shrink(blocks, probe);
            sb.append("\n  minimal polluter (fresh env): ")
               .append(RelScriptGenerator.combine(min).source().replace('\n', ' '))
               .append("\n  -> ").append(violationOnFresh(min, probe));
         }

         reports.add(sb.toString());
         System.out.println("[rel-fuzz] VIOLATION " + sb);
      }

      void print(String mode) {
         System.out.println("[rel-fuzz] " + mode + ": seeds=" + seeds + " ms=" + millis +
            " categories=" + byCategory + " discards=" + discards + " expectedDiscards=" +
            expectedDiscards + " unexpectedDiscards=" + unexpectedDiscards + " " +
            unexpectedDiscardKinds + " threw=" + threw +
            " (unexpected " + unexpectedThrows + ") driftSeen=" + driftSeen +
            " leftoverDiffs=" + leftoverDiffs + " violations=" + violations +
            " paranoiaViolations=" + paranoiaViolations + " (without FZ1 block: " +
            paranoiaWithoutPrototype + ") refCreations=" + refCreations + " probes=" + probes +
            " probeRetries=" + probeRetries +
            "\n[rel-fuzz] " + mode + " block kinds: " + kinds);
      }

      void assertClean() {
         assertEquals(1, refCreations, "the reference env's slot was never replaced");
         assertEquals(0, violations, () -> String.join("\n", reports));
         // a slow probe is rare; many would mean the retry hides something
         assertTrue(probeRetries * 100L <= probes, probeRetries + " of " + probes +
            " probes were retried");
      }

      final Map<Category, Integer> byCategory = new EnumMap<>(Category.class);
      final Map<String, Integer> kinds = new TreeMap<>();
      final Map<String, Integer> unexpectedDiscardKinds = new TreeMap<>();
      final Map<String, Integer> paranoiaKinds = new TreeMap<>();
      final List<String> reports = new ArrayList<>();
      long seeds, millis, refCreations;
      int probes, probeRetries;
      int discards, expectedDiscards, unexpectedDiscards, threw, unexpectedThrows, driftSeen,
         leftoverDiffs,
         violations;
      long paranoiaViolations, paranoiaWithoutPrototype;
   }

   private static final boolean LONG = Boolean.getBoolean("rel.long");
   private static final java.util.concurrent.atomic.AtomicInteger PROBE_RETRIES =
      new java.util.concurrent.atomic.AtomicInteger();
   private static final java.util.concurrent.atomic.AtomicInteger PROBES =
      new java.util.concurrent.atomic.AtomicInteger();
   private static String previousTimeout;
   private Boolean forcedBefore;
}

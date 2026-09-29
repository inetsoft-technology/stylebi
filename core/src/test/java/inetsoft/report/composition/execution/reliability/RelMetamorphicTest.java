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
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.PoolConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Metamorphic / differential reliability harness of the worksheet script context pool
 * (Testing #77123). Every script of the corpus sample and of a synthetic set runs through the
 * real sandbox pipeline in each shape; the oracle is the pool off with a sequential read.
 * <ul>
 * <li>MR1 batch: the pool on with every batchRows x maxBatchRows gives the oracle.</li>
 * <li>MR2 read: the pool on with every read pattern gives the oracle.</li>
 * <li>MR3 on/off: the pool off with every read pattern gives the oracle (a pre-existing
 * product issue if not).</li>
 * <li>MR5 fresh context: the pool on with cleanThreshold=-1 (a new context for every claim)
 * gives what the pool on with defaults gives.</li>
 * </ul>
 * A difference is allowed only when it is a documented drift (B1 object-valued var, implicit
 * global per claim, C6 host-copy display) and the pool is on in the compared run; those are
 * counted and listed.
 *
 * <p>A run of a 1200-row formula lens costs about 0.3-0.45 s, so the default run checks one
 * comparison of each relation per case, rotating over the matrix, for the synthetic set and
 * every 70th corpus script. {@code -Drel.long=true} checks the full matrix over the full
 * corpus, split into case ranges with {@code -Drel.from} / {@code -Drel.to}; runs of one case
 * use {@code -Drel.threads} (4) threads, each run on its own sandbox.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RelMetamorphicTest {
   @BeforeAll
   public static void quiet() {
      // an error-only script logs a warning for each of its rows in each run
      Logger logger = (Logger) LoggerFactory.getLogger("inetsoft");
      savedLevel = logger.getLevel();
      logger.setLevel(Level.ERROR);
   }

   @AfterAll
   public static void summary() {
      ((Logger) LoggerFactory.getLogger("inetsoft")).setLevel(savedLevel);
      StringBuilder str = new StringBuilder("RelMetamorphicTest summary (long=" + LONG + ")\n");
      STATS.forEach((k, v) -> str.append("  ").append(k).append(" = ").append(v).append('\n'));
      DRIFTS.forEach(d -> str.append("  drift: ").append(d).append('\n'));
      System.out.println(str);
   }

   public static Stream<Case> cases() {
      Stream<Case> cases = cases(Integer.getInteger("rel.step", LONG ? 1 : 70));
      // a long run is split into case ranges, each within one Maven call
      int from = Integer.getInteger("rel.from", 0);
      int to = Integer.getInteger("rel.to", Integer.MAX_VALUE);
      return cases.skip(from).limit(Math.max(0, to - from));
   }

   /**
    * The synthetic set and every {@code step}-th corpus script.
    */
   public static Stream<Case> cases(int step) {
      List<Case> list = new ArrayList<>();
      int i = 0;

      for(String[] s : SYNTHETIC) {
         list.add(new Case("syn:" + s[0], s[1], RelCorpus.classify(s[1]), 77123L + i, i++));
      }

      List<RelCorpus.Entry> corpus = RelCorpus.load();

      for(int c = 0; c < corpus.size(); c += step) {
         RelCorpus.Entry e = corpus.get(c);
         list.add(new Case("corpus#" + c + ":" + e.cls(), e.script(), e.cls(), 77123L + c,
                           i++));
      }

      return list.stream();
   }

   @Test
   public void corpusClassification() {
      Map<RelCorpus.Kind, Integer> counts = new EnumMap<>(RelCorpus.Kind.class);
      RelCorpus.load().forEach(e -> counts.merge(e.cls(), 1, Integer::sum));
      System.out.println("corpus classification: " + counts);
      assertEquals(979, RelCorpus.load().size());
      assertEquals("field['name'] + field['value'] + field[-1]['day']", RelCorpus.rename(
         "field['State'] + field['Sum(Sales)'] + field[-1]['Order Date']"));
      assertEquals(Drift.B1_OBJECT_VAR, drift("var list = list || []; list.push(1)"));
      assertEquals(Drift.IMPLICIT_GLOBAL, drift("n = (n || 0) + 1; n"));
      assertEquals(Drift.NONE, drift("var acc = (acc || 0) + 1; acc"));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   public void mr1Batch(Case c) throws Exception {
      List<Cmp> list = new ArrayList<>();

      for(Shape shape : Shape.values()) {
         for(int batch : new int[] { 0, 1, 7, 256 }) {
            for(int max : new int[] { 1, 256, 8192 }) {
               if(max < batch) {
                  continue;
               }

               RelConfig cfg = RelConfig.on()
                  .with(PoolConfig.BATCH_ROWS, String.valueOf(batch))
                  .with(PoolConfig.MAX_BATCH_ROWS, String.valueOf(max));
               list.add(new Cmp(shape, ORACLE, ReadPattern.SEQUENTIAL, cfg,
                                ReadPattern.SEQUENTIAL));
            }
         }
      }

      check("MR1", c, list, true);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   public void mr2Read(Case c) throws Exception {
      List<Cmp> list = new ArrayList<>();

      for(Shape shape : Shape.values()) {
         for(ReadPattern read : reads(shape, c)) {
            list.add(new Cmp(shape, ORACLE, ReadPattern.SEQUENTIAL, RelConfig.on(), read));
         }
      }

      check("MR2", c, list, true);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   public void mr3OffRead(Case c) throws Exception {
      List<Cmp> list = new ArrayList<>();

      for(Shape shape : Shape.values()) {
         for(ReadPattern read : reads(shape, c)) {
            if(read != ReadPattern.SEQUENTIAL) {
               list.add(new Cmp(shape, ORACLE, ReadPattern.SEQUENTIAL, RelConfig.off(), read));
            }
         }
      }

      // the pool off: no drift is allowed
      check("MR3", c, list, false);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   public void mr5FreshContext(Case c) throws Exception {
      RelConfig fresh = RelConfig.on().with(PoolConfig.CLEAN_THRESHOLD, "-1");
      List<Cmp> list = new ArrayList<>();

      for(Shape shape : Shape.values()) {
         for(ReadPattern read : reads(shape, c).subList(0, shape == Shape.CALC_FIELD ? 1 : 2)) {
            list.add(new Cmp(shape, RelConfig.on(), read, fresh, read));
         }
      }

      check("MR5", c, list, true);
   }

   /**
    * The read patterns of a shape: an aggregation visits its groups in its own order, so a
    * calc field has no read pattern.
    */
   static List<ReadPattern> reads(Shape shape, Case c) {
      return shape == Shape.CALC_FIELD ? List.of(ReadPattern.SEQUENTIAL)
         : ReadPattern.all(c.seed());
   }

   /**
    * Run the comparisons of one case, in parallel (each run has its own sandbox), and check
    * each. The default run checks one comparison of the list, rotating with the case index so
    * the sample covers the matrix; the long run checks all of them.
    */
   private static void check(String relation, Case c, List<Cmp> list, boolean poolCompared)
      throws Exception
   {
      List<Cmp> todo = LONG || Boolean.getBoolean("rel.full") ? list
         : List.of(list.get(c.index() % list.size()));
      Map<String, Future<List<String>>> runs = new HashMap<>();

      for(Cmp cmp : todo) {
         if(!cmp.isOracle()) {
            runs.computeIfAbsent(cmp.baseKey(), k -> EXECUTOR.submit(
               () -> run(c.script(), cmp.shape(), cmp.baseCfg(), cmp.baseRead())));
         }

         runs.computeIfAbsent(cmp.key(), k -> EXECUTOR.submit(
            () -> run(c.script(), cmp.shape(), cmp.cfg(), cmp.read())));
      }

      List<String> failures = new ArrayList<>();

      for(Cmp cmp : todo) {
         List<String> expected = cmp.isOracle() ? oracle(c, cmp.shape())
            : runs.get(cmp.baseKey()).get();
         List<String> actual = runs.get(cmp.key()).get();
         String failure = compare(relation, c, cmp.shape(), expected, actual, cmp.cfg(),
                                  cmp.read(), poolCompared);

         if(failure != null) {
            failures.add(failure);
         }
      }

      assertEquals(List.of(), failures);
   }

   /**
    * Compare one run with its expected result: equal, or a difference the script's documented
    * drift allows under the pool.
    *
    * @return {@code null} if allowed, else the failure.
    */
   private static String compare(String relation, Case c, Shape shape, List<String> expected,
                                 List<String> actual, RelConfig cfg, ReadPattern read,
                                 boolean poolCompared)
   {
      count(relation + ".comparisons");
      count("comparisons." + shape);

      if(nondeterministic(c.script())) {
         count(relation + ".nondeterministic");
      }

      expected = comparable(expected, c.script());
      actual = comparable(actual, c.script());

      if(expected.equals(actual)) {
         return null;
      }

      String diff = diff(expected, actual);
      Drift drift = drift(expected, actual, c.script());

      if(poolCompared && cfg.pool() && drift != Drift.NONE) {
         count(relation + ".drift." + drift);
         DRIFTS.add(relation + " " + drift + " " + c.label() + " " + shape + " " + cfg + " " +
                    read + ": " + diff);
         return null;
      }

      // the pool off keeps an implicit global for the whole env (status doc B6, main's
      // behaviour), so it survives invalidate() and the rows a read computes ahead: a
      // pre-existing behaviour, counted apart from the pool's drifts
      if(!cfg.pool() && drift == Drift.IMPLICIT_GLOBAL) {
         count(relation + ".preexisting." + drift);
         DRIFTS.add(relation + " pre-existing " + drift + " " + c.label() + " " + shape + " " +
                    cfg + " " + read + ": " + diff);
         return null;
      }

      count(relation + ".unknown");
      return relation + " " + c.label() + " " + shape + " " + cfg + " " + read + " differs: " +
         diff + "\nscript: " + c.script();
   }

   /**
    * @return whether a script computes a random or clock value.
    */
   static boolean nondeterministic(String script) {
      return NONDETERMINISTIC.matcher(RelCorpus.stripStrings(script)).find();
   }

   /**
    * @return the cells of a run as compared: a script of random or clock values has no
    * oracle value, so only the structure of its cells is compared.
    */
   static List<String> comparable(List<String> cells, String script) {
      return nondeterministic(script)
         ? cells.stream().map(RelMetamorphicTest::cellShape).toList() : cells;
   }

   /**
    * @return the structure of a cell of a random or clock script, whose value and even error
    * (e.g. an error only for some random values) have no oracle: a whole-run error, a CF2
    * cell's row id, else just that a cell is there.
    */
   static String cellShape(String cell) {
      int bar = cell.indexOf('|');
      return cell.startsWith("RUN-") ? cell : bar >= 0 ? cell.substring(0, bar + 1) : "cell";
   }

   /**
    * @return the oracle of a script in a shape: the pool off, read sequentially.
    */
   private static List<String> oracle(Case c, Shape shape) {
      String key = shape + "\u0000" + c.script();
      List<String> oracle = ORACLES.get(key);

      if(oracle == null) {
         oracle = run(c.script(), shape, RelConfig.off(), ReadPattern.SEQUENTIAL);
         ORACLES.put(key, oracle);
         count("oracle." + shape + (oracle.stream().allMatch(v -> v.startsWith("E:") || v.contains("|E:") ||
            v.startsWith("RUN-E:")) ? ".errorOnly" : ".computing"));
      }

      return oracle;
   }

   /**
    * Run one script, a whole-run exception as its result.
    */
   static List<String> run(String script, Shape shape, RelConfig cfg, ReadPattern read) {
      long start = System.nanoTime();

      try {
         return RelPipeline.run(script, shape, cfg, read);
      }
      catch(Exception ex) {
         return List.of("RUN-" + RelPipeline.error(ex));
      }
      finally {
         String key = (cfg.pool() ? "on." : "off.") + shape;
         STATS.computeIfAbsent("millis." + key, k -> new AtomicLong())
            .addAndGet((System.nanoTime() - start) / 1_000_000);
         count("runs." + key);
      }
   }

   static String diff(List<String> expected, List<String> actual) {
      int n = 0;
      int first = -1;

      for(int i = 0; i < Math.max(expected.size(), actual.size()); i++) {
         String a = i < expected.size() ? expected.get(i) : "<none>";
         String b = i < actual.size() ? actual.get(i) : "<none>";

         if(!a.equals(b)) {
            n++;
            first = first < 0 ? i : first;
         }
      }

      return n + " of " + expected.size() + " cells, first at index " + first + ": expected <" +
         (first < expected.size() ? expected.get(first) : "<none>") + "> got <" +
         (first < actual.size() ? actual.get(first) : "<none>") + ">";
   }

   /**
    * The documented drift a difference may be: C6 if every differing cell is a container whose
    * content is equal (a host copy displayed differently), else the drift of the script.
    */
   static Drift drift(List<String> expected, List<String> actual, String script) {
      boolean hostCopy = expected.size() == actual.size();

      for(int i = 0; hostCopy && i < expected.size(); i++) {
         String a = expected.get(i);
         String b = actual.get(i);

         if(!a.equals(b)) {
            int ia = a.indexOf(RelPipeline.CONTENT);
            int ib = b.indexOf(RelPipeline.CONTENT);
            hostCopy = ia >= 0 && ib >= 0 && a.substring(ia).equals(b.substring(ib));
         }
      }

      return hostCopy ? Drift.C6_HOST_COPY : drift(script);
   }

   /**
    * The documented drift a script may show with the pool on (status doc B1 residual, implicit
    * globals per claim), found from its text.
    */
   static Drift drift(String script) {
      String code = RelCorpus.stripStrings(script);

      if(OBJECT_VAR.matcher(code).find()) {
         return Drift.B1_OBJECT_VAR;
      }

      if(!RelCorpus.implicitGlobals(script).isEmpty()) {
         return Drift.IMPLICIT_GLOBAL;
      }

      return Drift.NONE;
   }

   private static void count(String key) {
      STATS.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
   }

   public enum Drift { NONE, B1_OBJECT_VAR, IMPLICIT_GLOBAL, C6_HOST_COPY }

   /**
    * One comparison: the run of cfg and read against the run of baseCfg and baseRead.
    */
   record Cmp(Shape shape, RelConfig baseCfg, ReadPattern baseRead, RelConfig cfg,
              ReadPattern read)
   {
      boolean isOracle() {
         return baseCfg == ORACLE && baseRead == ReadPattern.SEQUENTIAL;
      }

      String baseKey() {
         return shape + " " + baseCfg + " " + baseRead;
      }

      String key() {
         return shape + " " + cfg + " " + read;
      }
   }

   public record Case(String label, String script, RelCorpus.Kind cls, long seed, int index) {
      @Override
      public String toString() {
         return label;
      }
   }

   private static final Pattern NONDETERMINISTIC = Pattern.compile(
      "Math\\s*\\.\\s*random|\\bnow\\s*\\(|\\bnew\\s+Date\\s*\\(\\s*\\)|\\bnew\\s+Date\\b(?!\\s*\\()|" +
      "CALC\\s*\\.\\s*(?:today|now|rand\\w*)|Date\\s*\\.\\s*now|" +
      "\\b(?:today|rand|randbetween)\\s*\\(");
   // a self-referencing var whose value is an object: var x = x || [] / {} / new ... / function
   private static final Pattern OBJECT_VAR = Pattern.compile(
      "\\bvar\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*\\(?\\s*\\1\\s*\\|\\|\\s*(?:\\[|\\{|new\\b|function\\b)");

   /** name, body */
   static final String[][] SYNTHETIC = {
      { "acc", "var acc=(acc||0)+field['value'];acc" },
      { "dateVar", "var d=d||new Date(0); d.setTime(d.getTime()+1000); d.getTime()" },
      { "listVar", "var list = list || []; list.push(field['id']); list.length" },
      { "letConst", "let x = field['value'] * 2; const y = x + 1; y" },
      { "thisAcc", "var n = (n || 0) + 1; typeof this == 'object' ? n : -n" },
      { "thisPlain", "typeof this == 'object' ? field['id'] * 3 : -1" },
      { "multi", "var a = field['id']; var b = a * 2;\nif(b > 100) { b = b - 100; }\nb" },
      { "prevValue", "field[-1]['value']" },
      { "prevSelf", "(field[-1]['f'] == null ? 0 : field[-1]['f']) + field['id']" },
      { "calcFn", "CALC.mathroundup(field['value'], 1)" },
      { "libFn", "dateAdd('d', field['id'], field['day'])" },
      { "isNull", "isNull(field['name']) ? 'N' : field['name'].toUpperCase()" },
      { "throw150", "if(field['id'] % 150 == 0) { throw new Error('boom ' + field['id']); }\nfield['value']" },
      { "implicit", "cnt = (typeof cnt == 'undefined' ? 0 : cnt) + 1; cnt" },
      { "function", "function twice(x) { return x * 2; }\ntwice(field['id'])" },
      { "row", "row * 2 + field['id']" },
      { "parameter", "parameter == null ? 0 : 1" },
      { "string", "field['name'] == null ? '' : field['name'].substring(0, 1) + field['id']" },
      { "date", "field['day'] == null ? null : field['day'].getFullYear() * 100 + field['day'].getMonth()" },
      { "flag", "field['flag'] ? 'Y' : 'N'" },
      { "array", "[field['id'], field['name']]" },
      { "object", "({ a: field['id'], b: field['name'] })" },
      { "json", "JSON.stringify({ a: field['id'], v: field['value'] })" },
      { "primitiveStr", "var s = (s || '') + (field['id'] % 10); s.length > 20 ? s.substring(s.length - 20) : s" },
      { "number", "field['value'] / 3" },
      { "nan", "field['value'] / 0" },
      { "syntaxError", "field['value'] +* 2" },
      { "refError", "noSuchName + 1" },
   };

   private static final boolean LONG = Boolean.getBoolean("rel.long");
   /** the oracle configuration, compared by identity */
   private static final RelConfig ORACLE = RelConfig.off();
   private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
      Integer.getInteger("rel.threads", 4), r -> {
         Thread thread = new Thread(r, "rel-meta");
         thread.setDaemon(true);
         return thread;
      });
   private static final Map<String, List<String>> ORACLES = new ConcurrentHashMap<>();
   private static final Map<String, AtomicLong> STATS = new ConcurrentSkipListMap<>();
   private static final List<String> DRIFTS = Collections.synchronizedList(new ArrayList<>());
   private static Level savedLevel;
}

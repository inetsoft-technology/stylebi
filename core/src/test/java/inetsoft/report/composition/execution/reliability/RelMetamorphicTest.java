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
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
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
 * <li>MR1 batch: the pool on with every batchRows x maxBatchRows gives the oracle (only a
 * formula lens batches; a condition and a calc field compare once, pool on with the
 * defaults).</li>
 * <li>MR2 read: the pool on with every non-sequential read pattern gives the oracle (formula
 * lens shapes only: a condition runs its script once when it is built, a calc field in the
 * aggregation's order).</li>
 * <li>MR3 on/off: the pool off with every non-sequential read pattern gives the oracle (a
 * pre-existing product issue if not).</li>
 * <li>MR5 fresh context: the pool on with cleanThreshold=-1 (a new context for every claim)
 * gives what the pool on with defaults gives.</li>
 * </ul>
 * A difference is allowed only when it is a documented drift (B1 object-valued var, implicit
 * global per claim, C6 host-copy display) of the differing cells, the rest of the run equal,
 * and the pool is on in the compared run; those are counted and listed. Each comparison is
 * also counted by the kind of its oracle (error only, a condition without a value, a
 * row-independent constant, or computing). A random script is compared by the structure of
 * its cells, a clock script exactly with dates by day.
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
      EXECUTOR.shutdownNow();
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
      cases = cases.skip(from).limit(Math.max(0, to - from));
      // or a list of case indexes, e.g. to re-run chosen cases after a harness change
      String only = System.getProperty("rel.cases", "");

      if(!only.isBlank()) {
         Set<Integer> indexes = new HashSet<>();
         Arrays.stream(only.split(",")).forEach(i -> indexes.add(Integer.parseInt(i.trim())));
         cases = cases.filter(c -> indexes.contains(c.index()));
      }

      return cases;
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

   /**
    * A documented drift excuses only differing values of the drift's shape; the rest of the
    * run must be equal.
    */
   @Test
   public void driftRules() {
      String implicit = "n = (n || 0) + 1; n";
      assertEquals(Drift.IMPLICIT_GLOBAL, drift(List.of("Double:1.0", "Double:2.0"),
         List.of("Double:1.0", "Double:5.0"), implicit));
      assertEquals(Drift.IMPLICIT_GLOBAL, drift(List.of("7|Double:2.0", "v:Double:3.0", "true"),
         List.of("7|Double:1.0", "v:Double:1.0", "false"), implicit));
      // an error moved, a row id changed, a row missing or a type changed: not the drift
      assertEquals(Drift.NONE, drift(List.of("Double:1.0", "Double:2.0"),
         List.of("Double:1.0", "E:RuntimeException"), implicit));
      assertEquals(Drift.NONE, drift(List.of("7|Double:2.0"), List.of("8|Double:2.0"), implicit));
      assertEquals(Drift.NONE, drift(List.of("Double:1.0", "Double:2.0"),
         List.of("Double:1.0"), implicit));
      assertEquals(Drift.NONE, drift(List.of("Double:2.0"), List.of("S:2"), implicit));
      assertEquals(Drift.NONE, drift(List.of("Double:2.0"), List.of("null"), implicit));
      assertEquals(Drift.NONE, drift(List.of("RUN-E:X"), List.of("Double:1.0"), implicit));
      // C6: a GraalJS value against a host copy of the same typed content only
      String value = "Value:{a: 1}" + RelPipeline.CONTENT + "{a=N:1}";
      String copy = "M{a=Double:1.0}" + RelPipeline.CONTENT + "{a=N:1}";
      assertEquals(Drift.C6_HOST_COPY, drift(List.of(value), List.of(copy), "({ a: 1 })"));
      assertEquals(Drift.NONE, drift(List.of(value),
         List.of("M{a=S:1}" + RelPipeline.CONTENT + "{a=S:1}"), "({ a: 1 })"));
      assertEquals(Drift.NONE, drift(List.of("M{a=Integer:1}" + RelPipeline.CONTENT + "{a=N:1}"),
         List.of(copy), "({ a: 1 })"));
      // the content keeps each element's type class, only 1 and 1.0 are the same
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("a", 1.0);
      map.put("b", "1");
      map.put("c", true);
      assertEquals("M{a=Double:1.0,b=S:1,c=Boolean:true}" + RelPipeline.CONTENT +
                   "{a=N:1,b=S:1,c=B:true}", RelPipeline.str(map));
   }

   /**
    * Positive control of MR1/MR2: a single-threaded pooled formula lens read sequentially
    * really runs its 1200 rows in several batches, each on a claimed and then cleaned context,
    * and the batch size decides how many.
    */
   @Test
   public void pooledRunsCrossBatches() throws Exception {
      String script = SYNTHETIC[0][1];
      long one = cleans(script, 1, 1);
      long seven = cleans(script, 7, 7);
      long grown = cleans(script, 7, 8192);
      long defaults = cleans(script, 256, 8192);
      System.out.println("cleans per 1200-row pooled run: batchRows=1/max=1 " + one +
                         ", 7/7 " + seven + ", 7/8192 " + grown + ", 256/8192 " + defaults);
      assertTrue(grown > 1, "batchRows=7 ran in one batch: " + grown);
      assertTrue(defaults > 1, "the defaults ran in one batch: " + defaults);
      // a batch is at least the pool-off look-ahead of 10 rows, so 1/1 and 7/7 are alike;
      // a larger maxBatchRows lets the batches grow
      assertTrue(one >= seven && seven > grown, one + " / " + seven + " / " + grown);
      assertTrue(seven >= RelPipeline.ROWS / 17, "7/7 batches: " + seven);
   }

   /**
    * @return the context cleans (one per released claim) of one sequential read of a pooled
    * formula lens.
    */
   private static long cleans(String script, int batch, int max) throws Exception {
      RelConfig cfg = RelConfig.on().with(PoolConfig.BATCH_ROWS, String.valueOf(batch))
         .with(PoolConfig.MAX_BATCH_ROWS, String.valueOf(max));
      AssetQuerySandbox box = RelPipeline.sandbox(cfg);

      try {
         WorksheetScriptEnv env = (WorksheetScriptEnv) box.getScriptEnv();
         long before = env.getMetrics().getCleans();
         List<String> cells = RelPipeline.run(script, Shape.FTL, box, ReadPattern.SEQUENTIAL);
         assertEquals(RelPipeline.ROWS, cells.size());
         return env.getMetrics().getCleans() - before;
      }
      finally {
         box.dispose();
      }
   }

   /**
    * The oracle kind of each case in each shape, for the report's share of error-only
    * comparisons: only with -Drel.oracles=true (e.g. with -Drel.step=1 over the whole corpus),
    * since it runs the four pool-off oracles of each case and compares nothing.
    */
   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   public void oracleKinds(Case c) {
      Assumptions.assumeTrue(Boolean.getBoolean("rel.oracles"));

      for(Shape shape : Shape.values()) {
         OracleKind kind = kind(shape, oracle(c, shape));
         count("case." + shape + "." + kind);

         if(kind != OracleKind.COMPUTING) {
            DRIFTS.add("kind " + kind + " " + shape + " " + c.label());
         }
      }
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   public void mr1Batch(Case c) throws Exception {
      List<Cmp> list = new ArrayList<>();

      for(Shape shape : Shape.values()) {
         // only a formula lens reads the batch sizes: the other shapes compare once, pool on
         // with the defaults against the oracle
         if(!shape.rowScripted()) {
            list.add(new Cmp(shape, ORACLE, ReadPattern.SEQUENTIAL, RelConfig.on(),
                             ReadPattern.SEQUENTIAL));
            continue;
         }

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

      // a sequential read with the defaults (256 / 8192) is MR1's, and only a formula lens
      // runs its script in the read order
      for(Shape shape : Shape.values()) {
         for(ReadPattern read : otherReads(shape, c)) {
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
         for(ReadPattern read : otherReads(shape, c)) {
            list.add(new Cmp(shape, ORACLE, ReadPattern.SEQUENTIAL, RelConfig.off(), read));
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
         List<ReadPattern> reads = shape.rowScripted()
            ? ReadPattern.all(c.seed()).subList(0, 2) : List.of(ReadPattern.SEQUENTIAL);

         for(ReadPattern read : reads) {
            list.add(new Cmp(shape, RelConfig.on(), read, fresh, read));
         }
      }

      check("MR5", c, list, true);
   }

   /**
    * The non-sequential read patterns that change how a shape runs its script: a formula
    * lens runs it in the read order; a condition runs it once when it is built, a calc field
    * in the aggregation's order, so they have none.
    */
   static List<ReadPattern> otherReads(Shape shape, Case c) {
      return shape.rowScripted()
         ? ReadPattern.all(c.seed()).subList(1, ReadPattern.all(c.seed()).size()) : List.of();
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
      count(relation + ".kind." + kind(shape, oracle(c, shape)));

      if(random(c.script())) {
         count(relation + ".random");
      }
      else if(clock(c.script())) {
         count(relation + ".clock");
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
    * @return whether a script computes a random value.
    */
   static boolean random(String script) {
      return RANDOM.matcher(RelCorpus.stripStrings(script)).find();
   }

   /**
    * @return whether a script reads the clock (and computes no random value).
    */
   static boolean clock(String script) {
      return !random(script) && CLOCK.matcher(RelCorpus.stripStrings(script)).find();
   }

   /**
    * @return the cells of a run as compared: a random script has no oracle value, so only the
    * structure of its cells is compared; a clock script is compared exactly except that a
    * date cell is compared by its day (two runs are seconds apart; the corpus clock scripts
    * compute days, months or years, or add a fixed interval to now()).
    */
   static List<String> comparable(List<String> cells, String script) {
      if(random(script)) {
         return cells.stream().map(RelMetamorphicTest::cellShape).toList();
      }

      if(clock(script)) {
         return cells.stream().map(RelMetamorphicTest::dayOf).toList();
      }

      return cells;
   }

   /**
    * @return a cell with each date value (Type:epoch-ms) replaced by its day.
    */
   static String dayOf(String cell) {
      return DATE_CELL.matcher(cell).replaceAll(
         m -> m.group(1) + ":day" + Math.floorDiv(Long.parseLong(m.group(2)), 86_400_000L));
   }

   /**
    * The kind of result an oracle is, to tell comparisons of computed values from
    * comparisons of errors: ERROR when every cell failed; NO_VALUE for a condition whose
    * script gave no value (each group's value is null, or still the expression because the
    * script failed, e.g. it reads field[...] and a condition has no row, so its evaluations
    * are constant); CONSTANT when every non-error cell of a row shape is the same value
    * (the script does not depend on the row); else COMPUTING.
    */
   static OracleKind kind(Shape shape, List<String> oracle) {
      List<String> values = oracle.stream().map(RelMetamorphicTest::unprefixed)
         .filter(v -> !isError(v)).toList();

      if(values.isEmpty()) {
         return OracleKind.ERROR;
      }

      if(shape == Shape.CONDITION) {
         return values.stream().filter(v -> v.startsWith("v:"))
            .allMatch(v -> v.equals("v:null") || v.startsWith("v:ExpressionValue:"))
            ? OracleKind.NO_VALUE : OracleKind.COMPUTING;
      }

      return values.stream().distinct().count() == 1 ? OracleKind.CONSTANT
         : OracleKind.COMPUTING;
   }

   public enum OracleKind { ERROR, NO_VALUE, CONSTANT, COMPUTING }

   /**
    * @return a cell without the row id a condition-filtered cell starts with.
    */
   static String unprefixed(String cell) {
      int bar = cell.indexOf('|');
      return bar > 0 && cell.substring(0, bar).chars().allMatch(Character::isDigit)
         ? cell.substring(bar + 1) : cell;
   }

   /**
    * @return whether a cell (without its row id) is a failure: a failed row or group, or a
    * failed run.
    */
   static boolean isError(String cell) {
      return cell.startsWith("E:") || cell.startsWith("RUN-");
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
         count("oracle." + shape + "." + kind(shape, oracle));
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
    * The documented drift a difference may be, decided cell by cell. Both runs must have the
    * same number of cells, the same failed cells and the same row ids. Then it is C6 if every
    * differing cell is a GraalJS value on one side and a host container of equal typed
    * content on the other (a host copy displayed differently); else the script's drift (B1 /
    * implicit global) if every differing cell is a value of the same type on both sides (only
    * the variable's value differs); else none.
    */
   static Drift drift(List<String> expected, List<String> actual, String script) {
      if(expected.size() != actual.size()) {
         return Drift.NONE;
      }

      boolean hostCopy = true;
      boolean sameTypes = true;

      for(int i = 0; i < expected.size(); i++) {
         String a = expected.get(i);
         String b = actual.get(i);

         if(a.equals(b)) {
            continue;
         }

         String ua = unprefixed(a);
         String ub = unprefixed(b);
         String ida = a.substring(0, a.length() - ua.length());
         String idb = b.substring(0, b.length() - ub.length());

         // a row id, a failed cell or an extra row differs: never a documented drift
         if(!ida.equals(idb) || isError(ua) || isError(ub) || ua.startsWith("extra row") ||
            ub.startsWith("extra row"))
         {
            return Drift.NONE;
         }

         hostCopy = hostCopy && hostCopy(ua, ub);
         sameTypes = sameTypes && type(ua).equals(type(ub));
      }

      if(hostCopy) {
         return Drift.C6_HOST_COPY;
      }

      return sameTypes ? drift(script) : Drift.NONE;
   }

   /**
    * @return whether two cells hold the same typed content, one as a GraalJS value (what the
    * pool off can return) and the other as a host container (the pool's copy).
    */
   static boolean hostCopy(String a, String b) {
      int ia = a.indexOf(RelPipeline.CONTENT);
      int ib = b.indexOf(RelPipeline.CONTENT);

      if(ia < 0 || ib < 0 || !a.substring(ia).equals(b.substring(ib))) {
         return false;
      }

      boolean va = a.startsWith("Value:");
      boolean vb = b.startsWith("Value:");
      return va != vb && HOST.matcher(va ? b : a).lookingAt();
   }

   /**
    * @return the type of a value cell: its prefix (Double, S, Timestamp, ...), "v:" and that
    * of the value for a condition's value, the literal for a boolean or null.
    */
   static String type(String cell) {
      if(cell.startsWith("v:")) {
         return "v:" + type(cell.substring(2));
      }

      int colon = cell.indexOf(':');
      return colon > 0 ? cell.substring(0, colon)
         : cell.equals("true") || cell.equals("false") ? "boolean" : cell;
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

   private static final Pattern RANDOM = Pattern.compile(
      "Math\\s*\\.\\s*random|CALC\\s*\\.\\s*rand\\w*|\\b(?:rand|randbetween)\\s*\\(");
   private static final Pattern CLOCK = Pattern.compile(
      "\\bnow\\s*\\(|\\bnew\\s+Date\\s*\\(\\s*\\)|\\bnew\\s+Date\\b(?!\\s*\\()|" +
      "CALC\\s*\\.\\s*(?:today|now)|Date\\s*\\.\\s*now|\\btoday\\s*\\(");
   /** a date value in a cell string: its class and epoch ms */
   private static final Pattern DATE_CELL = Pattern.compile("\\b(Timestamp|Date|Time):(-?\\d+)");
   /** a host container cell: an array, list or map */
   private static final Pattern HOST = Pattern.compile("A\\[|L\\[|M\\{");
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

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
import inetsoft.mv.MVManager;
import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.*;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.graal.pool.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dashboard shape of the worksheet script context pool (Testing #77123, round 2): several
 * assemblies read ONE data-cached worksheet table A at once, each on its own thread, through a
 * different downstream lens. A's formula columns keep lens-owned vars that hold plain data: an
 * object counter ({@code vo}), an array accumulator ({@code va}) and a Date ({@code vd}), each
 * reading 1, 2, 3, ... down the table; {@code p} is plain row data. The assemblies are mirrors
 * of A (no cache of their own), so their sub-queries of A share the one cached lens
 * ({@code AssetQuery.doGetTableLens} -> {@code AssetDataCache.getOrMarkExecutingOrWait} /
 * {@code setCachedData}, which wraps A's formula lens in one TableFilter2); the test asserts
 * the sharing. The downstream shapes:
 * <ul>
 * <li>{@code plain}: the mirror as is;</li>
 * <li>{@code cache}: a formula column of the mirror's own over A's var ({@code w = vo + 1});</li>
 * <li>{@code agg}: group by grp, sum/max of the vars (SummaryFilter);</li>
 * <li>{@code xtab}: crosstab of sum(vo), a row per g2, a column per grp (CrossTabFilter);</li>
 * <li>{@code distinct}, {@code sort} (id descending);</li>
 * <li>{@code cond}: a JavaScript post condition (ConditionFilter2) and a formula column of the
 * mirror's own ({@code w}): the condition's population runs the mirror's formula batches,
 * which run A's (the pool-regression session's "cond" shape, PR #5955);</li>
 * <li>{@code condOnly}: the same JavaScript post condition without a formula column of the
 * mirror's own, so the condition reads A's lens directly.</li>
 * </ul>
 * Oracle: every table of every round equals the pool-off table, cell by cell. A difference is
 * excused only in {@link Rule#EVIDENCE} runs (the B1 rule of round 2: {@code round2-b1.md}),
 * where A's vars hold plain data, so a loss is documented only as a home another thread held
 * (B1_HOME_BUSY): the round must have logged that WARN for the var, the var's values in A's row
 * order must go on by one or restart at 1 (never an older value), no more restarts than
 * warnings of the var, and every row-preserving table of the round must read the same values
 * of A (they read one lens). Plain data ({@code p}, the base columns, the row count) is never
 * excused. {@link Rule#STRICT} runs excuse nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, RelDashboardTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
public class RelDashboardTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      // a data-cached table checks its MV state
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @BeforeAll
   public static void setUp() {
      Logger logger = (Logger) LoggerFactory.getLogger("inetsoft");
      savedLevel = logger.getLevel();
      logger.setLevel(Level.ERROR);
      OwnedVarWarnings.install();
   }

   @AfterAll
   public static void summary() {
      ((Logger) LoggerFactory.getLogger("inetsoft")).setLevel(savedLevel);
      OwnedVarWarnings.uninstall();
      StringBuilder str = new StringBuilder("RelDashboardTest summary\n");
      STATS.forEach((k, v) -> str.append("  ").append(k).append(" = ").append(v).append('\n'));
      NOTES.forEach(n -> str.append("  ").append(n).append('\n'));
      str.append("  unattributed owned-var warnings = ").append(OwnedVarWarnings.unattributed())
         .append('\n');
      System.out.println(str);
   }

   @AfterEach
   public void clearCache() {
      AssetDataCache.getCache().clearCache();
   }

   /**
    * Each downstream shape but the condition with a formula column of its own, k=4 assemblies
    * of that shape on 4 threads: exact.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "plain", "cache", "agg", "xtab", "distinct", "sort", "condOnly" })
   public void oneShapeMatchesThePoolOff(String kind) throws Exception {
      run(kind + "x4", Collections.nCopies(4, kind), ROUNDS, Rule.STRICT);
   }

   /**
    * The mixed dashboard: one assembly of each shape but {@code cond}, all at once: exact.
    */
   @Test
   public void mixedDashboardMatchesThePoolOff() throws Exception {
      run("mixed", MIXED, ROUNDS, Rule.STRICT);
   }

   /**
    * The condition shape with a formula column of the mirror's own, alone and in the mixed
    * dashboard: exact (the cond-home fix, PR #5955).
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "condx4", "mixedWithCond" })
   public void conditionShapeMatchesThePoolOff(String what) throws Exception {
      run(what, what.equals("condx4") ? Collections.nCopies(COND_K, "cond") : MIXED_WITH_COND,
          Integer.getInteger("rel.condRounds", 12), Rule.STRICT);
   }

   /**
    * The condition shape alone and in the mixed dashboard under the evidence rule: a
    * difference must be A's var, warned in its round as a home in use by another thread,
    * restart-shaped; plain data exact.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "condx4", "mixedWithCond" })
   public void conditionShapeLossIsDocumented(String what) throws Exception {
      run(what + ".evidence",
          what.equals("condx4") ? Collections.nCopies(COND_K, "cond") : MIXED_WITH_COND,
          Integer.getInteger("rel.condRounds", 12), Rule.EVIDENCE);
   }

   /**
    * Long run (-Drel.long=true, -Drel.longRounds=N, default 200): the mixed dashboard with the
    * condition shape under the evidence rule, for the frequency of the loss.
    */
   @Test
   public void longMixedDashboard() throws Exception {
      Assumptions.assumeTrue(Boolean.getBoolean("rel.long"), "long run only (-Drel.long=true)");
      run("long.mixedWithCond", MIXED_WITH_COND, Integer.getInteger("rel.longRounds", 200),
          Rule.EVIDENCE);
      run("long.mixed", MIXED, Integer.getInteger("rel.longRounds", 200), Rule.STRICT);
   }

   /**
    * Read the assemblies of a dashboard k times with the pool off (the truth, one thread),
    * then {@code rounds} times at once with the pool on, one thread per assembly, and compare.
    */
   private static void run(String name, List<String> kinds, int rounds, Rule rule)
      throws Exception
   {
      int k = kinds.size();
      long start = System.nanoTime();
      List<List<List<Object>>> truth = new ArrayList<>();
      AssetQuerySandbox off = RelPipeline.sandbox(RelConfig.off(), dashboard(kinds));

      try {
         AssetDataCache.getCache().clearCache();

         for(int i = 0; i < k; i++) {
            truth.add(drain(off.getTableLens(assembly(i, kinds), AssetQuerySandbox.RUNTIME_MODE)));
         }
      }
      finally {
         off.dispose();
      }

      checkTruth(kinds, truth);
      long leaked = PoolMetrics.nodeLeakedClaims();
      AssetQuerySandbox box = RelPipeline.sandbox(RelConfig.on(), dashboard(kinds));
      ExecutorService threads = Executors.newFixedThreadPool(k);
      List<String> failures = new ArrayList<>();
      int wrongTables = 0;
      int excusedTables = 0;

      try {
         for(int round = 0; round < rounds; round++) {
            AssetDataCache.getCache().clearCache();
            box.resetTableLens();
            CyclicBarrier barrier = new CyclicBarrier(k);
            List<Future<Object[]>> reads = new ArrayList<>();
            List<List<List<Object>>> tables = new ArrayList<>();
            Set<Object> shared = null;
            Map<String, String> lost;
            Map<String, Integer> warns;
            Map<String, Integer> kindsLost;

            try(OwnedVarWarnings.Recording rec = OwnedVarWarnings.recordAllThreads()) {
               for(int i = 0; i < k; i++) {
                  String assembly = assembly(i, kinds);
                  reads.add(threads.submit(() -> {
                     barrier.await(60, TimeUnit.SECONDS);
                     TableLens lens = box.getTableLens(assembly, AssetQuerySandbox.RUNTIME_MODE);
                     return new Object[] { drain(lens), sharedLenses(lens) };
                  }));
               }

               for(Future<Object[]> read : reads) {
                  Object[] result = read.get(180, TimeUnit.SECONDS);
                  @SuppressWarnings("unchecked")
                  List<List<Object>> table = (List<List<Object>>) result[0];
                  tables.add(table);
                  @SuppressWarnings("unchecked")
                  Set<Object> lenses = (Set<Object>) result[1];

                  if(shared == null) {
                     shared = new HashSet<>(lenses);
                  }
                  else {
                     shared.retainAll(lenses);
                  }
               }

               lost = rec.lost();
               warns = rec.counts();
               kindsLost = rec.kinds();
            }

            if(shared == null || shared.isEmpty()) {
               failures.add("round " + round + ": the assemblies did not read one cached lens of A");
            }

            kindsLost.forEach((kind, n) -> add(name + ".warn." + kind, n));
            List<String> roundFailures = new ArrayList<>();
            // A's var values of this round by id, from the first row-preserving table
            Map<String, double[]> aValues = new HashMap<>();

            for(int i = 0; i < k; i++) {
               String kind = kinds.get(i);
               List<String> diffs = compare(kind, truth.get(i), tables.get(i));

               if(diffs.isEmpty()) {
                  continue;
               }

               wrongTables++;
               Set<String> vars = new TreeSet<>();
               List<String> plain = new ArrayList<>();

               for(String diff : diffs) {
                  String var = diff.substring(0, diff.indexOf(' '));

                  if(var.equals("plain")) {
                     plain.add(diff);
                  }
                  else {
                     vars.add(var);
                  }
               }

               String what = "round " + round + " " + assembly(i, kinds) + ": " +
                  diffs.size() + " cells differ (vars " + vars + ", warned " + lost + ")";

               if(rule == Rule.STRICT || !plain.isEmpty()) {
                  roundFailures.add(what + (plain.isEmpty() ? "" : " incl. plain data " +
                     plain.subList(0, Math.min(3, plain.size()))) + ", first " + diffs.get(0));
                  continue;
               }

               List<String> unexcused = new ArrayList<>();

               for(String var : vars) {
                  String kindOfLoss = lost.get(JS_VAR.get(var));

                  if(kindOfLoss == null) {
                     unexcused.add(var + " differs without a warning (finding)");
                  }
                  else if(!OwnedVarWarnings.HOME_IN_USE.equals(kindOfLoss)) {
                     unexcused.add(var + " holds plain data but warned '" + kindOfLoss + "'");
                  }
               }

               if(ROW_PRESERVING.contains(kind)) {
                  for(String var : VARS) {
                     double[] v = varValues(kind, tables.get(i), var);
                     int restarts = restarts(v);

                     if(restarts < 0) {
                        unexcused.add(var + " neither goes on by one nor restarts at 1 at id " +
                                      -restarts + " (a stale or wrong value)");
                     }
                     else if(restarts > warns.getOrDefault(JS_VAR.get(var), 0)) {
                        unexcused.add(var + " restarts " + restarts + " times with " +
                                      warns.getOrDefault(JS_VAR.get(var), 0) + " warnings");
                     }

                     double[] other = aValues.putIfAbsent(var, v);

                     if(other != null && !Arrays.equals(other, v)) {
                        unexcused.add(var + " reads other values of A than another table " +
                                      "of the round");
                     }
                  }
               }

               if(unexcused.isEmpty()) {
                  excusedTables++;
                  NOTES.add(name + " B1_HOME_BUSY " + what);
               }
               else {
                  roundFailures.add(what + ": " + unexcused);
               }
            }

            failures.addAll(roundFailures);
         }

         PoolMetrics metrics = ((WorksheetScriptEnv) box.getScriptEnv()).getMetrics();
         add(name + ".handOffs", metrics.getHandOffs());
         add(name + ".pulls", metrics.getPulls());
         add(name + ".takeOvers", metrics.getTakeOvers());
         add(name + ".rebuilds", metrics.getRebuilds());
      }
      finally {
         threads.shutdownNow();
         box.dispose();
      }

      add(name + ".tables", (long) k * rounds);
      add(name + ".wrongTables", wrongTables);
      add(name + ".excusedTables", excusedTables);
      add(name + ".ms", (System.nanoTime() - start) / 1_000_000);
      assertEquals(0, SlotClaim.openClaims(), name + ": a claim was left open");
      assertEquals(leaked, PoolMetrics.nodeLeakedClaims(), name + ": a claim leaked");
      assertTrue(failures.isEmpty(), () -> name + ": " + failures.size() + " of " +
         k * rounds + " tables not as the pool off: " + failures);
   }

   // the pool-off tables are what the dashboard means: every var counts 1..ROWS down A
   private static void checkTruth(List<String> kinds, List<List<List<Object>>> truth) {
      for(int i = 0; i < kinds.size(); i++) {
         String kind = kinds.get(i);
         List<List<Object>> table = truth.get(i);

         if(ROW_PRESERVING.contains(kind)) {
            assertEquals(ROWS + 1, table.size(), kind + ": every row");

            for(String var : VARS) {
               assertEquals(0, restarts(varValues(kind, table, var)), kind + " " + var +
                  " counts 1.." + ROWS + " with the pool off");
            }
         }
         else {
            // agg: a row per grp; xtab: a row per g2, a column per grp
            assertEquals(kind.equals("agg") ? 6 : 4, table.size(),
                         () -> kind + ": a row per group: " + table);
         }
      }
   }

   /**
    * @return the differing cells of a table, each "var r,c expected actual" where var is the
    * A var the column reads, or "plain" (base columns, p, the row count, the header).
    */
   private static List<String> compare(String kind, List<List<Object>> expected,
                                       List<List<Object>> actual)
   {
      List<String> diffs = new ArrayList<>();

      if(expected.size() != actual.size() || !expected.get(0).equals(actual.get(0))) {
         diffs.add("plain rows " + expected.size() + " " + actual.size() + " header " +
                   expected.get(0) + " " + actual.get(0));
         return diffs;
      }

      List<Object> header = expected.get(0);

      for(int r = 1; r < expected.size(); r++) {
         for(int c = 0; c < header.size(); c++) {
            Object e = expected.get(r).get(c);
            Object a = actual.get(r).get(c);

            if(!Objects.equals(e, a)) {
               String var = varOf(kind, String.valueOf(header.get(c)), c);
               diffs.add((var == null ? "plain" : var) + " " + r + "," + c + " " + e + " " + a);
            }
         }
      }

      return diffs;
   }

   /**
    * @return the A var a column of a table reads, or null for plain data.
    */
   static String varOf(String kind, String header, int col) {
      if(kind.equals("xtab")) {
         // the g2 row header, then one sum(vo) column per grp value
         return col == 0 ? null : "vo";
      }

      for(String var : VARS) {
         if(Pattern.compile("\\b" + var + "\\b").matcher(header).find()) {
            return var;
         }
      }

      // the mirror's own formula column over vo
      return Pattern.compile("\\bw\\b").matcher(header).find() ? "vo" : null;
   }

   /**
    * @return the values of an A var of a row-preserving table by id (index 1..ROWS).
    */
   private static double[] varValues(String kind, List<List<Object>> table, String var) {
      List<Object> header = table.get(0);
      int id = header.indexOf("id");
      int col = header.indexOf(var);
      double[] v = new double[ROWS + 1];
      Arrays.fill(v, Double.NaN);

      for(int r = 1; r < table.size(); r++) {
         Object key = table.get(r).get(id);
         Object value = table.get(r).get(col);

         if(key instanceof Number n && n.intValue() >= 1 && n.intValue() <= ROWS) {
            v[n.intValue()] = value instanceof Number x ? x.doubleValue() : Double.NaN;
         }
      }

      return v;
   }

   /**
    * @return how often the values go back to 1 down ids 1..ROWS (each value otherwise the
    * previous one plus 1, the first 1), or -id of the first value that is neither.
    */
   static int restarts(double[] v) {
      int restarts = 0;

      for(int id = 1; id < v.length; id++) {
         if(id > 1 && v[id] == v[id - 1] + 1) {
            continue;
         }

         if(v[id] != 1) {
            return -id;
         }

         restarts += id > 1 ? 1 : 0;
      }

      return restarts;
   }

   @Test
   public void restartShape() {
      assertEquals(0, restarts(new double[] { 0, 1, 2, 3, 4 }));
      assertEquals(1, restarts(new double[] { 0, 1, 2, 1, 2 }));
      assertEquals(2, restarts(new double[] { 0, 1, 1, 2, 1 }));
      assertEquals(-3, restarts(new double[] { 0, 1, 2, 2, 3 }), "a repeated (stale) value");
      assertEquals(-4, restarts(new double[] { 0, 1, 2, 3, 2 }), "an older value");
      assertEquals(-1, restarts(new double[] { 0, Double.NaN, 1 }));
      assertEquals("vo", varOf("agg", "Sum(vo)", 1));
      assertNull(varOf("agg", "amount", 1));
      assertEquals("vo", varOf("cache", "w", 5));
      assertNull(varOf("xtab", "grp", 0));
   }

   /**
    * @return the lenses a table reads that are shared: A's cached lens (the TableFilter2 of
    * the data cache) and A's formula lens under it.
    */
   private static Set<Object> sharedLenses(TableLens lens) {
      Set<Object> lenses = Collections.newSetFromMap(new IdentityHashMap<>());

      for(TableLens t = lens; t != null; ) {
         if(t instanceof FormulaTableLens || t instanceof TableFilter2) {
            lenses.add(t);
         }

         t = t instanceof TableFilter filter ? filter.getTable() : null;
      }

      return lenses;
   }

   // A with the var columns, in the data cache, and a mirror per kind
   static Worksheet dashboard(List<String> kinds) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = new EmbeddedTableAssembly(ws, "A");
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "id", "grp", "g2", "amount" };

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] { i, i % 5, i % 3, i * 7 };
      }

      a.setEmbeddedData(new XEmbeddedTable(new String[] {
         XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(a);
      expression(a, "vo", VO);
      expression(a, "va", VA);
      expression(a, "vd", VD);
      expression(a, "p", P);

      for(int i = 0; i < kinds.size(); i++) {
         String kind = kinds.get(i);
         MirrorTableAssembly b = new MirrorTableAssembly(ws, assembly(i, kinds), a);
         b.setProperty("no_cache", "true");
         ws.addAssembly(b);
         b.update();
         ColumnSelection cols = b.getColumnSelection(false);

         switch(kind) {
         case "plain":
            break;
         case "cache":
            expression(b, "w", W);
            break;
         case "agg": {
            AggregateInfo agg = new AggregateInfo();
            agg.addGroup(new GroupRef(cols.getAttribute("grp")));
            agg.addAggregate(new AggregateRef(cols.getAttribute("vo"), AggregateFormula.SUM));
            agg.addAggregate(new AggregateRef(cols.getAttribute("va"), AggregateFormula.MAX));
            agg.addAggregate(new AggregateRef(cols.getAttribute("vd"), AggregateFormula.SUM));
            agg.addAggregate(new AggregateRef(cols.getAttribute("p"), AggregateFormula.SUM));
            b.setAggregateInfo(agg);
            break;
         }
         case "xtab": {
            AggregateInfo agg = new AggregateInfo();
            agg.addGroup(new GroupRef(cols.getAttribute("grp")));
            agg.addGroup(new GroupRef(cols.getAttribute("g2")));
            agg.addAggregate(new AggregateRef(cols.getAttribute("vo"), AggregateFormula.SUM));
            agg.setCrosstab(true);
            b.setAggregateInfo(agg);
            break;
         }
         case "distinct":
            b.setDistinct(true);
            break;
         case "sort": {
            SortInfo sort = new SortInfo();
            SortRef ref = new SortRef(cols.getAttribute("id"));
            ref.setOrder(XConstants.SORT_DESC);
            sort.addSort(ref);
            b.setSortInfo(sort);
            break;
         }
         case "cond":
            // a JavaScript condition value that keeps every row (ids are positive), and a
            // formula column of the mirror's own over A's object var
            jsCondition(b, "Math.min(0, " + i + ")");
            expression(b, "w", W);
            break;
         case "condOnly":
            jsCondition(b, "Math.min(0, " + i + ")");
            break;
         default:
            throw new IllegalArgumentException(kind);
         }
      }

      return ws;
   }

   private static String assembly(int i, List<String> kinds) {
      return "B" + i + "_" + kinds.get(i);
   }

   private static void expression(TableAssembly table, String name, String formula) {
      ColumnSelection columns = table.getColumnSelection(false);
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(formula);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.DOUBLE);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
   }

   private static void jsCondition(TableAssembly table, String js) {
      ConditionList list = new ConditionList();
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      ExpressionValue value = new ExpressionValue();
      value.setExpression(js);
      value.setType(ExpressionValue.JAVASCRIPT);
      cond.addValue(value);
      list.append(new ConditionItem(table.getColumnSelection(false).getAttribute("id"),
                                    cond, 0));
      table.setPostConditionList(list);
   }

   private static List<List<Object>> drain(TableLens t) {
      List<List<Object>> rows = new ArrayList<>();

      for(int r = 0; t.moreRows(r); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < t.getColCount(); c++) {
            Object o = t.getObject(r, c);
            row.add(o instanceof Number n ? (Object) n.doubleValue() : o);
         }

         rows.add(row);
      }

      return rows;
   }

   private static void add(String key, long n) {
      STATS.merge(key, n, Long::sum);
   }

   enum Rule {
      /** every table exact */
      STRICT,
      /** a plain-data var may differ only with its B1_HOME_BUSY evidence (see the class) */
      EVIDENCE
   }

   static final String VO = "var o = o || {n: 0}; o.n++; o.n";
   static final String VA = "var a = a || []; a.push(field['id']); a.length";
   static final String VD = "var d = d || new Date(0); d.setTime(d.getTime() + 86400000); " +
      "d.getTime() / 86400000";
   static final String P = "field['id'] * 3";
   static final String W = "field['vo'] + 1";
   static final List<String> VARS = List.of("vo", "va", "vd");
   // the top-level var each var column keeps (the name its loss warning gives)
   static final Map<String, String> JS_VAR = Map.of("vo", "o", "va", "a", "vd", "d");
   static final Set<String> ROW_PRESERVING =
      Set.of("plain", "cache", "distinct", "sort", "cond", "condOnly");
   static final List<String> MIXED =
      List.of("plain", "cache", "agg", "xtab", "distinct", "sort", "condOnly");
   static final List<String> MIXED_WITH_COND =
      List.of("plain", "cache", "agg", "xtab", "distinct", "sort", "condOnly", "cond");
   static final int ROWS = Integer.getInteger("rel.dashRows", 3000);
   static final int ROUNDS = Integer.getInteger("rel.dashRounds", 3);
   // the assemblies of the condition-only dashboard (-Drel.condK)
   static final int COND_K = Integer.getInteger("rel.condK", 4);
   private static final Map<String, Long> STATS = new ConcurrentSkipListMap<>();
   private static final List<String> NOTES = new CopyOnWriteArrayList<>();
   private static Level savedLevel;
}

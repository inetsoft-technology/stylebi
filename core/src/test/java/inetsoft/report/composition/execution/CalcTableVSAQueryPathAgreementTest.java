/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.report.composition.execution;

import inetsoft.mv.MVManager;
import inetsoft.report.*;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.binding.SimpleNamedGroupInfo;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A freehand table runs on one of two paths: the crosstab-optimized path (the layout becomes
 * temp crosstabs, see {@link CalcTableVSAQuery#quickCheck}) or the script path (every
 * formula cell sends the whole table there). The same layout must give the same cells on
 * both. Each scenario is run twice on the same embedded worksheet and layout; the second
 * run adds one constant formula cell in the header row, which forces the script path and
 * must not change any other cell. The quick path is asserted through the temp assemblies
 * that the sandbox is asked to bind.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  CalcTableVSAQueryPathAgreementTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class CalcTableVSAQueryPathAgreementTest {
   @Configuration
   static class TestConfig {
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   @AfterEach
   void tearDown() {
      if(box != null) {
         box.dispose();
         box = null;
      }
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("scenarios")
   void quickPathAndScriptPathGiveTheSameCells(Scenario sc) throws Exception {
      if(sc.knownQuickFailure != null) {
         // the quick path throws today; this turns red when it works again
         Exception e = assertThrows(Exception.class, () -> run(sc, false),
                                    sc.name + ": the quick path works now, remove the known " +
                                    "failure: " + sc.knownQuickFailure);
         assertTrue(causes(e).contains("Column not found: CF_Q"),
                    sc.name + ": the quick path fails for another reason: " + causes(e));
         // the script path does not prune: the same layout works there
         assertTrue(run(sc, true).size() > 1, sc.name + ": the script path gives no rows");
         return;
      }

      List<List<Object>> quick = run(sc, false);
      boolean quickRan = boundTables.stream().anyMatch(
         n -> n.startsWith(CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX));
      assertEquals(!sc.fallsBack, quickRan, sc.name + ": quick path ran: " + boundTables);

      List<List<Object>> script = run(sc, true);
      assertTrue(boundTables.stream().noneMatch(
                    n -> n.startsWith(CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX)),
                 sc.name + ": the script path ran a temp crosstab: " + boundTables);

      if(sc.expect != null) {
         sc.expect.accept(quick, "quick path");
         sc.expect.accept(script, "script path");
      }

      List<String> diffs = diff(quick, script, sc.cols, sc.nullIsEmpty);

      if(sc.knownDifference != null) {
         // pinned as the current behavior: this turns red when the difference is gone
         assertFalse(diffs.isEmpty(), sc.name + ": the known difference is gone, remove it: " +
                                      sc.knownDifference);
         assertEquals(sc.knownDiffs, diffs, sc.name + ": the cells that differ changed");
         return;
      }

      assertTrue(diffs.isEmpty(), sc.name + ": quick path vs script path\n  quick : " + quick +
                                  "\n  script: " + script + "\n  " + String.join("\n  ", diffs));
   }

   /**
    * The script path lists a null group value and an empty string as two groups, so each of them
    * must be summed on its own: null -> 1, "" -> 2.
    */
   @Test
   void scriptPathSumsANullGroupAndAnEmptyGroupSeparately() throws Exception {
      Scenario sc = new Scenario("null and empty group", sales(row(null, 1, 1.0),
                                                              row("", 2, 2.0),
                                                              row("a", 3, 4.0)),
                                 2, false, l -> {
         l.setCellBinding(1, 0, group("Region"));
         l.setCellBinding(1, 1, summary("Total", "Sum"));
      });
      List<List<Object>> script = run(sc, true);
      Map<Object, Object> sums = new LinkedHashMap<>();
      script.stream().skip(1).forEach(r -> sums.put(r.get(0), r.get(1)));
      assertEquals(1.0, ((Number) sums.get(null)).doubleValue(), "null group, script path");
      assertEquals(2.0, ((Number) sums.get("")).doubleValue(), "empty group, script path");
   }

   // ---- Bug #82482: the script path read the rows of a group by walking the interval between
   // its first and its last row, which is the whole table when the rows of the groups are
   // interleaved. The same rows in another order must give the same cells.

   /**
    * Region group, a nested Cat group, the Sum of Total per (Region, Cat) and per Region. The
    * rows are the same for any order; the totals are whole numbers so a sum does not depend
    * on the order of the additions.
    */
   private static Scenario interleaveScenario(String name, int rows, int groups, boolean mixed) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "Region", "Cat", "Qty", "Total" };

      for(int i = 0; i < rows; i++) {
         int g = mixed ? i % groups : (int) ((long) i * groups / rows);
         data[i + 1] = nrow("R" + g, "C" + (i % 7), 1, (double) (i % 13));
      }

      return new Scenario(name, nestedTypes(), data, 4, false, l -> {
         l.setCellBinding(1, 0, group("Region"));
         TableCellBinding inner = TableCellBinding.getGroupBinding("Cat");
         inner.setCellName("g2");
         inner.setRowGroup(GROUP_CELL);
         l.setCellBinding(1, 1, inner);
         TableCellBinding s = summary("Total", "Sum");
         s.setRowGroup("g2");
         l.setCellBinding(1, 2, s);
         l.setCellBinding(1, 3, summary("Total", "Sum"));
      });
   }

   @Test
   void interleavedGroupRowsGiveTheSameCellsAsSortedGroupRows() throws Exception {
      // the rows differ between the orders (the totals are assigned by index), so each order
      // is checked against the sums of its own rows
      for(boolean mixed : new boolean[] { false, true }) {
         Scenario sc = interleaveScenario(mixed ? "mixed" : "sorted", 700, 20, mixed);
         assertGroupSums(sc, run(sc, true));
         tearDown();
      }
   }

   // ---- Bug #82489: a manual order value that contains a semicolon was split in two when the
   // manual values option was read, so it matched no group and the group was sorted last.

   /** The values of a manual order that were never a problem and the one that was: a ';'. */
   private static String[] manualOrderValues() {
      return new String[] { "a;b", ";", "a;", ";b", "a;;b", "a,b", "a:b", "a'b", "a\"b", "a\\b",
                            "a(b", "a)b", "a[b", "a]b", "a=b", "a|b", "a%b", "plain" };
   }

   /**
    * A group with a manual order North, value, West: every value keeps its place on both paths,
    * and its own sum (North 5, value 40, West 35).
    */
   @Test
   void manualOrderValueWithSpecialCharactersKeepsItsPlaceOnBothPaths() throws Exception {
      List<String> wrong = new ArrayList<>();

      for(String value : manualOrderValues()) {
         Scenario sc = new Scenario("manual order " + value,
                                    sales(row("North", 1, 5.0), row(value, 2, 40.0),
                                          row("West", 3, 35.0)),
                                    2, false, l -> {
            TableCellBinding g = group("Region");
            OrderInfo order = g.getOrderInfo(true);
            order.setOrder(XConstants.SORT_SPECIFIC);
            order.setManualOrder(new ArrayList<>(List.of("North", value, "West")));
            l.setCellBinding(1, 0, g);
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         });

         for(boolean script : new boolean[] { false, true }) {
            Map<String, Double> groups = groups(run(sc, script));
            List<Object> shown = new ArrayList<>(groups.entrySet());

            if(!List.of("North", value, "West").equals(new ArrayList<>(groups.keySet())) ||
               !List.of(5.0, 40.0, 35.0).equals(new ArrayList<>(groups.values())))
            {
               wrong.add("value=" + value + " script=" + script + " got " + shown);
            }

            tearDown();
         }
      }

      assertTrue(wrong.isEmpty(), "wrong order or sums:\n" + String.join("\n", wrong));
   }

   private static void assertGroupSums(Scenario sc, List<List<Object>> result) {
      Map<String, Double> byRegion = new TreeMap<>();
      Set<String> pairs = new TreeSet<>();

      for(int i = 1; i < sc.data.length; i++) {
         byRegion.merge((String) sc.data[i][0], (Double) sc.data[i][3], Double::sum);
         pairs.add(sc.data[i][0] + "|" + sc.data[i][1]);
      }

      assertEquals(pairs.size() + 1, result.size(), sc.name + ": a row per (Region, Cat)");

      Set<String> seen = new TreeSet<>();
      String region = null;
      double inner = 0;

      for(int i = 1; i < result.size(); i++) {
         List<Object> r = result.get(i);

         if(r.get(0) != null && !"".equals(r.get(0))) {
            region = (String) r.get(0);
         }

         assertNotNull(region, sc.name + ": row " + i);
         seen.add(region);
         assertEquals(byRegion.get(region), ((Number) r.get(3)).doubleValue(), 0.0,
                      sc.name + ": outer sum of " + region + " in row " + i);
         inner += ((Number) r.get(2)).doubleValue();
      }

      assertEquals(byRegion.keySet(), seen, sc.name);
      assertEquals(byRegion.values().stream().mapToDouble(Double::doubleValue).sum(), inner, 0.0,
                   sc.name + ": the inner sums add up to the table total");
   }

   /**
    * Timing probe for Bug #82482, not a test: 100,000 rows in 4,000 interleaved groups used to
    * take about 16 times as long as the same rows sorted. Run it by hand.
    */
   @Disabled("timing probe, run by hand")
   @Test
   void interleavedGroupRowsAreNotMuchSlowerThanSortedGroupRows() throws Exception {
      long[] ms = new long[2];

      for(int i = 0; i < 2; i++) {
         boolean mixed = i == 0;
         long t0 = System.nanoTime();
         run(interleaveScenario(mixed ? "mixed" : "sorted", 100_000, 4_000, mixed), true);
         ms[i] = (System.nanoTime() - t0) / 1_000_000;
         tearDown();
      }

      System.out.println("mixed " + ms[0] + " ms, sorted " + ms[1] + " ms");
      assertTrue(ms[0] < ms[1] * 3 + 500, "mixed " + ms[0] + " ms vs sorted " + ms[1] + " ms");
   }

   /**
    * A column name with a double quote, grouped with Top 2 and Others: each row is the sum of its
    * own group on both paths (East 40, West 35, Others 5), not the total of the table.
    */
   @Test
   void topNWithOthersOnAColumnNameWithADoubleQuoteSumsEachGroup() throws Exception {
      String g = "g Say \"hi\"";
      String v = "v Say \"hi\"";
      Object[][] data = {
         { g, v }, { "East", 10.0 }, { "West", 20.0 }, { "East", 30.0 }, { "North", 5.0 },
         { "West", 15.0 }
      };
      Scenario sc = new Scenario("double quote name, top 2 with Others",
                                 new String[] { XSchema.STRING, XSchema.DOUBLE }, data, 2, false,
                                 l -> {
         TableCellBinding grp = group(g);
         grp.setTopN(topN(2, true));
         l.setCellBinding(1, 0, grp);
         l.setCellBinding(1, 1, summary(v, "Sum"));
      });

      for(boolean script : new boolean[] { false, true }) {
         // the script path labels the rest "Others " with a trailing space, on purpose
         Map<String, Double> groups = new LinkedHashMap<>();
         groups(run(sc, script)).forEach((k, d) -> groups.put(k.trim(), d));
         assertEquals(Map.of("East", 40.0, "West", 35.0, "Others", 5.0), groups,
                      "script=" + script);
      }
   }

   // ---- date interval > 1: always the script path, so no second path to compare with.
   // The expected buckets are hand computed from CalcDateTime.date: a year goes to
   // floor(year / n) * n, a month to floor(month / n) * n inside its year (the same rule as
   // SortOrder, the grouping of tables and worksheets).

   private static Object[][] intervalData() {
      return dated(drow("2019-06-01", 1.0), drow("2020-03-01", 2.0), drow("2021-01-15", 4.0),
                   drow("2021-12-31", 8.0), drow("2022-01-01", 16.0), drow("2023-05-05", 32.0));
   }

   private static Scenario intervalScenario(String name, Object[][] data, int level, int interval,
                                            TopNInfo topn)
   {
      return new Scenario(name, datedTypes(), data, 2, false, l -> {
         TableCellBinding g = dateGroup(level, interval);

         if(topn != null) {
            g.setTopN(topn);
         }

         l.setCellBinding(1, 0, g);
         l.setCellBinding(1, 1, summary("Total", "Sum"));
      }).fallsBack();
   }

   private static String monthKey(Object o) {
      Calendar c = Calendar.getInstance();
      c.setTime((java.util.Date) o);
      return c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1);
   }

   /** The data rows (the header row skipped) as group label -> sum, in the order shown. */
   private static Map<String, Double> groups(List<List<Object>> rows) {
      Map<String, Double> map = new LinkedHashMap<>();

      for(int i = 1; i < rows.size(); i++) {
         Object key = rows.get(i).get(0);
         map.put(key instanceof java.util.Date ? monthKey(key) : String.valueOf(key),
                 ((Number) rows.get(i).get(1)).doubleValue());
      }

      return map;
   }

   @Test
   void yearIntervalTwoGroupsByMultiplesOfTwoAndKeepsTheTotal() throws Exception {
      Map<String, Double> g = groups(run(
         intervalScenario("year every 2", intervalData(), XConstants.YEAR_DATE_GROUP, 2, null),
         false));

      // 2019 -> 2018; 2020 and 2021 -> 2020; 2022 and 2023 -> 2022
      assertEquals(Map.of("2018-1", 1.0, "2020-1", 14.0, "2022-1", 48.0), g);
      assertEquals(63.0, g.values().stream().mapToDouble(Double::doubleValue).sum(), "total");
   }

   @Test
   void yearIntervalThreeUsesMultiplesOfThree() throws Exception {
      Map<String, Double> g = groups(run(
         intervalScenario("year every 3", intervalData(), XConstants.YEAR_DATE_GROUP, 3, null),
         false));

      // floor(2019 / 3) * 3 = 2019; 2020 and 2021 -> 2019; floor(2022 / 3) * 3 = 2022
      assertEquals(Map.of("2019-1", 1.0 + 2.0 + 4.0 + 8.0, "2022-1", 16.0 + 32.0), g);
   }

   @Test
   void monthIntervalFiveRestartsEveryYear() throws Exception {
      Object[][] data = dated(drow("2021-01-15", 1.0), drow("2021-02-20", 2.0),
                              drow("2021-06-10", 4.0), drow("2021-12-31", 8.0),
                              drow("2022-01-01", 16.0));
      Map<String, Double> g = groups(run(
         intervalScenario("month every 5", data, XConstants.MONTH_DATE_GROUP, 5, null), false));

      // Jan, Feb -> January; June -> June; December -> November (floor(11 / 5) * 5 = 10)
      assertEquals(Map.of("2021-1", 3.0, "2021-6", 4.0, "2021-11", 8.0, "2022-1", 16.0), g);
   }

   @Test
   void yearIntervalWithTopNKeepsTheTwoLargestBuckets() throws Exception {
      // Bug #38292: the Top N did not work with a date period of 2
      Map<String, Double> g = groups(run(
         intervalScenario("year every 2, top 2", intervalData(), XConstants.YEAR_DATE_GROUP, 2,
                          topN(2, false)), false));

      assertEquals(Map.of("2020-1", 14.0, "2022-1", 48.0), g);
   }

   @Test
   void yearIntervalWithTopNAndOthersGroupsTheRest() throws Exception {
      Map<String, Double> g = groups(run(
         intervalScenario("year every 2, top 2 with Others", intervalData(),
                          XConstants.YEAR_DATE_GROUP, 2, topN(2, true)), false));

      assertEquals(63.0, g.values().stream().mapToDouble(Double::doubleValue).sum(),
                   "the buckets and Others still add up to the total: " + g);
      assertEquals(3, g.size(), "two buckets and Others: " + g);
      assertEquals(1.0, g.get("Others "), "the smallest bucket is the Others row");
   }

   // ---- week level with an interval: the bucket label is the start of a week group, so every
   // date of the bucket must lie in [label, label + interval weeks). This holds for any
   // week.start, so it does not depend on the setting. Each date has its own power of two as
   // the total, so the sum of a bucket tells which dates it holds.

   @Test
   void weekIntervalTwoPutsEveryDateInsideItsBucketWindow() throws Exception {
      String[] days = { "2021-01-01", "2021-01-03", "2021-01-10", "2021-01-17", "2021-01-24",
                        "2021-12-26", "2021-12-28", "2021-12-31", "2022-01-02", "2022-01-09" };
      Object[][] rows = new Object[days.length][];

      for(int i = 0; i < days.length; i++) {
         rows[i] = drow(days[i], (double) (1L << i));
      }

      List<List<Object>> result = run(
         intervalScenario("week every 2", dated(rows), XConstants.WEEK_DATE_GROUP, 2, null),
         false);
      long seen = 0;
      long window = 14L * 24 * 60 * 60 * 1000;

      for(int r = 1; r < result.size(); r++) {
         long label = ((java.util.Date) result.get(r).get(0)).getTime();
         long sum = ((Number) result.get(r).get(1)).longValue();

         for(int i = 0; i < days.length; i++) {
            if((sum & (1L << i)) != 0) {
               long d = Date.valueOf(days[i]).getTime();
               assertTrue(label <= d && d < label + window,
                          days[i] + " is in the bucket labeled " + new java.util.Date(label) +
                          ", which covers only two weeks from there: " + result);
            }
         }

         assertEquals(0L, seen & sum, "a date is in two buckets: " + result);
         seen |= sum;
      }

      assertEquals((1L << days.length) - 1, seen, "every date is in a bucket: " + result);
   }

   // ---- merged cells and page breaks of the expanded table (RuntimeCalcTableLens.postProcess).
   // Rows 1 to 6 are East/x, East/y, North/z, West/x, West/y, West/z; row 0 is the header.

   private static Scenario mergeScenario(String name, Consumer<TableCellBinding> outer) {
      Object[][] data = nested(nrow("East", "x", 1, 10.0), nrow("East", "y", 1, 20.0),
                               nrow("North", "z", 1, 1.0), nrow("West", "x", 1, 5.0),
                               nrow("West", "y", 1, 6.0), nrow("West", "z", 1, 7.0));
      return new Scenario(name, nestedTypes(), data, 3, false, l -> {
         TableCellBinding g = group("Region");
         outer.accept(g);
         l.setCellBinding(1, 0, g);
         TableCellBinding inner = TableCellBinding.getGroupBinding("Cat");
         inner.setCellName("g2");
         inner.setRowGroup(GROUP_CELL);
         l.setCellBinding(1, 1, inner);
         TableCellBinding s = summary("Total", "Sum");
         s.setRowGroup("g2");
         l.setCellBinding(1, 2, s);
      });
   }

   /** The span of a cell as "widthxheight"; a cell without a span is 1x1. */
   private static String span(TableLens lens, int r, int c) {
      java.awt.Dimension d = lens.getSpan(r, c);
      return d == null ? "1x1" : d.width + "x" + d.height;
   }

   private static boolean breaksAfter(TableLens lens, int r) {
      return (lens.getRowBorder(r, 0) & TableLens.BREAK_BORDER) != 0;
   }

   @Test
   void mergedGroupCellSpansTheRowsOfEachGroupOnBothPaths() throws Exception {
      Scenario sc = mergeScenario("merge within the group", g -> {
         g.setMergeCells(true);
         g.setMergeRowGroup(GROUP_CELL);
      });

      for(boolean script : new boolean[] { false, true }) {
         TableLens lens = openLens(sc, script);
         String path = script ? "script path" : "quick path";

         assertEquals(7, lens.getRowCount(), path);
         assertEquals("East", lens.getObject(1, 0), path);
         assertEquals("1x2", span(lens, 1, 0), path + ", East has two inner rows");
         assertEquals("1x1", span(lens, 3, 0), path + ", North has one inner row");
         assertEquals("1x3", span(lens, 4, 0), path + ", West has three inner rows");
      }
   }

   @Test
   void groupCellWithoutMergeHasNoSpanOnBothPaths() throws Exception {
      Scenario sc = mergeScenario("no merge", g -> { });

      for(boolean script : new boolean[] { false, true }) {
         TableLens lens = openLens(sc, script);

         for(int r = 1; r <= 6; r++) {
            assertEquals("1x1", span(lens, r, 0),
                         (script ? "script path" : "quick path") + ", row " + r);
         }
      }
   }

   @Test
   void mergeWithoutAMergeGroupSpansTheWholeExpansion() throws Exception {
      // a merge group of null merges the entire expanded range into one cell, labeled with
      // the first value (documented behavior of the merge group option)
      Scenario sc = mergeScenario("merge the whole range", g -> {
         g.setMergeCells(true);
         g.setMergeRowGroup(null);
      });

      for(boolean script : new boolean[] { false, true }) {
         TableLens lens = openLens(sc, script);
         assertEquals("1x6", span(lens, 1, 0), script ? "script path" : "quick path");
         assertEquals("East", lens.getObject(1, 0));
      }
   }

   @Test
   void pageAfterBreaksAfterTheLastRowOfEachGroupButNotAfterTheTable() throws Exception {
      Scenario sc = mergeScenario("page after", g -> g.setPageAfter(true));

      for(boolean script : new boolean[] { false, true }) {
         TableLens lens = openLens(sc, script);
         String path = script ? "script path" : "quick path";

         assertFalse(breaksAfter(lens, 1), path + ", East/x is followed by East/y");
         assertTrue(breaksAfter(lens, 2), path + ", the last East row");
         assertTrue(breaksAfter(lens, 3), path + ", the only North row");
         assertFalse(breaksAfter(lens, 4), path + ", West/x is followed by West/y");
         assertFalse(breaksAfter(lens, 5), path + ", West/y is followed by West/z");
         assertFalse(breaksAfter(lens, 6), path + ", the last row of the table");
      }
   }

   // ---- the same for columns (the column code of postProcess is a copy of the row code).
   // Runtime columns 1 to 6 are East/x, East/y, North/z, West/x, West/y, West/z; the outer
   // group is in header row 0, the inner group in header row 1 and the sums in row 2.

   private static Scenario horizontalMergeScenario(String name,
                                                   Consumer<TableCellBinding> outer)
   {
      Object[][] data = nested(nrow("East", "x", 1, 10.0), nrow("East", "y", 1, 20.0),
                               nrow("North", "z", 1, 1.0), nrow("West", "x", 1, 5.0),
                               nrow("West", "y", 1, 6.0), nrow("West", "z", 1, 7.0));
      return new Scenario(name, nestedTypes(), data, 7, false, l -> {
         l.insertRow(0); // two header rows: the outer group above the inner one
         TableCellBinding g = group("Region");
         g.setExpansion(TableCellBinding.EXPAND_H);
         outer.accept(g);
         l.setCellBinding(0, 1, g);
         TableCellBinding inner = TableCellBinding.getGroupBinding("Cat");
         inner.setCellName("g2");
         inner.setExpansion(TableCellBinding.EXPAND_H);
         inner.setColGroup(GROUP_CELL);
         l.setCellBinding(1, 1, inner);
         TableCellBinding s = summaryOfColumns("Total", "Sum");
         s.setColGroup("g2");
         l.setCellBinding(2, 1, s);
      });
   }

   private static void assertHorizontalLayout(TableLens lens, String path) {
      String[] outer = { "East", "East", "North", "West", "West", "West" };
      String[] inner = { "x", "y", "z", "x", "y", "z" };
      double[] sums = { 10, 20, 1, 5, 6, 7 };

      for(int i = 0; i < 6; i++) {
         // a merged cell shows its value in the first column only, so the outer row is not read
         assertEquals(inner[i], lens.getObject(1, i + 1), path + ", inner group, column " + (i + 1));
         assertEquals(sums[i], ((Number) lens.getObject(2, i + 1)).doubleValue(),
                      path + ", sum, column " + (i + 1));
      }

      assertEquals(outer[0], lens.getObject(0, 1), path + ", first outer cell");
   }

   @Test
   void mergedColumnGroupCellSpansTheColumnsOfEachGroupOnBothPaths() throws Exception {
      Scenario sc = horizontalMergeScenario("merge columns within the group", g -> {
         g.setMergeCells(true);
         g.setMergeColGroup(GROUP_CELL);
      });

      for(boolean script : new boolean[] { false, true }) {
         TableLens lens = openLens(sc, script);
         String path = script ? "script path" : "quick path";

         assertHorizontalLayout(lens, path);
         assertEquals("2x1", span(lens, 0, 1), path + ", East has two inner columns");
         assertEquals("1x1", span(lens, 0, 3), path + ", North has one inner column");
         assertEquals("3x1", span(lens, 0, 4), path + ", West has three inner columns");
      }
   }

   @Test
   void columnGroupCellWithoutMergeHasNoSpanOnBothPaths() throws Exception {
      Scenario sc = horizontalMergeScenario("no merge of columns", g -> { });

      for(boolean script : new boolean[] { false, true }) {
         TableLens lens = openLens(sc, script);
         String path = script ? "script path" : "quick path";

         assertHorizontalLayout(lens, path);

         for(int c = 1; c <= 6; c++) {
            assertEquals("1x1", span(lens, 0, c), path + ", column " + c);
         }
      }
   }

   @Test
   void columnMergeWithoutAMergeGroupSpansTheWholeExpansion() throws Exception {
      Scenario sc = horizontalMergeScenario("merge all columns", g -> {
         g.setMergeCells(true);
         g.setMergeColGroup(null);
      });

      for(boolean script : new boolean[] { false, true }) {
         TableLens lens = openLens(sc, script);
         String path = script ? "script path" : "quick path";

         assertHorizontalLayout(lens, path);
         assertEquals("6x1", span(lens, 0, 1), path);
      }
   }

   // ---- the named cells a formula, a highlight or a hyperlink can refer to
   // (RuntimeCalcTableLens.getKeyValuePairs and getFieldRowCol). The layout is the nested one
   // of the merge tests with the cells named g1 (Region), g2 (Cat) and s (the sum); rows 1 to
   // 6 are East/x 10, East/y 20, North/z 1, West/x 5, West/y 6, West/z 7.

   private static Scenario namedCellsScenario() {
      Object[][] data = nested(nrow("East", "x", 1, 10.0), nrow("East", "y", 1, 20.0),
                               nrow("North", "z", 1, 1.0), nrow("West", "x", 1, 5.0),
                               nrow("West", "y", 1, 6.0), nrow("West", "z", 1, 7.0));
      return new Scenario("named cells", nestedTypes(), data, 3, false, l -> {
         l.setCellBinding(1, 0, group("Region"));
         TableCellBinding inner = TableCellBinding.getGroupBinding("Cat");
         inner.setCellName("g2");
         inner.setRowGroup(GROUP_CELL);
         l.setCellBinding(1, 1, inner);
         TableCellBinding s = summary("Total", "Sum");
         s.setRowGroup("g2");
         s.setCellName("s");
         l.setCellBinding(1, 2, s);
      });
   }

   private RuntimeCalcTableLens openRuntimeLens(boolean script) throws Exception {
      TableLens lens = openLens(namedCellsScenario(), script);
      RuntimeCalcTableLens calc = (RuntimeCalcTableLens)
         inetsoft.report.internal.Util.getNestedTable(lens, RuntimeCalcTableLens.class);
      assertNotNull(calc, "the table is a RuntimeCalcTableLens");
      return calc;
   }

   @Test
   void keyValuePairsOfTheSummaryCellHoldItsGroupsAndItsOwnValue() throws Exception {
      for(boolean script : new boolean[] { false, true }) {
         String path = script ? "script path" : "quick path";
         Map<?, ?> kv = openRuntimeLens(script).getKeyValuePairs(2, 2, null);

         assertEquals("East", kv.get("g1"), path + ", group value of the outer group");
         assertEquals("y", kv.get("g2"), path + ", group value of the inner group");
         assertEquals(20.0, ((Number) kv.get("s")).doubleValue(), path + ", its own value");
      }
   }

   @Test
   void keyValuePairsOfAGroupCellFindTheNamedCellOfTheSameGroupsNotTheFirstOne() throws Exception {
      for(boolean script : new boolean[] { false, true }) {
         String path = script ? "script path" : "quick path";
         // West/y: the sum of this row is 6, the first sum of the table is 10
         Map<?, ?> kv = openRuntimeLens(script).getKeyValuePairs(5, 1, null);

         assertEquals("West", kv.get("g1"), path);
         assertEquals("y", kv.get("g2"), path);
         assertEquals(6.0, ((Number) kv.get("s")).doubleValue(), path + ", the sum of West/y");
         assertEquals(6.0, ((Number) kv.get(StyleConstants.COLUMN)).doubleValue(),
                      path + ", the column value follows the found cell");
      }
   }

   @Test
   void keyValuePairsOnlyAddNamedCellsOfTheGivenNamesButAlwaysTheGroups() throws Exception {
      for(boolean script : new boolean[] { false, true }) {
         String path = script ? "script path" : "quick path";
         RuntimeCalcTableLens calc = openRuntimeLens(script);

         Map<?, ?> none = calc.getKeyValuePairs(5, 1, Set.of());
         assertEquals(Set.of("g1", "g2"), none.keySet(), path + ", empty set: groups only");

         Map<?, ?> some = calc.getKeyValuePairs(5, 1, Set.of("s"));
         assertEquals(6.0, ((Number) some.get("s")).doubleValue(), path);
         assertTrue(some.containsKey("g1") && some.containsKey("g2"), path);
      }
   }

   @Test
   void keyValuePairsOfTheSameCellAreCachedAndOfAnotherCellAreNot() throws Exception {
      RuntimeCalcTableLens calc = openRuntimeLens(false);

      Map<?, ?> first = calc.getKeyValuePairs(5, 1, null);
      assertSame(first, calc.getKeyValuePairs(5, 1, null), "the same cell");
      assertEquals("x", calc.getKeyValuePairs(4, 1, null).get("g2"), "another cell");
      assertEquals("y", calc.getKeyValuePairs(5, 1, null).get("g2"), "back to the first cell");
   }

   /**
    * Two threads read the key-value pairs of one cell and two read another: every thread must see
    * the groups of its own cell. The one-entry cache keeps its key and its map in two fields.
    */
   @Disabled("Bug #82417: the one-entry cache of getKeyValuePairs is not thread-safe")
   @Test
   void keyValuePairsOfTwoCellsReadFromSeveralThreadsKeepTheirOwnGroups() throws Exception {
      RuntimeCalcTableLens calc = openRuntimeLens(false);
      String[] expected = { "x", "y", "x", "y" };
      int[] rows = { 4, 5, 4, 5 };
      java.util.concurrent.atomic.AtomicReference<String> wrong =
         new java.util.concurrent.atomic.AtomicReference<>();
      java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(4);
      Thread[] threads = new Thread[4];

      for(int t = 0; t < threads.length; t++) {
         int id = t;
         threads[t] = new Thread(() -> {
            try {
               start.await();

               for(int i = 0; i < 200000 && wrong.get() == null; i++) {
                  Object g2 = calc.getKeyValuePairs(rows[id], 1, null).get("g2");

                  if(!expected[id].equals(g2)) {
                     wrong.compareAndSet(null, "thread " + id + " row " + rows[id] +
                                         " expected " + expected[id] + " got " + g2 +
                                         " at round " + i);
                  }
               }
            }
            catch(Throwable e) {
               wrong.compareAndSet(null, "thread " + id + " failed: " + e);
            }
         });
         threads[t].start();
      }

      for(Thread thread : threads) {
         thread.join();
      }

      assertNull(wrong.get(), "the key-value pairs of a cell came back with the groups of another");
   }

   @Test
   void fieldPositionOfAParentGroupIsTheSameRuntimeRowAndTheGroupColumn() throws Exception {
      for(boolean script : new boolean[] { false, true }) {
         String path = script ? "script path" : "quick path";
         RuntimeCalcTableLens calc = openRuntimeLens(script);

         // from the sum cell of West/y (row 5, column 2) to the outer group cell: Point(row, col)
         java.awt.Point group = calc.getFieldRowCol("g1", 5, 2);
         assertEquals(new java.awt.Point(5, 0), group, path + ", the group cell to the left");

         // from the inner group cell (row 5, column 1) to the sum cell on its right
         java.awt.Point sum = calc.getFieldRowCol("s", 5, 1);
         assertEquals(new java.awt.Point(5, 2), sum, path + ", the sum cell to the right");

         assertNull(calc.getFieldRowCol("noSuchCell", 5, 1), path + ", an unknown name");
      }
   }

   @Test
   void weightedAverageWithACalcFieldWeightGivesTheWeightedMeansOnTheScriptPath()
      throws Exception
   {
      List<List<Object>> rows = run(
         calcFieldScenario("weighted average", "WeightedAverage(CF_Q)"), true);
      Map<Object, Object> byRegion = new HashMap<>();
      rows.stream().skip(1).forEach(r -> byRegion.put(r.get(0), r.get(1)));

      // East: (10 * 1 + 30 * 3) / (1 + 3); West: (20 * 2 + 15 * 5) / (2 + 5); North: 5
      assertEquals(25.0, ((Number) byRegion.get("East")).doubleValue(), 1e-9);
      assertEquals(115.0 / 7, ((Number) byRegion.get("West")).doubleValue(), 1e-9);
      assertEquals(5.0, ((Number) byRegion.get("North")).doubleValue(), 1e-9);
   }

   /**
    * A pre-condition of the table that compares a column with a detail calc field: Total is
    * greater than CF_Limit = Qty * 8. CF_Limit is read nowhere else, so the pruning of unused
    * calc fields must see it in the value of the condition, not only in its attribute.
    */
   private static Scenario conditionAgainstACalcFieldScenario(boolean usedElsewhere) {
      Object[][] data = sales(row("East", 1, 10.0), row("West", 2, 20.0), row("East", 3, 30.0),
                              row("North", 4, 5.0), row("West", 5, 15.0));
      return new Scenario("condition against a calc field", data, usedElsewhere ? 3 : 2, false, l -> {
         l.setCellBinding(1, 0, group("CF_R"));
         l.setCellBinding(1, 1, summary("CF_T", "Sum"));

         if(usedElsewhere) {
            l.setCellBinding(1, 2, summary("CF_Limit", "Max"));
         }
      }).setup((vs, calc) -> {
         vs.addCalcField("D", detailCalc("CF_R", "field['Region']", XSchema.STRING));
         vs.addCalcField("D", detailCalc("CF_T", "field['Total']", XSchema.DOUBLE));
         vs.addCalcField("D", detailCalc("CF_Limit", "field['Qty'] * 8", XSchema.DOUBLE));
         ColumnRef left = new ColumnRef(new AttributeRef(null, "Total"));
         left.setDataType(XSchema.DOUBLE);
         Condition cond = new Condition(XSchema.DOUBLE);
         cond.setOperation(XCondition.GREATER_THAN);
         cond.addValue(new ColumnRef(new AttributeRef(null, "CF_Limit")));
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(left, cond, 0));
         calc.setPreConditionList(list);
      });
   }

   @Test
   void conditionAgainstACalcFieldUsedByAnotherCellFiltersOnBothPaths() throws Exception {
      for(boolean script : new boolean[] { false, true }) {
         List<List<Object>> rows = run(conditionAgainstACalcFieldScenario(true), script);

         // East 10 > 8 and 30 > 24; West 20 > 16 (15 > 40 is false); North 5 > 32 is false
         assertEquals(Map.of("East", 40.0, "West", 20.0), groups(rows),
                      (script ? "script path: " : "quick path: ") + rows);
      }
   }

   @Test
   void conditionAgainstACalcFieldNoOtherCellUsesFiltersOnBothPaths() throws Exception {
      for(boolean script : new boolean[] { false, true }) {
         List<List<Object>> rows = run(conditionAgainstACalcFieldScenario(false), script);

         assertEquals(Map.of("East", 40.0, "West", 20.0), groups(rows),
                      (script ? "script path: " : "quick path: ") + rows);
      }
   }

   // ---- Bug #82487: column names with ' , ( " ) in a Top N or a sort by value. The script path
   // writes the names into the option string of toList(), where they were not escaped for the
   // option parser, so the rows were wrong or the script failed. The quick path was always
   // right, so both paths must give the same rows.

   /** The names that broke the script path, then the names that always worked as controls. */
   static Stream<String> columnNames() {
      return Stream.of("O'Brien", "a,b", "(x", "\"q", "x)y(z", ")a(", "a',b", "a b'",
                       "plain", "x)y", "a)", "a(b)c", "a=b", "a:b", "[x", "100%", "a|b",
                       "Year(Date)");
   }

   static Stream<String> columnNamesWithoutDoubleQuote() {
      return columnNames().filter(n -> n.indexOf('"') < 0);
   }

   private static Object[][] rankedData(String name) {
      return new Object[][] {
         { "g " + name, "v " + name, "w " + name },
         { "East", 10.0, 1.0 }, { "West", 20.0, 5.0 }, { "East", 30.0, 2.0 },
         { "North", 5.0, 9.0 }, { "West", 15.0, 3.0 } };
   }

   /** The group column, the sum of the value column and the sum of a third column. */
   private static Scenario rankScenario(String name, Consumer<TableCellBinding> group, int cols) {
      String[] types = { XSchema.STRING, XSchema.DOUBLE, XSchema.DOUBLE };
      return new Scenario("rank " + name, types, rankedData(name), cols, false, l -> {
         TableCellBinding g = group("g " + name);
         group.accept(g);
         l.setCellBinding(1, 0, g);
         l.setCellBinding(1, 1, summary("v " + name, "Sum"));

         if(cols > 2) {
            l.setCellBinding(1, 2, summary("w " + name, "Sum"));
         }
      });
   }

   /** The rows after the header as "group=value", the label of Others trimmed. */
   private static List<String> rankRows(List<List<Object>> rows) {
      List<String> list = new ArrayList<>();

      for(int i = 1; i < rows.size(); i++) {
         list.add(String.valueOf(rows.get(i).get(0)).trim() + "=" +
                  ((Number) rows.get(i).get(1)).doubleValue());
      }

      return list;
   }

   private void assertBothPaths(Scenario sc, List<String> expected) throws Exception {
      assertEquals(expected, rankRows(run(sc, false)), sc.name + ": quick path");
      assertEquals(expected, rankRows(run(sc, true)), sc.name + ": script path");
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("columnNames")
   void topNOfAColumnWithSpecialCharactersAgreesOnBothPaths(String name) throws Exception {
      Scenario sc = rankScenario(name, g -> g.setTopN(topN(2, false)), 2);
      assertBothPaths(sc, List.of("East=40.0", "West=35.0"));
   }

   // not for a double quote: the condition of the Others row reads the name through
   // rowValue["..."], which is Bug #82488 (the group condition), not the option string of this bug
   @ParameterizedTest(name = "{0}")
   @MethodSource("columnNamesWithoutDoubleQuote")
   void topNWithOthersOfAColumnWithSpecialCharactersAgreesOnBothPaths(String name)
      throws Exception
   {
      Scenario sc = rankScenario(name, g -> g.setTopN(topN(2, true)), 2);
      assertBothPaths(sc, List.of("East=40.0", "West=35.0", "Others=5.0"));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("columnNames")
   void sortByValueOfAColumnWithSpecialCharactersAgreesOnBothPaths(String name) throws Exception {
      Scenario sc = rankScenario(name, g -> {
         g.getOrderInfo(true).setOrder(XConstants.SORT_VALUE_DESC);
         g.getOrderInfo(true).setSortByCol(0);
      }, 2);
      assertBothPaths(sc, List.of("East=40.0", "West=35.0", "North=5.0"));
   }

   /** A Top N by the first sum and then a sort by the second sum: sorton and sorton2. */
   @ParameterizedTest(name = "{0}")
   @MethodSource("columnNames")
   void topNAndSortByAnotherValueOfAColumnWithSpecialCharactersAgreeOnBothPaths(String name)
      throws Exception
   {
      Scenario sc = rankScenario(name, g -> {
         g.setTopN(topN(2, false));
         g.getOrderInfo(true).setOrder(XConstants.SORT_VALUE_DESC);
         g.getOrderInfo(true).setSortByCol(1);
      }, 3);
      // East 40 and West 35 are the two largest; West has the larger third column (8 > 3)
      assertBothPaths(sc, List.of("West=35.0", "East=40.0"));
   }

   /** The messages of an exception and of all its causes. */
   // Bug #82483: a named group whose name or member value holds a special character.

   private static final String[] NAMED_GROUP_TEXTS = {
      "plain", "O'Brien", "Say \"hi\"", "a,b", "a:b", "back\\slash", "a;b", "a?b",
      "line1\nline2", "caf\u00e9 \u4e2d\u6587 \ud83d\ude00", "lit^_^eral", "a'b\"c\\d:e;f?g@h",
      "x".repeat(300)
   };

   static Stream<String> namedGroupTexts() {
      return Arrays.stream(NAMED_GROUP_TEXTS);
   }

   /** Region, Cat, Qty, Total: (sp, x, 1, 10), (sp, y, 2, 20), (West, x, 3, 5), (North, x, 4, 1). */
   private static Scenario namedGroupScenario(String name, String sp, String column, String type,
                                              String groupName, String... members)
   {
      Object[][] data = nested(nrow(sp, "x", 1, 10.0), nrow(sp, "y", 2, 20.0),
                               nrow("West", "x", 3, 5.0), nrow("North", "x", 4, 1.0));
      return new Scenario(name, nestedTypes(), data, 2, false, l -> {
         TableCellBinding g = group(column);
         SimpleNamedGroupInfo info = new SimpleNamedGroupInfo();
         AttributeRef ref = new AttributeRef(null, column);
         ref.setDataType(type);
         info.setDataRef(ref);
         info.setGroupValue(groupName, new ArrayList<>(Arrays.asList(members)));
         g.getOrderInfo(true).setNamedGroupInfo(info);
         g.getOrderInfo(true).setOthers(OrderInfo.GROUP_OTHERS);
         l.setCellBinding(1, 0, g);
         l.setCellBinding(1, 1, summary("Total", "Sum"));
      });
   }

   /** The shown group names with the value of the summary next to them. */
   private static Map<String, Object> namedGroupRows(List<List<Object>> rows) {
      Map<String, Object> map = new LinkedHashMap<>();

      for(int r = 1; r < rows.size(); r++) {
         map.put(String.valueOf(rows.get(r).get(0)), rows.get(r).get(1));
      }

      return map;
   }

   private void assertNamedGroup(Scenario sc, String group, double groupTotal,
                                 double othersTotal, String what) throws Exception
   {
      Map<String, Object> rows = namedGroupRows(run(sc, true));
      assertEquals(new LinkedHashSet<>(Arrays.asList(group, "Others")), rows.keySet(),
                   what + ", the shown names");
      assertEquals(groupTotal, ((Number) rows.get(group)).doubleValue(), 0.0001, what + ", group");
      assertEquals(othersTotal, ((Number) rows.get("Others")).doubleValue(), 0.0001,
                   what + ", Others");
   }

   @ParameterizedTest(name = "member value {0}")
   @MethodSource("namedGroupTexts")
   void namedGroupMemberValueWithSpecialCharacters(String sp) throws Exception {
      assertNamedGroup(namedGroupScenario("A", sp, "Region", XSchema.STRING, "G", sp),
                       "G", 30.0, 6.0, "A");
   }

   @ParameterizedTest(name = "group name {0}")
   @MethodSource("namedGroupTexts")
   void namedGroupNameWithSpecialCharacters(String sp) throws Exception {
      assertNamedGroup(namedGroupScenario("B", "sp", "Region", XSchema.STRING, sp, "West"),
                       sp, 5.0, 31.0, "B");
   }

   @Test
   void namedGroupWithNumberMembersIsUnchanged() throws Exception {
      // Number members keep the Number(..) form
      assertNamedGroup(namedGroupScenario("numbers", "sp", "Qty", XSchema.INTEGER, "low", "1", "2"),
                       "low", 30.0, 6.0, "numbers");
   }

   private static String causes(Throwable e) {
      StringBuilder buf = new StringBuilder();

      for(Throwable t = e; t != null; t = t.getCause()) {
         buf.append(t.getMessage()).append(" <- ");
      }

      return buf.toString();
   }

   private static List<String> diff(List<List<Object>> quick, List<List<Object>> script,
                                    int cols, boolean nullIsEmpty)
   {
      List<String> diffs = new ArrayList<>();

      if(quick.size() != script.size()) {
         diffs.add("row count: quick " + quick.size() + " script " + script.size());
      }

      for(int r = 0; r < Math.min(quick.size(), script.size()); r++) {
         for(int c = 0; c < cols; c++) {
            Object a = quick.get(r).get(c);
            Object b = script.get(r).get(c);

            if(nullIsEmpty && (a == null || "".equals(a)) && (b == null || "".equals(b))) {
               continue;
            }

            if(!same(a, b)) {
               diffs.add("[" + r + "," + c + "] quick=" + show(a) + " script=" + show(b));
            }
         }
      }

      return diffs;
   }

   private static boolean same(Object a, Object b) {
      if(a == null || b == null) {
         return a == b;
      }

      if(a instanceof Number && b instanceof Number) {
         double x = ((Number) a).doubleValue();
         double y = ((Number) b).doubleValue();
         return Double.compare(x, y) == 0 || Math.abs(x - y) < 1e-9;
      }

      // the crosstab gives a Timestamp where the script path keeps the sql Date: same moment
      if(a instanceof java.util.Date && b instanceof java.util.Date) {
         return ((java.util.Date) a).getTime() == ((java.util.Date) b).getTime();
      }

      return a.equals(b);
   }

   private static String show(Object o) {
      return o == null ? "null" : o + ":" + o.getClass().getSimpleName();
   }

   /**
    * Runs the scenario's layout on its worksheet and returns the resulting table. The sandbox
    * stays open until the next call or the end of the test. The script path is forced with
    * one extra constant formula cell in the header row.
    */
   private TableLens openLens(Scenario sc, boolean forceScript) throws Exception {
      boundTables.clear();

      if(box != null) {
         box.dispose();
         box = null;
      }

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "D");
      table.setEmbeddedData(new XEmbeddedTable(sc.types, sc.data));
      ws.addAssembly(table);

      Viewsheet vs = new Viewsheet();
      // setBaseWorksheet is not public and no test helper opens a viewsheet on a bare worksheet
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, ws);

      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      calc.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      ((CalcTableVSAssemblyInfo) calc.getVSAssemblyInfo()).setFillBlankWithZeroValue(sc.fillBlank);
      TableLayout layout = calc.getTableLayout();
      layout.setColCount(sc.cols + 1);
      sc.layout.accept(layout);

      if(sc.setup != null) {
         sc.setup.accept(vs, calc);
      }

      if(forceScript) {
         // a constant in the header row: forces the script path, no other cell depends on it
         layout.setCellBinding(0, sc.cols, new TableCellBinding(CellBinding.BIND_FORMULA, "1"));
      }

      vs.addAssembly(calc);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "test/PathAgreement", null);
      vs.setEntry(entry);
      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry) {
         @Override
         public TableAssembly getBoundTable(TableAssembly assembly, String vassembly,
                                            boolean detail)
            throws Exception
         {
            boundTables.add(vassembly + (detail ? " (detail)" : ""));
            return super.getBoundTable(assembly, vassembly, detail);
         }
      };
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);

      TableLens lens = new CalcTableVSAQuery(box, "Calc1", false).getTableLens();
      assertNotNull(lens, sc.name);
      lens.moreRows(TableLens.EOT);
      return lens;
   }

   /** The cells of the result, row by row. */
   private List<List<Object>> run(Scenario sc, boolean forceScript) throws Exception {
      TableLens lens = openLens(sc, forceScript);
      List<List<Object>> rows = new ArrayList<>();

      for(int r = 0; r < lens.getRowCount(); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < sc.cols; c++) {
            row.add(lens.getObject(r, c));
         }

         rows.add(row);
      }

      return rows;
   }

   private static TableCellBinding group(String column) {
      TableCellBinding b = TableCellBinding.getGroupBinding(column);
      b.setCellName(GROUP_CELL);
      return b;
   }

   /** A summary of the rows of the group cell, as the converter writes it. */
   private static TableCellBinding summary(String column, String formula) {
      TableCellBinding b = new TableCellBinding(CellBinding.BIND_COLUMN, column);
      b.setBType(CellBinding.SUMMARY);
      b.setFormula(formula);
      b.setRowGroup(GROUP_CELL);
      return b;
   }

   /** A summary of the columns of the group cell (horizontal expansion). */
   private static TableCellBinding summaryOfColumns(String column, String formula) {
      TableCellBinding b = summary(column, formula);
      b.setRowGroup(TableCellBinding.DEFAULT_GROUP);
      b.setColGroup(GROUP_CELL);
      return b;
   }

   private static TopNInfo topN(int n, boolean others) {
      TopNInfo topn = new TopNInfo();
      topn.setTopN(n);
      topn.setTopNSummaryCol(0);
      topn.setOthers(others);
      return topn;
   }

   private static Object[] header() {
      return new Object[] { "Region", "Qty", "Total" };
   }

   private static String[] types() {
      return new String[] { XSchema.STRING, XSchema.INTEGER, XSchema.DOUBLE };
   }

   private static String[] nestedTypes() {
      return new String[] { XSchema.STRING, XSchema.STRING, XSchema.INTEGER, XSchema.DOUBLE };
   }

   private static Object[][] nested(Object[]... rows) {
      Object[][] data = new Object[rows.length + 1][];
      data[0] = new Object[] { "Region", "Cat", "Qty", "Total" };
      System.arraycopy(rows, 0, data, 1, rows.length);
      return data;
   }

   private static Object[] nrow(String region, String cat, Integer qty, Double total) {
      return new Object[] { region, cat, qty, total };
   }

   private static String[] datedTypes() {
      return new String[] { XSchema.DATE, XSchema.DOUBLE };
   }

   private static Object[][] dated(Object[]... rows) {
      Object[][] data = new Object[rows.length + 1][];
      data[0] = new Object[] { "When", "Total" };
      System.arraycopy(rows, 0, data, 1, rows.length);
      return data;
   }

   private static Object[] drow(String date, Double total) {
      return new Object[] { date == null ? null : Date.valueOf(date), total };
   }

   /** A viewsheet calc field that reads the detail rows of the base table "D". */
   private static CalculateRef detailCalc(String name, String expression, String type) {
      CalculateRef calc = new CalculateRef(true);
      ExpressionRef ref = new ExpressionRef(null, name);
      ref.setExpression(expression);
      ref.setDataType(type);
      calc.setDataRef(ref);
      calc.setName(name);
      calc.setDataType(type);
      return calc;
   }

   /**
    * The sales table (Region, Qty, Total) read through detail calc fields CF_R, CF_Q and CF_T: a
    * group of CF_R and a summary of CF_T with the formula. CF_Q is read by the formula only, as
    * its second column.
    */
   private static Scenario calcFieldScenario(String name, String formula) {
      Object[][] data = sales(row("East", 1, 10.0), row("West", 2, 20.0), row("East", 3, 30.0),
                              row("North", 4, 5.0), row("West", 5, 15.0));
      return new Scenario(name, data, 2, false, l -> {
         l.setCellBinding(1, 0, group("CF_R"));
         l.setCellBinding(1, 1, summary("CF_T", formula));
      }).setup((vs, calc) -> {
         vs.addCalcField("D", detailCalc("CF_R", "field['Region']", XSchema.STRING));
         vs.addCalcField("D", detailCalc("CF_Q", "field['Qty']", XSchema.DOUBLE));
         vs.addCalcField("D", detailCalc("CF_T", "field['Total']", XSchema.DOUBLE));
      });
   }

   private static Scenario calcOfCalcScenario(String name) {
      Object[][] data = sales(row("East", 1, 10.0), row("West", 2, 20.0), row("East", 3, 30.0),
                              row("North", 4, 5.0), row("West", 5, 15.0));
      return new Scenario(name, data, 2, false, l -> {
         l.setCellBinding(1, 0, group("CF_R"));
         l.setCellBinding(1, 1, summary("CF_T2", "Sum"));
      }).setup((vs, calc) -> {
         vs.addCalcField("D", detailCalc("CF_R", "field['Region']", XSchema.STRING));
         vs.addCalcField("D", detailCalc("CF_T", "field['Total']", XSchema.DOUBLE));
         vs.addCalcField("D", detailCalc("CF_T2", "field['CF_T'] * 2", XSchema.DOUBLE));
      });
   }

   private static Scenario calcDateScenario(String name, int level) {
      return new Scenario(name, datedTypes(), intervalData(), 2, false, l -> {
         TableCellBinding g = group("CF_D");
         g.getOrderInfo(true).setInterval(1, level);
         l.setCellBinding(1, 0, g);
         l.setCellBinding(1, 1, summary("Total", "Sum"));
      }).setup((vs, calc) ->
         vs.addCalcField("D", detailCalc("CF_D", "field['When']", XSchema.DATE)));
   }

   private static String[] timestampTypes() {
      return new String[] { XSchema.TIME_INSTANT, XSchema.DOUBLE };
   }

   private static Object[] trow(String timestamp, Double total) {
      return new Object[] { timestamp == null ? null : Timestamp.valueOf(timestamp), total };
   }

   private static TableCellBinding timeSeriesGroup(int level) {
      TableCellBinding g = dateGroup(level, 1);
      g.setTimeSeries(true);
      return g;
   }

   /** A date group cell of the given level; interval 1 runs as a crosstab, a larger one does not. */
   private static TableCellBinding dateGroup(int level, int interval) {
      TableCellBinding g = group("When");
      g.getOrderInfo(true).setInterval(interval, level);
      return g;
   }

   // #82472: a column name holding a character that NamedCellRange or JavaScript reads must give the
   // same cells as a plain name, on the quick path and on the script path.
   static Stream<Arguments> specialNames() {
      String bs = "\\";
      return Stream.of(
         Arguments.of("a@b", "Total"), Arguments.of("who?", "Total"), Arguments.of("a;b", "Total"),
         Arguments.of("x=y;z", "Total"), Arguments.of("back" + bs + "slash", "Total"),
         Arguments.of("end" + bs, "Total"), Arguments.of("a^_^b", "Total"),
         Arguments.of("Region", "v a@b"), Arguments.of("Region", "v" + bs + "w"),
         Arguments.of("Region", "v^_^w"), Arguments.of("a,b", "Total"),
         Arguments.of("O'Brien", "Total"), Arguments.of("Region", "v a;b"),
         Arguments.of("Region", "Total"));
   }

   @ParameterizedTest(name = "group={0} measure={1}")
   @MethodSource("specialNames")
   void specialColumnNamesGiveTheSameCellsOnBothPaths(String group, String measure)
      throws Exception
   {
      Object[][] data = {
         { group, "Qty", measure }, { "East", 1, 30.0 }, { "East", 1, 10.0 },
         { "North", 1, 5.0 }, { "West", 1, 20.0 }, { "West", 1, 15.0 } };
      Scenario sc = new Scenario("special " + group + " / " + measure, data, 3, false, l -> {
         l.setCellBinding(1, 0, group(group));
         l.setCellBinding(1, 1, summary(measure, "Sum"));
         l.setCellBinding(1, 2, summary("Qty", "Sum"));
      }).expect((rows, path) -> {
         Map<String, List<Double>> m = new LinkedHashMap<>();

         for(int i = 1; i < rows.size(); i++) {
            List<Object> r = rows.get(i);
            assertNotNull(r.get(1), path + " sum of " + measure + " for " + r.get(0));
            m.put(String.valueOf(r.get(0)), List.of(((Number) r.get(1)).doubleValue(),
                                                    ((Number) r.get(2)).doubleValue()));
         }

         assertEquals(Map.of("East", List.of(40.0, 2.0),
                             "North", List.of(5.0, 1.0), "West", List.of(35.0, 2.0)),
                      m, path);
      });
      quickPathAndScriptPathGiveTheSameCells(sc);
   }

   private static Object[][] sales(Object[]... rows) {
      Object[][] data = new Object[rows.length + 1][];
      data[0] = header();
      System.arraycopy(rows, 0, data, 1, rows.length);
      return data;
   }

   private static Object[] row(Object region, Integer qty, Double total) {
      return new Object[] { region, qty, total };
   }

   static Stream<Scenario> scenarios() {
      Object[][] plain = sales(row("East", 1, 10.0), row("West", 2, 20.0), row("East", 3, 30.0),
                               row("North", 4, 5.0), row("West", 5, 15.0));
      Object[][] nulls = sales(row("East", 1, 10.0), row("East", null, null),
                               row("West", null, null), row("West", null, null),
                               row("North", 4, 5.0));
      Object[][] special = sales(row(null, 1, 1.0), row("", 2, 2.0), row("a,b", 3, 4.0),
                                 row("say \"hi\"", 4, 8.0), row("O'Brien", 5, 16.0),
                                 row("x]y", 6, 32.0), row("(p", 7, 64.0), row("a,b", 8, 128.0));
      // a group of Region with its Concat of Cat and the Concat of the whole table
      Consumer<TableLayout> concatLayout = l -> {
         l.setCellBinding(1, 0, group("Region"));
         l.setCellBinding(1, 1, summary("Cat", "Concat"));
         TableCellBinding all = summary("Cat", "Concat");
         all.setRowGroup(null);
         l.setCellBinding(1, 2, all);
      };

      Object[][] ranked = sales(row("A", 1, 100.0), row("B", 1, 90.0), row("C", 1, 80.0),
                                row("D", 1, 70.0), row("E", 1, 60.0), row("A", 1, 1.0));

      Consumer<TableLayout> oneGroup = l -> {
         l.setCellBinding(1, 0, group("Region"));
         l.setCellBinding(1, 1, summary("Total", "Sum"));
         l.setCellBinding(1, 2, summary("Qty", "Count"));
         l.setCellBinding(1, 3, summary("Total", "Max"));
         l.setCellBinding(1, 4, summary("Total", "Min"));
         l.setCellBinding(1, 5, summary("Total", "Average"));
      };

      Object[][] nestedData = nested(nrow("East", "x", 1, 10.0), nrow("East", "y", 2, 20.0),
                                     nrow("East", "x", 3, 30.0), nrow("West", "x", 4, 5.0),
                                     nrow("West", "z", 5, 15.0), nrow("West", "y", 6, 25.0),
                                     nrow("North", "z", 7, 1.0));

      Object[][] dates = dated(drow("2021-01-15", 10.0), drow("2021-02-20", 20.0),
                               drow("2021-02-25", 5.0), drow("2022-01-10", 7.0),
                               drow("2022-03-01", 4.0));
      Object[][] datesWithNull = dated(drow("2021-01-15", 10.0), drow("2021-02-20", 20.0),
                                       drow(null, 3.0), drow("2022-01-10", 7.0),
                                       drow(null, 6.0));
      Object[][] topData = nested(nrow("A", "x", 1, 100.0), nrow("A", "y", 1, 50.0),
                                  nrow("B", "x", 1, 90.0), nrow("B", "z", 1, 40.0),
                                  nrow("C", "x", 1, 80.0), nrow("C", "y", 1, 30.0),
                                  nrow("D", "z", 1, 20.0), nrow("E", "y", 1, 10.0));

      return Stream.of(
         new Scenario("D1 date group, year", datedTypes(), dates, 2, false, l -> {
            l.setCellBinding(1, 0, dateGroup(XConstants.YEAR_DATE_GROUP, 1));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }),
         new Scenario("D2 date group, month", datedTypes(), dates, 2, false, l -> {
            l.setCellBinding(1, 0, dateGroup(XConstants.MONTH_DATE_GROUP, 1));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }),
         new Scenario("D3 date group, year, with null dates", datedTypes(), datesWithNull, 2,
                      false, l -> {
            l.setCellBinding(1, 0, dateGroup(XConstants.YEAR_DATE_GROUP, 1));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }).knownDifference("the quick path labels the null date group with an empty string, " +
                            "the script path with null; both show blank, the sums agree",
                            "[1,0] quick=:String script=null"),
         new Scenario("D4 date group, every 2 years (not a crosstab)", datedTypes(), dates, 2,
                      false, l -> {
            l.setCellBinding(1, 0, dateGroup(XConstants.YEAR_DATE_GROUP, 2));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }).fallsBack(),
         // Bug #50302: the same hour of different days was merged
         new Scenario("H1 hour group: the same hour of two days is two groups", timestampTypes(),
                      dated(trow("2021-01-01 02:10:00", 1.0), trow("2021-01-02 02:20:00", 2.0),
                            trow("2021-01-01 03:05:00", 4.0), trow("2021-01-01 02:50:00", 8.0)),
                      2, false, l -> {
            l.setCellBinding(1, 0, dateGroup(XConstants.HOUR_DATE_GROUP, 1));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }).expect((rows, path) -> {
            // Jan 1 02:00 (1 + 8), Jan 2 02:00 (2) and Jan 1 03:00 (4): three groups
            assertEquals(4, rows.size(), path + ": " + rows);
            assertEquals(9.0, ((Number) rows.get(1).get(1)).doubleValue(),
                         path + ", Jan 1 02:00: " + rows);
         }),
         new Scenario("H2 hour of day (part): the same hour of two days is one group",
                      timestampTypes(),
                      dated(trow("2021-01-01 02:10:00", 1.0), trow("2021-01-02 02:20:00", 2.0),
                            trow("2021-01-01 03:05:00", 4.0), trow("2021-01-01 02:50:00", 8.0)),
                      2, false, l -> {
            l.setCellBinding(1, 0, dateGroup(XConstants.HOUR_OF_DAY_DATE_GROUP, 1));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }).expect((rows, path) -> {
            // hour 2 (1 + 2 + 8) and hour 3 (4): two groups
            assertEquals(3, rows.size(), path + ": " + rows);
            assertEquals(11.0, ((Number) rows.get(1).get(1)).doubleValue(),
                         path + ", hour 2: " + rows);
         }),
         new Scenario("S1 time series by year with a missing year", datedTypes(),
                      dated(drow("2021-03-01", 10.0), drow("2023-03-01", 5.0),
                            drow("2021-09-01", 20.0)),
                      2, false, l -> {
            l.setCellBinding(1, 0, timeSeriesGroup(XConstants.YEAR_DATE_GROUP));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }).expect((rows, path) ->
            // 2021, 2022 (no data) and 2023
            assertEquals(4, rows.size(), path + ": " + rows)),
         new Scenario("S2 time series by year with a null date", datedTypes(),
                      dated(drow("2021-03-01", 10.0), drow(null, 3.0), drow("2023-03-01", 5.0)),
                      2, false, l -> {
            l.setCellBinding(1, 0, timeSeriesGroup(XConstants.YEAR_DATE_GROUP));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }).knownDifference("the quick path labels the null date group with an empty string, " +
                            "the script path with null; both show blank, the sums agree",
                            "[1,0] quick=:String script=null"),
         new Scenario("T1 outer group top 2 without Others, inner group", nestedTypes(), topData,
                      3, false, l -> {
            TableCellBinding outer = group("Region");
            outer.setTopN(topN(2, false));
            l.setCellBinding(1, 0, outer);
            TableCellBinding inner = TableCellBinding.getGroupBinding("Cat");
            inner.setCellName("g2");
            inner.setRowGroup(GROUP_CELL);
            l.setCellBinding(1, 1, inner);
            TableCellBinding s = summary("Total", "Sum");
            s.setRowGroup("g2");
            l.setCellBinding(1, 2, s);
         }),
         new Scenario("T2 outer group top 2 with Others (not a crosstab)", nestedTypes(),
                      topData, 3, false, l -> {
            TableCellBinding outer = group("Region");
            outer.setTopN(topN(2, true));
            l.setCellBinding(1, 0, outer);
            TableCellBinding inner = TableCellBinding.getGroupBinding("Cat");
            inner.setCellName("g2");
            inner.setRowGroup(GROUP_CELL);
            l.setCellBinding(1, 1, inner);
            TableCellBinding s = summary("Total", "Sum");
            s.setRowGroup("g2");
            l.setCellBinding(1, 2, s);
         }).fallsBack(),
         new Scenario("T3 inner group top 2 with Others", nestedTypes(), topData, 3, false,
                      l -> {
            l.setCellBinding(1, 0, group("Region"));
            TableCellBinding inner = TableCellBinding.getGroupBinding("Cat");
            inner.setCellName("g2");
            inner.setRowGroup(GROUP_CELL);
            inner.setTopN(topN(2, true));
            l.setCellBinding(1, 1, inner);
            TableCellBinding s = summary("Total", "Sum");
            s.setRowGroup("g2");
            l.setCellBinding(1, 2, s);
         }),
         // detail calc fields as the group and the summary: the same cells as plain columns
         calcFieldScenario("CF1 group and summary are detail calc fields", "Sum"),
         // the second column of a two-column formula is a detail calc field no other cell uses
         // (Bug #82231): the temp crosstab must keep it
         calcFieldScenario("CF2 WeightedAverage, the weight is a calc field",
                           "WeightedAverage(CF_Q)").expect((rows, path) -> {
            Map<Object, Object> byRegion = new HashMap<>();
            rows.stream().skip(1).forEach(r -> byRegion.put(r.get(0), r.get(1)));
            assertEquals(25.0, ((Number) byRegion.get("East")).doubleValue(), 1e-9, path);
            assertEquals(115.0 / 7, ((Number) byRegion.get("West")).doubleValue(), 1e-9, path);
            assertEquals(5.0, ((Number) byRegion.get("North")).doubleValue(), 1e-9, path);
         }),
         calcFieldScenario("CF3 Covariance, the second column is a calc field",
                           "Covariance(CF_Q)"),
         calcFieldScenario("CF4 Correlation, the second column is a calc field",
                           "Correlation(CF_Q)"),
         calcFieldScenario("CF5 First, the second column is a calc field", "First(CF_Q)"),
         calcFieldScenario("CF6 Last, the second column is a calc field", "Last(CF_Q)"),
         // a detail calc field that reads another detail calc field
         calcOfCalcScenario("CF7 calc field reads another calc field"),
         // a date calc field as the group, by year and by month
         calcDateScenario("CF8 date calc field grouped by year", DateRangeRef.YEAR_INTERVAL),
         calcDateScenario("CF9 date calc field grouped by month", DateRangeRef.MONTH_INTERVAL),

         // the summary of each group next to a grand total (a summary of no group)
         new Scenario("GT1 grand total of the table, no Top N (control)", sales(
            row("A", 1, 100.0), row("B", 1, 90.0), row("C", 1, 80.0), row("D", 1, 70.0),
            row("E", 1, 60.0), row("A", 1, 1.0)), 3, false, l -> {
            l.setCellBinding(1, 0, group("Region"));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
            TableCellBinding all = summary("Total", "Sum");
            all.setRowGroup(null);
            l.setCellBinding(1, 2, all);
         }),
         new Scenario("GT2 grand total of the table, top 2 without Others", sales(
            row("A", 1, 100.0), row("B", 1, 90.0), row("C", 1, 80.0), row("D", 1, 70.0),
            row("E", 1, 60.0), row("A", 1, 1.0)), 3, false, l -> {
            TableCellBinding g = group("Region");
            g.setTopN(topN(2, false));
            l.setCellBinding(1, 0, g);
            l.setCellBinding(1, 1, summary("Total", "Sum"));
            TableCellBinding all = summary("Total", "Sum");
            all.setRowGroup(null);
            l.setCellBinding(1, 2, all);
         }).knownDifference("Bug #82408 [Dev feedback needed]: with a Top N and no Others the " +
                            "grand total is 191 (the shown groups) on the quick path and 401 " +
                            "(all data) on the script path",
                            "[1,2] quick=191.0:Double script=401.0:Double",
                            "[2,2] quick=191.0:Double script=401.0:Double"),
         new Scenario("GT3 grand total of the table, top 2 with Others", sales(
            row("A", 1, 100.0), row("B", 1, 90.0), row("C", 1, 80.0), row("D", 1, 70.0),
            row("E", 1, 60.0), row("A", 1, 1.0)), 3, false, l -> {
            TableCellBinding g = group("Region");
            g.setTopN(topN(2, true));
            l.setCellBinding(1, 0, g);
            l.setCellBinding(1, 1, summary("Total", "Sum"));
            TableCellBinding all = summary("Total", "Sum");
            all.setRowGroup(null);
            l.setCellBinding(1, 2, all);
         }).knownDifference("the script path labels it \"Others \" with a trailing space, " +
                            "on purpose; the grand total is 401 on both paths",
                            "[3,0] quick=Others:String script=Others :String"),
         new Scenario("CC1 Concat with null values and an empty group, group and grand total",
                      nestedTypes(), nested(nrow("East", "a", 1, 1.0), nrow("East", null, 2, 2.0),
                                            nrow("East", "a", 3, 3.0), nrow("North", "c", 4, 4.0),
                                            nrow("West", null, 5, 5.0),
                                            nrow("West", null, 6, 6.0)),
                      3, false, concatLayout).nullIsEmpty().expect((rows, path) -> {
            // Bug #82460: no stray comma in the grand total
            assertEquals("a,a,c", rows.get(rows.size() - 1).get(2), path + ": " + rows);
            assertEquals("a,a", rows.get(1).get(1), path + ": " + rows);
         }),
         new Scenario("CC2 Concat grand total is in row order, not in group order",
                      nestedTypes(), nested(nrow("East", "10", 1, 1.0), nrow("West", "20", 2, 2.0),
                                            nrow("East", "30", 3, 3.0), nrow("North", "5", 4, 4.0),
                                            nrow("West", "15", 5, 5.0),
                                            nrow("East", "20", 6, 6.0)),
                      3, false, concatLayout).expect((rows, path) -> {
            assertEquals("10,20,30,5,15,20", rows.get(rows.size() - 1).get(2),
                         path + ": " + rows);
            assertEquals("10,30,20", rows.get(1).get(1), path + ": " + rows);
         }),
         new Scenario("CC3 Concat with top 2 and Others", nestedTypes(),
                      nested(nrow("A", "x", 1, 100.0), nrow("B", "y", 1, 90.0),
                             nrow("C", null, 1, 80.0), nrow("D", null, 1, 70.0),
                             nrow("E", "z", 1, 60.0)),
                      3, false, l -> {
            TableCellBinding g = group("Region");
            g.setTopN(topN(2, true));
            l.setCellBinding(1, 0, g);
            l.setCellBinding(1, 1, summary("Cat", "Concat"));
            TableCellBinding all = summary("Cat", "Concat");
            all.setRowGroup(null);
            l.setCellBinding(1, 2, all);
         }).nullIsEmpty().knownDifference("the script path labels it \"Others \" with a " +
                            "trailing space, on purpose; Others is z on both paths",
                            "[3,0] quick=Others:String script=Others :String"),
         new Scenario("P2a nested groups, summary of the inner group", nestedTypes(), nestedData,
                      3, false, l -> {
            l.setCellBinding(1, 0, group("Region"));
            TableCellBinding inner = TableCellBinding.getGroupBinding("Cat");
            inner.setCellName("g2");
            inner.setRowGroup(GROUP_CELL);
            l.setCellBinding(1, 1, inner);
            TableCellBinding s = summary("Total", "Sum");
            s.setRowGroup("g2");
            l.setCellBinding(1, 2, s);
         }),
         new Scenario("P2b nested groups, the outer total repeated on each inner row",
                      nestedTypes(), nestedData, 4, false, l -> {
            l.setCellBinding(1, 0, group("Region"));
            TableCellBinding inner = TableCellBinding.getGroupBinding("Cat");
            inner.setCellName("g2");
            inner.setRowGroup(GROUP_CELL);
            l.setCellBinding(1, 1, inner);
            TableCellBinding s = summary("Total", "Sum");
            s.setRowGroup("g2");
            l.setCellBinding(1, 2, s);
            l.setCellBinding(1, 3, summary("Total", "Sum"));
         }),
         new Scenario("P2c two parallel groups with different group counts", nestedTypes(),
                      nestedData, 4, false, l -> {
            l.setCellBinding(1, 0, group("Region"));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
            TableCellBinding other = TableCellBinding.getGroupBinding("Cat");
            other.setCellName("g2");
            l.setCellBinding(1, 2, other);
            TableCellBinding s = summary("Total", "Sum");
            s.setRowGroup("g2");
            l.setCellBinding(1, 3, s);
         }).fallsBack(),
         new Scenario("P10 horizontal expansion", nestedTypes(), nestedData, 4, false, l -> {
            TableCellBinding g = group("Region");
            g.setExpansion(TableCellBinding.EXPAND_H);
            l.setCellBinding(0, 1, g);
            l.setCellBinding(1, 1, summaryOfColumns("Total", "Sum"));
         }),
         new Scenario("P1 one group, five aggregates", plain, 6, false, oneGroup),
         new Scenario("P1b the same, fill blank with zero", plain, 6, true, oneGroup),
         new Scenario("P6 aggregates with null values", nulls, 6, false, oneGroup),
         new Scenario("P6b aggregates with null values, fill blank with zero", nulls, 6, true,
                      oneGroup).knownDifference("accepted in #81084: Max/Min of an all-null " +
                                                "group are 0 on the quick path, empty on the " +
                                                "script path",
                            "[3,3] quick=0.0:Double script=null",
                            "[3,4] quick=0.0:Double script=null"),
         new Scenario("P5 special group values", special, 2, false, l -> {
            l.setCellBinding(1, 0, group("Region"));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }).knownDifference("Bug #82397: the quick path merges null and empty string into one " +
                            "group (3.0) like a crosstab; the script path keeps them apart " +
                            "(null 1.0, empty 2.0)",
                            "row count: quick 7 script 8",
                            "[1,0] quick=:String script=null",
                            "[1,1] quick=3.0:Double script=1.0:Double",
                            "[2,0] quick=(p:String script=:String",
                            "[2,1] quick=64.0:Double script=2.0:Double",
                            "[3,0] quick=a,b:String script=(p:String",
                            "[3,1] quick=132.0:Double script=64.0:Double",
                            "[4,0] quick=O'Brien:String script=a,b:String",
                            "[4,1] quick=16.0:Double script=132.0:Double",
                            "[5,0] quick=say \"hi\":String script=O'Brien:String",
                            "[5,1] quick=8.0:Double script=16.0:Double",
                            "[6,0] quick=x]y:String script=say \"hi\":String",
                            "[6,1] quick=32.0:Double script=8.0:Double"),
         new Scenario("P3 top 2 without Others", ranked, 2, false, l -> {
            TableCellBinding g = group("Region");
            g.setTopN(topN(2, false));
            l.setCellBinding(1, 0, g);
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }),
         new Scenario("P4 top 2 with Others", ranked, 2, false, l -> {
            TableCellBinding g = group("Region");
            g.setTopN(topN(2, true));
            l.setCellBinding(1, 0, g);
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }).knownDifference("the script path labels it \"Others \" with a trailing space, " +
                            "on purpose (LayoutTool: told apart from a named group)",
                            "[3,0] quick=Others:String script=Others :String"),
         new Scenario("P7 percent of the group total", plain, 3, false, l -> {
            l.setCellBinding(1, 0, group("Region"));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
            l.setCellBinding(1, 2, summary("Total", "Sum<4>"));
         })
      );
   }

   static final class Scenario {
      Scenario(String name, Object[][] data, int cols, boolean fillBlank,
               Consumer<TableLayout> layout)
      {
         this(name, types(), data, cols, fillBlank, layout);
      }

      Scenario(String name, String[] types, Object[][] data, int cols, boolean fillBlank,
               Consumer<TableLayout> layout)
      {
         this.name = name;
         this.data = data;
         this.cols = cols;
         this.fillBlank = fillBlank;
         this.layout = layout;
         this.types = types;
      }

      @Override
      public String toString() {
         return name;
      }

      /** Prepares the viewsheet and the calc table before it runs, e.g. adds calc fields. */
      Scenario setup(BiConsumer<Viewsheet, CalcTableVSAssembly> setup) {
         this.setup = setup;
         return this;
      }

      /** The quick path throws today; the reason is a bug number. */
      Scenario knownQuickFailure(String reason) {
         this.knownQuickFailure = reason;
         return this;
      }

      /** Hand computed checks of the rows, run on both paths; the second argument names the path. */
      Scenario expect(BiConsumer<List<List<Object>>, String> expect) {
         this.expect = expect;
         return this;
      }

      /**
       * An empty group is null on the script path and "" on the quick path (an empty Concat is
       * "" in the crosstab); both show blank, so the comparison treats them as equal.
       */
      Scenario nullIsEmpty() {
         this.nullIsEmpty = true;
         return this;
      }

      /** The layout is not run as temp crosstabs, so both runs use the script path. */
      Scenario fallsBack() {
         this.fallsBack = true;
         return this;
      }

      /**
       * The two paths differ today; the reason is a bug number or an accepted decision. The
       * differing cells are pinned too, so the scenario also fails when another cell changes.
       */
      Scenario knownDifference(String reason, String... diffs) {
         this.knownDifference = reason;
         this.knownDiffs = List.of(diffs);
         return this;
      }

      final String name;
      boolean fallsBack;
      boolean nullIsEmpty;
      String knownDifference;
      List<String> knownDiffs;
      String knownQuickFailure;
      BiConsumer<Viewsheet, CalcTableVSAssembly> setup;
      BiConsumer<List<List<Object>>, String> expect;
      final Object[][] data;
      final int cols;
      final boolean fillBlank;
      final Consumer<TableLayout> layout;
      final String[] types;
   }

   private static final String GROUP_CELL = "g1";
   private final List<String> boundTables =
      Collections.synchronizedList(new ArrayList<>());
   private ViewsheetSandbox box;
}

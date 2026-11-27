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
import inetsoft.report.composition.graph.VSDataSet;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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

   // ---- toList in a Freehand formula cell, end to end: the formula cell, the script engine and
   // the table behind data['*']. The function itself is covered by FormulaFunctionsTest and
   // FormulaFunctionsSortOrderTest; these cases check that the option string written in a cell
   // reaches it intact. A formula cell always runs on the script path.

   private static Scenario formulaCellScenario(String name, String formula, boolean expandDown) {
      Object[][] data = {
         { "customer", "amount" },
         { "Ann", 10.0 }, { "Bob", 50.0 }, { "Cy", 30.0 }, { "Di", 20.0 }, { "Ed", 40.0 },
         { "Bob", 5.0 } };
      return new Scenario(name, new String[] { XSchema.STRING, XSchema.DOUBLE }, data, 1, false,
                          l -> {
         TableCellBinding cell = new TableCellBinding(CellBinding.BIND_FORMULA, formula);

         if(expandDown) {
            cell.setExpansion(TableCellBinding.EXPAND_V);
         }

         l.setCellBinding(1, 0, cell);
      });
   }

   /** The values of the first column below the header row. */
   private List<Object> firstColumn(Scenario sc) throws Exception {
      List<Object> values = new ArrayList<>();
      run(sc, false).stream().skip(1).forEach(r -> values.add(r.get(0)));
      return values;
   }

   // Bug #81083: an unpaired quote or parenthesis in the remainder option swallowed the options
   // after it. The sums are Ann 10, Di 20, Cy 30, Ed 40, Bob 55; sorton ranks ascending (the
   // default sort), maxrows keeps the first three and the remainder label is added after them.
   @ParameterizedTest
   @ValueSource(strings = { "Don't know", "Others (misc", "Say \"hi", "Others)" })
   void toListRemainderWithAnUnpairedQuoteOrParenthesisKeepsTheOtherOptions(String remainder)
      throws Exception
   {
      Scenario sc = formulaCellScenario("remainder " + remainder,
         "toList(data['*'], \"remainder=" + remainder.replace("\"", "\\\"") +
         ",field=customer,sorton=sum(amount),maxrows=3\")", true);
      assertEquals(List.of("Ann", "Di", "Cy", remainder), firstColumn(sc));
   }

   // the same with the descending top 3
   @Test
   void toListRemainderWithAQuoteKeepsSortAndMaxrows() throws Exception {
      Scenario sc = formulaCellScenario("top 3 with remainder",
         "toList(data['*'], \"remainder=Don't know,field=customer,sorton=sum(amount)," +
         "sort=desc,maxrows=3\")", true);
      assertEquals(List.of("Bob", "Ed", "Cy", "Don't know"), firstColumn(sc));
   }

   // Bug #81071: the rows kept by maxrows were put back in ascending order
   @ParameterizedTest
   @CsvSource({
      "'sort=desc,maxrows=2', '4,3'",
      "'sort=asc,maxrows=2', '1,2'",
      "'sort=desc,maxrows=2,sort2=asc', '3,4'"
   })
   void toListKeepsTheSortDirectionOfTheRowsKeptByMaxrows(String options, String expected)
      throws Exception
   {
      Scenario sc = formulaCellScenario("toList " + options,
         "toList([4, 1, 3, 2], '" + options + "').join(',')", false);
      assertEquals(List.of(expected), firstColumn(sc));
   }

   // ---- column names with special characters (Bug #75665: a column called "YYYY, MMM D" came
   // out empty after the conversion; the converter itself is covered by VSTableConverterTest).
   // Here the names go through the layout of a freehand table: group, sum and count of such
   // columns on the crosstab path and on the script path. The UI refuses most of these characters
   // (only letters, digits and # % _ & - ! . are accepted), so a name reaches the layout only from
   // a database, Excel or JSON field.

   /** Names that work on both paths. */
   static Stream<String> specialColumnNames() {
      return Stream.of("YYYY, MMM D", "a,b", "Sales-Region", "Sales (USD)", "O'Brien", "say \"hi\"",
                       "a&b", "{braces}", "a.b", "a[0]", "50% off", "price $", "a:b",
                       "\u00e9t\u00e9 \u4e2d\u6587", "a=b", "=lead", "a#b", "a|b", "a~b");
   }

   /**
    * Names with a character that the range parser gives a meaning (@ ? ; and the backslash, and
    * the internal marker ^_^): no data on either path, Bug #82472.
    */
   static Stream<String> unsupportedColumnNames() {
      return Stream.of("a@b", "e-mail@work", "who?", "Active?", "a;b", "x=y;z", "back\\slash",
                       "end\\", "a^_^b");
   }

   private static Object[][] specialColumnData(String name) {
      return new Object[][] {
         { "g " + name, "v " + name },
         { "East", 10.0 }, { "West", 20.0 }, { "East", 30.0 }, { "North", 5.0 }, { "West", 15.0 } };
   }

   private static Scenario specialColumnScenario(String name) {
      String group = "g " + name;
      String value = "v " + name;
      return new Scenario("special column " + name,
                          new String[] { XSchema.STRING, XSchema.DOUBLE }, specialColumnData(name),
                          3, false, l -> {
         l.setCellBinding(1, 0, group(group));
         l.setCellBinding(1, 1, summary(value, "Sum"));
         l.setCellBinding(1, 2, summary(value, "Count"));
      }).expect((rows, path) -> {
         assertEquals(4, rows.size(), path + ": " + rows);
         String[] names = { "East", "North", "West" };
         double[] sums = { 40.0, 5.0, 35.0 };
         double[] counts = { 2.0, 1.0, 2.0 };

         for(int i = 0; i < 3; i++) {
            List<Object> row = rows.get(i + 1);
            assertEquals(names[i], row.get(0), path + ": " + rows);
            assertTrue(same(sums[i], row.get(1)), path + " sum of " + names[i] + ": " + rows);
            assertTrue(same(counts[i], row.get(2)), path + " count of " + names[i] + ": " + rows);
         }
      });
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("specialColumnNames")
   void aColumnNameWithSpecialCharactersWorksOnBothPaths(String name) throws Exception {
      quickPathAndScriptPathGiveTheSameCells(specialColumnScenario(name));
   }

   @Disabled("Bug #82472: a column name with @, ?, ; or a backslash gives no data on either path")
   @ParameterizedTest(name = "{0}")
   @MethodSource("unsupportedColumnNames")
   void aColumnNameWithAtQuestionMarkSemicolonOrBackslashWorksOnBothPaths(String name)
      throws Exception
   {
      quickPathAndScriptPathGiveTheSameCells(specialColumnScenario(name));
   }

   // the control of the case above: a plain crosstab on the same two columns is fine, so the data
   // is not lost before the freehand layout
   @ParameterizedTest(name = "{0}")
   @MethodSource("unsupportedColumnNames")
   void aPlainCrosstabOnAColumnWithSpecialCharactersGivesTheData(String name) throws Exception {
      String group = "g " + name;
      String value = "v " + name;

      Viewsheet vs = newViewsheet(new String[] { XSchema.STRING, XSchema.DOUBLE },
                                  specialColumnData(name));
      plainCrosstab(vs, group, value, AggregateFormula.SUM, false);
      openSandbox(vs, false);
      TableLens lens = (TableLens) box.getData("Crosstab1");
      assertNotNull(lens);
      lens.moreRows(TableLens.EOT);
      assertEquals(5, lens.getRowCount());
      String[] names = { "East", "North", "West", "Grand Total" };
      double[] sums = { 40.0, 5.0, 35.0, 80.0 };

      for(int i = 0; i < 4; i++) {
         assertEquals(names[i], lens.getObject(i + 1, 0));
         assertTrue(same(sums[i], lens.getObject(i + 1, 1)),
                    names[i] + ": " + lens.getObject(i + 1, 1));
      }
   }

   // ---- a table script of a freehand table that writes a component value to a bound text cell
   // (Bug #62888; #79806 made table[r][c] write to the runtime calc table and the label of a combo
   // box showed the placeholder again, Bug #80507 on the 13.x line). The cell is read from the
   // view table, the way the viewer shows it.

   static Stream<org.junit.jupiter.params.provider.Arguments> tableScripts() {
      return Stream.of(
         org.junit.jupiter.params.provider.Arguments.of(
            "table[0][0] = ComboBox1.selectedLabel;", "Label B"),
         org.junit.jupiter.params.provider.Arguments.of(
            "this.table[0][0] = ComboBox1.selectedLabel;", "Label B"),
         org.junit.jupiter.params.provider.Arguments.of(
            "table[0][0] = RadioButton1.selectedObject;", "r2"),
         org.junit.jupiter.params.provider.Arguments.of(
            "table[0][0] = 'Chosen Label';", "Chosen Label"));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("tableScripts")
   void aTableScriptWritesAComponentValueToABoundTextCell(String script, String expected)
      throws Exception
   {
      Object[][] data = sales(row("East", 1, 10.0), row("West", 2, 20.0));
      Scenario sc = new Scenario("table script " + script, data, 2, false, l -> {
         l.setCellBinding(0, 0, new TableCellBinding(CellBinding.BIND_TEXT, "placeholder"));
         l.setCellBinding(0, 1, new TableCellBinding(CellBinding.BIND_TEXT, "kept"));
         l.setCellBinding(1, 0, group("Region"));
         l.setCellBinding(1, 1, summary("Total", "Sum"));
      }).setup((vs, calc) -> {
         ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "ComboBox1");
         ListData comboData = new ListData();
         comboData.setValues(new Object[] { "va", "vb" });
         comboData.setLabels(new String[] { "Label A", "Label B" });
         combo.setListData(comboData);
         combo.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
         combo.setSelectedObject("vb");
         vs.addAssembly(combo);
         RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RadioButton1");
         ListData radioData = new ListData();
         radioData.setValues(new Object[] { "r1", "r2" });
         radioData.setLabels(new String[] { "R One", "R Two" });
         radio.setListData(radioData);
         radio.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
         radio.setSelectedObject("r2");
         vs.addAssembly(radio);
         CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) calc.getVSAssemblyInfo();
         info.setScriptEnabled(true);
         info.setScript(script);
      });

      openLens(sc, false);
      box.processChange("ComboBox1", VSAssembly.INPUT_DATA_CHANGED | VSAssembly.OUTPUT_DATA_CHANGED,
                        new ChangedAssemblyList());
      TableLens view = box.getVSTableLens("Calc1", false);
      view.moreRows(TableLens.EOT);
      assertEquals(expected, String.valueOf(view.getObject(0, 0)), "the cell the script wrote");
      assertEquals("kept", String.valueOf(view.getObject(0, 1)), "a cell the script did not touch");
      assertEquals("East", view.getObject(1, 0));
      assertTrue(same(10.0, view.getObject(1, 1)), String.valueOf(view.getObject(1, 1)));
   }

   // ---- the week of a date follows the week.start setting (Bug #81261: toList date=week used
   // Sunday whatever the setting was, the crosstab Week of Year group followed it). The group
   // cell of a freehand table with the Week of Year level and with the Week level runs on the
   // crosstab path and, with a formula cell added, on the script path (toList date=week and
   // rounddate=week); both must follow week.start. The expected values are computed from the
   // definition: week 1 is the week that contains January 1, a week starts on week.start.

   private static final String[] WEEK_DATES = {
      "2022-12-31", "2023-01-01", "2023-01-02", "2023-01-07", "2023-01-08", "2023-01-09",
      "2023-12-30", "2023-12-31", "2024-01-01" };

   private static int firstDayOf(String weekStart) {
      switch(weekStart) {
      case "monday": return Calendar.MONDAY;
      case "tuesday": return Calendar.TUESDAY;
      case "wednesday": return Calendar.WEDNESDAY;
      case "thursday": return Calendar.THURSDAY;
      case "friday": return Calendar.FRIDAY;
      case "saturday": return Calendar.SATURDAY;
      default: return Calendar.SUNDAY;
      }
   }

   private static Object[][] weekData() {
      Object[][] rows = new Object[WEEK_DATES.length][];

      for(int i = 0; i < WEEK_DATES.length; i++) {
         rows[i] = drow(WEEK_DATES[i], (double) (1 << i));
      }

      return dated(rows);
   }

   private static void withWeekStart(String weekStart, ThrowingRunnable action) {
      WeekStartUtil.withWeekStart(weekStart, () -> {
         try {
            action.run();
         }
         catch(RuntimeException | Error e) {
            throw e;
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   @ParameterizedTest(name = "week.start={0}")
   @ValueSource(strings = { "sunday", "monday", "wednesday", "saturday" })
   void weekOfYearGroupFollowsWeekStartOnBothPaths(String weekStart) throws Exception {
      withWeekStart(weekStart, () -> {
         // expected: week number -> sum of the totals of its dates
         Map<Integer, Double> expected = new TreeMap<>();

         for(int i = 0; i < WEEK_DATES.length; i++) {
            Calendar cal = new GregorianCalendar();
            cal.setFirstDayOfWeek(firstDayOf(weekStart));
            cal.setMinimalDaysInFirstWeek(1);
            cal.setTime(Date.valueOf(WEEK_DATES[i]));
            expected.merge(cal.get(Calendar.WEEK_OF_YEAR), (double) (1 << i), Double::sum);
         }

         Scenario sc = new Scenario("week of year, week.start=" + weekStart, datedTypes(),
                                    weekData(), 2, false, l -> {
            l.setCellBinding(1, 0, dateGroup(XConstants.WEEK_OF_YEAR_DATE_GROUP, 1));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }).expect((rows, path) -> {
            assertEquals(expected.size() + 1, rows.size(), path + ": " + rows);
            int i = 1;

            for(Map.Entry<Integer, Double> e : expected.entrySet()) {
               assertEquals(e.getKey().intValue(), ((Number) rows.get(i).get(0)).intValue(),
                            path + " row " + i + ": " + rows);
               assertTrue(same(e.getValue(), rows.get(i).get(1)), path + " row " + i + ": " + rows);
               i++;
            }
         });
         quickPathAndScriptPathGiveTheSameCells(sc);
      });
   }

   @ParameterizedTest(name = "week.start={0}")
   @ValueSource(strings = { "sunday", "monday", "wednesday", "saturday" })
   void weekGroupStartsOnTheWeekStartOnBothPaths(String weekStart) throws Exception {
      withWeekStart(weekStart, () -> {
         // expected: the first day of the week of each date -> sum of the totals
         Map<java.time.LocalDate, Double> expected = new TreeMap<>();
         int first = firstDayOf(weekStart);

         for(int i = 0; i < WEEK_DATES.length; i++) {
            java.time.LocalDate day = java.time.LocalDate.parse(WEEK_DATES[i]);
            int dow = day.getDayOfWeek().getValue() % 7 + 1;   // Calendar numbering, Sunday = 1
            expected.merge(day.minusDays(Math.floorMod(dow - first, 7)), (double) (1 << i),
                           Double::sum);
         }

         Scenario sc = new Scenario("week, week.start=" + weekStart, datedTypes(), weekData(), 2,
                                    false, l -> {
            l.setCellBinding(1, 0, dateGroup(XConstants.WEEK_DATE_GROUP, 1));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         }).expect((rows, path) -> {
            assertEquals(expected.size() + 1, rows.size(), path + ": " + rows);
            int i = 1;

            for(Map.Entry<java.time.LocalDate, Double> e : expected.entrySet()) {
               assertTrue(same(Date.valueOf(e.getKey()), rows.get(i).get(0)),
                          path + " row " + i + ": expected " + e.getKey() + " in " + rows);
               assertTrue(same(e.getValue(), rows.get(i).get(1)), path + " row " + i + ": " + rows);
               i++;
            }
         });
         quickPathAndScriptPathGiveTheSameCells(sc);
      });
   }

   // ---- the percent options of a summary cell. The Freehand editor offers Row Group (4), Row
   // GrandTotal (16), Col Group (8) and Col GrandTotal (32), by the groups the cell has; the
   // options 1 (Group) and 2 (GrandTotal) are not offered but can be in an older asset. The
   // expected values are computed by hand:
   //   one group: East 40, North 5, West 35, total 80
   //   nested: East x 40, East y 20, North z 1, West x 5, West y 25, West z 15, total 106 (East 60,
   //   North 1, West 45); the rows come as East/x, East/y, North/z, West/x, West/y, West/z
   //   horizontal: the same data with Cat across; East has no z, North has no x and y.

   private static final double[] ONE = { 1, 1, 1 };
   private static final double[] SHARE_OF_80 = { 40.0 / 80, 5.0 / 80, 35.0 / 80 };
   private static final double[] ALL_ONE_6 = { 1, 1, 1, 1, 1, 1 };
   private static final double[] NESTED_IN_PARENT = { 40.0 / 60, 20.0 / 60, 1, 5.0 / 45, 25.0 / 45,
                                                      15.0 / 45 };
   private static final double[] NESTED_IN_TOTAL = { 40.0 / 106, 20.0 / 106, 1.0 / 106, 5.0 / 106,
                                                     25.0 / 106, 15.0 / 106 };
   private static final double[] NESTED_OUTER = { 60.0 / 106, 60.0 / 106, 1.0 / 106, 45.0 / 106,
                                                  45.0 / 106, 45.0 / 106 };

   private static Object[][] percentPlain() {
      return sales(row("East", 1, 10.0), row("West", 2, 20.0), row("East", 3, 30.0),
                   row("North", 4, 5.0), row("West", 5, 15.0));
   }

   private static Object[][] percentNested() {
      return nested(nrow("East", "x", 1, 10.0), nrow("East", "y", 2, 20.0),
                    nrow("East", "x", 3, 30.0), nrow("West", "x", 4, 5.0),
                    nrow("West", "z", 5, 15.0), nrow("West", "y", 6, 25.0),
                    nrow("North", "z", 7, 1.0));
   }

   private static Scenario percentOneGroup(int code, double[] expected) {
      return new Scenario("percent one group Sum<" + code + ">", percentPlain(), 3, false, l -> {
         l.setCellBinding(1, 0, group("Region"));
         l.setCellBinding(1, 1, summary("Total", "Sum"));
         l.setCellBinding(1, 2, summary("Total", "Sum<" + code + ">"));
      }).expect((rows, path) -> {
         for(int i = 0; i < 3; i++) {
            assertTrue(same(expected[i], rows.get(i + 1).get(2)),
                       path + " group " + i + ": " + rows);
         }
      });
   }

   private static Scenario percentNestedGroups(int code, double[] inner, double[] outer) {
      return new Scenario("percent nested Sum<" + code + ">", nestedTypes(), percentNested(), 4,
                          false, l -> {
         l.setCellBinding(1, 0, group("Region"));
         TableCellBinding cat = TableCellBinding.getGroupBinding("Cat");
         cat.setCellName("g2");
         cat.setRowGroup(GROUP_CELL);
         l.setCellBinding(1, 1, cat);
         TableCellBinding in = summary("Total", "Sum<" + code + ">");
         in.setRowGroup("g2");
         l.setCellBinding(1, 2, in);
         l.setCellBinding(1, 3, summary("Total", "Sum<" + code + ">"));
      }).expect((rows, path) -> {
         assertEquals(7, rows.size(), path + ": " + rows);

         for(int i = 0; i < 6; i++) {
            assertTrue(same(inner[i], rows.get(i + 1).get(2)), path + " inner " + i + ": " + rows);
            assertTrue(same(outer[i], rows.get(i + 1).get(3)), path + " outer " + i + ": " + rows);
         }
      });
   }

   /** Rows East, North, West; columns x, y, z; null for a pair without data. */
   private static Scenario percentHorizontal(int code, Double[][] expected) {
      return new Scenario("percent empty intersection Sum<" + code + ">", nestedTypes(),
                          percentNested(), 4, false, l -> {
         l.setCellBinding(1, 0, group("Region"));
         TableCellBinding cat = TableCellBinding.getGroupBinding("Cat");
         cat.setCellName("g2");
         cat.setExpansion(TableCellBinding.EXPAND_H);
         l.setCellBinding(0, 1, cat);
         TableCellBinding cell = summary("Total", "Sum<" + code + ">");
         cell.setRowGroup(GROUP_CELL);
         cell.setColGroup("g2");
         l.setCellBinding(1, 1, cell);
      }).expect((rows, path) -> {
         assertEquals(4, rows.size(), path + ": " + rows);

         for(int r = 0; r < 3; r++) {
            for(int c = 0; c < 3; c++) {
               // a pair without data is pinned by the known difference (Bug #82479)
               if(expected[r][c] != null) {
                  assertTrue(same(expected[r][c], rows.get(r + 1).get(c + 1)),
                             path + " [" + r + "," + c + "]: " + rows);
               }
            }
         }
      }).knownDifference("Bug #82479: the percent of a row/column pair without data is blank on " +
                         "the crosstab path and 0.0 on the script path (a plain Sum is blank on " +
                         "both)",
                         "[1,3] quick=null script=0.0:Double",
                         "[2,1] quick=null script=0.0:Double",
                         "[2,2] quick=null script=0.0:Double");
   }

   static Stream<Scenario> percentScenarios() {
      Double[][] byRow = {
         { 40.0 / 45, 20.0 / 45, null }, { null, null, 1.0 / 16 },
         { 5.0 / 45, 25.0 / 45, 15.0 / 16 } };
      Double[][] byColumn = {
         { 40.0 / 60, 20.0 / 60, null }, { null, null, 1.0 }, { 5.0 / 45, 25.0 / 45, 15.0 / 45 } };

      return Stream.of(
         percentOneGroup(4, SHARE_OF_80), percentOneGroup(16, SHARE_OF_80),
         percentOneGroup(8, ONE), percentOneGroup(32, ONE),
         percentNestedGroups(4, NESTED_IN_PARENT, NESTED_OUTER),
         percentNestedGroups(16, NESTED_IN_TOTAL, NESTED_OUTER),
         percentNestedGroups(8, ALL_ONE_6, ALL_ONE_6),
         percentNestedGroups(32, ALL_ONE_6, ALL_ONE_6),
         percentHorizontal(4, byRow), percentHorizontal(16, byRow),
         percentHorizontal(8, byColumn), percentHorizontal(32, byColumn));
   }

   // The options Group (1) and GrandTotal (2): the script path gives the shares, the crosstab path
   // 100% for every group (Bug #82480, the editor does not offer them any more)
   @ParameterizedTest(name = "Sum<{0}>")
   @ValueSource(ints = { 1, 2 })
   void legacyPercentGroupAndGrandTotalGiveTheSharesOnTheScriptPath(int code) throws Exception {
      List<List<Object>> script = run(percentOneGroup(code, SHARE_OF_80), true);

      for(int i = 0; i < 3; i++) {
         assertTrue(same(SHARE_OF_80[i], script.get(i + 1).get(2)), "script path: " + script);
      }

      double[] inner = code == 1 ? NESTED_IN_PARENT : NESTED_IN_TOTAL;
      List<List<Object>> nested = run(percentNestedGroups(code, inner, NESTED_OUTER), true);

      for(int i = 0; i < 6; i++) {
         assertTrue(same(inner[i], nested.get(i + 1).get(2)), "script path inner: " + nested);
         assertTrue(same(NESTED_OUTER[i], nested.get(i + 1).get(3)), "script path outer: " + nested);
      }

      // pinned: this turns red when the crosstab path gives the shares as well
      List<List<Object>> quick = run(percentOneGroup(code, SHARE_OF_80), false);

      for(int i = 0; i < 3; i++) {
         assertTrue(same(1.0, quick.get(i + 1).get(2)), "Bug #82480 is fixed, update this: " + quick);
      }
   }

   // ---- the grand total after a Top N without Others covers the shown groups on the crosstab path
   // and all the rows on the script path, for every function (the question of Bug #82408). The
   // cells of the groups agree, and so does a Top N on the inner group; only the grand total
   // columns differ.

   private static Object[][] topNData() {
      return nested(nrow("A", "x", 1, 100.0), nrow("A", "y", 1, 50.0), nrow("B", "x", 1, 90.0),
                    nrow("B", "z", 1, 40.0), nrow("C", "x", 1, 80.0), nrow("C", "y", 1, 30.0),
                    nrow("D", "z", 1, 20.0), nrow("E", "y", 1, 10.0));
   }

   /** Columns in which the two paths of the scenario differ. */
   private Set<Integer> differingColumns(Scenario sc) throws Exception {
      List<List<Object>> quick = run(sc, false);
      List<List<Object>> script = run(sc, true);
      assertEquals(quick.size(), script.size(), sc.name + ": the groups differ");
      Set<Integer> cols = new TreeSet<>();

      for(String d : diff(quick, script, sc.cols, false)) {
         cols.add(Integer.parseInt(d.substring(d.indexOf(',') + 1, d.indexOf(']'))));
      }

      return cols;
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "Sum", "Count", "Median", "Average", "DistinctCount" })
   void topNWithoutOthersDiffersOnlyInTheGrandTotalOfEveryFunction(String f) throws Exception {
      // one group, top 2: group summary (1), grand total (2), grand total count (3)
      Scenario one = new Scenario("top 2 " + f, nestedTypes(), topNData(), 4, false, l -> {
         TableCellBinding g = group("Region");
         g.setTopN(topN(2, false));
         l.setCellBinding(1, 0, g);
         l.setCellBinding(1, 1, summary("Total", f));
         TableCellBinding all = summary("Total", f);
         all.setRowGroup(null);
         l.setCellBinding(1, 2, all);
         TableCellBinding count = summary("Total", "Count");
         count.setRowGroup(null);
         l.setCellBinding(1, 3, count);
      });
      // Bug #82408: the grand totals differ, the group cell does not
      assertEquals(Set.of(2, 3), differingColumns(one), "one group, " + f);

      // outer group top 2: inner summary (2), outer summary (3), grand total (4)
      Scenario nestedTop = new Scenario("outer top 2 " + f, nestedTypes(), topNData(), 5, false,
                                        l -> {
         TableCellBinding g = group("Region");
         g.setTopN(topN(2, false));
         l.setCellBinding(1, 0, g);
         TableCellBinding inner = TableCellBinding.getGroupBinding("Cat");
         inner.setCellName("g2");
         inner.setRowGroup(GROUP_CELL);
         l.setCellBinding(1, 1, inner);
         TableCellBinding in = summary("Total", f);
         in.setRowGroup("g2");
         l.setCellBinding(1, 2, in);
         l.setCellBinding(1, 3, summary("Total", f));
         TableCellBinding all = summary("Total", f);
         all.setRowGroup(null);
         l.setCellBinding(1, 4, all);
      });
      assertEquals(Set.of(4), differingColumns(nestedTop), "outer group, " + f);
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "Sum", "Count", "Median", "Average", "DistinctCount" })
   void topNOnTheInnerGroupAgreesOnBothPathsForEveryFunction(String f) throws Exception {
      Scenario sc = new Scenario("inner top 2 " + f, nestedTypes(), topNData(), 5, false, l -> {
         l.setCellBinding(1, 0, group("Region"));
         TableCellBinding inner = TableCellBinding.getGroupBinding("Cat");
         inner.setCellName("g2");
         inner.setRowGroup(GROUP_CELL);
         inner.setTopN(topN(2, false));
         l.setCellBinding(1, 1, inner);
         TableCellBinding in = summary("Total", f);
         in.setRowGroup("g2");
         l.setCellBinding(1, 2, in);
         l.setCellBinding(1, 3, summary("Total", f));
         TableCellBinding all = summary("Total", f);
         all.setRowGroup(null);
         l.setCellBinding(1, 4, all);
      });
      quickPathAndScriptPathGiveTheSameCells(sc);
   }

   // ---- a Freehand table bound to another viewsheet assembly (SourceInfo.VS_ASSEMBLY). It never
   // runs as temp crosstabs: the data of the source assembly is the base table, so both runs use
   // the script path. The expected cells are computed by hand.

   private static TableVSAssembly vsTable(Viewsheet vs, String name) {
      TableVSAssembly t = new TableVSAssembly(vs, name);
      t.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      ColumnSelection cols = new ColumnSelection();

      for(String[] c : new String[][] { { "Region", XSchema.STRING }, { "Cat", XSchema.STRING },
                                        { "Qty", XSchema.INTEGER }, { "Total", XSchema.DOUBLE } })
      {
         ColumnRef ref = new ColumnRef(new AttributeRef(null, c[0]));
         ref.setDataType(c[1]);
         cols.addAttribute(ref);
      }

      t.setColumnSelection(cols);
      vs.addAssembly(t);
      return t;
   }

   /** Rows Region, optional column header Cat, Sum of Total. */
   private static CrosstabVSAssembly vsCrosstab(Viewsheet vs, String name, boolean totals,
                                                boolean colDim)
   {
      CrosstabVSAssembly ct = new CrosstabVSAssembly(vs, name);
      ct.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      VSCrosstabInfo info = ct.getVSCrosstabInfo();
      info.setDesignRowHeaders(new VSDimensionRef[] { CrosstabVSAQuerySupport.dim("Region") });
      info.setDesignColHeaders(colDim ? new VSDimensionRef[] { CrosstabVSAQuerySupport.dim("Cat") }
                                      : new VSDimensionRef[0]);
      info.setDesignAggregates(new VSAggregateRef[] {
         CrosstabVSAQuerySupport.agg("Total", AggregateFormula.SUM) });
      info.setRowTotalVisibleValue(totals + "");
      info.setColTotalVisibleValue(totals + "");
      vs.addAssembly(ct);
      return ct;
   }

   /** East 60 (x 40, y 20), West 20 (x 5, z 15), North 1 (z 1); Total. */
   private static Object[][] vsSourceData() {
      return nested(nrow("East", "x", 1, 10.0), nrow("East", "y", 2, 20.0),
                    nrow("East", "x", 3, 30.0), nrow("West", "x", 4, 5.0),
                    nrow("West", "z", 5, 15.0), nrow("North", "z", 7, 1.0));
   }

   /** Source1 (table or crosstab) and a Freehand Calc1 over it: group Region, one summary. */
   private Scenario vsSourceScenario(String kind, String column, String formula, int cols) {
      return new Scenario("vs source " + kind, nestedTypes(), vsSourceData(), cols, false, l -> {
         l.setCellBinding(1, 0, group("Region"));
         l.setCellBinding(1, 1, summary(column, formula));
      }).setup((vs, calc) -> {
         switch(kind) {
         case "table" -> vsTable(vs, "Source1");
         case "crosstab" -> vsCrosstab(vs, "Source1", false, false);
         case "totals" -> vsCrosstab(vs, "Source1", true, false);
         default -> vsCrosstab(vs, "Source1", false, true);
         }

         calc.setSourceInfo(new SourceInfo(SourceInfo.VS_ASSEMBLY, null,
                                           Assembly.TABLE_VS_BOUND + "Source1"));
      });
   }

   @Test
   void freehandOverATableAssemblyGivesTheSameAsOverTheWorksheetTable() throws Exception {
      assertBothPaths(vsSourceScenario("table", "Total", "Sum", 2),
                      List.of(row(null, null), row("East", 60.0), row("North", 1.0),
                              row("West", 20.0)));
      assertBothPaths(vsSourceScenario("table", "Total", "Count", 2),
                      List.of(row(null, null), row("East", 3.0), row("North", 1.0), row("West", 2.0)));
      assertBothPaths(vsSourceScenario("table", "Total", "Max", 2),
                      List.of(row(null, null), row("East", 30.0), row("North", 1.0),
                              row("West", 15.0)));
   }

   /**
    * The data of a crosstab source gets its column names cleaned (parentheses become an
    * underscore): the column "Sum(Total)" is read as Sum_Total_.
    */
   @Test
   void freehandOverACrosstabAssemblyReadsTheAggregateColumnUnderTheCleanedName() throws Exception {
      List<List<Object>> expected =
         List.of(row(null, null), row("East", 60.0), row("North", 1.0), row("West", 20.0));
      assertBothPaths(vsSourceScenario("crosstab", "Sum_Total_", "Sum", 2), expected);

      // the name shown by the crosstab does not find the column
      assertBothPaths(vsSourceScenario("crosstab", "Sum(Total)", "Sum", 2),
                      List.of(row(null, null), row("East", null), row("North", null),
                              row("West", null)));
   }

   /** The rows of the source are all data of the Freehand, the Grand Total row of a crosstab too. */
   @Test
   void freehandOverACrosstabAssemblyWithTotalsTreatsTheGrandTotalRowAsAGroup() throws Exception {
      assertBothPaths(vsSourceScenario("totals", "Sum_Total_", "Sum", 2),
                      List.of(row(null, null), row("East", 60.0), row("Grand Total", 81.0),
                              row("North", 1.0), row("West", 20.0)));
   }

   /** The columns of a crosstab with a column header are named by the values of the data. */
   @Test
   void freehandOverACrosstabAssemblyWithAColumnHeaderReadsTheColumnsByTheirValues() throws Exception {
      for(boolean script : new boolean[] { false, true }) {
         List<List<Object>> rows = run(vsSourceScenario("columns", "x", "Sum", 2), script);
         assertEquals(row("East", 40.0), rows.get(1), "East x");
         assertEquals(row("West", 5.0), rows.get(3), "West x");
         Object north = rows.get(2).get(1);
         assertTrue(north == null || Double.valueOf(0.0).equals(north), "North has no x: " + north);
      }
   }

   /**
    * A condition on the source assembly changes the data of the Freehand table that is bound to it
    * after a reset of the source, as a selection or a brush does.
    */
   @Test
   void freehandOverAnAssemblyFollowsAConditionOnTheSource() throws Exception {
      for(String kind : new String[] { "table", "crosstab" }) {
         DynamicBindableVSAssembly source = openFreehandOverSource(kind);
         assertEquals("null,null ; East,60.0 ; North,1.0 ; West,20.0", sandboxRows("Calc1"), kind);

         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue("East");
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(new ColumnRef(new AttributeRef(null, "Region")), cond, 0));
         source.setPreConditionList(list);
         box.reset(null, new Assembly[] { (Assembly) source }, new ChangedAssemblyList(), false, false,
                   null);
         assertEquals("null,null ; East,60.0", sandboxRows("Calc1"), kind + " with the condition");

         source.setPreConditionList(null);
         box.reset(null, new Assembly[] { (Assembly) source }, new ChangedAssemblyList(), false, false,
                   null);
         assertEquals("null,null ; East,60.0 ; North,1.0 ; West,20.0", sandboxRows("Calc1"),
                      kind + " with the condition removed");
      }
   }

   /** Opens a sandbox with the Source1 assembly and the Freehand Calc1 over it. */
   private DynamicBindableVSAssembly openFreehandOverSource(String kind) throws Exception {
      Viewsheet vs = newViewsheet(nestedTypes(), vsSourceData());
      DynamicBindableVSAssembly source = kind.equals("table") ? vsTable(vs, "Source1")
         : vsCrosstab(vs, "Source1", false, false);
      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      calc.setSourceInfo(new SourceInfo(SourceInfo.VS_ASSEMBLY, null,
                                        Assembly.TABLE_VS_BOUND + "Source1"));
      TableLayout layout = calc.getTableLayout();
      layout.setColCount(2);
      layout.setCellBinding(1, 0, group("Region"));
      layout.setCellBinding(1, 1, summary(kind.equals("table") ? "Total" : "Sum_Total_", "Sum"));
      vs.addAssembly(calc);
      openSandbox(vs, false);
      return source;
   }

   /** The first two columns of the data of an assembly, as "a,b ; c,d". */
   private String sandboxRows(String name) throws Exception {
      Object data = box.getData(name);
      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      lens.moreRows(TableLens.EOT);
      StringBuilder sb = new StringBuilder();

      for(int r = 0; r < lens.getRowCount(); r++) {
         sb.append(r > 0 ? " ; " : "");

         for(int c = 0; c < Math.min(lens.getColCount(), 2); c++) {
            sb.append(c > 0 ? "," : "").append(lens.getObject(r, c));
         }
      }

      return sb.toString();
   }

   // ---- special characters in names and values of the generated script (escape sweep). The
   // layouts: plain group, Top N, Top N with Others, sort by value, manual order, named group.

   static final String[] SWEEP_TEXTS = { "O'Brien", "Say \"hi\"", "a,b", "a:b", "back\\slash", "x)y",
                                         "(x", "a=b", "x]y", "[x", "a;b", "a@b", "100%", "a|b" };

   /**
    * Sums: v0 = 40, v1 = 35, v2 = 5. Rows (v0, 10), (v1, 20), (v0, 30), (v2, 5), (v1, 15) in the
    * columns "g NAME" and "v NAME".
    */
   private static Scenario sweepScenario(String kind, String colName, String[] vals) {
      String group = "g " + colName;
      String value = "v " + colName;
      Object[][] data = {
         { group, value },
         { vals[0], 10.0 }, { vals[1], 20.0 }, { vals[0], 30.0 }, { vals[2], 5.0 },
         { vals[1], 15.0 } };
      return new Scenario("sweep " + kind, new String[] { XSchema.STRING, XSchema.DOUBLE }, data, 2,
                          false, l -> {
         TableCellBinding g = group(group);

         switch(kind) {
         case "topn2" -> g.setTopN(topN(2, false));
         case "topn2others" -> g.setTopN(topN(2, true));
         case "sortval" -> {
            g.getOrderInfo(true).setOrder(XConstants.SORT_VALUE_DESC);
            g.getOrderInfo(true).setSortByCol(0);
         }
         case "manual" -> {
            OrderInfo o = g.getOrderInfo(true);
            o.setOrder(XConstants.SORT_SPECIFIC);
            o.setManualOrder(new ArrayList<>(List.of(vals[2], vals[0], vals[1])));
         }
         case "named" -> {
            OrderInfo o = new OrderInfo();
            SNamedGroupInfo ng = new SimpleNamedGroupInfo();
            ng.setGroupValue("Coast", new ArrayList<>(List.of(vals[0])));
            o.setNamedGroupInfo(ng);
            o.setOthers(GROUP_OTHERS);
            g.setOrderInfo(o);
         }
         default -> { }
         }

         l.setCellBinding(1, 0, g);
         l.setCellBinding(1, 1, summary(value, "Sum"));
      });
   }

   /** What the layout must give, as "name=sum" in the order of the rows. */
   private static List<String> sweepExpected(String kind, String[] vals) {
      return switch(kind) {
         case "topn2" -> List.of(vals[0] + "=40.0", vals[1] + "=35.0");
         case "topn2others" -> List.of(vals[0] + "=40.0", vals[1] + "=35.0", "Others=5.0");
         case "sortval" -> List.of(vals[0] + "=40.0", vals[1] + "=35.0", vals[2] + "=5.0");
         case "manual" -> List.of(vals[2] + "=5.0", vals[0] + "=40.0", vals[1] + "=35.0");
         case "named" -> List.of("Coast=40.0", "Others=40.0");
         default -> List.of(vals[0] + "=40.0", vals[1] + "=35.0", vals[2] + "=5.0");
      };
   }

   /** The differences from the expected rows on both paths; an empty list is a pass. */
   private List<String> sweepMismatches(String kind, String colName, String[] vals) {
      List<String> out = new ArrayList<>();
      boolean ordered = !kind.equals("plain") && !kind.equals("topn2") &&
         !kind.equals("topn2others") &&
         !kind.equals("named");

      for(boolean script : new boolean[] { false, true }) {
         String path = script ? "script" : "crosstab";

         try {
            List<List<Object>> rows = run(sweepScenario(kind, colName, vals), script);
            List<String> actual = new ArrayList<>();

            for(List<Object> r : rows.subList(1, rows.size())) {
               String label = String.valueOf(r.get(0));
               actual.add((label.startsWith("Others") ? "Others" : label) + "=" + r.get(1));
            }

            List<String> expected = sweepExpected(kind, vals);

            if(!ordered) {
               actual = new ArrayList<>(new TreeSet<>(actual));
               expected = new ArrayList<>(new TreeSet<>(expected));
            }

            if(!expected.equals(actual)) {
               out.add(path + ": expected " + expected + " got " + actual);
            }
         }
         catch(Throwable e) {
            out.add(path + ": exception " + causes(e));
         }
      }

      return out;
   }

   private static final String[] SWEEP_PLAIN_VALUES = { "East", "West", "North" };

   /** Column names with @, ; or a backslash: no data in any layout, Bug #82472. */
   private static final Set<String> SWEEP_UNSUPPORTED_NAMES = Set.of("back\\slash", "a;b", "a@b");

   private static List<String> sweepNames(String... exclude) {
      Set<String> skip = new HashSet<>(SWEEP_UNSUPPORTED_NAMES);
      skip.addAll(List.of(exclude));
      return Arrays.stream(SWEEP_TEXTS).filter(t -> !skip.contains(t)).toList();
   }

   /** Mismatches of a layout for the column names, or the data values, of the texts. */
   private List<String> sweepAll(String kind, List<String> texts, boolean asName) {
      List<String> out = new ArrayList<>();

      for(String text : texts) {
         for(String m : asName ? sweepMismatches(kind, text, SWEEP_PLAIN_VALUES)
                               : sweepMismatches(kind, "plain",
                                                 new String[] { text, "West", "North" }))
         {
            out.add(kind + " " + (asName ? "column " : "value ") + text + " " + m);
         }
      }

      return out;
   }

   /** Group values with special characters, in every layout (the manual order but for ';'). */
   @Test
   void specialDataValuesGiveTheRightRowsInEveryLayout() throws Exception {
      List<String> mismatches = new ArrayList<>();

      for(String kind : new String[] { "plain", "topn2", "topn2others", "sortval" }) {
         mismatches.addAll(sweepAll(kind, Arrays.asList(SWEEP_TEXTS), false));
      }

      mismatches.addAll(sweepAll("manual", sweepNames(), false));
      assertEquals(List.of(), mismatches);
   }

   /** The column names that work, per layout; the ones that fail are the disabled tests below. */
   @Test
   void specialColumnNamesWorkInEveryLayoutWhereTheyDo() throws Exception {
      List<String> mismatches = new ArrayList<>();
      // the plain group is covered by aColumnNameWithSpecialCharactersWorksOnBothPaths
      mismatches.addAll(sweepAll("manual", sweepNames(), true));
      mismatches.addAll(sweepAll("named", sweepNames("Say \"hi\""), true));
      mismatches.addAll(sweepAll("topn2", sweepNames("O'Brien", "a,b", "(x"), true));
      mismatches.addAll(sweepAll("topn2others",
                                 sweepNames("O'Brien", "a,b", "(x", "Say \"hi\""), true));
      mismatches.addAll(sweepAll("sortval", sweepNames("O'Brien", "a,b", "(x"), true));
      assertEquals(List.of(), mismatches);
   }

   @Disabled("Bug #82487: Top N or sort by value on a column name with an apostrophe, a comma or" +
             " an unpaired parenthesis fails on the script path")
   @Test
   void topNAndSortByValueOnColumnNamesWithAnApostropheCommaOrParenthesis() throws Exception {
      List<String> mismatches = new ArrayList<>();

      for(String kind : new String[] { "topn2", "topn2others", "sortval" }) {
         mismatches.addAll(sweepAll(kind, List.of("O'Brien", "a,b", "(x"), true));
      }

      assertEquals(List.of(), mismatches);
   }

   @Disabled("Bug #82488: Top N with Others on a column name with a double quote gives the total in" +
             " every row on the script path")
   @Test
   void topNWithOthersOnAColumnNameWithADoubleQuote() throws Exception {
      assertEquals(List.of(), sweepAll("topn2others", List.of("Say \"hi\""), true));
   }

   @Disabled("Bug #82489: a manual order value with a semicolon is split in two")
   @Test
   void manualOrderValueWithASemicolon() throws Exception {
      assertEquals(List.of(), sweepAll("manual", List.of("a;b"), false));
   }

   @Disabled("Bug #81699: resolved on another branch; a named group on a column name with a double" +
             " quote still gives null here")
   @Test
   void namedGroupOnAColumnNameWithADoubleQuote() throws Exception {
      assertEquals(List.of(), sweepAll("named", List.of("Say \"hi\""), true));
   }

   // ---- named groups. A named group is never run as a temp crosstab, so both runs use the
   // script path; the expected cells are computed by hand.

   private static final int GROUP_OTHERS = OrderInfo.GROUP_OTHERS;
   private static final int LEAVE_OTHERS = OrderInfo.LEAVE_OTHERS;

   /** East 60, West 45, North 1, South 2 (Total); Cat and Qty vary. */
   private static Object[][] namedData() {
      return nested(nrow("East", "x", 1, 10.0), nrow("East", "y", 2, 20.0),
                    nrow("East", "x", 3, 30.0), nrow("West", "x", 4, 5.0),
                    nrow("West", "z", 5, 15.0), nrow("West", "y", 6, 25.0),
                    nrow("North", "z", 7, 1.0), nrow("South", "x", 8, 2.0));
   }

   /** Group cell on Region: Coast = East, and Inland = West + North when two is set. */
   private static TableCellBinding namedRegion(int others, boolean two, int order) {
      return namedGroup("Region", others, order, "Coast", List.of("East"),
                        two ? Map.of("Inland", List.of("West", "North")) : Map.of());
   }

   private static TableCellBinding namedGroup(String column, int others, int order, String name,
                                              List<String> members, Map<String, List<String>> more)
   {
      TableCellBinding g = group(column);
      OrderInfo o = new OrderInfo();
      SNamedGroupInfo ng = new SimpleNamedGroupInfo();
      ng.setGroupValue(name, new ArrayList<>(members));
      more.forEach((k, v) -> ng.setGroupValue(k, new ArrayList<>(v)));
      o.setNamedGroupInfo(ng);
      o.setOthers(others);
      o.setOrder(order);
      g.setOrderInfo(o);
      return g;
   }

   private static List<Object> row(Object... cells) {
      return Arrays.asList(cells);
   }

   private void assertBothPaths(Scenario sc, List<List<Object>> expected) throws Exception {
      for(boolean script : new boolean[] { false, true }) {
         assertEquals(expected, run(sc, script),
                      script ? "script path" : "crosstab path (falls back)");
      }
   }

   @Test
   void namedGroupSumsTheMembersAndMergesTheRestIntoOthers() throws Exception {
      for(boolean last : new boolean[] { true, false }) {
         for(int order : new int[] { 1, 2, 0 }) {
            Scenario sc = new Scenario("named group", nestedTypes(), namedData(), 2, false, l -> {
               l.setCellBinding(1, 0, namedRegion(GROUP_OTHERS, true, order));
               l.setCellBinding(1, 1, summary("Total", "Sum"));
            }).setup((vs, calc) -> ((CalcTableVSAssemblyInfo) calc.getInfo()).setSortOthersLast(last));
            List<List<Object>> expected = new ArrayList<>();
            expected.add(row(null, null));

            if(order == 2) {
               expected.add(row("Inland", 46.0));
               expected.add(row("Coast", 60.0));
            }
            else {
               expected.add(row("Coast", 60.0));
               expected.add(row("Inland", 46.0));
            }

            // Others is sorted with the groups only when "sort Others last" is off and the
            // order is descending
            expected.add(!last && order == 2 ? 1 : expected.size(), row("Others", 2.0));
            assertBothPaths(sc, expected);
         }
      }
   }

   @Test
   void namedGroupWithLeaveOthersKeepsTheOtherValuesAsTheirOwnGroups() throws Exception {
      for(int order : new int[] { 1, 2, 0 }) {
         Scenario sc = new Scenario("leave others", nestedTypes(), namedData(), 2, false, l -> {
            l.setCellBinding(1, 0, namedRegion(LEAVE_OTHERS, false, order));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         });
         List<List<Object>> expected = switch(order) {
            case 1 -> List.of(row(null, null), row("Coast", 60.0), row("North", 1.0),
                              row("South", 2.0), row("West", 45.0));
            case 2 -> List.of(row(null, null), row("West", 45.0), row("South", 2.0),
                              row("North", 1.0), row("Coast", 60.0));
            default -> List.of(row(null, null), row("Coast", 60.0), row("West", 45.0),
                               row("North", 1.0), row("South", 2.0));
         };
         assertBothPaths(sc, expected);
      }
   }

   @Test
   void namedGroupAsTheOuterAndAsTheInnerGroupOfANestedLayout() throws Exception {
      Scenario outer = new Scenario("named outer", nestedTypes(), namedData(), 4, false, l -> {
         l.setCellBinding(1, 0, namedRegion(GROUP_OTHERS, false, 1));
         TableCellBinding cat = TableCellBinding.getGroupBinding("Cat");
         cat.setCellName("g2");
         cat.setRowGroup(GROUP_CELL);
         l.setCellBinding(1, 1, cat);
         TableCellBinding in = summary("Total", "Sum");
         in.setRowGroup("g2");
         l.setCellBinding(1, 2, in);
         l.setCellBinding(1, 3, summary("Total", "Sum"));
      });
      assertBothPaths(outer, List.of(row(null, null, null, null),
                                     row("Coast", "x", 40.0, 60.0), row("Coast", "y", 20.0, 60.0),
                                     row("Others", "x", 7.0, 48.0), row("Others", "y", 25.0, 48.0),
                                     row("Others", "z", 16.0, 48.0)));

      Scenario inner = new Scenario("named inner", nestedTypes(), namedData(), 4, false, l -> {
         l.setCellBinding(1, 0, group("Cat"));
         TableCellBinding reg = namedRegion(GROUP_OTHERS, false, 1);
         reg.setCellName("g2");
         reg.setRowGroup(GROUP_CELL);
         l.setCellBinding(1, 1, reg);
         TableCellBinding in = summary("Total", "Sum");
         in.setRowGroup("g2");
         l.setCellBinding(1, 2, in);
         l.setCellBinding(1, 3, summary("Total", "Sum"));
      });
      assertBothPaths(inner, List.of(row(null, null, null, null),
                                     row("x", "Coast", 40.0, 47.0), row("x", "Others", 7.0, 47.0),
                                     row("y", "Coast", 20.0, 45.0), row("y", "Others", 25.0, 45.0),
                                     row("z", "Others", 16.0, 16.0)));
   }

   @Test
   void namedGroupWithMedianDistinctCountAndTheGrandTotal() throws Exception {
      Scenario sc = new Scenario("named group functions", nestedTypes(), namedData(), 4, false, l -> {
         l.setCellBinding(1, 0, namedRegion(GROUP_OTHERS, false, 1));
         l.setCellBinding(1, 1, summary("Total", "Median"));
         l.setCellBinding(1, 2, summary("Cat", "DistinctCount"));
         TableCellBinding all = summary("Total", "Sum");
         all.setRowGroup(null);
         l.setCellBinding(1, 3, all);
      });
      // Coast: 10, 20, 30 and the Cats x, y; Others: 1, 2, 5, 15, 25 and the Cats x, y, z
      assertBothPaths(sc, List.of(row(null, null, null, null), row("Coast", 20.0, 2.0, 108.0),
                                  row("Others", 5.0, 3.0, 108.0)));
   }

   @Test
   void namedGroupWithTopNKeepsTheBestGroupAndSumsTheRestIntoOthers() throws Exception {
      Scenario without = new Scenario("named top 1", nestedTypes(), namedData(), 2, false, l -> {
         TableCellBinding g = namedRegion(GROUP_OTHERS, true, 1);
         g.setTopN(topN(1, false));
         l.setCellBinding(1, 0, g);
         l.setCellBinding(1, 1, summary("Total", "Sum"));
      });
      assertBothPaths(without, List.of(row(null, null), row("Coast", 60.0)));

      Scenario with = new Scenario("named top 1 others", nestedTypes(), namedData(), 2, false, l -> {
         TableCellBinding g = namedRegion(GROUP_OTHERS, true, 1);
         g.setTopN(topN(1, true));
         l.setCellBinding(1, 0, g);
         l.setCellBinding(1, 1, summary("Total", "Sum"));
      });

      for(boolean script : new boolean[] { false, true }) {
         List<List<Object>> rows = run(with, script);
         assertEquals(3, rows.size());
         assertEquals(row("Coast", 60.0), rows.get(1));
         // Inland 46 + the rest 2; the label has a trailing space on this path (see the Top N tests)
         assertEquals(48.0, rows.get(2).get(1));
         assertTrue(String.valueOf(rows.get(2).get(0)).startsWith("Others"),
                    String.valueOf(rows.get(2)));
      }
   }

   @Test
   void namedGroupWithHorizontalExpansion() throws Exception {
      Scenario sc = new Scenario("named horizontal", nestedTypes(), namedData(), 3, false, l -> {
         TableCellBinding g = namedRegion(GROUP_OTHERS, false, 1);
         g.setExpansion(TableCellBinding.EXPAND_H);
         l.setCellBinding(0, 1, g);
         l.setCellBinding(1, 1, summaryOfColumns("Total", "Sum"));
      });
      assertBothPaths(sc, List.of(row(null, "Coast", "Others"), row(null, 60.0, 48.0)));
   }

   /** The texts that work today, as the group name and as a member value. */
   @ParameterizedTest
   @ValueSource(strings = { "plain", "a,b", "x]y", "[x", "100%" })
   void namedGroupNameAndMemberWithSpecialCharactersThatWork(String sp) throws Exception {
      assertEquals(List.of(), specialCharacterMismatches(sp, true, true));
   }

   @Test
   void namedGroupMemberWithAnApostropheWorks() throws Exception {
      assertEquals(List.of(), specialCharacterMismatches("O'Brien", true, false));
   }

   @Disabled("Bug #82483: a named group name or member value with a double quote, backslash or colon" +
             " (or a name with an apostrophe) gives null or missing totals")
   @Test
   void namedGroupNameAndMemberWithQuoteBackslashOrColon() throws Exception {
      List<String> mismatches = new ArrayList<>();

      for(String sp : new String[] { "Say \"hi\"", "back\\slash", "a:b", "a;b" }) {
         mismatches.addAll(specialCharacterMismatches(sp, true, true));
      }

      mismatches.addAll(specialCharacterMismatches("O'Brien", false, true));
      assertEquals(List.of(), mismatches);
   }

   /**
    * Data (sp, 10), (sp, 20), (West, 5), (North, 1). A: group G = {sp}, expected G 30, Others 6.
    * B: group sp = {West}, expected sp 5, Others 31. The returned list names the failing cells.
    */
   private List<String> specialCharacterMismatches(String sp, boolean asMember, boolean asName)
      throws Exception
   {
      Object[][] data = nested(nrow(sp, "x", 1, 10.0), nrow(sp, "y", 2, 20.0),
                               nrow("West", "x", 3, 5.0), nrow("North", "x", 4, 1.0));
      List<String> mismatches = new ArrayList<>();

      for(int variant = 0; variant < 2; variant++) {
         if(variant == 0 ? !asMember : !asName) {
            continue;
         }

         boolean member = variant == 0;
         Scenario sc = new Scenario("special", nestedTypes(), data, 2, false, l -> {
            l.setCellBinding(1, 0, member ?
               namedGroup("Region", GROUP_OTHERS, 1, "G", List.of(sp), Map.of()) :
               namedGroup("Region", GROUP_OTHERS, 1, sp, List.of("West"), Map.of()));
            l.setCellBinding(1, 1, summary("Total", "Sum"));
         });
         Map<String, Double> actual = new TreeMap<>();

         List<List<Object>> rows = run(sc, true);

         for(List<Object> r : rows.subList(1, rows.size())) {
            actual.put(String.valueOf(r.get(0)), (Double) r.get(1));
         }

         Map<String, Double> expected = new TreeMap<>(member ?
            Map.of("G", 30.0, "Others", 6.0) : Map.of(sp, 5.0, "Others", 31.0));

         if(!expected.equals(actual)) {
            mismatches.add((member ? "member " : "name ") + sp + ": expected " + expected +
                           " got " + actual);
         }
      }

      return mismatches;
   }

   @Disabled("Bug #82484: a named group called Others is merged with the automatic Others row")
   @Test
   void namedGroupCalledOthersStaysApartFromTheAutomaticOthersRow() throws Exception {
      Object[][] data = nested(nrow("East", "x", 1, 10.0), nrow("West", "x", 2, 5.0),
                               nrow(null, "x", 3, 7.0), nrow("North", "x", 4, 1.0));
      Scenario sc = new Scenario("named Others", nestedTypes(), data, 2, false, l -> {
         l.setCellBinding(1, 0,
            namedGroup("Region", GROUP_OTHERS, 1, "Others", List.of("East"), Map.of()));
         l.setCellBinding(1, 1, summary("Total", "Sum"));
      });
      List<Double> sums = new ArrayList<>();
      List<List<Object>> rows = run(sc, true);

      for(List<Object> r : rows.subList(1, rows.size())) {
         sums.add((Double) r.get(1));
      }

      Collections.sort(sums);
      assertEquals(List.of(10.0, 13.0), sums);
   }

   // ---- the order of the rows of the data. The rows of the groups are mixed or sorted by the group;
   // the cells must be the same, and the time of the script path must not depend on the order
   // (Bug #82482: the selector visits every row between the first and the last row of a group).

   /**
    * Region, Cat, Qty, Total. The same rows in both orders: Region of row i is i % groups, the rows
    * of a group are spread over the table (mixed) or stably sorted by Region (sorted).
    */
   private static Object[][] groupedRows(int rows, int groups, boolean sorted) {
      List<Object[]> list = new ArrayList<>();

      for(int i = 0; i < rows; i++) {
         list.add(new Object[] { String.format("g%05d", i % groups), "c" + (i % 7), i,
                                 (double) (i % 97) });
      }

      if(sorted) {
         list.sort(Comparator.comparing(r -> (String) r[0]));
      }

      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "Region", "Cat", "Qty", "Total" };

      for(int i = 0; i < rows; i++) {
         data[i + 1] = list.get(i);
      }

      return data;
   }

   /** Group Region, nested group Cat, the Sum of the inner and of the outer group. */
   private static Scenario nestedSumScenario(String name, Object[][] data) {
      return new Scenario(name, nestedTypes(), data, 4, false, l -> {
         l.setCellBinding(1, 0, group("Region"));
         TableCellBinding cat = TableCellBinding.getGroupBinding("Cat");
         cat.setCellName("g2");
         cat.setRowGroup(GROUP_CELL);
         l.setCellBinding(1, 1, cat);
         TableCellBinding in = summary("Total", "Sum");
         in.setRowGroup("g2");
         l.setCellBinding(1, 2, in);
         l.setCellBinding(1, 3, summary("Total", "Sum"));
      });
   }

   @Test
   void theCellsDoNotDependOnTheOrderOfTheRows() throws Exception {
      Scenario mixed = nestedSumScenario("mixed rows", groupedRows(3000, 300, false));
      Scenario sorted = nestedSumScenario("sorted rows", groupedRows(3000, 300, true));

      for(boolean script : new boolean[] { false, true }) {
         List<List<Object>> a = run(mixed, script);
         List<List<Object>> b = run(sorted, script);
         assertEquals(300 * 7 + 1, a.size(), (script ? "script" : "crosstab") + " path");
         assertEquals(List.of(), diff(a, b, 4, false), (script ? "script" : "crosstab") + " path");
      }
   }

   @Disabled("Bug #82482: the script path scans every row between the first and last row of a group")
   @Test
   void theTimeOfTheScriptPathDoesNotDependOnTheOrderOfTheRows() throws Exception {
      Scenario mixed = nestedSumScenario("mixed rows", groupedRows(100000, 4000, false));
      Scenario sorted = nestedSumScenario("sorted rows", groupedRows(100000, 4000, true));
      run(sorted, true);   // warm up

      long t0 = System.nanoTime();
      run(sorted, true);
      long sortedMs = (System.nanoTime() - t0) / 1_000_000;
      t0 = System.nanoTime();
      run(mixed, true);
      long mixedMs = (System.nanoTime() - t0) / 1_000_000;

      assertTrue(mixedMs < 5 * sortedMs + 500,
                 "script path: mixed rows " + mixedMs + " ms, sorted rows " + sortedMs + " ms");
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

   /** The messages of an exception and of all its causes. */
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
      Viewsheet vs = newViewsheet(sc.types, sc.data);
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
      openSandbox(vs, true);

      TableLens lens = new CalcTableVSAQuery(box, "Calc1", false).getTableLens();
      assertNotNull(lens, sc.name);
      lens.moreRows(TableLens.EOT);
      return lens;
   }

   /**
    * A viewsheet on a worksheet with the embedded table "D"; the sandbox of the last test step is
    * disposed. setBaseWorksheet is not public and no test helper opens a viewsheet on a bare
    * worksheet.
    */
   private Viewsheet newViewsheet(String[] types, Object[][] data) throws Exception {
      if(box != null) {
         box.dispose();
         box = null;
      }

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "D");
      table.setEmbeddedData(new XEmbeddedTable(types, data));
      ws.addAssembly(table);
      Viewsheet vs = new Viewsheet();
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, ws);
      return vs;
   }

   /** Opens {@link #box} on the viewsheet; trackBoundTables fills {@link #boundTables}. */
   private void openSandbox(Viewsheet vs, boolean trackBoundTables) {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "test/PathAgreement", null);
      vs.setEntry(entry);

      if(trackBoundTables) {
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
      }
      else {
         box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      }

      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
   }

   /** A crosstab "Crosstab1" on the table D: one row dimension, one aggregate, no column header. */
   private static CrosstabVSAssembly plainCrosstab(Viewsheet vs, String rowDim, String column,
                                                   AggregateFormula formula, boolean colTotal)
   {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      VSCrosstabInfo info = crosstab.getVSCrosstabInfo();
      info.setDesignRowHeaders(new VSDimensionRef[] { CrosstabVSAQuerySupport.dim(rowDim) });
      info.setDesignColHeaders(new VSDimensionRef[0]);
      info.setDesignAggregates(
         new VSAggregateRef[] { CrosstabVSAQuerySupport.agg(column, formula) });
      info.setRowTotalVisibleValue("true");

      if(colTotal) {
         info.setColTotalVisibleValue("true");
      }

      vs.addAssembly(crosstab);
      return crosstab;
   }

   /**
    * A plain crosstab (the quick path is built from the same CrossTabFilter) of the function
    * data: Region in the rows, one aggregate, row total shown. Returns the rows as text.
    */
   private List<String> crosstabRows(boolean nulls, String column, AggregateFormula formula)
      throws Exception
   {
      Viewsheet vs = newViewsheet(FUNCTION_TYPES, functionData(nulls));
      plainCrosstab(vs, "Region", column, formula, true);
      openSandbox(vs, false);

      TableLens lens = (TableLens) box.getData("Crosstab1");
      assertNotNull(lens);
      lens.moreRows(TableLens.EOT);
      List<String> rows = new ArrayList<>();

      for(int r = 0; r < lens.getRowCount(); r++) {
         StringBuilder buf = new StringBuilder();

         for(int c = 0; c < lens.getColCount(); c++) {
            buf.append(c > 0 ? " | " : "").append(lens.getObject(r, c));
         }

         rows.add(buf.toString());
      }

      return rows;
   }

   // Bug #82460
   @Test
   void crosstabConcatGrandTotalIgnoresAGroupWithoutValues() throws Exception {
      List<String> rows = crosstabRows(true, "Label", AggregateFormula.CONCAT);
      assertEquals("Grand Total | a,a,c | a,a,c", rows.get(rows.size() - 1));
   }

   @Test
   void crosstabConcatGrandTotalOfGroupsWithValuesJoinsTheRowsInRowOrder() throws Exception {
      List<String> rows = crosstabRows(false, "Label", AggregateFormula.CONCAT);
      assertEquals("East | a,b,a | a,b,a", rows.get(1));
      assertEquals("West | b,b | b,b", rows.get(3));
      assertEquals("Grand Total | a,b,a,b,b,c | a,b,a,b,b,c", rows.get(4));
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

   private static final String KNOWN_82231 =
      "Bug #82231: the pruning of the temp crosstab removes the calc field of the second column";

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

      return Stream.concat(Stream.of(
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
         // (Bug #82231): the pruning of the temp crosstab removes it
         calcFieldScenario("CF2 WeightedAverage, the weight is a calc field",
                           "WeightedAverage(CF_Q)").knownQuickFailure(KNOWN_82231),
         calcFieldScenario("CF3 Covariance, the second column is a calc field",
                           "Covariance(CF_Q)").knownQuickFailure(KNOWN_82231),
         calcFieldScenario("CF4 Correlation, the second column is a calc field",
                           "Correlation(CF_Q)").knownQuickFailure(KNOWN_82231),
         calcFieldScenario("CF5 First, the second column is a calc field", "First(CF_Q)")
            .knownQuickFailure(KNOWN_82231),
         calcFieldScenario("CF6 Last, the second column is a calc field", "Last(CF_Q)")
            .knownQuickFailure(KNOWN_82231),
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
                            "[3,0] quick=Others:String script=Others :String")
      ), Stream.concat(functionScenarios(), percentScenarios()));
   }

   // ---- the aggregate functions of the summary cell, one scenario per function. Each has a
   // group cell (East, North, West) and a summary cell of the group next to a summary of no
   // group (the grand total of the table). The expected values are computed by hand from the
   // rows below, not taken from either path:
   //   East: Qty 1, 3, 2   Total 10, 30, 20   Label a, b, a
   //   West: Qty 2, 5      Total 20, 15       Label b, b
   //   North: Qty 4        Total 5            Label c
   // The null variant: East has Qty 1, 2, 3 with Total 10, null, 30 and Label a, null, a; West
   // has two rows without any value.

   /** An expected value that is not checked: the cell is pinned by a known difference. */
   private static final Object SKIP = new Object();

   private static final String[] FUNCTION_TYPES =
      { XSchema.STRING, XSchema.INTEGER, XSchema.DOUBLE, XSchema.STRING };

   private static Object[][] functionData(boolean nulls) {
      if(nulls) {
         return new Object[][] {
            { "Region", "Qty", "Total", "Label" },
            { "East", 1, 10.0, "a" }, { "East", 2, null, null }, { "East", 3, 30.0, "a" },
            { "West", null, null, null }, { "West", null, null, null },
            { "North", 4, 5.0, "c" } };
      }

      return new Object[][] {
         { "Region", "Qty", "Total", "Label" },
         { "East", 1, 10.0, "a" }, { "East", 3, 30.0, "b" }, { "East", 2, 20.0, "a" },
         { "West", 2, 20.0, "b" }, { "West", 5, 15.0, "b" },
         { "North", 4, 5.0, "c" } };
   }

   /**
    * @param expected the value of East, North, West, and of the grand total
    */
   private static Scenario functionScenario(String column, String formula, boolean nulls,
                                            Object... expected)
   {
      return new Scenario((nulls ? "F-null " : "F ") + column + " " + formula, FUNCTION_TYPES,
                          functionData(nulls), 3, false, l -> {
         l.setCellBinding(1, 0, group("Region"));
         l.setCellBinding(1, 1, summary(column, formula));
         TableCellBinding all = summary(column, formula);
         all.setRowGroup(null);
         l.setCellBinding(1, 2, all);
      }).expect((rows, path) -> {
         // header row, then East, North, West (ascending)
         String[] names = { "East", "North", "West" };

         for(int i = 0; i < 3; i++) {
            assertEquals(names[i], rows.get(i + 1).get(0), path);

            if(expected[i] != SKIP) {
               assertTrue(same(expected[i], rows.get(i + 1).get(1)),
                          path + " " + names[i] + ": expected " + show(expected[i]) +
                          " but was " + show(rows.get(i + 1).get(1)));
            }
         }

         if(expected[3] != SKIP) {
            assertTrue(same(expected[3], rows.get(1).get(2)),
                       path + " grand total: expected " + show(expected[3]) + " but was " +
                       show(rows.get(1).get(2)));
         }
      });
   }

   /**
    * Two nested groups (Region, then Cat) with the summary of the inner group and the summary of
    * the outer group on each inner row. The outer summary of a function that is not additive
    * (median, distinct count, deviation) is computed from the rows of the Region, not from the
    * inner results. The rows are East/x, East/y, North/z, West/x, West/y, West/z:
    *   East: x = Total 10, 30   y = 20      North: z = 1      West: x = 5   y = 25   z = 15
    * @param inner the inner summary of the six rows
    * @param outer the outer summary of East, North, West
    */
   private static Scenario nestedFunctionScenario(String formula, Object[] inner, Object[] outer) {
      Object[][] data = nested(nrow("East", "x", 1, 10.0), nrow("East", "y", 2, 20.0),
                               nrow("East", "x", 3, 30.0), nrow("West", "x", 4, 5.0),
                               nrow("West", "z", 5, 15.0), nrow("West", "y", 6, 25.0),
                               nrow("North", "z", 7, 1.0));
      int[] regionOfRow = { 0, 0, 1, 2, 2, 2 };
      return new Scenario("F-nested Total " + formula, nestedTypes(), data, 4, false, l -> {
         l.setCellBinding(1, 0, group("Region"));
         TableCellBinding cat = TableCellBinding.getGroupBinding("Cat");
         cat.setCellName("g2");
         cat.setRowGroup(GROUP_CELL);
         l.setCellBinding(1, 1, cat);
         TableCellBinding in = summary("Total", formula);
         in.setRowGroup("g2");
         l.setCellBinding(1, 2, in);
         l.setCellBinding(1, 3, summary("Total", formula));
      }).expect((rows, path) -> {
         assertEquals(7, rows.size(), path + ": the rows are the header and six inner groups");

         for(int i = 0; i < 6; i++) {
            List<Object> row = rows.get(i + 1);
            assertTrue(same(inner[i], row.get(2)),
                       path + " " + row.get(0) + "/" + row.get(1) + " inner: expected " +
                       show(inner[i]) + " but was " + show(row.get(2)));
            assertTrue(same(outer[regionOfRow[i]], row.get(3)),
                       path + " " + row.get(0) + "/" + row.get(1) + " outer: expected " +
                       show(outer[regionOfRow[i]]) + " but was " + show(row.get(3)));
         }
      });
   }

   static Stream<Scenario> functionScenarios() {
      return Stream.of(
         functionScenario("Total", "Sum", false, 60.0, 5.0, 35.0, 100.0),
         functionScenario("Total", "Count", false, 3.0, 1.0, 2.0, 6.0),
         functionScenario("Total", "Average", false, 20.0, 5.0, 17.5, 100.0 / 6),
         functionScenario("Total", "Product", false, 6000.0, 5.0, 300.0, 9000000.0),
         // sample statistics (divide by n - 1); a group of one row gives 0, as the crosstab does
         // all rows: the sum of the squared deviations of Total from its mean is 1150/3
         functionScenario("Total", "Variance", false, 100.0, 0.0, 12.5, 1150.0 / 15),
         functionScenario("Total", "StandardDeviation", false, 10.0, 0.0, Math.sqrt(12.5),
                          Math.sqrt(1150.0 / 15)),
         functionScenario("Total", "PopulationVariance", false, 200.0 / 3, 0.0, 6.25,
                          1150.0 / 18),
         functionScenario("Total", "PopulationStandardDeviation", false, Math.sqrt(200.0 / 3),
                          0.0, 2.5, Math.sqrt(1150.0 / 18)),
         functionScenario("Total", "Median", false, 20.0, 5.0, 17.5, 17.5),
         // the second largest / smallest value counts duplicates (20, 20)
         functionScenario("Total", "NthLargest(2)", false, 20.0, null, 15.0, 20.0),
         functionScenario("Total", "NthSmallest(2)", false, 20.0, null, 20.0, 10.0),
         functionScenario("Total", "DistinctCount", false, 3.0, 1.0, 2.0, 5.0),
         // the second column is the order: the Total of the lowest / highest Qty
         functionScenario("Total", "First(Qty)", false, 10.0, 5.0, 20.0, 10.0),
         functionScenario("Total", "Last(Qty)", false, 30.0, 5.0, 15.0, 15.0),
         // all rows: sum of the products of the deviations -25/3, of Qty 65/6, of Total 1150/3
         functionScenario("Total", "Correlation(Qty)", false, 1.0, null, -1.0,
                          (-25.0 / 3) / Math.sqrt(65.0 / 6 * 1150.0 / 3)),
         // population covariance (the variance above divides by n - 1)
         functionScenario("Total", "Covariance(Qty)", false, 20.0 / 3, 0.0, -3.75, -25.0 / 18),
         functionScenario("Total", "WeightedAverage(Qty)", false, 140.0 / 6, 5.0, 115.0 / 7,
                          275.0 / 17),
         functionScenario("Label", "Mode", false, "a", "c", "b", "b"),
         functionScenario("Label", "Count", false, 3.0, 1.0, 2.0, 6.0),
         functionScenario("Label", "DistinctCount", false, 2.0, 1.0, 1.0, 3.0),
         functionScenario("Label", "Max", false, "b", "c", "b", "c"),
         functionScenario("Label", "Min", false, "a", "c", "b", "a"),
         functionScenario("Label", "First(Qty)", false, "a", "c", "b", "a"),
         functionScenario("Label", "Last(Qty)", false, "b", "c", "b", "b"),
         // a Sum or an Average of a string column has no value, on both paths
         functionScenario("Label", "Sum", false, null, null, null, null),
         functionScenario("Label", "Average", false, null, null, null, null),

         // nulls: West has no value at all, East has one null row
         functionScenario("Total", "Sum", true, 40.0, 5.0, null, 45.0),
         functionScenario("Total", "Count", true, 2.0, 1.0, 0.0, 3.0),
         functionScenario("Total", "Average", true, 20.0, 5.0, null, 15.0),
         functionScenario("Total", "Product", true, 300.0, 5.0, null, 1500.0),
         functionScenario("Total", "Variance", true, 200.0, 0.0, null, 175.0),
         functionScenario("Total", "PopulationVariance", true, 100.0, 0.0, null,
                          350.0 / 3),
         functionScenario("Total", "Median", true, 20.0, 5.0, null, 10.0),
         functionScenario("Total", "NthLargest(2)", true, 10.0, null, null, 10.0),
         functionScenario("Total", "NthSmallest(2)", true, 30.0, null, null, 10.0),
         functionScenario("Total", "DistinctCount", true, 2.0, 1.0, 0.0, 3.0),
         // the null Total row (Qty 2) is skipped, not the last row of East by Qty
         functionScenario("Total", "First(Qty)", true, 10.0, 5.0, null, 10.0),
         functionScenario("Total", "Last(Qty)", true, 30.0, 5.0, null, 5.0),
         functionScenario("Total", "WeightedAverage(Qty)", true, 25.0, 5.0, null, 15.0),
         functionScenario("Total", "Covariance(Qty)", true, 10.0, 0.0, null, 0.0),
         functionScenario("Label", "Mode", true, "a", "c", null, "a"),
         functionScenario("Label", "Count", true, 2.0, 1.0, 0.0, 3.0),
         functionScenario("Label", "DistinctCount", true, 1.0, 1.0, 0.0, 2.0),
         functionScenario("Label", "Max", true, "a", "c", null, "c"),

         nestedFunctionScenario("Median", new Object[] { 20.0, 20.0, 1.0, 5.0, 25.0, 15.0 },
                                new Object[] { 20.0, 1.0, 15.0 }),
         nestedFunctionScenario("DistinctCount", new Object[] { 2.0, 1.0, 1.0, 1.0, 1.0, 1.0 },
                                new Object[] { 3.0, 1.0, 3.0 }),
         nestedFunctionScenario("StandardDeviation",
                                new Object[] { Math.sqrt(200), 0.0, 0.0, 0.0, 0.0, 0.0 },
                                new Object[] { 10.0, 0.0, 10.0 }),
         nestedFunctionScenario("Product", new Object[] { 300.0, 20.0, 1.0, 5.0, 25.0, 15.0 },
                                new Object[] { 6000.0, 1.0, 1875.0 }),
         nestedFunctionScenario("NthLargest(2)",
                                new Object[] { 10.0, null, null, null, null, null },
                                new Object[] { 20.0, null, 15.0 }),

         // Concat (Bug #82460, fixed): the grand total is in row order on both paths and has no
         // comma for a group without values; an empty group is "" on the quick path, null on the
         // script path
         functionScenario("Total", "Concat", false, "10.0,30.0,20.0", "5.0", "20.0,15.0", SKIP),
         functionScenario("Label", "Concat", true, "a,a", "c", SKIP, SKIP).nullIsEmpty()
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

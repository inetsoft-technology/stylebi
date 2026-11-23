/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.data;

import inetsoft.graph.scale.TimeScale;
import inetsoft.test.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static inetsoft.graph.data.TimeSeriesRowFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gap filling of {@link TimeSeriesRow#calculate(DataSet)}: periods, groups, ranges, missing
 * values, the measure checker and the row limit.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TimeSeriesRowTest {
   private TimeZone savedZone;
   private Locale savedLocale;

   @BeforeEach
   void pinDefaults() {
      savedZone = TimeZone.getDefault();
      savedLocale = Locale.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      Locale.setDefault(Locale.US);
   }

   @AfterEach
   void restoreDefaults() {
      TimeZone.setDefault(savedZone);
      Locale.setDefault(savedLocale);
   }

   private static final String DT = "yyyy-MM-dd HH:mm";
   private static final String DTS = "yyyy-MM-dd HH:mm:ss";

   private static List<Object[]> run(TimeSeriesRow row, DataSet data) {
      return row.calculate(data);
   }

   // ---------------------------------------------------------------- levels

   @Test
   void yearGapsAreFilled() {
      DefaultDataSet d = ds(r(at(2019, 6, 15), "A", 10), r(at(2022, 6, 15), "A", 20));
      assertEquals(exp("2020-06-15|A|0", "2021-06-15|A|0"), render(run(tsr(TimeScale.YEAR, 0), d)));
   }

   @Test
   void quarterGapsAreFilled() {
      DefaultDataSet d = ds(r(at(2021, 2, 10), "A", 10), r(at(2022, 2, 10), "A", 20));
      assertEquals(exp("2021-05-10|A|0", "2021-08-10|A|0", "2021-11-10|A|0"),
                   render(run(tsr(TimeScale.QUARTER, 0), d)));
   }

   @Test
   void monthGapsAreFilled() {
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 10), r(at(2023, 5, 10), "A", 20));
      assertEquals(exp("2023-02-10|A|0", "2023-03-10|A|0", "2023-04-10|A|0"),
                   render(run(tsr(TimeScale.MONTH, 0), d)));
   }

   @Test
   void monthGapsAreFilledAcrossTheYearEnd() {
      DefaultDataSet d = ds(r(at(2022, 11, 5), "A", 10), r(at(2023, 2, 5), "A", 20));
      assertEquals(exp("2022-12-05|A|0", "2023-01-05|A|0"), render(run(tsr(TimeScale.MONTH, 0), d)));
   }

   @Test
   void weekGapsAreFilledMidYear() {
      DefaultDataSet d = ds(r(at(2023, 3, 6), "A", 10), r(at(2023, 3, 27), "A", 20));
      assertEquals(exp("2023-03-13|A|0", "2023-03-20|A|0"), render(run(tsr(TimeScale.WEEK, 0), d)));
   }

   @Test
   void dayGapsAreFilled() {
      DefaultDataSet d = ds(r(at(2023, 3, 1), "A", 10), r(at(2023, 3, 5), "A", 20));
      assertEquals(exp("2023-03-02|A|0", "2023-03-03|A|0", "2023-03-04|A|0"),
                   render(run(tsr(TimeScale.DAY, 0), d)));
   }

   @Test
   void dayGapsAreFilledAcrossTheMonthEnd() {
      DefaultDataSet d = ds(r(at(2023, 2, 27), "A", 10), r(at(2023, 3, 2), "A", 20));
      assertEquals(exp("2023-02-28|A|0", "2023-03-01|A|0"), render(run(tsr(TimeScale.DAY, 0), d)));
   }

   @Test
   void hourGapsAreFilled() {
      DefaultDataSet d = ds(r(at(2023, 7, 15, 10, 0), "A", 10), r(at(2023, 7, 15, 14, 0), "A", 20));
      assertEquals(exp("2023-07-15 11:00|A|0", "2023-07-15 12:00|A|0", "2023-07-15 13:00|A|0"),
                   render(run(tsr(TimeScale.HOUR, 0), d), DT));
   }

   @Test
   void minuteGapsAreFilled() {
      DefaultDataSet d = ds(r(at(2023, 7, 15, 10, 58), "A", 10), r(at(2023, 7, 15, 11, 2), "A", 20));
      assertEquals(exp("2023-07-15 10:59|A|0", "2023-07-15 11:00|A|0", "2023-07-15 11:01|A|0"),
                   render(run(tsr(TimeScale.MINUTE, 0), d), DT));
   }

   @Test
   void secondGapsAreFilled() {
      DefaultDataSet d = ds(r(at(2023, 7, 15, 10, 0, 58), "A", 10),
                            r(at(2023, 7, 15, 10, 1, 2), "A", 20));
      assertEquals(exp("2023-07-15 10:00:59|A|0", "2023-07-15 10:01:00|A|0",
                       "2023-07-15 10:01:01|A|0"),
                   render(run(tsr(TimeScale.SECOND, 0), d), DTS));
   }


   private static Date period(int level, int k) {
      GregorianCalendar cal;
      int field = level;
      int n = k;

      switch(level) {
      case TimeScale.YEAR:
         cal = new GregorianCalendar(2010, 0, 15, 12, 0, 0);
         break;
      case TimeScale.QUARTER:
         cal = new GregorianCalendar(2020, 0, 15, 12, 0, 0);
         field = Calendar.MONTH;
         n = 3 * k;
         break;
      case TimeScale.MONTH:
         cal = new GregorianCalendar(2020, 0, 15, 12, 0, 0);
         break;
      case TimeScale.WEEK:
         cal = new GregorianCalendar(2023, 2, 6, 12, 0, 0);
         field = Calendar.DATE;
         n = 7 * k;
         break;
      case TimeScale.DAY:
         cal = new GregorianCalendar(2023, 1, 25, 12, 0, 0);
         break;
      case TimeScale.HOUR:
         cal = new GregorianCalendar(2023, 6, 15, 18, 0, 0);
         break;
      case TimeScale.MINUTE:
         cal = new GregorianCalendar(2023, 6, 15, 10, 55, 0);
         break;
      default:
         cal = new GregorianCalendar(2023, 6, 15, 10, 0, 55);
         break;
      }

      cal.add(field, n);
      return cal.getTime();
   }

   @ParameterizedTest
   @ValueSource(ints = { TimeScale.YEAR, TimeScale.QUARTER, TimeScale.MONTH, TimeScale.WEEK,
                         TimeScale.DAY, TimeScale.HOUR, TimeScale.MINUTE, TimeScale.SECOND })
   @DisplayName("every period between the first and the last kept date appears exactly once")
   void everyPeriodAppearsOnceForRandomSubsets(int level) {
      for(int seed = 1; seed <= 20; seed++) {
         Random rnd = new Random(seed);
         List<Integer> idx = new ArrayList<>();

         for(int i = 0; i < 12; i++) {
            idx.add(i);
         }

         Collections.shuffle(idx, rnd);
         List<Integer> kept = new ArrayList<>(idx.subList(0, 2 + rnd.nextInt(11)));
         Collections.sort(kept);

         // the kept rows in a shuffled input order, to also cover the sort of the values
         List<Integer> shuffled = new ArrayList<>(kept);
         Collections.shuffle(shuffled, rnd);
         Object[][] rows = new Object[shuffled.size()][];

         for(int i = 0; i < rows.length; i++) {
            rows[i] = r(period(level, shuffled.get(i)), "A", 1 + i);
         }

         List<Object[]> out = run(tsr(level, 0), ds(rows));
         List<String> actual = new ArrayList<>();

         for(Integer k : kept) {
            actual.add(fmt(period(level, k), DTS));
         }

         for(Object[] row : out) {
            actual.add(fmt((Date) row[0], DTS));
         }

         Collections.sort(actual);
         List<String> expected = new ArrayList<>();

         for(int k = kept.get(0); k <= kept.get(kept.size() - 1); k++) {
            expected.add(fmt(period(level, k), DTS));
         }

         String msg = "level " + level + " seed " + seed + " kept " + kept;
         assertEquals(expected, actual, msg);
         assertEquals(expected.size() - kept.size(), out.size(), msg);
      }
   }

   @Test
   void consecutivePeriodsAddNothing() {
      assertEquals(List.of(), run(tsr(TimeScale.MONTH, 0), ds(months("A", 1, 2, 3))));
   }

   @Test
   void twoDatesInOnePeriodAddNothing() {
      DefaultDataSet d = ds(r(at(2023, 1, 5), "A", 1), r(at(2023, 1, 25), "A", 2));
      assertEquals(List.of(), run(tsr(TimeScale.MONTH, 0), d));
   }

   @Test
   void duplicateDatesAddTheGapOnce() {
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 1), r(at(2023, 1, 10), "A", 2),
                            r(at(2023, 3, 10), "A", 3));
      assertEquals(exp("2023-02-10|A|0"), render(run(tsr(TimeScale.MONTH, 0), d)));
   }

   @Test
   void inputOrderDoesNotChangeTheResult() {
      DefaultDataSet shuffled = ds(r(at(2023, 4, 10), "A", 4), r(at(2023, 1, 10), "A", 1),
                                   r(at(2023, 3, 10), "A", 3));
      DefaultDataSet sorted = ds(r(at(2023, 1, 10), "A", 1), r(at(2023, 3, 10), "A", 3),
                                 r(at(2023, 4, 10), "A", 4));
      List<String> a = render(run(tsr(TimeScale.MONTH, 0), shuffled));
      assertEquals(exp("2023-02-10|A|0"), a);
      assertEquals(render(run(tsr(TimeScale.MONTH, 0), sorted)), a);
   }

   static Stream<Arguments> dateClasses() {
      Function<Date, Object> util = d -> d;
      Function<Date, Object> ts = d -> new java.sql.Timestamp(d.getTime());
      Function<Date, Object> sqlDate = d -> new java.sql.Date(d.getTime());
      Function<Date, Object> time = d -> new java.sql.Time(d.getTime());
      return Stream.of(
         Arguments.of("util", util, TimeScale.DAY, Date.class),
         Arguments.of("timestamp", ts, TimeScale.DAY, java.sql.Timestamp.class),
         Arguments.of("sql date", sqlDate, TimeScale.DAY, java.sql.Date.class),
         Arguments.of("sql time", time, TimeScale.HOUR, java.sql.Time.class),
         Arguments.of("util hour", util, TimeScale.HOUR, Date.class));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("dateClasses")
   @DisplayName("an added date has the class of the input dates")
   void addedDatesKeepTheClassOfTheInput(String name, Function<Date, Object> conv, int level,
                                         Class<?> type)
   {
      Date first = at(2023, 3, 1, 10, 0);
      Calendar cal = new GregorianCalendar();
      cal.setTime(first);
      // three days or three hours apart, so there are gaps
      cal.add(level == TimeScale.DAY ? Calendar.DATE : Calendar.HOUR_OF_DAY, 3);
      DefaultDataSet d = ds(r(conv.apply(first), "A", 1), r(conv.apply(cal.getTime()), "A", 2));
      List<Object[]> out = run(tsr(level, 0), d);
      assertFalse(out.isEmpty());

      for(Object[] row : out) {
         assertSame(type, row[0].getClass());
      }
   }

   @Test
   void leapDayIsFilled() {
      DefaultDataSet d = ds(r(at(2024, 2, 28), "A", 1), r(at(2024, 3, 1), "A", 2));
      assertEquals(exp("2024-02-29|A|0"), render(run(tsr(TimeScale.DAY, 0), d)));
   }

   @Test
   void nonLeapYearHasNoFebruary29() {
      DefaultDataSet d = ds(r(at(2023, 2, 28), "A", 1), r(at(2023, 3, 1), "A", 2));
      assertEquals(List.of(), run(tsr(TimeScale.DAY, 0), d));
   }

   @Test
   void dayGapsAreFilledAcrossTheYearEnd() {
      DefaultDataSet d = ds(r(at(2022, 12, 30), "A", 1), r(at(2023, 1, 2), "A", 2));
      assertEquals(exp("2022-12-31|A|0", "2023-01-01|A|0"), render(run(tsr(TimeScale.DAY, 0), d)));
   }

   @Test
   void monthFromThe31stFillsOneMonth() {
      DefaultDataSet d = ds(r(at(2023, 1, 31), "A", 1), r(at(2023, 3, 31), "A", 2));
      assertEquals(exp("2023-02|A|0"), render(run(tsr(TimeScale.MONTH, 0), d), "yyyy-MM"));
   }

   @Test
   void monthFromThe31stFillsTwoMonths() {
      DefaultDataSet d = ds(r(at(2023, 1, 31), "A", 1), r(at(2023, 4, 30), "A", 2));
      assertEquals(exp("2023-02|A|0", "2023-03|A|0"),
                   render(run(tsr(TimeScale.MONTH, 0), d), "yyyy-MM"));
   }

   @Test
   void yearFromFebruary29FillsTheYearsBetween() {
      DefaultDataSet d = ds(r(at(2024, 2, 29), "A", 1), r(at(2027, 2, 28), "A", 2));
      assertEquals(exp("2025|A|0", "2026|A|0"), render(run(tsr(TimeScale.YEAR, 0), d), "yyyy"));
   }

   @Test
   void quarterOverTheYearEndFillsFirstQuarter() {
      DefaultDataSet d = ds(r(at(2021, 11, 15), "A", 1), r(at(2022, 5, 15), "A", 2));
      List<String> out = render(run(tsr(TimeScale.QUARTER, 0), d), "yyyy-MM");
      assertEquals(1, out.size());
      assertTrue(List.of("2022-01|A|0", "2022-02|A|0", "2022-03|A|0").contains(out.get(0)), out + "");
   }

   @Test
   void quarterFromQuarterEndFillsSecondQuarter() {
      DefaultDataSet d = ds(r(at(2021, 3, 31), "A", 1), r(at(2021, 7, 1), "A", 2));
      List<String> out = render(run(tsr(TimeScale.QUARTER, 0), d), "yyyy-MM");
      assertEquals(1, out.size());
      assertTrue(List.of("2021-04|A|0", "2021-05|A|0", "2021-06|A|0").contains(out.get(0)), out + "");
   }

   @ParameterizedTest
   @ValueSource(ints = { TimeScale.YEAR, TimeScale.QUARTER, TimeScale.MONTH, TimeScale.WEEK,
                         TimeScale.DAY, TimeScale.HOUR, TimeScale.MINUTE, TimeScale.SECOND })
   @DisplayName("a single row adds nothing at any level")
   void singleRowAddsNothing(int level) {
      assertEquals(List.of(), run(tsr(level, 0), ds(r(at(2023, 3, 10), "A", 1))));
   }

   @Test
   void quarterFillsLeadingAndTrailingPerGroup() {
      DefaultDataSet d = ds(r(at(2022, 7, 1), "A", 1), r(at(2022, 10, 1), "A", 2),
                            r(at(2022, 1, 1), "B", 3), r(at(2022, 4, 1), "B", 4));
      assertEquals(exp("2022-01-01|A|0", "2022-04-01|A|0", "2022-07-01|B|0", "2022-10-01|B|0"),
                   render(run(tsr(TimeScale.QUARTER, 0), d)));
   }

   @Test
   void trailingFillCoversSeveralPeriods() {
      DefaultDataSet d = ds(r(at(2023, 1, 5), "A", 1), r(at(2023, 1, 5), "B", 2),
                            r(at(2023, 3, 5), "B", 3));
      assertEquals(exp("2023-02-05|A|0", "2023-03-05|A|0", "2023-02-05|B|0"),
                   render(run(tsr(TimeScale.MONTH, 0), d)));
   }

   // ---------------------------------------------------------------- groups

   private static DefaultDataSet globalRangeData() {
      return ds(concat(months("A", 1, 2, 3, 4), months("B", 2, 3)));
   }

   @Test
   void globalRangeExtendsEveryGroupToTheDataSetRange() {
      assertEquals(exp("2023-01-10|B|0", "2023-04-10|B|0"),
                   render(run(tsr(TimeScale.MONTH, 0), globalRangeData())));
   }

   @Test
   void perGroupRangeAddsNothingWithoutMidGaps() {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      row.setGlobalRange(false);
      row.setOuterFields("g");
      assertEquals(List.of(), run(row, globalRangeData()));
   }

   private static DefaultDataSet outerData() {
      Object[] header = { "d", "r", "g", "v" };
      List<Object[]> rows = new ArrayList<>();

      for(int m : new int[]{ 1, 2, 3, 4 }) {
         rows.add(new Object[]{ at(2023, m, 10), "E", "A", m });
      }

      for(int m : new int[]{ 2, 3 }) {
         rows.add(new Object[]{ at(2023, m, 10), "E", "B", m });
      }

      for(int m : new int[]{ 3, 4 }) {
         rows.add(new Object[]{ at(2023, m, 10), "W", "C", m });
      }

      return build(header, rows.toArray(new Object[0][]));
   }

   private static TimeSeriesRow outerRow(boolean global) {
      TimeSeriesRow row = new TimeSeriesRow("d", TimeScale.MONTH, 0);
      row.setGroupFields("r", "g");
      row.setOuterFields("r");
      row.setGlobalRange(global);
      return row;
   }

   @Test
   void outerKeyLimitsTheRangeWhenNotGlobal() {
      assertEquals(exp("2023-01-10|E|B|0", "2023-04-10|E|B|0"),
                   render(run(outerRow(false), outerData())));
   }

   @Test
   void outerKeyIsIgnoredWhenGlobal() {
      assertEquals(exp("2023-01-10|E|B|0", "2023-04-10|E|B|0", "2023-01-10|W|C|0",
                       "2023-02-10|W|C|0"),
                   render(run(outerRow(true), outerData())));
   }

   @Test
   void groupWithOneValueIsFilledOnBothSides() {
      DefaultDataSet d = ds(concat(months("A", 1, 2, 3, 4), months("B", 2)));
      assertEquals(exp("2023-01-10|B|0", "2023-03-10|B|0", "2023-04-10|B|0"),
                   render(run(tsr(TimeScale.MONTH, 0), d)));
   }

   @Test
   void gapsAreIndependentPerGroup() {
      DefaultDataSet d = ds(concat(months("A", 1, 3), months("B", 2, 4)));
      assertEquals(exp("2023-02-10|A|0", "2023-04-10|A|0", "2023-01-10|B|0", "2023-03-10|B|0"),
                   render(run(tsr(TimeScale.MONTH, 0), d)));
   }

   @Test
   void midAndTrailingGapsTogether() {
      DefaultDataSet d = ds(concat(months("A", 1, 3), months("B", 1, 2)));
      assertEquals(exp("2023-02-10|A|0", "2023-03-10|B|0"),
                   render(run(tsr(TimeScale.MONTH, 0), d)));
   }

   @Test
   @DisplayName("a null group value is a group of its own and keeps the null in the added row")
   void nullGroupValueIsAGroup() {
      String control = "X";
      DefaultDataSet withNull = ds(r(at(2023, 1, 10), null, 1), r(at(2023, 3, 10), null, 2));
      assertEquals(exp("2023-02-10|null|0"), render(run(tsr(TimeScale.MONTH, 0), withNull)));
      DefaultDataSet named = ds(r(at(2023, 1, 10), control, 1), r(at(2023, 3, 10), control, 2));
      assertEquals(exp("2023-02-10|X|0"), render(run(tsr(TimeScale.MONTH, 0), named)));
   }

   @Test
   void calculateLeavesTheDataSetUnchanged() {
      DefaultDataSet d = globalRangeData();
      List<String> before = renderFrom(d, 0, DT);
      int rows = d.getRowCount();
      int cols = d.getColCount();
      run(tsr(TimeScale.MONTH, 0), d);
      assertEquals(rows, d.getRowCount());
      assertEquals(cols, d.getColCount());
      assertEquals(before, renderFrom(d, 0, DT));
   }

   // ---------------------------------------------------------------- null and empty

   @Test
   void rowsWithoutADateAreSkipped() {
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 1), r(null, "A", 2), r(at(2023, 3, 10), "A", 3));
      assertEquals(exp("2023-02-10|A|0"), render(run(tsr(TimeScale.MONTH, 0), d)));
   }

   @Test
   void allDatesNullAddsNothing() {
      DefaultDataSet d = ds(r(null, "A", 1), r(null, "A", 2));
      assertEquals(List.of(), run(tsr(TimeScale.MONTH, 0), d));
   }

   @Test
   void nonDateValueInTheDateColumnIsSkipped() {
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 1), r("n/a", "A", 2), r(at(2023, 3, 10), "A", 3));
      assertEquals(exp("2023-02-10|A|0"), render(run(tsr(TimeScale.MONTH, 0), d)));
   }

   @Test
   void emptyDataSetAddsNothing() {
      DefaultDataSet d = new DefaultDataSet(new Object[][]{ { "d", "g", "v" } });
      assertEquals(List.of(), run(tsr(TimeScale.MONTH, 0), d));
   }

   @Test
   void nullGroupAndOuterFieldsMeanOneGroup() {
      TimeSeriesRow row = new TimeSeriesRow("d", TimeScale.MONTH, 0);
      row.setGroupFields((String[]) null);
      row.setOuterFields((String[]) null);
      assertArrayEquals(new String[0], row.getGroupFields());
      assertArrayEquals(new String[0], row.getOuterFields());
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 1), r(at(2023, 3, 10), "A", 2));
      assertEquals(exp("2023-02-10|A|0"), render(run(row, d)));
   }

   @Test
   void defaultConstructorWithSettersMatchesTheFullConstructor() {
      TimeSeriesRow viaSetters = new TimeSeriesRow();
      viaSetters.setTimeColumn("d");
      viaSetters.setTimeType(TimeScale.MONTH);
      viaSetters.setGroupFields("g");
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 1), r(at(2023, 3, 10), "A", 2));
      List<String> expected = render(run(tsr(TimeScale.MONTH, null), d));
      assertEquals(exp("2023-02-10|A|null"), expected);
      assertEquals(expected, render(run(viaSetters, d)));
   }

   // ---------------------------------------------------------------- missing values

   private static DefaultDataSet ds2Gap() {
      return ds2(r(at(2023, 1, 10), "A", 10), r(at(2023, 4, 10), "A", 40));
   }

   @Test
   void missingValueZeroFillsEveryMeasure() {
      assertEquals(exp("2023-02-10|A|0|0", "2023-03-10|A|0|0"),
                   render(run(tsr(TimeScale.MONTH, 0), ds2Gap())));
   }

   @Test
   void missingValueOneIsTheBarFlagAndFillsNull() {
      assertEquals(exp("2023-02-10|A|null|null", "2023-03-10|A|null|null"),
                   render(run(tsr(TimeScale.MONTH, 1), ds2Gap())));
   }

   @Test
   void missingValueOtherNumberIsUsed() {
      assertEquals(exp("2023-02-10|A|2.5|2.5", "2023-03-10|A|2.5|2.5"),
                   render(run(tsr(TimeScale.MONTH, 2.5), ds2Gap())));
   }

   @Test
   void missingValueNullStillFillsMidGaps() {
      List<Object[]> out = run(tsr(TimeScale.MONTH, null), ds2Gap());
      assertEquals(exp("2023-02-10|A|null|null", "2023-03-10|A|null|null"), render(out));
      // same dates as with 0
      assertEquals(render(run(tsr(TimeScale.MONTH, 0), ds2Gap()), "yyyy-MM-dd").stream()
                      .map(s -> s.substring(0, 10)).toList(),
                   render(out).stream().map(s -> s.substring(0, 10)).toList());
   }

   @Test
   @DisplayName("pinned: missing value null does not extend the ranges at the ends")
   void missingValueNullDoesNotFillRangeEnds() {
      assertEquals(List.of(), run(tsr(TimeScale.MONTH, null), globalRangeData()));
      assertEquals(exp("2023-01-10|B|0", "2023-04-10|B|0"),
                   render(run(tsr(TimeScale.MONTH, 0), globalRangeData())));
   }

   @Test
   void perMeasureValueBeatsTheGlobalValue() {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      row.setMissingValue("w", 9);
      assertEquals(9, row.getMissingValue("w"));
      assertNull(row.getMissingValue("v"));
      assertEquals(exp("2023-02-10|A|0|9", "2023-03-10|A|0|9"), render(run(row, ds2Gap())));
   }

   @Test
   void explicitNullPerMeasureValueIsKept() {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      row.setMissingValue("w", null);
      assertEquals(exp("2023-02-10|A|0|null", "2023-03-10|A|0|null"), render(run(row, ds2Gap())));
   }

   @Test
   void perMeasureValueBeatsTheBarFlag() {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 1);
      row.setMissingValue("v", 7);
      assertEquals(exp("2023-02-10|A|7|null", "2023-03-10|A|7|null"), render(run(row, ds2Gap())));
   }

   @Test
   @DisplayName("a null measure in the later neighbour stays null in the gap rows")
   void laterNeighbourNullKeepsGapNull() {
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 10), r(at(2023, 4, 10), "A", null));
      assertEquals(exp("2023-02-10|A|null", "2023-03-10|A|null"),
                   render(run(tsr(TimeScale.MONTH, 0), d)));
   }

   @Test
   @DisplayName("pinned: a null measure in the earlier neighbour does not suppress the 0 fill")
   void earlierNeighbourNullStillFillsZero() {
      DefaultDataSet d = ds(r(at(2023, 4, 10), "A", 40), r(at(2023, 1, 10), "A", null));
      assertEquals(exp("2023-02-10|A|0", "2023-03-10|A|0"),
                   render(run(tsr(TimeScale.MONTH, 0), d)));
   }

   @Test
   void isNoNullDependsOnTheMissingValue() {
      assertTrue(tsr(TimeScale.MONTH, 0).isNoNull());
      assertTrue(tsr(TimeScale.MONTH, 2.5).isNoNull());
      assertFalse(tsr(TimeScale.MONTH, null).isNoNull());
      assertFalse(tsr(TimeScale.MONTH, 1).isNoNull());
   }

   static Stream<Object> numbers() {
      return Stream.of(0, 1);
   }

   @ParameterizedTest
   @MethodSource("numbers")
   @DisplayName("isNoNull agrees with the value of a filled row for Integer 0 and 1")
   void isNoNullMatchesTheFilledValueForIntegers(Object missing) {
      TimeSeriesRow row = tsr(TimeScale.MONTH, missing);
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 1), r(at(2023, 3, 10), "A", 2));
      Object v = run(row, d).get(0)[2];
      assertEquals(v != null, row.isNoNull());
   }

   // ---------------------------------------------------------------- measure checker

   private static DefaultDataSet ds2NullLater() {
      return ds2(r(at(2023, 1, 10), "A", 10), r(at(2023, 4, 10), "A", null));
   }

   @Test
   void measureCheckerTrueOverridesTheNullReference() {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      row.setMeasureChecker((data, r, measure) -> true);
      assertEquals(exp("2023-02-10|A|0|0", "2023-03-10|A|0|0"), render(run(row, ds2NullLater())));
   }

   @Test
   void measureCheckerDecidesPerMeasure() {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      row.setMeasureChecker((data, r, measure) -> "v".equals(measure));
      assertEquals(exp("2023-02-10|A|0|null", "2023-03-10|A|0|null"),
                   render(run(row, ds2NullLater())));
   }

   @Test
   void measureCheckerIsCalledWithInputRowsOfTheSameGroup() {
      List<Object[]> calls = new ArrayList<>();
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      row.setMeasureChecker((data, r, measure) -> {
         calls.add(new Object[]{ data, r, measure });
         return true;
      });
      DefaultDataSet d = ds2(concat(months("A", 1, 4), months("B", 2, 3)));
      List<Object[]> out = run(row, d);
      assertEquals(4, out.size());
      assertEquals(8, calls.size());

      // each added row asks for v and then w, in the order the rows were created
      for(int k = 0; k < out.size(); k++) {
         for(int j = 0; j < 2; j++) {
            Object[] call = calls.get(2 * k + j);
            assertSame(d, call[0]);
            assertEquals(j == 0 ? "v" : "w", call[2]);
            int r = (Integer) call[1];
            assertTrue(r >= 0 && r < 4, "input row index " + r);
            assertEquals(out.get(k)[1], d.getData("g", r));
         }
      }
   }

   @Test
   void isFillTimeGapOverrideSuppressesAMeasure() {
      TimeSeriesRow row = new TimeSeriesRow("d", TimeScale.MONTH, 0) {
         @Override
         protected boolean isFillTimeGap(String dim) {
            return !"w".equals(dim);
         }
      };
      row.setGroupFields("g");
      assertEquals(exp("2023-02-10|A|0|null", "2023-03-10|A|0|null"), render(run(row, ds2Gap())));
   }

   // ---------------------------------------------------------------- maxGaps

   @Test
   @Disabled("Bug #82095 (dead code): maxGaps 3 with 5 missing months: expected at most 3 added rows, actual 4 "
      + "(the row is added before the limit is checked)")
   @DisplayName("maxGaps is a strict limit on the number of added rows in the mid loop")
   void maxGapsLimitsAddedRowsStrictly() {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      row.setMaxGaps(3);
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 1), r(at(2023, 7, 10), "A", 2));
      assertTrue(run(row, d).size() <= 3);
   }

   @Test
   void maxGapsHasAnEffect() {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      row.setMaxGaps(3);
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 1), r(at(2024, 1, 10), "A", 2));
      int n = run(row, d).size();
      assertTrue(n >= 1 && n < 11, "rows " + n);
   }

   @Test
   void maxGapsEqualToTheGapsAddsAll() {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      row.setMaxGaps(2);
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 1), r(at(2023, 4, 10), "A", 2));
      assertEquals(exp("2023-02-10|A|0", "2023-03-10|A|0"), render(run(row, d)));
   }

   @Test
   @Disabled("Bug #82095 (dead code): maxGaps 2 with leading and trailing gaps: expected at most 2 added rows, actual 11 "
      + "(the leading and trailing loops do not check the limit)")
   @DisplayName("maxGaps also limits the rows added at the range ends")
   void maxGapsLimitsRangeEnds() {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      row.setMaxGaps(2);
      DefaultDataSet d = ds(concat(months("A", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
                                   months("B", 6)));
      assertTrue(run(row, d).size() <= 2);
   }

   // ---------------------------------------------------------------- accessors

   @Test
   @DisplayName("pinned: defaults and setter round trip")
   void defaultsAndSetters() {
      TimeSeriesRow row = new TimeSeriesRow();
      assertEquals(2000, row.getMaxGaps());
      assertTrue(row.isGlobalRange());
      assertFalse(row.isPreColumn());
      assertArrayEquals(new String[0], row.getGroupFields());
      assertArrayEquals(new String[0], row.getOuterFields());

      row.setTimeColumn("t");
      row.setTimeType(TimeScale.WEEK);
      row.setMaxGaps(5);
      row.setGlobalRange(false);
      row.setPreColumn(true);
      row.setGroupFields("a", "b");
      row.setOuterFields("a");
      assertEquals("t", row.getTimeColumn());
      assertEquals(TimeScale.WEEK, row.getTimeType());
      assertEquals(5, row.getMaxGaps());
      assertFalse(row.isGlobalRange());
      assertTrue(row.isPreColumn());
      assertArrayEquals(new String[]{ "a", "b" }, row.getGroupFields());
      assertArrayEquals(new String[]{ "a" }, row.getOuterFields());
   }

   @Test
   void comparatorIsNullForEveryColumn() {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      assertNull(row.getComparator("d"));
      assertNull(row.getComparator("v"));
   }

   @Test
   void serializedCopyKeepsSettingsAndResult() throws Exception {
      TimeSeriesRow row = tsr(TimeScale.MONTH, 0);
      row.setOuterFields("g");
      row.setMissingValue("v", 3);
      row.setMaxGaps(7);
      row.setGlobalRange(false);
      row.setPreColumn(true);

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(row);
      }

      TimeSeriesRow copy;

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         copy = (TimeSeriesRow) in.readObject();
      }

      assertEquals("d", copy.getTimeColumn());
      assertEquals(TimeScale.MONTH, copy.getTimeType());
      assertArrayEquals(row.getGroupFields(), copy.getGroupFields());
      assertArrayEquals(row.getOuterFields(), copy.getOuterFields());
      assertEquals(3, copy.getMissingValue("v"));
      assertEquals(7, copy.getMaxGaps());
      assertFalse(copy.isGlobalRange());
      assertTrue(copy.isPreColumn());

      DefaultDataSet d = ds(concat(months("A", 1, 3), months("B", 2, 4)));
      List<String> expected = render(run(row, d));
      assertEquals(exp("2023-02-10|A|3", "2023-03-10|B|3"), expected);
      assertEquals(expected, render(run(copy, d)));
   }
}

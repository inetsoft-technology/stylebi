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
package inetsoft.uql.asset;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The date group levels of {@code DateRangeRef} on each database: which levels are pushed to
 * SQL, and that the SQL of a level is right.
 * <p>
 * A level is pushed to SQL only when the database has the function for it
 * ({@code sqlhelper.xml}, {@code DateRangeRef.getExpression}); otherwise the group is computed in
 * memory. The table of the pushed levels per database is the current capability of the product,
 * it is not read from the database manuals. The SQL of Derby is run on the embedded Derby and
 * compared with the calendar arithmetic of {@code java.time}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateRangeRefDialectTest {
   private static final String URL =
      "jdbc:derby:memory:daterangedialect_" + UUID.randomUUID().toString().replace("-", "");

   /** The levels that have a SQL function name: level name to option. */
   private static final Map<String, Integer> LEVELS = new LinkedHashMap<>();

   static {
      LEVELS.put("year", DateRangeRef.YEAR_INTERVAL);
      LEVELS.put("quarter", DateRangeRef.QUARTER_INTERVAL);
      LEVELS.put("month", DateRangeRef.MONTH_INTERVAL);
      LEVELS.put("week", DateRangeRef.WEEK_INTERVAL);
      LEVELS.put("day", DateRangeRef.DAY_INTERVAL);
      LEVELS.put("hour", DateRangeRef.HOUR_INTERVAL);
      LEVELS.put("minute", DateRangeRef.MINUTE_INTERVAL);
      LEVELS.put("second", DateRangeRef.SECOND_INTERVAL);
      LEVELS.put("quarter of year", DateRangeRef.QUARTER_OF_YEAR_PART);
      LEVELS.put("month of year", DateRangeRef.MONTH_OF_YEAR_PART);
      LEVELS.put("week of year", DateRangeRef.WEEK_OF_YEAR_PART);
      LEVELS.put("day of month", DateRangeRef.DAY_OF_MONTH_PART);
      LEVELS.put("day of week", DateRangeRef.DAY_OF_WEEK_PART);
      LEVELS.put("hour of day", DateRangeRef.HOUR_OF_DAY_PART);
      LEVELS.put("minute of hour", DateRangeRef.MINUTE_OF_HOUR_PART);
      LEVELS.put("second of minute", DateRangeRef.SECOND_OF_MINUTE_PART);
   }

   private static Set<String> all() {
      return new TreeSet<>(LEVELS.keySet());
   }

   private static Set<String> allBut(String... levels) {
      Set<String> set = all();
      set.removeAll(Arrays.asList(levels));
      return set;
   }

   private static Set<String> only(String... levels) {
      return new TreeSet<>(Arrays.asList(levels));
   }

   /** Database type and the levels it pushes to SQL. */
   static Stream<Object[]> pushedLevels() {
      return Stream.of(
         new Object[] { "oracle", all() },
         new Object[] { "mysql", all() },
         new Object[] { "sql server", all() },
         new Object[] { "postgresql", all() },
         // DB2 has second() but the product only defines the part of a time column
         new Object[] { "db2", allBut("second of minute") },
         new Object[] { "informix", only("year", "quarter", "month", "day", "quarter of year",
                                         "month of year", "day of month", "day of week") },
         new Object[] { "derby", only("year", "quarter", "month", "day", "quarter of year",
                                      "month of year", "day of month", "hour of day") },
         // no date function is defined: every date group is computed in memory
         new Object[] { "clickhouse", only() },
         new Object[] { "h2", only() });
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("pushedLevels")
   void levelsPushedToSql(String database, Set<String> expected) {
      Set<String> actual = new TreeSet<>();

      for(Map.Entry<String, Integer> level : LEVELS.entrySet()) {
         if(DateRangeRef.isMergeable(database, level.getValue())) {
            actual.add(level.getKey());
         }
      }

      assertEquals(expected, actual, database);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("pushedLevels")
   void aLevelIsPushedExactlyWhenItHasAnExpression(String database, Set<String> expected) {
      AttributeRef ref = new AttributeRef(null, "c");

      for(Map.Entry<String, Integer> level : LEVELS.entrySet()) {
         String expression = DateRangeRef.getExpression(database, ref, level.getValue());
         boolean pushed = DateRangeRef.isMergeable(database, level.getValue());
         assertEquals(pushed, !expression.isEmpty(), database + " " + level.getKey());

         if(pushed) {
            assertTrue(expression.contains("field['c']"),
                       database + " " + level.getKey() + ": " + expression);
         }
      }
   }

   /** The levels the SQL translation does not support at all (DateRangeRef.getExpression). */
   static Stream<Integer> levelsWithoutSql() {
      return Stream.of(
         DateRangeRef.DAY_OF_YEAR_PART, DateRangeRef.DAY_OF_QUARTER_PART,
         DateRangeRef.WEEK_OF_MONTH_PART, DateRangeRef.WEEK_OF_QUARTER_PART,
         DateRangeRef.MONTH_OF_QUARTER_PART, DateRangeRef.MONTH_OF_QUARTER_FULL_WEEK_PART,
         DateRangeRef.MONTH_OF_FULL_WEEK, DateRangeRef.QUARTER_OF_FULL_WEEK_PART,
         DateRangeRef.QUARTER_OF_FULL_WEEK, DateRangeRef.YEAR_OF_FULL_WEEK,
         DateRangeRef.MONTH_OF_FULL_WEEK_PART);
   }

   @ParameterizedTest(name = "option {0}")
   @MethodSource("levelsWithoutSql")
   void levelsWithoutSqlAreNotPushedOnAnyDatabase(int option) {
      for(Object[] row : (Iterable<Object[]>) pushedLevels()::iterator) {
         String database = (String) row[0];
         assertFalse(DateRangeRef.isMergeable(database, option), database);

         // the translation refuses them, isMergeable turns that into false
         RuntimeException ex = assertThrows(RuntimeException.class, () ->
            DateRangeRef.getExpression(database, new AttributeRef(null, "c"), option), database);
         assertTrue(ex.getMessage().contains("not supported in SQL"), ex.getMessage());
      }
   }

   @Test
   void noGroupIsTheColumnItself() {
      for(Object[] row : (Iterable<Object[]>) pushedLevels()::iterator) {
         String database = (String) row[0];
         String expression = DateRangeRef.getExpression(
            database, new AttributeRef(null, "c"), DateRangeRef.NONE_INTERVAL);

         // ClickHouse and H2 define no date function at all, not even "none", so a column
         // without a date group is not pushed there either; that looks like an omission
         String expected = "clickhouse".equals(database) || "h2".equals(database) ?
            "" : "field['c']";
         assertEquals(expected, expression, database);
         assertEquals(!expected.isEmpty(),
                      DateRangeRef.isMergeable(database, DateRangeRef.NONE_INTERVAL), database);
      }
   }

   // ----------------------------------------------------------------------------------------
   // the SQL of Derby, run on the embedded Derby

   private static final LocalDateTime[] STAMPS = {
      LocalDateTime.of(2026, 3, 15, 10, 20, 30),
      // a leap day, and the last second of a year
      LocalDateTime.of(2024, 2, 29, 23, 59, 59),
      LocalDateTime.of(1999, 12, 31, 23, 59, 59),
      LocalDateTime.of(2000, 1, 1, 0, 0, 0),
      // the end of a quarter, and the start of the next
      LocalDateTime.of(2026, 3, 31, 12, 0, 0),
      LocalDateTime.of(2026, 4, 1, 0, 0, 0),
      LocalDateTime.of(2026, 6, 30, 23, 0, 0),
      LocalDateTime.of(2026, 7, 1, 1, 0, 0),
      // months with one and with two digits
      LocalDateTime.of(2026, 9, 30, 18, 5, 0),
      LocalDateTime.of(2026, 10, 1, 6, 30, 0),
      LocalDateTime.of(2026, 11, 9, 9, 9, 9),
      LocalDateTime.of(2026, 12, 31, 23, 59, 59),
      // single digit day and hour
      LocalDateTime.of(2026, 1, 2, 3, 4, 5)
   };

   @BeforeAll
   static void createStamps() throws SQLException {
      try(Connection conn = DriverManager.getConnection(URL + ";create=true");
          Statement st = conn.createStatement())
      {
         st.execute("create table stamps (id int primary key, c timestamp)");

         try(PreparedStatement ps = conn.prepareStatement("insert into stamps values (?, ?)")) {
            for(int i = 0; i < STAMPS.length; i++) {
               ps.setInt(1, i + 1);
               ps.setTimestamp(2, Timestamp.valueOf(STAMPS[i]));
               ps.executeUpdate();
            }

            // a null date
            ps.setInt(1, 0);
            ps.setNull(2, java.sql.Types.TIMESTAMP);
            ps.executeUpdate();
         }
      }
   }

   @AfterAll
   static void dropStamps() {
      try {
         DriverManager.getConnection(URL + ";drop=true").close();
      }
      catch(SQLException ex) {
         // Derby reports the drop of a database as an error
      }
   }

   private static LocalDateTime startOfQuarter(LocalDateTime t) {
      return LocalDateTime.of(t.getYear(), (t.getMonthValue() - 1) / 3 * 3 + 1, 1, 0, 0);
   }

   /** Level, the calendar arithmetic that is expected, and whether the result is a timestamp. */
   private static Stream<Object[]> derbyLevels() {
      Function<LocalDateTime, Object> year = t -> LocalDateTime.of(t.getYear(), 1, 1, 0, 0);
      Function<LocalDateTime, Object> quarter = DateRangeRefDialectTest::startOfQuarter;
      Function<LocalDateTime, Object> month =
         t -> LocalDateTime.of(t.getYear(), t.getMonthValue(), 1, 0, 0);
      Function<LocalDateTime, Object> day = t -> t.toLocalDate().atStartOfDay();
      Function<LocalDateTime, Object> quarterOfYear = t -> (t.getMonthValue() - 1) / 3 + 1;
      Function<LocalDateTime, Object> monthOfYear = LocalDateTime::getMonthValue;
      Function<LocalDateTime, Object> dayOfMonth = LocalDateTime::getDayOfMonth;
      Function<LocalDateTime, Object> hourOfDay = LocalDateTime::getHour;

      return Stream.of(
         new Object[] { "year", year, true },
         new Object[] { "quarter", quarter, true },
         new Object[] { "month", month, true },
         new Object[] { "day", day, true },
         new Object[] { "quarter of year", quarterOfYear, false },
         new Object[] { "month of year", monthOfYear, false },
         new Object[] { "day of month", dayOfMonth, false },
         new Object[] { "hour of day", hourOfDay, false });
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("derbyLevels")
   @SuppressWarnings("unchecked")
   void derbySqlGivesTheCalendarValue(String level, Function<LocalDateTime, Object> expected,
                                      boolean timestamp) throws SQLException
   {
      String expression = DateRangeRef.getExpression(
         "derby", new AttributeRef(null, "c"), LEVELS.get(level)).replace("field['c']", "c");
      List<String> problems = new ArrayList<>();

      try(Connection conn = DriverManager.getConnection(URL);
          Statement st = conn.createStatement();
          ResultSet rs = st.executeQuery("select id, c, " + expression + " from stamps order by id"))
      {
         while(rs.next()) {
            int id = rs.getInt(1);
            Object actual = timestamp ? timestampOrNull(rs) : intOrNull(rs);

            if(id == 0) {
               // a null date: a truncation is null, the hour of day is defined as 0
               Object nullValue = "hour of day".equals(level) ? Integer.valueOf(0) : null;

               if(!java.util.Objects.equals(nullValue, actual)) {
                  problems.add("null date -> " + actual + ", expected " + nullValue);
               }

               continue;
            }

            LocalDateTime input = STAMPS[id - 1];
            Object want = expected.apply(input);

            if(!java.util.Objects.equals(want, actual)) {
               problems.add(input + " -> " + actual + ", expected " + want);
            }
         }
      }

      assertTrue(problems.isEmpty(), level + ": " + problems + "\nSQL: " + expression);
   }

   private static Object timestampOrNull(ResultSet rs) throws SQLException {
      Timestamp value = rs.getTimestamp(3);
      return value == null ? null : value.toLocalDateTime();
   }

   private static Object intOrNull(ResultSet rs) throws SQLException {
      int value = rs.getInt(3);
      return rs.wasNull() ? null : value;
   }
}

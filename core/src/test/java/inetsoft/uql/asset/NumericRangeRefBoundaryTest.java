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

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.PluginsTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which segment of a numeric range group a value falls into, at the boundaries. The segments are
 * the values 10, 20, 30 with a bottom and a top segment; the expected label of every probe value
 * is written out by hand for each of the three inclusion modes. The SQL expression is run on
 * embedded Derby and the script expression on the worksheet formula engine, and both are
 * compared with the same table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class NumericRangeRefBoundaryTest {
   private static final String URL = "jdbc:derby:memory:numericrange";
   private static final String OTHERS = "Others";
   /** the probe values, null is the last */
   private static final Double[] PROBES = { 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, null };

   @BeforeAll
   static void createDatabase() throws SQLException {
      DriverManager.getConnection(URL + ";create=true").close();
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection(URL + ";drop=true").close();
      }
      catch(SQLException ex) {
         // Derby reports the drop of a database as an error
      }
   }

   private static NumericRangeRef range(InclusiveType type, boolean bottom, boolean top,
                                        double... values)
   {
      ValueRangeInfo info = new ValueRangeInfo();
      info.setValues(values);
      info.setLabels(new String[0]);
      info.setShowBottomValue(bottom);
      info.setShowTopValue(top);
      info.setInclusiveType(type);
      NumericRangeRef ref = new NumericRangeRef("range", new AttributeRef("x"));
      ref.setDBType("derby");
      ref.setValueRangeInfo(info);
      return ref;
   }

   /** The label of each probe value from the SQL expression. */
   private static List<String> bySql(NumericRangeRef ref) throws SQLException {
      String expression = ref.getExpression().replace("field['x']", "x");
      List<String> labels = new ArrayList<>();

      try(Connection conn = DriverManager.getConnection(URL);
          Statement st = conn.createStatement())
      {
         st.execute("create table probe (id int, x double)");

         try {
            try(PreparedStatement ps = conn.prepareStatement("insert into probe values (?, ?)")) {
               for(int i = 0; i < PROBES.length; i++) {
                  ps.setInt(1, i);

                  if(PROBES[i] == null) {
                     ps.setNull(2, Types.DOUBLE);
                  }
                  else {
                     ps.setDouble(2, PROBES[i]);
                  }

                  ps.executeUpdate();
               }
            }

            try(ResultSet rs = st.executeQuery(
               "select " + expression + " from probe order by id"))
            {
               while(rs.next()) {
                  labels.add(rs.getString(1));
               }
            }
         }
         finally {
            st.execute("drop table probe");
         }
      }

      return labels;
   }

   /** The label of each probe value from the script expression. */
   private static List<String> byScript(NumericRangeRef ref) {
      ScriptEnv env = new GraalJavaScriptEnv();
      Object[][] data = new Object[PROBES.length + 1][];
      data[0] = new Object[] { "x" };

      for(int i = 0; i < PROBES.length; i++) {
         data[i + 1] = new Object[] { PROBES[i] };
      }

      FormulaTableLens lens = new FormulaTableLens(new DefaultTableLens(data),
                                                   new String[] { "range" },
                                                   new String[] { ref.getScriptExpression() },
                                                   env, null);
      List<String> labels = new ArrayList<>();

      for(int r = 1; r <= PROBES.length; r++) {
         Object value = lens.getObject(r, 1);
         labels.add(value == null ? null : value.toString());
      }

      return labels;
   }

   private static void check(NumericRangeRef ref, List<String> expected) throws SQLException {
      assertEquals(expected, bySql(ref), "SQL");
      assertEquals(expected, byScript(ref), "script");
   }

   // the default of a range group: a segment includes its lower end, so 10 is in 10-20, and the
   // top segment starts at the last value
   @Test
   void lowerEndIsInTheSegment() throws Exception {
      check(range(InclusiveType.LOWER, true, true, 10, 20, 30), List.of(
         "<10", "10-20", "10-20", "20-30", "20-30", ">=30", ">=30", OTHERS));
   }

   // a segment includes its upper end, so 10 is in the bottom segment
   @Test
   void upperEndIsInTheSegment() throws Exception {
      check(range(InclusiveType.UPPER, true, true, 10, 20, 30), List.of(
         "<=10", "<=10", "10-20", "10-20", "20-30", "20-30", ">30", OTHERS));
   }

   // both ends are in the segment, a value on a shared end goes to the first segment, and the
   // bottom and top segments hold the values beyond the ends
   @Test
   void bothEndsAreInTheSegmentAndTheFirstOneWins() throws Exception {
      check(range(InclusiveType.NONE, true, true, 10, 20, 30), List.of(
         "<10", "10-20", "10-20", "10-20", "20-30", "20-30", ">30", OTHERS));
   }

   // without the bottom and top segments the values outside of the ends are "Others"
   @Test
   void valuesOutsideTheEndsAreOthersWithoutBottomAndTopSegments() throws Exception {
      check(range(InclusiveType.LOWER, false, false, 10, 20, 30), List.of(
         OTHERS, "10-20", "10-20", "20-30", "20-30", OTHERS, OTHERS, OTHERS));
   }

   // a null is never in a segment. The top segment of the script has no null test, and in
   // JavaScript null >= 0 is true: when the last value is 0 or less and the lower end is
   // included, a null falls into the top segment of the script but into "Others" in SQL.
   @Disabled("Bug #81105: the top segment of the script expression has no null test")
   @Test
   void nullIsOthersWhenTheLastValueIsNotPositive() throws Exception {
      NumericRangeRef ref = range(InclusiveType.LOWER, true, true, -20, -10, 0);
      Double[] probes = PROBES;
      assertEquals(probes.length, bySql(ref).size());
      assertEquals(OTHERS, bySql(ref).get(probes.length - 1), "SQL");
      assertEquals(OTHERS, byScript(ref).get(probes.length - 1), "script");
   }
}

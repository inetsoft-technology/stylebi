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
import inetsoft.uql.schema.XSchema;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The custom (named) groups of a column, {@link NamedRangeRef}: which group a value falls into.
 * A group is a name and a list of values, a value that is in no group keeps its own value, and a
 * null can be a member of a group. The SQL expression is run on embedded Derby and the script
 * expression on the worksheet formula engine, and the two are compared with the same values and
 * with the labels written out by hand.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class NamedRangeRefGroupsTest {
   private static final String URL =
      "jdbc:derby:memory:namedrange_" + UUID.randomUUID().toString().replace("-", "");

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

   /** The groups, each a name and its values (a null is the null group member). */
   private static NamedRangeRef groups(String type, Object... nameAndValues) {
      AttributeRef column = new AttributeRef("x");
      column.setDataType(type);
      SNamedGroupInfo info = new SNamedGroupInfo();
      info.setDataRef(column);

      for(int i = 0; i < nameAndValues.length; i += 2) {
         info.setGroupValue((String) nameAndValues[i], (List) nameAndValues[i + 1]);
      }

      NamedRangeRef ref = new NamedRangeRef("grouped", column);
      ref.setDBType("derby");
      ref.setDataType(XSchema.STRING);
      ref.setBaseDataType(type);
      ref.setNamedGroupInfo(info);
      return ref;
   }

   private static List<String> values(String... values) {
      return Arrays.asList(values);
   }

   /** The label of each value from the SQL expression. */
   private static List<String> bySql(NamedRangeRef ref, String sqlType, Object... probes)
      throws SQLException
   {
      String expression = ref.getExpression().replace("field['x']", "x");
      List<String> labels = new ArrayList<>();

      try(Connection conn = DriverManager.getConnection(URL);
          Statement st = conn.createStatement())
      {
         st.execute("create table probe (id int, x " + sqlType + ")");

         try {
            try(PreparedStatement ps = conn.prepareStatement("insert into probe values (?, ?)")) {
               for(int i = 0; i < probes.length; i++) {
                  ps.setInt(1, i);
                  ps.setObject(2, probes[i], probes[i] instanceof Integer ?
                     java.sql.Types.INTEGER : java.sql.Types.VARCHAR);
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

   /** The label of each value from the script expression. */
   private static List<String> byScript(NamedRangeRef ref, Object... probes) {
      ScriptEnv env = new GraalJavaScriptEnv();
      Object[][] data = new Object[probes.length + 1][];
      data[0] = new Object[] { "x" };

      for(int i = 0; i < probes.length; i++) {
         data[i + 1] = new Object[] { probes[i] };
      }

      FormulaTableLens lens = new FormulaTableLens(new DefaultTableLens(data),
                                                   new String[] { "grouped" },
                                                   new String[] { ref.getScriptExpression() },
                                                   env, null);
      List<String> labels = new ArrayList<>();

      for(int r = 1; r <= probes.length; r++) {
         Object value = lens.getObject(r, 1);
         labels.add(value == null ? null : value.toString());
      }

      return labels;
   }

   private static void check(NamedRangeRef ref, String sqlType, List<String> expected,
                             Object... probes) throws SQLException
   {
      assertEquals(expected, bySql(ref, sqlType, probes), "SQL");
      assertEquals(expected, byScript(ref, probes), "script");
   }

   private static final String TEXT = "varchar(30)";

   @Test
   void valueIsLabeledWithItsGroupAndOtherValuesKeepTheirOwn() throws Exception {
      NamedRangeRef ref = groups(XSchema.STRING,
                                 "Fruit", values("apple", "pear"),
                                 "Veg", values("leek"));

      check(ref, TEXT, values("Fruit", "Fruit", "Veg", "stone", "Apple"),
            "apple", "pear", "leek", "stone", "Apple");
   }

   // a value that is in two groups goes to the first group
   @Test
   void valueOfTwoGroupsGoesToTheFirstGroup() throws Exception {
      NamedRangeRef ref = groups(XSchema.STRING,
                                 "One", values("a", "b"),
                                 "Two", values("b", "c"));

      check(ref, TEXT, values("One", "One", "Two", "d"), "a", "b", "c", "d");
   }

   @Test
   void nullGroupMemberLabelsTheNulls() throws Exception {
      NamedRangeRef ref = groups(XSchema.STRING,
                                 "Missing or A", Arrays.asList(null, "a"),
                                 "B", values("b"));

      check(ref, TEXT, Arrays.asList("Missing or A", "Missing or A", "B", "z", "Missing or A"),
            null, "a", "b", "z", null);
   }

   // a group whose only member is a null: the script labels the nulls
   @Test
   void groupOfOnlyNullInScript() {
      NamedRangeRef ref = groups(XSchema.STRING, "Missing", Arrays.asList((Object) null),
                                 "B", values("b"));

      assertEquals(Arrays.asList("Missing", "B", "z"), byScript(ref, null, "b", "z"));
   }

   // The SQL of a group whose only member is a null ends with an empty list, "WHEN x IN ()",
   // which is a syntax error. Not reported yet.
   @Test
   @Disabled("a group of only a null gives 'IN ()' in the SQL expression, not reported yet")
   void groupOfOnlyNullInSql() throws Exception {
      NamedRangeRef ref = groups(XSchema.STRING, "Missing", Arrays.asList((Object) null),
                                 "B", values("b"));

      assertEquals(Arrays.asList("Missing", "B", "z"), bySql(ref, TEXT, null, "b", "z"));
   }

   @Test
   void numbersAreLabeledByTheirGroup() throws Exception {
      NamedRangeRef ref = groups(XSchema.INTEGER,
                                 "Small", Arrays.asList("1", "2"),
                                 "Big", Arrays.asList("100"));

      check(ref, "integer", values("Small", "Small", "Big"), 1, 2, 100);
   }

   // a quote in a value or in a group name is escaped by the script
   @Test
   void quoteInAValueOrInAGroupNameInScript() {
      NamedRangeRef ref = groups(XSchema.STRING,
                                 "O'Brien family", values("O'Brien", "O'Neil"));

      assertEquals(values("O'Brien family", "O'Brien family", "Smith"),
                   byScript(ref, "O'Brien", "O'Neil", "Smith"));
   }

   // The SQL puts the values and the group name between quotes as they are, so a quote ends the
   // string: the expression is a syntax error. Not reported yet.
   @Test
   @Disabled("a quote in a value or group name is not escaped in the SQL expression, not reported")
   void quoteInAValueOrInAGroupNameInSql() throws Exception {
      NamedRangeRef ref = groups(XSchema.STRING,
                                 "O'Brien family", values("O'Brien", "O'Neil"));

      assertEquals(values("O'Brien family", "O'Brien family", "Smith"),
                   bySql(ref, TEXT, "O'Brien", "O'Neil", "Smith"));
   }

   // ---- what the expressions are without groups, and where the groups are mergeable ----

   @Test
   void expressionWithoutGroupsIsZero() {
      NamedRangeRef ref = groups(XSchema.STRING);

      assertEquals("0", ref.getExpression());
      assertEquals("0", ref.getScriptExpression());
   }

   @Test
   void isNotMergeableOnDerbyAndAccessOnly() {
      NamedRangeRef ref = groups(XSchema.STRING, "A", values("a"));

      for(String type : new String[] { "derby", "access" }) {
         ref.setDBType(type);
         assertFalse(ref.isMergeable(), type);
      }

      for(String type : new String[] { "", "oracle", "mysql", "sql server", "db2" }) {
         ref.setDBType(type);
         assertTrue(ref.isMergeable(), type);
      }
   }

   @Test
   void baseNameStripsTheGroupTag() {
      assertEquals("city", NamedRangeRef.getBaseName(NamedRangeRef.getName("city",
         NamedRangeRef.DATA_GROUP)));
      assertEquals("city", NamedRangeRef.getBaseName("ColorGroup(city)"));
      assertEquals("city", NamedRangeRef.getBaseName("SizeGroup(city)", "t"));
      assertEquals("t.city", NamedRangeRef.getBaseName("t.TextGroup(city)", "t"));
      assertEquals("Group(city)", NamedRangeRef.getBaseName("Group(city)"));
      assertEquals("DataGroup", NamedRangeRef.getBaseName("DataGroup"));
      assertNull(NamedRangeRef.getBaseName(null));
   }

   @Test
   void nameOfAnUnknownOptionIsRejected() {
      assertThrows(RuntimeException.class, () -> NamedRangeRef.getName("city", 9));
   }
}

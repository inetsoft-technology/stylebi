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
package inetsoft.mv.data;

import inetsoft.report.composition.execution.AssetConditionGroup;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XExpression;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #79870: a 'not equal to' data condition returns the same rows with and without MV.
 * The no-MV sides are the data condition group (embedded / in-memory table) and a real SQL
 * query (database table, where the condition is pushed into SQL). The MV side is the real
 * dictionary, index and SubMV. The MV stores null and the empty string as the same value.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class NotEqualNullAbTest {
   private static final String[] NULLS_ONLY = { "v01", null, "v03", "v02", null, "v03", "v01" };
   private static final String[] WITH_BLANKS = { "v01", null, "", "v03", "v02", "", null, "v03" };

   @Test
   void embeddedNullAndBlank() throws Exception {
      for(String[] col : new String[][] { NULLS_ONLY, WITH_BLANKS }) {
         for(String val : new String[] { "v03", "v01", "zz9" }) {
            assertEquals(mv(col, val), dataGroup(col, val), Arrays.toString(col) + " <> " + val);
         }
      }
   }

   @Test
   void databaseNull() throws Exception {
      for(String val : new String[] { "v03", "v01", "zz9" }) {
         assertEquals(mv(NULLS_ONLY, val), sql(NULLS_ONLY, val), "<> " + val);
      }
   }

   /**
    * Known limit: the MV cannot tell '' from null, SQL keeps ''.
    */
   @Test
   void databaseBlankIsKnownLimit() throws Exception {
      BitSet sql = sql(WITH_BLANKS, "v03");
      BitSet mv = mv(WITH_BLANKS, "v03");
      assertEquals(rows(0, 2, 4, 5), sql);
      assertEquals(rows(0, 4), mv);
   }

   @Test
   void highlightGroupKeepsNullForNotEqual() {
      // highlight / chart color groups do not set the flag: null still is "not equal to" a value
      ConditionGroup group = new ConditionGroup();
      group.addCondition(0, notEqual("v03"), 0);
      assertTrue(group.evaluate(new Object[] { (Object) null }));
      assertTrue(group.evaluate(new Object[] { "" }));
   }

   private static BitSet dataGroup(String[] col, String val) throws Exception {
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new ColumnRef(new AttributeRef(null, "DIM")), notEqual(val), 0));
      AssetConditionGroup group = new AssetConditionGroup(list, 0, null);
      BitSet rows = new BitSet();

      for(int i = 0; i < col.length; i++) {
         if(group.evaluate(new Object[] { col[i] })) {
            rows.set(i);
         }
      }

      return rows;
   }

   private static AssetCondition notEqual(String val) {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setNegated(true);
      cond.addValue(val);
      return cond;
   }

   private static BitSet mv(String[] col, String val) throws Exception {
      XDimDictionary dict = new XDimDictionary();
      dict.setDataType(String.class);

      for(String v : Arrays.stream(col).filter(v -> v != null && !v.isEmpty()).distinct().sorted()
         .toList())
      {
         dict.addValue(v);
      }

      if(Arrays.stream(col).anyMatch(v -> v == null || v.isEmpty())) {
         dict.addValue(null);
      }

      dict.complete();
      BitDimIndex idx = new BitDimIndex();

      for(int row = 0; row < col.length; row++) {
         idx.addKey(dict.indexOf(col[row], row), row);
      }

      idx.complete();
      XBinaryCondition cond =
         new XBinaryCondition(new XExpression("col", XExpression.FIELD),
                              new XExpression(val, XExpression.VALUE), "=");
      cond.setIsNot(true);
      dict.fixFilter(cond);
      DefaultTableBlock table = org.mockito.Mockito.mock(DefaultTableBlock.class);
      org.mockito.Mockito.when(table.indexOfHeader("col")).thenReturn(0);
      SubMV sub = new SubMV(new XDimIndex[] { idx }, table);
      Field f = SubMV.class.getDeclaredField("rtdims");
      f.setAccessible(true);
      f.set(sub, new XDimIndex[] { idx });
      java.lang.reflect.Method m = SubMV.class.getDeclaredMethod("getRows", XBinaryCondition.class);
      m.setAccessible(true);
      return (BitSet) m.invoke(sub, cond);
   }

   private static BitSet sql(String[] col, String val) throws Exception {
      try(Connection c = DriverManager.getConnection("jdbc:derby:memory:ne79870;create=true")) {
         try(Statement st = c.createStatement()) {
            try {
               st.execute("DROP TABLE T");
            }
            catch(SQLException ignore) {
            }

            st.execute("CREATE TABLE T (ID INT, DIM VARCHAR(10))");
         }

         try(PreparedStatement ps = c.prepareStatement("INSERT INTO T VALUES (?, ?)")) {
            for(int i = 0; i < col.length; i++) {
               ps.setInt(1, i);
               ps.setString(2, col[i]);
               ps.executeUpdate();
            }
         }

         BitSet rows = new BitSet();

         try(PreparedStatement ps = c.prepareStatement("SELECT ID FROM T WHERE NOT (DIM = ?)")) {
            ps.setString(1, val);

            try(ResultSet rs = ps.executeQuery()) {
               while(rs.next()) {
                  rows.set(rs.getInt(1));
               }
            }
         }

         return rows;
      }
   }

   private static BitSet rows(int... rows) {
      BitSet set = new BitSet();

      for(int row : rows) {
         set.set(row);
      }

      return set;
   }
}

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
package inetsoft.uql.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SQLIteratorTest {
   SQLIterator.SQLListener listener;
   StringBuilder sb;
   Map<Integer, String> columns;
   String whereClause;
   List<String> vpmTables;
   List<String> vpmColumns;
   List<String> vpmAliases;

   @BeforeEach
   void setup() {
      sb = new StringBuilder();
      columns = new HashMap<>();
      whereClause = null;
      vpmTables = new ArrayList<>();
      vpmColumns = new ArrayList<>();
      vpmAliases = new ArrayList<>();
      listener = (type, value, comment) -> {
         switch(type) {
         case SQLIterator.TEXT_ELEMENT:
            sb.append(value);
            break;
         case SQLIterator.COLUMN_ELEMENT:
            sb.append(value);
            int index = (Integer) comment + 1;
            columns.put(index, value);
            break;
         case SQLIterator.WHERE_ELEMENT:
            sb.append(value);
            whereClause = value;
            break;
         case SQLIterator.COMMENT_TABLE:
            vpmTables.add(value);
            break;
         case SQLIterator.COMMENT_COLUMN:
            vpmColumns.add(value);
            break;
         case SQLIterator.COMMENT_ALIAS:
            vpmAliases.add(value);
            break;
         default:
            // do nothing
            break;
         }
      };
   }

   @Test
   void testSimpleComments() throws Exception {
      String sql = "--some random comments\n" +
         "select table1.col1, table1.col2\n" +
         "from table1\n" +
         "--more comments\n" +
         "where table1.col1 > 10";
      String expected = "select table1.col1, table1.col2\n" +
         "from table1\n" +
         "where table1.col1 > 10";

      SQLIterator iterator = new SQLIterator(sql);
      iterator.addSQLListener(listener);
      iterator.iterate();

      assertEquals(expected, sb.toString());
   }

   @Test
   void testVPMComments() throws Exception {
      String sql = "--vpm.tables:SA.CUSTOMERS,SA.ORDER_DETAILS,SA.PRODUCTS\n" +
         "\n" +
         "--vpm.columns:SA.ORDER_DETAILS.QUANTITY,SA.CUSTOMERS.COMPANY_NAME\n" +
         "\n" +
         "select /*<2>*/SA.CUSTOMERS.COMPANY_NAME,/*</2>*/ /*<1>*/SA.ORDER_DETAILS.QUANTITY+10,/*</1>*/ SA.ORDERS.DISCOUNT,SA.PRODUCTS.PRODUCT_NAME,SA.PRODUCTS.PRICE,SA.PRODUCTS.DESCRIPTION\n" +
         "\n" +
         "from SA.CUSTOMERS, SA.ORDER_DETAILS, SA.ORDERS, SA.PRODUCTS\n" +
         "\n" +
         "where /*<where>*/SA.ORDER_DETAILS.PRODUCT_ID = SA.PRODUCTS.PRODUCT_ID and SA.ORDERS.ORDER_ID = SA.ORDER_DETAILS.ORDER_ID and SA.ORDERS.CUSTOMER_ID = SA.CUSTOMERS.CUSTOMER_ID/*</where>*/";
      String expected = "\n\nselect SA.CUSTOMERS.COMPANY_NAME, SA.ORDER_DETAILS.QUANTITY+10, SA.ORDERS.DISCOUNT,SA.PRODUCTS.PRODUCT_NAME,SA.PRODUCTS.PRICE,SA.PRODUCTS.DESCRIPTION\n" +
         "\n" +
         "from SA.CUSTOMERS, SA.ORDER_DETAILS, SA.ORDERS, SA.PRODUCTS\n" +
         "\n" +
         "where SA.ORDER_DETAILS.PRODUCT_ID = SA.PRODUCTS.PRODUCT_ID and SA.ORDERS.ORDER_ID = SA.ORDER_DETAILS.ORDER_ID and SA.ORDERS.CUSTOMER_ID = SA.CUSTOMERS.CUSTOMER_ID";

      SQLIterator iterator = new SQLIterator(sql);
      iterator.addSQLListener(listener);
      iterator.iterate();

      assertEquals(expected, sb.toString());
      assertEquals("SA.ORDER_DETAILS.QUANTITY+10,", columns.get(1));
      assertEquals("SA.CUSTOMERS.COMPANY_NAME,", columns.get(2));
      assertEquals("SA.ORDER_DETAILS.PRODUCT_ID = SA.PRODUCTS.PRODUCT_ID" +
                      " and SA.ORDERS.ORDER_ID = SA.ORDER_DETAILS.ORDER_ID" +
                      " and SA.ORDERS.CUSTOMER_ID = SA.CUSTOMERS.CUSTOMER_ID", whereClause);
      assertArrayEquals("SA.CUSTOMERS,SA.ORDER_DETAILS,SA.PRODUCTS".split(","), vpmTables.toArray());
      assertArrayEquals("SA.ORDER_DETAILS.QUANTITY,SA.CUSTOMERS.COMPANY_NAME".split(","), vpmColumns.toArray());
   }

   @Test
   void testSingleLineSQL() throws Exception {
      String sql = "SELECT col1, col2 FROM table1 WHERE /*<where>*/col1 > 10/*</where>*/";
      String expected = "SELECT col1, col2 FROM table1 WHERE col1 > 10";

      SQLIterator iterator = new SQLIterator(sql);
      iterator.addSQLListener(listener);
      iterator.iterate();

      assertEquals(expected, sb.toString());
      assertEquals("col1 > 10", whereClause);
   }

   @Test
   void testVPMCommentsWithLineBreaks() throws Exception {
      // Bug #61739
      String sql = "--vpm.tables:SA.CUSTOMERS,SA.ORDER_DETAILS,SA.PRODUCTS\n" +
         "\n" +
         "--vpm.columns:SA.ORDER_DETAILS.QUANTITY,SA.CUSTOMERS.COMPANY_NAME\n" +
         "\n" +
         "select /*<2>*/SA.CUSTOMERS.COMPANY_NAME,/*</2>*/ /*<1>*/SA.ORDER_DETAILS.QUANTITY+10,/*</1>*/ SA.ORDERS.DISCOUNT,SA.PRODUCTS.PRODUCT_NAME,SA.PRODUCTS.PRICE,SA.PRODUCTS.DESCRIPTION\n" +
         "\n" +
         "from SA.CUSTOMERS, SA.ORDER_DETAILS, SA.ORDERS, SA.PRODUCTS\n" +
         "\n" +
         "where /*<where>*/SA.ORDER_DETAILS.PRODUCT_ID = SA.PRODUCTS.PRODUCT_ID\n" +
         "and SA.ORDERS.ORDER_ID = SA.ORDER_DETAILS.ORDER_ID\n" +
         "and SA.ORDERS.CUSTOMER_ID = SA.CUSTOMERS.CUSTOMER_ID/*</where>*/";
      String expected = "\n\nselect SA.CUSTOMERS.COMPANY_NAME, SA.ORDER_DETAILS.QUANTITY+10, SA.ORDERS.DISCOUNT,SA.PRODUCTS.PRODUCT_NAME,SA.PRODUCTS.PRICE,SA.PRODUCTS.DESCRIPTION\n" +
         "\n" +
         "from SA.CUSTOMERS, SA.ORDER_DETAILS, SA.ORDERS, SA.PRODUCTS\n" +
         "\n" +
         "where SA.ORDER_DETAILS.PRODUCT_ID = SA.PRODUCTS.PRODUCT_ID\n" +
         "and SA.ORDERS.ORDER_ID = SA.ORDER_DETAILS.ORDER_ID\n" +
         "and SA.ORDERS.CUSTOMER_ID = SA.CUSTOMERS.CUSTOMER_ID";

      SQLIterator iterator = new SQLIterator(sql);
      iterator.addSQLListener(listener);
      iterator.iterate();

      assertEquals(expected, sb.toString());
      assertEquals("SA.ORDER_DETAILS.QUANTITY+10,", columns.get(1));
      assertEquals("SA.CUSTOMERS.COMPANY_NAME,", columns.get(2));
      assertEquals("SA.ORDER_DETAILS.PRODUCT_ID = SA.PRODUCTS.PRODUCT_ID\n" +
                      "and SA.ORDERS.ORDER_ID = SA.ORDER_DETAILS.ORDER_ID\n" +
                      "and SA.ORDERS.CUSTOMER_ID = SA.CUSTOMERS.CUSTOMER_ID", whereClause);
      assertArrayEquals("SA.CUSTOMERS,SA.ORDER_DETAILS,SA.PRODUCTS".split(","), vpmTables.toArray());
      assertArrayEquals("SA.ORDER_DETAILS.QUANTITY,SA.CUSTOMERS.COMPANY_NAME".split(","), vpmColumns.toArray());
   }

   @Test
   void testSimpleVPMCommentsWithLineBreaks() throws Exception {
      String sql = "SELECT /*<1>*/col1,\n" +
         "/*</1>*/ col2\n" +
         "FROM table1\n" +
         "WHERE /*<where>*/col1 > 10\n" +
         "/*</where>*/";
      String expected = "SELECT col1,\n" +
         " col2\n" +
         "FROM table1\n" +
         "WHERE col1 > 10\n";

      SQLIterator iterator = new SQLIterator(sql);
      iterator.addSQLListener(listener);
      iterator.iterate();

      assertEquals(expected, sb.toString());
      assertEquals("col1,\n", columns.get(1));
      assertEquals("col1 > 10\n", whereClause);
   }
}

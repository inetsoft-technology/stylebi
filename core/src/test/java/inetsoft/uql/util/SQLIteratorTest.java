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

import inetsoft.test.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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

   // Bug #77663, a literal containing a line break and -- is the text of the literal, not a
   // comment line
   @Test
   void commentLineInLiteralIsKept() {
      String sql = "select * from t where note = 'a\n-- b\n' and x = 1";

      assertEquals(sql, iterate(sql));
   }

   // Bug #77663, a vpm annotation in a literal is not an annotation
   @Test
   void annotationInLiteralIsIgnored() {
      String sql = "-- vpm.tables: SA.ORDERS\n" +
         "select * from SA.ORDERS where note <> 'x\n-- vpm.tables: SA.OTHER\n' and " +
         "/*<where>*/1=1/*</where>*/";

      iterate(sql);

      assertEquals(List.of("SA.ORDERS"), vpmTables);
      assertEquals("1=1", whereClause);
   }

   // Bug #77663, a tag in a literal is the text of the literal, so the vpm condition isn't
   // placed in the literal and the literal isn't changed
   @Test
   void tagInLiteralIsText() {
      String sql = "select * from SA.ORDERS where note = '/*<where>*/x/*</where>*/'";

      assertEquals(sql, iterate(sql));
      assertNull(whereClause);

      setup();
      sql = "select * from t where s = 'a/*<1>*/b/*</1>*/c'";
      assertEquals(sql, iterate(sql));
      assertTrue(columns.isEmpty());

      // the last line was dropped, another line threw, a tag that isn't a number threw
      for(String text : new String[] {
         "select * from t where s = '/*<b>*/'",
         "select * from t where s = '/*<b>*/'\nand x = 1",
         "select * from t where s = '/*<1>*/'",
         "select * from t where s = '/*</where>*/'",
         "select * from t where s = '/*<p>*/' or s = '/*</p>*/'",
         "select * from t where s = '/*<x>*/y/*</x>*/'",
         "select \"/*<where>*/x/*</where>*/\", `/*<1>*/`, [/*<2>*/] from t" })
      {
         setup();
         assertEquals(text, iterate(text));
         assertNull(whereClause);
         assertTrue(columns.isEmpty());
      }
   }

   // Bug #77663, a literal that looks like the start of a comment doesn't hide the tags
   @Test
   void commentStartInLiteralDoesNotHideTags() {
      String sql = "select /*<1>*/a/*</1>*/ from SA.ORDERS where s like '/*%' and " +
         "/*<where>*/1=1/*</where>*/";

      assertEquals("select a from SA.ORDERS where s like '/*%' and 1=1", iterate(sql));
      assertEquals("a", columns.get(1));
      assertEquals("1=1", whereClause);
   }

   // Bug #77663, the closing tag was searched from the start of the sql, so a closing tag
   // before the line (in a comment line or a literal) moved the cursor back and never ended
   @Test
   void earlierClosingTagDoesNotLoop() {
      String sql = "-- /*</1>*/\nselect /*<1>*/a\n/*</1>*/ from t\n";

      assertEquals("select a\n from t\n",
                   assertTimeoutPreemptively(Duration.ofSeconds(10), () -> iterate(sql)));
      assertEquals("a\n", columns.get(1));

      setup();
      String sql2 = "select * from t where n = 'x\n-- /*</1>*/\n' and /*<1>*/a\n/*</1>*/ = 1\n";
      assertEquals("select * from t where n = 'x\n-- /*</1>*/\n' and a\n = 1\n",
                   assertTimeoutPreemptively(Duration.ofSeconds(10), () -> iterate(sql2)));
      assertEquals("a\n", columns.get(1));
   }

   // Bug #77663, a tag not closed on the last line was dropped with the whole line, it is
   // invalid as on any other line
   @Test
   void unclosedTagOnLastLineThrows() {
      assertThrows(RuntimeException.class, () -> iterate("select a from t /*<b>*/"));
      assertThrows(RuntimeException.class, () -> iterate("select a\nfrom t /*<where>*/1=1"));
   }

   // Bug #77663, an apostrophe in a comment, an escaped quote or a quoted name doesn't open
   // a literal that hides the later tags
   @Test
   void apostropheOutsideLiteralDoesNotHideTags() {
      // in a -- comment at the end of a line
      String sql = "select /*<1>*/a/*</1>*/, -- the customer's name\n" +
         "/*<2>*/b/*</2>*/ from SA.ORDERS\n" +
         "where /*<where>*/1=1/*</where>*/";
      assertEquals("select a, -- the customer's name\nb from SA.ORDERS\nwhere 1=1", iterate(sql));
      assertEquals("a", columns.get(1));
      assertEquals("b", columns.get(2));
      assertEquals("1=1", whereClause);

      // a backslash escape (MySQL), with and without a later literal
      for(String text : new String[] {
         "select * from SA.ORDERS where name <> 'O\\'Brien'\nand /*<where>*/1=1/*</where>*/",
         "select * from SA.ORDERS where name <> 'O\\'Brien'\nand /*<where>*/1=1/*</where>*/" +
            " and x = 'y'",
         // a literal ending with a backslash (ANSI)
         "select * from SA.ORDERS where path <> 'C:\\' and /*<where>*/1=1/*</where>*/" +
            " and x = 'y'",
         // in a comment
         "select a /* don't */ from SA.ORDERS\nwhere /*<where>*/1=1/*</where>*/",
         "-- the customer's name\nselect a from SA.ORDERS\nwhere /*<where>*/1=1/*</where>*/",
         // in a quoted name
         "select a as \"customer's name\", [Customer's], `it's` from SA.ORDERS\n" +
            "where /*<where>*/1=1/*</where>*/",
         // a quote that is not closed
         "select a from SA.ORDERS where b = 'x and /*<where>*/1=1/*</where>*/" })
      {
         setup();
         iterate(text);
         assertEquals("1=1", whereClause, text);
      }
   }

   // Bug #77663, a comment or quote form of one database (a mysql # comment, a postgresql
   // nested comment or dollar quote, an oracle q quote, a mysql 5--1, a snowflake // comment)
   // doesn't make a quote open a literal that hides the later annotations and tags
   @Test
   void dialectCommentAndQuoteDoesNotHideTags() {
      iterate("# Sales report - don't edit\n-- vpm.tables: SA.ORDERS\n" +
         "select * from SA.ORDERS where /*<where>*/1=1/*</where>*/ and region = 'East'");
      assertEquals(List.of("SA.ORDERS"), vpmTables);
      assertEquals("1=1", whereClause);

      for(String text : new String[] {
         "select a # customer's name\nfrom SA.ORDERS where /*<where>*/1=1/*</where>*/ and b = 'x'",
         "select a /* outer /* inner */ don't */ from SA.ORDERS\n" +
            "where /*<where>*/1=1/*</where>*/ and b = 'x'",
         "select $$it's$$ as a from SA.ORDERS\nwhere /*<where>*/1=1/*</where>*/ and b = 'x'",
         "select $tag$it's$tag$ as a from SA.ORDERS\n" +
            "where /*<where>*/1=1/*</where>*/ and b = 'x'",
         "select q'[it's]' as a from SA.ORDERS\nwhere /*<where>*/1=1/*</where>*/ and b = 'x'",
         "select nq'{it's}' as a from SA.ORDERS\nwhere /*<where>*/1=1/*</where>*/ and b = 'x'",
         "select 5--1 as a, 'x\ny' as b from SA.ORDERS\n" +
            "where /*<where>*/1=1/*</where>*/ and c = 'z'",
         "select a // don't\nfrom SA.ORDERS where /*<where>*/1=1/*</where>*/ and b = 'x'" })
      {
         setup();
         iterate(text);
         assertEquals("1=1", whereClause, text);
      }

      // a literal is still found when the forms of other databases are in the sql
      setup();
      String sql = "select a # x\nfrom t where n = 'a\n-- vpm.tables: SA.OTHER\n' and $(v) = 1";
      assertEquals(sql, iterate(sql));
      assertEquals(List.of(), vpmTables);
   }

   // Bug #77663, the text of a literal is still a text element, so a variable in it is found
   @Test
   void literalIsTextElement() {
      List<String> texts = new ArrayList<>();
      SQLIterator iterator = new SQLIterator("select * from t where n = '$(v)\n-- $(w)\n'");
      iterator.addSQLListener((type, value, comment) -> {
         if(type == SQLIterator.TEXT_ELEMENT) {
            texts.add(value);
         }
      });
      iterator.iterate();

      assertEquals(List.of("select * from t where n = '$(v)\n-- $(w)\n'"), texts);
   }

   // quotes that are not closed don't make the scan quadratic
   @Test
   void unclosedQuotesAreScannedInLinearTime() {
      String sql = "select * from t where /*<where>*/1=1/*</where>*/ and a = '" +
         "\\'".repeat(200000);

      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> iterate(sql));
      assertEquals("1=1", whereClause);
   }

   // a tag after a -- in the middle of a line is still a tag, as before
   @Test
   void tagAfterMidLineCommentIsUnchanged() {
      iterate("select * from SA.ORDERS -- /*<where>*/ /*</where>*/");

      assertEquals(" ", whereClause);
   }

   // Bug #77663, windows line breaks: the annotation lines are still found and a literal
   // spanning lines still hides the annotation in it
   @Test
   void literalWithWindowsLineBreaks() {
      String sql = "-- vpm.tables: SA.ORDERS\r\n" +
         "select /*<1>*/a/*</1>*/ from SA.ORDERS\r\n" +
         "where n = 'x\r\n-- vpm.tables: SA.OTHER\r\n' and /*<where>*/1=1/*</where>*/\r\n";

      assertEquals("select a from SA.ORDERS\r\n" +
                   "where n = 'x\r\n-- vpm.tables: SA.OTHER\r\n' and 1=1\r\n", iterate(sql));
      assertEquals(List.of("SA.ORDERS"), vpmTables);
      assertEquals("a", columns.get(1));
      assertEquals("1=1", whereClause);
   }

   // Bug #77663, a closing tag in a literal inside a tag spanning lines is not the closing tag
   @Test
   void closingTagInLiteralOfMultiLineTag() {
      String sql = "select /*<1>*/a || '/*</1>*/'\n/*</1>*/ from t\n" +
         "where /*<where>*/n = '/*</where>*/'\nand m = 1/*</where>*/";

      assertEquals("select a || '/*</1>*/'\n from t\nwhere n = '/*</where>*/'\nand m = 1",
                   assertTimeoutPreemptively(Duration.ofSeconds(10), () -> iterate(sql)));
      assertEquals("a || '/*</1>*/'\n", columns.get(1));
      assertEquals("n = '/*</where>*/'\nand m = 1", whereClause);
   }

   private String iterate(String sql) {
      SQLIterator iterator = new SQLIterator(sql);
      iterator.addSQLListener(listener);
      iterator.iterate();
      return sb.toString();
   }
}

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
   List<String> wheres;
   List<String> vpmTables;
   List<String> vpmColumns;
   List<String> vpmAliases;

   @BeforeEach
   void setup() {
      sb = new StringBuilder();
      columns = new HashMap<>();
      whereClause = null;
      wheres = new ArrayList<>();
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
            wheres.add(value);
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
         "select \"/*<where>*/x/*</where>*/\", `/*<1>*/` from t" })
      {
         setup();
         assertEquals(text, iterate(text));
         assertNull(whereClause);
         assertTrue(columns.isEmpty());
      }

      // a [ is not a quote in most databases (an array subscript), so a tag in [] is a tag,
      // as before
      setup();
      assertEquals("select [x] from t", iterate("select [/*<2>*/x/*</2>*/] from t"));
      assertEquals("x", columns.get(2));
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
      assertThrows(RuntimeException.class, () -> iterate("select a from t /*<1>*/"));
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
         "select a // don't\nfrom SA.ORDERS where /*<where>*/1=1/*</where>*/ and b = 'x'",
         // postgresql: a backslash escapes in an E'' string only
         "select E'O\\'Brien' as n, replace(path, '\\', '/') as p\nfrom docs where " +
            "/*<where>*/1=1/*</where>*/ and kind = 'x'",
         // spark: nested comments and backslash escapes
         "/* report /* v2 */ don't edit */\nselect \"a\\\"b\" as v from SA.ORDERS where " +
            "/*<where>*/1=1/*</where>*/ and c = \"w\" and d = 'z'",
         // sql server: nested comments and [names]
         "/* a /* b */ don't */\nselect [it's] from SA.ORDERS where " +
            "/*<where>*/1=1/*</where>*/ and c = 'x'",
         // a [ is an array subscript in most databases, a ] in its key doesn't close it
         "select m['a]b'] from SA.ORDERS where /*<where>*/1=1/*</where>*/ and c = 'y'",
         "select m[']'] from SA.ORDERS where /*<where>*/1=1/*</where>*/ and c = 'y'",
         "select m[\"a]\"] from SA.ORDERS where /*<where>*/1=1/*</where>*/ and c = \"y\"" })
      {
         setup();
         iterate(text);
         assertEquals("1=1", whereClause, text);
      }

      setup();
      iterate("select E'O\\'Brien' as n, replace(path, '\\', '/') as p\n" +
         "-- vpm.tables: SA.ORDERS\nfrom SA.ORDERS where /*<where>*/1=1/*</where>*/ and kind = 'x'");
      assertEquals(List.of("SA.ORDERS"), vpmTables);
      assertEquals("1=1", whereClause);

      setup();
      iterate("select v['a]'] k\n-- vpm.tables: SA.ORDERS\nfrom SA.ORDERS where c = 'x'");
      assertEquals(List.of("SA.ORDERS"), vpmTables);

      // a literal is still found when the forms of other databases are in the sql
      setup();
      String sql = "select a # x\nfrom t where n = 'a\n-- vpm.tables: SA.OTHER\n' and $(v) = 1";
      assertEquals(sql, iterate(sql));
      assertEquals(List.of(), vpmTables);
   }

   // Bug #77663, a quote in a sql server, sybase or access [name] doesn't open a literal or
   // name that hides the later annotations and tags, and a ]] in it doesn't close it
   @Test
   void bracketNameDoesNotHideTags() {
      for(String text : new String[] {
         "select [a\"b] from SA.ORDERS where /*<where>*/1=1/*</where>*/ and c = \"y\"",
         "select [a]]b'] from SA.ORDERS where /*<where>*/1=1/*</where>*/ and c = 'x'" })
      {
         setup();
         iterate(text);
         assertEquals("1=1", whereClause, text);
      }

      setup();
      iterate("select [Customer's Name] as n\n-- vpm.tables: SA.ORDERS\n" +
         "from SA.ORDERS where region = 'East'");
      assertEquals(List.of("SA.ORDERS"), vpmTables);
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

   // Bug #77663, the comment and quote forms of each database family (dollar quotes, q quotes,
   // nested comments, # and // comments, 5--1, an informix {}, a [ in a [name], a tag in a
   // comment) don't make the scan quadratic, closed or not
   @Test
   void dialectFormsAreScannedInLinearTime() {
      for(String part : new String[] {
         "$a$x'y ", "$a$x'y$a$ ", "$$'$$ ", "$1 'x' ", "q'[x ", "q'[it's]' ",
         "/* /* x */ 'y */ ", "# it's\n", "5--1 'x\n", "'http://x' // y\n", "m['a]'] ",
         // Bug #77695, #77696
         "-- /*<1>*/ ", "{x ", "'''x ", "'''' ", "\"\"\"x ", "`a\\` ", "[x[ ", "/*< ", "{ '} " })
      {
         setup();
         String sql = "select * from t where /*<where>*/1=1/*</where>*/ and a = " +
            part.repeat(1_000_000 / part.length());

         assertTimeoutPreemptively(Duration.ofSeconds(10), () -> iterate(sql), part);
         assertEquals("1=1", whereClause, part);
      }
   }

   // Bug #77695, #77696, many /*< that are not tags before the later >*/ of a where tag, and
   // many column tags with different names in a comment, are read in linear time
   @Test
   void tagNamesAreReadInLinearTime() {
      for(String part : new String[] {
         "/*<1 ", "/*< ", "/*<x ", "/*<where ", "/*<1>", "/*<1>/ ", "/*</1 ", "/*<+00 " })
      {
         setup();
         String sql = "select " + part.repeat(1_000_000 / part.length()) +
            " from T where /*<where>*/a=1/*</where>*/";

         // the /*< opens a comment that holds the where tag, so the sql is sent as it is
         assertEquals(sql, assertTimeoutPreemptively(Duration.ofSeconds(10), () -> iterate(sql),
                                                     part), part);
         assertNull(whereClause, part);
      }

      setup();
      StringBuilder sb = new StringBuilder("select a -- ");

      for(int i = 1; i <= 50_000; i++) {
         sb.append("/*<").append(i).append(">*/x/*</").append(i).append(">*/ ");
      }

      String sql = sb.append("\nfrom T where /*<where>*/a=1/*</where>*/").toString();

      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> iterate(sql));
      assertEquals("x", columns.get(50_000));
      assertEquals("a=1", whereClause);

      // where tags in a comment that starts at a different index in each database
      setup();
      String sql2 = "select a #t -- " + "/*<where>*/x/*</where>*/ ".repeat(40_000) +
         "\nfrom T where /*<where>*/a=1/*</where>*/";

      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> iterate(sql2));
      assertEquals(List.of("a=1"), wheres);
   }

   // Bug #77695, a tag after a -- in the middle of a line is in a comment, it is not a tag
   @Test
   void tagAfterMidLineCommentIsIgnored() {
      String sql = "select * from SA.ORDERS -- /*<where>*/ /*</where>*/";

      assertEquals(sql, iterate(sql));
      assertNull(whereClause);
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

   // Bug #77695, a tag after a mid-line -- is ignored, so a vpm condition isn't written into a
   // comment. A tag after #, // or a -- with no space is still read: those are comments only
   // in some databases (mysql 5--1)
   @Test
   void tagInMidLineCommentIsIgnored() {
      String sql = "--vpm.tables:SA.ORDERS\nselect * from SA.ORDERS t1 -- where " +
         "/*<where>*/t1.ORDER_ID > 0/*</where>*/";
      assertEquals("select * from SA.ORDERS t1 -- where /*<where>*/t1.ORDER_ID > 0/*</where>*/",
                   iterate(sql));
      assertEquals(List.of("SA.ORDERS"), vpmTables);
      assertNull(whereClause);

      setup();
      iterate("select * from T -- old /*<where>*/x/*</where>*/\nwhere /*<where>*/a=1/*</where>*/");
      assertEquals(List.of("a=1"), wheres);

      // a tag line ending with a comment that holds a slash-star threw
      setup();
      assertEquals("select * from T where a=1 -- see /* note",
                   iterate("select * from T where /*<where>*/a=1/*</where>*/ -- see /* note"));
      assertEquals("a=1", whereClause);

      setup();
      iterate("select * from T --x /*<where>*/1=1/*</where>*/");
      assertEquals("1=1", whereClause);

      setup();
      assertEquals("select * from T where x > 5--1 and 1=1",
                   iterate("select * from T where x > 5--1 and /*<where>*/1=1/*</where>*/"));
      assertEquals("1=1", whereClause);

      setup();
      iterate("select * from T where /*<where>*/x > 5--1/*</where>*/\norder by 1");
      assertEquals("x > 5--1", whereClause);
   }

   // Bug #77695, a where tag after a -- is ignored also when an earlier token on the line is
   // a comment in some databases only (a sql server #tmp, a postgresql #>> or $$x--y$$, a
   // --x or a--1), so each database reads the tag in a comment, at a different start
   @Test
   void tagInCommentOfEveryDatabaseIsIgnored() {
      for(String sql : new String[] {
         "select * from #tmp t1 -- where /*<where>*/t1.ORDER_ID > 0/*</where>*/",
         "select t1.DATA #>> '{a}' as v from SA.ORDERS t1 -- where /*<where>*/1=1/*</where>*/",
         "select $$x--y$$ as v from SA.ORDERS t1 -- where /*<where>*/1=1/*</where>*/",
         "select * from SA.ORDERS --old: -- where /*<where>*/ORDER_ID > 0/*</where>*/",
         "select a--1 as v from SA.ORDERS -- where /*<where>*/1=1/*</where>*/",
         "select * from #tmp t1 -- where /*<where>*/\nt1.ORDER_ID > 0/*</where>*/" })
      {
         setup();
         assertEquals(sql, iterate(sql), sql);
         assertNull(whereClause, sql);
      }

      // the closing tag in a -- comment after a #tmp ends the value at the --
      setup();
      String sql = "select * from #tmp t1 where /*<where>*/a=1 -- why /*</where>*/\norder by 1";
      assertEquals("select * from #tmp t1 where a=1 -- why \norder by 1", iterate(sql));
      assertEquals("a=1 ", whereClause);

      // a tag that some database reads as sql is still read
      for(String[] c : new String[][] {
         { "select * from #tmp t1 where /*<where>*/a=1/*</where>*/", "a=1" },
         { "select * from T where d #>> '{a}' <> '--' and /*<where>*/1=1/*</where>*/", "1=1" },
         { "select * from T where n <> 'O\\'Brien -- x' and /*<where>*/1=1/*</where>*/", "1=1" },
         { "select * from T where x > 5--1 and /*<where>*/1=1/*</where>*/", "1=1" },
         { "select * from T --x /*<where>*/1=1/*</where>*/", "1=1" } })
      {
         setup();
         iterate(c[0]);
         assertEquals(c[1], whereClause, c[0]);
      }
   }

   // Bug #77695, a where tag is ignored also when no -- with a space comes before it, if each
   // database reads it in a comment that starts elsewhere (mysql at a sql server #tmp, the
   // others at 5--1). A tag-like text in such a comment isn't paired with the closing tag of
   // the real tag on a later line, which took the lines between them as the where value
   @Test
   void tagInCommentsWithDifferentStartsIsIgnored() {
      String sql = "select * from #tmp t1 where t1.b > 5--1 and /*<where>*/a=1/*</where>*/";
      assertEquals(sql, iterate(sql));
      assertNull(whereClause);

      setup();
      sql = "select t1.a--1, #tmp.c, 'a /*<where>*/ b'\nfrom T t1 where /*<where>*/a=1/*</where>*/";
      assertEquals("select t1.a--1, #tmp.c, 'a /*<where>*/ b'\nfrom T t1 where a=1", iterate(sql));
      assertEquals(List.of("a=1"), wheres);
   }

   // Bug #77695, a slash-star not closed on its line, in a comment of every database wherever
   // it starts (mysql at a sql server #tmp, the others at --), is text of the comment, as the
   // where opener before it is. It was read as an open comment and the line threw
   @Test
   void unclosedSlashStarInCommentOfEveryDatabaseIsText() {
      String sql = "select t1.a from #tmp t1 -- where /*<where>*/ /* old\n" +
         "where /*<where>*/t1.a > 0/*</where>*/";
      String text = iterate(sql);
      assertEquals("select t1.a from #tmp t1 -- where /*<where>*/ /* old\nwhere t1.a > 0", text);
      assertEquals(List.of("t1.a > 0"), wheres);
      // main read the commented opener as the tag, with the value up to the real closing tag,
      // and kept the real opener instead, so the text differs only in which opener is kept
      String mainText = "select t1.a from #tmp t1 -- where  /* old\nwhere /*<where>*/t1.a > 0";
      assertEquals(mainText.replace("/*<where>*/", ""), text.replace("/*<where>*/", ""));

      setup();
      sql = "select t1.a from #tmp t1 -- was /*<where>*/t1.b > 0 /* old\n" +
         "where /*<where>*/t1.a > 0/*</where>*/";
      assertEquals("select t1.a from #tmp t1 -- was /*<where>*/t1.b > 0 /* old\nwhere t1.a > 0",
                   iterate(sql));
      assertEquals(List.of("t1.a > 0"), wheres);

      setup();
      sql = "select /*<1>*/a/*</1>*/ from #tmp t1 -- note /* old";
      assertEquals("select a from #tmp t1 -- note /* old", iterate(sql));
      assertEquals("a", columns.get(1));

      // the comment starts at the slash-star in some databases (mysql at #tmp, the others at
      // the slash-star), the text is read as on a line without a tag, which never threw
      setup();
      sql = "select /*<1>*/a/*</1>*/ from #tmp t1 /* x\nwhere /*<where>*/a=1/*</where>*/";
      assertEquals("select a from #tmp t1 /* x\nwhere a=1", iterate(sql));
      assertEquals(List.of("a=1"), wheres);
      setup();
      assertEquals("select a from #tmp t1 /* x\nwhere a=1",
                   iterate("select a from #tmp t1 /* x\nwhere /*<where>*/a=1/*</where>*/"));

      // the slash-star is in a literal in some database, so the line is still invalid
      assertThrows(RuntimeException.class, () -> iterate(
         "select /*<1>*/a/*</1>*/ from T where n = 'O\\'Brien /* x'"));
   }

   // Bug #77695, the unclosed slash-star passed on as text opens a block comment in every
   // database that runs to the next line's where tag, so that tag is still not read: the
   // text is passed through as written, and the vpm path rejects the query
   @Test
   void unclosedSlashStarTextDoesNotExposeLaterTagInComment() {
      String sql = "select a /* y\n, b from T t1 -- where /*<where>*/ /* old\n" +
         "where /*<where>*/t1.a > 0/*</where>*/";
      assertEquals(sql, iterate(sql));
      assertEquals(List.of(), wheres);

      setup();
      sql = "select /*<1>*/a/*</1>*/ /* y\n, b from T t1 -- where /*<where>*/ /* old\n" +
         "where /*<where>*/t1.a > 0/*</where>*/";
      assertEquals("select a /* y\n, b from T t1 -- where /*<where>*/ /* old\n" +
                   "where /*<where>*/t1.a > 0/*</where>*/", iterate(sql));
      assertEquals(List.of(), wheres);
      assertEquals("a", columns.get(1));
   }

   // Bug #77695, a closing tag in a -- comment that starts in the value ends the value at the
   // comment, and the comment is passed on as text, so the sql sent is unchanged
   @Test
   void closingTagInMidLineCommentEndsValue() {
      String sql = "select * from T where /*<where>*/a=1 -- note /*</where>*/\norder by 1";
      assertEquals("select * from T where a=1 -- note \norder by 1", iterate(sql));
      assertEquals("a=1 ", whereClause);

      setup();
      sql = "select * from T where /*<where>*/a=1 -- note /*</where>*/ and x=1\norder by 1";
      assertEquals("select * from T where a=1 -- note  and x=1\norder by 1", iterate(sql));
      assertEquals("a=1 ", whereClause);

      setup();
      sql = "select * from T where /*<where>*/a='--' -- x /*</where>*/ and b=1\norder by 1";
      assertEquals("select * from T where a='--' -- x  and b=1\norder by 1", iterate(sql));
      assertEquals("a='--' ", whereClause);

      setup();
      sql = "select /*<1>*/ssn -- x/*</1>*/, b from T\nwhere 1=1";
      assertEquals("select ssn -- x, b from T\nwhere 1=1", iterate(sql));
      assertEquals("ssn ", columns.get(1));
   }

   // Bug #77695, the closing tag of a value that spans lines is on a -- comment line, so the
   // value ends before the comment and a condition added after it is not commented out
   @Test
   void closingTagInCommentLineEndsMultiLineValue() {
      String sql = "select * from T where /*<where>*/a=1\n-- old /*</where>*/\norder by 1";
      assertEquals("select * from T where a=1\n-- old \norder by 1", iterate(sql));
      assertEquals(List.of("a=1\n"), wheres);

      setup();
      sql = "select /*<1>*/ssn,\n-- x /*</1>*/\nb from T";
      assertEquals("select ssn,\n-- x \nb from T", iterate(sql));
      assertEquals("ssn,\n", columns.get(1));
   }

   // Bug #77695, a tag name other than where or a column number is a regular comment, which
   // is passed on, instead of a NumberFormatException or a column index below 0. A column
   // number may have leading zeros or a + sign, as Integer.parseInt read it
   @Test
   void invalidTagNameIsRegularComment() {
      for(String sql : new String[] {
         "select a from T where /*<x>*/b=1/*</x>*/",
         "select a /*<b>*/x/*</b>*/ from T",
         "select a from T /*<note>*/",
         "select a /*<0>*/x/*</0>*/ from T",
         "select a /*<-1>*/x/*</-1>*/ from T",
         "select a /*<99999999999>*/x/*</99999999999>*/ from T",
         "select a /*</where>*/ from T",
         "select * from T where /*<WHERE>*/1=1/*</WHERE>*/",
         // a full-width where is not where, as before (main took it as a number and threw)
         "select * from T where /*<ｗｈｅｒｅ>*/1=1" +
            "/*</ｗｈｅｒｅ>*/",
         "select /*< 1 >*/x/*</ 1 >*/ from T" })
      {
         setup();
         assertEquals(sql, iterate(sql), sql);
         assertNull(whereClause, sql);
         assertTrue(columns.isEmpty(), sql);
      }

      setup();
      assertEquals("select x from T", iterate("select /*<03>*/x/*</03>*/ from T"));
      assertEquals("x", columns.get(3));

      setup();
      assertEquals("select x from T", iterate("select /*<+3>*/x/*</+3>*/ from T"));
      assertEquals("x", columns.get(3));

      // any unicode decimal digit is read as Integer.parseInt reads it: a full-width digit
      // typed with a CJK IME, an Arabic-Indic digit, and a full-width zero before it
      for(String name : new String[] { "３", "٣", "０３", "+３" }) {
         setup();
         String sql = "select a, b, /*<" + name + ">*/t2.SSN/*</" + name + ">*/ from T";
         assertEquals("select a, b, t2.SSN from T", iterate(sql), sql);
         assertEquals(Map.of(3, "t2.SSN"), columns, sql);
      }

      assertThrows(RuntimeException.class, () -> iterate("select a /*<1>*/ from T"));
      assertThrows(RuntimeException.class, () -> iterate("select a from t\nwhere /*<where>*/1=1"));
   }

   // Bug #77695, a slash-star comment on a tag line that ends on a later line (after a literal
   // spanning lines, Bug #77663) threw, and /*/ threw a StringIndexOutOfBoundsException
   @Test
   void blockCommentSpanningLinesOnTagLine() {
      String[][] cases = {
         { "select * from T where /*<where>*/T.A = 1/*</where>*/ and T.B <> 'x\ny' /* note\n" +
              "continues */",
           "select * from T where T.A = 1 and T.B <> 'x\ny' /* note\ncontinues */" },
         { "select * from T where /*<where>*/T.A = 1/*</where>*/ and T.B <> 'x\ny' /*/ note\n" +
              "continues */",
           "select * from T where T.A = 1 and T.B <> 'x\ny' /*/ note\ncontinues */" },
         { "select * from T where /*<where>*/T.A = 1/*</where>*/ and T.B <> 'x\ny' /* unclosed",
           "select * from T where T.A = 1 and T.B <> 'x\ny' /* unclosed" },
         { "select * from T where /*<where>*/T.A = 1/*</where>*/ /* note\ncontinues */",
           "select * from T where T.A = 1 /* note\ncontinues */" },
         { "select * from T where /*<where>*/T.A = 1/*</where>*/ /*/ c */",
           "select * from T where T.A = 1 /*/ c */" } };

      for(String[] c : cases) {
         setup();
         assertEquals(c[1], iterate(c[0]), c[0]);
         assertEquals("T.A = 1", whereClause, c[0]);
      }
   }

   // Bug #77695, a where tag in a slash-star comment is not a tag. A column tag in a comment
   // is still read if it is closed, and the annotation lines in a header comment are still read,
   // as before
   @Test
   void tagsInBlockComment() {
      String sql = "/* note\nwhere /*<where>*/1=1/*</where>*/ */\nselect * from T";
      assertEquals(sql, iterate(sql));
      assertNull(whereClause);

      setup();
      assertEquals("select a /* x b */ from T",
                   iterate("select a /* x /*<1>*/b/*</1>*/ */ from T"));
      assertEquals("b", columns.get(1));

      setup();
      assertEquals("select a, -- note \nssn from T",
                   iterate("-- vpm.columns: T.A, T.SSN\nselect a, -- note /*<2>*/\nssn/*</2>*/ from T"));
      assertEquals("\nssn", columns.get(2));

      setup();
      assertEquals("/* note\n*/\nselect * from T", iterate("/* note\n-- vpm.tables:SA.X\n*/\nselect * from T"));
      assertEquals(List.of("SA.X"), vpmTables);

      setup();
      iterate("select /* a\n-- vpm.tables: SA.X\n*/ * from T\nwhere /*<where>*/1=1/*</where>*/");
      assertEquals(List.of("SA.X"), vpmTables);
      assertEquals("1=1", whereClause);

      setup();
      assertEquals("/*\n*/\nselect NAME, SALARY from E",
                   iterate("/*\n-- vpm.columns: E.NAME, E.SALARY\n*/\nselect /*<1>*/NAME/*</1>*/, " +
                              "/*<2>*/SALARY/*</2>*/ from E"));
      assertEquals(List.of("E.NAME", "E.SALARY"), vpmColumns);
      assertEquals("SALARY", columns.get(2));
   }

   // Bug #77695, the character after a regular comment on a tag line was dropped
   @Test
   void characterAfterCommentIsKept() {
      assertEquals("select /*hint*/a, b from T", iterate("select /*hint*/a, /*<1>*/b/*</1>*/ from T"));
      assertEquals("b", columns.get(1));

      setup();
      assertEquals("select /*a*/b from T", iterate("select /*a*//*<1>*/b/*</1>*/ from T"));
      assertEquals("b", columns.get(1));

      setup();
      assertEquals("select /*+ index(t) */ a from T t where 1=1",
                   iterate("select /*+ index(t) */ a from T t where /*<where>*/1=1/*</where>*/"));
   }

   // Bug #77695, a -- or slash-star that is a comment in one database only, or is in a
   // literal of another database, doesn't hide the tags (a postgresql #>>, a mysql \')
   @Test
   void commentOfOneDatabaseDoesNotHideTags() {
      String[][] columnCases = {
         { "-- vpm.columns: T.V, T.X2, T.SSN\nselect data #>> '{a,b}' as v, coalesce(x, '--') as " +
              "x2, /*<3>*/ssn/*</3>*/ from T", "3" },
         { "-- vpm.columns: T.V, T.S, T.SSN\nselect data #>> '{a}' as v, '/*' as s,\n " +
              "/*<3>*/ssn/*</3>*/ from T", "3" },
         { "-- vpm.columns: E.NAME, E.SSN\nselect name from E where name <> 'O\\'Brien' union all\n" +
              "select concat(name, ' -- ', dept) as n, /*<2>*/ssn/*</2>*/ from E", "2" },
         { "select d #>> '{a}' as v, '--' as s, /*<1>*/x/*</1>*/ from T", "1" },
         { "-- vpm.columns: E.N, E.SALARY\nselect 'O\\'Brien' as q, concat(a, ' -- ', b) as n, " +
              "/*<2>*/SALARY/*</2>*/ from E", "2" },
         { "-- vpm.columns: T.Q, T.N, T.SSN\nselect replace(q, '''', '') as q, coalesce(n, '--') " +
              "as n, /*<3>*/ssn/*</3>*/ from T", "3" } };

      for(String[] c : columnCases) {
         setup();
         iterate(c[0]);
         assertNotNull(columns.get(Integer.parseInt(c[1])), c[0]);
      }

      setup();
      iterate("select 'O\\'Brien' as q, '/*' as s\n--vpm.tables:SA.T\nfrom SA.T");
      assertEquals(List.of("SA.T"), vpmTables);

      for(String sql : new String[] {
         "-- vpm.tables: T\nselect * from T where n <> 'O\\'Brien -- x' and /*<where>*/1=1/*</where>*/",
         "-- vpm.tables: T\nselect * from T where data #>> '{a}' <> '--' and /*<where>*/1=1/*</where>*/",
         "select * from #tmp t where t.p like '/x/*'\n-- vpm.tables: dbo.T\nand " +
            "/*<where>*/1=1/*</where>*/",
         "-- vpm.tables: T\nselect * from T where replace(q, '''', '') <> '--' and " +
            "/*<where>*/1=1/*</where>*/" })
      {
         setup();
         iterate(sql);
         assertEquals("1=1", whereClause, sql);
         assertEquals(1, vpmTables.size(), sql);
      }
   }

   // Bug #77696, an informix {...} comment, a backslash in a bigquery or clickhouse `name`,
   // and a sql server [name] holding a [ or a line break don't open a literal that hides the
   // annotations and tags. A jdbc escape {fn ...} is not an informix comment
   @Test
   void dialectNameAndCommentFormsDoNotHideTags() {
      String a = "-- vpm.tables: SA.ORDERS";
      String w = "where /*<where>*/t1.ORDER_ID > 0/*</where>*/ and t1.STATUS = 'OPEN'";

      for(String sql : new String[] {
         a + "\nselect * from SA.ORDERS t1 { customer's open orders }\n" + w,
         "select * from SA.ORDERS t1 { customer's open orders }\n" + a + "\n" + w,
         "{ Open orders by state.\n  Owner: ops team, don't edit without review }\n" + a +
            "\nselect * from SA.ORDERS t1\n" + w,
         a + "\nselect t1.`Customer\\`s Name` from SA.ORDERS t1\nwhere " +
            "/*<where>*/t1.ORDER_ID > 0/*</where>*/ and t1.`STATUS` = 'OPEN'",
         a + "\nselect t1.[Customer's [Old]] Name] from SA.ORDERS t1\n" + w,
         a + "\nselect t1.[Customer's\nName] from SA.ORDERS t1\n" + w,
         a + "\nselect {fn ucase(t1.NAME)}, {fn locate('}', t1.NAME)} from SA.ORDERS t1\n" + w,
         a + "\nselect ARRAY[\n 'a -- x',\n 'b'] from SA.ORDERS t1\n" + w })
      {
         setup();
         iterate(sql);
         assertEquals(List.of("SA.ORDERS"), vpmTables, sql);
         assertEquals("t1.ORDER_ID > 0", whereClause, sql);
      }

      // a bigquery '''...''' string holding a quote is not read, as before; the vpm rejects
      // the sql since it has a where tag that wasn't read
      setup();
      iterate(a + "\nselect t1.*, '''Customer's order''' as LABEL from SA.ORDERS t1\n" + w);
      assertNull(whereClause);
   }

   // Bug #77696, ansi quote idioms ('''' and ''',''') are sent as before, a '''x''' bigquery
   // form must not shift the quoting of the later literals
   @Test
   void quoteIdiomsAreSentAsBefore() {
      for(String sql : new String[] {
         "select '''' || a || ''',''' || b || '''' as line,\n 'header\n-- sep' as h from T",
         "select concat('''', a, ''', ''', b, '''') as csv, 'x\n-- y' as z from T",
         "select '''a' as x, '''b' as y, 'Total\n-- end' as s from T",
         "select '''' + a + ''',''' + b + '''' as csv, 'x\n-- y' as z from T",
         "select '''Hello''' as a, 'x\n-- y' as z from T",
         "select 'it''s' as a, '''' as b, 'x\n-- y' from T" })
      {
         setup();
         assertEquals(sql, iterate(sql), sql);
      }

      setup();
      iterate("select '''' || a || ''', ''' || b || '''' as csv, 'see /*<where>*/' as h from T " +
                 "where /*<where>*/1=1/*</where>*/");
      assertEquals("1=1", whereClause);
   }

   private String iterate(String sql) {
      SQLIterator iterator = new SQLIterator(sql);
      iterator.addSQLListener(listener);
      iterator.iterate();
      return sb.toString();
   }
}

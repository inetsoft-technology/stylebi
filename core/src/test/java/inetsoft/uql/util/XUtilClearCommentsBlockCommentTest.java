/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77788, clearComments removed every line whose first non-blank characters are --,
 * including one that starts inside a slash-star comment and ends it (-- keep, then a
 * star-slash). The comment then ran on to a later star-slash in the databases whose comments
 * don't nest (mysql, sqlite), and hid the sql between them, such as a vpm condition. An
 * executed query with such a line fails now. Every other sql is cleared as before, and the
 * plan text is cleared as before.
 */
@Tag("core")
class XUtilClearCommentsBlockCommentTest {
   // the issue sql, after the vpm condition is written
   @Test
   void closerLineFails() {
      assertFails("-- vpm.tables: SA.ORDERS\n" +
                     "select * from SA.ORDERS /* old version:\n" +
                     "-- keep */\n" +
                     "where (SA.ORDERS.ORDER_ID > 0) and (SA.ORDERS.STATE = 'NJ') /* end */",
                  "-- keep */");
   }

   // the shapes of the issue and the reviews, each line that ends or changes the comment fails
   @Test
   void closingShapesFail() {
      for(String sql : new String[] {
         // tags
         "select * from SA.ORDERS /* old version:\n-- keep */\n" +
            "where /*<where>*/SA.ORDERS.ORDER_ID > 0/*</where>*/ /* end */",
         // an empty where tag
         "select * from SA.ORDERS /* old:\n-- keep */\n/*<where>*//*</where>*/ /* end */",
         // \r\n
         "select * from SA.ORDERS /* old version:\r\n-- keep */\r\nwhere a = 1 /* end */",
         // a tagged where clause commented out
         "select * from SA.ORDERS /* old version:\n" +
            "--where /*<where>*/SA.ORDERS.ORDER_ID > 9/*</where>*/ */\n" +
            "where /*<where>*/SA.ORDERS.ORDER_ID > 0/*</where>*/ /* end */",
         // a union branch on the line, and on the next line, with and without a later comment
         "select * from t where /*<where>*/1=1/*</where>*/ /* old:\n" +
            "-- x */ union all select * from t\n/* end */",
         "select * from t where /*<where>*/1=1/*</where>*/ /* old:\n" +
            "-- x */\nunion all select * from t\n/* end */",
         "select * from t where /*<where>*/1=1/*</where>*/ /* old:\n" +
            "-- x */\nunion all select * from t\n",
         "select * from t where /*<where>*/1=1/*</where>*/ /* old:\n" +
            "--x /*<where>*/ union all select * from t/*</where>*/\n/* end */",
         // a column on the next line
         "select /*<1>*/t1.A/*</1>*/ /* old:\n-- */\n, t1.B\n/* end */ from t t1",
         // a vpm annotation that ends the comment
         "select * from SA.ORDERS /* x\n-- vpm.tables: SA.ORDERS */\n" +
            "where /*<where>*/SA.ORDERS.ORDER_ID > 0/*</where>*/",
         // the last line
         "select * from t /* a\n-- b */",
         "select * from t /* a\n  -- b */ ",
         // a comment or blanks after the closer
         "select 1 /* a\n-- keep */ -- note\nfrom t",
         "select 1 /* a\n-- keep */ /* more */\nfrom t",
         // mysql --x
         "select 1 /* a\n--keep */\nfrom t",
         // ends the comment where comments don't nest (mysql)
         "/* a\n-- see /* note */\nb */ select 1",
         "select * from t where /*<where>*/1=1/*</where>*/ /* old:\n" +
            "-- see /* note */\nunion all select * from t /* end */",
         // changes the depth where comments nest (postgresql, sql server)
         "/* a\n-- /* b\n*/ */ select 1",
         "/* a /* b\n-- c */\n*/ select 1",
         // a mysql # comment hides the slash-star from mysql only
         "select 1 # x /*\n--x */\nfrom t" })
      {
         assertFails(sql, null);
      }
   }

   // S5 and the other sql: the same as before, whether executed or not
   @Test
   void otherSqlIsClearedAsBefore() throws Exception {
      for(String sql : new String[] {
         "-- vpm.tables: SA.ORDERS\nselect * from SA.ORDERS /* old version:\n-- keep\n*/\n" +
            "where /*<where>*/a = 1/*</where>*/ /* end */",
         "select * from SA.ORDERS /* old version:\r\n-- keep\r\n*/\r\nwhere a = 1",
         "/* a\n-- b\n-- c\n*/ select 1",
         // nested: the line leaves the comment at the same depth
         "/* a /* b\n-- c\n*/ */ select 1",
         // a -- line not in a comment
         "-- vpm.tables: t\nselect 1 from t\n-- note */\n",
         "select 1 from t\n  --x */\nwhere a = 1",
         "-- vpm.tables: SA.ORDERS */\nselect * from SA.ORDERS",
         // the slash-star is in a literal, not a comment
         "select '/*' from t\n-- x */\nwhere a = 1",
         // a mid-line -- is not a comment line
         "select a /* x\n b -- c */ from t\nwhere a = 1",
         // the -- in a literal
         "select 'a\n-- b */' from t /* c */",
         // the comment ends and opens again on a line that is not a comment line
         "select 1 /* a\n*/ /* b\n-- c\n*/ from t" })
      {
         assertEquals(before(sql), execute(sql), sql);
         assertEquals(before(sql), plan(sql), sql);
      }
   }

   // a realistic generated corpus: an executed query is cleared as before, or fails if a
   // comment line ends or changes a comment. A line that ends a comment in the ansi reading
   // always fails it. The plan text is always cleared as before
   @Test
   void corpusIsClearedAsBeforeOrFails() {
      String[] parts = {
         "select * from t", " where a = 1", "/* c */", "/* open", "*/", " -- note", "'x'",
         "'/*'", "'*/'", "/*<where>*/a = 1/*</where>*/", "/*<1>*/t.a/*</1>*/", " # m /*",
         "\"q*/\"", "/*/", " union all select 1", "$$a*/$$", "{fn x()}" };
      String[] lineStarts = { "", "-- ", "--", "  -- ", "-- vpm.tables: t", "--x " };
      Random random = new Random(77788);
      int failed = 0;
      int same = 0;

      for(int n = 0; n < 20000; n++) {
         StringBuilder sb = new StringBuilder();
         int count = 1 + random.nextInt(6);

         for(int l = 0; l < count; l++) {
            sb.append(lineStarts[random.nextInt(lineStarts.length)]);

            for(int k = random.nextInt(4); k >= 0; k--) {
               sb.append(parts[random.nextInt(parts.length)]);
            }

            sb.append(random.nextInt(4) == 0 ? "\r\n" : "\n");
         }

         String sql = sb.toString();
         String expected;

         try {
            expected = before(sql);
         }
         catch(RuntimeException ex) {
            // an invalid line throws as before
            assertThrows(RuntimeException.class, () -> plan(sql), sql);
            assertThrows(RuntimeException.class, () -> execute(sql), sql);
            continue;
         }

         assertEquals(expected, plan(sql), sql);
         boolean ansi = endsAnsiComment(sql);
         boolean closing = closes(sql);

         if(ansi) {
            assertTrue(closing, () -> "a line that ends a comment is removed: " + sql);
         }

         try {
            String executed = execute(sql);
            assertFalse(closing, sql);
            assertEquals(expected, executed, sql);
            same++;
         }
         catch(SQLException ex) {
            assertTrue(closing, sql);
            failed++;
         }
      }

      assertTrue(failed > 100 && same > 100, "the corpus is too narrow: " + failed + ", " + same);
   }

   // linear time on adversarial input
   @Test
   void linearTime() {
      String[] inputs = {
         "/*\n-- */\n".repeat(120000),
         "/*".repeat(250000) + "\n-- x\n" + "*/".repeat(250000),
         "/* a\n" + "-- /* b\n".repeat(120000),
         "select 1\n" + "-- c\n".repeat(200000),
         "select 1 /*\n" + "-- c\n".repeat(200000) + "*/" };

      for(String sql : inputs) {
         assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            try {
               execute(sql);
            }
            catch(SQLException ex) {
               // a closing line fails
            }
         });
      }
   }

   private static void assertFails(String sql, String line) {
      SQLException ex = assertThrows(SQLException.class, () -> execute(sql), sql);
      assertTrue(ex.getMessage().contains("Move the */ off the -- line"), ex.getMessage());

      if(line != null) {
         assertTrue(ex.getMessage().contains("\"" + line + "\""), ex.getMessage());
      }

      // the plan text doesn't fail, it is cleared as before
      assertEquals(before(sql), plan(sql), sql);
   }

   private static String execute(String sql) throws SQLException {
      return sqlString(XUtil.clearCommentsToExecute(query(sql)));
   }

   private static String plan(String sql) {
      return sqlString(XUtil.clearComments(query(sql)));
   }

   private static JDBCQuery query(String text) {
      UniformSQL sql = new UniformSQL();
      sql.setSQLString(text, false);
      sql.setParseResult(UniformSQL.PARSE_FAILED);
      JDBCQuery query = new JDBCQuery();
      query.setName("q1");
      query.setSQLDefinition(sql);
      return query;
   }

   private static String sqlString(Object query) {
      return ((UniformSQL) ((JDBCQuery) query).getSQLDefinition()).getSQLString();
   }

   // the sql clearComments returned before the fix: every comment line is removed
   private static String before(String sql) {
      StringBuilder sb = new StringBuilder();
      SQLIterator iterator = new SQLIterator(sql);
      iterator.addSQLListener((type, value, comment) -> {
         if(type == SQLIterator.TEXT_ELEMENT || type == SQLIterator.COLUMN_ELEMENT ||
            type == SQLIterator.WHERE_ELEMENT)
         {
            sb.append(value);
         }
      });
      iterator.iterate();
      return sb.toString();
   }

   // check if findCommentChanges finds a comment line that ends or changes a comment
   private static boolean closes(String sql) {
      List<int[]> ranges = commentLines(sql);
      boolean[] changed = SQLQuoteScanner.findCommentChanges(
         sql, ranges.stream().mapToInt(r -> r[0]).toArray(),
         ranges.stream().mapToInt(r -> r[1]).toArray());

      for(boolean change : changed) {
         if(change) {
            return true;
         }
      }

      return false;
   }

   // check if a comment line ends a comment in the ansi reading, see ansiBlockComments
   private static boolean endsAnsiComment(String sql) {
      int[] block = ansiBlockComments(sql);

      for(int[] range : commentLines(sql)) {
         int comment = block[range[0]];

         for(int j = range[0]; comment >= 0 && j <= range[1]; j++) {
            if(block[j] != comment) {
               return true;
            }
         }
      }

      return false;
   }

   // the start and end of each comment line the iterator fires
   private static List<int[]> commentLines(String sql) {
      List<int[]> ranges = new ArrayList<>();
      SQLIterator iterator = new SQLIterator(sql);
      iterator.addSQLListener((type, value, comment) -> {
         if(type == SQLIterator.COMMENT_ELEMENT) {
            ranges.add(new int[] { iterator.getLineStart(), iterator.getLineEnd() });
         }
      });
      iterator.iterate();
      return ranges;
   }

   // the start of the slash-star comment holding each character in an ansi reading, written
   // apart from SQLQuoteScanner: '' "" and `` quotes (doubled to escape, one not closed is an
   // ordinary character, and so are the later quotes of its kind), -- comments and slash-star
   // comments that don't nest. The last element is the start of an unterminated comment
   private static int[] ansiBlockComments(String sql) {
      int len = sql.length();
      int[] block = new int[len + 1];
      Arrays.fill(block, -1);
      String unclosed = "";
      int i = 0;

      while(i < len) {
         char c = sql.charAt(i);

         if(sql.startsWith("--", i)) {
            int end = sql.indexOf('\n', i);
            i = end < 0 ? len : end;
         }
         else if(sql.startsWith("/*", i)) {
            int end = sql.indexOf("*/", i + 2);
            int stop = end < 0 ? len : end + 2;
            Arrays.fill(block, i, stop, i);
            block[len] = end < 0 ? i : -1;
            i = stop;
         }
         else if((c == '\'' || c == '"' || c == '`') && unclosed.indexOf(c) < 0) {
            int j = i + 1;

            while(j < len && (sql.charAt(j) != c || j + 1 < len && sql.charAt(j + 1) == c)) {
               j += sql.charAt(j) == c ? 2 : 1;
            }

            if(j >= len) {
               unclosed += c;
               i++;
            }
            else {
               i = j + 1;
            }
         }
         else {
            i++;
         }
      }

      return block;
   }
}

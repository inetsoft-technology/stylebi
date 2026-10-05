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

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77788, clearComments removed every line whose first non-blank characters are --,
 * including one that starts inside a slash-star comment and ends it (-- keep, then a
 * star-slash). The comment then ran on to a later star-slash in the databases whose comments
 * don't nest (mysql, sqlite), and hid the sql between them, such as a vpm condition. Such a
 * line is kept now, and every other comment line is removed as before.
 */
@Tag("core")
class XUtilClearCommentsBlockCommentTest {
   // the issue sql, after the vpm condition is written: the closer line is kept
   @Test
   void closerLineIsKept() {
      String sql = "-- vpm.tables: SA.ORDERS\n" +
         "select * from SA.ORDERS /* old version:\n" +
         "-- keep */\n" +
         "where (SA.ORDERS.ORDER_ID > 0) and (SA.ORDERS.STATE = 'NJ') /* end */";

      Lines lines = clear(sql);

      assertEquals("select * from SA.ORDERS /* old version:\n" +
                      "-- keep */\n" +
                      "where (SA.ORDERS.ORDER_ID > 0) and (SA.ORDERS.STATE = 'NJ') /* end */",
                   lines.sql);
      assertEquals(List.of("-- vpm.tables: SA.ORDERS\n"), lines.removed);
      assertEquals(List.of("-- keep */\n"), lines.kept);
      assertEquals(List.of(), lines.live, "the line only ends the comment");
   }

   // the tags of the issue sql are removed, the where clause is kept
   @Test
   void closerLineIsKeptWithTags() {
      String sql = "-- vpm.tables: SA.ORDERS\n" +
         "select * from SA.ORDERS /* old version:\n" +
         "-- keep */\n" +
         "where /*<where>*/SA.ORDERS.ORDER_ID > 0/*</where>*/ /* end */";

      assertEquals("select * from SA.ORDERS /* old version:\n" +
                      "-- keep */\n" +
                      "where SA.ORDERS.ORDER_ID > 0 /* end */", clear(sql).sql);
   }

   // an empty where tag after the closer line
   @Test
   void closerLineIsKeptBeforeEmptyWhereTag() {
      String sql = "select * from SA.ORDERS /* old:\n" +
         "-- keep */\n" +
         "/*<where>*//*</where>*/ /* end */";

      assertEquals("select * from SA.ORDERS /* old:\n-- keep */\n /* end */", clear(sql).sql);
   }

   // \r\n line ends
   @Test
   void closerLineIsKeptWithCrlf() {
      String sql = "-- vpm.tables: SA.ORDERS\r\n" +
         "select * from SA.ORDERS /* old version:\r\n" +
         "-- keep */\r\n" +
         "where SA.ORDERS.STATE = 'NJ' /* end */";

      Lines lines = clear(sql);

      assertEquals("select * from SA.ORDERS /* old version:\r\n" +
                      "-- keep */\r\n" +
                      "where SA.ORDERS.STATE = 'NJ' /* end */", lines.sql);
      assertEquals(List.of("-- keep */\r\n"), lines.kept);
   }

   // a tagged where clause commented out, that ends the comment: kept, tags and all, as the
   // database reads it
   @Test
   void taggedCloserLineIsKeptVerbatim() {
      String sql = "select * from SA.ORDERS /* old version:\n" +
         "--where /*<where>*/SA.ORDERS.ORDER_ID > 9/*</where>*/ */\n" +
         "where /*<where>*/SA.ORDERS.ORDER_ID > 0/*</where>*/ /* end */";

      Lines lines = clear(sql);

      assertEquals("select * from SA.ORDERS /* old version:\n" +
                      "--where /*<where>*/SA.ORDERS.ORDER_ID > 9/*</where>*/ */\n" +
                      "where SA.ORDERS.ORDER_ID > 0 /* end */", lines.sql);
      assertEquals(1, lines.live.size(), "sql follows the opener's star-slash in mysql");
   }

   // the closer line is the last line
   @Test
   void lastCloserLineIsKept() {
      assertEquals("select * from t /* a\n-- b */", clear("select * from t /* a\n-- b */").sql);
      assertEquals("select * from t /* a\n  -- b */ ",
                   clear("select * from t /* a\n  -- b */ ").sql);
   }

   // a live union branch after the closer is sql the database reads
   @Test
   void sqlAfterCloserIsLive() {
      String sql = "select * from t where /*<where>*/1=1/*</where>*/ /* old:\n" +
         "-- x */ union all select * from t\n" +
         "/* end */";

      Lines lines = clear(sql);

      assertEquals("select * from t where 1=1 /* old:\n" +
                      "-- x */ union all select * from t\n" +
                      "/* end */", lines.sql);
      assertEquals(List.of("-- x */ union all select * from t\n"), lines.live);
   }

   // a comment or blanks after the closer are not sql
   @Test
   void commentAfterCloserIsNotLive() {
      for(String line : new String[] {
         "-- keep */ -- note\n", "-- keep */ /* more */\n", "-- keep */   \n" })
      {
         Lines lines = clear("select 1 /* a\n" + line + "from t");

         assertEquals(List.of(line), lines.kept, line);
         assertEquals(List.of(), lines.live, line);
      }
   }

   // S5: a -- line inside a comment that doesn't end it is removed as before, byte for byte
   @Test
   void lineInsideCommentIsRemovedAsBefore() {
      for(String sql : new String[] {
         "select * from SA.ORDERS /* old version:\n-- keep\n*/\nwhere a = 1 /* end */",
         "select * from SA.ORDERS /* old version:\r\n-- keep\r\n*/\r\nwhere a = 1",
         "/* a\n-- b\n-- c\n*/ select 1",
         // nested: the line leaves the comment at the same depth
         "/* a /* b\n-- c\n*/ */ select 1",
         // a -- line not in a comment
         "-- vpm.tables: t\nselect 1 from t\n-- note */\n",
         "select 1 from t\n  --x */\nwhere a = 1",
         // the slash-star is in a literal, not a comment
         "select '/*' from t\n-- x */\nwhere a = 1",
         // a mid-line -- is not a comment line
         "select a /* x\n b -- c */ from t\nwhere a = 1",
         // the -- in a literal
         "select 'a\n-- b */' from t /* c */" })
      {
         Lines lines = clear(sql);

         assertEquals(before(sql), lines.sql, sql);
         assertEquals(List.of(), lines.kept, sql);
      }
   }

   // the comment ends on the line where comments don't nest (mysql), the line is kept
   @Test
   void nestedPairEndsCommentWhereCommentsDontNest() {
      assertEquals(List.of("-- see /* note */\n"),
                   clear("/* a\n-- see /* note */\nb */ select 1").kept);
   }

   // the line opens a nested comment (postgresql, sql server), the line is kept
   @Test
   void nestedOpenerChangesCommentDepth() {
      assertEquals(List.of("-- /* b\n"), clear("/* a\n-- /* b\n*/ */ select 1").kept);
      assertEquals(List.of("-- c */\n"), clear("/* a /* b\n-- c */\n*/ select 1").kept);
   }

   // a mysql # comment hides the slash-star from mysql only, the line is kept as the other
   // databases read it, and mysql reads the --x as sql
   @Test
   void hashCommentCorner() {
      Lines lines = clear("select 1 # x /*\n--x */\nfrom t");

      assertEquals(List.of("--x */\n"), lines.kept);
      assertEquals(List.of("--x */\n"), lines.live);
      assertEquals(List.of(), clear("select 1 # x /*\n-- x */\nfrom t").live);
   }

   // clearComments(XQuery) uses the same rule
   @Test
   void queryIsCleared() {
      UniformSQL sql = new UniformSQL();
      String text = "-- vpm.tables: t\nselect * from t /* a\n-- b */\nwhere x = 1 /* c */";
      sql.setSQLString(text, false);
      sql.setParseResult(UniformSQL.PARSE_FAILED);
      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(sql);

      JDBCQuery cleared = (JDBCQuery) XUtil.clearComments(query);

      assertEquals(text.substring(text.indexOf("select")),
                   ((UniformSQL) cleared.getSQLDefinition()).getSQLString());
   }

   // a realistic generated corpus: the sql is the same as before unless a line is kept, and
   // then it differs only by the kept lines. No line that ends a comment in the ansi reading
   // is removed
   @Test
   void corpusMatchesBeforeExceptKeptLines() {
      String[] parts = {
         "select * from t", " where a = 1", "/* c */", "/* open", "*/", " -- note", "'x'",
         "'/*'", "'*/'", "/*<where>*/a = 1/*</where>*/", "/*<1>*/t.a/*</1>*/", " # m /*",
         "\"q*/\"", "/*/", " union all select 1", "$$a*/$$", "{fn x()}" };
      String[] lineStarts = { "", "-- ", "--", "  -- ", "-- vpm.tables: t", "--x " };
      Random random = new Random(77788);
      int kept = 0;

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
            assertThrows(RuntimeException.class, () -> clear(sql), sql);
            continue;
         }

         Lines lines = clear(sql);
         List<int[]> ranges = new ArrayList<>();
         String withKept = beforeWithKept(sql, lines.kept, ranges);

         if(lines.kept.isEmpty()) {
            assertEquals(expected, lines.sql, sql);
         }
         else {
            kept++;
            assertEquals(withKept, lines.sql, sql);
         }

         int[] ansi = ansiBlockComments(sql);

         for(int[] range : ranges) {
            if(range[2] == 0) {
               assertFalse(endsComment(ansi, range[0], range[1]),
                           () -> "a line that ends a comment is removed: " +
                              sql.substring(range[0], range[1]) + " in " + sql);
            }
         }
      }

      assertTrue(kept > 100, "the corpus has too few kept lines: " + kept);
   }

   // linear time on adversarial input
   @Test
   void linearTime() {
      String[] inputs = {
         "/*\n-- */\n".repeat(120000),
         "/*".repeat(250000) + "\n-- x\n" + "*/".repeat(250000),
         "/* a\n" + "-- /* b\n".repeat(120000),
         "select 1\n" + "-- c\n".repeat(200000) };

      for(String sql : inputs) {
         assertTimeoutPreemptively(Duration.ofSeconds(20), () -> clear(sql));
      }
   }

   private static Lines clear(String sql) {
      Lines lines = new Lines();
      lines.sql = XUtil.clearComments(sql, lines.removed, lines.kept, lines.live);
      return lines;
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

   // the sql clearComments returned before the fix, with the kept lines in place. The comment
   // lines are matched to the kept lines by findCommentChanges, which clearComments uses. The
   // range of each comment line is added to ranges, with 1 if it is kept
   private static String beforeWithKept(String sql, List<String> kept, List<int[]> ranges) {
      List<Object> parts = new ArrayList<>();
      SQLIterator iterator = new SQLIterator(sql);
      iterator.addSQLListener((type, value, comment) -> {
         if(type == SQLIterator.COMMENT_ELEMENT) {
            parts.add(ranges.size());
            ranges.add(new int[] { iterator.getLineStart(), iterator.getLineEnd(), 0 });
         }
         else if(type == SQLIterator.TEXT_ELEMENT || type == SQLIterator.COLUMN_ELEMENT ||
            type == SQLIterator.WHERE_ELEMENT)
         {
            parts.add(value);
         }
      });
      iterator.iterate();

      boolean[] changed = SQLQuoteScanner.findCommentChanges(
         sql, ranges.stream().mapToInt(r -> r[0]).toArray(),
         ranges.stream().mapToInt(r -> r[1]).toArray(), null);
      StringBuilder sb = new StringBuilder();
      List<String> keptLines = new ArrayList<>();

      for(Object part : parts) {
         if(part instanceof String) {
            sb.append(part);
         }
         else if(changed[(Integer) part]) {
            int[] range = ranges.get((Integer) part);
            range[2] = 1;
            keptLines.add(sql.substring(range[0], range[1]));
            sb.append(sql, range[0], range[1]);
         }
      }

      assertEquals(keptLines, kept, sql);
      return sb.toString();
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

   // check if the line starts inside a slash-star comment and the comment ends on the line
   private static boolean endsComment(int[] block, int start, int end) {
      int comment = block[start];

      if(comment < 0) {
         return false;
      }

      for(int j = start; j <= end; j++) {
         if(block[j] != comment) {
            return true;
         }
      }

      return false;
   }

   private static class Lines {
      String sql;
      final List<String> removed = new ArrayList<>();
      final List<String> kept = new ArrayList<>();
      final List<String> live = new ArrayList<>();
   }
}

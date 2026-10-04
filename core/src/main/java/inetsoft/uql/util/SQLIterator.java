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

import inetsoft.util.Tool;

import java.util.*;

/**
 * SQL iterator iterates one sql string.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class SQLIterator {
   /**
    * Text element.
    */
   public static final int TEXT_ELEMENT = 1;
   /**
    * Column element.
    */
   public static final int COLUMN_ELEMENT = 2;
   /**
    * Where element.
    */
   public static final int WHERE_ELEMENT = 3;
   /**
    * Comment element.
    */
   public static final int COMMENT_ELEMENT = 4;
   /**
    * Comment table.
    */
   public static final int COMMENT_TABLE = 5;
   /**
    * Comment column.
    */
   public static final int COMMENT_COLUMN = 6;
   /**
    * Comment table alias.
    */
   public static final int COMMENT_ALIAS = 7;

   /**
    * Constructor.
    * @param sql the specified sql string.
    */
   public SQLIterator(String sql) {
      this.sql = sql;

      this.listeners = new ArrayList();
   }

   /**
    * Get the sql string.
    * @return the sql strings.
    */
   public String getSQL() {
      return sql;
   }

   /**
    * Add a sql listener.
    * @param listener the specified sql listener.
    */
   public void addSQLListener(SQLListener listener) {
      listeners.add(listener);
   }

   /**
    * remove a sql listener.
    * @param listener the specified sql listener.
    */
   public void removeSQLListener(SQLListener listener) {
      listeners.remove(listener);
   }

   /**
    * Get the count of all the sql listeners.
    * @return the count of all the sql listener.
    */
   public int getSQLListenerCount() {
      return listeners.size();
   }

   /**
    * Get the sql listener at an index.
    * @param index the specified index.
    * @return the sql listener at the index.
    */
   public SQLListener getSQLListener(int index){
      return (SQLListener) listeners.get(index);
   }

   /**
    * Iterate the sql string.
    */
   public void iterate() {
      // Bug #77663, a line break, a comment or a tag inside a literal or quoted name is the
      // text of the literal, not sql
      quoted = SQLQuoteScanner.findQuoted(sql);
      // Bug #77695, a tag inside a comment that every database reads as a comment (after a
      // mid-line --, or in a slash-star comment) is not sql, so it is not a tag. A tag opener
      // is not a comment itself, the text after it is sql
      opener = findOpeners(sql);
      lastCommentStart = new int[sql.length()];
      commentStart = SQLQuoteScanner.findComments(sql, i -> opener[i] != 0, lastCommentStart);
      lastCloser = null;
      int start = 0;

      for(int i = 0; i < sql.length(); i++) {
         char c = sql.charAt(i);

         // break line found?
         if(c == '\n' && !quoted[i]) {
            int end = i + 1;
            int pos = start;

            // ignore invisible char
            while(pos < end && sql.charAt(pos) <= ' ') {
               pos++;
            }

            String line = sql.substring(start, end);

            // is "--" pattern?
            if(pos + 1 < end && sql.charAt(pos) == '-' &&
               sql.charAt(pos + 1) == '-')
            {
               fireEvent(COMMENT_ELEMENT, line, null);
               line = sql.substring(pos + 2, end);
               iterateCommentLine(line);
            }
            else {
               String tag = iterateLine(line, start);

               if(tag != null) {
                  // the closing tag is after this line, searching from the start moved the
                  // cursor back to an earlier closing tag and never ended
                  int close = indexOfTag(sql, tag, i, 0);

                  if(close < 0) {
                     throw new RuntimeException("Invalid line found: " + line);
                  }

                  // move the cursor to the closing tag
                  i = close + tag.length() - 1;
                  continue;
               }
            }

            start = end;
         }
      }

      int end = sql.length();

      if(start < end) {
         int pos = start;

         while(pos < end && sql.charAt(pos) <= ' ') {
            pos++;
         }

         String line = sql.substring(start, end);

         if(pos + 1 < end && sql.charAt(pos) == '-' &&
            sql.charAt(pos + 1) == '-')
         {
            fireEvent(COMMENT_ELEMENT, line, null);
            line = sql.substring(pos + 2, end);
            iterateCommentLine(line);
         }
         // the last line is not closed by a later line either, so it is invalid as any other
         // line, instead of being dropped without its events
         else if(iterateLine(line, start) != null) {
            throw new RuntimeException("Invalid line found: " + line);
         }
      }
   }

   /**
    * Find a tag that is not inside quoted text.
    * @param text the text to search, the sql or a part of it.
    * @param from the index in the text to start from.
    * @param offset the index of the text in the sql.
    * @return the index of the tag in the text, or -1 if not found.
    */
   private int indexOfTag(String text, String tag, int from, int offset) {
      int index = text.indexOf(tag, from);

      while(index >= 0 && quoted[offset + index]) {
         index = text.indexOf(tag, index + 1);
      }

      return index;
   }

   /**
    * Iterator one comment line of the sql string.
    * @param line the specified comment line.
    */
   private void iterateCommentLine(String line) {
      line = line.trim();
      int id = -1;

      if(line.startsWith(CT_PREFIX)) {
         line = line.substring(CT_PREFIX.length());
         id = COMMENT_TABLE;
      }
      else if(line.startsWith(CA_PREFIX)) {
         line = line.substring(CA_PREFIX.length());
         id = COMMENT_ALIAS;
      }
      else if(line.startsWith(CC_PREFIX)) {
         line = line.substring(CC_PREFIX.length());
         id = COMMENT_COLUMN;
      }
      else {
         return;
      }

      int i = 0;

      for(; i < line.length(); i++) {
         if(line.charAt(i) > ' ') {
            break;
         }
      }

      if(i == line.length()) {
         return;
      }

      if(line.charAt(i) != ':') {
         return;
      }

      line = line.substring(i + 1);
      String[] result = Tool.split(line, ',');

      for(i = 0; i < result.length; i++) {
         fireEvent(id, result[i].trim(), Integer.valueOf(i));
      }
   }

   /**
    * Iterate one line of the sql string.
    * @param line the specified line.
    * @param offset the index of the line in the sql.
    * @return the closing tag if a tag is not closed in the line, or null.
    */
   private String iterateLine(String line, int offset) {
      if(line.length() == 0) {
         return null;
      }

      if(line.indexOf(TAG1) < 0 || line.indexOf(TAG2) < 0) {
         fireEvent(TEXT_ELEMENT, line, null);
         return null;
      }

      int i = 0;
      int index = -1;
      int sindex = -1;
      int eindex = -1;
      int state = TEXT_STATE;
      List<SQLIteratorEvent> events = new ArrayList<>();

      while(i < line.length()) {
         char c = line.charAt(i);
         char lc = i > 0 ? line.charAt(i - 1) : '\uffff';

         if(state == TEXT_STATE) {
            // Bug #77695, a column tag in a comment is still read if it is closed, as before,
            // since its value is removed from the sql (the comment may end at the tag's */)
            if(c != '*' || lc != '/' || quoted[offset + i - 1] ||
               commentStart[offset + i - 1] >= 0 && !isClosedColumnOpener(offset + i - 1) ||
               isCommentedWhereOpener(offset + i - 1))
            {
               i++;
               continue;
            }
            else {
               String text = line.substring(index + 1, i - 1);

               if(text.length() > 0) {
                  events.add(new SQLIteratorEvent(TEXT_ELEMENT, text, null));
               }

               state = COMMENT_STATE;
               sindex = i + 1;
               i++;
            }
         }
         else if(state == COMMENT_STATE) {
            // the * of the opening slash-star doesn't close the comment (/*/)
            if(c != '/' || lc != '*' || i - 1 < sindex) {
               i++;
               continue;
            }

            eindex = i - 1;
            String cname = line.substring(sindex, eindex);

            // Bug #77695, a name other than where or a column number is a regular comment
            if(cname.length() < 3 || cname.charAt(0) != '<' ||
               cname.charAt(cname.length() - 1) != '>' ||
               !isTagName(cname.substring(1, cname.length() - 1)))
            {
               String text = line.substring(sindex - 2, i + 1);

               // @by larryl, if this is a regular comment that doesn't contain
               // the special tag for vpm, we should pass it on to the final
               // sql since it could be a hint to db
               events.add(new SQLIteratorEvent(TEXT_ELEMENT, text, null));
               state = TEXT_STATE;
               // the closing / is the last character used, the text after it is kept
               index = i;
               i++;
               continue;
            }

            cname = cname.substring(1, cname.length() - 1);
            String rpattern = "/*</" + cname + ">*/";
            int index2 = indexOfTag(line, rpattern, i + 1, offset);

            if(index2 == -1) {
               return rpattern;
            }

            Object comment;
            int type;

            if(!cname.equals("where")) {
               comment = Integer.valueOf(getColumnNumber(cname) - 1);
               type = COLUMN_ELEMENT;
            }
            else {
               comment = null;
               type = WHERE_ELEMENT;
            }

            String val = line.substring(eindex + 2, index2);
            // a where closing tag in a comment of every database ends the value at the latest
            // start, as the opener (sql server #tmp, postgresql #>> or $$x--y$$ before a --)
            int cstart = (type == WHERE_ELEMENT ? lastCommentStart : commentStart)
               [offset + index2] - offset;

            // Bug #77695, the closing tag is in a -- comment that starts in the value, so the
            // value ends at the comment, which is passed on as text as the database reads it
            if(cstart >= eindex + 2 && line.startsWith("--", cstart)) {
               events.add(new SQLIteratorEvent(type, line.substring(eindex + 2, cstart), comment));
               events.add(new SQLIteratorEvent(TEXT_ELEMENT, line.substring(cstart, index2), null));
            }
            else {
               events.add(new SQLIteratorEvent(type, val, comment));
            }

            index = index2 + rpattern.length() - 1;
            i = index + 1;
            state = TEXT_STATE;
         }
      }

      if(index < line.length() - 1) {
         if(state == COMMENT_STATE) {
            // Bug #77695, a slash-star that is not a tag and is not closed on the line, in a
            // comment of every database wherever it starts (a -- comment after a sql server
            // #tmp table), is text of that comment, as a where opener there is, and as on a
            // line without a tag. No tag follows it on the line, since the tag's star-slash
            // would have closed it
            if(!isCommentedNonTag(offset + sindex - 2)) {
               throw new RuntimeException("Invalid line found: " + line);
            }

            index = sindex - 3;
         }

         String val = line.substring(index + 1);
         events.add(new SQLIteratorEvent(TEXT_ELEMENT, val, null));
      }

      // fire events at the end in case there are issues with the sql or the contents of
      // the special vpm tag are split across multiple lines
      for(SQLIteratorEvent event : events) {
         fireEvent(event.type, event.val, event.comment);
      }

      return null;
   }

   /**
    * Fire event.
    * @param type the event type.
    * @param value the event value.
    * @param comment the event comment.
    */
   private void fireEvent(int type, String val, Object comment) {
      for(int i = 0; i < getSQLListenerCount(); i++) {
         SQLListener listener = getSQLListener(i);
         listener.nextElement(type, val, comment);
      }
   }

   /**
    * SQL listener.
    */
   public static interface SQLListener {
      /**
       * Find the next element.
       * @param type the specified element type.
       * @param value the specified element value.
       * @param comment the specified comment.
       */
      public void nextElement(int type, String value, Object comment);
   }

   private static class SQLIteratorEvent {
      public SQLIteratorEvent(int type, String val, Object comment) {
         this.type = type;
         this.val = val;
         this.comment = comment;
      }

      int type;
      String val;
      Object comment;
   }

   /**
    * Find the tag openers of the sql, a slash-star tag <name> with a valid name, whether or not
    * they are quoted or in a comment.
    * @return WHERE_OPENER or COLUMN_OPENER at the index of each opener, 0 elsewhere.
    */
   private static byte[] findOpeners(String sql) {
      int len = sql.length();
      byte[] opener = new byte[len];

      // the name is read in place up to the first character that can't be in a valid name,
      // so each opener reads a few characters (a run of zeros is read by one opener only),
      // and the sql is scanned in linear time
      for(int i = sql.indexOf(TAG1); i >= 0; i = sql.indexOf(TAG1, i + 1)) {
         int end = getTagNameEnd(sql, i + 3);

         if(end > 0 && sql.startsWith(TAG2, end)) {
            opener[i] = sql.charAt(i + 3) == 'w' ? WHERE_OPENER : COLUMN_OPENER;
         }
      }

      return opener;
   }

   /**
    * Check if a where tag opens at the index inside a comment of every database family. The
    * comment may start at a different index in each family, such as at a sql server #tmp
    * table, a postgresql #>> operator or $$x--y$$ string, or a --x, which is a comment in
    * some databases, before the -- that is a comment in all of them. Each database reads the
    * tag in a comment, so it is not a tag, and the condition is not written into the comment.
    */
   private boolean isCommentedWhereOpener(int index) {
      return opener[index] == WHERE_OPENER && lastCommentStart[index] >= 0;
   }

   /**
    * Check if a slash-star that is not a tag opener is at the index inside a comment of every
    * database family, wherever the comment starts in each family (at the slash-star or before
    * it).
    */
   private boolean isCommentedNonTag(int index) {
      return opener[index] == 0 && lastCommentStart[index] >= 0;
   }

   /**
    * Check if a column tag opens at the index and its closing tag is found after it, not
    * quoted.
    */
   private boolean isClosedColumnOpener(int index) {
      if(opener[index] != COLUMN_OPENER) {
         return false;
      }

      int end = sql.indexOf("*/", index + 2);
      String rpattern = "/*</" + sql.substring(index + 3, end - 1) + ">*/";

      // the last closing tag of each name, found in one scan of the sql when first needed, so
      // the sql is not searched again for each name
      if(lastCloser == null) {
         lastCloser = findLastClosers();
      }

      return lastCloser.getOrDefault(rpattern, -1) > end;
   }

   /**
    * Find the last closing tag of each valid tag name that is not quoted.
    * @return the index of the last closing tag by the closing tag.
    */
   private Map<String, Integer> findLastClosers() {
      Map<String, Integer> closers = new HashMap<>();

      for(int x = sql.indexOf(CLOSER); x >= 0; x = sql.indexOf(CLOSER, x + 1)) {
         int end = getTagNameEnd(sql, x + 4);

         if(end > 0 && sql.startsWith(TAG2, end) && !quoted[x]) {
            closers.put(sql.substring(x, end + 3), x);
         }
      }

      return closers;
   }

   /**
    * Check if a tag name is valid: where, or a column number from 1.
    */
   private static boolean isTagName(String name) {
      return getTagNameEnd(name, 0) == name.length();
   }

   /**
    * Read a valid tag name at the index: where, or a column number from 1 as Integer.parseInt
    * reads it (a + sign and leading zeros, at most 9 digits from the first nonzero digit).
    * The characters are read in place up to the first one that can't be in the name.
    * @return the index after the name, or -1 if no valid name starts at the index.
    */
   private static int getTagNameEnd(String text, int start) {
      if(text.startsWith("where", start)) {
         return start + 5;
      }

      int len = text.length();
      int i = start;

      if(i < len && text.charAt(i) == '+') {
         i++;
      }

      while(i < len && text.charAt(i) == '0') {
         i++;
      }

      if(i >= len || text.charAt(i) < '1' || text.charAt(i) > '9') {
         return -1;
      }

      int first = i;
      i++;

      while(i < len && i - first < 9 && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
         i++;
      }

      return i;
   }

   /**
    * Get the number of a column tag name, which may have a + sign and leading zeros.
    */
   private static int getColumnNumber(String name) {
      return Integer.parseInt(name.startsWith("+") ? name.substring(1) : name);
   }

   private static final String CT_PREFIX = "vpm.tables";
   private static final String CA_PREFIX = "vpm.aliases";
   private static final String CC_PREFIX = "vpm.columns";
   private static final String TAG1 = "/*<";
   private static final String TAG2 = ">*/";
   private static final int TEXT_STATE = 0;
   private static final int COMMENT_STATE = 1;
   private static final byte WHERE_OPENER = 1;
   private static final byte COLUMN_OPENER = 2;
   private static final String CLOSER = "/*</";

   private String sql; // sql string
   private boolean[] quoted; // the characters of the sql inside quoted text
   private byte[] opener; // the tag openers of the sql
   private int[] commentStart; // the start of the comment holding each character, or -1
   // the latest start of the comments holding each character in every database family, or -1
   private int[] lastCommentStart;
   private Map<String, Integer> lastCloser; // see isClosedColumnOpener
   private List listeners; // sql listeners
}
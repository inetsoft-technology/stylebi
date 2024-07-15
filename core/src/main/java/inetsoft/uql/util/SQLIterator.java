/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.util;

import inetsoft.util.Tool;

import java.util.ArrayList;
import java.util.List;

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
      int start = 0;

      for(int i = 0; i < sql.length(); i++) {
         char c = sql.charAt(i);

         // break line found?
         if(c == '\n') {
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
               String tag = iterateLine(line);

               if(tag != null) {
                  if(!sql.contains(tag)) {
                     throw new RuntimeException("Invalid line found: " + line);
                  }

                  // move the cursor to the closing tag
                   i = sql.indexOf(tag) + tag.length() - 1;
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
         else {
            iterateLine(line);
         }
      }
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
    */
   private String iterateLine(String line) {
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
            if(c != '*' || lc != '/') {
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
            if(c != '/' || lc != '*') {
               i++;
               continue;
            }

            eindex = i - 1;
            String cname = line.substring(sindex, eindex);

            if(cname.length() < 3 || cname.charAt(0) != '<' ||
               cname.charAt(cname.length() - 1) != '>')
            {
               String text = line.substring(sindex - 2, i + 1);

               // @by larryl, if this is a regular comment that doesn't contain
               // the special tag for vpm, we should pass it on to the final
               // sql since it could be a hint to db
               events.add(new SQLIteratorEvent(TEXT_ELEMENT, text, null));
               state = TEXT_STATE;
               i++;
               index = i;
               continue;
            }

            cname = cname.substring(1, cname.length() - 1);
            String rpattern = "/*</" + cname + ">*/";
            int index2 = line.indexOf(rpattern, i + 1);

            if(index2 == -1) {
               return rpattern;
            }

            Object comment;
            int type;

            if(!cname.equals("where")) {
               comment = Integer.valueOf(Integer.parseInt(cname) - 1);
               type = COLUMN_ELEMENT;
            }
            else {
               comment = null;
               type = WHERE_ELEMENT;
            }

            String val = line.substring(eindex + 2, index2);
            events.add(new SQLIteratorEvent(type, val, comment));

            index = index2 + rpattern.length() - 1;
            i = index + 1;
            state = TEXT_STATE;
         }
      }

      if(index < line.length() - 1) {
         if(state == COMMENT_STATE) {
            throw new RuntimeException("Invalid line found: " + line);
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

   private static final String CT_PREFIX = "vpm.tables";
   private static final String CA_PREFIX = "vpm.aliases";
   private static final String CC_PREFIX = "vpm.columns";
   private static final String TAG1 = "/*<";
   private static final String TAG2 = ">*/";
   private static final int TEXT_STATE = 0;
   private static final int COMMENT_STATE = 1;

   private String sql; // sql string
   private List listeners; // sql listeners
}
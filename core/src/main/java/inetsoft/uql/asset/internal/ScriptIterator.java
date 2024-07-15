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
package inetsoft.uql.asset.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Script iterator iterates one script statement.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class ScriptIterator {
   /**
    * Constructor.
    * @param script the specified script statement.
    */
   public ScriptIterator(String script) {
      this.script = script;
      this.listeners = new ArrayList<>();
   }

   /**
    * Get the script statement.
    * @return the script statement.
    */
   public String getScript() {
      return script;
   }

   /**
    * Add a script listener.
    * @param listener the specified script listener.
    */
   public void addScriptListener(ScriptListener listener) {
      listeners.add(listener);
   }

   /**
    * remove a script listener.
    * @param listener the specified script listener.
    */
   public void removeScriptListener(ScriptListener listener) {
      listeners.remove(listener);
   }

   /**
    * Get the count of all the script listeners.
    * @return the count of all the script listener.
    */
   public int getScriptListenerCount() {
      return listeners.size();
   }

   /**
    * Get the script listener at an index.
    * @param index the specified index.
    * @return the script listener at the index.
    */
   public ScriptListener getScriptListener(int index) {
      return listeners.get(index);
   }

   /**
    * Iterate the script statement.
    */
   public void iterate() {
      if(script == null) {
         return;
      }

      List<Token> tokens = new ArrayList<>();
      char[] sarr = script.toCharArray(); // optimization
      state = UNKNOWN_STATE;
      index = -1;
      boolean comment = false;

      for(int i = 0; i < sarr.length; i++) {
         char c = sarr[i];
         char lc = i > 0 ? sarr[i - 1] : '\uffff';

         if(c == '/' && lc == '/') {
            comment = true;
         }
         else if(comment && c == '\n') {
            comment = false;
         }

         if(state == STRING_STATE) {
            if(c != '\"' || (i > 0 && lc == '\\')) {
               continue;
            }
            else {
               state = UNKNOWN_STATE;
            }
         }
         else if(state == STRING_STATE_2) {
            if(c != '\'' || (i > 0 && lc == '\\')) {
               continue;
            }
            else {
               state = UNKNOWN_STATE;
            }
         }
         else if(c == '\"' && (i <= 0 || lc != '\\')) {
            state = STRING_STATE;
         }
         else if(c == '\'' && (i <= 0 || lc != '\\')) {
            state = STRING_STATE_2;
         }
         else if(!comment && c == '.') {
            Token sref = findRef(i, false);

            if(sref != null) {
               int index2 = i - sref.length;

               if(index + 1 < index2) {
                  String sub = script.substring(index + 1, index2);
                  Token token = new Token(Token.TEXT, sub, sub.length());
                  tokens.add(token);
               }

               tokens.add(sref);
               index = i - 1;
               Token child = findNormalRef(i, true);

               if(child != null) {
                  boolean found = false;

                  for(int j = i + child.length + 1; j < sarr.length; j++) {
                     char c2 = sarr[j];

                     if(c2 <= ' ') {
                        continue;
                     }

                     if(c2 == '.' || c2 == '[') {
                        found = true;
                     }

                     break;
                  }

                  if(!found) {
                     tokens.add(new Token(Token.TEXT, ".", 1));
                     tokens.add(child);
                     index = i + child.length;
                     i = index + 1;
                  }
               }
            }
         }
         else if(c == '[') {
            Token sref = findRef(i, false);

            if(sref != null) {
               int index2 = i - sref.length;

               if(index + 1 < index2) {
                  String sub = script.substring(index + 1, index2);
                  Token token = new Token(Token.TEXT, sub, sub.length());
                  tokens.add(token);
               }

               tokens.add(sref);
               index = i - 1;
               Token child = findRef(i - 1, true);

               if(child != null) {
                  boolean found = false;

                  for(int j = i + child.length; j < sarr.length; j++) {
                     char c2 = sarr[j];

                     if(c2 <= ' ') {
                        continue;
                     }

                     if(c2 == '.' || c2 == '[') {
                        found = true;
                     }

                     break;
                  }

                  if(!found) {
                     tokens.add(child);
                     index = i + child.length - 1;
                     i = index + 1;
                  }
               }
            }
         }
      }

      if(index != script.length() - 1) {
         String sub = script.substring(index + 1);
         Token token = new Token(Token.TEXT, sub, sub.length());
         tokens.add(token);
      }

      for(int i = 0; i < tokens.size(); i++) {
         Token token = tokens.get(i);

         if(token.type == Token.TEXT) {
            Token cref = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            fireEvent(token, null, cref);
         }
         else {
            Token pref = getParentRef(tokens, i);
            Token cref = getChildRef(tokens, i);

            fireEvent(token, pref, cref);
         }
      }
   }

   /**
    * Get the parent ref.
    */
   protected Token getParentRef(List<Token> tokens, int pos) {
      if(pos - 1 < 0) {
         return null;
      }

      Token token = tokens.get(pos);
      Token ptoken = tokens.get(pos - 1);

      if(token.type == Token.DOUBLE_REF || token.type == Token.SINGLE_REF) {
         if(ptoken.isRef()) {
            return ptoken;
         }
      }
      else if(ptoken.isDot() && token.type == Token.NORMAL_REF) {
         if(pos - 2 < 0) {
            return null;
         }

         ptoken = tokens.get(pos - 2);

         if(ptoken.isRef()) {
            return ptoken;
         }
      }
      else if(ptoken.isRunQuery() && token.type == Token.TEXT) {
         return ptoken;
      }

      return null;
   }

   /**
    * Get the child ref.
    */
   protected Token getChildRef(List<Token> tokens, int pos) {
      if(pos + 1 >= tokens.size()) {
         return null;
      }

      Token token = tokens.get(pos);
      Token ctoken = tokens.get(pos + 1);

      if(ctoken.type == Token.DOUBLE_REF || ctoken.type == Token.SINGLE_REF) {
         return ctoken;
      }
      else if(ctoken.isDot()) {
         if(pos + 2 >= tokens.size()) {
            return null;
         }

         ctoken = tokens.get(pos + 2);

         if(ctoken.type == Token.NORMAL_REF) {
            return ctoken;
         }
      }

      return null;
   }

   /**
    * Fire event.
    */
   protected void fireEvent(Token token, Token pref, Token cref) {
      inIter.set(true);

      try {
         for(int i = 0; i < getScriptListenerCount(); i++) {
            ScriptListener listener = getScriptListener(i);
            listener.nextElement(token, pref, cref);
         }
      }
      finally {
         inIter.remove();
      }
   }

   /**
    * Check if the thread is from ScriptIterator.
    */
   public static boolean isProcessing() {
      return inIter.get();
   }

   /**
    * If set to true, variable names are used to lookup assembly in viewsheet.
    */
   public static void setProcessing(boolean flag) {
      inIter.set(flag);
   }

   /**
    * Find ref.
    */
   protected Token findRef(int pos, boolean forward) {
      Token sref = findQuotationRef(pos, forward);

      if(sref != null) {
         return sref;
      }

      return findNormalRef(pos, forward);
   }

   /**
    * Find the quotation bracket ref.
    */
   protected Token findQuotationRef(int pos, boolean forward) {
      int spos = -1;
      int epos = -1;
      int delta = forward ? 1 : -1;
      int schar = forward ? '[' : ']';
      int echar = forward ? ']' : '[';
      int bcount = 0;
      char[] sarr = script.toCharArray(); // optimization

      for(int i = forward ? pos + 1 : pos - 1; i >= 0 && i < sarr.length;
         i += delta)
      {
         char c = sarr[i];

         if(spos == -1) {
            if(c <= ' ') {
               bcount++;
               continue;
            }

            if(c != schar) {
               return null;
            }

            spos = i;
         }

         if(c == echar) {
            epos = i;
            break;
         }
      }

      if(spos == -1 || epos == -1) {
         return null;
      }

      String sub = forward ? script.substring(spos + 1, epos) :
         script.substring(epos + 1, spos);

      if(sub.startsWith("'") && sub.endsWith("'") && sub.length() > 2) {
         return new Token(Token.SINGLE_REF, sub.substring(1, sub.length() - 1),
                          sub.length() + bcount + 2);
      }
      else if(sub.startsWith("\"") && sub.endsWith("\"") && sub.length() > 2) {
         return new Token(Token.DOUBLE_REF, sub.substring(1, sub.length() - 1),
                          sub.length() + bcount + 2);
      }

      return null;
   }

   /**
    * Find the normal ref.
    */
   protected Token findNormalRef(int pos, boolean forward) {
      int spos = -1;
      int epos = -1;
      int delta = forward ? 1 : -1;
      int bcount = 0;
      char[] sarr = script.toCharArray(); // optimization

      for(int i = forward ? pos + 1 : pos - 1; i >= 0 && i < sarr.length;
         i += delta)
      {
         char c = sarr[i];

         if(spos == -1) {
            if(c <= ' ') {
               bcount++;
               continue;
            }

            if(!Character.isJavaIdentifierPart(c)) {
               return null;
            }

            spos = i;
         }

         if(Character.isJavaIdentifierPart(c)) {
            epos = i;
         }
         else {
            break;
         }
      }

      if(spos == -1 || epos == -1) {
         return null;
      }

      String name = forward ? script.substring(spos, epos + 1) :
         script.substring(epos, spos + 1);
      return new Token(Token.NORMAL_REF, name, name.length() + bcount);
   }

   /**
    * Script Token.
    */
   public static class Token {
      /**
       * Double quotation ref token.
       */
      public static final int DOUBLE_REF = 1;
      /**
       * Single quotation ref token.
       */
      public static final int SINGLE_REF = 2;
      /**
       * Normal ref token.
       */
      public static final int NORMAL_REF = 4;
      /**
       * Text token.
       */
      public static final int TEXT = 8;

      /**
       * Constructor.
       */
      public Token(int type, String val, int length) {
         super();

         this.type = type;
         this.val = val;
         this.length = length;
      }

      /**
       * Check if this token is a ref.
       * @return <tt>true</tt> if is a ref, <tt>false</tt> otherwise.
       */
      public boolean isRef() {
         return type == DOUBLE_REF || type == NORMAL_REF || type == SINGLE_REF;
      }

      /**
       * Check if this token is a dot.
       * @return <tt>true</tt> if is a dot, <tt>false</tt> otherwise.
       */
      public boolean isDot() {
         return ".".equals(val.trim());
      }

      /**
       * Check if this token is runQuery.
       * @return <tt>true</tt> if is runQuery, <tt>false</tt> otherwise.
       */
      public boolean isRunQuery() {
         return "runQuery".equals(val.trim());
      }

      /**
       * Check if this token is blank.
       * @return <tt>true</tt> if blank, <tt>false</tt> otherwise.
       */
      public boolean isBlank() {
         if(type != TEXT) {
            return false;
         }

         for(int i = 0; i < val.length(); i++) {
            if(val.charAt(i) > ' ') {
               return false;
            }
         }

         return true;
      }

      /**
       * Get the string representation.
       * @return the string representation.
       */
      public String toString() {
         if(type == DOUBLE_REF) {
            return "[\"" + val + "\"]";
         }
         else if(type == SINGLE_REF) {
            return "['" + val + "']";
         }
         else {
            return val;
         }
      }

      public int type;
      public String val;
      public int length;
   }

   /**
    * Script listener.
    */
   public interface ScriptListener {
      /**
       * Find the next element.
       * @param token current token.
       * @param pref parent ref token.
       * @param cref child ref token.
       */
      void nextElement(Token token, Token pref, Token cref);
   }

   private static final ThreadLocal<Boolean> inIter = ThreadLocal.withInitial(() -> false);
   protected static final int UNKNOWN_STATE = 1; // normal
   protected static final int STRING_STATE = 2; // double quote
   protected static final int STRING_STATE_2 = 3; // quote

   protected String script; // script statement
   protected List<ScriptListener> listeners; // script listeners
   protected int state; // current state
   protected int index; // current index
}

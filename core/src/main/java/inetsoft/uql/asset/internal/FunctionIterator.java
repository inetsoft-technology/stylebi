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

import java.util.*;

/**
 * Function iterator iterates one script statement to find function name.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class FunctionIterator extends ScriptIterator {
   /**
    * Constructor.
    * @param script the specified script statement.
    */
   public FunctionIterator(String script) {
      super(script);
   }

   /**
    * Iterate the script statement.
    */
   @Override
   public void iterate() {
      List<Token> tokens = new ArrayList<>();
      char[] sarr = script.toCharArray(); // optimization
      state = UNKNOWN_STATE;
      index = -1;

      for(int i = 0; i < sarr.length; i++) {
         char c = sarr[i];
         char lc = i > 0 ? sarr[i - 1] : '\uffff';

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
         else if(c == '=') {
            for(int j = i + 1; j < sarr.length; j++) {
               char c1 = sarr[j];

               if(c1 <= ' ') {
                  continue;
               }

               if(c1 == '\"' || c1 == '\'') {
                  int state0 = c1 == '\"' ? STRING_STATE : STRING_STATE_2;
                  int endIndex = 0;

                  for(int k = j + 1; k < sarr.length; k++) {
                     if(state0 == STRING_STATE ?
                        sarr[k] == '\"' : sarr[k] == '\'')
                     {
                        endIndex = k;
                        break;
                     }
                  }

                  if(endIndex != 0) {
                     Token keyRef = findRef(i, false);

                     if(keyRef != null) {
                        variablesMap.put(keyRef.val.trim(),
                           script.substring(j + 1, endIndex).trim());
                     }
                  }
               }

               break;
            }
         }
         else if(c == '(') {
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

                     if(c2 == '(') {
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
         Token pref = getParentRef(tokens, i);

         if(token.type == Token.TEXT) {
            if(pref != null && pref.isRunQuery()) {
               fireEvent(token, pref, null);
            }
            else {
               fireEvent(token, null, null);
            }
         }
         else {
            Token cref = getChildRef(tokens, i);
            fireEvent(token, pref, cref);
         }
      }
   }

   /**
    * Get the variable value.
    */
   public String getVariable(String key) {
      return variablesMap.get(key);
   }

   /**
    * Find the quotation bracket ref.
    */
   @Override
   protected Token findQuotationRef(int pos, boolean forward) {
      int spos = -1;
      int epos = -1;
      int delta = forward ? 1 : -1;
      int schar = forward ? '(' : ')';
      int echar = forward ? ')' : '(';
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
      else {
         char end = ' ';
         int type = -1;

         if(sub.startsWith("'")) {
            end = '\'';
            type = Token.SINGLE_REF;
         }
         else if(sub.startsWith("\"")) {
            end = '\"';
            type = Token.DOUBLE_REF;
         }

         if(end != ' ' && sub.length() > 1) {
            int idx2 = sub.indexOf(end, 1);

            if(idx2 > 0) {
               return new Token(Token.SINGLE_REF, sub.substring(1, idx2),
                                sub.length() + bcount + 2);
            }
         }
      }

      return null;
   }

   private final Map<String, String> variablesMap = new HashMap<>();
}

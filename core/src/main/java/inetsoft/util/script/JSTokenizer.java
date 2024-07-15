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
package inetsoft.util.script;

import org.apache.commons.text.StringTokenizer;
import org.apache.commons.text.matcher.StringMatcher;
import org.apache.commons.text.matcher.StringMatcherFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for working with js tokens.
 *
 * @version 13.2, 4/26/2020
 * @author InetSoft Technology Corp
 */
public class JSTokenizer {
   public static boolean containsTokens(String str, String... tokens) {
      Set<String> set = new HashSet<>();
      set.addAll(Arrays.asList(tokens));
      StringTokenizer tokenizer = getStringTokenizer(str);

      while(tokenizer.hasNext()) {
         final String token = tokenizer.next();

         if(set.contains(token)) {
            return true;
         }
      }

      return false;
   }

   private static StringTokenizer getStringTokenizer(String str) {
      StringTokenizer tokenizer = new StringTokenizer(str);
      tokenizer.setQuoteMatcher(StringMatcherFactory.INSTANCE.quoteMatcher());
      tokenizer.setDelimiterMatcher(new StringMatcher() {
         @Override
         public int isMatch(char[] chars, int pos, int i1, int i2) {
            char ch = chars[pos];
            return Character.isJavaIdentifierPart(ch) ? 0 : 1;
         }
      });
      return tokenizer;
   }

   public static List<String> tokenize(String input) {
      List<String> tokens = new ArrayList<>();
      Pattern pattern = Pattern.compile("([\"'].*?[\"']|\\w+|\\p{Punct})");
      Matcher matcher = pattern.matcher(input);

      while (matcher.find()) {
         String token = matcher.group(1);
         tokens.add(token);
      }

      return tokens;
   }

   // check if the expr is a single function call.
   public static boolean isFunctionCall(String expr, String func) {
      expr = expr.trim();

      if(expr.startsWith(func + "(") && expr.endsWith(")")) {
         List<String> tokens = tokenize(expr.substring(func.length() + 1));
         int cnt = 1;

         for(String token : tokens) {

            if("(".equals(token)) {
               cnt++;
            }
            else if(")".equals(token) && cnt > 0) {
               cnt--;
            }
            // alrithmic between functions.
            else if(cnt == 0 && ("+".equals(token) || "-".equals(token) ||
               "*".equals(token) || "/".equals(token)))
            {
               return false;
            }
         }

         return cnt == 0 && ")".equals(tokens.get(tokens.size() - 1));
      }

      return false;
   }
}

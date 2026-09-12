/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.util.cachefs;

import java.util.regex.PatternSyntaxException;

// copied from sun.nio.fs.Globs
class Globs {
   private Globs() {
   }

   private static boolean isRegexMeta(char c) {
      return regexMetaChars.indexOf(c) != -1;
   }

   private static boolean isGlobMeta(char c) {
      return globMetaChars.indexOf(c) != -1;
   }

   private static char next(String glob, int i) {
      if(i < glob.length()) {
         return glob.charAt(i);
      }

      return EOL;
   }

   public static String toRegexPattern(String globPattern) {
      boolean inGroup = false;
      StringBuilder regex = new StringBuilder("^");

      int i = 0;
      while(i < globPattern.length()) {
         char c = globPattern.charAt(i++);
         switch(c) {
         case '\\':
            // escape special characters
            if(i == globPattern.length()) {
               throw new PatternSyntaxException("No character to escape",
                                                globPattern, i - 1);
            }
            char next = globPattern.charAt(i++);
            if(isGlobMeta(next) || isRegexMeta(next)) {
               regex.append('\\');
            }
            regex.append(next);
            break;
         case '/':
            regex.append(c);
            break;
         case '[':
            // don't match name separator in class
            regex.append("[[^/]&&[");
            if(next(globPattern, i) == '^') {
               // escape the regex negation char if it appears
               regex.append("\\^");
               i++;
            }
            else {
               // negation
               if(next(globPattern, i) == '!') {
                  regex.append('^');
                  i++;
               }
               // hyphen allowed at start
               if(next(globPattern, i) == '-') {
                  regex.append('-');
                  i++;
               }
            }
            boolean hasRangeStart = false;
            char last = 0;
            while(i < globPattern.length()) {
               c = globPattern.charAt(i++);
               if(c == ']') {
                  break;
               }
               if(c == '/') {
                  throw new PatternSyntaxException("Explicit 'name separator' in class",
                                                   globPattern, i - 1);
               }
               if(c == '\\' || c == '[' ||
                  c == '&' && next(globPattern, i) == '&')
               {
                  // escape '\', '[' or "&&" for regex class
                  regex.append('\\');
               }
               regex.append(c);

               if(c == '-') {
                  if(!hasRangeStart) {
                     throw new PatternSyntaxException("Invalid range",
                                                      globPattern, i - 1);
                  }
                  if((c = next(globPattern, i++)) == EOL || c == ']') {
                     break;
                  }
                  if(c < last) {
                     throw new PatternSyntaxException("Invalid range",
                                                      globPattern, i - 3);
                  }
                  regex.append(c);
                  hasRangeStart = false;
               }
               else {
                  hasRangeStart = true;
                  last = c;
               }
            }
            if(c != ']') {
               throw new PatternSyntaxException("Missing ']", globPattern, i - 1);
            }
            regex.append("]]");
            break;
         case '{':
            if(inGroup) {
               throw new PatternSyntaxException("Cannot nest groups",
                                                globPattern, i - 1);
            }
            regex.append("(?:(?:");
            inGroup = true;
            break;
         case '}':
            if(inGroup) {
               regex.append("))");
               inGroup = false;
            }
            else {
               regex.append('}');
            }
            break;
         case ',':
            if(inGroup) {
               regex.append(")|(?:");
            }
            else {
               regex.append(',');
            }
            break;
         case '*':
            if(next(globPattern, i) == '*') {
               // crosses directory boundaries
               regex.append(".*");
               i++;
            }
            else {
               // within directory boundary
               regex.append("[^/]*");
            }
            break;
         case '?':
            regex.append("[^/]");
            break;

         default:
            if(isRegexMeta(c)) {
               regex.append('\\');
            }
            regex.append(c);
         }
      }

      if(inGroup) {
         throw new PatternSyntaxException("Missing '}", globPattern, i - 1);
      }

      return regex.append('$').toString();
   }

   private static final String regexMetaChars = ".^$+{[]|()";
   private static final String globMetaChars = "\\*?[{";
   private static final char EOL = 0;  //TBD
}

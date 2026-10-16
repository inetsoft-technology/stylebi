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
package inetsoft.web.wiz.viewsheet;

import java.util.regex.Pattern;

/**
 * The Composer's viewsheet assembly-name rule, ported from the Angular validators
 * (nameSpecialCharacters, doesNotStartWithNumber, notWhiteSpace) so that names set through the
 * wiz tools and the shared property funnel obey the same rule as the Composer UI.
 */
public final class AssemblyNameRules {
   private AssemblyNameRules() {
   }

   private static final Pattern ALLOWED =
      Pattern.compile("[＀-￯一-龥a-zA-Z0-9 $#_%&,?!@'\\-]*");

   public static final String RULE_TEXT =
      "The Composer allows only letters, digits, Chinese characters, spaces and " +
      "$ # _ % & - , ? ! @ ' ; it must not start with a digit, be only whitespace, " +
      "end with a fullwidth colon or question mark, or be wrapped in single quotes.";

   /**
    * Returns the reason the name violates the rule, or null when it is valid.
    */
   public static String violation(String name) {
      if(name == null || name.isEmpty()) {
         return "the name is empty";
      }

      if(name.trim().isEmpty() || name.chars().allMatch(Character::isWhitespace)) {
         return "the name is only whitespace";
      }

      if(!ALLOWED.matcher(name).matches()) {
         return "the name contains a character that is not allowed";
      }

      char last = name.charAt(name.length() - 1);

      if(last == '\uFF1A' || last == '\uFF1F') {
         return "the name ends with a fullwidth colon or question mark";
      }

      if(name.startsWith("'") && name.endsWith("'")) {
         return "the name is wrapped in single quotes";
      }

      char first = name.charAt(0);

      if(first >= '0' && first <= '9') {
         return "the name starts with a digit";
      }

      return null;
   }

   public static boolean isValid(String name) {
      return violation(name) == null;
   }

   /**
    * @param context the prefix of the error, e.g. {@code "Edit op 'rename': 'newName'"}.
    */
   public static void requireValid(String context, String name) {
      String reason = violation(name);

      if(reason != null) {
         throw new IllegalArgumentException(
            context + " \"" + name + "\" is not a valid assembly name (" + reason + "). " +
            RULE_TEXT);
      }
   }
}

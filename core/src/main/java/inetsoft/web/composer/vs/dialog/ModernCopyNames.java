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
package inetsoft.web.composer.vs.dialog;

import inetsoft.util.Catalog;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class ModernCopyNames {
   private ModernCopyNames() {
   }

   /**
    * Gets the first free copy name, or null when none up to 999 is free.
    */
   public static String suggest(String baseName, Predicate<String> taken, Catalog catalog) {
      String name = catalog.getString("composer.vs.modernize.copyName", baseName);

      if(!taken.test(name)) {
         return name;
      }

      for(int i = 2; i <= 999; i++) {
         name = catalog.getString("composer.vs.modernize.copyNameN", baseName, i);

         if(!taken.test(name)) {
            return name;
         }
      }

      return null;
   }

   /**
    * Gets the message for a name the save rules refuse, or null when the name is valid.
    */
   public static String invalidMessage(String name, Catalog catalog) {
      if(name == null || name.isEmpty()) {
         return null;
      }

      if(!START.matcher(name).find()) {
         return catalog.getString("asset.tree.checkStart");
      }

      if(BANNED.matcher(name).find()) {
         return catalog.getString("composer.sheet.checkSpeChar");
      }

      return null;
   }

   private static final Pattern BANNED = Pattern.compile("[\\\\/\"<%^~]");
   private static final Pattern START =
      Pattern.compile("^[a-zA-Z0-9\\u00c0-\\u024f\\u4e00-\\u9fa5]");
}

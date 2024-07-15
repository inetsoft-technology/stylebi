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
package inetsoft.report.io;

public abstract class ArabicTextUtil {
   /**
    * Returns true if the string contains any arabic characters.
    *
    * @param text The string to test.
    *
    * @return True if at least one char is arabic, false otherwise.
    */
   public abstract boolean containsArabic(String text);

   /**
    * Where possible substitutes plain arabic glyphs with their shaped
    * forms.  This is needed when the arabic text is rendered using
    * an AWT font.  Simple arabic ligatures will also be recognised
    * and replaced by a single character so the length of the
    * resulting string may be shorter than the number of characters
    * in the given text.
    *
    * @param text Contains the text to process.
    *
    * @return A String containing the shaped versions of the arabic characters
    */
   public abstract String createSubstituteString(String text);

   /**
    * Returns a mirror character if one exists for a given character
    *
    * @param c character for which its mirror should be returned
    *
    * @return mirror character if one exists; otherwise returns the passed in character
    */
   public abstract char getMirrorCharacter(char c);

   public static ArabicTextUtil getInstance() {
      if(instance == null) {
         try {
            Class<?> clazz = ArabicTextUtil.class.getClassLoader()
               .loadClass("inetsoft.report.io.ArabicTextUtilImpl");
            instance = (ArabicTextUtil) clazz.getConstructor().newInstance();
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to create ArabicTextUtil instance", e);
         }
      }

      return instance;
   }

   private static ArabicTextUtil instance;
}

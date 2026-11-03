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
package inetsoft.report.io.viewsheet.html;

/**
 * Escaping rules shared by the HTML viewsheet export writers.
 *
 * The correct escaping depends on the output context, not on the writer:
 * <ul>
 *   <li>Element text and titles (cells, labels, selection labels, Text assembly, component
 *   titles): {@link #text}. Data such as {@code <none>} or {@code a<b}, and a title such as
 *   {@code <html>...</html>}, must reach the file as written, so no string is treated as
 *   markup.</li>
 *   <li>Single-quoted attribute values: {@link #attribute}.</li>
 * </ul>
 * HTMLPresenter cells and annotation rich text are written raw by their callers and never
 * pass through here. Raw markup is not sanitized.
 */
final class HTMLEscape {
   private HTMLEscape() {
   }

   /**
    * Escape text for use as element content.
    */
   static String text(Object value) {
      return escape(value == null ? null : value.toString(), false);
   }

   /**
    * Escape a value for use inside a single (or double) quoted attribute.
    */
   static String attribute(Object value) {
      return escape(value == null ? null : value.toString(), true);
   }

   private static String escape(String str, boolean attribute) {
      if(str == null) {
         return null;
      }

      StringBuilder buf = null;

      for(int i = 0; i < str.length(); i++) {
         char c = str.charAt(i);
         String rep;

         switch(c) {
         case '&':
            rep = "&amp;";
            break;
         case '<':
            rep = "&lt;";
            break;
         case '>':
            rep = "&gt;";
            break;
         case '\'':
            rep = attribute ? "&#39;" : null;
            break;
         case '"':
            rep = attribute ? "&quot;" : null;
            break;
         default:
            rep = null;
         }

         if(rep != null) {
            if(buf == null) {
               buf = new StringBuilder(str.length() + 16);
               buf.append(str, 0, i);
            }

            buf.append(rep);
         }
         else if(buf != null) {
            buf.append(c);
         }
      }

      return buf == null ? str : buf.toString();
   }
}

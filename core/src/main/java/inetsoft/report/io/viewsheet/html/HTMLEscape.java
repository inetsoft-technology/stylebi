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

import java.util.regex.Pattern;

/**
 * Escaping rules shared by the HTML viewsheet export writers.
 *
 * The correct escaping depends on the output context, not on the writer:
 * <ul>
 *   <li>Element text with viewer-HTML semantics (plain table/crosstab cells, selection
 *   labels, Text assembly): {@link #viewerText} and {@link #viewerCellText} keep the
 *   string raw where the portal viewer would render it as markup and escape it otherwise.</li>
 *   <li>Element text without such semantics (input labels, combo box labels,
 *   textarea): {@link #text}.</li>
 *   <li>Single-quoted attribute values: {@link #attribute}.</li>
 * </ul>
 * HTMLPresenter cells and annotation rich text are written raw by their callers and never
 * pass through here. Raw text is not sanitized (the viewer sanitizes, the export does not).
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

   /**
    * Mirror of the viewer's plain table cell rule (vs-table-cell.component.ts updateCellType):
    * a string that contains a tag like {@code <b>} is HTML, everything else is text. A newline
    * alone does not make the text raw here, so {@code a<b} followed by a newline keeps its text;
    * callers convert newlines to {@code <br>} after calling this method.
    */
   static String viewerCellText(Object value) {
      String str = value == null ? null : value.toString();
      return str != null && CELL_TAG.matcher(str).find() ? str : text(str);
   }

   /**
    * Mirror of the viewer's rule for selection labels and Text assembly (an HTML parse that
    * finds an element). Approximated by a start tag with optional attributes.
    */
   static String viewerText(Object value) {
      String str = value == null ? null : value.toString();
      return str != null && START_TAG.matcher(str).find() ? str : text(str);
   }

   /**
    * Mirror of the viewer's title rule (vs-title/title-cell bind GuiTool.getHTMLText with
    * innerHTML): a title wrapped in html tags is unwrapped and kept as markup, other
    * text follows the {@link #viewerText} rule.
    */
   static String viewerTitle(Object value) {
      String str = value == null ? null : value.toString();

      if(str != null && str.startsWith("<html>") && str.endsWith("</html>") && str.length() >= 13) {
         return str.substring(6, str.length() - 7);
      }

      return viewerText(str);
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

   private static final Pattern CELL_TAG = Pattern.compile("<[a-zA-Z]+>");
   private static final Pattern START_TAG = Pattern.compile("<[a-zA-Z][^<>]*>");
}

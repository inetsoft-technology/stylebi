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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Escaping rules shared by the HTML viewsheet export writers.
 *
 * The correct escaping depends on the role of the string, not on the writer:
 * <ul>
 *   <li>Author-written text, i.e. the content of a Text assembly and component titles: {@link
 *   #markup}. The viewer renders these through {@code GuiTool.getHTMLText} into innerHTML, so
 *   the export treats them as markup as well.</li>
 *   <li>Data and values (cells, input labels, selection labels, selected values, option
 *   labels): {@link #text}. Data such as {@code <none>} or {@code a<b} must reach the file as
 *   written, so it is never treated as markup.</li>
 *   <li>Single-quoted attribute values: {@link #attribute}.</li>
 * </ul>
 * HTMLPresenter cells and annotation rich text are written raw by their callers and never
 * pass through here.
 *
 * Known limit: the markup path is written raw, without sanitizing, as the export did before
 * #79693. The viewer's Angular innerHTML binding strips script and event handlers; the export
 * does not. The content is author-controlled, but a Text assembly can also contain values
 * substituted from variables.
 */
final class HTMLEscape {
   private static final Pattern LEADING_SPACES = Pattern.compile("^ +", Pattern.MULTILINE);

   private HTMLEscape() {
   }

   /**
    * Return author-written text (Text assembly content, component title) as HTML markup.
    * Mirrors the portal's {@code GuiTool.getHTMLText}: text wrapped in {@code <html>} and
    * {@code </html>} is unwrapped and otherwise left as is; any other text has trailing
    * whitespace removed, newlines turned into {@code <br>}, and leading spaces and double
    * spaces turned into non-breaking spaces. The result is not escaped or sanitized.
    */
   static String markup(Object value) {
      String str = value == null ? null : value.toString();

      if(str == null || str.isEmpty()) {
         return "";
      }

      if(str.startsWith("<html>") && str.endsWith("</html>")) {
         return str.substring(6, str.length() - 7);
      }

      int end = str.length();

      while(end > 0 && isJsWhitespace(str.charAt(end - 1))) {
         end--;
      }

      String result = str.substring(0, end).replace("\n", "<br>");
      Matcher matcher = LEADING_SPACES.matcher(result);
      StringBuffer buf = new StringBuffer();

      while(matcher.find()) {
         matcher.appendReplacement(buf, matcher.group().replace(" ", "&nbsp;"));
      }

      matcher.appendTail(buf);
      return buf.toString().replace("  ", " &nbsp;");
   }

   // whitespace as defined by JavaScript's String.prototype.trimEnd
   private static boolean isJsWhitespace(char c) {
      return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\uFEFF';
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

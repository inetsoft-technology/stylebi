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
package inetsoft.web.wiz.service;

import java.util.regex.Pattern;

/**
 * Converts markdown (as produced for a saved visualization's "insights") into plain, wrapped
 * text for the board export's PDF/PPTX layouts. A defensive regex-based transform, never a real
 * markdown parser — it cannot throw on malformed/unbalanced markup; worst case is a stray
 * leftover symbol in the output.
 */
public final class MarkdownPlainText {
   private MarkdownPlainText() {
   }

   public static String strip(String markdown) {
      if(markdown == null) {
         return "";
      }

      String[] lines = markdown.split("\n", -1);
      StringBuilder out = new StringBuilder();

      for(int i = 0; i < lines.length; i++) {
         String line = lines[i];
         String trimmed = line.strip();
         String result;

         if(HEADER.matcher(trimmed).find()) {
            result = HEADER.matcher(trimmed).replaceFirst("");
         }
         else if(BULLET.matcher(trimmed).find()) {
            result = BULLET.matcher(trimmed).replaceFirst("• ");
         }
         else {
            result = line;
         }

         result = BOLD_STAR.matcher(result).replaceAll("$1");
         result = BOLD_UNDERSCORE.matcher(result).replaceAll("$1");
         result = ITALIC_STAR.matcher(result).replaceAll("$1");
         result = ITALIC_UNDERSCORE.matcher(result).replaceAll("$1");

         out.append(result);

         if(i < lines.length - 1) {
            out.append("\n");
         }
      }

      return out.toString();
   }

   private static final Pattern HEADER = Pattern.compile("^#{1,6}\\s+");
   private static final Pattern BULLET = Pattern.compile("^[-*+]\\s+");
   private static final Pattern BOLD_STAR = Pattern.compile("\\*\\*(.+?)\\*\\*");
   private static final Pattern BOLD_UNDERSCORE = Pattern.compile("__(.+?)__");
   private static final Pattern ITALIC_STAR = Pattern.compile("\\*(.+?)\\*");
   private static final Pattern ITALIC_UNDERSCORE = Pattern.compile("_(.+?)_");
}

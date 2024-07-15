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
package inetsoft.report.io.excel;

import inetsoft.report.io.rtf.RichText;
import inetsoft.report.io.rtf.RichTextFont;

import java.util.*;

/**
 * This is the rich-text cell class.
 *
 * @version 10.2, 12/21/2009
 * @author InetSoft Technology Corp
 */
public class RichTextString {
   public static final short NO_FONT = 0;

   /**
    * Get the rich-text string.
    */
   public String getContent() {
      return content == null ? "" : content;
   }

   /**
    * Set the rich-text string.
    */
   public void setContent(String content) {
      this.content = content;
   }

   /**
    * Get the rich-text font.
    */
   public RichTextFont getFont(int idx) {
      return (RichTextFont) fontMap.get(idx);
   }

   /**
    * Set the rich-text font.
    */
   public void setFont(int idx, RichTextFont font) {
      if(!fontMap.containsKey(idx)) {
         fontMap.put(idx, font);
      }
   }

   public void setStartIdx(int idx) {
      startIdx.add(idx);
   }

   public int getStartIdx(int idx) {
      return Integer.parseInt(startIdx.get(idx).toString());
   }

   public int length() {
      return content.length();
   }

   public void setText(RichText rt) {
      rts.add(rt);
   }

   public RichText getText(int idx) {
      return (RichText) rts.get(idx);
   }

   public void setFontIdx(int idx) {
      fontIdx.add(idx);
   }

   public int getFontIdx(int idx) {
      return Integer.parseInt(fontIdx.get(idx).toString());
   }

   /**
    * Get font size.
    */
   public int getFontSize() {
      return fontMap.size();
   }

   public String toString() {
      return content;
   }

   private int preRecord = -1;
   private Map<Integer, RichTextFont> fontMap = new HashMap();
   private Vector startIdx = new Vector();
   private Vector fontIdx = new Vector();
   private Vector rts = new Vector();
   private String content;
}
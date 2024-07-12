/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
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
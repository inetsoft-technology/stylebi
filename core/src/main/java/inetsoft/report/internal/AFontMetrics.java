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
package inetsoft.report.internal;

import java.awt.*;
import java.io.*;
import java.util.*;

/**
 * The AFontMetrics can be used to parse a AFM file for font metrics
 * information. It implements the interface of FontMetrics for 
 * retrieving various information about a font. The information is parsed
 * from the supplied AFM file. Pair kerning is handled but track kerning
 * is currently ignored.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class AFontMetrics extends FontMetrics {
   /**
    * Empty font.
    */
   protected AFontMetrics() {
      super(null);
   }

   /**
    * Parse a AFM file.
    * @param instream AFM file input.
    */
   public AFontMetrics(InputStream instream) throws IOException {
      super(null);
      parse(instream);
   }

   /**
    * Set the size of the font.
    * @param size font size.
    */
   public void setSize(int size) {
      this.size = size;
   }

   /**
    * Get the font size in points.
    * @return font size.
    */
   public int getSize() {
      return size;
   }

   /**
    * Determines the <em>font ascent</em> of the font described by this 
    * font metric. The font ascent is the distance from the font's 
    * baseline to the top of most alphanumeric characters. Some 
    * characters in the font may extend above the font ascent line. 
    * @return     the font ascent of the font.
    * @see        java.awt.FontMetrics#getMaxAscent
    * @since      JDK1.0
    */
   @Override
   public int getAscent() {
      return (int) Math.ceil(getHeight() * ascender / (ascender + descender));
   }

   /**
    * Determines the <em>font descent</em> of the font described by this 
    * font metric. The font descent is the distance from the font's 
    * baseline to the bottom of most alphanumeric characters with 
    * descenders. Some characters in the font may extend below the font 
    * descent line. 
    * @return     the font descent of the font.
    * @see        java.awt.FontMetrics#getMaxDescent
    * @since      JDK1.0
    */
   @Override
   public int getDescent() {
      return (int) Math.ceil(getHeight() * descender / (ascender + descender));
   }

   /**
    * Gets the maximum advance width of any character in this Font. 
    * The advance width is the amount by which the current point is
    * moved from one character to the next in a line of text.
    * @return    the maximum advance width of any character 
    *            in the font, or <code>-1</code> if the 
    *            maximum advance width is not known.
    * @since     JDK1.0
    */
   @Override
   public int getMaxAdvance() {
      return (int) Math.ceil(advance * size / 1000.0);
   }

   /**
    * Make sure the height is at least as high as the bbox.
    */
   @Override
   public int getHeight() {
      return (int) Math.ceil(Math.max(bbox.height * size / 1000.0,
         (descender + ascender) * size / 1000.0));
   }

   /**
    * Returns the advance width of the specified character in this Font.
    * The advance width is the amount by which the current point is
    * moved from one character to the next in a line of text.
    * @param ch the character to be measured
    * @return     the advance width of the specified <code>char</code> >
    *                  in the font described by this font metric.
    * @see        java.awt.FontMetrics#charsWidth
    * @see        java.awt.FontMetrics#stringWidth
    * @since      JDK1.0
    */
   @Override
   public int charWidth(char ch) {
      return (int) Math.ceil(getWidth(ch) * size / 1000.0);
   }

   /**
    * Returns the total advance width for showing the specified String
    * in this Font.
    * The advance width is the amount by which the current point is
    * moved from one character to the next in a line of text.
    * @param str the String to be measured
    * @return    the advance width of the specified string 
    *                  in the font described by this font metric.
    * @see       java.awt.FontMetrics#bytesWidth
    * @see       java.awt.FontMetrics#charsWidth
    * @since     JDK1.0
    */
   @Override
   public int stringWidth(String str) {
      int w = 0;
      int len = str.length();

      // accumulate width
      for(int i = 0; i < len; i++) {
         w += getWidth(str.charAt(i));
      }

      // subtract pair kerning
      for(int i = 0; i < len - 1; i++) {
         Integer kern = pairKern.get(str.substring(i, i + 2));

         if(kern != null) {
            w += kern;
         }
      }

      return (int) Math.ceil(w * size / 1000.0);
   }

   /**
    * Gets the advance widths of the first 256 characters in the Font.
    * The advance width is the amount by which the current point is
    * moved from one character to the next in a line of text.
    * @return    an array giving the advance widths of the 
    *                 characters in the font 
    *                 described by this font metric.
    * @since     JDK1.0
    */
   @Override
   public int[] getWidths() {
      int[] ws = new int[widths.length];

      for(int i = 0; i < ws.length; i++) {
         ws[i] = getWidth(i) * size / 1000;
      }

      return ws;
   }

   /**
    * Parse a AFM file. Partial implementation of the AFM format.
    */
   void parse(InputStream instream) throws IOException {
      Hashtable<String, Character> nameChar = new Hashtable<>(); // "comma" -> ','
      String line;
      BufferedReader reader = 
         new BufferedReader(new InputStreamReader(instream));
      int skip = 0; // skip number of lines

      // alloc space
      pairKern = new HashMap<>();
      widths = new int[0x100];

      while((line = reader.readLine()) != null) {
         if(skip > 0) {
            skip--;
            continue;
         }

         StringTokenizer tok = new StringTokenizer(line, " \t");
         String name = tok.nextToken();

         if(name == null || name.equals("Comment")) {
            continue;
         }

         if(name.equals("FontName")) {
            fontName = tok.nextToken();
         }
         else if(name.equals("FullName")) {
            fullName = tok.nextToken();
         }
         else if(name.equals("FamilyName")) {
            familyName = tok.nextToken();
         }
         else if(name.equals("Weight")) {
            weight = tok.nextToken();
         }
         else if(name.equals("IsFixedPitch")) {
            fixedPitch = tok.nextToken().equals("true");
         }
         else if(name.equals("ItalicAngle")) {
            italicAngle = Double.valueOf(tok.nextToken());
         }
         else if(name.equals("Ascender")) {
            ascender = Integer.parseInt(tok.nextToken());
         }
         else if(name.equals("Descender")) {
            descender = -Integer.parseInt(tok.nextToken());
         }
         else if(name.equals("FontBBox")) {
            bbox = new Rectangle();
            bbox.x = Integer.parseInt(tok.nextToken());
            bbox.y = Integer.parseInt(tok.nextToken());
            bbox.width = Integer.parseInt(tok.nextToken());
            bbox.height = Integer.parseInt(tok.nextToken());
            bbox.width = bbox.width - bbox.x; // ur.x - ll.x
            bbox.height = bbox.height - bbox.y; // ur.y - ll.y
            bbox.y += bbox.height;
         }
         else if(name.equals("StartCharMetrics")) {
            while((line = reader.readLine()) != null) {
               if(line.startsWith("EndCharMetrics")) {
                  break;
               }

               int cc = -1;
               int cw = 0;
               String cn = null;
               StringTokenizer pairs = new StringTokenizer(line, ";");

               while(pairs.hasMoreTokens()) {
                  StringTokenizer t = new StringTokenizer(pairs.nextToken());
                  String n = t.nextToken();

                  if(n.equals("C")) {
                     cc = Integer.parseInt(t.nextToken());
                  }
                  else if(n.equals("WX") || n.equals("W0X")) {
                     cw = Integer.parseInt(t.nextToken());
                  }
                  else if(n.equals("N")) {
                     cn = t.nextToken();
                  }
               }

               if(cc >= 0) {
                  widths[cc] = cw;
                  advance = Math.max(advance, getWidth(cc));
               }

               if(cn != null) {
                  nameChar.put(cn, (char) cc);
               }
            }
         }
         else if(name.equals("StartKernPairs")) {
            while((line = reader.readLine()) != null) {
               if(line.startsWith("EndKernPairs")) {
                  break;
               }

               StringTokenizer pairs = new StringTokenizer(line, ";");

               while(pairs.hasMoreTokens()) {
                  StringTokenizer t = new StringTokenizer(pairs.nextToken());
                  String n = t.nextToken();

                  if(n.equals("KP") || n.equals("KPX")) {
                     String n1 = t.nextToken();
                     String n2 = t.nextToken();
                     Character co;
                     char c1 = n1.charAt(0);
                     char c2 = n2.charAt(0);

                     if(n1.length() > 1) {
                        c1 = ((co = nameChar.get(n1)) != null) ? co : (char) 0;
                     }

                     if(n2.length() > 1) {
                        c2 = ((co = nameChar.get(n2)) != null) ? co : (char) 0;
                     }

                     if(c1 != 0 && c2 != 0) {
                        pairKern.put(c1 + "" + c2,
                           Integer.valueOf(t.nextToken()));
                     }
                  }
                  else if(n.equals("KPH")) {
                     String n1 = t.nextToken();
                     String n2 = t.nextToken();
                     char c1 = (char) Integer.parseInt(n1.substring(1,
                        n1.length() - 1),
                        16);
                     char c2 = (char) Integer.parseInt(n2.substring(1,
                        n2.length() - 1),
                        16);

                     pairKern.put(c1 + "" + c2, Integer.valueOf(t.nextToken()));
                  }
               }
            }
         }
         else if(name.equals("StartComposites") ||
            name.equals("StartTrackKern")) {
            skip = Integer.parseInt(tok.nextToken());
            continue;
         }
      }
   }

   protected int getWidth(int idx) {
      return (idx < widths.length && widths[idx] > 0) ?
         widths[idx] :
         widths[(int) 'a'];
   }

   protected String fontName;
   protected String fullName;
   protected String familyName;
   protected String weight;
   protected boolean fixedPitch;
   protected double italicAngle;
   protected int ascender;
   protected int descender;
   protected int[] widths;
   protected HashMap<String, Integer> pairKern; // "Ab" -> Integer
   protected int advance;			// max advance
   protected Rectangle bbox;
   int size = 10;		// font size
}


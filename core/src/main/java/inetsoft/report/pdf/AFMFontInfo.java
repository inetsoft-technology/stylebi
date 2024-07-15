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
package inetsoft.report.pdf;

import java.awt.*;
import java.io.*;
import java.util.Hashtable;
import java.util.StringTokenizer;

/**
 * Type1 FontInfo class. The font information is parsed from .afm files.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
class AFMFontInfo extends FontInfo {
   /**
    * Parse a .afm file.
    */
   public void parse(InputStream instream) throws IOException {
      Hashtable nameChar = new Hashtable(); // "comma" -> ','
      String line;
      BufferedReader reader =
         new BufferedReader(new InputStreamReader(instream));
      int skip = 0; // skip number of lines

      // alloc space
      // pairKern = new Hashtable();
      widths = new short[0x100];

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
            italicAngle = Double.valueOf(tok.nextToken()).doubleValue();
         }
         else if(name.equals("Ascender")) {
            ascender = Integer.parseInt(tok.nextToken());
         }
         else if(name.equals("Descenter")) {
            descender = Integer.parseInt(tok.nextToken());
         }
         else if(name.equals("FontBBox")) {
            bbox = new Rectangle();
            bbox.x = Integer.parseInt(tok.nextToken());
            bbox.y = Integer.parseInt(tok.nextToken());
            bbox.width = Integer.parseInt(tok.nextToken());
            bbox.height = Integer.parseInt(tok.nextToken());
            bbox.width -= bbox.x;
            bbox.height -= bbox.y;
            bbox.y += bbox.height;
         }
         else if(name.equals("EncodingScheme")) {
            encoding = tok.nextToken();
         }
         else if(name.equals("CapHeight")) {
            capHeight = Integer.parseInt(tok.nextToken());
         }
         else if(name.equals("StartCharMetrics")) {
            while((line = reader.readLine()) != null) {
               if(line.startsWith("EndCharMetrics")) {
                  break;
               }

               int cc = -1, cw = 0;
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
                  widths[cc] = (short) cw;
                  advance = Math.max(advance, cw);
               }

               if(cn != null) {
                  nameChar.put(cn, Character.valueOf((char) cc));
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
                     char c1 = n1.charAt(0), c2 = n2.charAt(0);

                     if(n1.length() > 1) {
                        c1 = ((co = (Character) nameChar.get(n1)) != null) ?
                           co.charValue() :
                           (char) 0;
                     }

                     if(n2.length() > 1) {
                        c2 = ((co = (Character) nameChar.get(n2)) != null) ?
                           co.charValue() :
                           (char) 0;
                     }

                     if(c1 != 0 && c2 != 0) {
                        pairLeft.set(c1);
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
                     pairLeft.set(c1);
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

   /**
    * Return a name, fullname pair.
    */
   public static String[] getFontNames(InputStream inp) throws IOException {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inp));
      String line, name = null, fullname = null;

      while((line = reader.readLine()) != null) {
         line = line.trim();

         if(line.startsWith("FullName")) {
            fullname = line.substring(8).trim();
         }
         else if(line.startsWith("FontName")) {
            name = line.substring(10).trim();
         }

         if(name != null && fullname != null) {
            break;
         }
      }

      return (name == null) ? null : new String[] {name, fullname};
   }
}


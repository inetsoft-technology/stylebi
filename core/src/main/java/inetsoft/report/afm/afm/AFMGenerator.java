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
package inetsoft.report.afm.afm;

import inetsoft.report.internal.AFontMetrics;
import inetsoft.util.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Iterator;

/**
 * Generate font metrics classes for each .afm file.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class AFMGenerator extends AFontMetrics {
   public static void main(String[] args) {
      for(int i = 0; i < args.length; i++) {
         try {
            new AFMGenerator(FileSystemService.getInstance().getFile(args[i]));
         }
         catch(Exception e) {
            LOG.error(args[i], e);
         }
      }
   }

   public AFMGenerator(File afm) throws IOException {
      super(new FileInputStream(afm));

      String name = afm.getName().replace('-', '_');
      int pos = name.indexOf(".");

      if(pos > 0) {
         name = name.substring(0, pos);
      }

      File outf = FileSystemService.getInstance().getFile("..", name + ".java");
      PrintWriter out = new PrintWriter(new FileOutputStream(outf));

      out.println("package inetsoft.report.afm;");
      out.println("import java.util.*;");
      out.println("import java.awt.Rectangle;");
      out.println("import inetsoft.report.internal.AFontMetrics;");
      out.println("public class " + name + " extends AFontMetrics {");
      out.println("static String s_fontName = \"" + fontName + "\";");
      out.println("static String s_fullName = \"" + fullName + "\";");
      out.println("static String s_familyName = \"" + familyName + "\";");
      out.println("static String s_weight = \"" + weight + "\";");
      out.println("static boolean s_fixedPitch = " + fixedPitch + ";");
      out.println("static double s_italicAngle = " + italicAngle + ";");
      out.println("static int s_ascender = " + ascender + ";");
      out.println("static int s_descender = " + descender + ";");
      out.println("static int s_advance = " + advance + ";");
      out.println("static Rectangle s_bbox = new Rectangle(" + bbox.x + "," +
         bbox.y + "," + bbox.width + "," + bbox.height + ");");
      out.println("static int[] s_widths = {");

      for(int i = 0; i < widths.length; i++) {
         if(i > 0 && (i % 15) == 0) {
            out.println();
         }

         out.print(widths[i]);
         if(i < widths.length - 1) {
            out.print(",");
         }
      }

      out.println("};");
      out.println("static HashMap s_pairKern = new HashMap();");
      out.println("static {");
      Iterator keys = pairKern.keySet().iterator();

      while(keys.hasNext()) {
         String key = (String) keys.next();

         out.println("s_pairKern.put(\"\" + (char) " + ((int) key.charAt(0)) +
            " + (char) " + ((int) key.charAt(1)) + ", Integer.valueOf(" +
            pairKern.get(key) + "));");
      }

      out.println("};");
      out.println("{");
      out.println("fontName = s_fontName;");
      out.println("fullName = s_fullName;");
      out.println("familyName = s_familyName;");
      out.println("weight = s_weight;");
      out.println("fixedPitch = s_fixedPitch;");
      out.println("italicAngle = s_italicAngle;");
      out.println("ascender = s_ascender;");
      out.println("descender = s_descender;");
      out.println("widths = s_widths;");
      out.println("pairKern = s_pairKern;");
      out.println("advance = s_advance;");
      out.println("bbox = s_bbox;");
      out.println("};");
      out.println("}");
      out.close();
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(AFMGenerator.class);
}


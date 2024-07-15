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
package inetsoft.graph.internal;

import inetsoft.sree.SreeEnv;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * This implementation connects to the StyleReport classes.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class SRImpl extends DefaultImpl {
   /**
    * Get the font metrics of the font.
    */
   @Override
   public FontMetrics getFontMetrics(Font font) {
      if(useCommonFM()) {
         return inetsoft.report.internal.Common.getFractionalFontMetrics(font);
      }
      else {
         return super.getFontMetrics(font);
      }
   }

   /**
    * Calculate the width of the string.
    */
   @Override
   public double stringWidth(String str, Font fn) {
      if(useCommonFM()) {
         return inetsoft.report.internal.Common.stringWidth(str, fn);
      }
      else {
         return super.stringWidth(str, fn);
      }
   }

   /**
    * Break the text into lines.
    */
   @Override
   public String[] breakLine(String text, Font fn, int spacing, float width, float height) {
      inetsoft.report.internal.Bounds box = 
         new inetsoft.report.internal.Bounds(0, 0, width, height);
      FontMetrics fm = getFontMetrics(fn);
      // vector of int[] {index, end-index}
      Vector offsets = inetsoft.report.internal.Common.
         processText(text, box, 0, true, fn, box, new Vector(), spacing, fm, 0);
      List<String> lines = new ArrayList<>();
      int fontH = fm.getHeight();
      float totalH = 0;

      for(int i = 0; i < offsets.size(); i++) {
         int[] line = (int[]) offsets.get(i);
         String str = text.substring(line[0], line[1]);

         totalH += fontH + spacing;

         // can't fit next line, clip the string
         if(totalH + fontH > height && i < offsets.size() - 1) {
            int[] nextline = (int[]) offsets.get(i + 1);
            String nextstr = text.substring(nextline[0], nextline[1]);

            str += " " + nextstr;

            int idx = inetsoft.report.internal.Util.breakLine(str, width, fn, false);
            lines.add(idx < 0 ? str : str.substring(0, idx));

            break;
         }

         lines.add(str);
      }

      return lines.toArray(new String[lines.size()]);
   }

   /**
    * Draw a string on the graphics output.
    */
   @Override
   public void drawString(Graphics2D g, String str, double x, double y) {
      if(useCommonFM()) {
         inetsoft.report.internal.Common.drawString(g, str, (int) x, (int) y);
      }
      else {
         super.drawString(g, str, x, y);
      }
   }

   /**
    * Get a property defined in the configuration.
    */
   @Override
   public String getProperty(String name, String def) {
      return SreeEnv.getProperty(name, def);
   }

   /**
    * Set a property defined in the configuration.
    */
   @Override
   public void setProperty(String name, String val) {
      SreeEnv.setProperty(name, val);
   }

   /**
    * Get a localized string.
    */
   @Override
   public String getString(String str, Object... params) {
      return inetsoft.util.Catalog.getCatalog().getString(str, params);
   }

   /**
    * Unwrap a script value.
    */
   @Override
   public Object unwrap(Object val) {
      return inetsoft.util.script.JavaScriptEngine.unwrap(val);
   }

   private boolean useCommonFM() {
      return GTool.isPDF() || "true".equals(uniformFm.get());
   }

   private static final SreeEnv.Value uniformFm =
      new SreeEnv.Value("graph.fm.uniform", 30000, "false");
}

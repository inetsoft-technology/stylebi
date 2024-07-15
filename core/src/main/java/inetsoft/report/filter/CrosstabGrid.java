/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.filter;

import inetsoft.report.StyleConstants;

import java.awt.*;
import java.io.PrintWriter;
import java.io.Serializable;

/**
 * This class defines the grid lines in a crosstab. It can be used to
 * control how the grid is drawn in a crosstab.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class CrosstabGrid implements Serializable, Cloneable {
   /**
    * Column label top border.
    */
   public static final int COLUMN_LABEL_TOP_BORDER = 0;
   /**
    * Column label left border.
    */
   public static final int COLUMN_LABEL_LEFT_BORDER = 1;
   /**
    * Column label right border
    */
   public static final int COLUMN_LABEL_RIGHT_BORDER = 2;
   /**
    * Column label bottom border
    */
   public static final int COLUMN_LABEL_BOTTOM_BORDER = 3;
   /**
    * Column label vertical lines.
    */
   public static final int COLUMN_LABEL_VERTICAL_LINES = 4;
   /**
    * Column label horizontal lines.
    */
   public static final int COLUMN_LABEL_HORIZONTAL_LINES = 5;
   /**
    * Row label top border.
    */
   public static final int ROW_LABEL_TOP_BORDER = 6;
   /**
    * Row label left border.
    */
   public static final int ROW_LABEL_LEFT_BORDER = 7;
   /**
    * Row label right border
    */
   public static final int ROW_LABEL_RIGHT_BORDER = 8;
   /**
    * Row label bottom border
    */
   public static final int ROW_LABEL_BOTTOM_BORDER = 9;
   /**
    * Row label vertical lines.
    */
   public static final int ROW_LABEL_VERTICAL_LINES = 10;
   /**
    * Row label horizontal lines.
    */
   public static final int ROW_LABEL_HORIZONTAL_LINES = 11;
   /**
    * Cells label right border
    */
   public static final int CELLS_RIGHT_BORDER = 12;
   /**
    * Cells label bottom border
    */
   public static final int CELLS_BOTTOM_BORDER = 13;
   /**
    * Cells label vertical lines.
    */
   public static final int CELLS_VERTICAL_LINES = 14;
   /**
    * Cells label horizontal lines.
    */
   public static final int CELLS_HORIZONTAL_LINES = 15;
   /**
    * Number of border types.
    */
   public static final int BORDER_MAX = 16;
   /**
    * Create a crosstab grid descriptor.
    */
   public CrosstabGrid() {
      for(int i = 0; i < borders.length; i++) {
         borders[i] = StyleConstants.THIN_LINE;
         colors[i] = Color.black;
      }
   }

   /**
    * Set the border of a particular type.
    * @param type border type defined in this class.
    * @param style line style to use for the border type. 
    * Use StyleConstants.NO_BORDER to turn a border off.
    */
   public void setBorder(int type, int style) {
      borders[type] = style;
   }

   /**
    * Get the border line style of a particular type.
    * @param type border type defined in this class.
    * @return border line style.
    */
   public int getBorder(int type) {
      return borders[type];
   }

   /**
    * Set the border color of a particular type.
    * @param type border type defined in this class.
    * @param color border line color.
    */
   public void setBorderColor(int type, Color color) {
      this.colors[type] = color;
   }

   /**
    * Get the border line color of a particular type.
    * @param type border type defined in this class.
    * @return border line color.
    */
   public Color getBorderColor(int type) {
      return colors[type];
   }

   /**
    * Writer a crosstab grid descriptor.
    */
   public void writeXML(PrintWriter writer) {
      // check if anything is specified
      boolean nospec = true;

      for(int i = 0; i < borders.length; i++) {
         if(borders[i] >= 0) {
            nospec = false;
            break;
         }

         if(colors[i] != null && !colors[i].equals(Color.black)) {
            nospec = false;
            break;
         }
      }

      if(nospec) {
         return;
      }

      writer.println("<CrosstabGrid>");

      for(int i = 0; i < borders.length; i++) {
         if(borders[i] < 0) {
            continue;
         }

         writer.print("<Border type=\"" + i + "\" style=\"" + borders[i] +
            "\"");

         if(colors[i] != null) {
            writer.print(" color=\"" + colors[i].getRGB() + "\"");
         }

         writer.println("/>");
      }

      writer.println("</CrosstabGrid>");
   }

   @Override
   public Object clone() {
      CrosstabGrid grid = new CrosstabGrid();

      System.arraycopy(borders, 0, grid.borders, 0, borders.length);
      System.arraycopy(colors, 0, grid.colors, 0, colors.length);

      return grid;
   }

   private int[] borders = new int[BORDER_MAX];
   private Color[] colors = new Color[BORDER_MAX];
}


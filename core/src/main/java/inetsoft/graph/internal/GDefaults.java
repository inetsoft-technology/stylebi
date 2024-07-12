/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.internal;

import inetsoft.report.StyleFont;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * Default values.
 *
 * @version 13.6
 * @author InetSoft Technology Corp
 */
public class GDefaults {
   /**
    * Transformation for flipping the Y coord.
    */
   public static final AffineTransform FLIPY = new AffineTransform(1, 0, 0, -1, 0, 0);
   /**
    * Transformation for flipping the X coord.
    */
   public static final AffineTransform FLIPX = new AffineTransform(-1, 0, 0, 1, 0, 0);
   /**
    * Target line fill z index.
    */
   public static final int TARGET_FILL_Z_INDEX = 10;
   /**
    * Coord border z index.
    */
   public static final int COORD_BORDER_Z_INDEX = 20;
   /**
    * Grid line z index.
    */
   public static final int GRIDLINE_Z_INDEX = 30;
   /**
    * Facet grid line z index.
    */
   public static final int FACET_GRIDLINE_Z_INDEX = 40;
   /**
    * 3DAxis z index.
    */
   public static final int AXIS2D_Z_INDEX = 45;
   /**
    * Axis border z index.
    */
   public static final int AXIS_BORDER_Z_INDEX = 50;
   /**
    * Visual object z index.
    */
   public static final int VO_Z_INDEX = 60;
   /**
    * Axis z index.
    */
   public static final int AXIS_Z_INDEX = 70;
   /**
    * Visual object with only outline z index.
    */
   public static final int VO_OUTLINE_Z_INDEX = 75;
   /**
    * Grid line z index on top of VO.
    */
   public static final int GRIDLINE_TOP_Z_INDEX = 80;
   /**
    * Default z index for form objects.
    */
   public static final int FORM_Z_INDEX = 90;
   /**
    * Text z index.
    */
   public static final int TEXT_Z_INDEX = 100;
   /**
    * Trend line z index.
    */
   public static final int TREND_LINE_Z_INDEX = 110;
   /**
    * Target line z index.
    */
   public static final int TARGET_LINE_Z_INDEX = 120;
   /**
    * Target line label z index.
    */
   public static final int TARGET_LABEL_ZINDEX = 130;
   /**
    * Tick and grid line min gap.
    */
   public static final int TICK_MIN_GAP = 4;
   /**
    * Visual graph max area.
    */
   public static final double GRAPH_MAX_AREA = 4000 * 4000;
   /**
    * Subgraph max count.
    */
   public static final int SUBGRAPH_MAX_COUNT = 1000;
   /**
    * Axis label max count.
    */
   public static final int AXIS_LABEL_MAX_COUNT = 2000;
   /**
    * Legend item max count.
    */
   public static final int LEGEND_MAX_COUNT = 500;
   /**
    * Default font color.
    */
   public static final Color DEFAULT_TEXT_COLOR = new Color(0x4b4b4b);
   /**
    * Default font color.
    */
   public static final Color DEFAULT_TITLE_COLOR = new Color(0x2b2b2b);
   /**
    * Default grid line color.
    */
   public static final Color DEFAULT_GRIDLINE_COLOR = new Color(0xeeeeee);
   /**
    * Default line color, used for border color and axis line color.
    */
   public static final Color DEFAULT_LINE_COLOR = new Color(0xeeeeee);
   /**
    * Default target line color
    */
   public static final Color DEFAULT_TARGET_LINE_COLOR = new Color(0xafafad);
   /**
    * The default small text font.
    */
   public static final Font DEFAULT_SMALL_FONT =
      new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 9);
   /**
    * Default text font.
    */
   public static final Font DEFAULT_TEXT_FONT =
      new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 10);
   /**
    * Default title text font.
    */
   public static final Font DEFAULT_TITLE_FONT =
      new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 11);
   /**
    * Max geometry graph count of an element graph.
    */
   public static final int MAX_GGRAPH_COUNT = 50000;
   /**
    * An empty string representing a null value in data.
    */
   public static final String NULL_STR = new String("");

   /**
    * Return a value by checking if it's a null string (NULL_STR).
    */
   public static Object getValue(Object v) {
      return v == NULL_STR ? null : v;
   }
}

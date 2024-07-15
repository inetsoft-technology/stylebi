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
package inetsoft.graph;

/**
 * This class contains all constants used in the graph package.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public interface GraphConstants {
   /**
    * This mask can be used to check if a line style is solid.
    */
   public static final int SOLID_MASK = 0x1000;
   /**
    * This mask is used to extract the dash length.
    */
   static final int DASH_MASK = 0x0F0;
   /**
    * This mask is used to extract the encoded line width from line styles.
    */
   static final int WIDTH_MASK = 0x0F;
   /**
    * Fraction width, 1/16 point.
    */
   static final int FRACTION_WIDTH_MASK = 0xF0000;
   /**
    * Line butt cap style. Can be OR'ed with a line style to control the
    * line cap.
    */
   public static final int LINECAP_BUTT = 0x100000;
   /**
    * Line round cap style. Can be OR'ed with a line style to control the
    * line cap.
    */
   public static final int LINECAP_ROUND = 0x200000;
   /**
    * Line square cap style. Can be OR'ed with a line style to control the
    * line cap.
    */
   public static final int LINECAP_SQUARE = 0x400000;
   /**
    * Line miter join style. Can be OR'ed with a line style to control the
    * line join.
    */
   public static final int LINEJOIN_MITER = 0x800000;
   /**
    * Line round join style. Can be OR'ed with a line style to control the
    * line join.
    */
   public static final int LINEJOIN_ROUND = 0x1000000;
   /**
    * Line bevel join style. Can be OR'ed with a line style to control the
    * line join.
    */
   public static final int LINEJOIN_BEVEL = 0x2000000;
   /**
    * This constant is used to signal no value (e.g. legend or border).
    */
   public static final int NONE = 0;
   /**
    * Very thin line, at quarter of the width of a thin line.
    */
   public static final int ULTRA_THIN_LINE = 0x40000 + SOLID_MASK;
   /**
    * Very thin line, at half of the width of a thin line.
    */
   public static final int THIN_THIN_LINE = 0x80000 + SOLID_MASK;
   /**
    * Thin, single width line.
    */
   public static final int THIN_LINE = 1 + SOLID_MASK;
   /**
    * Medium, two pixel width line.
    */
   public static final int MEDIUM_LINE = 2 + SOLID_MASK;
   /**
    * Thick, three pixel width line.
    */
   public static final int THICK_LINE = 3 + SOLID_MASK;
   /**
    * Draw dotted lines.
    */
   public static final int DOT_LINE = 1 + SOLID_MASK + 0x10;
   /**
    * Draw dashed lines.
    */
   public static final int DASH_LINE = 1 + SOLID_MASK + 0x30;
   /**
    * Draw medium dashed lines.
    */
   public static final int MEDIUM_DASH = 1 + SOLID_MASK + 0x60;
   /**
    * Draw large dashed lines.
    */
   public static final int LARGE_DASH = 1 + SOLID_MASK + 0x90;

   /**
    * Placement at the top.
    */
   public static final int TOP = 1;
   /**
    * Placement at the right.
    */
   public static final int RIGHT = 2;
   /**
    * Placement at the bottom.
    */
   public static final int BOTTOM = 3;
   /**
    * Placement at the left.
    */
   public static final int LEFT = 4;
   /**
    * Placement at the center of the shape.
    */
   public static final int CENTER = 100;
   /**
    * Placement at the center of the shape and fill the shape as much as possible. This is
    * only supported by certain chart types.
    */
   public static final int CENTER_FILL = 200;

   /**
    * Vertical top alignment.
    */
   public static final int TOP_ALIGNMENT = 8;
   /**
    * Vertical middel alignment.
    */
   public static final int MIDDLE_ALIGNMENT = 16;
   /**
    * Vertical bottom alignment.
    */
   public static final int BOTTOM_ALIGNMENT = 32;
   /**
    * Horizontal left alignment.
    */
   public static final int LEFT_ALIGNMENT = 1;
   /**
    * Horizontal center alignment.
    */
   public static final int CENTER_ALIGNMENT = 2;
   /**
    * Horizontal right alignment.
    */
   public static final int RIGHT_ALIGNMENT = 4;

   /**
    * Auto placement.
    */
   public static final int AUTO = 0;
   /**
    * The legend will be floated on the top of the chart.
    */
   public static final int IN_PLACE = 5;
}

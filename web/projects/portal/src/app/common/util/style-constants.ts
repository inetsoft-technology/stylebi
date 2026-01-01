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
/**
 * Style Constant
 * @version 12.3
 * @author InetSoft Technology Corp
 */
export const enum StyleConstants {
   /**
    * Fit contents.
    */
   TABLE_FIT_CONTENT = 0,
   /**
    * Fit page width.
    */
   TABLE_FIT_PAGE = 1,
   /**
    * Equals with colums.
    */
   TABLE_EQUAL_WIDTH = 2,
   /**
    * Fit Contents And Wrap, One Table Region Per Page
    */
   TABLE_FIT_CONTENT_1PP = 3,
   /**
    * Fit Contents And Fill Page
    */
   TABLE_FIT_CONTENT_PAGE = 4,

   /**
    * Font normal style.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   FONT_PLAIN = 0,
   /**
    * Font bold style.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   FONT_BOLD = 1,
   /**
    * Font italic style.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   FONT_ITALIC = 2,
   /**
    * Font bold and italic style.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   FONT_BOLDITALIC = 3,

   /**
    * No sorting.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   SORT_NONE = 0,
   /**
    * Ascending sorting order.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   SORT_ASC = 1,
   /**
    * Descending sorting order.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   SORT_DESC = 2,
   /**
    * Original sorting.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   SORT_ORIGINAL = 4,
   /**
    * Ascending sorting order by value.
    */
   SORT_VALUE_ASC = 17,
   /**
    * Descending sorting order by value.
    */
   SORT_VALUE_DESC = 18,
   /**
    * SPECIFIED sorting.
    */
   SORT_SPECIFIC = 8,
   /**
    * Top n operation definition that determines if only favor
    * top n rows.
    */
   TOP_N = 9,
   /**
    * Bottom n operation definition that determines if only favor
    * bottom n rows.
    */
   BOTTOM_N = 10,
   /**
    * Line style: thin line
    */
   THIN_LINE = 4097,
   /**
    * Line style: no border
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   NO_BORDER = 0,
   /**
    * Line style: mixed border
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   MIXED_BORDER = 1,
   /**
    * Line style: medium line
    */
   MEDIUM_LINE = 4098,
   /**
    * Line style: thick line
    */
   THICK_LINE = 4099,
   /**
    * Line style: thin thin line
    */
   THIN_THIN_LINE = 528384,
   /**
    * Line style: ultra thin line
    */
   ULTRA_THIN_LINE = 266240,
   /**
    * This mask can be used to check if a line style is solid.
    */
   SOLID_MASK = 0x1000,
   /**
    * Double line.
    */
   DOUBLE_LINE = 3 + 0x2000, // 8195
   /**
    * Line style: raised 3D
    */
   RAISED_3D = 2 + 0x2000 + 0x4000,
   /**
    * Line style: lowered 3D
    */
   LOWERED_3D = 2 + 0x2000 + 0x8000,
   /**
    * Line style: double 3D raised
    */
   DOUBLE_3D_RAISED = 3 + 0x2000 + 0x4000,
   /**
    * Line style: double 3D lowered
    */
   DOUBLE_3D_LOWERED = 3 + 0x2000 + 0x8000,
   /**
    * Draw dotted lines.
    */
   DOT_LINE = 1 + 0x1000 + 0x10, // 4113
   /**
    * Draw dashed lines.
    */
   DASH_LINE = 1 + 0x1000 + 0x30, //4145
   /**
    * Draw medium dashed lines.
    */
   MEDIUM_DASH = 1 + 0x1000 + 0x60, //4193
   /**
    * Draw large dashed lines.
    */
   LARGE_DASH = 1 + 0x1000 + 0x90, //4241
   /**
    * Circle shape style.
    */
   CIRCLE = 900,
   /**
    * Triangle shape style.
    */
   TRIANGLE = 901,
   /**
    * Square shape style.
    */
   SQUARE = 902,
   /**
    * Cross shape style.
    */
   CROSS = 903,
   /**
    * Star shape style.
    */
   STAR = 904,
   /**
    * Diamond shape style.
    */
   DIAMOND = 905,
   /**
    * X shape style.
    */
   X = 906,
   /**
    * Filled circle shape style.
    */
   FILLED_CIRCLE = 907,
   /**
    * Filled triangle shape style.
    */
   FILLED_TRIANGLE = 908,
   /**
    * Filled square shape style.
    */
   FILLED_SQUARE = 909,
   /**
    * Filled diamond shape style.
    */
   FILLED_DIAMOND = 910,
   /**
    * V angle shape style. (V)
    */
   V_ANGLE = 911,
   /**
    * Right angle shape style.
    */
   RIGHT_ANGLE = 912,
   /**
    * "Less_than" angle shape style. (<)
    */
   LT_ANGLE = 913,
   /**
    * Vertical line shape style.
    */
   V_LINE = 914,
   /**
    * Horizontal line shape style.
    */
   H_LINE = 915,
   /**
    * An empty shape.
    */
   NIL = 916,
   /**
    * None Pattern.
    */
   PATTERN_NONE = -1,
   /**
    * Pattern 0.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PATTERN_0 = 0,
   /**
    * Pattern 1.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PATTERN_1 = 1,
   /**
    * Pattern 2.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PATTERN_2 = 2,
   /**
    * Pattern 3.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PATTERN_3 = 3,
   /**
    * Pattern 4.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PATTERN_4 = 4,
   /**
    * Pattern 5.
    */
   PATTERN_5 = 5,
   /**
    * Pattern 6.
    */
   PATTERN_6 = 6,
   /**
    * Pattern 7.
    */
   PATTERN_7 = 7,
   /**
    * Pattern 8.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PATTERN_8 = 8,
   /**
    * Pattern 9.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PATTERN_9 = 9,
   /**
    * Pattern 10.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PATTERN_10 = 10,
   /**
    * Pattern 11.
    */
   PATTERN_11 = 11,
   /**
    * Pattern 12.
    */
   PATTERN_12 = 12,
   /**
    * Pattern 13.
    */
   PATTERN_13 = 13,
   /**
    * Pattern 14.
    */
   PATTERN_14 = 14,
   /**
    * Pattern 15.
    */
   PATTERN_15 = 15,
   /**
    * Pattern 16.
    */
   PATTERN_16 = 16,
   /**
    * Pattern 17.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PATTERN_17 = 17,
   /**
    * Pattern 18.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PATTERN_18 = 18,
   /**
    * Pattern 19.
    */
   PATTERN_19 = 19,
   /**
    * Filled arrow.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   FILLED_ARROW = 1,
   /**
    * Not filled arrow.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   WHITE_ARROW = 3,
   /**
    * Two sided arrow.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   EMPTY_ARROW = 2,
   /**
    * horizontal flag.
    */
   H_MASK = 0x87,
   /**
    * vertical flag.
    */
   V_MASK = 0x78,
   /**
    *
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   NONE = 0,
   /**
    * Left at horizontal direction.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   H_LEFT = 1,
   /**
    * Center at horizontal direction.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   H_CENTER = 2,
   /**
    * Right align at horizontal direction.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   H_RIGHT = 4,
   /**
    * Currency align at horizontal direction.
    */
   H_CURRENCY = 128,
   /**
    * Top align at vertical direction.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   V_TOP = 8,
   /**
    * Center at vertical direction.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   V_CENTER = 16,
   /**
    * Bottom align at vertical direction.
    */
   V_BOTTOM = 32,
   /**
    * Vertical aligned at baseline of text elements. This is
    * the default for vertical alignment.
    */
   V_BASELINE = 64,

   /**
    * No Percentage.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PERCENTAGE_NONE = 0,
   /**
    * Percentage of group.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PERCENTAGE_OF_GROUP = 1,
   /**
    * Percentage of grand total.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PERCENTAGE_OF_GRANDTOTAL = 2,
   /**
    * Percentage of row group.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PERCENTAGE_OF_ROW_GROUP = 4,
   /**
    * Percentage of col group.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PERCENTAGE_OF_COL_GROUP = 8,
   /**
    * Percentage of row grand total.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PERCENTAGE_OF_ROW_GRANDTOTAL = 16,
   /**
    * Percentage of col grand total.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PERCENTAGE_OF_COL_GRANDTOTAL = 32,
   /**
    * Report background layout, tiled.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   BACKGROUND_TILED = 1,
   /**
    * Report background layout, center.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   BACKGROUND_CENTER = 2,

   /**
    * Percentage by column. Only use in CrossTabFilter.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PERCENTAGE_BY_COL = 1,

   /**
    * Percentage by row. Only use in CrossTabFilter.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   PERCENTAGE_BY_ROW = 2,

   /**
    * Grand total option.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   GRAND_TOTAL = 1,

   /**
    * Sub total option.
    */
   // eslint-disable-next-line @typescript-eslint/no-duplicate-enum-values
   SUB_TOTAL = 2
}

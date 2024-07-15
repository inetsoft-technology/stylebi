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
package inetsoft.report;

import inetsoft.graph.GraphConstants;
import inetsoft.uql.XConstants;

/**
 * This class defines the constants used in package inetsoft.report.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface StyleConstants extends XConstants {
   /**
    * The origin is at the bottom left of the paper with
    * x running bottom to top and y running left to right.
    */
   public static final int LANDSCAPE = 0;
   /**
    *  The origin is at the top left of the paper with
    *  x running to the right and y running down the
    *  paper.
    */
   public static final int PORTRAIT = 1;
   /**
    * This mask is used to extract the encoded line width from line styles.
    */
   static final int WIDTH_MASK = GraphConstants.WIDTH_MASK;
   /**
    * This mask is used to extract the dash length.
    */
   static final int DASH_MASK = GraphConstants.DASH_MASK;
   /**
    * This mask can be used to check if a line style is solid.
    */
   static final int SOLID_MASK = GraphConstants.SOLID_MASK;
   /**
    * This mask is set if the line is a double line (two lines at edges).
    */
   static final int DOUBLE_MASK = 0x2000;
   /**
    * This mask is set if the line is raised 3D style.
    */
   static final int RAISED_MASK = 0x4000;
   /**
    * This mask is set if the line is lowered 3D style.
    */
   static final int LOWERED_MASK = 0x8000;
   /**
    * Fraction width, 1/16 point.
    */
   static final int FRACTION_WIDTH_MASK = GraphConstants.FRACTION_WIDTH_MASK;
   /**
    * Line butt cap style. Can be OR'ed with a line style to control the
    * line cap.
    */
   public static final int LINECAP_BUTT = GraphConstants.LINECAP_BUTT;
   /**
    * Line round cap style. Can be OR'ed with a line style to control the
    * line cap.
    */
   public static final int LINECAP_ROUND = GraphConstants.LINECAP_ROUND;
   /**
    * Line square cap style. Can be OR'ed with a line style to control the
    * line cap.
    */
   public static final int LINECAP_SQUARE = GraphConstants.LINECAP_SQUARE;
   /**
    * Line miter join style. Can be OR'ed with a line style to control the
    * line join.
    */
   public static final int LINEJOIN_MITER = GraphConstants.LINEJOIN_MITER;
   /**
    * Line round join style. Can be OR'ed with a line style to control the
    * line join.
    */
   public static final int LINEJOIN_ROUND = GraphConstants.LINEJOIN_ROUND;
   /**
    * Line bevel join style. Can be OR'ed with a line style to control the
    * line join.
    */
   public static final int LINEJOIN_BEVEL = GraphConstants.LINEJOIN_BEVEL;
   /**
    * Empty border.
    */
   public static final int NO_BORDER = 0;
   /**
    * Very thin line, at quarter of the width of a thin line.
    */
   public static final int ULTRA_THIN_LINE = GraphConstants.ULTRA_THIN_LINE;
   /**
    * Very thin line, at half of the width of a thin line.
    */
   public static final int THIN_THIN_LINE = GraphConstants.THIN_THIN_LINE;
   /**
    * Thin, single width line.
    */
   public static final int THIN_LINE = GraphConstants.THIN_LINE;
   /**
    * Medium, two pixel width line.
    */
   public static final int MEDIUM_LINE = GraphConstants.MEDIUM_LINE;
   /**
    * Thick, three pixel width line.
    */
   public static final int THICK_LINE = GraphConstants.THICK_LINE;
   /**
    * Double line.
    */
   public static final int DOUBLE_LINE = 3 + DOUBLE_MASK;
   /**
    * Draw raised 3D lines.
    */
   public static final int RAISED_3D = 2 + DOUBLE_MASK + RAISED_MASK;
   /**
    * Draw lowered 3D lines.
    */
   public static final int LOWERED_3D = 2 + DOUBLE_MASK + LOWERED_MASK;
   /**
    * Draw raised 3D lines.
    */
   public static final int DOUBLE_3D_RAISED = 3 + DOUBLE_MASK + RAISED_MASK;
   /**
    * Draw lowered 3D lines.
    */
   public static final int DOUBLE_3D_LOWERED = 3 + DOUBLE_MASK + LOWERED_MASK;
   /**
    * Arrow line type 1.
    */
   public static final int ARROW_LINE_1 = 1;
   /**
    * Arrow line type 2.
    */
   public static final int ARROW_LINE_2 = 2;
   /**
    * Arrow line type 3.
    */
   public static final int ARROW_LINE_3 = 3;
   /**
    * Draw dotted lines.
    */
   public static final int DOT_LINE = GraphConstants.DOT_LINE;
   /**
    * Draw dashed lines.
    */
   public static final int DASH_LINE = GraphConstants.DASH_LINE;
   /**
    * Draw medium dashed lines.
    */
   public static final int MEDIUM_DASH = GraphConstants.MEDIUM_DASH;
   /**
    * Draw large dashed lines.
    */
   public static final int LARGE_DASH = GraphConstants.LARGE_DASH;
   /**
    * Mask for retrieving the horizontal alignment flag.
    */
   public static final int H_ALIGN_MASK = 0x87;
   /**
    * Mask for retrieving the vertical alignment flag.
    */
   public static final int V_ALIGN_MASK = 0x78;
   /**
    * Alignment flag determines how the cell components are aligned
    * inside a cell. The default is FILL. You can use either the
    * final flags, e.g. LEFT_TOP, which is a product of OR of
    * H_ and V_ flags. Or you can compose the flags using H_
    * and V_ flags explicitly. For example, if H_LEFT is passed
    * as the alignment, the cell will be aligned to the left
    * of the cell space, and the height of the cell component
    * will be stretched to fit the cell height, and the cell component
    * with will be the preferred width of the component if
    * it's less than the cell space width. If LEFT_TOP is used,
    * which is equivalent to H_LEFT|V_TOP, the cell component
    * will be aligned to top left of cell space, and the component
    * will not be stretched to fit neither dimension of the
    * cell space.
    */
   public static final int H_LEFT = 1;
   /**
    * Center at horizontal direction.
    */
   public static final int H_CENTER = 2;
   /**
    * Right align at horizontal direction.
    */
   public static final int H_RIGHT = 4;
   /**
    * Currency align at horizontal direction.
    */
   public static final int H_CURRENCY = 128;
   /**
    * Top align at vertical direction.
    */
   public static final int V_TOP = 8;
   /**
    * Center at vertical direction.
    */
   public static final int V_CENTER = 16;
   /**
    * Bottom align at vertical direction.
    */
   public static final int V_BOTTOM = 32;
   /**
    * Vertical aligned at baseline of text elements. This is
    * the default for vertical alignment.
    */
   public static final int V_BASELINE = 64;
   /**
    * Resize the component to fill the cell.
    */
   public static final int FILL = 0;
   /**
    * This is the horizontal left alignment.
    */
   public static final int LEFT = H_LEFT;
   /**
    * This is the horizontal center alignment.
    */
   public static final int CENTER = H_CENTER;
   /**
    * This is the horizontal right alignment.
    */
   public static final int RIGHT = H_RIGHT;
   /**
    * This is the horizontal currency alignment.
    */
   public static final int CURRENCY = H_CURRENCY;
   /**
    * This constant can be returned as a column width to claim the
    * remainder of the space.
    */
   public static final int REMAINDER = Integer.MAX_VALUE;

   /**
    * Printer tray selector.
    */
   public static final int TRAY_UPPER = 1;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_ONLYONE = 1;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_LOWER = 2;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_MIDDLE = 3;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_MANUAL = 4;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_ENVELOPE = 5;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_ENVMANUAL = 6;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_AUTO = 7;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_TRACTOR = 8;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_SMALLFMT = 9;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_LARGEFMT = 10;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_LARGECAPACITY = 11;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_CASSETTE = 14;
   /**
    * Printer tray selector.
    */
   public static final int TRAY_FORMSOURCE = 15;

   /**
    * This constant is used in a few places to signal no value.
    */
   public static final int NONE = 0;

   /**
    * Letter 8 1/2 x 11 inch.
    */
   public static final Size PAPER_LETTER = new Size(8.5, 11);
   /**
    * Letter Small 8 1/2 x 11 inch.
    */
   public static final Size PAPER_LETTERSMALL = new Size(8.5, 11);
   /**
    * Tabloid 11 x 17 inch.
    */
   public static final Size PAPER_TABLOID = new Size(11, 17);
   /**
    * Ledger 17 x 11 inch.
    */
   public static final Size PAPER_LEDGER = new Size(17, 11);
   /**
    * Legal 8 1/2 x 14 inch.
    */
   public static final Size PAPER_LEGAL = new Size(8.5, 14);
   /**
    * Statement 5 1/2 x 8 1/2 inch.
    */
   public static final Size PAPER_STATEMENT = new Size(5.5, 8.5);
   /**
    * Executive 7 1/4 x 10 1/2 inch.
    */
   public static final Size PAPER_EXECUTIVE = new Size(7.25, 10.5);
   /**
    * A3 297 x 420 mmch.
    */
   public static final Size PAPER_A3 = new Size(11.693455, 16.5362);
   /**
    * A4 210 x 297 mmch.
    */
   public static final Size PAPER_A4 = new Size(8.2681, 11.693455);
   /**
    * A4 Small 210 x 297 mmch.
    */
   public static final Size PAPER_A4SMALL = new Size(8.2681, 11.693455);
   /**
    * A5 148 x 210 mmch.
    */
   public static final Size PAPER_A5 = new Size(5.827042, 8.2681);
   /**
    * B4 (JIS) 250 x 354ch.
    */
   public static final Size PAPER_B4 = new Size(9.842976, 13.937654);
   /**
    * B5 (JIS) 182 x 257 mmch.
    */
   public static final Size PAPER_B5 = new Size(7.165687, 10.118579);
   /**
    * Folio 8 1/2 x 13 inch.
    */
   public static final Size PAPER_FOLIO = new Size(8.5, 13);
   /**
    * Quarto 215 x 275 mmch.
    */
   public static final Size PAPER_QUARTO = new Size(8.464959, 10.827274);
   /**
    * 10x14 inch.
    */
   public static final Size PAPER_10X14 = new Size(11, 14);
   /**
    * 11x17 inch.
    */
   public static final Size PAPER_11X17 = new Size(11, 17);
   /**
    * Note 8 1/2 x 11 inch.
    */
   public static final Size PAPER_NOTE = new Size(8.5, 11);
   /**
    * Envelope #9 3 7/8 x 8 7/8ch.
    */
   public static final Size PAPER_ENV_9 = new Size(3.875, 8.875);
   /**
    * Envelope #10 4 1/8 x 9 1/2ch.
    */
   public static final Size PAPER_ENV_10 = new Size(4.125, 9.5);
   /**
    * Envelope #11 4 1/2 x 10 3/8ch.
    */
   public static final Size PAPER_ENV_11 = new Size(4.5, 10.375);
   /**
    * Envelope #12 4 \276 x 11ch.
    */
   public static final Size PAPER_ENV_12 = new Size(4.75, 11);
   /**
    * Envelope #14 5 x 11 1/2ch.
    */
   public static final Size PAPER_ENV_14 = new Size(5, 11.5);
   /**
    * C size sheetch.
    */
   public static final Size PAPER_CSHEET = new Size(17, 22);
   /**
    * D size sheetch.
    */
   public static final Size PAPER_DSHEET = new Size(22, 34);
   /**
    * E size sheetch.
    */
   public static final Size PAPER_ESHEET = new Size(34, 44);
   /**
    * Envelope DL 110 x 220mmch.
    */
   public static final Size PAPER_ENV_DL = new Size(4.330909, 8.661819);
   /**
    * Envelope C5 162 x 229 mmch.
    */
   public static final Size PAPER_ENV_C5 = new Size(6.378248, 9.016166);
   /**
    * Envelope C3  324 x 458 mmch.
    */
   public static final Size PAPER_ENV_C3 = new Size(12.756497, 18.032332);
   /**
    * Envelope C4  229 x 324 mmch.
    */
   public static final Size PAPER_ENV_C4 = new Size(9.016166, 12.756497);
   /**
    * Envelope C6  114 x 162 mmch.
    */
   public static final Size PAPER_ENV_C6 = new Size(4.488397, 6.378248);
   /**
    * Envelope C65 114 x 229 mmch.
    */
   public static final Size PAPER_ENV_C65 = new Size(4.488397, 9.016166);
   /**
    * Envelope B4  250 x 353 mmch.
    */
   public static final Size PAPER_ENV_B4 = new Size(9.842976, 13.898282);
   /**
    * Envelope B5  176 x 250 mmch.
    */
   public static final Size PAPER_ENV_B5 = new Size(6.929455, 9.842976);
   /**
    * Envelope B6  176 x 125 mmch.
    */
   public static final Size PAPER_ENV_B6 = new Size(6.929455, 4.921488);
   /**
    * Envelope 110 x 230 mmch.
    */
   public static final Size PAPER_ENV_ITALY = new Size(4.330909, 9.055538);
   /**
    * Envelope Monarch 3.875 x 7.5 inch.
    */
   public static final Size PAPER_ENV_MONARCH = new Size(3.875, 7.5);
   /**
    * 6 3/4 Envelope 3 5/8 x 6 1/2 inch.
    */
   public static final Size PAPER_ENV_PERSONAL = new Size(3.625, 6.5);
   /**
    * US Std Fanfold 14 7/8 x 11 inch.
    */
   public static final Size PAPER_FANFOLD_US = new Size(14.875, 11);
   /**
    * German Std Fanfold 8 1/2 x 12 inch.
    */
   public static final Size PAPER_FANFOLD_STD_GERMAN = new Size(8.5, 12);
   /**
    * German Legal Fanfold 8 1/2 x 13 inch.
    */
   public static final Size PAPER_FANFOLD_LGL_GERMAN = new Size(8.5, 13);
   /**
    * B4 (ISO) 250 x 353 mmch.
    */
   public static final Size PAPER_ISO_B4 = new Size(9.84375, 13.90625);
   /**
    * Japanese Postcard 100 x 148 mmch.
    */
   public static final Size PAPER_JAPANESE_POSTCARD = new Size(5.5, 5.828125);
   /**
    * 9 x 11 inch.
    */
   public static final Size PAPER_9X11 = new Size(9, 11);
   /**
    * 10 x 11 inch.
    */
   public static final Size PAPER_10X11 = new Size(10, 11);
   /**
    * 15 x 11 inch.
    */
   public static final Size PAPER_15X11 = new Size(15, 11);
   /**
    * Envelope Invite 220 x 220 mmch.
    */
   public static final Size PAPER_ENV_INVITE = new Size(8.661819, 8.661819);
   /**
    * Letter Extra 9 \275 x 12 inch.
    */
   public static final Size PAPER_LETTER_EXTRA = new Size(9.5, 12);
   /**
    * Legal Extra 9 \275 x 15 inch.
    */
   public static final Size PAPER_LEGAL_EXTRA = new Size(9.5, 15);
   /**
    * Tabloid Extra 11.69 x 18 inch.
    */
   public static final Size PAPER_TABLOID_EXTRA = new Size(11.69, 18);
   /**
    * A4 Extra 9.27 x 12.69 inch.
    */
   public static final Size PAPER_A4_EXTRA = new Size(9.24, 12.69);
   /**
    * Letter Transverse 8 \275 x 11 inch.
    */
   public static final Size PAPER_LETTER_TRANSVERSE = new Size(8.5, 11);
   /**
    * A4 Transverse 210 x 297 mmch.
    */
   public static final Size PAPER_A4_TRANSVERSE = new Size(8.5, 11.7);
   /**
    * Letter Extra Transverse 9\275 x 12 inch.
    */
   public static final Size PAPER_LETTER_EXTRA_TRANSVERSE = new Size(9.5, 12);
   /**
    * SuperA/SuperA/A4 227 x 356 mmch.
    */
   public static final Size PAPER_A_PLUS = new Size(8.9375, 14);
   /**
    * SuperB/SuperB/A3 305 x 487 mmch.
    */
   public static final Size PAPER_B_PLUS = new Size(12, 19.171875);
   /**
    * Letter Plus 8.5 x 12.69 inch.
    */
   public static final Size PAPER_LETTER_PLUS = new Size(8.5, 12.69);
   /**
    * A4 Plus 210 x 330 mmch.
    */
   public static final Size PAPER_A4_PLUS = new Size(8.5, 13);
   /**
    * A5 Transverse 148 x 210 mmch.
    */
   public static final Size PAPER_A5_TRANSVERSE = new Size(5.8, 8.5);
   /**
    * B5 (JIS) Transverse 182 x 257 mmch.
    */
   public static final Size PAPER_B5_TRANSVERSE = new Size(7.171875, 10.125);
   /**
    * A3 Extra 322 x 445 mmch.
    */
   public static final Size PAPER_A3_EXTRA = new Size(12.6875, 17.5);
   /**
    * A5 Extra 174 x 235 mmch.
    */
   public static final Size PAPER_A5_EXTRA = new Size(6.85, 9.25);
   /**
    * B5 (ISO) Extra 201 x 276 mmch.
    */
   public static final Size PAPER_B5_EXTRA = new Size(7.090625, 10.859375);
   /**
    * A2 420 x 594 mmch.
    */
   public static final Size PAPER_A2 = new Size(16.5, 23.375);
   /**
    * A3 Transverse 297 x 420 mmch.
    */
   public static final Size PAPER_A3_TRANSVERSE = new Size(11.625, 16.75);
   /**
    * A3 Extra Transverse 322 x 445 mmch.
    */
   public static final Size PAPER_A3_EXTRA_TRANSVERSE = new Size(12.6875, 17.5);
   /**
    * Default Paper.
    */
   public static final Size DEFAULT_PAGE_SIZE = PAPER_LETTER;

   /**
    * Shape style.
    */

   /**
    * Circle shape style.
    */
   public static final int CIRCLE = 900;
   /**
    * Triangle shape style.
    */
   public static final int TRIANGLE = 901;
   /**
    * Square shape style.
    */
   public static final int SQUARE = 902;
   /**
    * Cross shape style.
    */
   public static final int CROSS = 903;
   /**
    * Star shape style.
    */
   public static final int STAR = 904;
   /**
    * Diamond shape style.
    */
   public static final int DIAMOND = 905;
   /**
    * X shape style.
    */
   public static final int X = 906;
   /**
    * Filled circle shape style.
    */
   public static final int FILLED_CIRCLE = 907;
   /**
    * Filled triangle shape style.
    */
   public static final int FILLED_TRIANGLE = 908;
   /**
    * Filled square shape style.
    */
   public static final int FILLED_SQUARE = 909;
   /**
    * Filled diamond shape style.
    */
   public static final int FILLED_DIAMOND = 910;
   /**
    * V angle shape style. (V)
    */
   public static final int V_ANGLE = 911;
   /**
    * Right angle shape style.
    */
   public static final int RIGHT_ANGLE = 912;
   /**
    * "Less_than" angle shape style. (&lt;)
    */
   public static final int LT_ANGLE = 913;
   /**
    * Vertical line shape style.
    */
   public static final int V_LINE = 914;
   /**
    * Horizontal line shape style.
    */
   public static final int H_LINE = 915;
   /**
    * An empty shape.
    */
   public static final int NIL = 916;

   /**
    * Pattern style.
    */

   /**
    * None Pattern.
    */
   public static final int PATTERN_NONE = -1;
   /**
    * Pattern 0.
    */
   public static final int PATTERN_0 = 0;
   /**
    * Pattern 1.
    */
   public static final int PATTERN_1 = 1;
   /**
    * Pattern 2.
    */
   public static final int PATTERN_2 = 2;
   /**
    * Pattern 3.
    */
   public static final int PATTERN_3 = 3;
   /**
    * Pattern 4.
    */
   public static final int PATTERN_4 = 4;
   /**
    * Pattern 5.
    */
   public static final int PATTERN_5 = 5;
   /**
    * Pattern 6.
    */
   public static final int PATTERN_6 = 6;
   /**
    * Pattern 7.
    */
   public static final int PATTERN_7 = 7;
   /**
    * Pattern 8.
    */
   public static final int PATTERN_8 = 8;
   /**
    * Pattern 9.
    */
   public static final int PATTERN_9 = 9;
   /**
    * Pattern 10.
    */
   public static final int PATTERN_10 = 10;
   /**
    * Pattern 11.
    */
   public static final int PATTERN_11 = 11;
   /**
    * Pattern 12.
    */
   public static final int PATTERN_12 = 12;
   /**
    * Pattern 13.
    */
   public static final int PATTERN_13 = 13;
   /**
    * Pattern 14.
    */
   public static final int PATTERN_14 = 14;
   /**
    * Pattern 15.
    */
   public static final int PATTERN_15 = 15;
   /**
    * Pattern 16.
    */
   public static final int PATTERN_16 = 16;
   /**
    * Pattern 17.
    */
   public static final int PATTERN_17 = 17;
   /**
    * Pattern 18.
    */
   public static final int PATTERN_18 = 18;
   /**
    * Pattern 19.
    */
   public static final int PATTERN_19 = 19;

   /**
    * TextBox shape, rectangle.
    */
   public static final int BOX_RECTANGLE = 1;
   /**
    * TextBox shape, rounded rectangle.
    */
   public static final int BOX_ROUNDED_RECTANGLE = 2;

   /**
    * Report background layout, tiled.
    */
   public static final int BACKGROUND_TILED = 1;
   /**
    * Report background layout, center.
    */
   public static final int BACKGROUND_CENTER = 2;

   /**
    * Chart type constant for bar charts.
    */
   public static final int CHART_BAR = 0x01;
   /**
    * Chart type constant for inverted bar charts.
    */
   public static final int CHART_INV_BAR = 0x02;
   /**
    * Chart type constant for stacked bar charts.
    */
   public static final int CHART_STACK_BAR = 0x03;
   /**
    * Chart type constant for inverted stacked bar charts.
    */
   public static final int CHART_INV_STACK_BAR = 0x04;
   /**
    * Chart type constant for bar charts with a 3D effect.
    */
   public static final int CHART_3D_BAR = 0x05;
   /**
    * Chart type constant for stacked bar charts with a 3D effect.
    */
   public static final int CHART_3D_STACK_BAR = 0x06;
   /**
    * Chart type constant for bar charts in 3D coordinates with a 3D effect.
    */
   public static final int CHART_3D_BAR_3D = 0x07;
   /**
    * Chart type constant for pie charts.
    */
   public static final int CHART_PIE = 0x08;
   /**
    * Chart type constant for donut charts.
    */
   public static final int CHART_DONUT = 0x28;
   /**
    * Chart type constant for sunburst charts.
    */
   public static final int CHART_SUNBURST = 0x41;
   /**
    * Chart type constant for treemap charts.
    */
   public static final int CHART_TREEMAP = 0x42;
   /**
    * Chart type constant for circle packing charts.
    */
   public static final int CHART_CIRCLE_PACKING = 0x43;
   /**
    * Chart type constant for icicle charts.
    */
   public static final int CHART_ICICLE = 0x44;
   /**
    * Chart type constant for pie charts with a 3D effect.
    */
   public static final int CHART_3D_PIE = 0x09;
   /**
    * Chart type constant for line charts.
    */
   public static final int CHART_LINE = 0x0A;
   /**
    * Chart type constant for inverted line charts.
    */
   public static final int CHART_INV_LINE = 0x0B;
   /**
    * Chart type constant for ribbon charts (line charts with a 3D effect).
    */
   public static final int CHART_RIBBON = 0x0C;
   /**
    * Chart type constant for curve charts.
    */
   public static final int CHART_CURVE = 0x0D;
   /**
    * Chart type constant for inverted curve charts.
    */
   public static final int CHART_INV_CURVE = 0x0E;
   /**
    * Chart type constant for area charts.
    */
   public static final int CHART_AREA = 0x0F;
   /**
    * Chart type constant for stacked area charts.
    */
   public static final int CHART_STACK_AREA = 0x10;
   /**
    * Chart type constant for stock charts.
    */
   public static final int CHART_STOCK = 0x11;
   /**
    * Chart type constant for stick charts.
    */
   public static final int CHART_STICK = 0x12;
   /**
    * Chart type constant for point charts.
    */
   public static final int CHART_POINT = 0x13;
   /**
    * Chart type constant for inverted point charts.
    */
   public static final int CHART_INV_POINT = 0x14;
   /**
    * Chart type constant for scatter charts.
    */
   public static final int CHART_SCATTER = 0x15;
   /**
    * Chart type constant for line charts where the x-value is a coordinate.
    */
   public static final int CHART_XY_LINE = 0x16;
   /**
    * Chart type constant for bubble charts.
    */
   public static final int CHART_BUBBLE = 0x17;
   /**
    * Chart type constant for radar charts.
    */
   public static final int CHART_RADAR = 0x18;
   /**
    * Chart type constant for filled radar charts.
    */
   public static final int CHART_FILL_RADAR = 0x19;
   /**
    * Chart type constant for candle charts.
    */
   public static final int CHART_CANDLE = 0x1A;
   /**
    * Graph type constant for box plot charts.
    */
   public static final int CHART_BOXPLOT = 0x45;
   /**
    * Graph type constant for marimekko charts.
    */
   public static final int CHART_MEKKO = 0x46;
   /**
    * Chart type constant for 3D surface charts.
    */
   public static final int CHART_SURFACE = 0x1B;
   /**
    * Chart type constant for 3D volume charts.
    */
   public static final int CHART_VOLUME = 0x1C;
   /**
    * Chart type constant for waterfall charts.
    */
   public static final int CHART_WATERFALL = 0x1D;
   /**
    * Chart type constant for pareto charts.
    */
   public static final int CHART_PARETO = 0x1E;
   /**
    * Chart type constant for speedometer charts.
    */
   public static final int CHART_SPEEDOMETER = 0x1F;
   /**
    * Chart type constant for gantt charts.
    */
   public static final int CHART_GANTT = 0x20;
   /**
    * This constant represents no explosion for pie charts.
    */
   public static final int CHART_PIE_EXPLODED_NONE = 0x2A;
   /**
    * This constant represents no explosion for pie charts.
    */
   public static final int CHART_PIE_EXPLODED_ALL = 0x2B;
   /**
    * This constant represents no explosion for pie charts.
    */
   public static final int CHART_PIE_EXPLODED_FIRST = 0x2C;
   /**
    * This constant is the minimum value for user-defined chart types.
    */
   public static final int CHART_USER = 0x80;
   /**
    * This constant is the maximum value for user-defined chart types.
    */
   public static final int CHART_MAX_USER = 0xFF;
}

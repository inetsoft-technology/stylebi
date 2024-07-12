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
package inetsoft.report.composition.region;

import inetsoft.graph.GraphConstants;
import inetsoft.report.StyleConstants;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;

/**
 * The ChartConstants defines some constants of the chart.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ChartConstants {
   /**
    * Renders a linear polynomial trendline.
    */
   public static final int LINEAR = 1;
   /**
    * Renders a quadradic polynomial trendline.
    */
   public static final int QUADRATIC = 2;
   /**
    * Renders a cubic polynomial trendline.
    */
   public static final int CUBIC = 3;
   /**
    * Renders a exponential trendline.
    */
   public static final int EXPONENTIAL = 4;
   /**
    * Renders a logarithmic trendline.
    */
   public static final int LOGARITHMIC = 5;
   /**
    * Renders a power law trendline.
    */
   public static final int POWER = 6;

   /**
    * Aesthetic color field type in script.
    */
   public static final int AESTHETIC_COLOR = 1;
   /**
    * Aesthetic shape field type in script.
    */
   public static final int AESTHETIC_SHAPE = 2;
   /**
    * Aesthetic size field type in script.
    */
   public static final int AESTHETIC_SIZE = 3;
   /**
    * Aesthetic text field type in script.
    */
   public static final int AESTHETIC_TEXT = 4;
   /**
    * Binding field type in script.
    */
   public static final int BINDING_FIELD = 5;
   /**
    * High field of stock or candle in script.
    */
   public static final int HIGH = 0;
   /**
    * Low field of stock or candle in script.
    */
   public static final int LOW = 1;
   /**
    * Close field of stock or candle in script.
    */
   public static final int CLOSE = 2;
   /**
    * Open field of candle in script.
    */
   public static final int OPEN = 3;
   /**
    * Drop to plot region
    */
   public static final int DROP_REGION_PLOT = 1;
   /**
    * Drop to x region.
    */
   public static final int DROP_REGION_X = 2;
   /**
    * Drop to y region.
    */
   public static final int DROP_REGION_Y = 3;
   /**
    * Drop to x2 region.
    */
   public static final int DROP_REGION_X2 = 4;
   /**
    * Drop to y2 region.
    */
   public static final int DROP_REGION_Y2 = 5;
   /**
    * Drop to color region.
    */
   public static final int DROP_REGION_COLOR = 6;
   /**
    * Drop to shape region.
    */
   public static final int DROP_REGION_SHAPE = 7;
   /**
    * Drop to size region.
    */
   public static final int DROP_REGION_SIZE = 8;
   /**
    * Drop to text region.
    */
   public static final int DROP_REGION_TEXT = 9;
   /**
    * Drop to high region.
    */
   public static final int DROP_REGION_HIGH = 10;
   /**
    * Drop to low region.
    */
   public static final int DROP_REGION_LOW = 11;
   /**
    * Drop to open region.
    */
   public static final int DROP_REGION_OPEN = 12;
   /**
    * Drop to close region.
    */
   public static final int DROP_REGION_CLOSE = 13;
   /**
    * Drop to group region.
    */
   public static final int DROP_REGION_GROUP = 14;
   /**
    * Drop to path region.
    */
   public static final int DROP_REGION_PATH = 15;
   /**
    * Drop to geo region for map chart.
    */
   public static final int DROP_REGION_GEO = 16;
   /**
    * Drop to source field of tree/network.
    */
   public static final int DROP_REGION_SOURCE = 17;
   /**
    * Drop to target field of tree/network.
    */
   public static final int DROP_REGION_TARGET = 18;
   /**
    * Drop to start field of gantt.
    */
   public static final int DROP_REGION_START = 19;
   /**
    * Drop to end field of gantt.
    */
   public static final int DROP_REGION_END = 20;
   /**
    * Drop to milestone field of gantt.
    */
   public static final int DROP_REGION_MILESTONE = 21;
   /**
    * String type in script.
    */
   public static final String STRING = "string";
   /**
    * Number type in script.
    */
   public static final String NUMBER = "number";
   /**
    * Date type in script.
    */
   public static final String DATE = "date";
   /**
    * Country map layer.
    */
   public static final String COUNTRY = "Country";
   /**
    * State map layer.
    */
   public static final String STATE = "State";
   /**
    * Province map layer.
    */
   public static final String PROVINCE = "Province";
   /**
    * City map layer.
    */
   public static final String CITY = "City";
   /**
    * Zip map layer.
    */
   public static final String ZIP = "Zip";
   /**
    * Drill down op.
    */
   public static final String DRILL_DOWN_OP = "+";
   /**
    * Drill up op.
    */
   public static final String DRILL_UP_OP = "-";
   /**
    * Drill x direction.
    */
   public static final String DRILL_DIRECTION_X = "x";
   /**
    * Drill y direction.
    */
   public static final String DRILL_DIRECTION_Y = "y";
   /**
    * Drill t area of treeMap.
    */
   public static final String DRILL_DIRECTION_T = "t";
   /**
    * Drill g area of treeMap.
    */
   public static final String DRILL_DIRECTION_G = "g";
   /**
    * Drill aesthetic area.
    */
   public static final String DRILL_DIRECTION_AESTHETIC = "aesthetic";

   /**
    * Drill source area of relation.
    */
   public static final String DRILL_DIRECTION_SOURCE = "source";

   /**
    * Drill target area of relation.
    */
   public static final String DRILL_DIRECTION_TARGET = "target";

   /**
    * This mask can be used to check if a line style is solid.
    */
   public static final int SOLID_MASK = GraphConstants.SOLID_MASK;
   /**
    * This mask is used to extract the dash length.
    */
   static final int DASH_MASK = GraphConstants.DASH_MASK;
   /**
    * This mask is used to extract the encoded line width from line styles.
    */
   static final int WIDTH_MASK = GraphConstants.WIDTH_MASK;
   /**
    * This constant is used to signal no value (e.g. legend or border).
    */
   public static final int NONE = GraphConstants.NONE;
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
    * Placement at the top.
    */
   public static final int TOP = GraphConstants.TOP;
   /**
    * Placement at the right.
    */
   public static final int RIGHT = GraphConstants.RIGHT;
   /**
    * Placement at the bottom.
    */
   public static final int BOTTOM = GraphConstants.BOTTOM;
   /**
    * Placement at the left.
    */
   public static final int LEFT = GraphConstants.LEFT;
   /**
    * Placement at the center.
    */
   public static final int CENTER = GraphConstants.CENTER;
   /**
    * Vertical top alignment.
    */
   public static final int TOP_ALIGNMENT = GraphConstants.TOP_ALIGNMENT;
   /**
    * Vertical middel alignment.
    */
   public static final int MIDDLE_ALIGNMENT = GraphConstants.BOTTOM_ALIGNMENT;
   /**
    * Vertical bottom alignment.
    */
   public static final int BOTTOM_ALIGNMENT = GraphConstants.BOTTOM_ALIGNMENT;
   /**
    * Horizontal left alignment.
    */
   public static final int LEFT_ALIGNMENT = GraphConstants.LEFT_ALIGNMENT;
   /**
    * Horizontal center alignment.
    */
   public static final int CENTER_ALIGNMENT = GraphConstants.CENTER_ALIGNMENT;
   /**
    * Horizontal right alignment.
    */
   public static final int RIGHT_ALIGNMENT = GraphConstants.RIGHT_ALIGNMENT;
   /**
    * Auto placement.
    */
   public static final int AUTO = GraphConstants.AUTO;
   /**
    * The legend will be floated on the top of the chart.
    */
   public static final int IN_PLACE = GraphConstants.IN_PLACE;
   /**
    * Year Option.
    */
   public static final int YEAR_INTERVAL = XConstants.YEAR_DATE_GROUP;
   /**
    * Quarter Option.
    */
   public static final int QUARTER_INTERVAL = XConstants.QUARTER_DATE_GROUP;
   /**
    * Month Option.
    */
   public static final int MONTH_INTERVAL = XConstants.MONTH_DATE_GROUP;
   /**
    * Week Option.
    */
   public static final int WEEK_INTERVAL = XConstants.WEEK_DATE_GROUP;
   /**
    * Day Option.
    */
   public static final int DAY_INTERVAL = XConstants.DAY_DATE_GROUP;
   /**
    * Hour Option.
    */
   public static final int HOUR_INTERVAL = XConstants.HOUR_DATE_GROUP;
   /**
    * Minute Option.
    */
   public static final int MINUTE_INTERVAL = XConstants.MINUTE_DATE_GROUP;
   /**
    * Second Option.
    */
   public static final int SECOND_INTERVAL = XConstants.SECOND_DATE_GROUP;
   /**
    * Dimension data ref.
    */
   public static final int DIMENSION = DateRangeRef.DIMENSION;
   /**
    * Measure data ref.
    */
   public static final int MEASURE = DateRangeRef.MEASURE;
   /**
    * Time dimension data ref.
    */
   public static final int TIME = DateRangeRef.TIME;
   /**
    * Quarter Of Year Part.
    */
   public static final int QUARTER_OF_YEAR_PART =
      XConstants.QUARTER_OF_YEAR_DATE_GROUP;
   /**
    * Month Of Year Part.
    */
   public static final int MONTH_OF_YEAR_PART =
      XConstants.MONTH_OF_YEAR_DATE_GROUP;
   /**
    * Week Of Year Part.
    */
   public static final int WEEK_OF_YEAR_PART =
      XConstants.WEEK_OF_YEAR_DATE_GROUP;
   /**
    * Day Of Month Part.
    */
   public static final int DAY_OF_MONTH_PART =
      XConstants.DAY_OF_MONTH_DATE_GROUP;
   /**
    * Day Of Week Part.
    */
   public static final int DAY_OF_WEEK_PART =
      XConstants.DAY_OF_WEEK_DATE_GROUP;
   /**
    * Hour Of Day Part.
    */
   public static final int HOUR_OF_DAY_PART =
      XConstants.HOUR_OF_DAY_DATE_GROUP;
   /**
    * Minute Of Hour Part.
    */
   public static final int MINUTE_OF_HOUR_PART =
      DateRangeRef.MINUTE_OF_HOUR_PART;
   /**
    * Second Of Minute Part.
    */
   public static final int SECOND_OF_MINUTE_PART =
      DateRangeRef.SECOND_OF_MINUTE_PART;
   /**
    * Ascending sorting order.
    */
   public static final int SORT_ASC = DateRangeRef.SORT_ASC;
   /**
    * Descending sorting order.
    */
   public static final int SORT_DESC = DateRangeRef.SORT_DESC;
   /**
    * No sorting.
    */
   public static final int SORT_NONE = DateRangeRef.SORT_NONE;
   /**
    * Original sorting.
    */
   public static final int SORT_ORIGINAL = DateRangeRef.SORT_ORIGINAL;
   /**
    * Specific sorting.
    */
   public static final int SORT_SPECIFIC = DateRangeRef.SORT_SPECIFIC;
   /**
    * Sort by (aggregate) value, ascending.
    */
   public static final int SORT_VALUE_ASC = DateRangeRef.SORT_VALUE_ASC;
   /**
    * Sort by (aggregate) value, descending.
    */
   public static final int SORT_VALUE_DESC = DateRangeRef.SORT_VALUE_DESC;
   /**
    * None date group.
    */
   public static final int NONE_DATE_GROUP = DateRangeRef.NONE_DATE_GROUP;
   /**
    * Year date group.
    */
   public static final int YEAR_DATE_GROUP = DateRangeRef.YEAR_DATE_GROUP;
   /**
    * Quarter date group.
    */
   public static final int QUARTER_DATE_GROUP = DateRangeRef.QUARTER_DATE_GROUP;
   /**
    * Month date group.
    */
   public static final int MONTH_DATE_GROUP = DateRangeRef.MONTH_DATE_GROUP;
   /**
    * Week date group.
    */
   public static final int WEEK_DATE_GROUP = DateRangeRef.WEEK_DATE_GROUP;
   /**
    * Day date group.
    */
   public static final int DAY_DATE_GROUP = DateRangeRef.DAY_DATE_GROUP;
   /**
    * AM/PM date group.
    */
   public static final int AM_PM_DATE_GROUP = DateRangeRef.AM_PM_DATE_GROUP;
   /**
    * Hour date group.
    */
   public static final int HOUR_DATE_GROUP = DateRangeRef.HOUR_DATE_GROUP;
   /**
    * Minute date group.
    */
   public static final int MINUTE_DATE_GROUP = DateRangeRef.MINUTE_DATE_GROUP;
   /**
    * Second date group.
    */
   public static final int SECOND_DATE_GROUP = DateRangeRef.SECOND_DATE_GROUP;
   /**
    * Millisecond date group.
    */
   public static final int MILLISECOND_DATE_GROUP =
      DateRangeRef.MILLISECOND_DATE_GROUP;
   /**
    * Part date group.
    */
   public static final int PART_DATE_GROUP = DateRangeRef.PART_DATE_GROUP;
   /**
    * Quarter of year date group.
    */
   public static final int QUARTER_OF_YEAR_DATE_GROUP =
      DateRangeRef.QUARTER_OF_YEAR_DATE_GROUP;
   /**
    * Month of year date group.
    */
   public static final int MONTH_OF_YEAR_DATE_GROUP =
      DateRangeRef.MONTH_OF_YEAR_DATE_GROUP;
   /**
    * Week of of year date group.
    */
   public static final int WEEK_OF_YEAR_DATE_GROUP =
      DateRangeRef.WEEK_OF_YEAR_DATE_GROUP;
   /**
    * Week of month date group.
    */
   public static final int WEEK_OF_MONTH_DATE_GROUP =
      DateRangeRef.WEEK_OF_MONTH_DATE_GROUP;
   /**
    * Day of year date group.
    */
   public static final int DAY_OF_YEAR_DATE_GROUP =
      DateRangeRef.DAY_OF_YEAR_DATE_GROUP;
   /**
    * Day of month date group.
    */
   public static final int DAY_OF_MONTH_DATE_GROUP =
      DateRangeRef.DAY_OF_MONTH_DATE_GROUP;
   /**
    * Day of week date group.
    */
   public static final int DAY_OF_WEEK_DATE_GROUP =
      DateRangeRef.DAY_OF_WEEK_DATE_GROUP;
   /**
    * Am/pm of day date group.
    */
   public static final int AM_PM_OF_DAY_DATE_GROUP =
      DateRangeRef.AM_PM_OF_DAY_DATE_GROUP;
   /**
    * Hour of day date group.
    */
   public static final int HOUR_OF_DAY_DATE_GROUP =
      DateRangeRef.HOUR_OF_DAY_DATE_GROUP;
   /**
    * Formula "None".
    */
   public static final String NONE_FORMULA = DateRangeRef.NONE_FORMULA;
   /**
    * Formula "Average".
    */
   public static final String AVERAGE_FORMULA = DateRangeRef.AVERAGE_FORMULA;
   /**
    * Formula "Count".
    */
   public static final String COUNT_FORMULA = DateRangeRef.COUNT_FORMULA;
   /**
    * Formula "DistinctCount".
    */
   public static final String DISTINCTCOUNT_FORMULA =
      DateRangeRef.DISTINCTCOUNT_FORMULA;
   /**
    * Formula "Max".
    */
   public static final String MAX_FORMULA = DateRangeRef.MAX_FORMULA;
   /**
    * Formula "Min".
    */
   public static final String MIN_FORMULA = DateRangeRef.MIN_FORMULA;
   /**
    * Formula "Product".
    */
   public static final String PRODUCT_FORMULA = DateRangeRef.PRODUCT_FORMULA;
   /**
    * Formula "Sum".
    */
   public static final String SUM_FORMULA = DateRangeRef.SUM_FORMULA;
   /**
    * Formula "Set".
    */
   public static final String SET_FORMULA = DateRangeRef.SET_FORMULA;
   /**
    * Formula "Concat".
    */
   public static final String CONCAT_FORMULA =
      DateRangeRef.CONCAT_FORMULA;
   /**
    * Formula "StandardDeviation".
    */
   public static final String STANDARDDEVIATION_FORMULA =
      DateRangeRef.STANDARDDEVIATION_FORMULA;
   /**
    * Formula "Variance".
    */
   public static final String VARIANCE_FORMULA = DateRangeRef.VARIANCE_FORMULA;
   /**
    * Formula "PopulationStandardDeviation".
    */
   public static final String POPULATIONSTANDARDDEVIATION_FORMULA =
      DateRangeRef.POPULATIONSTANDARDDEVIATION_FORMULA;
   /**
    * Formula "PopulationVariance".
    */
   public static final String POPULATIONVARIANCE_FORMULA =
      DateRangeRef.POPULATIONVARIANCE_FORMULA;
   /**
    * Formula "Correlation".
    */
   public static final String CORRELATION_FORMULA =
      DateRangeRef.CORRELATION_FORMULA;
   /**
    * Formula "Covariance".
    */
   public static final String COVARIANCE_FORMULA =
      DateRangeRef.COVARIANCE_FORMULA;
   /**
    * Formula "Median".
    */
   public static final String MEDIAN_FORMULA = DateRangeRef.MEDIAN_FORMULA;
   /**
    * Formula "Mode".
    */
   public static final String MODE_FORMULA = DateRangeRef.MODE_FORMULA;
   /**
    * Formula "NthLargest".
    */
   public static final String NTHLARGEST_FORMULA =
      DateRangeRef.NTHLARGEST_FORMULA;
   /**
    * Formula "NthMostFrequent".
    */
   public static final String NTHMOSTFREQUENT_FORMULA =
      DateRangeRef.NTHMOSTFREQUENT_FORMULA;
   /**
    * Formula "NthSmallest".
    */
   public static final String NTHSMALLEST_FORMULA =
      DateRangeRef.NTHSMALLEST_FORMULA;
   /**
    * Formula "PthPercentile".
    */
   public static final String PTHPERCENTILE_FORMULA =
      DateRangeRef.PTHPERCENTILE_FORMULA;
   /**
    * Formula "WeightedAverage".
    */
   public static final String WEIGHTEDAVERAGE_FORMULA =
      DateRangeRef.WEIGHTEDAVERAGE_FORMULA;
   /**
    * Formula "WeightSum".
    */
   public static final String SUMWT_FORMULA = DateRangeRef.SUMWT_FORMULA;
   /**
    * Forumula "SumSQ"
    */
   public static final String SUMSQ_FORMULA = DateRangeRef.SUMSQ_FORMULA;
   /**
    * Date format type.
    */
   public static final String DATE_FORMAT = DateRangeRef.DATE_FORMAT;
   /**
    * Decimal format type.
    */
   public static final String DECIMAL_FORMAT = DateRangeRef.DECIMAL_FORMAT;
   /**
    * Percent format type.
    */
   public static final String PERCENT_FORMAT = DateRangeRef.PERCENT_FORMAT;
   /**
    * Message format type.
    */
   public static final String MESSAGE_FORMAT = DateRangeRef.MESSAGE_FORMAT;
   /**
    * No Percentage.
    */
   public static final int PERCENTAGE_NONE = DateRangeRef.PERCENTAGE_NONE;
   /**
    * Percentage of group.
    */
   public static final int PERCENTAGE_OF_GROUP =
      DateRangeRef.PERCENTAGE_OF_GROUP;
   /**
    * Percentage of grand total.
    */
   public static final int PERCENTAGE_OF_GRANDTOTAL =
      DateRangeRef.PERCENTAGE_OF_GRANDTOTAL;
   /**
    * Percentage by column. Only use in CrossTabFilter.
    */
   public static final int PERCENTAGE_BY_COL = DateRangeRef.PERCENTAGE_BY_COL;
   /**
    * Percentage by row. Only use in CrossTabFilter.
    */
   public static final int PERCENTAGE_BY_ROW = DateRangeRef.PERCENTAGE_BY_ROW;
   /**
    * Represents current column.
    */
   public static final String COLUMN = DateRangeRef.COLUMN;

   /**
    * Texture styles.
    */
   public static final int[] TEXTURE_STYLES = {StyleConstants.PATTERN_NONE,
      StyleConstants.PATTERN_0, StyleConstants.PATTERN_1,
      StyleConstants.PATTERN_2, StyleConstants.PATTERN_3,
      StyleConstants.PATTERN_4, StyleConstants.PATTERN_5,
      StyleConstants.PATTERN_6, StyleConstants.PATTERN_7,
      StyleConstants.PATTERN_8, StyleConstants.PATTERN_9,
      StyleConstants.PATTERN_10, StyleConstants.PATTERN_11,
      StyleConstants.PATTERN_12, StyleConstants.PATTERN_13,
      StyleConstants.PATTERN_14, StyleConstants.PATTERN_15,
      StyleConstants.PATTERN_16, StyleConstants.PATTERN_17,
      StyleConstants.PATTERN_18, StyleConstants.PATTERN_19,
   };

   /**
    * Line styles for line model.
    */
   public static final int[] M_LINE_STYLES = {StyleConstants.THIN_LINE,
      StyleConstants.DOT_LINE, StyleConstants.DASH_LINE,
      StyleConstants.MEDIUM_DASH, StyleConstants.LARGE_DASH,
   };

   /**
    * Trendline types.
    */
   public static final int[] TRENDLINE_TYPES = {LINEAR,
      QUADRATIC, CUBIC, EXPONENTIAL, LOGARITHMIC, POWER
   };
}

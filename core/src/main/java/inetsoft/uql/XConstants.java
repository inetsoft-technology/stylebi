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
package inetsoft.uql;

/**
 * This class defines the constants used in package inetsoft.uql.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public interface XConstants extends java.io.Serializable {
   /**
    * Ascending sorting order.
    */
   int SORT_ASC = 1;
   /**
    * Descending sorting order.
    */
   int SORT_DESC = 2;
   /**
    * No sorting.
    */
   int SORT_NONE = 0;
   /**
    * Original sorting.
    */
   int SORT_ORIGINAL = 4;
   /**
    * Specific sorting.
    */
   int SORT_SPECIFIC = 8;
   /**
    * Sort by (aggregate) value, ascending.
    */
   int SORT_VALUE_ASC = SORT_ASC | 16;
   /**
    * Sort by (aggregate) value, descending.
    */
   int SORT_VALUE_DESC = SORT_DESC | 16;

   /**
    * Other group option when using specific order: put all others together.
    */
   int GROUP_OTHERS = 1;
   /**
    * Other group option when using specific order:
    * leave all other data in their own group.
    */
   int LEAVE_OTHERS = 2;

   /**
    * None date group.
    */
   int NONE_DATE_GROUP = 0;
   /**
    * Year date group.
    */
   int YEAR_DATE_GROUP = 5;
   /**
    * Quarter date group.
    */
   int QUARTER_DATE_GROUP = 4;
   /**
    * Month date group.
    */
   int MONTH_DATE_GROUP = 3;
   /**
    * Week date group.
    */
   int WEEK_DATE_GROUP = 2;
   /**
    * Day date group.
    */
   int DAY_DATE_GROUP = 1;
   /**
    * AM/PM date group.
    */
   int AM_PM_DATE_GROUP = 9;
   /**
    * Hour date group.
    */
   int HOUR_DATE_GROUP = 8;
   /**
    * Minute date group.
    */
   int MINUTE_DATE_GROUP = 7;
   /**
    * Second date group.
    */
   int SECOND_DATE_GROUP = 6;
   /**
    * Millisecond date group.
    */
   int MILLISECOND_DATE_GROUP = 10;

   /**
    * Part date group.
    */
   int PART_DATE_GROUP = 512;
   /**
    * Quarter of year date group.
    */
   int QUARTER_OF_YEAR_DATE_GROUP = 1 | PART_DATE_GROUP;
   /**
    * Month of year date group.
    */
   int MONTH_OF_YEAR_DATE_GROUP = 2 | PART_DATE_GROUP;
   /**
    * Week of of year date group.
    */
   int WEEK_OF_YEAR_DATE_GROUP = 3 | PART_DATE_GROUP;
   /**
    * Week of month date group.
    */
   int WEEK_OF_MONTH_DATE_GROUP = 4 | PART_DATE_GROUP;
   /**
    * Day of year date group.
    */
   int DAY_OF_YEAR_DATE_GROUP = 5 | PART_DATE_GROUP;
   /**
    * Day of month date group.
    */
   int DAY_OF_MONTH_DATE_GROUP = 6 | PART_DATE_GROUP;
   /**
    * Day of week date group.
    */
   int DAY_OF_WEEK_DATE_GROUP = 7 | PART_DATE_GROUP;
   /**
    * Am/pm of day date group.
    */
   int AM_PM_OF_DAY_DATE_GROUP = 8 | PART_DATE_GROUP;
   /**
    * Hour of day date group.
    */
   int HOUR_OF_DAY_DATE_GROUP = 9 | PART_DATE_GROUP;
   /**
    * Minute of hour date group.
    */
   int MINUTE_OF_HOUR_DATE_GROUP = 10 | PART_DATE_GROUP;
   /**
    * Second of minute date group.
    */
   int SECOND_OF_MINUTE_DATE_GROUP = 11 | PART_DATE_GROUP;

   /**
    * Join operation.
    */
   int JOIN = 1;
   /**
    * Inner join operation.
    */
   int INNER_JOIN = 2 | JOIN;
   /**
    * Left join operation.
    */
   int LEFT_JOIN = 4 | INNER_JOIN;
   /**
    * Right join operation.
    */
   int RIGHT_JOIN = 8 | INNER_JOIN;
   /**
    * Full join operation.
    */
   int FULL_JOIN = LEFT_JOIN | RIGHT_JOIN;
   /**
    * Not equal join operation.
    */
   int NOT_EQUAL_JOIN = 16 | JOIN;
   /**
    * Greater join operation.
    */
   int GREATER_JOIN = 32 | JOIN;
   /**
    * Greater equal join operation.
    */
   int GREATER_EQUAL_JOIN = GREATER_JOIN | INNER_JOIN;
   /**
    * Less join operation.
    */
   int LESS_JOIN = 64 | JOIN;
   /**
    * Less equal join operation.
    */
   int LESS_EQUAL_JOIN = LESS_JOIN | INNER_JOIN;

   /**
    * Formula "None".
    */
   String NONE_FORMULA = "none";
   /**
    * Formula "Average".
    */
   String AVERAGE_FORMULA = "Average";
   /**
    * Formula "Count".
    */
   String COUNT_FORMULA = "Count";
   /**
    * Formula "DistinctCount".
    */
   String DISTINCTCOUNT_FORMULA = "DistinctCount";
   /**
    * Formula "Max".
    */
   String MAX_FORMULA = "Max";
   /**
    * Formula "Min".
    */
   String MIN_FORMULA = "Min";
   /**
    * Formula "Product".
    */
   String PRODUCT_FORMULA = "Product";
   /**
    * Formula "Sum".
    */
   String SUM_FORMULA = "Sum";
   /**
    * Formula "Set".
    */
   String SET_FORMULA = "Set";
   /**
    * Formula "Concat".
    */
   String CONCAT_FORMULA = "Concat";
   /**
    * Formula "StandardDeviation".
    */
   String STANDARDDEVIATION_FORMULA = "StandardDeviation";
   /**
    * Formula "Variance".
    */
   String VARIANCE_FORMULA = "Variance";
   /**
    * Formula "PopulationStandardDeviation".
    */
   String POPULATIONSTANDARDDEVIATION_FORMULA =
      "PopulationStandardDeviation";
   /**
    * Formula "PopulationVariance".
    */
   String POPULATIONVARIANCE_FORMULA =
      "PopulationVariance";
   /**
    * Formula "Correlation".
    */
   String CORRELATION_FORMULA = "Correlation";
   /**
    * Formula "Covariance".
    */
   String COVARIANCE_FORMULA = "Covariance";
   /**
    * Formula "Median".
    */
   String MEDIAN_FORMULA = "Median";
   /**
    * Formula "Mode".
    */
   String MODE_FORMULA = "Mode";
   /**
    * Formula "NthLargest".
    */
   String NTHLARGEST_FORMULA = "NthLargest";
   /**
    * Formula "NthMostFrequent".
    */
   String NTHMOSTFREQUENT_FORMULA = "NthMostFrequent";
   /**
    * Formula "NthSmallest".
    */
   String NTHSMALLEST_FORMULA = "NthSmallest";
   /**
    * Formula "PthPercentile".
    */
   String PTHPERCENTILE_FORMULA = "PthPercentile";
   /**
    * Formula "WeightedAverage".
    */
   String WEIGHTEDAVERAGE_FORMULA = "WeightedAverage";
   /**
    * Formula "WeightSum".
    */
   String SUMWT_FORMULA = "SumWT";
   /**
    * Forumula "SumSQ"
    */
   String SUMSQ_FORMULA = "SumSQ";
   /**
    * Formula "Calc".
    */
   String CALC_FORMULA = "Calc";
   /**
    * Formula "First".
    */
   String FIRST_FORMULA = "First";
   /**
    * Formula "Last".
    */
   String LAST_FORMULA = "Last";

   /**
    * Date format type.
    */
   String DATE_FORMAT = "DateFormat";

   /**
    * Time format type.
    */
   String TIME_FORMAT = "TimeFormat";

   /**
    * TimeInstant format type.
    */
   String TIMEINSTANT_FORMAT = "TimeInstantFormat";

   /**
    * Decimal format type.
    */
   String DECIMAL_FORMAT = "DecimalFormat";
   /**
    * Comma format type.
    */
   String COMMA_FORMAT = "CommaFormat";
   /**
    * Currency format type.
    */
   String CURRENCY_FORMAT = "CurrencyFormat";
   /**
    * Percent format type.
    */
   String PERCENT_FORMAT = "PercentFormat";
   /**
    * Message format type.
    */
   String MESSAGE_FORMAT = "MessageFormat";
   /**
    * Duration format left pad with zeros type.
    */
   String DURATION_FORMAT = "DurationFormat";
   /**
    * Duration format left do not pad with zeros type.
    */
   String DURATION_FORMAT_PAD_NON = "DurationFormatPadNon";
   /**
    * Condition NULL value.
    */
   String CONDITION_NULL_VALUE = "NULL_VALUE";
   /**
    * Condition Empty string.
    */
   String CONDITION_EMPTY_STRING = "EMPTY_STRING";
   /**
    * Condition string null.
    */
   String CONDITION_NULL_STRING = "NULL_STRING";
   /**
    * Condition real NULL.
    */
   String CONDITION_REAL_NULL = "REAL_NULL";

   /**
    * No Percentage.
    */
   int PERCENTAGE_NONE = 0;
   /**
    * Percentage of group.
    */
   int PERCENTAGE_OF_GROUP = 1;
   /**
    * Percentage of grand total.
    */
   int PERCENTAGE_OF_GRANDTOTAL = 2;
   /**
    * Percentage of row group.
    */
   int PERCENTAGE_OF_ROW_GROUP = 4;
   /**
    * Percentage of col group.
    */
   int PERCENTAGE_OF_COL_GROUP = 8;
   /**
    * Percentage of row grand total.
    */
   int PERCENTAGE_OF_ROW_GRANDTOTAL = 16;
   /**
    * Percentage of col grand total.
    */
   int PERCENTAGE_OF_COL_GRANDTOTAL = 32;
   /**
    * Percentage by column. Only use in CrossTabFilter.
    */
   int PERCENTAGE_BY_COL = 1;
   /**
    * Percentage by row. Only use in CrossTabFilter.
    */
   int PERCENTAGE_BY_ROW = 2;
   /**
    * column headers. Only use in CrossTab binding script.
    */
   int COL_HEADER = 1;
   /**
    * row headers. Only use in CrossTab binding script.
    */
   int ROW_HEADER = 2;

   /**
    * Current repository will change.
    */
   String CURRENT_REP_WILL_CHANGE =
      "__current_rep_will_change__";
   /**
    * Current repository changed.
    */
   String CURRENT_REP_CHANGED = "__current_rep_changed__";

   /**
    * Represents current column.
    */
   String COLUMN = "this.column";
   /**
    * Sub query parameter in drill path.
    */
   String SUB_QUERY_PARAM = "sub_query_param";

   /**
    * Sub query parameter in drill path.
    */
   String SUB_QUERY_PARAM_PREFIX = SUB_QUERY_PARAM + "_";

   /**
    * Sub query parameter prefix in drill path.
    */
   String PARAM_PREFIX = "Param_";
}
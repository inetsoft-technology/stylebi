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
export const enum XConstants {
   /**
    * No sorting.
    */
   SORT_NONE = 0,
   /**
    * Ascending sorting order.
    */
   SORT_ASC = 1,
   /**
    * Descending sorting order.
    */
   SORT_DESC = 2,
   /**
    * Original sorting order.
    */
   SORT_ORIGINAL = 4,
   /**
    * Specific sorting.
    */
   SORT_SPECIFIC = 8,
   /**
    * Sort by (aggregate) value, ascending.
    */
   SORT_VALUE_ASC = 1 | 16,
   /**
    * Sort by (aggregate) value, descending.
    */
   SORT_VALUE_DESC = 2 | 16,

   /**
    * None date group.
    */
   NONE_DATE_GROUP = 0,
   /**
    * Year date group.
    */
   YEAR_DATE_GROUP = 5,
   /**
    * Quarter date group.
    */
   QUARTER_DATE_GROUP = 4,
   /**
    * Month date group.
    */
   MONTH_DATE_GROUP = 3,
   /**
    * Week date group.
    */
   WEEK_DATE_GROUP = 2,
   /**
    * Day date group.
    */
   DAY_DATE_GROUP = 1,
   /**
    * AM/PM date group.
    */
   AM_PM_DATE_GROUP = 9,
   /**
    * Hour date group.
    */
   HOUR_DATE_GROUP = 8,
   /**
    * Minute date group.
    */
   MINUTE_DATE_GROUP = 7,
   /**
    * Second date group.
    */
   SECOND_DATE_GROUP = 6,
   /**
    * Millisecond date group.
    */
   MILLISECOND_DATE_GROUP = 10,
   /**
    * Part date group.
    */
   PART_DATE_GROUP = 512,
   /**
    * Quarter of year date group.
    */
   QUARTER_OF_YEAR_DATE_GROUP = 1 | PART_DATE_GROUP,
   /**
    * Month of year date group.
    */
   MONTH_OF_YEAR_DATE_GROUP = 2 | PART_DATE_GROUP,
   /**
    * Week of of year date group.
    */
   WEEK_OF_YEAR_DATE_GROUP = 3 | PART_DATE_GROUP,
   /**
    * Week of month date group.
    */
   WEEK_OF_MONTH_DATE_GROUP = 4 | PART_DATE_GROUP,
   /**
    * Day of year date group.
    */
   DAY_OF_YEAR_DATE_GROUP = 5 | PART_DATE_GROUP,
   /**
    * Day of month date group.
    */
   DAY_OF_MONTH_DATE_GROUP = 6 | PART_DATE_GROUP,
   /**
    * Day of week date group.
    */
   DAY_OF_WEEK_DATE_GROUP = 7 | PART_DATE_GROUP,
   /**
    * Am/pm of day date group.
    */
   AM_PM_OF_DAY_DATE_GROUP = 8 | PART_DATE_GROUP,
   /**
    * Hour of day date group.
    */
   HOUR_OF_DAY_DATE_GROUP = 9 | PART_DATE_GROUP,
   /**
    * Minute of hour date group.
    */
    MINUTE_OF_HOUR_DATE_GROUP = 10 | PART_DATE_GROUP,
   /**
    * Second of minute date group.
    */
   SECOND_OF_MINUTE_DATE_GROUP = 11 | PART_DATE_GROUP,
   /**
    * Join operation.
    */
   JOIN = 1,
   /**
    * Inner join operation.
    */
   INNER_JOIN = 2 | JOIN,
   /**
    * Left join operation.
    */
   LEFT_JOIN = 4 | INNER_JOIN,
   /**
    * Right join operation.
    */
   RIGHT_JOIN = 8 | INNER_JOIN,
   /**
    * Full join operation.
    */
   FULL_JOIN = LEFT_JOIN | RIGHT_JOIN,
   /**
    * Not equal join operation.
    */
   NOT_EQUAL_JOIN = 16 | JOIN,
   /**
    * Greater join operation.
    */
   GREATER_JOIN = 32 | JOIN,
   /**
    * Greater equal join operation.
    */
   GREATER_EQUAL_JOIN = GREATER_JOIN | INNER_JOIN,
   /**
    * Less join operation.
    */
   LESS_JOIN = 64 | JOIN,
   /**
    * Less equal join operation.
    */
   LESS_EQUAL_JOIN = LESS_JOIN | INNER_JOIN,
   /**
    * Concatenation operation.
    */
   CONCATENATION = 512,
   /**
    * Union operation.
    */
   UNION = 1024 | CONCATENATION,
   /**
    * Intersect operation.
    */
   INTERSECT = 2048 | CONCATENATION,
   /**
    * Minus operation.
    */
   MINUS = 4096 | CONCATENATION
}

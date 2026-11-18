/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.uql.XConstants;

/**
 * The CHANGE/VALUE "previous period" shifts that cannot land on a date dimension's buckets,
 * shared by the crosstab and the chart binding guards (bugs #80016, #81687).
 */
final class CalculatorShift {
   private CalculatorShift() {
   }

   /**
    * The (target level, from) pairs whose shift is not a whole number of the level's buckets, so
    * the previous-date lookup never finds a key. year: 5/6/7, quarter: 6/7, month: 6.
    */
   static boolean smallerThanBucket(int level, int from) {
      return switch(level) {
         case XConstants.YEAR_DATE_GROUP -> from == ValueOfCalc.PREVIOUS_QUARTER ||
            from == ValueOfCalc.PREVIOUS_WEEK || from == ValueOfCalc.PREVIOUS_MONTH;
         case XConstants.QUARTER_DATE_GROUP -> from == ValueOfCalc.PREVIOUS_WEEK ||
            from == ValueOfCalc.PREVIOUS_MONTH;
         case XConstants.MONTH_DATE_GROUP -> from == ValueOfCalc.PREVIOUS_WEEK;
         default -> false;
      };
   }

   static String advice(int from) {
      return switch(from) {
         case ValueOfCalc.PREVIOUS_QUARTER ->
            "Bind the date at quarter level or finer so the Previous Quarter value exists, use " +
            "from 4 (Previous Year) on a year dimension, or remove the calculateInfo.";
         case ValueOfCalc.PREVIOUS_WEEK ->
            "Bind the date at day level so the Previous Week value exists, or remove the " +
            "calculateInfo.";
         default ->
            "Bind the date at month level or finer (rows = [the date field at month level] " +
            "compares January with the previous December), use from 4 (Previous Year) on a " +
            "year dimension, or remove the calculateInfo.";
      };
   }

   static String previousName(int from) {
      return switch(from) {
         case ValueOfCalc.PREVIOUS_YEAR -> "Previous Year";
         case ValueOfCalc.PREVIOUS_QUARTER -> "Previous Quarter";
         case ValueOfCalc.PREVIOUS_WEEK -> "Previous Week";
         default -> "Previous Month";
      };
   }
}

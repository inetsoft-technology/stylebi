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
package inetsoft.web.wiz.service;

import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.web.binding.model.graph.CalculateInfo;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import inetsoft.web.wiz.model.Ranking;

final class WizFieldInfoFactory {
   private WizFieldInfoFactory() {
   }

   static DimensionFieldInfo createDimensionFieldInfo(VSDimensionRef dim) {
      DimensionFieldInfo info = baseDimensionFieldInfo(dim);
      info.setFullName(dim.getFullName());
      applyDateGroup(info, dim);
      applyRanking(info, dim);
      return info;
   }

   static DimensionFieldInfo createCrosstabDimensionFieldInfo(VSDimensionRef dim) {
      DimensionFieldInfo info = baseDimensionFieldInfo(dim);
      info.setType(dim.getDataType());
      info.setFullName(crosstabDimFullName(dim));
      applyDateGroup(info, dim);
      applyRanking(info, dim);
      info.setSummarize(dim.isSubTotalVisible());
      return info;
   }

   /**
    * A crosstab design ref built from an explicit binding has no backing ColumnRef, so getVSName()
    * is empty and getFullName() short-circuits to "" before the date-qualifying branch. Derive the
    * name directly from the group column: a level-qualified name (e.g. "DayOfWeek(date_start)") for a
    * DATE-typed dimension that carries a real date level, else the plain column name.
    *
    * The date-type guard matters: a non-date crosstab dimension (a string/numeric column) can carry a
    * spurious default YEAR date level, and date-naming it would echo a bogus "Year(status)" fullName
    * that pollutes the downstream facts pack. We only date-qualify genuine date dimensions, and always
    * fall back to the group column (never an empty string) for everything else.
    */
   static String crosstabDimFullName(VSDimensionRef dim) {
      String fullName = dim.getFullName();
      String groupColumn = dim.getGroupColumnValue();

      if((fullName == null || fullName.isEmpty() || fullName.equals(groupColumn))
         && groupColumn != null && !groupColumn.isEmpty())
      {
         if(dim.isDateTime() && dim.getDateLevel() != XConstants.NONE_DATE_GROUP) {
            return DateRangeRef.getName(groupColumn, dim.getDateLevel());
         }

         return groupColumn;
      }

      return fullName;
   }

   static DimensionFieldInfo createChartDimensionFieldInfo(VSDimensionRef dim) {
      DimensionFieldInfo info = baseDimensionFieldInfo(dim);
      info.setFullName(dim.getFullName());
      applyDateGroup(info, dim);
      applyRanking(info, dim);
      return info;
   }

   static MeasureFieldInfo createMeasureFieldInfo(VSAggregateRef agg) {
      MeasureFieldInfo info = new MeasureFieldInfo();
      info.setField(agg.getColumnValue());
      info.setFullName(agg.getFullName());
      info.setAggregateFormula(agg.getFormulaValue());

      if(agg.getSecondaryColumnValue() != null) {
         info.setSecondaryField(agg.getSecondaryColumnValue());
      }

      if(agg.getN() != 0) {
         info.setNOrP(agg.getN());
      }

      return info;
   }

   static MeasureFieldInfo createCrosstabMeasureFieldInfo(VSAggregateRef agg) {
      MeasureFieldInfo info = new MeasureFieldInfo();
      info.setField(agg.getColumnValue());
      info.setAggregateFormula(agg.getFormula() != null ? agg.getFormula().getFormulaName() : null);
      info.setCalculateInfo(CalculateInfo.createCalcInfo(agg.getCalculator()));
      info.setPercentage(percentageName(agg.getPercentageOption()));
      return info;
   }

   /** Reverse of WizVsService#percentageOption: XConstants option → wiz percentage name. */
   private static String percentageName(int option) {
      return switch(option) {
         case XConstants.PERCENTAGE_OF_GRANDTOTAL -> "GrandTotal";
         case XConstants.PERCENTAGE_OF_GROUP -> "Group";
         default -> "None";
      };
   }

   static MeasureFieldInfo createChartMeasureFieldInfo(VSAggregateRef agg) {
      MeasureFieldInfo info = new MeasureFieldInfo();
      info.setField(agg.getColumnValue());
      info.setAggregateFormula(agg.getFormulaValue());
      info.setFullName(agg.getFullName());
      info.setCalculateInfo(CalculateInfo.createCalcInfo(agg.getCalculator()));

      if(agg instanceof VSChartAggregateRef chartAgg) {
         info.setDiscrete(chartAgg.isDiscrete());
         info.setSecondaryY(chartAgg.isSecondaryY());
      }

      return info;
   }

   private static DimensionFieldInfo baseDimensionFieldInfo(VSDimensionRef dim) {
      DimensionFieldInfo info = new DimensionFieldInfo();
      info.setField(dim.getGroupColumnValue());

      String sortByCol = dim.getSortByColValue();

      if(sortByCol != null && !sortByCol.isEmpty()) {
         info.setSortByCol(sortByCol);
      }

      return info;
   }

   private static void applyRanking(DimensionFieldInfo info, VSDimensionRef dim) {
      int opt = dim.getRankingOption();

      if(opt != XCondition.NONE) {
         Ranking ranking = new Ranking();
         ranking.setOptionValue(opt);

         int n = dim.getRankingN();

         if(n != 0) {
            ranking.setRankingN(n);
         }

         ranking.setRankingCol(dim.getRankingColValue());
         ranking.setGroupOthers(dim.isGroupOthers());
         info.setRanking(ranking);
      }
   }

   private static void applyDateGroup(DimensionFieldInfo info, VSDimensionRef dim) {
      int level = dim.getDateLevel();

      if(XSchema.isDateType(dim.getDataType()) && level != XConstants.NONE_DATE_GROUP) {
         info.setDateGroupLevel(WizDateLevelUtil.getDateGroupLevelName(level));
         info.setTimeSeries(dim.isTimeSeries());
      }
   }
}

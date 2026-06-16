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

import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
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
      applyDateGroup(info, dim);
      applyRanking(info, dim);
      return info;
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
      return info;
   }

   static MeasureFieldInfo createChartMeasureFieldInfo(VSAggregateRef agg) {
      MeasureFieldInfo info = new MeasureFieldInfo();
      info.setField(agg.getColumnValue());
      info.setAggregateFormula(agg.getFormulaValue());
      info.setFullName(agg.getFullName());
      info.setCalculateInfo(CalculateInfo.createCalcInfo(agg.getCalculator()));
      return info;
   }

   private static DimensionFieldInfo baseDimensionFieldInfo(VSDimensionRef dim) {
      DimensionFieldInfo info = new DimensionFieldInfo();
      info.setField(dim.getGroupColumnValue());
      return info;
   }

   private static void applyRanking(DimensionFieldInfo info, VSDimensionRef dim) {
      String optValue = dim.getRankingOptionValue();

      if(optValue != null && !"0".equals(optValue)) {
         Ranking ranking = new Ranking();
         ranking.setOptionValue(Integer.parseInt(optValue));

         String nValue = dim.getRankingNValue();

         if(nValue != null) {
            ranking.setRankingN(Integer.parseInt(nValue));
         }

         ranking.setRankingCol(dim.getRankingColValue());
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

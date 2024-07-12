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
package inetsoft.web.vswizard.recommender;

import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.*;

/**
 * Utility methods for vs wizard's crosstab recommender.
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class CrosstabRecommenderUtil {
   public static VSAggregateRef createAggregateRef(AssetEntry entry) {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(WizardRecommenderUtil.getFieldName(entry));
      String dtype = entry.getProperty("dtype");
      agg.setOriginalDataType(dtype);
      int rtype = Integer.parseInt(entry.getProperty("refType"));
      agg.setRefType(rtype);

      if((rtype & AbstractDataRef.AGG_CALC) == 0) {
         String formula = "None";// to do

         if(formula != null) {
            agg.setFormula(AggregateFormula.getFormula(formula));
         }
         else {
            agg.setFormula(AggregateFormula.getDefaultFormula(dtype));
         }
      }

      return agg;
   }

   public static VSDimensionRef createDimensionRef(AssetEntry entry) {
      VSDimensionRef dim = new VSDimensionRef();
      dim.setDataType(entry.getProperty("dtype"));
      int refType = entry.getProperty("refType") == null ?
         AbstractDataRef.NONE : Integer.parseInt(entry.getProperty("refType"));
      dim.setRefType(refType);
      dim.setGroupColumnValue(WizardRecommenderUtil.getFieldName(entry));

      if(XSchema.isDateType(dim.getDataType())) {
         WizardRecommenderUtil.setDefaultDateLevel(dim, entry);
      }

      return dim;
   }

   public static VSDimensionRef createVSDimension(ChartRef ref, boolean brandNewColumn) {
      if(!(ref instanceof VSChartDimensionRef)) {
         return null;
      }

      VSChartDimensionRef odim = (VSChartDimensionRef) ref;
      VSDimensionRef dim = new VSDimensionRef();
      dim.setGroupColumnValue(odim.getGroupColumnValue());
      dim.setDataType(odim.getDataType());
      dim.setRefType(odim.getRefType());
      dim.setDateLevel(odim.getDateLevel());
      dim.setDateLevelValue(odim.getDateLevelValue());
      dim.setOrder(odim.getOrder());
      dim.setSortByColValue(odim.getSortByCol());
      dim.setRankingOptionValue(odim.getRankingOption() + "");
      dim.setRankingNValue(odim.getRankingN() + "");
      dim.setRankingColValue(odim.getRankingCol());
      dim.setGroupOthersValue(odim.getGroupOthersValue());
      dim.setCaption(odim.getCaption());
      dim.setTimeSeries(odim.isTimeSeries());
      dim.setNamedGroupInfo(odim.getNamedGroupInfo());
      dim.setGroupType(odim.getGroupType());
      dim.setRankingOptionValue(odim.getRankingOptionValue());
      dim.setRankingNValue(odim.getRankingNValue());
      dim.setRankingColValue(odim.getRankingColValue());

      if(brandNewColumn && XSchema.isDateType(dim.getDataType())) {
         dim.setTimeSeries(false);
      }

      return dim;
   }

   public static VSDimensionRef createVSDimension(ChartRef ref) {
      return createVSDimension(ref, false);
   }

   public static VSAggregateRef createVSAggregate(ChartRef ref) {
      if(!(ref instanceof VSChartAggregateRef)) {
         return null;
      }

      VSChartAggregateRef oagg = (VSChartAggregateRef) ref;
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(oagg.getColumnValue());
      agg.setRefType(oagg.getRefType());
      agg.setFormula(oagg.getFormula());
      agg.setNValue(oagg.getNValue());
      agg.setCaption(oagg.getCaption());
      agg.setSecondaryColumnValue(oagg.getSecondaryColumnValue());
      agg.setDataRef(oagg.getDataRef());

      return agg;
   }
}

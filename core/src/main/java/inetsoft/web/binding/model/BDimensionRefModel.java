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
package inetsoft.web.binding.model;

import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.binding.VSDataController;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.graph.CalculateInfo;
import inetsoft.web.binding.service.DataRefModelWrapperFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Matcher;

/**
 * A BDimensionRefModel object represents a dimension reference.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class BDimensionRefModel extends AbstractBDRefModel {
   /**
    * Create a default BDimensionRefModel.
    */
   public BDimensionRefModel() {
   }

   /**
    * Create a BDimensionRefModel according to dimension ref.
    */
   public BDimensionRefModel(XDimensionRef ref) {
      super(ref);

      this.timeseries = ref.isTimeSeries();
      this.order = ref.getOrder();
      XNamedGroupInfo namedGroupInfo = null;

      if(ref instanceof VSDimensionRef) {
         VSDimensionRef dim = (VSDimensionRef) ref;
         namedGroupInfo = ref.getNamedGroupInfo();
         Matcher matcher = VSUtil.matchVariableFormula(dim.getSortByColValue());

         if(matcher != null && matcher.find()) {
            this.sortByCol = dim.getSortByCol();
         }
         else {
            this.sortByCol = dim.getSortByColValue();
         }

         matcher = VSUtil.matchVariableFormula(dim.getRankingColValue());


         if(matcher != null && matcher.find()) {
            this.rankingCol = dim.getRankingCol();
         }
         else {
            this.rankingCol = dim.getRankingColValue();
         }

         this.timeseries = dim.isTimeSeries();
         this.rankingOpt = "null".equals(dim.getRankingOptionValue()) ?
            null : dim.getRankingOptionValue();
         this.rankingN = dim.getRankingNValue();
         this.groupOthers = Boolean.parseBoolean(dim.getGroupOthersValue());
         this.dlevel = dim.getDateLevelValue();
         this.summarize = dim.getSubTotalVisibleValue();
         List orderList = dim.getManualOrderList();
         this.manualOrder = orderList == null ? null : (ArrayList) orderList;
         this.setComboType(dim.getComboType());

         // date default to year if not set
         if(this.dlevel == null && XSchema.isDateType(dim.getDataType())) {
            if(XSchema.TIME.equals(dim.getDataType())) {
               this.dlevel = DateRangeRef.HOUR_INTERVAL + "";
            }
            else {
               this.dlevel = DateRangeRef.YEAR_INTERVAL + "";
            }
         }
      }

      setDataType(ref.getDataType());

      this.ngInfoModel = new NamedGroupInfoModel(namedGroupInfo);
   }

   /**
    * Set order type.
    * @param order the order type.
    */
   public void setOrder(int order) {
      this.order = order;
   }

   /**
    * Get order type.
    * @return the order type.
    */
   public int getOrder() {
      return order;
   }

   /**
    * Set sort by col index.
    * @param sortByCol by col index.
    */
   public void setSortByCol(String sortByCol) {
      this.sortByCol = sortByCol;
   }

   /**
    * Get sort by col index.
    * @return sort by col index.
    */
   public String getSortByCol() {
      return sortByCol;
   }

   /**
    * Get ranking option.
    * @return ranking option.
    */
   public String getRankingOption() {
      return rankingOpt;
   }

   /**
    * Set ranking option.
    * @param opt ranking option.
    */
   public void setRankingOption(String opt) {
      this.rankingOpt = opt;
   }

   /**
    * Get ranking n value.
    * @return ranking n value.
    */
   public String getRankingN() {
      return rankingN;
   }

   /**
    * Set ranking n value.
    * @param n ranking n value.
    */
   public void setRankingN(String n) {
      this.rankingN = n;
   }

   /**
    * Get ranking col value.
    * @return ranking col value.
    */
   public String getRankingCol() {
      return rankingCol;
   }

   /**
    * Set ranking col.
    * @param col ranking col index.
    */
   public void setRankingCol(String col) {
      this.rankingCol = col;
   }

   /**
    * Get is group others or not.
    * @return true is group others, false otherwise.
    */
   public boolean isGroupOthers() {
      return groupOthers;
   }

   /**
    * Set group others.
    * @param others is group others.
    */
   public void setGroupOthers(boolean others) {
      this.groupOthers = others;
   }

   /**
    * Get is top group others or not.
    * @return true is topn group others, false otherwise.
    */
   public boolean isOthers() {
      return namedOthers;
   }

   /**
    * Set topn others
    * @param others is topn others.
    */
   public void setOthers(boolean others) {
      this.namedOthers = others;
   }

   /**
    * Get date level.
    * @return date level.
    */
   public String getDateLevel() {
      return dlevel;
   }

   /**
    * Set date level.
    * @param dlevel the date level.
    */
   public void setDateLevel(String dlevel) {
      this.dlevel = dlevel;
   }

   /**
    * Set date interval
    * @param interval the date interval.
    */
   public void setDateInterval(double interval) {
      this.interval = interval;
   }

   /**
    * Get is time series.
    * @return true if is time series.
    */
   public boolean isTimeSeries() {
      return timeseries;
   }

   /**
    * Set time series.
    * @param ts is time series.
    */
   public void setTimeSeries(boolean ts) {
      this.timeseries = ts;
   }

   /**
    * Set named group info.
    * @param ngInfoModel the named group info.
    */
   public void setNamedGroupInfo(NamedGroupInfoModel ngInfoModel) {
      this.ngInfoModel = ngInfoModel;
   }

   /**
    * Get named group info.
    * @return named group info.
    */
   public NamedGroupInfoModel getNamedGroupInfo() {
      return ngInfoModel;
   }

   public NamedGroupInfoModel getCustomNamedGroupInfo() {
      return customNamedGroupInfo;
   }

   public void setCustomNamedGroupInfo(NamedGroupInfoModel customNamedGroupInfo) {
      this.customNamedGroupInfo = customNamedGroupInfo;
   }

   /**
    * Set summarize value.
    */
   public void setSummarize(String summarize) {
      this.summarize = summarize;
   }

   /**
    * Set page break.
    * @param pageBreak page break value.
    */
   public void setPageBreak(boolean pageBreak) {
      this.pageBreak = pageBreak;
   }

   /**
    * Get is page break or not.
    * @return true if is page break.
    */
   public boolean getPageBreak() {
      return pageBreak;
   }

   /**
    * Get the group column value.
    * @return the group column value.
    */
   public String getColumnValue() {
      return columnValue;
   }

   /**
    * Set the group column value.
    * @param value the column value.
    */
   public void setColumnValue(String value) {
      this.columnValue = value;
   }

   /**
    * Get caption.
    * @param caption the caption of the dimension.
    */
   public void setCaption(String caption) {
      this.caption = caption;
   }

   /**
    * Set caption.
    * @return the caption of the dimension.
    */
   public String getCaption() {
      return caption;
   }

   /**
    * @return the manualOrder
    */
   public List getManualOrder() {
      return manualOrder;
   }

   /**
    * @param manualOrder the manualOrder to set
    */
   public void setManualOrder(ArrayList manualOrder) {
      this.manualOrder = manualOrder;
   }

   /**
    * @return the manualOrder
    */
   public SortOptionModel getSortOptionModel() {
      return sortOptionModel;
   }

   /**
    * @param sortOptionModel the sortOptionModel to set
    */
   public void setSortOptionModel(SortOptionModel sortOptionModel) {
      this.sortOptionModel = sortOptionModel;
   }

   /**
    * Set runtime id.
    */
   public void setRuntimeID(int rid) {
      this.runtimeID = rid;
   }

   /**
    * Get runtime id.
    */
   public int getRuntimeID() {
      return runtimeID;
   }

   @Override
   public DataRef createDataRef() {
      String calcAggregateFullName = getCalcAggregateFullName(getRankingAgg());

      if(!Tool.isEmptyString(calcAggregateFullName)) {
         rankingCol = calcAggregateFullName;
      }

      calcAggregateFullName = getCalcAggregateFullName(getSortByColAgg());

      if(!Tool.isEmptyString(calcAggregateFullName)) {
         sortByCol = calcAggregateFullName;
      }

      VSDimensionRef dim = new VSDimensionRef();
      dim.setOrder(this.getOrder());
      dim.setSortByColValue(this.getSortByCol());
      dim.setDateLevelValue(this.getDateLevel());
      dim.setRankingOptionValue(this.getRankingOption());
      dim.setRankingNValue(rankingN);
      dim.setRankingColValue(this.getRankingCol());
      dim.setGroupOthersValue(this.isGroupOthers() + "");
      dim.setGroupColumnValue(this.getColumnValue());
      dim.setCaption(this.getCaption());
      dim.setSubTotalVisibleValue(summarize + "");
      dim.setManualOrderList(VSDataController.fixNull(this.manualOrder));
      dim.setRuntimeID(this.runtimeID);
      dim.setComboType(this.getComboType());
      dim.setTimeSeries(this.isTimeSeries());
      dim.setDataType(getDataType());

      DataRefModel refMode = getDataRefModel();

      if(refMode != null) {
         dim.setDataRef(refMode.createDataRef());
      }

      if(this.getOrder() == XConstants.SORT_SPECIFIC && (ngInfoModel == null ||
         ngInfoModel.getGroups() == null || ngInfoModel.getGroups().isEmpty()) &&
         (this.getManualOrder() == null || this.getManualOrder().isEmpty()))
      {
         dim.setOrder(getOrder() & ~XConstants.SORT_SPECIFIC);
      }

      return dim;
   }

   private String getCalcAggregateFullName(CalcAggregate calcAggregate) {
      if(calcAggregate == null || Tool.isEmptyString(calcAggregate.getBaseAggregateName()) ||
         calcAggregate.getCalculateInfo() == null ||
         !calcAggregate.calculateInfo.isSupportSortByValue())
      {
         return null;
      }

      return calcAggregate.getCalculateInfo().getPrefix() + calcAggregate.getBaseAggregateName();
   }

   public CalcAggregate getRankingAgg() {
      return rankingAgg;
   }

   @Nullable
   public void setRankingAgg(CalcAggregate rankingAgg) {
      this.rankingAgg = rankingAgg;
   }

   public CalcAggregate getSortByColAgg() {
      return sortByColAgg;
   }

   @Nullable
   public void setSortByColAgg(CalcAggregate sortByColAgg) {
      this.sortByColAgg = sortByColAgg;
   }

   public String toString() {
      return "rankingCol=" + rankingCol + ", groupOthers=" + groupOthers;
   }

   public static class CalcAggregate {
      public String getBaseAggregateName() {
         return baseAggregateName;
      }

      public void setBaseAggregateName(String baseAggregateName) {
         this.baseAggregateName = baseAggregateName;
      }

      public CalculateInfo getCalculateInfo() {
         return calculateInfo;
      }

      public void setCalculateInfo(CalculateInfo calculateInfo) {
         this.calculateInfo = calculateInfo;
      }

      private String baseAggregateName;
      private CalculateInfo calculateInfo;
   }

   private int order = XConstants.SORT_ASC;
   private String sortByCol;
   private String rankingOpt = XCondition.NONE + "";
   private String rankingN;
   private String rankingCol;
   private boolean groupOthers = false; // group others in topN
   private String dlevel = "-1";
   private double interval;
   private boolean timeseries = false;
   private NamedGroupInfoModel ngInfoModel;
   private NamedGroupInfoModel customNamedGroupInfo;
   private boolean namedOthers; // group others in named group
   private String summarize = "true";
   private boolean pageBreak = false;
   private String columnValue;
   private String caption;
   private List manualOrder;
   private SortOptionModel sortOptionModel;//sort option ref
   private int runtimeID = -1;
   private CalcAggregate rankingAgg;
   private CalcAggregate sortByColAgg;

   @Component
   public static final class VSDimensionRefModelFactory
      extends DataRefModelWrapperFactory<VSDimensionRef, BDimensionRefModel>
   {
      @Override
      public Class<VSDimensionRef> getDataRefClass() {
         return VSDimensionRef.class;
      }

      @Override
      public BDimensionRefModel createDataRefModel0(VSDimensionRef ref) {
         BDimensionRefModel model =  new BDimensionRefModel(ref);
         model.setColumnValue(ref.getGroupColumnValue());
         model.setCaption(ref.getCaption());
         model.setRuntimeID(ref.getRuntimeID());
         model.setTimeSeries(ref.isTimeSeries());

         return model;
      }
   }

   @Component
   public static final class VSChartDimensionRefModelFactory
      extends DataRefModelWrapperFactory<VSChartDimensionRef, BDimensionRefModel>
   {
      @Override
      public Class<VSChartDimensionRef> getDataRefClass() {
         return VSChartDimensionRef.class;
      }

      @Override
      public BDimensionRefModel createDataRefModel0(VSChartDimensionRef ref) {
         BDimensionRefModel model = new BDimensionRefModel(ref);
         model.setColumnValue(ref.getGroupColumnValue());
         model.setCaption(ref.getCaption());
         model.setRuntimeID(ref.getRuntimeID());
         model.setTimeSeries(ref.isTimeSeries());

         return model;
      }
   }

}

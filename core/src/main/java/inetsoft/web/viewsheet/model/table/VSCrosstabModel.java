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
package inetsoft.web.viewsheet.model.table;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.filter.CrossTabFilterUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DCNamedGroupInfo;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.CalculateAggregate;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.handler.BaseDrillHandler;
import inetsoft.web.viewsheet.model.VSObjectModelFactory;
import org.springframework.stereotype.Component;

import java.util.*;

public class VSCrosstabModel extends BaseTableModel<CrosstabVSAssembly> {
   public VSCrosstabModel(CrosstabVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);

      CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) assembly.getVSAssemblyInfo();
      VSCrosstabInfo vinfo = cinfo.getVSCrosstabInfo();
      this.editedByWizard = cinfo.isEditedByWizard();

      if(cinfo != null) {
         setDateComparisonDescription(DateComparisonUtil.getDateComparisonDescription(cinfo));
         setAppliedDateComparison(vinfo.isAppliedDateComparison());
      }

      if(vinfo != null) {
         DataRef[] rheaders = vinfo.getRuntimeRowHeaders();
         DataRef[] cheaders = vinfo.getRuntimeColHeaders();
         DataRef[] aggregates = vinfo.getRuntimeAggregates();
         setSummarySideBySide(vinfo.isSummarySideBySide());

         for(int i = 0; i < rheaders.length; i++) {
            DataRef rheader = rheaders[i];

            if(rheader instanceof VSDimensionRef) {
               VSDimensionRef dim = (VSDimensionRef) rheader;
               rowNames.add(dim.isDate() && Tool.equals(DateRangeRef.NONE_INTERVAL + "", dim.getDateLevelValue())
                  ? dim.getDataRef().getName() : dim.getFullName());

               dataTypeMap.put(dim.getName(), dim.getDataType());
               sortTypeMap.put(dim.getFullName(), dim.getOrder());

               String colValue = dim.getSortByColValue();

               if(colValue != null) {
                  for(DataRef ref : aggregates) {
                     VSAggregateRef aggRef = (VSAggregateRef) ref;

                     if(colValue.equals(CrossTabFilterUtil.getCrosstabRTAggregateName(aggRef, true))) {
                        int aggOrder = XConstants.SORT_NONE;

                        if(dim.getOrder() == XConstants.SORT_VALUE_ASC) {
                           aggOrder = XConstants.SORT_ASC;
                        }
                        else if(dim.getOrder() == XConstants.SORT_VALUE_DESC) {
                           aggOrder = XConstants.SORT_DESC;
                        }

                        sortTypeMap.put(CrossTabFilterUtil.getCrosstabRTAggregateName(aggRef, true), aggOrder);
                        break;
                     }
                  }
               }

               if(dim.isDateRange() || dim.getDataRef() instanceof CalculateRef &&
                  ((CalculateRef) dim.getDataRef()).isDcRuntime())
               {
                  dateRangeNames.add(dim.getFullName());

                  if(dim.getDateLevel() != 0 && dim.isTimeSeries()) {
                     timeSeriesNames.add(dim.getFullName());
                  }
               }

               if(dim.containsDynamic()) {
                  setHasDynamic(true);
               }
            }
         }

         for(int i = 0; i < cheaders.length; i++) {
            DataRef cheader = cheaders[i];

            if(cheader instanceof VSDimensionRef) {
               VSDimensionRef dim = (VSDimensionRef) cheader;
               colNames.add(dim.getFullName());
               dataTypeMap.put(dim.getName(), dim.getDataType());
               sortTypeMap.put(dim.getFullName(), dim.getOrder());

               String colValue = dim.getSortByColValue();

               if(colValue != null) {
                  for(DataRef ref : aggregates) {
                     VSAggregateRef aggRef = (VSAggregateRef) ref;
                     String aggName = CrossTabFilterUtil.getCrosstabRTAggregateName(aggRef, true);

                     if(colValue.equals(aggName)) {
                        int aggOrder = XConstants.SORT_NONE;

                        if(dim.getOrder() == XConstants.SORT_VALUE_ASC) {
                           aggOrder = XConstants.SORT_ASC;
                        }
                        else if(dim.getOrder() == XConstants.SORT_VALUE_DESC) {
                           aggOrder = XConstants.SORT_DESC;
                        }

                        if(sortTypeMap.get(aggName) == null) {
                           sortTypeMap.put(aggName, aggOrder);
                        }

                        break;
                     }
                  }
               }

               if(dim.isDateRange() || dim.getDataRef() instanceof CalculateRef &&
                  ((CalculateRef) dim.getDataRef()).isDcRuntime())
               {
                  dateRangeNames.add(dim.getFullName());

                  if(dim.isTimeSeries()) {
                     timeSeriesNames.add(dim.getFullName());
                  }
               }

               if(dim.containsDynamic()) {
                  setHasDynamic(true);
               }
            }
         }

         String aggrName = null;

         for(DataRef ref : aggregates) {
            if(((VSAggregateRef) ref).containsDynamic()) {
               setHasDynamic(true);
            }

            if(VSUtil.isFake(ref)) {
               containsFakeAggregate = true;
               break;
            }

            aggrNames.add(CrossTabFilterUtil.getCrosstabRTAggregateName((CalculateAggregate) ref, true));
         }

         DataRef[] refs = vinfo.getRuntimeDateComparisonRefs();

         if(refs != null) {
            dcMergedColumn = Arrays.stream(refs).filter(ref -> ref instanceof VSDimensionRef &&
               ((VSDimensionRef) ref).getNamedGroupInfo() instanceof DCNamedGroupInfo)
               .map(ref -> ((VSDimensionRef) ref).getFullName())
               .findFirst().orElse(null);
         }
      }

      setDateComparisonDefined(DateComparisonUtil.isDateComparisonDefined(cinfo));
      setDateComparisonEnabled(cinfo.isDateComparisonEnabled());
      DateComparisonInfo dcInfo = cinfo.getDateComparisonInfo();
      setCustomPeriod(dcInfo != null && dcInfo.getPeriods() instanceof CustomPeriods);
      setHasHiddenColumn(assembly.getCrosstabInfo().hasHiddenColumn());
      setFilterFields(BaseDrillHandler.getDrillFiltersFields(rvs.getViewsheet()));

      sortOnHeader = "true".equals(SreeEnv.getProperty("vs.crosstab.sortonheader"));
      sortAggregate = "true".equals(SreeEnv.getProperty("sort.crosstab.aggregate"));
      sortDimension = "true".equals(SreeEnv.getProperty("sort.crosstab.dimension"));
   }

   public List<String> getRowNames() {
      return Collections.unmodifiableList(rowNames);
   }

   public List<String> getColNames() {
      return Collections.unmodifiableList(colNames);
   }

   public List<String> getAggrNames() {
      return Collections.unmodifiableList(aggrNames);
   }

   public boolean isSortOnHeader() {
      return sortOnHeader;
   }

   public boolean isSortAggregate() {
      return sortAggregate;
   }

   public boolean isSortDimension() {
      return sortDimension;
   }

   public Map<String, Integer> getSortTypeMap() {
      return sortTypeMap;
   }

   public void setSortTypeMap(Map<String, Integer> sortTypeMap) {
      this.sortTypeMap = sortTypeMap;
   }

   public boolean isContainsFakeAggregate() {
      return containsFakeAggregate;
   }

   public boolean isSummarySideBySide() {
      return summarySideBySide;
   }

   public void setSummarySideBySide(boolean summarySideBySide) {
      this.summarySideBySide = summarySideBySide;
   }

   public List<String> getDateRangeNames() {
      return dateRangeNames;
   }

   public List<String> getTimeSeriesNames() {
      return timeSeriesNames;
   }

    public Map<String, String> getDataTypeMap() {
      return dataTypeMap;
   }

   public boolean isEditedByWizard() {
      return editedByWizard;
   }

   public void setEditedByWizard(boolean editedByWizard) {
      this.editedByWizard = editedByWizard;
   }

   public boolean isHasHiddenColumn() {
      return hasHiddenColumn;
   }

   public void setHasHiddenColumn(boolean hasHiddenColumn) {
      this.hasHiddenColumn = hasHiddenColumn;
   }

   public List<String> getFilterFields() {
      return filterFields;
   }

   public void setFilterFields(List<String> filterFields) {
      this.filterFields = filterFields;
   }

   public boolean isDateComparisonEnabled() {
      return dateComparisonEnabled;
   }

   public void setDateComparisonEnabled(boolean dateComparisonEnabled) {
      this.dateComparisonEnabled = dateComparisonEnabled;
   }

   public boolean isDateComparisonDefined() {
      return dateComparisonDefined;
   }

   public void setDateComparisonDefined(boolean dateComparisonDefined) {
      this.dateComparisonDefined = dateComparisonDefined;
   }

   public boolean isCustomPeriod() {
      return customPeriod;
   }

   public void setCustomPeriod(boolean customPeriod) {
      this.customPeriod = customPeriod;
   }

   public String getDateComparisonDescription() {
      return dateComparisonDescription;
   }

   public void setDateComparisonDescription(String dateComparisonDescription) {
      this.dateComparisonDescription = dateComparisonDescription;
   }

   public boolean isAppliedDateComparison() {
      return appliedDateComparison;
   }

   public void setAppliedDateComparison(boolean appliedDateComparison) {
      this.appliedDateComparison = appliedDateComparison;
   }

   public String getDcMergedColumn() {
      return dcMergedColumn;
   }

   public void setDcMergedColumn(String dcMergedColumn) {
      this.dcMergedColumn = dcMergedColumn;
   }

   private final List<String> rowNames = new ArrayList<>();
   private final List<String> colNames = new ArrayList<>();
   private final List<String> aggrNames = new ArrayList<>();
   private final List<String> timeSeriesNames = new ArrayList<>();
   private Map<String, Integer> sortTypeMap = new HashMap<>();
   private Map<String, String> dataTypeMap = new HashMap<>();
   private boolean sortOnHeader;
   private boolean sortAggregate;
   private boolean sortDimension;
   private boolean containsFakeAggregate;
   private boolean summarySideBySide;
   private List<String> dateRangeNames = new ArrayList<>();
   private boolean editedByWizard = true; //Not edited by binding
   private List<String> filterFields = new ArrayList<>();
   private String dcMergedColumn;

   private boolean hasHiddenColumn = false;
   private boolean customPeriod;
   private boolean dateComparisonEnabled;
   private boolean dateComparisonDefined;
   private boolean appliedDateComparison;
   private String dateComparisonDescription;

   @Component
   public static final class VSCrosstabModelFactory
      extends VSObjectModelFactory<CrosstabVSAssembly, VSCrosstabModel>
   {
      public VSCrosstabModelFactory() {
         super(CrosstabVSAssembly.class);
      }

      @Override
      public VSCrosstabModel createModel(CrosstabVSAssembly assembly, RuntimeViewsheet rvs) {
         try {
            return new VSCrosstabModel(assembly, rvs);
         }
         catch(RuntimeException e) {
            throw e;
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to get runtime viewsheet instance", e);
         }
      }
   }
}

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
package inetsoft.web.vswizard.recommender.object;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.VSWizardData;
import inetsoft.web.vswizard.model.recommender.VSCrosstabRecommendation;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class VSCrosstabDefaultRecommendationFactory implements VSCrosstabRecommendationFactory {
   @Autowired
   public VSCrosstabDefaultRecommendationFactory(ViewsheetService viewsheetService,
                                                 VSWizardBindingHandler bindingHandler)
   {
      this.viewsheetService = viewsheetService;
      this.bindingHandler = bindingHandler;
   }

   /**
    * Get the strategy name of the current recommendation factory.
    */
   @Override
   public String getStrategyName() {
      return STRATEGY_NAME;
   }

   /**
    * Return the VSRecommendObject for target selections.
    * @param wizardData
    * @return
    */
   @Override
   public VSCrosstabRecommendation recommend(VSWizardData wizardData, Principal principal) {
      List<AssetEntry> dimensions = WizardRecommenderUtil.
         getDimensionFields(wizardData.getSelectedEntries());
      VSChartInfo temp = null;

      try {
         temp = bindingHandler.getTempChart(wizardData);
      }
      catch(Exception ex) {
         LOG.error("Failed to get the temp chart", ex);
         return null;
      }

      VSCrosstabInfo crosstabInfo = new VSCrosstabInfo();
      updateCrosstabInfo(crosstabInfo, temp, dimensions,
                         wizardData.getVsTemporaryInfo().isAutoOrder(),
                         wizardData.getVsTemporaryInfo());
      VSCrosstabRecommendation crosstabRecommendation = new VSCrosstabRecommendation();
      crosstabRecommendation.setCrosstabInfo(crosstabInfo);

      return crosstabRecommendation;
   }

   /**
    * Put dimensions of crosstab as this:
    * If no hierarchy or there are two hieratchy, put no hierarchy col to row/col side by side.
    * If has one hierarchy, put hierarchy col on row, others to col.
    * Date col will be in no hierarchy. If split no hierarchy, split date as one hierarchy.
    */
   private void updateCrosstabInfo(VSCrosstabInfo crosstabInfo, VSChartInfo temp,
                                   List<AssetEntry> dimensions, boolean autoOrder,
                                   VSTemporaryInfo temporaryInfo)
   {
      List<List<AssetEntry>> sortedHierarchy = new ArrayList<>();

      if(autoOrder) {
         sortedHierarchy = WizardRecommenderUtil.getSortedHierarchyLists(
            dimensions.toArray(new AssetEntry[0]));
      }

      fixGroups(crosstabInfo, temp, sortedHierarchy, temporaryInfo, autoOrder, dimensions);
      fixAggregates(crosstabInfo, temp);
      crosstabInfo.setAggregateInfo(temp.getAggregateInfo());
   }

   private void fixGroups(VSCrosstabInfo crosstabInfo, VSChartInfo temp,
                          List<List<AssetEntry>> hierarchy, VSTemporaryInfo temporaryInfo,
                          boolean autoOrder, List<AssetEntry> dimensions)
   {
      ArrayList<VSDimensionRef> rows = new ArrayList<>();
      ArrayList<VSDimensionRef> cols = new ArrayList<>();
      List<ChartRef> groups = Arrays.asList(temp.getXFields());
      addHierarchyCol(rows, cols, groups, hierarchy, temporaryInfo);
      addNoHierarchyCol(rows, cols, groups, hierarchy, temporaryInfo, autoOrder, dimensions);

      while(rows.remove(null)) {
      }

      while(cols.remove(null)) {
      }

      crosstabInfo.setDesignRowHeaders(rows.toArray(new DataRef[rows.size()]));
      crosstabInfo.setDesignColHeaders(cols.toArray(new DataRef[cols.size()]));
   }

   private void fixAggregates(VSCrosstabInfo crosstabInfo, VSChartInfo temp) {
      ArrayList<VSAggregateRef> aggregates = new ArrayList<>();
      List<ChartRef> aggs = Arrays.asList(temp.getYFields());

      for(int i = 0; i < aggs.size(); i++) {
         ChartRef ref = aggs.get(i);
         aggregates.add(CrosstabRecommenderUtil.createVSAggregate(ref));
      }

      crosstabInfo.setDesignAggregates(aggregates.toArray(new DataRef[aggregates.size()]));
   }

   /**
    * Add columns belonging to a hierarchy.
    * @param rows row headers to add to.
    * @param cols column headers to add to.
    * @param groups dimensions in user selection.
    * @param hierarchy hierarchies.
    */
   private void addHierarchyCol(List<VSDimensionRef> rows, List<VSDimensionRef> cols,
                                List<ChartRef> groups, List<List<AssetEntry>> hierarchy,
                                VSTemporaryInfo tempInfo)
   {
      Set<AssetEntry> added = new HashSet<>();

      for(int i = 0; i < hierarchy.size(); i++) {
         List<AssetEntry> list = hierarchy.get(i);

         if(i % 2 == 0) {
            list.stream().forEach(entry -> rows.add(findGroup(entry, groups, tempInfo, added)));
         }
         else {
            list.stream().forEach(entry -> cols.add(findGroup(entry, groups, tempInfo, added)));
         }
      }
   }

   private void addNoHierarchyCol(List<VSDimensionRef> rows, List<VSDimensionRef> cols,
                                  List<ChartRef> groups, List<List<AssetEntry>> hierarchy,
                                  VSTemporaryInfo temporaryInfo, boolean autoOrder,
                                  List<AssetEntry> dimensions)
   {
      List<ChartRef> dates = autoOrder ? WizardRecommenderUtil.getDateDimensions(groups) : null;
      List<ChartRef> others = autoOrder ? getOtherDimensions(groups, hierarchy, dimensions) :groups;
      boolean hasDate = dates != null && dates.size() > 0;
      boolean hasRowHierarchy = hierarchy.size() == 1;

      if(hasRowHierarchy) {
         dates.stream().forEach(
            date -> cols.add(CrosstabRecommenderUtil.createVSDimension(date,
               temporaryInfo.isBrandNewColumn(date.getFullName()))));
         others.stream().forEach(
            noDate -> cols.add(CrosstabRecommenderUtil.createVSDimension(noDate,
               temporaryInfo.isBrandNewColumn(noDate.getFullName()))));
      }
      // autoOrder puts dates on row headers
      else if(hasDate) {
         dates.stream().forEach(
            date -> rows.add(CrosstabRecommenderUtil.createVSDimension(date,
               temporaryInfo.isBrandNewColumn(date.getFullName()))));
         others.stream().forEach(
            noDate -> cols.add(CrosstabRecommenderUtil.createVSDimension(noDate,
               temporaryInfo.isBrandNewColumn(noDate.getFullName()))));
      }
      else {
         Map<ChartRef, List<ChartRef>> sameMerged = mergeSameDimension(others);
         int index = 0;

         for(Map.Entry<ChartRef, List<ChartRef>> entry : sameMerged.entrySet()) {
            for(ChartRef ref : entry.getValue()) {
               if(index % 2 == 0) {
                  rows.add(CrosstabRecommenderUtil.createVSDimension(ref));
               }
               else {
                  cols.add(CrosstabRecommenderUtil.createVSDimension(ref));
               }
            }

            index++;
         }
      }
   }

   private Map<ChartRef, List<ChartRef>> mergeSameDimension(List<ChartRef> refs) {
      Map<ChartRef, List<ChartRef>> map = new LinkedHashMap<>();

      refs.forEach(ref -> {
         List<ChartRef> sameRefList = map.get(ref);

         if(sameRefList == null) {
            sameRefList = new ArrayList<>();
            sameRefList.add(ref);
            map.put(ref, sameRefList);
         }
         else {
            sameRefList.add(ref);
         }
      });

      return map;
   }

   /**
    * Find the dimension in groups that matches entry.
    */
   private VSDimensionRef findGroup(AssetEntry entry, List<ChartRef> groups,
                                    VSTemporaryInfo temporaryInfo, Set<AssetEntry> added)
   {
      if(added.contains(entry)) {
         return null;
      }

      for(int i = 0; i < groups.size(); i++) {
         if(!(groups.get(i) instanceof VSChartDimensionRef)) {
            continue;
         }

         String groupName = WizardRecommenderUtil.getRefName(groups.get(i));
         String entryName = WizardRecommenderUtil.getFieldName(entry);
         boolean brandNewColumn = temporaryInfo.isBrandNewColumn(groupName);

         if(Tool.equals(groupName, entryName)) {
            added.add(entry);
            return CrosstabRecommenderUtil.createVSDimension(groups.get(i), brandNewColumn);
         }
      }

      return null;
   }

   // Get fields that don't belong to a hierarchy.
   private static List<ChartRef> getOtherDimensions(List<ChartRef> dims,
                                                    List<List<AssetEntry>> hierarchy,
                                                    List<AssetEntry> dimensions)
   {
      AssetEntry[] entries = dimensions.toArray(new AssetEntry[0]);
      List<ChartRef> refs = dims.stream()
         .filter(dim -> !XSchema.isDateType(dim.getDataType()) && !findInHierarchy(dim, hierarchy))
         .collect(Collectors.toList());
      // sort dimensions by cardinality so higher cardinality dimension sre added to
      // rows on crosstab.
      Collections.sort(refs, (a, b) -> {
         return ChartRecommenderUtil.getCardinality(a, entries) -
            ChartRecommenderUtil.getCardinality(b, entries);
      });
      return refs;
   }

   private static boolean findInHierarchy(ChartRef dim, List<List<AssetEntry>> hierarchy) {
      if(hierarchy == null) {
         return false;
      }

      String groupName = WizardRecommenderUtil.getRefName(dim);

      for(int i = 0; i < hierarchy.size(); i++) {
         List<AssetEntry> list = hierarchy.get(i);

         for(int j = 0; j < list.size(); j++) {
            String entryName = WizardRecommenderUtil.getFieldName(list.get(j));

            if(Tool.equals(groupName, entryName)) {
               return true;
            }
         }
      }

      return false;
   }

   public static final String STRATEGY_NAME = "default_crosstab_recommender";
   private final ViewsheetService viewsheetService;
   private final VSWizardBindingHandler bindingHandler;
   private static final Logger LOG =
      LoggerFactory.getLogger(VSCrosstabDefaultRecommendationFactory.class);
}

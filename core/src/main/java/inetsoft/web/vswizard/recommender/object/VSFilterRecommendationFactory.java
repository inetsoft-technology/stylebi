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

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.vswizard.model.VSWizardData;
import inetsoft.web.vswizard.model.recommender.*;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;

@Component
public class VSFilterRecommendationFactory
   implements VSObjectRecommendationFactory<VSFilterRecommendation>
{
   public VSFilterRecommendationFactory() {
   }

   @Override
   public VSFilterRecommendation recommend(VSWizardData wizardData, Principal principal) {
      VSFilterRecommendation filterRecommendation = new VSFilterRecommendation();
      List<VSSubType> subTypes = getFilterSubTypes(wizardData.getSelectedEntries());
      filterRecommendation.setSubTypes(subTypes);
      filterRecommendation.setDataRefs(createDataRefs(wizardData));
      filterRecommendation.setSelectedIndex(subTypes.size() > 0 ? 0 : -1);

      return filterRecommendation;
   }

   private List<VSSubType> getFilterSubTypes(AssetEntry[] entries) {
      if(entries == null || entries.length < 1) {
         return new ArrayList<>();
      }

      if(entries.length > 1) {
         return TREE_RECOMMENDATION;
      }

      AssetEntry entry = entries[0];

      if(WizardRecommenderUtil.isNumericType(entry)) {
         return NUMERIC_RECOMMENDATION;
      }
      else if(WizardRecommenderUtil.isBooleanType(entry)) {
         return BOOLEAN_RECOMMENDATION;
      }
      else if(WizardRecommenderUtil.isDateType(entry)
         && !WizardRecommenderUtil.isTimeType(entry))
      {
         return DATE_RECOMMENDATION;
      }
      else if(WizardRecommenderUtil.isTimeType(entry)) {
         return TIME_RECOMMENDATION;
      }
      else {
         return STRING_RECOMMENDATION;
      }
   }

   private DataRef[] createDataRefs(VSWizardData wizardData) {
      ChartVSAssembly temp = wizardData.getVsTemporaryInfo().getTempChart();

      if(temp == null) {
         return null;
      }

      VSChartInfo tempInfo = temp.getVSChartInfo();
      AssetEntry[] entries = wizardData.getSelectedEntries();
      ChartRef[] fields =
         (ChartRef[]) ArrayUtils.addAll(tempInfo.getXFields(), tempInfo.getYFields());

      if(entries == null || entries.length < 1) {
         return null;
      }

      List<DataRef> refs = new ArrayList<>();
      Arrays.asList(fields).forEach(field ->
         refs.add(WizardRecommenderUtil.createColumnRef(getField(field, entries))));
      return refs.toArray(new DataRef[0]);
   }

   private static AssetEntry getField(DataRef field, AssetEntry[] entries) {
      for(AssetEntry entry : entries) {
         String entryName = WizardRecommenderUtil.getFieldName(entry);
         String vsName = null;

         if(field instanceof VSAggregateRef) {
            vsName = ((VSAggregateRef) field).getVSName();
         }

         if(entryName != null && (entryName.equals(field.getName()) || entryName.equals(vsName))) {
            return entry;
         }
      }

      return null;
   }

   private static final List<VSSubType> NUMERIC_RECOMMENDATION = Arrays.asList(
      new VSSubType(VSFilterType.RANGE_SLIDER + ""),
      new VSSubType(VSFilterType.SELECTION_LIST + ""));
   private static final List<VSSubType> DATE_RECOMMENDATION = Arrays.asList(
      new VSSubType(VSFilterType.CALENDAR + ""),
      new VSSubType(VSFilterType.RANGE_SLIDER + ""),
      new VSSubType(VSFilterType.SELECTION_LIST + ""));
   private static final List<VSSubType> TIME_RECOMMENDATION = Arrays.asList(
      new VSSubType(VSFilterType.RANGE_SLIDER + ""),
      new VSSubType(VSFilterType.SELECTION_LIST + ""));
   private static final List<VSSubType> STRING_RECOMMENDATION = Arrays.asList(
      new VSSubType(VSFilterType.SELECTION_LIST + ""));
   private static final List<VSSubType> TREE_RECOMMENDATION = Arrays.asList(
      new VSSubType(VSFilterType.SELECTION_TREE + ""));
   private static final List<VSSubType> BOOLEAN_RECOMMENDATION = Arrays.asList(
      new VSSubType(VSFilterType.SELECTION_LIST + ""));
}

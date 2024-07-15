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
package inetsoft.web.vswizard.recommender.execution;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.SingletonManager;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.recommender.execution.data.HierarchyData;
import inetsoft.web.vswizard.recommender.execution.data.WizardData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 13.2
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(HierarchyExecutor.Reference.class)
public class HierarchyExecutor extends WizardDataExecutor {
   /**
    * @param  box       the viewsheet sandbox.
    * @param  parent    the assumptive parent entry.
    * @param  child     the assumptive child entry.
    * @param  tname     the binding source.
    */
   public static HierarchyData getData(VSTemporaryInfo tempInfo, ViewsheetSandbox box,
                                       AssetEntry parent, AssetEntry child, String tname)
      throws Exception
   {
      String cacheKey = getCacheKey(box, tname);
      String fieldsKey = getFieldsKey(parent, child);
      HierarchyData data = getHierarchyData(cacheKey, fieldsKey);

      if(data != null) {
         return data;
      }

      if(!isCountableSqlType(parent) || !isCountableSqlType(child)) {
         return new HierarchyData(false);
      }

      data = calcData(tempInfo, box, parent, child);

      if(data != null) {
         addHierarchyData(cacheKey, fieldsKey, data);
      }

      return data;
   }

   /**
    * Return a key for cardiality data in WizardDataMap.
    */
   private static String getFieldsKey(AssetEntry parent, AssetEntry child) {
      if(parent == null || child == null) {
         return null;
      }

      return FILED_PREIX + "__" + WizardRecommenderUtil.getFieldName(parent) + ":" +
         WizardRecommenderUtil.getFieldName(child);
   }

   /**
    * Get HierarchyData from cache.
    * @param key the cache entry key.
    * @param fieldsKey target field to get HierarchyData cache.
    */
   private static HierarchyData getHierarchyData(String key, String fieldsKey) {
      WizardData data = getCacheData(key, fieldsKey);
      return data instanceof HierarchyData ? (HierarchyData) data : null;
   }

   /**
    * Add HierarchyData to cache.
    * @param key the cache entry key.
    * @param data the target HierarchyData to add into cache.
    */
   private static void addHierarchyData(String key, String fieldKey, HierarchyData data) {
      if(data == null) {
         return;
      }

      addCacheData(key, fieldKey, data);
   }

   /**
    * Get the data. If the data doesn't exist, execute the query and add to
    * cache.
    * @param  box        the viewsheet sandbox.
    * @param  parent    the assumptive parent entry.
    * @param  child     the assumptive child entry.
    */
   private static HierarchyData calcData(VSTemporaryInfo tempInfo, ViewsheetSandbox box,
                                         AssetEntry parent, AssetEntry child)
      throws Exception
   {
      if(box.getViewsheet().getViewsheetInfo().isMetadata()) {
         return null;
      }

      CrosstabVSAssembly crosstab = getTempCrosstab(box, tempInfo.getTempChart().getSourceInfo());
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      VSDimensionRef[] groups = { createDimensionRef(child) };
      cinfo.setDesignRowHeaders(groups);
      VSAggregateRef[] aggregates = { createAggregateRef(parent, AggregateFormula.COUNT_DISTINCT) };
      cinfo.setDesignAggregates(aggregates);
      VSTableLens base = box.getVSTableLens(crosstab.getAbsoluteName(), false);
      clearTempCrosstab(box, crosstab);

      if(base == null || getRowCount(base) <= 1) {
         return new HierarchyData(false);
      }

      try {
         return new HierarchyData(isHierarchy(base));
      }
      catch(NumberFormatException ex) {
         if(box.getViewsheet().getViewsheetInfo().isMetadata()) {
            return null;
         }

         LOG.debug("Failed to calculate hierarchy data", ex);
      }

      return null;
   }

   private static boolean isHierarchy(VSTableLens base) throws NumberFormatException {
      int rowCount = base.getRowCount();
      int zeroRowCount = 0;

      for(int r = 1; r < rowCount; r++) {
         int value = Integer.parseInt(base.getText(r, 1));

         // must is not a parent-child if child have more than one assumptive parents.
         if(value > 1) {
            return false;
         }

         if(value == 0) {
            zeroRowCount++;
         }
      }

      if(zeroRowCount > 0) {
         // is not a parent-child if more 10 percentage rows have 0 assumptive parent.
         return zeroRowCount * 100.0 / base.getRowCount() > 10;
      }

      return true;
   }

   public static final class Reference
      extends SingletonManager.Reference<HierarchyExecutor>
   {
      @Override
      public synchronized HierarchyExecutor get(Object ... parameters) {
         if(executor == null) {
            executor = new HierarchyExecutor();
         }

         return executor;
      }

      @Override
      public synchronized void dispose() {
         if(executor != null) {
            executor = null;
         }
      }

      private HierarchyExecutor executor;
   }

   private static final String FILED_PREIX = "Hierarchy";
   private static final Logger LOG = LoggerFactory.getLogger(HierarchyExecutor.class);
}

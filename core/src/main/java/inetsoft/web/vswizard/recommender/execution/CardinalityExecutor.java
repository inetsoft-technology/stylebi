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
package inetsoft.web.vswizard.recommender.execution;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.SingletonManager;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.recommender.execution.data.CardinalityData;
import inetsoft.web.vswizard.recommender.execution.data.WizardData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 13.2
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(CardinalityExecutor.Reference.class)
public class CardinalityExecutor extends WizardDataExecutor {
   /**
    * Return the CardinalityData for the target binding field.
    * @param  box       the viewsheet sandbox.
    * @param  entry     asset entry of the target field.
    * @param  tname     the binding source.
    */
   public static CardinalityData getData(ViewsheetSandbox box, VSTemporaryInfo temporaryInfo,
                                         AssetEntry entry, String tname)
      throws Exception
   {
      String cacheKey = getCacheKey(box, tname);
      String fieldName = WizardRecommenderUtil.getFieldName(entry);
      String fieldKey = getFieldKey(fieldName);
      WizardData data = getCacheData(cacheKey, fieldKey);

      if(data != null) {
         return (CardinalityData) data;
      }

      data = calcCurrentCardinality(box, temporaryInfo, entry);

      if(data != null) {
         addCardinalityData(cacheKey, fieldKey, (CardinalityData) data);
      }
      else {
         LOG.debug("Failed to get cardinality for field", new RuntimeException(fieldName));
      }

      return (CardinalityData) data;
   }

   /**
    * Return a key for cardiality data in WizardDataMap.
    */
   private static String getFieldKey(String fieldName) {
      return FILED_PREIX + "__" + fieldName;
   }

   /**
    * Add CardinalityData to cache.
    * @param key the cache entry key.
    * @param data the target CardinalityData to add into cache.
    */
   private static void addCardinalityData(String key, String fieldKey, CardinalityData data) {
      if(data == null) {
         return;
      }

      addCacheData(key, fieldKey, data);
   }

   private static CardinalityData calcCurrentCardinality(ViewsheetSandbox box,
                                                         VSTemporaryInfo tempInfo,
                                                         AssetEntry entry)
      throws Exception
   {
      if(box.getViewsheet().getViewsheetInfo().isMetadata()) {
         return null;
      }

      String fieldName = WizardRecommenderUtil.getFieldName(entry);

      if(!isCountableSqlType(entry)) {
         return new CardinalityData(fieldName, Integer.MAX_VALUE, Integer.MAX_VALUE);
      }

      CrosstabVSAssembly crosstab = getTempCrosstab(box, tempInfo.getTempChart().getSourceInfo());
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      VSAggregateRef aggr0 = createAggregateRef(entry, AggregateFormula.COUNT_ALL);
      VSAggregateRef aggr1 = (VSAggregateRef) aggr0.clone();
      aggr1.setFormula(AggregateFormula.COUNT_DISTINCT);
      VSAggregateRef[] aggregates = { aggr0, aggr1 };
      cinfo.setDesignAggregates(aggregates);
      VSTableLens lens = box.getVSTableLens(crosstab.getAbsoluteName(), false);
      clearTempCrosstab(box, crosstab);
      int rowCount = getRowCount(lens);

      if(rowCount > 1) {
         int count = lens.getInt(1, 0);
         int distinctCount = lens.getInt(1, 1);

         if(box.getViewsheet().getViewsheetInfo().isMetadata() && (count < 0 || distinctCount < 0))
         {
            return null;
         }

         return CardinalityData.create(fieldName, count, distinctCount);
      }

      return null;
   }

   public static final class Reference
      extends SingletonManager.Reference<CardinalityExecutor>
   {
      @Override
      public synchronized CardinalityExecutor get(Object ... parameters) {
         if(executor == null) {
            executor = new CardinalityExecutor();
         }

         return executor;
      }

      @Override
      public synchronized void dispose() {
         if(executor != null) {
            executor = null;
         }
      }

      private CardinalityExecutor executor;
   }

   private static final String FILED_PREIX = "Cardinality";
   private static final Logger LOG = LoggerFactory.getLogger(CardinalityExecutor.class);
}

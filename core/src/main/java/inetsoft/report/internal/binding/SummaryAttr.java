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
package inetsoft.report.internal.binding;

import inetsoft.report.*;
import inetsoft.report.filter.SortOrder;
import inetsoft.report.internal.RuntimeAssetEngine;
import inetsoft.report.internal.Util;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

/**
 * SummaryAttr holds the common attributes of grouping and summarization.
 *
 * @version 6.0 9/30/2003
 * @author mikec
 */
public abstract class SummaryAttr {
   public static final String NONE_FORMULA = "none";
   public static final String AVERAGE_FORMULA = "Average";
   public static final String COUNT_FORMULA = "Count";
   public static final String DISTINCTCOUNT_FORMULA = "DistinctCount";
   public static final String MAX_FORMULA = "Max";
   public static final String MIN_FORMULA = "Min";
   public static final String FIRST_FORMULA = "First";
   public static final String LAST_FORMULA = "Last";
   public static final String PRODUCT_FORMULA = "Product";
   public static final String SUM_FORMULA = "Sum";
   public static final String CONCAT_FORMULA = "Concat";
   public static final String STANDARDDEVIATION_FORMULA = "StandardDeviation";
   public static final String VARIANCE_FORMULA = "Variance";
   public static final String POPULATIONSTANDARDDEVIATION_FORMULA
      = "PopulationStandardDeviation";
   public static final String POPULATIONVARIANCE_FORMULA
      = "PopulationVariance";
   public static final String CORRELATION_FORMULA = "Correlation";
   public static final String COVARIANCE_FORMULA = "Covariance";
   public static final String MEDIAN_FORMULA = "Median";
   public static final String MODE_FORMULA = "Mode";
   public static final String NTHLARGEST_FORMULA = "NthLargest";
   public static final String NTHMOSTFREQUENT_FORMULA = "NthMostFrequent";
   public static final String NTHSMALLEST_FORMULA = "NthSmallest";
   public static final String PTHPERCENTILE_FORMULA = "PthPercentile";
   public static final String WEIGHTEDAVERAGE_FORMULA = "WeightedAverage";
   public static final int SORT_ASC = StyleConstants.SORT_ASC;
   public static final int SORT_DESC = StyleConstants.SORT_DESC;
   public static final int SORT_VALUE_ASC = StyleConstants.SORT_VALUE_ASC;
   public static final int SORT_VALUE_DESC = StyleConstants.SORT_VALUE_DESC;
   public static final int SORT_NONE = StyleConstants.SORT_NONE;
   public static final int SORT_SPECIFIC = StyleConstants.SORT_SPECIFIC;
   public static final int SORT_ORIGINAL = StyleConstants.SORT_ORIGINAL;

   // constants listing different types of formulas for UI
   public static final String[] NUMBER_FORMULAS = { SUM_FORMULA,
      AVERAGE_FORMULA, MAX_FORMULA, MIN_FORMULA, COUNT_FORMULA,
      DISTINCTCOUNT_FORMULA, FIRST_FORMULA, LAST_FORMULA, PRODUCT_FORMULA,
      CONCAT_FORMULA, STANDARDDEVIATION_FORMULA, VARIANCE_FORMULA,
      POPULATIONSTANDARDDEVIATION_FORMULA, POPULATIONVARIANCE_FORMULA,
      CORRELATION_FORMULA, COVARIANCE_FORMULA, MEDIAN_FORMULA, MODE_FORMULA,
      NTHLARGEST_FORMULA, NTHMOSTFREQUENT_FORMULA, NTHSMALLEST_FORMULA,
      PTHPERCENTILE_FORMULA, WEIGHTEDAVERAGE_FORMULA };
   public static final String[] NUMBER_FORMULAS2 = { NONE_FORMULA, SUM_FORMULA,
      AVERAGE_FORMULA, MAX_FORMULA, MIN_FORMULA, COUNT_FORMULA,
      DISTINCTCOUNT_FORMULA, FIRST_FORMULA, LAST_FORMULA, PRODUCT_FORMULA,
      CONCAT_FORMULA, STANDARDDEVIATION_FORMULA, VARIANCE_FORMULA,
      POPULATIONSTANDARDDEVIATION_FORMULA, POPULATIONVARIANCE_FORMULA,
      CORRELATION_FORMULA, COVARIANCE_FORMULA, MEDIAN_FORMULA, MODE_FORMULA,
      NTHLARGEST_FORMULA, NTHMOSTFREQUENT_FORMULA, NTHSMALLEST_FORMULA,
      PTHPERCENTILE_FORMULA, WEIGHTEDAVERAGE_FORMULA };
   public static final String[] DATE_FORMULAS = { MAX_FORMULA, MIN_FORMULA,
      COUNT_FORMULA, DISTINCTCOUNT_FORMULA, // MEDIAN_FORMULA,
      FIRST_FORMULA, LAST_FORMULA,
      NTHLARGEST_FORMULA, NTHMOSTFREQUENT_FORMULA, NTHSMALLEST_FORMULA,
      PTHPERCENTILE_FORMULA };
   public static final String[] DATE_FORMULAS2 = { NONE_FORMULA, MAX_FORMULA,
      MIN_FORMULA, COUNT_FORMULA, DISTINCTCOUNT_FORMULA, // MEDIAN_FORMULA,
      FIRST_FORMULA, LAST_FORMULA,
      NTHLARGEST_FORMULA, NTHMOSTFREQUENT_FORMULA, NTHSMALLEST_FORMULA,
      PTHPERCENTILE_FORMULA };
   public static final String[] STRING_FORMULAS = { MAX_FORMULA, MIN_FORMULA,
      COUNT_FORMULA, DISTINCTCOUNT_FORMULA, FIRST_FORMULA, LAST_FORMULA,
      PRODUCT_FORMULA, CONCAT_FORMULA, NTHLARGEST_FORMULA,
      CORRELATION_FORMULA, COVARIANCE_FORMULA, MEDIAN_FORMULA, MODE_FORMULA,
      NTHMOSTFREQUENT_FORMULA, NTHSMALLEST_FORMULA, PTHPERCENTILE_FORMULA,
      WEIGHTEDAVERAGE_FORMULA };
   public static final String[] STRING_FORMULAS2 = { NONE_FORMULA, MAX_FORMULA,
      MIN_FORMULA, COUNT_FORMULA, DISTINCTCOUNT_FORMULA,
      FIRST_FORMULA, LAST_FORMULA, PRODUCT_FORMULA, CONCAT_FORMULA,
      CORRELATION_FORMULA, COVARIANCE_FORMULA, MEDIAN_FORMULA, MODE_FORMULA,
      NTHLARGEST_FORMULA, NTHMOSTFREQUENT_FORMULA, NTHSMALLEST_FORMULA,
      PTHPERCENTILE_FORMULA, WEIGHTEDAVERAGE_FORMULA };
   public static final String[] BOOL_FORMULAS = { COUNT_FORMULA,
      DISTINCTCOUNT_FORMULA, FIRST_FORMULA, LAST_FORMULA,
      NTHMOSTFREQUENT_FORMULA };
   public static final String[] BOOL_FORMULAS2 = { NONE_FORMULA, COUNT_FORMULA,
      DISTINCTCOUNT_FORMULA, FIRST_FORMULA, LAST_FORMULA,
      NTHMOSTFREQUENT_FORMULA };

   public static String[] getFormulas(String dataType) {
      switch(dataType) {
         case XSchema.DATE:
         case XSchema.TIME:
         case XSchema.TIME_INSTANT:
            return DATE_FORMULAS;
         case XSchema.CHAR:
         case XSchema.STRING:
         case XSchema.CHARACTER:
            return STRING_FORMULAS;
         case XSchema.BOOLEAN:
            return BOOL_FORMULAS;
         default:
            return NUMBER_FORMULAS;
      }
   }

   /**
    * Get asset named group infos.
    * @param fld the specified field.
    * @param rep the specified asset repository.
    * @param report the specified report sheet.
    */
   public static AssetNamedGroupInfo[] getAssetNamedGroupInfos(DataRef fld,
      AssetRepository rep, ReportSheet report)
   {
      AssetRepository engine = new RuntimeAssetEngine(rep, report);
      AssetNamedGroupInfo[] infos = new AssetNamedGroupInfo[0];

      try {
         infos = (AssetNamedGroupInfo[]) ncache.get(engine);
         List list = new ArrayList();

         for(int i = 0; i < infos.length; i++) {
            if(infos[i].matches(fld)) {
               list.add(infos[i]);
            }
         }

         infos = new AssetNamedGroupInfo[list.size()];
         list.toArray(infos);
      }
      catch(Exception ex) {
         LOG.error("Failed to get asset named groups", ex);
      }

      return infos;
   }

   // named group cache stores all the predefined named groups
   private static ResourceCache ncache = new ResourceCache(50) {
      @Override
      protected boolean checkTimeOut() {
         List list = new ArrayList(map.keySet());
         boolean changed = false;

         for(int i = 0; i < list.size(); i++) {
            RuntimeAssetEngine engine = (RuntimeAssetEngine) list.get(i);

            if(!engine.isAvailable()) {
               map.remove(engine);
               changed = true;
            }
         }

         changed = super.checkTimeOut() || changed;
         return changed;
      }

      @Override
      protected Object create(Object key) throws Exception {
         final RuntimeAssetEngine engine = (RuntimeAssetEngine) key;
         Principal user = ThreadContext.getContextPrincipal();
         ReportSheet report = engine.getReport();
         AssetEntry[] roots = {
            (report != null) ? AssetEntry.createReportRoot() : null,
            (user != null) ? AssetEntry.createUserRoot(user) : null,
            AssetEntry.createGlobalRoot()};
         List infos = new ArrayList();
         AssetChangeListener listener = event -> {
            if(event.getEntryType() == Worksheet.NAMED_GROUP_ASSET) {
               remove(engine);
            }
         };

         // @by billh, do not remove this line, otherwise the listener might
         // be gced for we are using weak reference to keep this listener
         engine.setAssetChangeListener(listener);
         engine.addAssetChangeListener(listener);

         for(int i = 0; i < roots.length; i++) {
            if(roots[i] == null) {
               continue;
            }

            AssetEntry[] arr;

            try {
               arr = AssetUtil.getEntries(engine, roots[i], user,
                  AssetEntry.Type.WORKSHEET, Worksheet.NAMED_GROUP_ASSET, true);
            }
            catch(Exception ex) {
               LOG.warn("Failed to get child entries of " +
                  roots[i] + " for user " + user, ex);
               arr = new AssetEntry[0];
            }

            for(int j = 0; j < arr.length; j++) {
               Worksheet worksheet = null;

               try {
                  worksheet = (Worksheet) engine.getSheet(arr[j], user, false,
                                                          AssetContent.ALL);
               }
               catch(Exception ex) {
                  LOG.warn("Failed to get worksheet " + arr[j] + " for user " + user,
                     ex);
                  continue;
               }

               Assembly assembly = worksheet.getPrimaryAssembly();

               if(!(assembly instanceof NamedGroupAssembly)) {
                  continue;
               }

               AssetNamedGroupInfo info = new AssetNamedGroupInfo(arr[j],
                  (NamedGroupAssembly) assembly);

               if(!infos.contains(info)) {
                  infos.add(info);
               }
            }
         }

         Collections.sort(infos);
         AssetNamedGroupInfo[] arr = new AssetNamedGroupInfo[infos.size()];
         infos.toArray(arr);

         return arr;
      }
   };

   private static final Logger LOG = LoggerFactory.getLogger(SummaryAttr.class);
}

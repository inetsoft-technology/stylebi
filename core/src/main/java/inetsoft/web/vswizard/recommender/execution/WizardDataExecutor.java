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


import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.MaxRowsTableLens;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.util.Tool;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.recommender.execution.data.WizardData;
import inetsoft.web.vswizard.recommender.execution.data.WizardDataMap;

import java.sql.Types;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @version 13.2
 * @author InetSoft Technology Corp
 */

public class WizardDataExecutor {
   public static VSAggregateRef createAggregateRef(AssetEntry entry, AggregateFormula formula) {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(WizardRecommenderUtil.getFieldName(entry));
      String dtype = entry.getProperty("dtype");
      agg.setOriginalDataType(dtype);
      int rtype = Integer.parseInt(entry.getProperty("refType"));
      agg.setRefType(rtype);
      agg.setFormula(formula);

      return agg;
   }

   public static VSDimensionRef createDimensionRef(AssetEntry entry) {
      String dtype = entry.getProperty("dtype");

      if(XSchema.isDateType(dtype)) {
         return null;
      }

      VSDimensionRef dim = new VSDimensionRef();
      dim.setDataType(dtype);
      int refType = entry.getProperty("refType") == null ?
         AbstractDataRef.NONE : Integer.parseInt(entry.getProperty("refType"));
      dim.setRefType(refType);
      dim.setGroupColumnValue(WizardRecommenderUtil.getFieldName(entry));

      return dim;
   }

   public static CrosstabVSAssembly getTempCrosstab(ViewsheetSandbox box, SourceInfo source) {
      Viewsheet vs = box.getViewsheet();
      String name = VSWizardConstants.TEMP_CROSSTAB_NAME + tempId.incrementAndGet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
      crosstab.getVSAssemblyInfo().setWizardTemporary(true);
      crosstab.getVSAssemblyInfo().setVisible(false);
      vs.addAssembly(crosstab, false, false);
      box.resetDataMap(crosstab.getAbsoluteName());

      if(source != null) {
         crosstab.setSourceInfo((SourceInfo) source.clone());
      }
      
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      cinfo.setDesignRowHeaders(new DataRef[0]);
      cinfo.setDesignColHeaders(new DataRef[0]);
      cinfo.setDesignAggregates(new DataRef[0]);

      return crosstab;
   }

   public static void clearTempCrosstab(ViewsheetSandbox box, CrosstabVSAssembly temp) {
      try {
         Viewsheet vs = box.getViewsheet();
         box.resetDataMap(temp.getAbsoluteName());
         vs.removeAssembly(temp);
      }
      catch(Exception ignore) {
         // ignored
      }
   }

   public static String getColumnName(DataRef ref) {
      String name = ref.getName();

      if(ref instanceof VSAggregateRef) {
         name = ((VSAggregateRef) ref).getColumnValue();
      }
      else if(ref instanceof VSDimensionRef) {
         name = ((VSDimensionRef) ref).getGroupColumnValue();
      }

      if(name != null && name.startsWith("Range@")) {
         name = name.substring("Range@".length());
      }

      return name;
   }

   /**
    * Load row data and return the real row count of the tablelens.
    */
   public static int getRowCount(TableLens lens) {
      if(lens == null) {
         return -1;
      }

      int row = lens.getHeaderRowCount();
      int max = -1;

      if(lens instanceof MaxRowsTableLens) {
         max = ((MaxRowsTableLens) lens).getMaxRows();
      }

      while(lens.moreRows(row)) {
         row = lens.getRowCount();

         if(row <= -1) {
            row = -row - 1;
         }

         if(max != -1 && row >= max) {
            break;
         }
      }

      return lens.getRowCount();
   }

   public static String getCacheKey(ViewsheetSandbox box, String tname) {
      return getCacheKey(box.getViewsheet(), tname);
   }

   public static String getCacheKey(Viewsheet vs, String tname) {
      AssetEntry entry = vs.getBaseEntry();
      String key = entry == null ? null : entry.getPath() + "\\" + tname;
      return Tool.normalizeFileName(key);
   }

   public static WizardData getCacheData(String key, String fieldKey) {
      WizardDataMap map = WizardDataCache.getCache(key);
      return map != null ? map.get(fieldKey) : null;
   }

   public static void addCacheData(String key, String fieldKey, WizardData data) {
      WizardDataMap map = WizardDataCache.getCache(key);
      map.put(fieldKey, data);
   }

   public static boolean isSupportedSqlType(AssetEntry entry) {
      if(entry == null) {
         return false;
      }

      try {
         int sqlType = Integer.parseInt(entry.getProperty("sqltype"));
         return isSupportedSqlType(sqlType);
      }
      catch(NumberFormatException ex) {
         // default support
         return true;
      }
   }

   public static boolean isCountableSqlType(AssetEntry entry) {
      if(entry == null) {
         return false;
      }

      try {
         int sqlType = Integer.parseInt(entry.getProperty("sqltype"));
         return isCountableSqlType(sqlType);
      }
      catch(NumberFormatException ex) {
         // default support
         return true;
      }
   }

   public static boolean isSupportedSqlType(VSTemporaryInfo tempInfo, DataRef ref) {
      DataRef column = getColumn(tempInfo, ref);

      if(column instanceof ColumnRef) {
         return isSupportedSqlType(((ColumnRef) column).getSqlType());
      }

      return true;
   }

   public static boolean isSupportedSqlType(AggregateInfo ainfo, DataRef ref) {
      DataRef column = getColumDataRef(ainfo, ref);

      if(column instanceof ColumnRef) {
         return isSupportedSqlType(((ColumnRef) column).getSqlType());
      }

      return true;
   }

   /**
    * Find the columnref from AggregateInfo by target ref, this should be safe than find in temp
    * chart info, because fields in chartinfo may have no sub dataref.
    */
   private static DataRef getColumn(VSTemporaryInfo tempInfo, DataRef ref) {
      ChartVSAssembly chart = tempInfo.getTempChart();
      VSChartInfo cinfo = chart.getVSChartInfo();
      AggregateInfo ainfo = cinfo.getAggregateInfo();

      return getColumDataRef(ainfo, ref);
   }

   private static DataRef getColumDataRef(AggregateInfo ainfo, DataRef ref) {
      String name = getColumnName(ref);

      if(ainfo == null) {
         return null;
      }

      GroupRef group = ainfo.getGroup(name);

      if(group != null) {
         return group.getDataRef();
      }

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aggr = ainfo.getAggregate(i);

         if(aggr.getDataRef() != null && Tool.equals(aggr.getDataRef().getName(), name)) {
            return ref;
         }
      }

      return null;
   }

   private static boolean isCountableSqlType(int sqltype) {
      switch(sqltype) {
         case Types.BIT:
         case Types.TINYINT:
         case Types.SMALLINT:
         case Types.INTEGER:
         case Types.BIGINT:
         case Types.FLOAT:
         case Types.DOUBLE:
         case Types.REAL:
         case Types.NUMERIC:
         case Types.DECIMAL:
         case Types.CHAR:
         case Types.VARCHAR:
         case Types.NVARCHAR:
            return true;
         default:
            return false;
      }
   }

   /**
    *
    * @param  sqltype the field sqltype.
    * @return         true if supported to calcualte cardinality or hierarchy in wizard, else false.
    */
   private static boolean isSupportedSqlType(int sqltype) {
      if(isCountableSqlType(sqltype)) {
         return true;
      }

      switch(sqltype) {
         case Types.DATE:
         case Types.TIME:
         case Types.TIMESTAMP:
            return true;
         default:
            return false;
      }
   }

   public static final String CACHE_ID_PREFIX = "wizard";
   private static final AtomicLong tempId = new AtomicLong(0);
}

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

import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.util.SingletonManager;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.recommender.execution.data.IntervalData;
import inetsoft.web.vswizard.recommender.execution.data.WizardData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * @version 13.2
 * @author InetSoft Technology CorpF
 */
@SingletonManager.Singleton(IntervalExecutor.Reference.class)
public class IntervalExecutor extends WizardDataExecutor {
   public static IntervalData getData(ViewsheetSandbox box, VSTemporaryInfo tempInfo,
                                      AssetEntry entry, String tname, String type)
      throws Exception
   {
      return getData(box, tempInfo.getTempChart().getSourceInfo(), entry, tname, type);
   }

   public static IntervalData getData(ViewsheetSandbox box, SourceInfo source,
                                      AssetEntry entry, String tname, String type)
      throws Exception
   {
      String cacheKey = getCacheKey(box, tname);
      String fieldName = WizardRecommenderUtil.getFieldName(entry);
      String fieldKey = getFieldKey(fieldName);
      IntervalData data = getIntervalData(cacheKey, fieldKey);

      if(data != null) {
         return data;
      }

      if(!isSupportedSqlType(entry)) {
         return new IntervalData(-1, 0, 0);
      }

      CrosstabVSAssembly crosstab = getTempCrosstab(box, source);
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      VSAggregateRef aggr0 = createAggregateRef(entry, AggregateFormula.MIN);
      VSAggregateRef aggr1 = (VSAggregateRef) aggr0.clone();
      aggr1.setFormula(AggregateFormula.MAX);
      VSAggregateRef[] aggregates = {aggr0, aggr1};
      cinfo.setDesignAggregates(aggregates);
      return calcData(box, crosstab, type, cacheKey, fieldKey);
   }

   public static IntervalData getData(ViewsheetSandbox box, VSTemporaryInfo tempInfo,
                                      DataRef ref, String tname, String type)
      throws Exception
   {
      ChartVSAssembly chart = tempInfo.getTempChart();
      VSChartInfo cinfo = chart.getVSChartInfo();
      AggregateInfo ainfo = cinfo.getAggregateInfo();

      return getData(box, ainfo, chart.getSourceInfo(), ref, tname, type);
   }

   public static IntervalData getData(ViewsheetSandbox box, AggregateInfo ainfo,
                                      SourceInfo source, DataRef ref, String tname, String type)
      throws Exception
   {
      if(!isSupportedSqlType(ainfo, ref)) {
         return new IntervalData(-1, 0, 0);
      }

      String cacheKey = getCacheKey(box, tname);
      String fieldKey = getFieldKey(getColumnName(ref));

      if(cacheKey == null) {
         return null;
      }

      IntervalData data = getIntervalData(cacheKey, fieldKey);

      if(data != null) {
         return data;
      }

      CrosstabVSAssembly crosstab = getTempCrosstab(box, source);
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      VSAggregateRef aggr0 = createAggregateRef(ref, false);
      VSAggregateRef aggr1 = (VSAggregateRef) aggr0.clone();
      aggr1.setFormula(AggregateFormula.MAX);
      VSAggregateRef[] aggregates = { aggr0, aggr1 };
      cinfo.setDesignAggregates(aggregates);

      return calcData(box, crosstab, type, cacheKey, fieldKey);
   }

   private static IntervalData calcData(ViewsheetSandbox box, CrosstabVSAssembly crosstab,
                                        String type, String cacheKey, String fieldKey)
      throws Exception
   {
      if(box.getViewsheet().getViewsheetInfo().isMetadata()) {
         return null;
      }

      VSTableLens tableLens = box.getVSTableLens(crosstab.getAbsoluteName(), false);
      clearTempCrosstab(box, crosstab);

      try {
         IntervalData data = createIntervalData(tableLens, type);

         if(data != null) {
            addIntervalData(cacheKey, fieldKey, data);
            return data;
         }
      }
      catch(NumberFormatException ignore) {
         if(box.getViewsheet().getViewsheetInfo().isMetadata()) {
            return null;
         }

         LOG.debug("Failed to calculate interval data", ignore);
      }

      return null;
   }

   private static IntervalData createIntervalData(TableLens tableLens, String type)
      throws NumberFormatException
   {
      int row = getRowCount(tableLens);

      if(row <= 1) {
         return null;
      }

      int hrow = tableLens.getHeaderRowCount();
      Object startObject = tableLens.getObject(hrow, 0);
      Object endObject = null;

      if(row == hrow + 1) {
         endObject = tableLens.getObject(hrow, 1);
      }

      if(startObject instanceof Date || endObject instanceof Date) {
         int level = getLevelByRange(startObject, endObject, type);
         long start = startObject instanceof Date ? ((Date) startObject).getTime() : -1;
         long end = endObject instanceof Date ? ((Date) endObject).getTime() : -1;

         return new IntervalData(level, start, end);
      }
      else if(startObject instanceof Number && endObject instanceof Number) {
         double start = ((Number) startObject).doubleValue();
         double end = ((Number) endObject).doubleValue();
         return new IntervalData(0, start, end);
      }
      else {
         double start = startObject != null ? Double.parseDouble(startObject + "") : 0;
         double end = endObject != null ? Double.parseDouble(endObject + "") : 0;
         return new IntervalData(0, start, end);
      }
   }

   /**
    * Return a key for IntervalData in WizardDataMap.
    */
   private static String getFieldKey(String fieldName) {
      return FILED_PREIX + "__" + fieldName;
   }

   /**
    * Get IntervalData from cache.
    * @param key the cache entry key.
    * @param fieldKey target field key to get IntervalData cache.
    */
   private static IntervalData getIntervalData(String key, String fieldKey) {
      WizardData data = getCacheData(key, fieldKey);
      return data instanceof IntervalData ? (IntervalData) data : null;
   }

   /**
    * Add CardinalityData to cache.
    * @param key the cache entry key.
    * @param data the target CardinalityData to add into cache.
    */
   private static void addIntervalData(String key, String fieldKey, IntervalData data) {
      if(data == null) {
         return;
      }

      addCacheData(key, fieldKey, data);
   }

   /**
    * Get the default date level by time range.
    * @param startObject start time
    * @param endObject end time
    * @return
    */
   private static int getLevelByRange(Object startObject, Object endObject, String type) {
      int level = DateRangeRef.NONE_INTERVAL;

      if(startObject instanceof Date && endObject instanceof Date) {
         Date start = (Date) startObject;
         Date end = (Date) endObject;
         boolean isTime = XSchema.TIME.equals(type);
         boolean isDate = XSchema.DATE.equals(type);

         if(matchTimeRange(start, end, DateRangeRef.YEAR_INTERVAL, DATE_RANGE_LIMIT) && !isTime) {
            level = DateRangeRef.YEAR_INTERVAL;
         }
         else if(matchTimeRange(start, end, DateRangeRef.MONTH_INTERVAL, DATE_RANGE_LIMIT)
            && !isTime)
         {
            level = DateRangeRef.MONTH_INTERVAL;
         }
         else if(matchTimeRange(start, end, DateRangeRef.DAY_INTERVAL, DATE_RANGE_LIMIT)
            && !isTime)
         {
            level = DateRangeRef.DAY_INTERVAL;
         }
         else if(matchTimeRange(start, end, DateRangeRef.HOUR_INTERVAL, TIME_RANGE_LIMIT) && !isDate) {
            level = DateRangeRef.HOUR_INTERVAL;
         }
         else if(matchTimeRange(start, end, DateRangeRef.MINUTE_INTERVAL, TIME_RANGE_LIMIT) && !isDate) {
            level = DateRangeRef.MINUTE_INTERVAL;
         }
         // use day as smallest level for date type.
         else if(XSchema.DATE.equals(type)) {
            level = DateRangeRef.DAY_INTERVAL;
         }
         else {
            level = DateRangeRef.SECOND_INTERVAL;
         }
      }

      return level;
   }

   public static long getTimeRange(Date startDate, Date endDate, int level) {
      long range = 0;
      LocalDateTime start = getLocalDateTime(startDate);
      LocalDateTime end = getLocalDateTime(endDate);

      if(level == DateRangeRef.YEAR_INTERVAL) {
         start = LocalDateTime.of(start.getYear(), 1, 1, 0, 0);
         end = LocalDateTime.of(end.getYear(), 1, 1, 0, 0);
         range = Period.between(start.toLocalDate(), end.toLocalDate()).getYears();
      }
      else if(level == DateRangeRef.QUARTER_INTERVAL) {
         start = LocalDateTime.of(start.getYear(), start.getMonth().firstMonthOfQuarter(), 1, 0, 0);
         end = LocalDateTime.of(end.getYear(), end.getMonth().firstMonthOfQuarter(), 1, 0, 0);
         range = start.until(end, ChronoUnit.MONTHS) / 3;
      }
      else if(level == DateRangeRef.MONTH_INTERVAL) {
         start = LocalDateTime.of(start.getYear(), start.getMonth(), 1, 0, 0);
         end = LocalDateTime.of(end.getYear(), end.getMonth(), 1, 0, 0);
         range = start.until(end, ChronoUnit.MONTHS);
      }
      else if(level == DateRangeRef.WEEK_INTERVAL) {
         start = LocalDateTime.of(start.getYear(), start.getMonth(), start.getDayOfMonth(), 0, 0);
         end = LocalDateTime.of(end.getYear(), end.getMonth(), end.getDayOfMonth(), 0, 0);
         range = start.until(end, ChronoUnit.WEEKS);
      }
      else if(level == DateRangeRef.DAY_INTERVAL) {
         start = LocalDateTime.of(start.getYear(), start.getMonth(), start.getDayOfMonth(), 0, 0);
         end = LocalDateTime.of(end.getYear(), end.getMonth(), end.getDayOfMonth(), 0, 0);
         range = start.until(end, ChronoUnit.DAYS);
      }
      else if(level == DateRangeRef.HOUR_INTERVAL) {
         start = LocalDateTime.of(start.getYear(), start.getMonth(), start.getDayOfMonth(),
            start.getHour(), 0);
         end = LocalDateTime.of(end.getYear(), end.getMonth(), end.getDayOfMonth(), end.getHour(),
            0);
         range = start.until(end, ChronoUnit.HOURS);
      }
      else if(level == DateRangeRef.MINUTE_INTERVAL) {
         start = LocalDateTime.of(start.getYear(), start.getMonth(), start.getDayOfMonth(),
            start.getHour(), start.getMinute());
         end = LocalDateTime.of(end.getYear(), end.getMonth(), end.getDayOfMonth(), end.getHour(),
            end.getMinute());
         range = start.until(end, ChronoUnit.MINUTES);
      }

      return range + 1;
   }

   /**
    * Weather match the min range count.
    * @param startDate start time
    * @param endDate end time
    * @param level date level
    * @param rangeLimit min range count.
    * @return
    */
   private static boolean matchTimeRange(Date startDate, Date endDate, int level, int rangeLimit) {
      long range = getTimeRange(startDate, endDate, level);

      return range >= rangeLimit;
   }

   private static LocalDateTime getLocalDateTime(Date date) {
      if(date instanceof java.sql.Time) {
         LocalDate ldate = LocalDate.now();
         LocalTime ltime = ((java.sql.Time) date).toLocalTime();
         return LocalDateTime.of(ldate, ltime);
      }
      else if(date instanceof java.sql.Date) {
         date = new Date(date.getTime());
      }

      return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
   }

   /**
    * Create a AggregateRef for the target field.
    * @param  ref     the field name.
    * @param  max  using max as formula if true, else using min.
    */
   private static VSAggregateRef createAggregateRef(DataRef ref, boolean max) {
      VSAggregateRef agg = new VSAggregateRef();
      String name = getColumnName(ref);
      agg.setColumnValue(name);
      String dtype = ref.getDataType();
      agg.setOriginalDataType(dtype);

      agg.setRefType(ref.getRefType());
      AggregateFormula formula = max ? AggregateFormula.MAX : AggregateFormula.MIN;
      agg.setFormula(formula);

      return agg;
   }

   public static final class Reference
      extends SingletonManager.Reference<IntervalExecutor>
   {
      @Override
      public synchronized IntervalExecutor get(Object ... parameters) {
         if(executor == null) {
            executor = new IntervalExecutor();
         }

         return executor;
      }

      @Override
      public synchronized void dispose() {
         if(executor != null) {
            executor = null;
         }
      }

      private IntervalExecutor executor;
   }

   private static final int DATE_RANGE_LIMIT = 3;
   private static final int TIME_RANGE_LIMIT = 5;
   private static final String FILED_PREIX = "Interval";
   private static final Logger LOG = LoggerFactory.getLogger(IntervalExecutor.class);
}

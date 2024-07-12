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
package inetsoft.graph.scale;

import com.inetsoft.build.tern.*;
import inetsoft.graph.AxisSpec;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.text.*;
import java.util.*;

/**
 * A time scale is used to map date/time values on a linear scale.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=TimeScale")
public class TimeScale extends Scale {
   /**
    * Second interval.
    */
   @TernField
   public static final int SECOND = Calendar.SECOND;
   /**
    * Minute interval.
    */
   @TernField
   public static final int MINUTE = Calendar.MINUTE;
   /**
    * Hour interval.
    */
   @TernField
   public static final int HOUR = Calendar.HOUR_OF_DAY;
   /**
    * Day interval.
    */
   @TernField
   public static final int DAY = Calendar.DATE;
   /**
    * Week interval.
    */
   @TernField
   public static final int WEEK = Calendar.WEEK_OF_YEAR;
   /**
    * Month interval.
    */
   @TernField
   public static final int MONTH = Calendar.MONTH;
   /**
    * Quarter interval.
    */
   @TernField
   public static final int QUARTER = 1000;
   /**
    * Year interval.
    */
   @TernField
   public static final int YEAR = Calendar.YEAR;

   /**
    * Default constructor.
    */
   public TimeScale() {
   }

   /**
    * Create a scale with user specified min and max.
    */
   public TimeScale(Date min, Date max) {
      this.umin = min.getTime();
      this.umax = max.getTime();
   }

   /**
    * Create a scale for the specified columns.
    */
   @TernConstructor
   public TimeScale(String... fields) {
      super(fields);
   }

   /**
    * Initialize the scale to use the values in the dataset.
    */
   @Override
   public void init(DataSet data) {
      String[] flds = getDataFields();
      int option = getScaleOption();
      List<Long> dates = new ArrayList<>();

      // use fields if data fields are not defined
      if(flds == null || flds.length == 0) {
         flds = getFields();
      }

      long min0 = Long.MAX_VALUE;
      long max0 = Long.MIN_VALUE;
      boolean nonull = (option & NO_NULL) != 0;
      boolean trimLeading = (getScaleOption() & NO_LEADING_NULL_VAR) != 0;
      boolean trimTrailing = (getScaleOption() & NO_TRAILING_NULL_VAR) != 0;

      nullValue = false;
      nodata = false;
      ticks = null;

      int rowCnt = data.getRowCount();
      Set nullVals = new HashSet();

      for(int i = 0; i < rowCnt; i++) {
         if(!isAccepted(data, i)) {
            continue;
         }

         for(int j = 0; j < flds.length; j++) {
            Object val = data.getData(flds[j], i);
            Date date = (val instanceof Date) ? (Date) val : null;
            isTime = date instanceof Time;

            if(j == 0) {
               dates.add(date == null ? null : date.getTime());
            }

            if(date != null) {
               long time = date.getTime();

               if(time < min0) {
                  if(!trimLeading || !isNullMeasures(data, i)) {
                     min0 = time;
                  }
               }

               if(time > max0) {
                  if(!trimTrailing || !isNullMeasures(data, i)) {
                     max0 = time;
                  }
               }
            }
            else if(!nonull) {
               nullVals.add(val);
               nullValue = true;
            }
         }
      }

      // others label (52619)
      nullLabel = nullVals.size() == 1 ? nullVals.iterator().next() : null;

      // no data
      if(min0 == Long.MAX_VALUE) {
         min0 = max0 = new Date().getTime();
         nodata = true;
      }

      // calculate interval
      Collections.sort(dates, new Comparator() {
         @Override
         public int compare(Object a, Object b) {
            if(a == null) {
               return b == null ? 0 : -1;
            }
            else if(b == null) {
               return 1;
            }
            else {
               return ((Comparable) a).compareTo(b);
            }
         }
      });

      Long ldate = dates.size() > 0 ? dates.get(0) : null;
      // year is the largest interval for time
      interval = 1000L * 60 * 60 * 24 * 365; // year

      // get min interval
      for(int i = 1; i < dates.size(); i++) {
         if(ldate != null && dates.get(i) != null) {
            double interval2 = dates.get(i) - ldate;

            // ignore millisecond
            if(interval2 >= 1000) {
               interval = Math.min(interval, interval2);
            }
         }

         ldate = dates.get(i);
      }

      // type not specified? calculate type
      if(type0 == null && interval > 0) {
         int index = -1;
         double ratio = 100000000.0;

         for(int i = 0; i < INC_LENGTH.length; i++) {
            // if the range of the data is less than the interval, use a
            // smaller interval
            if(max0 - min0 < INC_LENGTH[i]) {
               continue;
            }

            double ratio2 = interval / INC_LENGTH[i];

            if(index < 0) {
               index = i;
               ratio = ratio2;
               continue;
            }

            // 1 is the best ratio, a ratio closer to 1 is better
            if(Math.abs(ratio2 - 1) < Math.abs(ratio - 1)) {
               index = i;
               ratio = ratio2;
            }
         }

         // index < 0 ? maybe only one date value
         type = INC_FIELD[index < 0 ? 0 : index][0];
         // if the type is changed here, update the cached default format
         cacheSpec = null;
      }

      cmin = min0;
      cmax = max0;

      // force min/max to include ticks
      getTicks();
   }

   /**
    * Initialize the values by copying the values from the other scale. This function
    * assumes the two scales have identical options (e.g. increment).
    */
   public void copyValues(TimeScale scale) {
      this.cmin = scale.cmin;
      this.cmax = scale.cmax;
      this.type = scale.type;
      this.nullValue = scale.nullValue;
      this.nodata = scale.nodata;
      this.interval = scale.interval;
      this.ticks = scale.ticks;
   }

   /**
    * Map a value to a logical position using this scale.
    *
    * @return double represent the logical position of this value;
    */
   @Override
   public double mapValue(Object val) {
      if(val instanceof Date) {
         return ((Date) val).getTime();
      }
      else if(val instanceof Number) {
         return ((Number) val).doubleValue();
      }

      // if ticks is not included in range, and there is null, ignore the
      // point otherwise the range will be modified (if getTicks is called).
      // init() treats all non-date as null, and we should do the same here
      // so 'Others' is handled property. (52619)
      if(nullValue && ticks != null) {
         return ticks[0];
      }

      return Double.NaN;
   }

   /**
    * Get the gap for min and max.
    */
   private long getGap() {
      // don't introduce a gap if not initialized otherwise scale in visual
      // frame won't be initialized properly
      if(fill || (cmin == 0 && cmin == cmax && interval == -1)) {
         return 0;
      }

      for(int i = 0; i < INC_FIELD.length; i++) {
         if(INC_FIELD[i][0] == type) {
            return INC_LENGTH[i] / 2;
         }
      }

      return 0;
   }

   /**
    * Get the minimum value on the scale.
    */
   @Override
   @TernMethod
   public double getMin() {
      return getMin0() - getGap();
   }

   /**
    * Get the minimum value on the scale.
    */
   private double getMin0() {
      return (umin == null) ? cmin : umin;
   }

   /**
    * Set the minimum value of the scale.
    */
   @TernMethod
   public void setMin(Date min) {
      this.umin = min.getTime();
      ticks = null;
   }

   /**
    * Get the maximum value on the scale.
    */
   @Override
   @TernMethod
   public double getMax() {
      return getMax0() + getGap();
   }

   /**
    * Get the maximum value on the scale.
    */
   private double getMax0() {
      return (umax == null) ? cmax : umax;
   }

   /**
    * Set the maximum value of the scale.
    */
   @TernMethod
   public void setMax(Date max) {
      this.umax = max.getTime();
      ticks = null;
   }

   /**
    * Get the date type defined in TimeScale.
    */
   @TernMethod
   public int getType() {
      return type;
   }

   /**
    * Set the date type defined in TimeScale. If this is not set, the type is
    * derived from the actual time data by approximating the smallest interval
    * between dates.
    * @param type the specified date type, e.g. MONTH, YEAR.
    */
   @TernMethod
   public void setType(int type) {
      boolean found = false;

      for(int i = 0; i < INC_FIELD.length; i++) {
         if(type == INC_FIELD[i][0]) {
            found = true;
            break;
         }
      }

      if(!found) {
         throw new RuntimeException("Incorrect time scale type: " + type);
      }

      this.type = type;
      this.type0 = type < 0 ? null : type;
      cacheSpec = null;
   }

   /**
    * Get the tick increment.
    */
   @TernMethod
   public Number getIncrement() {
      return increment;
   }

   /**
    * Set the tick increment. If the increment is null, an increment is
    * calculated automatically. If the increment is set to a positive integer,
    * it's the number of period (e.g. Month for month interval scale) to
    * increment for ticks.
    */
   @TernMethod
   public void setIncrement(Number increment) {
      this.increment = increment;
   }

   /**
    * Get the maximum number of ticks.
    */
   @TernMethod
   public Integer getMaxTicks() {
      return maxTicks;
   }

   /**
    * Set the maximum number of ticks. If the property is not set, the maximum number
    * of ticks are determined algorithmically.
    */
   @TernMethod
   public void setMaxTicks(Integer maxTicks) {
      this.maxTicks = maxTicks;
   }

   /**
    * Each interval of the time scale is treated as an unit.
    */
   @TernMethod
   @Override
   public int getUnitCount() {
      double inc = getIncLength();

      if(interval > 0) {
         inc = Math.min(inc, interval);
      }

      // @by larryl, this is a hack to remember if we are in VGraph generation
      // stage. It's used later to decide whether to cache the AxisSpec
      gen = true;

      // @by cehnw, it seems that year, quarter, month have a precision problem.
      double val = (getMax0() - getMin0()) / inc;
      int ceil = (int) Math.ceil(val);

      // avoid too manu unit for calculating preferred width
      switch(type) {
      case SECOND:
      case MINUTE:
      case HOUR:
         ceil = Math.min(600, ceil);
         break;
      case DAY:
         ceil = Math.min(1000, ceil);
         break;
      }

      return nodata ? 2 : ceil - val == 0 ? ceil + 1 : Math.max(1, ceil);
   }

   /**
    * Get the tick positions. The values of the ticks are logical coordinate
    * position same as the values returned by map().
    *
    * @return double[] represent the logical position of each tick.
    */
   @TernMethod
   @Override
   public double[] getTicks() {
      if(ticks == null) {
         double diff = Double.MAX_VALUE;
         long min = (long) getMin0();
         long max = (long) getMax0();

         if(type < 0) {
            type = DAY;

            // find the interval that produces ~5 ticks
            for(int i = 0; i < INC_LENGTH.length; i++) {
               double diff2 = Math.abs((max - min) / INC_LENGTH[i] - 5);

               if(diff2 < diff) {
                  type = INC_FIELD[i][0];
                  diff = diff2;
               }
            }
         }

         // add dates on the tick to dates
         List<Date> dates = new ArrayList<>();
         GregorianCalendar cal = new GregorianCalendar();
         int n = increment != null ? Math.max(1, increment.intValue()) : 1;
         long incLen = getIncLength();
         int maxTicks = Integer.MAX_VALUE;

         if(this.maxTicks != null) {
            maxTicks = this.maxTicks;
         }
         // use user set increment if set
         else if(increment != null && increment.intValue() > 0) {
            String mstr = GTool.getProperty("graph.axislabel.maxcount",
                                            GDefaults.AXIS_LABEL_MAX_COUNT + "");
            maxTicks = Integer.parseInt(mstr);
         }
         // make sure the ticks are not too crowded
         // if the interval is month or higher, don't skip ticks
         else if(incLen < 1000L * 60 * 60 * 24 * 30) {
            // less than a month
            maxTicks = 50;

            // week interval allow for one year
            if(incLen > 1000L * 60 * 60 * 24) {
               maxTicks = 54;
            }
         }

         // sanity check (e.g. max could be really huge and month/year doesn't set a default above)
         maxTicks = Math.min(maxTicks, GDefaults.AXIS_LABEL_MAX_COUNT * 2);

         boolean hasDup = isFormattedToHigherInterval();

         // make sure the number of ticks is not too large (out of memory)
         if(!hasDup) {
            while((max - min) / (incLen * n) > maxTicks) {
               n++;
            }
         }

         cal.setTime(new Date(min));

         // set the first tick, or use min if it's explicitly set
         if(this.umin == null) {
            // clear out the lower level values (e.g. day type clears out hour/minute/second)
            for(int i = 0; type != INC_FIELD[i][0]; i++) {
               if(INC_FIELD[i][1] < 0 ||
                  (type == WEEK && INC_FIELD[i][0] == DAY) ||
                  (type == QUARTER && INC_FIELD[i][0] == MONTH))
               {
                  continue;
               }

               cal.set(INC_FIELD[i][0], INC_FIELD[i][1]);
            }
         }

         // add null value
         if(nullValue) {
            add(cal, type, -n);
            dates.add(cal.getTime());
            cmin = cal.getTime().getTime();

            if(nodata) {
               cmax = cmin;
            }

            add(cal, type, n);
         }

         if(!nodata) {
            String lastLabel = null;
            Format fmt = getAxisSpec().getTextSpec().getFormat();

            // add ticks between min and max, with another sanity check on size
            while(cal.getTime().getTime() <= max && (hasDup || dates.size() <= maxTicks * 2)) {
               // if has duplicate, only include the first distinct value.
               if(hasDup) {
                  String label = fmt != null ? fmt.format(cal.getTime()) : lastLabel;

                  if(!Objects.equals(label, lastLabel)) {
                     dates.add(cal.getTime());
                  }

                  lastLabel = label;
               }
               else {
                  dates.add(cal.getTime());
               }

               add(cal, type, n);
            }

            // enforce maxTicks for hasDup since maxTicks was ignored in the above loop.
            while(hasDup && dates.size() > maxTicks) {
               for(int i = 1; i < dates.size(); i++) {
                  dates.remove(i);
               }
            }

            // make sure last date is on scale if the increment changed due to maxTicks. (53670)
            if(n > 1 && (increment == null || n != increment.intValue()) && dates.size() > 0) {
               if(dates.get(dates.size() - 1).getTime() < max) {
                  dates.add(new Date(max));
               }
            }
         }

         ticks = dates.stream().mapToDouble(Date::getTime).toArray();

         boolean includeTicks = (getScaleOption() & TICKS) != 0;

         if(ticks.length > 0 && includeTicks && !(nodata && nullValue)) {
            cmin = Math.min(min, dates.get(0).getTime());
            cmax = Math.max(max, dates.get(ticks.length - 1).getTime());
         }
      }

      return ticks.clone();
   }

   @Override
   public boolean isUniformInterval() {
      return !isFormattedToHigherInterval();
   }

   // check if the format turns data into higher level interval (e.g. day -> yyyy-MM).
   private boolean isFormattedToHigherInterval() {
      Format fmt = getAxisSpec().getTextSpec().getFormat();

      if(fmt instanceof DateFormat && type >= 0 && type < Calendar.ZONE_OFFSET) {
         Calendar now = new GregorianCalendar();
         Calendar next = new GregorianCalendar();
         next.add(type, 1);

         if(fmt.format(now.getTime()).equals(fmt.format(next.getTime()))) {
            return true;
         }
      }

      return false;
   }

   /**
    * Add type.
    */
   private void add(Calendar cal, int type, int step) {
      if(type == QUARTER) {
         type = MONTH;
         step *= 3;
      }

      cal.add(type, step);
   }

   /**
    * Get the values at each tick.
    *
    * @return Object[] represent values on each tick.
    */
   @Override
   @TernMethod
   public Object[] getValues() {
      double[] ticks = getTicks();
      Object[] values = new Object[ticks.length];

      for(int i = 0; i < values.length; i++) {
         if(nullValue && i == 0) {
            values[i] = nullLabel;
         }
         else if(isTime) {
            values[i] = new Time((long) ticks[i]);
         }
         else {
            values[i] = new Date((long) ticks[i]);
         }
      }

      return values;
   }

   /**
    * Get the length of the increment type.
    */
   private long getIncLength() {
      for(int i = 0; i < INC_FIELD.length; i++) {
         if(INC_FIELD[i][0] == type) {
            return INC_LENGTH[i];
         }
      }

      return INC_LENGTH[INC_LENGTH.length - 1];
   }

   /**
    * Set whether the scale should fill or leave gaps at two sides.
    * @param fill an boolean specifying the suggested fill or leave
    *        gaps at two sides.
    */
   @TernMethod
   public void setFill(boolean fill) {
      this.fill = fill;
   }

   /**
    * Check whether the scale should fill or leave gaps at two sides.
    *
    * @return true if fill the gaps at two sides, false otherwise.
    */
   @TernMethod
   public boolean isFill() {
      return fill;
   }

   /**
    * Set the attribute for creating the axis for this scale.
    */
   @Override
   @TernMethod
   public void setAxisSpec(AxisSpec axisSpec) {
      super.setAxisSpec(axisSpec);
      cacheSpec = null;
   }

   /**
    * Get the associated axis attributes.
    */
   @Override
   @TernMethod
   public AxisSpec getAxisSpec() {
      // don't create a copy of the spec until generation starts, so setting
      // by caller on AxisSpec won't be lost
      if(!gen) {
         return super.getAxisSpec();
      }

      if(cacheSpec == null) {
         cacheSpec = (AxisSpec) super.getAxisSpec().clone();

         if(cacheSpec.getTextSpec().getFormat() == null) {
            cacheSpec.getTextSpec().setFormat(getLabelFormat());
         }
      }

      return cacheSpec;
   }

   /**
    * Get the label format.
    */
   private Format getLabelFormat() {
      // don't set format if not initialized (type is not known)
      if(cmin == cmax) {
         return null;
      }

      switch(type) {
      case TimeScale.SECOND:
         return SECOND_FORMAT;
      case TimeScale.MINUTE:
         return MINUTE_FORMAT;
      case TimeScale.HOUR:
         return HOUR_FORMAT;
      case TimeScale.DAY:
      case TimeScale.WEEK:
         return DATE_FORMAT;
      case TimeScale.MONTH:
      case TimeScale.QUARTER:
         return MONTH_FORMAT;
      case TimeScale.YEAR:
         return YEAR_FORMAT;
      }

      return DATE_FORMAT;
   }

   private static Format SECOND_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
   private static Format MINUTE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
   private static Format HOUR_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH");
   private static Format DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
   private static Format MONTH_FORMAT = new SimpleDateFormat("yyyy MMM");
   private static Format YEAR_FORMAT = new SimpleDateFormat("yyyy");

   // increment durations
   private static long[] INC_LENGTH = {
      1000L, // second
      1000L * 60, // minute
      1000L * 60 * 60, // hour
      1000L * 60 * 60 * 24, // day
      1000L * 60 * 60 * 24 * 7, // week of year
      1000L * 60 * 60 * 24 * 30, // month
      1000L * 60 * 60 * 24 * 30 * 3, // quarter
      1000L * 60 * 60 * 24 * 365, // year
   };

   // increment fields, and the value for the MIN
   private static int[][] INC_FIELD = {
      { SECOND, 0 },
      { MINUTE, 0 },
      { HOUR, 0 },
      { DAY, 1 },
      { WEEK, -1 },
      { MONTH, 0 },
      { QUARTER, -1 },
      { YEAR, 0 },
      };

   private Long umin = null; // explicitly set min
   private Long umax = null; // explicitly set max
   private long cmin = 0; // calculated min
   private long cmax = 0; // calculated max
   private int type = DAY;
   private Integer type0 = null; // explicitly set type
   private boolean nullValue = false;
   private Object nullLabel; // others label
   private boolean nodata = false;
   private boolean fill = false; // default to leave gap
   private transient AxisSpec cacheSpec; // cached spec
   private double interval = -1; // smallest interval in data
   private Number increment = null; // increment for ticks
   private Integer maxTicks = null;
   private boolean isTime = false;
   private transient double[] ticks;
   private transient boolean gen = false; // true if VGraph generation started

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(TimeScale.class);
}

/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * A date dimension column.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class DateMVColumn extends MVColumn implements XDynamicMVColumn {
   /**
    * Create all date mv columns.
    * @param table the specified table assembly.
    * @param col the base MVColumn of this DateMVColumn.
    * @param sonly true if only selection bounds to this table assembly. For
    * selection, date part is useless, and time is useless.
    */
   public static DateMVColumn[] createAll(TableAssembly table, MVColumn col, boolean sonly) {
      Set<Integer> realOpts = new HashSet<>();
      int[] dates;

      if(col.isDate()) {
         if(sonly) {
            dates = new int[] {DateRangeRef.YEAR_INTERVAL,
                               DateRangeRef.MONTH_INTERVAL,
                               DateRangeRef.WEEK_INTERVAL,
                               DateRangeRef.DAY_INTERVAL};
         }
         else {
            dates = alldates;
            String userDefined = SreeEnv.getProperty("date.common.options");

            // user not defined, "" may be user don't want to create any option
            if(userDefined == null) {
               realOpts.add(DateRangeRef.YEAR_INTERVAL);
               realOpts.add(DateRangeRef.MONTH_INTERVAL);
            }
            else {
               String[] ops = userDefined.split(",");

               for(int i = 0; i < ops.length; i++) {
                  if("".equals(ops[i].trim())) {
                     continue;
                  }

                  Integer op = dateOpts.get(ops[i].trim().toLowerCase());

                  if(op == null) {
                     LOG.warn(
                        "Date option '" + ops[i].trim() +
                        "' is invalid, options include (case insensitive): " +
                        "year, quarter, month, week, day, hour, minute, " +
                        "second, quarterofyear, monthofyear, weekofyear, " +
                        "dayofmonth, dayofweek, and hourofday.");
                  }

                  realOpts.add(op);
               }
            }
         }
      }
      else {
         if(sonly) {
            dates = new int[0];
         }
         else {
            dates = alltimes;
            String userDefined = SreeEnv.getProperty("time.common.options");

            if(userDefined == null) {
               realOpts.add(DateRangeRef.HOUR_INTERVAL);
               realOpts.add(DateRangeRef.HOUR_OF_DAY_PART);
            }
            else {
               String[] ops = userDefined.split(",");

               for(int i = 0; i < ops.length; i++) {
                  if("".equals(ops[i].trim())) {
                     continue;
                  }

                  Integer op = timeOpts.get(ops[i].trim().toLowerCase());

                  if(op == null) {
                     LOG.warn(
                        "Time option '" + ops[i].trim() +
                        "' is invalid, options include (case insensitive): " +
                        "hour, minute, second, and hourofday.");
                  }

                  realOpts.add(op);
               }
            }
         }
      }

      DateMVColumn[] arr = new DateMVColumn[dates.length];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = create(col, table, dates[i]);
         arr[i].setReal(realOpts.contains(dates[i]));
      }

      return arr;
   }

   /**
    * Get the range date option.
    */
   public static final int getRangeDateOption() {
      return RANGE_INTERVAL;
   }

   /**
    * Create one date mv column.
    */
   private static DateMVColumn create(MVColumn col, TableAssembly table, int dtype) {
      ColumnRef bcol = col.getColumn();
      ColumnSelection cols = table.getColumnSelection(false);
      ColumnRef wbcol = AssetUtil.getColumnRefFromAttribute(cols, bcol, true);

      if(wbcol == null) {
         ColumnSelection pcols = table.getColumnSelection(true);
         wbcol = AssetUtil.getColumnRefFromAttribute(pcols, bcol, true);
      }

      ColumnRef nwbcol = createDateColumn(dtype, wbcol);
      ColumnRef nbcol = VSUtil.getVSColumnRef(nwbcol);
      DateMVColumn column = new DateMVColumn(col, nbcol, dtype);
      column.setDimension(true);
      return column;
   }

   /**
    * Create date column.
    */
   public static ColumnRef createDateColumn(int dtype, ColumnRef wbcol) {
      String name = getRangeName(VSUtil.getAttribute(wbcol), dtype);
      DateRangeRef aref = new DateRangeRef(name, wbcol.getDataRef());
      String otype = wbcol.getDataType();
      aref.setOriginalType(otype);
      aref.setDateOption(dtype);
      aref.setRefType(wbcol.getRefType());
      ColumnRef ncolumn = new ColumnRef(aref);
      String rangeType = DateRangeRef.getDataType(dtype);

      // if original data is time, grouped internal should be time instead of timestamp
      if(XSchema.TIME.equals(otype) && XSchema.TIME_INSTANT.equals(rangeType)) {
         rangeType = otype;
      }

      ncolumn.setDataType(rangeType);
      return ncolumn;
   }

   /**
    * Get the name of the associated date range ref.
    */
   public static String getRangeName(String attr, int dtype) {
      return DateRangeRef.getName(attr, dtype);
   }

   /**
    * Create an instance of DateMVColumn.
    */
   public DateMVColumn() {
      super();
   }

   /**
    * Create an instance of DateMVColumn.
    */
   public DateMVColumn(MVColumn base, ColumnRef column, int level) {
      super(column);

      this.base = base;
      this.level = level;
   }

   /**
    * Get the date level.
    */
   public int getLevel() {
      return level;
   }

   /**
    * Convert raw value to range value.
    */
   @Override
   public Object convert(Object obj) {
      if(obj == null || !(obj instanceof Date)) {
         return null;
      }

      if(level == RANGE_INTERVAL) {
         if(min0 == null) {
            min0 = (Date) obj;
            max0 = (Date) obj;
         }
         else if(obj != null) {
            Date dval = (Date) obj;

            if(min0.compareTo(dval) > 0) {
               min0 = dval;
            }
            else if(max0.compareTo(dval) < 0) {
               max0 = dval;
            }
         }
      }

      return DateRangeRef.getData(level, (Date) obj);
   }

   /**
    * Get the base mv column.
    */
   @Override
   public MVColumn getBase() {
      return base;
   }

   /**
    * Get the original max value.
    */
   public Date getMax() {
      return max0;
   }

   /**
    * Set the date range max value.
    */
   public void setMax(Date max) {
      this.max0 = max;
   }

   /**
    * Get the original min value.
    */
   public Date getMin() {
      return min0;
   }

   /**
    * Set the date range min value.
    */
   public void setMin(Date min) {
      this.min0 = min;
   }

   @Override
   public int getDataLength() {
      int len = super.getDataLength() + base.getDataLength() + 4;
      len += 8;

      if(max0 != null) {
         len += 8;
      }

      if(min0 != null) {
         len += 8;
      }

      len += 1;
      return len;
   }

   @Override
   public void write(ByteBuffer buf) {
      super.write(buf);
      base.write(buf);
      buf.putInt(level);
      buf.putInt(max0 == null ? 0 : 1);

      if(max0 != null) {
         buf.putLong(max0.getTime());
      }

      buf.putInt(min0 == null ? 0 : 1);

      if(min0 != null) {
         buf.putLong(min0.getTime());
      }

      buf.put((byte) (real ? -4 : -5));
   }

   @Override
   public void read(ByteBuffer buf) {
      super.read(buf);
      base = new MVColumn();
      base.read(buf);
      level = buf.getInt();

      if(buf.getInt() == 1) {
         max0 = new Date(buf.getLong());
      }

      if(buf.getInt() == 1) {
         min0 = new Date(buf.getLong());
      }

      int pos = buf.position();

      if(buf.hasRemaining()) {
         byte flag = buf.get();

         if(flag == -4 || flag == -5) {
            real = flag == -4;
         }
         else {
            XSwapUtil.position(buf, pos);
         }
      }
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.println(" level=\"" + level + "\" real=\"" + real + "\" ");
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      base.writeXML(writer);

      if(max0 != null) {
         writer.println("<max>" + max0.getTime() + "</max>");
      }

      if(min0 != null) {
         writer.println("<min>" + min0.getTime() + "</min>");
      }
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      super.parseXML(tag);
      level = Integer.parseInt(Tool.getAttribute(tag, "level"));
      real = "true".equals(Tool.getAttribute(tag, "real"));
      base = new MVColumn();
      base.parseXML(Tool.getChildNodeByTagName(tag, "mvcolumn"));
      Element maxNode = Tool.getChildNodeByTagName(tag, "max");

      if(maxNode != null) {
         long maxVal = Long.parseLong(Tool.getValue(maxNode));
         max0 = new Date(maxVal);
      }

      Element minNode = Tool.getChildNodeByTagName(tag, "min");

      if(minNode != null) {
         long minVal = Long.parseLong(Tool.getValue(minNode));
         min0 = new Date(minVal);
      }
   }

   /**
    * Set perform real data.
    */
   public void setReal(boolean real) {
      this.real = real;
   }

   /**
    * Check if perform real data.
    */
   public boolean isReal() {
      return this.real;
   }

   private static final int RANGE_INTERVAL = DateRangeRef.YEAR_INTERVAL;
   private int level = 0;
   private MVColumn base = null;
   private Date max0 = null;
   private Date min0 = null;
   private boolean real = true;

   private static int[] alldates = new int[] {
      DateRangeRef.YEAR_INTERVAL, DateRangeRef.QUARTER_INTERVAL,
      DateRangeRef.MONTH_INTERVAL, DateRangeRef.WEEK_INTERVAL,
      DateRangeRef.DAY_INTERVAL, DateRangeRef.HOUR_INTERVAL,
      DateRangeRef.MINUTE_INTERVAL, DateRangeRef.SECOND_INTERVAL,
      DateRangeRef.QUARTER_OF_YEAR_PART, DateRangeRef.MONTH_OF_YEAR_PART,
      DateRangeRef.WEEK_OF_YEAR_PART, DateRangeRef.DAY_OF_MONTH_PART,
      DateRangeRef.DAY_OF_WEEK_PART, DateRangeRef.HOUR_OF_DAY_PART,
      DateRangeRef.MINUTE_OF_HOUR_PART, DateRangeRef.SECOND_OF_MINUTE_PART,
      DateRangeRef.NONE_INTERVAL,
      // intervals used in date comparison
      DateRangeRef.DAY_OF_YEAR_PART, DateRangeRef.DAY_OF_QUARTER_PART,
      DateRangeRef.WEEK_OF_MONTH_PART, DateRangeRef.WEEK_OF_QUARTER_PART,
      DateRangeRef.MONTH_OF_QUARTER_PART, DateRangeRef.MONTH_OF_QUARTER_FULL_WEEK_PART,
      DateRangeRef.MONTH_OF_FULL_WEEK, DateRangeRef.MONTH_OF_FULL_WEEK_PART,
      DateRangeRef.QUARTER_OF_FULL_WEEK_PART, DateRangeRef.QUARTER_OF_FULL_WEEK,
      DateRangeRef.YEAR_OF_FULL_WEEK };

   private static int[] alltimes = new int[] {
      DateRangeRef.HOUR_INTERVAL, DateRangeRef.MINUTE_INTERVAL, DateRangeRef.SECOND_INTERVAL,
      DateRangeRef.HOUR_OF_DAY_PART, DateRangeRef.MINUTE_OF_HOUR_PART,
      DateRangeRef.SECOND_OF_MINUTE_PART};

   private static Map<String, Integer> dateOpts = new HashMap();
   static {
      dateOpts.put("year", DateRangeRef.YEAR_INTERVAL);
      dateOpts.put("quarter", DateRangeRef.QUARTER_INTERVAL);
      dateOpts.put("month", DateRangeRef.MONTH_INTERVAL);
      dateOpts.put("week", DateRangeRef.WEEK_INTERVAL);
      dateOpts.put("day", DateRangeRef.DAY_INTERVAL);
      dateOpts.put("hour", DateRangeRef.HOUR_INTERVAL);
      dateOpts.put("minute", DateRangeRef.MINUTE_INTERVAL);
      dateOpts.put("second", DateRangeRef.SECOND_INTERVAL);
      dateOpts.put("quarterofyear", DateRangeRef.QUARTER_OF_YEAR_PART);
      dateOpts.put("monthofyear", DateRangeRef.MONTH_OF_YEAR_PART);
      dateOpts.put("weekofyear", DateRangeRef.WEEK_OF_YEAR_PART);
      dateOpts.put("dayofmonth", DateRangeRef.DAY_OF_MONTH_PART);
      dateOpts.put("dayofweek", DateRangeRef.DAY_OF_WEEK_PART);
      dateOpts.put("hourofday", DateRangeRef.HOUR_OF_DAY_PART);
      dateOpts.put("minuteofhour", DateRangeRef.MINUTE_OF_HOUR_PART);
      dateOpts.put("secondofminute", DateRangeRef.SECOND_OF_MINUTE_PART);
      dateOpts.put("none", DateRangeRef.NONE_INTERVAL);
   }
   private static Map<String, Integer> timeOpts = new HashMap();
   static {
      timeOpts.put("hour", DateRangeRef.HOUR_INTERVAL);
      timeOpts.put("minute", DateRangeRef.MINUTE_INTERVAL);
      timeOpts.put("second", DateRangeRef.SECOND_INTERVAL);
      timeOpts.put("hourofday", DateRangeRef.HOUR_OF_DAY_PART);
      timeOpts.put("minuteofhour", DateRangeRef.MINUTE_OF_HOUR_PART);
      timeOpts.put("secondofminute", DateRangeRef.SECOND_OF_MINUTE_PART);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(DateMVColumn.class);
}

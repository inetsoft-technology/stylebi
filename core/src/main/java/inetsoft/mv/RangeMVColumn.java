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
package inetsoft.mv;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * RangeMVColumn, represents one range column used in MV.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class RangeMVColumn extends MVColumn implements XDynamicMVColumn {
   /**
    * Default tick numbers for range column.
    */
   public static final int TICKCOUNT = 512;

   /**
    * Prefix of range column.
    */
   public static final String PREFIX = "RANGE_";

   /**
    * Create one date mv column.
    */
   public static RangeMVColumn create(TableAssembly table, MVColumn col,
                                      boolean log) {
      ColumnRef bcol = col.getColumn(); // viewsheet col
      ColumnSelection cols = table.getColumnSelection(false);
      ColumnSelection pcols = table.getColumnSelection(true);
      ColumnRef wbcol = AssetUtil.getColumnRefFromAttribute(cols, bcol, true);
      wbcol = wbcol != null ? wbcol :
         AssetUtil.getColumnRefFromAttribute(pcols, bcol, true);

      if(wbcol == null) {
         return null;
      }

      ColumnRef nwbcol = createRangeColumn(wbcol);
      // cols.addAttribute(nwbcol);
      ColumnRef nbcol = VSUtil.getVSColumnRef(nwbcol);
      RangeMVColumn column = new RangeMVColumn(col, nbcol, log);
      column.setDimension(true);
      return column;
   }

   /**
    * Create range column.
    */
   public static ColumnRef createRangeColumn(ColumnRef wbcol) {
      String name = getRangeName(VSUtil.getAttribute(wbcol));
      NumericRangeRef range = new NumericRangeRef(name, wbcol.getDataRef());
      range.setRefType(wbcol.getRefType());
      ColumnRef ncolumn = new ColumnRef(range);
      ncolumn.setDataType(XSchema.DOUBLE);
      return ncolumn;
   }

   /**
    * Get the range grouping column name.
    */
   public static String getRangeName(String attr) {
      return PREFIX + attr;
   }

   /**
    * Create an instance of RangeMVColumn.
    */
   public RangeMVColumn() {
      super();
   }

   /**
    * Create an instance of RangeMVColumn.
    */
   public RangeMVColumn(MVColumn base, ColumnRef column, boolean log) {
      super(column);

      this.base = base;
      this.log = log;
   }

   /**
    * Get the max value.
    */
   public Number getMax() {
      return max;
   }

   /**
    * Get the min value.
    */
   public Number getMin() {
      return min;
   }

   /**
    * Get the basic interval.
    */
   public Number getInterval() {
      return interval;
   }

   /**
    * Get the tick count.
    */
   public int getTickCount() {
      return ticks.length;
   }

   /**
    * Check if the range is using log scale.
    */
   public boolean isLogScale() {
      return log;
   }

   /**
    * Reset the range mv column.
    */
   @Override
   public void setRange(Number min, Number max) {
      super.setRange(min, max);
      this.min = min;
      this.max = max;
      data = null;

      if(min != null && max != null) {
         double[] nums = TimeSliderVSAssembly.getNiceNumbers(min.doubleValue(),
            max.doubleValue(), TICKCOUNT);
         this.interval = nums[2];
         int tickCount = (int) ((nums[1] - nums[0]) / nums[2]);
         this.ticks = TimeSliderVSAssembly.getPreferredTicks(nums[0],
            nums[1], tickCount, false, log, nums[2]);
         this.min = ticks[0];
         this.max = ticks[ticks.length - 1];
      }
   }

   /**
    * Check if is upper inclusive.
    */
   public boolean isUpperInclusive(Object obj) {
      if(obj == null || !(obj instanceof Number) || ticks.length == 0) {
         return true;
      }

      Double val = ((Number) obj).doubleValue();
      double max = ticks[ticks.length - 1];
      return val <= max;
   }

   /**
    * Convert raw value to range value.
    */
   @Override
   public Object convert(Object obj) {
      if(obj == null || !(obj instanceof Number)) {
         return null;
      }

      if(ticks == null) {
         return obj;
      }

      int idx = Arrays.binarySearch(ticks, ((Number) obj).doubleValue());

      if(idx < 0) {
         idx = -idx - 1;

         if(idx == 0) {
            return ticks[idx];
         }
         else if(idx == ticks.length) {
            return ticks[idx - 1];
         }

         // the value betwwen tick should be group independent with tick
         return (ticks[idx - 1] + ticks[idx]) / 2;
      }
      else {
         return ticks[idx];
      }
   }

   /**
    * Get the base mv column.
    */
   @Override
   public MVColumn getBase() {
      return base;
   }

   @Override
   public int getDataLength() {
      int len = super.getDataLength() + base.getDataLength();
      String[] datas = getData();
      len += 1; // datas is null?

      if(datas != null) {
         for(String str : getData()) {
            len += (4 + (str == null ? 0 : str.length() * 2));
         }
      }

      len += 1; // ticks is null?

      if(ticks != null) {
         len += 4; // ticks length;
         len += ticks.length * 8;
      }

      len += 1; // log?
      return len;
   }

   @Override
   public void write(ByteBuffer buf) {
      super.write(buf);
      base.write(buf);

      String[] datas = getData();
      buf.put(datas == null ? (byte) 0 : (byte) 1);

      if(datas != null) {
         for(String str : datas) {
            int ssize = str == null ? -1 : str.length();
            buf.putInt(ssize);

            for(int i = 0; i < ssize; i++) {
               buf.putChar(str.charAt(i));
            }
         }
      }

      buf.put(ticks == null ? (byte) 0 : (byte) 1);

      if(ticks != null) {
         buf.putInt(ticks.length);

         for(double tick : ticks) {
            buf.putDouble(tick);
         }
      }

      buf.put((byte) (log ? 1 : 0));
   }

   @Override
   public void read(ByteBuffer buf) {
      super.read(buf);
      base = new MVColumn();
      base.read(buf);

      if(buf.get() == 1) {
         int len = 3 * 2;
         String[] strs = new String[len];

         for(int i = 0; i < len; i++) {
            int strlen = buf.getInt();

            if(strlen != -1) {
               char[] chars = new char[strlen];

               for(int j = 0; j < strlen; j++) {
                  chars[j] = buf.getChar();
               }

               strs[i] = new String(chars);
            }
         }

         min = (Number) Tool.getData(strs[0], strs[1]);
         max = (Number) Tool.getData(strs[2], strs[3]);
         interval = (Number) Tool.getData(strs[4], strs[5]);
      }

      if(buf.get() == 1) {
         int len = buf.getInt();
         ticks = new double[len];

         for(int i = 0; i < len; i++) {
            ticks[i] = buf.getDouble();
         }
      }

      log = buf.get() == 1 ? true : false;
   }

   /**
    * Convert min, max, interval to string representation.
    */
   private String[] getData() {
      if(min == null) {
         return null;
      }

      data = data != null ? data : new String[] {
         Tool.getDataType(min.getClass()), Tool.getDataString(min),
         Tool.getDataType(max.getClass()), Tool.getDataString(max),
         Tool.getDataType(interval.getClass()), Tool.getDataString(interval)};
      return data;
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" log=\"" + log + "\" ");
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(min != null) {
         Number[] values = new Number[] {min, max, interval};

         for(Number val : values) {
            writer.println("<value type=\"" + Tool.getDataType(val.getClass()) +
               "\" val=\"" + Tool.getDataString(val) + "\" />");
         }
      }

      if(ticks != null) {
         writer.print("<ticks>");

         for(int i = 0; i < ticks.length; i++) {
            writer.print(i == 0 ? "" : ",");
            writer.print(ticks[i]);
         }

         writer.println("</ticks>");
      }

      base.writeXML(writer);
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      super.parseXML(tag);
      log = "true".equals(Tool.getAttribute(tag, "log"));

      Element tnode = Tool.getChildNodeByTagName(tag, "ticks");

      if(tnode != null) {
         String[] strs = Tool.getValue(tnode).split(",");
         ticks = new double[strs.length];

         for(int i = 0; i < ticks.length; i++) {
            ticks[i] = Double.parseDouble(strs[i]);
         }
      }

      NodeList list = Tool.getChildNodesByTagName(tag, "value");

      if(list != null && list.getLength() == 5) {
         for(int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            Number val = (Number) Tool.getData(Tool.getAttribute(elem, "type"),
                                               Tool.getAttribute(elem, "val"));
            switch(i) {
            case 0:
               min = val;
               break;
            case 1:
               max = val;
               break;
            case 2:
               interval = val;
               break;
            }
         }
      }

      base = new MVColumn();
      base.parseXML(Tool.getChildNodeByTagName(tag, "mvcolumn"));
   }

   private MVColumn base;
   private Number max;
   private Number min;
   private Number interval;
   private double[] ticks;
   private boolean log;
   private transient String[] data;
}

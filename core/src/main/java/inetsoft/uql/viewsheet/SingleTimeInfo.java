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
package inetsoft.uql.viewsheet;

import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * SingleTimeInfo specifies how a date/time column is used in a time slider or
 * other date/time assemblies.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class SingleTimeInfo extends TimeInfo {
   /**
    * Constructor.
    */
   public SingleTimeInfo() {
      super();
   }

   /**
    * Check if is date time.
    */
   public boolean isDateTime() {
      int rangeType = getRangeType();
      return rangeType == YEAR || rangeType == MONTH || rangeType == DAY ||
         rangeType == HOUR || rangeType == MINUTE ||
         rangeType == HOUR_OF_DAY || rangeType == MINUTE_OF_DAY;
   }

   /**
    * Get the range time, YEAR, MONTH, DAY, or NUMBER.
    * @return the unit of time information.
    */
   public int getRangeType() {
      return rangeTypeValue.getIntValue(false, MONTH);
   }

   /**
    * Set the range type. The constants are
    * defined as MONTH, YEAR, DAY, and NUMBER.
    */
   public void setRangeType(int rtype) {
      rangeTypeValue.setRValue(rtype);
   }

   /**
    * Get the range time, YEAR, MONTH, DAY, or NUMBER.
    * @return the unit of time information.
    */
   public int getRangeTypeValue() {
      return rangeTypeValue.getIntValue(true, MONTH);
   }

   /**
    * Set the range type. The constants are
    * defined as MONTH, YEAR, DAY, and NUMBER.
    */
   public void setRangeTypeValue(int rtype) {
      rangeTypeValue.setDValue(rtype + "");
   }

   /**
    * Get the numeric range size.
    */
   public double getRangeSize() {
      return rangeSizeValue.getDoubleValue(false, 0);
   }

   /**
    * Set the numeric range size. This is the smallest unit of selection.
    * A user may resize a slider to be a multiple of this range size, but the
    * range would never be less than the range size.
    */
   public void setRangeSize(double rsize) {
      rangeSizeValue.setRValue(rsize);
   }

   /**
    * Get the numeric range size.
    */
   public double getRangeSizeValue() {
      return rangeSizeValue.getDoubleValue(true, 0);
   }

   /**
    * Set the numeric range size. This is the smallest unit of selection.
    * A user may resize a slider to be a multiple of this range size, but the
    * range would never be less than the range size.
    */
   public void setRangeSizeValue(double rsize) {
      rangeSizeValue.setDValue(rsize + "");
   }

   /**
    * Get the maximum numeric range size.
    */
   public double getMaxRangeSize() {
      return maxSizeValue.getDoubleValue(false, 0);
   }

   /**
    * Set the maximum numeric range size. This is the maximum number of units
    * a user may resize a slider to.
    */
   public void setMaxRangeSize(double rsize) {
      maxSizeValue.setRValue(rsize);
   }

   /**
    * Get the maximum numeric range size.
    */
   public double getMaxRangeSizeValue() {
      return maxSizeValue.getDoubleValue(true, 0);
   }

   /**
    * Set the maximum numeric range size.
    */
   public void setMaxRangeSizeValue(double rsize) {
      maxSizeValue.setDValue(rsize + "");
   }

   /**
    * Get the column reference of time information.
    * @return the column reference of time information.
    */
   public DataRef getDataRef() {
      return column;
   }

   /**
    * Set the column reference of time information.
    * @param column the column reference of time information.
    */
   public void setDataRef(DataRef column) {
      this.column = column;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" rangeType=\"" + getRangeType() + "\"");
      writer.print(" rangeSize=\"" + getRangeSize() + "\"");
      writer.print(" rangeTypeValue=\"" + getRangeTypeValue() + "\"");
      writer.print(" rangeSizeValue=\"" + getRangeSizeValue() + "\"");
      writer.print(" maxSize=\"" + getMaxRangeSize() + "\"");
      writer.print(" maxSizeValue=\"" + getMaxRangeSizeValue() + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
      String prop;

      prop = Tool.getAttribute(elem, "rangeTypeValue");
      prop = prop == null ? Tool.getAttribute(elem, "rangeType") : prop;

      if(prop != null) {
         rangeTypeValue.setDValue(prop);
      }

      prop = Tool.getAttribute(elem, "rangeSizeValue");
      prop = prop == null ? Tool.getAttribute(elem, "rangeSize") : prop;

      if(prop != null) {
         rangeSizeValue.setDValue(prop);
      }

      prop = Tool.getAttribute(elem, "maxSizeValue");

      if(prop != null) {
         maxSizeValue.setDValue(prop);
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(column != null) {
         column.writeXML(writer);
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element cnode = Tool.getChildNodeByTagName(elem, "dataRef");

      if(cnode != null) {
         column = AbstractDataRef.createDataRef(cnode);
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         SingleTimeInfo info = (SingleTimeInfo) super.clone();

         if(column != null) {
            info.column = (DataRef) column.clone();
         }

         info.rangeTypeValue = (DynamicValue2) rangeTypeValue.clone();
         info.rangeSizeValue = (DynamicValue2) rangeSizeValue.clone();
         info.maxSizeValue = (DynamicValue2) maxSizeValue.clone();

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone SingleTimeInfo", ex);
      }

      return null;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof SingleTimeInfo)) {
         return false;
      }

      SingleTimeInfo sinfo = (SingleTimeInfo) obj;
      return Tool.equals(rangeTypeValue, sinfo.rangeTypeValue) &&
         Tool.equals(rangeSizeValue, sinfo.rangeSizeValue) &&
         Tool.equals(maxSizeValue, sinfo.maxSizeValue) &&
         Tool.equals(column, sinfo.column) &&
         (getRangeSize() == sinfo.getRangeSize()) &&
         (getRangeType() == sinfo.getRangeType());
   }

   /**
    * Check if the binding information has changed.
    */
   @Override
   public boolean equalsBinding(TimeInfo tinfo) {
      if(!super.equalsBinding(tinfo)) {
         return false;
      }

      try {
         SingleTimeInfo info2 = (SingleTimeInfo) tinfo;

         return Tool.equals(rangeTypeValue, info2.rangeTypeValue) &&
            (getRangeType() == info2.getRangeType()) &&
            Tool.equals(column, info2.column);

      }
      catch(Exception ex) {
         return false;
      }
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      rangeTypeValue.setRValue(null);
      rangeSizeValue.setRValue(null);
      maxSizeValue.setRValue(null);
   }

   private DynamicValue2 rangeTypeValue = new DynamicValue2(MONTH + "", XSchema.INTEGER);
   private DynamicValue2 rangeSizeValue = new DynamicValue2("0", XSchema.DOUBLE);
   private DynamicValue2 maxSizeValue = new DynamicValue2("0", XSchema.DOUBLE);
   private DataRef column;

   private static final Logger LOG =
      LoggerFactory.getLogger(SingleTimeInfo.class);
}

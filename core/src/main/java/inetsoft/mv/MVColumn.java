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
package inetsoft.mv;

import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Objects;

/**
 * MVColumn, represents one column used in MV, so whether it's a dimension or
 * measure is defined.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public class MVColumn implements XMLSerializable, Serializable {
   /**
    * Dimension column.
    */
   public static final int DIMENSION_COLUMN = 1;
   /**
    * Measure column.
    */
   public static final int MEASURE_COLUMN = 2;

   /**
    * Create one mv column for the given class name.
    */
   public static MVColumn create(String cname) throws Exception {
      cname = PREFIX + cname;
      return (MVColumn) Class.forName(cname).newInstance();
   }

   /**
    * Check if is numeric.
    */
   private static final boolean isNumericType(String dtype) {
      return XSchema.DOUBLE.equals(dtype) || XSchema.FLOAT.equals(dtype) ||
         XSchema.LONG.equals(dtype) || XSchema.INTEGER.equals(dtype) ||
         XSchema.SHORT.equals(dtype) || XSchema.BYTE.equals(dtype);
   }

   /**
    * Create an instance of MVColumn.
    */
   public MVColumn() {
      super();
   }

   /**
    * Create an instance of MVColumn.
    */
   public MVColumn(ColumnRef column) {
      this(column, !isNumericType(column.getDataType()));
   }

   /**
    * Create an instance of MVColumn.
    */
   public MVColumn(ColumnRef column, boolean dim) {
      super();

      this.column = column;
      this.dim = dim;
   }

   /**
    * Get the base column.
    */
   public final ColumnRef getColumn() {
      return column;
   }

   /**
    * Check if this MVColumn matches the specified column.
    */
   public final boolean matches(String col, boolean dim) {
      return this.dim == dim && Tool.equals(col, column.getAttribute());
   }

   /**
    * Set whether it's a dimension.
    */
   public final void setDimension(boolean dim) {
      this.dim = dim;
   }

   /**
    * Check if the MVColumn is a dimension.
    */
   public final boolean isDimension() {
      return dim;
   }

   /**
    * Check if is date.
    */
   public final boolean isDateTime() {
      String dtype = column.getDataType();
      return XSchema.TIME_INSTANT.equals(dtype) || XSchema.DATE.equals(dtype) ||
         XSchema.TIME.equals(dtype);
   }

   /**
    * Check if is date.
    */
   public final boolean isDate() {
      String dtype = column.getDataType();
      return XSchema.TIME_INSTANT.equals(dtype) || XSchema.DATE.equals(dtype);
   }

   /**
    * Check if is time.
    */
   public final boolean isTime() {
      String dtype = column.getDataType();
      return XSchema.TIME.equals(dtype);
   }

   /**
    * Check if is numeric.
    */
   public final boolean isNumeric() {
      String dtype = column.getDataType();
      return isNumericType(dtype);
   }

   /**
    * Get the class name.
    */
   public final String getClassName() {
      String cls = getClass().getName();
      int index = cls.lastIndexOf(".");
      return cls.substring(index + 1);
   }

   /**
    * Get the string representation.
    */
   public final String toString() {
      return getClassName() + "@" + System.identityHashCode(this) +
         "-" + dim + '<' + column.toString() + '>';
   }

   /**
    * Get the name of this mv column.
    */
   public final String getName() {
      String alias = column.getAlias();
      return alias == null || alias.length() == 0 ?
         column.getAttribute() : alias;
   }

   /**
    * Get the original max value.
    */
   public Number getOriginalMax() {
      return max0;
   }

   /**
    * Get the original min value.
    */
   public Number getOriginalMin() {
      return min0;
   }

   /**
    * Reset the range mv column.
    */
   public void setRange(Number min, Number max) {
      this.min0 = min;
      this.max0 = max;
   }

   /**
    * Expand the range, by a number
    */
    public void expandRange(Number n) {
      if(n != null) {
         if(this.min0 == null || n.doubleValue() < this.min0.doubleValue()) {
            this.min0 = n;
         }
         if(this.max0 == null || n.doubleValue() > this.max0.doubleValue()) {
            this.max0 = n;
         }
      }
   }

   /**
    * Expand the range, by a Date
    */
   public void expandRange(Date d) {
      if(d != null) {
         expandRange(d.getTime());
      }
   }

   /**
    * Get data length.
    */
   public int getDataLength() {
      String[] strs = getData();
      int len = 0;

      for(String str : strs) {
         len += (4 + (str == null ? 0 : str.length() * 2));
      }

      len += 1;
      len += 16 + 1; // flag + min0, max0

      return len;
   }

   /**
    * Get the meta info of the base column.
    */
   public XMetaInfo getXMetaInfo() {
      return info;
   }

   /**
    * Set the meta info of the base column.
    */
   public void setXMetaInfo(XMetaInfo info) {
      this.info = info;
   }

   /**
    * read context from byte buffer.
    */
   public void read(ByteBuffer buf) {
      int len = 4;
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

      column = new ColumnRef(new AttributeRef(strs[0], strs[1]));
      column.setAlias(strs[2]);
      column.setDataType(strs[3]);
      dim = buf.get() == 1 ? true : false;

      if(buf.get() == 1) {
         min0 = buf.getDouble();
         max0 = buf.getDouble();
      }
      else {
         buf.getDouble();
         buf.getDouble();
      }
   }

   /**
    * Write context to byte buffer.
    */
   public void write(ByteBuffer buf) {
      String[] strs = getData();

      for(String str : strs) {
         int ssize = str == null ? -1 : str.length();
         buf.putInt(ssize);

         for(int i = 0; i < ssize; i++) {
            buf.putChar(str.charAt(i));
         }
      }

      buf.put((byte) (dim ? 1 : 0));

      if(min0 != null && max0 != null) {
         buf.put((byte) 1);
         buf.putDouble(min0.doubleValue());
         buf.putDouble(max0.doubleValue());
      }
      else {
         buf.put((byte) 0);
         buf.putDouble(0);
         buf.putDouble(0);
      }
   }

   private final String[] getData() {
      return new String[] {column.getEntity(), column.getAttribute(),
                           column.getAlias(), column.getDataType()};
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<mvcolumn");
      writeAttributes(writer);
      writer.print(">");
      writeContents(writer);
      writer.println("</mvcolumn>");
   }

   protected void writeAttributes(PrintWriter writer) {
      String dstr = !dim ? " dim=\"" + dim + "\" " : " ";
      writer.print(" class=\"" + getClassName() + "\"" + dstr);

      if(min0 != null && max0 != null) {
         writer.print(" min=\"" + min0 + "\" max=\"" + max0 + "\"");
      }
   }

   protected void writeContents(PrintWriter writer) {
      column.writeXML(writer);
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      dim = !"false".equals(Tool.getAttribute(tag, "dim"));
      column = (ColumnRef) AbstractDataRef.createDataRef(
         Tool.getChildNodeByTagName(tag, "dataRef"));

      String attr;

      if((attr = Tool.getAttribute(tag, "min")) != null) {
         min0 = Double.parseDouble(attr);
      }

      if((attr = Tool.getAttribute(tag, "max")) != null) {
         max0 = Double.parseDouble(attr);
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(!(o instanceof MVColumn)) return false;
      MVColumn mvColumn = (MVColumn) o;
      return dim == mvColumn.dim && Objects.equals(column, mvColumn.column) &&
         Objects.equals(max0, mvColumn.max0) && Objects.equals(min0, mvColumn.min0) &&
         Objects.equals(info, mvColumn.info);
   }

   @Override
   public int hashCode() {
      return Objects.hash(column, dim, max0, min0, info);
   }

   private static final String PREFIX = "inetsoft.mv.";
   private ColumnRef column;
   private boolean dim;
   private Number max0;
   private Number min0;
   private XMetaInfo info;
}

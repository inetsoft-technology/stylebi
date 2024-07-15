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

import inetsoft.report.StyleConstants;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.Tool;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Class holding a reference to a cube measure.
 *
 * @author  InetSoft Technology Corp.
 * @since   6.0
 */
public class MeasureRef extends AbstractDataRef implements Field {
   /**
    * No 80/20 filter.
    */
   public static final int NONE = 0;
   /**
    * Top 80% filter.
    */
   public static final int TOP_EIGHTY = 1;
   /**
    * Top 20% filter.
    */
   public static final int TOP_TWENTY = 2;
   /**
    * Bottom 80% filter.
    */
   public static final int BOTTOM_EIGHTY = 3;
   /**
    * Bottom 20% filter.
    */
   public static final int BOTTOM_TWENTY = 4;

   /**
    * Default format label.
    */
   public static final String DEFAULT_FORMAT_LABEL = "Default";

   /**
    * Default format.
    */
   public static final String DEFAULT_FORMAT = "#.##";

   /**
    * Percentage format lable.
    */
   public static final String PERCENTAGE_FORMAT_LABEL = "Percent";

   /**
    * Percentage format.
    */
   public static final String PERCENTAGE_FORMAT = "#.##%";

   /**
    * Currencty format label.
    */
   public static final String CURRENCY_FORMAT_LABEL = "Currency";

   /**
    * Currencty format.
    */
   public static final String CURRENCY_FORMAT = "\u00A4#.##";

   /**

   /**
    * Constructor.
    */
   public MeasureRef() {
      super();
   }

   /**
    * Get self.
    */
   @Override
   public Field getField() {
      return this;
   }

   /**
    * Get the type of the field.
    * @return the type of the field.
    */
   @Override
   public int getRefType() {
      return CUBE_MEASURE;
   }

   /**
    * Set the visibility of the field.
    *
    * @param visible true if is visible, false otherwise
    */
   @Override
   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   /**
    * Check the visibility of the field.
    *
    * @return true if is visible, false otherwise
    */
   @Override
   public boolean isVisible() {
      return visible;
   }

   /**
    * Set the sorting order of the field.
    *
    * @param order the specified sorting order defined in StyleConstants
    */
   @Override
   public void setOrder(int order) {
      this.order = order;
   }

   /**
    * Get the sorting order of the field.
    *
    * @return the sorting order defined in StyleConstants
    */
   @Override
   public int getOrder() {
      return order;
   }

   /**
    * Set the data type of the field.
    *
    * @param type the specified data type defined in XSchema
    */
   @Override
   public void setDataType(String type) {
      this.dtype = type;
   }

   /**
    * Get the data type of the field.
    *
    * @return the data type defined in XSchema
    */
   @Override
   public String getDataType() {
      // @by charvi 2004-04-23
      // If the type is "String, then return "double" instead
      // since all measures are of numeric type.
      if(dtype.trim().equalsIgnoreCase("string")) {
         return "double";
      }

      return dtype;
   }

   /**
    * Get the type node presentation of this field.
    *
    * @return the type node
    */
   @Override
   public XTypeNode getTypeNode() {
      return XSchema.createPrimitiveType(getDataType());
   }

   /**
    * Get the name of the cube.
    *
    * @return the name of the cube
    */
   @Override
   public String getEntity() {
      return getCube();
   }

   /**
    * Get the name of the measure
    *
    * @return the measure name.
    */
   @Override
   public String getAttribute() {
      return getName();
   }

   /**
    * Get the name of the OLAP data source to which this reference refers.
    *
    * @return the name of a OLAP data source.
    */
   public String getDataSource() {
      return datasource;
   }

   /**
    * Set the name of the OLAP data source to which this reference refers.
    *
    * @param datasource the name of a OLAP data source.
    */
   public void setDataSource(String datasource) {
      this.datasource = datasource;
   }

   /**
    * Get the name of the cube to which this reference refers.
    *
    * @return the name of cube
    */
   public String getCube() {
      return cube;
   }

   /**
    * Set the name of the cube to which this reference refers.
    *
    * @param cube the name of cube
    */
   public void setCube(String cube) {
      this.cube = cube;
      cname = null;
      chash = Integer.MIN_VALUE;
   }

   /**
    * Get the name of the measure
    *
    * @return the name of the measure
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Set the name of the measure
    *
    * @param name the name of the measure
    */
   public void setName(String name) {
      this.name = name;
      cname = null;
      chash = Integer.MIN_VALUE;
   }

   /**
    * Check if is date type.
    *
    * @return true if is, false otherwise
    */
   @Override
   public boolean isDate() {
      XTypeNode node = getTypeNode();
      return node != null && node.isDate();
   }

   /**
    * Check if is empty.
    *
    * @return true if is, false otherwise
    */
   @Override
   public boolean isEmpty() {
      return name == null || name.length() == 0;
   }

   /**
    * Set whether the field is processed in query generator.
    */
   @Override
   public void setProcessed(boolean processed) {
      this.processed = processed;
   }

   /**
    * Check if the field is processed in query generator.
    */
   @Override
   public boolean isProcessed() {
      return processed;
   }

   /**
    * Write the attributes of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      writer.print("visible=\"" + isVisible() + "\" ");
      writer.print("dataType=\"" + getDataType() + "\" ");
      writer.print("order=\"" + getOrder() + "\" ");

      if(getDataSource() != null) {
         writer.print("datasource=\"" + Tool.escape(getDataSource()) + "\" ");
      }

      writer.print("cube=\"" + Tool.escape(getCube()) + "\" ");
      writer.print("name=\"" + Tool.escape(getName()) + "\" ");
   }

   /**
    * Read in the attribute of this object from an XML tag.
    *
    * @param tag the XML Element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws DOMException {
      String val = null;

      if((val = Tool.getAttribute(tag, "visible")) != null) {
         setVisible(val.equals("true"));
      }

      if((val = Tool.getAttribute(tag, "dataType")) != null) {
         setDataType(val);
      }

      if((val = Tool.getAttribute(tag, "order")) != null) {
         setOrder(Integer.parseInt(val));
      }

      if((val = Tool.getAttribute(tag, "datasource")) != null) {
         setDataSource(val);
      }

      if((val = Tool.getAttribute(tag, "cube")) != null) {
         setCube(val);
      }

      if((val = Tool.getAttribute(tag, "name")) != null) {
         setName(val);
      }
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(super.equals(obj)) {
         if(!(obj instanceof MeasureRef)) {
            return false;
         }

         MeasureRef ref2 = (MeasureRef) obj;

         if(getDataSource() == null) {
            if(ref2.getDataSource() != null) {
               return false;
            }
         }
         else if(!getDataSource().equals(ref2.getDataSource())) {
            return false;
         }

         return true;
      }

      return false;
   }

   /**
    * Get the view representation of this field.
    *
    * @return the view representation of this field
    */
   @Override
   public String toView() {
      return toString();
   }

   /**
    * Create an embedded field.
    * @return the created embedded field.
    */
   @Override
   public Field createEmbeddedField() {
      return (Field) clone();
   }

   /**
    * Set whether is a group field.
    */
   @Override
   public void setGroupField(boolean gfld) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Check if is group field.
    */
   @Override
   public boolean isGroupField() {
      return false;
   }

   private boolean visible = true;
   private String dtype = XSchema.STRING;
   private int order = StyleConstants.SORT_NONE;
   private String datasource;
   private String cube;
   private String name;
   private boolean processed = false;
}

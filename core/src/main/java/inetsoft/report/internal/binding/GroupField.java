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
package inetsoft.report.internal.binding;

import inetsoft.report.filter.SortOrder;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * GroupField is a field that is composed of multiple other fields. In
 * some situations, such as grouping, a group field and mixed with
 * regular fields.
 * <p>
 * When use a GroupField to be a key, please be very careful for when
 * the contained fields change, the GroupField hash code value will change
 * too.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class GroupField extends BaseField implements DataRefWrapper, CalcGroup {
   /**
    * Create a group field.
    */
   public GroupField() {
      this(new BaseField(""));
   }

   /**
    * Wrap a field in a group field.
    */
   public GroupField(Field field) {
      this.field = field;
   }

   /**
    * Create a group field with a name.
    *
    * @param name the specified name
    */
   public GroupField(String name) {
      this(new BaseField(name));
   }

   /**
    * Get the contained data ref.
    */
   @Override
   public DataRef getDataRef() {
      return field;
   }

   /**
    * Set the contained data ref.
    */
   @Override
   public void setDataRef(DataRef field) {
      this.field = (Field) field;
   }

   /**
    * Get the base field.
    */
   @Override
   public Field getField() {
      return field;
   }

   /**
    * Set the base field.
    */
   public void setField(Field field) {
      this.field = field;
   }

   /**
    * Get the attribute's parent entity.
    *
    * @return the name of the entity
    */
   @Override
   public String getEntity() {
      return field.getEntity();
   }

   /**
    * Get the referenced attribute.
    *
    * @return the name of the attribute
    */
   @Override
   public String getAttribute() {
      return field.getAttribute();
   }

   /**
    * Set the visibility of the field.
    *
    * @param visible true if is visible, false otherwise
    */
   @Override
   public void setVisible(boolean visible) {
      field.setVisible(visible);
   }

   /**
    * Check the visibility of the field.
    *
    * @return true if is visible, false otherwise
    */
   @Override
   public boolean isVisible() {
      return field.isVisible();
   }

   /**
    * Set the data type of the field.
    *
    * @param type the specified data type defined in XSchema
    */
   @Override
   public void setDataType(String type) {
      field.setDataType(type);
   }

   /**
    * Get the data type of the field.
    *
    * @return the data type defined in XSchema
    */
   @Override
   public String getDataType() {
      return field.getDataType();
   }

   /**
    * Get the type node presentation of this field.
    */
   @Override
   public XTypeNode getTypeNode() {
      return field.getTypeNode();
   }

   /**
    * Write the attributes of this object.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" summarize=\"" + summarized + "\"");
      writer.print(" timeSeries=\"" + timeSeries + "\"");
   }

   /**
    * Write the contents of this object.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      field.writeXML(writer);

      if(order != null) {
         order.writeXML(writer);
      }

      if(topn != null) {
         topn.writeXML(writer);
      }
   }

   /**
    * Read in the attribute of this object from an XML tag.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);

      summarized = "true".equals(Tool.getAttribute(tag, "summarize"));
      timeSeries = "true".equals(Tool.getAttribute(tag, "timeSeries"));
   }

   /**
    * Read in the contents of this object from an xml tag.
    *
    * @param tag the output stream to which to write the XML data
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      Element tag2 = Tool.getChildNodeByTagName(tag, "dataRef");
      String cls = Tool.getAttribute(tag2, "class");

      if(cls != null) {
         Field fld = (Field) Class.forName(cls).newInstance();
         fld.parseXML(tag2);

         field = fld;
      }

      if((tag2 = Tool.getChildNodeByTagName(tag, "groupSort")) != null) {
         order = new OrderInfo();
         order.parseXML(tag2);
      }

      if((tag2 = Tool.getChildNodeByTagName(tag, "topn")) != null) {
         topn = new TopNInfo();
         topn.parseXML(tag2);
      }
   }

   /**
    * Check if is date type.
    *
    * @return true if is, false otherwise
    */
   @Override
   public boolean isDate() {
      XTypeNode node = field.getTypeNode();
      return node != null && node.isDate();
   }

   /**
    * Get the grouping ordering.
    */
   @Override
   public OrderInfo getOrderInfo() {
      return order;
   }

   /**
    * Set the grouping ordering.
    */
   @Override
   public void setOrderInfo(OrderInfo info) {
      this.order = info;
   }

   /**
    * Get the topN definition.
    */
   @Override
   public TopNInfo getTopN() {
      return topn;
   }

   /**
    * Set the topN definition.
    */
   @Override
   public void setTopN(TopNInfo topn) {
      this.topn = topn;
   }

   /**
    * Set whether a group (specified as group column) should be summarized.
    */
   public void setSummarize(boolean sum) {
      this.summarized = sum;
   }

   /**
    * Check if a group should be summarized.
    */
   public boolean isSummarize() {
      return summarized;
   }

   /**
    * Set time series.
    */
   public void setTimeSeries(boolean timeSeries) {
      this.timeSeries = timeSeries;
   }

   /**
    * Check if a group should be timeSeries.
    */
   public boolean isTimeSeries() {
      return timeSeries;
   }

   public boolean isNamedGroupAvailable() {
      return !isTimeSeries() && order != null && order.isSpecific() &&
         order.getRealNamedGroupInfo() != null;
   }

   /**
    * Check if equals another object.
    *
    * @param obj the specified object
    * @return true if equals, false otherwise
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof GroupField)) {
         return false;
      }

      GroupField cfld2 = (GroupField) obj;

      if(!(isDate() && cfld2.isDate())) {
         return field.equals(cfld2.field);
      }

      if(timeSeries != cfld2.isTimeSeries()) {
         return false;
      }

      // compare order to support same date field used multiple times with
      // different date level
      return field.equals(cfld2.field) && order.equals(cfld2.order);
   }

   /**
    * Get a hash code value for the object.
    *
    * @return a hash code value for this object
    */
   public int hashCode() {
      return field.hashCode();
   }

   /**
    * Get the view representation of this field.
    * @return the view representation of this field
    */
   @Override
   public String toView() {
      if(order.getOption() != 0 && XSchema.isDateType(getDataType())) {
         return DateRangeRef.getName(field.toView(), order.getOption());
      }

      return field.toView();
   }

   public String getFullName() {
      if(XSchema.isDateType(getDataType())) {
         return DateRangeRef.getName(field.getName(), order.getOption());
      }

      return field.getName();
   }

   /**
    * Get a textual representation of this object.
    *
    * @return a String representation of this object
    */
   public String toString() {
      return "[" + field + "]";
   }

   /**
    * Create a copy of this object.
    *
    * @return a copy of this object
    */
   @Override
   public Object clone() {
      GroupField cfld2 = (GroupField) super.cloneObject();

      cfld2.field = (Field) field.clone();

      if(order != null) {
         cfld2.order = (OrderInfo) order.clone();
      }

      if(topn != null) {
         cfld2.topn = (TopNInfo) topn.clone();
      }

      return cfld2;
   }

   /**
    * Create an embedded field.
    * @return the created embedded field.
    */
   @Override
   public Field createEmbeddedField() {
      GroupField cfld = (GroupField) clone();

      cfld.field = field.createEmbeddedField();
      return cfld;
   }

   private Field field;
   private OrderInfo order = new OrderInfo(SortOrder.SORT_ASC);
   private TopNInfo topn;
   private boolean summarized = true;
   private boolean timeSeries;
}

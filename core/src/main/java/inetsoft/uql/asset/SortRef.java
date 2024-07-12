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
package inetsoft.uql.asset;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.*;
import java.util.Enumeration;

/**
 * Sort ref represents a sorted data ref.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class SortRef extends AbstractDataRef implements AssetObject,
   DataRefWrapper
{
   /**
    * Constructor.
    */
   public SortRef() {
      super();
      order = XConstants.SORT_NONE;
      position = -1;
   }

   /**
    * Constructor.
    */
   public SortRef(DataRef ref) {
      this();
      this.ref = ref;
   }

   /**
    * Copy sort ref.
    * @param ref the specified data ref.
    * @return the copied sort ref.
    */
   public SortRef copySortRef(DataRef ref) {
      SortRef sort = (SortRef) clone();
      sort.ref = ref;

      return sort;
   }

   /**
    * Get the type of the field.
    * @return the type of the field.
    */
   @Override
   public int getRefType() {
      return ref == null ? NONE : ref.getRefType();
   }

   /**
    * Check if the attribute is an expression.
    * @return <tt>true</tt> if is an expression, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpression() {
      return ref.isExpression();
   }

   /**
    * Get the attribute's parent entity.
    * @return the name of the entity.
    */
   @Override
   public String getEntity() {
      return ref.getEntity();
   }

   /**
    * Get the attribute's parent entity.
    * @return an Enumeration with the name of the entity.
    */
   @Override
   public Enumeration getEntities() {
      return ref.getEntities();
   }

   /**
    * Get the referenced attribute.
    * @return the name of the attribute.
    */
   @Override
   public String getAttribute() {
      return ref.getAttribute();
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    * @return an Enumeration containing AttributeRef objects.
    */
   @Override
   public Enumeration getAttributes() {
      return ref.getAttributes();
   }

   /**
    * Determine if the entity is blank.
    * @return <code>true</code> if entity is <code>null</code> or blank.
    */
   @Override
   public boolean isEntityBlank() {
      return ref.isEntityBlank();
   }

   /**
    * Get the name of the field.
    * @return the name of the field.
    */
   @Override
   public String getName() {
      return ref.getName();
   }

   /**
    * Get the contained data ref.
    * @return the contained data ref.
    */
   @Override
   public DataRef getDataRef() {
      return ref;
   }

   /**
    * Set the contained data ref.
    * @param ref the contained data ref.
    */
   @Override
   public void setDataRef(DataRef ref) {
      this.ref = ref;
      chash = Integer.MIN_VALUE;
      cname = null;
   }

   /**
    * Refresh the contained data ref.
    * @param cols the specified column selection.
    */
   public void refreshDataRef(ColumnSelection cols) {
      int index = cols.indexOfAttribute(ref);

      if(index >= 0) {
         setDataRef(cols.getAttribute(index));
      }
   }

   /**
    * Get the data type.
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType() {
      if(!(ref instanceof ColumnRef)) {
         return XSchema.STRING;
      }

      return ((ColumnRef) ref).getDataType();
   }

   /**
    * Get the sorting order.
    * @return the sorting order defined in XConstants.
    */
   public int getOrder() {
      return order;
   }

   /**
    * Set the sorting order.
    * @param order the specified sorting order defined in XConstants.
    */
   public void setOrder(int order) {
      this.order = order;
   }

   /**
    * Get the sort position.
    * @return the sorting position.
    */
   public int getPosition() {
      return position;
   }

   /**
    * Set the sort position.
    * @param position the specified sorting position.
    */
   public void setPosition(int position) {
      this.position = position;
   }

   /**
    * Write the attributes of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" order=\"" + order + "\"");
   }

   /**
    * Write the attributes of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeAttributes2(DataOutputStream dos) {
      try {
         dos.writeInt(order);
      }
      catch (IOException e) {
      }
   }

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the XML element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      order = Integer.parseInt(Tool.getAttribute(tag, "order"));
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      ref.writeXML(writer);
   }

   /**
    * Write the contents of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeContents2(DataOutputStream dos) {
      try {
         dos.writeUTF(ref.getClass().getName());
         ref.writeData(dos);
      }
      catch (IOException e) {
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      Element dnode = Tool.getChildNodeByTagName(tag, "dataRef");
      ref = createDataRef(dnode);
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   public boolean printKey(PrintWriter writer) throws Exception {
      writer.print("SORT[");
      ConditionUtil.printDataRefKey(ref, writer);
      writer.print(",");
      writer.print(order);
      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean equalsSort(Object obj) {
      if(!(obj instanceof SortRef)) {
         return false;
      }

      SortRef sort2 = (SortRef) obj;
      return sort2.ref.equals(ref) && sort2.order == order;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return ref.toString();
   }

   /**
    * Get the view representation of this field.
    * @return the view representation of this field.
    */
   @Override
   public String toView() {
      return ref.toView();
   }

   private DataRef ref;
   private int order;
   private int position;
}

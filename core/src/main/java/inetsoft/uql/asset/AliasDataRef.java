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
package inetsoft.uql.asset;

import inetsoft.uql.erm.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Vector;

/**
 * AliasDataRef represents an alias data ref.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class AliasDataRef extends ExpressionRef implements AssetObject {
   /**
    * Constructor.
    */
   public AliasDataRef() {
      super();
   }

   /**
    * Constructor.
    */
   public AliasDataRef(String name, DataRef ref) {
      this();

      this.oref = ref;
      this.name = name;
      // in case of column ref, aggregate ref, etc.
      this.ref = DataRefWrapper.getBaseDataRef(ref);
   }

   /**
    * Get the referenced attribute.
    * @return the name of the attribute.
    */
   @Override
   public String getAttribute() {
      return name;
   }

   /**
    * Get the base data ref.
    */
   public DataRef getDataRef() {
      return ref;
   }

   /**
    * Set the base data ref.
    */
   public void setDataRef(DataRef ref) {
      this.oref = ref;
      this.ref = ref;
   }

   /**
    * Check if this alias is created for aggregate calc field.
    */
   public boolean isAggrCalc() {
      return aggrCalc;
   }

   /**
    * Set if this alias is created for aggregate calc field.
    */
   public void setAggrCalc(boolean aggrCalc) {
      this.aggrCalc = aggrCalc;
   }

   /**
    * Get the data type.
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType() {
      return (dataType != null || oref == null) ? dataType : oref.getDataType();
   }

   /**
    * Overwrite the default data type (base column type).
    */
   @Override
   public void setDataType(String type) {
      this.dataType = type;
   }

   @Override
   public boolean isDataTypeSet() {
      return dataType != null;
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      ref.writeXML(writer);

      writer.print("<attribute>");
      writer.print("<![CDATA[" + name + "]]>");
      writer.println("</attribute>");
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      Element dnode = Tool.getChildNodeByTagName(tag, "dataRef");
      ref = createDataRef(dnode);

      Element anode = Tool.getChildNodeByTagName(tag, "attribute");
      name = Tool.getValue(anode);
   }

   /**
    * Get the name of this reference.
    * @return the reference name.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Get the SQL expression of this reference.
    * @return a SQL expression.
    */
   @Override
   public String getExpression() {
      return "field['" + ref.getName() + "']";
   }

   /**
    * Get the script expression of this reference.
    * @return a script expression.
    */
   @Override
   public String getScriptExpression() {
      String fname = ref.getName();
      fname = fname.replace("'", "\\'");
      return "field['" + fname + "']";
   }

   /**
    * Check if expression is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpressionEditable() {
      return false;
   }

   /**
    * Check if this expression is sql expression.
    * @return true if is, false otherwise.
    */
   @Override
   public boolean isSQL() {
      return true;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      AliasDataRef aref = (AliasDataRef) super.clone();
      aref.ref = (DataRef) ref.clone();
      return aref;
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    * @return an Enumeration containing AttributeRef objects.
    */
   @Override
   public Enumeration getAttributes() {
      Vector list = new Vector();

      if(ref instanceof ExpressionRef) {
         Enumeration enumeration = ref.getAttributes();

         while(enumeration.hasMoreElements()) {
            list.add(enumeration.nextElement());
         }
      }
      else {
         list.add(ref);
      }

      return list.elements();
   }

   private DataRef oref;
   private DataRef ref;
   private String name;
   private String dataType;
   private boolean aggrCalc;

}

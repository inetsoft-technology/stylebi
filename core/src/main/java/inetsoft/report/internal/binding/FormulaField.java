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
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Enumeration;

/**
 * Formula Field represents a formula script field bind to the table.
 *
 * @version 6.0
 * @author InetSoft Technology Corp
 */
public class FormulaField extends ExpressionRef implements Field, SourceField {
   /**
    * Script formula.
    */
   public static final String SCRIPT = "Script";
   /**
    * SQL formula.
    */
   public static final String SQL = "SQL";

   /**
    * Create a blank formula field.
    */
   public FormulaField() {
      super();
   }

   /**
    * Create a formula field with name.
    *
    * @param name the specified name
    */
   public FormulaField(String name) {
      super(null, name);
   }

   /**
    * Create a formula field with name and expression.
    *
    * @param name the specified name
    * @param exp the specicified expression
    */
   public FormulaField(String name, String exp) {
      super(null, name);
      setExpression(exp);
   }

   /**
    * Create a formula field with name and expression.
    *
    * @param name the specified name
    * @param exp the specicified expression
    */
   public FormulaField(String entity, String name, String exp) {
      super(entity, name);
      setExpression(exp);
   }

   /**
    * Get self.
    */
   @Override
   public Field getField() {
      return this;
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
    * Get the type node presentation of this field.
    *
    * @return the type node
    */
   @Override
   public XTypeNode getTypeNode() {
      return XSchema.createPrimitiveType(getDataType());
   }

   /**
    * Get the formula type, avaliabed values are SCRIPT or SQL.
    *
    * @return the formula type
    */
   public String getType() {
      return formulaType;
   }

   /**
    * Set the formula type.
    *
    * @param type the formula type
    */
   public void setType(String type) {
      this.formulaType = type;
   }

   /**
    * Find attribute refs which are wrapped by "field['" and "']", or
    * "field('" and "')".
    *
    * @return an enumeration contains Attribute refs
    */
   @Override
   public Enumeration getAttributes() {
      return XUtil.findAttributes(getExpression());
   }

   /**
    * Check if a formula is a SQL based formula.
    */
   @Override
   public boolean isSQL() {
      return SQL.equals(formulaType);
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
      return getName() == null || getName().length() == 0;
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
    * Get the string presentation of the field.
    *
    * @return the string presentation
    */
   public String toString() {
      return getName();
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
    * Write the attributes of this object.
    *
    * @param writer the output stream to which to write the XML data
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print("visible=\"" + isVisible() + "\" ");
      writer.print("order=\"" + getOrder() + "\" ");
      writer.print("formulaType=\"" + getType() + "\" ");
      writer.print("fake=\"" + isFake() + "\" ");

      // only when default is invisible, write this property
      if(defVisible) {
         writer.print("defVis=\"" + defVisible + "\" ");
      }

      if(source != null) {
         writer.print("source=\"" + Tool.escape(source) + "\" ");
         writer.print("prefix=\"" + Tool.escape(prefix) + "\" ");
         writer.print("sourceType=\"" + sourceType + "\" ");
      }
   }

   /**
    * Write the attributes of this object.
    *
    * @param tag the XML element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String str;

      if((str = Tool.getAttribute(tag, "visible")) != null) {
         setVisible(str.equals("true"));
      }

      if((str = Tool.getAttribute(tag, "dataType")) != null) {
         setDataType(str);
      }

      if((str = Tool.getAttribute(tag, "order")) != null) {
         setOrder(Integer.parseInt(str));
      }

      if((str = Tool.getAttribute(tag, "type")) != null) {
         setType(str);
      }
      else if((str = Tool.getAttribute(tag, "formulaType")) != null) {
         setType(str);
      }

      setFake("true".equals(Tool.getAttribute(tag, "fake")));

      if((str = Tool.getAttribute(tag, "defVis")) != null) {
         defVisible = "true".equals(str);
      }

      if(Tool.getAttribute(tag, "source") != null) {
         source = Tool.getAttribute(tag, "source");
         prefix = Tool.getAttribute(tag, "prefix");

         if(Tool.getAttribute(tag, "sourceType") != null) {
            sourceType = Integer.parseInt(Tool.getAttribute(tag, "sourceType"));
         }
      }
   }

   /**
    * Create an embedded field.
    * @return the created embedded field.
    */
   @Override
   public Field createEmbeddedField() {
      BaseField field = new BaseField(this);
      field.setVisible(visible);
      field.setOrder(order);
      field.setProcessed(processed);
      return field;
   }

   /**
    * Get the original field.
    */
   public Field getOriginalField() {
      return ofld;
   }

   /**
    * Set the original field.
    */
   public void setOriginalField(Field fld) {
      this.ofld = fld;
   }

   /**
    * Check if is group field.
    */
   @Override
   public boolean isGroupField() {
      return gfld;
   }

   /**
    * Set whether is a group field.
    */
   @Override
   public void setGroupField(boolean gfld) {
      this.gfld = gfld;
   }

   /**
    * Set the formula field is a fake formula or not.
    */
   public void setFake(boolean fake) {
      this.fake = fake;
   }

   /**
    * Check if the formula field is a fake field.
    */
   public boolean isFake() {
      return this.fake;
   }

    /**
    * Check if equals another object content.
    * @param obj the specified object.
    * @return true if equals, false otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof FormulaField) || !super.equalsContent(obj)) {
         return false;
      }

      FormulaField ref = (FormulaField) obj;

      return Tool.equals(formulaType, ref.getType());
   }

   /**
    * Set the source of this field. It could be the name of the query or data
    * model.
    */
   @Override
   public void setSource(String source) {
      this.source = source;
   }

   /**
    * Get the source of this field.
    */
   @Override
   public String getSource(boolean force) {
      return source;
   }

   /**
    * Get the source of this field.
    */
   @Override
   public String getSource() {
      return getSource(false);
   }

   /**
    * Set the prefix of source.
    */
   @Override
   public void setSourcePrefix(String prefix) {
      this.prefix = prefix;
   }

   /**
    * Get the prefix of the source.
    */
   @Override
   public String getSourcePrefix() {
      return prefix;
   }

   /**
    * Set the type of this source.
    */
   @Override
   public void setSourceType(int type) {
      this.sourceType = type;
   }

   /**
    * Get the type of the source.
    */
   @Override
   public int getSourceType() {
      return sourceType;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(obj instanceof FormulaField) {
         FormulaField ffield = (FormulaField) obj;

         if(getSource(true) != null && ffield.getSource(true) != null) {
            return getSource(true).equals(ffield.getSource(true));
         }
      }

      return true;
   }

   public FormulaHeaderInfo getHeaderInfo() {
      return headerInfo;
   }

   public void setHeaderInfo(FormulaHeaderInfo headerInfo) {
      this.headerInfo = headerInfo;
   }

   private boolean visible = true;
   private int order = StyleConstants.SORT_NONE;
   private String formulaType = SCRIPT;
   private boolean fake = false;
   private boolean processed = false;
   private transient Field ofld;
   private transient boolean gfld;
   // @by davyc, a identical property for BC from 10.2 to 10.3
   // for summary only table in old version, detail fields visible is true,
   // but now from 10.3, it will be false, so when doing BC, we will convert
   // detail fields for this situation's visible to false, this will cause
   // script problem, if user use script to modify the summary only to false,
   // these detail fields will not be shown as 10.2, because they are invisible,
   // so from 10.3, the script for set summary only to false, we will change all
   // detail fields' visible to true, but we don't know which field is user set
   // visible to false in old version, which field is set visible to false by
   // BC, so this property will be set for those field in old version visible
   // is false
   private boolean defVisible = false;
   private String source = null;
   private String prefix;
   private int sourceType;
   private FormulaHeaderInfo headerInfo;
}

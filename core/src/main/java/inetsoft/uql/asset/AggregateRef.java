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
package inetsoft.uql.asset;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.*;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;

/**
 * AggregateRef represents a data ref has aggregate.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class AggregateRef extends AbstractDataRef implements AssetObject,
   DataRefWrapper, ContentObject, IAggregateRef, CalcAggregate
{
   /**
    * Constructor.
    */
   public AggregateRef() {
      super();
   }

   /**
    * Constructor for an aggregate that calculates results from one column.
    */
   public AggregateRef(DataRef ref, AggregateFormula formula) {
      this(ref, null, formula);
   }

   /**
    * Constructor for an aggregate that calculates results from two columns.
    */
   public AggregateRef(DataRef ref, DataRef ref2, AggregateFormula formula) {
      this.ref = ref;
      this.ref2 = ref2;
      this.formula = formula;
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
      return ref != null && ref.isExpression();
   }

   /**
    * Get the attribute's parent entity.
    * @return the name of the entity.
    */
   @Override
   public String getEntity() {
      return ref != null ? ref.getEntity() : null;
   }

   /**
    * Get the attribute's parent entity.
    * @return an Enumeration with the name of the entity.
    */
   @Override
   public Enumeration getEntities() {
      return ref != null ? ref.getEntities() : Collections.emptyEnumeration();
   }

   /**
    * Get the referenced attribute.
    * @return the name of the attribute.
    */
   @Override
   public String getAttribute() {
      return ref != null ? ref.getAttribute() : null;
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    * @return an Enumeration containing AttributeRef objects.
    */
   @Override
   public Enumeration getAttributes() {
      return ref != null ? ref.getAttributes() : Collections.emptyEnumeration();
   }

   /**
    * Determine if the entity is blank.
    * @return <code>true</code> if entity is <code>null</code> or blank.
    */
   @Override
   public boolean isEntityBlank() {
      return ref == null || ref.isEntityBlank();
   }

   /**
    * Get the name of the field.
    * @return the name of the field.
    */
   @Override
   public String getName() {
      return ref != null ? ref.getName() : "";
   }

   /**
    * Get the SQL expression for this aggregate.
    */
   public String getExpression(AggregateHelper helper) {
      String col = helper.getColumnString(ref);
      String col2 = helper.getColumnString(ref2);

      return formula.getExpression(col, col2, helper);
   }

   /**
    * Get an unique id for the formula that can be used to identify an
    * aggregate column.
    */
   public String getUID() {
      return formula.getUID(ref != null ? ref.getName() : "",
                            ref2 != null ? ref2.getName() : null);
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
    * @param ref the specified data ref.
    */
   @Override
   public void setDataRef(DataRef ref) {
      this.ref = ref;
      cname = null;
      chash = Integer.MIN_VALUE;
   }

   /**
    * Refresh the contained data ref.
    * @param cols the specified column selection.
    */
   public void refreshDataRef(ColumnSelection cols) {
      ref = refreshDataRef(cols, ref);
      cname = null;
      chash = Integer.MIN_VALUE;
      ref2 = refreshDataRef(cols, ref2);
   }

   /**
    * Find the data ref in column selection and return the ref from the column
    * selection.
    */
   private DataRef refreshDataRef(ColumnSelection cols, DataRef ref) {
      if(ref != null) {
         int index = cols.indexOfAttribute(ref);

         if(index >= 0) {
            ref = cols.getAttribute(index);
         }
      }

      return ref;
   }

   /**
    * Get the data type.
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType() {
      if(ref == null) {
         return XSchema.STRING;
      }

      String type = formula == null || AggregateFormula.NONE.equals(formula) ?
         null : formula.getDataType();

      if(type == null) {
         type = ref.getDataType();
      }
      else if(type.equals(XSchema.INTEGER) && percentage) {
         type = XSchema.DOUBLE;
      }

      return type;
   }

   /**
    * Write the attributes of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      if(percentage) {
         writer.print(" percentage=\"" + percentage + "\"");
      }

      if(percentageOption != XConstants.PERCENTAGE_NONE) {
         writer.print(" percentageOption=\"" + percentageOption + "\"");
      }

      if(composite) {
         writer.print(" composite=\"" + composite + "\"");
      }

      writer.print(" n=\"" + num + "\"");
   }

   /**
    * Write the attributes of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeAttributes2(DataOutputStream dos) {
      try {
         dos.writeBoolean(percentage);
         dos.writeInt(percentageOption);
         dos.writeInt(num);
      }
      catch(IOException e) {
         // ignore
      }
   }

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the XML element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      percentage = "true".equals(Tool.getAttribute(tag, "percentage"));
      String str = Tool.getAttribute(tag, "percentageOption");

      if(str != null) {
         percentageOption = Integer.parseInt(str);
      }

      if((str = Tool.getAttribute(tag, "n")) != null) {
         num = Integer.parseInt(str);
      }

      composite = "true".equalsIgnoreCase(Tool.getAttribute(tag, "composite"));
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      if(ref != null) {
         ref.writeXML(writer);
      }

      if(ref2 != null) {
         writer.println("<secondaryColumn>");
         ref2.writeXML(writer);
         writer.println("</secondaryColumn>");
      }

      writer.print("<formula>");
      writer.print("<![CDATA[" + AggregateFormula.getIdentifier(formula) + "]]>");
      writer.println("</formula>");
   }

   /**
    * Write the contents of this object.
    * @param dos the output stream to which to write the stream data.
    */
   @Override
   protected void writeContents2(DataOutputStream dos) {
      try {
         dos.writeUTF(ref.getClass().getName());
         ref.writeData(dos);

         dos.writeBoolean(ref2 == null);

         if(ref2 != null) {
            dos.writeUTF(ref2.getClass().getName());
            ref2.writeData(dos);
         }

         dos.writeUTF(AggregateFormula.getIdentifier(formula));
      }
      catch (IOException e) {
         // ignore
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      Element anode = Tool.getChildNodeByTagName(tag, "dataRef");

      if(anode != null) {
         ref = createDataRef(anode);
      }

      cname = null;
      chash = Integer.MIN_VALUE;
      Element node2 = Tool.getChildNodeByTagName(tag, "secondaryColumn");

      if(node2 != null) {
         node2 = Tool.getChildNodeByTagName(node2, "dataRef");
         ref2 = createDataRef(node2);
      }

      Element fnode = Tool.getChildNodeByTagName(tag, "formula");
      String ftext = Tool.getValue(fnode);
      formula = ftext == null || "null".equals(ftext) ? null : AggregateFormula.getFormula(ftext);

      if(formula != null && !this.formula.isTwoColumns()) {
         ref2 = null;
      }
   }

   /**
    * Check if shown as a percentage.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isPercentage() {
      return percentage;
   }

   /**
    * Set the percentage option.
    * @param percentage <tt>true</tt> if shown as a percentage, <tt>false</tt>
    * otherwise.
    */
   public void setPercentage(boolean percentage) {
      this.percentage = percentage;

      if(!percentage) {
         percentageOption = XConstants.PERCENTAGE_NONE;
      }
      else if(percentageOption != XConstants.PERCENTAGE_OF_GROUP &&
              percentageOption != XConstants.PERCENTAGE_OF_GRANDTOTAL)
      {
         percentageOption = XConstants.PERCENTAGE_OF_GRANDTOTAL;
      }
   }

   /**
    * Get the percentage option of the aggregate ref.
    * @return the percentage option of this reference.
    */
   @Override
   public int getPercentageOption() {
      return percentageOption;
   }

   /**
    * Set the percentage option of the aggregate ref.
    * @param percentageOption the percentage option of this reference.
    */
   @Override
   public void setPercentageOption(int percentageOption) {
      this.percentageOption = percentageOption;
      percentage = percentageOption != XConstants.PERCENTAGE_NONE;
   }

   /**
    * Get the formula.
    * @return the formula of the aggregate ref.
    */
   @Override
   public AggregateFormula getFormula() {
      return formula;
   }

   /**
    * Get the formula.
    * @return the formula name.
    */
   @Override
   public String getFormulaName() {
      if(formula == null) {
         return "none";
      }

      String name = formula.getFormulaName();

      if(percentageOption != XConstants.PERCENTAGE_NONE) {
         name = name + "<" + percentageOption + ">";
      }

      if(isNth(name)) {
         name = name + "(" + num + ")";
      }
      else if(ref2 != null) {
         name = name + "(" + ref2.getName() + ")";
      }

      return name;
   }

   /**
    * Set the formula name to the aggregate ref.
    * @param  name the specified formula.
    */
   @Override
   public void setFormulaName(String name) {
      if(name == null) {
         return;
      }

      String fname = null;

      int start = name.indexOf('<');
      int end = name.indexOf('>');

      if(start > 0) {
         fname = name.substring(0, start);
         String percent = name.substring(start + 1, end);
         percentageOption = Integer.parseInt(percent);
      }

      start = name.indexOf('(');
      end = name.indexOf(')');

      if(start > 0) {
         fname = fname == null ? name.substring(0, start) : fname;
         String secondf = name.substring(start + 1, end);

         if(isNth(fname)) {
            ref2 = null;
            num = Integer.parseInt(secondf);
         }
         else {
            int dotidx = secondf.lastIndexOf('.');
            String entity = dotidx == -1 ? null : secondf.substring(0, dotidx);
            String attr = dotidx == -1 ? secondf : secondf.substring(dotidx + 1);
            ref2 = new ColumnRef(new AttributeRef(entity, attr));
         }
      }

      if(fname != null) {
         formula = AggregateFormula.getFormula(fname);
      }
   }

   // if formula is nth or pth
   private static boolean isNth(String name) {
      return "NthLargest".equals(name) || "NthSmallest".equals(name) ||
         "NthMostFrequent".equals(name) || "PthPercentile".equals(name);
   }

   /**
    * Get the percentage type.
    *
    * @return the percentage type
    */
   @Override
   public int getPercentageType() {
      return percentageOption;
   }

   /**
    * Set the percentage type.
    *
    * @param type the specified percentage type
    */
   @Override
   public void setPercentageType(int type) {
      this.percentageOption = type;
   }

   /**
    * Get the value for N (nth) or P (pth).
    */
   public int getN() {
      return num;
   }

   /**
    * Set the value for N (nth) or P (pth).
    */
   public void setN(int num) {
      this.num = num;
   }

   /**
    * Set the formula to the aggregate ref.
    * @param formula the specified formula.
    */
   @Override
   public void setFormula(AggregateFormula formula) {
      this.formula = formula;

      if(formula != null && !this.formula.isTwoColumns()) {
         this.ref2 = null;
      }
   }

   /**
    * Test if the aggregate info is aggregated.
    * @return true if the aggregate info is aggregated, false otherwise.
    */
   public boolean isAggregated() {
      return !AggregateFormula.NONE.equals(formula);
   }

   /**
    * Get the formula secondary column.
    * @return secondary column.
    */
   @Override
   public DataRef getSecondaryColumn() {
      return ref2;
   }

   /**
    * Set the secondary column to be used in the formula.
    * @param ref formula secondary column.
    */
   @Override
   public void setSecondaryColumn(DataRef ref) {
      this.ref2 = ref;

      if(formula != null && !formula.isTwoColumns()) {
         this.ref2 = null;
      }
   }

   /**
    * Get the aggregate columns that could be used to calculate this formula.
    * @return an empty collection if this formula can not be composed from
    * other formulas. Otherwise returns a collection of the aggregate columns
    * that can be used to calculate this function.
    */
   public Collection<AggregateRef> getSubAggregates() {
      if(formula == null) {
         HashSet<AggregateRef> set = new HashSet<>();
         set.add(this);
         return set;
      }

      if(formula.isTwoColumns() && ref2 == null) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "secondary.column.notDefined", formula));
      }

      return formula.getSubAggregates(ref, ref2);
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean equalsAggregate(Object obj) {
      if(!(obj instanceof AggregateRef)) {
         return false;
      }

      AggregateRef aggregate2 = (AggregateRef) obj;

      return Tool.equals(aggregate2.ref, ref) && Tool.equals(aggregate2.formula, formula) &&
         Tool.equals(ref2, aggregate2.ref2);
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      writer.print("AGG");
      writer.print("[");
      ConditionUtil.printDataRefKey(ref, writer);
      ConditionUtil.printDataRefKey(ref2, writer);
      writer.print(percentage ? "T" : "F");
      writer.print(percentageOption);
      writer.print(num);
      writer.print(formula == null ? null : formula.getName());
      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof AggregateRef)) {
         return false;
      }

      AggregateRef aref = (AggregateRef) obj;

      if(!ConditionUtil.equalsDataRef(ref, aref.ref)) {
         return false;
      }

      if(!ConditionUtil.equalsDataRef(ref2, aref.ref2)) {
         return false;
      }

      if(percentage != aref.percentage) {
         return false;
      }

      if(percentageOption != aref.percentageOption) {
         return false;
      }

      if(!Tool.equals(formula, aref.formula)) {
         return false;
      }

      if(formula != null && formula.hasN() && num != aref.num) {
         return false;
      }

      return true;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   @Override
   public String toString() {
      AggregateFormula formula = getFormula();

      if(formula != null && formula.isTwoColumns() && ref2 != null) {
         return formula.getFormulaName() + "(" + ref.toString() + ", " +
            ref2.toString() + ")";
      }

      String name = formula == null ? "null" : formula.getFormulaName();
      return name + "(" + ref + ")";
   }

   /**
    * Get the view representation of this field.
    * @return the view representation of this field.
    */
   @Override
   public String toView() {
      AggregateFormula formula = getFormula();

      if(formula == null) {
         return null;
      }

      if(formula.isTwoColumns() && ref2 != null) {
         return formula.getFormulaName() + "(" + ref.toView() + ", " + ref2.toView() + ")";
      }

      if(formula.hasN()) {
         return formula.getFormulaName() + "(" + ref.toView() + ", " + num + ")";
      }

      return formula.getFormulaName() + "(" + ref.toView() + ")";
   }

   /**
    * Check if is one sub aggregate for composite.
    */
   public boolean isComposite() {
      return composite;
   }

   /**
    * Set whether is one sub aggregate for composite.
    */
   public void setComposite(boolean composite) {
      this.composite = composite;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         AggregateRef aggregate = (AggregateRef) super.clone();
         aggregate.ref = ref == null ? null : (DataRef) ref.clone();
         aggregate.ref2 = ref2 == null ? null : (DataRef) ref2.clone();
         return aggregate;
      }
      catch(Exception ex) {
         // ignore it for impossible
         return null;
      }
   }

   private DataRef ref;  // aggregate column
   private DataRef ref2; // secondary column
   private AggregateFormula formula; // aggregate formula
   private boolean percentage; // percent option
   private int percentageOption;
   private int num; // for nth or pth
   // write composite down, otherwise in distribute mv, the info will lost
   private boolean composite;
}

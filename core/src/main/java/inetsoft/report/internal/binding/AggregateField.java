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

import inetsoft.report.StyleConstants;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.security.Principal;

/**
 * AggregateField holds the summarization columns and formulas.
 * It is mostly used in SummaryAttr to store the summary columns for grouping
 * and crosstabs.
 *
 * @version 6.0 9/30/2003
 * @author mikec
 */
public class AggregateField extends BaseField implements DataRefWrapper,
   CalcAggregate, CalculateAggregate
{

   /**
    * Create a blank aggregate field.
    */
   public AggregateField() {
      this(new BaseField());
   }

   /**
    * Create an aggregate field with a field.
    */
   public AggregateField(Field fld) {
      this.field = fld;
   }

   private static int getPercentageType(String formula) {
      int start = formula == null ? -1 : formula.indexOf('<');

      if(start < 0) {
         return StyleConstants.PERCENTAGE_NONE;
      }

      assert formula != null;
      int end = formula.indexOf('>');
      String percent = formula.substring(start + 1, end);

      return Integer.parseInt(percent);
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
    * Set the sorting order of the field.
    *
    * @param order the specified sorting order defined in StyleConstants
    */
   @Override
   public void setOrder(int order) {
      field.setOrder(order);
   }

   /**
    * Get the sorting order of the field.
    *
    * @return the sorting order defined in StyleConstants
    */
   @Override
   public int getOrder() {
      return field.getOrder();
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
    * Set the summarization formula. The formula strings are defined as
    * constants in SummaryAttr.
    */
   public void setFormula(String f) {
      if(f == null) {
         formula = SummaryAttr.NONE_FORMULA;
      }
      else {
         formula = f;

         if(getPercentageType() == StyleConstants.PERCENTAGE_NONE) {
            // make sure redundant <0> is stripped from formula name
            setPercentageType(StyleConstants.PERCENTAGE_NONE);
         }
      }
   }

   /**
    * Get the formula name specified for this aggregate.
    * @return formula.
    */
   public String getFormula() {
      return formula;
   }

   /**
    * Get the formula.
    * @return the formula name.
    */
   @Override
   public String getFormulaName() {
      return getFormula();
   }

   /**
    * Set the formula name to the aggregate ref.
    * @param f name the specified formula.
    */
   @Override
   public void setFormulaName(String f) {
      setFormula(f);
   }

   /**
    * Get the formula function.
    */
   public String getFunction() {
      String form = this.formula;

      if(form == null) {
         return null;
      }

      int index = form.indexOf('<');

      if(index >= 0) {
         form = form.substring(0, index);
      }

      index = form.indexOf('(');

      if(index >= 0) {
         form = form.substring(0, index);
      }

      return form.trim();
   }

   /**
    * Get the source of this field.
    */
   @Override
   public String getSource() {
      String src = null;

      if(field instanceof BaseField) {
         src = ((BaseField) field).getSource();
      }

      return src;
   }

   /**
    * Get the secondary field if exists.
    *
    * @return the secondary field, null means not existing
    */
   public Field getSecondaryField() {
      String str = getSecondFormulaName();

      if(str == null) {
         return null;
      }

      int dotidx = str.lastIndexOf('.');
      String entity = dotidx == -1 ? null : str.substring(0, dotidx);
      String attr = dotidx == -1 ? str : str.substring(dotidx + 1);
      return new BaseField(entity, attr);
   }

   /**
    * Get second formula name.
    */
   public String getSecondFormulaName() {
      if(!containsSecondaryField()) {
         return null;
      }

      int sidx = formula.indexOf('(');
      int eidx = formula.indexOf(')');

      if(sidx < 0 || eidx < 0) {
         return null;
      }

      return formula.substring(sidx + 1, eidx);
   }

   /**
    * Set the secondary field.
    * @param field the specified field.
    */
   public void setSecondaryField(Field field) {
      if(formula == null) {
         return;
      }

      int start = formula.indexOf('(');

      if(start > 0) {
         int end = formula.indexOf(')');
         formula = formula.substring(0, start) + formula.substring(end + 1);
      }

      if(field != null) {
         formula = formula + "(" + field.getName() + ")";
      }
   }

   /**
    * Get the percentage type.
    *
    * @return the percentage type
    */
   @Override
   public int getPercentageType() {
      return getPercentageType(formula);
   }

   /**
    * Get the view representation of this field.
    * @return the view representation of this field.
    */
   public String getFullName() {
      return getFullName(true);
   }

   /**
    * Get the view representation of this field.
    * @return the view representation of this field.
    */
   public String getFullName(boolean applyCalc) {
      if(SummaryAttr.NONE_FORMULA.equals(formula) || "None".equals(formula)) {
         return super.getName();
      }

      String cprefix = "";

      if(applyCalc && calculator != null) {
         cprefix = calculator.getPrefixView();
         cprefix = cprefix == null ? "" : cprefix;
      }

      if(containsSecondaryField()) {
         String secondFld = getSecondaryField() == null ? "" :
            getSecondaryField().getName();

         return cprefix + getFormulaName0() + "(" +
            super.getName() + ", " + secondFld + ")";
      }

      String formula = getFormulaName0();
      int sidx = formula.indexOf('(');

      if(sidx < 0) {
         return cprefix + formula + "(" + super.getName() + ")";
      }

      String fname = formula.substring(0, sidx);
      return cprefix + fname + "(" + super.getName() + "," + formula.substring(sidx + 1);
   }

   /**
    * Get the view representation of this field.
    * @return the view representation of this field.
    */
   @Override
   public String toView() {
      return toView(true);
   }


   public String toView(boolean applyCalc) {
      if(SummaryAttr.NONE_FORMULA.equals(formula) || "None".equals(formula)) {
         return super.toView();
      }

      String cprefix = "";

      if(applyCalc && calculator != null) {
         cprefix = calculator.getPrefixView();
         cprefix = cprefix == null ? "" : cprefix;
         cprefix = Catalog.getCatalog().getString(cprefix);
      }

      if(containsSecondaryField()) {
         String secondFld = getSecondaryField() == null ? "" :
            getSecondaryField().toView();

         return cprefix + Catalog.getCatalog().getString(getFormulaName0()) + "(" +
            super.toView() + ", " + secondFld + ")";
      }

      String formula = getFormulaName0();
      int sidx = formula.indexOf('(');

      if(sidx < 0) {
         return cprefix + Catalog.getCatalog().getString(formula) + "(" + super.toView() + ")";
      }

      String fname = Catalog.getCatalog().getString(formula.substring(0, sidx));
      return cprefix + fname + "(" + super.toView() + "," + formula.substring(sidx + 1);
   }

   /**
    * Set the percentage type.
    *
    * @param type the specified percentage type
    */
   @Override
   public void setPercentageType(int type) {
      if(formula == null) {
         return;
      }

      int start = formula.indexOf('<');

      if(start > 0) {
         int end = formula.indexOf('>');
         formula = formula.substring(0, start) + formula.substring(end + 1);
      }

      if(type != StyleConstants.PERCENTAGE_NONE) {
         formula = formula + "<" + type + ">";
      }
   }

   /**
    * Check if contains the secondary field.
    *
    * @return true if is, false otherwise
    */
   private boolean containsSecondaryField() {
      return formula != null &&
         (formula.contains(SummaryAttr.CORRELATION_FORMULA) ||
         formula.contains(SummaryAttr.COVARIANCE_FORMULA) ||
         formula.contains(SummaryAttr.WEIGHTEDAVERAGE_FORMULA) ||
         formula.contains(SummaryAttr.FIRST_FORMULA) ||
         formula.contains(SummaryAttr.LAST_FORMULA));
   }

   @Override
   public Calculator getCalculator() {
      return calculator;
   }

   @Override
   public void setCalculator(Calculator calc) {
      this.calculator = calc;
   }

   /**
    * Create a copy of this object.
    *
    * @return a copy of this object
    */
   @Override
   public Object clone() {
      AggregateField aggregate = (AggregateField) super.cloneObject();
      aggregate.field = (Field) field.clone();

      if(calculator != null) {
         aggregate.calculator = (Calculator) calculator.clone();
      }

      return aggregate;
   }

   /**
    * Write the attributes of this object.
    *
    * @param writer the output stream to which to write the XML data
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      writer.print("formula=\"" + Tool.escape(formula) + "\" ");

      String sview = getSecondColView();

      if(sview != null) {
         writer.print("sview=\"" + sview + "\" ");
      }
   }

   /**
    * Get second column view.
    */
   private String getSecondColView() {
      StringBuilder buf = new StringBuilder();
      Field col2 = getSecondaryField();

      if(formula != null && col2 != null) {
         Principal user = ThreadContext.getContextPrincipal();
         Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);
         boolean model = col2.getEntity() != null &&
            col2.getEntity().length() > 0;

         if(model) {
            buf.append(catalog.getString(col2.getEntity()));
            buf.append('.');
            buf.append(catalog.getString(col2.getAttribute()));
         }
         else {
            String column = col2.getAttribute();
            int idx = column.lastIndexOf('.');

            if(idx > 0) {
               buf.append(catalog.getString(column.substring(0, idx)));
               buf.append('.');
               buf.append(catalog.getString(column.substring(idx + 1)));
            }
            else {
               buf.append(catalog.getString(column));
            }
         }
      }

      return buf.toString();
   }

   /**
    * Write the contents of this object.
    *
    * @param writer the output stream to which to write the XML data
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      field.writeXML(writer);

      if(calculator != null) {
         calculator.writeXML(writer);
      }
   }

   /**
    * Read in the attribute of this object from an XML tag.
    *
    * @param tag the XML Element representing this object
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      setFormula(Tool.getAttribute(tag, "formula"));
   }

   /**
    * Read in the contents of this object from an xml tag.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      NodeList list = Tool.getChildNodesByTagName(tag, "dataRef");

      if(list.getLength() == 0) {
         throw new Exception("No field tag found for AggregateField");
      }

      Element fld = (Element) list.item(0);
      String cls = Tool.getAttribute(fld, "class");

      if(cls == null) {
         throw new Exception("No class found in Field tag");
      }
      else {
         field = (Field) Class.forName(cls).newInstance();
         field.parseXML(fld);
      }

      Element node = Tool.getChildNodeByTagName(tag, "calc");
      calculator = null;

      if(node != null) {
         calculator = AbstractCalc.createCalc(node);
      }
   }

   /**
    * Create an embedded field.
    */
   @Override
   public Field createEmbeddedField() {
      AggregateField afld = (AggregateField) super.clone();

      if(field != null) {
         afld.field = field.createEmbeddedField();
      }

      return afld;
   }

   /**
    * Get formula name.
    */
   private String getFormulaName0() {
      String name = formula;

      if(containsSecondaryField()) {
         int sidx = name.indexOf('(');
         name = sidx > 0 ? name.substring(0, sidx) : name;
      }

      // percentage info..
      int perIdx = name.indexOf('<');

      if(perIdx > 0) {
         name = name.substring(0, perIdx);
      }

      return name;
   }

   /**
    * Compare two agg field is the same or not.
    */
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof AggregateField) || !super.equals(obj)) {
         return false;
      }

      AggregateField ref = (AggregateField) obj;

      return Tool.equals(formula, ref.getFormula()) &&
         Tool.equals(calculator, ref.getCalculator()) &&
         containsSecondaryField() == ref.containsSecondaryField() &&
         Tool.equals(getSecondaryField(), ref.getSecondaryField());
   }

   private Field field = null;
   private Calculator calculator = null;
   private String formula = SummaryAttr.SUM_FORMULA;
}

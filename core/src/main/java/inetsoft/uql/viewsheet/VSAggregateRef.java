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

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.graph.calc.DynamicCalc;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import org.mozilla.javascript.FunctionObject;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A VSAggregateRef object represents a dimension reference.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSAggregateRef extends AbstractDataRef implements ContentObject, XAggregateRef {
   /**
    * Constructor.
    */
   public VSAggregateRef() {
      super();

      refValue = new DynamicValue();
      ref2Value = new DynamicValue();
      formulaValue = new DynamicValue(XConstants.NONE_FORMULA, XSchema.STRING,
         AggregateFormula.getIdentifiers(true));
      percentageValue = new DynamicValue(
         null, XSchema.INTEGER,
         new int[] {XConstants.PERCENTAGE_NONE, XConstants.PERCENTAGE_OF_GROUP,
                    XConstants.PERCENTAGE_OF_GRANDTOTAL},
         new String[] {"", "group", "grand total"});
      nValue = new DynamicValue("1", XSchema.INTEGER);
   }

   /**
    * Get the type of the field.
    * @return the type of the field.
    */
   @Override
   public int getRefType() {
      if(!(ref instanceof ColumnRef)) {
         return this.refType;
      }

      return ref.getRefType();
   }

   /**
    * set the type of the field.
    * @param refType the type of the field.
    */
   public void setRefType(int refType) {
      this.refType = (byte) refType;
   }

   /**
    * Get caption.
    * @param caption of the dimension.
    */
   public void setCaption(String caption) {
      this.caption = caption;
   }

   /**
    * Set caption.
    * @return the caption of the dimension.
    */
   public String getCaption() {
      return caption;
   }

   /**
    * Check if the attribute is an expression.
    * @return <tt>true</tt> if is an expression, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpression() {
      return ref == null ? false : ref.isExpression();
   }

   /**
    * Get the attribute's parent entity.
    * @return the name of the entity.
    */
   @Override
   public String getEntity() {
      return ref == null ? null : ref.getEntity();
   }

   /**
    * Get the attribute's parent entity.
    * @return an Enumeration with the name of the entity.
    */
   @Override
   public Enumeration getEntities() {
      return ref == null ? new Vector().elements() : ref.getEntities();
   }

   /**
    * Get the referenced attribute.
    * @return the name of the attribute.
    */
   @Override
   public String getAttribute() {
      return ref == null ? "" : ref.getAttribute();
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    * @return an Enumeration containing AttributeRef objects.
    */
   @Override
   public Enumeration getAttributes() {
      return ref == null ? new Vector().elements() : ref.getAttributes();
   }

   @Override
   public boolean equals(Object obj) {
      return super.equals(obj) && equalsContent(obj);
   }

   /**
    * Determine if the entity is blank.
    * @return <code>true</code> if entity is <code>null</code> or blank.
    */
   @Override
   public boolean isEntityBlank() {
      return ref == null ? true : ref.isEntityBlank();
   }

   /**
    * Get the name of the field.
    * @return the name of the field.
    */
   @Override
   public String getName() {
      if(ref != null) {
         return ref.getName();
      }

      Object rval = refValue.getRValue();

      if(rval instanceof String) {
         return (String) rval;
      }
      else if(rval instanceof Object[]) {
         return Tool.arrayToString(rval);
      }

      return rval + "";
   }

   /**
    * Get the name for viewsheet only. Entity will be excluded.
    */
   public String getVSName() {
      if(ref == null) {
         if(refValue == null) {
            return "";
         }

         Object rval = refValue.getRValue();

         if(rval == null) {
            return "";
         }

         if(rval.getClass().isArray()) {
            if(Array.getLength(rval) > 0) {
               rval = Array.get(rval, 0);
            }

            return (rval == null) ? "" : rval.toString();
         }

         return rval.toString();
      }

      if(ref instanceof ColumnRef) {
         String caption = ((ColumnRef) ref).getCaption();

         if(caption != null && caption.length() > 0) {
            return caption;
         }

         String alias = ((ColumnRef) ref).getAlias();

         if(alias != null && alias.length() > 0) {
            return alias;
         }
      }

      return ref.getAttribute();
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

      AggregateFormula formula = getFormula();
      String type = (formula == null || !isAggregateEnabled()) ? null : formula.getDataType();
      int percent = getPercentageOption();

      if(type == null) {
         type = ref.getDataType();
      }
      else if(AssetUtil.isNumberType(type) && percent != XConstants.PERCENTAGE_NONE) {
         type = XSchema.DOUBLE;
      }

      return type;
   }

   /**
    * Get the original data type.
    */
   @Override
   public String getOriginalDataType() {
      return (ref != null) ? ref.getDataType() : (rodtype != null ? rodtype : XSchema.STRING);
   }

   /**
    * Set calculator.
    */
   @Override
   public void setCalculator(Calculator calculator) {
      this.calculator = calculator;
   }

   /**
    * Get calculator
    */
   @Override
   public Calculator getCalculator() {
      return calculator;
   }

   /**
    * Create CalcColumn.
    */
   @Override
   public CalcColumn createCalcColumn() {
      return calculator != null ? calculator.createCalcColumn(getFullName(false)) : null;
   }

   /**
    * Get the full name.
    */
   @Override
   public String getFullName() {
      return getFullName(true);
   }

   /**
    * Get the full name.
    * @param applyCalc flag to apply calculation for full name.
    */
   @Override
   public String getFullName(boolean applyCalc) {
      String name = getVSName();

      if(!isAggregateEnabled() || VSUtil.isAggregateCalc(ref)) {
         // if aggregation is disabled (boxplot), ignore calculation
         return applyCalc && calculator != null && aggregated ?
            calculator.getPrefix() + name : name;
      }

      return getFullName(name, applyCalc);
   }

   /**
    * Get the full name.
    */
   public String getFullName2() {
      // @by cehnw, bug1253515109951, because getFullName return a name
      // without formula when one of the aggregate ref formula is none,
      // please see updateAggregateStatus() in VSChartInfo.
      String name = getVSName();

      if(getFormula() == null || getFormula().equals(AggregateFormula.NONE)) {
         return calculator == null ? name : calculator.getPrefix() + name;
      }

      return getFullName(name, true);
   }

   /**
    * Get the full name append the formula part.
    */
   private String getFullName(String name, boolean applyCalc) {
      return getFullName(name, getFormula(), applyCalc ? calculator : null);
   }

   /**
    * Get the full name append the formula part.
    */
   public String getFullName(String name, AggregateFormula formula, Calculator calculator) {
      // do not localize the full name, for user may have different locale,
      // when it's localized, mv won't be hit for the fullname is not equal
      StringBuilder sb = new StringBuilder();
      String fname = formula == null ? null : formula.getFormulaName();

      if(calculator != null) {
         sb.append(calculator.getPrefix());
      }

      if(fname != null) {
         sb.append(fname);
         sb.append("(");
         sb.append(name);

         if(formula.isTwoColumns()) {
            sb.append(", ");
            sb.append(ref2Value.getRValue() != null ? ref2Value.getRValue() : ref2Value.getDValue());
         }

         if(formula.hasN()) {
            sb.append(", ");
            sb.append(getN());
         }

         sb.append(")");
      }
      else {
         sb.append(name);
      }

      return sb.toString();
   }

   /**
    * Create aggregate ref.
    */
   public AggregateRef createAggregateRef(ColumnSelection cols) {
      AggregateFormula formula = getFormula();
      formula = isAggregateEnabled() ? formula : AggregateFormula.NONE;
      DataRef col = cols == null ? getDataRef() :
         AssetUtil.getColumnRefFromAttribute(cols, getDataRef());

      if(col == null) {
         return null;
      }

      ColumnRef col2 = (ColumnRef) getSecondaryColumn();

      if(col2 != null) {
         col2 = cols == null ? col2 : AssetUtil.getColumnRefFromAttribute(cols, col2);
      }

      String name = getFullName(false);
      String vname = getVSName();

      if(!name.equals(vname)) {
         DataRef oref = col;
         AliasDataRef aref = new AliasDataRef(name, col);
         aref.setRefType(col.getRefType());
         col = new ColumnRef(aref);
         ((ColumnRef) col).setDataType(oref.getDataType());
      }

      AggregateRef aggr = new AggregateRef(col, col2, formula);
      boolean percent = getPercentageOption() != XConstants.PERCENTAGE_NONE;
      aggr.setPercentage(percent);
      aggr.setPercentageOption(getPercentageOption());
      aggr.setN(getN());

      return aggr;
   }

   /**
    * Check if the column is a variable.
    */
   public boolean isVariable() {
      return VSUtil.isVariableValue(refValue.getDValue());
   }

   /**
    * Check if the column is a variable.
    */
   public boolean isScript() {
      return VSUtil.isScriptValue(refValue.getDValue());
   }

   /**
    * Get the column value.
    * @return the column value.
    */
   public String getColumnValue() {
      return this.refValue.getDValue();
   }

   public String getFullNameByDVariable() {
      String name = getFullName();

      if(isVariable() && refValue != null && name != null) {
         return name.replace("" + refValue.getRValue(), refValue.getDValue());
      }
      else {
         return name;
      }
   }

   /**
    * Set the column value.
    * @param value the column value.
    */
   public void setColumnValue(String value) {
      this.refValue.setDValue(value);
   }

   /**
    * Get the aggregate column of this reference.
    * @return the aggregate column of this reference.
    */
   @Override
   public DataRef getDataRef() {
      return ref;
   }

   /**
    * Set the aggregate column of this reference.
    */
   @Override
   public void setDataRef(DataRef ref) {
      this.ref = ref;
      cname = null;
      chash = Integer.MIN_VALUE;
   }

   /**
    * Get the formula.
    * @return the formula of the aggregate ref.
    */
   @Override
   public AggregateFormula getFormula() {
      Object value = formulaValue.getRuntimeValue(true);
      String text = value.toString();
      return AggregateFormula.getFormula(text);
   }

   /**
    * Set the formula.
    */
   @Override
   public void setFormula(AggregateFormula formula) {
      setFormulaValue(formula.getFormulaName());
   }

   /**
    * Get the formula value.
    * @return the formula value of the aggregate ref.
    */
   public String getFormulaValue() {
      return formulaValue.getDValue();
   }

   /**
    * Set the formula value to the aggregate ref.
    * @param formula value the specified formula.
    */
   public void setFormulaValue(String formula) {
      this.formulaValue.setDValue(formula);
      this.noneFormula = null;

      if(!VSUtil.isDynamicValue(formula)) {
         this.noneFormula = isNoneFormula(formula);
      }
   }

   /**
    * Get the percentage option of this reference.
    * @return the percentage option of this reference.
    */
   @Override
   public int getPercentageOption() {
      Integer value = (Integer) percentageValue.getRuntimeValue(true);
      return value.intValue();
   }

   /**
    * Set the percentage option of this reference.
    * @param percentage the percentage option of this reference.
    */
   @Override
   public void setPercentageOption(int percentage) {
      this.percentageValue.setRValue(Integer.valueOf(percentage));
   }

   /**
    * Get the percentage option value of this reference.
    * @return the percentage option value of this reference.
    */
   public String getPercentageOptionValue() {
      return percentageValue.getDValue();
   }

   /**
    * Set the percentage option of this reference.
    * @param percentage the percentage option of this reference.
    */
   public void setPercentageOptionValue(String percentage) {
      this.percentageValue.setDValue(percentage);
   }

   /**
    * Get the runtime N value.
    */
   public int getN() {
      Integer n = (Integer) nValue.getRuntimeValue(true);
      return n == null ? 1 : n;
   }

   /**
    * Set the runtime N value.
    */
   public void setN(int n) {
      this.nValue.setRValue(n);
   }

   /**
    * Get the design time N value.
    */
   public String getNValue() {
      return this.nValue.getDValue();
   }

   /**
    * Set the design time N value.
    */
   public void setNValue(String n) {
      this.nValue.setDValue(n);
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
   }

   /**
    * Get the formula secondary column value.
    * @return secondary column value.
    */
   public String getSecondaryColumnValue() {
      return ref2Value.getDValue();
   }

   /**
    * Set the secondary column value to be used in the formula.
    * @param ref2 formula secondary column value.
    */
   public void setSecondaryColumnValue(String ref2) {
      this.ref2Value.setDValue(ref2);
   }

   /**
    * Get the dynamic values.
    * @return the dynamic values.
    */
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(refValue);
      list.add(ref2Value);
      list.add(formulaValue);
      list.add(percentageValue);
      list.add(nValue);

      if(calculator instanceof DynamicCalc) {
         list.addAll(((DynamicCalc) calculator).getDynamicValues());
      }

      return list;
   }

   /**
    * Get the dynamic values.
    * @return the dynamic values.
    */
   public List<DynamicValue> getHyperlinkDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();

      if(this instanceof HyperlinkRef) {
         Hyperlink hyperlink = ((HyperlinkRef)this).getHyperlink();

         if(hyperlink != null && VSUtil.isScriptValue(hyperlink.getLinkValue())) {
            list.add(hyperlink.getDLink());
         }
      }

      return list;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      VSUtil.renameDynamicValueDepended(oname, nname, refValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, ref2Value, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, formulaValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, percentageValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, nValue, vs);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "VSAggregateRef" + addr() + "[" + refValue + "," + ref2Value + "," +
         "," + formulaValue + "," + percentageValue + "," + nValue + "," +
         refType + "," + calculator + "]";
   }

   /**
    * Get the view representation of this field.
    * @return the view representation of this field.
    */
   @Override
   public String toView() {
      return toView(true);
   }

   /**
    * Get the view representation of this field.
    * @param all flag to show calculator prefix for view.
    * @return the view representation of this field.
    */
   @Override
   public String toView(boolean all) {
      Catalog catalog = Catalog.getCatalog();
      String cprefix = "";

      if(all && aggregated) {
         cprefix = calculator == null ? "" : calculator.getPrefixView();
         cprefix = cprefix == null ? "" : cprefix;
      }

      AggregateFormula formula = getFormula();

      if(formula == null || AggregateFormula.NONE.equals(formula) || !isAggregateEnabled()) {
         return cprefix + refValue.getDValue();
      }

      if(ref == null) {
         // fix Bug #3775, display dvalue for variable, because rvalue maybe an
         // array, and only one value in the array is the value for current ref.
         // String val = isVariable() ? refValue.getRValue() + "" :
         //   refValue.getDValue() ;
         String val = refValue.getDValue();
         String view = cprefix + catalog.getString(formula.getFormulaName()) + "(" + val;

         if(formula.isTwoColumns() && ref2Value.getDValue() != null &&
            ref2Value.getDValue().length() > 0)
         {
           view += ", " + ref2Value.getDValue();
         }

         if(formula.hasN() && nValue.getDValue() != null) {
            view += ", " + getN();
         }

         return view + ")";
      }

      DataRef dref = (DataRef) ref.clone();

      if(dref instanceof ColumnRef) {
         ColumnRef col2 = (ColumnRef) dref;
         DataRef bref = col2.getDataRef();

         if(bref instanceof AliasDataRef) {
            DataRef aref = ((AliasDataRef) bref).getDataRef();

            if(formula != null && aref != null) {
               bref = new AttributeRef(aref.getName());
               dref = (ColumnRef) col2.clone();
               ((ColumnRef) dref).setDataRef(bref);
            }
         }
      }

      if(formula.isTwoColumns()) {
         String str = cprefix + catalog.getString(formula.getFormulaName()) +
            "(" + dref.toView() + ", ";

         if(ref2 != null) {
            str += ref2.toView();
         }
         else {
            str += ref2Value.getRValue();
         }

         return str + ")";
      }

      if(formula.hasN()) {
         return cprefix + catalog.getString(formula.getFormulaName()) +
            "(" + dref.toView() + ", " + getN() + ")";
      }

      return cprefix + catalog.getString(formula.getFormulaName()) + "(" + dref.toView() + ")";
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      String refTypeStr = Tool.getAttribute(elem, "refType");

      if(refTypeStr != null && refTypeStr.length() > 0) {
         this.refType = (byte) Integer.parseInt(refTypeStr);
      }

      String ridStr = Tool.getAttribute(elem, "runtimeID");

      if(ridStr != null && ridStr.length() > 0) {
         setRuntimeID((byte) Integer.parseInt(ridStr));
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      refValue.setDValue(Tool.getChildValueByTagName(elem, "refValue"));
      ref2Value.setDValue(Tool.getChildValueByTagName(elem, "secondaryValue"));
      setFormulaValue(Tool.getChildValueByTagName(elem, "formulaValue"));
      percentageValue.setDValue(Tool.getChildValueByTagName(elem, "percentageValue"));
      nValue.setDValue(Tool.getChildValueByTagName(elem, "nValue"));
      caption = Tool.getChildValueByTagName(elem, "caption");

      Element node = Tool.getChildNodeByTagName(elem, "ref");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         ref = AbstractDataRef.createDataRef(node);
         cname = null;
         chash = Integer.MIN_VALUE;
      }

      node = Tool.getChildNodeByTagName(elem, "calculator");
      calculator = null;

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         calculator = AbstractCalc.createCalc(node);
      }

      String comboTypeString = Tool.getChildValueByTagName(elem, "comboType");

      if(comboTypeString != null) {
         comboType = ComboMode.values()[Integer.parseInt(comboTypeString)];
      }
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" refType=\"" + refType + "\" ");

      if(runtimeID != -1) {
         writer.print(" runtimeID=\"" + runtimeID + "\" ");
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(refValue.getDValue() != null) {
         writer.print("<refValue>");
         writer.print("<![CDATA[" + refValue.getDValue() + "]]>");
         writer.println("</refValue>");
      }

      if(refValue.getRuntimeValue(true) != null) {
         writer.print("<refRValue>");
         writer.print("<![CDATA[" + refValue.getRuntimeValue(true) + "]]>");
         writer.println("</refRValue>");
      }

      if(ref2Value.getDValue() != null) {
         writer.print("<secondaryValue>");
         writer.print("<![CDATA[" + ref2Value.getDValue() + "]]>");
         writer.println("</secondaryValue>");
      }

      if(ref2Value.getRuntimeValue(true) != null) {
         writer.print("<secondaryRValue>");
         writer.print("<![CDATA[" + ref2Value.getRuntimeValue(true) + "]]>");
         writer.println("</secondaryRValue>");
      }

      if(formulaValue.getDValue() != null) {
         writer.print("<formulaValue>");
         writer.print("<![CDATA[" + formulaValue.getDValue() + "]]>");
         writer.println("</formulaValue>");
      }

      if(formulaValue.getRuntimeValue(true) != null) {
         writer.print("<formulaRValue>");
         writer.print("<![CDATA[" + formulaValue.getRuntimeValue(true) +
                      "]]>");
         writer.println("</formulaRValue>");
      }

      if(percentageValue.getDValue() != null) {
         writer.print("<percentageValue>");
         writer.print("<![CDATA[" + percentageValue.getDValue() + "]]>");
         writer.println("</percentageValue>");
      }

      if(percentageValue.getRuntimeValue(true) != null) {
         writer.print("<percentageRValue>");
         writer.print("<![CDATA[" + percentageValue.getRuntimeValue(true) +
                      "]]>");
         writer.println("</percentageRValue>");
      }

      if(nValue.getDValue() != null) {
         writer.print("<nValue>");
         writer.print("<![CDATA[" + nValue.getDValue() + "]]>");
         writer.println("</nValue>");
      }

      if(getOriginalDataType() != null) {
         writer.print("<dtype>");
         writer.print("<![CDATA[" + getOriginalDataType() + "]]>");
         writer.println("</dtype>");
      }

      if(caption != null) {
         writer.print("<caption>");
         writer.print("<![CDATA[" + caption + "]]>");
         writer.println("</caption>");
      }

      if(getDataType() != null) {
         writer.print("<aggregateDtype>");
         writer.print("<![CDATA[" + getDataType() + "]]>");
         writer.println("</aggregateDtype>");
      }

      if(ref != null) {
         writer.print("<ref>");
         DataRef wref = ref;

         // as AliasDataRef is unsupported in flex, here convert it
         // to supported data ref - AttributeRef
         if(ref instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) ref;
            DataRef bref = col.getDataRef();

            if(bref instanceof AliasDataRef) {
               AliasDataRef aref = (AliasDataRef) bref;
               bref = new AttributeRef(aref.getDataRef().getAttribute());
               col = (ColumnRef) col.clone();
               col.setDataRef(bref);
               wref = col;
            }
         }

         wref.writeXML(writer);
         writer.println("</ref>");
      }

      if(calculator != null) {
         writer.print("<calculator>");
         calculator.writeXML(writer);
         writer.print("</calculator>");
      }

      String fullName = getFullName();

      if(fullName != null && !fullName.equals("")) {
         writer.print("<fullName>");
         writer.print("<![CDATA[" + fullName + "]]>");
         writer.println("</fullName>");
      }

      String oriFullName = getFullName(false);

      if(oriFullName != null && !oriFullName.equals("")) {
         writer.print("<oriFullName>");
         writer.print("<![CDATA[" + oriFullName + "]]>");
         writer.println("</oriFullName>");
      }

      // write original view for viewsheet ranking and sor by column
      String oriView = toView(false);

      if(oriView != null && !oriView.equals(toView())) {
         writer.print("<oriView>");
         writer.print("<![CDATA[" + oriView + "]]>");
         writer.println("</oriView>");
      }

      if(comboType != null ) {
         writer.print("<comboType>");
         writer.print("<![CDATA[" + comboType.ordinal() + "]]>");
         writer.println("</comboType>");
      }
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      throw new Exception("Unsupported method called!");
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof VSAggregateRef)) {
         return false;
      }

      VSAggregateRef vref = (VSAggregateRef) obj;

      return Tool.equals(refValue, vref.refValue) &&
         Tool.equals(ref2Value, vref.ref2Value) &&
         Tool.equals(formulaValue, vref.formulaValue) &&
         Tool.equals(percentageValue, vref.percentageValue) &&
         Tool.equals(nValue, vref.nValue) &&
         refType == vref.refType && Tool.equals(calculator, vref.calculator);
   }

   /**
    * Check if the aggregate ref is aggregated. It's aggregated if the aggregate
    * formula is not none, and the aggregate flag is on.
    */
   @Override
   public boolean isAggregateEnabled() {
      if(!aggregated) {
         return false;
      }

      if(VSUtil.isAggregateCalc(ref)) {
         return true;
      }

      if(noneFormula != null) {
         return noneFormula;
      }

      return isNoneFormula(formulaValue.getRuntimeValue(true).toString());
   }

   private boolean isNoneFormula(String formula) {
      if(formula == null) {
         return false;
      }

      String text = formula.toLowerCase();
      return text.length() > 0 &&
         !text.startsWith(AggregateFormula.NONE.getFormulaName()) &&
         !text.startsWith("null");
   }

   @Override
   public boolean isAggregated() {
      return this.aggregated;
   }

   @Override
   public void setAggregated(boolean aggregated) {
      this.aggregated = aggregated;
   }

   /**
    * Set whether is to apply alias.
    */
   public void setApplyAlias(boolean aalias) {
      this.aalias = aalias;
   }

   /**
    * Check if is to apply alias.
    */
   public boolean isApplyAlias() {
      return aalias;
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   public List<DataRef> update(Viewsheet vs, ColumnSelection columns) {
      List<DataRef> refs = new ArrayList<>();
      Object[] arr = toArray(refValue.getRuntimeValue(false));
      Object[] arr2 = toArray(ref2Value.getRuntimeValue(false));
      Object[] farr = toArray(formulaValue.getRuntimeValue(false));
      Object[] parr = toArray(percentageValue.getRuntimeValue(false));
      Object[] narr = toArray(nValue.getRuntimeValue(false));

      // support an empty vs aggregate ref, e.g. change aesthetic ref
      // from cost to none by a combo box
      /*
      if(arr.length == 0) {
         throw new ColumnNotFoundException(Catalog.getCatalog().getString
            ("common.viewsheet.aggrInvalid", refValue));
      }
      */
      final boolean aggregated = isAggregateEnabled();
      final boolean variable = isVariable();

      if(this.isDynamicBinding()) {
         setDataRef(null);
      }

      for(int i = 0; i < arr.length; i++) {
         if((variable || isScript()) && arr[i] == null) {
            continue;
         }

         String rtext = Tool.toString(arr[i]);
         DataRef ref = columns.getAttribute(rtext, getEntity());
         DataRef ref2 = null;

         // fix Bug #41264, CalculateRef do not support formula.
         if(isApplyAlias() && ref instanceof CalculateRef) {
            this.formulaValue.setRValue(AggregateFormula.NONE);
            this.noneFormula = isNoneFormula(AggregateFormula.NONE.getName());
         }

         if(ref == null && !rtext.equals("null") && !rtext.equals("")) {
            boolean shouldThrow = true;

            // parse out the aggregate and column name for variable bindings so we can
            // change the formula type dynamically
            if(variable && !aggregated && rtext.contains("(")) {
               final int open = rtext.indexOf('(');
               final boolean isAggText = rtext.endsWith(")");

               if(open > 0 && isAggText) {
                  final String agg = rtext.substring(0, open);
                  final AggregateFormula formula = AggregateFormula.getFormula(agg);

                  if(formula != null) {
                     farr[i] = agg;
                     final String name = rtext.substring(open + 1, rtext.length() - 1);
                     arr[i] = name;
                     ref = columns.getAttribute(name, getEntity());
                     shouldThrow = false;
                  }
               }
            }

            if(shouldThrow) {
               String message = rtext;

               if(arr[i] != null && arr[i] instanceof FunctionObject) {
                  message = arr[i].getClass() + ": " + ((FunctionObject) arr[i]).getFunctionName();
               }

               throw new ColumnNotFoundException(Catalog.getCatalog().getString
                  ("common.viewsheet.aggrInvalid", message + " in " +
                     columns.stream().map(a -> a.getName()).collect(Collectors.joining(","))));
            }
         }

         if(arr2.length > 0) {
            ref2 = columns.getAttribute(Tool.toString(arr2[i % arr2.length]));
         }

         // When the ref's runtime value is vingues, use its own value to create
         // alias ref.
         VSAggregateRef aref = (VSAggregateRef) this.clone();
         aref.refValue.setRValue(arr[i]);

         if(ref instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) ref;

            if(col.getDataRef() instanceof AttributeRef) {
               AttributeRef attr = (AttributeRef) col.getDataRef();
               setCaption(attr.getCaption());
            }
         }

         // in case CalculateRef is not set in the aggregate ref
         // (perhaps from an early problem), fix it here. (62361)
         if(ref instanceof CalculateRef && getDataRef() instanceof ColumnRef ||
            !this.isDynamicBinding() && getDataRef() == null)
         {
            setDataRef(ref);
         }

         if(ref != null) {
            //aalias = false;
            // if apply alias, when required, we need to create AliasDataRef
            if(aalias) {
               String name = aref.getFullName(false);
               String vname = aref.getVSName();

               if(!name.equals(vname)) {
                  if(ref instanceof CalculateRef) {
                     CalculateRef cref = ((CalculateRef) ref);
                     ExpressionRef eref = (ExpressionRef) cref.getDataRef();
                     eref.setOnAggregate(!cref.isBaseOnDetail());
                  }

                  DataRef oref = ref;
                  AliasDataRef aliasRef = new AliasDataRef(name, ref);
                  aliasRef.setRefType(ref.getRefType());
                  ref = new ColumnRef(aliasRef);
                  ((ColumnRef) ref).setDataType(oref.getDataType());
               }
            }

            aref.setDataRef(ref);

            // because the ref is runtime option, for design time, it always
            // null, so getOrigianlDataType or design time aggregate ref will
            // always String, here add an option to maintain it correct
            // see bug1349942206039
            this.rodtype = aref.getOriginalDataType();
         }

         aref.setCaption(caption);
         aref.setSecondaryColumn(ref2);

         if(farr.length > 0) {
            aref.formulaValue.setRValue(farr[i % farr.length]);
         }

         if(parr.length > 0) {
            aref.percentageValue.setRValue(parr[i % parr.length]);
         }

         if(narr.length > 0) {
            aref.nValue.setRValue(narr[i % narr.length]);
         }

         refs.add(aref);
      }

      return refs;
   }

   public void setOriginalDataType(String datatype) {
      this.rodtype = datatype;
   }

   /**
    * Get an array that contains the object or if the object is an array.
    */
   static Object[] toArray(Object robj) {
      if(robj == null) {
         return new Object[] {robj};
      }

      if(!(robj instanceof Object[])) {
         robj = new Object[] {robj};
      }

      return (Object[]) robj;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      VSAggregateRef agg = (VSAggregateRef) super.clone();
      agg.refValue = (DynamicValue) refValue.clone();
      agg.ref2Value = (DynamicValue) ref2Value.clone();
      agg.formulaValue = (DynamicValue) formulaValue.clone();
      agg.percentageValue = (DynamicValue) percentageValue.clone();
      agg.nValue = (DynamicValue) nValue.clone();
      agg.calculator = calculator == null ? null : (Calculator) calculator.clone();
      return agg;
   }

   /**
    * Set runtime id.
    */
   public void setRuntimeID(int rid) {
      this.runtimeID = (byte) rid;
   }

   /**
    * Get runtime id.
    */
   public int getRuntimeID() {
      return runtimeID;
   }

   /**
    * Get combo type.
    */
   public ComboMode getComboType() {
      return comboType;
   }

   /**
    * Set combo type.
    */
   public void setComboType(ComboMode comboType) {
      this.comboType = comboType;
   }

   /**
    * Check if contains dynamic value.
    */
   public boolean containsDynamic() {
      return VSUtil.isDynamic(refValue) || VSUtil.isDynamic(ref2Value) ||
         VSUtil.isDynamic(formulaValue) || VSUtil.isDynamic(percentageValue) ||
         VSUtil.isDynamic(nValue);
   }

   /**
    * Check if dynamic binding.
    */
   public boolean isDynamicBinding() {
      return VSUtil.isDynamic(refValue) || VSUtil.isDynamic(ref2Value) ||
         VSUtil.isDynamic(formulaValue);
   }

   private DynamicValue refValue;
   private DynamicValue ref2Value;
   private DynamicValue formulaValue;
   private DynamicValue percentageValue;
   private DynamicValue nValue;
   private String caption;
   // runtime
   private DataRef ref;
   private DataRef ref2;
   private boolean aggregated = true; // whether aggregation applied
   private boolean aalias = false; // apply alias or not when update columns
   private String rodtype; // runtime original data type

   private transient Boolean noneFormula; // optimization, cached none formula

   private byte refType = NONE;
   private Calculator calculator = null;
   private int runtimeID = (byte) -1;
   private ComboMode comboType = ComboMode.VALUE;
}

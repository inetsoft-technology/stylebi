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

import inetsoft.uql.AbstractCondition;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.XConditionInfo;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Set;

/**
 * Default Date range assembly implements <tt>DateRangeAssembly</tt>
 * contains a predefined <tt>DateRange</tt>.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class DefaultDateRangeAssembly extends AbstractWSAssembly implements DateRangeAssembly {
   /**
    * Constructor.
    */
   public DefaultDateRangeAssembly() {
      super();

      cinfo = new XConditionInfo();
   }

   /**
    * Constructor.
    */
   public DefaultDateRangeAssembly(Worksheet ws, String name) {
      super(ws, name);

      cinfo = new XConditionInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Worksheet.DATE_RANGE_ASSET;
   }

   /**
    * Get the minimum size.
    * @return the minimum size of the assembly.
    */
   @Override
   public Dimension getMinimumSize() {
      return MIN_SIZE;
   }

   /**
    * Get the date range.
    * @return the date range of the date range assembly.
    */
   @Override
   public DateCondition getDateRange() {
      return range;
   }

   /**
    * Set the date range.
    * @param range the specified date range.
    */
   @Override
   public void setDateRange(DateCondition range) {
      this.range = range;
   }

   /**
    * Check if type is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isTypeChangeable() {
      return true;
   }

   /*
    * Get the range value data type.
    * @return the data type of this range. The type will be one of the
    * constants defined in {@link inetsoft.uql.schema.XSchema}.
    */
   @Override
   public String getType() {
      return cinfo.getType();
   }

   /**
    * Set the range value data type.
    * @param type the data type of the range. Must be one of the data type
    * constants defined in {@link inetsoft.uql.schema.XSchema}.
    */
   @Override
   public void setType(String type) {
      cinfo.setType(type);
   }

   /**
    * Check if operation is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isOperationChangeable() {
      return true;
   }

   /**
    * Get the comparison operation of this range.
    * @return one of the operation constant, one of the operation constants
    * defined in this class.
    * @see #EQUAL_TO
    * @see #ONE_OF
    * @see #LESS_THAN
    * @see #GREATER_THAN
    * @see #BETWEEN
    * @see #STARTING_WITH
    * @see #LIKE
    * @see #CONTAINS
    * @see #NULL
    * @see #TOP_N
    * @see #DATE_IN
    */
   @Override
   public int getOperation() {
      return cinfo.getOperation();
   }

   /**
    * Set the comparison operation of this range.
    * @param op one of the operation constants defined in this class.
    */
   @Override
   public void setOperation(int op) {
      cinfo.setOperation(op);
   }

   /**
    * Check if equal is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEqualChangeable() {
      return false;
   }

   /**
    * Determine whether equivalence will be tested in addition to the
    * defined comparison operation.
    * @return <code>true</code> if equivalence will be tested
    */
   @Override
   public boolean isEqual() {
      return cinfo.isEqual();
   }

   /**
    * Set the equal to option when the comparison operation is
    * <code>LESS_THAN</code> or <code>GREATER_THAN</code>, i.e.
    * <code><i>a</i> &gt;= <i>b</i></code>.
    * @param equal <code>true</code> if equivalence should be tested
    */
   @Override
   public void setEqual(boolean equal) {
      cinfo.setEqual(equal);
   }

   /**
    * Check if negated is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNegatedChangeable() {
      return true;
   }

   /**
    * Set whether this range result should be negated. A negated range
    * will evaluate as <code>true</code> if the if its range definition(s)
    * are <b>not</b> met.
    * @return <code>true</code> if this range is negated.
    */
   @Override
   public boolean isNegated() {
      return cinfo.isNegated();
   }

   /**
    * Determine whether this range result should be negated. A negated
    * range will evaluate as <code>true</code> if the if its range
    * definition(s) are <b>not</b> met.
    * @param negated <code>true</code> if this range is negated.
    */
   @Override
   public void setNegated(boolean negated) {
      cinfo.setNegated(negated);
   }

   /**
    * Update the assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean update() {
      return super.update();
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariable(VariableTable vars) {
      replaceVariables(vars);
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      range.replaceVariable(vars);
   }

   /**
    * Get all variables in the range value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return range.getAllVariables();
   }

   /**
    * Evaluate this range against the specified value object.
    * @param value the value object this range should be compared with.
    * @return <code>true</code> if the value object meets this range.
    */
   @Override
   public boolean evaluate(Object value) {
      boolean result = range.evaluate(value);
      return isNegated() ? !result : result;
   }

   /**
    * Check if the range is a valid range.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      super.checkValidity(checkCrossJoins);

      if(!range.isValid()) {
         throw new MessageException(Catalog.getCatalog().
            getString("common.InvalidDateRange", getName()));
      }
   }

   /**
    * Check if the condition is a valid condition.
    * @return true if is valid, false otherwise.
    */
   @Override
   public boolean isValid() {
      return range.isValid();
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      // do nothing
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   public void parseAttributes(Element elem) {
      super.parseAttributes(elem);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      range.writeXML(writer);

      writer.println("<xConditionInfo>");
      cinfo.writeXML(writer);
      writer.println("</xConditionInfo>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   public void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element dnode = Tool.getChildNodeByTagName(elem, "xCondition");
      range = (DateCondition) AbstractCondition.createXCondition(dnode);

      Element cnode = Tool.getChildNodeByTagName(elem, "xConditionInfo");
      cnode = Tool.getFirstChildNode(cnode);
      cinfo.parseXML(cnode);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder();
      Catalog catalog = Catalog.getCatalog();
      sb.append(" [");

      if(isNegated()) {
         sb.append(catalog.getString("is not"));
      }
      else {
         sb.append(catalog.getString("is"));
      }

      sb.append("]");
      sb.append(" [");
      sb.append(catalog.getString("in range"));
      sb.append("]");
      sb.append(" [");
      sb.append(getName());
      sb.append("]");

      return sb.toString();
   }

   /**
    * Check if equals another object in content.
    */
   @Override
   public boolean equalsContent(Object obj) {
      return equals(obj);
   }

   /**
    * Print the key to identify this content object. If the keys of two
    * content objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      writer.print("DR[");
      writer.print("[");
      cinfo.printKey(writer);

      if(range != null) {
         writer.print(",");
         range.printKey(writer);
      }

      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof DefaultDateRangeAssembly)) {
         return false;
      }

      DefaultDateRangeAssembly assembly2 = (DefaultDateRangeAssembly) obj;

      return getName().equals(assembly2.getName()) &&
         Tool.equals(cinfo, assembly2.cinfo) &&
         Tool.equals(range, assembly2.range);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), range, cinfo);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public DefaultDateRangeAssembly clone() {
      try {
         DefaultDateRangeAssembly assembly =
            (DefaultDateRangeAssembly) super.clone();
         assembly.range = range.clone();
         assembly.cinfo = (XConditionInfo) cinfo.clone();
         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private static final Dimension MIN_SIZE = new Dimension(AssetUtil.defw, 2 * AssetUtil.defh);

   private DateCondition range;
   private XConditionInfo cinfo;

   private static final Logger LOG = LoggerFactory.getLogger(DefaultDateRangeAssembly.class);
}

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

import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.log.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;

/**
 * MirrorDateRangeAssembly, the mirror of a date range assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MirrorDateRangeAssembly extends AbstractWSAssembly implements DateRangeAssembly, MirrorAssembly {
   /**
    * Constructor.
    */
   public MirrorDateRangeAssembly() {
      super();

      cinfo = new XConditionInfo();
   }

   /**
    * Constructor.
    */
   public MirrorDateRangeAssembly(Worksheet ws, String name, AssetEntry entry,
                                  boolean outer, WSAssembly assembly) {
      super(ws, name);

      MirrorAssemblyImpl impl = new MirrorAssemblyImpl(entry, outer, assembly);
      impl.setWorksheet(ws);

      setImpl(impl);

      cinfo = new XConditionInfo();
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new MirrorDateRangeAssemblyInfo();
   }

   /**
    * Get the mirror assembly impl.
    * @return the mirror assembly impl.
    */
   private MirrorAssemblyImpl getImpl() {
      return ((MirrorDateRangeAssemblyInfo) getInfo()).getImpl();
   }

   /**
    * Set the mirror assembly impl.
    * @param impl the specified mirror assembly impl.
    */
   private void setImpl(MirrorAssemblyImpl impl) {
      ((MirrorDateRangeAssemblyInfo) getInfo()).setImpl(impl);
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
    * Set the worksheet.
    * @param ws the specified worksheet.
    */
   @Override
   public void setWorksheet(Worksheet ws) {
      super.setWorksheet(ws);
      getImpl().setWorksheet(ws);
   }

   /**
    * Get the minimum size.
    * @return the minimum size of the assembly.
    */
   @Override
   public Dimension getMinimumSize() {
      DateRangeAssembly assembly = getDateRangeAssembly();

      if(assembly == null) {
         return new Dimension(AssetUtil.defw, 2 * AssetUtil.defh);
      }

      return assembly.getMinimumSize();
   }

   /**
    * Get the worksheet entry.
    * @return the worksheet entry of the mirror assembly.
    */
   @Override
   public AssetEntry getEntry() {
      return getImpl().getEntry();
   }

   /**
    * Set the worksheet entry.
    * @param entry the specified worksheet entry.
    */
   @Override
   public void setEntry(AssetEntry entry) {
      getImpl().setEntry(entry);
   }

   /**
    * Get the assembly name.
    * @return the assembly name.
    */
   @Override
   public String getAssemblyName() {
      return getImpl().getAssemblyName();
   }

   /**
    * Check if is outer mirror.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isOuterMirror() {
      return getImpl().isOuterMirror();
   }

   /**
    * Get the last modified time.
    * @return the last modified time of the assembly.
    */
   @Override
   public long getLastModified() {
      return getImpl().getLastModified();
   }

   /**
    * Set last modified time.
    * @param modified the specified last modified time.
    */
   @Override
   public void setLastModified(long modified) {
      getImpl().setLastModified(modified);
   }

   /**
    * Check if is auto update.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isAutoUpdate() {
      return getImpl().isAutoUpdate();
   }

   /**
    * Set auto update.
    * @param auto <tt>true</tt> to open auto update.
    */
   @Override
   public void setAutoUpdate(boolean auto) {
      getImpl().setAutoUpdate(auto);
   }

   /**
    * Update the inner mirror assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean update() {
      if(!super.update()) {
         return false;
      }

      return getImpl().update();
   }

   /**
    * Update the outer mirror assembly.
    * @param engine the specified asset repository.
    * @param user the specified user.
    */
   @Override
   public void updateMirror(AssetRepository engine, Principal user)
      throws Exception
   {
      getImpl().updateMirror(engine, user);
   }

   /**
    * Get the assembly.
    * @return the assembly of the mirror assembly.
    */
   @Override
   public Assembly getAssembly() {
      return getImpl().getAssembly();
   }

   @Override
   public void checkValidity() throws Exception {
      checkValidity(true);
   }

   /**
    * Check if the mirror assembly is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      super.checkValidity(checkCrossJoins);
      getImpl().checkValidity(checkCrossJoins);
   }

   /**
    * Check if the condition is a valid condition.
    * @return true if is valid, false otherwise.
    */
   @Override
   public boolean isValid() {
      DateRangeAssembly assembly = getDateRangeAssembly();

      if(assembly == null) {
         return false;
      }

      return assembly.isValid();
   }

   /**
    * Get the range assembly.
    * @return the range assembly.
    */
   private DateRangeAssembly getDateRangeAssembly() {
      if(!(getAssembly() instanceof DateRangeAssembly)) {
         LOG.debug("The mirror assembly is invalid",
            new Exception("Stack trace"));
      }

      return getAssembly() instanceof DateRangeAssembly ?
         (DateRangeAssembly) getAssembly() :
         null;
   }

   /**
    * Get the date range.
    * @return the date range of the range assembly.
    */
   @Override
   public DateCondition getDateRange() {
      DateRangeAssembly assembly = getDateRangeAssembly();

      if(assembly == null) {
         PeriodCondition cond = new PeriodCondition();
         cond.setFrom(new Date());
         cond.setTo(new Date());

         return cond;
      }

      return assembly.getDateRange();
   }

   /**
    * Set the date range.
    * @param range the specified date range.
    */
   @Override
   public void setDateRange(DateCondition range) {
      throw new RuntimeMessageException("Mirror date range is not editable!",
                                        LogLevel.DEBUG);
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
    * @see #CONTAINS
    * @see #LIKE
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
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      replaceVariable(vars);
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariable(VariableTable vars) {
      getDateRange().replaceVariable(vars);
   }

   /**
    * Get all variables in the range value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return getDateRange().getAllVariables();
   }

   /**
    * Evaluate this range against the specified value object.
    * @param value the value object this range should be compared with.
    * @return <code>true</code> if the value object meets this range.
    */
   @Override
   public boolean evaluate(Object value) {
      boolean result = getDateRange().evaluate(value);
      return isNegated() ? !result : result;
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      String name = getAssemblyName();
      set.add(new AssemblyRef(new AssemblyEntry(name, Worksheet.DATE_RANGE_ASSET)));
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      getImpl().renameDepended(oname, nname);
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

      Element cnode = Tool.getChildNodeByTagName(elem, "xConditionInfo");
      cnode = Tool.getFirstChildNode(cnode);
      cinfo.parseXML(cnode);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         MirrorDateRangeAssembly assembly =
            (MirrorDateRangeAssembly) super.clone();
         assembly.cinfo = (XConditionInfo) cinfo.clone();
         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
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
      cinfo.printKey(writer);
      DateCondition range = getDateRange();

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
      if(!(obj instanceof MirrorDateRangeAssembly)) {
         return false;
      }

      MirrorDateRangeAssembly assembly2 = (MirrorDateRangeAssembly) obj;

      return getName().equals(assembly2.getName()) &&
         Tool.equals(cinfo, assembly2.cinfo) &&
         Tool.equals(getDateRange(), assembly2.getDateRange());
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), cinfo);
   }

   private XConditionInfo cinfo;

   private static final Logger LOG = LoggerFactory.getLogger(MirrorDateRangeAssembly.class);
}

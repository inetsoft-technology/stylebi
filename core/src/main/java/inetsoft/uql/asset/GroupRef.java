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

import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.report.filter.GroupTuple;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.*;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;

/**
 * Group ref represents a group data ref.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GroupRef extends AbstractDataRef implements AssetObject,
   DataRefWrapper, XConstants, ContentObject, CalcGroup
{
   /**
    * Constructor.
    */
   public GroupRef() {
      super();
   }

   /**
    * Constructor.
    */
   public GroupRef(DataRef ref) {
      this.ref = ref;
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
    * Set the column for this grouping.
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
      int index = cols.indexOfAttribute(ref);

      if(index >= 0) {
         ref = cols.getAttribute(index);
         cname = null;
         chash = Integer.MIN_VALUE;
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

      // name group returns string (group name)
      if(groupInfo != null && !groupInfo.isEmpty()) {
         return XSchema.STRING;
      }

      return ref.getDataType();
   }

   /**
    * Write the attributes of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" timeSeries=\"" + timeSeries + "\" ");

      if(dgroup != NONE_DATE_GROUP) {
         writer.print(" dateGroup=\"" + dgroup + "\" ");
      }

      if(source != null) {
         writer.print("source=\"" + Tool.escape(source) + "\" ");
         writer.print("prefix=\"" + Tool.escape(prefix) + "\" ");
         writer.print("type=\"" + (type) + "\" ");
      }
   }

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the XML element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      String val = Tool.getAttribute(tag, "dateGroup");

      if(val != null) {
         dgroup = Integer.parseInt(val);
      }

      val = Tool.getAttribute(tag, "timeSeries");

      if(val != null) {
         timeSeries = Boolean.parseBoolean(val);
      }

      if(Tool.getAttribute(tag, "source") != null) {
         source = Tool.getAttribute(tag, "source");
         prefix = Tool.getAttribute(tag, "prefix");

         if(Tool.getAttribute(tag, "type") != null) {
            type = Integer.parseInt(Tool.getAttribute(tag, "type"));
         }
      }
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      ref.writeXML(writer);

      if(assembly != null) {
         writer.print("<assembly>");
         writer.print("<![CDATA[" + assembly + "]]>");
         writer.println("</assembly>");
      }
      else if(groupInfo != null) {
         writer.println("<groupInfo>");
         ((XMLSerializable) groupInfo).writeXML(writer);
         writer.println("</groupInfo>");
      }

      if(order != null) {
         order.writeXML(writer);
      }

      if(topn != null) {
         topn.writeXML(writer);
      }
   }

   /**
    * Write the contents of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeContents2(DataOutputStream dos) {
      try {
         // dos.writeInt(dgroup); it's written by writeAttributes2
         dos.writeUTF(ref.getClass().getName());
         ref.writeData(dos);
         dos.writeBoolean(assembly == null);

         if(assembly != null) {
            dos.writeUTF(assembly);
         }
      }
      catch (IOException e) {
      }
   }

   /**
    * Write the attributes of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeAttributes2(DataOutputStream dos) {
      try {
         dos.writeInt(dgroup);
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
      cname = null;
      chash = Integer.MIN_VALUE;
      Element anode = Tool.getChildNodeByTagName(tag, "assembly");

      if(anode != null) {
         assembly = Tool.getValue(anode);
      }

      anode = Tool.getChildNodeByTagName(tag, "groupInfo");

      if(anode != null) {
         anode = Tool.getChildNodeByTagName(anode, "namedgroups");
         groupInfo = new SNamedGroupInfo();

         if(anode != null) {
            ((XMLSerializable) groupInfo).parseXML(anode);
         }
      }

      if((anode = Tool.getChildNodeByTagName(tag, "groupSort")) != null) {
         order = new OrderInfo();
         order.parseXML(anode);
      }

      if((anode = Tool.getChildNodeByTagName(tag, "topn")) != null) {
         topn = new TopNInfo();
         topn.parseXML(anode);
      }
   }

   /**
    * Get the date group.
    * @return the date group.
    */
   public int getDateGroup() {
      return dgroup;
   }

   /**
    * Set the date group.
    * @param dgroup the specified date group. Date group option is defined in
    * the XConstants class.
    */
   public void setDateGroup(int dgroup) {
      this.dgroup = dgroup;
   }

   /**
    * Get the named group assembly.
    * @return the named group assembly.
    */
   public String getNamedGroupAssembly() {
      return assembly;
   }

   /**
    * Set the named group assembly.
    * @param assembly the specified named group assembly.
    */
   public void setNamedGroupAssembly(String assembly) {
      if(assembly == null || assembly.trim().length() == 0) {
         assembly = null;
      }

      this.assembly = assembly;
   }

   /**
    * Set the named group info. If the named group assembly is set, the group
    * info is retrieved from the assembly automatically. This method is used
    * to override the group info for internal usage.
    */
   public void setNamedGroupInfo(XNamedGroupInfo info) {
      this.groupInfo = info;
   }

   /**
    * Get the named group info.
    * @return the named group info of the group ref.
    */
   public XNamedGroupInfo getNamedGroupInfo() {
      return groupInfo;
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   public void replaceVariables(VariableTable vars) {
      if(groupInfo != null) {
         groupInfo.replaceVariables(vars);
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   public UserVariable[] getAllVariables() {
      if(groupInfo == null) {
         return new UserVariable[0];
      }

      return groupInfo.getAllVariables();
   }

   public void setTimeSeries(boolean timeSeries) {
      this.timeSeries = timeSeries;
   }

   public boolean isTimeSeries() {
      return timeSeries;
   }

   /**
    * Update the group ref.
    * @param ws the associated worksheet.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean update(Worksheet ws) {
      if(ws == null) {
         return false;
      }

      if(assembly == null) {
         return true;
      }

      NamedGroupAssembly ngassembly = ws == null ? null :
         (NamedGroupAssembly) ws.getAssembly(assembly);

      if(ngassembly == null) {
         return false;
      }

      groupInfo = null;

      if(ngassembly.getAttachedType() == AttachedAssembly.DATA_TYPE_ATTACHED) {
         String dtype = ngassembly.getAttachedDataType();

         if(!AssetUtil.isCompatible(dtype, ref.getDataType())) {
            return true;
         }
      }
      else if(ngassembly.getAttachedType() == AttachedAssembly.COLUMN_ATTACHED){
         DataRef aref = ngassembly.getAttachedAttribute();
         String attr = aref == null ? null : aref.getAttribute();

         if(!Tool.equals(ref.getAttribute(), attr)) {
            return true;
         }
      }

      groupInfo = ngassembly.getNamedGroupInfo();

      if(groupInfo != null) {
         groupInfo = (XNamedGroupInfo) groupInfo.clone();
      }

      return true;
   }

   /**
    * Get the assemblies depended on.
    */
   public void getDependeds(Set set) {
      if(assembly != null) {
         set.add(new AssemblyRef(
            new AssemblyEntry(assembly, Worksheet.NAMED_GROUP_ASSET)));
      }
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname) {
      if(oname.equals(assembly)) {
         assembly = nname;
      }
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
   public String getSource() {
      return source;
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
      this.type = type;
   }

   /**
    * Get the type of the source.
    */
   @Override
   public int getSourceType() {
      return type;
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
    * Check if is date type.
    *
    * @return true if is, false otherwise
    */
   @Override
   public boolean isDate() {
      return XSchema.isDateType(ref.getDataType());
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

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      writer.print("GREF[");
      writer.print(dgroup);
      writer.print(timeSeries);

      if(groupInfo != null) {
         groupInfo.printKey(writer);
      }

      if(order != null) {
         order.printKey(writer);
      }

      ConditionUtil.printDataRefKey(ref, writer);
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
      if(!(obj instanceof GroupRef)) {
         return false;
      }

      GroupRef gref = (GroupRef) obj;

      if(dgroup != gref.dgroup) {
         return false;
      }

      if(!Tool.equals(groupInfo, gref.groupInfo)) {
         return false;
      }

      if(!Tool.equals(order, gref.order)) {
         return false;
      }

      if(!Tool.equals(assembly, gref.assembly)) {
         return false;
      }

      if(!ConditionUtil.equalsDataRef(ref, gref.ref)) {
         return false;
      }

      return true;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         GroupRef group = (GroupRef) super.clone();
         group.ref = ref == null ? null : (DataRef) ref.clone();
         return group;
      }
      catch(Exception ex) {
         // ignore it for impossible
         return null;
      }
   }

   public Map<GroupTuple, DCMergeDatesCell> getDcMergeGroup() {
      return dcMergeGroup;
   }

   public void setDcMergeGroup(Map<GroupTuple, DCMergeDatesCell> dcMergeGroup) {
      this.dcMergeGroup = dcMergeGroup;
   }

   public boolean isDcRangeCol() {
      return dcRangeCol;
   }

   public void setDcRangeCol(boolean dcRangeCol) {
      this.dcRangeCol = dcRangeCol;
   }

   public boolean isSortOthersLast() {
      return sortOthersLast;
   }

   public void setSortOthersLast(boolean sortOthersLast) {
      this.sortOthersLast = sortOthersLast;
   }

   private DataRef ref;
   private int dgroup = NONE_DATE_GROUP;
   private int type;
   private String assembly = null;
   private String source = null;
   private String prefix;
   private XNamedGroupInfo groupInfo;
   private OrderInfo order;
   private TopNInfo topn;
   private boolean timeSeries;
   private Map<GroupTuple, DCMergeDatesCell> dcMergeGroup;
   private boolean dcRangeCol;
   private boolean sortOthersLast;
}

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
package inetsoft.report.internal.binding;

import inetsoft.report.ReportSheet;
import inetsoft.report.internal.RuntimeAssetEngine;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Asset named group info references to a named group assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AssetNamedGroupInfo implements XNamedGroupInfo, XMLSerializable, Comparable {
   /**
    * Constructor.
    */
   public AssetNamedGroupInfo() {
      entry = new AssetEntry();
      info = new NamedGroupInfo();
   }

   /**
    * Constructor.
    */
   public AssetNamedGroupInfo(AssetEntry entry, NamedGroupAssembly assembly) {
      this.entry = entry;
      this.info = assembly.getNamedGroupInfo();
      this.name = assembly.getName();

      this.assembly = assembly;
   }

   /**
    * Get the named group info.
    * @return the named group info.
    */
   public XNamedGroupInfo getNamedGroupInfo() {
      return info;
   }

   /**
    * Get the asset entry.
    * @return the asset entry.
    */
   public AssetEntry getEntry() {
      return entry;
   }

   /**
    * Get the name of the asset named group info.
    * @return the name of the asset named group info.
    */
   public String getName() {
      return name;
   }

   /**
    * Get the named group assembly.
    * @return the assembly if any.
    */
   public NamedGroupAssembly getNamedGroupAssembly() {
      return assembly;
   }

   /**
    * Check if matches a field.
    * @param fld the specified field.
    * @return <tt>true</tt> if matches, <tt>false</tt> otherwise.
    */
   public boolean matches(DataRef fld) {
      if(assembly == null) {
         return true;
      }

      if(fld instanceof GroupField) {
         fld = ((GroupField) fld).getDataRef();
      }

      if(assembly.getAttachedType() == NamedGroupAssembly.COLUMN_ATTACHED) {
         DataRef attr = assembly.getAttachedAttribute();
         return fld.getAttribute().equals(attr.getAttribute());
      }
      else if(assembly.getAttachedType() ==
         NamedGroupAssembly.DATA_TYPE_ATTACHED)
      {
         return AssetUtil.isCompatible(assembly.getAttachedDataType(),
                                       fld.getDataType());
      }

      return false;
   }

   /**
    * Update the asset named group info.
    * @param rep the specified asset repository.
    * @param report the specified report sheet.
    */
   public void update(AssetRepository rep, ReportSheet report) throws Exception
   {
      rep = new RuntimeAssetEngine(rep, report);
      Worksheet ws = (Worksheet) rep.getSheet(entry, null, false,
                                              AssetContent.ALL);

      if(ws == null) {
         throw new Exception("Worksheet not found: " + entry);
      }

      Assembly assembly = ws.getPrimaryAssembly();

      if(!(assembly instanceof NamedGroupAssembly)) {
         throw new Exception("Primary named group assembly is required: " +
                             entry);
      }

      this.info = ((NamedGroupAssembly) assembly).getNamedGroupInfo();
   }

   /**
    * Get type of the named group info.
    * @return the type of the named group info.
    */
   @Override
   public int getType() {
      return ASSET_NAMEDGROUP_INFO_REF;
   }

   /**
    * Get the condition list of a group.
    * @param name the group name.
    * @return the condition list of the group.
    */
   @Override
   public ConditionList getGroupCondition(String name) {
      return info.getGroupCondition(name);
   }

   /**
    * Get the option for others.
    */
   public int getOthers() {
      return info == null ? XConstants.GROUP_OTHERS : info.getOthers();
   }

   /**
    * Reset the named group info.
    */
   @Override
   public void clear() {
      info.clear();
   }

   /**
    * Check if the named group info is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmpty() {
      return info.isEmpty();
   }

   /**
    * Get all the groups.
    * @return all the group.
    */
   @Override
   public String[] getGroups(boolean sort) {
      return info.getGroups(sort);
   }

   /**
    * Remove a group.
    * @param name the specified group name.
    */
   @Override
   public void removeGroup(String name) {
      info.removeGroup(name);
   }

   /**
    * Check if equals another object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof AssetNamedGroupInfo)) {
         return false;
      }

      AssetNamedGroupInfo info2 = (AssetNamedGroupInfo) obj;
      return Tool.equals(name, info2.name);
   }

   public boolean equalsContent(Object obj) {
      if(!(obj instanceof AssetNamedGroupInfo)) {
         return false;
      }

      AssetNamedGroupInfo info2 = (AssetNamedGroupInfo) obj;

      return Tool.equals(name, info2.name) && Tool.equals(info, info2.info) && Tool.equals(entry, info2.entry);
   }

   /**
    * Compare to another object.
    * @param obj the specified object.
    * @return compare result.
    */
   @Override
   public int compareTo(Object obj) {
      if(!(obj instanceof AssetNamedGroupInfo)) {
         return 1;
      }

      AssetNamedGroupInfo info2 = (AssetNamedGroupInfo) obj;
      return name.compareTo(info2.name);
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      return name.hashCode();
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return name;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<namedgroups type=\"" + getType() + "\"");
      writer.print(" source=\"" + Tool.escape(entry.toIdentifier()) + "\"");
      writer.println(">");

      /*
      // CAUTION! This tag will be used to find dependency
      writer.println();
      writer.print("<assetDependency>");
      writer.print("<![CDATA[" + entry.toIdentifier() + "]]>");
      writer.println("</assetDependency>");
      */

      writer.print("<name>");
      writer.print("<![CDATA[" + ("".equals(name) ? BLANKSTRING : name) + "]]>");
      writer.println("</name>");

      writer.println("<info>");
      info.writeXML(writer);
      writer.println("</info>");

      writer.println("</namedgroups>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      String identifier = Tool.getAttribute(elem, "source");
      entry = AssetEntry.createAssetEntry(identifier);

      name = Tool.getChildValueByTagName(elem, "name");

      if(name == null || BLANKSTRING.equals(name)) {
         name = "";
      }

      Element inode = Tool.getChildNodeByTagName(elem, "info");
      inode = Tool.getFirstChildNode(inode);
      info.parseXML(inode);
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return new UserVariable[0];
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      return true;
   }

   /**
    * Clone it.
    */
   @Override
   public Object clone() {
      try {
         AssetNamedGroupInfo info2 = (AssetNamedGroupInfo) super.clone();
         info2.entry = (AssetEntry) entry.clone();
         info2.info = (NamedGroupInfo) info.clone();

         return info2;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone group info", ex);
         return null;
      }
   }

   private AssetEntry entry;
   private NamedGroupInfo info;
   private String name;
   private final String BLANKSTRING = "__blank__";

   private transient NamedGroupAssembly assembly;

   private static final Logger LOG =
      LoggerFactory.getLogger(AssetNamedGroupInfo.class);
}

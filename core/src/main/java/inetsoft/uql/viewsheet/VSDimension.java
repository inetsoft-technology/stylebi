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

import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.ItemList;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * A VSDimension object represents a dimension.
 *
 * @author InetSoft Technology
 * @version 8.5
 */
public class VSDimension implements XDimension {
   /**
    * Constructor of VSDimension.
    */
   public VSDimension() {
      super();
      this.members = new ArrayList();
   }

   /**
    * Get the name of this dimension.
    * @return the name of this dimension.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Set the name of this cube member.
    * @param name the member name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the number of levels in this dimension.
    * @return the number of levels in this dimension.
    */
   @Override
   public int getLevelCount() {
      return members.size();
   }

   /**
    * Get the specified level.
    * @param idx the index of the requested level.
    * @return the cube member of the level
    */
   @Override
   public XCubeMember getLevelAt(int idx) {
      return (XCubeMember) members.get(idx);
   }

   /**
    * Get the scope (index) of the specified level.
    * @param levelName the name of the level.
    * @return the index of the specified level.
    */
   @Override
   public int getScope(String levelName) {
      for(int scope = 0; scope < members.size(); scope++) {
         if(((XCubeMember) members.get(scope)).getName().equals(levelName)) {
	    return scope;
         }
      }

      return -1;
   }

   /**
    * Get the type of this dimension.
    *
    * @return the type of this dimension.
    */
   @Override
   public int getType() {
      return DataRef.NONE;
   }

   /**
    * Add a level to this dimension.
    * @param member the level to add.
    */
   public void addLevel(XCubeMember member) {
      if(!members.contains(member)) {
         members.add(member);
      }
      else {
         int scope = getScope(member.getName());
         members.remove(scope);
         members.add(scope, member);
      }
   }

   /**
    * Add a level to this dimension.
    * @param idx the index.
    * @param member the level to add.
    */
   public void addLevelAt(int idx, XCubeMember member) {
      if(!members.contains(member)) {
         members.add(idx, member);
      }
      else {
         int scope = getScope(member.getName());
         members.remove(scope);
         members.add(idx, member);
      }
   }

   /**
    * Remove the specified level from this dimension.
    * @param name the name of the level to remove.
    */
   public void removeLevel(String name) {
      members.remove(getScope(name));
   }

   /**
    * Remove the specified level from this dimension.
    * @param idx the name of the level to remove.
    */
   public void removeLevelAt(int idx) {
      members.remove(idx);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return super.toString() + "[" + name + ", " + members + "]";
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public final void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      // do nothing
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      name = Tool.getChildValueByTagName(elem, "name");

      Element cnode = Tool.getChildNodeByTagName(elem, "members");

      if(cnode != null) {
         ItemList list = new ItemList();
         list.parseXML(cnode);
         Iterator iter = list.itemsIterator();
         members = new ArrayList();

         while(iter.hasNext()) {
            members.add(iter.next());
         }
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<VSDimension class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</VSDimension>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      // do nothing
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(name != null) {
         writer.print("<name>");
         writer.print("<![CDATA[" + name + "]]>");
         writer.println("</name>");
      }

      if(members != null && members.size() > 0) {
         ItemList list = new ItemList("members");
         list.addAllItems(members);

         list.writeXML(writer);
      }
   }

   /**
    * Create a copy of this object.
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         VSDimension dimension = (VSDimension) super.clone();
         dimension.members = Tool.deepCloneCollection(members);

         return dimension;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VSDimension", ex);
      }

      return null;
   }

   /**
    * Get the hash code.
    * @return the hash code.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSDimension)) {
         return false;
      }

      VSDimension dim = (VSDimension) obj;
      return Tool.equals(name, dim.name) && members.equals(dim.members);
   }

   /**
    * Get the data type.
    * @return the data type.
    */
   public String getDataType() {
      if(members.size() == 0) {
         return XSchema.STRING;
      }

      VSDimensionMember dmember = (VSDimensionMember) members.get(0);
      return dmember.getDataType();
   }

   /**
    * Get the column ref.
    * @return the column ref.
    */
   public ColumnRef getColumnRef() {
      AttributeRef attr = new AttributeRef(null, name);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(getDataType());
      column.setAlias(name);

      return column;
   }

   /**
    * Check if is an expression dimension.
    * @return <tt>true</tt> if is an expression dimension, <tt>false</tt>
    * otherwise.
    */
   public boolean isExpressionDimension() {
      for(int i = 0; i < members.size(); i++) {
         VSDimensionMember member = (VSDimensionMember) members.get(i);
         ColumnRef column = member.getColumnRef();

         if(column.isExpression()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get all the cube members.
    * @return all the cube members.
    */
   public XCubeMember[] getLevels() {
      XCubeMember[] arr = new XCubeMember[members.size()];
      members.toArray(arr);

      return arr;
   }

   /**
    * Validate this viewsheet cube.
    * @param columns the specified column selection.
    * @return <tt>true if changed, <tt>false</tt> otherwise.
    */
   public boolean validate(ColumnSelection columns) {
      if(columns == null || columns.getAttributeCount() == 0) {
         return false;
      }

      boolean changed = false;

      for(int i = members.size() - 1; i >= 0; i--) {
         VSDimensionMember member = (VSDimensionMember) members.get(i);
         DataRef ref = member.getDataRef();

         if(ref == null || columns.getAttribute(ref.getName()) == null) {
            members.remove(i);
            changed = true;
         }
      }

      return changed;
   }

   /**
    * Check if this dimension is empty.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isEmpty() {
      return members.size() == 0;
   }

   private String name;
   private List members;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSDimension.class);
}

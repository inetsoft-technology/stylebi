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
package inetsoft.uql.util;

import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * This class defines the IdentityNode.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
public class IdentityNode implements XMLSerializable {
   /**
    * Root.
    */
   public static final int ROOT = -1;
   /**
    * Users.
    */
   public static final int USERS = -2;
   /**
    * Roles.
    */
   public static final int ROLES = -3;

   /**
    * Virtual folder for groups.
    */
   public static final int GROUPS_VFOLDER = -4;

   /**
    * Virtual folder for users.
    */
   public static final int USERS_VFOLDER = -5;

   /**
    * Groups.
    */
   public static final int GROUPS = -6;

   /**
    * Constructor.
    */
   public IdentityNode() {
      super();
   }

   /**
    * Constructor.
    */
   public IdentityNode(Identity identity) {
      this(identity.getIdentityID(), identity.getType(), identity.isEditable());
   }

   /**
    * Constructor.
    */
   public IdentityNode(IdentityID identityID, int type, boolean isEditable) {
      this.identityID = identityID;
      this.type = type;
      this.isEditable = isEditable;
   }

   /**
    * Creates a new instance of <tt>IdentityNode</tt>. This constructor is used
    * to create a virtual folder.
    *
    * @param identityID       the name of the folder.
    * @param type       the type of the folder.
    * @param parentName the name of the actual parent of the entries.
    * @param parentType the type of the actual parent of the entries.
    * @param firstEntry the name of the first entry in the folder.
    * @param lastEntry  the name of the last entry in the folder.
    */
   public IdentityNode(IdentityID identityID, int type, String parentName, int parentType,
                       String firstEntry, String lastEntry)
   {
      this.identityID = identityID;
      this.type = type;
      this.parentName = parentName;
      this.parentType = parentType;
      this.firstEntry = firstEntry;
      this.lastEntry = lastEntry;
      this.isEditable = false;
      this.isFolder = true;
   }

   /**
    * Get name.
    */
   public IdentityID getIdentityID() {
      return identityID;
   }

   /**
    * Get type.
    */
   public int getType() {
      return type;
   }

   /**
    * Check if it is editable.
    */
   public boolean isEditable() {
      return isEditable;
   }

   /**
    * Gets the name of the actual parent of the entries in the virtual folder.
    *
    * @return the parent name.
    */
   public String getParentName() {
      return parentName;
   }

   /**
    * Gets the type of the actual parent of the entries in the virtual folder.
    *
    * @return the parent type.
    */
   public int getParentType() {
      return parentType;
   }

   /**
    * Gets the name of the first child entry in the virtual folder.
    *
    * @return the first entry.
    */
   public String getFirstEntry() {
      return firstEntry;
   }

   /**
    * Gets the name of the last child entry in the virtual folder.
    *
    * @return the last entry.
    */
   public String getLastEntry() {
      return lastEntry;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<node");
      writeAttributes(writer);
      writer.print(">");
      writer.print("<name><![CDATA[" + Tool.byteEncode(identityID.convertToKey() ) + "]]></name>");
      writer.print("<type><![CDATA[" + this.type + "]]></type>");
      writer.print("<isEditable>" + this.isEditable + "</isEditable>");

      if(parentName != null) {
         writer.print("<parentName><![CDATA[" + Tool.byteEncode(parentName) +
                      "]]></parentName>");
         writer.print("<parentType><![CDATA[" + this.parentType +
                      "]]></parentType>");
         writer.print("<firstEntry><![CDATA[" + Tool.byteEncode(firstEntry) +
                      "]]></firstEntry>");
         writer.print("<lastEntry><![CDATA[" + Tool.byteEncode(lastEntry) +
                      "]]></lastEntry>");
      }

      writer.print("</node>");
   }

   /**
    * Write attributes.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" classname=\"");
      writer.print(getClass().getName());
      writer.print("\" isFolder=\"");
      writer.print(isFolder);
      writer.print("\"");
   }

   /**
    * Method to parse an xml segment.
    * @param tag the specified xml element.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      isFolder = "true".equals(Tool.getAttribute(tag, "isFolder"));
      Element nameNode = Tool.getChildNodeByTagName(tag, "name");
      this.identityID = IdentityID.getIdentityIDFromKey(Tool.byteDecode(Tool.getValue(nameNode)));
      this.type = Integer.parseInt(Tool.getValue(
         Tool.getChildNodeByTagName(tag, "type")));
      this.isEditable = "true".equals(Tool.getValue(
         Tool.getChildNodeByTagName(tag, "isEditable")));

      Element node = null;

      if((node = Tool.getChildNodeByTagName(tag, "parentName")) != null) {
         this.parentName = Tool.byteDecode(Tool.getValue(node));
      }

      if((node = Tool.getChildNodeByTagName(tag, "parentType")) != null) {
         this.parentType = Integer.parseInt(Tool.getValue(node));
      }

      if((node = Tool.getChildNodeByTagName(tag, "firstEntry")) != null) {
         this.firstEntry = Tool.byteDecode(Tool.getValue(node));
      }

      if((node = Tool.getChildNodeByTagName(tag, "lastEntry")) != null) {
         this.lastEntry = Tool.byteDecode(Tool.getValue(node));
      }
   }

   public boolean isFolder() {
      return isFolder;
   }

   public void setFolder(boolean isFolder) {
      this.isFolder = isFolder;
   }

   public String toString() {
      return "IdentityNode[" + type + ":" + identityID + "]";
   }

   private boolean isFolder = false;
   private IdentityID identityID;
   private int type;
   private boolean isEditable;
   private String parentName;
   private int parentType;
   private String firstEntry;
   private String lastEntry;
}

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
package inetsoft.uql.viewsheet;

import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Description of bookmark.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class VSBookmarkInfo implements XMLSerializable {
   /**
    * The private bookmark.
    */
   public static final int PRIVATE = 0;

   /**
    * The all user shared bookmark.
    */
   public static final int ALLSHARE = 1;

   /**
    * The group user shared bookmark.
    */
   public static final int GROUPSHARE  = 2;

   /**
    *
    */
   public VSBookmarkInfo() {
   }

   /**
    * Create a viewsheet bookmark info.
    */
   public VSBookmarkInfo(String name, int type, IdentityID owner, boolean readOnly,
      long time)
   {
      this.name = name;
      this.type = type;
      this.owner = owner;
      this.readOnly = readOnly;
      this.lastModifyTime = time;
   }

   /**
    * Create a viewsheet bookmark info.
    */
   public VSBookmarkInfo(String name, int type, IdentityID owner, boolean readOnly,
                         long time, long accessTime)
   {
      this.name = name;
      this.type = type;
      this.owner = owner;
      this.readOnly = readOnly;
      this.lastModifyTime = time;
      this.lastAccessedTime = accessTime;
   }

   /**
    * Create a viewsheet bookmark info.
    */
   public VSBookmarkInfo(String name, int type, IdentityID owner, boolean readOnly,
                         long time, long accessTime, long createTime)
   {
      this.name = name;
      this.type = type;
      this.owner = owner;
      this.readOnly = readOnly;
      this.lastModifyTime = time;
      this.lastAccessedTime = accessTime;
      this.createTime = createTime;
   }

   /**
    * Get the bookmark name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the bookmark name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the bookmark type.
    */
   public int getType() {
      return type;
   }

   /**
    * Set the bookmark type.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get the bookmark owner.
    */
   public IdentityID getOwner() {
      return owner;
   }

   /**
    * Set the bookmark name.
    */
   public void setOwner(IdentityID owner) {
      this.owner = owner;
   }

   /**
    * Get the bookmark read/write info.
    */
   public boolean isReadOnly() {
      return readOnly;
   }

   /**
    * Set the bookmark read/write info.
    */
   public void setReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
   }

   /**
    * Get the bookmark last modified time.
    */
   public long getLastModified() {
      return lastModifyTime;
   }

   /**
    * Set the bookmark last modified time.
    */
   public void setLastModified(long lastModifyTime) {
      this.lastModifyTime = lastModifyTime;
   }

   /**
    * Get the bookmark last accessed time.
    */
   public long getLastAccessed() {
      return lastAccessedTime;
   }

   /**
    * Set the bookmark last accessed time.
    */
   public void setLastAccessed(long lastAccessedTime) {
      this.lastAccessedTime = lastAccessedTime;
   }

   public long getCreateTime() {
      return createTime;
   }

   public void setCreateTime(long createTime) {
      this.createTime = createTime;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<bookmarkInfo class=\"" + getClass().getName() + "\">");
      writer.print("<name>");
      writer.print("<![CDATA[" + name + "]]>");
      writer.println("</name>");
      writer.print("<type>");
      writer.print("<![CDATA[" + type + "]]>");
      writer.println("</type>");
      writer.print("<owner>");
      writer.print("<![CDATA[" + owner.convertToKey() + "]]>");
      writer.println("</owner>");
      writer.print("<readOnly>");
      writer.print("<![CDATA[" + readOnly + "]]>");
      writer.println("</readOnly>");
      writer.println("</bookmarkInfo>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      name = Tool.getChildValueByTagName(tag, "name");

      Element typeElem = Tool.getChildNodeByTagName(tag, "type");

      if(typeElem != null) {
         type = Integer.parseInt(Tool.getValue(typeElem));
      }
      else {
         type = VSBookmarkInfo.PRIVATE;
      }

      owner = IdentityID.getIdentityIDFromKey(Tool.getChildValueByTagName(tag, "owner"));
      readOnly = "true".equals(Tool.getChildValueByTagName(tag, "readOnly"));
   }

   @Override
   public String toString() {
      return "VSBookmarkInfo{" +
         "readOnly=" + readOnly +
         ", name='" + name + '\'' +
         ", type=" + type +
         ", owner='" + owner + '\'' +
         ", lastModifyTime=" + lastModifyTime +
         ", lastAccessedTime=" + lastAccessedTime +
         ", createTime=" + createTime +
         '}';
   }

   private boolean readOnly;
   private String name;
   private int type;
   private IdentityID owner;
   private long lastModifyTime;
   private long lastAccessedTime = -1;
   private long createTime = -1;
}

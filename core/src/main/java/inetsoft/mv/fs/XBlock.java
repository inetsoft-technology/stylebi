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
package inetsoft.mv.fs;

import inetsoft.mv.comm.*;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;

/**
 * XBlock, logic file block.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public abstract class XBlock
   implements XMLSerializable, XTransferable, Cloneable, Serializable
{
   /**
    * Constructor.
    */
   public XBlock() {
      super();
   }

   /**
    * Constructor.
    */
   public XBlock(String parent, String id) {
      super();

      this.parent = parent;
      this.id = id;
   }

   /**
    * Get parent (XFile) name of this block.
    */
   public String getParent() {
      return parent;
   }

   /**
    * Get block id.
    */
   public String getID() {
      return id;
   }

   /**
    * Get block file length.
    */
   public long getLength() {
      return length;
   }

   /**
    * Set block file length.
    */
   public void setLength(long length) {
      this.length = length;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<XBlock");
      writeAttributes(writer);
      writer.print(">");
      writer.println("</XBlock>");
   }

   /**
    * Write attributes.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" id=\"" + Tool.escape(id) + "\"");
      writer.print(" length=\"" + length + "\"");
      writer.print(" parent=\"" + Tool.escape(parent) + "\"");
   }

   /**
    * Method to parse an xml segment about parameter element information.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseAttribute(tag);
   }

   /**
    * Parse attributes.
    */
   protected void parseAttribute(Element tag) {
      this.id = Tool.getAttribute(tag, "id");
      this.parent = Tool.getAttribute(tag, "parent");
      String val = Tool.getAttribute(tag, "length");

      if(val != null) {
         length = Long.parseLong(val);
      }
   }

   /**
    * Check if equals another object.
    * @param obj the specified opject to compare.
    * @return true if equals, false otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof XBlock)) {
         return false;
      }

      XBlock block = (XBlock) obj;
      return Tool.equals(id, block.id);
   }

   /**
    * Get the hash code value.
    */
   public int hashCode() {
      return (id == null) ? 0 : id.hashCode();
   }

   /**
    * Read this transferable.
    */
   @Override
   public void read(XReadBuffer buf) throws IOException {
      parent = buf.readString();
      id = buf.readString();
      length = buf.readLong();
   }

   /**
    * Write this transferable.
    */
   @Override
   public void write(XWriteBuffer buf) throws IOException {
      buf.writeString(parent);
      buf.writeString(id);
      buf.writeLong(length);
   }

   /**
    * Clone this XBlock object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone block", ex);
         return null;
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "XBlock[parent=" + parent + ", id=" + id +
         ", length=" + length + ", version=" + version + "]";
   }

   /**
    * Set version.
    * @version the specified version number.
    */
   public final void setVersion(int version) {
      this.version = version;
   }

   /**
    * Get version.
    * @return version number.
    */
   public final int getVersion() {
      return version;
   }

   private static final Logger LOG = LoggerFactory.getLogger(XBlock.class);
   private String parent;
   private String id;
   private long length;
   private int version;
}

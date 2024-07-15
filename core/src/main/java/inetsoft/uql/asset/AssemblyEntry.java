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

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Assembly entry locates an assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AssemblyEntry implements AssetObject {
   /**
    * Constructor.
    */
   public AssemblyEntry() {
      super();

      this.hash = -1;
   }

   /**
    * Constructor.
    */
   public AssemblyEntry(String name, int type) {
      this(name, name, type);
   }

   /**
    * Constructor.
    */
   public AssemblyEntry(String name, String aname, int type) {
      this();

      this.name = name;
      this.aname = aname == null ? name : aname;
      this.type = type;
      this.hash = -1;
   }

   /**
    * Get the name.
    * @return the name of the assembly.
    */
   public String getName() {
      return name;
   }

   /**
    * Get the absolute name.
    * @return the absolute name.
    */
   public String getAbsoluteName() {
      return aname;
   }

   /**
    * Get the type. The types are defined in AbstractSheet, e.g. TABLE_ASSET.
    * @return the type of the assembly.
    */
   public int getType() {
      return type;
   }

   /**
    * Check if is a worksheet assembly.
    * @return <tt>true</tt> is a worksheet assembly, <tt>false</tt> otherwise.
    */
   public boolean isWSAssembly() {
      return type <= 100;
   }

   /**
    * Check if is a viewsheet assembly.
    * @return <tt>true</tt> if is a viewsheet assembly, <tt>false</tt>
    * otherwise.
    */
   public boolean isVSAssembly() {
      return type > 100;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "AssemblyEntry: [" + aname + "," + type + "]";
   }

   /**
    * Check if equals another object.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof AssemblyEntry)) {
         return false;
      }

      AssemblyEntry entry2 = (AssemblyEntry) obj;
      return type == entry2.type && Objects.equals(aname, entry2.aname);
   }

   /**
    * Get the hash code.
    * @return the hash code of the report entry.
    */
   public int hashCode() {
      if(hash < 0) {
         hash = aname == null ? 0 : aname.hashCode();
         hash = hash ^ type;
         hash = Math.abs(hash);
      }

      return hash;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<assetEntry type=\"" + type + "\">");
      writer.print("<name>");
      writer.print("<![CDATA[" + name + "]]>");
      writer.print("</name>");
      writer.println("</assetEntry>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      this.type = Integer.parseInt(Tool.getAttribute(elem, "type"));
      this.name = Tool.getChildValueByTagName(elem, "name");
      this.hash = -1;
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         // ignore it
      }

      return null;
   }

   /**
    * Get an AssemblyEntry representing the unqualified name.
    * 
    * @return  the entry of the unqualified name, or null if not qualified.
    */
   public AssemblyEntry getUnqualifiedEntry() {
      int index = aname.lastIndexOf(".");
      String unqualifiedName = index >= 0 ? aname.substring(index + 1) : aname;

      if(aname.equals(unqualifiedName)) {
         return null;
      }
      else {
         return new AssemblyEntry(name, unqualifiedName, type);
      }
   }

   private String aname;
   private String name;
   private int type;
   private transient int hash;
}

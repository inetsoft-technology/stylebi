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

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Abstract assembly, implements most methods defined in Assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractAssembly implements Assembly {
   /**
    * Constructor.
    */
   public AbstractAssembly() {
      super();
   }

   /**
    * Get the absolute name of this assembly.
    * @return the absolute name of this assembly.
    */
   @Override
   public String getAbsoluteName() {
      return getName();
   }

   /**
    * Get the assembly entry.
    * @return the assembly entry.
    */
   @Override
   public AssemblyEntry getAssemblyEntry() {
      return new AssemblyEntry(getName(), getAbsoluteName(), getAssemblyType());
   }

   /**
    * Get the class name.
    */
   protected String getClassName(boolean compact) {
      return getClass().getName();
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      boolean storage = Tool.isCompact();
      String cls = "class=\"" + getClassName(storage) + "\"";
      writer.print("<assembly " + cls);
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</assembly>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      // do nothing
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      // do nothing
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      getInfo().writeXML(writer);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element inode = Tool.getChildNodeByTagName(elem, "assemblyInfo");
      getInfo().parseXML(inode);
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
    * Set the bounds.
    * @param bounds the specified bounds.
    */
   @Override
   public void setBounds(Rectangle bounds) {
      setPixelOffset(bounds.getLocation());
      setPixelSize(bounds.getSize());
   }

   /**
    * Get the bounds.
    * @return the bounds of the assembly.
    */
   @Override
   public Rectangle getBounds() {
      return new Rectangle(getPixelOffset(), getPixelSize());
   }

   /**
    * Check if is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEditable() {
      return getInfo().isEditable();
   }

   /**
    * Check if is visible.
    * @return <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isVisible() {
      return getInfo().isVisible();
   }

   /**
    * Get the hash code.
    * @return the hash code.
    */
   public int hashCode() {
      return getAssemblyEntry().hashCode();
   }

   /**
    * Get the original hash code.
    * @return the original hash code.
    */
   @Override
   public int addr() {
      return super.hashCode();
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof Assembly)) {
         return false;
      }

      Assembly assembly = (Assembly) obj;
      return Objects.equals(getAbsoluteName(), assembly.getAbsoluteName()) &&
         getAssemblyType() == assembly.getAssemblyType();
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      String cls = getClass().getName();
      return getAbsoluteName() + "[" + cls.substring(cls.lastIndexOf('.') + 1) + "@" + addr() + "]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractAssembly.class);
}

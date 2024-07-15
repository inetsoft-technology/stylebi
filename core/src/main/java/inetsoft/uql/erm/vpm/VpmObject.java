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
package inetsoft.uql.erm.vpm;

import inetsoft.util.*;
import inetsoft.util.xml.XMLStorage.XMLFragment;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * Vpm object abstracts the common features of the vpm entities like
 * conditions and hidden columns.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class VpmObject
   implements XMLSerializable, Serializable, Cloneable, Comparable, XMLFragment
{
   /**
    * Create a <tt>VpmObject</tt> from an xml element.
    * @param elem the specified xml element.
    * @return the created <tt>VpmObject</tt> object.
    */
   public static VpmObject createVpmObject(Element elem) throws Exception {
      TransformerManager transf =
         TransformerManager.getManager(TransformerManager.VPM);
      transf.transform(elem);
      String cls = Tool.getAttribute(elem, "class");
      VpmObject obj;

      try {
         obj = (VpmObject) Class.forName(cls).newInstance();
      }
      catch(ClassNotFoundException ex) {
         obj = (VpmObject) Class.forName(cls.replace("uql.erm", "uql.erm.vpm")).newInstance();
      }

      obj.parseXML(elem);
      return obj;
   }

   /**
    * Constructor.
    */
   public VpmObject() {
      super();
   }

   /**
    * Get the name of the virtual private model.
    * @return the name of the virtual private model.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the name of the virtual private model.
    * @param name the name of the virtual private model.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the script.
    * @return the script to check if should apply the vpm object.
    */
   public String getScript() {
      return script;
   }

   /**
    * Set the script.
    * @param script the script to check if should apply the vpm object.
    */
   public void setScript(String script) {
      this.script = script;
   }

   /**
    * Get the hash code value of the vpm object.
    * @return the hash code value of the vpm object.
    */
   public int hashCode() {
      return name.hashCode();
   }

   /**
    * Check if equals to another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(obj == null) {
         return false;
      }

      if(!getClass().equals(obj.getClass())) {
         return false;
      }

      VpmObject vobj = (VpmObject) obj;
      return name.equals(vobj.name);
   }

   /**
    * Compare to another object.
    * @param obj the specified object to compare to.
    */
   @Override
   public int compareTo(Object obj) {
      if(obj == null) {
         return 1;
      }

      if(!getClass().equals(obj.getClass())) {
         return getClass().getName().compareTo(obj.getClass().getName());
      }

      VpmObject vobj = (VpmObject) obj;
      return name.compareTo(vobj.name);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writeStart(writer);
      writeEnd(writer);
   }

   @Override
   public void writeStart(PrintWriter writer) {
      writer.print("<vpmObject class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writer.println("<version>" + FileVersions.DATASOURCE + "</version>");
      writeContents(writer);
   }

   @Override
   public void writeEnd(PrintWriter writer) {
      writer.print("</vpmObject>");
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
      writer.print("<name>");
      writer.print("<![CDATA[" + Encode.forCDATA(name) + "]]>");
      writer.println("</name>");

      if(script != null) {
         writer.print("<script>");
         writer.print("<![CDATA[" + Encode.forCDATA(script) + "]]>");
         writer.println("</script>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      name = Tool.getChildValueByTagName(elem, "name");
      script = Tool.getChildValueByTagName(elem, "script");
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
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return getName();
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

   private String name;
   private String script;

   private static final Logger LOG =
      LoggerFactory.getLogger(VpmObject.class);
}

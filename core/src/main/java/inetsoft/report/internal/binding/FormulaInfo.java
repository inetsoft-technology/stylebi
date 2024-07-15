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
package inetsoft.report.internal.binding;

import inetsoft.uql.asset.SourceInfo;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * FormulaInfo stores formula field and source info.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class FormulaInfo implements Serializable, XMLSerializable, Cloneable {
   /**
    * Constructor.
    */
   public FormulaInfo() {
      this.source = new SourceInfo();
      this.field = new FormulaField();
   }

   /**
    * Constructor.
    */
   public FormulaInfo(SourceInfo source, FormulaField fld) {
      this.source = source;
      this.field = fld;
   }

   /**
    * Set the source info of formula field.
    */
   public void setSource(SourceInfo source) {
      this.source = source;
   }

   /**
    * Get the source info of formula field.
    */
   public SourceInfo getSource() {
      return source;
   }

   /**
    * Set the formula field.
    */
   public void setFormulaField(FormulaField fld) {
      this.field = fld;
   }

   /**
    * Get the formula field.
    */
   public FormulaField getFormulaField() {
      return field;
   }

   /**
    * Write the xml segment to the destination writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<formulaInfo ");
      writer.println("source=\"" + Tool.escape(source.getSource()) + "\" type=\"" +
                     source.getType()  + "\" prefix=\"" + Tool.escape(source.getPrefix()) + "\">");
      writer.print("<formulaField>");

      if(field != null) {
         field.writeXML(writer);
      }

      writer.println("</formulaField>");
      writer.println("</formulaInfo>");
   }

   /**
    * Parse the xml segment.
    */
   @Override
   public void parseXML(Element node) throws Exception {
      if(node != null) {
         String qname = Tool.getAttribute(node, "source");
         int type = 0;

         if(Tool.getAttribute(node, "type") != null) {
            type = Integer.parseInt(Tool.getAttribute(node, "type"));
         }

         String prefix = Tool.getAttribute(node, "prefix");
         source = new SourceInfo(type, prefix, qname);

         Element fnode = Tool.getChildNodeByTagName(node, "formulaField");
         Element ref = Tool.getChildNodeByTagName(fnode, "dataRef");

         if(ref != null) {
            field = (FormulaField) Class.forName(
               Tool.getAttribute(ref, "class")).newInstance();
            field.parseXML(ref);
         }
      }
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "FormulaInfo[source: " + source.getSource() + ", " + "field :" +
         field + "]";
   }

   /**
    * Get the cloned object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         FormulaInfo info = (FormulaInfo) super.clone();

         if(source != null) {
            info.source = (SourceInfo) source.clone();
         }

         if(field != null) {
            info.field = (FormulaField) field.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone formula info", ex);
      }

      return null;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(FormulaInfo.class);
   private SourceInfo source;
   private FormulaField field;
}

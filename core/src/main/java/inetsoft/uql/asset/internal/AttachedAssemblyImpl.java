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
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.AttachedAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * AttachedAssemblyImpl implements <tt>AttactedAssembly</tt>.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AttachedAssemblyImpl implements AttachedAssembly {

   /**
    * Constructor.
    */
   public AttachedAssemblyImpl() {
      super();
   }

   /**
    * Get the attached type.
    * @return the attached type.
    */
   @Override
   public int getAttachedType() {
      return type;
   }

   /**
    * Set the attached type.
    * @param type the specified type.
    */
   @Override
   public void setAttachedType(int type) {
      this.type = type;
   }

   /**
    * Get the attached source.
    * @return the attached source.
    */
   @Override
   public SourceInfo getAttachedSource() {
      return source;
   }

   /**
    * Set the attached source.
    * @param info the specified source.
    */
   @Override
   public void setAttachedSource(SourceInfo info) {
      this.source = info;
   }

   /**
    * Get the attached attribute.
    * @return the attached attribute.
    */
   @Override
   public DataRef getAttachedAttribute() {
      return attr;
   }

   /**
    * Set the attached attribute.
    * @param attr the specified attribute.
    */
   @Override
   public void setAttachedAttribute(DataRef attr) {
      this.attr = attr;
   }

   /**
    * Get the attached data type.
    * @return the attached data type.
    */
   @Override
   public String getAttachedDataType() {
      return dtype;
   }

   /**
    * Set the attached data type.
    * @return the attached data type.
    */
   @Override
   public void setAttachedDataType(String dtype) {
      this.dtype = dtype;
   }

   /**
    * Check if the attached assembly is valid.
    */
   @Override
   public void isAttachedValid() throws Exception {
      Catalog catalog = Catalog.getCatalog();

      if((type & SOURCE_ATTACHED) != 0) {
         if(source == null) {
            MessageException ex = new MessageException(catalog.getString(
               "common.sourceNull"));
            throw ex;
         }
      }

      if((type & COLUMN_ATTACHED) == COLUMN_ATTACHED) {
         if(attr == null) {
            MessageException ex = new MessageException(catalog.getString(
                "common.attachedAssembly.columnNull"));
            throw ex;
         }
      }

      if((type & DATA_TYPE_ATTACHED) == DATA_TYPE_ATTACHED) {
         if(dtype == null) {
            MessageException ex = new MessageException(catalog.getString(
               "common.attachedAssembly.dataTypeNull"));
            throw ex;
         }
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<attachedAssembly type=\"" + type + "\">");

      if(source != null) {
         writer.print("<source>");
         source.writeXML(writer);
         writer.println("</source>");
      }

      if(attr != null) {
         attr.writeXML(writer);
      }

      if(dtype != null) {
         writer.print("<dataType>");
         writer.print("<![CDATA[" + dtype + "]]>");
         writer.println("</dataType>");
      }

      writer.println("</attachedAssembly>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      type = Integer.parseInt(Tool.getAttribute(elem, "type"));

      Element snode = Tool.getChildNodeByTagName(elem, "source");

      if(snode != null) {
         snode = Tool.getFirstChildNode(snode);
         source = new SourceInfo();
         source.parseXML(snode);
      }

      dtype = Tool.getChildValueByTagName(elem, "dataType");

      Element anode = Tool.getChildNodeByTagName(elem, "dataRef");

      if(anode != null) {
         attr = AbstractDataRef.createDataRef(anode);
      }
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
         return null;
      }
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof AttachedAssembly)) {
         return false;
      }

      AttachedAssembly attached = (AttachedAssembly) obj;
      return attached.getAttachedType() == type &&
         Tool.equals(attached.getAttachedSource(), source) &&
         Tool.equals(attached.getAttachedAttribute(), attr) &&
         Tool.equals(attached.getAttachedDataType(), dtype);
   }

   private int type;
   private SourceInfo source;
   private DataRef attr;
   private String dtype;

   private static final Logger LOG =
      LoggerFactory.getLogger(AttachedAssemblyImpl.class);
}

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
package inetsoft.uql.xmla;

import inetsoft.uql.XCubeMember;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * An DimMember represents a dimension level in an OLAP
 * cube.
 *
 * @author  InetSoft Technology
 * @since   10.1
 */
public class DimMember implements XCubeMember {
   /**
    * Get the name of this dimension level.
    *
    * @return the name of this dimension level.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Set the name of this dimension level.
    *
    * @param name the name of this dimension level.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the unique name of this dimension level.
    *
    * @return the unique name of this dimension level.
    */
   public String getUniqueName() {
      return uniqueName;
   }

   /**
    * Set the unique name of this dimension level.
    *
    * @param uniqueName the unique name of this dimension level.
    */
   public void setUniqueName(String uniqueName) {
      this.uniqueName = uniqueName;
   }

   /**
    * Get the caption of this dimension level.
    *
    * @return the caption of this dimension level.
    */
   public String getCaption() {
      return memberCaption;
   }

   /**
    * Set the caption of this dimension level.
    *
    * @param memberCaption the caption of this dimension level.
    */
   public void setCaption(String memberCaption) {
      this.memberCaption = memberCaption;
   }

   /**
    * Get the level number of this dimension level.
    *
    * @return the level number of this dimension level.
    */
   public int getNumber() {
      return number;
   }

   /**
    * Set the level number of this dimension level.
    *
    * @param number the level number of this dimension level.
    */
   public void setNumber(int number) {
      this.number = number;
   }

   /**
    * Get the data reference of this dimension level.
    *
    * @return an XDataRef object.
    */
   @Override
   public DataRef getDataRef() {
      return ref;
   }

   /**
    * Set the data reference of this dimension level.
    *
    * @param ref XDataRef object.
    */
   public void setDataRef(DataRef ref) {
      this.ref = ref;
   }

   /**
    * Get the XMetaInfo of this cube member.
    *
    * @return the XMetaInfo of this cube member.
    */
   @Override
   public XMetaInfo getXMetaInfo() {
      return meta;
   }

   /**
    * Set the XMetaInfo of this dimension level.
    *
    * @param meta the XMetaInfo object.
    */
   public void setXMetaInfo(XMetaInfo meta) {
      this.meta = meta;
   }

   /**
    * Get the data type of this dimension level.
    * This will be one of the data type
    * constants defined in <code>inetsoft.uql.schema.XSchema</code>.
    *
    * @return the data type of this dimension level.
    */
   @Override
   public String getType() {
      return meta != null && meta.isAsDate() ?
         XSchema.TIME_INSTANT : XSchema.STRING;
   }

   /**
    * Get the folder of this measure.
    *
    * @return folder of this measure.
    */
   @Override
   public String getFolder() {
      // not currently implemented
      return null;
   }

   @Override
   public String getDateLevel() {
      return this.dateLevel;
   }

   @Override
   public String getOriginalType() {
      return originalType;
   }

   @Override
   public void setOriginalType(String originalType) {
      this.originalType = originalType;
   }

   @Override
   public void setDateLevel(String dateLevel) {
      this.dateLevel = dateLevel;
   }

   /**
    * Write an XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<DimMember name=\"" + Tool.byteEncode(name) + "\"");
      writer.print(" uniqueName=\"" + Tool.byteEncode(uniqueName) + "\"");
      writer.print(" memberCaption=\"" + Tool.byteEncode(getCaption()) + "\"");
      writer.print(" number=\"" + number + "\"");
      writer.print(">");
      writer.print("<DataRef>");

      if(ref != null) {
         ref.writeXML(writer);
      }

      writer.print("</DataRef>");

      if(meta != null) {
         meta.writeXML(writer);
      }

      writer.print("</DimMember>");
   }

   /**
    * Read in the definition of this object from an XML tag.
    *
    * @param tag the XML Element representing this object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      name = Tool.getAttribute(tag, "name");
      name = Tool.byteDecode(name);
      uniqueName = Tool.getAttribute(tag, "uniqueName");
      uniqueName = Tool.byteDecode(uniqueName);
      memberCaption = Tool.getAttribute(tag, "memberCaption");
      memberCaption = Tool.byteDecode(memberCaption);
      number = Integer.parseInt(Tool.getAttribute(tag, "number"));
      Element refNode = Tool.getChildNodeByTagName(tag, "DataRef");

      if(refNode != null && refNode.getFirstChild() != null) {
         ref = AbstractDataRef.createDataRef((Element) refNode.getFirstChild());
      }

      Element mElem = Tool.getChildNodeByTagName(tag, "XMetaInfo");

      if(mElem != null) {
         meta = new XMetaInfo();
         meta.parseXML(mElem);
      }
   }

   /**
    * Create a copy of this object.
    *
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         DimMember member = (DimMember) super.clone();

         if(ref != null) {
            member.ref = (DataRef) ref.clone();
         }

         return member;
      }
      catch(CloneNotSupportedException e) {
         LOG.error(e.getMessage(), e);
      }

      return null;
   }

   private String name = null;
   private String uniqueName = null;
   private String memberCaption = null;
   private int number;
   private DataRef ref = null;
   private XMetaInfo meta;
   private String originalType = null;
   private String dateLevel = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(DimMember.class);
}
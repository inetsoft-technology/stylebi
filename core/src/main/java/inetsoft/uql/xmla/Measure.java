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
package inetsoft.uql.xmla;

import inetsoft.uql.XCubeMember;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Measure represents a measure in an OLAP cube.
 *
 * @author  InetSoft Technology
 * @since   10.1
 */
public class Measure implements XCubeMember {
   /**
    * Get the name of this cube member.
    *
    * @return the name of this cube member.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Set the name of this cube member.
    *
    * @param name the name of this cube member.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the unique name of this cube member.
    *
    * @return the unique name of this cube member.
    */
   public String getUniqueName() {
      return uniqueName;
   }

   /**
    * Set the unique name of this cube member.
    *
    * @param uniqueName the unique name of this cube member.
    */
   public void setUniqueName(String uniqueName) {
      this.uniqueName = uniqueName;
   }

   /**
    * Get the caption of this cube member.
    *
    * @return the caption of this cube member.
    */
   public String getCaption() {
      return caption == null ? name : caption;
   }

   /**
    * Set the caption of this cube member.
    *
    * @param caption the caption of this cube member.
    */
   public void setCaption(String caption) {
      this.caption = caption;
   }

   /**
    * Get the data reference of this cube member.
    *
    * @return an XDataRef object.
    */
   @Override
   public DataRef getDataRef() {
      return ref;
   }

   /**
    * Set the data reference of this cube member.
    *
    * @param ref XDataRef object.
    */
   public void setDataRef(DataRef ref) {
      this.ref = ref;
   }

   /**
    * Get the data type of this cube member. This will be one of the data type
    * constants defined in <code>inetsoft.uql.schema.XSchema</code>.
    *
    * @return the data type of this cube member.
    */
   @Override
   public String getType() {
      return type;
   }

   /**
    * Set the data type of this cube member.  This will be one of the data type
    * constants defined in <code>inetsoft.uql.schema.XSchema</code>.
    *
    * @param type data type of this cube member.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Get the folder of this measure.
    *
    * @return folder of this measure.
    */
   @Override
   public String getFolder() {
      return folder;
   }

   /**
    * Set the folder of this measure.
    *
    * @param f folder for this measure.
    */
   public void setFolder(String f) {
      this.folder = f;
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
    * Set the XMetaInfo of this cube member.
    *
    * @param meta the XMetaInfo object.
    */
   public void setXMetaInfo(XMetaInfo meta) {
      this.meta = meta;
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
   public String getDateLevel() {
      return null;
   }

   @Override
   public void setDateLevel(String dateLevel) {
   }

   /**
    * Write an XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<Measure name=\"" + Tool.byteEncode(name) + "\"");
      writer.print(" uniqueName=\"" + Tool.byteEncode(uniqueName) + "\"");
      writer.print(" caption=\"" + Tool.byteEncode(getCaption()) + "\"");
      writer.print(" type=\"" + type + "\"");

      if(folder != null) {
         writer.print(" folder=\"" + Tool.byteEncode(folder) + "\"");
      }

      writer.print(">");
      writer.print("<DataRef>");

      if(ref != null) {
         ref.writeXML(writer);
      }

      writer.print("</DataRef>");

      if(meta != null) {
         meta.writeXML(writer);
      }

      writer.print("</Measure>");
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
      caption = Tool.getAttribute(tag, "caption");
      caption = Tool.byteDecode(caption);
      type = Tool.getAttribute(tag, "type");
      folder = Tool.getAttribute(tag, "folder");
      folder = Tool.byteDecode(folder);
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
         Measure m = (Measure) super.clone();

         if(ref != null) {
            m.ref = (DataRef) ref.clone();
         }

         return m;
      }
      catch(CloneNotSupportedException e) {
         LOG.error("Failed to clone Measure", e);
      }

      return null;
   }

   private String name = null;
   private String uniqueName = null;
   private String caption = null;
   private DataRef ref = null;
   private String type = null;
   private String folder = null;
   private XMetaInfo meta;
   private String originalType = null;

   private static final Logger LOG = LoggerFactory.getLogger(Measure.class);
}

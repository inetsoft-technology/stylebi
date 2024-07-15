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
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.SourceInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * BoundTableAssemblyInfo stores bound table assembly information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class BoundTableAssemblyInfo extends TableAssemblyInfo {
   /**
    * Constructor.
    */
   public BoundTableAssemblyInfo() {
      super();

      source = new SourceInfo();
   }

   /**
    * Get the source info.
    * @return the source info of the bound table assembly.
    */
   public SourceInfo getSourceInfo() {
      return source;
   }

   /**
    * Set the source info.
    * @param source the specified source info.
    */
   public void setSourceInfo(SourceInfo source) {
      this.source = source;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.println("<source>");
      source.writeXML(writer);
      writer.println("</source>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element snode = Tool.getChildNodeByTagName(elem, "source");
      snode = Tool.getFirstChildNode(snode);
      source.parseXML(snode);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         BoundTableAssemblyInfo info = (BoundTableAssemblyInfo) super.clone();
         info.source = (SourceInfo) source.clone();
         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private SourceInfo source;

   private static final Logger LOG =
      LoggerFactory.getLogger(BoundTableAssemblyInfo.class);
}

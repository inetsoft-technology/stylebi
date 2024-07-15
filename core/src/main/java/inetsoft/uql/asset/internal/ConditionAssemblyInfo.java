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
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * ConditionAssemblyInfo stores basic condition assembly information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class ConditionAssemblyInfo extends WSAssemblyInfo {
   /**
    * Constructor.
    */
   public ConditionAssemblyInfo() {
      super();
   }

   /**
    * Get the attached source.
    * @return the attached source.
    */
   public SourceInfo getAttachedSource() {
      return source;
   }

   /**
    * Set the attached source.
    * @param source the specified source.
    */
   public void setAttachedSource(SourceInfo source) {
      this.source = source;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(source != null) {
         writer.println("<source>");
         source.writeXML(writer);
         writer.println("</source>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element snode = Tool.getChildNodeByTagName(elem, "source");

      if(snode != null) {
         source = new SourceInfo();
         snode = Tool.getFirstChildNode(snode);
         source.parseXML(snode);
      }
   }

   private SourceInfo source;
}

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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * VSEditableAssemblyLayout stores assembly information for an editable
 * assembly layout which is added for header or footer region of printlayout.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class VSEditableAssemblyLayout extends VSAssemblyLayout {
   /**
    * Constructor.
    */
   public VSEditableAssemblyLayout() {
      super();
      this.info = createInfo();
   }

   /**
    * Constructor.
    */
   public VSEditableAssemblyLayout(VSAssemblyInfo info, String name,
      Point position, Dimension size)
   {
      super(name, position, size);
      this.info = info;
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   private VSAssemblyInfo createInfo() {
      return new VSAssemblyInfo();
   }

   /**
    * Get assembly info.
    */
   public VSAssemblyInfo getInfo() {
      return info != null ? info : new VSAssemblyInfo();
   }

   /**
    * Set assembly info.
    */
   public void setInfo(VSAssemblyInfo info) {
      this.info = info;
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      getInfo().writeXML(writer);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element inode = Tool.getChildNodeByTagName(elem, "assemblyInfo");

      if(inode != null) {
         info = (VSAssemblyInfo) AssemblyInfo.createAssemblyInfo(inode);
      }
   }

   private VSAssemblyInfo info;
}
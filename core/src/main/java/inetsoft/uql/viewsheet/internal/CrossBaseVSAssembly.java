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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public abstract class CrossBaseVSAssembly extends TableDataVSAssembly
   implements CrosstabDataVSAssembly {

   public CrossBaseVSAssembly() {
      super();
   }

   public CrossBaseVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   public CrossBaseVSAssemblyInfo getCrossBaseAssemblyInfo() {
      return (CrossBaseVSAssemblyInfo) getInfo();
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);
      CrossBaseVSAssemblyInfo crossBaseAssemblyInfo = getCrossBaseAssemblyInfo();
      CrosstabTree crosstabTree = crossBaseAssemblyInfo.getCrosstabTree();

      if(crosstabTree != null) {
         writer.println("<state_crosstabTree>");
         crosstabTree.writeXML(writer);
         writer.println("</state_crosstabTree>");
      }
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseStateContent(Element elem, boolean runtime) throws Exception {
      super.parseStateContent(elem, runtime);
      CrossBaseVSAssemblyInfo crossBaseAssemblyInfo = getCrossBaseAssemblyInfo();

      Element cTreeNode = Tool.getChildNodeByTagName(elem, "state_crosstabTree");

      if(cTreeNode != null) {
         cTreeNode = Tool.getFirstChildNode(cTreeNode);
         CrosstabTree crosstabTree = crossBaseAssemblyInfo.getCrosstabTree();
         crosstabTree.parseXML(cTreeNode);
      }
   }
}

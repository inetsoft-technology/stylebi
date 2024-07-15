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
package inetsoft.analytic.composition.command;

import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * Show data tooltip command.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ShowDataTipCommand extends ViewsheetCommand {
   /**
    * Constructor.
    */
   public ShowDataTipCommand() {
      super();
   }

   /**
    * Constructor.
    * @param info the assembly info.
    * @param childInfos the children info(s) of the param info, if the info is
    *  group container info, the childInfos will exist, otherwise will be null.
    * @param conds the condition of the data tip view.
    * @param pName the name of the object whose data tip view info is param info.
    */
   public ShowDataTipCommand(VSAssemblyInfo info, VSAssemblyInfo[] childInfos,
                             String conds, String pName) {
      this();
      put("condition", conds);
      put("pName", pName);
      this.info = info;
      this.childInfos = childInfos;
   }

   /**
    * Get main object info.
    */
   public VSAssemblyInfo getMainInfo() {
      return info;
   }

   /**
    * Get children info.
    */
   public VSAssemblyInfo[] getChildInfos() {
      return childInfos;
   }

   public String getAbsoluteName() {
      return info != null ? info.getAbsoluteName() : null;
   }

   public String[] getChildrenNames() {
      if(childInfos == null) {
         return new String[0];
      }

      String[] names = new String[childInfos.length];

      for(int i = 0; i < childInfos.length; i++) {
         names[i] = childInfos[i].getAbsoluteName();
      }

      return names;
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.println("<info>");
      info.writeXML(writer);
      writer.println("</info>");

      if(childInfos != null) {
         writer.println("<childInfos>");

         for(int i = 0; i < childInfos.length; i++) {
            childInfos[i].writeXML(writer);
         }

         writer.println("</childInfos>");
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);

      Element infoNode = Tool.getChildNodeByTagName(tag, "info");
      info = (VSAssemblyInfo) AssemblyInfo.createAssemblyInfo(infoNode);
      Element infosNode = Tool.getChildNodeByTagName(tag, "childInfos");

      if(infosNode != null) {
         NodeList infosNodeList = infosNode.getChildNodes();
         childInfos = new VSAssemblyInfo[infosNodeList.getLength()];

         for(int i = 0; i < childInfos.length; i++) {
            AssemblyInfo info =
               AssemblyInfo.createAssemblyInfo((Element) infosNodeList.item(i));
            childInfos[i] = (VSAssemblyInfo) info;
         }
      }
   }

   private VSAssemblyInfo info;
   private VSAssemblyInfo[] childInfos;
}

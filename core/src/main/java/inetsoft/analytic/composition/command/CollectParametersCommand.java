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
package inetsoft.analytic.composition.command;

import inetsoft.report.composition.command.CollectVariablesCommand;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * Collect parameters.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CollectParametersCommand extends ViewsheetCommand {
   /**
    * Constructor.
    */
   public CollectParametersCommand() {
      super();
   }

   /**
    * Constructor.
    * @param variables user variables.
    * @param name assembly name.
    * @param recursive touch dependency or not.
    */
   public CollectParametersCommand(UserVariable[] variables, String name,
                                  boolean recursive) {
      this();
      put("name", name);
      put("recursive", "" + recursive);
      infos = new CollectVariablesCommand.VariableInfo[variables.length];

      for(int i = 0; i < infos.length; i++) {
         CollectVariablesCommand.VariableInfo info =
            new CollectVariablesCommand.VariableInfo();
         info.setUserVariable(variables[i]);
         infos[i] = info;
      }
   }

   /**
    * Write contents of asset variable.
    * @writer the specified output stream.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(infos != null) {
         writer.println("<infos>");

         for(int i = 0; i < infos.length; i++) {
            CollectVariablesCommand.VariableInfo info = infos[i];
            info.writeXML(writer);
         }

         writer.println("</infos>");
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);

      Element infosNode = Tool.getChildNodeByTagName(tag, "infos");

      if(infosNode != null) {
         NodeList infosNodeList = infosNode.getChildNodes();
         infos =
            new CollectVariablesCommand.VariableInfo[infosNodeList.getLength()];

         for(int i = 0; i < infos.length; i++) {
            CollectVariablesCommand.VariableInfo info =
               new CollectVariablesCommand.VariableInfo();
            info.parseXML((Element) infosNodeList.item(i));
            infos[i] = info;
         }
      }
   }

   /**
    * Get variable infos.
    */
   public CollectVariablesCommand.VariableInfo[] getVariableInfos() {
      return infos;
   }

   public String[] getDisNames() {
      if(vsInfo == null) {
         return new String[0];
      }
      return vsInfo.getDisabledVariables();
   }

   public boolean isDisableParameterSheet() {
      if(vsInfo == null) {
         return false;
      }

      return vsInfo.isDisableParameterSheet();
   }

   public void setViewsheetInfo(ViewsheetInfo vsInfo) {
      this.vsInfo = vsInfo;
   }

   private CollectVariablesCommand.VariableInfo[] infos;
   private ViewsheetInfo vsInfo;
}

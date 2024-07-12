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
package inetsoft.report.composition.command;

import inetsoft.report.composition.*;
import inetsoft.report.composition.command.CollectVariablesCommand.VariableInfo;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;

/**
 * Collect connection valiables.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class CollectConnectionVariablesCommand extends AssetCommand {
   /**
    * Constructor.
    */
   public CollectConnectionVariablesCommand() {
      super();
   }

   /**
    * Constructor.
    * @param variables user variables.
    * @param event the specified asset event.
    */
   public CollectConnectionVariablesCommand(UserVariable[] variables,
                                            AssetEvent event) {
      this();
      put("event", event);
      infos = new VariableInfo[variables.length];

      for(int i = 0; i < infos.length; i++) {
         VariableInfo info = new VariableInfo();
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
            VariableInfo info = infos[i];
            info.writeXML(writer);
         }

         writer.println("</infos>");
      }
   }

   /**
    * Write contents of asset variable.
    * @param dos the specified output stream.
    */
   @Override
   protected void writeContents2(DataOutputStream dos) {
      try {
         dos.writeBoolean(infos == null);

         if(infos != null) {
            dos.writeInt(infos.length);

            for(int i = 0; i < infos.length; i++) {
               VariableInfo info = infos[i];
               info.writeData(dos);
            }
         }
      }
      catch (IOException e){
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
         infos = new VariableInfo[infosNodeList.getLength()];

         for(int i = 0; i < infos.length; i++) {
            VariableInfo info = new VariableInfo();
            info.parseXML((Element) infosNodeList.item(i));
            infos[i] = info;
         }
      }
   }

   private VariableInfo[] infos;
}

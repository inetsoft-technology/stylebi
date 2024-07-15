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
package inetsoft.report.composition.command;

import inetsoft.report.composition.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;

/**
 * Refresh assembly tree command.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class RefreshAssemblyTreeCommand extends AssetCommand {
   /**
    * Constructor.
    */
   public RefreshAssemblyTreeCommand() {
      super();
   }

   /**
    * Constructor.
    * @param model asset tree model.
    */
   public RefreshAssemblyTreeCommand(AssemblyTreeModel model) {
      super();
      this.model = model;
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(model != null) {
         writer.println("<model>");
         model.writeXML(writer);
         writer.println("</model>");
      }
   }

   /**
    * Write the contents of this object.
    * @param dos the output stream to which to write the data.
    */
   @Override
   protected void writeContents2(DataOutputStream dos) {
      try {
         dos.writeBoolean(model == null);

         if(model != null) {
            model.writeData(dos);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to write command", ex);
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element mnode = Tool.getChildNodeByTagName(tag, "model");

      if(mnode != null) {
         mnode = Tool.getFirstChildNode(mnode);
         model = new AssemblyTreeModel();
         model.parseXML(mnode);
      }
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      try {
         boolean val = super.parseData(input);

         if(!val) {
            return false;
         }

         boolean nmodel = input.readBoolean();

         if(!nmodel) {
            model = new AssemblyTreeModel();
            model.parseData(input);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to read command", ex);
         return false;
      }

      return true;
   }

   protected AssemblyTreeModel model;
   private static final Logger LOG =
      LoggerFactory.getLogger(RefreshAssemblyTreeCommand.class);
}

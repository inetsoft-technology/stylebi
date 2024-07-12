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
package inetsoft.analytic.composition.command;

import inetsoft.report.composition.AssetCommand;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.ItemList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.PrintWriter;

/**
 * Set base worksheet tree model command.
 *
 * @version 8.5, 08/01/2006
 * @author InetSoft Technology Corp
 */
public class SetWSTreeModelCommand extends AssetCommand {
   /**
    * Constructor.
    */
   public SetWSTreeModelCommand() {
      super();
   }

   /**
    * Constructor.
    * @param model asset tree model.
    */
   public SetWSTreeModelCommand(AssetTreeModel model) {
      this.model = model;
   }

   /**
    * Constructor.
    * @param model asset tree model.
    * @param fields the refs need to be gray out.
    */
   public SetWSTreeModelCommand(AssetTreeModel model, DataRef[] fields) {
      this(model);
      
      ItemList items = new ItemList();

      for(int i = 0; fields != null && i < fields.length; i++) {
         items.addItem(fields[i]);
      }

      put("gfields", items);
   }

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

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      
      if(model != null) {
         model.writeXML(writer);
      }
   }

   private AssetTreeModel model;

   private static final Logger LOG =
      LoggerFactory.getLogger(SetWSTreeModelCommand.class);
}

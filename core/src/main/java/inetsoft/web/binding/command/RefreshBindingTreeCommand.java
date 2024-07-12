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
package inetsoft.web.binding.command;

import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.viewsheet.command.ViewsheetCommand;

/**
 * Command that instructs the client to remove an assembly object.
 *
 * @since 12.3
 */
public class RefreshBindingTreeCommand implements ViewsheetCommand {
   /**
    * Construstor.
    */
   public RefreshBindingTreeCommand(TreeNodeModel treeModel) {
      this.treeModel = treeModel;
   }
   
   /**
    * Get the tree model.
    * @return the tree model.
    */
   public TreeNodeModel getTreeModel() {
      return treeModel;
   }

   /**
    * Set tree model.
    * @param treeModel the tree model.
    */
   public void setTreeModel(TreeNodeModel treeModel) {
      this.treeModel = treeModel;
   }
   
   private TreeNodeModel treeModel;
}

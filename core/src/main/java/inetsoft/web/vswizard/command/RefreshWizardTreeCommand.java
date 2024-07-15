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
package inetsoft.web.vswizard.command;

import inetsoft.web.binding.command.RefreshBindingTreeCommand;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.vswizard.model.VSWizardTreeInfoModel;

public class RefreshWizardTreeCommand extends RefreshBindingTreeCommand implements TimeSensitiveCommand {

   public RefreshWizardTreeCommand() {
      super(null);
   }

   public RefreshWizardTreeCommand(TreeNodeModel treeModel) {
      super(treeModel);
   }

   public RefreshWizardTreeCommand(TreeNodeModel treeModel, VSWizardTreeInfoModel treeInfo,
                                   boolean reload, boolean forceRefresh)
   {
      super(treeModel);
      this.treeInfo = treeInfo;
      this.reload = reload;
      this.forceRefresh = forceRefresh;
   }

   public VSWizardTreeInfoModel getTreeInfo() {
      return treeInfo;
   }

   public void setTreeInfo(VSWizardTreeInfoModel treeInfo) {
      this.treeInfo = treeInfo;
   }

   public boolean isReload() {
      return reload;
   }

   public void setReload(boolean reload) {
      this.reload = reload;
   }

   public void setForceRefresh(boolean forceRefresh) { this.forceRefresh = forceRefresh; }

   public boolean isForceRefresh() { return forceRefresh; }

   private VSWizardTreeInfoModel treeInfo;
   private boolean reload;
   private boolean forceRefresh = false;
}

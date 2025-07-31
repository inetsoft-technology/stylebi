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
package inetsoft.uql.asset.sync;


import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetObject;

import java.io.Serializable;

public class RenameTransformFinishedEvent implements Serializable {
   public RenameTransformFinishedEvent() {
   }

   public RenameTransformFinishedEvent(AssetObject asset, RenameDependencyInfo dinfo) {
      entry = asset;
      dependencyInfo = dinfo;
      this.orgID = OrganizationManager.getInstance().getCurrentOrgID();
   }

   public RenameDependencyInfo getDependencyInfo() {
      return dependencyInfo;
   }

   public void setDependencyInfo(RenameDependencyInfo dependencyInfo) {
      this.dependencyInfo = dependencyInfo;
   }

   public AssetObject getEntry() {
      return entry;
   }

   public void setEntry(AssetObject asset) {
      this.entry = asset;
   }

   public String getOrgID() {
      return orgID;
   }

   public void setOrgID(String orgID) {
      this.orgID = orgID;
   }

   public void setReload(boolean load) {
      this.reload = load;
   }

   public boolean isReload() {
      return this.reload;
   }


   private AssetObject entry;
   private String orgID;
   private boolean reload;
   private RenameDependencyInfo dependencyInfo;
}

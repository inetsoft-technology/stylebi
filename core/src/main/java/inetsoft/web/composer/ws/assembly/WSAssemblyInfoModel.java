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
package inetsoft.web.composer.ws.assembly;

import inetsoft.uql.asset.internal.WSAssemblyInfo;

public class WSAssemblyInfoModel {
   public WSAssemblyInfoModel(WSAssemblyInfo info) {
      this.editable = info.isEditable();
   }

   public boolean getEditable() {
      return editable;
   }

   public void setEditable(boolean editable) {
      this.editable = editable;
   }

   public WSMirrorAssemblyInfoModel getMirrorInfo() {
      return mirrorInfo;
   }

   public void setMirrorInfo(WSMirrorAssemblyInfoModel mirrorInfo) {
      this.mirrorInfo = mirrorInfo;
   }

   private boolean editable;
   private WSMirrorAssemblyInfoModel mirrorInfo;
}

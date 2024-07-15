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
package inetsoft.web.composer.ws.event;

import javax.annotation.Nullable;

public class WSRefreshAssemblyEvent implements AssetEvent {
   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   public boolean isRecursive() {
      return recursive;
   }

   public void setRecursive(boolean recursive) {
      this.recursive = recursive;
   }

   public boolean isReset() {
      return reset;
   }

   public void setReset(boolean reset) {
      this.reset = reset;
   }

   @Nullable
   @Override
   public String name() {
      return null;
   }

   @Override
   public boolean confirmed() {
      return confirmed;
   }

   public void setConfirmed(boolean confirmed) {
      this.confirmed = confirmed;
   }

   private String assemblyName;
   private boolean recursive;
   private boolean reset;
   private boolean confirmed;
}

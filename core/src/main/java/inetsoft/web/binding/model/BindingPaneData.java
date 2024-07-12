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
package inetsoft.web.binding.model;

public class BindingPaneData {
   public BindingPaneData() {
   }

   public BindingPaneData(String runtimeId, boolean useMeta) {
      this.runtimeId = runtimeId;
      this.useMeta = useMeta;
   }

   /**
    * @return if the new created runtime sheet id.
    */
   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   /**
    * @return if current vs using meta.
    */
   public boolean getUseMeta() {
      return useMeta;
   }

   public void setUseMeta(boolean useMeta) {
      this.useMeta = useMeta;
   }

   private String runtimeId;
   private boolean useMeta;
}

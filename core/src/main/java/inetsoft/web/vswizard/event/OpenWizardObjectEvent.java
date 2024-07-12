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
package inetsoft.web.vswizard.event;

import javax.annotation.Nullable;

public class OpenWizardObjectEvent {
   public int getX() {
      return x;
   }

   public void setX(int x) {
      this.x = x;
   }

   public int getY() {
      return y;
   }

   public void setY(int y) {
      this.y = y;
   }

   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   public void setBindingOption(String bindingOption) {
      this.bindingOption = bindingOption;
   }

   public String getBindingOption() {
      return bindingOption;
   }

   public String getAssemblyName() {
      return assemblyName;
   }

   @Nullable
   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   private int x;
   private int y;
   private String runtimeId;
   private String assemblyName;
   private String bindingOption;
}

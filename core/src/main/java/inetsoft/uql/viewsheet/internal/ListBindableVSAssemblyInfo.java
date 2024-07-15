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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.viewsheet.ListBindingInfo;

/**
 * ListBindableVSAssemblyInfo, the assembly info of a list bindable assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface ListBindableVSAssemblyInfo extends BindableVSAssemblyInfo {
   /**
    * Get the list binding info.
    * @return the list binding info of this assembly info.
    */
   public ListBindingInfo getListBindingInfo();

   /**
    * Set the list binding info to this assembly info.
    * @param info the specified list binding info.
    */
   public void setListBindingInfo(ListBindingInfo info);
}

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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.viewsheet.ScalarBindingInfo;

/**
 * ScalarBindableVSAssemblyInfo, the assembly info of a scalar bindable
 * assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface ScalarBindableVSAssemblyInfo extends BindableVSAssemblyInfo {
   /**
    * Get the scalar binding info.
    * @return the scalar binding info of this assembly info.
    */
   public ScalarBindingInfo getScalarBindingInfo();

   /**
    * Set the scalar binding info to this assembly info.
    * @param info the specified scalar binding info.
    */
   public void setScalarBindingInfo(ScalarBindingInfo info);
}

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
package inetsoft.web.binding.model;

import inetsoft.web.binding.drm.DataRefModel;

/**
 * BindingRefModel, the design time binding data ref, which might be converted to
 * GroupRef, ColumnRef, AggregateRef, and the like at runtime.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public interface BindingRefModel extends DataRefModel {
   /**
    * Set the full name of this BindingRefModel.
    *
    * @param fullName, the full name to be set.
    */
   public void setFullName(String fullName);

   /**
    * Get the full name of this BindingRefModel. It might not be the same as
    * the name of the contained data ref. For example, a VSAggregateRef
    * might has a full name like "Sum of Sales".
    *
    * @return the full name of this BindingRefModel.
    */
   public String getFullName();
}

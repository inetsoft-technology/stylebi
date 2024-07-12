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
package inetsoft.uql.viewsheet;

import inetsoft.uql.erm.DataRef;

/**
 * VSDataRef, the design time viewsheet data ref, which might be converted to
 * GroupRef, ColumnRef, AggregateRef, and the like at runtime.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface VSDataRef extends DataRef {
   /**
    * Get the full name of this VSDataRef. It might not be the same as
    * the name of the contained data ref. For example, a VSAggregateRef
    * might has a full name like "Sum of Sales".
    * @return the full name of this VSDataRef.
    */
   public String getFullName();
}

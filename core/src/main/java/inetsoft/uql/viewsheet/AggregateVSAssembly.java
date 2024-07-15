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
package inetsoft.uql.viewsheet;

import inetsoft.uql.ConditionList;

/**
 * AggregateVSAssembly defines the common API for assemblies that are based
 * on aggregate data.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface AggregateVSAssembly {
   /**
    * Get the detail condition list.
    * @return the detail condition list.
    */
   public abstract ConditionList getDetailConditionList();

   /**
    * Set the detail condition list.
    * @param detail the specified detail condition list.
    * @return the changed hint.
    */
   public abstract int setDetailConditionList(ConditionList detail);
}

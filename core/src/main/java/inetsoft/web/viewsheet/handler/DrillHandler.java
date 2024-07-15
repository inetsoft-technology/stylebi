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
package inetsoft.web.viewsheet.handler;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.DrillFilterVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.web.viewsheet.model.DrillFilterAction;
import inetsoft.web.viewsheet.service.CommandDispatcher;

import java.security.Principal;

public interface DrillHandler <T extends DrillFilterVSAssembly, R extends DrillFilterAction> {
   /**
    * Whether this handles the drill for the assembly.
    */
   boolean isHandler(VSAssembly obj);

   /**
    * Process drill filter
    */
   void processDrillFilter(T assembly, R drillFilterInfo,
                           CommandDispatcher dispatcher, String linkUri,
                           Principal principal) throws Exception;

   /**
    * Process drill action
    * @param assembly target assembly
    * @param drillFilterAction drill and filter param info
    */
   void processDrillAction(T assembly, DrillFilterAction drillFilterAction,
                           DrillFilterVSAssembly targetAssembly,
                           CommandDispatcher dispatcher, String linkUri,
                           Principal principal) throws Exception;

   /**
    * Remove drill filter of assembly by dimension ref.
    * @param dataRef data ref. this should is dimension ref.
    * @param assembly assembly.
    * @param removeSelf <tt>true</tt> remove self condition, otherwise, remove parent.
    * @param parent true to remove all parents conditions.
    */
   void removeDrillFilter(DataRef dataRef, T assembly, boolean removeSelf, boolean parent);

   /**
    * Get a field (dimension or aggregate) by full name.
    */
   DataRef getFieldByName(T assembly, String field);
}

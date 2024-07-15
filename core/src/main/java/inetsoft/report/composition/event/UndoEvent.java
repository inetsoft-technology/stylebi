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
package inetsoft.report.composition.event;

import inetsoft.report.composition.*;
import inetsoft.util.Catalog;

/**
 * Undo event.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class UndoEvent extends WorksheetEvent {
   /**
    * Constructor.
    */
   public UndoEvent() {
      super();
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Undo");
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return false;
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      return null;
   }

   /**
    * Process undo event.
    */
   @Override
   public void process(RuntimeWorksheet rws, AssetCommand command)
      throws Exception
   {
      rws.undo(null);
      AssetEventUtil.refreshWorksheet(rws, getID(), command, false, false);
      AssetEventUtil.refreshDateRange(rws.getWorksheet(), command);
   }
}

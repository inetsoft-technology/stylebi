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
package inetsoft.report.composition.event;

import inetsoft.report.composition.*;
import inetsoft.util.Catalog;

/**
 * Refresh date range provider event.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RefreshDateRangeEvent extends WorksheetEvent {
   /**
    * Constructor.
    */
   public RefreshDateRangeEvent() {
      super();
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Refresh Date Ranges");
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
      return new String[0];
   }

   /**
    * Process refresh datarange event.
    */
   @Override
   public void process(RuntimeWorksheet rws, AssetCommand command)
      throws Exception
   {
      AssetEventUtil.refreshDateRange(rws.getWorksheet(), command);
   }
}

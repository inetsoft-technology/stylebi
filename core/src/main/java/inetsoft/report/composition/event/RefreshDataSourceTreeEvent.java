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

import inetsoft.report.composition.AssetCommand;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.command.RefreshTreeCommand2;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Catalog;
/**
 * Refresh data source tree event.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class RefreshDataSourceTreeEvent extends RefreshTreeEvent {
   /**
    * Constructor.
    */
   public RefreshDataSourceTreeEvent() {
      super();
   }

   /**
    * Constructor.
    */
   public RefreshDataSourceTreeEvent(AssetEntry[] entries) {
      super(entries);
   }

   /**
    * Constructor.
    */
   public RefreshDataSourceTreeEvent(AssetEntry[] entries, String dataSource)
   {
      this(entries);
      put("dataSource", dataSource);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Refresh Data Source Tree");
   }

   /**
    * Process this event.
    */
   @Override
   public void process(AssetRepository engine, AssetCommand command)
      throws Exception
   {
      AssetTreeModel model = AssetEventUtil.refreshDataSourceTree(engine, getUser(), this);
      command.addCommand(new RefreshTreeCommand2(model));
   }
}
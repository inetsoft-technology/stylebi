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
package inetsoft.web.composer.vs.event;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;

/**
 * Class that encapsulates the parameters for opening a viewsheet.
 *
 * @since 12.3
 */
public class NewViewsheetEvent extends OpenViewsheetEvent {
   /**
    * Gets the asset entry of the data source.
    *
    * @return the entry identifier.
    */
   public AssetEntry getDataSource() {
      return dataSource;
   }

   /**
    * Sets the asset entry of the data source.
    *
    * @param dataSource the data source entry.
    */
   public void setDataSource(AssetEntry dataSource) {
      this.dataSource = dataSource;
   }

   @Override
   public String toString() {
      return "OpenViewsheetEvent{" +
         "dataSource='" + dataSource + "\'}";
   }

   private AssetEntry dataSource;
}

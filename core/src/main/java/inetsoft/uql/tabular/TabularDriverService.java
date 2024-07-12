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
package inetsoft.uql.tabular;

/**
 * This class is the API of a service for tabular driver.
 *
 * @version 12.3, 2/26/2018
 * @author InetSoft Technology Corp
 */
public interface TabularDriverService {
   /**
    * Check if this driver matches the data source.
    */
   public boolean matches(TabularDataSource xds);

   /**
    * Get the executor to execute tabular queries.
    */
   public TabularExecutor getTabularExecutor();
}

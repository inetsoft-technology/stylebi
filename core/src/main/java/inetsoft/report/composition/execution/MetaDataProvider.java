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
package inetsoft.report.composition.execution;

/**
 * This class defines the API for the table (worksheet) meta data provider.
 * A meta data provider contains methods for retrieve table meta data, either
 * from cached data or realtime processing.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface MetaDataProvider {
   /**
    * Get the table meta data.
    * @param tname a table assembly name.
    */
   public TableMetaData getTableMetaData(String tname) throws Exception;
}

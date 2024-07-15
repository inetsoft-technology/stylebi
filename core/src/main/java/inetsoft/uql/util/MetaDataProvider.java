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
package inetsoft.uql.util;

import inetsoft.uql.XDataSource;
import inetsoft.uql.XNode;
import inetsoft.uql.util.rgraph.TableNode;

/**
 * Provides table metadata to consumers.
 *
 * @author  InetSoft Technology
 * @since   6.0
 */
public interface MetaDataProvider {
   /**
    * Get the datasource associated with this property pane.
    */
   public XDataSource getDataSource();

   /**
    * Get the metadata of the specified table.
    *
    * @param table an XNode with the same name as the table being queried.
    *
    * @return a TableNode object describing the table and its columns.
    */
   public TableNode getTableMetaData(XNode table);

   /**
    * Get the primary keys of a table.
    */
   public XNode getPrimaryKeys(XNode table) throws Exception;
}

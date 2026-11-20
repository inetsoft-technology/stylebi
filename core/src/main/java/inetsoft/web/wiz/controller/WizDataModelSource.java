/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.controller;

import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.JDBCDataSource;

/**
 * Resolves the data model of a database (JDBC) data source for the data model routes; XMLA and
 * tabular sources have none and are refused by name (design A14).
 */
class WizDataModelSource {
   WizDataModelSource(XRepository repository) {
      this.repository = repository;
   }

   /**
    * Returns the data source's data model.
    *
    * @throws WizDataModelErrors.DmFailure {@code NOT_FOUND} for a missing source (or one without a
    *         data model), {@code NOT_JDBC} for a source that is not a database.
    */
   XDataModel requireJdbcDataModel(String database) throws Exception {
      XDataSource source = repository.getDataSource(database);

      if(source == null) {
         throw new WizDataModelErrors.DmFailure(
            WizDataModelWire.NOT_FOUND, "database", "no data source named \"" + database + "\"",
            null, null);
      }

      if(!(source instanceof JDBCDataSource)) {
         throw new WizDataModelErrors.DmFailure(
            WizDataModelWire.NOT_JDBC, "database",
            "data models exist only on database (JDBC) data sources; \"" + database +
               "\" is a " + source.getType() + " data source", null, null);
      }

      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new WizDataModelErrors.DmFailure(
            WizDataModelWire.NOT_FOUND, "database",
            "data source \"" + database + "\" has no data model", null, null);
      }

      return dataModel;
   }

   private final XRepository repository;
}

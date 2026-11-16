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

package inetsoft.web.wiz.service;

/**
 * Thrown when a data source that is answering did not finish a metadata request within the time
 * budget (for example a column scan over a very large schema). Unlike
 * {@link DatasourceUnreachableException} it does not mean the data source is down.
 */
public class DatasourceBudgetExceededException extends Exception {
   public DatasourceBudgetExceededException(String datasource, long budgetSeconds) {
      super("Data source '" + datasource + "' exceeded the " + budgetSeconds +
            " s time budget while reading its metadata; narrow the request and retry");
      this.datasource = datasource;
   }

   /**
    * Gets the name of the data source.
    */
   public String getDatasource() {
      return datasource;
   }

   private final String datasource;
}

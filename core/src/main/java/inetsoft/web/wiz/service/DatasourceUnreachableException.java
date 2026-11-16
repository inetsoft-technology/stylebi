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
 * Thrown when a JDBC data source cannot supply its metadata in time, or its metadata request has
 * already failed. The message names the data source so a caller can tell an unreachable source
 * apart from a table that really does not exist.
 */
public class DatasourceUnreachableException extends Exception {
   public DatasourceUnreachableException(String datasource, String detail) {
      super("Data source '" + datasource + "' is unreachable: " + detail);
      this.datasource = datasource;
      this.detail = detail;
   }

   /**
    * Gets the name of the data source that could not be reached.
    */
   public String getDatasource() {
      return datasource;
   }

   /**
    * Gets the reason, without the data source prefix.
    */
   public String getDetail() {
      return detail;
   }

   private final String datasource;
   private final String detail;
}

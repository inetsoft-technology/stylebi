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
 * Raised when wiz metadata/annotation is requested for a datasource that exists but is not
 * relational (e.g. MongoDB or another tabular/NoSQL datasource). These sources have no
 * JDBC-style catalog/schema/table metadata for StyleBI to serve, so the request fails by
 * design rather than by a transient error.
 *
 * Distinguishing this from "the datasource doesn't exist at all" lets callers show a clear,
 * actionable message instead of the generic "not found" — see
 * DatasourceMetaApiController.handleUnsupportedDatasource, which maps this to HTTP 422 with a
 * structured body instead of letting the underlying failure surface as a raw 500.
 */
public class UnsupportedDatasourceException extends Exception {
   public UnsupportedDatasourceException(String datasourceName, String datasourceType) {
      // XDataSource.getType() can in principle return null for some implementations. Normalize
      // once and reuse everywhere below — the message ("...for 'null' datasources...") isn't the
      // only thing at stake: wiz-services' handler for this exception treats a non-empty
      // datasourceType as the discriminator that confirms a 422 really is this case, so a null
      // here would also make that response silently fail to be recognized as friendly at all.
      this.datasourceName = datasourceName;
      this.datasourceType = datasourceType != null ? datasourceType : "unknown";
   }

   @Override
   public String getMessage() {
      return "Operation failed. Annotations are currently not supported for '" + datasourceType +
         "' datasources. Please check the datasource type or contact your administrator.";
   }

   public String getDatasourceName() {
      return datasourceName;
   }

   public String getDatasourceType() {
      return datasourceType;
   }

   private final String datasourceName;
   private final String datasourceType;
}

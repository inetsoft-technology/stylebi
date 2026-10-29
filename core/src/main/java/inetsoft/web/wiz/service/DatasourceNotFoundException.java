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
 * Raised when a wiz request names a datasource that is not registered at all. It is the
 * counterpart of {@link UnsupportedDatasourceException} (the datasource exists but is not
 * relational) and deliberately not a subclass of it: callers that catch the unsupported case
 * must not also swallow a missing datasource.
 *
 * Being a distinct type lets {@link inetsoft.web.wiz.WizControllerErrorHandler} answer with a
 * 4xx that names the datasource, instead of the generic server-error text a bare
 * {@code Exception} receives.
 *
 * The message keeps the words "Data source ... not found": wiz-services'
 * {@code isUnsupportedDatasourceError} fallback matches them on the StyleBI error text.
 */
public class DatasourceNotFoundException extends Exception {
   public DatasourceNotFoundException(String datasourceName) {
      super("Data source '" + datasourceName + "' not found.");
      this.datasourceName = datasourceName;
   }

   public String getDatasourceName() {
      return datasourceName;
   }

   private final String datasourceName;
}

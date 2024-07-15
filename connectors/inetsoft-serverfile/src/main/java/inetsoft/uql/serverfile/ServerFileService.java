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
package inetsoft.uql.serverfile;

import inetsoft.uql.tabular.TabularService;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Tabular service used to pull data from local files.
 */
public class ServerFileService extends TabularService {
   @Override
   public String getDataSourceType() {
      return ServerFileDataSource.TYPE;
   }

   @Override
   public String getDataSourceClass() {
      return "inetsoft.uql.serverfile.ServerFileDataSource";
   }

   @Override
   public String getQueryClass() {
      return "inetsoft.uql.serverfile.ServerFileQuery";
   }

   @Override
   public String getRuntimeClass() {
      return "inetsoft.uql.serverfile.ServerFileRuntime";
   }

   @Override
   public String getDisplayLabel(Locale locale) {
      return ResourceBundle.getBundle("inetsoft.uql.serverfile.Bundle", locale)
         .getString("display.name");
   }
}

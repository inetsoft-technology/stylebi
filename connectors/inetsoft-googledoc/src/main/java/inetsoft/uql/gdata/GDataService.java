/*
 * inetsoft-googledoc - StyleBI is a business intelligence web application.
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
package inetsoft.uql.gdata;

import inetsoft.uql.tabular.TabularService;

import java.util.Locale;
import java.util.ResourceBundle;

public class GDataService extends TabularService {
   public String getDataSourceType() {
      return GDataDataSource.TYPE;
   }

   public String getDataSourceClass() {
      return "inetsoft.uql.gdata.GDataDataSource";
   }

   public String getQueryClass() {
      return "inetsoft.uql.gdata.GDataQuery";
   }

   public String getRuntimeClass() {
      return "inetsoft.uql.gdata.GDataRuntime";
   }

   @Override
   public String getDisplayLabel(Locale locale) {
      return ResourceBundle.getBundle("inetsoft.uql.gdata.Bundle", locale)
         .getString("display.name");
   }
}

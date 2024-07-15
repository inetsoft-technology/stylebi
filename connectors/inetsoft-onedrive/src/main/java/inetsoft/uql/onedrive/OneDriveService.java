/*
 * inetsoft-onedrive - StyleBI is a business intelligence web application.
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
package inetsoft.uql.onedrive;

import inetsoft.uql.tabular.TabularService;

import java.util.*;

public class OneDriveService extends TabularService {
   @Override
   public String getDataSourceType() {
      return OneDriveDataSource.TYPE;
   }

   @Override
   public String getDataSourceClass() {
      return "inetsoft.uql.onedrive.OneDriveDataSource";
   }

   @Override
   public String getQueryClass() {
      return "inetsoft.uql.onedrive.OneDriveQuery";
   }

   @Override
   public String getRuntimeClass() {
      return "inetsoft.uql.onedrive.OneDriveRuntime";
   }

   @Override
   public String getDisplayLabel(Locale locale) {
      String label;

      try {
         ResourceBundle bundle = getResourceBundle(locale);
         label = bundle.getString(getDataSourceType());
      }
      catch(MissingResourceException ignore) {
         label = getDataSourceType();
      }

      return label;
   }
}

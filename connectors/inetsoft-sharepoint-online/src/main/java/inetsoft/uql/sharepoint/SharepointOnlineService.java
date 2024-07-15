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
package inetsoft.uql.sharepoint;

import inetsoft.uql.tabular.TabularService;

import java.util.*;

public class SharepointOnlineService extends TabularService {
   @Override
   public String getDataSourceType() {
      return SharepointOnlineDataSource.TYPE;
   }

   @Override
   public String getDataSourceClass() {
      return "inetsoft.uql.sharepoint.SharepointOnlineDataSource";
   }

   @Override
   public String getQueryClass() {
      return "inetsoft.uql.sharepoint.SharepointOnlineQuery";
   }

   @Override
   public String getRuntimeClass() {
      return "inetsoft.uql.sharepoint.SharepointOnlineRuntime";
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

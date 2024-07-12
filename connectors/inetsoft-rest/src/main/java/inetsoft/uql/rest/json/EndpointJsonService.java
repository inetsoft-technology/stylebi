/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.json;

import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.tabular.TabularService;

import java.util.*;

public abstract class EndpointJsonService extends TabularService {
   protected EndpointJsonService(String type,
                                 Class<? extends AbstractRestDataSource> dataSourceClass,
                                 Class<? extends EndpointJsonQuery<?>> queryClass)
   {
      this.type = type;
      this.dataSourceClass = dataSourceClass.getName();
      this.queryClass = queryClass.getName();
   }

   @Override
   public String getDataSourceType() {
      return type;
   }

   @Override
   public String getDataSourceClass() {
      return dataSourceClass;
   }

   @Override
   public String getQueryClass() {
      return queryClass;
   }

   @Override
   public String getRuntimeClass() {
      return EndpointJsonRuntime.class.getName();
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

   private final String type;
   private final String dataSourceClass;
   private final String queryClass;
}

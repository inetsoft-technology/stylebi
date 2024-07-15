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
package inetsoft.uql.rest.json;

import inetsoft.uql.DataSourceListing;
import inetsoft.uql.XDataSource;
import inetsoft.uql.rest.AbstractRestDataSource;

import java.lang.reflect.Field;

public abstract class EndpointJsonListing<T extends AbstractRestDataSource>
   extends DataSourceListing
{
   protected EndpointJsonListing(Class<T> dataSourceClass, String category) {
      super(getType(dataSourceClass), category, getIcon(dataSourceClass));
      this.dataSourceClass = dataSourceClass;
   }

   @Override
   public XDataSource createDataSource() throws Exception {
      T dataSource = dataSourceClass.getConstructor().newInstance();
      dataSource.setName(getAvailableName());
      return dataSource;
   }

   private static String getType(Class<? extends AbstractRestDataSource> dataSourceClass) {
      try {
         Field field = dataSourceClass.getDeclaredField("TYPE");
         field.setAccessible(true);
         return (String) field.get(null);
      }
      catch(Exception e) {
         throw new RuntimeException(
            "Data source class " + dataSourceClass.getName() + "is missing the TYPE field", e);
      }
   }

   private static String getIcon(Class<? extends AbstractRestDataSource> dataSourceClass) {
      return "/" + dataSourceClass.getPackage().getName().replace('.', '/') +
         "/icon.svg";
   }

   private final Class<T> dataSourceClass;
}

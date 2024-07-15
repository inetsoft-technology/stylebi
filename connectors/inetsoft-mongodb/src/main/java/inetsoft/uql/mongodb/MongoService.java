/*
 * inetsoft-mongodb - StyleBI is a business intelligence web application.
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
package inetsoft.uql.mongodb;

import inetsoft.uql.tabular.TabularService;

import java.util.Locale;
import java.util.ResourceBundle;

public class MongoService extends TabularService {
   public String getDataSourceType() {
      return MongoDataSource.TYPE;
   }

   public String getDataSourceClass() {
      return "inetsoft.uql.mongodb.MongoDataSource";
   }

   public String getQueryClass() {
      return "inetsoft.uql.mongodb.MongoQuery";
   }

   public String getRuntimeClass() {
      return "inetsoft.uql.mongodb.MongoRuntime";
   }

   @Override
   public String getDisplayLabel(Locale locale) {
      return ResourceBundle.getBundle("inetsoft.uql.mongodb.Bundle", locale)
         .getString("display.name");
   }
}

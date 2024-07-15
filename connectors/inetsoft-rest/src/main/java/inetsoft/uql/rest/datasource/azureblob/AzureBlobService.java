/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.datasource.azureblob;

import inetsoft.uql.rest.xml.RestXMLService;

import java.util.Locale;

public class AzureBlobService extends RestXMLService {
   @Override
   public String getRuntimeClass() {
      return super.getRuntimeClass();
   }

   @Override
   public String getDataSourceType() {
      return AzureBlobDataSource.TYPE;
   }

   @Override
   public String getDataSourceClass() {
      return AzureBlobDataSource.class.getName();
   }

   @Override
   public String getQueryClass() {
      return AzureBlobQuery.class.getName();
   }

   @Override
   public String getDisplayLabel(Locale locale) {
      return "Azure Blob";
   }
}

/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.viewsheet.service;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.util.Catalog;

import java.io.File;
import java.util.List;
import java.util.Set;

public interface ImportXLSService {
   void updateViewsheet(File excelFile, String type, RuntimeViewsheet rvs, String linkUri,
                        CommandDispatcher dispatcher, PlaceholderService placeholderService,
                        Catalog catalog, List<String> assemblies, Set<String> notInRange)
      throws Exception;

   static ImportXLSService getInstance() {
      try {
         Class<?> clazz = ImportXLSService.class.getClassLoader()
            .loadClass("inetsoft.web.viewsheet.service.PoiImportXLSService");
         return (ImportXLSService) clazz.getConstructor().newInstance();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create parser instance", e);
      }
   }
}

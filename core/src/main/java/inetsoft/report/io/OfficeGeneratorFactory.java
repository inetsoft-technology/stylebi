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
package inetsoft.report.io;

import java.io.OutputStream;

/**
 * Factory interface for classes that create instances of MS Office file format generators.
 */
public interface OfficeGeneratorFactory {
   AbstractGenerator createExcelGenerator(OutputStream output);

   AbstractGenerator createExcelGenerator(OutputStream output, String version);

   AbstractGenerator createExcelSheetGenerator(OutputStream output);

   AbstractGenerator createExcelDataGenerator(OutputStream output);

   AbstractGenerator createExcelOnlyDataGenerator(OutputStream output);

   AbstractGenerator createPowerpointGenerator(OutputStream output);

   static OfficeGeneratorFactory getInstance() {
      try {
         Class<?> clazz = OfficeGeneratorFactory.class.getClassLoader()
            .loadClass("inetsoft.report.io.PoiOfficeGeneratorFactory");
         return (OfficeGeneratorFactory) clazz.getConstructor().newInstance();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create factory instance", e);
      }
   }
}

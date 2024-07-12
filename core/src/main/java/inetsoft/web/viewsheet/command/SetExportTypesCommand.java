/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.viewsheet.command;

import inetsoft.util.Catalog;

import java.util.Arrays;

/**
 * Class used to send the available export types for a viewsheet to the client.
 *
 * @since 12.3
 */
public class SetExportTypesCommand implements ViewsheetCommand, CollapsibleCommand {
   public String[] getExportTypes() {
      return exportTypes;
   }

   public Object[] getExportLabels() {
      return exportLabels;
   }

   public void setExportTypes(String[] exportTypes) {
      this.exportTypes = exportTypes;
      this.exportLabels = Arrays.stream(exportTypes)
         .map(t -> Catalog.getCatalog().getString(t))
         .toArray();
   }

   private String[] exportTypes;
   private Object[] exportLabels;
}

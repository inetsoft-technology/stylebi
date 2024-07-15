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
package inetsoft.report.lib.physical;

import inetsoft.report.lib.logical.LogicalLibrary;
import inetsoft.report.style.XTableStyle;

public class TableStyleWriter extends AbstractLibraryAssetWriter<XTableStyle> {
   public TableStyleWriter(PhysicalLibrary physicalLibrary,
                           LogicalLibrary<XTableStyle> logicalLibrary)
   {
      super(physicalLibrary, logicalLibrary, new TableStyleWriterStrategy());
   }

   @Override
   protected String getFailedMessage(String name) {
      return "Failed to write table style: " + name;
   }
}

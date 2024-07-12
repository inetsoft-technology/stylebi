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
package inetsoft.report.lib.physical;

import inetsoft.report.lib.ScriptEntry;
import inetsoft.report.lib.logical.LogicalLibrary;

public class ScriptWriter extends AbstractLibraryAssetWriter<ScriptEntry> {
   public ScriptWriter(PhysicalLibrary physicalLibrary,
                       LogicalLibrary<ScriptEntry> logicalLibrary)
   {
      super(physicalLibrary, logicalLibrary, new ScriptWriterStrategy());
   }

   @Override
   protected String getFailedMessage(String name) {
      return "Failed to write script: " + name;
   }
}

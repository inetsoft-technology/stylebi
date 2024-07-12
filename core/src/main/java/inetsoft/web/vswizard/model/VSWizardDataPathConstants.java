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
package inetsoft.web.vswizard.model;

import inetsoft.report.TableDataPath;
import inetsoft.uql.schema.XSchema;

public final class VSWizardDataPathConstants {
   /**
    * Data path for detail cells.
    */
   public static final TableDataPath DETAIL = new TableDataPath(-1,
      TableDataPath.DETAIL, XSchema.STRING, new String[0], true, false);

   /**
    * Basic data path for header cells.
    */
   public static final TableDataPath GROUP_HEADER_CELL = new TableDataPath(0,
         TableDataPath.GROUP_HEADER, XSchema.STRING, new String[0], true, false);
}

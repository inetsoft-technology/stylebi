/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
export const enum SourceInfoType {
   /**
    * A normal query source.
    */
   QUERY = 0,
   /**
    * A data model source.
    */
   MODEL = 1,
   /**
    * A source from other bindable report elements in the same report.
    */
   REPORT = 2,
   /**
    * A normal source from report parameters.
    */
   PARAMETER = 3,
   /**
    * A local query source.
    */
   LOCAL_QUERY = 4,
   /**
    * A cube source.
    */
   CUBE = 5,
   /**
    * An embedded data source.
    */
   EMBEDDED_DATA = 6,
   /**
    * An asset source.
    */
   ASSET = 7,
   /**
    * A physical table source.
    */
   PHYSICAL_TABLE = 8,
   /**
    * A source from other bindable assembly elements in the same viewsheet.
    */
   VS_ASSEMBLY = 64,
   /**
    * NULL source.
    */
   NONE = -1
}

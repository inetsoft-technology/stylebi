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
package inetsoft.report.composition.execution;

import inetsoft.uql.erm.DataRef;

import java.util.List;
import java.util.Objects;

/**
 * Value class for identifying the selection path for a given table and data refs.
 *
 * @since 13.1
 */
public class SelectionPathIdentifier {
   public SelectionPathIdentifier(String tableName, List<DataRef> refs) {
      this.tableName = tableName;
      this.dataRefs = refs;
   }

   public String getTableName() {
      return tableName;
   }

   public List<DataRef> getDataRefs() {
      return dataRefs;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      SelectionPathIdentifier that = (SelectionPathIdentifier) o;
      return Objects.equals(tableName, that.tableName) &&
         Objects.equals(dataRefs, that.dataRefs);
   }

   @Override
   public int hashCode() {
      return Objects.hash(tableName, dataRefs);
   }

   private final String tableName;
   private final List<DataRef> dataRefs;
}

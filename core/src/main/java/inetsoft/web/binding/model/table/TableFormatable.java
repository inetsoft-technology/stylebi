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
package inetsoft.web.binding.model.table;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.report.TableDataPath;

@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.PROPERTY,
   property = "type")
@JsonSubTypes({
   @JsonSubTypes.Type(value = TableCell.class, name = "cell"),
   @JsonSubTypes.Type(value = TableRow.class, name = "row"),
   @JsonSubTypes.Type(value = TableColumn.class, name = "col")
})
public class TableFormatable {
   /**
    * Get format.
    * @return the format.
    */
   public TableFormatInfo getFormat() {
      return format;
   }

   /**
    * Set format.
    * @param format the format.
    */
   public void setFormat(TableFormatInfo format) {
      this.format = format;
   }

   public TableDataPath getCellPath() {
      return this.cellPath;
   }

   public void setCellPath(TableDataPath cellPath) {
      this.cellPath = cellPath;
   }

   private TableDataPath cellPath;
   private TableFormatInfo format;
}

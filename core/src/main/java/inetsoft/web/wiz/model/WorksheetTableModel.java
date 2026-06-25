/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.wiz.model.WorksheetTableResponse.ColumnData;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

import java.io.Serializable;
import java.util.List;

@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableWorksheetTableModel.class)
@JsonDeserialize(as = ImmutableWorksheetTableModel.class)
public interface WorksheetTableModel extends Serializable {
   String tableName();
   List<String> baseTables();

   /**
    * "physical table" | "mirror table" | "relational join table"
    * (constant strings from StyleBI's TableType enum)
    */
   String tableType();

   List<ColumnData> columns();

   /**
    * Rich metadata used by wiz services, including column lineage.
    */
   List<WorksheetColumnInfo> primaryColumnMetas();

   /**
    * True when the table has GROUP BY and aggregate information.
    */
   boolean hasAggregate();

   /**
    * True when the table has pre-aggregate, post-aggregate, or ranking conditions.
    */
   boolean hasCondition();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableWorksheetTableModel.Builder {
   }
}

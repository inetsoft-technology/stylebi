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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

/**
 * One worksheet table, mirroring the TypeScript {@code WorksheetTableModel} shape consumed
 * by the Wiz AI service. Produced by {@code WorksheetTableService.getWorksheetModel}.
 */
@Value.Immutable
@Serial.Structural
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonSerialize(as = ImmutableWorksheetTableModel.class)
@JsonDeserialize(as = ImmutableWorksheetTableModel.class)
public interface WorksheetTableModel extends Serializable {
   String name();

   @Nullable String description();

   @Nullable List<String> baseTables();

   /**
    * "physical table" | "mirror table" | "relational join table" | "sql query table"
    * (constant strings from StyleBI's TableType enum)
    */
   String tableType();

   List<WorksheetColumnData> columns();

   /** Join definitions; present only for relational join tables. */
   @Nullable List<JoinPath> joinPaths();

   /** GROUP BY + aggregate specification; null when the table is not aggregated. */
   @Nullable WorksheetAggregateInfo aggregateInfo();

   /** WHERE-equivalent conditions, applied before GROUP BY. */
   @Nullable List<VisualizationConditionModel.ConditionNode> preAggregateCondition();

   /** HAVING-equivalent conditions, applied after GROUP BY. */
   @Nullable List<VisualizationConditionModel.ConditionNode> postAggregateCondition();

   /** Top / bottom-N ranking filter, applied last. */
   @Nullable List<VisualizationConditionModel.ConditionNode> rankingCondition();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableWorksheetTableModel.Builder {
   }
}

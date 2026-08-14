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
package inetsoft.web.wiz.binding;

import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-modify-write over {@code ChartBindingModel}.
 *
 * <p>Callers must pass the model returned by {@code VSBindingService.createModel} and mutate
 * it in place. Constructing a fresh model would drop every field this class does not set —
 * including the thirteen in {@link ChartBindingFields#AESTHETIC}, which
 * {@code ChangeChartRefEvent} round-trips whether or not a data-binding write means to touch
 * them.
 */
public final class ChartBindingMutator {
   /** Shelves this phase writes. Chart's specialized shelves arrive in 2b Phase 2. */
   public static final List<String> SHELVES = List.of("x", "y", "group");

   private ChartBindingMutator() {
   }

   public static void setShelf(ChartBindingModel model, String shelf, List<FieldRef> fields) {
      String name = shelf == null ? "" : shelf.trim().toLowerCase();

      if(!SHELVES.contains(name)) {
         throw new IllegalArgumentException(
            "Unknown chart shelf '" + shelf + "'. Valid shelves: " +
            String.join(", ", SHELVES) + ".");
      }

      List<ChartRefModel> refs = new ArrayList<>();

      for(FieldRef field : fields == null ? List.<FieldRef>of() : fields) {
         FieldRefFactory.requireType(field);
         refs.add(toChartRef(field));
      }

      switch(name) {
      case "x" -> model.setXFields(refs);
      case "y" -> model.setYFields(refs);
      default -> model.setGroupFields(refs);
      }
   }

   private static ChartRefModel toChartRef(FieldRef field) {
      if(FieldRefFactory.MEASURE.equalsIgnoreCase(field.type())) {
         ChartAggregateRefModel ref = new ChartAggregateRefModel();
         ref.setColumnValue(field.column());
         ref.setName(field.column());

         if(field.aggregate() != null) {
            ref.setFormula(field.aggregate());
         }

         return ref;
      }

      ChartDimensionRefModel ref = new ChartDimensionRefModel();
      ref.setColumnValue(field.column());
      ref.setName(field.column());

      if(field.dateLevel() != null) {
         ref.setDateLevel(field.dateLevel());
      }

      return ref;
   }
}

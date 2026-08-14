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

import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.List;

/** Converts between StyleBI's ref models and the agent-facing {@link FieldRef}. */
public final class FieldRefFactory {
   public static final String DIMENSION = "dimension";
   public static final String MEASURE = "measure";

   private static final List<String> TYPES = List.of(DIMENSION, MEASURE);

   private FieldRefFactory() {
   }

   public static FieldRef from(DataRefModel ref) {
      if(ref instanceof BAggregateRefModel aggregate) {
         return new FieldRef(aggregate.getColumnValue(), MEASURE, aggregate.getFormula(),
                             null, null);
      }

      if(ref instanceof BDimensionRefModel dimension) {
         return new FieldRef(dimension.getColumnValue(), DIMENSION, null,
                             dimension.getDateLevel(),
                             dimension.getNamedGroupInfo() == null
                                ? null : dimension.getNamedGroupInfo().getName());
      }

      return new FieldRef(ref == null ? null : ref.getName(), null, null, null, null);
   }

   /**
    * Fails loud when the discriminator is absent or unrecognized. Never defaults it: a ref
    * with a guessed role lands on the wrong shelf and renders plausibly wrong, which is the
    * failure this vocabulary exists to prevent.
    */
   public static void requireType(FieldRef ref) {
      String type = ref == null || ref.type() == null ? null : ref.type().trim().toLowerCase();

      if(type == null || !TYPES.contains(type)) {
         throw new IllegalArgumentException(
            "Field '" + (ref == null ? "?" : ref.column()) + "' needs a 'type' of " +
            String.join(" or ", TYPES) + ", got '" +
            (ref == null ? "null" : String.valueOf(ref.type())) + "'.");
      }
   }
}

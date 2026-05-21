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
package inetsoft.web.vswizard.recommender.chart;

import java.util.*;

/**
 * Carries the user's slot-field preferences for chart recommendation.
 * Keys are slot names ("x", "y", "group", "color", "shape", "size", "text");
 * values are the set of field names the user wants in that slot.
 */
public class ChartPreference {
   public ChartPreference(Map<String, Set<String>> slotFields) {
      this.slotFields = slotFields != null ? slotFields : Collections.emptyMap();
   }

   public Map<String, Set<String>> getSlotFields() {
      return slotFields;
   }

   public boolean isEmpty() {
      return slotFields.isEmpty();
   }

   private final Map<String, Set<String>> slotFields;
}

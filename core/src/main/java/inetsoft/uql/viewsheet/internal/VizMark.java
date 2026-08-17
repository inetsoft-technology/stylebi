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
package inetsoft.uql.viewsheet.internal;

/**
 * An assembly's provenance mark: which modern-visualization gate was in force when it was created.
 * Absent (null) means legacy or unclaimed content, which no automatic behavior ever touches.
 */
public enum VizMark {
   MODERN_LIGHT("modern-light"),
   MODERN_DARK("modern-dark");

   VizMark(String value) {
      this.value = value;
   }

   /** The persisted form, written as the vizMark XML attribute. */
   public String value() {
      return value;
   }

   /**
    * The mark for an assembly created now, or null when the gate is off. Only consulted where an
    * assembly is genuinely being created; existing content keeps whatever it already carries.
    */
   public static VizMark fromGate() {
      if(!VSDensityDefaults.isModern()) {
         return null;
      }

      return VSDensityDefaults.isDark() ? MODERN_DARK : MODERN_LIGHT;
   }

   /**
    * Parse a persisted mark. Absent or unrecognized reads as unmarked, so a state written by a newer
    * build degrades to legacy rather than being guessed at.
    */
   public static VizMark parse(String value) {
      for(VizMark mark : values()) {
         if(mark.value.equals(value)) {
            return mark;
         }
      }

      return null;
   }

   private final String value;
}

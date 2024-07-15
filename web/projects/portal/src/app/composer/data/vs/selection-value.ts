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
export class SelectionValue {
   static STATE_SELECTED = 1;
   static STATE_INCLUDED = 2;
   static STATE_EXCLUDED = 4;
   static STATE_COMPATIBLE = 8;
   static DISPLAY_STATES = SelectionValue.STATE_SELECTED |
      SelectionValue.STATE_INCLUDED | SelectionValue.STATE_EXCLUDED;

   // Client-only state to keep track of excluded selections of assemblies which
   // don't submit on change.
   static STATE_WAS_EXCLUDED = 1024;

   public static isSelected(state: number): boolean {
      return (state & SelectionValue.STATE_SELECTED) !== 0;
   }

   public static isIncluded(state: number): boolean {
      return (state & SelectionValue.STATE_INCLUDED) !== 0;
   }

   public static isExcluded(state: number): boolean {
      return (state & SelectionValue.STATE_EXCLUDED) !== 0;
   }

   public static isCompatible(state: number): boolean {
      return (state & SelectionValue.STATE_COMPATIBLE) !== 0;
   }

   public static wasExcluded(state: number): boolean {
      return (state & SelectionValue.STATE_WAS_EXCLUDED) !== 0;
   }

   public static applyWasExcluded(state: number): number {
      return state | SelectionValue.STATE_WAS_EXCLUDED;
   }

   public static applyWasNotExcluded(state: number): number {
      return state & ~SelectionValue.STATE_WAS_EXCLUDED;
   }
}

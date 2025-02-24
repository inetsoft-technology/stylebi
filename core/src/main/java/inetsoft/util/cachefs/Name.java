/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.util.cachefs;

import java.util.Comparator;
import java.util.Objects;

final class Name {
   public static Name create(String display, String canonical) {
      return new Name(display, canonical);
   }

   private Name(String display, String canonical) {
      this.display = Objects.requireNonNull(display);
      this.canonical = Objects.requireNonNull(canonical);
   }

   @Override
   public boolean equals(Object o) {
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      Name name = (Name) o;
      return Objects.equals(canonical, name.canonical);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(canonical);
   }

   @Override
   public String toString() {
      return display;
   }

   static Comparator<Name> displayComparator() {
      return DISPLAY_COMPARATOR;
   }

   static final Name EMPTY = new Name("", "");
   public static final Name SELF = new Name(".", ".");
   public static final Name PARENT = new Name("..", "..");

   private final String display;
   private final String canonical;

   private static final Comparator<Name> DISPLAY_COMPARATOR = Comparator.comparing((Name n) -> n.display);
}

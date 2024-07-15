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
package inetsoft.report.lib.logical;

import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
public interface LogicalLibraryEntry<T> {
   T asset();

   @Nullable
   String comment();

   boolean audit();

   @Value.Default
   default long created(){
      return 0;
   }

   @Value.Default
   default long modified(){
      return 0;
   }

   @Nullable
   String createdBy();

   @Nullable
   String modifiedBy();

   static <T> Builder<T> builder() {
      return new Builder<>();
   }

   class Builder<T> extends ImmutableLogicalLibraryEntry.Builder<T> {
   }
}

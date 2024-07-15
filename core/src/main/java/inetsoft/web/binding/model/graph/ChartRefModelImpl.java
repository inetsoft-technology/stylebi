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
package inetsoft.web.binding.model.graph;

public class ChartRefModelImpl {
   /**
    * Set the original descriptor.
    */
   public void setOriginal(OriginalDescriptor original) {
      this.original = original;
   }

   /**
    * Get the original descriptor.
    */
   public OriginalDescriptor getOriginal() {
      return original;
   }

   public void setRefConvertEnabled(boolean refConvertEnabled) {
      this.refConvertEnabled = refConvertEnabled;
   }

   public boolean isRefConvertEnabled() {
      return refConvertEnabled;
   }

   private boolean refConvertEnabled;
   private OriginalDescriptor original;
}
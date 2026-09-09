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

package inetsoft.web.api.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.*;

public class DataCycleList {
   /**
    * Gets the list of data cycles.
    *
    * @return the cycle list.
    */
   @NotNull
   @Schema(description = "The data cycles.")
   public List<DataCycle> getCycles() {
      if(cycles == null) {
         cycles = new ArrayList<>();
      }

      return cycles;
   }

   /**
    * Sets the list of cycles.
    *
    * @param cycles the cycle list.
    */
   public void setCycles(List<DataCycle> cycles) {
      this.cycles = cycles;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      DataCycleList that = (DataCycleList) o;
      return Objects.equals(cycles, that.cycles);
   }

   @Override
   public int hashCode() {
      return Objects.hash(cycles);
   }

   @Override
   public String toString() {
      return "DataCycleList{" +
         "cycles=" + cycles +
         '}';
   }

   private List<DataCycle> cycles;
}

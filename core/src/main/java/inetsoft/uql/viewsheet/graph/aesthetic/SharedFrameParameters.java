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
package inetsoft.uql.viewsheet.graph.aesthetic;

import java.util.*;

/**
 * Holds an arbitrary list of parameters used to match frames that should be shared
 */
public class SharedFrameParameters {
   public void addParameter(Object parameter) {
      parameters.add(parameter);
   }

   public Set<Object> getParameters() {
      return Collections.unmodifiableSet(parameters);
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      SharedFrameParameters that = (SharedFrameParameters) o;
      return parameters.equals(that.parameters);
   }

   @Override
   public int hashCode() {
      return parameters.hashCode();
   }

   private final Set<Object> parameters = new HashSet<>();
}

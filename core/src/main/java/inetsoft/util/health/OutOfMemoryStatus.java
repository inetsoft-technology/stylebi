/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.health;

import java.io.Serializable;

public final class OutOfMemoryStatus implements Serializable {
   public OutOfMemoryStatus() {
      this(false, null);
   }

   public OutOfMemoryStatus(boolean outOfMemory, String time) {
      this.outOfMemory = outOfMemory;
      this.time = time;
   }

   public boolean isOutOfMemory() {
      return outOfMemory;
   }

   public String getTime() {
      return time;
   }

   @Override
   public String toString() {
      return "OutOfMemoryStatus{" +
         "outOfMemory=" + outOfMemory +
         ", time='" + time + '\'' +
         '}';
   }

   private final boolean outOfMemory;
   private final String time;
   private static final long serialVersionUID = 1L;
}

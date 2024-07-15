/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.health;

import java.io.Serializable;

public final class ReportFailureStatus implements Serializable {
   public ReportFailureStatus() {
      this(false, 0);
   }

   public ReportFailureStatus(boolean excessiveFailures, int failureCount) {
      this.excessiveFailures = excessiveFailures;
      this.failureCount = failureCount;
   }

   public boolean isExcessiveFailures() {
      return excessiveFailures;
   }

   public int getFailureCount() {
      return failureCount;
   }

   @Override
   public String toString() {
      return "ReportFailureStatus{" +
         "excessiveFailures=" + excessiveFailures +
         ", failureCount=" + failureCount +
         '}';
   }

   private final boolean excessiveFailures;
   private final int failureCount;
   private static final long serialVersionUID = 1L;
}

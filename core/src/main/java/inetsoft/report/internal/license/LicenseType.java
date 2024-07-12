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
package inetsoft.report.internal.license;

public enum LicenseType {
   INVALID(false, false, true),
   CPU(false, false, true),
   CONCURRENT_SESSION(true, false, true),
   NAMED_USER(true, false, true),
   VIEWER(true, true, true),
   NAMED_USER_VIEWER(true, true, true),
   SCHEDULER(false, false, false);

   private final boolean pooled;
   private final boolean viewer;
   private final boolean server;

   LicenseType(boolean pooled, boolean viewer, boolean server) {
      this.pooled = pooled;
      this.viewer = viewer;
      this.server = server;
   }

   public boolean isPooled() {
      return pooled;
   }

   public static LicenseType forTypeChar(char c) {
      switch(c) {
      case 'Q':
         return SCHEDULER;
      case 'X':
         return CPU;
      case 'S':
         return CONCURRENT_SESSION;
      case 'U':
         return NAMED_USER;
      default:
         return INVALID;
      }
   }
}

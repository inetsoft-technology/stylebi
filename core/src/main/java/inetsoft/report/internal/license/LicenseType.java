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
package inetsoft.report.internal.license;

public enum LicenseType {
   INVALID(false, false, true),
   CPU(false, false, true),
   CONCURRENT_SESSION(true, false, true),
   NAMED_USER(true, false, true),
   VIEWER(true, true, true),
   NAMED_USER_VIEWER(true, true, true),
   ELASTIC(true, false, true),
   HOSTED(true, false, true),
   COMMUNITY(false, false, false);

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
      return switch(c) {
         case 'X' -> CPU;
         case 'S' -> CONCURRENT_SESSION;
         case 'U' -> NAMED_USER;
         case 'E' -> ELASTIC;
         case 'H' -> HOSTED;
         case 'O' -> COMMUNITY;
         default -> INVALID;
      };
   }
}

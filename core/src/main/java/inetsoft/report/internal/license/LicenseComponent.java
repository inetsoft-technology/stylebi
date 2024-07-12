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

import java.util.EnumSet;
import java.util.Optional;

public enum LicenseComponent {
   ADHOC(0x0004, "inetsoft.analytic.adhoc.AdhocInitializer"),
   DASHBOARD(0x0002, "inetsoft.sree.web.dashboard.DashboardInitializer"),
   FORM(0x0080, "inetsoft.analytic.composition.FormInitializer"),
   MASTER(0x0040, null),
   PROFESSIONAL(0x0100, null),
   REPORT(0x1000, null),
   STANDALONE(0x0200, null),
   TEST(0x0400, null),
   VIEWER(0x0800, null),
   WORKSHEET(0x0001, "inetsoft.analytic.composition.DWInitializer");

   private final int flag;
   private final String initializerClass;

   LicenseComponent(int flag, String initializerClass) {
      this.flag = flag;
      this.initializerClass = initializerClass;
   }

   Optional<String> getInitializerClass() {
      return Optional.ofNullable(initializerClass);
   }

   public static EnumSet<LicenseComponent> forFlags(int flags) {
      EnumSet<LicenseComponent> components = EnumSet.noneOf(LicenseComponent.class);

      for(LicenseComponent component : values()) {
         int flag = component.flag;

         if((flags & flag) == flag) {
            components.add(component);
         }
      }

      return components;
   }
}

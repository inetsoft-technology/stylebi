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

package inetsoft.web.wiz;

import java.util.*;

/**
 * Permission strings of the Wiz product, stored in StyleBI as {@code ResourceType.WIZ_PERMISSION}
 * resources with the {@code ACCESS} action. The strings are identical to the ones in
 * wiz-services {@code entitlementService.ts} (ADMIN_PERMISSIONS and END_USER_PERMISSIONS).
 */
public final class WizPermissions {
   private WizPermissions() {
   }

   /** Administrative permissions: denied by default, held by system/site administrators. */
   public static final List<String> ADMIN = List.of(
      "config.read", "config.write", "config.write.critical",
      "entitlement.read", "entitlement.write",
      "annotation.approve", "annotation.edit", "annotation.exclude", "annotation.delete",
      "observability.read",
      "content.read", "content.delete",
      "audit.read");

   /** Ordinary-use permissions: allowed by default when no permission is defined. */
   public static final List<String> END_USER = List.of(
      "plugin.use",
      "portal.tab.chat", "portal.tab.visualizations", "portal.tab.datasources",
      "portal.tab.annotation", "portal.tab.annotationBrowser", "portal.tab.documents",
      "portal.tab.boards",
      "chart.save", "chart.saveToBoard", "chart.export",
      "visualization.delete", "visualization.rename",
      "board.delete",
      "datasource.create", "datasource.edit", "datasource.delete", "datasource.move",
      "document.create", "document.delete");

   public static final List<String> ALL;
   private static final Set<String> END_USER_SET = Set.copyOf(END_USER);

   static {
      List<String> all = new ArrayList<>(ADMIN);
      all.addAll(END_USER);
      ALL = Collections.unmodifiableList(all);
   }

   /** Whether the permission is an end-user permission (allowed unless a permission is defined). */
   public static boolean isEndUser(String permission) {
      return permission != null && END_USER_SET.contains(permission);
   }
}

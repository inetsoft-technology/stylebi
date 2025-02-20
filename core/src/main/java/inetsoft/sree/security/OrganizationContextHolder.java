/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.security;

import inetsoft.web.portal.model.database.StringWrapper;

public class OrganizationContextHolder {
   public static void setCurrentOrgId(String orgId) {
      THREAD_LOCAL.set(new StringWrapper(orgId));
   }

   public static String getCurrentOrgId() {
      StringWrapper wrapper = THREAD_LOCAL.get();
      return wrapper != null ? wrapper.getBody() : null;
   }

   public static void clear() {
      StringWrapper wrapper = THREAD_LOCAL.get();

      if(wrapper != null) {
         // Clear the body to ensure that the initial value of inherited threadlocal is also cleared,
         // thus avoiding interference caused by thread reuse.
         wrapper.setBody(null);
      }

      THREAD_LOCAL.remove();
   }

   private static final InheritableThreadLocal<StringWrapper> THREAD_LOCAL = new InheritableThreadLocal<>();
}

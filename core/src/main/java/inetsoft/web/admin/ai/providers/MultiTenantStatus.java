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
package inetsoft.web.admin.ai.providers;

// Wrapper class for AdminProviderController#getMultiTenantStatus (bug 76716 follow-up) -- a
// purpose-built read of SUtil.isMultiTenant() itself, so admin-chat's own DATABASE databaseSpec
// query-shape check does not have to go through the generic properties catalog/echo path (whose
// uncatalogued/never-set "exists: unknown" response is genuinely ambiguous with a misspelled
// property name) to learn this one boolean.
public class MultiTenantStatus {
   public MultiTenantStatus() {
   }

   public MultiTenantStatus(boolean multiTenant) {
      this.multiTenant = multiTenant;
   }

   public boolean isMultiTenant() {
      return multiTenant;
   }

   public void setMultiTenant(boolean multiTenant) {
      this.multiTenant = multiTenant;
   }

   private boolean multiTenant;
}

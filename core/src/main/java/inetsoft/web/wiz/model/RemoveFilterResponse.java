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
package inetsoft.web.wiz.model;

/** Response body for {@code POST /api/wiz/viewsheet/filters/remove}. */
public class RemoveFilterResponse {
   public RemoveFilterResponse() {
   }

   public RemoveFilterResponse(boolean removed) {
      this.removed = removed;
   }

   public boolean isRemoved() {
      return removed;
   }

   public void setRemoved(boolean removed) {
      this.removed = removed;
   }

   private boolean removed;
}

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
package inetsoft.web.wiz.service;

/** An explicit binding pin that cannot be honored. Maps to HTTP 400 with a structured body. */
public class UnsatisfiableBindingException extends IllegalArgumentException {
   public UnsatisfiableBindingException(String role, String field, String reason) {
      super("unsatisfiable explicit binding: " + role + "=" + field + " (" + reason + ")");
      this.role = role;
      this.field = field;
      this.reason = reason;
   }

   public String getRole() {
      return role;
   }

   public String getField() {
      return field;
   }

   public String getReason() {
      return reason;
   }

   private final String role;
   private final String field;
   private final String reason;
}

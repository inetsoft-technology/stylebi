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

import java.util.List;

/** An explicit binding pin that cannot be honored. Maps to HTTP 400 with a structured body. */
public class UnsatisfiableBindingException extends IllegalArgumentException {
   /** A single offending pin (role + field) for the multi-pin set-conflict case. */
   public record Pin(String role, String field) {}

   /**
    * Single-pin failure: this specific pin is invalid on its own (unknown slot,
    * wrong field type, missing field, etc.). The body names this one pin.
    */
   public UnsatisfiableBindingException(String role, String field, String reason) {
      // Use a placeholder for null role/field so the log message reads
      // "<missing>=amount" rather than "null=amount".
      super("unsatisfiable explicit binding: " + orMissing(role) + "=" + orMissing(field)
               + " (" + reason + ")");
      this.role = role;
      this.field = field;
      this.pins = List.of();
      this.reason = reason;
   }

   /**
    * Set-conflict failure: the pins cannot all be satisfied together, and no single
    * pin is necessarily the culprit (each may be individually valid). The body reports
    * every pin so the client isn't misled into "fixing" a pin that is already valid.
    */
   public UnsatisfiableBindingException(List<Pin> pins, String reason) {
      super("unsatisfiable explicit bindings: " + pins + " (" + reason + ")");
      this.role = null;
      this.field = null;
      this.pins = pins == null ? List.of() : List.copyOf(pins);
      this.reason = reason;
   }

   public String getRole() {
      return role;
   }

   public String getField() {
      return field;
   }

   /** The full set of conflicting pins, or empty for a single-pin failure. */
   public List<Pin> getPins() {
      return pins;
   }

   public String getReason() {
      return reason;
   }

   private static String orMissing(String value) {
      return value != null ? value : "<missing>";
   }

   private final String role;
   private final String field;
   private final List<Pin> pins;
   private final String reason;
}

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
package inetsoft.web.admin.ai.providers;

/**
 * The two independent, ordered provider chains this area administers (01-spec.md section 1).
 * Deliberately no abbreviation aliasing at the Java layer (unlike verb) -- "auth" is genuinely
 * ambiguous between the two full words, so a caller-supplied abbreviation must fail loud rather than
 * be silently guessed (01-spec.md section 11).
 */
public enum ProviderChain {
   AUTHENTICATION("authentication"),
   AUTHORIZATION("authorization");

   ProviderChain(String label) {
      this.label = label;
   }

   public String label() {
      return label;
   }

   private final String label;
}

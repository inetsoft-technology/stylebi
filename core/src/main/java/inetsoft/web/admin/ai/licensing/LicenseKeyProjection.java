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
package inetsoft.web.admin.ai.licensing;

import inetsoft.report.internal.license.License;

/**
 * This area's own read/audit projection of a {@link License}, built directly off the real object
 * -- never off {@code LicenseKeyModel}, whose {@code valid()} always returns {@code true}
 * regardless of the underlying license's real validity ({@code stylebi#76344}, 01-spec.md section
 * 14 D2/D6). {@code type} is the {@code LicenseType} enum name (or {@code null} on an
 * unparseable/Noop-backed result), {@code description} is {@link License#description()}'s
 * free-text string (the "Expired"/"Invalid" signal currently smuggled into that field).
 */
public record LicenseKeyProjection(String key, String type, boolean valid, String description) {
   public static LicenseKeyProjection of(License license) {
      return new LicenseKeyProjection(license.key(),
                                      license.type() == null ? null : license.type().name(),
                                      license.valid(), license.description());
   }

   /** Canonical string form used both for the plan hash's per-change projection (01-spec.md
    * section 5) and for the audit {@code beforeValue}/{@code afterValue} columns (section 8). */
   public String canonical() {
      return "key=" + key + ";type=" + type + ";valid=" + valid + ";description=" + description;
   }
}

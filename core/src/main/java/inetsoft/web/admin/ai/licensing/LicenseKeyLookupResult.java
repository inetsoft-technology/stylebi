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

/**
 * Response body for {@code GET /api/wiz/v1/admin/licensing/keys/{key}}.
 * {@code found: false} is a normal answer, not an error, matching
 * {@code get_permission_grant}'s own precedent (01-spec.md section 3) -- most literal key strings
 * a caller might ask about were never installed.
 */
public record LicenseKeyLookupResult(boolean found, LicenseKeyProjection license) {
}

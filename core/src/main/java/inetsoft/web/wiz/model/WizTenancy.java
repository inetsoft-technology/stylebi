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

/**
 * Whether this StyleBI runs multi-tenant ({@code SUtil.isMultiTenant()}). wiz reads it to decide whether
 * organization-level settings apply at all: when it is false, every user belongs to the default
 * organization and wiz uses its global settings only.
 *
 * @param multiTenant true when security is enabled, the license allows organizations and
 *                    {@code security.users.multiTenant} is on.
 */
public record WizTenancy(boolean multiTenant) {
}

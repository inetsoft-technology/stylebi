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
 * Whether the caller may administer the deployment-wide endpoint catalogue.
 *
 * <p>Exists because the answer cannot be derived outside StyleBI. The system administrator role is
 * named by the configured security provider, not by a fixed string, and role inheritance has to be
 * expanded before the question can be answered — which is what
 * {@code OrganizationManager.isSiteAdmin} does. A client that string-matched the {@code roles} claim
 * of its token would get a different answer on every deployment that renamed the role, and would
 * miss every user who holds it by inheritance.</p>
 *
 * @param siteAdmin true when the caller holds the system administrator role, directly or by
 *                  inheritance. Deployments running without security answer true for the default
 *                  admin principal, so a single-user development install is not locked out.
 */
public record WizEndpointCatalogAdmin(boolean siteAdmin) {
}

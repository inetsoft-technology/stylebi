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

import java.util.List;

/**
 * The data source types the tabular editor offers.
 *
 * @param listings   the offered listings, sorted by {@code name}. Only genuinely tabular listings
 *                   appear: a JDBC listing has no {@code TabularView} to render and belongs to the
 *                   {@code /databases} editor.
 * @param categories distinct RAW {@code category} values, sorted — the grouping order for the type
 *                   picker. Raw rather than translated precisely so it can be joined against
 *                   {@link WizTabularListing#category()}; the display text for a group is the
 *                   {@code categoryLabel} of the listings in it. Derivable from {@code listings},
 *                   and supplied so a client's group order matches the server's without
 *                   reproducing the sort.
 */
public record WizTabularListings(List<WizTabularListing> listings, List<String> categories) {
}

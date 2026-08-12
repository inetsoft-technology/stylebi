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
 * The card wall of tabular data source types a user may create.
 *
 * @param listings   the offered listings, sorted by identifier. Only listings whose template is a
 *                   tabular data source appear: a JDBC listing describes a database, which the wiz
 *                   portal creates through its own {@code /databases} editor and which has no
 *                   {@code TabularView} to render.
 * @param categories the distinct <em>raw</em> categories present in {@code listings}, sorted.
 *                   Derivable from the listings, and supplied so the client's grouping order matches
 *                   the server's without it having to reproduce the sort. Raw rather than translated
 *                   so it can be joined against {@code WizTabularListing.category}; the display text
 *                   for each is that listing's {@code categoryLabel}.
 */
public record WizTabularListings(List<WizTabularListing> listings, List<String> categories) {
}

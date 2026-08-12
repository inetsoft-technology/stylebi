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
 * One kind of tabular data source the wiz portal can create.
 *
 * <p>Identifiers and display text are separate fields here, which is what lets the wiz portal follow
 * the same rule as the browsing endpoints — key off raw values, translate them itself — without
 * needing a translation table for every data source type StyleBI or a plugin might ship. Group and
 * look up by {@code name} and {@code category}; render {@code label} and {@code categoryLabel}
 * whenever the portal has no translation of its own.</p>
 *
 * @param name          the listing's stable identifier, e.g. {@code "MongoDB"}. The same value the
 *                      {@code visible.datasource.types} and {@code hidden.datasource.types}
 *                      properties are written in terms of, and the one {@code /tabular/listing}
 *                      takes.
 * @param label         the same listing's display name, resolved through StyleBI's resource bundles
 *                      and therefore in the <em>server's</em> language. Never an identifier: it
 *                      changes with the locale.
 * @param category      the raw, untranslated category the card wall groups by, e.g.
 *                      {@code "Relational Database"}. Stable across locales, so grouping and any
 *                      client-side translation must key off this one.
 * @param categoryLabel the same category run through {@code Catalog}, i.e. exactly what the native
 *                      selection view emits in place of the raw value. Present so a client with no
 *                      translation of its own still has something to display.
 * @param iconUrl       the icon's path <em>inside the listing's own classpath</em>, e.g.
 *                      {@code "mongodb.svg"}. Not a URL the wiz portal can fetch: the native portal
 *                      resolves it against the listing class through its own image endpoint, which
 *                      is gated on the portal tab permission. Passed through unchanged so a later
 *                      step can serve the bytes; a client that cannot resolve it should fall back to
 *                      a generic icon rather than requesting it.
 * @param keywords      the listing's search keywords, empty when it declares none.
 */
public record WizTabularListing(String name, String label, String category, String categoryLabel,
                                String iconUrl, List<String> keywords)
{
}

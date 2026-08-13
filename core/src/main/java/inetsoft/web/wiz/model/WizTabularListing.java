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
 * One data source type offered by the tabular editor.
 *
 * <p>Key and label are separate fields on purpose. {@code DataSourceListing.getDisplayName()} is
 * {@code getName()} run through a resource bundle for the caller's locale, so the portal's own
 * selection view — which sends the display name as the key and looks listings up by it — silently
 * changes its key set when the locale changes. This model keeps the stable {@code name} as the key
 * and carries the translation alongside, so a client's stored choice survives a locale switch.</p>
 *
 * @param name          stable identifier, e.g. {@code "MongoDB"}. The key
 *                      {@code GET /tabular/listing?name=} takes, and the value StyleBI's
 *                      {@code visible.datasource.types} / {@code hidden.datasource.types}
 *                      properties are written in. Never localized.
 * @param label         display name for the caller's locale. Never use as a key.
 * @param category      raw category, e.g. {@code "Relational Database"}. Group and join on this.
 * @param categoryLabel display form of {@code category}. Never use as a key.
 * @param iconUrl       icon path inside the listing's own classpath, e.g. {@code "mongodb.svg"} —
 *                      despite the name, not a URL a client can fetch. Forwarded unchanged so a
 *                      later step can serve the bytes.
 * @param keywords      search terms; empty when the listing declares none.
 */
public record WizTabularListing(String name, String label, String category, String categoryLabel,
                                String iconUrl, List<String> keywords)
{
}

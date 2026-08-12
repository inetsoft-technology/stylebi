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
 * One row of the wiz data source browser: either a data source folder or a data source.
 *
 * <p>Deliberately not the portal's own {@code DataSourceInfo}. That model carries a
 * {@code NameLabelTuple} whose label has already been translated by {@code Catalog} into the
 * StyleBI server language, and it carries a pre-formatted {@code createdDateLabel} in the server
 * time zone. The wiz portal runs its own i18n, so every field here is raw: enum names verbatim and
 * epoch millis, formatted client-side.</p>
 *
 * <p>{@code DataSourceInfo.type()} names the <em>kind of node</em> ({@code DATABASE},
 * {@code DATA_SOURCE}, {@code DATA_SOURCE_FOLDER}, …), never the database product. The product is
 * a separate lookup against the repository, which is why {@code sourceType} and
 * {@code databaseType} exist alongside {@code nodeType}.</p>
 *
 * @param name         the display name (the last path segment).
 * @param path         the full path, e.g. {@code "Examples/Orders"}.
 * @param nodeType     the raw {@link inetsoft.web.portal.data.PortalDataType} name, untranslated.
 * @param folder       derived from {@code nodeType}; the client's branch between "enter" and
 *                     "select".
 * @param sourceType   {@code XDataSource.getType()}, e.g. {@code "jdbc"} / {@code "Rest"}. Null for
 *                     folders, and null when the data source could not be loaded.
 * @param databaseType {@code JDBCDataSource.getDatabaseTypeString()}, e.g. {@code "MYSQL"}. Null for
 *                     anything that is not a JDBC database.
 * @param createdBy    the alias of the creating user, may be null.
 * @param createdDate  creation time in epoch millis, 0 when unknown. Not formatted.
 * @param editable     whether the caller holds WRITE on this entry.
 * @param deletable    whether the caller holds DELETE on this entry.
 * @param hasSubFolder whether a folder entry contains further folders.
 */
public record WizDatasourceEntry(
   String name,
   String path,
   String nodeType,
   boolean folder,
   String sourceType,
   String databaseType,
   String createdBy,
   long createdDate,
   boolean editable,
   boolean deletable,
   boolean hasSubFolder)
{
}

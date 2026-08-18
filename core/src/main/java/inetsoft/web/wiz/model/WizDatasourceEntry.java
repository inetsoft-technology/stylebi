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
 * @param sourceType   {@code XDataSource.getType()}, which is the SPECIFIC connector type — {@code
 *                     "jdbc"} for a database, {@code "Rest.Stripe"} rather than {@code "Rest"} for a
 *                     REST connector. The coarse family name is {@code getBaseType()}, a different
 *                     method that {@code AbstractRestDataSource} overrides to return {@code "Rest"};
 *                     this field never carries it. That distinction is load-bearing: this value is
 *                     what joins a data source to its endpoint catalogue, which is keyed by the
 *                     specific type. Null for folders, and null when the data source could not be
 *                     loaded.
 * @param databaseType {@code JDBCDataSource.getDatabaseTypeString()}, e.g. {@code "MYSQL"}. Null for
 *                     anything that is not a JDBC database.
 * @param annotationClass
 *                     how this data source can be annotated, which decides what the wiz portal has
 *                     to ask of the user before it can index it. Null for folders. One of:
 *                     <dl>
 *                     <dt>{@code JDBC}</dt>
 *                     <dd>a relational database; tables and columns come from JDBC metadata.</dd>
 *                     <dt>{@code FILE}</dt>
 *                     <dd>a browsable tree of files or sheets. Targets are enumerable; columns are
 *                     read from the file.</dd>
 *                     <dt>{@code METADATA}</dt>
 *                     <dd>the service can be asked what it holds — collections, entities, objects,
 *                     tables. Targets are enumerable without the user supplying anything.</dd>
 *                     <dt>{@code ENDPOINT_CATALOG}</dt>
 *                     <dd>ships an {@code endpoints.json}, so the callable set is known offline and
 *                     exactly. Needs no discovery and no documentation.</dd>
 *                     <dt>{@code DOCUMENT_REQUIRED}</dt>
 *                     <dd>a REST service with no catalogue of its own. Nothing can enumerate what it
 *                     offers, so the user must supply documentation before it can be annotated.</dd>
 *                     <dt>{@code UNSUPPORTED}</dt>
 *                     <dd>output shape is decided by a user script, so there is no stable target to
 *                     annotate. Shown, but not annotatable.</dd>
 *                     <dt>{@code UNKNOWN}</dt>
 *                     <dd>classification failed, usually a connector plugin that did not load. Kept
 *                     distinct from the others so the cause is not mistaken for a verdict.</dd>
 *                     </dl>
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
   String annotationClass,
   String createdBy,
   long createdDate,
   boolean editable,
   boolean deletable,
   boolean hasSubFolder)
{
}

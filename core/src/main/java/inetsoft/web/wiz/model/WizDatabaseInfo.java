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

import java.util.Map;

/**
 * The type-specific half of a database definition, flattened.
 *
 * <p>StyleBI models this as an abstract {@code DatabaseInfo} with one subclass per database type,
 * serialized with an external type discriminator. Reproducing that polymorphism across the wire
 * would force the wiz portal to know the discriminator rules; instead every type's fields live side
 * by side here and only the ones that apply to the current {@code type} are populated. The
 * controller maps this onto the right subclass in both directions.</p>
 *
 * <p>Fields not relevant to the current type are null on read and ignored on write.</p>
 *
 * @param databaseName   the database/catalog name. {@code MYSQL}, {@code DB2}, {@code POSTGRESQL},
 *                       {@code SQLSERVER}, {@code INFORMIX} (and {@code SYBASE}).
 * @param sid            the Oracle SID. {@code ORACLE} only.
 * @param instanceName   the named instance. {@code SQLSERVER} only.
 * @param serverName     the INFORMIXSERVER value. {@code INFORMIX} only.
 * @param databaseLocale the db_locale value. {@code INFORMIX} only.
 * @param driverClass    the JDBC driver class name. {@code CUSTOM} only.
 * @param jdbcUrl        the JDBC URL as parsed from the stored connection. {@code CUSTOM} only, and
 *                       read-only in practice — see {@code customUrl}.
 * @param testQuery      the connection test query. {@code CUSTOM} only.
 * @param customEditMode whether the URL is written by hand rather than assembled from host and port.
 *                       Always true for {@code CUSTOM}.
 * @param customUrl      the hand-written URL. This, not {@code jdbcUrl}, is what the server stores
 *                       when {@code customEditMode} is set, so it is the field a {@code CUSTOM}
 *                       editor must write to.
 * @param poolProperties the connection pool properties, may be null.
 */
public record WizDatabaseInfo(
   String databaseName,
   String sid,
   String instanceName,
   String serverName,
   String databaseLocale,
   String driverClass,
   String jdbcUrl,
   String testQuery,
   boolean customEditMode,
   String customUrl,
   Map<String, String> poolProperties)
{
}

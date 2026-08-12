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
 * A JDBC database connection as the wiz portal edits it.
 *
 * <p>Deliberately not StyleBI's own {@code DatabaseDefinition}, which carries three fields the
 * native Angular editor only gets away with because it round-trips the server's JSON untouched
 * rather than rebuilding it from a typed interface:</p>
 * <ul>
 *    <li>{@code oldName} — absent here on purpose. It is what lets the server recover the stored
 *        password when the client did not send one; a client that omitted it (as any client
 *        building the payload field by field naturally would) would silently blank the password on
 *        every save. The controller fills it in from the request's {@code path} instead, so the
 *        wire contract cannot get it wrong.</li>
 *    <li>{@code permissions} — absent here. The wiz portal does not edit permissions, and the
 *        native model writes them whenever a {@code changed} flag survives the round trip.</li>
 *    <li>the password mask constant — see {@link WizAuthentication}.</li>
 * </ul>
 *
 * @param name                 the connection name, i.e. the last segment of its repository path.
 *                             Never null; the blank template supplies {@code ""}.
 * @param description          free-text description, may be null.
 * @param type                 the database type identifier, e.g. {@code "MYSQL"} or {@code "CUSTOM"}.
 * @param network              host and port; null for {@code CUSTOM}.
 * @param authentication       the credentials; never null on read.
 * @param info                 the type-specific settings; never null on read.
 * @param ansiJoin             whether to generate ANSI join syntax.
 * @param transactionIsolation JDBC isolation level: -1 default, or 1 / 2 / 4 / 8.
 * @param tableNameOption      how table names are qualified: 0 Catalog.Schema.Table,
 *                             1 Schema.Table, 2 Table, 3 default.
 * @param defaultDatabase      the database to switch to on connect; only applied when
 *                             {@code changeDefaultDB} is set.
 * @param changeDefaultDB      whether {@code defaultDatabase} is applied at all.
 * @param deletable            whether the caller may delete this connection. Read-only; ignored on
 *                             write.
 */
public record WizDatabaseDefinition(
   String name,
   String description,
   String type,
   WizNetworkLocation network,
   WizAuthentication authentication,
   WizDatabaseInfo info,
   boolean ansiJoin,
   int transactionIsolation,
   int tableNameOption,
   String defaultDatabase,
   boolean changeDefaultDB,
   boolean deletable)
{
}

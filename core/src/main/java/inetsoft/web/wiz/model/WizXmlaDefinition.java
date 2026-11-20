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
 * An XMLA (OLAP) data source connection as the wiz portal edits it.
 *
 * <p>Deliberately not StyleBI's {@code DataSourceXmlaDefinition}: that carries the cube/domain
 * metadata, {@code oldName} and the password. The domain is out of scope (an update sends none,
 * which the service reads as "leave the stored domain unchanged"), {@code oldName} is filled in from
 * the request path, and the password follows {@link WizXmlaAuthentication}.</p>
 *
 * @param name           the data source name, the last segment of its repository path.
 * @param description    free text, may be null.
 * @param datasource     the XMLA {@code DataSource} property.
 * @param datasourceName the XMLA data source name.
 * @param datasourceInfo the XMLA data source info.
 * @param catalogName    the catalog.
 * @param url            the XMLA endpoint URL.
 * @param login          whether the endpoint requires a login.
 * @param authentication the login; never null on read.
 * @param useCredential  whether a cloud-secret credential replaces the user name and password.
 * @param credentialId   the cloud-secret id (a reference, never the secret). Null unless
 *                       {@code useCredential}.
 * @param deletable      whether the caller may delete this source. Read-only; ignored on write.
 */
public record WizXmlaDefinition(
   String name,
   String description,
   String datasource,
   String datasourceName,
   String datasourceInfo,
   String catalogName,
   String url,
   boolean login,
   WizXmlaAuthentication authentication,
   boolean useCredential,
   String credentialId,
   boolean deletable)
{
}

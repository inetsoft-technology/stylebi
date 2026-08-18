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
import java.util.Map;

/**
 * One answer covering every requested type.
 *
 * @param catalogs      the types that have a catalogue, keyed by the requested type string.
 * @param notCatalogued types that resolved to a query class shipping no {@code endpoints.json}.
 *                      Not an error: it is the normal state of a DOCUMENT_REQUIRED data source, and
 *                      the portal must ask the user for documentation rather than report a fault.
 * @param unavailable   types that are registered but whose query class could not be loaded, usually
 *                      a connector plugin that did not load, plus types whose catalogue exists but
 *                      failed to parse. Kept apart from {@code notCatalogued} for the same reason
 *                      {@link WizDatasourceEntry} keeps UNKNOWN apart from UNSUPPORTED: a
 *                      classification failure must never be read as a classification result. This
 *                      one is an environment problem and goes away when the plugin is installed, so
 *                      the portal may retry it later.
 * @param unknownType   types that are not registered in this build at all, i.e. {@code
 *                      Config.getQueryClass} has no entry for them. Unlike {@code unavailable},
 *                      this is permanent - no plugin install or retry will ever change the answer -
 *                      because the type string itself does not exist, whether from a client typo
 *                      (such as a mistyped connector id) or a version mismatch between the caller
 *                      and this server. The portal must not retry these; it is either its own bug
 *                      or the caller is talking to an older/newer server than it expects.
 */
public record WizEndpointCatalogResponse(
   Map<String, WizEndpointCatalog> catalogs,
   List<String> notCatalogued,
   List<String> unavailable,
   List<String> unknownType)
{
}

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
 * The last known connection state of one data source.
 *
 * <p>The portal's own {@code DataSourceStatus} carries only {@code connected} and {@code message},
 * with no way back to the data source it describes — the native client relies on positional
 * correspondence with the request. This model re-attaches the path so the wiz client can fill in
 * rows out of order.</p>
 *
 * @param path      the requested data source path, echoed back.
 * @param connected whether the last recorded connection attempt succeeded.
 * @param message   the human-readable status text. Already translated by {@code Catalog} into the
 *                  StyleBI server language and stamped with a server-formatted timestamp; it is
 *                  passed through because there is no structured form of it to hand out. Null when
 *                  the data source has never been tested.
 */
public record WizDatasourceStatus(
   String path,
   boolean connected,
   String message)
{
}

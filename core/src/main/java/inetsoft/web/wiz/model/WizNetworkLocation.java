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
 * Where the database server listens.
 *
 * <p>Null for {@code CUSTOM}, whose URL is written by hand and never assembled from host and port.
 * For every other type the server assembles the JDBC URL from these two values, so both matter.</p>
 *
 * @param hostName   the host name or IP address.
 * @param portNumber the listening port.
 */
public record WizNetworkLocation(String hostName, int portNumber) {
}

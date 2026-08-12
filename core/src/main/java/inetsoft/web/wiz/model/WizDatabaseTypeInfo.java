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
 * One selectable database type in the wiz database editor.
 *
 * <p>{@code installed} is not a filter: a type whose JDBC driver is missing is still offered, so the
 * client can explain why the connection will fail instead of failing silently at save time. Only
 * types the wiz editor has no form for at all are omitted server-side.</p>
 *
 * @param type        the raw type identifier, e.g. {@code "MYSQL"}. Untranslated — the wiz portal
 *                    maps it to a label itself.
 * @param installed   whether the JDBC driver for this type is on the server classpath.
 * @param defaultPort the port to pre-fill when the user picks this type; 0 for types that have no
 *                    network location ({@code CUSTOM}).
 */
public record WizDatabaseTypeInfo(String type, boolean installed, int defaultPort) {
}

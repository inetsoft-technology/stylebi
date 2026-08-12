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
 * Everything the wiz database editor needs to render its type dropdown, fetched once per page.
 *
 * @param types         the database types the wiz editor supports, in server registration order.
 *                      Never null; never contains {@code ACCESS} or {@code ODBC}, which need a file
 *                      upload and a server-side DSN enumeration respectively and are out of scope.
 * @param driverClasses the JDBC driver classes installed on the server, for the {@code CUSTOM}
 *                      type's driver-class autocomplete. Never null, possibly empty.
 */
public record WizDatabaseMeta(List<WizDatabaseTypeInfo> types, List<String> driverClasses) {
}

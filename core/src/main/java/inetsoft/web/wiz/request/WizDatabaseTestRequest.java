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

package inetsoft.web.wiz.request;

import inetsoft.web.wiz.model.WizDatabaseDefinition;

/**
 * Tests a database connection without saving it.
 *
 * <p>{@code path} is what makes testing an existing database with an untouched password work: it is
 * how the server locates the stored password to test with. Omit it and a test of an unchanged
 * connection will connect with no password and fail, even though saving it would have succeeded.</p>
 *
 * @param path       the existing database's full path. Null or empty when testing a database that
 *                   has not been created yet.
 * @param definition the settings to test, as currently shown in the editor.
 */
public record WizDatabaseTestRequest(String path, WizDatabaseDefinition definition) {
}

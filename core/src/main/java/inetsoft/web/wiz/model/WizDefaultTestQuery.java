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
 * The suggested connection test query for a database type.
 *
 * <p>A wrapper around a single string rather than the bare string the native endpoint returns:
 * Spring serves a {@code String} return value through {@code StringHttpMessageConverter} as
 * {@code text/plain}, which a client sending {@code Accept: application/json} rejects with 406.</p>
 *
 * @param query the test query, e.g. {@code "SELECT 1"}. Null when the type has no known default.
 */
public record WizDefaultTestQuery(String query) {
}

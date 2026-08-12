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
 * Whether a data source path is already taken.
 *
 * <p>An object rather than the bare boolean the native endpoint returns, so that the response is
 * valid JSON for a client sending {@code Accept: application/json} — the same reason
 * {@code WizDefaultTestQuery} wraps its string.</p>
 *
 * @param duplicate whether a data source already exists at the checked path.
 */
public record WizTabularDuplicateResult(boolean duplicate) {
}

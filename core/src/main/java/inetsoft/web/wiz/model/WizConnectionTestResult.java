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
 * The outcome of a connection test.
 *
 * @param connected whether the driver opened a connection.
 * @param message   the server's account of what happened. Translated by {@code Catalog} into the
 *                  StyleBI server language and carrying the raw driver error text on failure, with
 *                  no structured form available — display it as an opaque string, do not parse it
 *                  and do not translate it again.
 */
public record WizConnectionTestResult(boolean connected, String message) {
}

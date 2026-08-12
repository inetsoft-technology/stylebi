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
 * The credentials used to connect to the database.
 *
 * <p><b>Password semantics.</b> On the way out, {@code password} is <em>always</em> null: the stored
 * password never leaves the server, not even as the mask constant StyleBI's own admin API sends. If
 * the mask were echoed to the client, a client that faithfully returned what it received would be
 * indistinguishable from a user who had literally typed the mask, and one of those two must lose.</p>
 *
 * <p>On the way in, {@code password} is a three-way switch:</p>
 * <ul>
 *    <li>{@code null} — leave the stored password alone. The controller translates this into the
 *        mask constant that triggers StyleBI's existing recovery path, which is also why the
 *        controller must fill in {@code oldName} itself on every update.</li>
 *    <li>{@code ""} — clear the password.</li>
 *    <li>anything else — set the password to that value.</li>
 * </ul>
 *
 * @param required whether the database requires a login at all. When false the other two fields are
 *                 ignored by the server.
 * @param userName the user name, may be null.
 * @param password see above. Always null on read.
 */
public record WizAuthentication(boolean required, String userName, String password) {
}

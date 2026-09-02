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

import java.util.Map;

/**
 * Which of the checked items have an outer dependency, and why.
 *
 * <p>Lets the delete confirmation dialog warn the user before they even click delete once, instead
 * of only discovering a conflict from a refused {@code force=false} delete attempt. Calling this
 * first is optional — the delete endpoint enforces the same rule itself.</p>
 *
 * @param messagesByPath one entry per item that has a conflict, keyed by path. An item with no
 *                       conflict has no entry at all; an empty map means every checked item is
 *                       clear to delete without {@code force}.
 */
public record WizDependencyCheckResult(Map<String, String> messagesByPath) {
}

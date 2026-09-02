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
 * One item's outcome from a {@code /datasources/delete} call.
 *
 * @param path              the item's path, echoed back so the caller can pair a result with the
 *                          request it made.
 * @param ok                whether the item was deleted.
 * @param reason            null when {@code ok}. Otherwise one of {@code PERMISSION_DENIED},
 *                          {@code HAS_DEPENDENCIES}, {@code UNKNOWN}.
 * @param dependencyMessage present only when {@code reason} is {@code HAS_DEPENDENCIES} — the
 *                          server-locale message describing what depends on this item.
 */
public record WizDatasourceDeleteItemResult(
   String path, boolean ok, String reason, String dependencyMessage)
{
   public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
   public static final String HAS_DEPENDENCIES = "HAS_DEPENDENCIES";
   public static final String UNKNOWN = "UNKNOWN";
}

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

import java.util.List;

/**
 * Moves one or more data sources and/or data source folders to a single destination folder.
 *
 * <p>Unlike the native {@code MoveCommand[]} contract — where the client precomputes every item's
 * full destination path — this takes the source items plus one {@code targetPath} (the folder
 * chosen once in the picker dialog), and the controller builds each destination path itself. That
 * is friendlier to a portal that lets the user pick one destination for however many items are
 * selected.</p>
 *
 * @param items      the data sources and/or folders to move.
 * @param targetPath the destination folder. Null, empty or {@code "/"} means the root.
 */
public record WizMoveRequest(List<WizDatasourceRef> items, String targetPath) {
}

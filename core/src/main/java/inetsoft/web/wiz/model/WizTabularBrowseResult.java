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
 * Answer to {@code POST /api/wiz/tabular/browse}: what one folder of a file-based connector holds.
 *
 * <p>PATHS ARE RELATIVE, and that is the point of not reusing the composer's {@code TreeNodeModel}.
 * The dialog's node carries an {@code absolutePath} because a human is looking at a file picker on
 * a machine they administer; this answer goes to an agent, and a server filesystem path is both
 * something it must never send back ({@code tabularSource.target} is relative) and something it has
 * no business learning. A {@code path} here is exactly what {@code target} takes.</p>
 *
 * @param datasource the connector instance that was browsed.
 * @param path       the folder that was listed, relative to the connector's root; {@code ""} is it.
 * @param entries    what the folder holds, folders first, each ordered by name.
 * @param truncated  true when the walk hit its entry cap and stopped. Under truncation the absence
 *                   of a file means "not reached", not "not there" — a caller that conflates the
 *                   two will report a directory as smaller than it is. Browse the sub-folders
 *                   individually to see the rest.
 */
public record WizTabularBrowseResult(String datasource, String path,
                                     List<WizTabularBrowseEntry> entries, boolean truncated)
{
   /**
    * One file or folder.
    *
    * @param path   path relative to the connector's ROOT folder — not to the folder being listed —
    *               so it can be sent straight back as {@code tabularSource.target} with nothing to
    *               re-join. This is also the identity the annotation stores for the table.
    * @param name   the last segment, for display.
    * @param folder true when this can be browsed into, false when it can be bound as a table.
    */
   public record WizTabularBrowseEntry(String path, String name, boolean folder) {
   }
}

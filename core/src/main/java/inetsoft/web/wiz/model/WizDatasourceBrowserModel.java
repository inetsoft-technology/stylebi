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
 * One page of the wiz data source browser: the contents of a folder plus the trail that leads to
 * it.
 *
 * @param entries    the folders and data sources directly under the requested path, already
 *                   filtered to what the caller may read.
 * @param breadcrumb the folder trail as full paths, outermost first, <em>including the requested
 *                   folder itself</em> as the last element. Empty at the root. The synthetic "/"
 *                   root folder is not included — the client supplies its own root label, since
 *                   the server's would be pre-translated.
 * @param root       whether this page is the repository root (no path was requested).
 */
public record WizDatasourceBrowserModel(
   List<WizDatasourceEntry> entries,
   List<String> breadcrumb,
   boolean root)
{
}

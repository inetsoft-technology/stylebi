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
 * Batch lookup of the recorded connection state of several data sources.
 *
 * @param paths   the full data source paths to report on. Folder paths are meaningless here and
 *                will report as disconnected.
 * @param refresh true to re-test each connection and store the outcome (the Portal's "Refresh
 *                Status"); false or absent reports the stored status only. A refresh opens a
 *                connection per path and writes the data source, so it needs WRITE on every path.
 */
public record WizDatasourceStatusRequest(List<String> paths, boolean refresh) {
   /** The browse-time request: stored state only. */
   public WizDatasourceStatusRequest(List<String> paths) {
      this(paths, false);
   }
}

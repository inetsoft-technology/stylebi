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

/**
 * Body of {@code POST /api/wiz/tabular/browse}.
 *
 * <p>The wiz counterpart of {@code /api/composer/ws/tabular-query-dialog/browse}. It reads the same
 * connector metadata the composer dialog does — the file property's {@code relativeTo},
 * {@code foldersOnly} and {@code acceptTypes} editor properties — but carries no
 * {@code TabularView}: the dialog posts one back because a human has been editing it, whereas this
 * caller has nothing to edit and only needs what a freshly created query already answers.</p>
 *
 * @param datasource full repository path of the connector instance.
 * @param path       folder to list, RELATIVE to the connector's root; null or empty means the root.
 *                   Never absolute and never containing {@code ".."} — the root folder is the whole
 *                   of what the data source grants access to.
 * @param property   the file property to read the browse rules from, e.g. {@code "fileFolder"}.
 *                   Null resolves the connector's own file property, which is what a caller that
 *                   does not know the connector should send.
 * @param all        true ignores the property's {@code acceptTypes} filter and lists every file.
 * @param recursive  true walks sub-folders in one call, which is what an annotation pass wants —
 *                   the alternative is one request per directory. Bounded; see
 *                   {@code WizTabularBrowseResult.truncated}.
 */
public record WizTabularBrowseRequest(String datasource, String path, String property,
                                      boolean all, boolean recursive)
{
}

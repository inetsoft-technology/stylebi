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
package inetsoft.uql.tabular;

import java.util.List;

/**
 * A TabularQuery whose file/item property cannot be walked as a local java.io.File -- addressed
 * through a remote API instead (a cloud-drive item, a document-library item, ...) -- but which can
 * still enumerate its own children. WizTabularController.browse() dispatches here BEFORE its
 * local-filesystem findFileView()/collect() path, by a type test (instanceof BrowsableQuery),
 * matching the same discipline SelectableTabularQuery already established: a capability test, not
 * a name check on the connector.
 */
public interface BrowsableQuery {
   /**
    * Name of the property this query browses -- what WizTabularBrowseRequest.property() is matched
    * against when the caller names one explicitly. A connector with exactly one browsable property
    * (every implementor so far) returns its constant name.
    */
   String getBrowsablePropertyName();

   /**
    * The connector's own extension whitelist (e.g. [".txt", ".csv", ".xls", ".xlsx"]),
    * read by the controller and skipped when the caller's request carries all=true --
    * mirroring exactly how WizTabularController.browse()'s local-file path reads
    * acceptTypes off a @PropertyEditor, just declared in code instead of an
    * annotation because there is no java.io.File-typed property to hang the annotation off.
    */
   List<String> getAcceptedExtensions();

   /**
    * List one folder's entries, optionally recursing into sub-folders.
    *
    * @param path        folder to list, relative to this connector's own root; "" is the root.
    * @param recursive   true walks sub-folders in the same call (bounded by maxEntries).
    * @param acceptTypes extension whitelist to apply to files (folders are always listed); empty
    *                    accepts every file. Already resolved by the caller against "all".
    * @param maxEntries  stop and report truncated once this many entries have been collected.
    * @throws Exception  a real failure (auth, network, throttling) -- never swallowed into an
    *                     empty/short listing. The caller must let this propagate.
    */
   BrowseListing browseChildren(String path, boolean recursive, List<String> acceptTypes,
                                int maxEntries) throws Exception;

   record BrowseEntry(String path, String name, boolean folder) {}

   /** truncated is true when maxEntries (or an internal request-count cap) stopped the walk. */
   record BrowseListing(List<BrowseEntry> entries, boolean truncated) {}
}

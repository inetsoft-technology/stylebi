/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.lib.logical;

public final class LogicalLibraryFactory {
   public LogicalLibraryFactory(LibrarySecurity security) {
      this.security = security;
   }

   public ScriptLogicalLibrary createScriptLogicalLibrary() {
      return new ScriptLogicalLibrary(security);
   }

   public TableStyleLogicalLibrary createTableStyleLogicalLibrary() {
      return new TableStyleLogicalLibrary(security);
   }

   public TableStyleFolderLogicalLibrary createTableStyleFolderLogicalLibrary() {
      return new TableStyleFolderLogicalLibrary(security);
   }

   private final LibrarySecurity security;
}

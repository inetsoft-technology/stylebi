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
package inetsoft.report.composition;

/**
 * Thin accessor interface implemented by WorksheetEngine (and therefore ViewsheetEngine).
 * Exposes getSheetDirect for the agent pairing path, allowing tests to mock the accessor
 * without triggering WorksheetEngine's static initializers.
 */
public interface SheetDirectAccessor {
   /**
    * Return the RuntimeSheet for the given runtimeId without performing the
    * per-session matches() check. Returns null if the id is not in the local cache.
    */
   RuntimeSheet getSheetDirect(String runtimeId);

   /**
    * Return the RuntimeSheet only if it is resident in this node's own memory. Unlike
    * {@link #getSheetDirect}, never falls back to the distributed cache, so it cannot create a
    * copy of another node's sheet or revive one that was just disposed.
    */
   default RuntimeSheet getSheetLocal(String runtimeId) {
      // Fail closed: an accessor that cannot tell what is local must not fall back to
      // getSheetDirect, which reloads from the distributed cache.
      return null;
   }
}

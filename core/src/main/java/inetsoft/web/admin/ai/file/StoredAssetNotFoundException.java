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
package inetsoft.web.admin.ai.file;

/**
 * Thrown when a {@code path} names an entry that does not exist -- mapped to a structured 404 by
 * {@link AdminFileContentController}, distinguished from an empty folder (01-design.md section
 * 6.2's {@code {status: "not-found", path}} shape, matching {@code get_permission_grant}'s
 * {@code {found: false}} discipline).
 */
public class StoredAssetNotFoundException extends RuntimeException {
   public StoredAssetNotFoundException(String path) {
      super("No stored asset at \"" + path + "\"");
      this.path = path;
   }

   public String path() {
      return path;
   }

   private final String path;
}

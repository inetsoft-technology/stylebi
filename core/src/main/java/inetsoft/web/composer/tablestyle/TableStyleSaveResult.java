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
package inetsoft.web.composer.tablestyle;

/**
 * Result of saving a table style. When {@code conflict} is true nothing was saved because the
 * stored style changed after the editor opened it; {@code message} explains why.
 */
public class TableStyleSaveResult {
   public TableStyleSaveResult() {
   }

   public TableStyleSaveResult(boolean conflict, long lastModified, String message) {
      this.conflict = conflict;
      this.lastModified = lastModified;
      this.message = message;
   }

   public boolean isConflict() {
      return conflict;
   }

   public long getLastModified() {
      return lastModified;
   }

   public String getMessage() {
      return message;
   }

   private boolean conflict;
   private long lastModified;
   private String message;
}

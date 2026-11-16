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
 * Result of saving a table style under a new name: the id of the saved style and its new
 * last-modified token, which the editor must use for its next plain save.
 */
public class TableStyleSaveAsResult {
   public TableStyleSaveAsResult() {
   }

   public TableStyleSaveAsResult(String styleId, long lastModified) {
      this.styleId = styleId;
      this.lastModified = lastModified;
   }

   public String getStyleId() {
      return styleId;
   }

   public long getLastModified() {
      return lastModified;
   }

   private String styleId;
   private long lastModified;
}

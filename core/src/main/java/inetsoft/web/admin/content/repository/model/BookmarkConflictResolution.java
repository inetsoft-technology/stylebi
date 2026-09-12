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
package inetsoft.web.admin.content.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * The admin's resolution for a single bookmark conflict.
 * When {@code keepImported} is {@code false}, the existing (current) bookmark is kept.
 * When {@code keepImported} is {@code true} (or when no resolution is present), the
 * imported bookmark wins — consistent with the default merge behaviour.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookmarkConflictResolution {
   private String viewsheetPath;
   private String user;
   private String bookmarkName;
   private boolean keepImported = true;

   public String getViewsheetPath() { return viewsheetPath; }
   public void setViewsheetPath(String viewsheetPath) { this.viewsheetPath = viewsheetPath; }

   public String getUser() { return user; }
   public void setUser(String user) { this.user = user; }

   public String getBookmarkName() { return bookmarkName; }
   public void setBookmarkName(String bookmarkName) { this.bookmarkName = bookmarkName; }

   public boolean isKeepImported() { return keepImported; }
   public void setKeepImported(boolean keepImported) { this.keepImported = keepImported; }

   // Equality is identifier-only (path + user + name). keepImported is intentionally excluded
   // so instances can be found by key regardless of their current resolution state.
   @Override
   public boolean equals(Object o) {
      if(this == o) { return true; }
      if(!(o instanceof BookmarkConflictResolution)) { return false; }
      BookmarkConflictResolution that = (BookmarkConflictResolution) o;
      return Objects.equals(viewsheetPath, that.viewsheetPath) &&
         Objects.equals(user, that.user) &&
         Objects.equals(bookmarkName, that.bookmarkName);
   }

   @Override
   public int hashCode() {
      return Objects.hash(viewsheetPath, user, bookmarkName);
   }
}

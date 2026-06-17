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

import java.util.ArrayList;
import java.util.List;

/**
 * Request body for {@code POST /api/em/content/repository/import/{importId}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImportAssetRequest {
   private List<String> ignoreList = new ArrayList<>();
   private List<BookmarkConflictResolution> bookmarkResolutions = new ArrayList<>();

   public List<String> getIgnoreList() { return ignoreList; }
   public void setIgnoreList(List<String> ignoreList) { this.ignoreList = ignoreList; }

   public List<BookmarkConflictResolution> getBookmarkResolutions() { return bookmarkResolutions; }
   public void setBookmarkResolutions(List<BookmarkConflictResolution> bookmarkResolutions) {
      this.bookmarkResolutions = bookmarkResolutions;
   }
}

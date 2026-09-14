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
package inetsoft.web.admin.ai.repository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared fields for {@code POST .../import/preview} and {@code POST .../import/apply} --
 * {@code apply} adds {@code planHash}/{@code taskToken}/{@code reviewOutcome}/{@code
 * acknowledgeOverwrite} on top (see {@link RepositoryImportApplyRequest}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryImportRequest {
   private String task;
   private String stagingToken;
   private String targetFolderPath;
   private String targetOwner;
   private List<String> ignoreAssetNames = new ArrayList<>();
   private List<BookmarkResolutionEntry> bookmarkResolutions = new ArrayList<>();

   public String getTask() { return task; }
   public void setTask(String task) { this.task = task; }

   public String getStagingToken() { return stagingToken; }
   public void setStagingToken(String stagingToken) { this.stagingToken = stagingToken; }

   public String getTargetFolderPath() { return targetFolderPath; }
   public void setTargetFolderPath(String targetFolderPath) {
      this.targetFolderPath = targetFolderPath;
   }

   public String getTargetOwner() { return targetOwner; }
   public void setTargetOwner(String targetOwner) { this.targetOwner = targetOwner; }

   public List<String> getIgnoreAssetNames() { return ignoreAssetNames; }
   public void setIgnoreAssetNames(List<String> ignoreAssetNames) {
      this.ignoreAssetNames = ignoreAssetNames;
   }

   public List<BookmarkResolutionEntry> getBookmarkResolutions() { return bookmarkResolutions; }
   public void setBookmarkResolutions(List<BookmarkResolutionEntry> bookmarkResolutions) {
      this.bookmarkResolutions = bookmarkResolutions;
   }

   public static class BookmarkResolutionEntry {
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
   }
}

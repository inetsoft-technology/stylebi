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
 * Request body for {@code POST /api/wiz/v1/admin/repository/export}. {@code
 * includeDependencyNames} is only consulted when {@code includeDependenciesMode} is {@code
 * "list"} -- the plugin tool decomposes its own {@code includeDependencies: "all"|"none"|string[]}
 * input into these two fields before calling this endpoint (01-design.md section 3, Flagged
 * Decision 1).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryExportRequest {
   private String task;
   private List<String> viewsheetAssetIds = new ArrayList<>();
   private List<String> folderPaths = new ArrayList<>();
   private String name;
   /** {@code "all"} (default), {@code "none"}, or {@code "list"}. */
   private String includeDependenciesMode = "all";
   private List<String> includeDependencyNames = new ArrayList<>();

   public String getTask() { return task; }
   public void setTask(String task) { this.task = task; }

   public List<String> getViewsheetAssetIds() { return viewsheetAssetIds; }
   public void setViewsheetAssetIds(List<String> viewsheetAssetIds) {
      this.viewsheetAssetIds = viewsheetAssetIds;
   }

   public List<String> getFolderPaths() { return folderPaths; }
   public void setFolderPaths(List<String> folderPaths) { this.folderPaths = folderPaths; }

   public String getName() { return name; }
   public void setName(String name) { this.name = name; }

   public String getIncludeDependenciesMode() { return includeDependenciesMode; }
   public void setIncludeDependenciesMode(String includeDependenciesMode) {
      this.includeDependenciesMode = includeDependenciesMode;
   }

   public List<String> getIncludeDependencyNames() { return includeDependencyNames; }
   public void setIncludeDependencyNames(List<String> includeDependencyNames) {
      this.includeDependencyNames = includeDependencyNames;
   }
}

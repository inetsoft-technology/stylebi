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

package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for POST /api/wiz/visualization/save.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WizVisualizationSaveEvent {
   public String getSourceViewsheetIdentifier() {
      return sourceViewsheetIdentifier;
   }

   public void setSourceViewsheetIdentifier(String sourceViewsheetIdentifier) {
      this.sourceViewsheetIdentifier = sourceViewsheetIdentifier;
   }

   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   public String getConversationId() {
      return conversationId;
   }

   public void setConversationId(String conversationId) {
      this.conversationId = conversationId;
   }

   /** Full path of the target folder (e.g. visualization-components-.../Sales Reports). */
   public String getTargetFolderPath() {
      return targetFolderPath;
   }

   public void setTargetFolderPath(String targetFolderPath) {
      this.targetFolderPath = targetFolderPath;
   }

   public String getDisplayName() {
      return displayName;
   }

   public void setDisplayName(String displayName) {
      this.displayName = displayName;
   }

   private String sourceViewsheetIdentifier;
   private String assemblyName;
   private String conversationId;
   private String targetFolderPath;
   private String displayName;
}

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
package inetsoft.web.composer.model.vs;

import java.io.Serializable;

public class ModernizeDialogModel implements Serializable {
   public boolean isKeepAvailable() {
      return keepAvailable;
   }

   public void setKeepAvailable(boolean keepAvailable) {
      this.keepAvailable = keepAvailable;
   }

   public boolean isNeverSaved() {
      return neverSaved;
   }

   public void setNeverSaved(boolean neverSaved) {
      this.neverSaved = neverSaved;
   }

   public String getSuggestedName() {
      return suggestedName;
   }

   public void setSuggestedName(String suggestedName) {
      this.suggestedName = suggestedName;
   }

   public String getParentId() {
      return parentId;
   }

   public void setParentId(String parentId) {
      this.parentId = parentId;
   }

   public boolean isEmbedsDashboards() {
      return embedsDashboards;
   }

   public void setEmbedsDashboards(boolean embedsDashboards) {
      this.embedsDashboards = embedsDashboards;
   }

   public String getScriptWarning() {
      return scriptWarning;
   }

   public void setScriptWarning(String scriptWarning) {
      this.scriptWarning = scriptWarning;
   }

   private boolean keepAvailable;
   private boolean neverSaved;
   private String suggestedName;
   private String parentId;
   private boolean embedsDashboards;
   private String scriptWarning;
}

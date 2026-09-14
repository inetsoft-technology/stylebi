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
package inetsoft.web.admin.ai.mv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Request body for {@code POST /api/wiz/v1/admin/mv/analyze}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MvAnalyzeRequest {
   public String getViewsheetAssetId() { return viewsheetAssetId; }
   public void setViewsheetAssetId(String v) { this.viewsheetAssetId = v; }
   public boolean isExpandGroups() { return expandGroups; }
   public void setExpandGroups(boolean v) { this.expandGroups = v; }
   public boolean isBypassVpm() { return bypassVpm; }
   public void setBypassVpm(boolean v) { this.bypassVpm = v; }
   public boolean isFullData() { return fullData; }
   public void setFullData(boolean v) { this.fullData = v; }
   public boolean isApplyParentVsParameters() { return applyParentVsParameters; }
   public void setApplyParentVsParameters(boolean v) { this.applyParentVsParameters = v; }

   private String viewsheetAssetId;
   private boolean expandGroups;
   private boolean bypassVpm;
   private boolean fullData;
   private boolean applyParentVsParameters;
}

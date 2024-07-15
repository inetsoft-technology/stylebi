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
package inetsoft.web.portal.model;

import java.util.List;

public class DashboardTabModel {
   public List<DashboardModel> getDashboards() {
      return dashboards;
   }

   public void setDashboards(List<DashboardModel> dashboards) {
      this.dashboards = dashboards;
   }

   public boolean isDashboardTabsTop() {
      return dashboardTabsTop;
   }

   public void setDashboardTabsTop(boolean dashboardTabsTop) {
      this.dashboardTabsTop = dashboardTabsTop;
   }

   public boolean isEditable() {
      return editable;
   }

   public void setEditable(boolean editable) {
      this.editable = editable;
   }
   
   public boolean isComposerEnabled() {
      return composerEnabled;
   }

   public void setComposerEnabled(boolean composerEnabled) {
      this.composerEnabled = composerEnabled;
   }

   private List<DashboardModel> dashboards;
   private boolean dashboardTabsTop;
   private boolean composerEnabled;
   private boolean editable;
}

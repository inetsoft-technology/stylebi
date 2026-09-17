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
package inetsoft.web.admin.ai.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One requested change: either a single dashboard ({@code create}/{@code update}/{@code delete})
 * or the dashboard folder's ordering ({@code reorder}) -- the "Portal Dashboard Tab"/"User Portal
 * Dashboard Tab" admin feature (Redmine #76695), not the self-service per-user pin feature.
 *
 * <p>{@code owner} follows the same convention as the viewsheet/folder area's own {@code owner}
 * field: a bare identity name or a {@code name:orgId}-shaped key, omitted for the global scope.
 *
 * <p>{@code oname} always identifies an EXISTING dashboard by its current display name (required
 * for {@code update}/{@code delete}); {@code name} is always the DESIRED display name (required
 * for {@code create}, optional on {@code update} to rename). This mirrors the
 * {@code RepositoryDashboardSettingsModel} field split it wraps.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DashboardChangeRequest {
   public static final String UNIT_DASHBOARD = "dashboard";
   public static final String UNIT_DASHBOARD_FOLDER = "dashboardFolder";

   public static final String VERB_CREATE = "create";
   public static final String VERB_UPDATE = "update";
   public static final String VERB_DELETE = "delete";
   public static final String VERB_REORDER = "reorder";

   public String getUnitType() { return unitType; }
   public void setUnitType(String v) { this.unitType = v; }

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   /** A bare identity name or a {@code name:orgId}-shaped key. Omitted means the global scope
    * ("Portal Dashboard Tab"); set means that user's scope ("User Portal Dashboard Tab"). */
   public String getOwner() { return owner; }
   public void setOwner(String v) { this.owner = v; }

   /** {@code unitType: "dashboard"}: the desired display name. Required for {@code create};
    * optional for {@code update} (a rename). Not used for {@code delete}. */
   public String getName() { return name; }
   public void setName(String v) { this.name = v; }

   /** {@code unitType: "dashboard"}, {@code verb: "update"|"delete"}: the EXISTING dashboard's
    * current display name, used to locate it. Not used for {@code create}. */
   public String getOname() { return oname; }
   public void setOname(String v) { this.oname = v; }

   /** {@code unitType: "dashboard"}: {@code create}/{@code update} only. {@code null} (omitted)
    * means leave unchanged on update; an explicit empty string clears it. */
   public String getDescription() { return description; }
   public void setDescription(String v) { this.description = v; }

   /** {@code unitType: "dashboard"}: {@code create}/{@code update} only -- the bound viewsheet's
    * asset identifier. */
   public String getViewsheet() { return viewsheet; }
   public void setViewsheet(String v) { this.viewsheet = v; }

   /** {@code unitType: "dashboard"}: {@code create}/{@code update} only. Defaults to {@code true}
    * on create when omitted; leaves the current state unchanged on update when omitted. */
   public Boolean getEnable() { return enable; }
   public void setEnable(Boolean v) { this.enable = v; }

   /** {@code unitType: "dashboardFolder"}, {@code verb: "reorder"}: the full replacement ordered
    * list of dashboard names for this owner scope. */
   public List<String> getDashboards() { return dashboards; }
   public void setDashboards(List<String> v) { this.dashboards = v; }

   private String unitType;
   private String verb;
   private String owner;
   private String name;
   private String oname;
   private String description;
   private String viewsheet;
   private Boolean enable;
   private List<String> dashboards;
}

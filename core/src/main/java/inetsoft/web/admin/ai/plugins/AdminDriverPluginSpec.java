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
package inetsoft.web.admin.ai.plugins;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The {@code asDriverPlugin} sub-object of {@code install_driver_or_plugin} (03-reconcile.md):
 * present only when the upload is a bare JDBC driver jar that needs a generated plugin wrapper
 * (the scan-then-create path), absent when the upload is already a full StyleBI plugin zip
 * (the direct-install path).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminDriverPluginSpec {
   public String getPluginId() { return pluginId; }
   public void setPluginId(String v) { this.pluginId = v; }
   public String getPluginName() { return pluginName; }
   public void setPluginName(String v) { this.pluginName = v; }
   public String getPluginVersion() { return pluginVersion; }
   public void setPluginVersion(String v) { this.pluginVersion = v; }
   public List<String> getDrivers() { return drivers; }
   public void setDrivers(List<String> v) { this.drivers = v; }

   private String pluginId, pluginName, pluginVersion;
   private List<String> drivers;
}

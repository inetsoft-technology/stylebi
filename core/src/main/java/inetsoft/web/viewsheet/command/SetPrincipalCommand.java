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
package inetsoft.web.viewsheet.command;

import inetsoft.sree.security.*;
import inetsoft.web.AutoSaveUtils;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Command used to notify the client of the viewsheet principal user.
 *
 * @since 12.3
 */
public class SetPrincipalCommand implements ViewsheetCommand {
   public SetPrincipalCommand(SecurityEngine securityEngine, Principal principal) throws Exception {
      this.principalKey = principal.getName();

      if(securityEngine != null) {
         SecurityProvider securityProvider = securityEngine.getSecurityProvider();
         this.securityEnabled = securityProvider != null && !securityProvider.isVirtual();
         this.viewsheetPermission = securityEngine.checkPermission(
            principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS);
         this.worksheetPermission = securityEngine.checkPermission(
            principal, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS);
         this.tableStylePermission = securityEngine.checkPermission(
            principal, ResourceType.CREATE_TABLE_STYLE, "*", ResourceAction.ACCESS);
         this.scriptPermission = securityEngine.checkPermission(
            principal, ResourceType.CREATE_SCRIPT, "*", ResourceAction.ACCESS);

         autoSaveFiles = new ArrayList<>();
         initAutoSaveFiles(principal);
      }
   }

   private void initAutoSaveFiles(Principal principal) {
      this.autoSaveFiles = AutoSaveUtils.getUserAutoSaveFiles(principal);
   }

   /**
    * Gets the viewsheet principal.
    *
    * @return the principal.
    */
   public String getPrincipal() {
      return principalKey;
   }

   /**
    * Flag set if security is enabled
    *
    * @return true if security is enabled, otherwise false
    */
   public boolean isSecurityEnabled() {
      return securityEnabled;
   }

   public boolean isViewsheetPermission() {
      return viewsheetPermission;
   }

   public boolean isWorksheetPermission() {
      return worksheetPermission;
   }

   public boolean isScriptPermission() {
      return scriptPermission;
   }

   public boolean isTableStylePermission() {
      return tableStylePermission;
   }

   public void setAutoSaveFiles(List<String>  files) {
      autoSaveFiles = files;
   }

   public List<String>  getAutoSaveFiles() {
      return autoSaveFiles;
   }

   private String principalKey;
   private boolean securityEnabled;
   private boolean viewsheetPermission;
   private boolean worksheetPermission;
   private boolean tableStylePermission;
   private boolean scriptPermission;
   private List<String> autoSaveFiles;
}

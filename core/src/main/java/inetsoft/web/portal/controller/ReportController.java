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
package inetsoft.web.portal.controller;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.portal.PortalWelcomePage;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.portal.model.ReportTabModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Controller that provides a REST endpoint for the report tab in portal
 *
 * @since 12.3
 */
@RestController
public class ReportController {
   @Autowired
   public ReportController(SecurityProvider securityProvider) {
      this.securityProvider = securityProvider;
   }

   @GetMapping(value = "/api/portal/report-tab-model")
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Report")
   })
   public ReportTabModel getReportTabModel(Principal principal) throws Exception {
      PortalThemesManager manager = PortalThemesManager.getManager();

      boolean searchEnabled = manager.isButtonVisible(PortalThemesManager.SEARCH_BUTTON);

      String welcomePageUri = null;
      PortalWelcomePage welcomePage = manager.getWelcomePage();

      if(welcomePage.getType() == PortalWelcomePage.URI) {
         welcomePageUri = welcomePage.getData();
      }
      else if(welcomePage.getType() == PortalWelcomePage.RESOURCE) {
         welcomePageUri = "../portal/source/welcomepage";
      }

      String licensedComponentMsg = null;
      Catalog catalog = Catalog.getCatalog();
      licensedComponentMsg = catalog.getString("dashboard");

      ReportTabModel model = new ReportTabModel();
      model.setExpandAllNodes(manager.isAutoExpand());
      model.setShowRepositoryAsList(
         manager.getReportListType() == PortalThemesManager.LIST);
      model.setSearchEnabled(searchEnabled);
      model.setWelcomePageUri(welcomePageUri);
      model.setLicensedComponentMsg(licensedComponentMsg);
      model.setDragAndDrop(securityProvider.checkPermission(principal,
                                                            ResourceType.PORTAL_REPOSITORY_TREE_DRAG_AND_DROP,
                                                            "*", ResourceAction.ACCESS));

      return model;
   }

   private final SecurityProvider securityProvider;
}

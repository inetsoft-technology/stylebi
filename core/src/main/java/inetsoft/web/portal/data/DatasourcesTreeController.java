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
package inetsoft.web.portal.data;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class DatasourcesTreeController implements ApplicationContextAware {
   public DatasourcesTreeController() {
   }

   @Autowired
   public DatasourcesTreeController(DatasourcesTreeService datasourcesTreeService) {
      this.datasourcesTreeService = datasourcesTreeService;
   }

   @Autowired
   public void setDatasourcesTreeService(DatasourcesTreeService datasourcesTreeService) {
      this.datasourcesTreeService = datasourcesTreeService;
   }

   @GetMapping("api/portal/data/tree")
   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   public TreeNodeModel getDataNavigationTree(Principal principal) throws Exception {
      DatasourcesTreeService treeService = getDatasourcesTreeService();
      return treeService == null ? TreeNodeModel.builder().build() : treeService.getRoot(principal);
   }

   @GetMapping("api/portal/data/tree/search")
   @Secured(@RequiredPermission(
      resourceType = ResourceType.PORTAL_TAB,
      resource = "Data",
      actions = ResourceAction.ACCESS
   ))
   public TreeNodeModel searchDataNavigationTree(@RequestParam("searchString") String searchString,
                                                 Principal principal)
      throws Exception
   {
      DatasourcesTreeService treeService = getDatasourcesTreeService();
      return treeService == null ? TreeNodeModel.builder().build() :
         treeService.search(searchString, principal);
   }

   @Override
   public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
      this.applicationContext = applicationContext;
   }

   private DatasourcesTreeService getDatasourcesTreeService() {
      if(this.datasourcesTreeService == null && this.applicationContext != null) {
         this.datasourcesTreeService = this.applicationContext.getBean(DatasourcesTreeService.class);
      }

      return this.datasourcesTreeService;
   }

   private DatasourcesTreeService datasourcesTreeService;
   private ApplicationContext applicationContext;
}

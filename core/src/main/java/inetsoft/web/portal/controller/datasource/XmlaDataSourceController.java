/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller.datasource;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.data.DataSourceXmlaDefinition;
import inetsoft.web.portal.model.database.cube.xmla.*;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import inetsoft.web.security.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
public class XmlaDataSourceController {
   public XmlaDataSourceController(XmlaDatasourceService xmlaDatasourceService) {
      this.xmlaDatasourceService = xmlaDatasourceService;
   }

   @PostMapping("api/portal/data/datasource/xmla/new")
   public void saveNewDatasource(@RequestBody DataSourceXmlaDefinition model, Principal principal)
      throws Exception
   {
      xmlaDatasourceService.createNewDataSource(model, principal);
   }

   @GetMapping("api/portal/data/datasource/xmla/new")
   public DataSourceXmlaDefinition createModel(@RequestParam("parentPath") String parentPath)
      throws Exception
   {
      return xmlaDatasourceService.getNewDataSourceModel(parentPath);
   }

   @GetMapping("api/portal/data/datasource/xmla/edit/**")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public DataSourceXmlaDefinition getModel(@RemainingPath @PermissionPath String path,
                                            Principal principal)
      throws Exception
   {
      return xmlaDatasourceService.getDataSourceModel(path, principal);
   }

   @PostMapping("api/portal/data/datasource/xmla/update")
   public void setModel(@RequestParam("path") String path,
                        @RequestBody DataSourceXmlaDefinition model,
                        Principal principal)
      throws Exception
   {
      xmlaDatasourceService.updateDataSource(path, model, principal);
   }

   @PostMapping("api/portal/data/datasource/xmla/catalogs")
   public List<String> getCatalogs(@RequestBody DataSourceXmlaDefinition model) throws Exception {
      return xmlaDatasourceService.getCatalogs(model);
   }

   @GetMapping("api/portal/data/datasource/xmla/metadataTree/**")
   public TreeNodeModel getCubes(@RemainingPath String name) throws Exception {
      return xmlaDatasourceService.getCubeMetaTree(name);
   }

   @PostMapping("api/portal/data/datasource/xmla/metadata/refresh")
   public CubeMetaModel refreshMeta(@RequestBody DataSourceXmlaDefinition model,
                                    Principal principal)
   {
      return xmlaDatasourceService.refreshCubes(model, principal);
   }

   @PostMapping("api/portal/data/datasource/xmla/viewSampleData")
   public SampleDataModel viewSampleData(@RequestBody ViewSampleDataRequest model) {
      return SampleDataModel.builder()
         .tableCells(xmlaDatasourceService.getSampleData(model))
         .build();
   }

   @PostMapping("api/portal/data/datasource/xmla/test")
   public ConnectionStatus testConnect(@RequestBody DataSourceXmlaDefinition model) {
      return xmlaDatasourceService.testConnect(model);
   }

   private final XmlaDatasourceService xmlaDatasourceService;
}

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
package inetsoft.web.admin.content.database.model;

import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyException;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.model.database.StringWrapper;
import inetsoft.web.viewsheet.AuditObjectName;
import inetsoft.web.viewsheet.Audited;
import org.apache.commons.io.FileExistsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
public class DataModelFolderManagerService {

   @Autowired
   public DataModelFolderManagerService(XRepository repository,
                                        SecurityEngine securityEngine,
                                        LogicalModelService modelService,
                                        DataSourceService dataSourceService,
                                        DatasourcesService datasourcesService,
                                        PhysicalModelManagerService physicalModelManagerService)
   {
      this.repository = repository;
      this.modelService = modelService;
      this.securityEngine = securityEngine;
      this.dataSourceService = dataSourceService;
      this.datasourcesService = datasourcesService;
      this.physicalModelManagerService = physicalModelManagerService;
   }

   /**
    * Create a data model folder to data base.
    */
   @Audited(
      objectType = ActionRecord.OBJECT_TYPE_FOLDER,
      actionName = ActionRecord.ACTION_NAME_CREATE
   )
   public void setDataModelFolder(@AuditObjectName(order = 1) String databasePath,
                                  @AuditObjectName(order = 2) String folderName,
                                  Principal principal)
      throws Exception
   {
      boolean hasPermission = securityEngine.checkPermission(principal,
         ResourceType.DATA_MODEL_FOLDER, databasePath + "/" + folderName,
         ResourceAction.WRITE);

      if(!hasPermission) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + databasePath + folderName + "\" by user " +
               principal);
      }

      XDataModel dataModel = dataSourceService.getDataModel(databasePath);

      if(dataModel == null) {
         throw new FileExistsException(databasePath);
      }

      dataModel.addFolder(folderName);
      repository.updateDataModel(dataModel);
   }

   @Audited(
      objectType = ActionRecord.OBJECT_TYPE_FOLDER,
      actionName = ActionRecord.ACTION_NAME_DELETE
   )
   public boolean deleteDataModelFolder(@AuditObjectName(order = 1) String databasePath,
                                        @AuditObjectName(order = 2) String folderName,
                                        Principal principal)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(databasePath);

      if(dataModel == null) {
         throw new FileExistsException(databasePath);
      }

      boolean hasPermission = securityEngine.checkPermission(principal,
         ResourceType.DATA_MODEL_FOLDER, databasePath + "/" + folderName,
         ResourceAction.DELETE);

      if(!hasPermission) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + databasePath + folderName + "\" by user " +
               principal);
      }

      for(String name : dataModel.getLogicalModelNames()) {
         XLogicalModel logicalModel = dataModel.getLogicalModel(name);

         if(Tool.equals(logicalModel.getFolder(), folderName)) {
            modelService.removeModel(databasePath, folderName, logicalModel.getName(), null,
               principal);
         }
      }

      String[] partitionNames = dataModel.getPartitionNames();

      for(String partitionName : partitionNames) {
         XPartition partition = dataModel.getPartition(partitionName);

         if(partition != null && Tool.equals(partition.getFolder(), folderName)) {
            physicalModelManagerService.removeModel(databasePath, partition.getFolder(),
               partitionName, null, true, principal);
         }
      }

      dataModel.removeFolder(folderName);
      repository.updateDataModel(dataModel);
      securityEngine.removePermission(ResourceType.DATA_MODEL_FOLDER,
         databasePath + "/" + folderName);

      return true;
   }

   /**
    * Return the logical/vpm list which not in the target folder and depends on the target partition.
    *
    * @param dataModel    the data model.
    * @param partitionName the target partition name.
    * @param folder        the data model folder of the partition.
    * @return
    */
   private List getPartitionOuterDependencies(XDataModel dataModel, String partitionName,
                                              String folder)
   {
      List list = new ArrayList();

      for(String name : dataModel.getLogicalModelNames()) {
         XLogicalModel lmodel = dataModel.getLogicalModel(name);

         if(Tool.equals(partitionName, lmodel.getPartition()) &&
            !Tool.equals(folder, lmodel.getFolder()))
         {
            list.add(lmodel);
         }
      }

      String[] names = dataModel.getVirtualPrivateModelNames();

      for(String name : names) {
         VirtualPrivateModel vpm = dataModel.getVirtualPrivateModel(name);
         @SuppressWarnings("unchecked")
         Enumeration<VpmCondition> conds = vpm.getConditions();

         while(conds.hasMoreElements()) {
            VpmCondition cond = conds.nextElement();

            if(cond.getType() == VpmCondition.PHYSICMODEL &&
               partitionName.equals(cond.getTable()))
            {
               list.add(vpm);
            }
         }
      }

      return list;
   }

   /**
    * Check outer dependencies for logical models under the target folder;
    */
   public StringWrapper checkOuterDependencies(String database, String folder) throws Exception {
      if(database == null || folder == null) {
         return null;
      }

      Catalog catalog = Catalog.getCatalog();
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new MessageException(catalog.getString("notFind.dataModel", database));
      }

      List<XLogicalModel> list = dataModel.getLogicalModels(folder);
      StringBuffer buffer = new StringBuffer();

      for(int i = 0; list != null && i < list.size(); i++) {
         if(list.get(i) == null) {
            continue;
         }

         try {
            datasourcesService.checkModelOuterDependencies(list.get(i));
         }
         catch(DependencyException ex) {
            if(buffer.length() != 0) {
               buffer.append("\n");
            }

            buffer.append(ex.getMessage(false));
         }
      }

      String[] partitionNames = dataModel.getPartitionNames();
      List dependenciesList = null;

      for(String partitionName : partitionNames) {
         XPartition partition = dataModel.getPartition(partitionName);

         if(partition != null && Tool.equals(partition.getFolder(), folder)) {
            dependenciesList = getPartitionOuterDependencies(dataModel, partitionName, folder);

            if(dependenciesList.size() != 0) {
               DependencyException ex = new DependencyException(partition);
               ex.addDependencies(dependenciesList.toArray());

               if(buffer.length() != 0) {
                  buffer.append("\n");
               }

               buffer.append(ex.getMessage(false));
            }
         }
      }

      if(buffer.length() == 0) {
         return null;
      }

      buffer.append(" " + Catalog.getCatalog().getString("Proceed") + "?");
      StringWrapper wrapper = new StringWrapper();
      wrapper.setBody(buffer.toString());
      return wrapper;
   }

   private final PhysicalModelManagerService physicalModelManagerService;
   private final LogicalModelService modelService;
   private final XRepository repository;
   private final SecurityEngine securityEngine;
   private final DataSourceService dataSourceService;
   private final DatasourcesService datasourcesService;
}

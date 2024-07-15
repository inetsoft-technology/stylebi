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
package inetsoft.web.portal.controller.database;

import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.XMetaDataNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.rgraph.TableNode;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.CheckTableAliasEvent;
import inetsoft.web.portal.service.database.PhysicalGraphService;
import inetsoft.web.viewsheet.*;
import org.apache.commons.io.FileExistsException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.uql.DataSourceListingService.LOG;

/**
 * {@code PhysicalModelManagerService} provides methods to manage physical models.
 */
@Component
public class PhysicalModelManagerService {
   @Autowired
   public PhysicalModelManagerService(DataSourceService dataSourceService,
                                      PhysicalModelService physicalModelService,
                                      RuntimePartitionService runtimePartitionService,
                                      PhysicalGraphService graphService,
                                      XRepository repository)
   {
      this.dataSourceService = dataSourceService;
      this.physicalModelService = physicalModelService;
      this.runtimePartitionService = runtimePartitionService;
      this.graphService = graphService;
      this.repository = repository;
   }

   /**
    * Opens an existing physical model.
    *
    * @param dataSource  the name of the parent data source.
    * @param parent      the name of the parent physical model or {@code null} if none.
    * @param name        the name of the physical model.
    * @param principal   a principal that identifies the remote user.
    *
    * @return the physical model definition.
    */
   public PhysicalModelDefinition openModel(String dataSource, String parent, String name,
                                            Principal principal) throws Exception
   {
      XDataModel dataModel = physicalModelService.getDataModel(dataSource, name);
      XPartition partition = dataModel.getPartition(parent == null ? name : parent);
      String folder = partition == null ? null : partition.getFolder();

      if(!dataSourceService.checkPermission(dataSource, folder, ResourceAction.WRITE, principal)) {
         throw new SecurityException("Unauthorized access to resource \"" + dataSource
            + (folder == null ? "" : "/" + folder) + "\" by user " + principal);
      }

      RuntimePartitionService.RuntimeXPartition runtimeXPartition =
         this.runtimePartitionService.openModel(dataModel, name, parent);

      if(runtimeXPartition == null) {
         throw new FileNotFoundException(dataSource + "/" + name);
      }

      // for table location of portal <--- studio
      graphService.shrinkColumnHeight(runtimeXPartition);

      return physicalModelService.createModel(dataModel, runtimeXPartition);
   }

   /**
    * Closes an open model.
    *
    * @param id the runtime identifier of the open model.
    */
   public void closeModel(String id) {
      this.runtimePartitionService.destroy(id);
   }

   /**
    * Creates a new physical model.
    *
    * @param dataSource the name of the parent data source.
    * @param parent     the name of the parent model or {@code null} if none.
    * @param model      the model definition.
    * @param principal  a principal that identifies the remote user.
    *
    * @return the new physical model definition.
    */
   public PhysicalModelDefinition createModel(String dataSource, String parent,
                                              PhysicalModelDefinition model, Principal principal)
      throws Exception
   {
      if(model == null) {
         // throw exception
         return null;
      }

      if(!dataSourceService.checkPermission(dataSource, model.getFolder(),
         ResourceAction.WRITE, principal))
      {
         throw new SecurityException(
            "Unauthorized access to resource \"" + dataSource + "\" by user " + principal);
      }

      boolean isExtended = !StringUtils.isEmpty(parent);
      XPartition partition = physicalModelService.createPartition(model);
      XDataModel dataModel = getDataModel(dataSource);
      partition.setDataModel(dataModel);

      if(isExtended) {
         XPartition basePartition = dataModel.getPartition(parent);

         if(basePartition == null) {
            throw new FileNotFoundException(dataSource + "/" + parent);
         }

         partition.setBaseParitition(basePartition);
      }

      partition = (XPartition) partition.deepClone(true);
      RuntimePartitionService.RuntimeXPartition rp =
         this.runtimePartitionService.createModel(partition, dataSource);

      // for table location of portal <--- studio
      graphService.shrinkColumnHeight(rp);

      return physicalModelService.createModel(dataModel, rp);
   }

   /**
    * Creates a new model and immediately saves it.
    *
    * @param dataSource the name of the parent data source.
    * @param parent     the name of the parent model or {@code null} if none.
    * @param model      the model definition.
    * @param principal  a principal that identifies the remote user.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_CREATE,
      objectType = ActionRecord.OBJECT_TYPE_PHYSICAL_VIEW
   )
   public void createAndSaveModel(@AuditObjectName(order = 0) String dataSource,
                                  @AuditObjectName(order = 1) String folder,
                                  @AuditObjectName(order = 2) String parent,
                                  @AuditObjectName(value = "getName()", order = 3) PhysicalModelDefinition model,
                                  Principal principal)
      throws Exception
   {
      XDataModel dataModel = getDataModel(dataSource);
      boolean isExtended = !StringUtils.isEmpty(parent);
      String path;

      if(!isExtended) {
         path = dataSource + "/" + model.getName();
      }
      else {
         path = dataSource + "/" + parent + "/" + model.getName();
      }

      if(!dataSourceService.checkPermission(dataSource, folder, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + dataSource + "\" by user " + principal);
      }

      if(!isExtended) {
         if(dataModel.getPartition(model.getName()) != null) {
            throw new FileExistsException(path);
         }
      }
      else {
         XPartition parentModel = dataModel.getPartition(parent);

         if(parentModel != null && parentModel.getPartition(model.getName()) != null) {
            throw new FileExistsException(path);
         }
      }

      getRuntimePartition(model.getId()).ifPresent(
         p -> createAndSaveModel(dataModel, p, path, parent, isExtended, principal));
   }

   private void createAndSaveModel(XDataModel dataModel,
                                   RuntimePartitionService.RuntimeXPartition rPartition,
                                   String path, String parent, boolean isExtended,
                                   Principal principal)
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      XPartition partition = rPartition.getPartition();
      graphService.unfoldColumnHeight(rPartition, null);

      if(!isExtended) {
         dataModel.addPartition(partition);
      }
      else {
         XPartition parentModel = dataModel.getPartition(parent);

         if(parentModel != null) {
            partition.setBaseParitition(parentModel);
            parentModel.addPartition(partition, false);
         }
      }

      try {
         repository.updateDataModel(dataModel);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to update data model", e);
      }

      AssetEntry entry;

      if(!isExtended) {
         entry =  new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PARTITION,
                                 path, null);
      }
      else {
         entry =  new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.EXTENDED_PARTITION,
                                 path, null);
      }

      entry = dataSourceService.getModelAssetEntry(entry);
      entry.setCreatedUsername(principal == null ? null : pId.name);
      dataSourceService.updateDataSourceAssetEntry(entry);
      DependencyHandler.getInstance().updatePhysicalDependencies(entry, dataModel.getDataSource());

      // re-shrink columns height
      partition = (XPartition) partition.deepClone(true);
      rPartition.setPartition(partition);

      graphService.shrinkColumnHeight(rPartition);
   }

   /**
    * Updates a physical model and immediately saves it.
    *
    * @param dsName the name of the parent data source.
    * @param parent     the name of the parent model or {@code null} if none.
    * @param name       the current name of the physical model.
    * @param model      the updated physical model definition.
    * @param principal  a principal that identifies the remote user.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_PHYSICAL_VIEW
   )
   public void updateAndSaveModel(@AuditObjectName(order = 0) String dsName,
                                  @AuditObjectName(order = 1) String folder,
                                  @AuditObjectName(order = 2) String parent, String name,
                                  @AuditObjectName(value = "getName()", order = 3) PhysicalModelDefinition model,
                                  Principal principal)
      throws Exception
   {
      XDataModel dataModel = getDataModel(dsName);
      boolean isExtended = !StringUtils.isEmpty(parent);

      if(isExtended) {
         XPartition parentPartition = dataModel.getPartition(parent);

         if(parentPartition == null) {
            throw new FileNotFoundException(dsName + "/" + parent);
         }

         if(parentPartition.getPartition(name) == null) {
            throw new FileNotFoundException(dsName + "/" + parent + "/" + name);
         }
      }
      else {
         if(dataModel.getPartition(name) == null) {
            throw new FileNotFoundException(dsName + "/" + name);
         }
      }

      if(!dataSourceService.checkPermission(dsName, folder, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + dsName + "\" by user " +
               principal);
      }

      for(PhysicalTableModel table: model.getTables()) {
         if(!Tool.equals(table.getOldAlias(), table.getAlias())) {
            for(String logicalModelName : dataModel.getLogicalModelNames()) {
               XLogicalModel logicalModel = dataModel.getLogicalModel(logicalModelName);
               String partition = logicalModel.getPartition();

               if(Tool.equals(partition, name)) {
                  renameAttribute(logicalModel, table);
               }
            }
         }
      }

      String path = isExtended ? dsName + "/" + parent + "/" + model.getName():
         dsName + "/" + model.getName();
      AssetEntry.Type entryType = isExtended ? AssetEntry.Type.EXTENDED_PARTITION :
         AssetEntry.Type.PARTITION;
      AssetEntry entry = dataSourceService.getModelAssetEntry(
         new AssetEntry(AssetRepository.QUERY_SCOPE, entryType, path, null));
      getRuntimePartition(model.getId()).ifPresent(
         p -> updateAndSaveModel(dataModel, p, parent, name, entry, isExtended));
   }

   private void renameAttribute(XLogicalModel logicalModel, PhysicalTableModel table) {
      String path = null;
      AssetEntry.Type type = null;
      Enumeration<XEntity> entities = logicalModel.getEntities();

      while(entities.hasMoreElements()) {
         XEntity xEntity = entities.nextElement();
         Enumeration<XAttribute> attributes = xEntity.getAttributes();

         while(attributes.hasMoreElements()) {
            XAttribute xAttribute = attributes.nextElement();

            if(Tool.equals(table.getOldAlias(), xAttribute.getTable())) {
               xAttribute.setTable(table.getAlias());
            }
         }
      }

      if(logicalModel.getBaseModel() != null) {
         path = logicalModel.getDataSource() + "/" + logicalModel.getBaseModel().getName() + "/" + logicalModel.getName();
         type = AssetEntry.Type.EXTENDED_LOGIC_MODEL;
      }
      else {
         path = logicalModel.getDataSource() + "/" + logicalModel.getName();
         type = AssetEntry.Type.LOGIC_MODEL;
      }

      DataSourceRegistry.getRegistry().updateObject(path, path, type, logicalModel);
   }

   private void updateAndSaveModel(XDataModel dataModel,
                                   RuntimePartitionService.RuntimeXPartition rPartition,
                                   String parent, String name, AssetEntry entry,
                                   boolean isExtended)
   {
      XPartition xPartition = rPartition.getPartition();

      if(isExtended) {
         XPartition parentPartition = dataModel.getPartition(parent);
         XPartition originalPartition = parentPartition.getPartition(name);
         graphService.unfoldColumnHeight(rPartition, originalPartition);
         parentPartition.removePartition(name);
         parentPartition.addPartition(xPartition, false);
      }
      else {
         XPartition originalPartition = dataModel.getPartition(name);
         graphService.unfoldColumnHeight(rPartition, originalPartition);
         dataModel.addPartition(xPartition);
      }

      try {
         repository.updateDataModel(dataModel);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to update data model", e);
      }

      String user = entry.getCreatedUsername();
      Date date = entry.getCreatedDate();

      entry = dataSourceService.getModelAssetEntry(entry);
      entry.setCreatedUsername(user);
      entry.setCreatedDate(date);
      dataSourceService.updateDataSourceAssetEntry(entry);
      DependencyHandler dependencyHandler = DependencyHandler.getInstance();
      dependencyHandler.updatePhysicalDependencies(entry, dataModel.getDataSource());

      // re-shrink columns height
      xPartition = (XPartition) xPartition.deepClone(true);
      rPartition.setPartition(xPartition);

      graphService.shrinkColumnHeight(rPartition);
   }

   /**
    * Renames a physical model.
    *
    * @param dataSource  the name of the parent data source.
    * @param oldName     the old name of the model.
    * @param newName     the new name of the model.
    * @param description a description of the model.
    * @param principal   a principal that identifies the remote user.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_RENAME,
      objectType = ActionRecord.OBJECT_TYPE_PHYSICAL_VIEW
   )
   public void renameModel(@AuditActionError(value = "'Target Entry: ' + #this", order = 1) @AuditObjectName(order = 1) String dataSource,
                           @AuditActionError(order = 2) @AuditObjectName(order = 2) String folder,
                           @AuditObjectName(order = 3) String oldName,
                           @AuditActionError(order = 3) String newName,
                           String description, Principal principal)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(dataSource);

      if(dataModel == null) {
         throw new FileNotFoundException(dataSource);
      }

      if(!dataSourceService.checkPermission(dataSource, folder, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + dataSource + "\" by user " +
               principal);
      }

      String path = dataSource + "/" + oldName;
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.PARTITION, path, null);

      entry = dataSourceService.getModelAssetEntry(entry);
      String user = entry.getCreatedUsername();
      Date date = entry.getCreatedDate();
      dataModel.renamePartition(oldName, newName, description);
      repository.updateDataModel(dataModel);
      String npath = dataSource + "/" + newName;
      AssetEntry newEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                             AssetEntry.Type.PARTITION, npath, null);
      newEntry = dataSourceService.getModelAssetEntry(newEntry);
      newEntry.setCreatedUsername(user);
      newEntry.setCreatedDate(date);
      dataSourceService.updateDataSourceAssetEntry(newEntry);
      RenameInfo rinfo = new RenameInfo(path, npath, RenameInfo.PARTITION | RenameInfo.SOURCE);
      rinfo.setModelFolder(folder);
      RenameTransformHandler.getTransformHandler().addTransformTask(rinfo);
      DependencyStorageService service = DependencyStorageService.getInstance();
      DependencyHandler.getInstance().renameDependencies(entry, newEntry);
      service.rename(entry.toIdentifier(), newEntry.toIdentifier());
   }

   /**
    * Removes a physical model.
    *
    * @param dataSource the name of the parent data source.
    * @param name       the name of the physical model.
    * @param principal  a principal that identifies the remote user.
    *
    * @return {@code true} if the model was remove or {@code false} if it does not exist.
    */
   public boolean removeModel(@AuditObjectName(order = 0) String dataSource,
                              @AuditObjectName(order = 1) String folder,
                              @AuditObjectName(order = 3) String name,
                              @AuditObjectName(order = 2) String parent,
                              Principal principal)
      throws Exception
   {
      return removeModel(dataSource, folder, name, parent, false, principal);
   }

   /**
    * Removes a physical model.
    *
    * @param dataSource the name of the parent data source.
    * @param name       the name of the physical model.
    * @param force      force to delete without checking dependencies, else not.
    * @param principal  a principal that identifies the remote user.
    *
    * @return {@code true} if the model was remove or {@code false} if it does not exist.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_DELETE,
      objectType = ActionRecord.OBJECT_TYPE_PHYSICAL_VIEW
   )
   public boolean removeModel(@AuditObjectName(order = 0) String dataSource,
                              @AuditObjectName(order = 1) String folder,
                              @AuditObjectName(order = 3) String name,
                              @AuditObjectName(order = 2) String parent,
                              boolean force,
                              Principal principal)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(dataSource);

      if(dataModel == null) {
         throw new FileNotFoundException(dataSource);
      }

      if(!dataSourceService.checkPermission(dataSource, folder, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + dataSource + "\" by user " +
               principal);
      }

      boolean isExtended = !StringUtils.isEmpty(parent);
      XPartition partition;
      XPartition basePartition = null;

      if(!isExtended) {
         partition = dataModel.getPartition(name);

         if(partition == null) {
            throw new FileNotFoundException(dataSource + "/" + name);
         }
      }
      else {
         basePartition = dataModel.getPartition(parent);

         if(basePartition == null) {
            throw new FileNotFoundException(dataSource + "/" + parent);
         }

         partition = basePartition.getPartition(name);

         if(partition == null) {
            throw new FileNotFoundException(dataSource + "/" + parent + "/" + name);
         }
      }

      if(!force && dataModel.partitionIsUsed(name)) {
         return false;
      }

      if(isExtended && basePartition != null) {
         basePartition.removePartition(name);
      }
      else {
         dataModel.removePartition(name);
      }

      repository.updateDataModel(dataModel);
      String path = dataSource + "/" + name;
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.PARTITION, path, null);
      DependencyHandler.getInstance().deleteDependencies(entry);
      DependencyHandler.getInstance().deleteDependenciesKey(entry);
      return true;
   }

   /**
    * Adds a table to an open physical model.
    *
    * @param runtimeId the runtime identifier of the open physical model.
    * @param table     the table to add.
    * @param fixBounds {@code true} to fix the bounding rectangle of the table.
    *
    * @return the name of the added table.
    */
   public String addTable(String runtimeId, PhysicalTableModel table, boolean fixBounds) {
      return getRuntimePartition(runtimeId).map(p -> addTable(p, table, fixBounds)).orElse(null);
   }

   /**
    * Adds a table to an open physical model.
    *
    * @param runtimeId the runtime identifier of the open physical model.
    * @param tableNode     the table to add.
    * @param fixBounds {@code true} to fix the bounding rectangle of the table.
    *
    * @return the name of the added table.
    */
   public String addTable(String runtimeId, PhysicalModelTreeNodeModel tableNode,
                          boolean fixBounds)
      throws Exception
   {
      PhysicalTableModel table = createTableModel(runtimeId, tableNode);
      return getRuntimePartition(runtimeId).map(p -> addTable(p, table, fixBounds)).orElse(null);
   }

   public String addTable(String runtimeId, String name, String catalog, String schema, String path,
                          String alias, String sql, String aliasSource, boolean supportCatalog,
                          boolean fixBounds)
      throws Exception
   {
      PhysicalTableModel table = createTableModel(
         runtimeId, name, catalog, schema, path, alias, sql, aliasSource, supportCatalog);
      return getRuntimePartition(runtimeId).map(p -> addTable(p, table, fixBounds)).orElse(null);
   }

   /**
    * Create a table model using tree node.
    * @param runtimeId runtime partition id.
    * @param node tree node.
    * @return created table model.
    */
   private PhysicalTableModel createTableModel(String runtimeId, PhysicalModelTreeNodeModel node)
      throws Exception
   {
      return createTableModel(
         runtimeId, node.getName(), node.getCatalog(), node.getSchema(), node.getPath(),
         node.getAlias(), node.getSql(), node.getAliasSource(), node.isSupportCatalog());
   }

   private PhysicalTableModel createTableModel(String runtimeId, String name, String catalog,
                                               String schema, String path, String alias, String sql,
                                               String aliasSource, boolean supportCatalog)
      throws Exception
   {
      PhysicalTableModel table = new PhysicalTableModel();
      table.setName(name);
      table.setCatalog(catalog);
      table.setSchema(schema);
      table.setPath(path);
      table.setAlias(alias);
      table.setSql(sql);
      table.setType(!StringUtils.isEmpty(sql) ? TableType.VIEW : TableType.PHYSICAL);
      table.setJoins(new ArrayList<>());
      table.setAutoAliases(new ArrayList<>());
      table.setAutoAliasesEnabled(false);
      table.setAliasSource(aliasSource);
      table.setBaseTable(false);

      RuntimePartitionService.RuntimeXPartition rp = getRuntimePartition(runtimeId).get();
      XDataModel dataModel = getDataModel(rp.getDataSource());
      String additional = rp.getPartition().getConnection();
      JDBCDataSource jdbc = (JDBCDataSource) dataSourceService.
         getDataSource(rp.getDataSource(), additional);

      if(jdbc == null || dataModel == null) {
         throw new FileNotFoundException(rp.getDataSource());
      }

      DefaultMetaDataProvider metaData =
         dataSourceService.getDefaultMetaDataProvider(jdbc, dataModel);
      XNode query = new XMetaDataNode(name);
      query.setAttribute("type", "TABLE");
      query.setAttribute("catalog", table.getCatalog());
      query.setAttribute("schema", schema);
      query.setAttribute("supportCatalog", Boolean.toString(supportCatalog));
      query.setAttribute(XUtil.PORTAL_DATA, "true");
      TableNode tableMetaData = metaData.getTableMetaData(query);

      if(tableMetaData == null) {
         throw new FileNotFoundException(table.getPath());
      }

      table.setQualifiedName(tableMetaData.getName());

      return table;
   }

   private String addTable(RuntimePartitionService.RuntimeXPartition rp, PhysicalTableModel table,
                           boolean fixBounds)
   {
      XPartition partition = rp.getPartition();
      String tableName = null;

      if(table != null) {
         tableName = physicalModelService.addPartitionTable(partition, table);

         if(fixBounds) {
            fixTableBounds(rp.getId(), partition, tableName);
         }
      }

      return tableName;
   }

   /**
    * Removes a table from an open physical model.
    *
    * @param runtimeId     the runtime identifier of the open physical model.
    * @param qualifiedName the fully-qualified name of the table.
    * @param tableName     the name of the table.
    */
   public void removeTable(String runtimeId, String qualifiedName, String tableName) {
      if(!StringUtils.isEmpty(qualifiedName)) {
         getRuntimePartition(runtimeId).ifPresent(p -> removeTable(p, qualifiedName, tableName));
      }
   }

   private void removeTable(RuntimePartitionService.RuntimeXPartition rp, String qualifiedName, String tableName) {
      XPartition partition = rp.getPartition();

      partition.removeTable(qualifiedName);
      rp.removeMovedTable(qualifiedName);

      for(int i = partition.getRelationshipCount() -1 ; i >= 0; i--) {
         XRelationship relationship = partition.getRelationship(i);

         if(Tool.equals(qualifiedName, relationship.getIndependentTable()) ||
            Tool.equals(qualifiedName, relationship.getDependentTable())) {
            partition.removeRelationship(i);
         }
      }

      AutoAlias autoAlias = partition.getAutoAlias(tableName);

      if(autoAlias != null) {
         autoAlias.removeIncomingJoin(qualifiedName);
      }
   }

   /**
    * Creates an inline view.
    *
    * @param runtimeId the runtime identifier of an open physical model.
    * @param table     the view definition.
    */
   public void createInlineView(String runtimeId, PhysicalTableModel table) {
      String tableName = addTable(runtimeId, table, false);

      if(tableName != null) {
         getPartition(runtimeId).ifPresent(p -> fixTableBounds(runtimeId, p, tableName));
      }
   }

   /**
    * Updates an inline view definition.
    *
    * @param runtimeId the runtime identifier of the physical model.
    * @param oldName   the old name of the view.
    * @param table     the updated view definition.
    */
   public void updateInlineView(String runtimeId, String oldName, PhysicalTableModel table) {
      if(table != null && !StringUtils.isEmpty(oldName)) {
         getRuntimePartition(runtimeId).ifPresent(p -> updateInlineView(p, oldName, table));
      }
   }

   private void updateInlineView(RuntimePartitionService.RuntimeXPartition rp, String oldName,
                                 PhysicalTableModel table)
   {
      XPartition partition = rp.getPartition();
      XPartition.PartitionTable oldTable = getPartitionTable(partition, oldName);
      Rectangle bounds = partition.getBounds(oldName);
      XPartition applyAliasPartition = partition.applyAutoAliases();
      Enumeration<XPartition.PartitionTable> tables = applyAliasPartition.getTables();

      // remove the out going auto alias tables these are base from old table to remove the meta data.
      while(tables.hasMoreElements()) {
         XPartition.PartitionTable applyAliasTable = tables.nextElement();

         if(applyAliasTable != null && oldName != null &&
            oldName.equals(applyAliasTable.getSourceTable()))
         {
            applyAliasPartition.removeTable(applyAliasTable.getName());
         }
      }

      partition.removeTable(oldName);

      if(oldTable != null) {
         String name = table.getAlias();
         name = name == null ? table.getName() : name;
         oldTable.setName(name);
         oldTable.setSql(table.getSql());
         partition.addTable(oldTable.getName(), oldTable.getType(), oldTable.getSql(),
                            bounds, oldTable.getCatalog(), oldTable.getSchema());
      }

      for(Enumeration<XRelationship> e = partition.getRelationships(); e.hasMoreElements();) {
         XRelationship rs = e.nextElement();

         if(Tool.equals(rs.getIndependentTable(), oldName)) {
            rs.setIndependent(table.getName(), rs.getIndependentColumn());
         }

         if(Tool.equals(rs.getDependentTable(), oldName)) {
            rs.setDependent(table.getName(), rs.getDependentColumn());
         }
      }

      updateAliasMap(partition, oldName, table.getName());
   }

   /**
    * Sync the aliastable map.
    * @param partition
    * @param oname the old source table name.
    * @param nname the new source table name.
    */
   private void updateAliasMap(XPartition partition, String oname, String nname) {
      if(oname == null || nname == null || partition == null) {
         return;
      }

      Enumeration<XPartition.PartitionTable> tables = partition.getTables(true);

      while(tables.hasMoreElements()) {
         XPartition.PartitionTable table = tables.nextElement();

         if(table != null && oname.equals(partition.getAliasTable(table.getName()))) {
            partition.setAliasTable(table.getName(), nname);
         }
      }
   }

   /**
    * Gets the options for auto-aliasing a table.
    *
    * @param qualifiedName the fully-qualified name of the table.
    *
    * @return the list of options.
    */
   public List<AutoAliasJoinModel> getAliases(String qualifiedName, String runtimeId) {
      XPartition partition = runtimePartitionService.getPartition(runtimeId);
      List<AutoAliasJoinModel> result = new ArrayList<>();
      AutoAlias autoAlias = partition.getAutoAlias(qualifiedName);
      Set<String> selectedTables = new HashSet<>();

      if(autoAlias != null) {
         int count = autoAlias.getIncomingJoinCount();

         for(int i = 0; i < count; i++) {
            AutoAlias.IncomingJoin incomingJoin = autoAlias.getIncomingJoin(i);
            AutoAliasJoinModel join = new AutoAliasJoinModel(incomingJoin);
            result.add(join);
            selectedTables.add(incomingJoin.getSourceTable());
         }
      }

      for(Enumeration<XRelationship> e2 = partition.getRelationships();
          e2.hasMoreElements(); ) {
         XRelationship relationship = e2.nextElement();
         String foreignTable = null;

         if(relationship.getDependentTable()
            .equals(qualifiedName)) {
            foreignTable = relationship.getIndependentTable();
         }
         else if(relationship.getIndependentTable()
            .equals(qualifiedName)) {
            foreignTable = relationship.getDependentTable();
         }

         if(foreignTable != null && !selectedTables.contains(foreignTable)) {
            selectedTables.add(foreignTable);
            AutoAliasJoinModel join = new AutoAliasJoinModel();
            join.setForeignTable(foreignTable);
            result.add(join);
         }
      }

      return result;
   }

   /**
    * Creates an alias table.
    *
    * @param runtimeId the runtime identifier of the open physical model.
    * @param table     the alias table to add.
    */
   public String createAlias(String runtimeId, PhysicalTableModel table) {
      String alias = table.getAlias();
      alias = alias == null ? table.getName() : alias;
      String invalidMessage = checkAliasValid(runtimeId, alias, null);

      if(invalidMessage != null) {
         return invalidMessage;
      }

      String tableName = addTable(runtimeId, table, false);

      if(tableName != null) {
         getPartition(runtimeId).ifPresent(p -> fixTableBounds(runtimeId, p, tableName));
      }

      return null;
   }

   /**
    * Modifies a table alias.
    *
    * @param runtimeId the runtime identifier of the open physical model.
    * @param oldName   the old name of the alias table.
    * @param table     the updated table definition.
    */
   public void updateAlias(String runtimeId, String oldName, PhysicalTableModel table) {
      if(table != null && !StringUtils.isEmpty(oldName)) {
         getRuntimePartition(runtimeId).ifPresent(p -> updateAlias(p, oldName, table));
      }
   }

   private void updateAlias(RuntimePartitionService.RuntimeXPartition rp, String oldName,
                            PhysicalTableModel table)
   {
      editAlias(table.getAlias(), oldName, rp);
   }

   public void editAlias(String alias, String oldAlias,
                         RuntimePartitionService.RuntimeXPartition runtimePartition)
   {
      if(alias.equals(oldAlias)) {
         return;
      }

      XPartition partition = runtimePartition.getPartition();
      String dataSource = runtimePartition.getDataSource();

      partition.renameAlias(dataSource, oldAlias, alias);

      // check the need renamed tables
      Enumeration<XPartition.PartitionTable> tenums = partition.getTables();

      while(tenums.hasMoreElements()) {
         XPartition.PartitionTable temp = tenums.nextElement();
         String tempAlias = temp.getName();

         if(partition.getAutoAlias(tempAlias) != null) {
            inetsoft.uql.erm.AutoAlias auto =
               partition.getAutoAlias(tempAlias);

            for(int j = 0; j < auto.getIncomingJoinCount(); j++) {
               inetsoft.uql.erm.AutoAlias.IncomingJoin join =
                  auto.getIncomingJoin(j);

               if(join.getSourceTable().equals(oldAlias)) {
                  join.setSourceTable(alias);
               }
            }
         }

         if(!tempAlias.equals(alias) && oldAlias.equals(partition.getAliasTable(tempAlias))) {
            partition.setAliasTable(tempAlias, alias);
         }
      }

      if(runtimePartition.removeMovedTable(oldAlias)) {
         runtimePartition.addMovedTable(alias);
      }
   }

   /**
    * Updates the auto-aliasing settings of a table.
    *
    * @param runtimeId the runtime identifier of the open physical model.
    * @param table     the table to update.
    */
   public void updateAutoAliasing(String runtimeId, PhysicalTableModel table) {
      if(table != null) {
         getPartition(runtimeId).ifPresent(p -> physicalModelService.addRemoveAutoAlias(p, table));
      }
   }

   /**
    * Gets the auto-join columns for a physical model.
    *
    * @param dataSource the name of the parent data source.
    * @param model      the physical model.
    * @param principal  a principal that identifies the remote user.
    *
    * @return the auto-join columns.
    */
   @SuppressWarnings("unchecked")
   public AutoJoinColumnsModel getAutoJoinColumns(String dataSource, PhysicalModelDefinition model,
                                                  Principal principal)
      throws Exception
   {
      String additional = model.getConnection();
      JDBCDataSource jdbc = (JDBCDataSource) dataSourceService.getDataSource(dataSource, additional);
      XDataModel dataModel = getDataModel(dataSource);
      AutoJoinColumnsModel result = new AutoJoinColumnsModel();
      DefaultMetaDataProvider metaData =
         dataSourceService.getDefaultMetaDataProvider(jdbc, dataModel);
      XPartition partition = physicalModelService.createPartition(model);
      boolean metaInfoAvailable = false;

      List<TableNode> tableNodeList = new ArrayList<>();

      for(Enumeration<XPartition.PartitionTable> e = partition.getTables();
          e.hasMoreElements();)
      {
         XPartition.PartitionTable partitionTable = e.nextElement();
         String fqn = partitionTable.getName();
         XNode xTableNode;
         TableNode tableNode;

         if(TableType.forType(partitionTable.getType()) == TableType.PHYSICAL) {
            xTableNode = PhysicalModelService.getTableXNode(partition, fqn, metaData);

            if(xTableNode == null) {
               continue;
            }

            tableNode = (TableNode) metaData.getTableMetaData(xTableNode).clone();

            if(partition.isAlias(fqn)) {
               tableNode.setName(fqn);
               tableNode.setAlias(fqn);
            }
         }
         else {
            xTableNode =
               physicalModelService.executeSQLQuery(partitionTable.getSql(), dataSource, principal);

            if(xTableNode == null) {
               continue;
            }

            XNodeTableLens lens = new XNodeTableLens(xTableNode);
            tableNode = new TableNode(fqn);

            for(int i = 0; i < lens.getColCount(); i++) {
               String column = lens.getColumnIdentifier(i);
               String type = lens.getColType(i).getCanonicalName().toLowerCase();
               type = type.substring(type.lastIndexOf('.') + 1);
               type = "timestamp".equals(type) ? "timeInstant" : type;
               tableNode.addColumn(column, type);
            }
         }

         if(tableNode.isKeyMetaDataAvailable()) {
            metaInfoAvailable = true;
         }

         tableNodeList.add(tableNode);
      }

      Map<String, Object> availableColumnNames =
         XUtil.getAutoJoinColumns(tableNodeList.toArray(new TableNode[0]),
                                  metaData, false);

      List<AutoJoinNameColumnModel> nameColumns = availableColumnNames.entrySet().stream()
         .map((nameSet) -> {
            AutoJoinNameColumnModel nameColumn = new AutoJoinNameColumnModel();
            nameColumn.setLabel(nameSet.getKey());
            nameColumn.setColumn(nameSet.getKey());
            nameColumn.setTables((List<String>) nameSet.getValue());
            return nameColumn;
         })
         .collect(Collectors.toList());

      result.setNameColumns(nameColumns);

      if(metaInfoAvailable) {
         result.setMetaAvailable(true);
         Map<String, Object> availableMetaColumns =
            XUtil.getAutoJoinColumns(tableNodeList.toArray(new TableNode[0]),
                                     metaData, true);

         List<AutoJoinMetaColumnModel> metaColumns = availableMetaColumns.entrySet().stream()
            .map((metaSet) -> {
               AutoJoinMetaColumnModel metaColumn = new AutoJoinMetaColumnModel();
               String name = metaSet.getKey();
               metaColumn.setLabel(name);
               int index = name.lastIndexOf('.');
               metaColumn.setTable(name.substring(0, index));
               metaColumn.setColumn(name.substring(index + 1));
               Vector<String[]> relatedColumns = (Vector<String[]>) metaSet.getValue();
               List<AutoJoinMetaColumnModel> forColumns = relatedColumns.stream()
                  .map(relatedColumn -> {
                     AutoJoinMetaColumnModel relatedMetaColumn = new AutoJoinMetaColumnModel();
                     relatedMetaColumn.setTable(relatedColumn[0]);
                     relatedMetaColumn.setColumn(relatedColumn[1]);
                     return relatedMetaColumn;
                  })
                  .collect(Collectors.toList());

               metaColumn.setForColumns(forColumns);
               return metaColumn;
            })
            .collect(Collectors.toList());

         result.setMetaColumns(metaColumns);
      }
      else {
         result.setMetaAvailable(false);
      }

      return result;
   }

   /**
    * Adds an auto-join to an open physical model.
    *
    * @param runtimeId the runtime identifier of the open physical model.
    * @param join      the join definition.
    * @param tableName the name of the independent table.
    * @param principal a principal that identifies the remote user.
    */
   public void addAutoJoin(String runtimeId, JoinModel join, String tableName, Principal principal)
   {
      getRuntimePartition(runtimeId).ifPresent(p -> addAutoJoin(p, join, tableName, principal));
   }

   private void addAutoJoin(RuntimePartitionService.RuntimeXPartition rp, JoinModel join,
                            String tableName, Principal principal)
   {
      String database = rp.getDataSource();
      XPartition xPartition = rp.getPartition();

      try {
         JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.getDataSource(
            database, xPartition.getConnection());
         XDataModel dataModel = getDataModel(database);
         DefaultMetaDataProvider metaData =
            dataSourceService.getDefaultMetaDataProvider(dataSource, dataModel);
         boolean isDependentKey = false;
         boolean isIndependentKey = false;
         XPartition.PartitionTable dependentTable =
            xPartition.getPartitionTable(tableName);
         XPartition.PartitionTable independentTable =
            xPartition.getPartitionTable(join.getForeignTable());

         if(dependentTable != null) {
            XNode tableNode;

            if(TableType.forType(dependentTable.getType()) == TableType.PHYSICAL) {
               tableNode = metaData.getPrimaryKeys(
                  PhysicalModelService.getTableXNode(xPartition, tableName, metaData));
            }
            else {
               tableNode = metaData.getPrimaryKeys(physicalModelService.executeSQLQuery(
                  dependentTable.getSql(), database, principal));
            }

            isDependentKey = XUtil.isPrimaryKey(join.getColumn(), tableNode);
         }

         if(independentTable != null) {
            XNode tableNode;

            if(TableType.forType(independentTable.getType()) == TableType.PHYSICAL) {
               tableNode = metaData.getPrimaryKeys(PhysicalModelService.getTableXNode(
                  xPartition, join.getForeignTable(), metaData));
            }
            else {
               tableNode = metaData.getPrimaryKeys(physicalModelService.executeSQLQuery(
                  independentTable.getSql(), database, principal));
            }

            isIndependentKey = XUtil.isPrimaryKey(join.getForeignColumn(), tableNode);
         }

         if(isDependentKey) {
            if(isIndependentKey) {
               join.setCardinality(JoinCardinality.ONE_TO_ONE);
            }
            else {
               join.setCardinality(JoinCardinality.ONE_TO_MANY);
            }
         }
         else {
            if(isIndependentKey) {
               join.setCardinality(JoinCardinality.MANY_TO_ONE);
            }
            else {
               join.setCardinality(JoinCardinality.MANY_TO_MANY);
            }
         }
      }
      catch(Exception ex) {
         LoggerFactory.getLogger(this.getClass()).warn(ex.getMessage(), ex);
      }

      XRelationship relationship = convertJoin(join, tableName);
      xPartition.addRelationship(relationship);
   }

   /**
    * Adds a join.
    *
    * @param runtimeId the runtime identifier of the open physical model.
    * @param join      the join definition.
    * @param tableName the name of the independent table.
    */
   public boolean checkJoinExist(String runtimeId, JoinModel join, String tableName) {
      Optional<XPartition> rtp = getPartition(runtimeId);

      if(rtp.isPresent()) {
         XRelationship relationship = convertJoin(join, tableName);

         Enumeration<XRelationship> relationships = rtp.get().getRelationships();

         while(relationships.hasMoreElements()) {
            if(relationships.nextElement().equalContents(relationship)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Adds a join.
    *
    * @param runtimeId the runtime identifier of the open physical model.
    * @param join      the join definition.
    * @param tableName the name of the independent table.
    */
   public void addJoin(String runtimeId, JoinModel join, String tableName) {
      getPartition(runtimeId).ifPresent(p -> addJoin(p, join, tableName));
   }

   private void addJoin(XPartition partition, JoinModel join, String tableName) {
      XRelationship relationship = convertJoin(join, tableName);
      partition.addRelationship(relationship);
   }

   /**
    * Updates a join definition.
    *
    * @param runtimeId the runtime identifier of the open physical model.
    * @param join      the updated join definition.
    * @param oldJoin   the old join definition.
    * @param tableName the name of the independent table.
    */
   public void updateJoin(String runtimeId, JoinModel join, JoinModel oldJoin, String tableName) {
      getPartition(runtimeId).ifPresent(p -> updateJoin(p, join, oldJoin, tableName));
   }

   private void updateJoin(XPartition partition, JoinModel join, JoinModel oldJoin,
                           String tableName)
   {
      XRelationship oldRelationship = convertJoin(oldJoin, tableName);
      XRelationship newRelationship = convertJoin(join, tableName);

      for(int i = 0; i < partition.getRelationshipCount(); i++) {
         if(oldRelationship.equalContents(partition.getRelationship(i))) {
            partition.setRelationship(i, newRelationship);
            break;
         }
      }
   }

   /**
    * Removes a join.
    *
    * @param runtimeId    the runtime identifier of the open physical model.
    * @param join         the join to remove.
    * @param foreignTable the name of the foreign table.
    * @param tableName    the name of the independent table.
    */
   public void removeJoin(String runtimeId, JoinModel join, String foreignTable,
                          String tableName)
   {
      getPartition(runtimeId).ifPresent(p -> removeJoin(p, join, foreignTable, tableName));
   }

   private void removeJoin(XPartition partition, JoinModel join, String foreignTable,
                           String tableName)
   {
      if(join != null) {
         XRelationship removeRelation = convertJoin(join, tableName);

         for(int i = 0; i < partition.getRelationshipCount(); i++) {
            if(removeRelation.equalContents(partition.getRelationship(i))) {
               deleteJoin(partition, partition.getRelationship(i));

               break;
            }
         }
      }

      if(foreignTable == null) {
         return;
      }

      for(int i = partition.getRelationshipCount() - 1; i >= 0; i--) {
         XRelationship relationship = partition.getRelationship(i);

         if(foreignTable.equals(relationship.getDependentTable()) ||
            foreignTable.equals(relationship.getIndependentTable()))
         {
            partition.removeRelationship(i);
         }
      }
   }

   public void deleteJoin(XPartition partition, XRelationship deleteJoin) {
      if(partition.removeRelationship(deleteJoin)) {
         // remove auto alias settings
         String sourceTable = deleteJoin.getDependentTable();
         String targetTable = deleteJoin.getIndependentTable();

         if(partition.findRelationship(sourceTable, targetTable) == null) {
            removeAutoAlias(partition, sourceTable, targetTable);
            removeAutoAlias(partition, targetTable, sourceTable);
         }
      }
   }

   private void removeAutoAlias(XPartition partition, String sourceTable, String targetTable) {
      AutoAlias sourceAutoAlias = partition.getAutoAlias(sourceTable);

      if(sourceAutoAlias == null) {
         return;
      }

      sourceAutoAlias.removeIncomingJoin(targetTable);

      if (sourceAutoAlias.getIncomingJoinCount() < 1) {
         partition.setAutoAlias(sourceTable, null);
      }
   }

   /**
    * Converts a join definition to a relationship model.
    *
    * @param join      the join definition.
    * @param tableName the name of the independent table.
    *
    * @return a new relationship model.
    */
   private XRelationship convertJoin(JoinModel join, String tableName) {
      XRelationship relationship = new XRelationship();
      relationship.setDependent(tableName, join.getColumn());
      relationship.setIndependent(
         join.getForeignTable(), join.getForeignColumn());
      relationship.setJoinType(join.getType().getType());
      relationship.setMerging(join.getMergingRule().getType());
      relationship.setOrder(join.getOrderPriority());
      relationship.setWeakJoin(join.isWeak());

      if(join.getCardinality() == JoinCardinality.ONE_TO_ONE) {
         relationship.setDependentCardinality(XRelationship.ONE);
         relationship.setIndependentCardinality(XRelationship.ONE);
      }
      else if(join.getCardinality() == JoinCardinality.ONE_TO_MANY) {
         relationship.setDependentCardinality(XRelationship.ONE);
         relationship.setIndependentCardinality(XRelationship.MANY);
      }
      else {
         relationship.setDependentCardinality(XRelationship.MANY);

         if(join.getCardinality() == JoinCardinality.MANY_TO_ONE) {
            relationship.setIndependentCardinality(XRelationship.ONE);
         }
         else {
            relationship.setIndependentCardinality(XRelationship.MANY);
         }
      }

      return relationship;
   }

   private XDataModel getDataModel(String database) throws Exception {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      return dataModel;
   }



   private XPartition.PartitionTable getPartitionTable(XPartition partition, String name) {
      Enumeration<XPartition.PartitionTable> tables = partition.getTables();

      while(tables.hasMoreElements()) {
         XPartition.PartitionTable t = tables.nextElement();

         if(Tool.equals(t.getName(), name)) {
            return t;
         }
      }

      return null;
   }

   /**
    * Load runtime columns for target partition table.
    * @param database         the database name.
    * @param partitionId      the partition runtime id in portal data model.
    * @param tname            the target table name.
    * @return
    * @throws Exception
    */
   public List<String> loadTableColumns(String database, String partitionId, String tname)
      throws Exception
   {
      return loadTableColumns(database, partitionId, tname, true);
   }

   /**
    * Load runtime columns for target partition table.
    * @param database         the database name.
    * @param partitionId      the partition runtime id in portal data model.
    * @param tname            the target table name.
    * @param preview          whether get table form preview mode.
    * @return
    * @throws Exception
    */
   public List<String> loadTableColumns(String database, String partitionId, String tname,
                                        boolean preview)
      throws Exception
   {
      XPartition partition = this.runtimePartitionService.getPartition(partitionId);

      if(preview) {
         partition = partition.applyAutoAliases();
      }

      String additional = partition != null ? partition.getConnection() : null;

      if(StringUtils.isEmpty(additional)) {
         additional = XUtil.OUTER_MOSE_LAYER_DATABASE;
      }

      XDataModel dataModel = getDataModel(database);
      DefaultMetaDataProvider metaData = physicalModelService.getMetaDataProvider0(
         database, dataModel, additional);
      XNode tableNode = PhysicalModelService.getTableXNode(partition, tname, metaData);

      if(tableNode != null) {
         tableNode.setAttribute(XUtil.DATASOURCE_ADDITIONAL, partition.getConnection() == null ?
            XUtil.OUTER_MOSE_LAYER_DATABASE : partition.getConnection());
      }

      XPartition.PartitionTable ptable = null;

      for(Enumeration<XPartition.PartitionTable> e = partition.getTables();
          e.hasMoreElements();)
      {
         XPartition.PartitionTable table = e.nextElement();

         if(table != null && Tool.equals(tname, table.getName())) {
            ptable = table;
            break;
         }
      }

      List<GraphColumnInfo> cols =
         physicalModelService.getTableColumns(tableNode, metaData, ptable);
      return cols.stream().map(info -> info.getName()).collect(Collectors.toList());
   }

   private void fixTableBounds(String runtimeId, XPartition partition, String tableName) {
      try {
         physicalModelService.fixTableBounds(runtimeId, partition, tableName);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to fix table bounds", e);
      }
   }

   private Optional<String> optionalString(String runtimeId) {
      return StringUtils.isEmpty(runtimeId) ? Optional.empty() : Optional.of(runtimeId);
   }

   private Optional<RuntimePartitionService.RuntimeXPartition> getRuntimePartition(String runtimeId)
   {
      return optionalString(runtimeId).map(runtimePartitionService::getRuntimePartition);
   }

   private Optional<XPartition> getPartition(String runtimeId) {
      return optionalString(runtimeId).map(runtimePartitionService::getPartition);
   }

   public String checkAliasValid(String runtimeId, String alias, String oldAlias) {
      Catalog catalog = Catalog.getCatalog();

      if(StringUtils.isEmpty(alias)) {
         return catalog.getString("designer.qb.emptyAlias");
      }

      if(alias.contains(".")) {
         return catalog.getString("designer.qb.aliasWithDot");
      }

      if(StringUtils.hasText(oldAlias) && alias.equals(oldAlias)) {
         return null;
      }

      String msg = Tool.checkValidIdentifier(alias, null, false, false, null);

      if(msg != null) {
         return msg;
      }

      if(hasDuplicateCheck(new CheckTableAliasEvent(runtimeId, alias))) {
         return catalog.getString("designer.qb.aliasExists");
      }

      return null;
   }

   public boolean hasDuplicateCheck(CheckTableAliasEvent event) {
      XPartition partition = this.runtimePartitionService.getPartition(event.getRuntimeId());
      return partition.containsTable(event.getAlias());
   }

   private final DataSourceService dataSourceService;
   private final PhysicalModelService physicalModelService;
   private final RuntimePartitionService runtimePartitionService;
   private final PhysicalGraphService graphService;
   private final XRepository repository;
}

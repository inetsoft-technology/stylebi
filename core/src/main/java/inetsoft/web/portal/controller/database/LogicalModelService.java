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
package inetsoft.web.portal.controller.database;

import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.ExpressionRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.viewsheet.*;
import org.apache.commons.io.FileExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;

/**
 * {@code LogicalModelService} contains methods to manage logical models.
 */
@Component
public class LogicalModelService {
   @Autowired
   public LogicalModelService(SecurityEngine securityEngine, XRepository repository,
                              DataSourceService dataSourceService,
                              DataRefModelFactoryService dataRefService,
                              LogicalModelTreeService treeService,
                              AssetRepository assetRepository)
   {
      this.securityEngine = securityEngine;
      this.repository = repository;
      this.dataSourceService = dataSourceService;
      this.dataRefService = dataRefService;
      this.treeService = treeService;
      this.assetRepository = assetRepository;
   }

   /**
    * Checks if a user has the specified permission on a logical model.
    *
    * @param database      the name of the parent data source.
    * @param modelFolder   the data model folder.
    * @param name          the name of the logical model.
    * @param additional    the additional database
    * @param action        the action to check the permission of.
    * @param principal     the principal that identifies the remote user.
    *
    * @return {@code true} if the user has permission or {@code false} if they do not.
    */
   public boolean checkPermission(String database, String modelFolder, String name,
                                  String additional, ResourceAction action, Principal principal)
   {
      IdentityID userId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      String path = database + "/" + (modelFolder == null ? "" : modelFolder + "/") + name;
      AssetEntry.Type type = AssetEntry.Type.LOGIC_MODEL;

      if(!StringUtils.isEmpty(additional)) {
         path += "/" + additional;
         type =  AssetEntry.Type.EXTENDED_LOGIC_MODEL;
      }

      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, userId);
      entry.setProperty("prefix", database);
      entry.setProperty(XUtil.DATASOURCE_ADDITIONAL, additional);
      entry.setProperty("folder", modelFolder);

      try {
         assetRepository.checkAssetPermission(principal, entry, action);
         return true;
      }
      catch(Exception e) {
         LOG.debug("Failed to check permission", e);
         return false;
      }
   }

   /**
    * Checks if a user has the specified permission on a logical model.
    *
    * @param database      the name of the parent data source.
    * @param model         the target logical model.
    * @param action        the action to check the permission of.
    * @param principal     the principal that identifies the remote user.
    *
    * @throws SecurityException if the user does not have the required permission.
    */
   private void validatePermission(String database, XLogicalModel model,
                                   ResourceAction action, Principal principal)
      throws SecurityException
   {
      if(model != null) {
         XLogicalModel base = model.getBaseModel();
         String baseName = base == null ? null : base.getName();
         String folder = base == null ? model.getFolder() : base.getFolder();
         validatePermission(database, folder, baseName, model.getName(),
            model.getConnection(), action, principal);
      }
   }

   /**
    * Checks if a user has the specified permission on a logical model.
    *
    * @param database      the name of the parent data source.
    * @param model         the target logical model.
    * @param action        the action to check the permission of.
    * @param principal     the principal that identifies the remote user.
    *
    * @throws SecurityException if the user does not have the required permission.
    */
   private void validatePermission(String database, LogicalModelDefinition model,
                                   ResourceAction action, Principal principal)
      throws SecurityException
   {
      if(model != null) {
         validatePermission(database, model.getFolder(), model.getParent(), model.getName(),
            model.getConnection(), action, principal);
      }
   }

   /**
    * Checks if a user has the specified permission on a logical model.
    *
    * @param dataSource      the name of the parent data source.
    * @param modelFolder   the data model folder.
    * @param baseModel     the name of the base logical model.
    * @param name          the name of the logical model.
    * @param additional    the additional database
    * @param action        the action to check the permission of.
    * @param principal     the principal that identifies the remote user.
    *
    * @throws SecurityException if the user does not have the required permission.
    */
   private void validatePermission(String dataSource, String modelFolder, String baseModel,
                                   String name, String additional,
                                   ResourceAction action, Principal principal)
      throws SecurityException
   {
      if(!checkPermission(dataSource, modelFolder, name, additional, action, principal)) {
         String path = dataSource + "/" + (modelFolder == null ? "" : modelFolder + "/") +
            (baseModel == null ? "" : baseModel + "/") + name;
         throw new SecurityException(
            String.format("Unauthorized access to resource \"%s\" by %s", path, principal));
      }
   }

   /**
    * Gets a logical model.
    *
    * @param dataSource    the name of the parent data source.
    * @param physicalModel the name of the physical model.
    * @param name          the name of the logical model.
    * @param parent        the name of the parent model or {@code null} if none.
    * @param principal     a principal that identifies the remote user.
    *
    * @return the logical model definition.
    */
   public LogicalModelDefinition getModel(String dataSource, String physicalModel, String name,
                                          String parent, Principal principal) throws Exception
   {
      XLogicalModel logicalModel = getLogicalModel(dataSource, physicalModel,
         parent, name, principal, ResourceAction.READ);
      treeService.refreshPartitionMetaData(dataSource, physicalModel, name,
         parent, logicalModel.getConnection());
      return convertModel(logicalModel, principal);
   }

   /**
    * Creates a new logical model.
    *
    * @param dataSource    the name of the parent data source.
    * @param physicalModel the name of the physical model.
    * @param model         the logical model definition.
    * @param parent        the name of the parent model or {@code null} if none.
    * @param principal     a principal that identifies the remote user.
    *
    * @return the new logical model definition.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_CREATE,
      objectType = ActionRecord.OBJECT_TYPE_LOGICAL_MODEL
   )
   public LogicalModelDefinition createModel(@AuditObjectName(order = 0) String dataSource,
                                             @AuditObjectName(order = 0) String folder,
                                             String physicalModel,
                                             @AuditObjectName(value = "getName()", order = 2)
                                             LogicalModelDefinition model,
                                             @AuditObjectName(order = 1) String parent,
                                             Principal principal)
      throws Exception
   {
      XDataModel dataModel = getDataModel(dataSource);
      boolean isExtended = !StringUtils.isEmpty(parent);
      String path = isExtended ? dataSource + "/" + parent + "/" + model.getName() :
         dataSource + "/" + model.getName();

      validateModelParameters(dataSource, physicalModel, model, principal, dataModel, path);

      if(isExtended) {
         XLogicalModel pLogicalModel = dataModel.getLogicalModel(parent);

         if(pLogicalModel == null) {
            throw new FileNotFoundException(dataSource + "/" + parent);
         }

         XLogicalModel newModel = createModel(model, dataModel, pLogicalModel);
         pLogicalModel.addLogicalModel(newModel, false);
      }
      else {
         XLogicalModel nmodel = createModel(model, dataModel);
         dataModel.addLogicalModel(nmodel);
         DependencyHandler.getInstance().updateModelDependencies(nmodel, true);
      }

      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      repository.updateDataModel(dataModel);
      AssetEntry entry = getModelEntry(path, isExtended);
      entry = dataSourceService.getModelAssetEntry(entry);
      entry.setCreatedUsername(pId.name);
      dataSourceService.updateDataSourceAssetEntry(entry);

      return model;
   }

   /**
    * Creates a new extended logical model.
    *
    * @param dataSource    the name of the parent data source.
    * @param physicalModel the name of the physical model.
    * @param model         the logical model definition.
    * @param parent        the name of the parent model or {@code null} if none.
    * @param principal     a principal that identifies the remote user.
    *
    * @return the new logical model definition.
    */
   public LogicalModelDefinition createExtendedModel(String dataSource, String physicalModel,
                                                     LogicalModelDefinition model, String parent,
                                                     Principal principal) throws Exception
   {
      XDataModel dataModel = getDataModel(dataSource);
      boolean isExtended = !StringUtils.isEmpty(parent);
      String path = dataSource + "/" + parent + "/" + model.getName();

      if(!isExtended) {
         throw new FileNotFoundException(dataSource + "/" + parent);
      }

      validateModelParameters(dataSource, physicalModel, model, principal, dataModel, path);

      XLogicalModel pLogicalModel = dataModel.getLogicalModel(parent);

      if(pLogicalModel == null) {
         throw new FileNotFoundException(dataSource + "/" + parent);
      }

      XLogicalModel newModel = createModel(model, dataModel);
      newModel.setBaseModel(pLogicalModel);

      return convertModel(newModel, principal);
   }

   /**
    * Updates a logical model.
    *
    * @param dataSource    the name of the parent data source.
    * @param model         the logical model definition.
    * @param parent        the name of the parent model or {@code null} if none.
    * @param principal     a principal that identifies the remote user.
    *
    * @return the updated logical model definition.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_LOGICAL_MODEL
   )
   public LogicalModelDefinition updateModel(@AuditObjectName(order = 0) String dataSource,
                                             @AuditObjectName(order = 1) String folder,
                                             String name,
                                             @AuditObjectName(value = "getName()", order = 3)
                                             LogicalModelDefinition model,
                                             @AuditObjectName(order = 2) String parent,
                                             Principal principal)
      throws Exception
   {
      getLogicalModel(
         dataSource, model.getPartition(), parent, name, principal, ResourceAction.WRITE);
      XDataModel dataModel = getDataModel(dataSource);
      boolean isExtended = !StringUtils.isEmpty(parent);
      String path = isExtended ? dataSource + "/" + parent + "/" + name :
         dataSource + "/" + name;
      AssetEntry entry = getModelEntry(path, isExtended);
      entry = dataSourceService.getModelAssetEntry(entry);
      String user = entry.getCreatedUsername();
      Date date = entry.getCreatedDate();
      XLogicalModel ologicalModel = null;
      XLogicalModel nlogicalModel = null;

      if(isExtended) {
         XLogicalModel pLogicalModel = dataModel.getLogicalModel(parent);

         if(pLogicalModel == null) {
            throw new FileNotFoundException(dataSource + "/" + parent);
         }

         ologicalModel = pLogicalModel.getLogicalModel(model.getName());
         XLogicalModel newModel = createModel(model, dataModel, pLogicalModel);
         pLogicalModel.addLogicalModel(newModel, false);
         nlogicalModel = pLogicalModel.getLogicalModel(model.getName());
      }
      else {
         ologicalModel = dataModel.getLogicalModel(model.getName());
         dataModel.addLogicalModel(createModel(model, dataModel));
         nlogicalModel = dataModel.getLogicalModel(model.getName());
      }

      repository.updateDataModel(dataModel);
      entry = dataSourceService.getModelAssetEntry(entry);
      entry.setCreatedUsername(user);
      entry.setCreatedDate(date);
      dataSourceService.updateDataSourceAssetEntry(entry);

      if(ologicalModel != null) {
         DependencyHandler.getInstance().updateModelDependencies(ologicalModel, false);
      }

      if(nlogicalModel != null) {
         DependencyHandler.getInstance().updateModelDependencies(nlogicalModel, true);
      }

      renameDependencies(dataSource, name, model);

      return model;
   }

   private void renameDependencies(String dname, String mname, LogicalModelDefinition model) {
      if(!StringUtils.isEmpty(model.getParent())) {
         return;
      }

      List<EntityModel> entities =  model.getEntities();
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      int entityType = RenameInfo.LOGIC_MODEL | RenameInfo.TABLE;
      int attrType = RenameInfo.LOGIC_MODEL | RenameInfo.COLUMN;
      List<AssetObject> list = DependencyTransformer.getModelDependencies(
              DependencyTransformer.getSourceUnique(dname, mname));

      for(EntityModel entity : entities) {
         String oEntityName = entity.getOldName();

         // For entity add new, do not update dependency, for it will not be binding.
         if(oEntityName == null) {
            continue;
         }

         String entityName = entity.getName();
         List<XAttributeModel> attributes = entity.getAttributes();

         for(XAttributeModel attribute : attributes) {
            String oAttrName = attribute.getOldName();

            if(oAttrName == null) {
               continue;
            }

            if(Tool.equals(oAttrName, attribute.getName())) {
               continue;
            }

            attribute.setOldName(attribute.getName());
            String oname = oEntityName + "." + oAttrName;
            String nname = entityName + "." + attribute.getName();
            addDependency(dinfo, list, oname, nname, mname, dname, entityName, oEntityName,
                    attrType);
         }

         if(!Tool.equals(oEntityName, entityName)) {
            entity.setOldName(entityName);
            addDependency(dinfo, list, oEntityName, entityName, mname, dname, entityName,
               oEntityName, entityType);
         }
      }

      if(!dinfo.getDependencyMap().isEmpty()) {
         sortRenameInfos(dinfo);
         RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
      }
   }

   private void renameCubeMember(DataRefModel ref,
                                 String oentity, String oattribute,
                                 String entity, String attribute)
   {
      if(ref instanceof AttributeRefModel) {
         if(Tool.equals(ref.getEntity(), oentity)) {
            ref.setEntity(entity);

            if(Tool.equals(ref.getAttribute(), oattribute)) {
               ref.setAttribute(attribute);
            }
         }
      }
      else if(ref instanceof ExpressionRefModel) {
         StringBuilder expr = new StringBuilder().append(
            ((ExpressionRefModel) ref).getExp());
         String find = "field['" + oentity + ".";
         String repl = "field['" + entity + ".";
         int idx = -1;

         if(oattribute != null && attribute != null) {
            find = find + oattribute + "']";
            repl = repl + attribute + "']";
         }

         while((idx = expr.toString().indexOf(find)) >= 0) {
            expr.delete(idx, idx + find.length());
            expr.insert(idx, repl);
         }

         ((ExpressionRefModel) ref).setExp(expr.toString());
      }
   }

    // logic to fix logic model entity and attribute.
   private void addDependency(RenameDependencyInfo dinfo, List<AssetObject> list,
                              String oname, String nname, String source, String prefix,
                              String entity, String oentity, int type)
   {
      list.stream().forEach(obj -> {
         if(!(obj instanceof AssetObject)) {
            return;
         }

         AssetObject entry = obj;
         RenameInfo rinfo = new RenameInfo(oname, nname, type, source, entity);
         rinfo.setPrefix(prefix);
         rinfo.setOldEntity(oentity);
         dinfo.addRenameInfo(entry, rinfo);
      });
   }

   private void sortRenameInfos(RenameDependencyInfo dinfo) {
      Map<AssetObject, List<RenameInfo>> map = dinfo.getDependencyMap();
      Iterator it = map.keySet().iterator();

      // do sort, attribute should be update before entity.
      while(it.hasNext()) {
         AssetObject entry = (AssetObject) it.next();
         List<RenameInfo> list = map.get(entry);

         list.sort((RenameInfo o1, RenameInfo o2) -> o1.isEntity() ? 1 : -1);
      }
   }

   /**
    * Renames a logical model.
    *
    * @param dataSource  the name of the parent data source.
    * @param newName     the new name of the model.
    * @param oldName     the old name of the model.
    * @param description a description of the model.
    * @param principal   a principal that identifies the remote user.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_RENAME,
      objectType = ActionRecord.OBJECT_TYPE_LOGICAL_MODEL
   )
   public void renameModel(@AuditActionError(value = "'Target Entry: ' + #this", order = 1) @AuditObjectName(order = 1) String dataSource,
                           @AuditActionError(order = 2) @AuditObjectName(order = 2) String folder,
                           @AuditActionError(order = 3) String newName,
                           @AuditObjectName(order = 3) String oldName,
                           String description, Principal principal)
      throws Exception
   {
      XDataModel dataModel = getDataModel(dataSource);
      XLogicalModel logicalModel = dataModel.getLogicalModel(oldName);
      validatePermission(dataSource, logicalModel, ResourceAction.DELETE, principal);

      String path = dataSource + "/" + oldName;
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.LOGIC_MODEL, path, null);
      entry = dataSourceService.getModelAssetEntry(entry);

      if(entry == null) {
         throw new MissingResourceException(dataSource + "/" + oldName);
      }

      String user = entry.getCreatedUsername();
      Date date = entry.getCreatedDate();
      logicalModel.setLastModified(System.currentTimeMillis());
      dataModel.renameLogicalModel(oldName, newName, description);
      repository.updateDataModel(dataModel);

      path = dataSource + "/" + newName;
      entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                             AssetEntry.Type.LOGIC_MODEL, path, null);
      entry = dataSourceService.getModelAssetEntry(entry);
      entry.setCreatedUsername(user);
      entry.setCreatedDate(date);
      dataSourceService.updateDataSourceAssetEntry(entry);
   }

   /**
    * Removes a logical model.
    *
    * @param dataSource the name of the parent data source.
    * @param name       the name of the logical model.
    * @param principal  a principal that identifies the remote user.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_DELETE,
      objectType = ActionRecord.OBJECT_TYPE_LOGICAL_MODEL
   )
   public void removeModel(@AuditObjectName(order = 0) String dataSource,
                           @AuditObjectName(order = 1) String folder,
                           @AuditObjectName(order = 3) String name,
                           @AuditObjectName(order = 2) String parent,
                           Principal principal)
      throws Exception
   {
      XDataModel dataModel = getDataModel(dataSource);
      boolean isExtended = !StringUtils.isEmpty(parent);
      XLogicalModel baseModel = null;
      XLogicalModel model = null;

      if(isExtended) {
         baseModel = dataModel.getLogicalModel(parent);

         if(baseModel == null) {
            throw new FileNotFoundException(dataSource + "/" + parent);
         }

         model = baseModel.getLogicalModel(name);
      }
      else {
         model = dataModel.getLogicalModel(name);
      }

      if(model == null) {
         throw new FileNotFoundException(dataSource + "/"
            + (parent == null ? "" : parent + "/") + name);
      }

      validatePermission(dataSource, model, ResourceAction.DELETE, principal);

      if(isExtended) {
         baseModel.removeLogicalModel(name);
      }
      else {
         dataModel.removeLogicalModel(name);
      }

      String path = dataSource + "/" + name;
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
         path, null);
      DependencyHandler.getInstance().deleteDependencies(entry);
      DependencyHandler.getInstance().deleteDependenciesKey(entry);

      repository.updateDataModel(dataModel);

      if(!isExtended) {
         String resource = name + "::" + dataSource + (folder == null ? "" : "/" + folder);
         securityEngine.removePermission(ResourceType.QUERY, resource);
      }
   }

   private LogicalModelDefinition convertModel(XLogicalModel logicalModel, Principal principal)
      throws Exception
   {
      LogicalModelDefinition result = new LogicalModelDefinition();
      result.setName(logicalModel.getName());
      result.setPartition(logicalModel.getPartition());
      result.setDescription(logicalModel.getDescription());
      result.setConnection(logicalModel.getConnection());
      String folder = logicalModel.getFolder();

      if(logicalModel.getBaseModel() != null) {
         folder = logicalModel.getBaseModel().getFolder();
      }

      result.setFolder(folder);
      Enumeration<XEntity> xEntities = logicalModel.getEntities();
      List<EntityModel> entityList = new ArrayList<>();
      String additional = logicalModel.getConnection() == null ? XUtil.OUTER_MOSE_LAYER_DATABASE :
         logicalModel.getConnection();

      while(xEntities.hasMoreElements()) {
         EntityModel entity = new EntityModel();
         XEntity xEntity = xEntities.nextElement();
         entity.setName(xEntity.getName());
         entity.setOldName(xEntity.getName());
         entity.setDescription(xEntity.getDescription());
         Enumeration<XAttribute> xAttributes = xEntity.getAttributes();
         List<XAttributeModel> attributeList = new ArrayList<>();

         while(xAttributes.hasMoreElements()) {
            XAttribute xAttribute = xAttributes.nextElement();
            XAttributeModel attribute = new XAttributeModel();
            attribute.setParentEntity(xEntity.getName());
            attribute.setName(xAttribute.getName());
            attribute.setOldName(xAttribute.getName());
            attribute.setDescription(xAttribute.getDescription());
            attribute.setTable(xAttribute.getTable());
            attribute.setColumn(xAttribute.getColumn());
            attribute.setBaseElement(xEntity.isBaseAttribute(xAttribute.getName()));
            attribute.setDataType(xAttribute.getDataType());
            attribute.setBrowseData(xAttribute.isBrowseable());
            attribute.setBrowseQuery(xAttribute.getBrowseDataQuery());
            attribute.setVisible(xEntity.isAttributeVisible(xAttribute.getName()));
            RefTypeModel refType = new RefTypeModel();
            refType.setType(xAttribute.getRefType());
            refType.setFormula(xAttribute.getDefaultFormula());
            attribute.setRefType(refType);
            XFormatInfo xFormatInfo = xAttribute.getXMetaInfo().getXFormatInfo();
            XDrillInfo xDrillInfo = xAttribute.getXMetaInfo().getXDrillInfo();
            attribute.setErrorMessage(validateAttribute(xAttribute, logicalModel.getDataModel(),
               logicalModel.getPartition(), logicalModel, additional, principal));

            if(xFormatInfo != null) {
               attribute.setFormat(DatabaseModelUtil.createXFormatInfoModel(xFormatInfo));
            }

            if(xDrillInfo != null) {
               attribute.setDrillInfo(
                  DatabaseModelUtil.createAutoDrillInfoModel(xDrillInfo, repository, principal));
            }

            if(xAttribute instanceof ExpressionAttribute) {
               attribute.setType("expression");
               attribute.setExpression(
                  ((ExpressionAttribute) xAttribute).getExpression());
               attribute.setAggregate(
                  ((ExpressionAttribute) xAttribute).isAggregateExpression());
               attribute.setParseable(
                  ((ExpressionAttribute) xAttribute).isParseable());
            }
            else {
               attribute.setType("column");
               attribute.setQualifiedName(attribute.getTable() + "." + attribute.getColumn());
            }

            if(attribute.getErrorMessage() != null) {
               entity.setErrorMessage(Catalog.getCatalog().getString(
                  "designer.qb.dataModel.invalidEntity"));
            }

            attributeList.add(attribute);
         }

         entity.setAttributes(attributeList);
         entity.setBaseElement(logicalModel.isBaseEntity(xEntity.getName()));
         entity.setVisible(logicalModel.isEntityVisible(xEntity.getName()));
         entityList.add(entity);
      }

      result.setEntities(entityList);
      XLogicalModel baseModel = logicalModel.getBaseModel();

      if(baseModel != null) {
         result.setParent(baseModel.getName());
      }

      return result;
   }

   private String validateAttribute(XAttribute attr, XDataModel dataModel, String partitionName,
                                    XLogicalModel logicalModel, String additional,
                                    Principal principal)
      throws Exception
   {
      if(attr.isExpression() || attr.getTable() == null) {
         return null;
      }

      JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.getDataSource(
         dataModel.getDataSource(), additional);
      DefaultMetaDataProvider metaData =
         dataSourceService.getDefaultMetaDataProvider(dataSource, dataModel);
      XPartition partition = dataModel.getPartition(partitionName);
      Catalog catalog = Catalog.getCatalog(principal);

      if(logicalModel.getBaseModel() != null) {
         String conn = logicalModel.getConnection();

         if(conn != null && partition.containPartition(conn)) {
            partition = partition.getPartition(conn);
         }
         else {
            String[] pnames = partition.getPartitionNames();

            for(String sname : pnames) {
               if(partition.getPartition(sname).getConnection() == null) {
                  partition = partition.getPartition(sname);
                  break;
               }
            }
         }
      }

      if(partition == null) {
         return Catalog.getCatalog().getString("designer.qb.dataModel.missingPartition",
            logicalModel.getPartition());
      }

      XPartition aliasedPartition = partition.applyAutoAliases();

      if(!aliasedPartition.containsTable(attr.getTable())) {
         return catalog.getString(
            "data.logicalmodel.missingPMTable", attr.getTable(), partitionName);
      }

      String tableName = attr.getTable();

      if(aliasedPartition.isAlias(tableName)) {
         tableName = aliasedPartition.getAliasTable(tableName);
      }

      try {
         XNode tableNode = new XNode();
         XPartition.PartitionTable xTable =
            aliasedPartition.getPartitionTable(attr.getTable());

         if(xTable == null) {
            xTable = aliasedPartition.getPartitionTable(
               aliasedPartition.getAutoAliasTable(attr.getTable()));
         }

         if(xTable != null && xTable.getType() == 1 && xTable.getSql() != null)
         {
            for(XTypeNode xTypeNode : xTable.getColumns()) {
               tableNode.addChild(xTypeNode);
            }
         }
         else {
            XNode partitionNode = metaData.getMetaData(partition, true, additional);
            tableNode = partitionNode.getChild(attr.getTable());
         }

         if(tableNode == null) {
            return catalog.getString(
               "designer.qb.dataModel.missingDSTable",
               tableName, dataSource.getName());
         }

         if(tableNode.getChild(attr.getColumn()) == null) {
            return catalog.getString(
               "designer.qb.dataModel.missingColumn",
               attr.getColumn(), tableName);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to validate Attribute [" + attr.getName() + "]", ex);
      }

      return null;
   }

   private XLogicalModel createModel(LogicalModelDefinition model, XDataModel dataModel)
      throws RemoteException
   {
      return createModel(model, dataModel, null);
   }

   /**
    * Creates a XLogicalModel from a DTO structure model
    * @param model the DTO structure logical model
    * @param dataModel
    * @return the XLogicalModel
    */
   private XLogicalModel createModel(LogicalModelDefinition model, XDataModel dataModel,
                                     XLogicalModel parentModel)
      throws RemoteException
   {
      XLogicalModel logicalModel = new XLogicalModel(model.getName());
      logicalModel.setPartition(model.getPartition());
      logicalModel.setEntityOrder(false);
      logicalModel.setDescription(model.getDescription());
      logicalModel.setConnection(model.getConnection());
      logicalModel.setDataModel(dataModel);
      logicalModel.setFolder(model.getFolder());

      if(parentModel != null) {
         logicalModel.setBaseModel(parentModel, false);
      }

      for(EntityModel entity : model.getEntities()) {
         XEntity xEntity = new XEntity(entity.getName());
         xEntity.setVisible(entity.isVisible());
         xEntity.setDescription(entity.getDescription());

         if(parentModel != null && logicalModel.isBaseEntity(entity.getName())) {
            xEntity.setBaseEntity(logicalModel.getBaseEntity(entity.getName()));
         }

         for(int i = 0; i < entity.getAttributes().size(); i++) {
            XAttributeModel attrModel = entity.getAttributes().get(i);

            if("column".equals(attrModel.getType())) {
               XAttribute attr = new XAttribute(attrModel.getName(), attrModel.getTable(),
                  attrModel.getColumn(), attrModel.getDataType());
               copyProperties(xEntity, attr, attrModel);
               attr.setXMetaInfo(createXMetaInfo(attrModel));
               xEntity.addAttribute(i, attr);
            }
            else {
               ExpressionAttribute exp =
                  new ExpressionAttribute(attrModel.getName(), attrModel.getExpression());
               exp.setDataType(attrModel.getDataType());
               exp.setAggregateExpression(attrModel.isAggregate());
               exp.setParseable(attrModel.isParseable());
               copyProperties(xEntity, exp, attrModel);
               exp.setXMetaInfo(createXMetaInfo(attrModel));
               xEntity.addAttribute(i, exp);
            }
         }

         logicalModel.addEntity(xEntity);
      }

      return logicalModel;
   }

   private void copyProperties(XEntity entity, XAttribute attribute, XAttributeModel model) {
      entity.setAttributeVisible(attribute.getName(), model.isVisible());
      attribute.setDescription(model.getDescription());
      attribute.setRefType(model.getRefType().getType());
      attribute.setDefaultFormula(model.getRefType().getFormula());
      attribute.setBrowseable(model.isBrowseData());
      attribute.setBrowseDataQuery(model.getBrowseQuery());
      attribute.setEntity(model.getParentEntity());
   }

   private XMetaInfo createXMetaInfo(XAttributeModel model) {
      return DatabaseModelUtil.createXMetaInfo(model.getFormat(), model.getDrillInfo());
   }

   public XDataModel getDataModel(String dataSource) throws Exception {
      XDataModel dataModel = repository.getDataModel(dataSource);

      if(dataModel == null) {
         throw new FileNotFoundException(dataSource);
      }

      return dataModel;
   }

   private XLogicalModel getLogicalModel(String dataSource, String physicalModel, String parent,
                                         String name, Principal principal, ResourceAction action)
      throws Exception
   {
      XDataModel dataModel = getDataModel(dataSource);

      if(dataModel.getPartition(physicalModel) == null) {
         throw new FileNotFoundException(dataSource + "/" + physicalModel);
      }

      XLogicalModel logicalModel;
      boolean isExtended = !StringUtils.isEmpty(parent);

      if(!isExtended) {
         logicalModel = dataModel.getLogicalModel(name);
      }
      else {
         XLogicalModel pModel = dataModel.getLogicalModel(parent);

         if(pModel == null) {
            throw new FileNotFoundException(dataSource + "/" + parent);
         }

         logicalModel = pModel.getLogicalModel(name);
      }

      if(logicalModel == null) {
         throw new FileNotFoundException(dataSource + "/" + name);
      }

      validatePermission(dataSource, logicalModel, action, principal);
      return logicalModel;
   }

   private void validateModelParameters(String dataSource, String physicalModel,
                                        LogicalModelDefinition model, Principal principal,
                                        XDataModel dataModel, String path) throws Exception
   {
      validatePermission(dataSource, model, ResourceAction.WRITE, principal);

      if(dataModel.getPartition(physicalModel) == null) {
         throw new FileNotFoundException(dataSource + "/" + physicalModel);
      }

      if(StringUtils.isEmpty(model.getParent())) {
         if(dataModel.getLogicalModel(model.getName()) != null) {
            throw new FileExistsException(path);
         }
      }
      else {
         XLogicalModel pLogicalModel = dataModel.getLogicalModel(model.getParent());

         if(pLogicalModel == null || pLogicalModel.getPartition() != null &&
            !pLogicalModel.getPartition().equals(physicalModel))
         {
            throw new FileNotFoundException(dataSource + "/" + physicalModel + "/" +
               model.getParent());
         }

         if(pLogicalModel.getLogicalModel(model.getName()) != null) {
            throw new FileExistsException(dataSource + "/" + physicalModel + "/" +
               model.getParent() + "/" + model.getName());
         }
      }
   }

   private AssetEntry getModelEntry(String path, boolean isExtended) {
      AssetEntry entry;

      if(isExtended) {
         entry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.EXTENDED_LOGIC_MODEL, path, null);
      }
      else {
         entry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL, path, null);
      }

      return entry;
   }

   private final SecurityEngine securityEngine;
   private final XRepository repository;
   private final DataSourceService dataSourceService;
   private final DataRefModelFactoryService dataRefService;
   private final LogicalModelTreeService treeService;
   private final AssetRepository assetRepository;
   private static final Logger LOG = LoggerFactory.getLogger(LogicalModelService.class.getName());
}

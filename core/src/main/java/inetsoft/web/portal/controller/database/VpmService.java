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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.*;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.portal.model.database.Condition;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.viewsheet.*;
import org.apache.commons.io.FileExistsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;

/**
 * The virtual private model operations behind both the Portal {@link VPMController} and the
 * data model bridge ({@code WizVpmController}). The bodies are the ones that used to live in the
 * controller, moved unchanged so the Portal keeps its exact behavior, together with the two
 * conversions between the stored model and the Portal DTO ({@link #toModel} and
 * {@link #fromModel}). Audit annotations moved here with them: every create, update and rename
 * writes one audit record, whichever caller reached the service.
 *
 * <p>Every method needs WRITE on the data source (the Data tab lists and edits VPMs only with
 * it); the denial is the {@code SecurityException} the controller always threw.</p>
 */
@Component
public class VpmService {
   @Autowired
   public VpmService(DataRefModelFactoryService dataRefModelFactoryService,
                     DataSourceService dataSourceService, XRepository repository,
                     SecurityEngine securityEngine, DependencyHandler dependencyHandler,
                     RenameTransformHandler renameTransformHandler)
   {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.dataSourceService = dataSourceService;
      this.repository = repository;
      this.securityEngine = securityEngine;
      this.dependencyHandler = dependencyHandler;
      this.renameTransformHandler = renameTransformHandler;
   }

   /**
    * VPM reads need the permission the Data tab requires to list and edit VPMs: WRITE on the
    * data source.
    */
   public void checkVpmPermission(String database, Principal principal) throws Exception {
      if(!dataSourceService.checkPermission(database, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " + principal);
      }
   }

   /**
    * Gets a virtual private model as the Portal DTO.
    *
    * @throws FileNotFoundException if the database or the model does not exist.
    */
   public VPMDefinition get(String database, String name, Principal principal) throws Exception {
      return toModel(getStored(database, name, principal), database, principal);
   }

   /**
    * Gets the stored virtual private model (same checks as {@link #get}).
    *
    * @throws FileNotFoundException if the database or the model does not exist.
    */
   public VirtualPrivateModel getStored(String database, String name, Principal principal)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      // the Data tab lists and edits VPMs only with WRITE on the data source
      checkVpmPermission(database, principal);
      VirtualPrivateModel vpm = dataModel.getVirtualPrivateModel(name);

      if(vpm == null) {
         throw new FileNotFoundException(database + "/" + name);
      }

      return vpm;
   }

   /**
    * Adds a model to a database.
    *
    * @throws FileExistsException if a VPM of that name exists.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_CREATE,
      objectType = ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL
   )
   public void create(@AuditObjectName(order = 0) String database,
                      @AuditObjectName(value = "getName()", order = 1) VPMDefinition model,
                      Principal principal)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);
      String path = database + "/" + model.getName();

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(!dataSourceService.checkPermission(database, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " +
               principal);
      }

      if(dataModel.getVirtualPrivateModel(model.getName()) != null) {
         throw new FileExistsException(path);
      }

      VirtualPrivateModel vpm = fromModel(model);
      dataModel.addVirtualPrivateModel(vpm, true);
      repository.updateDataModel(dataModel);
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM,
                                        path, null);
      entry = dataSourceService.getModelAssetEntry(entry);
      String createdUsername = principal.getName() != null ?
         IdentityID.getIdentityIDFromKey(principal.getName()).getName() : null;
      entry.setCreatedUsername(createdUsername);
      dataSourceService.updateDataSourceAssetEntry(entry);
      dependencyHandler.updateVPMDependencies(vpm, null, database, entry);
   }

   /**
    * Renames a model. The description is overwritten with the argument, as the Portal does.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_RENAME,
      objectType = ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL
   )
   public void rename(@AuditActionError(value = "'Target Entry: ' + #this", order = 1)
                      @AuditObjectName(order = 1) String database,
                      @AuditActionError(order = 2) @AuditObjectName(order = 2) String oldName,
                      String newName, String description, Principal principal)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(!dataSourceService.checkPermission(database, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " +
               principal);
      }

      VirtualPrivateModel vpm = dataModel.getVirtualPrivateModel(oldName);

      if(vpm == null) {
         throw new FileNotFoundException(oldName);
      }

      String path = database + "/" + vpm.getName();
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM, path, null);

      entry = dataSourceService.getModelAssetEntry(entry);
      String user = entry.getCreatedUsername();
      Date date = entry.getCreatedDate();

      vpm.setName(newName);
      vpm.setDescription(description);
      dataModel.renameVirtualPrivateModel(oldName, vpm);
      repository.updateDataModel(dataModel);
      String dataSource = dataModel.getDataSource();
      String oldPath = dataSource + "/" + oldName;
      String newPath = dataSource + "/" + newName;
      RenameInfo rinfo = new RenameInfo(oldPath, newPath, RenameInfo.VPM);
      path = database + "/" + newName;
      AssetEntry nentry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM, path, null);
      dependencyHandler.updateDependencies(entry, nentry);
      renameTransformHandler.addTransformTask(rinfo);
      nentry = dataSourceService.getModelAssetEntry(nentry);

      nentry.setCreatedUsername(user);
      nentry.setCreatedDate(date);
      dataSourceService.updateDataSourceAssetEntry(nentry);
   }

   /**
    * Replaces the virtual private model {@code name} with the given definition.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL
   )
   public void update(@AuditObjectName(order = 0) String database,
                      @AuditObjectName(order = 1) String name, VPMDefinition vpm,
                      Principal principal)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(!dataSourceService.checkPermission(database, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " +
               principal);
      }

      if(dataModel.getVirtualPrivateModel(name) == null) {
         throw new FileNotFoundException(database + "/" + name);
      }

      // the write below removes the model named by the caller and adds the one named in the
      // definition, silently overwriting a different existing model of that name, so the
      // definition must be the model the WRITE check was made on (renaming has its own method)
      if(!Tool.equals(name, vpm.getName())) {
         throw new SecurityException(String.format(
            "Virtual private model \"%s\" cannot be saved as \"%s\" by %s",
            database + "/" + name, vpm.getName(), principal));
      }

      String path = database + "/" + vpm.getName();
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM, path, null);

      entry = dataSourceService.getModelAssetEntry(entry);
      String user = entry.getCreatedUsername();
      Date date = entry.getCreatedDate();
      VirtualPrivateModel ovpm = dataModel.getVirtualPrivateModel(name);
      dataModel.removeVirtualPrivateModel(name);
      VirtualPrivateModel nvpm = fromModel(vpm);
      dataModel.addVirtualPrivateModel(nvpm, true);
      repository.updateDataModel(dataModel);
      dependencyHandler.updateVPMDependencies(nvpm, ovpm, database, entry);

      entry = dataSourceService.getModelAssetEntry(entry);

      entry.setCreatedUsername(user);
      entry.setCreatedDate(date);
      dataSourceService.updateDataSourceAssetEntry(entry);
   }

   /**
    * Deletes virtual private models, one audit record each, and saves the data model once. A
    * null list deletes nothing.
    */
   public void delete(String database, List<String> names, Principal principal)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(!dataSourceService.checkPermission(database, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " +
               principal);
      }

      if(names == null) {
         return;
      }

      for(String name : names) {
         removeVPM(dataModel, database, name, principal);
         String path = database + "/" + name;
         AssetEntry entry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM, path, null);
         dependencyHandler.deleteDependencies(entry);
      }

      repository.updateDataModel(dataModel);
   }

   private void removeVPM(XDataModel dataModel, String database, String name, Principal principal)
      throws Exception
   {
      if(dataModel.getVirtualPrivateModel(name) == null) {
         throw new FileNotFoundException(database + "/" + name);
      }

      dataModel.removeVirtualPrivateModel(name);
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_DELETE,
         database + "/" + name, ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL);
      Audit.getInstance().auditAction(actionRecord, principal);
   }

   /**
    * Runs the VPM against a user or a role and returns the resulting SQL text (what the Portal
    * shows). A draft definition is tested; nothing is stored.
    *
    * @param type "user" or anything else for a role.
    */
   public String test(String database, String type, String name, VPMDefinition vpModel,
                      Principal principal)
      throws Exception
   {
      checkVpmPermission(database, principal);
      SecurityProvider provider = securityEngine.getSecurityProvider();

      IdentityID user;
      IdentityID[] roles;
      String[] groups;
      String orgID = null;
      String userGroup;
      ArrayList<String> groupList = new ArrayList<>(0);

      if("user".equals(type)) {
         user = new IdentityID(name, OrganizationManager.getInstance().getCurrentOrgID());
         groups = SUtil.getGroups(user);

         if(groups.length != 0) {
            userGroup = groups[0];
            Group currentGroup = provider.getGroup(new IdentityID(userGroup, user.orgID));
            String[] parent = currentGroup.getGroups();
            groupList.add(userGroup);

            while (parent.length != 0) {
               groupList.add(parent[0]);
               currentGroup = provider.getGroup(new IdentityID(parent[0], currentGroup.getOrganizationID()));
               parent = currentGroup.getGroups();
            }

            groups = new String[groupList.size()];
            groups = groupList.toArray(groups);
         }

         roles = securityEngine.getRoles(user);
         User securityUser =  provider.getUser(new IdentityID(name, OrganizationManager.getInstance().getCurrentOrgID()));

         if(securityUser != null) {
            orgID = securityUser.getOrganizationID();
         }
      }
      else {
         user = null;
         groups = new String[0];
         roles = new IdentityID[]{new IdentityID(name, OrganizationManager.getInstance().getCurrentOrgID())};
      }

      user = user == null ? new IdentityID(Identity.UNKNOWN_USER, OrganizationManager.getInstance().getCurrentOrgID()) : user;
      roles = roles == null ? new IdentityID[0] : roles;
      XDataModel dataModel = repository.getDataModel(database);
      Catalog catalog = Catalog.getCatalog();
      VirtualPrivateModel vpm = fromModel(vpModel);
      String result = null;

      try {
         result = VpmProcessor.getInstance()
            .testVPM(user, groups, roles, orgID, dataModel, catalog, vpm);
      }
      catch(Exception ex) {
         if(!(ex instanceof MessageException)) {
            throw new MessageException(ex.getMessage(), ex);
         }
      }

      return result;
   }

   /**
    * Creates a model DTO structure from a virtual private model.
    *
    * @param vpm the virtual private model.
    *
    * @return the model.
    */
   public VPMDefinition toModel(VirtualPrivateModel vpm, String datasource, Principal principal) {
      VPMDefinition result = new VPMDefinition();
      result.setName(vpm.getName());
      result.setDescription(vpm.getDescription());

      List<Condition> conditions = new ArrayList<>();

      for(Enumeration<?> e = vpm.getConditions(); e.hasMoreElements();) {
         VpmCondition condition = (VpmCondition) e.nextElement();
         XFilterNode xfn = condition.getCondition();

         HierarchyList hl = new FilterList();

         for(HierarchyItem item :
            XUtil.constructConditionList(xfn))
         {
            hl.append(item);
         }

         hl.validate(false);

         Condition c = new Condition();
         c.setName(condition.getName());

         List<DataConditionItem> clauses = new ArrayList<>();
         XDataSource xDataSource = dataSourceService.getDataSource(datasource);

         for(int i = 0; i < hl.getSize(); i++) {
            HierarchyItem hi = hl.getItem(i);
            int level = hi.getLevel();

            if(hi instanceof XSetItem) {
               Conjunction con = new Conjunction();

               XSet xset = ((XSetItem) hi).getXSet();

               con.setLevel(level);
               con.setValue(xset.toString());
               con.setIsNot(xset.isIsNot());
               con.setConjunction(xset.getRelation());
               con.setJunc(true);
               clauses.add(con);
            }
            else {
               XFilterNode xfilter = ((XFilterNodeItem) hi).getNode();

               Clause clause = JDBCUtil.createCondition(xfilter, xDataSource);
               clause.setLevel(level);
               clauses.add(clause);
            }
         }

         c.setScript(condition.getScript());
         c.setType(condition.getType());
         c.setTableName(condition.getTable());
         c.setClauses(clauses);

         conditions.add(c);
      }

      result.setConditions(conditions);

      try {
         HiddenColumns hc = vpm.getHiddenColumns();
         HiddenColumnsModel hcModel = new HiddenColumnsModel();
         hcModel.setScript(hc.getScript());
         hcModel.setName(hc.getName());
         Enumeration<?> roles = hc.getRoles();
         List<String> roleModels = new ArrayList<>();

         while(roles.hasMoreElements()) {
            roleModels.add(String.valueOf(roles.nextElement()));
         }

         hcModel.setRoles(roleModels);
         Enumeration<?> hiddenColumns = hc.getHiddenColumns();
         List<DataRefModel> hiddenColumnModels = new ArrayList<>();

         while(hiddenColumns.hasMoreElements()) {
            DataRef hiddenColumn = (DataRef) hiddenColumns.nextElement();
            hiddenColumnModels.add(dataRefModelFactoryService.createDataRefModel(hiddenColumn));
         }

         hcModel.setHiddens(hiddenColumnModels);
         result.setHidden(hcModel);
      }
      catch(Exception ignore) {
         // No script
      }

      String lookupScript = vpm.getScript();

      result.setLookup(lookupScript);

      return result;
   }

   /**
    * Creates a virtual private model from a model DTO structure.
    *
    * @param model the model to convert.
    *
    * @return the vpm object.
    */
   public VirtualPrivateModel fromModel(VPMDefinition model) throws Exception {
      VirtualPrivateModel vpm = new VirtualPrivateModel(model.getName());
      List<Condition> conditions = model.getConditions();
      vpm.setDescription(model.getDescription());

      for(Condition c : conditions) {
         List<DataConditionItem> clauses = c.getClauses();
         HierarchyList hl = new FilterList();
         HierarchyListModel clm = new HierarchyListModel(hl);

         for(DataConditionItem ci : clauses) {
            if(ci instanceof Clause) {
               XFilterNode xfn = JDBCUtil.createXFilterNode((Clause) ci);
               XFilterNodeItem xfni = new XFilterNodeItem(xfn, ci.getLevel());
               clm.append(xfni);
            }
            else if(ci instanceof Conjunction) {
               XSet xs = new XSet(((Conjunction) ci).getConjunction());
               xs.setIsNot(((Conjunction) ci).isIsNot());
               XSetItem xsi = new XSetItem(xs, ci.getLevel());
               clm.append(xsi);
            }
         }

         clm.fixConditions();

         ConditionListHandler handler = new ConditionListHandler();
         XFilterNode header = handler.createXFilterNode(clm.getHierarchyList());

         VpmCondition newCondition = new VpmCondition();
         newCondition.setName(c.getName());
         newCondition.setCondition(header);
         newCondition.setScript(c.getScript());
         newCondition.setType(c.getType());
         newCondition.setTable(c.getTableName());

         vpm.addCondition(newCondition);
      }

      HiddenColumnsModel hcModel = model.getHidden();

      if(hcModel != null) {
         HiddenColumns hc = new HiddenColumns();
         List<DataRefModel> hiddens = hcModel.getHiddens();

         if(hiddens != null && hiddens.size() > 0) {
            hiddens.forEach(hidden -> hc.addHiddenColumn(hidden.createDataRef()));
         }

         List<String> roles = hcModel.getRoles();

         if(roles != null && roles.size() > 0) {
            roles.forEach(hc::addRole);
         }

         hc.setScript(hcModel.getScript());
         vpm.setHiddenColumns(hc);
      }

      vpm.setScript(model.getLookup());

      return vpm;
   }

   private final DataRefModelFactoryService dataRefModelFactoryService;
   private final DataSourceService dataSourceService;
   private final XRepository repository;
   private final SecurityEngine securityEngine;
   private final DependencyHandler dependencyHandler;
   private final RenameTransformHandler renameTransformHandler;
}

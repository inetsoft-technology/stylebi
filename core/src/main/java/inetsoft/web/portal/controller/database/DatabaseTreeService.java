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

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XFactory;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.*;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.model.database.*;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.security.Principal;
import java.util.*;
import java.util.function.Function;

/**
 * DatabaseTreeService is used for get tree nodes for portal physical table tree and vpm table tree.
 *
 * @since 13.3
 */
@Service
public class DatabaseTreeService {
   @Autowired
   public DatabaseTreeService(AssetRepository assetRepository,
                              DataSourceService datasourceService)
   {
      this.assetRepository = assetRepository;
      this.datasourceService = datasourceService;
   }

   public boolean isAliasNode(String nodePath) {
      return nodePath != null && nodePath.indexOf(ALIAS_NODE_NAME) >= 0;
   }

   public List<DatabaseTreeNode> getDatabaseNodes(String parentPath, boolean loadColumns,
                                                  boolean ignoreVpm, Principal principal)
      throws Exception
   {
      return getDatabaseNodes(parentPath, null, null, loadColumns, ignoreVpm, principal);
   }

   public List<DatabaseTreeNode> getDatabaseNodes(String parentPath,
                                                  String additional,
                                                  boolean loadColumns, boolean ignoreVpm,
                                                  Principal principal)
      throws Exception
   {
      return getDatabaseNodes(parentPath, null, additional, loadColumns, ignoreVpm, principal);
   }

   public List<DatabaseTreeNode> getDatabaseNodes(String parentPath,
                                                  String parr,
                                                  String additional,
                                                  boolean loadColumns, boolean ignoreVpm,
                                                  Principal principal)
      throws Exception
   {
      AssetRepository repository = assetRepository;
      List<DatabaseTreeNode> results = new ArrayList<>();

      AssetEntry parentEntry = DatabaseModelUtil.getDatabaseEntry(parentPath, parr,
         assetRepository, additional, principal);
      parentEntry = parentEntry == null ? null : (AssetEntry) parentEntry.clone();
      parentEntry.setProperty("fromPortalDataModel", "true");

      AssetEntry.Selector selector = new AssetEntry.Selector(
         AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL, AssetEntry.Type.FOLDER);

      if(loadColumns) {
         selector.add(AssetEntry.Type.COLUMN);

         if(parentEntry.isPhysicalTable() && ignoreVpm) {
            parentEntry.setProperty("ignoreVpm", "true");
         }
      }

      if(parentEntry != null) {
         if(!parentEntry.isDataSource()) {
            parentEntry.setProperty("sortNameByCase", "true");
         }
         else {
            parentEntry.setProperty("skipSort", "true");
         }

         DatabaseModelUtil.addAdditionalProperties(parentEntry, additional);
      }

      // use this flag to mark portal data model, to make sure portal data's meta data only
      // be shared to portal data, and should not effect other component like composer.
      parentEntry.setProperty(XUtil.PORTAL_DATA, "true");
      AssetEntry[] entries = repository.getEntries(parentEntry, principal, ResourceAction.READ,
         selector);

      for(AssetEntry entry : entries) {
         // see PhysicalModelTreePane.updateComponents
         if(parentEntry.isDataSource() &&
            !ArrayUtils.contains(ACCEPT_DATABASE_TABLE_TABLE, entry.getName()))
         {
            continue;
         }

         if(!entry.isLogicModel() && !entry.isQuery()) {
            DatabaseTreeNode node = new DatabaseTreeNode();
            // see DatabaseModelUtil.getDatabaseEntry().
            node.setPath(parentPath + "/" + entry.getName());
            node.setParr(Objects.toString(parr, parentPath)
               + AssetEntry.PATH_ARRAY_SEPARATOR + entry.getName());
            node.setName(entry.getName());

            if(entry.isPhysicalTable()) {
               node.setType(DatabaseTreeNodeType.TABLE);
               node.setCatalog(entry.getProperty(XSourceInfo.CATALOG));
               node.setSchema(entry.getProperty(XSourceInfo.SCHEMA));
               node.setQualifiedName(entry.getProperty("source"));
               node.setSupportCatalog("true".equals(entry.getProperty("supportCatalog")));
            }
            else if(entry.isFolder()) {
               node.setType(DatabaseTreeNodeType.FOLDER);
               node.setCatalog(entry.getProperty(XSourceInfo.CATALOG));
               node.setSchema(entry.getProperty(XSourceInfo.SCHEMA));
            }
            else if(entry.isColumn()) {
               node.setType(DatabaseTreeNodeType.COLUMN);
               node.setName(entry.getName());
               node.setAttribute(entry.getProperty("attribute"));
               node.setEntity(entry.getProperty("source"));
               node.setQualifiedName(entry.getProperty("source"));
            }

            results.add(node);
         }
      }

      return results;
   }

   public List<TreeNodeModel> getFullDatabaseTree(String parent,
                                                  String parr,
                                                  String additional,
                                                  boolean loadColumns,
                                                  boolean ignoreVpm,
                                                  Principal principal)
      throws Exception
   {
      return getFullDatabaseTree(parent, parr, additional, loadColumns, ignoreVpm, principal,
         System.currentTimeMillis(), null);
   }

   /**
    * Get the full data base tree.
    * @param parent start node.
    * @param parr
    * @param loadColumns whether to load columns.
    * @param ignoreVpm whether to ignore VPM.
    * @param principal user.
    * @param timeOutFunc to call when load time out.
    * @return
    * @throws Exception
    */
   public List<TreeNodeModel> getFullDatabaseTree(String parent, String parr, String additional,
                                                  boolean loadColumns, boolean ignoreVpm,
                                                  Principal principal,
                                                  Function<Boolean, Boolean> timeOutFunc)
      throws Exception
   {
      return getFullDatabaseTree(parent, parr, additional, loadColumns, ignoreVpm, principal,
         System.currentTimeMillis(), timeOutFunc);
   }

   private List<TreeNodeModel> getFullDatabaseTree(String parent, String parr, String additional,
                                                   boolean loadColumns, boolean ignoreVpm, Principal principal,
                                                   long startTime, Function<Boolean, Boolean> func)
      throws Exception
   {
      List<TreeNodeModel> results = new ArrayList<>();
      List<DatabaseTreeNode> children = getDatabaseNodes(
         parent, parr, additional, loadColumns, ignoreVpm, principal);

      for(DatabaseTreeNode child : children) {
         boolean isLeaf = loadColumns ? "column".equals(child.getType()) : !Folder.TYPE.equals(child.getType());

         TreeNodeModel.Builder  builder = TreeNodeModel.builder()
            .label(child.getName())
            .data(child)
            .leaf(isLeaf)
            .type(child.getType())
            .cssClass("action-color");

         boolean loadTimeOut = System.currentTimeMillis() >= startTime + META_LOAD_TIME_OUT;

         if(loadTimeOut) {
            if(func != null) {
               func.apply(true);
            }

            break;
         }

         if(!isLeaf) {
            builder.children(getFullDatabaseTree(
               child.getPath(), child.getParr(), additional, loadColumns, ignoreVpm, principal));
         }

         results.add(builder.build());
      }

      return results;
   }

   public TreeNodeModel getAllAlias(String basePath, Principal principal) throws Exception {
      DatabaseTreeNode tempNode = new DatabaseTreeNode();

      tempNode.setPath(basePath);
      tempNode.setType(DatabaseTreeNodeType.DATABASE);

      List<DatabaseTreeNode> alias = getAlias(tempNode);

      if(CollectionUtils.isEmpty(alias)) {
         return null;
      }

      DatabaseTreeNode aliasRoot = alias.get(0);

      return TreeNodeModel.builder()
         .label(aliasRoot.getName())
         .data(aliasRoot)
         .children(getAllAlias0(aliasRoot, principal))
         .type(aliasRoot.getType())
         .cssClass("action-color")
         .build();
   }

   private List<TreeNodeModel> getAllAlias0(DatabaseTreeNode node, Principal principal)
      throws Exception
   {
      List<TreeNodeModel> result = new ArrayList<>();

      List<DatabaseTreeNode> alias = getAlias(node);

      for(DatabaseTreeNode child : alias) {
         boolean isLeaf = "column".equals(child.getType());

         TreeNodeModel.Builder  builder = TreeNodeModel.builder()
            .label(child.getName())
            .data(child)
            .leaf(isLeaf)
            .type(child.getType())
            .cssClass("action-color");

         if(!isLeaf) {
            builder.children(getAllAlias0(child, principal));
         }

         result.add(builder.build());
      }

      return result;
   }

   /**
    * Gets the data repository instance.
    *
    * @return the repository.
    *
    * @throws Exception if the repository could not be obtained.
    */
   private XDataModel getDataModel(String database) throws Exception {
      return XFactory.getRepository().getDataModel(database);
   }

   /**
    * Get alias table nodes for portal vpm tree.
    */
   public List<DatabaseTreeNode> getAlias(DatabaseTreeNode parentNode) throws Exception {
      List<DatabaseTreeNode> results = new ArrayList<>();
      String parentPath = parentNode.getPath();
      String type = parentNode.getType();

      if(DatabaseTreeNodeType.DATABASE.equals(type)) {
         DatabaseTreeNode node = new DatabaseTreeNode();
         node.setName(ALIAS_NODE_NAME);
         node.setDatabase(parentNode.getPath());
         node.setType(DatabaseTreeNodeType.ALIAS_TABLE_FOLDER);
         node.setPath(parentPath + "/" + ALIAS_NODE_NAME);
         results.add(node);
      }
      else if(DatabaseTreeNodeType.ALIAS_TABLE_FOLDER.equals(type)) {
         String databasePath = parentNode.getDatabase();
         XDataModel dataModel = getDataModel(databasePath);

         if(dataModel != null) {
            String[] partitionNames = dataModel.getPartitionNames();

            // add physical view node
            for(String pname: partitionNames) {
               DatabaseTreeNode pNode = new DatabaseTreeNode();
               pNode.setDatabase(databasePath);
               pNode.setPhysicalView(pname);
               pNode.setType(DatabaseTreeNodeType.PHYSICAL_MODEL);
               pNode.setName(pname);
               pNode.setPath(parentPath + "/" + pname);
               results.add(pNode);
            }
         }
      }
      else if(DatabaseTreeNodeType.PHYSICAL_MODEL.equals(type)) {
         String databasePath = parentNode.getDatabase();
         XDataModel dataModel = getDataModel(databasePath);
         String physicalName = parentNode.getPhysicalView();
         XPartition partition = null;

         if(physicalName != null) {
            partition = dataModel.getPartition(physicalName);
         }

         if(partition != null) {
            // when displaying physical tables for entity mapping, we want to
            // show the auto aliases already applied
            partition = partition.applyAutoAliases();
            Enumeration<XPartition.PartitionTable> tables = partition.getTables();

            while(tables.hasMoreElements()) {
               XPartition.PartitionTable table = tables.nextElement();

               if(table.getType() == inetsoft.uql.erm.PartitionTable.PHYSICAL &&
                  (partition.isAlias(table.getName()) || partition.isAutoAlias(table.getName())))
               {
                  DatabaseTreeNode aliasTable = new DatabaseTreeNode();
                  aliasTable.setName(table.getName());
                  aliasTable.setDatabase(databasePath);
                  aliasTable.setPhysicalView(physicalName);

                  if(table.getSchema() != null) {
                     aliasTable.setSchema(String.valueOf(table.getSchema()));
                  }

                  if(table.getCatalog() != null) {
                     aliasTable.setCatalog(String.valueOf(table.getCatalog()));
                  }

                  aliasTable.setType(DatabaseTreeNodeType.ALIAS_TABLE);
                  aliasTable.setPath(parentPath + "/" + table.getName());
                  results.add(aliasTable);
               }
            }
         }
      }
      else if(DatabaseTreeNodeType.ALIAS_TABLE.equals(type)) {
         String databasePath = parentNode.getDatabase();
         XDataModel dataModel = getDataModel(databasePath);
         String tableName = parentNode.getName();
         String physicalName = parentNode.getPhysicalView();
         XPartition partition = null;

         if(physicalName != null) {
            partition = dataModel.getPartition(physicalName);
         }

         if(partition != null) {
            // when displaying physical tables for entity mapping, we want to
            // show the auto aliases already applied
            partition = partition.applyAutoAliases();

            if(partition.getAliasTable(tableName, true) != null) {
               Enumeration<XPartition.PartitionTable> tables = partition.getTables();
               tableName = partition.getAliasTable(tableName, true);
            }
         }

         XDataSource jdx = datasourceService.getDataSource(dataModel.getDataSource());
         XAgent agent = XAgent.getAgent(jdx);
         XTypeNode[] cols = agent.getColumns(tableName, jdx,
            new DefaultMetaDataProvider().getSession());

         if(cols != null) {
            for(XTypeNode column : cols) {
               DatabaseTreeNode columnNode = new DatabaseTreeNode();
               columnNode.setName(column.getName());
               columnNode.setAttribute(column.getName());
               columnNode.setEntity(parentNode.getName());
               columnNode.setType(DatabaseTreeNodeType.COLUMN);
               columnNode.setPath(parentPath + "/" + column.getName());
               columnNode.setQualifiedName(physicalName + "." + parentNode.getName());
               results.add(columnNode);
            }
         }
      }

      return results;
   }

   private final AssetRepository assetRepository;
   private final DataSourceService datasourceService;
   private final static String[] ACCEPT_DATABASE_TABLE_TABLE = {
      "TABLE", "BASE TABLE", "VIEW", "SYNONYM", "MATERIALIZED VIEW", "SYSTEM TABLE"
   };
   public static final String ALIAS_NODE_NAME = "ALIAS_TABLE";
   public static final long META_LOAD_TIME_OUT = 20000L;
}

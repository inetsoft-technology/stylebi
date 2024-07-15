/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller.database;

import inetsoft.report.XSessionManager;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.XHandler;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.rgraph.TableNode;
import inetsoft.util.Catalog;
import inetsoft.web.portal.model.database.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.List;
import java.util.*;

import static inetsoft.uql.erm.PartitionTable.PHYSICAL;
import static inetsoft.web.portal.model.database.JoinGraphModel.SCALE_Y;
import static inetsoft.web.portal.model.database.graph.PhysicalGraphLayout.*;

/**
 * {@code PhysicalModelService} provides methods to manipulate data models.
 */
@Component
public class PhysicalModelService {
   @Autowired
   public PhysicalModelService(RuntimePartitionService runtimePartitionService,
                               XRepository repository,
                               AssetRepository assetRepository,
                               DataSourceService dataSourceService)
   {
      this.runtimePartitionService = runtimePartitionService;
      this.repository = repository;
      this.assetRepository = assetRepository;
      this.dataSourceService = dataSourceService;
   }

   private void addColumnInfo(PhysicalTableModel table, DefaultMetaDataProvider metaData,
                              XNode tableNode, XPartition.PartitionTable partitionTable)
   {
      List<GraphColumnInfo> cols = getTableColumns(tableNode, metaData, partitionTable);
      table.setCols(cols);
   }

   /**
    * Load columns for partition table.
    *
    * @param tableNode   the meta node of the target partition table.
    * @param metaData    the DefaultMetaDataProvider for current datasource.
    * @param partitionTable the target partition table.
    * @return
    */
   public List<GraphColumnInfo> getTableColumns(XNode tableNode,
                                                DefaultMetaDataProvider metaData,
                                                XPartition.PartitionTable partitionTable)
   {
      List<GraphColumnInfo> cols = new ArrayList<>();
      String tableName = partitionTable.getName();
      TableNode tableMetaData = null;

      if(partitionTable.getType() == PHYSICAL) {
         if(tableNode != null) {
            tableMetaData = metaData.getTableMetaData(tableNode);
         }
      }
      else {
         partitionTable.setDataSource(metaData.getDataSource());
         tableMetaData = partitionTable.getTableNode(); // sql table
      }

      if(tableMetaData == null) {
         return cols;
      }

      for(int i = 0; i < tableMetaData.getColumnCount(); i++) {
         GraphColumnInfo colInfo = new GraphColumnInfo();
         String columnName = tableMetaData.getColumn(i);
         colInfo.setId(tableName + GraphColumnInfo.TABLE_COLUMN_SEPARATOR + columnName);
         colInfo.setName(columnName);
         colInfo.setType(tableMetaData.getColumnType(columnName));
         colInfo.setTable(tableName);

         if(!cols.contains(colInfo)) {
            cols.add(colInfo);
         }
      }

      return cols;
   }

   public XDataModel getDataModel(String database, String name) throws Exception {
      if(database == null || name == null) {
         throw new FileNotFoundException(database + "/" + name);
      }

      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      return dataModel;
   }

   public XPartition getPartition(String database, String name) throws Exception {
      XPartition partition = getDataModel(database, name).getPartition(name);

      if(partition == null) {
         throw new FileNotFoundException(database + "/" + name);
      }

      return partition;
   }
   /**
    * Creates a model DTO structure from a partition data structure.
    *
    * @param dataModel the parent data model.
    * @param partition the partition.
    *
    * @return the model.
    *
    * @throws Exception if the model could not be created.
    */
   public PhysicalModelDefinition createModel(XDataModel dataModel,
                                              RuntimePartitionService.RuntimeXPartition partition)
      throws Exception
   {
      PhysicalModelDefinition modelDefinition =
         createModel(partition.getDataSource(), dataModel, partition.getPartition());
      modelDefinition.setId(partition.getId());

      return modelDefinition;
   }

   public DefaultMetaDataProvider getMetaDataProvider(String runtimeId) throws Exception {
      RuntimePartitionService.RuntimeXPartition rPartition
         = runtimePartitionService.getRuntimePartition(runtimeId);

      String database = rPartition.getDataSource();
      String pvName = rPartition.getPartition().getName();
      String addtional = rPartition.getPartition().getConnection();
      XDataModel dataModel = getDataModel(database, pvName);

      return getMetaDataProvider0(database, dataModel, addtional);
   }

   public DefaultMetaDataProvider getMetaDataProvider0(String database, XDataModel dataModel,
                                                       String additional)
      throws Exception
   {
      JDBCDataSource dataSource =
         (JDBCDataSource) dataSourceService.getDataSource(database, additional);
      DefaultMetaDataProvider metaData =
         dataSourceService.getDefaultMetaDataProvider(dataSource, dataModel);

      return metaData;
   }

   public PhysicalModelDefinition createModel(String database, XDataModel dataModel,
                                              XPartition partition) throws Exception
   {
      return createModel(database, dataModel, partition, false);
   }

   /**
    * Creates a model DTO structure from a partition data structure.
    *
    * @param database  the database name.
    * @param dataModel the parent data model.
    * @param partition the partition.
    *
    * @return the model.
    *
    * @throws Exception if the model could not be created.
    */
   public PhysicalModelDefinition createModel(String database, XDataModel dataModel,
                                              XPartition partition, boolean graphView)
      throws Exception
   {
      XPartition originalPartition = partition;

      if(graphView) {
         partition = partition.applyAutoAliases();
      }

      String additional = partition != null ? partition.getConnection() : null;

      if(StringUtils.isEmpty(additional)) {
         additional = XUtil.OUTER_MOSE_LAYER_DATABASE;
      }

      JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.
         getDataSource(database, additional);
      DefaultMetaDataProvider metaData = getMetaDataProvider0(database, dataModel, additional);

      PhysicalModelDefinition result = new PhysicalModelDefinition();
      result.setName(partition.getName());
      result.setMetaData(metaData);
      result.setConnection(partition.getConnection());
      String folder = partition.getFolder();

      if(partition.getBasePartition() != null) {
         folder = partition.getBasePartition().getFolder();
      }

      result.setFolder(folder);

      Map<String, PhysicalTableModel> tables = new HashMap<>();
      SQLHelper sqlHelper = dataSourceService.getSqlHelper(dataSource, additional);
      boolean supportFullOuterJoin = JDBCUtil.supportFullOuterJoin(dataSource, true);

      for(Enumeration<XPartition.PartitionTable> e = partition.getTables();
          e.hasMoreElements();)
      {
         XPartition.PartitionTable partitionTable = e.nextElement();
         PhysicalTableModel table = new PhysicalTableModel();
         table.setBounds(partition.getBounds(partitionTable, graphView).getBounds());
         table.setType(TableType.forType(partitionTable.getType()));
         final String fqn = partitionTable.getName();
         final String oldAlias = partitionTable.getOldName();
         table.setQualifiedName(fqn);
         String catalog = null;
         String schema = null;
         String tableName = fqn;
         String path = null;
         XNode tableNode = getTableXNode(partition, fqn, metaData);

         if(table.getType() == TableType.PHYSICAL) {
            if(tableNode != null) {
               catalog = (String) tableNode.getAttribute("catalog");
               schema = (String) tableNode.getAttribute("schema");
               tableName = getTableName(tableNode, sqlHelper);
               path = getTablePath(dataSource, tableNode);
            }
         }
         else {
            String tableAlias = partition.getAliasTable(fqn, true);

            if(tableAlias == null) {
               tableAlias = fqn;
            }

            tableName = tableAlias;
            path = database + "/" + tableName;
            table.setSql(partitionTable.getSql());
         }

         boolean isAliasTable = partition.isAlias(fqn);
         String aliasTable = isAliasTable ? partition.getAliasTable(fqn) : null;

         if(isAliasTable) {
            table.setAlias(fqn);
            table.setOldAlias(oldAlias);
            table.setAliasSource(aliasTable);

            if(graphView) {
               table.setOutgoingAliasSource(DatabaseModelUtil.getOutgoingAutoAliasSource(fqn,
                  originalPartition, partition));
            }
         }

         boolean isBaseTable = partition.isBaseTable(fqn);

         if(graphView && isAliasTable && !isBaseTable && partition.getBasePartition() != null) {
            String outLinkAliasSource = DatabaseModelUtil.getOutgoingAutoAliasSource(fqn,
               originalPartition, partition);
            isBaseTable = outLinkAliasSource != null
               && !originalPartition.containsTable(outLinkAliasSource, false, true)
               && !originalPartition.containsTable(aliasTable, false, true);
         }

         table.setBaseTable(isBaseTable);
         table.setName(tableName);
         table.setCatalog(catalog);
         table.setSchema(schema);
         table.setPath(path);

         if(tableNode != null) {
            tableNode.setAttribute(XUtil.DATASOURCE_ADDITIONAL,
                                   partition.getConnection() == null ?
                                   XUtil.OUTER_MOSE_LAYER_DATABASE : partition.getConnection());
         }

         addColumnInfo(table, metaData, tableNode, partitionTable);

         AutoAlias autoAlias = partition.getAutoAlias(fqn);
         Set<String> selectedTables = new HashSet<>();

         if(autoAlias != null) {
            int count = autoAlias.getIncomingJoinCount();

            for(int i = 0; i < count; i++) {
               AutoAlias.IncomingJoin incomingJoin = autoAlias.getIncomingJoin(i);
               AutoAliasJoinModel join = new AutoAliasJoinModel(incomingJoin);
               table.getAutoAliases().add(join);
               table.setAutoAliasesEnabled(true);
               selectedTables.add(incomingJoin.getSourceTable());
            }
         }

         for(Enumeration<XRelationship> e2 = partition.getRelationships();
             e2.hasMoreElements();)
         {
            XRelationship relationship = e2.nextElement();
            String foreignTable = null;

            if(relationship.getDependentTable()
               .equals(table.getQualifiedName()))
            {
               foreignTable = relationship.getIndependentTable();
            }
            else if(relationship.getIndependentTable()
               .equals(table.getQualifiedName()))
            {
               foreignTable = relationship.getDependentTable();
            }

            if(foreignTable != null && !selectedTables.contains(foreignTable)) {
               selectedTables.add(foreignTable);
               AutoAliasJoinModel join = new AutoAliasJoinModel();
               join.setForeignTable(foreignTable);
               table.getAutoAliases().add(join);
            }
         }

         result.getTables().add(table);
         tables.put(fqn, table);
      }

      Set<XRelationship> cycleRelationships = partition.getCycleRelationships();
      boolean hasAliasCycle = checkAliasCycle(originalPartition);

      for(Enumeration<XRelationship> e = partition.getRelationships();
          e.hasMoreElements();)
      {
         XRelationship relationship = e.nextElement();

         String dtableName = relationship.getDependentTable();
         String itableName = relationship.getIndependentTable();
         XPartition.PartitionTable dtable = partition.getPartitionTable(dtableName);
         XPartition.PartitionTable itable = partition.getPartitionTable(itableName);

         if(dtable == null || itable == null) {
            continue;
         }

         JoinModel join = new JoinModel();
         join.setBaseJoin(isBaseJoin(partition, graphView, originalPartition, relationship));
         join.setRelationship(relationship);
         join.setTable(relationship.getDependentTable());
         join.setColumn(relationship.getDependentColumn());
         join.setForeignTable(relationship.getIndependentTable());
         join.setForeignColumn(relationship.getIndependentColumn());
         join.setType(JoinType.forType(relationship.getJoinType()));
         join.setMergingRule(MergingRule.forType(relationship.getMerging()));
         join.setOrderPriority(relationship.getOrder());
         join.setWeak(relationship.isWeakJoin());
         join.setCycle(hasAliasCycle && cycleRelationships != null
                          && cycleRelationships.contains(relationship));
         join.setSupportFullOuter(supportFullOuterJoin);

         if(relationship.getDependentCardinality() == XRelationship.ONE &&
            relationship.getIndependentCardinality() == XRelationship.ONE) {
            join.setCardinality(JoinCardinality.ONE_TO_ONE);
         }
         else if(relationship.getDependentCardinality() == XRelationship.ONE) {
            join.setCardinality(JoinCardinality.ONE_TO_MANY);
         }
         else if(relationship.getIndependentCardinality() == XRelationship.ONE) {
            join.setCardinality(JoinCardinality.MANY_TO_ONE);
         }
         else if(relationship.getDependentCardinality() == 0 &&
            relationship.getIndependentCardinality() == 0)
         {
            // backward compatibility, default to many to one
            join.setCardinality(null);
         }
         else {
            join.setCardinality(JoinCardinality.MANY_TO_MANY);
         }

         String dependentTable = relationship.getDependentTable();
         PhysicalTableModel tableModel = tables.get(dependentTable);

         // graph view apply auto alias
         if(graphView && tableModel == null
            & !originalPartition.isRealTable(dependentTable)
            && partition.isRealTable(dependentTable))
         {
            String alias = partition.getAlias(dependentTable);
            tableModel = tables.get(alias);
         }

         tableModel.getJoins().add(join);
      }

      return result;
   }

   public boolean isBaseJoin(XPartition partition, boolean graphView,
                             XPartition originalPartition, XRelationship relationship)
   {
      boolean isBaseJoin = partition.isBaseRelationship(relationship);

      String leftAlias = relationship.getDependentTable();
      String rightAlias = relationship.getIndependentTable();

      if(graphView && !isBaseJoin && partition.getBasePartition() != null
         && (partition.isAlias(leftAlias) || partition.isAlias(rightAlias)))
      {
         String leftTable = partition.isAlias(leftAlias) ? partition.getAliasTable(leftAlias)
            : leftAlias;
         String rightTable = partition.isAlias(rightAlias) ? partition.getAliasTable(rightAlias)
            : rightAlias;

         XRelationship clone = (XRelationship) relationship.clone();
         clone.setDependent(leftTable, clone.getDependentColumn());
         clone.setIndependent(rightTable, clone.getIndependentColumn());

         isBaseJoin = originalPartition.isBaseRelationship(clone);
      }

      return isBaseJoin;
   }

   private boolean checkAliasCycle(XPartition partition) {
      boolean hasAliasCycle = true;

      try {
         XPartition partition2 = partition.applyAutoAliases();
         Set<XRelationship> temp = partition2.getCycleRelationships();
         hasAliasCycle = temp != null && temp.size() > 0;
      }
      // the partition may be applied alias
      catch(Exception ex) {
         // ignore it
      }

      return hasAliasCycle;
   }

   public static XNode getTableXNode(XPartition partition, String tableName,
                                     DefaultMetaDataProvider metaData)
      throws Exception
   {
      String tableAlias = partition.getAliasTable(tableName, true);
      XNode tableNode;

      if(tableAlias == null) {
         tableAlias = tableName;
      }

      XPartition.PartitionTable partitionTable =
         partition.getPartitionTable(tableAlias);
      String additional = partition.getConnection() == null ? XUtil.OUTER_MOSE_LAYER_DATABASE :
         partition.getConnection();

      if(partitionTable == null) {
         tableNode = metaData.getTable(tableAlias, additional);
      }
      else {
         Object catalog = partitionTable.getCatalog();

         if(catalog != null) {
            catalog = catalog.toString();
         }

         Object schema = partitionTable.getSchema();

         if(schema != null) {
            schema = schema.toString();
         }

         if(schema == null && catalog == null) {
            tableNode = getTableMetaData(tableAlias, additional, metaData);
         }
         else {
            tableNode = getTableMetaData(
               (String) catalog, (String) schema, partitionTable.getName(), additional, metaData);
         }
      }

      return tableNode;
   }

   public static XNode getTableMetaData(String table, String additional,
                                       DefaultMetaDataProvider metaData)
      throws Exception
   {
      return metaData.getTable(table, additional);
   }

   public static XNode getTableMetaData(String catalog, String schema, String table,
                                        String additional, DefaultMetaDataProvider metaData)
      throws Exception
   {
      return metaData.getTable(catalog, schema, table, additional, true);
   }

   public static TableNode getTableNode(XPartition partition,
                                        String tableName,
                                        DefaultMetaDataProvider metadata)
      throws Exception
   {
      TableNode result = null;
      String tableAlias = partition.getAliasTable(tableName, true);

      if(tableAlias == null) {
         tableAlias = tableName;
      }

      XPartition.PartitionTable partitionTable =
         partition.getPartitionTable(tableAlias);

      // alias table maybe do not have source table in the partition, this time to get the alias self.
      if(partitionTable == null) {
         partitionTable = partition.getPartitionTable(tableName);
      }

      XNode tableNode = getTableXNode(partition, tableAlias, metadata);

      if(partitionTable.getType() == PHYSICAL) {
         if(tableNode != null) {
            result = metadata.getTableMetaData(tableNode);
         }
      }
      else {
         result = partitionTable.getTableNode(); // sql table
      }

      return result;
   }

   public String getTableName(XNode tableNode, SQLHelper sqlHelper) {
      String catalog = (String) tableNode.getAttribute("catalog");
      String schema = (String) tableNode.getAttribute("schema");
      String tableName = tableNode.getName();
      String separator = (String) tableNode.getAttribute("catalogSep");

      if(separator == null) {
         separator = ".";
      }

      if(tableName.contains(separator)) {
         String quote = sqlHelper.getQuote();
         String prefix = "";

         if(catalog != null) {
            prefix = catalog + separator;

            if(!tableName.startsWith(prefix)) {
               prefix = quote + catalog + quote + separator;

               if(!tableName.startsWith(prefix)) {
                  prefix = "";
               }
            }
         }

         if(schema != null) {
            String oldPrefix = prefix;
            prefix += schema + separator;

            if(!tableName.startsWith(prefix)) {
               prefix = oldPrefix + quote + schema + quote + separator;

               if(!tableName.startsWith(prefix)) {
                  prefix = oldPrefix;
               }
            }
         }

         tableName = tableName.substring(prefix.length());

         if(tableName.startsWith(quote) && tableName.endsWith(quote)) {
            tableName = tableName.substring(
               quote.length(), tableName.length() - quote.length());
         }
      }

      return tableName;
   }

   public String getTablePath(JDBCDataSource dataSource, XNode tableNode) {
      StringBuilder path =
         new StringBuilder().append(dataSource.getFullName()).append('/');
      path.append(tableNode.getAttribute("type")).append('/');

      if(tableNode.getAttribute("catalog") != null) {
         path.append(tableNode.getAttribute("catalog")).append('/');
      }

      if(tableNode.getAttribute("schema") != null) {
         path.append(tableNode.getAttribute("schema")).append('/');
      }

      path.append(tableNode.getName());
      return path.toString();
   }

   public List<String> getTableColumns(String tablePath, Principal principal) throws Exception {
      List<String> results = new ArrayList<>();

      AssetEntry tableEntry =
         DatabaseModelUtil.getDatabaseEntry(tablePath, assetRepository, null, principal);

      AssetEntry[] entries = assetRepository.getEntries(
         tableEntry, principal, ResourceAction.READ, new AssetEntry.Selector(
            AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL,
            AssetEntry.Type.FOLDER));

      for(AssetEntry entry : entries) {
         results.add(entry.getName());
      }

      return results;
   }

   public List<String> getViewColumns(String dataSource, String additional, String sql,
                                      Principal principal)
      throws Exception
   {
      if(sql == null || (sql = sql.trim()).isEmpty()) {
         throw new Exception("The SQL statement is empty");
      }

      XNode node = executeSQLQuery(sql, dataSource, additional, principal);
      XNodeTableLens lens = new XNodeTableLens(node);
      Set<String> columns = new LinkedHashSet<>();

      for(int i = 0; i < lens.getColCount(); i++) {
         String column = lens.getColumnIdentifier(i);

         if(columns.contains(column)) {
            throw new Exception(
               Catalog.getCatalog().getString("data.physicalmodel.inlineViewColumnDuplicate"));
         }

         columns.add(column);
      }

      return new ArrayList<>(columns);
   }

   /**
    * Creates a partition data structure from a model DTO structure.
    *
    * @param model the model to convert.
    *
    * @return the partition object.
    */
   public XPartition createPartition(PhysicalModelDefinition model) {
      XPartition partition = new XPartition(model.getName());

      if(org.springframework.util.StringUtils.hasText(model.getDescription())) {
         partition.setDescription(model.getDescription());
      }

      if(org.springframework.util.StringUtils.hasText(model.getFolder())) {
         partition.setFolder(model.getFolder());
      }

      for(PhysicalTableModel table : model.getTables()) {
         addPartitionTable(partition, table);
      }

      partition.setConnection(model.getConnection());

      return partition;
   }

   public String addPartitionTable(XPartition partition, PhysicalTableModel table) {
      String tableName = org.springframework.util.StringUtils.isEmpty(table.getAlias()) ?
         table.getQualifiedName() : table.getAlias();

      partition.addTable(
         tableName, table.getType().getType(), table.getSql(), table.getCatalog(),
         table.getSchema());

      if(table.getAlias() != null) {
         String aliasSource = table.getAliasSource();

         if(!org.springframework.util.StringUtils.isEmpty(aliasSource) &&
            partition.isAlias(aliasSource))
         {
            partition.setAliasTable(table.getAlias(), aliasSource);
         }
         else {
            partition.setAliasTable(table.getAlias(), table.getQualifiedName());
         }
      }

      addRemoveAutoAlias(partition, table);

      for(JoinModel join : table.getJoins()) {
         XRelationship relationship = PhysicalModelService.createJoin(tableName, join);
         partition.addRelationship(relationship);
      }

      return tableName;
   }

   public void addRemoveAutoAlias(XPartition partition, PhysicalTableModel table) {
      String tableName = table.getAlias() == null ?
         table.getQualifiedName() : table.getAlias();

      if(!table.getAutoAliases().isEmpty() && table.isAutoAliasesEnabled()) {
         AutoAlias autoAlias = new AutoAlias();
         boolean anySelected = false;

         for(AutoAliasJoinModel join : table.getAutoAliases()) {
            if(join.isSelected()) {
               anySelected = true;
               AutoAlias.IncomingJoin incomingJoin =
                  new AutoAlias.IncomingJoin();
               incomingJoin.setSourceTable(join.getForeignTable());
               incomingJoin.setAlias(join.getAlias());
               incomingJoin.setPrefix(join.getPrefix());
               incomingJoin.setKeepOutgoing(join.isKeepOutgoing());
               autoAlias.addIncomingJoin(incomingJoin);
            }
         }

         table.setAutoAliasesEnabled(anySelected);
         AutoAlias oldAutoalias = partition.getAutoAlias(tableName);
         partition.setAutoAlias(tableName, autoAlias);
         syncAutoAliasTablePosition(oldAutoalias, autoAlias, partition, tableName);
      }
      else {
         partition.setAutoAlias(tableName, null);
      }
   }

   private void syncAutoAliasTablePosition(AutoAlias oldAutoAlias,
                                           AutoAlias autoAlias,
                                           XPartition partition,
                                           String tableName)
   {
      if(AutoAlias.isEmpty(oldAutoAlias) || AutoAlias.isEmpty(autoAlias)) {
         return;
      }

      Object[] linked = partition.getNeighbors(tableName);
      int oldJoinCount = oldAutoAlias.getIncomingJoinCount();
      int joinCount = autoAlias.getIncomingJoinCount();

      for(int i = 0; i < oldJoinCount; i++) {
         AutoAlias.IncomingJoin oldJoin = oldAutoAlias.getIncomingJoin(i);

         for(int j = 0; j < joinCount; j++) {
            AutoAlias.IncomingJoin join = autoAlias.getIncomingJoin(j);
            String sourceTable = join.getSourceTable();

            if(!Objects.equals(oldJoin.getSourceTable(), sourceTable)) {
               continue;
            }

            // sync auto alias
            String oldAliasTableName = oldJoin.getAlias();
            String aliasTableName = join.getAlias();
            Rectangle oldAliasBounds = partition.getRuntimeAliasTableBounds(oldAliasTableName);

            if(oldAliasBounds != null && !Objects.equals(oldAliasTableName, aliasTableName)) {
               partition.setRuntimeAliasTableBounds(aliasTableName, oldAliasBounds.getBounds());
            }

            // sync outgoing
            String oldPrefix = oldJoin.getPrefix();
            String prefix = join.getPrefix();

            if(!join.isKeepOutgoing() || !oldJoin.isKeepOutgoing() ||
               org.springframework.util.StringUtils.isEmpty(oldPrefix) ||
               org.springframework.util.StringUtils.isEmpty(prefix) ||
               Objects.equals(oldPrefix, prefix))
            {
               continue;
            }

            for(Object element : linked) {
               String link = (String) element;
               String oldOutgoingName = oldPrefix + XPartition.OUTGOING_TABLE_SEPARATOR + link;
               String outgoingName = prefix + XPartition.OUTGOING_TABLE_SEPARATOR + link;
               Rectangle outgoingBounds = partition.getRuntimeAliasTableBounds(oldOutgoingName);

               if(outgoingBounds == null) {
                  continue;
               }

               partition.setRuntimeAliasTableBounds(outgoingName, outgoingBounds.getBounds());
            }
         }
      }
   }

   public static XRelationship createJoin(String tableName, JoinModel join) {
      XRelationship relationship = new XRelationship();
      join.store(tableName, relationship);

      return relationship;
   }

   public void fixTableBounds(String runtimeId, XPartition partition, String tableName)
      throws Exception
   {
      RuntimePartitionService.RuntimeXPartition rPartition
         = runtimePartitionService.getRuntimePartition(runtimeId);
      String ds = rPartition.getDataSource();
      XDataModel dataModel = getDataModel(ds, partition.getName());
      // for auto alias tables using graph view nodes.
      PhysicalModelDefinition pmModel
         = createModel(rPartition.getDataSource(), dataModel, partition, true);
      JoinGraphModel physicalGraphModel
         = JoinGraphModel.convertModel(pmModel, null, partition, true);
      GraphViewModel graphViewModel = physicalGraphModel.getGraphViewModel();

      // runtime graph view is scaled.
      int maxTop = (int) (DEFAULT_GRAPH_TOP_GAP * SCALE_Y);

      for(GraphModel graph : graphViewModel.getGraphs()) {
         String fqn = graph.getNode().getName();

         Rectangle bounds = graph.getBounds();

         if(bounds.y > maxTop) {
            maxTop = bounds.y;
         }

         if(tableName.equals(fqn)) {
            int width = DEFAULT_GRAPH_WIDTH;
            int height = DEFAULT_GRAPH_HEIGHT;
            List<GraphColumnInfo> cols = graph.getCols();

            if(cols != null) {
               int columnCount = cols.size();
               int columnLength = fqn.length();

               for(GraphColumnInfo col : cols) {
                  String columnName = col.getName();

                  if(columnName.length() > columnLength) {
                     columnLength = columnName.length();
                  }
               }

               if(columnLength > 0) {
                  width = DEFAULT_CHAR_WIDTH * columnLength + DEFAULT_COLUMN_MARGIN;
               }

               if(columnCount > 0) {
                  height = columnCount * DEFAULT_COLUMN_LINE_HEIGHT + DEFAULT_TITLE_LINE_HEIGHT;
               }
            }

            rPartition.setBounds(
               tableName,
               new Rectangle(DEFAULT_GRAPH_LEFT_GAP, DEFAULT_GRAPH_TOP_GAP, width, height));
         }
      }

      partition.getBounds(tableName).y =
         (int) Math.round((maxTop + PORTAL_GRAPH_NODE_HEIGHT + PORTAL_ADD_NODE_TOP_GAP) / SCALE_Y);
      rPartition.addMovedTable(tableName);
   }

   /**
    * Executes an inline views sql query on the datasource.
    *
    * @param sql        the sql query
    * @param database   the datasource name
    * @return the XNode representing the table and columns returned by the query
    * @throws Exception if the query could not be executed
    */
   public XNode executeSQLQuery(String sql, String database, Principal principal) throws Exception {
      return executeSQLQuery(sql, database, null, principal);
   }

   /**
    * Executes an inline views sql query on the datasource.
    *
    * @param sql        the sql query
    * @param database   the datasource name
    * @return the XNode representing the table and columns returned by the query
    * @throws Exception if the query could not be executed
    */
   public XNode executeSQLQuery(String sql, String database, String additional, Principal principal)
      throws Exception
   {
      JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.
         getDataSource(database, additional);

      if(dataSource == null) {
         throw new FileNotFoundException();
      }

      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(new UniformSQL(sql, false));
      query.setDataSource(dataSource);

      VariableTable parameters = new VariableTable();
      parameters.put(JDBCQuery.HINT_MAX_ROWS, "1");

      XHandler handler = XFactory.getDataService().getHandler(
         XSessionManager.getSessionManager().getSession(), dataSource,
         new VariableTable());

      return handler.execute(query, parameters, null, null);
   }

   public JoinModel getCardinality(String database, String additional,
                                   CardinalityHelper helper, Principal principal)
   {
      JoinModel join = helper.getJoin();

      try {
         if(StringUtils.isEmpty(additional)) {
            additional = XUtil.OUTER_MOSE_LAYER_DATABASE;
         }

         JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.
            getDataSource(database, additional);
         XDataModel dataModel = repository.getDataModel(database);

         if(dataSource == null || dataModel == null) {
            throw new FileNotFoundException(database);
         }

         DefaultMetaDataProvider metaData = getMetaDataProvider0(database, dataModel, additional);
         XPartition xPartition = createPartition(helper.getModel());
         boolean isDependentKey = false;
         boolean isIndependentKey = false;
         XPartition.PartitionTable dependentTable = xPartition.getPartitionTable(helper.getTable());
         XPartition.PartitionTable independentTable =
            xPartition.getPartitionTable(join.getForeignTable());

         if(dependentTable != null) {
            XNode tableNode;

            if(TableType.forType(dependentTable.getType()) == TableType.PHYSICAL) {
               tableNode = metaData.getPrimaryKeys(
                  getTableXNode(xPartition, helper.getTable(), metaData));
            }
            else {
               tableNode = metaData.getPrimaryKeys(
                  executeSQLQuery(independentTable.getSql(), database, principal));
            }

            isDependentKey = XUtil.isPrimaryKey(join.getColumn(), tableNode);
         }

         if(independentTable != null) {
            XNode tableNode;

            if(TableType.forType(independentTable.getType()) == TableType.PHYSICAL) {
               tableNode = metaData.getPrimaryKeys(
                  getTableXNode(xPartition, join.getForeignTable(), metaData));
            }
            else {
               tableNode = metaData.getPrimaryKeys(
                  executeSQLQuery(independentTable.getSql(), database, principal));
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
         LOG.warn(ex.getMessage(), ex);
      }

      return join;
   }

   public List<String> getPhysicalViews(String database, Principal principal) throws Exception {
      List<String> result  = new ArrayList<>();

      if(!dataSourceService.checkPermission(database, ResourceAction.READ, principal)) {
         return result;
      }

      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         return result;
      }

      String[] partitionNames = dataModel.getPartitionNames();

      if(partitionNames != null) {
         result = Arrays.asList(partitionNames);
      }

      return result;
   }

   // TODO runtime calc
   private static final int DEFAULT_COLUMN_LINE_HEIGHT = 14;
   private static final int DEFAULT_TITLE_LINE_HEIGHT = 18;
   private static final int DEFAULT_CHAR_WIDTH = 6;
   private static final int DEFAULT_COLUMN_MARGIN = 5;
   private static final int DEFAULT_GRAPH_WIDTH = 120;
   private static final int DEFAULT_GRAPH_HEIGHT = 240;

   private final RuntimePartitionService runtimePartitionService;
   private final XRepository repository;
   private final AssetRepository assetRepository;
   private final DataSourceService dataSourceService;

   private static final Logger LOG = LoggerFactory.getLogger(PhysicalModelService.class);
}

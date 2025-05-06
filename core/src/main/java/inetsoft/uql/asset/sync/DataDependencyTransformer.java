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
package inetsoft.uql.asset.sync;

import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.*;

/**
 * This class used to transform when table option of jdbc datasource changed.
 *
 * @version 13.3
 * @author InetSoft Technology Corp
 */
public class DataDependencyTransformer extends DependencyTransformer {
   /**
    * Create a transformer to rename dependenies when table option of datasource changed.
    *
    * @param entry the target physical view or physical table which need to be transformed.
    */
   public DataDependencyTransformer(AssetEntry entry) {
      this.entry = entry;
   }

   @Override
   public RenameDependencyInfo process(List<RenameInfo> infos) {
      if(infos == null || infos.size() == 0) {
         return null;
      }

      for(int i = 0; i < infos.size(); i++) {
         RenameInfo rinfo = infos.get(i);

         if(!(infos.get(i) instanceof ChangeTableOptionInfo)) {
            continue;
         }

         ChangeTableOptionInfo tinfo = (ChangeTableOptionInfo) rinfo;
         transformTableName(tinfo);
      }

      return null;
   }

   private void transformTableName(ChangeTableOptionInfo tinfo) {
      if(tinfo == null || StringUtils.isEmpty(tinfo.getSource())) {
         return;
      }

      String database = tinfo.getSource();
      XDataModel dataModel = getDataModel(database);

      if(dataModel == null) {
         LOG.error(catalog.getString("notFind.dataModel", database));
         return;
      }

      XDataSource xDataSource = getDataSource(database, null);

      if(xDataSource == null) {
         LOG.error(catalog.getString("notFind.database", database));
         return;
      }

      if(!(xDataSource instanceof JDBCDataSource)) {
         return;
      }

      JDBCDataSource ds = (JDBCDataSource) xDataSource;
      JDBCDataSource oldDs = (JDBCDataSource) ds.clone();
      oldDs.setTableNameOption(tinfo.getOldOption());
      DefaultMetaDataProvider metadata = new DefaultMetaDataProvider();
      metadata.setDataSource(oldDs);
      transformTableName0(tinfo, metadata, null);
      String[] names = ds.getDataSourceNames();

      for(String name : names) {
         JDBCDataSource oadditional = (JDBCDataSource) ds.getDataSource(name).clone();
         oadditional.setTableNameOption(tinfo.getOldOption());
         JDBCDataSource nadditional = (JDBCDataSource) ds.getDataSource(name).clone();
         nadditional.setTableNameOption(tinfo.getNewOption());
         DefaultMetaDataProvider ametadata = new DefaultMetaDataProvider();
         ametadata.setDataSource(nadditional);
         transformTableName0(tinfo, ametadata, name);
      }
   }

   private void transformTableName0(ChangeTableOptionInfo tinfo, DefaultMetaDataProvider metadata,
                                    String additional)
   {
      if(entry.isPhysical()) {
         transformPartitions(tinfo, metadata, additional);
      }
      else if(entry.isPhysicalTable()) {
         RenameDependencyInfo dinfo = new RenameDependencyInfo();
         List<RenameInfo> rinfos = new ArrayList<>();
         addPhyTableRenameInfo(tinfo, dinfo, rinfos, metadata, additional);
         addSQLTableRenameInfo(tinfo, dinfo, rinfos);

         if(additional == null) {
            RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
         }
      }
      else if(entry.isVPM()) {
         transformVPMs(tinfo, metadata, additional);
      }
   }

   private void addSQLTableRenameInfo(ChangeTableOptionInfo info, RenameDependencyInfo dinfo,
                                      List<RenameInfo> rinfos)
   {
      String dsname = info.getSource();
      String key = DependencyTransformer.getAssetId(dsname, AssetEntry.Type.DATA_SOURCE, null);

      // For sql table, its table name is in column info, so only add its prefix
      // to rename info and fix it in AssetSQLTableDependencyTransformer.
      RenameInfo ninfo = new RenameInfo(dsname, dsname,
         RenameInfo.SQL_TABLE | RenameInfo.DATA_SOURCE_OPTION);
      List<AssetObject> entries = DependencyTransformer.getDependencies(key);

      if(entries == null) {
         rinfos.add(ninfo);
         return;
      }

      for(AssetObject obj : entries) {
         dinfo.addRenameInfo(obj, ninfo);
      }
   }

   private void addPhyTableRenameInfo(ChangeTableOptionInfo info, RenameDependencyInfo dinfo,
                                      List<RenameInfo> rinfos, DefaultMetaDataProvider metadata,
                                      String additional)
   {
      String db = info.getSource();
      DependencyStorageService service = DependencyStorageService.getInstance();
      Set<String> keys = service.getKeys(null);

      if(db.indexOf("/") != -1) {
         db = db.substring(db.lastIndexOf("/") + 1);
      }

      for(String key : keys) {
         if(key.startsWith("directory.")) {
            continue;
         }

         AssetEntry entry = AssetEntry.createAssetEntry(key);

         if(!entry.isPhysicalTable()) {
            continue;
         }

         String id = entry.getPath();

         if(id.indexOf("/") == -1) {
            continue;
         }

         int idx = id.indexOf("/");
         String source = id.substring(0, idx);
         String table = id.substring(idx + 1);

         // If the key is current sources's physical table, do not check it
         if(!Tool.equals(source, db)) {
            continue;
         }

         String ntableName = getQualifiedTableName(table, info.getNewOption(), metadata, additional);

         if(ntableName == null || Tool.equals(table, ntableName)) {
            continue;
         }

         RenameInfo ninfo = new RenameInfo(table, ntableName,
            RenameInfo.PHYSICAL_TABLE | RenameInfo.SOURCE);
         ninfo.setPrefix(db);
         List<AssetObject> entries = DependencyTransformer.getDependencies(key);

         if(entries == null) {
            continue;
         }

         for(AssetObject obj : entries) {
            dinfo.addRenameInfo(obj, ninfo);
         }

         rinfos.add(ninfo);
         service.rename(key, key.replace(table, ntableName), ninfo.getOrganizationId());
      }
   }

   /**
    * Transform dependencies for the target query.
    *
    * @param query the target query.
    * @param infos the RenameInfo list created by transform query table.
    */
   private void transformDependencies(XQuery query, List<RenameInfo> infos) {
      List<AssetObject> entries = DependencyTransformer.getQueryDependencies(query.getName());

      if(entries == null) {
         return;
      }

      RenameDependencyInfo dinfo = new RenameDependencyInfo();

      for(AssetObject entry : entries) {
         for(RenameInfo rinfo : infos) {
            dinfo.addRenameInfo(entry, rinfo);
         }
      }

      RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
   }

   /**
    * Transform vpm when table option changed.
    * @param info
    */
   private void transformVPMs(ChangeTableOptionInfo info, DefaultMetaDataProvider metadata,
                              String additional)
   {
      XDataModel model = getDataModel(info.getSource());

      if(model == null) {
         LOG.error(catalog.getString("notFind.dataModel", info.getSource()));
         return;
      }

      String[] names = model.getVirtualPrivateModelNames();

      if(names == null || names.length == 0) {
         return;
      }

      for(String vpmName : names) {
         VirtualPrivateModel vm = model.getVirtualPrivateModel(vpmName);
         transformVPMHiddenColumns(vm, info, metadata, additional);
         transformVPMConditions(vm, info, metadata, additional);
         model.removeVirtualPrivateModel(vm.getName());
         model.addVirtualPrivateModel(vm, true);
      }
   }

   private void transformVPMConditions(VirtualPrivateModel vm, ChangeTableOptionInfo info,
                                       DefaultMetaDataProvider metadata, String additional)
   {
      Enumeration enu = vm.getConditions();

      while(enu.hasMoreElements()) {
         VpmCondition con = (VpmCondition) enu.nextElement();
         XFilterNode condition = con.getCondition();
         XExpression expression1 = condition.getExpression1();
         String otable = con.getTable();
         String ntableName = getQualifiedTableName(otable, info.getNewOption(), metadata, additional);

         if(ntableName == null) {
            continue;
         }

         if(expression1 != null && !Tool.equals(otable, ntableName)) {
            String value1 = (String) expression1.getValue();
            String nvalue = Tool.replaceAll(value1, otable + ".", ntableName + ".");
            expression1.setValue(nvalue, expression1.getType());
         }

         if(!Objects.equals(otable, ntableName)) {
            con.setTable(ntableName);
         }
      }
   }

   private void transformVPMHiddenColumns(VirtualPrivateModel vm, ChangeTableOptionInfo info,
                                          DefaultMetaDataProvider metadata, String additional)
   {
      HiddenColumns hiddens = vm.getHiddenColumns();

      if(hiddens == null) {
         return;
      }

      Enumeration cols = hiddens.getHiddenColumns();
      ArrayList<AttributeRef> ncols = new ArrayList<>();

      while(cols.hasMoreElements()) {
         AttributeRef ref = (AttributeRef) cols.nextElement();

         if(ref == null) {
            continue;
         }

         String tname = ref.getEntity();
         String ntableName = getQualifiedTableName(tname, info.getNewOption(), metadata, additional);

         if(ntableName == null) {
            continue;
         }

         if(!Tool.equals(tname, ntableName)) {
            AttributeRef nref = new AttributeRef(ntableName, ref.getAttribute());
            String caption = ref.getCaption();

            if(caption != null) {
               nref.setCaption(ref.getCaption().replace(tname, ntableName));
            }

            ncols.add(nref);
         }
         else {
            ncols.add(ref);
         }
      }

      if(ncols.size() > 0) {
         hiddens.removeHiddenColumns();
         ncols.forEach(ncol -> hiddens.addHiddenColumn(ncol));
      }
   }

   /**
    * Transform partitions(include additional partitions) when table option changed.
    * @param info
    */
   private void transformPartitions(ChangeTableOptionInfo info, DefaultMetaDataProvider metadata,
                                    String additional)
   {
      try {
         XDataModel model = getDataModel(info.getSource());

         if(model == null) {
            LOG.error(catalog.getString("notFind.dataModel", info.getSource()));
            return;
         }

         for(String pname : model.getPartitionNames()) {
            XPartition partition = model.getPartition(pname);

            if(additional != null) {
               partition = partition.getPartition(additional);
            }

            transformPartition(partition, info, metadata, additional);
            model.addPartition(partition);
         }
      }
      catch(Exception exc) {
         LOG.error("Failed to update table name option", exc);
      }
   }

   /**
    * Transform the target partition table names when table option changed.
    */
   private void transformPartition(XPartition partition, ChangeTableOptionInfo info,
                                   DefaultMetaDataProvider metadata, String additional)
   {
      if(partition == null) {
         return;
      }

      List<RenameInfo> infos = new ArrayList<>();
      transformPartitionTable(partition, info, infos, metadata, additional);
      transformLogicalModel(partition, infos, additional);
   }

   /**
    * 1. transform logical models which depends on the partition
    * 2. transform dependencies depends on the logical model.
    *
    * @param partition the target partition.
    * @param infos     the RenameInfo list which created when transform partition tables.
    */
   private void transformLogicalModel(XPartition partition, List<RenameInfo> infos, String additional) {
      try {
         XDataModel model = partition.getDataModel();

         for(String name : model.getLogicalModelNames()) {
            XLogicalModel lm = model.getLogicalModel(name);
            transformLMEntity(lm, partition, infos, additional);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to update table name option", e);
      }
   }

   private void transformLMEntity(XLogicalModel lm, XPartition partition,
                                  List<RenameInfo> pinfos, String additional)
   {
      String partitionName = lm.getPartition();

      if(additional != null) {
         lm = lm.getLogicalModel(additional);
      }

      if(lm == null) {
         return;
      }

      Enumeration<XEntity> entities = lm.getEntities();

      if(additional == null && !Objects.equals(partition.getName(), partitionName)
         || additional != null && !Objects.equals(additional, partition.getName()))
      {
         return;
      }

      List<RenameInfo> lmInfos = new ArrayList<>();

      while(entities.hasMoreElements()) {
         XEntity entity = entities.nextElement();
         Enumeration<XAttribute> attributes = entity.getAttributes();

         while(attributes.hasMoreElements()) {
            XAttribute attribute = attributes.nextElement();
            String table = attribute.getTable();

            if(partition.getAutoAliasTable(table) != null) {
               continue;
            }

            if(attribute instanceof ExpressionAttribute) {
               transformExpressionAttribute((ExpressionAttribute) attribute, pinfos);
               continue;
            }

            for(RenameInfo rinfo : pinfos) {
               if(Objects.equals(rinfo.getOldName(), table) &&
                  !Objects.equals(table, rinfo.getNewName()))
               {
                  attribute.setTable(rinfo.getNewName());
               }
            }
         }

         for(RenameInfo rinfo : pinfos) {
            if(Objects.equals(rinfo.getOldName(), entity.getName()) &&
               !Objects.equals(entity.getName(), rinfo.getNewName()))
            {
               entity.setName(rinfo.getNewName());
               RenameInfo renameInfo = new RenameInfo(entity.getName(), rinfo.getNewName(),
                  RenameInfo.LOGIC_MODEL | RenameInfo.TABLE);

               if(!lmInfos.contains(renameInfo)) {
                  lmInfos.add(renameInfo);
               }
            }
         }
      }

      updateLogicalModel(lm);

      if(additional == null) {
         transformDependencies(lm, lmInfos);
      }
   }

   /**
    * Add the RenameInfos to RenameDependencyInfo.
    *
    * @param lm       the target logical.
    * @param infos     the RenameInfos created by transform attribute names.
    */
   private void transformDependencies(XLogicalModel lm, List<RenameInfo> infos) {
      if(infos.size() == 0) {
         return;
      }

      List<AssetObject> entries = DependencyTransformer.getModelDependencies(lm.getName());

      if(entries == null) {
         return;
      }

      RenameDependencyInfo dinfo = new RenameDependencyInfo();

      for(AssetObject entry : entries) {
         for(RenameInfo rinfo : infos) {
            dinfo.addRenameInfo(entry, rinfo);
         }
      }

      RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
   }

   private void updateLogicalModel(XLogicalModel lm) {
      String path = null;
      AssetEntry.Type type = null;

      if(lm.getBaseModel() != null) {
         path = lm.getDataSource() + "/" + lm.getBaseModel().getName() + "/" + lm.getName();
         type = AssetEntry.Type.EXTENDED_LOGIC_MODEL;
      }
      else {
         path = lm.getDataSource() + "/" + lm.getName();
         type = AssetEntry.Type.LOGIC_MODEL;
      }

      DataSourceRegistry.getRegistry().updateObject(path, path, type, lm);
   }

   private void transformExpressionAttribute(ExpressionAttribute attribute, List<RenameInfo> pinfos) {
      String exp = attribute.getExpression();
      int start = 0;
      int end = 0;

      while((start = exp.indexOf("field['", end)) != -1) {
         end = exp.length() <= start + 7 ? -1 : exp.indexOf("']", start + 7);

         if(end == -1) {
            break;
         }

         if(start + 7 > end) {
            break;
         }

         String name = exp.substring(start + 7, end);
         String nname = UniformSQL.fixFieldName(name, pinfos);
         exp = exp.replace(name, nname);
      }

      attribute.setExpression(exp);
   }

   private void transformPartitionTable(XPartition partition, ChangeTableOptionInfo info,
                                        List<RenameInfo> infos, DefaultMetaDataProvider metadata,
                                        String additional)
   {
      Enumeration<XPartition.PartitionTable> e = partition.getTables(true);
      Set<XPartition.PartitionTable> tables = new HashSet<>();

      while(e.hasMoreElements()) {
         tables.add(e.nextElement());
      }

      for(XPartition.PartitionTable table : tables) {
         String tableName = table.getName();
         String tableAlias = partition.getAliasTable(tableName, true);
         AutoAlias autoAlias = partition.getAutoAlias(tableName);

         if(autoAlias != null) {
            for(int i = 0; i < autoAlias.getIncomingJoinCount(); i++) {
               AutoAlias.IncomingJoin join = autoAlias.getIncomingJoin(i);
               String sourceTable = join.getSourceTable();
               transformPartitionTable0(partition, sourceTable, info, infos, metadata, additional);
            }
         }

         if(tableAlias == null || tableAlias.equals(tableName)) {
            transformPartitionTable0(partition, tableName, info, infos, metadata, additional);
         }
      }
   }

   private void transformPartitionTable0(XPartition partition, String tableName,
                                         ChangeTableOptionInfo info, List<RenameInfo> infos,
                                         DefaultMetaDataProvider metadata, String additional)
   {
      try {
         XNode node = metadata.getTable(tableName, additional, false);
         Object catalogVal = node == null ? null : node.getAttribute("catalog");
         Object schemaVal = node == null ? null : node.getAttribute("schema");
         String catalog = catalogVal == null ? null : catalogVal + "";
         String schema = schemaVal == null ? null : schemaVal + "";

         String ntableName = getQualifiedTableName(tableName, info.getNewOption(), metadata, additional);

         if(catalog != null) {
            partition.renameTable(tableName, ntableName, catalog, schema);
         }

         RenameInfo rinfo = new RenameInfo(tableName, ntableName, RenameInfo.PHYSICAL_TABLE);

         if(!infos.contains(rinfo)) {
            infos.add(rinfo);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get table", e);
      }
   }

   public static XNode getTable(DefaultMetaDataProvider provider, String table, String additional) {
      try {
         return provider.getTable(table, additional, false);
      }
      catch(Exception e) {
         LOG.error("Failed to get table", e);
      }

      return null;
   }

   /**
    * get data model
    * @param source
    * @return
    */
   private XDataModel getDataModel(String source) {
      try {
         return getRepository().getDataModel(source);
      }
      catch(RemoteException e) {
         LOG.error(e.getMessage(), e);
      }

      return null;
   }

   /**
    * Get the fully qualified table name from for physical table.
    */
   public static String getQualifiedTableName(String tableName, int tableOption,
                                              DefaultMetaDataProvider metadata, String additional)
   {
      if(tableName == null) {
         return tableName;
      }

      XDataSource dx = metadata.getDataSource();

      if(dx == null || !(dx instanceof JDBCDataSource)) {
         LOG.error("The transform for physical table name only be doen for jdbc datasource!");
      }

      JDBCDataSource dataSource = (JDBCDataSource) dx.clone();
      dataSource.setTableNameOption(tableOption);

      try {
         // 1. if table name has catalog and schema, this metadata.getTable function will compare
         // each schema and catalog to find the right table node.
         // 2. the table name has no catalog and schema, this metadata.getTable function will
         // just find the from in the last schema.
         XNode node = metadata.getTable(tableName, additional, false);
         return SQLTypes.getSQLTypes(dataSource).getQualifiedName(node, dataSource);
      }
      catch(Exception ex) {
         LOG.error("Failed to get qualified name for the physical table: " + tableName);
      }

      return tableName;
   }

   private AssetEntry entry;
   private Catalog catalog = Catalog.getCatalog();
   private static final Logger LOG = LoggerFactory.getLogger(DataDependencyTransformer.class);
}

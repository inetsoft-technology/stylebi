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
package inetsoft.uql.util;

import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.uql.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.jdbc.util.XMetaDataNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.rgraph.TableNode;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.rmi.RemoteException;
import java.util.List;
import java.util.*;

/**
 * Base implementation of <tt>MetaDataProvider</tt>.
 */
public class DefaultMetaDataProvider implements MetaDataProvider {
   /**
    * Creates a new instance of <tt>DefaultMetaDataProvider</tt>.
    */
   public DefaultMetaDataProvider() {
      try {
         this.session = XFactory.getRepository().bind(
            System.getProperty("user.name"));
      }
      catch(RemoteException exc) {
         LOG.error(exc.getMessage(), exc);
      }
   }

   /**
    * Get the data model being edited.
    *
    * @return the data model being edited.
    */
   public XDataModel getDataModel() {
      return model;
   }

   /**
    * Set the data model to edit.
    *
    * @param model the data model to edit.
    */
   public void setDataModel(XDataModel model) {
      this.model = model;
   }

   /**
    * Set the XBuilder session used to connect to the data source.
    *
    * @param session an XBuilder session object.
    */
   public void setSession(Object session) {
      this.session = session;
   }

   /**
    * Get the XBuilder session used to connect to the data source.
    *
    * @return an XBuilder session object.
    */
   public Object getSession() {
      return session;
   }

   /**
    * Set if this meta data provider is just for portal data model.
    * @param portalData
    */
   public void setPortalData(boolean portalData) {
      this.portalData = portalData;
   }

   /**
    * Gets the requested meta-data.
    *
    * @param query the meta-data query.
    * @param clone <tt>true</tt> to clone the results.
    *
    * @return the meta-data.
    *
    * @throws Exception if an unhandled error occurs.
    */
   public XNode getMetaData(XNode query, boolean clone) throws Exception {
      return getMetaData(
         listener, getRepository(query), getSession(), getDataSource(), query,
         clone);
   }

   /**
    * Get meta data.
    */
   protected XNode getMetaData(XRepository.MetaDataListener listener,
                               XRepository repository, Object session,
                               XDataSource xds, XNode mtype, boolean clone)
      throws Exception
   {
      XNode meta;

      if(mtype != null && portalData) {
         mtype.setAttribute(XUtil.PORTAL_DATA, "true");
      }

      if(listener != null) {
         listener.start(xds, "");
         meta = repository.getMetaData(session, xds, mtype, clone, listener);
         listener.end(xds);
      }
      else {
         meta = repository.getMetaData(session, xds, mtype, clone, null);
      }

      return meta;
   }

   /**
    * Set the datasource associated with this property pane.
    */
   public void setDataSource(XDataSource xds) {
      this.xds = xds;
   }

   /**
    * Get the datasource associated with this property pane.
    */
   @Override
   public XDataSource getDataSource() {
      if(xds == null && getDataModel() != null) {
         try {
            String ds = getDataModel().getDataSource();
            xds = XFactory.getRepository().getDataSource(ds);
         }
         catch(RemoteException exc) {
            LOG.error(exc.getMessage(), exc);
         }
      }

      return xds;
   }

   /**
    * Gets the node for the specified table.
    *
    * @param catalog the name of the catalog that contains the table.
    * @param schema  the name of the schema that contains the table.
    * @param table   the name of the table.
    *
    * @return the table node or <tt>null</tt> if it is not found.
    *
    * @throws Exception if an unhandled error occurs.
    */
   public XNode getTable(String catalog, String schema, String table, boolean strict)
      throws Exception
   {
      return getTable(catalog, schema, table, null, strict);
   }

   /**
    * Gets the node for the specified table.
    *
    * @param catalog the name of the catalog that contains the table.
    * @param schema  the name of the schema that contains the table.
    * @param table   the name of the table.
    *
    * @return the table node or <tt>null</tt> if it is not found.
    *
    * @throws Exception if an unhandled error occurs.
    */
   public XNode getTable(String catalog, String schema, String table, String additional, boolean strict)
      throws Exception
   {
      synchronized(tableCache) {
         TableCacheKey key = new TableCacheKey(catalog, schema, table);

         if(tableCache.containsKey(key)) {
            return tableCache.get(key);
         }

         if(schemaList == null) {
            XNode query = new XNode();
            query.setAttribute("type", "SCHEMAS");
            query.setAttribute(XUtil.PORTAL_DATA, portalData + "");

            if(!StringUtils.isEmpty(additional)) {
               query.setAttribute("additional", additional);
            }

            schemaList = getMetaData(query, true);
         }

         XNode schemas = (XNode) schemaList.clone();
         XNode parent = schemas;

         if(catalog != null) {
            for(int i = 0; i < parent.getChildCount(); i++) {
               XNode node = parent.getChild(i);

               if(node.getName().equals(catalog)) {
                  parent = node;
                  break;
               }
            }

            if(parent == schemas) {
               LOG.warn("Catalog \"" + catalog + "\" not found");
               tableCache.put(key, null);
               return null;
            }
         }

         if(schema != null) {
            XNode oldParent = parent;

            for(int i = 0; i < parent.getChildCount(); i++) {
               XNode node = parent.getChild(i);

               if(node.getName().equals(schema)) {
                  parent = node;
                  break;
               }
            }

            if(parent == oldParent) {
               LOG.warn("Schema \"" + schema + "\" not found");
               tableCache.put(key, null);
               return null;
            }
         }

         XNode result = findTable(table, Collections.singletonList(parent), additional, strict);
         tableCache.put(key, result);
         return result;
      }
   }

   public XNode getTable(String table, String additional) throws Exception {
      return getTable(table, additional, true);
   }

   /**
    * Gets the node for the specified table in a additional connection.
    *
    * @param table the name of the table.
    * @param additional additional connection name.
    *
    * @return the table node or <tt>null</tt> if it is not found.
    *
    * @throws Exception if an unhandled error occurs.
    */
   public XNode getTable(String table, String additional, boolean strict) throws Exception {
      synchronized(tableCache) {
         TableCacheKey key = new TableCacheKey(null, null, table);
         tableCache.clear();

         if(tableCache.containsKey(key)) {
            return tableCache.get(key);
         }

         if(schemaList == null) {
            XNode query = new XNode();
            query.setAttribute("type", "SCHEMAS");

            if(!StringUtils.isEmpty(additional)) {
               query.setAttribute("additional", additional);
            }

            schemaList = getMetaData(query, true);
         }

         XNode schemas = (XNode) schemaList.clone();
         SQLHelper helper;

         if(portalData) {
            // don't get additional by user.
            helper = SQLHelper.getSQLHelper(xds, additional, null);
         }
         else {
            helper = SQLHelper.getSQLHelper(xds);
         }

         String quote = helper.getQuote();
         String separator = Objects.toString(schemas.getAttribute("catalogSep"), ".");

         String qName = table;

         Deque<XNode> path = new ArrayDeque<>();
         path.addLast(schemas);

         boolean catalogUsed = false;
         boolean userUsed = false;

         while(path.getLast().getChildCount() > 0) {
            if(path.size() > 1) {
               separator = ".";
            }

            boolean found = false;

            for(int i = 0; i < path.getLast().getChildCount(); i++) {
               XNode child = path.getLast().getChild(i);
               String prefix = child.getName() + separator;

               if(qName.startsWith(prefix)) {
                  path.add(child);
                  found = true;
                  qName = qName.substring(prefix.length());
                  break;
               }

               prefix = quote + child.getName() + quote + separator;

               if(qName.startsWith(prefix)) {
                  path.add(child);
                  found = true;
                  qName = qName.substring(prefix.length());
                  break;
               }
            }

            if(!found && !catalogUsed) {
               catalogUsed = true;
               String catalog = (String) schemas.getAttribute("defaultCatalog");

               if(catalog == null) {
                  catalog = ((JDBCDataSource) getDataSource()).getDefaultDatabase();
               }

               found = findInPath(catalog, path);
            }

            if(!found && !userUsed) {
               userUsed = true;
               String user = ((JDBCDataSource) getDataSource()).getUser();
               found = findInPath(user, path);
            }

            if(!found) {
               path = null;
               break;
            }
         }

         List<XNode> possibleSchemas = new ArrayList<>();

         if(path == null) {
            // not found yet
            int depth = 0;
            path = new ArrayDeque<>();
            path.addLast(schemas);

            while(!path.isEmpty()) {
               XNode node = path.removeLast();
               ++depth;

               if(node.getChildCount() == 0) {
                  // leaf
                  break;
               }
               else {
                  for(int i = 0; i < node.getChildCount(); i++) {
                     path.addLast(node.getChild(i));
                  }
               }
            }

            if(depth == 3) {
               // schema tree contains both catalog and schema

               // check if qualified name is catalog.table
               for(int i = 0; i < schemas.getChildCount(); i++) {
                  XNode node = schemas.getChild(i);
                  String catalog = node.getName();

                  if(table.startsWith(catalog + ".")) {
                     // search all schemas in the matching catalog
                     for(int j = 0; j < node.getChildCount(); j++) {
                        possibleSchemas.add(node.getChild(j));
                     }

                     break;
                  }
               }

               if(possibleSchemas.isEmpty()) {
                  // check if qualified name is schema.table
                  for(int i = 0; i < schemas.getChildCount(); i++) {
                     XNode catalogNode = schemas.getChild(i);

                     for(int j = 0; j < catalogNode.getChildCount(); j++) {
                        XNode schemaNode = catalogNode.getChild(j);
                        String schema = schemaNode.getName();

                        if(table.startsWith(schema + ".")) {
                           // search all matching schemas
                           possibleSchemas.add(schemaNode);
                           break;
                        }
                     }
                  }
               }
            }

            if(possibleSchemas.isEmpty()) {
               // search all tables
               LOG.warn(
                  "Could not determine path for table: " + table +
                     ", searching all tables");
               XNode node = new XNode();

               // for db transform when table option changed, if find no schema,
               // but the db support catalog, then find the table from default catalog.
               if(!strict &&
                  "true".equalsIgnoreCase(schemas.getAttribute("supportCatalog") + "")
                  && schemas.getAttribute("defaultCatalog") != null)
               {
                  node.setAttribute("supportCatalog", "true");
                  node.setAttribute("catalog", schemas.getAttribute("defaultCatalog"));
               }
               else {
                  node.setAttribute("supportCatalog", "false");
               }

               possibleSchemas.add(node);
            }
         }
         else {
            possibleSchemas.add(path.getLast());
         }

         XNode result = findTable(table, possibleSchemas, additional, strict);

         if(!AssetDataCache.isDebugData()) {
            tableCache.put(key, result);
         }

         return result;
      }
   }

   /**
    * Gets the node for the specified table.
    *
    * @param table the name of the table.
    *
    * @return the table node or <tt>null</tt> if it is not found.
    *
    * @throws Exception if an unhandled error occurs.
    */
   public XNode getTable(String table) throws Exception {
      return getTable(table, null);
   }

   private boolean findInPath(String name, Deque<XNode> path) {
      boolean found = false;

      if(name != null) {
         for(int i = 0; i < path.getLast().getChildCount(); i++) {
            XNode child = path.getLast().getChild(i);

            if(child.getName().equalsIgnoreCase(name)) {
               path.add(child);
               found = true;
               break;
            }
         }
      }

      return found;
   }

   /**
    * Finds the meta-data node for a table.
    *
    * @param table   the qualified name of the table.
    * @param schemas the schemas that may contain the table.
    *
    * @return the table node or <tt>null</tt> if it is not found.
    *
    * @throws Exception if an unhandled error occurs.
    */
   private XNode findTable(String table, List<XNode> schemas) throws Exception {
      return findTable(table, schemas, null, true);
   }

   /**
    * Finds the meta-data node for a table.
    *
    * @param table   the qualified name of the table.
    * @param schemas the schemas that may contain the table.
    *
    * @return the table node or <tt>null</tt> if it is not found.
    *
    * @throws Exception if an unhandled error occurs.
    */
   private XNode findTable(String table, List<XNode> schemas, String additional, boolean strict)
      throws Exception
   {
      XNode node = findTable(table, schemas, additional, false, strict);

      if(node == null) {
         node = findTable(table, schemas, additional, true, strict);
      }

      return node;
   }

   /**
    * Finds the meta-data node for a table.
    *
    * @param table   the qualified name of the table.
    * @param schemas the schemas that may contain the table.
    *
    * @return the table node or <tt>null</tt> if it is not found.
    *
    * @throws Exception if an unhandled error occurs.
    */
   private XNode findTable(String table, List<XNode> schemas, String additional,
                           boolean ignoreQuote, boolean strict)
      throws Exception
   {
      XNode tableTypes = (XNode) getTableTypeList(additional).clone();

      for(XNode schema : schemas) {
         for(int i = 0; i < tableTypes.getChildCount(); i++) {
            XNode type = tableTypes.getChild(i);
            SchemaCacheKey cacheKey = new SchemaCacheKey(
               (String) schema.getAttribute("catalog"),
               (String) schema.getAttribute("schema"), type.getName());
            XNode tableList = schemaCache.get(cacheKey);

            if(tableList == null) {
               XNode query = new XNode();

               if(type.getName().equals("PROCEDURE")) {
                  query.setAttribute("type", "SCHEMAPROCEDURES");
               }
               else {
                  query.setAttribute("type", "SCHEMATABLES_" + type.getName());
                  query.setAttribute("tableType", type.getName());
               }

               copyAttribute(schema, query, "supportCatalog");
               copyAttribute(schema, query, "catalog");
               copyAttribute(schema, query, "catalogSep");
               copyAttribute(schema, query, "schema");

               if(!StringUtils.isEmpty(additional)) {
                  query.setAttribute(XUtil.DATASOURCE_ADDITIONAL, additional);
               }

               tableList = getMetaData(query, true);
               schemaCache.put(cacheKey, tableList);
            }

            XNode tables = (XNode) tableList.clone();

            for(int j = 0; j < tables.getChildCount(); j++) {
               XNode node = tables.getChild(j);
               Object oldFixQuote = null;

               if(ignoreQuote) {
                  oldFixQuote = node.getAttribute("fixquote");
                  node.setAttribute("fixquote", "false");
               }

               String qName = XAgent.getAgent(getDataSource())
                  .getQualifiedName(node, getDataSource());
               String qName2 = null;

               if(getDataSource() instanceof JDBCDataSource) {
                  XDataSource base =
                     ((JDBCDataSource) getDataSource()).getBaseDatasource();

                  if(base != null) {
                     qName2 = XAgent.getAgent(base).getQualifiedName(node, base);
                  }
               }

               if(ignoreQuote) {
                  node.setAttribute("fixquote", oldFixQuote);
               }

               String tableName = table.lastIndexOf(".") != -1 ?
                  table.substring(table.lastIndexOf(".") + 1) : table;

               if(qName.equals(table) || qName2 != null && qName2.equals(table)
                  || !strict && Objects.equals(node.getName(), tableName))
               {
                  if(schema.getParent() == null) {
                     // no catalog or schema
                     type.addChild(node);
                  }
                  else if(schema.getParent().getParent() == null) {
                     // just catalog or schema
                     type.addChild(schema);
                     schema.addChild(node);
                  }
                  else {
                     // both catalog and schema
                     type.addChild(schema.getParent());
                     schema.addChild(node);
                  }

                  return node;
               }
            }
         }
      }

      LOG.debug("Failed to find table node: " + table);
      return null;
   }

   /**
    * Get the repository, connected to the current database.
    */
   protected XRepository getRepository(XNode query) throws Exception {
      XRepository repository = XFactory.getRepository();
      XDataSource xds = getDataSource();

      if(query != null && !StringUtils.isEmpty((String) query.getAttribute("additional"))) {
         xds.setFromPortal(true);
      }

      repository.connect(session, xds, getRepositoryParameters());
      return repository;
   }

   /**
    * Get the repository, connected to the current database.
    */
   protected XRepository getRepository() throws Exception {
      return getRepository(null);
   }

   protected VariableTable getRepositoryParameters() throws Exception {
      return null;
   }

   protected Component getOwner() {
      return null;
   }

   /**
    * Get the primary keys of a table.
    */
   @Override
   public XNode getPrimaryKeys(XNode table) throws Exception {
      if(table == null) {
         return new XNode();
      }

      // find all primary keys for the table
      XNode mType = (XNode) table.clone();
      mType.setAttribute("type", "PRIMARYKEY");

      return getMetaData(mType, true);
   }

   /**
    * Get the metadata for the specified partition.
    *
    * @param partition the name of the partition.
    *
    * @param sortChildren
    * @return a XNode containing the metadata of the tables in the partition.
    */
   public synchronized XNode getMetaData(String partition, boolean sortChildren) throws Exception {
      return getMetaData(partition, sortChildren, null);
   }

   /**
    * Get the metadata for the specified partition.
    *
    * @param partition the name of the partition.
    *
    * @param sortChildren
    * @return a XNode containing the metadata of the tables in the partition.
    */
   public synchronized XNode getMetaData(String partition, boolean sortChildren, String additional)
      throws Exception
   {
      if(metadataCache == null) {
         metadataCache = new HashMap<>();
      }

      String key = getDataModel().getDataSource() + "::" + partition;
      XNode metadata = metadataCache.get(key);

      if(metadata == null) {
         refreshMetaData(partition, sortChildren, additional);
         metadata = metadataCache.get(key);
      }

      return metadata;
   }

   /**
    * Get the metadata for the specified partition.
    *
    * @param partition the partition.
    *
    * @return a XNode containing the metadata of the tables in the partition.
    */
   public XNode getMetaData(XPartition partition) throws Exception {
      return getMetaData(partition, true);
   }

   /**
    * Get the metadata for the specified partition.
    *
    * @param partition the partition.
    * @param sortChildren Whether to sort MetaData child.
    *
    * @return a XNode containing the metadata of the tables in the partition.
    */
   public XNode getMetaData(XPartition partition, boolean sortChildren)
      throws Exception
   {
      return getMetaData(partition, sortChildren, null);
   }

   /**
    * Get the metadata for the specified partition.
    *
    * @param partition the partition.
    * @param sortChildren Whether to sort MetaData child.
    *
    * @return a XNode containing the metadata of the tables in the partition.
    */
   public XNode getMetaData(XPartition partition, boolean sortChildren, String additional)
      throws Exception
   {
      if(partition == null) {
         return null;
      }

      // when displaying physical tables for entity mapping, we want to
      // show the auto aliases already applied
      partition = partition.applyAutoAliases();

      XDataSource xds = getDataSource();
      XNode tableTypes = (XNode) getTableTypeList(additional).clone();

      if(schemaList == null) {
         XNode query = new XNode();
         query.setAttribute("type", "SCHEMAS");
         query.setAttribute("additional", additional);
         schemaList = getMetaData(query, true);
      }

      XNode schemas = (XNode) schemaList.clone();
      Map<String, XNode> tableMap = new HashMap<>();
      Enumeration<XPartition.PartitionTable> e = partition.getTables();

      while(e.hasMoreElements()) {
         XPartition.PartitionTable table = e.nextElement();
         String tableName = table.getName();
         String tableAlias = partition.getAliasTable(tableName, true);
         tableAlias = tableAlias == null ? tableName : tableAlias;
         XNode parent = null;

         if(table.getCatalog() != null) {
            parent = schemas.getChild(table.getCatalog().toString());
         }

         String schema;
         XNode tableNode = null;

         if(table.getSchema() != null) {
            schema = table.getSchema().toString();
            tableNode = findTableNode(schema, tableAlias, schemas,
                                      parent, tableTypes, false, additional);
         }
         else if("true".equals(schemas.getAttribute("hasSchema"))) {
            schema = ((JDBCDataSource) xds).getUser();

            // @by davyc, for version before 11.2, XPartition.PartitionTables
            // no catalog and schema node, so here we need try to find it from
            // table name
            // fix bug1374046651127
            tableNode = getTableNode(table, xds, tableAlias, schemas, parent,
                                     tableTypes, schema, additional);
         }

         if(tableNode == null) {
            tableNode = getTableNode(table, xds, tableAlias, schemas, parent,
                                     tableTypes, null, additional);
         }

         if(tableNode == null) {
            XPartition.PartitionTable tbl = partition.getPartitionTable(tableAlias);

            if(tbl == null) {
               LOG.warn("Could not find table: " + tableAlias);
               continue;
            }
         }

         if(tableNode == null) {
            continue;
         }

         tableMap.put(tableAlias, tableNode);

         // @by vincentx, 2004-08-19, fix bug1092623278371
         // commented the above getColumns method, use the
         // JDBCUtil.getTableColumns instead
         XTypeNode cols = JDBCUtil.getTableColumns(
            tableNode, getRepository(), getSession(), xds);

         for(int i = 0; i < cols.getChildCount(); i++) {
            if(tableNode.getChild(cols.getChild(i).getName()) == null) {
               tableNode.addChild(cols.getChild(i), sortChildren);
            }
         }
      }

      // add table and aliases
      e = partition.getTables();
      XNode pmeta = new XNode();

      while(e.hasMoreElements()) {
         XPartition.PartitionTable temp = e.nextElement();
         String name = temp.getName();
         int type = temp.getType();
         String table = partition.getAliasTable(name, true);

         // auto alias already expended here so we should not add to tree
         if(partition.getAutoAlias(name) != null) {
            continue;
         }

         // if not alias, the name is the real table name
         if(table == null) {
            table = name;
         }

         XNode tnode;

         if(type == inetsoft.uql.erm.PartitionTable.PHYSICAL) {
            tnode = tableMap.get(table);

            if(tnode != null) {
               tnode = (XNode) tnode.clone();
               tnode.setName(name);

               if(!table.equals(name)) { // is not a real table
                  tnode.setAttribute("isAliasView", true);
                  tnode.setAttribute("entity", name);
               }

               pmeta.addChild(tnode);
            }
         }
         else {
            TableNode tableNode = temp.getTableNode();

            if(tableNode != null) {
               XMetaDataNode xMetaNode = new XMetaDataNode();
               xMetaNode.setName(temp.getName());
               tableNode.copyColumnsInto(xMetaNode);
               pmeta.addChild(xMetaNode);
            }
         }
      }

      return pmeta;
   }

   private XNode getTableTypeList() throws Exception {
      return getTableTypeList(null);
   }

   private XNode getTableTypeList(String additional) throws Exception {
      if(tableTypeList == null) {
         XNode query = new XNode();
         query.setAttribute("type", "TABLETYPES");

         if(!StringUtils.isEmpty(additional)) {
            query.setAttribute(XUtil.DATASOURCE_ADDITIONAL, additional);
         }

         tableTypeList = getMetaData(query, true);
         tableTypeList.addChild(new XMetaDataNode("PROCEDURE"));
      }

      return tableTypeList;
   }

   private XNode getTableNode(XPartition.PartitionTable table, XDataSource xds,
                              String tableAlias, XNode schemas, XNode parent, XNode tableTypes,
                              String schema, String additional)
      throws Exception
   {
      XNode tableNode = null;

      if(table.getCatalog() == null) {
         String temp = findSchema(xds, tableAlias);

         if(temp != null) {
            tableNode = findTableNode(temp, tableAlias, schemas, parent,
                                      tableTypes, true, additional);
         }
      }

      if(tableNode == null) {
         tableNode = findTableNode(schema, tableAlias, schemas,
                                   parent, tableTypes, false, additional);
      }

      return tableNode;
   }

   private String findSchema(XDataSource xds, String tableName) {
      int dot = tableName.lastIndexOf(".");

      if(dot < 0) {
         return null;
      }

      return xds.getFullName() + "." + tableName.substring(0, dot);
   }

   private XNode findTableNode(String schema, String tableAlias, XNode schemas,
                               XNode parent, XNode tableTypes, boolean checkPath,
                               String additional)
      throws Exception
   {
      if(schema != null) {
         XNode root = parent == null ? schemas : parent;
         parent = findParent(root, schema, checkPath);
      }

      XNode tableNode = null;

      XNode query = new XNode();
      copyAttribute(parent, query, "supportCatalog");
      copyAttribute(parent, query, "catalog");
      copyAttribute(parent, query, "catalogSep");
      copyAttribute(parent, query, "schema");
      query.setAttribute("additional", additional);

      for(int i = 0; i < tableTypes.getChildCount(); i++) {
         String tableType = tableTypes.getChild(i).getName();

         if("PROCEDURE".equals(tableType)) {
            query.setAttribute("type", "SCHEMAPROCEDURES");
         }
         else {
            query.setAttribute("type", "SCHEMATABLES_" + tableType);
            query.setAttribute("tableType", tableType);
         }

         XNode tables = getMetaData(query, true);

         for(int j = 0; j < tables.getChildCount(); j++) {
            XNode node = tables.getChild(j);
            node.setAttribute("additional", additional);
            String qName = XAgent.getAgent(xds).getQualifiedName(node, xds);

            if(qName.equals(tableAlias)) {
               tableNode = node;
               break;
            }
         }

         if(tableNode != null) {
            break;
         }
      }

      return tableNode;
   }

   private XNode findParent(XNode root, String schema, boolean checkPath) {
      XNode parent = null;

      for(int i = 0; i < root.getChildCount(); i++) {
         XNode child = root.getChild(i);

         if(checkPath && schema.equalsIgnoreCase(child.getPath()) ||
            !checkPath && schema.equalsIgnoreCase(child.getName()))
         {
            parent = child;
            break;
         }
         else if(child.getChildCount() > 0) {
            parent = findParent(child, schema, checkPath);

            if(parent != null) {
               return parent;
            }
         }
      }

      return parent;
   }

   /**
    * Copies an attribute from one node to another, if it exists.
    *
    * @param from the source node.
    * @param to   the target node.
    * @param name the attribute name.
    */
   private void copyAttribute(XNode from, XNode to, String name) {
      if(from != null) {
         Object value = from.getAttribute(name);

         if(value != null) {
            to.setAttribute(name, value);
         }
      }
   }

   /**
    * Set meta data listener.
    */
   public void setMetaDataListener(XRepository.MetaDataListener listener) {
      this.listener = listener;
   }

   /**
    * Get meta data listener.
    */
   public XRepository.MetaDataListener getMetaDataListener() {
      return listener;
   }

   /**
    * Synchronize the metadata cached in this pane with the metadata from the
    * data source.
    */
   public synchronized void refreshMetaData() throws Exception {
      tableMetadata.clear();
   }

   /**
    * Create a table node and populate the column list.
    */
   @Override
   public TableNode getTableMetaData(XNode table) {
      TableNode tnode = tableMetadata.get(table);

      if(tnode == null) {
         synchronized(this) {
            if((tnode = tableMetadata.get(table)) == null) {
               try {
                  XDataSource xds = getDataSource();
                  XNode tmeta = getMetaData(table, true);
                  tnode = new TableNode(XAgent.getAgent(xds).getQualifiedName(tmeta, xds));

                  // @by jamshedd extract the prefix to the table name
                  // if any which contains database, schema names
                  String namePrefix = "";

                  if(tnode.getName().contains(".")) {
                     namePrefix = tnode.getName().substring(
                        0, tnode.getName().lastIndexOf(".") + 1);
                  }

                  tmeta = tmeta.getChild(0);

                  String[] cnames = new String[tmeta == null ? 0 : tmeta.getChildCount()];
                  XNode curCol;

                  for(int i = 0; i < cnames.length; i++) {
                     curCol = tmeta.getChild(i);
                     cnames[i] = curCol.getName();
                     String colType = curCol.getClass().getName();
                     int dot = colType.lastIndexOf(".");
                     int tidx = colType.indexOf("Type");

                     if(dot >= 0 && tidx >= 0) {
                        colType = colType.substring(dot + 1, tidx);
                     }
                     else {
                        colType = XSchema.STRING;
                     }

                     tnode.addColumnType(cnames[i], colType);

                     // @by jamshedd store relationship information in the
                     // table node object. This information will be used in the
                     // auto join.
                     List<?> rel = (List<?>) curCol.getAttribute("ForeignKey");

                     if(rel != null && rel.size() > 0) {
                        for(Object relObj : rel) {
                           String[] relArr = (String[]) relObj;

                           if(!relArr[0].startsWith(namePrefix)) {
                              relArr[0] = namePrefix + relArr[0];
                           }
                        }

                        tnode.addForeignKeys(cnames[i], rel);
                     }
                  }

                  // Bug #7092, keep the original order in Partition Table.
                  // Arrays.sort(cnames);

                  for(String cname : cnames) {
                     tnode.addColumn(cname);
                  }

                  tableMetadata.put(table, tnode);
                  tnode.setUserObject(table);
               }
               catch(Exception exc) {
                  LOG.error(exc.getMessage(), exc);
               }
            }
         }
      }

      return tnode;
   }

   /**
    * Synchronize the metadata cached in this pane with the metadata from the
    * data source.
    * @param pname the name of partition.
    */
   public synchronized void refreshMetaData(String pname) throws Exception {
      refreshMetaData(pname, true);
   }

   /**
    * Synchronize the metadata cached in this pane with the metadata from the
    * data source.
    * @param pname the name of partition.
    * @param sortChildren Whether to sort MetaData child.
    */
   public synchronized void refreshMetaData(String pname, boolean sortChildren)
      throws Exception
   {
      refreshMetaData(pname, sortChildren, null);
   }

   /**
    * Synchronize the metadata cached in this pane with the metadata from the
    * data source.
    * @param pname the name of partition.
    * @param sortChildren Whether to sort MetaData child.
    */
   public synchronized void refreshMetaData(String pname, boolean sortChildren, String additional)
      throws Exception
   {
      refreshMetaData();

      if(metadataCache == null) {
         metadataCache = new HashMap<>();
      }

      String[] names = pname.split(":");
      XPartition partition = getDataModel().getPartition(names[0]);

      if(names.length > 1) {
         partition = partition.getPartition(names[1]);
      }

      if(partition == null) {
         return;
      }

      metadataCache.put(getDataModel().getDataSource() + "::" +
         pname, getMetaData(partition, sortChildren, additional));
   }

   private boolean portalData = false;
   private XRepository.MetaDataListener listener;
   private XDataSource xds;
   private XDataModel model;
   private Object session;
   private HashMap<XNode, TableNode> tableMetadata = new HashMap<>();
   private HashMap<String, XNode> metadataCache;
   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultMetaDataProvider.class);

   private XNode schemaList = null;
   private XNode tableTypeList = null;

   private final HashMap<SchemaCacheKey, XNode> schemaCache = new HashMap<>();
   private final HashMap<TableCacheKey, XNode> tableCache = new HashMap<>();

   private static final class SchemaCacheKey {
      public SchemaCacheKey(String catalog, String schema, String type) {
         this.catalog = catalog;
         this.schema = schema;
         this.type = type;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         SchemaCacheKey that = (SchemaCacheKey) o;
         return Tool.equals(catalog, that.catalog) &&
            Tool.equals(schema, that.schema) && Tool.equals(type, that.type);
      }

      @Override
      public int hashCode() {
         int result = catalog != null ? catalog.hashCode() : 0;
         result = 31 * result + (schema != null ? schema.hashCode() : 0);
         result = 31 * result + (type != null ? type.hashCode() : 0);
         return result;
      }

      private final String catalog;
      private final String schema;
      private final String type;
   }

   private static final class TableCacheKey {
      public TableCacheKey(String catalog, String schema, String table) {
         this.catalog = catalog;
         this.schema = schema;
         this.table = table;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         TableCacheKey cacheKey = (TableCacheKey) o;
         return Tool.equals(catalog, cacheKey.catalog) &&
            Tool.equals(schema, cacheKey.schema) &&
            Tool.equals(table, cacheKey.table);
      }

      @Override
      public int hashCode() {
         int result = catalog != null ? catalog.hashCode() : 0;
         result = 31 * result + (schema != null ? schema.hashCode() : 0);
         result = 31 * result + (table != null ? table.hashCode() : 0);
         return result;
      }

      private final String catalog;
      private final String schema;
      private final String table;
   }
}

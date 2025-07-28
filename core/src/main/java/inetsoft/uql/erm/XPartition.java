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
package inetsoft.uql.erm;

import inetsoft.report.XSessionManager;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XHandler;
import inetsoft.uql.util.rgraph.TableNode;
import inetsoft.util.*;
import inetsoft.util.xml.XMLStorage.XMLFragment;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.*;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A partition is a group of related tables.
 *
 * @author  InetSoft Technology Corp.
 * @since   5.0
 */
public class XPartition implements Cloneable, Serializable, XMLSerializable, XMLFragment {
   /**
    * Status flag indicating that no problems exist with the model.
    */
   public static final int VALID = 0;

   /**
    * Status flag indicating that a cycle exists among the relationships of the
    * model.
    */
   public static final int CYCLE = 1;

   /**
    * Status flag indicating that unjoined tables exist in the model.
    */
   public static final int UNJOINED = 2;

   /**
    * Creates a new instance of XPartition. Default constructor should only
    * be used when loading a partition from an XML file.
    */
   public XPartition() {
      this.relationships = new Vector<>();
   }

   /**
    * Creates a new instance of XPartition.
    *
    * @param name the name of the partition.
    */
   public XPartition(String name) {
      this();
      this.name = name;
   }

   /**
    * Creates a new instance of XPartition.
    *
    * @param name the name of the partition.
    * @param base the base partition.
    */
   public XPartition(String name, XPartition base) {
      this(name);
      setBaseParitition(base);
   }

   /**
    * Add a child XPartition.
    */
   public void addPartition(XPartition child, boolean isImport) {
      String path = getDataModel().getDataSource() + "/" + getName() +
              "/" + child.getName();
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.EXTENDED_PARTITION, path, null);
      child.setBaseParitition(this);

      if(!isImport && getRegistry().getObject(entry, true, false) == null) {
         child.setCreated(System.currentTimeMillis());
      }

      if(!isImport) {
         child.setLastModified(System.currentTimeMillis());
      }

      getRegistry().setObject(entry, child);
   }

   /**
    * Check if child XPartition exists.
    */
   public Boolean containPartition(String name) {
      String path = getDataModel().getDataSource() + "/" + getName()
              + "/" + name;
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.EXTENDED_PARTITION, path, null);
      return getRegistry().containObject(entry);
   }

   /**
    * Get child XPartition with specified name.
    */
   public XPartition getPartition(String name) {
      XPartition result = null;

      if(name != null) {
         String path = getDataModel().getDataSource() + "/" + getName() +
                 "/" +name;
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                 AssetEntry.Type.EXTENDED_PARTITION, path, null);
         result = (XPartition) getRegistry().getObject(entry, true);

         if(result != null) {
            result.setBaseParitition(this);
            getRegistry().setCache(entry, result);
         }
      }

      return result;
   }

   /**
    * Get child XPartition by specified connection.
    */
   public XPartition getPartitionByConnection(String connection) {
      if(connection != null) {
         XPartition partiton = getPartition(connection);

         if(partiton != null && partiton.getConnection() != null) {
            return partiton;
         }
      }

      String[] names = getPartitionNames();

      for(String name : names) {
         XPartition partiton = getPartition(name);

         if(partiton != null && partiton.getConnection() == null) {
            return partiton;
         }
      }

      return null;
   }

   /**
    * Rename child partition
    */
   public void renamePartition(String oname, XPartition partition) {
      String oldPath = getDataModel().getDataSource() + "/" + getName() +
              "/" + oname;
      String newPath = getDataModel().getDataSource() + "/" + getName() +
              "/" + partition.getName();
      getRegistry().updateObject(oldPath, newPath,
              AssetEntry.Type.EXTENDED_PARTITION, partition);
   }

   /**
    * Get names of the child partitions.
    */
   public String[] getPartitionNames() {
      String[] result = null;

      try {
         String path = getDataModel().getDataSource() + "/" + getName() + "/";
         AssetEntry[] entries =
                 getRegistry().getEntries(path, AssetEntry.Type.EXTENDED_PARTITION);
         result = new String[entries.length];

         for(int i = 0; i < entries.length; i++) {
            result[i] = entries[i].getPath().substring(path.length());
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get extended partition "
                 + "names of partition: " + getName(), e);
      }

      return result;
   }


   /**
    * remove a child XPartition.
    */
   public void removePartition(String name) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.EXTENDED_PARTITION,
              getDataModel().getDataSource() + "/" + getName() + "/" + name,
              null);
      getRegistry().removeObject(entry);
   }

   /**
    * rename a child XPartition.
    */
   public void renamePartition(String oname, String nname) {
      XPartition p = getPartition(oname);

      if(p != null) {
         p.setName(nname);
         String oldPath = getDataModel().getDataSource() + "/" +
                 getName() + "/" + oname;
         String newPath = getDataModel().getDataSource() + "/" +
                 getName() + "/" + nname;
         getRegistry().updateObject(oldPath, newPath,
                 AssetEntry.Type.EXTENDED_PARTITION, p);
      }
   }

   /**
    * Get the partition connection.
    */
   public String getConnection() {
      return connection;
   }

   /**
    * set the partition connection.
    */
   public void setConnection(String connection) {
      this.connection = connection;
   }

   /**
    * Get the base partition.
    */
   public XPartition getBasePartition() {
      return base;
   }

   /**
    * Check this table is in the base partition.
    */
   public boolean isBaseTable(String table) {
      return base != null && base.containsTable(table, false);
   }

   /**
    * Check this XRelationship is in the base partition.
    */
   public boolean isBaseRelationship(XRelationship replation) {
      return base != null && base.relationships.contains(replation);
   }

   /**
    * Gets the name of this partition.
    *
    * @return the name of this partition.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of this partition.
    *
    * @param name the name of this partition.
    */
   public void setName(String name) {
      removeMetaData();
      this.name = name;
   }

   /**
    * Set include all join property. If include all join is true, then all
    * the joins in the partition will be added to query, no matter how many
    * partition tables is used.
    */
   public void setIncludeAllJoin(boolean all) {
      this.includeAllJoin = all;
   }

   /**
    * Check if include all join.
    */
   public boolean isIncludeAllJoin() {
      return includeAllJoin;
   }

   /**
    * Gets a description of this partition.
    *
    * @return a description of this partition.
    */
   public String getDescription() {
      return base != null ? base.description : description;
   }

   /**
    * Sets the description of this partition.
    *
    * @param description a description of this partition.
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Get created time.
    * @return created time.
    */
   public long getCreated() {
      return created;
   }

   /**
    * Set created time.
    * @param created the specified created time.
    */
   public void setCreated(long created) {
      this.created = created;
   }

   /**
    * Get last modified.
    * @return last modified time.
    */
   public long getLastModified() {
      return modified;
   }

   /**
    * Set last modified time.
    * @param modified the specified last modified time.
    */
   public void setLastModified(long modified) {
      this.modified = modified;
   }

   /**
    * Get the created person.
    * @return the created person.
    */
   public String getCreatedBy() {
      return createdBy;
   }

   /**
    * Set the created person
    * @param createdBy the created person.
    */
   public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
   }

   /**
    * Get last modified person.
    * @return last modified person.
    */
   public String getLastModifiedBy() {
      return modifiedBy;
   }

   /**
    * Set last modified person.
    * @param modifiedBy the specified last modified person.
    */
   public void setLastModifiedBy(String modifiedBy) {
      this.modifiedBy = modifiedBy;
   }

   /**
    * Get data model.
    */
   public XDataModel getDataModel() {
      return base != null ? base.model : model;
   }

   /**
    * Set data model.
    */
   public void setDataModel(XDataModel model) {
      this.model = model;
   }

   /**
    * Gets a list of the tables in this partition.
    * @return an Iterator of table names.
    */
   public Enumeration<PartitionTable> getTables() {
      return getTables(false);
   }

   /**
    * Gets a list of the tables in this partition.
    * @return an Iterator of tables.
    */
   public Enumeration<PartitionTable> getTables(boolean self) {
      final Enumeration<PartitionTable> enu1 =
         !self && base != null ? base.tables.keys() : null;
      final Enumeration<PartitionTable> enu2 = tables.keys();
      final Set<PartitionTable> tset = new ObjectOpenHashSet<>();

      return new Enumeration<PartitionTable>() {
         PartitionTable table;

         @Override
         public boolean hasMoreElements() {
            while(true) {
               table = null;

               if(enu1 != null && enu1.hasMoreElements()) {
                  table = enu1.nextElement();
               }
               else if(enu2.hasMoreElements()) {
                  table = enu2.nextElement();
               }

               if(table != null && !tset.contains(table)) {
                  tset.add(table);
                  return true;
               }

               if(table == null) {
                  return false;
               }
            }
         }

         @Override
         public PartitionTable nextElement() {
            return table;
         }
      };
   }

   /**
    * Get the number of tables in this partition.
    * @return the number of tables in this partition.
    */
   public int getTableCount() {
      HashSet<String> set = new HashSet<>();
      Enumeration<PartitionTable> enu = tables.keys();

      while(enu.hasMoreElements()) {
         PartitionTable temp = enu.nextElement();
         set.add(temp.getName());
      }

      if(base != null) {
         enu = base.tables.keys();

         while(enu.hasMoreElements()) {
            PartitionTable temp = enu.nextElement();
            set.add(temp.getName());
         }
      }

      return set.size();
   }

   public PartitionTable getPartitionTable(String name) {
      return getPartitionTable(name, false);
   }

   /**
    * Get the PartitionTable of tables in this partition.
    * @param name the name of PartitionTable.
    * @return the PartitionTables.
    */
   public PartitionTable getPartitionTable(String name, boolean self) {
      Enumeration e = getTables(self);

      while(e.hasMoreElements()) {
         PartitionTable temp = (PartitionTable) e.nextElement();
         String pname = temp.getName();

         if(pname.equals(name)) {
            return temp;
         }
      }

      return null;
   }

   /**
    * Adds a table or an alias to this partition. If the name is an alias, the
    * getAliasTable(table) will return the real physical table name.
    *
    * @param table the name of the table or alias.
    */
   public void addTable(String table, Object catalog, Object schema) {
      addTable(table, new Rectangle(-1, -1, -1, -1), catalog, schema);
   }

   /**
    * Adds a table or an alias to this partition. If the name is an alias, the
    * getAliasTable(table) will return the real physical table name.
    *
    * @param table the name of the table or alias.
    */
   public void addTable(String table, int type, String sql, Object catalog, Object schema) {
      addTable(table, type, sql, new Rectangle(-1, -1, -1, -1), catalog, schema);
   }

   /**
    * Adds a table to this partition that is rendered at the specified location.
    *
    * @param table the name of the table.
    * @param bounds position and size of the table.
    */
   public void addTable(String table, Rectangle bounds) {
      addTable(table, bounds, null, null);
   }

   /**
    * Adds a table to this partition that is rendered at the specified location.
    *
    * @param table the name of the table.
    * @param bounds position and size of the table.
    */
   public void addTable(String table, Rectangle bounds, Object catalog, Object schema) {
      this.addTable(table, inetsoft.uql.erm.PartitionTable.PHYSICAL, "", bounds, catalog, schema);
   }

   /**
    * Adds a table to this partition that is rendered at the specified location.
    *
    * @param table the name of the table.
    * @param bounds position and size of the table.
    */
   public void addTable(String table, int type, String sql, Rectangle bounds,
                        Object catalog, Object schema) {
      bounds = bounds == null ? new Rectangle(-1, -1, -1, -1) : bounds;

      tables.put(new PartitionTable(table, type, sql, catalog, schema), bounds);
      clearGraph();
   }

   /**
    * Check if a table is an alias to another table.
    */
   public boolean isAlias(String table) {
      return base != null && base.isAlias(table) || aliastables.get(table) != null;
   }

   public boolean isRealTable(String table) {
      return base != null && base.isRealTable(table) || aliastables.containsValue(table);
   }

   public String getAlias(String table) {
      if(base != null && base.isRealTable(table)) {
         return base.getAlias(table);
      }

      for(Map.Entry<String, String> aliasTableEntry : aliastables.entrySet()) {
         if(aliasTableEntry.getValue().equals(table)) {
            return aliasTableEntry.getKey();
         }
      }

      return null;
   }

   /**
    * Check if a table is an auto-alias to another table.
    */
   public boolean isAutoAlias(String alias) {
      return  base != null && base.isAutoAlias(alias) || autoContainsTable(alias);
   }

   /**
    * Check if a table is an alias or auto-alias to another table.
    */
   public boolean isAliasTable(String alias) {
      return isAlias(alias) || isAutoAlias(alias);
   }

   /**
    * Get real table by alias table name.
    */
   public String getRealTable(String alias, boolean recursive) {
      if(isAlias(alias)) {
         return getAliasTable(alias, recursive);
      }
      else if(isAutoAlias(alias)) {
         return getAutoAliasTable(alias);
      }

      return alias;
   }

   /**
    * Renames a table.
    *
    * @param oldName the old table name.
    * @param newName the new table name.
    */
   public void renameTable(String oldName, String newName, String catalog, String schema) {
      renameTable(oldName, newName, catalog, schema, true);
   }

   /**
    * Renames a table.
    *
    * @param oldName   the old table name.
    * @param newName   the new table name.
    * @param recursive <tt>true</tt> to rename the table on the base/child
    *                  partitions.
    */
   @SuppressWarnings("unchecked")
   private void renameTable(String oldName, String newName, String catalog, String schema, boolean recursive) {
      PartitionTable table = getPartitionTable(oldName);

      if(table != null) {
         Rectangle bounds = tables.remove(table);
         table.setName(newName);
         table.setCatalog(catalog);
         table.setSchema(schema);
         tables.put(table, bounds);
      }

      String aliasTable = aliastables.remove(oldName);

      if(aliasTable != null) {
         aliastables.put(newName, aliasTable);
      }

      for(Map.Entry<String, String> entry : aliastables.entrySet()) {
         if(oldName.equals(entry.getValue())) {
            entry.setValue(newName);
         }
      }

      for(Object element : relationships) {
         XRelationship relationship = (XRelationship) element;

         if(relationship.getDependentTable().equals(oldName)) {
            relationship.setDependent(
               newName, relationship.getDependentColumn());
         }
         else if(relationship.getIndependentTable().equals(oldName)) {
            relationship.setIndependent(
               newName, relationship.getIndependentColumn());
         }
      }

      AutoAlias autoAlias = autoaliases.remove(oldName);

      if(autoAlias != null) {
         autoaliases.put(newName, autoAlias);
      }

      for(AutoAlias element : autoaliases.values()) {
         for(int i = 0; i < element.getIncomingJoinCount(); i++) {
            AutoAlias.IncomingJoin join = element.getIncomingJoin(i);

            if(join.getSourceTable().equals(oldName)) {
               join.setSourceTable(newName);
            }
         }
      }

      if(recursive) {
         if(base != null) {
            base.renameTable(oldName, newName, catalog, schema, false);
         }
         else {
            for(String pname : getPartitionNames()) {
               getPartition(pname).renameTable(oldName, newName, catalog, schema, false);
            }
         }
      }

      clearGraph();
   }

   /**
    * Removes a table from this partition.
    *
    * @param table the name of the table.
    */
   public void removeTable(String table) {
      removeTable(table, false);
   }

   public void clearTable() {
      removeMetaData();
      tables.clear();
      autoaliases.clear();
      aliastables.clear();
   }

   /**
    * Removes a table from this partition.
    *
    * @param table the name of the table.
    */
   private void removeTable(String table, boolean self) {
      PartitionTable temp = getPartitionTable(table);

      if(temp != null) {
         temp.removeMetaData();
         tables.remove(temp);
      }

      autoaliases.remove(table);

      if(aliastables.containsKey(table)) {
         aliastables.remove(table);
      }

      Iterator<Map.Entry<String, AutoAlias>> iter =
         autoaliases.entrySet().iterator();

      while(iter.hasNext()) {
         Map.Entry<String, AutoAlias> entry = iter.next();
         entry.getValue().removeIncomingJoin(table);

         if(entry.getValue().getIncomingJoinCount() == 0) {
            iter.remove();
         }
      }

      if(!self) {
         if(base != null) {
            base.removeTable(table, true);
         }
         else {
            for(String pname : getPartitionNames()) {
               getPartition(pname).removeTable(table, true);
            }
         }
      }

      clearGraph();
   }

   /**
    * Determines if this partition contains the specified table.
    *
    * @param table the name of the table.
    *
    * @return <code>true</code> if the table is in this partition.
    */
   public boolean containsTable(String table) {
      return containsTable(table, true);
   }

   public boolean containsTable(String table, boolean fuzzy) {
      return containsTable(table, fuzzy, false);
   }

   /**
    * Determines if this partition contains the specified table.
    *
    * @param table the name of the table.
    * @param fuzzy <tt>true</tt> to check if in all containers, <tt>false</tt>
    * to check if an existent table only.
    *
    * @return <code>true</code> if the table is in this partition.
    */
   public boolean containsTable(String table, boolean fuzzy, boolean self) {
      if(!self && base != null && base.containsTable(table, fuzzy)) {
         return true;
      }

      PartitionTable temp = getPartitionTable(table, self);

      if(!fuzzy) {
         return temp != null;
      }

      // @by larryl, when applying auto aliases, the original table is
      // removed from the tables, but left on autoaliases so the logic check
      // if the table is in partition still works
      return temp != null || autoaliases.containsKey(table) ||
         aliastables.containsValue(table) || autoContainsTable(table);
   }

   /**
    * Determines if autoaliases contains the specified table.
    */
   private boolean autoContainsTable(String table) {
      if(base != null && base.autoContainsTable(table)) {
         return true;
      }

      for(AutoAlias auto : autoaliases.values()) {
         for(int j = 0; j < auto.getIncomingJoinCount(); j++) {
            AutoAlias.IncomingJoin join = auto.getIncomingJoin(j);

            if(join.getAlias().equals(table)) {
               return true;
            }
         }
      }

      return false;
   }

   public Rectangle getRuntimeAliasTableBounds(String autoAlias) {
      Rectangle rectangle = base != null ? base.getRuntimeAliasTableBounds(autoAlias) : null;

      return rectangle != null ? rectangle : runtimeAliasTables.get(autoAlias);
   }

   public void setRuntimeAliasTableBounds(String autoAlias, Rectangle bounds) {
      if(base != null && base.runtimeAliasTables.containsKey(autoAlias)) {
         return;
      }

      runtimeAliasTables.put(autoAlias, bounds);
   }

   /**
    * Gets the location and size of the table.
    * @param table the name of the table.
    * @return the bounds of the table.
    */
   public Rectangle getBounds(String table) {
      return getBounds(table, false);
   }

   public Rectangle getBounds(String table, boolean runtime) {
      if(runtime) {
         Rectangle box = getRuntimeAliasTableBounds(table);

         if(box != null) {
            return box;
         }
      }

      PartitionTable temp = getPartitionTable(table);

      return getBounds(temp, runtime);
   }

   /**
    * Gets the location and size of the table.
    * @param table the name of the table.
    * @return the bounds of the table.
    */
   public Rectangle getBounds(PartitionTable table) {
      return getBounds(table, false);
   }

   public Rectangle getBounds(PartitionTable table, boolean runtime) {
      Rectangle rectangle = null;

      if(runtime) {
         rectangle = getRuntimeAliasTableBounds(table.getName());
      }

      if(rectangle == null) {
         rectangle = base != null ? base.getBounds(table) : null;

         rectangle = rectangle != null ? rectangle : tables.get(table);
      }

      return rectangle;
   }

   /**
    * Get the location and size of the table.
    * @param table the name of the table.
    * @param bounds the bounds of the table.
    */
   public void setBounds(String table, Rectangle bounds) {
      PartitionTable temp = getPartitionTable(table);

      if(base != null && base.tables.containsKey(temp)) {
         return;
      }

      tables.put(temp, bounds);
   }

   public void setBounds(String table, Rectangle bounds, boolean runtime) {
      if(runtime && getRuntimeAliasTableBounds(table) != null) {
         setRuntimeAliasTableBounds(table, bounds);
         return;
      }

      PartitionTable temp = getPartitionTable(table);

      if(base != null && base.tables.containsKey(temp)) {
         return;
      }

      tables.put(temp, bounds);
   }

   /**
    * Get the auto alias definition for a table.
    */
   public AutoAlias getAutoAlias(String table) {
      AutoAlias autoAlias = base != null ? base.getAutoAlias(table) : null;
      return autoAlias != null ? autoAlias : autoaliases.get(table);
   }

   /**
    * Get the auto alias table which contains this table name.
    * @param alias the alias name.
    */
   public String getAutoAliasTable(String alias) {
      return getAutoAliasTable0(alias, autoaliases);
   }

   public String getAllAutoAliasTable(String alias) {
      String result = null;

      if(base != null) {
         result = getAutoAliasTable0(alias, base.autoaliases);
      }

      if(result != null) {
         return result;
      }

      return getAutoAliasTable(alias);
   }

   private String getAutoAliasTable0(String alias, Hashtable<String, AutoAlias> autoAliases) {
      for(Map.Entry<String, AutoAlias> entry : autoAliases.entrySet()) {
         String base = entry.getKey();
         AutoAlias auto = entry.getValue();

         for(int i = 0; i < auto.getIncomingJoinCount(); i++) {
            if(auto.getIncomingJoin(i).getAlias().equals(alias)) {
               return base;
            }

         }
      }

      return null;
   }

   /**
    * Set the auto alias definition for a table.
    */
   public void setAutoAlias(String table, AutoAlias alias) {
      PartitionTable temp = getPartitionTable(table);

      if(base != null && base.tables.containsKey(temp)) {
         return;
      }

      if(alias == null) {
         autoaliases.remove(table);
      }
      else {
         autoaliases.put(table, alias);
         aliastables.remove(table);
      }

      clearGraph();
   }

   /**
    * Remove all auto-alias setting.
    */
   public void removeAllAutoAliases() {
      autoaliases.clear();
      clearGraph();
   }

   /**
    * Get the physical table name of a table alias.
    * @param table name of a table alias.
    */
   public String getAliasTable(String table) {
      return getAliasTable(table, false);
   }

   /**
    * Get the physical table name of a table alias.
    * @param table name of a table alias.
    */
   public String getAliasTable(String table, boolean recursive) {
      // @by jasonshobe, optimization: don't make a copy of aliastables and
      // base.aliastables, use them directly
      if(!recursive) {
         if(base != null && base.aliastables.containsKey(table)) {
            return base.aliastables.get(table);
         }

         return aliastables.get(table);
      }
      else {
         String table2;

         if(base != null && base.aliastables.containsKey(table)) {
            table2 = base.aliastables.get(table);
         }
         else {
            table2 = aliastables.get(table);
         }

         while(table2 != null && !table2.equals(table)) {
            table = table2;

            if(base != null && base.aliastables.containsKey(table)) {
               table2 = base.aliastables.get(table);
            }
            else {
               table2 = aliastables.get(table);
            }
         }

         return table;
      }
   }

   /**
    * Check if a table is a runtime alias to another table.
    */
   public boolean isRuntimeAlias(String table) {
      if(isAlias(table)) {
         return true;
      }

      // if this table is an embedded view, we should treat it as a alias
      // rather than table
      PartitionTable pt = getPartitionTable(table);
      return pt != null && pt.getType() == inetsoft.uql.erm.PartitionTable.VIEW;
   }

   /**
    * Get the physical table name of a table alias.
    * @param alias name of a table alias.
    */
   @SuppressWarnings("UnusedParameters")
   public Object getRunTimeTable(String alias, boolean recursive) {
      Object table = alias;
      PartitionTable temp = getPartitionTable(alias);

      if(temp != null && temp.getType() == inetsoft.uql.erm.PartitionTable.PHYSICAL) {
         table = getAliasTable(alias, true);
      }
      else if(temp != null) {
         table = new UniformSQL(temp.getSql(), false);
      }

      return table;
   }

   /**
    * Set the physical table of an alias.
    */
   public void setAliasTable(String alias, String table) {
      if(table == null) {
         aliastables.remove(alias);
      }
      else {
         aliastables.put(alias, table);
         autoaliases.remove(alias);
      }

      clearGraph();
   }

   /**
    * Rename a table alias.
    */
   public void renameAlias(String source, String oname, String name) {
      renameAlias(source, oname, name, inetsoft.uql.erm.PartitionTable.PHYSICAL, "");
   }

   /**
    * Rename a table alias.
    */
   @SuppressWarnings("UnusedParameters")
   public void renameAlias(String source, String oname, String name,
                           int type, String sql) {
      PartitionTable otable = getPartitionTable(oname);
      PartitionTable table = otable.clone();
      table.setName(name);
      table.setOldName(oname);

      tables.put(table, tables.get(otable));
      aliastables.put(name, aliastables.get(oname));

      tables.remove(otable);
      aliastables.remove(oname);
      clearGraph();

      // rename tables in relations
      for(XRelationship rel : relationships) {
         if(rel.getDependentTable().equals(oname)) {
            rel.setDependent(name, rel.getDependentColumn());
         }
         else if(rel.getIndependentTable().equals(oname)) {
            rel.setIndependent(name, rel.getIndependentColumn());
         }
      }

      // @by vincentx, update logical model
      if(model != null) {
         for(String lmname : model.getLogicalModelNames()) {
            XLogicalModel xlm = model.getLogicalModel(lmname);

            if(xlm.getPartition().equals(getName())) {
               XEntity entity = xlm.getEntity(oname);

               if(entity != null) {
                  entity.setName(name);
                  xlm.updateEntity(oname, entity);
               }
            }
         }
      }
   }

   /**
    * Add a relationship to this data model.
    *
    * @param relationship the relationship to add.
    */
   public void addRelationship(XRelationship relationship) {
      if(!isBaseRelationship(relationship) &&
         !isRelationshipContained(relationship))
      {
         relationships.add(relationship);
         clearGraph();
      }
   }

   /**
    * Check whether the realationship contains in this.
    */
   private boolean isRelationshipContained(XRelationship relationship) {
      for(XRelationship rel : relationships) {
         if(relationship.equalContents(rel)) {
            return true;
         }
      }

      return false;
   }

   public Enumeration<XRelationship> getRelationships() {
      return getRelationships(false);
   }

   /**
    * Get a list of the relationships defined in this data model.
    *
    * @return an Enumeration containing all the relationships in this data
    *         model.
    */
   public Enumeration<XRelationship> getRelationships(boolean onlySelf) {
      final Iterator<XRelationship> ite1 = !onlySelf && base != null ?
          base.relationships.iterator() : null;
      final Iterator<XRelationship> ite2 = relationships.iterator();

      return new Enumeration<XRelationship>() {
         boolean isbase;

         @Override
         public boolean hasMoreElements() {
            isbase = ite1 != null && ite1.hasNext();
            return isbase || ite2.hasNext();
         }

         @Override
         public XRelationship nextElement() {
            assert !isbase || ite1 != null;
            return !isbase ? ite2.next() : ite1.next();
         }
      };
   }

   /**
    * Get the number of relationships defined in this data model.
    * @return the number of relationships defined in this data model.
    */
   public int getRelationshipCount() {
      return (base != null ? base.relationships.size() : 0 ) +
             relationships.size();
   }

   /**
    * Get relationship by index.
    */
   public XRelationship getRelationship(int idx) {
      int bsize = base == null ? 0 : base.relationships.size();

      return idx < bsize && base != null ? base.relationships.get(idx) :
         relationships.get(idx - bsize);
   }

   /**
    * Set relationship to index place.
    * @param index
    * @param relationship
    */
   public void setRelationship(int index, XRelationship relationship) {
      int bsize = base == null ? 0 : base.relationships.size();

      if(index < bsize) {
         assert base != null;
         base.relationships.set(index, relationship);
      }
      else {
         relationships.set(index - bsize, relationship);
      }
   }

   /**
    * Remove relationship by index.
    */
   public void removeRelationship(int idx) {
      int bsize = base == null ? 0 : base.relationships.size();

      if(idx < bsize) {
         assert base != null;
         base.relationships.removeElementAt(idx);
      }
      else {
         relationships.removeElementAt(idx - bsize);
      }
   }

   public void clearRelationship() {
      relationships = new Vector<>();
   }

   /**
    * Finds a join between the specified tables.
    *
    * @param dependentTable the dependent table in the relationship.
    * @param independentTable the independent table in the relationship.
    *
    * @return a relationship between the two tables or <code>null</code> if
    * no such relationship between the tables has been defined.
    */
   public XRelationship findRelationship(String dependentTable,
                                         String independentTable) {
      Enumeration e = getRelationships();

      while(e.hasMoreElements()) {
         XRelationship rel = (XRelationship) e.nextElement();

         // when we check the relationship between two tables, there is no
         // need to care about the direction. Otherwise two directly related
         // table, e.g. orders and orderdetails, may not be allowed to be
         // joined depending on the order they are passed in. The directional
         // search in the graph should have already handled the directions
         if(rel.getIndependentTable().equals(dependentTable) &&
            rel.getDependentTable().equals(independentTable))
         {
            return rel;
         }
         else if(rel.getDependentTable().equals(dependentTable) &&
                 rel.getIndependentTable().equals(independentTable)) {
            return rel;
         }
      }

      return null;
   }

   /**
    * Gets the path between the specified tables, ignoring the direction of the
    * joins.
    * @param table1 the first table.
    * @param table2 the second table.
    * @param tables all nodes that should be included as part of the path.
    * @param distances the distance from the origin to the node for weighted
    * graph. This is an output parameter and is populated on return. List of
    * Double objects.
    * @return an array of table names defining the path between the two tables
    *         or <code>null</code> if no such path exists.
    */
   @SuppressWarnings("SuspiciousSystemArraycopy")
   private String[] findJoinPath(String table1, String table2,
                                 Collection<String> tables,
                                 List<Double> distances, boolean useAlias)
   {
      String [] baseJoinPath = base == null ? null :
         base.findJoinPath(table1, table2, tables, distances, useAlias);
      Graph graph = getGraph(useAlias);

      if(graph == null) {
         buildGraph(useAlias);
         graph = getGraph(useAlias);
      }

      Object[] path = graph.findPath(table1, table2, tables, distances);

      if(path == null || path.length == 1) {
         return null;
      }

      String[] spath = new String[path.length +
         (baseJoinPath == null ? 0 : baseJoinPath.length)];
      System.arraycopy(path, 0, spath, 0, path.length);

      if(baseJoinPath != null) {
         System.arraycopy(baseJoinPath, 0, spath, path.length, baseJoinPath.length);
      }

      return spath;
   }

   /**
    * Checks the status of this physical view. This method checks for cycles and
    * unjoined tables.
    *
    * @return one of <code>VALID</code>, <code>CYCLE</code>, or
    *         <code>UNJOINED</code>.
    *
    * @since 8.0
    */
   public int getStatus() {
      if(getTableCount() < 2) {
         return VALID;
      }

      // force the graph to be rebuilt
      clearGraph();

      // check for missing joins
      String[] unjoined = getUnjoinedTables();

      if(unjoined != null) {
         return UNJOINED;
      }

      // check for cycle
      Object[] cycle = getGraph().findCycle(Graph.WEAK_WEIGHT);

      if(cycle != null) {
         if(cycle.length != 2 || !isMultipleKeyRelationShip(cycle[0], cycle[1]))
         {
            return CYCLE;
         }
      }

      // TO DO, check for multiple weak join in a cycle.
      // @by larryl, having multiple weak joins in one cycle may not be
      // a problem as that cycle may contain smaller cycles, which the weak
      // join can be used to resolve the ambiguity in the smaller cycles.
      // Currently will not check for multiple joins until we get a better
      // understanding of what situations we want to warn after the initial
      // release (7.0).

      return VALID;
   }

   /**
    * Verify the integrity of this physical view. The following conditions are
    * checked:<br>
    * <li>Determine if all the specified tables are joined together.</li>
    * <li>Determine if any cycle exists in the join graph.</li>
    * <li>Determine if there are more than one weak join in a join path.</li>
    * @return null if verification is successful. Otherwise the error message
    * if verification failed.
    */
   public String verify() {
      if(getTableCount() < 2) {
         return null;
      }

      Catalog catalog = Catalog.getCatalog();

      // force the graph to be rebuilt
      clearGraph();

      // check for missing joins
      String[] unjoined = getUnjoinedTables();

      if(unjoined != null) {
         return catalog.getString("designer.qb.partitionProp.confirmMsg", (Object[]) unjoined);
      }

      // check for cycle
      Object[] cycle = getGraph().findCycle(Graph.WEAK_WEIGHT);

      if(cycle != null) {
         if(cycle.length != 2 || !isMultipleKeyRelationShip(cycle[0], cycle[1]))
         {
            return catalog.getString("designer.qb.partitionProp.cycleMsg");
         }
      }

      // TO DO, check for multiple weak join in a cycle.
      // @by larryl, having multiple weak joins in one cycle may not be
      // a problem as that cycle may contain smaller cycles, which the weak
      // join can be used to resolve the ambiguity in the smaller cycles.
      // Currently will not check for multiple joins until we get a better
      // understanding of what situations we want to warn after the initial
      // release (7.0).

      return null;
   }

   /**
    * Checks for any unjoined tables in the view. Returns the first pair of
    * unjoined tables that are found.
    *
    * @return the names of the unjoined tables or <code>null</code> if no
    *         unjoined tables are found.
    *
    * @since 8.0
    */
   public String[] getUnjoinedTables() {
      Enumeration e = getTables();
      String[] tables = new String[getTableCount()];

      for(int i = 0; e.hasMoreElements(); i++) {
         PartitionTable temp = (PartitionTable) e.nextElement();
         tables[i] = temp.getName();
      }

      // check for missing joins
      for(int i = 1; i < tables.length; i++) {
         if(findJoinPath(tables[0], tables[i], null, new ArrayList<>(), false) == null)
         {
            return new String[] { tables[0], tables[i] };
         }
      }

      return null;
   }

   /**
    * Gets the relationships involved in a cycle.
    * @return a Set of XRelationship objects that are involved in a cycle or
    *         <code>null</code> if no cycle exists.
    *
    * @since 8.0
    */
   public Set<XRelationship> getCycleRelationships() {
      return getCycleRelationships(false);
   }

   /**
    * Gets the relationships involved in a cycle.
    *
    * @return a Set of XRelationship objects that are involved in a cycle or
    *         <code>null</code> if no cycle exists.
    *
    * @since 8.0
    */
   public Set<XRelationship> getCycleRelationships(boolean refresh) {
      buildGraph(refresh, false);
      Graph graph = getGraph();

      if(graph == null) {
         return null;
      }

      Object[] cycle = graph.findCycle(Graph.WEAK_WEIGHT);

      if(cycle == null ||
         (cycle.length == 2 && isMultipleKeyRelationShip(cycle[0], cycle[1])))
      {
         return null;
      }

      HashSet<XRelationship> relset = new HashSet<>();
      Enumeration iter = getRelationships();

      while(iter.hasMoreElements()) {
         XRelationship rel = (XRelationship) iter.nextElement();
         boolean incycle = false;

         for(int i = 0; i < cycle.length; i++) {
            String tbl1 = cycle[i].toString();

            for(int j = i + 1; j < cycle.length; j++) {
               String tbl2 = cycle[j].toString();

               if((rel.getIndependentTable().equals(tbl1) &&
                   rel.getDependentTable().equals(tbl2)) ||
                  (rel.getIndependentTable().equals(tbl2) &&
                   rel.getDependentTable().equals(tbl1)))
               {
                  relset.add(rel);
                  incycle = true;
                  break;
               }
            }

            if(incycle) {
               break;
            }
         }
      }

      return relset;
   }

   private boolean isMultipleKeyRelationShip(Object tbl1, Object tbl2) {
      Enumeration e = getRelationships();

      Set<String> col1 = new HashSet<>();
      Set<String> col2 = new HashSet<>();

      while(e.hasMoreElements()) {
         XRelationship rel = (XRelationship) e.nextElement();

         String c1 = null;
         String c2 = null;

         if(rel.getIndependentTable().equals(tbl1) &&
            rel.getDependentTable().equals(tbl2))
         {
            c1 = rel.getIndependentColumn();
            c2 = rel.getDependentColumn();
         }
         else if(rel.getIndependentTable().equals(tbl2) &&
                 rel.getDependentTable().equals(tbl1)) {
            c1 = rel.getDependentColumn();
            c2 = rel.getIndependentColumn();
         }

         if(c1 != null && c2 != null) {
            if(col1.contains(c1) || col2.contains(c2)) {
               return false;
            }

            col1.add(c1);
            col2.add(c2);
         }
      }

      return true;
   }

   /**
    * Gets all the relationships required to create a join between the
    * specified tables. This methods handles weak joins in the model.
    * The tables parameter should include all tables that are in the
    * query. It is necessary for correctly handling weak joins.
    *
    * @param dependentTable the dependent table in the join.
    * @param independentTable the independent table in the join.
    * @param tables all tables that are already included in the query.
    *
    * @return an array of relationships containing all the relationships
    *         required to create the join, or <code>null</code> if no join is
    *         possible between the two tables.
    */
   public XRelationship[] findRelationships(String dependentTable,
                                            String independentTable,
                                            Collection<String> tables)
   {
      return findRelationships(dependentTable, independentTable, tables, false);
   }

   /**
    * Gets all the relationships required to create a join between the
    * specified tables. This methods handles weak joins in the model.
    * The tables parameter should include all tables that are in the
    * query. It is necessary for correctly handling weak joins.
    *
    * @param dependentTable the dependent table in the join.
    * @param independentTable the independent table in the join.
    * @param tables all tables that are already included in the query.
    * @param useAlias true if use alias.
    *
    * @return an array of relationships containing all the relationships
    *         required to create the join, or <code>null</code> if no join is
    *         possible between the two tables.
    */
   public XRelationship[] findRelationships(String dependentTable,
                                            String independentTable,
                                            Collection<String> tables,
                                            boolean useAlias)
   {
      String[] path = findJoinPath(dependentTable, independentTable, tables,
                                   new ArrayList<>(), useAlias);

      if(path == null) {
         return null;
      }

      List<XRelationship> v = new ArrayList<>();

      for(int i = 1; i < path.length; i++) {
         Enumeration<XRelationship> e = getRelationships();
         String path1 = path[i - 1];
         String path2 = path[i];

         while(e.hasMoreElements()) {
            XRelationship rel = e.nextElement();

            if(equalsPath(rel, path1, path2, useAlias)) {
               v.add(rel);
            }
         }
      }

      return v.toArray(new XRelationship[v.size()]);
   }

   /**
    * Find all relations necessary to connect the tables in the originating
    * set to the others set. All tables in the originating should already
    * be connected. All tables in the others should be disjoint. This function
    * ensures the shortest path is used for all table pairs with regard to
    * weak joins. The originating and others sets are modified on return.
    * If all tables are joined, the others set should be empty.
    *
    * @param originating set of table names (string).
    * @param others set of table names (string).
    * @return an array of relationships containing all the relationships
    *         required to create the join.
    */
   public XRelationship[] findRelationships(Set<String> originating,
                                            Set<String> others)
   {
      return findRelationships(originating, others, false);
   }

   /**
    * Find all relations necessary to connect the tables in the originating
    * set to the others set. All tables in the originating should already
    * be connected. All tables in the others should be disjoint. This function
    * ensures the shortest path is used for all table pairs with regard to
    * weak joins. The originating and others sets are modified on return.
    * If all tables are joined, the others set should be empty.
    *
    * @param originating set of table names (string).
    * @param others set of table names (string).
    * @param useAlias true if use alias.
    * @return an array of relationships containing all the relationships
    *         required to create the join.
    */
   public XRelationship[] findRelationships(Set<String> originating,
                                            Set<String> others,
                                            boolean useAlias)
   {
      // @by larryl, use ordered set so the order is deterministic
      Set<String> tables = new LinkedHashSet<>();
      Set<XRelationship> joins = new LinkedHashSet<>();

      tables.addAll(originating);
      tables.addAll(others);

      if(isIncludeAllJoin()) {
         tables.addAll(others);
         others.clear();

         if(relationships != null) {
            joins.addAll(relationships);
         }
      }

      while(others.size() > 0) {
         Iterator<String> iter1 = originating.iterator();
         int otherCnt = others.size();
         String[] spath = null;
         double shortest = Double.MAX_VALUE;

         while(iter1.hasNext()) {
            String table1 = iter1.next();

            for(String table2 : others) {
               List<Double> weights = new ArrayList<>();

               String[] path = findJoinPath(table1, table2, tables, weights,
                                            useAlias);

               if(path == null) {
                  continue;
               }

               double weight = weights.get(weights.size() - 1);

               if(weight < shortest) {
                  spath = path;
                  shortest = weight;
               }
            }
         }

         if(spath != null) {
            for(int i = 1; i < spath.length; i++) {
               Enumeration e = getRelationships();
               String path1 = spath[i - 1];
               String path2 = spath[i];

               while(e.hasMoreElements()) {
                  XRelationship rel = (XRelationship) e.nextElement();

                  if(equalsPath(rel, path1, path2, useAlias)) {
                     joins.add(rel);

                     originating.add(path1);
                     originating.add(path2);

                     others.remove(path1);
                     others.remove(path2);
                  }
               }
            }
         }

         if(others.size() == otherCnt) {
            break;
         }
      }

      XRelationship[] result = new XRelationship[joins.size()];

      joins.toArray(result);

      Arrays.sort(result, (v1, v2) -> {
         int t1 = v1.getOrder();
         int t2 = v2.getOrder();

         // always respect priority
         if(t1 != t2) {
            return Integer.compare(t2, t1);
         }

         final Tuple join1 = new Tuple(v1.getDependentTable(), v1.getIndependentTable());
         final Tuple join2 = new Tuple(v2.getDependentTable(), v2.getIndependentTable());

         // sort 'and' before 'or'
         if(Objects.equals(join1, join2)) {
            final String merge1 = v1.getMerging();
            final String merge2 = v2.getMerging();

            if(!Objects.equals(merge1, merge2)) {
               return XSet.AND.equals(merge1) ? -1 : 1;
            }
         }

         return 0;
      });

      return result;
   }

   /**
    * Check if path is equal.
    */
   private boolean equalsPath(XRelationship rel, String path1, String path2,
      boolean useAlias)
   {
      String itable = rel.getIndependentTable();
      String dtable = rel.getDependentTable();

      return equalTable(itable, path1, useAlias) &&
         equalTable(dtable, path2, useAlias) ||
         equalTable(itable, path2, useAlias) &&
         equalTable(dtable, path1, useAlias);
   }

   /**
    * Check if table is equal.
    */
   private boolean equalTable(String table1, String table2, boolean useAlias) {
      if(useAlias) {
         String aliasTable2 = getAutoAliasTable(table2);
         table2 = aliasTable2 == null ? table2 : aliasTable2;
      }

      return table1.equals(table2);
   }

   /**
    * Remove a relationship from this data model.
    *
    * @param relationship the relationship to remove.
    * @return <tt>true</tt> is removed.
    */
   public boolean removeRelationship(XRelationship relationship) {
      boolean removed = relationships.remove(relationship);
      clearGraph();

      return removed;
   }

   /**
    * Create a new partition with all auto-aliases expanded. Each alias appears
    * as a regular table alias, and all relationships are added.
    */
   public XPartition applyAutoAliases() {
      Map<String, Rectangle> oldRuntimeAlias = Tool.deepCloneMap(this.runtimeAliasTables);
      this.runtimeAliasTables.clear();
      final XPartition partition = (XPartition) clone();

      if(partition.base != null) {
         partition.base = (XPartition) (partition.base.clone());
      }

      Enumeration<String> iter = new Enumeration<String>() {
         Enumeration<String> enu1 =
            partition.base == null ? null : partition.base.autoaliases.keys();
         Enumeration<String> enu2 = autoaliases.keys();
         String res = null;

         @Override
         public boolean hasMoreElements() {
            res = null;

            if(enu1 != null && enu1.hasMoreElements()) {
               res = enu1.nextElement();
            }
            else if(enu2.hasMoreElements()) {
               res = enu2.nextElement();
            }

            return res != null;
         }

         @Override
         public String nextElement() {
            return res;
         }
      };

      buildGraph();

      while(iter.hasMoreElements()) {
         String table = iter.nextElement();
         AutoAlias auto = getAutoAlias(table);
         HashSet<String> incomings = new HashSet<>();

         // find all incoming joins
         for(int i = 0; i < auto.getIncomingJoinCount(); i++) {
            AutoAlias.IncomingJoin join = auto.getIncomingJoin(i);
            incomings.add(join.getSourceTable());
         }

         // all tables that has split into aliases that needs to be
         // deleted after all aliases have been created
         Map<String, Integer> aliases = new HashMap<>();

         // create alias for each incoming join
         for(int i = 0; i < auto.getIncomingJoinCount(); i++) {
            AutoAlias.IncomingJoin join = auto.getIncomingJoin(i);
            String sourceTbl = join.getSourceTable();
            String alias = join.getAlias();

            if(partition.aliastables.containsKey(alias)) {
               LOG.warn("The alias exists or is identical to a table: {}", alias);
            }

            String prefix = join.getPrefix();
            boolean outgoing = join.isKeepOutgoing();
            Map<String, String> tablealiases = new HashMap<>();
            Set<String> processed = new HashSet<>(incomings);
            processed.add(table);

            if(prefix == null) {
               prefix = alias;
            }

            addAliases(partition, sourceTbl, table, alias, prefix,
                       outgoing, incomings, processed, tablealiases, aliases);
         }

         // the original table is no longer visible after the auto alias
         // is applied. We only remove the table from the tables map but
         // leave it on autoaliases so the table is still considered part
         // of the partition for meta data retrieval
         Set<String> used = aliases.keySet();

         for(String key : used) {
            PartitionTable temp = partition.getPartitionTable(key);

            if(partition.base != null && temp != null) {
               partition.base.tables.remove(temp);
            }

            if(temp != null) {
               partition.tables.remove(temp);
            }
         }

         // removed all orphaned joins
         for(int i = 0; i < partition.getRelationshipCount(); i++) {
            XRelationship rel = partition.getRelationship(i);

            if(used.contains(rel.getDependentTable()) ||
               used.contains(rel.getIndependentTable()))
            {
               partition.removeRelationship(i);
               i--;
            }
         }
      }

      restoreRuntimeAliasMap(oldRuntimeAlias);
      partition.runtimeAliasTables = Tool.deepCloneMap(this.runtimeAliasTables);

      partition.clearGraph(); // need to rebuild the graph
      partition.validate(); // clean up orphan relations

      return partition;
   }

   private void restoreRuntimeAliasMap(Map<String, Rectangle> oldRuntimeAlias) {
      oldRuntimeAlias.forEach((key, value) -> {
         this.runtimeAliasTables.computeIfPresent(key, (table, bounds) -> value);
      });
   }

   /**
    * Add the alias to partition, and all outgoing (recursively) linked
    * tables if outgoing is true.
    * @param sourceTbl the incoming table.
    * @param table the table to create alias (auto-alias).
    * @param outgoing true to traverse outgoing links.
    * @param processed processed tables to ignore.
    * @param tablealiases table to alias mapping of the current auto-alias branch.
    * @param aliasMap tables that have been created aliases and should be
    */
   @SuppressWarnings("deprecation")
   private void addAliases(XPartition partition, String sourceTbl,
                           String table, String alias, String prefix,
                           boolean outgoing, Set<String> incomings,
                           Set<String> processed,
                           Map<String, String> tablealiases,
                           Map<String, Integer> aliasMap)
   {
      int index = aliasMap.compute(table,
         (key, oldValue) -> oldValue == null ? 0 : oldValue + 1);

      if(alias != null) {
         PartitionTable temp = partition.getPartitionTable(table);

         if(getPartitionTable(table) == null) {
            return;
         }

         Rectangle box = getBounds(table);

         if(temp != null && temp.getType() == inetsoft.uql.erm.PartitionTable.VIEW) {
            partition.addTable(alias, temp.getType(), temp.getSql(), box,
                               temp.getCatalog(), temp.getSchema());
         }
         else {
            Object catalog = temp == null ? null : temp.getCatalog();
            Object schema = temp == null ? null : temp.getSchema();
            partition.addTable(alias, box, catalog, schema);
         }

         box = box.getBounds();
         box.y += index * GRAPH_VIEW_AUTO_ALIAS_Y_GAP;

         this.runtimeAliasTables.put(alias, box);

         // the table is an alias table?
         if(getAliasTable(table) != null) {
            partition.setAliasTable(alias, getAliasTable(table));
         }
         // the table is not an alias table?
         else {
            partition.setAliasTable(alias, table);
         }

         if(outgoing) {
            PartitionTable aliseTable = partition.getPartitionTable(alias);

            if(aliseTable != null) {
               aliseTable.sourceTable = table;
            }
         }


         for(int i = 0; i < getRelationshipCount(); i++) {
            XRelationship rel = getRelationship(i);
            int dependentCardinality = rel.getDependentCardinality();
            int independentCardinality = rel.getIndependentCardinality();
            boolean weak = rel.isWeakJoin();
            String tbl2 = null;
            String joinType = rel.getJoinType();
            int order = rel.getOrder();
            String merging = rel.getMerging();

            if(rel.getDependentTable().equals(table)) {
               tbl2 = rel.getIndependentTable();
               String alias2 = tablealiases.get(tbl2);

               if(alias2 != null) {
                  tbl2 = alias2;
               }

               rel = new XRelationship(alias, rel.getDependentColumn(),
                                       tbl2, rel.getIndependentColumn(),
                                       rel.getType());
            }
            else if(rel.getIndependentTable().equals(table)) {
               tbl2 = rel.getDependentTable();
               String alias2 = tablealiases.get(tbl2);

               if(alias2 != null) {
                  tbl2 = alias2;
               }

               rel = new XRelationship(tbl2, rel.getDependentColumn(),
                                       alias, rel.getIndependentColumn(),
                                       rel.getType());
            }

            if(tbl2 != null) {
               // @by larryl, if the join is from an incoming join to the
               // auto-alias node, don't added it as it should split out
               // another alias.
               // If the join is from an outgoing node to the incoming, we
               // should keep it.
               if(incomings.contains(tbl2) && !tbl2.equals(sourceTbl) &&
                  incomings.contains(sourceTbl))
               {
                  continue;
               }

               rel.setDependentCardinality(dependentCardinality);
               rel.setIndependentCardinality(independentCardinality);
               rel.setWeakJoin(weak);
               rel.setJoinType(joinType);
               rel.setOrder(order);
               rel.setMerging(merging);

                partition.addRelationship(rel);
             }
         }

         tablealiases.put(table, alias);
         processed.add(table);
      }

      if(outgoing) {
         Object[] linked = getGraph().getNeighbors(table);

         for(Object element : linked) {
            String link = (String) element;

            if(processed.contains(link)) {
               continue;
            }

            // @by mikec, use underline here is more safe,
            // some database do not support space.
            addAliases(partition, table, link,
                       prefix + OUTGOING_TABLE_SEPARATOR + link, prefix, true,
                       incomings, processed, tablealiases, aliasMap);
         }
      }
   }

   public Object[] getNeighbors(String table) {
      buildGraph();

      return getGraph().getNeighbors(table);
   }

   /**
    * Validate the data model.
    */
   public void validate() {
      if(base != null) {
         List<String> dup = new ArrayList<>();
         Enumeration<PartitionTable> tables = this.tables.keys();

         while(tables.hasMoreElements()) {
            PartitionTable ptable = tables.nextElement();

            if(base.tables.containsKey(ptable)) {
               dup.add(ptable.getName());
            }
         }

         if(dup.size() > 0) {
            LOG.warn("{} exists in parent and has been removed", dup);

            for(String table : dup) {
               removeTable(table, true);
            }
         }

         for(int i = relationships.size() - 1; i >= 0; i--) {
            XRelationship relationship = relationships.get(i);

            if(base.relationships.contains(relationship)) {
               LOG.warn("{} exists in parent and has been removed", relationship);
               relationships.remove(i);
            }
         }
      }

      // @by billh, validate the relationships to make sure that related tables
      // are all contained in the data model
      Vector<XRelationship> nrelations = new Vector<>();

      for(XRelationship relation : relationships) {
         String dependent = relation.getDependentTable();
         String independent = relation.getIndependentTable();

         if(containsTable(dependent) && containsTable(independent)) {
            nrelations.add(relation);
         }
      }

      relationships = nrelations;
   }

   /**
    * Update child partition reference.
    */
   public void updateReference() {
      String[] names = getPartitionNames();

      for(String name : names) {
         getPartition(name).setBaseParitition(this);
      }
   }

   /**
    * Creates a copy of this object.
    *
    * @return a copy of this instance.
    */
   @Override
   @SuppressWarnings("CloneDoesntCallSuperClone")
   public Object clone() {
      return deepClone(false);
   }

   public Object deepClone(boolean deep) {
      XPartition part = new XPartition();

      part.setName(this.getName());
      part.setDataModel(this.model);
      part.setDescription(description);
      part.setFolder(folder);
      part.setZoomRatio(this.zoomRatio);

      if(deep && base != null) {
         part.setBaseParitition((XPartition) base.deepClone(true));
      }
      else {
         part.setBaseParitition(base);
      }

      part.setConnection(connection);
      part.setIncludeAllJoin(isIncludeAllJoin());

      Enumeration e = getTables(true);

      while(e.hasMoreElements()) {
         PartitionTable table = (PartitionTable) e.nextElement();
         Rectangle box = getBounds(table);

         part.addTable(table.getName(), table.getType(), table.getSql(),
            box != null ? box.getBounds() : null,
            table.getCatalog(), table.getSchema());
      }

      part.aliastables = new Hashtable<>(aliastables);
      part.autoaliases = Tool.deepCloneMap(autoaliases);
      part.relationships = Tool.deepCloneCollection(relationships);

      if(part.relationships == null) {
         part.relationships = new Vector<>(relationships);
      }

      part.runtimeAliasTables = Tool.deepCloneMap(this.runtimeAliasTables);
      part.modified = modified;

      return part;
   }

   /**
    * Determines if this object is equivalent to another object.
    *
    * @param obj the reference object with which to compare.
    *
    * @return <code>true</code> if the objects are are equivalent,
    *         <code>false</code> otherwise.
    */
   public boolean equals(Object obj) {
      return obj instanceof XPartition &&
         this.getName().equals(((XPartition) obj).getName());
   }

   @Override
   public void writeStart(PrintWriter writer) {
      writer.print("<partition name=\"" + Tool.escape(getName()) + "\"");

      if(folder != null) {
         writer.print(" folder = \"" + Tool.escape(folder) + "\" ");
      }

      if(connection != null) {
         writer.print(" connection = \"" + Tool.escape(connection) + "\" ");
      }

      writer.print(" includeAllJoin=\"" + includeAllJoin + "\" ");
      writer.println(">");

      if(createdBy != null) {
         writer.print("<createdBy>");
         writer.print("<![CDATA[");
         writer.print(createdBy);
         writer.print("]]>");
         writer.println("</createdBy>");
      }

      if(modifiedBy != null) {
         writer.print("<modifiedBy>");
         writer.print("<![CDATA[");
         writer.print(modifiedBy);
         writer.print("]]>");
         writer.println("</modifiedBy>");
      }

      if(created != 0) {
         writer.print("<created>");
         writer.print("<![CDATA[");
         writer.print(created);
         writer.print("]]>");
         writer.println("</created>");
      }

      if(modified != 0) {
         writer.print("<modified>");
         writer.print("<![CDATA[");
         writer.print(modified);
         writer.print("]]>");
         writer.println("</modified>");
      }

      if(description != null) {
         writer.print("<description>");
         writer.print("<![CDATA[");
         writer.print(description);
         writer.print("]]>");
         writer.println("</description>");
      }

      Enumeration e = getTables(true);

      while(e.hasMoreElements()) {
         PartitionTable temp = (PartitionTable) e.nextElement();
         String table = temp.getName();
         Rectangle box = tables.get(temp);
         AutoAlias autoalias = autoaliases.get(table);
         String aliasTbl = getAliasTable(table);
         String sql = temp.getSql();
         Object catalog = temp.getCatalog();
         Object schema = temp.getSchema();
         int type = temp.getType();

         // in case the view was zoomed-in, unscale the location values
         // before saving them
         int x = Math.round((float) (box.x * (1 / zoomRatio)));
         int y = Math.round((float) (box.y * (1 / zoomRatio)));
         int w = Math.round((float) (box.width * (1 / zoomRatio)));
         int h = Math.round((float) (box.height * (1 / zoomRatio)));

         if(type == inetsoft.uql.erm.PartitionTable.PHYSICAL) {
            writer.println("<table name=\"" + Tool.escape(table) + "\" type=\""
                  + "0" + "\" x=\"" + x + "\" y=\"" + y + "\" width=\"" + w +
                  "\" height=\"" + h + "\">");
         }
         else {
            writer.println("<table name=\"" + Tool.escape(table) + "\" type=\""
                  + "1" + "\" x=\"" + x + "\" y=\"" + y + "\" width=\"" + w +
                  "\" height=\"" + h + "\">");

            if(sql != null) {
               writer.println("<sql><![CDATA[" + Tool.encodeCDATA(sql) + "]]></sql>");
            }
         }

         if(autoalias != null) {
            autoalias.writeXML(writer);
         }
         else if(aliasTbl != null) {
            writer.println("<aliasTable><![CDATA[" + aliasTbl +
                           "]]></aliasTable>");
         }

         if(catalog != null) {
            writer.println("<catalog><![CDATA[" + catalog + "]]></catalog>");
         }

         if(schema != null) {
            writer.println("<schema><![CDATA[" + schema + "]]></schema>");
         }

         writer.println("</table>");
      }

      for(XRelationship relationship : relationships) {
         relationship.writeXML(writer);
      }

      writeRuntimeAliasTables(writer);
   }

   /**
    * {@link #parseRuntimeAliasTables}
    */
   private void writeRuntimeAliasTables(PrintWriter writer) {
      runtimeAliasTables.forEach((table, box) -> {
         int x = Math.round((float) (box.x * (1 / zoomRatio)));
         int y = Math.round((float) (box.y * (1 / zoomRatio)));
         int w = Math.round((float) (box.width * (1 / zoomRatio)));
         int h = Math.round((float) (box.height * (1 / zoomRatio)));

         writer.println("<runtimeAliasTable alias=\"" + Tool.escape(table)
            + "\" x=\"" + x + "\" y=\"" + y + "\" width=\"" + w +
            "\" height=\"" + h + "\"></runtimeAliasTable>");
      });
   }

   @Override
   public void writeEnd(PrintWriter writer) {
      writer.println("</partition>");
   }

   /**
    * Writes an XML representation of this partition.
    * @param writer the output stream to which to write the XML representation.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writeStart(writer);
      writeEnd(writer);
   }

   /**
    * Reads the definition of this partition from an XML element.
    *
    * @param tag the XML element that represents this partition.
    *
    * @throws DOMException if an error occurs while parsing the XML element.
    */
   @Override
   public void parseXML(Element tag) throws DOMException {
      String attr;

      if((attr = Tool.getAttribute(tag, "name")) != null) {
         setName(attr);
      }

      if((attr = Tool.getAttribute(tag, "folder")) != null) {
         setFolder(attr);
      }

      connection = Tool.getAttribute(tag, "connection");
      includeAllJoin = "true".equals(Tool.getAttribute(tag, "includeAllJoin"));
      NodeList nl = null;

      nl = Tool.getChildNodesByTagName(tag, "description");

      if(nl != null && nl.getLength() > 0) {
         setDescription(Tool.getValue(nl.item(0)));
      }

      nl = Tool.getChildNodesByTagName(tag, "createdBy");

      if(nl != null && nl.getLength() > 0) {
         setCreatedBy(Tool.getValue(nl.item(0)));
      }

      nl = Tool.getChildNodesByTagName(tag, "modifiedBy");

      if(nl != null && nl.getLength() > 0) {
         setLastModifiedBy(Tool.getValue(nl.item(0)));
      }

      nl = Tool.getChildNodesByTagName(tag, "created");

      if(nl != null && nl.getLength() > 0) {
         setCreated(Long.parseLong(Tool.getValue(nl.item(0))));
      }

      nl = Tool.getChildNodesByTagName(tag, "modified");

      if(nl != null && nl.getLength() > 0) {
         setLastModified(Long.parseLong(Tool.getValue(nl.item(0))));
      }

      nl = Tool.getChildNodesByTagName(tag, "table");

      for(int i = 0; nl != null && i < nl.getLength(); i++) {
         Element elem = (Element) nl.item(i);
         String table = Tool.getAttribute(elem, "name");
         Rectangle box = parseTableBounds(elem);
         String sql = "";
         int type = inetsoft.uql.erm.PartitionTable.PHYSICAL;

         if((attr = Tool.getAttribute(elem, "type")) != null) {
            type = Integer.parseInt(attr);
         }

         if((attr = Tool.getAttribute(elem, "sql")) != null) {
            sql = attr;
         }

         if(table != null) {
            Element autoalias = Tool.getChildNodeByTagName(elem, "autoAlias");
            Element aliasTbl = Tool.getChildNodeByTagName(elem, "aliasTable");
            Element catalogElem = Tool.getChildNodeByTagName(elem, "catalog");
            Object catalog = catalogElem == null ? null : Tool.getValue(catalogElem);
            Element schemaElem = Tool.getChildNodeByTagName(elem, "schema");
            Object schema = schemaElem == null ? null : Tool.getValue(schemaElem);
            Element tableCatalogElem = Tool.getChildNodeByTagName(elem, "tableCatalog");
            Object tableCatalog = tableCatalogElem == null ? null : Tool.getValue(tableCatalogElem);
            Element tableSchemaElem = Tool.getChildNodeByTagName(elem, "schema");
            Object tableSchema = tableSchemaElem == null ? null : Tool.getValue(tableSchemaElem);
            Element sqlElem = Tool.getChildNodeByTagName(elem, "sql");
            sql = sqlElem == null ? sql : Tool.decodeCDATA(Tool.getValue(sqlElem));

            if(type == inetsoft.uql.erm.PartitionTable.PHYSICAL) {
               addTable(table, inetsoft.uql.erm.PartitionTable.PHYSICAL, "", box, catalog, schema);
            }
            else {
               addTable(table, type, sql, box, catalog, schema);
            }

            if(autoalias != null) {
               AutoAlias alias = new AutoAlias();

               alias.parseXML(autoalias);
               setAutoAlias(table, alias);
            }
            else if(aliasTbl != null) {
               setAliasTable(table, Tool.getValue(aliasTbl));
            }
         }
      }

      NodeList list = Tool.getChildNodesByTagName(tag, "relationship");

      for(int i = 0; list != null && i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);
         XRelationship relationship = new XRelationship();
         relationship.parseXML(elem);
         addRelationship(relationship);
      }

      parseRuntimeAliasTables(tag);
   }

   /**
    * must after all parse
    * {@link #writeRuntimeAliasTables}
    */
   private void parseRuntimeAliasTables(Element tag) {
      NodeList nl = Tool.getChildNodesByTagName(tag, "runtimeAliasTable");

      for(int i = 0; i < nl.getLength(); i++) {
         Element elem = (Element) nl.item(i);
         String table = Tool.getAttribute(elem, "alias");

         if(StringUtils.hasText(table)) {
            Rectangle box = parseTableBounds(elem);
            runtimeAliasTables.put(table, box);
         }
      }
   }

   private Rectangle parseTableBounds(Element elem) {
      String attr;
      Rectangle box = new Rectangle(-1, -1, -1, -1);

      if((attr = Tool.getAttribute(elem, "x")) != null) {
         box.x = Integer.parseInt(attr);
      }

      if((attr = Tool.getAttribute(elem, "y")) != null) {
         box.y = Integer.parseInt(attr);
      }

      if((attr = Tool.getAttribute(elem, "width")) != null) {
         box.width = Integer.parseInt(attr);
      }

      if((attr = Tool.getAttribute(elem, "height")) != null) {
         box.height = Integer.parseInt(attr);
      }

      if(box.x < 0 || box.y < 0) {
         box.x = -1;
         box.y = -1;
      }

      return box;
   }

   /**
    * Finds a join between the specified tables.
    *
    * @return a relationship between the two tables or <code>null</code> if
    * no such relationship between the tables has been defined.
    */
   private Enumeration<XRelationship> findRelationships(String table) {
      Enumeration<XRelationship> e = getRelationships();
      List<XRelationship> rels = new ArrayList<>();

      while(e.hasMoreElements()) {
         XRelationship rel = e.nextElement();

         if(rel.getIndependentTable().equals(table) ||
            rel.getDependentTable().equals(table))
         {
            if(rel.isWeakJoin()) {
               rels.add(rel);
            }
            else {
               rels.add(0, rel);
            }
         }
      }

      return Collections.enumeration(rels);
   }

   /**
    * Finds a join between the specified tables.
    *
    * @return a relationship between the two tables or <code>null</code> if
    * no such relationship between the tables has been defined.
    */
   private Enumeration findRelationshipPairs() {
      Enumeration e = getTables();
      Map<String, Tuple> pairs = new HashMap<>();

      while(e.hasMoreElements()) {
         PartitionTable temp = (PartitionTable) e.nextElement();
         String table = temp.getName();
         Enumeration rels = findRelationships(table);

         // when we check the relationship between two tables, there is no
         // need to care about the direction. Otherwise two directly related
         // table, e.g. orders and orderdetails, may not be allowed to be
         // joined depending on the order they are passed in. The directional
         // search in the graph should have already handled the directions
         while(rels.hasMoreElements()) {
            XRelationship relation = (XRelationship) rels.nextElement();

            String table1;
            String table2;

            if(relation.getIndependentTable().equals(table)) {
               table1 = table;
               table2 = relation.getDependentTable();
            }
            else {
               table1 = relation.getIndependentTable();
               table2 = table;
            }

            String key = table1 + table2;
            Tuple tuple = pairs.get(key);

            if(tuple == null) {
               key = table2 + table1;
               tuple = pairs.get(key);
            }

            if(tuple == null) {
               tuple = new Tuple(table1, table2);
            }

            tuple.addRelationship(relation);
            pairs.put(key, tuple);
         }
      }

      return Collections.enumeration(pairs.values());
   }

   class Tuple {
      public Tuple(String table1, String table2) {
         this.table1 = table1;
         this.table2 = table2;
      }

      public void addRelationship(XRelationship ship) {
         if(relations.size() == 0) {
            relations.add(ship);
         }
         else {
            for(int i = 0; i < relations.size(); i++) {
               XRelationship rel = relations.get(i);

               if(rel.equals(ship)) {
                  continue;
               }

               if(rel.getIndependentTable().equals(ship.getIndependentTable())){
                  if(rel.getIndependentColumn().equals(
                        ship.getIndependentColumn()) ||
                     rel.getDependentColumn().equals(ship.getDependentColumn()))
                  {
                     relations.add(ship);
                     break;
                  }
               }
               else {
                  if(rel.getIndependentColumn().equals(
                        ship.getDependentColumn()) ||
                     rel.getDependentColumn().equals(
                        ship.getIndependentColumn()))
                  {
                     relations.add(ship);
                     break;
                  }
               }
            }
         }
      }

      public Enumeration<XRelationship> getRelationships() {
         return Collections.enumeration(relations);
      }

      public String toString() {
         return table1 + table2;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         Tuple tuple = (Tuple) o;

         // ignore join direction
         if(table1.equals(tuple.table1) && table2.equals(tuple.table2)) {
            return true;
         }
         else if(table1.equals(tuple.table2) && table2.equals(tuple.table1)) {
            return true;
         }

         return false;
      }

      @Override
      public int hashCode() {
         return Objects.hash(table1, table2, relations);
      }

      String table1;
      String table2;
      Vector<XRelationship> relations = new Vector<>();
   }

   public class PartitionTable {
      public PartitionTable(String name, int type, String sql) {
         this(name, type, sql, null, null);
      }

      public PartitionTable(String name, int type, String sql, Object catalog, Object schema) {
         this.name = name;
         this.type = type;

         if(sql == null || sql.length() == 0) {
            sql = "";
         }

         this.sql = sql;
         this.hash = name.hashCode();
         this.catalog = catalog;
         this.schema = schema;
      }

      public String getName() {
         return name;
      }

      public int getType() {
         return type;
      }

      public String getSql() {
         return sql;
      }

      public String setSql() {
         return sql;
      }

      public void setName(String name) {
         this.name = name;
         this.hash = name.hashCode();
      }

      public String getOldName() {
         return oldName;
      }

      public void setOldName(String oldName) {
         this.oldName = oldName;
      }

      public void setType(int type) {
         this.type = type;
      }

      public Object getCatalog() {
         return catalog;
      }

      public void setCatalog(Object catalog) {
         this.catalog = catalog;
      }

      public Object getSchema() {
         return schema;
      }

      public void setSchema(Object schema) {
         this.schema = schema;
      }

      @Override
      @SuppressWarnings("CloneDoesntCallSuperClone")
      public PartitionTable clone() {
         return new PartitionTable(name, type, sql, catalog, schema);
      }

      public void setSql(String sql) {
         this.sql = sql;
      }

      public boolean equals(Object obj) {
         if(obj == this) {
            return true;
         }

         if(!(obj instanceof PartitionTable)) {
            return false;
         }

         PartitionTable ptable = (PartitionTable) obj;
         return ptable.name.equals(name) && ptable.sql.equals(sql) &&
            ptable.type == type && Tool.equals(ptable.catalog, catalog) &&
            Tool.equals(ptable.schema, schema);
      }

      public int hashCode() {
         return hash;
      }

      private void init() {
         UniformSQL usql = new UniformSQL();
         usql.setParseSQL(false);
         usql.setSQLString(sql);

         JDBCQuery query = new JDBCQuery();
         query.setSQLDefinition(usql);
         XDataSource ds = getDataSource();
         query.setDataSource(ds);
         table = new TableNode(name);

         try {
            VariableTable vtable = new VariableTable();
            vtable.put(JDBCQuery.HINT_MAX_ROWS, "1");
            XHandler handler = XFactory.getDataService().getHandler(
               XSessionManager.getSessionManager().getSession(),
               query.getDataSource(), new VariableTable());
            XNode node = handler.execute(
               query, vtable, ThreadContext.getContextPrincipal(), null);
            XNodeTableLens lens = new XNodeTableLens(node);
            Set<String> columnList = new HashSet<>();
            Catalog catalog = Catalog.getCatalog();
            cols = new XTypeNode[lens.getColCount()];

            for(int i = 0; i < lens.getColCount(); i++) {
               String column = lens.getColumnIdentifier(i);
               columnList.add(column);
               String type = lens.getColType(i).getCanonicalName().toLowerCase();
               type = type.substring(type.lastIndexOf(".") + 1);
               type = type.equals("timestamp") ? "timeInstant" : type;
               table.addColumn(column, type);
               table.addColumnType(column, type);

               cols[i] = XSchema.createPrimitiveType(
                  column, ((XTableNode) node).getType(i));
            }
         }
         catch(Exception ex) {
            cols = new XTypeNode[0];
            LOG.error("Failed to initialize embedded view", ex);
         }
      }

      /**
       * Get the base datasource for inline sql execution.
       * @return
       */
      private XDataSource getDataSource() {
         if(datasource == null) {
            String dname = XPartition.this.getDataModel().getDataSource();
            datasource = DataSourceRegistry.getRegistry().getDataSource(dname);
            datasource = (XDataSource) datasource.clone();
         }

         // here, columns should be executed by current connection of the parition,
         // but not the additional for the login user.
         if(datasource != null) {
            datasource.setFromPortal(true);
         }

         return datasource;
      }

      public TableNode getTableNode() {
         if(getMetaData() == null) {
            init();
            putMetaData(getKey());
         }

         return table;
      }

      public XTypeNode[] getColumns() {
         init();
         return cols;
      }

      /**
       * Set the base datasource for inline sql execution.
       * @param datasource
       */
      public void setDataSource(XDataSource datasource) {
         this.datasource = datasource;
      }

      /**
       * Get the metadata for this PartitionTables.
       */
      private TableNode getMetaData() {
         String key = getKey();
         table = metaDataCache.get(key);

         if(table != null) {
            return table;
         }

         XNode node = readMetaData(key);

         if(node == null) {
            return null;
         }

         table = new TableNode(name);

         for(int i = 0; i < node.getChildCount(); i++) {
            XNode tnode = node.getChild(i);
            String column = tnode.getName();
            table.addColumn(column, tnode.getValue());
            Object type = tnode.getAttribute(column + TableNode.COLUMN_TYPE_SUFFIX);

            if(tnode instanceof XTypeNode && type != null) {
               try {
                  table.addColumnType(column, (String) type);
               }
               catch(Exception ignore) {
               }
            }
         }

         return table;
      }

      /**
       * Read the metadata from the cached file for this PartitionTables.
       */
      private XNode readMetaData(String key) {
         XNode node = null;
         FileSystemService fileSystemService = FileSystemService.getInstance();
         File dir = fileSystemService.getMetadataDirectory();
         File file = fileSystemService.getFile(dir, key);

         if(file.exists()) {
            try(ObjectInputStream in = new ObjectInputStream(
               new BufferedInputStream(new FileInputStream(file))))
            {
               node = (XNode) in.readObject();
            }
            catch(Exception ignore) {
            }
         }

         return node;
      }

      /**
       * Cache the metadata for this PartitionTables.
       */
      private void putMetaData(String key) {
         if(table.getColumnCount() == 0) {
            return;
         }

         metaDataCache.put(key, table);
         XNode node = new XNode();
         node.setName(name);
         table.copyColumnsInto(node);
         writeMetaData(key, node);
      }

      /**
       * Write the metadata for this PartitionTables.
       */
      private void writeMetaData(String key, XNode meta) {
         FileSystemService fileSystemService = FileSystemService.getInstance();
         File dir = fileSystemService.getMetadataDirectory();

         try(FileOutputStream file = new FileOutputStream(fileSystemService.getFile(dir, key));
             ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(file)))
         {
            out.writeObject(meta);
         }
         catch(Exception ignore) {
         }
      }

      /**
       * Remove the metadata for this PartitionTables.
       */
      public void removeMetaData() {
         String key = getKey();
         key = Tool.normalizeFileName(key);
         metaDataCache.remove(key);
         FileSystemService fileSystemService = FileSystemService.getInstance();
         File dir = fileSystemService.getMetadataDirectory();
         File file = fileSystemService.getFile(dir, key);

         if(file.exists() && !file.delete()) {
            LOG.warn("Failed to delete meta-data file: {}", file);
         }
      }

      /**
       * Get table alias source.
       * @return
       */
      public String getSourceTable() {
         return sourceTable;
      }

      /**
       * Get the cache key for the metadata.
       */
      private String getKey() {
         String ds = datasource != null ? datasource.getFullName() :
            XPartition.this.getDataModel().getDataSource();
         String key = ds;
         key = key + "__" +  XPartition.this.name + "__" + name + "PhysicalView";

         return Tool.replaceAll(key, "/", "^_^");
      }

      String name;
      String oldName;
      int hash;
      int type;
      String sql;
      Object catalog;
      Object schema;
      TableNode table;
      XTypeNode[] cols;
      String sourceTable; // for alias table after apply outlinks.
      XDataSource datasource;
   }

   /**
    * Constructs a graph of the joins between tables.
    */
   private synchronized void buildGraph() {
      buildGraph(false, false);
   }

   /**
    * Constructs a graph of the joins between tables.
    */
   private synchronized void buildGraph(boolean useAlias) {
      buildGraph(false, useAlias);
   }

   /**
    * Constructs a graph of the joins between tables.
    */
   private synchronized void buildGraph(boolean refresh, boolean useAlias) {
      Graph graph = getGraph(useAlias);

      if(graph != null && !refresh) {
         return;
      }

      graph = new Graph(true);

      if(useAlias) {
         agraph = graph;
      }
      else {
         tgraph = graph;
      }

      // @by mikec, here for multiple column relationship, we treat them
      // as composite key,
      // For the cycle relatioship we should push them to graph to detect cycle
      // and for composite key we only push once so that they will not be
      // treated as a cycle.
      // Composite Key sample:
      //   table1.column1  ---  table2.column1
      //   table1.column2  ---  table2.column2
      //
      // Cycle sample:
      //   table1.column1  ---  table2.column1
      //   table1.column1  ---  table2.column2
      Enumeration e = findRelationshipPairs();

      while(e.hasMoreElements()) {
         Tuple tuple = (Tuple) e.nextElement();
         Enumeration res = tuple.getRelationships();

         while(res.hasMoreElements()) {
            XRelationship rel = (XRelationship) res.nextElement();
            int weight = rel.isWeakJoin() ? Graph.WEAK_WEIGHT : 1;
            String dtable = rel.getDependentTable();
            String itable = rel.getIndependentTable();

            if(useAlias) {
               AutoAlias alias = getAutoAlias(itable);

               if(alias != null) {
                  for(int i = 0; i < alias.getIncomingJoinCount(); i++) {
                     AutoAlias.IncomingJoin join = alias.getIncomingJoin(i);

                     if(dtable.equals(join.getSourceTable())) {
                        itable = join.getAlias();
                        break;
                     }
                  }
               }
            }

            graph.addEdge(dtable, itable, weight, false);
         }
      }
   }

   /**
    * Set the value of the zoomRatio.
    */
   public void setZoomRatio(double zoom) {
      zoomRatio = zoom;
   }

   /**
    * Get the string representaion.
    * @return the string representation.
    */
   public String toString() {
      return super.toString() + "[" + name + "]";
   }

   /**
    * Set the base partition.
    */
   public void setBaseParitition(XPartition base) {
      this.base = base;
      this.validate();
   }

   private DataSourceRegistry getRegistry() {
      return DataSourceRegistry.getRegistry();
   }

   /**
    * Get graph.
    */
   private Graph getGraph() {
      return getGraph(false);
   }

   /**
    * Get graph.
    */
   private Graph getGraph(boolean useAlias) {
      return useAlias ? agraph : tgraph;
   }

   /**
    * Clear graph.
    */
   private void clearGraph() {
      agraph = null;
      tgraph = null;
   }

   public void removeMetaData() {
      Enumeration<PartitionTable> e = getTables();

      while(e.hasMoreElements()) {
         PartitionTable temp = e.nextElement();

         if(inetsoft.uql.erm.PartitionTable.VIEW == temp.getType()) {
            temp.removeMetaData();
         }
      }
   }

   public String getFolder() {
      return folder;
   }

   public void setFolder(String folder) {
      this.folder = Tool.isEmptyString(folder) ? null : folder;
   }

   public static final String OUTGOING_TABLE_SEPARATOR = "_";
   public static final int GRAPH_VIEW_AUTO_ALIAS_Y_GAP = 56;

   private String name = null;
   private String description = null;
   private long created;
   private long modified;
   private String createdBy;
   private String modifiedBy;
   private String folder;
   private Hashtable<PartitionTable, Rectangle> tables = new Hashtable<>();
   private Map<String, Rectangle> runtimeAliasTables = new ConcurrentHashMap<>();
   private Vector<XRelationship> relationships = null;
   private Hashtable<String, String> aliastables = new Hashtable<>();
   private Hashtable<String, AutoAlias> autoaliases = new Hashtable<>();
   private Graph agraph = null; // graph uses table alias
   private Graph tgraph = null; // graph use table name
   private double zoomRatio = 1; // zoom value
   private XDataModel model = null;
   private XPartition base;
   private String connection;
   private boolean includeAllJoin = false;
   private Map<String, TableNode> metaDataCache = new HashMap<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(XPartition.class);
}

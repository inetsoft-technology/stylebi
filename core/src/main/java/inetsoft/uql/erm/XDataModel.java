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

import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.xml.XMLStorage.Filter;
import inetsoft.util.xml.XMLStorage.XMLFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.security.Principal;
import java.util.*;

/**
 * An XDataModel represents a set of logical models for a physical data source.
 * A logical model consists of a collection of entities and relationships.
 *
 * @author InetSoft Technology Corp.
 * @version 8.0
 */
public class XDataModel implements Cloneable, Serializable,
        XMLFragment, XMLSerializable
{
   public static final String DATAMODEL = "DataModel";
   public static final String LOGICALMODEL = "LogicalModel";
   public static final String PARTITION = "partition";
   public static final String VPMOBJECT = "vpmObject";
   public static final String DEFAULTCONNECTION = "(Default Connection)";


   /**
    * Create a new instance of XDataModel.
    */
   public XDataModel() {}

   /**
    * Create a new instance of XDataModel.
    *
    * @param datasource the name of the JDBC data source that the model
    *                   represents.
    */
   public XDataModel(String datasource) {
      this.datasource = datasource;
   }

   public String getDataSource() {
      return datasource;
   }

   public void setDataSource(String datasource) {
      this.datasource = datasource;
   }

   /**
    * Add a logical model to this data model.
    *
    * @param model the logical model to add.
    */
   public void addLogicalModel(XLogicalModel model) {
      addLogicalModel(model, true);
   }

   /**
    * Add a logical model to this data model.
    *
    * @param model the logical model to add.
    * @param modelChange when update dependencies or import, return not change.
    */
   public void addLogicalModel(XLogicalModel model, boolean modelChange) {
      model.setDataModel(this);
      String path = getDataSource() + "/" + model.getName();
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.LOGIC_MODEL, path, null);

      if(model.getCreated() == 0 && modelChange) {
         model.setCreated(System.currentTimeMillis());
      }

      if(modelChange) {
         model.setLastModified(System.currentTimeMillis());
      }

      getRegistry().setObject(entry, model);
      model.updateReference();
   }

   private Filter[] getPath(String type, String name) {
      return getPath(type, "name", name);
   }

   private Filter[] getPath(String type, String attribute, String value) {
      List<Filter> list = new ArrayList<>();

      Filter filter = new Filter(DATAMODEL);
      filter.attributes.put("datasource", getDataSource());
      list.add(filter);

      if(type.equals(VPMOBJECT)) {
         filter = new Filter("vpms");
         list.add(filter);
      }

      filter = new Filter(type);

      if(attribute != null) {
         filter.attributes.put(attribute, value);
      }

      list.add(filter);
      Filter[] filters = new Filter[list.size()];

      return list.toArray(filters);
   }

   public void renameLogicalModel(String oldName, String newName) {
      renameLogicalModel(oldName, newName);
   }

   /**
    * Rename a logical model in this data model.
    *
    * @param oldName the old name of the logical model.
    * @param newName the new name of the logical model.
    */
   public void renameLogicalModel(String oldName, String newName, String description) {
      XLogicalModel model = getLogicalModel(oldName);

      if(model != null) {
         DependencyHandler.getInstance().updateModelDependencies(model, false);
         model.setName(newName);
         String oldPath = getDataSource() + "/" + oldName;
         String newPath = getDataSource() + "/" + newName;

         if(description != null) {
            model.setDescription(description);
         }

         model.setLastModified(System.currentTimeMillis());
         getRegistry().updateObject(oldPath, newPath, AssetEntry.Type.LOGIC_MODEL,
                 model);
         getRegistry().renameObjects(oldPath + "/", newPath + "/", true);
         model.updateReference();
         DependencyHandler.getInstance().updateModelDependencies(model, true);
         int type = RenameInfo.LOGIC_MODEL | RenameInfo.SOURCE;
         RenameInfo rinfo = new RenameInfo(oldName, newName, type);
         rinfo.setPrefix(getDataSource());
         rinfo.setModelFolder(model.getFolder());
         RenameTransformHandler.getTransformHandler().addTransformTask(rinfo);
      }
   }

   /**
    * Rename a virtual private model in this data model.
    *
    * @param oldName the old name of the virtual private model.
    * @param vpm the new name of the virtual private model.
    */
   public void renameVirtualPrivateModel(String oldName,
                                         VirtualPrivateModel vpm) {
      String oldPath = getDataSource() + "/" + oldName;
      String newPath = getDataSource() + "/" + vpm.getName();
      vpm.setLastModified(System.currentTimeMillis());
      getRegistry().updateObject(oldPath, newPath, AssetEntry.Type.VPM, vpm);
   }


   /**
    * Update a logical model.
    * @deprecated
    * @param model the logical model to update.
    */
   @Deprecated
   public void updateLogicalModel(XLogicalModel model) {
      addLogicalModel(model);
   }

   /**
    * Get the logical model with the specified name.
    *
    * @param name the name of the logical model.
    *
    * @return a logical model or <code>null</code> if no logical model with the
    *         specified name exists.
    */
   public XLogicalModel getLogicalModel(String name) {
      String path = getDataSource() + "/" + name;
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.LOGIC_MODEL, path, null);
      XLogicalModel res = (XLogicalModel) getRegistry().getObject(entry, true);

      if(res != null) {
         res.setDataModel(this);
      }

      return res;
   }

   /**
    * Return logical models under the target data model folder.
    */
   public List<XLogicalModel> getLogicalModels(String folder) {
      List<XLogicalModel> list = new ArrayList<>();

      for(String name : getLogicalModelNames()) {
         XLogicalModel lm = getLogicalModel(name);

         if(lm != null && Tool.equals(folder, lm.getFolder())) {
            list.add(lm);
         }
      }

      return list;
   }

   /**
    * Get the runtime mode logical model with the specified name.
    *
    * @param name the name of the logical model.
    *
    * @return a logical model or <code>null</code> if no logical model with the
    *         specified name exists.
    */
   public XLogicalModel getLogicalModel(String name, Principal user) {
      return getLogicalModel(name, user, false);
   }

   /**
    * Get the runtime mode logical model with the specified name.
    *
    * @param name the name of the logical model.
    * @param hideAttributes hide invisible entities and attributes or not.
    *
    * @return a logical model or <code>null</code> if no logical model with the
    *         specified name exists.
    */
   public XLogicalModel getLogicalModel(String name, Principal user,
                                        boolean hideAttributes) {
      XLogicalModel lmodel = getLogicalModel(name);
      boolean validUser = isActualUser(user);

      if(lmodel != null && (validUser)) {
         return lmodel.applyRuntime(user, hideAttributes);
      }

      return lmodel;
   }

   /**
    * Get the names of all logical models in this data model.
    */
   public String[] getLogicalModelNames() {
      try {
         AssetEntry[] entries = getRegistry().getEntries(getDataSource() + "/",
                 AssetEntry.Type.LOGIC_MODEL);
         String[] names = new String[entries.length];
         int nameStartIndex = getDataSource().length() + 1;

         for(int i = 0; i < entries.length; i++) {
            names[i] = entries[i].getPath().substring(nameStartIndex);
         }

         Arrays.sort(names);
         return names;
      }
      catch(Exception e) {
         LOG.error("Failed to get logical model names for "
                 + " data model of data source: " + getDataSource(), e);
      }

      return new String[0];
   }

   /**
    * Get the number of logical models in this data model.
    *
    * @return the number of logical models in this data model.
    */
   public int getLogicalModelCount() {
      int result = -1;
      try {
         result =  getRegistry().getEntries(getDataSource() + "/",
                 AssetEntry.Type.LOGIC_MODEL).length;
      }
      catch(Exception e) {
         LOG.error("Failed to get logical model count for "
                 + " data model of data source: " + getDataSource(), e);
      }

      return result;
   }

   /**
    * Remove a logical model from this data model.
    *
    * @param name the name of the logical model to remove.
    */
   public void removeLogicalModel(String name) {
      removeLogicalModel(name, true);
   }

   /**
    * Remove a logical model from this data model.
    */
   public boolean removeLogicalModel(String name, boolean removeAnyWay) {
      try {
         String path = getDataSource() + "/" + name;
         AssetEntry[] children = getRegistry().getEntries(path + "/",
               AssetEntry.Type.EXTENDED_LOGIC_MODEL);

         if(!removeAnyWay && children.length > 0) {
            return false;
         }

         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                 AssetEntry.Type.LOGIC_MODEL, path, null);
         getRegistry().removeObject(entry);

         for(int i = 0; i < children.length; i++) {
            getRegistry().removeObject(children[i]);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to remove logical model \"" +
            name + "\" of data model of data source: " + getDataSource(), e);
      }
      return true;
   }

   /**
    * Add a virtual private model to this data model.
    *
    * @param model the virtual private model.
    * @param change check whether the last modified time is updated
    */
   public void addVirtualPrivateModel(VirtualPrivateModel model, boolean change) {
      String path = getDataSource() + "/" + model.getName();
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.VPM, path, null);

      if(change && model.getCreated() == 0) {
         model.setCreated(System.currentTimeMillis());
      }

      if(change) {
         model.setLastModified(System.currentTimeMillis());
      }

      getRegistry().setObject(entry, model);
   }

   /**
    * Check if contains a virtual private model in this data model.
    *
    * @param model the speified virtual private model.
    *
    * @return <tt>true</tt> if contains the virtual private model,
    * <tt>false</tt> otherwise.
    */
   public boolean containsVirtualPrivateModel(VirtualPrivateModel model) {
      String path = getDataSource() + "/" + model.getName();
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.VPM, path, null);
      return getRegistry().containObject(entry);
   }

   /**
    * Get the names of all virtual private model in this data model.
    */
   public String[] getVirtualPrivateModelNames() {
      String[] result = null;

      try {
         String path = getDataSource() + "/";
         AssetEntry[] entries = getRegistry().getEntries(path, AssetEntry.Type.VPM);
         result = new String[entries.length];

         for(int i = 0; i < entries.length; i++) {
            result[i] = entries[i].getPath().substring(path.length());
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get VPM names for "
                 + " data model of data source: " + getDataSource(), e);
      }

      return result;
   }

   /**
    * Get the virtual private model with the specified private name.
    *
    * @param name the private name of the virtual private model.
    *
    * @return a VirtualPrivateModel or <code>null</code> if no virtual private
    *         model with the specified name exists.
    */
   public VirtualPrivateModel getVirtualPrivateModel(String name) {
      String path = getDataSource() + "/" + name;
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.VPM, path, null);
      return (VirtualPrivateModel) getRegistry().getObject(entry, true);
   }

   /**
    * Get all the virtual private models.
    * @deprecated
    * @return all the virtual private models.
    */
   @Deprecated
   public Enumeration<VirtualPrivateModel> getVirtualPrivateModels() {
      final String[] names = getVirtualPrivateModelNames();

      return new Enumeration<VirtualPrivateModel>() {
         int i = -1;
         @Override
         public boolean hasMoreElements() {
            return ++i < names.length;
         }

         @Override
         public VirtualPrivateModel nextElement() {
            return getVirtualPrivateModel(names[i]);
         }
      };
   }

   /**
    * Remove a virtual private model from this data model.
    * @param name the name of the virtual private model to remove.
    */
   public void removeVirtualPrivateModel(String name) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.VPM, getDataSource() + "/" + name, null);
      getRegistry().removeObject(entry);
   }

   /**
    * Remove a virtual private model from this data model.
    *
    * @param model the virtual private model to remove.
    */
   public void removeVirtualPrivateModel(VirtualPrivateModel model) {
      removeVirtualPrivateModel(model.getName());
   }

   /**
    * Remove all the virtual private models from this data model.
    */
   public void removeVirtualPrivateModels() {
      try {
         AssetEntry[] vpmEntries =
                 getRegistry().getEntries(getDataSource() + "/", AssetEntry.Type.VPM);

         for(int i = 0; i < vpmEntries.length; i++) {
            getRegistry().removeObject(vpmEntries[i]);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to remove VPMs of "
                 + " data model of data source: " + getDataSource(), e);
      }
   }

   /**
    * Add a partition to this model.
    *
    * @param partition the partition to add.
    */
   public void addPartition(XPartition partition) {
      addPartition(partition, false);
   }

   /**
    * Add a partition to this model.
    *
    * @param partition the partition to add.
    * @param isImport
    */
   public void addPartition(XPartition partition, boolean isImport) {
      partition.setDataModel(this);
      String path = getDataSource() + "/" + partition.getName();
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.PARTITION, path, null);

      if(!isImport && partition.getCreated() == 0) {
         partition.setCreated(System.currentTimeMillis());
      }

      if(!isImport) {
         partition.setLastModified(System.currentTimeMillis());
      }

      getRegistry().setObject(entry, partition);
      partition.updateReference();
   }

   /**
    * Get the names of all partition in this data model.
    */
   public String[] getPartitionNames() {
      String[] result = null;

      try {
         String path = getDataSource() + "/";
         AssetEntry[] entries = getRegistry().getEntries(path,
                 AssetEntry.Type.PARTITION);
         result = new String[entries.length];

         for(int i = 0; i < entries.length; i++) {
            result[i] = entries[i].getPath().substring(path.length());
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get partition names for "
                 + " data model of data source: " + getDataSource(), e);
      }

      return result;
   }

   /**
    * Get the partition with the specified name.
    *
    * @param name the name of the partition.
    *
    * @return the partition or <code>null</code> if no partitions exist with
    *         the specified name.
    */
   public XPartition getPartition(String name) {
      String path = getDataSource() + "/" + name;
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.PARTITION, path, null);
      XPartition partition = (XPartition) getRegistry().getObject(entry, true);

      if(partition != null) {
         partition.setDataModel(this);
      }

      return partition;
   }

   /**
    * Get the partition with the specified name.
    *
    * @param name the name of the partition.
    *
    * @return the partition or <code>null</code> if no partitions exist with
    *         the specified name.
    */
   public XPartition getPartition(String name, Principal user) {
      XPartition partition = getPartition(name);
      XPartition extended = null;

      if(partition != null) {
         String ds = XUtil.getAdditionalDatasource(user, getDataSource());
         extended = partition.getPartitionByConnection(ds);
      }

      return extended != null ? extended : partition;
   }

   /**
    * Get a list of all partitions contained in this data model.
    * @deprecated
    * @return an Enumeration containing all the partitions in this data model.
    */
   @Deprecated
   public Enumeration<XPartition> getPartitions() {
      final String[] names = getPartitionNames();

      return new Enumeration<XPartition>() {
         int i = -1;
         @Override
         public boolean hasMoreElements() {
            return ++i < names.length;
         }

         @Override
         public XPartition nextElement() {
            return getPartition(names[i]);
         }
      };
   }

   /**
    * Get the number of partitions in this data model.
    *
    * @return the number of partitions in this data model.
    */
   public int getPartitionCount() {
      int result = -1;

      try {
         String path = getDataSource() + "/";
         result = getRegistry().getEntries(path, AssetEntry.Type.PARTITION).length;
      }
      catch(Exception e) {
         LOG.error("Failed to get partition count for "
                 + " data model of data source: " + getDataSource(), e);
      }

       return result;
   }

   /**
    * Remove a partition from this data model.
    *
    * @param name the name of the partition to remove.
    */
   public void removePartition(String name) {
      try {
         String path = getDataSource() + "/" + name;
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                 AssetEntry.Type.PARTITION, path, null);
         getRegistry().removeObject(entry);
         AssetEntry[] children = getRegistry().getEntries(path + "/",
                 AssetEntry.Type.EXTENDED_PARTITION);

         for(int i = 0; i < children.length; i++) {
            getRegistry().removeObject(children[i]);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get remove partition \"" +
                 name + "\" of data model of data source: " + getDataSource(),
                 e);
      }
   }

   public void updatePartition(String partitionName) {
      XPartition partition = getPartition(partitionName);

      if(partition != null) {
         String path = getDataSource() + "/" + partitionName;
         getRegistry().updateObject(path, path, AssetEntry.Type.PARTITION, partition);
         partition.updateReference();
      }
   }

   public void renamePartition(String oldName, String newName) {
      renamePartition(oldName, newName, null);
   }

   /**
    * Rename a partition in this data model.
    *
    * @param oldName the old name of the partition.
    * @param newName the new name of the partition.
    */
   public void renamePartition(String oldName, String newName, String description) {
      XPartition partition = getPartition(oldName);

      if(partition != null) {
         partition.setName(newName);

         if(description != null) {
            partition.setDescription(description);
         }

         String oldPath = getDataSource() + "/" + oldName;
         String newPath = getDataSource() + "/" + newName;
         partition.setLastModified(System.currentTimeMillis());
         getRegistry().updateObject(oldPath, newPath, AssetEntry.Type.PARTITION,
                 partition);
         getRegistry().renameObjects(oldPath + "/", newPath + "/");
         partition.updateReference();

         for(String name : getLogicalModelNames()) {
            XLogicalModel lm = getLogicalModel(name);

            if(oldName.equals(lm.getPartition())) {
               lm.setPartition(newName);
               addLogicalModel(lm);
            }
         }

         String[] vnames = getVirtualPrivateModelNames();

         for(String name : vnames) {
            VirtualPrivateModel vm = getVirtualPrivateModel(name);
            boolean changed = false;
            Enumeration enu = vm.getConditions();

            while(enu.hasMoreElements()) {
               VpmCondition con = (VpmCondition) enu.nextElement();

               if(con.getType() == VpmCondition.PHYSICMODEL &&
                  oldName.equals(con.getTable()))
               {
                  con.setTable(newName);
                  changed = true;
               }
            }

            if(changed) {
               addVirtualPrivateModel(vm, true);
            }
         }
      }
   }

   /**
    * Check if a table is contained in the data model, will iterate the data
    * model's partitions to get result.
    *
    * @param table the specified table.
    * @return <tt>true</tt> if it is, <tt>false</tt> otherwise.
    */
   public boolean containsTable(String table) {
      String[] names = getPartitionNames();

      for(String name : names) {
         XPartition part = getPartition(name);

         if(part.containsTable(table)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Validate the data model.
    */
   public void validate() {
      String[] names = getPartitionNames();

      for(String name : names) {
         getPartition(name).validate();
      }
   }

   @Override
   public void writeStart(PrintWriter writer) {
      writer.print("<DataModel datasource=\"");
      writer.print(Tool.escape(datasource));
      writer.println("\">");
   }

   @Override
   public void writeEnd(PrintWriter writer) {
      writer.println("</DataModel>");
   }

   /**
    * Write an XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writeStart(writer);

      for(String folder : folders) {
         writer.println("<folder name=\"" + Tool.escape(folder) + "\"/>");
      }

      writeEnd(writer);
   }

   /**
    * Read in the definition of this object from an XML tag.
    *
    * @param tag the XML Element representing this object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String val = Tool.getAttribute(tag, "datasource");

      if(val != null) {
         datasource = val;
      }

      NodeList nlist = tag.getElementsByTagName("folder");

      for(int i = 0; nlist != null && i < nlist.getLength(); i++) {
         Element node = (Element) nlist.item(i);
         addFolder(Tool.getAttribute(node, "name"));
      }
   }

   public void startWriteDTO(Map<String, Object> properties) {
      properties.put("datasource", datasource);
   }

   public void endWriteDTO(Map<String, Object> properties) {
   }

   /**
    * Create a copy of this object.
    *
    * @return a copy of this object.
    */
   @Override
   public XDataModel clone() {
      XDataModel copy = new XDataModel(datasource);
      copy.folders = this.folders;
      return copy;
   }

   /**
    * Add a folder to the data model.
    */
   public void addFolder(String folder) {
      if(!folders.contains(folder)) {
         folders.add(folder);
      }
   }

   /**
    * Remove a folder from the data model.
    */
   public void removeFolder(String folder) {
      folders.remove(folder);
   }

   /**
    * Get all folders of a data model.
    */
   public String[] getFolders() {
      return folders.toArray(new String[folders.size()]);
   }

   /**
    * Remove all the data model folders.
    */
   public void removeFolders() {
      folders.clear();
   }

   /**
    * @param user the user to check
    *
    * @return false if the user is a virtual user, true otherwise
    */
   private boolean isActualUser(Principal user) {
      boolean validUser;

      if(user != null) {
         if(user instanceof XPrincipal) {
            XPrincipal srPrincipal = (XPrincipal) user;
            validUser = !"true".equals(srPrincipal.getProperty("virtual"));
         }
         else {
            validUser = true;
         }
      }
      else {
         validUser = false;
      }

      return validUser;
   }

   public boolean partitionIsUsed(String partitionName) {
      boolean found = false;

      for(String name : getLogicalModelNames()) {
         XLogicalModel lmodel = getLogicalModel(name);

         if(partitionName.equals(lmodel.getPartition())) {
            found = true;
            break;
         }
      }

      if(!found) {
         String[] names = getVirtualPrivateModelNames();

         for(String name : names) {
            VirtualPrivateModel vpm = getVirtualPrivateModel(name);
            @SuppressWarnings("unchecked")
            Enumeration<VpmCondition> conds = vpm.getConditions();

            while(conds.hasMoreElements()) {
               VpmCondition cond = conds.nextElement();

               if(cond.getType() == VpmCondition.PHYSICMODEL &&
                  partitionName.equals(cond.getTable()))
               {
                  found = true;
                  break;
               }
            }
         }
      }

      return found;
   }

   private DataSourceRegistry getRegistry() {
      return DataSourceRegistry.getRegistry();
   }

   private String datasource;
   private ArrayList<String> folders = new ArrayList<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(XDataModel.class);
}

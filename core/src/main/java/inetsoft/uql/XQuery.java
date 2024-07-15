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
package inetsoft.uql;

import inetsoft.report.internal.Util;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XNodePath;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XQuery object represents a query in the query registry. Each query
 * must have an unique name in a registry.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class XQuery implements Serializable, Cloneable, XMLSerializable {
   /**
    * A hint to change the query max rows setting. This can be passed in
    * as a query parameter to override the static setting in the query.
    */
   public static final String HINT_MAX_ROWS = "__HINT_MAX_ROWS__";
   /**
    * A hint to change the query timeout setting. This can be passed in
    * as a query parameter to override the static setting in the query.
    */
   public static final String HINT_TIMEOUT = "__HINT_TIMEOUT__";

   /**
    * A hint indicates that the max rows is the default value defined
    * internally.
    */
   public static final String HINT_DEFAULT_MAX_ROWS = "__HINT_DEF_MAX_ROWS__";
   /**
    * A hint indicates whether this query is used for preview/live-data.
    */
   public static final String HINT_PREVIEW = "__HINT_PREVIEW__";

   /**
    * A hint indicates that the max rows definition should be ignored.
    */
   public static final String HINT_IGNORE_MAX_ROWS = "__HINT_IGNORE_MAX_ROWS__";

   /**
    * Create a query object with the specified type.
    * @param type data source type. One of values defined in XDataSource.
    */
   protected XQuery(String type) {
      this.type = type;
   }

   /**
    * Set the query name.
    */
   public void setName(String name) {
      this.name = name;

      // change the variable source name
      for(XVariable var : varmap.values()) {
         var.setSource(name);
      }
   }

   /**
    * Get the query name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the query folder. The folder is only used for displaying a query
    * and does not change the name scope of a query.
    */
   public void setFolder(String folder) {
      this.folder = folder;
   }

   /**
    * Get the query folder. If the folder is null or empty, the query is
    * displayed under the datasource root.
    */
   public String getFolder() {
      return this.folder;
   }

   /**
    * Get the query type.
    */
   public String getType() {
      return type;
   }

   /**
    * Set the data source this query is associated with.
    */
   public void setDataSource(XDataSource datasource) {
      this.datasource = datasource;
   }

   /**
    * Get the data source this query is associated with.
    */
   public XDataSource getDataSource() {
      return datasource;
   }

   /**
    * Get the output type of the query. The return value is either the
    * root of a subtree of the a type tree, or a one level tree with
    * each child representing a table column.
    *
    * @param session the session object
    * @param full true to fetch full name
    */
   public abstract XTypeNode getOutputType(Object session, boolean full);

   /**
    * Check if is output type of the query is available. For a non-parse SQL,
    * output type may not be available.
    */
   public boolean isOutputTypeAvailable() {
      return true;
   }

   /**
    * Get the output type of the query. The return value is either the
    * root of a subtree of the a type tree, or a one level tree with
    * each child representing a table column.
    *
    * @param session the session object
    */
   public XTypeNode getOutputType(Object session) {
      return getOutputType(session, false);
   }

   /**
    * Get the names of all variables need to be defined in this query.
    */
   public Enumeration<String> getDefinedVariables() {
      return getVariableNames();
   }

   /**
    * Get the names of all variables need to be defined in this query, include
    * all variables, such as build in variable.
    */
   public Enumeration<String> getAllDefinedVariables() {
      return getVariableNames0(true);
   }

   /**
    * Get the names of all variables used in this query. The variables
    * are either UserVariable or QueryVariable.
    */
   public Enumeration<String> getVariableNames() {
      return getVariableNames0(false);
   }

   private Enumeration<String> getVariableNames0(boolean all) {
      final Map<String, XVariable> ovarmap = new HashMap<>(varmap);
      varmap.clear();
      findVariables(ovarmap);

      // need to copy to the new map since we can't delete from the varmap
      // otherwise the enumeration index would be wrong
      final ConcurrentHashMap<String, XVariable> nmap = new ConcurrentHashMap<>();

      // replace the newly added variables with existing variable definition
      for(Map.Entry<String, XVariable> entry : varmap.entrySet()) {
         final String varName = entry.getKey();
         XVariable var = entry.getValue();
         XVariable ovar = ovarmap.get(varName);

         if(!all && VariableTable.isBuiltinVariable(varName)) {
            continue;
         }

         nmap.put(varName, var);

         if((ovar instanceof UserVariable) && (var instanceof UserVariable)) {
            UserVariable uvar = (UserVariable) var;
            UserVariable ouvar = (UserVariable) ovar;

            if(uvar.getValueNode() == null ||
               uvar.getValueNode().getValue() == null && ouvar.getValueNode() != null ||
               "".equals(uvar.getValueNode().getValue()) ||
               (ouvar.getValueNode() != null &&
                ouvar.getValueNode().getValue() != null &&
                !(uvar.getValueNode().getValue().equals(ouvar.getValueNode().getValue()))))
            {
               nmap.put(varName, ovar);
            }
         }
         else if(ovar instanceof QueryVariable) {
            nmap.put(varName, ovar);
         }
      }

      varmap = nmap;

      return new IteratorEnumeration<>(varmap.keySet().iterator());
   }

   /**
    * Get a variable defined in this query.
    * @param name variable name.
    * @return variable definition.
    */
   public XVariable getVariable(String name) {
      return varmap.get(name);
   }

   /**
    * Add a variable to this query.
    * @param var variable definition.
    */
   public void addVariable(XVariable var) {
      var.setSource(getName());
      varmap.put(var.getName(), var);
   }

   /**
    * Remove a variable from this query.
    * @param name variable name.
    */
   public void removeVariable(String name) {
      varmap.remove(name);
   }

   /**
    * Set the description of this data source.
    */
   public void setDescription(String desc) {
      this.desc = desc;
   }

   /**
    * Get the description of this data source.
    */
   public String getDescription() {
      return desc;
   }

   /**
    * Set the row limit (maximum number of rows) of this query.
    * No limit is placed on the number of rows if the value is zero.
    */
   public void setMaxRows(int rowlimit) {
      this.rowlimit = rowlimit;
   }

   /**
    * Get the row limit (maximum number of rows) of this query.
    */
   public int getMaxRows() {
      // Feature #39140, always respect the global row limit
      return Util.getQueryLocalRuntimeMaxrow(rowlimit);
   }

   /**
    * Set query timeout value in seconds.
    * No limit is placed on the query if the value is zero.
    */
   public void setTimeout(int seconds) {
      this.timeout = seconds;
   }

   /**
    * Get query timeout value in seconds.
    */
   public int getTimeout() {
      return timeout;
   }

   /**
    * Set if this query should be visible to end user (composer).
    */
   public void setVisible(boolean vis) {
      visible = vis;
   }

   /**
    * Check if this query is visible to end user.
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * Gets the name of the partition used to apply VPM conditions to this
    * query.
    *
    * @return the name of a partition or <code>null</code> if no VPM
    *         conditions should be applied.
    *
    * @since 6.0
    */
   public String getPartition() {
      return partition;
   }

   /**
    * Sets the name of the partition used to apply VPM conditions to this
    * query.
    *
    * @param partition the name of a partition or <code>null</code> if no VPM
    * conditions should be applied.
    *
    * @since 6.0
    */
   public void setPartition(String partition) {
      this.partition = partition;
   }

   /**
    * Revalidate the query object if context changes, for example, data source
    * registry changes.
    */
   public void revalidate() {
      // reset data source object contained in the query object
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      String dsstr = getDataSource().getFullName();
      XDataSource ds = registry.getDataSource(dsstr);

      if(ds != null) {
         setDataSource(ds);
      }
   }

   /**
    * Set a property value. Property is generic interface for attaching
    * additional information to a query object. Properties are transient and
    * is not saved as part of the query definition.
    */
   public void setProperty(String name, Object val) {
      propmap.put(name, val);
   }

   /**
    * Get a property value.
    */
   public Object getProperty(String name) {
      return propmap.get(name);
   }

   /**
    * Get a set of property keys
    */
   public Set<String> getPropertyKeys() {
      return propmap.keySet();
   }

   /**
    * Get the XSelection object.
    */
   public abstract XSelection getSelection();

   /**
    * @return true if this query depends on the current MV session, false otherwise.
    */
   public boolean dependsOnMVSession() {
      return false;
   }

   /**
    * Parse the XML element that contains information on this query.
    */
   @Override
   public void parseXML(Element root) throws Exception {
      NodeList nlist = Tool.getChildNodesByTagName(root, "variable");

      for(int i = 0; i < nlist.getLength(); i++) {
         XVariable var = XVariable.parse((Element) nlist.item(i));

         if(var.getName() != null) {
            varmap.put(var.getName(), var);
         }
      }

      nlist = Tool.getChildNodesByTagName(root, "createdBy");

      if(nlist.getLength() > 0) {
         createdBy = Tool.getValue(nlist.item(0));
      }

      nlist = Tool.getChildNodesByTagName(root, "modifiedBy");

      if(nlist.getLength() > 0) {
         modifiedBy = Tool.getValue(nlist.item(0));
      }

      nlist = Tool.getChildNodesByTagName(root, "created");

      if(nlist.getLength() > 0) {
         created = Long.parseLong(Tool.getValue(nlist.item(0)));
      }

      nlist = Tool.getChildNodesByTagName(root, "modified");

      if(nlist.getLength() > 0) {
         modified = Long.parseLong(Tool.getValue(nlist.item(0)));
      }

      nlist = Tool.getChildNodesByTagName(root, "description");

      if(nlist.getLength() > 0) {
         desc = Tool.getValue(nlist.item(0));
      }

      nlist = Tool.getChildNodesByTagName(root, "maxrows");

      if(nlist.getLength() > 0) {
         rowlimit = Integer.parseInt(Tool.getValue(nlist.item(0)));
      }

      nlist = Tool.getChildNodesByTagName(root, "timeout");

      if(nlist.getLength() > 0) {
         timeout = Integer.parseInt(Tool.getValue(nlist.item(0)));
      }

      nlist = Tool.getChildNodesByTagName(root, "visible");

      if(nlist.getLength() > 0) {
         visible = Tool.getValue(nlist.item(0)).equals("true");
      }

      nlist = Tool.getChildNodesByTagName(root, "name");

      if(nlist.getLength() > 0) {
         setName(Tool.getValue(nlist.item(0)));
      }

      nlist = Tool.getChildNodesByTagName(root, "folder");

      if(nlist.getLength() > 0) {
         setFolder(Tool.getValue(nlist.item(0)));
      }

      nlist = Tool.getChildNodesByTagName(root, "datasource");

      if(nlist.getLength() > 0) {
         DataSourceRegistry registry = DataSourceRegistry.getRegistry();
         String datasourceName = Tool.getValue(nlist.item(0));

         if(REMOTE_PUT.get()) {
            this.datasourceFullName = datasourceName;
         }

         XDataSource xds = registry.getDataSource(datasourceName);
         setDataSource(xds != null ? xds : getDataSource());
      }

      nlist = Tool.getChildNodesByTagName(root, "partition");

      if(nlist.getLength() > 0) {
         setPartition(Tool.getValue(nlist.item(0)));
      }

      // backward compatibility, before 8.0, we attach a logical model to
      // a query to find related columns and apply vpm; after 8.0, only a
      // partition is enough to find related columns
      nlist = Tool.getChildNodesByTagName(root, "logicalModel");

      if(nlist.getLength() > 0 && getPartition() == null && datasource != null) {
         String lname = Tool.getValue(nlist.item(0));

         try {
            DataSourceRegistry dr = DataSourceRegistry.getRegistry();
            XDataModel model = dr.getDataModel(datasource.getFullName());

            if(model != null) {
               XLogicalModel lmodel = model.getLogicalModel(lname);

               if(lmodel != null) {
                  setPartition(lmodel.getPartition());
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get logical model: " + datasource.getFullName(), ex);
         }
      }

      nlist = Tool.getChildNodesByTagName(root, "metadata");
      XSelection selection = getSelection();

      for(int i = 0; i < nlist.getLength(); i++) {
         Element elem = (Element) nlist.item(i);
         Element pelem = Tool.getChildNodeByTagName(elem, "path");
         Element melem = Tool.getChildNodeByTagName(elem, "XMetaInfo");

         if(selection != null && melem != null) {
            int idx = i;

            if(pelem != null) {
               // @by mikec, BC code
               // if defined path, use path instead.
               String path = Tool.getValue(pelem);
               idx = selection.indexOf(path);
            }

            XMetaInfo meta = new XMetaInfo();
            meta.parseXML(melem);

            if(idx > -1 && idx < selection.getColumnCount()) {
               selection.setXMetaInfo(idx, meta);
            }
         }
      }

      Element dsnode = Tool.getChildNodeByTagName(root, "dependencies");

      if(dsnode != null) {
         NodeList list = Tool.getChildNodesByTagName(dsnode, "assetEntry");

         for(int i = 0; i < list.getLength(); i++) {
            Element anode = (Element) list.item(i);
            AssetEntry entry = AssetEntry.createAssetEntry(anode);
            this.dependencies.add(entry);
         }
      }
   }

   /**
    * Generate the XML segment to represent this query.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      Enumeration<String> vars = getDefinedVariables();
      List<String> vnames = new ArrayList<>();

      while(vars.hasMoreElements()) {
         vnames.add(vars.nextElement());
      }

      Collections.sort(vnames);

      for(String vname : vnames) {
         XVariable var = getVariable(vname);

         if(var != null) {
            var.writeXML(writer);
         }
      }

      if(createdBy != null) {
         writer.println("<createdBy>" + Tool.escape(createdBy) + "</createdBy>");
      }

      if(modifiedBy != null) {
         writer.println("<modifiedBy>" + Tool.escape(modifiedBy) + "</modifiedBy>");
      }

      writer.println("<created>" + created + "</created>");
      writer.println("<modified>" + modified + "</modified>");

      if(desc != null) {
         writer.println("<description>" + Tool.escape(desc) + "</description>");
      }

      writer.println("<version>" + FileVersions.QUERY + "</version>");
      writer.println("<maxrows>" + rowlimit + "</maxrows>");
      writer.println("<timeout>" + timeout + "</timeout>");
      writer.println("<visible>" + visible + "</visible>");

      if(name != null) {
         writer.println("<name><![CDATA[" + name + "]]></name>");
      }

      if(folder != null) {
         writer.println("<folder><![CDATA[" + folder + "]]></folder>");
      }

      String dataSourceName = datasource == null ? REMOTE_PUT.get() ? datasourceFullName  : null : datasource.getFullName();

      if(dataSourceName != null) {
         writer.println("<datasource><![CDATA[" + dataSourceName + "]]></datasource>");
      }

      if(partition != null) {
         writer.print("<partition>");
         writer.print(partition);
         writer.println("</partition>");
      }

      XSelection selection = getSelection();

      if(selection != null) {
         for(int i = 0; i < selection.getColumnCount(); i++) {
            writer.println("<metadata>");
            selection.getXMetaInfo(i).writeXML(writer);
            writer.println("</metadata>");
         }
      }

      Object[] entries = getOuterDependencies();

      if(entries.length > 0) {
         writer.println("<dependencies>");

         for(Object entry : entries) {
            if(entry instanceof AssetEntry) {
               ((AssetEntry) entry).writeXML(writer);
            }
         }

         writer.println("</dependencies>");
      }
   }

   /**
    * Find all variables used in this query.
    */
   protected void findVariables(Map varmap) {
      // do nothing
   }

   /**
    * Find all variables in the value tree and add to the variable list.
    */
   public void findVariables(XNode root) {
      if(root == null) {
         return;
      }

      if(root instanceof XValueNode) {
         XValueNode node = (XValueNode) root;
         String vname = node.getVariable();

         if(vname != null && getVariable(vname) == null) {
            UserVariable var = new UserVariable(vname);
            XTypeNode varTypeNode = XSchema.createPrimitiveType(node.getType());

            var.setValueNode(node);

            if(varTypeNode != null) {
               var.setTypeNode(varTypeNode);
            }

            addVariable(var);
         }
      }
      else if(root instanceof XUnaryCondition) {
         findVariables(((XUnaryCondition) root).getExpression1());
      }
      else if(root instanceof XBinaryCondition) {
         findVariables(((XBinaryCondition) root).getExpression1());
         findVariables(((XBinaryCondition) root).getExpression2());
      }
      else if(root instanceof XTrinaryCondition) {
         findVariables(((XTrinaryCondition) root).getExpression1());
         findVariables(((XTrinaryCondition) root).getExpression2());
         findVariables(((XTrinaryCondition) root).getExpression3());
      }

      for(int i = 0; i < root.getChildCount(); i++) {
         findVariables(root.getChild(i));
      }
   }

   /**
    * Find all variables in the string and add to the variable list.
    */
   protected void findVariables(XExpression expression) {
      if(XExpression.EXPRESSION.equals(expression.getType())) {
         String str = (String) expression.getValue();
         List<UserVariable> vars = XUtil.findVariables(str);

         for(UserVariable var: vars) {
            if(getVariable(var.getName()) == null) {
               addVariable(var);
            }
         }
      }
   }

   /**
    * Find all variables in the path and add to the variable list.
    */
   protected void findVariables(XNodePath path) {
      if(path == null) {
         return;
      }

      String[] vnames = path.getVariables();

      for(final String vname : vnames) {
         if(getVariable(vname) == null) {
            addVariable(new UserVariable(vname));
         }
      }
   }

   /**
    * Update the query to set the local query repository to the query variable,
    * so that the local query can be found at runtime.
    */
   public void updateQueryVariable(XQueryRepository repository) {
   }

   /**
    * Get the outer dependencies.
    * @return the outer dependencies.
    */

   public Object[] getOuterDependencies() {
      Object[] arr = new Object[dependencies.size()];
      dependencies.toArray(arr);
      return arr;
   }

   /**
    * Add an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */

   public boolean addOuterDependency(AssetObject entry) {
      return dependencies.add(entry);
   }

   /**
    * Remove an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */

   public boolean removeOuterDependency(AssetObject entry) {
      return dependencies.remove(entry);
   }

   /**
    * Remove all the outer dependencies.
    */

   public void removeOuterDependencies() {
      dependencies.clear();
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
    * Create a clone of this object.
    */
   @Override
   public XQuery clone() {
      try {
         XQuery nquery = (XQuery) super.clone();
         nquery.datasource = datasource == null ? null : (XDataSource) datasource.clone();
         nquery.varmap = new ConcurrentHashMap<>(varmap);
         nquery.propmap = (HashMap<String, Object>) propmap.clone();
         nquery.dependencies = new HashSet<>(dependencies);

         return nquery;
      }
      catch(CloneNotSupportedException ex) {
         LOG.error("Failed to clone XQuery", ex);
      }

      return null;
   }

   /**
    * Get the hash code value.
    */
   public int hashCode() {
      return (name == null) ? super.hashCode() : name.hashCode();
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(obj instanceof XQuery) {
         String objName = ((XQuery) obj).getName();
         return Objects.equals(name, objName);
      }

      return super.equals(obj);
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "XQuery:[" + name + "]";
   }

   private String name; // query name
   private String folder; // query folder, for display only
   private String type; // query type
   private XDataSource datasource; // associated datasource
   private String datasourceFullName; // associated datasource full name if the datasource do not exist.
   private ConcurrentHashMap<String, XVariable> varmap = new ConcurrentHashMap<>(); // var name -> XVariable
   private String desc; // description
   private int rowlimit = 0; // max rows
   private int timeout = 0; // query timeout
   private boolean visible = true; // visible in composer
   private String partition = null; // associated partition
   private HashMap<String, Object> propmap = new HashMap<>(); // properties
   private HashSet<AssetObject> dependencies = new HashSet<>(); // outer dependencies

   private long created;
   private long modified;
   private String createdBy;
   private String modifiedBy;

   public static final ThreadLocal<Boolean> REMOTE_PUT = ThreadLocal.withInitial(() -> Boolean.FALSE);

   private static final Logger LOG = LoggerFactory.getLogger(XQuery.class);
}

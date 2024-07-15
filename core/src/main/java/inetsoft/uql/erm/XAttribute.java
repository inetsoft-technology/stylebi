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
package inetsoft.uql.erm;

import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.DependencyException;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;

/**
 * An XAttribute represents an attribute of an entity in a data model. Every
 * XAttribute is mapped to a single column in the data source. Additionally,
 * the data type for the attribute can be specified.
 *
 * @author  InetSoft Technology Corp.
 * @since   4.4
 */
public class XAttribute implements Cloneable, Serializable, XMLSerializable {
   /**
    * Property name for the name of the attribute.
    */
   public static final String NAME = "ATTRIBUTE_NAME";

   /**
    * Property name for the name of the browse data query.
    */
   public static final String BROWSE_DATA_QUERY = "ATTRIBUTE_BROWSE_DATA_QUERY";

   /**
    * Property name for the browseable property.
    */
   public static final String BROWSEABLE = "ATTRIBUTE_BROWSEABLE";

   /**
    * Property name for the description of the attribute.
    */
   public static final String DESCRIPTION = "ATTRIBUTE_DESCRIPTION";

   /**
    * Property name of the data type of the attribute.
    */
   public static final String DATA_TYPE = "ATTRIBUTE_DATA_TYPE";

   /**
    * Property name of the mapped column of the attribute.
    */
   public static final String MAPPED_COLUMN = "ATTRIBUTE_MAPPED_COLUMN";

   /**
    * Property name of the meta data of the attribute.
    */
   public static final String META_DATA = "META_DATA";

   /**
    * Creates a new instance of XAttribute. Default constructor that should
    * only be used when loading the attribute from an XML file.
    */
   XAttribute() {
      super();
   }

   /**
    * Creates a new instance of XAttribute with a default data type of String.
    *
    * @param name the name of this attribute. The name should be in a human
    *             readable format and allow a user to infer its meaning and
    *             useage.
    * @param table the name of the database table this attribute maps into.
    * @param column the name of the database column this attribute maps into.
    */
   public XAttribute(String name, String table, String column) {
      this(name, table, column, XSchema.STRING);
   }

   /**
    * Creates a new instance of XAttribute with the specified data type.
    *
    * @param name the name of this attribute. The name should be in a human
    *             readable format and allow a user to infer its meaning and
    *             useage.
    * @param table the name of the database table this attribute maps into.
    * @param column the name of the database column this attribute maps into.
    * @param type the data type of this attribute. Must be one of the data
    *             type constants defined in
    *             {@link inetsoft.uql.schema.XSchema}.
    */
   public XAttribute(String name, String table, String column, String type) {
      this.name = name;
      this.table = table;
      this.column = column;
      this.type = type;
   }

   /**
    * Sets the name of this attribute. The name should be a human readable and
    * allow the user to infer the attributes usage.
    *
    * @param name the name of the attribute.
    */
   public void setName(String name) {
      String oname = this.name;
      this.name = name;
      firePropertyChange(NAME, oname, name);
   }

   /**
    * Gets the name of this attribute.
    *
    * @return the name of this attribute.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the nameChanged of this attribute.
    *
    * @param nameChanged the nameChanged of this attribute.
    */
   public void setNameChanged(boolean nameChanged) {
      this.nameChanged = nameChanged;
   }

   /**
    * Gets the nameChanged of this attribute.
    *
    * @return the attribute's nameChanged.
    */
   public boolean isNameChanged() {
      return nameChanged;
   }

   /**
    * Sets the oldName of this attribute.
    *
    * @param oldName the oldName of this attribute.
    */
   public void setOldName(String oldName) {
      this.oldName = oldName;
   }

   /**
    * Gets the old name of this attribute.
    *
    * @return the attribute's oldName.
    */
   public String getOldName() {
      return oldName;
   }

   /**
    * Check if the attribute is an expression.
    *
    * @return <tt>true</tt> if is an expression, <tt>false</tt> otherwise.
    */
   public boolean isExpression() {
      return false;
   }

   /**
    * Set the query to use for retrieve a list of values for Browse Data.
    *
    * @param query the name of the query.
    */
   public void setBrowseDataQuery(String query) {
      String oquery = browseQuery;
      browseQuery = query;
      firePropertyChange(BROWSE_DATA_QUERY, oquery, query);
   }

   /**
    * Get the query to use for retrieve a list of values for Browse Data.
    *
    * @return the name of the query.
    */
   public String getBrowseDataQuery() {
      return browseQuery;
   }

   /**
    * Set whether this attribute can be browsed by end user. By default it's
    * true. If this attribute is in a very large table, this property can be
    * turned off to avoid excessive long queries.
    *
    * @param browse <code>true</code> if this attribute is browsable.
    */
   public void setBrowseable(boolean browse) {
      Boolean obrowse = Boolean.valueOf(browseable);
      this.browseable = browse;
      firePropertyChange(BROWSEABLE, obrowse, Boolean.valueOf(browse));
   }

   /**
    * Check if this attribute can be browsed.
    *
    * @return <code>true</code> if this attribute is browsable.
    */
   public boolean isBrowseable() {
      return browseable;
   }

   /**
    * Sets the description for this attribute. The description should tell a
    * user the intended usage of this attribute and the type of information it
    * provides.
    *
    * @param description a description of this attribute.
    */
   public void setDescription(String description) {
      String odesc = this.description;
      this.description = description;
      firePropertyChange(DESCRIPTION, odesc, description);
   }

   /**
    * Gets a description of this attribute. The description contains
    * information on the intended usage of this attribute and the type of data
    * it provides.
    *
    * @return a description of this attribute.
    */
   public String getDescription() {
      return description;
   }

   /**
    * Maps this attribute to the specified column in the data source.
    *
    * @param table the name of the table to map into.
    * @param column the name of the column to map into.
    */
   public void mapToColumn(String table, String column) {
      String omapping = this.table + "." + this.column;
      String mapping = table + "." + column;
      this.table = table;
      this.column = column;
      firePropertyChange(MAPPED_COLUMN, omapping, mapping);
   }

   /**
    * Gets the name of the database table this attribute is mapped to.
    *
    * @return the name of a database table.
    */
   public String getTable() {
      return table;
   }

   public void setTable(String table) {
      this.table = table;
   }

   /**
    * Get the name of the database column this attribute is mapped to.
    *
    * @return the name of a database column.
    */
   public String getColumn() {
      return column;
   }

   /**
    * Gets the name of all database tables referenced by this attribute.
    *
    * @return an array of String objects that always contains the result of
    *         getTable().
    */
   public String[] getTables() {
      return new String[] { getTable() };
   }

   /**
    * Ge the full name of all datatable columns referenced by this attribute.
    */
   public String[] getColumns() {
      return new String[] {table + "." + column};
   }

   /**
    * Sets the data type of this attribute. Values must be one of the data type
    * constants defined in {@link inetsoft.uql.schema.XSchema}.
    *
    * @param type the data type.
    */
   public void setDataType(String type) {
      String otype = this.type;
      this.type = type;
      firePropertyChange(DATA_TYPE, otype, type);
   }

   /**
    * Gets the data type of this attribute.
    *
    * @return one of the data type constants defined in
    *         {@link inetsoft.uql.schema.XSchema}
    */
   public String getDataType() {
      return type == null ? XSchema.STRING : type;
   }

   /**
    * Sets the meta data type of this attribute.
    *
    * @param meta the meta data.
    */
   public void setXMetaInfo(XMetaInfo meta) {
      XMetaInfo ometa = this.meta;
      this.meta = meta;
      firePropertyChange(META_DATA, ometa, meta);
   }

   /**
    * Get the meta data of this attribute.
    *
    * @return the meta data.
    */
   public XMetaInfo getXMetaInfo() {
      if(meta == null) {
         meta = new XMetaInfo();
      }

      return meta;
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

   public boolean addOuterDependency(Object entry) {
      return dependencies.add(entry);
   }

   /**
    * Remove an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */

   public boolean removeOuterDependency(Object entry) {
      return dependencies.remove(entry);
   }

   /**
    * Remove all the outer dependencies.
    */

   public void removeOuterDependencies() {
      dependencies.clear();
   }

   /**
    * Writes the XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<attribute name=\"" + Tool.escape(getName()) +
         "\" type=\"" + getDataType() + "\" browse=\"" + browseable +
         "\" refType=\"" + getRefType() + "\" formula=\"" + formula + "\">");

      if(getDescription() != null) {
         writer.print("<description>");
         writer.print("<![CDATA[");
         writer.print(getDescription());
         writer.print("]]>");
         writer.println("</description>");
      }

      writer.print("<table>");
      writer.print("<![CDATA[");
      writer.print(getTable());
      writer.print("]]>");
      writer.println("</table>");

      writer.print("<column>");
      writer.print("<![CDATA[");
      writer.print(getColumn());
      writer.print("]]>");
      writer.println("</column>");

      if(getBrowseDataQuery() != null) {
         writer.print("<browseDataQuery>");
         writer.print("<![CDATA[");
         writer.print(getBrowseDataQuery());
         writer.print("]]>");
         writer.println("</browseDataQuery>");
      }

      if(meta != null && !meta.isEmpty()) {
         meta.writeXML(writer);
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

      writer.println("</attribute>");
   }

   /**
    * Reads in an attribute definition from its XML representation.
    *
    * @param tag the XML Element for this object.
    *
    * @throws Exception if an error occurs while parsing the XML element.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String attr = null;

      if((attr = Tool.getAttribute(tag, "name")) != null) {
         setName(attr);
      }

      if((attr = Tool.getAttribute(tag, "type")) != null) {
         setDataType(attr);
      }

      if((attr = Tool.getAttribute(tag, "browse")) != null) {
         setBrowseable(attr.equals("true"));
      }

      if((attr = Tool.getAttribute(tag, "refType")) != null) {
         setRefType(Integer.parseInt(attr));
      }

      if((attr = Tool.getAttribute(tag, "formula")) != null) {
         setDefaultFormula(Tool.getAttribute(tag, "formula"));
      }

      NodeList nl = Tool.getChildNodesByTagName(tag, "description");

      if(nl != null && nl.getLength() > 0) {
         setDescription(Tool.getValue(nl.item(0)));
      }

      String tbl = null;
      String col = null;

      nl = Tool.getChildNodesByTagName(tag, "table");

      if(nl != null && nl.getLength() > 0) {
         tbl = Tool.getValue(nl.item(0));
         tbl = tbl == null || tbl.equals("") ? null : tbl;
      }

      nl = Tool.getChildNodesByTagName(tag, "column");

      if(nl != null && nl.getLength() > 0) {
         col = Tool.getValue(nl.item(0));
         col = col == null || col.equals("") ? null : col;
      }

      nl = Tool.getChildNodesByTagName(tag, "browseDataQuery");

      if(nl != null && nl.getLength() > 0) {
         browseQuery = Tool.getValue(nl.item(0));
      }

      mapToColumn(tbl, col);

      Element elem = Tool.getChildNodeByTagName(tag, "XMetaInfo");

      if(elem != null) {
         meta = new XMetaInfo();
         meta.parseXML(elem);
      }

      Element dsnode = Tool.getChildNodeByTagName(tag, "dependencies");

      if(dsnode != null) {
         NodeList list = Tool.getChildNodesByTagName(dsnode, "assetEntry");

         for(int i = 0; list != null && i < list.getLength(); i++) {
            Element anode = (Element) list.item(i);
            AssetEntry entry = AssetEntry.createAssetEntry(anode);
            this.dependencies.add(entry);
         }
      }
   }

   /**
    * Gets a textual representation of this attribute. For user interface
    * purposes use the {@link #getName()} method.
    *
    * @return a string representation of this object. This value will have the
    *         format <CODE>XAttribute: <I>attribute name</I></CODE>.
    */
   public String toString() {
      return "XAttribute: " + name + " desc:" + description +
             " table:" + table + " column:" + column + " type:"+ type +
             " browseable:" + browseable + " browseQuery:" + browseQuery +
             " meta:" + meta;
   }

   /**
    * Creates and returns a copy of this attribute object.
    *
    * @return a clone of this instance.
    */
   @Override
   public Object clone() {
      try {
         XAttribute attr = (XAttribute) super.clone();
         attr.propertyListeners = null;
         attr.setID(id);
         attr.meta = meta == null ? null : (XMetaInfo) meta.clone();
         return attr;
      }
      catch(Exception ex) {
         // shoud never happen
      }

      return null;
   }

   /*
    * Determines if this object is equivilent to another object.
    *
    * @param obj the reference object with which to compare.
    *
    * @return <code>true</code> if the objects are are equivilent,
    *         <code>false</code> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof XAttribute)) {
         return false;
      }

      XAttribute xattr = (XAttribute) obj;

      return this.getName().equals(xattr.getName()) &&
             this.getTable().equals(xattr.getTable()) &&
             this.getColumn().equals(xattr.getColumn()) &&
             this.getXMetaInfo().equals(xattr.getXMetaInfo());
   }

   /**
    * Adds a listener that is notified when a property of this attribute is
    * changed.
    *
    * @param l the listener to add.
    */
   public void addPropertyChangeListener(PropertyChangeListener l) {
      if(propertyListeners == null) {
         propertyListeners = new HashSet();
      }

      propertyListeners.add(l);
   }

   /**
    * Removes a property change listener from the notification list.
    *
    * @param l the listener to remove.
    */
   public void removePropertyChangeListener(PropertyChangeListener l) {
      if(propertyListeners == null) {
         return;
      }

      propertyListeners.remove(l);
   }

   /**
    * Removes all registered property change listeners from the notification
    * list.
    */
   public void removeAllPropertyChangeListeners() {
      if(propertyListeners == null) {
         return;
      }

      propertyListeners.clear();
   }

   /**
    * Notifies all registered listeners that a property of this attribute has
    * been changed.
    *
    * @param property the name of the property.
    * @param oldValue the old value of the property.
    * @param newValue the new value of the property.
    */
   private void firePropertyChange(String property, Object oldValue,
                                   Object newValue) {
      // nothing actually changed
      if((oldValue == null && newValue == null) ||
         (oldValue != null && newValue != null && oldValue.equals(newValue)))
      {
         return;
      }

      if(propertyListeners == null) {
         return;
      }

      PropertyChangeEvent evt = null;
      Iterator it = propertyListeners.iterator();

      while(it.hasNext()) {
         if(evt == null) {
            evt = new PropertyChangeEvent(this, property, oldValue, newValue);
         }

         ((PropertyChangeListener) it.next()).propertyChange(evt);
      }
   }

   /**
    * Set the ref type.
    */
   public void setRefType(int refType) {
      this.refType = refType;
   }

   /**
    * Get the ref type.
    *
    * @return the ref type, one of NONE, DIMENSION and MEASURE.
    */
   public int getRefType() {
      return refType;
   }

   /**
    * Get the default formula.
    */
   public String getDefaultFormula() {
      return formula;
   }

   /**
    * Set the default formula.
    */
   public void setDefaultFormula(String formula) {
      this.formula = formula;
   }

   /**
    * Set the runtime entity name.
    * @hidden
    */
   public void setEntity(String entity) {
      this.entity = entity;
   }

   /**
    * Get the runtime entity name.
    * @hidden
    */
   public String getEntity() {
      return entity;
   }

   /**
    * Return xAttribute's outer dependencies.
    */
   public DependencyException getDependencyException() {
      Object[] entries = getOuterDependencies();

      if(entries == null || entries.length == 0) {
         return null;
      }

      DependencyException ex = new DependencyException(this);
      ex.addDependencies(entries);
      return ex;
   }

   // Get the id for attribute so to update dependency.
   public String getID() {
      return id;
   }

   // Set the id for attribute so to update dependency.
   public void setID(String id) {
      this.id = id;
   }

   private int refType;
   private String formula = "None";
   private String description;
   private String name;
   private String id;
   private String oldName = "";
   private boolean nameChanged = false;
   private String table;
   private String column;
   private String type;
   private boolean browseable = true; // allow browse in composer
   private String browseQuery; // browse data query
   private XMetaInfo meta = null; // meta data
   private transient HashSet propertyListeners = null;
   private transient String entity;
   private HashSet dependencies = new HashSet(); // outer dependencies

   private static final Logger LOG = LoggerFactory.getLogger(XAttribute.class);
}

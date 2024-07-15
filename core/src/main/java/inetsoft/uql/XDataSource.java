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
package inetsoft.uql;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.text.ParseException;
import java.util.*;

/**
 * XDataSource defines the API for all data sources. All data source
 * classes are extended from this class, and may choose to add more
 * attributes suitable for that partiular data source type.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class XDataSource implements Serializable, Cloneable, XMLSerializable {
   /**
    * JDBC (SQL) data source.
    */
   public static final String JDBC = "jdbc";
   /**
    * XMLA data source.
    */
   public static final String XMLA = "xmla";
   /**
    * Domain not supported.
    */
   public static final int DOMAIN_NONE = 0;
   /**
    * Default relational SQL based domain.
    */
   public static final int DOMAIN_SQL = -1;
   /**
    * Oracle olap domain.
    */
   public static final int DOMAIN_ORACLE = 0x1;
   /**
    * Db2 olap domain.
    */
   public static final int DOMAIN_DB2 = 0x4;
   /**
    * SQL server olap domain.
    */
   public static final int DOMAIN_SQLSERVER = 0x7;

   public XDataSource() {
      this(null);
   }

   /**
    * Create a data source of the specified type.
    */
   protected XDataSource(String type) {
      this.type = type;
   }

   /**
    * Get the data source connection parameters.
    */
   public abstract UserVariable[] getParameters();

   /**
    * Set the data source name. The name of a data source must be unique
    * in a given registry.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the data source name.
    */
   public String getName() {
      return DataSourceFolder.getDisplayName(name);
   }

   /**
    * Get the data source full name.
    */
   public String getFullName() {
      return name;
   }

   /**
    * Get the type of the data source.
    * @return data source type, one of the value defined in this class.
    */
   public String getType() {
      return type;
   }

   /**
    * Get the data source implementation type. This is same as getType() by default.
    * But could return a the base type for a class of data source such as "Rest",
    * while getType() returns specific source such as "GitHub".
    */
   public String getBaseType() {
      return type;
   }

   /**
    * Get the domain type associated with this datasource. Return DOMAIN_NONE
    * if this datasource does not support domain.
    */
   public int getDomainType() {
      return DOMAIN_NONE;
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
   public IdentityID getCreatedBy() {
      return createdBy;
   }

   /**
    * Set the created person
    * @param createdBy the created person.
    */
   public void setCreatedBy(IdentityID createdBy) {
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
    * Add a query folder to the datasource.
    */
   public void addFolder(String folder) {
      if(!folders.contains(folder)) {
         folders.add(folder);
      }
   }

   /**
    * Remove a query folder from the datasource.
    */
   public void removeFolder(String folder) {
      folders.remove(folder);
   }

   /**
    * Get all folders of a datasource.
    */
   public String[] getFolders() {
      return folders.toArray(new String[folders.size()]);
   }

   /**
    * Remove all the query folders.
    */
   public void removeFolders() {
      folders.clear();
   }

   /**
    * Parse the XML element that contains information on this
    * data source.
    */
   @Override
   public void parseXML(Element root) throws Exception {
      NodeList nlist = Tool.getChildNodesByTagName(root, "description");

      if(nlist.getLength() > 0) {
         desc = Tool.getValue(nlist.item(0));
      }

      nlist = Tool.getChildNodesByTagName(root, "createdUsername");

      if(nlist.getLength() > 0) {
         createdBy = IdentityID.getIdentityIDFromKey(Tool.getValue(nlist.item(0)));
      }

      nlist = Tool.getChildNodesByTagName(root, "createdBy");

      if(nlist.getLength() > 0 && createdBy == null) {
         createdBy = IdentityID.getIdentityIDFromKey(Tool.getValue(nlist.item(0)));
      }

      nlist = Tool.getChildNodesByTagName(root, "createdDate");

      if(nlist.getLength() > 0) {
         Date dateData = getDateData(Tool.getValue(nlist.item(0)));
         created = dateData == null ? 0 : dateData.getTime();
      }

      nlist = Tool.getChildNodesByTagName(root, "created");

      if(nlist.getLength() > 0 && created == 0) {
         created = Long.parseLong(Tool.getValue(nlist.item(0)));
      }

      nlist = Tool.getChildNodesByTagName(root, "modifiedBy");

      if(nlist.getLength() > 0) {
         modifiedBy = Tool.getValue(nlist.item(0));
      }

      nlist = Tool.getChildNodesByTagName(root, "modified");

      if(nlist.getLength() > 0) {
         modified = Long.parseLong(Tool.getValue(nlist.item(0)));
      }

      nlist = root.getElementsByTagName("folder");

      for(int i = 0; nlist != null && i < nlist.getLength(); i++) {
         Element node = (Element) nlist.item(i);
         addFolder(Tool.getAttribute(node, "name"));
      }

      Element dsnode = Tool.getChildNodeByTagName(root, "dependencies");

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
    * Generate the XML segment to represent this data source.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<version>" + FileVersions.DATASOURCE + "</version>");

      if(desc != null) {
         writer.println("<description>" + Tool.escape(desc) + "</description>");
      }

      if(createdBy != null) {
         writer.println("<createdBy>" + Tool.escape(createdBy.convertToKey()) + "</createdBy>");
      }

      if(modifiedBy != null) {
         writer.println("<modifiedBy>" + Tool.escape(modifiedBy) + "</modifiedBy>");
      }

      writer.println("<created>" + created + "</created>");
      writer.println("<modified>" + modified + "</modified>");

      for(String folder : folders) {
         writer.println("<folder name=\"" + Tool.escape(folder) + "\"/>");
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
    * Retrieves the data from the XML Element and converts it to a Date.
    * @param value the element with date data
    * @return  the date, or null if no data or parse error
    */
   private Date getDateData(String value) {
      try {
         if(value != null) {
            return Tool.parseDateTime(value);
         }
      }
      catch (ParseException e) {
         // ignore
      }

      return null;
   }

   /**
    * Create a clone of this object.
    */
   @Override
   @SuppressWarnings("unchecked")
   public Object clone() {
      try {
         XDataSource source = (XDataSource) super.clone();
         source.folders = (ArrayList<String>) folders.clone();
         source.dependencies = new HashSet<>(dependencies);
         source.fromPortal = fromPortal;
         return source;
      }
      catch(CloneNotSupportedException ex) {
         LOG.error("Failed to clone XDataSource", ex);
      }

      return null;
   }

   /**
    * Check if connection information has changed.
    */
   public boolean equalsConnection(XDataSource dx) {
      // this only concerns jdbc connections presently. if other data source types require
      // resetting connection, need to override this (and possibly changing the reset logic
      // to only reset the connections for changed data source).
      return true;
   }

   /**
    * Get the hash code value.
    */
   @Override
   public int hashCode() {
      int hash = (name == null) ? super.hashCode() : name.hashCode();

      if(fromPortal != null) {
         hash += fromPortal.hashCode();
      }
      return hash;
    }

    /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(obj instanceof XDataSource) {
         String fullName = ((XDataSource) obj).getFullName();
         Boolean fromPortal = ((XDataSource) obj).fromPortal;

         return name != null && name.equals(fullName) && fromPortal == this.fromPortal;
      }

      return super.equals(obj);
   }

   /**
    * Get the string representation.
    */
   @Override
   public String toString() {
      return "XDataSource:[" + getName() + "]";
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
    * Check whether from portal.
    * @return
    */
   public Boolean isFromPortal() {
      return fromPortal;
   }

   /**
    * Set from portal.
    * @param fromPortal
    */
   public void setFromPortal(Boolean fromPortal) {
      this.fromPortal = fromPortal;
   }

   private String name;
   private String type;
   private String desc;
   private long created;
   private long modified;
   private IdentityID createdBy;
   private String modifiedBy;
   private ArrayList<String> folders = new ArrayList<>();
   private HashSet dependencies = new HashSet();
   private Boolean fromPortal = false; // for portal data model.
   private static final Logger LOG = LoggerFactory.getLogger(XDataSource.class);
}

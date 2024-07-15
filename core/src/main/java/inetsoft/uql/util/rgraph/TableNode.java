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
package inetsoft.uql.util.rgraph;

import inetsoft.uql.XNode;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;
import java.util.List;
import java.util.*;

/**
 * TableNode is the logic table containing table related information but
 * has not visual representation.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TableNode implements Serializable, Cloneable {
   /**
    * Create a table with the specified name.
    */
   public TableNode(String name) {
      super();
      setName(name);
      setAlias(name);
   }

   /**
    * Set the table name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the table name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the table alias.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Get the table alias.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Add a column to the table.
    */
   public void addColumn(String cname) {
      addColumn(cname, null);
   }

   /**
    * Add a column to the table.
    */
   public void addColumn(String cname, Object uobj) {
      cols.addElement(cname);
      objs.addElement(uobj);
   }

   /**
    * Get the number of columns.
    */
   public int getColumnCount() {
      return cols.size();
   }

   /**
    * Get the column name.
    */
   public String getColumn(int idx) {
      return cols.elementAt(idx);
   }

   /**
    * Find a column by name
    */
   public int findColumn(String name) {
      return cols.indexOf(name);
   }

   /**
    * Get the user object for a column.
    */
   public Object getUserObject(int idx) {
      return objs.elementAt(idx);
   }

   /**
    * Add relations to other tables.
    * @param col the source column name.
    * @param relArr the foreign keys.
    */
   public void addForeignKeys(String col, List relArr) {
      relStrs.put(col, relArr);
   }

   /**
    * Get the relations for the column.
    */
   public List getForeignKeys(String col) {
      return (List) relStrs.get(col);
   }

   /**
    * Returns true if primary-foreign key info is available
    */
   public boolean isKeyMetaDataAvailable() {
      return relStrs.size() > 0;
   }

    /**
     * Add column type
     */
    public void addColumnType(String name, String type) {
        colType.put(name, type);
    }

    /**
     * Get column type
     */
    public String getColumnType(String name) {
        return colType.get(name);
    }

   /**
    * Add a relation from the column to another talbe/column.
    */
   public void addRelation(String cname, Relation dest) {
      // don't add relation to the same dest twice
      Relation[] dests = dest.getTable().getRelations(dest.getColumn());

      for(int i = 0; i < dests.length; i++) {
         if(dests[i].getTable().equals(this) &&
            dests[i].getColumn().equals(cname))
         {
            return;
         }
      }

      Vector<Relation> vec = rels.computeIfAbsent(cname, k -> new Vector<>());

      // don't add relation to the same dest twice
      for(int i = 0; i < vec.size(); i++) {
         Relation r = vec.get(i);

         if(r.getTable().equals(dest.getTable()) &&
            r.getColumn().equals(dest.getColumn()))
         {
            return;
         }
      }

      vec.addElement(dest);
   }

   /**
    * Remove a relation.
    */
   public void removeRelation(String cname, Relation dest) {
      Vector<Relation> vec = rels.get(cname);

      if(vec != null) {
         vec.removeElement(dest);

         if(vec.size() == 0) {
            rels.remove(cname);
         }
      }
   }

   /**
    * Remove all relations to the specified table.
    */
   public void removeRelationToTable(TableNode table) {
      for(int i = 0; i < getColumnCount(); i++) {
         Relation[] tc = getRelations(getColumn(i));

         if(tc != null) {
            for(int j = 0; j < tc.length; j++) {
               if(tc[j].getTable().equals(table)) {
                  removeRelation(getColumn(i), tc[j]);
               }
            }
         }
      }
   }

   /**
    * Get all targets for all relations in the table.
    */
   public Relation[] getRelations() {
      List<Relation> vv = new ArrayList<>();
      Enumeration<Vector<Relation>> allv = rels.elements();

      while(allv.hasMoreElements()) {
         Vector<Relation> vec = allv.nextElement();

         for(int i = 0; i < vec.size(); i++) {
            vv.add(vec.elementAt(i));
         }
      }

      return vv.toArray(new Relation[0]);
   }

   /**
    * Get the targets for all relations from the column.
    */
   public Relation[] getRelations(String cname) {
      Vector<Relation> vec = rels.get(cname);

      if(vec != null) {
         Relation[] arr = new Relation[vec.size()];

         vec.copyInto(arr);
         return arr;
      }

      return new Relation[0];
   }

   /**
    * Remove all relations.
    */
   public void removeAllRelations() {
      rels.clear();
   }

   /**
    * Set the user object associated with this table node.
    */
   public void setUserObject(Object obj) {
      userObj = obj;
   }

   /**
    * Get the user object associated with this table node.
    */
   public Object getUserObject() {
      return userObj;
   }

   /**
    * Set the location of this table node.
    * @param loc the location of the top left corner of the table.
    */
   public void setLocation(Point loc) {
      location = loc;
   }

   /**
    * Get the location of with this table node.
    * @return the top left corner of the table.
    */
   public Point getLocation() {
      return location;
   }

   /**
    * Set the location of this table node.
    * @param loc the location of the top left corner of the table.
    */
   public void setScrollLocation(Point loc) {
      scrollLocation = loc;
   }

   /**
    * Get the location of with this table node.
    * @return the top left corner of the table.
    */
   public Point getScrollLocation() {
      return scrollLocation;
   }

   /**
    * Set the type with this table node.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Get the type with this table node.
    */
   public String getType() {
      return type;
   }

   /**
    * Copies the column meta-data into the specified node.
    *
    * @param node the node into which the column meta-data will be copied.
    */
   public void copyColumnsInto(XNode node) {
      for(int i = 0; i < getColumnCount(); i++) {
         XTypeNode xNode = new XTypeNode();
         String column = getColumn(i);
         xNode.setName(column);
         xNode.setValue(getUserObject(i));
         xNode.setAttribute(column + COLUMN_TYPE_SUFFIX, getColumnType(column));
         node.addChild(xNode);
      }
   }

   /**
    * Two tables are equal if they have the same name.
    */
   public boolean equals(Object obj) {
      if(obj instanceof TableNode) {
         TableNode tnode = (TableNode) obj;

         if(tnode.alias != alias && (tnode.alias != null || alias != null)) {
            return false;
         }

         return tnode.name.equals(name) && tnode.alias.equals(alias);
      }

      return false;
   }

   public String toString() {
      String str = "TableNode: " + name;

      if(alias != null) {
         str += " as " + alias;
      }

      return str + "[" + cols + "]";
   }

   @Override
   public Object clone() {
      try {
         TableNode node = (TableNode) super.clone();

         node.cols = Tool.deepCloneCollection(cols);
         node.objs = Tool.deepCloneCollection(objs);
         node.colType = Tool.deepCloneMap(colType);
         node.relStrs = Tool.deepCloneMap(relStrs);
         node.rels = Tool.deepCloneMap(rels);
         node.location = (Point) location.clone();
         node.type = this.type;
         node.scrollLocation = (Point) scrollLocation.clone();

         return node;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone TableNode", ex);
      }

      return null;
   }

   private String name;
   private String alias;
   private String type;
   private Vector<String> cols = new Vector<>(); // columns
   private Vector<Object> objs = new Vector<>(); // user objects
   private Hashtable<String, String> colType = new Hashtable<>(); // columnName -> type
   private Hashtable relStrs = new Hashtable(); // colName=>Vector({T,C},{T,C})
   private Hashtable<String, Vector<Relation>> rels = new Hashtable<>(); // cname -> Vector of TableColumn
   private Object userObj; // user object
   private Point location = new Point(-1, -1); // top left corner of the table
   private Point scrollLocation = new Point(0, 0);

   public static final String COLUMN_TYPE_SUFFIX = ".^type^";

   private static final Logger LOG =
      LoggerFactory.getLogger(TableNode.class);
}

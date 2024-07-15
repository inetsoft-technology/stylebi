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
package inetsoft.uql.xmla;

import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTableNode;
import inetsoft.uql.table.XObjectColumn;
import inetsoft.uql.table.XTableColumnCreator;

import java.io.*;
import java.util.*;

/**
 * This class converts an xml element to a table node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XMLATableNode extends XTableNode implements Serializable, Cloneable {
   /**
    * Constructor.
    */
   public XMLATableNode() {
      super();
   }

   /**
    * Constructor.
    * @param handler a handler handles result from XMLA execute method.
    */
   public XMLATableNode(ExecuteHandler handler) {
      data = handler.getData();
      types = handler.getTypes();
   }

   /**
    * Constructor.
    * @param c a collection of members from xmla discover method.
    * @param header table column header.
    */
   public XMLATableNode(Collection<MemberObject> c, String header) {
      data = new Object[c.size() + 1][1];
      data[0][0] = header;
      Iterator<MemberObject> it = c.iterator();
      int idx = 1;

      while(it.hasNext()) {
         data[idx][0] = it.next();
         idx++;
      }

      types = new Class[1];
      types[0] = String.class;
   }

   /**
    * Find out member object with its unique name if any.
    * @param uName member unique name or full caption.
    * @return member object if found.
    */
   public MemberObject findMember(String uName, boolean uNameOnly) {
      if(getColCount() != 1 || uName == null) {
         return null;
      }

      if(uNameOnly) {
      	 if(map == null) {
            createMap();
         }

         return map.get(uName);
      }

      for(int i = 1; i < data.length; i++) {
         if(!(data[i][0] instanceof MemberObject)) {
            return null;
         }

         MemberObject mobj = (MemberObject) data[i][0];

         if(uName.equals(mobj.uName)) {
            return mobj;
         }

         if(XMLAUtil.isIdentity(uName, mobj)) {
            return mobj;
         }

         if(uName.equals(mobj.caption)) {
            return mobj;
         }
      }

      return null;
   }

   /**
    * Create map.
    */
   private void createMap() {
      map = new HashMap();

      for(int i = 1; i < data.length; i++) {
         if(!(data[i][0] instanceof MemberObject)) {
            continue;
         }

         MemberObject mobj = (MemberObject) data[i][0];
         map.put(mobj.uName, mobj);
      }
   }

   /**
    * Do summary for all data, for if has condition for higher level of data,
    * always select the higher datas, and get lower level's data from the level
    * value, so here should summary them.
    */
   public void groupAll() {
      data = XMLAUtil.groupAll(this, data);
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    * @return true if there are more rows.
    */
   @Override
   public boolean next() {
      row++;

      return row < data.length;
   }

   /**
    * Get the number of columns in the table.
    */
   @Override
   public int getColCount() {
      return data[0].length;
   }

   /**
    * Get the column name.
    * @param col column index.
    * @return column name or alias if defined.
    */
   @Override
   public String getName(int col) {
      return (String) data[0][col];
   }

   /**
    * Get the column type.
    * @param col column index.
    * @return column data type.
    */
   @Override
   public Class getType(int col) {
      return types[col];
   }

   /**
    * Get the value in the current row at the specified column.
    * @param col column index.
    * @return column value.
    */
   @Override
   public Object getObject(int col) {
      return data[row][col];
   }

   /**
    * Get the meta info at the specified column.
    * @param col column index.
    * @return the meta info.
    */
   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return null;
   }

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    * @return true if the rewinding is successful.
    */
   @Override
   public boolean rewind() {
      row = -1;

      return true;
   }

   /**
    * Check if the cursor can be rewinded.
    * @return true if the cursor can be rewinded.
    */
   @Override
   public boolean isRewindable() {
      return true;
   }

   @Override
   public XTableColumnCreator getColumnCreator(int col) {
      return XObjectColumn.getCreator();
   }

   /**
    * Get a cloned object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      XMLATableNode table = (XMLATableNode) super.clone();

      if(isCachedMembers()) {
         table.data = new Object[data.length][1];
         table.data[0][0] = data[0][0];

         for(int i = 1; i < table.data.length; i++) {
            table.data[i][0] = data[i][0];
         }
      }

      return table;
   }

   /**
    * Write object.
    */
   private void writeObject(ObjectOutputStream out) throws IOException {
      out.writeObject(data);
      out.writeObject(types);
   }

   /**
    * Read object.
    */
   private void readObject(ObjectInputStream in) throws IOException,
                                                        ClassNotFoundException {
      data = (Object[][]) in.readObject();
      types = (Class[]) in.readObject();
   }

   /**
    * Check if this table is a cache table for a level.
    */
   private boolean isCachedMembers() {
      if(data == null || data.length == 0 || data[0].length != 1) {
         return false;
      }

      return data[data.length - 1][0] instanceof MemberObject;
   }

   private Object[][] data;
   private Class[] types;
   private int row;
   private Map<String, MemberObject> map;
}
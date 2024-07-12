/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.*;
import inetsoft.util.*;
import inetsoft.util.xml.VersionControlComparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.geom.Point2D;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CompositeTableAssemblyInfo stores basic composite table assembly information.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class CompositeTableAssemblyInfo extends ComposedTableAssemblyInfo {
   /**
    * Constructor.
    */
   public CompositeTableAssemblyInfo() {
      super();
      map = new HashMap<>();
      schemaTableInfos = new HashMap<>();
   }

   /**
    * Get the operator count.
    * @return the operator count.
    */
   public int getOperatorCount() {
      return map.size();
   }

   /**
    * Get all the operators in the table.
    */
   public Enumeration<TableAssemblyOperator> getOperators() {
      return new IteratorEnumeration<>(map.values().iterator());
   }

   /**
    * Get the table pairs with operators defined.
    * @return table pairs are returned as String[] with exactly two items.
    */
   public Enumeration getOperatorTables() {
      final Iterator iter = map.keySet().iterator();

      return new Enumeration() {
         @Override
         public boolean hasMoreElements() {
            return iter.hasNext();
         }

         @Override
         public Object nextElement() {
            Pair pair = (Pair) iter.next();

            return new String[] {pair.getLeftTable(), pair.getRightTable()};
         }
      };
   }

   /**
    * Get the operator.
    * @param ltable the specified left table.
    * @param rtable the specified right table.
    * @return the operator.
    */
   public TableAssemblyOperator getOperator(String ltable, String rtable) {
      Pair pair = new Pair(ltable, rtable);
      TableAssemblyOperator operator = map.get(pair);

      if(operator == null) {
         pair = new Pair(rtable, ltable);
         operator = map.get(pair);

         if(operator != null) {
            for(int i = 0; i < operator.getOperatorCount(); i++) {
               int type = operator.getOperator(i).getOperation();

               if(type == TableAssemblyOperator.LEFT_JOIN ||
                  type == TableAssemblyOperator.RIGHT_JOIN ||
                  type == TableAssemblyOperator.GREATER_JOIN ||
                  type == TableAssemblyOperator.GREATER_EQUAL_JOIN ||
                  type == TableAssemblyOperator.LESS_JOIN ||
                  type == TableAssemblyOperator.LESS_EQUAL_JOIN ||
                  type == TableAssemblyOperator.MINUS)
               {
                  // Join is not symmetric, reversing the table order does not
                  // produce the same result, so don't return it.
                  operator = null;
                  break;
               }
            }
         }
      }

      return operator;
   }

   /**
    * Get the operator of a table.
    * @param table the specified table.
    * @return the operator which relates to the table, <tt>null</tt> otherwise.
    */
   public TableAssemblyOperator getOperator(String table) {
      TableAssemblyOperator operator = null;

      for(Map.Entry<Pair, TableAssemblyOperator> e : map.entrySet()) {
         if(e.getKey().getLeftTable().equals(table) ||
            e.getKey().getRightTable().equals(table))
         {
            operator = e.getValue();
            break;
         }
      }

      return operator;
   }

   /**
    * Get the operator of a table, prioritizing lower indexed tables.
    * @param table the specified table.
    * @return the operator which relates to the table, <tt>null</tt> otherwise.
    */
   public TableAssemblyOperator getOperator(String table, String[] tnames) {
      TableAssemblyOperator operator = null;
      int lowestIndex = -1;

      for(Map.Entry<Pair, TableAssemblyOperator> e : map.entrySet()) {
         if(e.getKey().getLeftTable().equals(table) ||
            e.getKey().getRightTable().equals(table))
         {
            boolean left = e.getKey().getLeftTable().equals(table);
            String otherTable = left ? e.getKey().getRightTable() : e.getKey().getLeftTable();

            for(int index = 0; index < tnames.length; index ++) {
               if(tnames[index].equals(otherTable)) {
                  if(index < lowestIndex || lowestIndex == -1) {
                     lowestIndex = index;
                     operator = e.getValue();
                  }
               }
            }
         }
      }

      return operator;
   }

   /**
    * Get the schema table infos.
    * @return the map containing the schema table infos of the composite table.
    */
   public Map<String, SchemaTableInfo> getSchemaTableInfos() {
      return schemaTableInfos;
   }

   /**
    * Set the schema table info of the given table.
    *
    * @param table the table to set
    * @param info  the schema table info
    */
   public void setSchemaTableInfo(String table, SchemaTableInfo info) {
      schemaTableInfos.put(table, info);
   }

   /**
    * Get the schema table info of the given table
    *
    * @param table the table to get the schema info of
    *
    * @return the schema table info of the table if it is a subtable of the composite table,
    * otherwise null
    */
   public SchemaTableInfo getSchemaTableInfo(String table) {
      return schemaTableInfos.get(table);
   }

   /**
    * Gets the position of a particular schema table.
    *
    * @param table the specified table.
    *
    * @return the position of the table.
    */
   public Point2D.Double getSchemaPixelPosition(String table) {
      final SchemaTableInfo info = this.schemaTableInfos.get(table);
      return info != null ? info.getLocation() : null;
   }

   /**
    * Sets the position of a schema table.
    * If p is null, then a new point will be found for the table.
    *
    * @param table the specified table.
    * @param p     the specified point at which to set it at.
    */
   public void setSchemaPixelPosition(String table, Point2D.Double p) {
      Point2D.Double point;

      if(p != null) {
         point = new Point2D.Double();
         double x = Math.max(p.getX(), 0);
         double y = Math.max(p.getY(), 0);
         point.setLocation(x, y);
      }
      else if(schemaTableInfos.containsKey(table)) {
         point = this.schemaTableInfos.get(table).getLocation();
      }
      else {
         point = new Point2D.Double(0, 0);
         final Set<Point2D.Double> points = schemaTableInfos.values().stream()
            .map(SchemaTableInfo::getLocation)
            .collect(Collectors.toSet());

         // TODO create proper layout algorithm.
         while(points.contains(point)) {
            point = new Point2D.Double(point.x + 225, point.y);
         }
      }

      final SchemaTableInfo info = schemaTableInfos.getOrDefault(
         table, new SchemaTableInfo());
      info.setLocation(point);
      schemaTableInfos.put(table, info);
   }

   /**
    * Set the operator.
    * @param ltable the specified left table.
    * @param rtable the specified right table.
    * @param operator the specified operator.
    */
   public void setOperator(
      String ltable, String rtable,
      TableAssemblyOperator operator)
   {
      Pair pair = new Pair(ltable, rtable);
      map.put(pair, operator);

      if(!schemaTableInfos.containsKey(ltable)) {
         setSchemaPixelPosition(ltable, null);
      }

      if(!schemaTableInfos.containsKey(rtable)) {
         setSchemaPixelPosition(rtable, null);
      }
   }

   /**
    * Remove the operator.
    * @param ltable the specified left table.
    * @param rtable the specified right table.
    */
   public void removeOperator(String ltable, String rtable) {
      Pair pair = new Pair(ltable, rtable);
      map.remove(pair);

      if(getOperator(ltable) == null) {
         schemaTableInfos.remove(ltable);
      }

      if(getOperator(rtable) == null) {
         schemaTableInfos.remove(rtable);
      }
   }

   public List<String> removeCrossJoinOperator() {
      List<String> removedTables = new ArrayList<>();
      Iterator<Map.Entry<Pair, TableAssemblyOperator>> iterator = map.entrySet().iterator();

      while(iterator.hasNext()) {
         Map.Entry<Pair, TableAssemblyOperator> entry = iterator.next();

         Pair pair = entry.getKey();
         String ltable = pair.getLeftTable();
         String rtable = pair.getRightTable();
         TableAssemblyOperator assemblyOperator = entry.getValue();

         if(assemblyOperator != null) {
            TableAssemblyOperator.Operator[] ops = assemblyOperator.getOperators();

            if(ops == null) {
               continue;
            }

            for(int i = ops.length - 1; i >= 0 ; i--) {
               if(ops[i].isCrossJoin()) {
                  assemblyOperator.removeOperator(i);
               }
            }

            if(assemblyOperator.getOperatorCount() == 0) {
               iterator.remove();
               removedTables.add(ltable);
               removedTables.add(rtable);
            }

            if(getOperator(ltable) == null) {
               schemaTableInfos.remove(ltable);
            }

            if(getOperator(rtable) == null) {
               schemaTableInfos.remove(rtable);
            }
         }
      }

      return removedTables;
   }

   /**
    * Reset the operators.
    * @param tables the available tables.
    */
   public void resetOperators(Collection tables) {
      if(tables == null) {
         map.clear();
         schemaTableInfos.clear();
      }

      List<Pair> pairs = new ArrayList<>(map.keySet());

      for(Pair pair : pairs) {
         if(!pair.isValid(tables)) {
            map.remove(pair);
         }
      }

      if(tables != null) {
         schemaTableInfos.keySet().retainAll(tables);
      }
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    * @param ws the specified worksheet.
    */
   public void renameDepended(String oname, String nname, Worksheet ws) {
      List<Pair> pairs = new ArrayList<>(map.keySet());

      for(Pair pair : pairs) {
         Pair npair;

         if(pair.getLeftTable().equals(oname)) {
            npair = new Pair(nname, pair.getRightTable());
         }
         else if(pair.getRightTable().equals(oname)) {
            npair = new Pair(pair.getLeftTable(), nname);
         }
         else {
            npair = pair;
         }

         Assembly lassembly = ws.getAssembly(npair.getLeftTable());
         Assembly rassembly = ws.getAssembly(npair.getRightTable());

         TableAssemblyOperator top = map.remove(pair);
         top.renameDepended(oname, nname, lassembly, rassembly);
         map.put(npair, top);
      }

      final SchemaTableInfo info = schemaTableInfos.get(oname);

      if(info != null) {
         schemaTableInfos.remove(oname);
         schemaTableInfos.put(nname, info);
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.println("<operators>");

      List<Map.Entry<Pair, TableAssemblyOperator>> entries
         = VersionControlComparators.sortComparableKeyMap(map);

      for(Map.Entry<Pair, TableAssemblyOperator> e : entries) {
         writer.println("<pairOperator>");
         e.getKey().writeXML(writer);
         e.getValue().writeXML(writer);
         writer.println("</pairOperator>");
      }

      writer.println("</operators>");

      if(schemaTableInfos != null && schemaTableInfos.size() > 0) {
         writer.println("<schemaTableInfos>");

         List<Map.Entry<String, SchemaTableInfo>> schemaTableInfoList
            = VersionControlComparators.sortStringKeyMap(schemaTableInfos);

         for(Map.Entry<String, SchemaTableInfo> e : schemaTableInfoList) {
            writer.println("<schemaTableInfo>");
            writer.print("<tablename>");
            writer.print("<![CDATA[" + e.getKey() + "]]>");
            writer.println("</tablename>");
            final SchemaTableInfo info = e.getValue();

            if(info != null) {
               writer.format(Locale.US, "<info left=\"%f\" top=\"%f\" width=\"%f\"></info>%n",
                             info.getLeft(), info.getTop(), info.getWidth());
            }

            writer.println("</schemaTableInfo>");
         }

         writer.println("</schemaTableInfos>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element tpsnode = Tool.getChildNodeByTagName(elem, "schemaTableInfos");

      if(tpsnode != null) {
         NodeList tpnodes = Tool.getChildNodesByTagName(tpsnode, "schemaTableInfo");

         for(int i = 0; i < tpnodes.getLength(); i++) {
            Element tpnode = (Element) tpnodes.item(i);
            Element tnode = Tool.getChildNodeByTagName(tpnode, "tablename");
            Element inode = Tool.getChildNodeByTagName(tpnode, "info");
            final String table = Tool.getValue(tnode);
            SchemaTableInfo info = null;

            if(inode != null) {
               final String left = Tool.getAttribute(inode, "left");
               final String top = Tool.getAttribute(inode, "top");
               final String width = Tool.getAttribute(inode, "width");
               info = new SchemaTableInfo(Double.parseDouble(left), Double.parseDouble(top),
                                          Double.parseDouble(width));
            }

            schemaTableInfos.put(table, info);
         }
      }

      Element osnode = Tool.getChildNodeByTagName(elem, "operators");
      NodeList ponodes = Tool.getChildNodesByTagName(osnode, "pairOperator");

      for(int i = 0; i < ponodes.getLength(); i++) {
         Element ponode = (Element) ponodes.item(i);
         Element pnode = Tool.getChildNodeByTagName(ponode, "pair");
         Pair pair = new Pair();
         pair.parseXML(pnode);
         Element onode = Tool.getChildNodeByTagName(ponode,
            "tableAssemblyOperator");
         TableAssemblyOperator operator = new TableAssemblyOperator();
         operator.parseXML(onode);

         map.put(pair, operator);

         if(!operator.isMergeJoin()) {
            if(!schemaTableInfos.containsKey(pair.getLeftTable())) {
               setSchemaPixelPosition(pair.getLeftTable(), null);
            }

            if(!schemaTableInfos.containsKey(pair.getRightTable())) {
               setSchemaPixelPosition(pair.getRightTable(), null);
            }
         }
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone(boolean recursive) {
      try {
         CompositeTableAssemblyInfo info =
            (CompositeTableAssemblyInfo) super.clone(recursive);
         info.map = Tool.deepCloneMap(map);
         info.schemaTableInfos = Tool.deepCloneMap(schemaTableInfos);
         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Left table and right table pair.
    */
   private static class Pair implements Serializable, XMLSerializable, DataSerializable, Comparable<Pair> {
      public Pair() {
         super();
      }

      public Pair(String ltable, String rtable) {
         this();

         this.ltable = ltable;
         this.rtable = rtable;
      }

      public String getLeftTable() {
         return ltable;
      }

      public String getRightTable() {
         return rtable;
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.println("<pair>");
         writer.print("<leftTableAssembly>");
         writer.print("<![CDATA[" + ltable + "]]>");
         writer.println("</leftTableAssembly>");
         writer.print("<rightTableAssembly>");
         writer.print("<![CDATA[" + rtable + "]]>");
         writer.println("</rightTableAssembly>");
         writer.println("</pair>");
      }

      @Override
      public void parseXML(Element tag) throws Exception {
         Element ltnode = Tool.getChildNodeByTagName(tag, "leftTableAssembly");
         ltable = Tool.getValue(ltnode);

         Element rtnode = Tool.getChildNodeByTagName(tag, "rightTableAssembly");
         rtable = Tool.getValue(rtnode);
      }

      @Override
      public void writeData(DataOutputStream dos) {
         try {
            dos.writeUTF(ltable);
            dos.writeUTF(rtable);
         }
         catch(IOException ignore) {
         }
      }

      @Override
      public boolean parseData(DataInputStream input) {
         //do nothing
         return true;
      }

      public boolean isValid(Collection tables) {
         return tables.contains(ltable) && tables.contains(rtable);
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         Pair pair = (Pair) o;
         return Tool.equals(ltable, pair.ltable) &&
            Tool.equals(rtable, pair.rtable);
      }

      @Override
      public int hashCode() {
         int result = ltable != null ? ltable.hashCode() : 0;
         result = 31 * result + (rtable != null ? rtable.hashCode() : 0);
         return result;
      }

      @Override
      public int compareTo(Pair o) {
         int order = this.ltable.compareTo(o.ltable);

         if(order == 0) {
            return this.rtable.compareTo(o.rtable);
         }

         return order;
      }

      public String toString() {
         return "Pair[" + ltable + "," + rtable + "]";
      }

      private String ltable;
      private String rtable;
   }

   private Map<Pair, TableAssemblyOperator> map;
   private Map<String, SchemaTableInfo> schemaTableInfos;

   private static final Logger LOG =
      LoggerFactory.getLogger(CompositeTableAssemblyInfo.class);
}

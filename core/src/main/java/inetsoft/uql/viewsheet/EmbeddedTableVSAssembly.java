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
package inetsoft.uql.viewsheet;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.EmbeddedTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * EmbeddedTableVSAssembly represents one embedded table assembly contained in
 * a <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class EmbeddedTableVSAssembly extends TableVSAssembly {
   /**
    * Constructor.
    */
   public EmbeddedTableVSAssembly() {
      super();
   }

   /**
    * Constructor.
    * @param vs the specified viewsheet container.
    * @param name the specified name of this embedded table assembly.
    */
   public EmbeddedTableVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new EmbeddedTableVSAssemblyInfo();
   }

   /**
    * Get embedded table assembly info.
    * @return the embedded table assembly info.
    */
   protected EmbeddedTableVSAssemblyInfo getEmbeddedTableInfo() {
      return (EmbeddedTableVSAssemblyInfo) getInfo();
   }

   /**
    * Set the column selection of the table.
    * @param columns the specified column selection of the table.
    */
   @Override
   public int setColumnSelection(ColumnSelection columns) {
      int hint = super.setColumnSelection(columns);

      synchronized(dmap) {
         for(CellRef ref : new HashSet<>(dmap.keySet())) {
            if(columns.getAttribute(ref.getCol()) == null) {
               dmap.remove(ref);
            }
         }

         return hint | VSAssembly.OUTPUT_DATA_CHANGED;
      }
   }

   /**
    * Set the assembly info.
    * @param info the specified viewsheet assembly info.
    * @return the hint to reset view, input data or output data.
    */
   @Override
   public int setVSAssemblyInfo(VSAssemblyInfo info) {
      return super.setVSAssemblyInfo(info);
   }

   /**
    * Check if allows cycle.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean allowsCycle() {
      return true;
   }

   /**
    * Get the data map.
    * @return the data map.
    */
   public Map<CellRef,Object> getStateDataMap() {
      return dmap;
   }

   /**
    * Set the data map.
    * @param map the specified data map.
    * @return the change hint.
    */
   public int setStateDataMap(Map<CellRef,Object> map) {
      synchronized(dmap) {
         if(!Tool.equals(this.dmap, map)) {
            this.dmap = map == null ? new HashMap<>() : map;
            return OUTPUT_DATA_CHANGED;
         }

         return NONE_CHANGED;
      }
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);

      writer.println("<dataMap>");

      synchronized(dmap) {
         Iterator<CellRef> keys = dmap.keySet().iterator();
         ColumnSelection columns = getColumnSelection();
         ColumnSelection tcolumns = getBaseColumns();

         while(keys.hasNext()) {
            CellRef ref = keys.next();
            Object obj = dmap.get(ref);
            boolean existInOldColumns =
               tcolumns != null && tcolumns.getAttribute(ref.getCol()) != null;

            if(columns.getAttribute(ref.getCol()) == null && !existInOldColumns)
            {
               continue;
            }

            writer.print("<cell>");
            ref.writeXML(writer);

            String val = Tool.getDataString(obj);
            writer.print("<value>");
            writer.print("<![CDATA[" + val + "]]>");
            writer.println("</value>");

            writer.print("</cell>");
         }

         writer.println("</dataMap>");
      }
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseStateContent(Element elem, boolean runtime)
      throws Exception
   {
      super.parseStateContent(elem, runtime);

      dmap.clear();
      Element dnode = Tool.getChildNodeByTagName(elem, "dataMap");

      if(dnode != null) {
         ColumnSelection columns = getColumnSelection();
         ColumnSelection tcolumns = getBaseColumns();
         NodeList cnodes = Tool.getChildNodesByTagName(dnode, "cell");

         for(int i = 0; i < cnodes.getLength(); i++) {
            Element cnode = (Element) cnodes.item(i);
            Element rnode = Tool.getChildNodeByTagName(cnode, "cellRef");
            Element vnode = Tool.getChildNodeByTagName(cnode, "value");
            CellRef ref = new CellRef();
            ref.parseXML(rnode);
            String val = Tool.getValue(vnode);
            DataRef nref = columns.getAttribute(ref.getCol());

            if(nref == null && tcolumns != null) {
               nref = tcolumns.getAttribute(ref.getCol());
            }

            String dtype = nref != null ? nref.getDataType() : XSchema.STRING;
            Object obj = Tool.getData(dtype, val);
            dmap.put(ref, obj);
         }
      }

      String table = getTableName();
      Worksheet ws = getWorksheet();
      Assembly assembly = ws == null || table == null ? null :
         ws.getAssembly(table);

      if(assembly instanceof EmbeddedTableAssembly &&
         !(assembly instanceof SnapshotEmbeddedTableAssembly))
      {
         EmbeddedTableAssembly etable = (EmbeddedTableAssembly) assembly;
         etable.setEmbeddedData(etable.getOriginalEmbeddedData());
      }
   }

   /**
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      String table = getTableName();
      Worksheet ws = getWorksheet();
      Assembly assembly = ws == null || table == null ? null :
         ws.getAssembly(table);

      if(assembly instanceof TableAssembly) {
         return new AssemblyRef[] {new AssemblyRef(AssemblyRef.INPUT_DATA,
            assembly.getAssemblyEntry())};
      }

      return new AssemblyRef[0];
   }

   /**
    * Get the depending worksheet assemblies to modify.
    * @return the depending worksheet assemblies to modify.
    */
   @Override
   public AssemblyRef[] getDependingWSAssemblies() {
      String table = getTableName();
      Worksheet ws = getWorksheet();

      Assembly assembly = ws == null || table == null ? null :
         ws.getAssembly(table);

      if(assembly instanceof TableAssembly) {
         return new AssemblyRef[] {new AssemblyRef(AssemblyRef.OUTPUT_DATA,
            assembly.getAssemblyEntry())};
      }

      return new AssemblyRef[0];
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public EmbeddedTableVSAssembly clone() {
      try {
         EmbeddedTableVSAssembly assembly = (EmbeddedTableVSAssembly) super.clone();
         assembly.dmap = new HashMap<>(dmap);
         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone EmbeddedTableVSAssembly", ex);
      }

      return null;
   }

   /**
    * Get base column selection in ws table assembly.
    */
   private ColumnSelection getBaseColumns() {
      String table = getTableName();
      Worksheet ws = getWorksheet();
      Assembly assembly = ws == null || table == null ? null :
         ws.getAssembly(table);
      ColumnSelection tcolumns = null;

      if(assembly instanceof EmbeddedTableAssembly) {
         tcolumns = ((EmbeddedTableAssembly) assembly).getColumnSelection(false);
      }

      return tcolumns;
   }

   private Map<CellRef, Object> dmap = new HashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(EmbeddedTableVSAssembly.class);
}

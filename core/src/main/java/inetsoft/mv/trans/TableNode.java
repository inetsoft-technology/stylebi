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
package inetsoft.mv.trans;

import inetsoft.uql.XNode;
import inetsoft.uql.asset.*;

import java.util.*;

/**
 * The table assembly node in table tree.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class TableNode extends XNode {
   /**
    * Build a table tree from an assembly. The tree is used for navigation only
    * and any modification on the tree does not affect the assembly definitions.
    * The name of each node matches the assembly name. The assembly object is
    * stored on the node as node value.
    */
   public static XNode create(Worksheet ws, String rassembly, TransformationDescriptor desc) {
      Assembly aobj = ws.getAssembly(rassembly);
      XNode root = new TableNode((TableAssembly) aobj);
      root.setValue(aobj);
      buildSubTree(ws, root, aobj, desc);
      return root;
   }

   /**
    * Build a sub-trees for the assembly if the assembly has dependent table
    * assemblies.
    */
   private static void buildSubTree(Worksheet ws, XNode pnode, Assembly parent,
                                    TransformationDescriptor desc)
   {
      List<XNode> children = new ArrayList<>();

      if(parent instanceof ComposedTableAssembly) {
         TableAssembly[] tables = ((ComposedTableAssembly) parent).getTableAssemblies(false);

         for(int i = 0; i < tables.length; i++) {
            TableAssembly table = tables[i];

            if(!ws.containsAssembly(table)) {
               ws.addAssembly(table);
            }

            XNode child = new TableNode(table);
            child.setValue(table);
            children.add(child);
         }
      }

      // also add dependencies (such as tables referenced in expression) to the tree
      // so if an embedded table used by this table is bound to input assembly, we don't
      // materialize this table since the sub-table value can change at runtime.
      if(desc.hasDynamicTable()) {
         Set<AssemblyRef> deps = new HashSet<>();
         parent.getDependeds(deps);

         for(Object dep : deps) {
            AssemblyRef ref = (AssemblyRef) dep;

            if(ref.getType() == AssemblyRef.INPUT_DATA) {
               AssemblyEntry entry = ref.getEntry();
               Assembly table = ws.getAssembly(entry);

               if(table instanceof TableAssembly) {
                  XNode child = new TableNode((TableAssembly) table);
                  child.setValue(table);
                  children.add(child);
               }
            }
         }
      }

      if(!children.isEmpty()) {
         for(XNode child : children) {
            TableAssembly table = (TableAssembly) child.getValue();
            pnode.addChild(child, false, false);
            buildSubTree(ws, child, table, desc);
         }
      }
   }

   /**
    * Create an instance of TableNode.
    */
   public TableNode(TableAssembly table) {
      super();

      setValue(table);
      setName(table.getName());
   }

   /**
    * Get the table assembly.
    */
   public TableAssembly getTable() {
      return (TableAssembly) super.getValue();
   }

   /**
    * Set the value to this node.
    */
   @Override
   public void setValue(Object value) {
      super.setValue(value);
      setName(((TableAssembly) value).getName());
   }

   /**
    * Set the name to this node.
    */
   @Override
   public void setName(String name) {
      super.setName(name);
      getTable().getInfo().setName(name);
   }

   /**
    * Check if selection is nested at this node.
    */
   public boolean isSelectionNested() {
      return nested;
   }

   /**
    * Set whether selection is nested at this node.
    */
   public void setSelectionNested(boolean nested) {
      this.nested = nested;
   }

   private boolean nested;
}

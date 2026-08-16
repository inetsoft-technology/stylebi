/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.binding;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Projects the Composer's binding tree into a flat table/column list.
 *
 * <p>{@code TreeNodeModel} is a UI tree — icon, expandedIcon, toggleCollapsedIcon, tooltip,
 * organization, expand state. An agent needs the structure, not the chrome, so this keeps
 * names and data types and discards the rest.
 */
@Service
public class BindableFieldsService {
   @Autowired
   public BindableFieldsService(VSBindingTreeService tree) {
      this.tree = tree;
   }

   public List<BindableTable> list(String runtimeId, String assembly, Principal user)
      throws Exception
   {
      TreeNodeModel root = tree.getBinding(runtimeId, assembly, false, user);
      List<BindableTable> tables = new ArrayList<>();

      if(root != null) {
         collect(root, tables, null);
      }

      return tables;
   }

   /**
    * A node with leaf children holds columns; anything else is a folder to descend into. Keeping
    * the rule structural rather than depth-based means the projection does not care how deeply
    * the Composer nests its tree.
    *
    * <p>{@code sourceName} is the nearest enclosing <em>table</em>, carried down rather than read
    * from the node holding the columns. In the Composer's binding tree that node is a
    * "Dimensions"/"Measures" folder, so naming the group after it produced a listing where every
    * group was called "Dimensions" or "Measures" and no column could be traced to the table it
    * came from — observed live, nine groups and two distinct names between them.
    */
   private void collect(TreeNodeModel node, List<BindableTable> tables, String sourceName) {
      String source = isTable(node) ? node.label() : sourceName;
      List<BindableField> fields = new ArrayList<>();

      for(TreeNodeModel child : node.children()) {
         if(child.leaf()) {
            fields.add(new BindableField(child.label(), dataTypeOf(child)));
         }
         else {
            collect(child, tables, source);
         }
      }

      if(!fields.isEmpty()) {
         // Fall back to the node's own label only when no table ancestor was found, so a tree
         // shape this does not anticipate degrades to the old behaviour rather than to a blank.
         tables.add(new BindableTable(source != null ? source : node.label(), fields));
      }
   }

   /** Whether this node IS a source table, as opposed to a folder grouping one's columns. */
   private boolean isTable(TreeNodeModel node) {
      if(!(node.data() instanceof AssetEntry entry)) {
         return false;
      }

      AssetEntry.Type type = entry.getType();

      return type == AssetEntry.Type.TABLE || type == AssetEntry.Type.PHYSICAL_TABLE ||
         type == AssetEntry.Type.QUERY || type == AssetEntry.Type.LOGIC_MODEL;
   }

   /**
    * The column's data type.
    *
    * <p>Read from the {@link AssetEntry}'s {@code dtype} property, which is where the rest of the
    * codebase reads it from ({@code Viewsheet}, {@code VSEventUtil}, {@code JDBCUtil}). This
    * previously tested for a {@code DataRefModel}, which the tree never carries: {@code
    * VSTreeHandler.createNodeFromEntry} builds every node with {@code .data(entry)}. The branch
    * could not match, so every column reported a null type while the tool's description promised
    * one.
    */
   private String dataTypeOf(TreeNodeModel node) {
      return node.data() instanceof AssetEntry entry ? entry.getProperty("dtype") : null;
   }

   private final VSBindingTreeService tree;
}

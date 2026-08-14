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

import inetsoft.web.binding.drm.DataRefModel;
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
         collect(root, tables);
      }

      return tables;
   }

   /**
    * A node with leaf children is a table; anything else is a folder to descend into. Keeping
    * the rule structural rather than depth-based means the projection does not care how
    * deeply the Composer nests its tree.
    */
   private void collect(TreeNodeModel node, List<BindableTable> tables) {
      List<BindableField> fields = new ArrayList<>();

      for(TreeNodeModel child : node.children()) {
         if(child.leaf()) {
            fields.add(new BindableField(child.label(), dataTypeOf(child), null));
         }
         else {
            collect(child, tables);
         }
      }

      if(!fields.isEmpty()) {
         tables.add(new BindableTable(node.label(), fields));
      }
   }

   /** A column node carries its {@link DataRefModel} in {@code data()}. */
   private String dataTypeOf(TreeNodeModel node) {
      return node.data() instanceof DataRefModel ref ? ref.getDataType() : null;
   }

   private final VSBindingTreeService tree;
}

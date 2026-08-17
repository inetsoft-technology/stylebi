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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.Viewsheet;
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
   public BindableFieldsService(VSBindingTreeService tree, ViewsheetService viewsheetService) {
      this.tree = tree;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Refuses an {@code assembly} that does not exist, rather than silently listing everything.
    *
    * <p>{@code VSBindingTreeService.getBinding} resolves the assembly with
    * {@code viewsheet.getAssembly(name)}, which returns {@code null} for an unknown name exactly
    * as it does for {@code null} itself — so "list every table" and "list the tables for a name
    * that doesn't exist" fell into the identical fallback branch and returned the identical
    * whole-viewsheet tree. An agent scoping to the wrong assembly name got a full, plausible field
    * list back and had no way to tell its scoping request was silently ignored (CLAUDE.md's
    * tool-misuse-accepted-silently class). {@code assembly == null} is the caller's deliberate
    * "list everything," so only a non-null name that fails to resolve is refused.
    */
   public List<BindableTable> list(String runtimeId, String assembly, Principal user)
      throws Exception
   {
      if(assembly != null) {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, user);
         Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

         if(vs == null || vs.getAssembly(assembly) == null) {
            throw new IllegalArgumentException(
               "'" + assembly + "' is not an assembly on this viewsheet. Omit 'assembly' to list " +
               "every table the viewsheet offers, or call read_viewsheet_model to see what " +
               "assemblies exist.");
         }
      }

      TreeNodeModel root = tree.getBinding(runtimeId, assembly, false, user);
      List<BindableTable> tables = new ArrayList<>();

      if(root != null) {
         collect(root, tables, null);
      }

      return tables;
   }

   /**
    * One group per source table, holding every column beneath it.
    *
    * <p>The Composer's tree is {@code TABLE -> Dimensions/Measures folder -> COLUMN}, so collecting
    * a group per node-that-holds-columns produced <em>two</em> groups per table. That was tolerable
    * only while they were named after the folders; once they are named after the table, it yields
    * two entries with the same name whose only distinguishing feature was the label just replaced.
    * A caller keying by table name -- the natural move -- silently keeps one and drops half the
    * columns. So a table absorbs all of its descendants into a single group.
    *
    * <p>{@code sourceName} is the nearest enclosing table, carried down for the shapes that have no
    * table ancestor at all.
    */
   private void collect(TreeNodeModel node, List<BindableTable> tables, String sourceName) {
      if(isTable(node)) {
         List<BindableField> fields = new ArrayList<>();
         gather(node, fields);

         if(!fields.isEmpty()) {
            tables.add(new BindableTable(node.label(), fields));
         }

         return;
      }

      List<BindableField> direct = new ArrayList<>();

      for(TreeNodeModel child : node.children()) {
         if(child.leaf()) {
            direct.add(fieldOf(child));
         }
         else {
            collect(child, tables, sourceName);
         }
      }

      // No table ancestor: name the group after the node that holds the columns. That is the
      // pre-fix behaviour, and for the Composer's own tree it is the *bug* being fixed here -- a
      // group called "Dimensions". It survives only as a floor for tree shapes this does not
      // anticipate, where a wrong-but-present name beats a blank one.
      if(!direct.isEmpty()) {
         tables.add(new BindableTable(sourceName != null ? sourceName : node.label(), direct));
      }
   }

   /** Every column at or below this node, however deeply the Composer nests them. */
   private void gather(TreeNodeModel node, List<BindableField> out) {
      for(TreeNodeModel child : node.children()) {
         if(child.leaf()) {
            out.add(fieldOf(child));
         }
         else {
            gather(child, out);
         }
      }
   }

   private BindableField fieldOf(TreeNodeModel node) {
      return new BindableField(node.label(), dataTypeOf(node), roleOf(node));
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

   /**
    * "dimension" or "measure".
    *
    * <p>Every binding tool downstream requires an explicit {@code type} of dimension or measure per
    * field, and this is the tool a caller is told to run first -- so omitting the distinction here
    * forces them to guess it from the column name. It was previously hardcoded null on the grounds
    * that the tree does not carry it. It does: {@link AssetEntry#CUBE_COL_TYPE}, set alongside the
    * {@code dtype} property this class already reads.
    *
    * <p>The property has <b>three</b> states, not two. {@code BaseTreeModelBuilder} writes
    * {@code type == -1 ? "" : type + ""}, so a column can carry it <em>present but empty</em> --
    * and it does for every column of a table VS assembly, which is a tree this tool reads. Empty
    * means "not stated", the case the data-type fallback exists for, so blank is treated as absent
    * rather than as a parse failure.
    *
    * <p>The classification follows {@code VSChartBindingHandler.isDimension} -- same bit test, same
    * data-type fallback -- but deliberately not its exact behaviour on that empty value, where it
    * would throw {@code NumberFormatException}. Falling through to the fallback is the answer the
    * column deserves; matching the handler there would only reproduce a latent bug.
    */
   private String roleOf(TreeNodeModel node) {
      if(!(node.data() instanceof AssetEntry entry)) {
         return null;
      }

      String cubeColType = entry.getProperty(AssetEntry.CUBE_COL_TYPE);

      if(cubeColType != null && !cubeColType.isBlank()) {
         try {
            return (Integer.parseInt(cubeColType) & AssetEntry.MEASURES) == 0 ? DIMENSION : MEASURE;
         }
         catch(NumberFormatException ex) {
            // Not a number: unstated, so fall through to the data type rather than give up. A
            // `return null` here would swallow the fallback for exactly the columns that need it.
         }
      }

      String dtype = entry.getProperty("dtype");

      return dtype == null ? null : XSchema.isNumericType(dtype) ? MEASURE : DIMENSION;
   }

   private static final String DIMENSION = "dimension";
   private static final String MEASURE = "measure";

   private final VSBindingTreeService tree;
   private final ViewsheetService viewsheetService;
}

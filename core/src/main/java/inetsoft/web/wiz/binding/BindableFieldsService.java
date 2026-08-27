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
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DataVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
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
      String source = null;

      if(assembly != null) {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, user);
         Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
         VSAssembly target = vs == null ? null : vs.getAssembly(assembly);

         if(target == null) {
            throw new IllegalArgumentException(
               "'" + assembly + "' is not an assembly on this viewsheet. Omit 'assembly' to list " +
               "every table the viewsheet offers, or call read_viewsheet_model to see what " +
               "assemblies exist.");
         }

         source = sourceNameOf(target);
      }

      TreeNodeModel root = tree.getBinding(runtimeId, assembly, false, user);
      List<BindableTable> tables = new ArrayList<>();

      if(root != null) {
         collect(root, tables, null);
      }

      return assembly == null ? tables : marked(tables, source);
   }

   /** The table an assembly is bound to right now, or {@code null} if it has none yet. */
   private static String sourceNameOf(VSAssembly assembly) {
      if(!(assembly instanceof DataVSAssembly data)) {
         return null;
      }

      SourceInfo source = data.getSourceInfo();

      return source == null || source.isEmpty() ? null : source.getSource();
   }

   /**
    * The logical model this table node belongs to, or null when it is a source in its own right.
    *
    * <p>A logical model has one source — its own name, which is what {@code SourceInfo.getSource}
    * stores and the only value {@code set_chart_source} accepts — while its entities are groups
    * inside it. The chart-scoped tree has no node for the model, so its entities were reported as
    * separate source tables: names no {@code set_*_source} tool accepts, which a shelf write would
    * nonetheless store as the assembly's source, leaving a chart whose source is not a source and
    * which therefore never produced a graph. It also meant a correctly bound chart matched none of
    * them and read back as having no source at all.
    *
    * <p>The answer is on the node: {@code BaseTreeModelBuilder.applyTableNodeProperties} stamps
    * {@code sourceType=LOGIC_MODEL} and {@code table=<model>} onto every entity node it builds for
    * a model-backed sheet. Read from there rather than from the viewsheet, so no assembly has to
    * be resolved to answer an unscoped listing.
    */
   private static String modelOf(TreeNodeModel node) {
      if(!(node.data() instanceof AssetEntry entry)) {
         return null;
      }

      if(!String.valueOf(AssetEntry.Type.LOGIC_MODEL).equals(entry.getProperty("sourceType"))) {
         return null;
      }

      String model = entry.getProperty("table");

      return model != null && !model.isBlank() ? model : null;
   }

   /**
    * Adds the fields to the model's table, creating it on the first entity that names it.
    *
    * <p>Folding here rather than merging afterwards keeps it to the one case that needs it: two
    * groups that legitimately share a label — the {@code Dimensions}/{@code Measures} fallback
    * naming below, say — stay separate, as they did before.
    */
   private static void fold(List<BindableTable> tables, String model, List<BindableField> fields) {
      for(int i = 0; i < tables.size(); i++) {
         BindableTable seen = tables.get(i);

         if(model.equalsIgnoreCase(seen.name())) {
            List<BindableField> all = new ArrayList<>(seen.fields());
            all.addAll(fields);
            tables.set(i, new BindableTable(seen.name(), seen.current(), all));
            return;
         }
      }

      tables.add(new BindableTable(model, null, fields));
   }


   /**
    * Flags the one live table, so the single-source rule can be followed from this call alone.
    *
    * <p>Only for a scoped call: unscoped, {@code current} stays null, because there is no assembly
    * to be current for and {@code false} everywhere would read as "none of these is live" — the
    * opposite of the truth. An assembly with no source yet does get {@code false} everywhere, which
    * is accurate: nothing is live, and that is exactly the state where a shelf write would be
    * accepted and render nothing.
    */
   private static List<BindableTable> marked(List<BindableTable> tables, String source) {
      List<BindableTable> out = new ArrayList<>(tables.size());

      for(BindableTable table : tables) {
         // equalsIgnoreCase is null-safe on its argument, so a sourceless assembly yields false.
         out.add(new BindableTable(table.name(), table.name().equalsIgnoreCase(source),
                                   table.fields()));
      }

      return out;
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
         gather(node, fields, isLogicalModel(node), null);

         if(!fields.isEmpty()) {
            // An entity of a logical model is not a source: the model is, and its name is what
            // SourceInfo stores and set_chart_source accepts. The chart-scoped tree has no node
            // for the model, but BaseTreeModelBuilder.applyTableNodeProperties stamps the model's
            // name onto every entity node it builds, so the node says which model it belongs to
            // and the sheet does not have to be resolved to find out.
            String model = modelOf(node);

            if(model == null) {
               tables.add(new BindableTable(node.label(), null, fields));
            }
            else {
               fold(tables, model, fields);
            }
         }

         return;
      }

      List<BindableField> direct = new ArrayList<>();

      for(TreeNodeModel child : node.children()) {
         if(isColumn(child)) {
            direct.add(fieldOf(child, null));
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
         tables.add(new BindableTable(sourceName != null ? sourceName : node.label(), null, direct));
      }
   }

   /** Every column at or below this node, however deeply the Composer nests them. */
   /**
    * @param model  the logical model this subtree belongs to, or null when it does not belong to
    *               one. Only inside a model is a folder an entity whose label prefixes a column.
    * @param entity the entity folder currently being descended, or null at the top.
    */
   /**
    * @param inModel true when {@code node} is a logical model, so the nodes between it and its
    *                columns are its entities and their labels prefix a column name.
    * @param entity  the entity currently being descended, or null at the top.
    */
   private void gather(TreeNodeModel node, List<BindableField> out, boolean inModel, String entity) {
      for(TreeNodeModel child : node.children()) {
         if(isColumn(child)) {
            out.add(fieldOf(child, entity));
         }
         else {
            // Any node between a logical model and a column is one of its entities, and its label
            // is the prefix the binding tools require: they accept "Customer:Region" and refuse a
            // bare "Region", which is what this listing handed back before — ambiguously, since
            // Address, City, Company, Region, State and Zip each occur under more than one entity
            // of the sample model. Not a folder test: VSEventUtil.appendChildNodes types every
            // folder child of the base tree as TABLE, so isFolder never matches an entity there
            // and a folder test made this a no-op. Outside a model no prefix is applied, where an
            // intermediate node is just a node and prefixing would invent a name.
            gather(child, out, inModel, inModel ? child.label() : entity);
         }
      }
   }

   /**
    * Whether a node is safe to treat as a column — checked by its actual children rather than the
    * tree's own {@code leaf} flag, and never true for a table, columns or not.
    *
    * <p>{@code leaf} is a UI hint, not a structural guarantee, and the two can disagree:
    * {@code VSTreeHandler.isLeaf} marks every {@code AssetEntry.Type.WORKSHEET} entry as a leaf so
    * the Composer's asset browser does not expand into a referenced worksheet inline. But
    * {@code VSEventUtil.refreshBaseWSTree} — the tree an unscoped {@code list_bindable_fields}
    * reads — reuses a WORKSHEET-typed entry as the *container* holding the viewsheet's actual
    * tables, which does have real children. {@code createNodeFromEntry} sets {@code .leaf(...)}
    * and {@code .children(...)} independently, so that container node ends up {@code leaf: true}
    * with real table children underneath it at the same time.
    *
    * <p>Trusting {@code leaf()} there treated the container itself as one column — {@code
    * fieldOf(wsNode)} turned the worksheet's own label into a fabricated {@code {column, dataType:
    * null, role: null}} — and never walked into the real tables beneath it, so an unscoped call
    * returned that one manufactured field instead of the viewsheet's tables. A node with children
    * is never a column regardless of what {@code leaf()} claims; only genuine childlessness is.
    *
    * <p>Childlessness alone is not sufficient, though: a table with nothing exposed under it
    * (permission-filtered, a fresh embedded table, mid-load metadata) has no children either, and
    * without the {@link #isTable} exclusion this reintroduces the exact defect it fixes one level
    * up — an empty table's own label gets fabricated into a column standing in for the table,
    * instead of correctly reporting no fields for it. {@code isTable} was already the answer to
    * "is this a table" everywhere else in this class; the childlessness check must defer to it
    * rather than deciding leaf-ness on its own.
    *
    * <p>An empty grouping <em>folder</em> is the same trap a third time, and the reason each
    * exclusion has to be stated rather than inferred: {@code VSTreeHandler} adds both a Dimensions
    * and a Measures folder to every table as long as it has at least one ref of <em>either</em>
    * kind, so a table whose columns are all measures carries an empty Dimensions folder. Childless
    * and not a table, it satisfied everything above and turned into
    * {@code {column: "Dimensions", dataType: null, role: null}} — a column no caller can bind,
    * sitting first in the list where it is most likely to be picked.
    */
   private boolean isColumn(TreeNodeModel node) {
      return node.children().isEmpty() && !isTable(node) && !isFolder(node);
   }

   /**
    * Whether this node is a grouping folder rather than something bindable.
    *
    * <p>Keyed on the entry <em>type</em>, the way {@link #isTable} already decides, and not on
    * whether the node carries a {@code dtype}: this class deliberately supports columns whose data
    * type is absent — {@link #roleOf} has a whole fallback path for them — so treating a missing
    * type as "not a column" would drop real columns to fix a fake one.
    */
   private boolean isLogicalModel(TreeNodeModel node) {
      return node.data() instanceof AssetEntry entry &&
         entry.getType() == AssetEntry.Type.LOGIC_MODEL;
   }

   private boolean isFolder(TreeNodeModel node) {
      return node.data() instanceof AssetEntry entry &&
         entry.getType() == AssetEntry.Type.FOLDER;
   }

   private BindableField fieldOf(TreeNodeModel node, String entity) {
      String column = node.label();

      // Already prefixed on the chart-scoped tree, so this is not applied twice.
      if(entity != null && column != null && !column.contains(":")) {
         column = entity + ":" + column;
      }

      return new BindableField(column, dataTypeOf(node), roleOf(node));
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

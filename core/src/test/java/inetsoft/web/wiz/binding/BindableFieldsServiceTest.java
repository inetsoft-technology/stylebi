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
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class BindableFieldsServiceTest {
   /**
    * Every node in the real tree carries an {@link AssetEntry} in {@code data()} --
    * {@code VSTreeHandler.createNodeFromEntry} builds them all with {@code .data(entry)}. These
    * tests used to mock a {@code DataRefModel} there, which the tree never produces, so they
    * passed while every column in production reported a null data type.
    */
   private static AssetEntry entry(AssetEntry.Type type, String name, String dtype) {
      return entry(type, name, dtype, null);
   }

   private static AssetEntry entry(AssetEntry.Type type, String name, String dtype, Integer cube) {
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.getType()).thenReturn(type);
      when(entry.getProperty("dtype")).thenReturn(dtype);
      when(entry.getProperty(AssetEntry.CUBE_COL_TYPE))
         .thenReturn(cube == null ? null : String.valueOf(cube));
      return entry;
   }

   @Test
   void flattensTheTreeIntoTablesAndColumnsAndDropsTheUiChrome() throws Exception {
      TreeNodeModel column = TreeNodeModel.builder()
         .label("Sales").leaf(true).data(entry(AssetEntry.Type.COLUMN, "Sales", "double"))
         .icon("column-icon").tooltip("a tooltip").build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("Orders").data(entry(AssetEntry.Type.TABLE, "Orders", null))
         .addChildren(column).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableTable> tables = serviceReturning(root).list("rt1", null, principal());

      assertEquals(1, tables.size());
      assertEquals("Orders", tables.get(0).name());
      assertEquals(1, tables.get(0).fields().size());
      assertEquals("Sales", tables.get(0).fields().get(0).column());
      assertEquals("double", tables.get(0).fields().get(0).dataType());
   }

   @Test
   void descendsThroughFolderLevelsSoNestingDepthDoesNotMatter() throws Exception {
      TreeNodeModel column = TreeNodeModel.builder().label("Qty").leaf(true).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("Items").data(entry(AssetEntry.Type.TABLE, "Items", null))
         .addChildren(column).build();
      TreeNodeModel folder = TreeNodeModel.builder().label("Folder").addChildren(table).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(folder).build();

      List<BindableTable> tables = serviceReturning(root).list("rt1", null, principal());

      assertEquals(1, tables.size(), "the folder level must not become a table");
      assertEquals("Items", tables.get(0).name());
   }

   /**
    * The live shape: a table whose columns sit under "Dimensions"/"Measures" folders.
    *
    * <p>Two things have to hold together. Naming each group after the node that directly holds the
    * columns produced nine groups called only "Dimensions"/"Measures", with no way to tell which
    * table a column came from. But naming them after the table while still emitting one group per
    * folder produces two groups with the <em>same</em> name -- and a caller keying by table name
    * keeps one and silently drops half the columns. So a table yields exactly one group.
    */
   @Test
   void yieldsOneGroupPerTableNamedAfterIt_notOnePerDimensionsFolder() throws Exception {
      TreeNodeModel qty = TreeNodeModel.builder().label("Qty").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "Qty", "integer", AssetEntry.MEASURES)).build();
      TreeNodeModel city = TreeNodeModel.builder().label("City").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "City", "string", AssetEntry.DIMENSIONS)).build();
      TreeNodeModel measures = TreeNodeModel.builder().label("Measures").addChildren(qty).build();
      TreeNodeModel dimensions = TreeNodeModel.builder()
         .label("Dimensions").addChildren(city).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("ORDERS1").data(entry(AssetEntry.Type.TABLE, "ORDERS1", null))
         .addChildren(dimensions).addChildren(measures).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableTable> tables = serviceReturning(root).list("rt1", null, principal());

      assertEquals(1, tables.size(), "one table must not become two same-named groups");
      assertEquals("ORDERS1", tables.get(0).name());
      assertEquals(2, tables.get(0).fields().size(), "both folders' columns belong to the table");
   }

   /**
    * The dimension/measure distinction has to survive, because every binding tool downstream
    * requires it per field and this is the tool a caller runs first. Merging the folders removed
    * the only place it used to live implicitly (the group label), so it moves onto the field.
    */
   @Test
   void reportsDimensionOrMeasurePerColumn() throws Exception {
      TreeNodeModel qty = TreeNodeModel.builder().label("Qty").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "Qty", "integer", AssetEntry.MEASURES)).build();
      TreeNodeModel city = TreeNodeModel.builder().label("City").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "City", "string", AssetEntry.DIMENSIONS)).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("ORDERS1").data(entry(AssetEntry.Type.TABLE, "ORDERS1", null))
         .addChildren(city).addChildren(qty).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableField> fields = serviceReturning(root).list("rt1", null, principal())
         .get(0).fields();

      assertEquals("dimension", fields.get(0).role());
      assertEquals("measure", fields.get(1).role());
   }

   /**
    * The third state: CUBE_COL_TYPE present but <em>empty</em>.
    *
    * <p>{@code BaseTreeModelBuilder.applyColumnNodeProperties} writes {@code type == -1 ? "" : …},
    * and {@code appendColumnNodes} passes -1 for a table VS assembly — a tree this tool reads. So
    * every column of a table assembly carries an empty value. Guarding only on null let
    * {@code parseInt("")} throw and swallowed the data-type fallback, leaving role null with the
    * data type sitting right there.
    */
   @Test
   void treatsAnEmptyCubeColumnTypeAsUnstatedAndFallsBackToTheDataType() throws Exception {
      TreeNodeModel amount = TreeNodeModel.builder().label("Amount").leaf(true)
         .data(entryWithBlankCubeType("double")).build();
      TreeNodeModel name = TreeNodeModel.builder().label("Name").leaf(true)
         .data(entryWithBlankCubeType("string")).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("T").data(entry(AssetEntry.Type.TABLE, "T", null))
         .addChildren(name).addChildren(amount).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableField> fields = serviceReturning(root).list("rt1", null, principal())
         .get(0).fields();

      assertEquals("dimension", fields.get(0).role());
      assertEquals("measure", fields.get(1).role());
   }

   private static AssetEntry entryWithBlankCubeType(String dtype) {
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.getType()).thenReturn(AssetEntry.Type.COLUMN);
      when(entry.getProperty("dtype")).thenReturn(dtype);
      when(entry.getProperty(AssetEntry.CUBE_COL_TYPE)).thenReturn("");
      return entry;
   }

   /** Without CUBE_COL_TYPE at all, fall back to the data type. */
   @Test
   void fallsBackToTheDataTypeWhenTheTreeCarriesNoCubeColumnType() throws Exception {
      TreeNodeModel amount = TreeNodeModel.builder().label("Amount").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "Amount", "double")).build();
      TreeNodeModel name = TreeNodeModel.builder().label("Name").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "Name", "string")).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("T").data(entry(AssetEntry.Type.TABLE, "T", null))
         .addChildren(name).addChildren(amount).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableField> fields = serviceReturning(root).list("rt1", null, principal())
         .get(0).fields();

      assertEquals("dimension", fields.get(0).role());
      assertEquals("measure", fields.get(1).role());
   }

   @Test
   void returnsNoTablesRatherThanFailingWhenTheTreeIsEmpty() throws Exception {
      assertTrue(serviceReturning(null).list("rt1", null, principal()).isEmpty());
   }

   /**
    * The B3-observed live shape: an unscoped call's tree has a WORKSHEET-typed container node
    * between the root and the real tables, and {@code VSTreeHandler.isLeaf} marks every
    * {@code AssetEntry.Type.WORKSHEET} entry a leaf unconditionally -- so this container node is
    * {@code leaf: true} at the same time as carrying real table children, because
    * {@code createNodeFromEntry} sets the two independently.
    *
    * <p>Trusting {@code leaf()} treated the container itself as a column: {@code
    * fieldOf(wsContainer)} turned its own label into a fabricated {@code {column, dataType: null,
    * role: null}}, and the real table beneath it was never visited. Observed live as
    * {@code {name: null, fields: [{column: "a1", dataType: null, role: null}]}} in place of the
    * viewsheet's actual tables.
    */
   @Test
   void walksIntoAWorksheetTypedContainerNodeDespiteItClaimingToBeALeaf() throws Exception {
      TreeNodeModel column = TreeNodeModel.builder().label("Sales").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "Sales", "double")).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("Orders").data(entry(AssetEntry.Type.TABLE, "Orders", null))
         .addChildren(column).build();
      // The container: WORKSHEET-typed (so VSTreeHandler.isLeaf marks it leaf: true) but carrying
      // the real table as a child regardless -- exactly what refreshBaseWSTree's non-direct-source
      // branch builds.
      TreeNodeModel wsContainer = TreeNodeModel.builder()
         .label("MyWorksheet").leaf(true)
         .data(entry(AssetEntry.Type.WORKSHEET, "MyWorksheet", null))
         .addChildren(table).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(wsContainer).build();

      List<BindableTable> tables = serviceReturning(root).list("rt1", null, principal());

      assertEquals(1, tables.size(),
                   "must not fabricate a field from the WORKSHEET container's own label");
      assertEquals("Orders", tables.get(0).name());
      assertEquals("Sales", tables.get(0).fields().get(0).column());
   }

   /**
    * Review finding (larryliang-inetsoft): trusting childlessness alone reintroduces the same
    * defect one level up. A table with nothing exposed under it -- permission-filtered, a fresh
    * embedded table, mid-load metadata -- has no children either, so without excluding tables
    * from {@code isColumn} its own label gets fabricated into a column standing in for it, the
    * same shape as the WORKSHEET-container bug this fix closes. The old {@code leaf()}-based code
    * never had this failure mode, because {@code leaf()} is hardcoded false for table types
    * regardless of children.
    */
   @Test
   void anEmptyTableYieldsNoFieldsRatherThanFabricatingOneFromItsOwnLabel() throws Exception {
      TreeNodeModel emptyTable = TreeNodeModel.builder()
         .label("EmptyTable").data(entry(AssetEntry.Type.TABLE, "EmptyTable", null)).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(emptyTable).build();

      List<BindableTable> tables = serviceReturning(root).list("rt1", null, principal());

      assertTrue(tables.isEmpty(),
                 "an empty table must report no fields, not a fabricated column named after it");
   }

   /**
    * A childless Dimensions/Measures folder must yield nothing, not a field named after itself.
    *
    * <p>{@code VSTreeHandler} adds <em>both</em> grouping folders to every table unconditionally
    * (it checks only that the table has at least one ref of either kind), so a table whose columns
    * are all measures carries an <b>empty</b> Dimensions folder. Childless, and not a table, it
    * satisfied {@link BindableFieldsService#isColumn} and its own label was fabricated into
    * {@code {column: "Dimensions", dataType: null, role: null}}. Observed live on
    * {@code ORDER_DETAILS1}, whose three columns are all integers, and reproduced on two separate
    * viewsheets — but only through a scoped call, since the unscoped tree has no such folders.
    *
    * <p>This is the third instance of the class {@code isColumn}'s own Javadoc records fixing
    * twice. Both previous fixes keyed on what the node <em>is</em> — a WORKSHEET container, then a
    * table — and a folder is neither, so it kept slipping through.
    */
   @Test
   void anEmptyDimensionsFolderYieldsNoFieldRatherThanFabricatingOneFromItsLabel()
      throws Exception
   {
      TreeNodeModel qty = TreeNodeModel.builder().label("QUANTITY").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "QUANTITY", "integer", AssetEntry.MEASURES)).build();
      // Empty, exactly as VSTreeHandler leaves it for a table with no dimension refs.
      TreeNodeModel dimensions = TreeNodeModel.builder().label("Dimensions")
         .data(entry(AssetEntry.Type.FOLDER, "Dimensions", null)).build();
      TreeNodeModel measures = TreeNodeModel.builder().label("Measures")
         .data(entry(AssetEntry.Type.FOLDER, "Measures", null)).addChildren(qty).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("ORDER_DETAILS1").data(entry(AssetEntry.Type.TABLE, "ORDER_DETAILS1", null))
         .addChildren(dimensions).addChildren(measures).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableField> fields = serviceReturning(root).list("rt1", null, principal())
         .get(0).fields();

      assertEquals(List.of("QUANTITY"), fields.stream().map(BindableField::column).toList(),
                   "an empty grouping folder must not appear as a bindable column");
   }

   /**
    * The mirror case, for a table with only dimensions and no measures.
    *
    * <p>Stated plainly: this passed the moment the fix above landed, so it drove nothing. It is
    * here as a guard, because the tempting narrow fix is to special-case the literal label
    * {@code "Dimensions"} — which is what the live reproduction happened to show — and that fix
    * would leave the identical hole open on the other folder. Only the live case was ever
    * observed; this one is derived from {@code VSTreeHandler} adding both folders unconditionally.
    */
   @Test
   void anEmptyMeasuresFolderIsExcludedToo() throws Exception {
      TreeNodeModel city = TreeNodeModel.builder().label("City").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "City", "string", AssetEntry.DIMENSIONS)).build();
      TreeNodeModel dimensions = TreeNodeModel.builder().label("Dimensions")
         .data(entry(AssetEntry.Type.FOLDER, "Dimensions", null)).addChildren(city).build();
      TreeNodeModel measures = TreeNodeModel.builder().label("Measures")
         .data(entry(AssetEntry.Type.FOLDER, "Measures", null)).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("REGIONS").data(entry(AssetEntry.Type.TABLE, "REGIONS", null))
         .addChildren(dimensions).addChildren(measures).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableField> fields = serviceReturning(root).list("rt1", null, principal())
         .get(0).fields();

      assertEquals(List.of("City"), fields.stream().map(BindableField::column).toList());
   }

   /**
    * A table under a folder still reports its columns — the exclusion must not stop the walk.
    *
    * <p>{@link BindableFieldsService#collect} recurses into anything that is not a column, so
    * excluding folders from {@code isColumn} only changes what becomes a <em>field</em>, never
    * what gets descended into. This pins that down, because the obvious wrong way to write the
    * fix — returning early for folders in {@code collect}/{@code gather} — would silently drop
    * every column beneath the Dimensions and Measures folders and make the tool return nothing
    * at all for a scoped call.
    */
   @Test
   void aFolderWithChildrenIsStillWalkedIntoRatherThanSkipped() throws Exception {
      TreeNodeModel qty = TreeNodeModel.builder().label("QUANTITY").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "QUANTITY", "integer", AssetEntry.MEASURES)).build();
      TreeNodeModel measures = TreeNodeModel.builder().label("Measures")
         .data(entry(AssetEntry.Type.FOLDER, "Measures", null)).addChildren(qty).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("ORDER_DETAILS1").data(entry(AssetEntry.Type.FOLDER, "grouping", null))
         .addChildren(TreeNodeModel.builder()
                         .label("ORDER_DETAILS1")
                         .data(entry(AssetEntry.Type.TABLE, "ORDER_DETAILS1", null))
                         .addChildren(measures).build())
         .build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableTable> tables = serviceReturning(root).list("rt1", null, principal());

      assertEquals(1, tables.size());
      assertEquals("ORDER_DETAILS1", tables.get(0).name());
      assertEquals(List.of("QUANTITY"),
                   tables.get(0).fields().stream().map(BindableField::column).toList());
   }

   // ── which table the assembly is actually bound to ─────────────────────────

   /**
    * A scoped call marks the assembly's current source, because every field on a chart's shelves
    * must come from that one table.
    *
    * <p>The constraint is enforced hard by the Composer and invisible here: dropping a column from
    * a second table calls {@code VSAssemblyInfoHandler.changeSource} and then
    * {@code validateChartColumns}, which <em>deletes</em> every bound field absent from the new
    * source. So a chart holds fields from exactly one table, ever. The agent write path
    * ({@code changeChartRef}) never calls {@code validateBinding} at all, so an off-source column
    * lands there as a ref that resolves to nothing.
    *
    * <p>Listing every table is still right — a chart may be repointed to any of them — so the fix
    * is not to narrow the list but to say which one is live. Otherwise the rule can only be obeyed
    * by cross-referencing a second call to {@code get_binding}, and nothing tells a caller to make
    * it: three tables come back, all of them look equally bindable, and the wrong choice is
    * accepted silently.
    */
   @Test
   void marksTheTableTheAssemblyIsCurrentlyBoundTo() throws Exception {
      TreeNodeModel root = twoTableTree();
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getSourceInfo())
         .thenReturn(new SourceInfo(SourceInfo.ASSET, null, "ORDERS1"));

      List<BindableTable> tables = serviceFor(root, "Chart1", chart).list("rt1", "Chart1",
                                                                         principal());

      assertEquals(Boolean.TRUE, byName(tables, "ORDERS1").current());
      assertEquals(Boolean.FALSE, byName(tables, "ORDER_DETAILS1").current(),
                   "a table the assembly is not bound to must say so, not stay silent");
   }

   /**
    * An unscoped call leaves it unset rather than saying {@code false} everywhere.
    *
    * <p>There is no assembly to be current *for*, so {@code false} would be an assertion the call
    * is in no position to make — and the reading that matters ("none of these is the live one") is
    * exactly wrong.
    */
   @Test
   void leavesCurrentUnsetWhenNoAssemblyWasNamed() throws Exception {
      List<BindableTable> tables = serviceReturning(twoTableTree()).list("rt1", null, principal());

      assertNull(byName(tables, "ORDERS1").current());
      assertNull(byName(tables, "ORDER_DETAILS1").current());
   }

   /** An assembly with no source yet — nothing is current, and nothing pretends to be. */
   @Test
   void marksNothingCurrentWhenTheAssemblyHasNoSourceYet() throws Exception {
      TreeNodeModel root = twoTableTree();
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getSourceInfo()).thenReturn(null);

      List<BindableTable> tables = serviceFor(root, "Chart1", chart).list("rt1", "Chart1",
                                                                         principal());

      assertEquals(Boolean.FALSE, byName(tables, "ORDERS1").current());
      assertEquals(Boolean.FALSE, byName(tables, "ORDER_DETAILS1").current());
   }

   private static BindableTable byName(List<BindableTable> tables, String name) {
      return tables.stream().filter(t -> name.equals(t.name())).findFirst().orElseThrow();
   }

   private static TreeNodeModel twoTableTree() {
      TreeNodeModel paid = TreeNodeModel.builder().label("PAID").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "PAID", "integer", AssetEntry.MEASURES)).build();
      TreeNodeModel orders = TreeNodeModel.builder()
         .label("ORDERS1").data(entry(AssetEntry.Type.TABLE, "ORDERS1", null))
         .addChildren(paid).build();
      TreeNodeModel qty = TreeNodeModel.builder().label("QUANTITY").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "QUANTITY", "integer", AssetEntry.MEASURES)).build();
      TreeNodeModel details = TreeNodeModel.builder()
         .label("ORDER_DETAILS1").data(entry(AssetEntry.Type.TABLE, "ORDER_DETAILS1", null))
         .addChildren(qty).build();

      return TreeNodeModel.builder().label("root")
         .addChildren(orders).addChildren(details).build();
   }

   private static BindableFieldsService serviceFor(TreeNodeModel root, String name,
                                                   inetsoft.uql.viewsheet.VSAssembly assembly)
      throws Exception
   {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(name)).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getViewsheet(eq("rt1"), any(Principal.class))).thenReturn(rvs);
      VSBindingTreeService tree = mock(VSBindingTreeService.class);
      when(tree.getBinding(eq("rt1"), any(), anyBoolean(), any(Principal.class))).thenReturn(root);

      return new BindableFieldsService(tree, engine);
   }

   // ── unknown assembly name (the D4 regression) ────────────────────────────

   /**
    * {@code VSBindingTreeService.getBinding} resolves {@code viewsheet.getAssembly(name)}, which
    * returns {@code null} for an unknown name exactly as it does for {@code null} itself — so
    * scoping to a name that does not exist used to fall into the same "list everything" branch as
    * not scoping at all, and silently returned the whole-viewsheet field list instead of refusing.
    */
   @Test
   void refusesAnAssemblyNameThatDoesNotExistRatherThanListingEverything() throws Exception {
      TreeNodeModel root = TreeNodeModel.builder().label("root").build();
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Nope")).thenReturn(null);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getViewsheet(eq("rt1"), any(Principal.class))).thenReturn(rvs);
      VSBindingTreeService tree = mock(VSBindingTreeService.class);
      when(tree.getBinding(eq("rt1"), any(), anyBoolean(), any(Principal.class))).thenReturn(root);
      BindableFieldsService service = new BindableFieldsService(tree, engine);

      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> service.list("rt1", "Nope", principal()));

      assertTrue(thrown.getMessage().contains("Nope"));
      verify(tree, never()).getBinding(anyString(), any(), anyBoolean(), any(Principal.class));
   }

   /** A real assembly name still resolves normally, without touching the existence check's error. */
   @Test
   void listsNormallyWhenTheAssemblyNameIsReal() throws Exception {
      inetsoft.uql.viewsheet.VSAssembly assembly = mock(inetsoft.uql.viewsheet.VSAssembly.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Table1")).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getViewsheet(eq("rt1"), any(Principal.class))).thenReturn(rvs);
      VSBindingTreeService tree = mock(VSBindingTreeService.class);
      when(tree.getBinding(eq("rt1"), eq("Table1"), anyBoolean(), any(Principal.class)))
         .thenReturn(null);
      BindableFieldsService service = new BindableFieldsService(tree, engine);

      assertTrue(service.list("rt1", "Table1", principal()).isEmpty());
   }

   /** Omitting the assembly is the deliberate "list everything" request; it must not resolve one. */
   @Test
   void doesNotResolveAnAssemblyWhenNoneWasNamed() throws Exception {
      ViewsheetService engine = mock(ViewsheetService.class);
      serviceReturning(null, engine).list("rt1", null, principal());

      verify(engine, never()).getViewsheet(anyString(), any(Principal.class));
   }

   private static BindableFieldsService serviceReturning(TreeNodeModel root) throws Exception {
      return serviceReturning(root, mock(ViewsheetService.class));
   }

   private static BindableFieldsService serviceReturning(TreeNodeModel root,
                                                         ViewsheetService engine)
      throws Exception
   {
      VSBindingTreeService tree = mock(VSBindingTreeService.class);
      when(tree.getBinding(eq("rt1"), any(), anyBoolean(), any(Principal.class)))
         .thenReturn(root);
      return new BindableFieldsService(tree, engine);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}

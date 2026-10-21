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
package inetsoft.uql.viewsheet;

import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.CrosstabTree;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * {@code updateGroupExpandPath}'s two casts to {@code SNamedGroupInfo} widen to
 * {@code XNamedGroupInfo} -- a pure widen, since the method only ever calls {@code isEmpty()}/
 * {@code equals()}, both already interface-generic. Invoked via reflection since the method
 * itself is private and its public entry point ({@code setVSAssemblyInfo}) needs a full
 * cube/aggregate-change scenario unrelated to what this method actually does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrosstabVSAssemblyTest {
   private static VSDimensionRef regionRef() {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setDataRef(new ColumnRef(new AttributeRef("REGION")));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   private static void markDrilled(CrosstabVSAssembly assembly, String field) throws Exception {
      Field expandedField = CrosstabTree.class.getDeclaredField("expanded");
      expandedField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Set<String>> expanded =
         (Map<String, Set<String>>) expandedField.get(assembly.getCrosstabTree());
      expanded.put(field, new HashSet<>(Set.of("some-drilled-value")));
   }

   private static void invokeUpdateGroupExpandPath(CrosstabVSAssembly assembly,
                                                    VSDimensionRef nref, VSDimensionRef oref)
      throws Exception
   {
      Method method = CrosstabVSAssembly.class.getDeclaredMethod(
         "updateGroupExpandPath", inetsoft.uql.erm.DataRef.class, inetsoft.uql.erm.DataRef.class);
      method.setAccessible(true);
      method.invoke(assembly, nref, oref);
   }

   @Test
   void removesTheDrillWhenAnExpertNamedGroupIsAddedToAPreviouslyUngroupedDimension() throws Exception {
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();
      VSDimensionRef oref = regionRef();
      markDrilled(assembly, oref.getFullName());

      VSDimensionRef nref = regionRef();
      nref.setNamedGroupInfo(new ExpertNamedGroupInfo());
      ExpertNamedGroupInfo info = (ExpertNamedGroupInfo) nref.getNamedGroupInfo();
      info.setGroupCondition("West", new inetsoft.uql.ConditionList());

      assertDoesNotThrow(() -> invokeUpdateGroupExpandPath(assembly, nref, oref));
      assertFalse(assembly.getCrosstabTree().isDrilled(oref.getFullName()),
         "grouping a previously-ungrouped dimension should invalidate its drill path");
   }

   @Test
   void removesTheDrillWhenAnExpertNamedGroupIsChanged() throws Exception {
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();

      VSDimensionRef oref = regionRef();
      ExpertNamedGroupInfo oldInfo = new ExpertNamedGroupInfo();
      oldInfo.setGroupCondition("West", new inetsoft.uql.ConditionList());
      oref.setNamedGroupInfo(oldInfo);
      markDrilled(assembly, oref.getFullName());

      VSDimensionRef nref = regionRef();
      ExpertNamedGroupInfo newInfo = new ExpertNamedGroupInfo();
      newInfo.setGroupCondition("East", new inetsoft.uql.ConditionList());
      nref.setNamedGroupInfo(newInfo);

      assertDoesNotThrow(() -> invokeUpdateGroupExpandPath(assembly, nref, oref));
      assertFalse(assembly.getCrosstabTree().isDrilled(oref.getFullName()),
         "changing an Expert named group's conditions should invalidate the drill path");
   }

   /**
    * Bug #76951 (VCF-002), found live: renaming a bound calc field renamed the aggregate's column
    * value but left the ref it had already resolved to on the old name. VSAggregateRef.update()
    * names the aggregate's alias from a clone of that stale ref, so the crosstab kept labelling
    * the renamed measure 'Sum(Net Sales)' and lost the format keyed on 'Sum(Net Revenue)'.
    */
   @Test
   void renameBindingColAlsoRenamesTheAggregatesResolvedRef() {
      ExpressionRef expr = new ExpressionRef(null, "Net Sales");
      expr.setExpression("field['Total']");
      CalculateRef resolved = new CalculateRef(true);
      resolved.setDataRef(expr);

      VSAggregateRef aggr = new VSAggregateRef();
      aggr.setColumnValue("Net Sales");
      aggr.setFormulaValue("Sum");
      aggr.setDataRef(resolved);

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignAggregates(new inetsoft.uql.erm.DataRef[] { aggr });
      cinfo.setAggregateInfo(new AggregateInfo());
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();
      assembly.setVSCrosstabInfo(cinfo);

      assembly.renameBindingCol("Net Sales", "Net Revenue");

      assertEquals("Net Revenue", aggr.getColumnValue());
      assertEquals("Net Revenue", aggr.getDataRef().getAttribute());
      assertEquals("Net Revenue", aggr.getVSName());
      assertEquals("Net Sales", resolved.getAttribute(),
         "the rename must work on a copy, never mutate the ref it was resolved from");
   }

   /**
    * Bug #78973: renaming a bound calc field rewrote a dimension's Top-N ranking
    * ({@code rankingColValue}) but left its value sort ({@code sortByColValue}) pointing at the
    * old aggregate name -- the two fields are structurally parallel (same
    * {@code "Formula(column)"} shape, same {@code VSUtil.matchRefName}/{@code getMatchRefName}
    * matching logic) but only one of them was rewritten. Covers both the column-header and
    * row-header loops in {@code renameBindingCol}.
    */
   @Test
   void renameBindingColAlsoRenamesTheDimensionsSortByField() {
      VSDimensionRef colRef = regionRef();
      colRef.setRankingColValue("Sum(Net Revenue)");
      colRef.setSortByColValue("Sum(Net Revenue)");

      VSDimensionRef rowRef = regionRef();
      rowRef.setRankingColValue("Sum(Net Revenue)");
      rowRef.setSortByColValue("Sum(Net Revenue)");

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignColHeaders(new inetsoft.uql.erm.DataRef[] { colRef });
      cinfo.setDesignRowHeaders(new inetsoft.uql.erm.DataRef[] { rowRef });
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();
      assembly.setVSCrosstabInfo(cinfo);

      assembly.renameBindingCol("Net Revenue", "Net Sales");

      assertEquals("Sum(Net Sales)", colRef.getRankingColValue());
      assertEquals("Sum(Net Sales)", colRef.getSortByColValue(),
         "a column header's value sort must follow a calc-field rename just like its ranking does");
      assertEquals("Sum(Net Sales)", rowRef.getRankingColValue());
      assertEquals("Sum(Net Sales)", rowRef.getSortByColValue(),
         "a row header's value sort must follow a calc-field rename just like its ranking does");
   }

   /**
    * Bug #78974: renaming a calc-field-backed aggregate ('Net Sales' -> 'Net Revenue') left a
    * crosstab highlight's condition attribute (a {@code BaseField}, the real runtime type a
    * crosstab highlight condition's attribute is built as -- not a bare {@code AttributeRef})
    * pointed at the old formula-wrapped name ('Sum(Net Sales)'), because
    * {@code VSUtil.renameDataRef} has no branch for that shape and silently no-ops. The stale
    * reference then failed {@code VSAssemblyInfoHandler.cleanHighlight}'s column-resolution check
    * (invoked separately, right after the rename, on every crosstab binding re-apply) and the
    * whole highlight was deleted. This test proves both halves of the fix: the condition's
    * attribute is actually renamed (and keeps its concrete {@code BaseField} type, not downgraded
    * to a plain {@code AttributeRef}), and the highlight survives the real
    * {@code cleanHighlight} pass once that rename has happened.
    */
   @Test
   void renameBindingColRenamesHighlightConditionBaseFieldAndSurvivesCleanHighlight() throws Exception {
      ExpressionRef expr = new ExpressionRef(null, "Net Sales");
      expr.setExpression("field['Total']");
      CalculateRef resolved = new CalculateRef(true);
      resolved.setDataRef(expr);

      VSAggregateRef aggr = new VSAggregateRef();
      aggr.setColumnValue("Net Sales");
      aggr.setFormulaValue("Sum");
      aggr.setDataRef(resolved);

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignAggregates(new inetsoft.uql.erm.DataRef[] { aggr });
      cinfo.setAggregateInfo(new AggregateInfo());
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();
      assembly.setVSCrosstabInfo(cinfo);

      inetsoft.report.internal.binding.BaseField condAttr =
         new inetsoft.report.internal.binding.BaseField(null, "Sum(Net Sales)");
      condAttr.setDataType(XSchema.DOUBLE);
      inetsoft.uql.Condition cond = new inetsoft.uql.Condition();
      cond.setOperation(inetsoft.uql.XCondition.GREATER_THAN);
      cond.addValue(3000000d);
      inetsoft.uql.ConditionList clist = new inetsoft.uql.ConditionList();
      clist.append(new inetsoft.uql.ConditionItem(condAttr, cond, 0));

      inetsoft.report.filter.ColumnHighlight highlight = new inetsoft.report.filter.ColumnHighlight();
      highlight.setConditionGroup(clist);
      inetsoft.report.filter.HighlightGroup hgroup = new inetsoft.report.filter.HighlightGroup();
      hgroup.addHighlight("Above3M", highlight);

      inetsoft.report.internal.table.TableHighlightAttr thattr =
         new inetsoft.report.internal.table.TableHighlightAttr();
      inetsoft.report.TableDataPath path = new inetsoft.report.TableDataPath("Sum(Net Sales)");
      path.setType(inetsoft.report.TableDataPath.SUMMARY);
      thattr.setHighlight(path, hgroup);
      assembly.getCrosstabInfo().setHighlightAttr(thattr);

      assembly.renameBindingCol("Net Sales", "Net Revenue");

      // 1. the condition's attribute was actually renamed, and kept its concrete BaseField type
      inetsoft.uql.erm.DataRef renamedRef = clist.getConditionItem(0).getAttribute();
      assertEquals("Sum(Net Revenue)", renamedRef.getName(),
         "the highlight condition's BaseField attribute must follow the rename, not stay stale");
      assertInstanceOf(inetsoft.report.internal.binding.BaseField.class, renamedRef,
         "the renamed ref must stay a BaseField, not be downgraded to a plain AttributeRef");

      // 2. the highlight survives the real cleanHighlight pass that runs right after a rename
      inetsoft.web.binding.handler.VSAssemblyInfoHandler handler =
         new inetsoft.web.binding.handler.VSAssemblyInfoHandler(
            mock(inetsoft.web.viewsheet.service.CoreLifecycleService.class),
            mock(inetsoft.web.binding.service.DataRefModelFactoryService.class),
            mock(inetsoft.web.viewsheet.service.ParameterService.class),
            mock(inetsoft.uql.service.DataSourceRegistry.class),
            mock(inetsoft.web.portal.controller.database.QueryManagerService.class));
      Method cleanHighlight = inetsoft.web.binding.handler.VSAssemblyInfoHandler.class
         .getDeclaredMethod("cleanHighlight",
            inetsoft.report.internal.table.TableHighlightAttr.class,
            inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo.class);
      cleanHighlight.setAccessible(true);
      cleanHighlight.invoke(handler, thattr, assembly.getCrosstabInfo());

      assertNotNull(hgroup.getHighlight("Above3M"),
         "the highlight must survive cleanHighlight once its condition is correctly renamed");
   }

   @Test
   void leavesTheDrillAloneWhenTheExpertNamedGroupIsUnchanged() throws Exception {
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();

      VSDimensionRef oref = regionRef();
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      info.setGroupCondition("West", new inetsoft.uql.ConditionList());
      oref.setNamedGroupInfo(info);
      markDrilled(assembly, oref.getFullName());

      VSDimensionRef nref = regionRef();
      nref.setNamedGroupInfo((ExpertNamedGroupInfo) info.clone());

      assertDoesNotThrow(() -> invokeUpdateGroupExpandPath(assembly, nref, oref));
      assertTrue(assembly.getCrosstabTree().isDrilled(oref.getFullName()),
         "an unchanged Expert named group must not disturb an existing drill path");
   }
}

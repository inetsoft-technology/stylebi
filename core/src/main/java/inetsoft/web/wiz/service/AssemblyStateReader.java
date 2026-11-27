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

package inetsoft.web.wiz.service;

import inetsoft.report.TableDataPath;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.BindableVSAssembly;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.DataVSAssembly;
import inetsoft.uql.viewsheet.DynamicBindableVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.CandleChartInfo;
import inetsoft.uql.viewsheet.graph.ChartBindable;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.HighlightRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.WorksheetStructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Reads the parts of {@link CreateViewsheetResult.AssemblyState} straight from the assembly model:
 * no Composer pairing session, no per-Region dialog read and no rendered table lens, so it works
 * on a skip-execution runtime too. See {@code WizVsService#fillAssemblyState}.
 */
final class AssemblyStateReader {
   private AssemblyStateReader() {
   }

   /**
    * Where the assembly reads its data: the viewsheet's base entry kind ("worksheet", "model" or
    * "physical", null for an unrecognized base) and, for a worksheet base, its identifier; the bound
    * table from the source info (data assemblies) or the binding table name (output assemblies),
    * with the outer-table suffix stripped.
    */
   static CreateViewsheetResult.AssemblySource readSource(Viewsheet vs, VSAssembly assembly) {
      AssetEntry base = vs == null ? null : vs.getBaseEntry();
      String kind = null;
      String worksheet = null;

      if(base != null) {
         switch(MetadataApiService.sourceKindOf(base)) {
         case "worksheet" -> {
            kind = "worksheet";
            worksheet = base.toIdentifier();
         }
         case "logicalModel" -> kind = "model";
         case "physicalTable" -> kind = "physical";
         default -> kind = null;
         }
      }

      String table = null;

      if(assembly instanceof DataVSAssembly data && data.getSourceInfo() != null) {
         table = data.getSourceInfo().getSource();
      }
      else if(assembly instanceof BindableVSAssembly bindable) {
         table = bindable.getTableName();
      }

      return new CreateViewsheetResult.AssemblySource(kind, worksheet, VSUtil.stripOuter(table));
   }

   /** The assembly's own pre-condition list, as structure leaves with phase "chart". */
   static List<WorksheetStructure.ConditionLeaf> readConditions(VSAssembly assembly) {
      List<WorksheetStructure.ConditionLeaf> out = new ArrayList<>();

      if(assembly instanceof DynamicBindableVSAssembly bindable) {
         MetadataApiService.collectStructureConditions(bindable.getPreConditionList(), "chart", out);
      }

      return out;
   }

   /**
    * Every highlight the assembly holds. A chart: its chart-level group, then the group and text
    * group of every design binding ref and of the text aesthetic ref (the chart's own, plus each
    * binding ref's own text field on a multi-style chart), keyed by the ref's full name; a
    * candle/stock chart's high/low/open/close fields count as binding refs here.
    * A table or crosstab: every cell path of its highlight attribute, keyed by the path's last
    * segment ("row" for a row path). An output: its one group. A group reached twice (the same
    * group object) is listed once; distinct groups are always listed, even under the same name.
    */
   static List<CreateViewsheetResult.AssemblyHighlight> readHighlights(VSAssembly assembly) {
      List<CreateViewsheetResult.AssemblyHighlight> out = new ArrayList<>();
      Set<HighlightGroup> seen = Collections.newSetFromMap(new IdentityHashMap<>());
      VSAssemblyInfo info = assembly == null ? null : assembly.getVSAssemblyInfo();

      if(assembly instanceof ChartVSAssembly chart) {
         VSChartInfo chartInfo = chart.getVSChartInfo();

         if(chartInfo != null) {
            addGroup(out, seen, chartInfo.getHighlightGroup(), null);
            List<ChartRef> refs = new ArrayList<>(List.of(chartInfo.getBindingRefs(false)));

            // Candle/stock high/low/open/close fields are not binding refs but carry their own
            // highlight groups (as AssemblyHighlightService.textFields enumerates them).
            if(chartInfo instanceof CandleChartInfo candle) {
               for(ChartRef ref : new ChartRef[] { candle.getHighField(), candle.getLowField(),
                                                   candle.getOpenField(), candle.getCloseField() })
               {
                  if(ref != null) {
                     refs.add(ref);
                  }
               }
            }

            for(ChartRef ref : refs) {
               addRef(out, seen, ref);
            }

            addText(out, seen, chartInfo.getTextField());

            // Multi-style charts (and gantt, which is always multi-aesthetic) keep a text field,
            // with its own text highlight group, on each binding ref and candle/stock field.
            if(chartInfo.isMultiAesthetic()) {
               for(ChartRef ref : refs) {
                  if(ref instanceof ChartBindable bindable) {
                     addText(out, seen, bindable.getTextField());
                  }
               }
            }
         }
      }
      else if(info instanceof TableDataVSAssemblyInfo tableInfo) {
         TableHighlightAttr attr = tableInfo.getHighlightAttr();

         if(attr != null) {
            Enumeration<TableDataPath> paths = attr.getAllDataPaths();

            while(paths.hasMoreElements()) {
               TableDataPath path = paths.nextElement();

               if(path != null) {
                  addGroup(out, seen, attr.getHighlight(path), tableTarget(path));
               }
            }
         }
      }
      else if(info instanceof OutputVSAssemblyInfo outputInfo) {
         addGroup(out, seen, outputInfo.getHighlightGroup(), null);
      }

      return out;
   }

   /** The visible columns of a plain table; null for every other assembly type. */
   static CreateViewsheetResult.TableColumns readTableColumns(VSAssembly assembly) {
      if(!(assembly instanceof TableVSAssembly table)) {
         return null;
      }

      List<String> names = new ArrayList<>();
      ColumnSelection visible = table.getVisibleColumns();

      for(int i = 0; visible != null && i < visible.getAttributeCount(); i++) {
         DataRef ref = visible.getAttribute(i);

         if(ref != null) {
            names.add(ref.getName());
         }
      }

      return new CreateViewsheetResult.TableColumns(names);
   }

   /**
    * One condition leaf as a clause string, in the field order of the highlight read's clauses:
    * junction, negation, field, operator (inclusive bound folded in), values.
    */
   static String formatClause(WorksheetStructure.ConditionLeaf leaf) {
      StringBuilder out = new StringBuilder();

      if(leaf.getJunction() != null) {
         out.append(leaf.getJunction()).append(' ');
      }

      if(leaf.isNegated()) {
         out.append("NOT ");
      }

      String operation = leaf.getOperation();

      if(Boolean.TRUE.equals(leaf.getEqual()) &&
         ("LESS_THAN".equals(operation) || "GREATER_THAN".equals(operation)))
      {
         operation += "_OR_EQUAL";
      }

      if(leaf.getField() != null) {
         out.append(leaf.getField()).append(' ');
      }

      out.append(operation);

      if(leaf.getValues() != null && !leaf.getValues().isEmpty()) {
         out.append(' ').append(String.join(", ", leaf.getValues()));
      }

      return out.toString();
   }

   private static void addText(List<CreateViewsheetResult.AssemblyHighlight> out,
                               Set<HighlightGroup> seen, AestheticRef text)
   {
      if(text != null && text.getDataRef() instanceof ChartRef textRef) {
         addRef(out, seen, textRef);
      }
   }

   private static void addRef(List<CreateViewsheetResult.AssemblyHighlight> out,
                              Set<HighlightGroup> seen, ChartRef ref)
   {
      if(ref instanceof HighlightRef highlightRef) {
         String target = ref.getFullName();
         addGroup(out, seen, highlightRef.getHighlightGroup(), target);
         addGroup(out, seen, highlightRef.getTextHighlightGroup(), target);
      }
   }

   private static void addGroup(List<CreateViewsheetResult.AssemblyHighlight> out,
                                Set<HighlightGroup> seen, HighlightGroup group, String target)
   {
      if(group == null || !seen.add(group)) {
         return;
      }

      for(String level : group.getLevels()) {
         for(String name : group.getNames(level)) {
            Highlight highlight = group.getHighlight(level, name);

            if(highlight == null) {
               continue;
            }

            List<WorksheetStructure.ConditionLeaf> leaves = new ArrayList<>();
            MetadataApiService.collectStructureConditions(highlight.getConditionGroup(), "highlight", leaves);
            List<String> clauses = new ArrayList<>();

            for(WorksheetStructure.ConditionLeaf leaf : leaves) {
               clauses.add(formatClause(leaf));
            }

            out.add(new CreateViewsheetResult.AssemblyHighlight(name, target, clauses));
         }
      }
   }

   private static String tableTarget(TableDataPath path) {
      if(path.isRow()) {
         return "row";
      }

      String[] segments = path.getPath();
      return segments == null || segments.length == 0 ? null : segments[segments.length - 1];
   }
}

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

import inetsoft.report.TableDataPath;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.SortInfo;
import inetsoft.uql.asset.SortRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.HighlightRef;
import inetsoft.uql.viewsheet.graph.HyperlinkRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;

import java.util.*;

/**
 * Bug #79651: removing a calc field strips it from every binding, but the native remove path
 * leaves a dimension's sort-by / ranking measure pointing at the removed aggregate and silently
 * deletes the highlights, hyperlinks and selection measure that named it. This clears the sort and
 * ranking that name the field and reports everything the removal took with it.
 */
final class CalcFieldRemovalCleanup {
   private final Viewsheet vs;
   private final String table;
   private final String field;
   private final List<String> messages = new ArrayList<>();
   private final Map<String, Map<String, HlSlot>> highlights = new LinkedHashMap<>();
   private final Map<String, Integer> hyperlinks = new LinkedHashMap<>();

   private CalcFieldRemovalCleanup(Viewsheet vs, String table, String field) {
      this.vs = vs;
      this.table = table;
      this.field = field;
   }

   /**
    * Clears the sort and ranking of every dimension on {@code table} that names {@code field},
    * and snapshots the highlights and hyperlinks so {@link #finish} can report what the removal
    * deleted. Call before the removal.
    */
   static CalcFieldRemovalCleanup begin(Viewsheet vs, String table, String field) {
      CalcFieldRemovalCleanup cleanup = new CalcFieldRemovalCleanup(vs, table, field);

      for(BindableVSAssembly bind : cleanup.boundAssemblies()) {
         cleanup.clearSortAndRanking(bind);

         if(bind instanceof TableDataVSAssembly || bind instanceof ChartVSAssembly) {
            cleanup.highlights.put(bind.getName(), scanHighlights(bind));
            cleanup.hyperlinks.put(bind.getName(), hyperlinkCount(bind));
         }
      }

      return cleanup;
   }

   /**
    * Call after the removal. The messages name the sorts and rankings {@link #begin} cleared and
    * the highlights and hyperlinks the removal deleted.
    */
   List<String> finish() {
      for(BindableVSAssembly bind : boundAssemblies()) {
         if(!highlights.containsKey(bind.getName())) {
            continue;
         }

         Map<String, HlSlot> after = scanHighlights(bind);
         Set<String> lost = new TreeSet<>();
         Set<String> emptied = new TreeSet<>();
         Set<String> shrunk = new TreeSet<>();

         for(Map.Entry<String, HlSlot> e : highlights.get(bind.getName()).entrySet()) {
            HlSlot was = e.getValue();
            HlSlot now = after.get(e.getKey());

            if(now == null) {
               lost.add(was.name);
            }
            else if(was.size > 0 && now.size == 0) {
               // an emptied condition list matches no cell (HighlightGroup.findGroup skips it),
               // so what is left is a dead highlight; remove it instead of leaving it
               now.group.removeHighlight(now.level, now.name);
               emptied.add(was.name);
            }
            else if(now.size < was.size) {
               shrunk.add(was.name);
            }
         }

         if(!lost.isEmpty()) {
            messages.add("Highlight(s) " + quote(lost) + " on '" + bind.getName() +
                         "' were DELETED because they were attached to '" + field + "'.");
         }

         if(!emptied.isEmpty()) {
            messages.add("Highlight(s) " + quote(emptied) + " on '" + bind.getName() +
                         "' were DELETED because every condition they had was on '" + field + "'.");
         }

         if(!shrunk.isEmpty()) {
            messages.add("Highlight(s) " + quote(shrunk) + " on '" + bind.getName() +
                         "' lost their condition(s) on '" + field + "' and now match more cells.");
         }

         int before = hyperlinks.get(bind.getName());
         int now = hyperlinkCount(bind);

         if(now < before) {
            messages.add((before - now) + " hyperlink(s) on '" + bind.getName() +
                         "' were DELETED because they were attached to '" + field + "'.");
         }
      }

      return messages;
   }

   private List<BindableVSAssembly> boundAssemblies() {
      List<BindableVSAssembly> binds = new ArrayList<>();

      Assembly[] assemblies = vs.getAssemblies();

      for(Assembly assembly : assemblies == null ? new Assembly[0] : assemblies) {
         if(assembly instanceof BindableVSAssembly &&
            !(assembly instanceof InputVSAssembly) &&
            Tool.equals(table, ((BindableVSAssembly) assembly).getTableName()))
         {
            binds.add((BindableVSAssembly) assembly);
         }
      }

      return binds;
   }

   private void clearSortAndRanking(BindableVSAssembly bind) {
      List<DataRef> refs = new ArrayList<>();

      if(bind instanceof CrosstabVSAssembly) {
         VSCrosstabInfo info = ((CrosstabVSAssembly) bind).getVSCrosstabInfo();

         if(info != null) {
            addAll(refs, info.getDesignColHeaders());
            addAll(refs, info.getDesignRowHeaders());
         }
      }
      else if(bind instanceof ChartVSAssembly) {
         VSChartInfo info = ((ChartVSAssembly) bind).getVSChartInfo();

         if(info != null) {
            addAll(refs, info.getFields());
         }
      }
      else if(bind instanceof TableVSAssembly) {
         SortInfo sortInfo = ((TableVSAssemblyInfo) ((TableVSAssembly) bind).getVSAssemblyInfo())
            .getSortInfo();
         SortRef[] sorts = sortInfo == null ? null : sortInfo.getSorts();

         for(int i = sorts == null ? -1 : sorts.length - 1; i >= 0; i--) {
            DataRef sortRef = sorts[i].getDataRef();
            String name = sortRef == null ? sorts[i].getName() : sortRef.getName();

            if(refersTo(name)) {
               sortInfo.removeSort(i);
               messages.add("'" + bind.getName() + "' was sorted by column '" + name +
                            "'; that sort was removed because '" + field + "' was removed.");
            }
         }
      }
      else if(bind instanceof SelectionVSAssembly) {
         String measure = ((SelectionVSAssembly) bind).getMeasureValue();

         if(measure != null && VSUtil.matchRefName(measure, field)) {
            messages.add("Selection '" + bind.getName() + "' lost its measure '" + measure +
                         "' because '" + field + "' was removed.");
         }
      }

      for(DataRef ref : refs) {
         if(!(ref instanceof VSDimensionRef)) {
            continue;
         }

         VSDimensionRef dim = (VSDimensionRef) ref;
         String sortBy = dim.getSortByColValue();
         String rankBy = dim.getRankingColValue();

         if(refersTo(sortBy)) {
            dim.setSortByColValue(null);

            if(OrderInfo.isSortByVal(dim.getOrder())) {
               dim.setOrder(XConstants.SORT_ASC);
            }

            messages.add("'" + bind.getName() + "' dimension '" + dim.getGroupColumnValue() +
                         "' was sorted by '" + sortBy + "'; that sort was reset to ascending " +
                         "because '" + field + "' was removed.");
         }

         if(refersTo(rankBy)) {
            dim.setRankingColValue(null);
            dim.setRankingOptionValue(String.valueOf(XCondition.NONE));

            messages.add("'" + bind.getName() + "' dimension '" + dim.getGroupColumnValue() +
                         "' was ranked by '" + rankBy + "'; that ranking was cleared because '" +
                         field + "' was removed.");
         }
      }
   }

   private boolean refersTo(String col) {
      return col != null && (VSUtil.matchRefName(col, field) || col.endsWith(": " + field));
   }

   private static void addAll(List<DataRef> refs, DataRef[] arr) {
      if(arr != null) {
         refs.addAll(Arrays.asList(arr));
      }
   }

   private static final class HlSlot {
      final HighlightGroup group;
      final String level;
      final String name;
      final int size;

      HlSlot(HighlightGroup group, String level, String name) {
         this.group = group;
         this.level = level;
         this.name = name;
         ConditionList list = group.getHighlight(level, name).getConditionGroup();
         this.size = list == null ? 0 : list.getSize();
      }
   }

   /**
    * Every highlight on the assembly, keyed by where it lives (table path, chart-level group or
    * chart ref) plus level and name, so two highlights sharing a name stay distinct.
    */
   private static Map<String, HlSlot> scanHighlights(BindableVSAssembly bind) {
      Map<String, HlSlot> slots = new LinkedHashMap<>();

      if(bind instanceof TableDataVSAssembly) {
         TableHighlightAttr attr =
            ((TableDataVSAssemblyInfo) ((TableDataVSAssembly) bind).getVSAssemblyInfo())
               .getHighlightAttr();

         if(attr != null) {
            for(Map.Entry<TableDataPath, HighlightGroup> e : attr.getHighlightMap().entrySet()) {
               addSlots(slots, "path:" + e.getKey(), e.getValue());
            }
         }
      }
      else if(bind instanceof ChartVSAssembly) {
         VSChartInfo cinfo = ((ChartVSAssembly) bind).getVSChartInfo();

         if(cinfo != null) {
            addSlots(slots, "chart", cinfo.getHighlightGroup());

            for(VSDataRef ref : cinfo.getFields()) {
               if(ref instanceof HighlightRef) {
                  HighlightGroup[] groups = ((HighlightRef) ref).highlights()
                     .toArray(HighlightGroup[]::new);

                  for(int i = 0; i < groups.length; i++) {
                     addSlots(slots, "ref:" + ref.getFullName() + "#" + i, groups[i]);
                  }
               }
            }
         }
      }

      return slots;
   }

   private static void addSlots(Map<String, HlSlot> slots, String owner, HighlightGroup group) {
      if(group == null || group.getLevels() == null) {
         return;
      }

      for(String level : group.getLevels()) {
         String[] names = group.getNames(level);

         for(String name : names == null ? new String[0] : names) {
            slots.put(owner + "|" + level + "|" + name, new HlSlot(group, level, name));
         }
      }
   }

   /**
    * The number of hyperlinks that live on a binding of the assembly (table cell links, chart
    * measure/dimension links). Parameter-only strips are not counted; the link itself survives.
    */
   private static int hyperlinkCount(BindableVSAssembly bind) {
      if(bind instanceof TableDataVSAssembly) {
         TableHyperlinkAttr attr =
            ((TableDataVSAssemblyInfo) ((TableDataVSAssembly) bind).getVSAssemblyInfo())
               .getHyperlinkAttr();
         return attr == null ? 0 : attr.getHyperlinkMap().size();
      }

      int count = 0;
      VSChartInfo cinfo = ((ChartVSAssembly) bind).getVSChartInfo();

      if(cinfo != null) {
         for(VSDataRef ref : cinfo.getFields()) {
            if(ref instanceof HyperlinkRef && ((HyperlinkRef) ref).getHyperlink() != null) {
               count++;
            }
         }
      }

      return count;
   }

   private static String quote(Collection<String> names) {
      StringJoiner joiner = new StringJoiner(", ");
      names.forEach(n -> joiner.add("'" + n + "'"));
      return joiner.toString();
   }
}

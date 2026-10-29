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

import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
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
   private final Map<String, Set<String>> highlights = new LinkedHashMap<>();
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

         if(bind instanceof TableDataVSAssembly) {
            TableDataVSAssemblyInfo info =
               (TableDataVSAssemblyInfo) ((TableDataVSAssembly) bind).getVSAssemblyInfo();
            cleanup.highlights.put(bind.getName(), highlightNames(info));
            cleanup.hyperlinks.put(bind.getName(), hyperlinkCount(info));
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
         if(!(bind instanceof TableDataVSAssembly) || !highlights.containsKey(bind.getName())) {
            continue;
         }

         TableDataVSAssemblyInfo info =
            (TableDataVSAssemblyInfo) ((TableDataVSAssembly) bind).getVSAssemblyInfo();
         Set<String> lost = new TreeSet<>(highlights.get(bind.getName()));
         lost.removeAll(highlightNames(info));

         if(!lost.isEmpty()) {
            messages.add("Highlight(s) " + quote(lost) + " on '" + bind.getName() +
                         "' were DELETED because they were attached to '" + field + "'.");
         }

         int before = hyperlinks.get(bind.getName());
         int after = hyperlinkCount(info);

         if(after < before) {
            messages.add((before - after) + " hyperlink(s) on '" + bind.getName() +
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

   private static Set<String> highlightNames(TableDataVSAssemblyInfo info) {
      Set<String> names = new HashSet<>();
      TableHighlightAttr attr = info.getHighlightAttr();

      if(attr != null) {
         Enumeration<HighlightGroup> groups = attr.getAllHighlights();

         while(groups.hasMoreElements()) {
            String[] groupNames = groups.nextElement().getNames();

            if(groupNames != null) {
               names.addAll(Arrays.asList(groupNames));
            }
         }
      }

      return names;
   }

   private static int hyperlinkCount(TableDataVSAssemblyInfo info) {
      TableHyperlinkAttr attr = info.getHyperlinkAttr();
      return attr == null ? 0 : attr.getHyperlinkMap().size();
   }

   private static String quote(Collection<String> names) {
      StringJoiner joiner = new StringJoiner(", ");
      names.forEach(n -> joiner.add("'" + n + "'"));
      return joiner.toString();
   }
}

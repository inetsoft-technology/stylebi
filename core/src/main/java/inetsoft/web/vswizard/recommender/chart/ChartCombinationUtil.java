/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.recommender.object.VSChartScoreComparator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Utility methods for chart combination.
 *
 * 1. get all numbers combination of x/y/inside.
 * 2. get all the combination of ref index.
 * 3. combination the two will get all the combination.

 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class ChartCombinationUtil {
   /**
    * Method to filter chart combination.
    */
   public static List<ChartInfo> getChartInfos(AssetEntry[] entries, VSChartInfo temp,
                                               ColumnSelection geoCols, boolean autoOrder)
   {
      ChartRef[] groups = temp.getXFields();
      ChartRef[] aggs = temp.getYFields();
      int n = groups.length + aggs.length;

      // sanity check. the thread should never reach here (see ChartRecommenderUtil.isTableOnly).
      // if the n is large, it could cause OOM. (42284)
      if(n > 10) {
         LOG.error("Number of columns exceeds chart limit: " + n + " x: " +
                      Arrays.toString(groups) + " y: " + Arrays.toString(aggs));
         return new ArrayList<>();
      }

      List<List<ChartRef>> hgroup = getHierarchy(entries, temp);
      List<ChartTypeFilter> filters = createFilters(entries, temp, hgroup, geoCols, autoOrder);
      List<ChartInfo> infos = getChartInfos(n, filters);

      return infos;
   }

   public static List<List<ChartRef>> getHierarchy(AssetEntry[] entries, VSChartInfo temp) {
      List<List<AssetEntry>>  hierarchy = WizardRecommenderUtil.getSortedHierarchyLists(entries);
      return ChartRecommenderUtil.getHierarchyGroups(getAllRefs(temp), hierarchy);
   }

   public static List<ChartRef> getAllRefs(VSChartInfo temp) {
      return Arrays.asList(temp.getBindingRefs(false));
   }

   private static List<ChartTypeFilter> createFilters(AssetEntry[] entries, VSChartInfo temp,
                                                      List<List<ChartRef>> hgroup,
                                                      ColumnSelection geoCols, boolean autoOrder)
   {
      List<ChartTypeFilter> filters = new ArrayList<>();
      // These styles will be primary chart.
      filters.add(new MapChartFilter(entries, temp, geoCols, hgroup, autoOrder));
      filters.add(new ContourMapChartFilter(entries, temp, geoCols, hgroup, autoOrder));
      filters.add(new LineChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetLineChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new AreaChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetAreaChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new StepChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetStepChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new StepAreaChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetStepAreaChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new JumpChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetJumpChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new BarChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetBarChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetHBarChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new PointChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetPointChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new ScatterChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new ContourScatterChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetScatterChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new RadarChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetRadarChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new WordCloudFilter(entries, temp, hgroup, autoOrder));
      // These styles will be secondary chart.
      filters.add(new Y2ChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new PieChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetPieChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new DonutChartFilter(entries, temp, hgroup));
      filters.add(new HistogramChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new HeatMapFilter(entries, temp, hgroup, autoOrder));
      filters.add(new DotPlotFilter(entries, temp, hgroup, autoOrder));
      filters.add(new TreemapChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new CirclePackingChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new SunburstChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new IcicleChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new ScatterMatrixFilter(entries, temp, hgroup, autoOrder));
      filters.add(new MekkoChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new WaterfallChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new ParetoChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new BoxChartFilter(entries, temp, hgroup, autoOrder));
      filters.add(new FacetBoxChartFilter(entries, temp, hgroup, autoOrder));

      return filters;
   }

   /**
    * Method to get all the combination for chart.
    * The i j inside indicate the ref counts for x y inside. For example, if there are 3 refs,
    * it will return strings such as: 003, 012, 021....
    */
   private static List<ChartInfo> getChartInfos(int n, List<ChartTypeFilter> filters) {
      getChartCombination(n, filters);

      List<ChartInfo> allInfos = new ArrayList<>();
      Map<Integer, Integer> scores = new HashMap<>();

      // Filter infos in every styles at first, then score for the filtered infos.
      filters.forEach(f -> {
         List<ChartInfo> charts = f.filter();
         scores.putAll(f.getScores());
         charts = charts.stream()
            .filter(chartInfo -> GraphTypeUtil.checkChartStylePermission(chartInfo.getChartType()))
            .collect(Collectors.toList());
         allInfos.addAll(charts);
      });

      Collections.sort(allInfos, new VSChartScoreComparator(scores));

      return allInfos;
   }

   private static void getChartCombination(int n, List<ChartTypeFilter> filters) {
      IntList indexes = new IntArrayList();

      for(int i = 0; i < n; i++) {
         indexes.add(i);
      }

      // xcount: i   ycount: j   inside count: n - i - j
      for(int i = 0; i <= n; i++) {
         for(int j = 0; j <= n - i; j++) {
            createChartCombination(i, j, indexes, filters);
         }
      }
   }

   /**
    * The xcount/ycount/icount saves the ref counts in x/y/inside.
    * The index saves the order of all refs.
    * Combination them will get all combination of chart.
    *
    * Such as there are 4 refs:
    * xcount/ycount/icont will be 1:1:2 1:2:1 ....
    */
   private static void createChartCombination(int xCount, int yCount, IntList list,
                                              List<ChartTypeFilter> filters)
   {
      List<IntList> xCombinations = getCombinationResult(xCount, list);

      for(IntList x : xCombinations) {
         IntList list0 = new IntArrayList(list);
         list0.removeAll(x);
         List<IntList> yCombinations = getCombinationResult(yCount, list0);

         for(IntList y : yCombinations) {
            IntList inside = new IntArrayList(list0);
            inside.removeAll(y);
            ChartRefCombination chartRefCombination = new ChartRefCombination(x, y, inside);

            filterCombination(filters, chartRefCombination);
         }
      }
   }

   private static List<IntList> getCombinationResult(final int num, IntList list) {
      List<IntList> result = new ArrayList<>();

      if(num == 0) {
         result.add(new IntArrayList(0));
         return result;
      }
      else if(num == 1) {
         list.forEach((IntConsumer) item -> {
            IntList single = new IntArrayList(1);
            single.add(item);
            result.add(single);
         });

         return result;
      }
      else if(num >= list.size()) {
         result.add(new IntArrayList(list));
         return result;
      }

      for(int i = 0; i < list.size() - num + 1; i++) {
         IntList newList = list.subList(i + 1, list.size());
         List<IntList> subCom = getCombinationResult(num - 1, newList);
         int item = list.getInt(i);

         subCom.forEach(com -> {
            com.add(0, item);
            result.add(com);
         });
      }

      return result;
   }

   private static void filterCombination(List<ChartTypeFilter> filters,
                                         ChartRefCombination chartRefCombination)
   {
      for(int i = 0; i < filters.size(); i++) {
         ChartTypeFilter filter = filters.get(i);

         if(!filter.isValid(chartRefCombination)) {
            continue;
         }

         ChartInfo info = filter.createChartInfo(chartRefCombination);

         if(info == null) {
            continue;
         }

         filter.addChartInfo(info);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(ChartCombinationUtil.class);
}

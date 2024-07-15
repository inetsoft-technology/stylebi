/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.util.data.CommonKVModel;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticSizeFrameWrapper;
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;
import inetsoft.web.vswizard.recommender.object.VSChartScoreComparator;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

public class ChartTypeFilter {
   public ChartTypeFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                          boolean autoOrder)
   {
      this(entries, temp, autoOrder);
      this.hierarchyGroups = hgroup;
   }

   public ChartTypeFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup) {
      this(entries, temp, hgroup, true);
   }

   /** For auto-order:
    *  1. for treemap and its children type, no need to fix auto-order, for its order will
    *  according to hierarchy(complete match)
    *  2. for others chart, x/y will also order by cardinality from smaller to large.
    *
    *  3. auto order affect for the two cases:
    *   (1) if x/y not hierarchy, change cols of x/y and inside.
    *   (2) change cols in inside.
    */
   public ChartTypeFilter(AssetEntry[] entries, VSChartInfo temp, boolean autoOrder) {
      this.entries = entries;
      this.temp = temp;
      this.autoOrder = autoOrder;
      scores = new HashMap<>();
      infos = new ArrayList<>();

      initValues();
   }

   private void initValues() {
      this.dateDimCount = getDateCount(temp);
   }

   public boolean isValid(ChartRefCombination comb) {
      return true;
   }

   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      return new VSChartInfo();
   }

   public void addChartInfo(ChartInfo info) {
      infos.add(info);
   }

   public List<ChartInfo> filter() {
      if(infos.isEmpty()) {
         return infos;
      }

      VSChartScoreComparator scoreComp = new VSChartScoreComparator(scores);
      Collections.sort(infos, scoreComp);

      return infos.stream().limit(getStyleCount()).collect(Collectors.toList());
   }

   /**
    * Get the scores from chart info (hash) to it's score.
    */
   public Map<Integer, Integer> getScores() {
      return scores;
   }

   // To be override.
   protected int getScore(ChartInfo chart) {
      return 1;
   }

   protected int getStyleCount() {
      return 1;
   }

   protected int getDimCount() {
      return this.temp.getXFields() == null ? 0 : this.temp.getXFields().length;
   }

   protected int getMeaCount() {
      return this.temp.getYFields() == null ? 0 : this.temp.getYFields().length;
   }

   protected ChartRef getDim(int i) {
      return this.temp.getXFields() == null ? null : this.temp.getXFields()[i];
   }

   protected boolean hasXDimension(ChartRefCombination comb) {
      if(comb.xDimension != null) {
         return comb.xDimension;
      }

      return comb.xDimension = hasDimension(comb.getX());
   }

   protected boolean hasYDimension(ChartRefCombination comb) {
      if(comb.yDimension != null) {
         return comb.yDimension;
      }

      return comb.yDimension = hasDimension(comb.getY());
   }

   protected boolean hasInsideDimension(ChartRefCombination comb) {
      if(comb.insideDimension != null) {
         return comb.insideDimension;
      }

      return comb.insideDimension = hasDimension(comb.getInside());
   }

   private boolean hasDimension(IntList list) {
      return list.stream().anyMatch(this::isDimension);
   }

   protected boolean hasXMeasure(ChartRefCombination comb) {
      if(comb.xMeasure != null) {
         return comb.xMeasure;
      }

      return comb.xMeasure = hasMeasure(comb.getX());
   }

   protected boolean hasYMeasure(ChartRefCombination comb) {
      if(comb.yMeasure != null) {
         return comb.yMeasure;
      }

      return comb.yMeasure = hasMeasure(comb.getY());
   }

   protected boolean hasInsideMeasure(ChartRefCombination comb) {
      if(comb.insideMeasure != null) {
         return comb.insideMeasure;
      }

      return comb.insideMeasure = hasMeasure(comb.getInside());
   }

   private boolean hasMeasure(IntList list) {
      return list.stream().anyMatch(this::isMeasure);
   }

   protected int getMeasureCount(IntList inside) {
      return (int) inside.stream().filter(this::isMeasure).count();
   }

   protected int getDimensionCount(IntList inside) {
      return inside.size() - getMeasureCount(inside);
   }

   protected boolean hasStringDimension(IntList list) {
      return list.stream().anyMatch(this::isStringDimension);
   }

   protected boolean isStringDimension(int index) {
      if(isMeasure(index)) {
         return false;
      }

      VSChartDimensionRef ref = (VSChartDimensionRef) getDim(index);
      return XSchema.STRING.equals(ref.getDataType());
   }

   protected List<ChartRef> getAllRefs(boolean clone) {
      // clone to avoid change made in one filter affecting others
      return ChartCombinationUtil.getAllRefs(temp)
         .stream()
         .map(r -> {
            ChartRef ref = clone ? (ChartRef) r.clone() : r;

            // clear any setting created from wizard so they can be auto-created as part
            // of new recommendation
            if(ref instanceof VSChartAggregateRef && clone) {
               ((VSChartAggregateRef) ref).setSizeFrameWrapper(new StaticSizeFrameWrapper());
            }

            if(ref instanceof HyperlinkRef) {
               ((HyperlinkRef) ref).setHyperlink(null);
            }

            return ref;
         })
         .collect(Collectors.toList());
   }

   protected void addXFields(VSChartInfo info, IntList x, List<ChartRef> refs) {
      if(autoOrder) {
         getSortRefs(x, refs).forEach(ref -> addXYField(info, ref, true));
      }
      else {
         getRefs(x, refs).forEach(ref -> addXYField(info, ref, true));
      }
   }

   protected void addYFields(VSChartInfo info, IntList y, List<ChartRef> refs) {
      if(autoOrder) {
         getSortRefs(y, refs).forEach(ref -> addXYField(info, ref, false));
      }
      else {
         getRefs(y, refs).forEach(ref -> addXYField(info, ref, false));
      }
   }

   // For x/y/inside, we will put the dimension always by the order. So sort by default.
   // Sort refs by this:
   // dimension before aggregate
   // dimension sort by cardinality
   protected List<ChartRef> getSortRefs(IntList indexs, List<ChartRef> refs) {
      List<ChartRef> srefs = new ArrayList<>();
      Map<String, Integer> cands = new HashMap<>();

      indexs.forEach((IntConsumer) index -> {
         ChartRef ref = refs.get(index);
         srefs.add(ref);

         if(ref instanceof VSDimensionRef) {
            cands.put(ref.getFullName(), getCardinality(ref));
         }
      });

      List<List<ChartRef>> hierarchyRefs = findHierarchy(srefs);
      ChartRefComparator scoreComp = new ChartRefComparator(cands);
      removeHierarchy(srefs, hierarchyRefs);
      Collections.sort(srefs, scoreComp);
      addHierarchy(srefs, hierarchyRefs);

      return srefs;
   }

   // Find hierarchy in currrent indexs.
   private List<List<ChartRef>> findHierarchy(List<ChartRef> srefs) {
      List<List<ChartRef>> lists = new ArrayList<>();

      hierarchyGroups.forEach(hierarchy -> {
         List<ChartRef> list = new ArrayList<>();

         hierarchy.forEach(ref -> {
            srefs.forEach(sref -> {
               if(sref.getName().equals(ref.getName())) {
                  list.add(sref);
               }
            });
         });

         if(list.size() > 1) {
            lists.add(list);
         }
      });

      return lists;
   }

   // Using only top field in current hierarchy to sorting.
   private void removeHierarchy(List<ChartRef> srefs, List<List<ChartRef>> hierarchys) {
      hierarchys.forEach(hierarchy -> {
         for(int i = 1; i < hierarchy.size(); i++) {
            srefs.remove(hierarchy.get(i));
         }
      });
   }

   // Find top one in list and insert others in hierarchy.
   private void addHierarchy(List<ChartRef> srefs, List<List<ChartRef>> hierarchys) {
      hierarchys.forEach(hierarchy -> {
         String name = hierarchy.get(0).getName();
         int idx = -1;

         for(int i = 0; i < srefs.size(); i++) {
            if(name.equals(srefs.get(i).getName())) {
               idx = i;
            }
         }

         for(int j = 1; j < hierarchy.size(); j++) {
            srefs.add(idx + j, hierarchy.get(j));
         }
      });
   }

   protected List<ChartRef> getRefs(IntList indexs, List<ChartRef> arefs) {
      List<ChartRef> refs = new ArrayList<>();

      indexs.forEach((IntConsumer) index -> {
         refs.add(arefs.get(index));
      });

      return refs;
   }

   protected void addXYField(VSChartInfo info, ChartRef ref, boolean x) {
      if(x) {
         info.addXField(ref);
      }
      else {
         info.addYField(ref);
      }
   }

   protected void addInsideField(VSChartInfo info, ChartRefCombination comb, List<ChartRef> refs) {
      if(autoOrder) {
         getSortRefs(comb.getInside(), refs).forEach(ref -> putInside(info, ref));
      }
      else {
         getRefs(comb.getInside(), refs).forEach(ref -> putInside(info, ref));
      }

      GraphUtil.fixVisualFrames(info);
   }

   // Filter info. Can be override to filter some infos in own type.
   protected VSChartInfo getClassyInfo(VSChartInfo info) {
      int score = getScore(info);

      if(score < 0) {
         return null;
      }

      scores.put(info.hashCode(), score);
      return info;
   }

   // To be override.
   protected void putInside(VSChartInfo info, ChartRef ref) {
   }

   protected VSAestheticRef createAestheticRef(ChartRef ref0) {
      VSAestheticRef aref = new VSAestheticRef();
      ChartRef ref = (ChartRef) ref0.clone();
      aref.setDataRef(ref);

      return aref;
   }

   protected VSAestheticRef getAestheticRef(int idx) {
      List<ChartRef> refs = getAllRefs(true);

      if(idx < refs.size()) {
         return createAestheticRef(refs.get(idx));
      }

      return null;
   }

   public int getCardinality(ChartRef ref) {
      return ChartRecommenderUtil.getCardinality(ref, entries);
   }

   protected boolean isDateDimension(int index) {
      if(isMeasure(index)) {
         return false;
      }

      return isDateRef(getDim(index));
   }

   protected boolean isDateRef(ChartRef ref) {
      if(ref == null || !(ref instanceof VSChartDimensionRef)) {
         return false;
      }

      VSChartDimensionRef dim = (VSChartDimensionRef) ref;
      return XSchema.isDateType(dim.getDataType());
   }

   protected boolean isMatchHierarchy(ChartRef[] refs, List<ChartRef> hierarchy) {
      return ChartRecommenderUtil.isMatchHierarchy(refs, hierarchy);
   }

   protected boolean hasXDate(ChartRefCombination comb) {
      if(comb.xDate != null) {
         return comb.xDate;
      }

      return comb.xDate = hasDate(comb.getX());
   }

   protected boolean hasYDate(ChartRefCombination comb) {
      if(comb.yDate != null) {
         return comb.yDate;
      }

      return comb.yDate = hasDate(comb.getY());
   }

   protected boolean hasInsideDate(ChartRefCombination comb) {
      if(comb.insideDate != null) {
         return comb.insideDate;
      }

      return comb.insideDate = hasDate(comb.getInside());
   }

   private boolean hasDate(IntList list) {
      return list.stream().anyMatch(this::isDateDimension);
   }

   protected boolean allDateDimension(IntList list) {
      return list.stream().allMatch(this::isDateDimension);
   }

   protected int getDateCount(VSChartInfo temp) {
      return (int) Arrays.stream(temp.getXFields()).filter(this::isDateRef).count();
   }

   // For refs will put by this: all dims + all aggs. So index < getDimCount will be dimensions.
   // and index >= getDimCount will be measures.
   protected boolean isDimension(int index) {
      return index < getDimCount();
   }

   protected boolean isMeasure(int index) {
      return index >= getDimCount();
   }

   // If auto order, we will put col with less cardinality to color, but is not auto order,
   // ignore it.
   protected int getAestheticScore(VSChartInfo info) {
      if(!autoOrder) {
         return 0;
      }

      int score = 0;
      int maxShapes = 8;
      int maxSize = 10;

      if(GraphTypes.isLine(info.getChartType())) {
         maxShapes = 5;
      }
      else if(GraphTypes.isPoint(info.getChartType())) {
         maxShapes = 16;

         // word cloud
         if(info.getTextField() != null) {
            maxSize = 200;
         }
      }

      AestheticRef[] arefs = { info.getColorField(), info.getShapeField(), info.getSizeField() };
      int[] maxN = { 40, maxShapes, maxSize };

      for(int i = 0; i < arefs.length; i++) {
         AestheticRef aref = arefs[i];

         if(aref != null) {
            ChartRef ref = (ChartRef) aref.getDataRef();

            if(ref instanceof VSChartDimensionRef) {
               int n = getCardinality(ref);
               double max = autoOrder ? maxN[i] : (maxN[i] * 1.5);

               if(n > max) {
                  return -1000;
               }
               else {
                  score -= n * 4 / max;
               }
            }
         }
      }

      return score;
   }

   // clear formula from aggregates on x/y
   protected List<CommonKVModel<String, String>> clearFormula(VSChartInfo info) {
      List<CommonKVModel<String, String>> clearedFormula = new ArrayList<>();

      Arrays.stream(info.getXFields())
         .filter(f -> f instanceof VSChartAggregateRef)
         .map(f -> (VSChartAggregateRef) f)
         .forEach(f -> clearFormula(f, clearedFormula));
      Arrays.stream(info.getYFields())
         .filter(f -> f instanceof VSChartAggregateRef)
         .map(f -> (VSChartAggregateRef) f)
         .forEach(f -> clearFormula(f, clearedFormula));

      return clearedFormula;
   }

   private void clearFormula(VSChartAggregateRef ref,
                             List<CommonKVModel<String, String>> clearedFormula)
   {
      String fullName = ref.getFullName();
      clearFormula(ref);
      String displayFormula = ref.getFormulaValue();

      clearedFormula.add(new CommonKVModel<>(fullName, displayFormula));
   }

   private void clearFormula(VSChartAggregateRef ref) {
      if(ref != null) {
         ref.setFormulaValue(AggregateFormula.NONE.getFormulaName());
         ref.setDiscrete(false);
      }
   }

   protected boolean hasAggCalc(VSChartInfo info) {
      List<ChartRef> refs = new ArrayList();
      refs.addAll(Arrays.asList(info.getXFields()));
      refs.addAll(Arrays.asList(info.getYFields()));

      return refs.stream().anyMatch(ref -> ChartRecommenderUtil.isAggCalc(ref, entries));
   }

   // base score for primary chart types, e.g. bar/line/point
   protected static final int PRIMARY_SCORE = 20;
   // base score for chart type with a narrow focus and strict matching criteria, e.g. sunburst
   protected static final int SPECIAL_PURPOSE_SCORE = 15;
   // base score for secondary chart type, e.g. pie
   protected static final int SECOND_SCORE = 10;
   // base score for misc chart type, e.g. waterfall
   protected static final int OTHER_SCORE = 5;

   protected AssetEntry[] entries;
   protected VSChartInfo temp;
   protected List<List<ChartRef>> hierarchyGroups;
   protected Map<Integer, Integer> scores;
   protected List<ChartInfo> infos;
   protected boolean autoOrder = true;
   protected int dateDimCount = 0;
}

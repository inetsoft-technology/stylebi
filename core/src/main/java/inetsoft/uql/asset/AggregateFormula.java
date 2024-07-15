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
package inetsoft.uql.asset;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import inetsoft.util.OrderedSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.io.Serializable;
import java.util.*;

/**
 * Aggregate formula represents an aggregate formula in SQL.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AggregateFormula implements Serializable, Cloneable {
   /**
    * None formula.
    */
   public static final AggregateFormula NONE = new None();
   /**
    * AVG formula.
    */
   public static final AggregateFormula AVG = new Avg();
   /**
    * Count all formula.
    */
   public static final AggregateFormula COUNT_ALL = new CountAll();
   /**
    * Count distinct formula.
    */
   public static final AggregateFormula COUNT_DISTINCT = new CountDistinct();
   /**
    * Max formula.
    */
   public static final AggregateFormula MAX = new Max();
   /**
    * Min formula.
    */
   public static final AggregateFormula MIN = new Min();
   /**
    * Sum formula.
    */
   public static final AggregateFormula SUM = new Sum();
   /**
    * Sum formula.
    */
   public static final AggregateFormula FIRST = new First();
   /**
    * Sum formula.
    */
   public static final AggregateFormula LAST = new Last();
   /**
    * Median formula.
    */
   public static final AggregateFormula MEDIAN = new Median();
   /**
    * Mode formula.
    */
   public static final AggregateFormula MODE = new Mode();
   /**
    * Correlation formula.
    */
   public static final AggregateFormula CORRELATION = new Correlation();
   /**
    * Covariance formula.
    */
   public static final AggregateFormula COVARIANCE = new Covariance();
   /**
    * Variance formula.
    */
   public static final AggregateFormula VARIANCE = new Variance();
   /**
    * Standard deviation formula.
    */
   public static final AggregateFormula STANDARD_DEVIATION =
      new StandardDeviation();
   /**
    * Population Variance formula.
    */
   public static final AggregateFormula POPULATION_VARIANCE = new PopulationVariance();
   /**
    * Population standard deviation formula.
    */
   public static final AggregateFormula POPULATION_STANDARD_DEVIATION =
      new PopulationStandardDeviation();
   /**
    * Weighted average formula.
    */
   public static final AggregateFormula WEIGHTED_AVG = new WeightedAvg();
   /**
    * Product formula.
    */
   public static final AggregateFormula PRODUCT = new Product();
   /**
    * Concat formula.
    */
   public static final AggregateFormula CONCAT = new Concat();
   /**
    * NthLargest formula.
    */
   public static final AggregateFormula NTH_LARGEST = new NthLargest();
   /**
    * NthSmallest formula.
    */
   public static final AggregateFormula NTH_SMALLEST = new NthSmallest();
   /**
    * NthMostFrequent formula.
    */
   public static final AggregateFormula NTH_MOST_FREQUENT = new NthMostFrequent();
   /**
    * PthPercentile formula.
    */
   public static final AggregateFormula PTH_PERCENTILE = new PthPercentile();
   /**
    * Sum of square.
    */
   public static final AggregateFormula SUMSQ = new SumSQ();
   /**
    * Weighted sum.
    */
   public static final AggregateFormula SUMWT = new SumWT();
   /**
    * Aggregate formula for cube.
    */
   public static final AggregateFormula AGGREGATE = new Aggregate();
   /**
    * Weighted sum.
    */
   private static final AggregateFormula SUM2 = new Sum2();

   /**
    * Get all the available formulas.
    * @return all the available formulas.
    */
   public static AggregateFormula[] getFormulas() {
      if(formulas == null) {
         synchronized(AggregateFormula.class) {
            if(formulas == null) {
               AggregateFormula[] formulas = new AggregateFormula[]{
                  AVG, COUNT_ALL, COUNT_DISTINCT, MAX, MIN, SUM, MEDIAN, SUMSQ, SUMWT,
                  MODE, CORRELATION, COVARIANCE, VARIANCE, STANDARD_DEVIATION,
                  POPULATION_VARIANCE, POPULATION_STANDARD_DEVIATION, WEIGHTED_AVG,
                  AGGREGATE, FIRST, LAST, PRODUCT, CONCAT, NTH_LARGEST, NTH_SMALLEST,
                  NTH_MOST_FREQUENT, PTH_PERCENTILE, SUM2
               };

               // optimization, use map to quickly find formula
               for(int i = 0; i < formulas.length; i++) {
                  fmap.put(formulas[i].getName(), formulas[i]);
                  fmap.put(formulas[i].getFormulaName(), formulas[i]);
                  fmap.put(formulas[i].getName().toLowerCase(), formulas[i]);
                  fmap.put(formulas[i].getFormulaName().toLowerCase(), formulas[i]);
                  fmap.put(formulas[i].getLabel().toLowerCase(), formulas[i]);
               }

               AggregateFormula.formulas = formulas;
            }
         }
      }

      return formulas;
   }

   /**
    * Get all the available identifiers.
    * @param none <tt>true</tt> to include none, <tt>false</tt> otherwise.
    */
   public static String[] getIdentifiers(boolean none) {
      AggregateFormula[] forms = getFormulas();
      List<String> ids = new ArrayList<>();

      if(AggregateFormula.ids == null) {
         for(int i = 0; i < forms.length; i++) {
            ids.add(getIdentifier(forms[i]));
            ids.add(forms[i].getName());
            ids.add(forms[i].getLabel());
         }

         AggregateFormula.ids = ids;
      }
      else {
         ids.addAll(AggregateFormula.ids);
      }

      if(none) {
         ids.add(0, XConstants.NONE_FORMULA);
      }

      return ids.toArray(new String[ids.size()]);
   }

   /**
    * Get the identifier.
    * @param formula the specified aggregate formula.
    * @return the identifier of the aggregate formula.
    */
   public static String getIdentifier(AggregateFormula formula) {
      return formula == null ? null : formula.getFormulaName();
   }

   /**
    * Get the aggregate formula.
    * @param name the specified formula name or identifier.
    * @return the aggregate formula of the name, <tt>null</tt>
    * if not found.
    */
   public static AggregateFormula getFormula(String name) {
      if(name == null) {
         return null;
      }

      AggregateFormula formula = fmap.get(name);

      // optimization, should find formula here in most cases
      if(formula != null) {
         return formula;
      }

      String name2 = name.toLowerCase();

      if(name2.equals("null") || name2.equals("none")) {
         fmap.put(name, NONE);
         return NONE;
      }

      AggregateFormula[] formulas = getFormulas(); // load fmap

      if(name2.indexOf("<") > 0) {
         name2 = name2.substring(0, name2.indexOf("<"));
      }

      if(name2.endsWith(")")) {
         int idx = name2.indexOf("(");
         name2 = name2.substring(0, idx);
      }

      return fmap.get(name2);
   }

   /**
    * Get the default formula for the data ref.
    * @param type data type.
    */
   public static AggregateFormula getDefaultFormula(String type) {
      if(AssetUtil.isNumberType(type)) {
         return SUM;
      }

      return COUNT_ALL;
   }

   /**
    * Get the display name.
    * @return the name of the aggregate formula.
    */
   public abstract String getName();

   /**
    * Get the SQL expression of the formula.
    * @param column the specified column used in the expression.
    * @param col2 the secondary column for formulas that perform calculation
    * on two columns, such as correlation and weighted average. This parameter
    * is ignored for formulas that don't require a secondary column.
    * @param helper the helper for the target database.
    * @return the expression of the formula.
    */
   public abstract String getExpression(String column, String col2, AggregateHelper helper);

   /**
    * Get the SQL expression using sub-aggregates. The sub-aggregates should
    * be columns that's on the same table. The sub-aggregate column can be
    * looked up using the submap from the UID to the aggregate ref.
    * This method needs to aggregate the sub-aggregates.
    * @param column the specified column used in the expression.
    * @param col2 the secondary column for formulas that perform calculation
    * on two columns, such as correlation and weighted average. This parameter
    * is ignored for formulas that don't require a secondary column.
    * @param helper the helper for the target database.
    * @return the expression of the formula.
    */
   public abstract String getExpressionSub(String column, String col2, AggregateHelper helper);

   /**
    * Get a formula that can be used to create a formula calculation object.
    * This is an internal method used during runtime for post processing.
    */
   public abstract String getFormulaName();

   /**
    * Get formula name used for mdx.
    */
   public String getCubeFormulaName() {
      return null;
   }

   /**
    * Get the expression of the formula used for mdx.
    */
   public String getCubeExpression(String set, String measure) {
      if(set == null) {
         return measure;
      }

      if(getCubeFormulaName() != null) {
         StringBuilder buffer = new StringBuilder();
         buffer.append(getCubeFormulaName());
         buffer.append("(");
         buffer.append(set);
         buffer.append(", ");
         buffer.append(measure);
         buffer.append(")");

         return buffer.toString();
      }

      return "";
   }

   /**
    * Check if this formula can be calculated from the result of sub-groups,
    * or the sub aggregate of the sub-groups.
    */
   public boolean isCombinable() {
      return true;
   }

   /**
    * Set whether the calculation should be done by composing results from
    * sub-aggregates.
    */
   public void setComposite(boolean composite) {
      this.composite = composite;
   }

   /**
    * Check if this is a composite formula.
    */
   public boolean isComposite() {
      return composite;
   }

   /**
    * Get an unique id for the formula that can be used to identify an aggregate
    * column.
    */
   public String getUID(String column, String col2) {
      return getUID(column, col2, getFormulaName());
   }

   /**
    * Get the uid name of a data ref.
    * @param ref the specified data ref.
    */
   protected String getUIDName(DataRef ref) {
      if(ref instanceof ColumnRef) {
         ColumnRef column = (ColumnRef) ref;
         DataRef iref = column.getDataRef();

         if(iref instanceof AliasDataRef) {
            ref = ((AliasDataRef) iref).getDataRef();
         }
      }

      return ref == null ? null : ref.getName();
   }

   /**
    * Get an unique id for the formula that can be used to identify an aggregate
    * column.
    */
   public String getUID(String column, String col2, String formula) {
      column = column.replace('.', '_');
      String id = formula + "_" + column;

      if(col2 != null) {
         col2 = col2.replace('.', '_');
         id += '_' + col2;
      }

      // UID should not be longer than 30, which is not supported by oracle,
      // sybase 28
      if(id.length() > 28) {
         id = id.substring(0, 5) + id.hashCode();
         id = id.replace('-', '_');
      }

      return id;
   }

   /**
    * Get the aggregate columns that could be used to calculate this formula.
    * @return an empty collection if this formula can not be composed from
    * other formulas. Otherwise returns a collection of the aggregate columns
    * that can be used to calculate this function.
    */
   Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
      return new HashSet<>();
   }

   /**
    * Get the type of the formula result. Returns null if the result is
    * the same as the input data.
    * @return a type defined in XSchema.
    */
   public String getDataType() {
      return null;
   }

   /**
    * Check if the formula requires two columns.
    */
   public boolean isTwoColumns() {
      return false;
   }

   /**
    * Check if the formula requires N or P.
    */
   public boolean hasN() {
      return false;
   }

   /**
    * Check if the formula operates on numeric values.
    */
   public boolean isNumeric() {
      return true;
   }

   /**
    * Get the label of the agggregate formula.
    */
   public String getLabel() {
      return Catalog.getCatalog().getString(getFormulaName());
   }

   /**
    * Get string representation.
    */
   public String toString() {
      return getLabel();
   }

   /**
    * Get the hash code.
    * @return the hash code of the aggregate formula.
    */
   public int hashCode() {
      return getName().hashCode();
   }

   /**
    * Check if equals another object.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof AggregateFormula)) {
         return false;
      }

      AggregateFormula formula = (AggregateFormula) obj;
      return getName().equals(formula.getName());
   }

   /**
    * Get the parent formula.
    * @return the parent formula if this aggregate supports aggregate on
    * aggregate, <tt>null</tt> otherwise.
    */
   public AggregateFormula getParentFormula() {
      return null;
   }

   /**
    * No-op formula.
    */
   private static class None extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "None";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return column;
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return column;
      }

      /**
       * Get the aggregate columns that could be used to calculate this
       * formula.
       * @return an empty collection if this formula can not be composed from
       * other formulas. Otherwise returns a collection of the aggregate columns
       * that can be used to calculate this function.
       */
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         col = new AliasDataRef(NONE.getUID(getUIDName(col), null), col);
         col = new ColumnRef(col);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return "none";
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * Average formula.
    */
   private static class Avg extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "AVG";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return "AVG(" + column + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return SUM.getExpressionSub(column, col2, helper) +
            "/" + COUNT_ALL.getExpressionSub(column, col2, helper);
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "CompositeAverageFormula" : "Average";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "Avg";
      }

      /**
       * Get the sub aggregate columns if any.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         set.addAll(SUM.getSubAggregates(col, null));
         set.addAll(COUNT_ALL.getSubAggregates(col, null));
         return set;
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return keepType ? null : XSchema.DOUBLE;
      }

      private boolean keepType = "true".equals(SreeEnv.getProperty("formula.avg.keep.type"));
   }

   /**
    * Count all formula.
    */
   private static class CountAll extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "COUNT ALL";
      }

      /**
       * Get the expression of the formula.
       */
      @Override
      public String getCubeExpression(String set, String measure) {
         StringBuilder buffer = new StringBuilder();
         buffer.append("Count");

         if(set == null) {
            buffer.append("({");
            buffer.append(measure);
            buffer.append("})");

            return buffer.toString();
         }

         buffer.append("(crossjoin(");
         buffer.append(set);
         buffer.append(", {");
         buffer.append(measure);
         buffer.append("}), EXCLUDEEMPTY)");

         return buffer.toString();
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return "COUNT(" + column + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "SUM(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      /**
       * Get the aggregate columns that could be used to calculate this
       * formula.
       * @return an empty collection if this formula can not be composed from
       * other formulas. Otherwise returns a collection of the aggregate columns
       * that can be used to calculate this function.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         col = new AliasDataRef(COUNT_ALL.getUID(getUIDName(col), null), col);
         col = new ColumnRef(col);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "Sum" : "Count";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "Count";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }

      /**
       * Get the parent formula.
       */
      @Override
      public AggregateFormula getParentFormula() {
         return SUM;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * Count dintinct formula.
    */
   private static class CountDistinct extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "COUNT DISTINCT";
      }

      /**
       * Check if this formula can be calculated from the result of sub-groups,
       * or the sub aggregate of the sub-groups.
       */
      @Override
      public boolean isCombinable() {
         return false;
      }

      /**
       * Get the expression of the formula.
       */
      @Override
      public String getCubeExpression(String set, String measure) {
         StringBuilder buffer = new StringBuilder();
         buffer.append("Count(Distinct");

         if(set == null) {
            buffer.append("({");
            buffer.append(measure);
            buffer.append("}))");

            return buffer.toString();
         }

         buffer.append("(crossjoin(");
         buffer.append(set);
         buffer.append(", {");
         buffer.append(measure);
         buffer.append("})), EXCLUDEEMPTY)");

         return buffer.toString();
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return "COUNT(DISTINCT " + column + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "SUM(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      /**
       * Get the aggregate columns that could be used to calculate this
       * formula.
       * @return an empty collection if this formula can not be composed from
       * other formulas. Otherwise returns a collection of the aggregate columns
       * that can be used to calculate this function.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         col = new AliasDataRef(COUNT_DISTINCT.getUID(getUIDName(col), null), col);
         col = new ColumnRef(col);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "Sum" : "DistinctCount";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "DistinctCount";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.INTEGER;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * Max formula.
    */
   private static class Max extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "MAX";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return "MAX(" + column + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "MAX(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      /**
       * Get the aggregate columns that could be used to calculate this
       * formula.
       * @return an empty collection if this formula can not be composed from
       * other formulas. Otherwise returns a collection of the aggregate columns
       * that can be used to calculate this function.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         String dtype = col.getDataType();
         col = new AliasDataRef(MAX.getUID(getUIDName(col), null), col);
         col = new ColumnRef(col);
         ((ColumnRef) col).setDataType(dtype);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return "Max";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "Max";
      }

      /**
       * Get the parent formula.
       */
      @Override
      public AggregateFormula getParentFormula() {
         return MAX;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * Min formula.
    */
   private static class Min extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "MIN";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return "MIN(" + column + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "MIN(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      /**
       * Get the aggregate columns that could be used to calculate this
       * formula.
       * @return an empty collection if this formula can not be composed from
       * other formulas. Otherwise returns a collection of the aggregate columns
       * that can be used to calculate this function.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         String dtype = col.getDataType();
         col = new AliasDataRef(MIN.getUID(getUIDName(col), null), col);
         col = new ColumnRef(col);
         ((ColumnRef) col).setDataType(dtype);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return "Min";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "Min";
      }

      /**
       * Get the parent formula.
       */
      @Override
      public AggregateFormula getParentFormula() {
         return MIN;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * Sum formula.
    */
   private static class Sum extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "SUM";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return "SUM(" + column + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "SUM(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      /**
       * Get the aggregate columns that could be used to calculate this
       * formula.
       * @return an empty collection if this formula can not be composed from
       * other formulas. Otherwise returns a collection of the aggregate columns
       * that can be used to calculate this function.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         col = new AliasDataRef(SUM.getUID(getUIDName(col), null), col);
         col = new ColumnRef(col);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return "Sum";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "Sum";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }

      /**
       * Get the parent formula.
       */
      @Override
      public AggregateFormula getParentFormula() {
         return SUM;
      }
   }

   /**
    * Median formula.
    */
   private static class Median extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "MEDIAN";
      }

      /**
       * Check if this formula can be calculated from the result of sub-groups,
       * or the sub aggregate of the sub-groups.
       */
      @Override
      public boolean isCombinable() {
         return false;
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return "MEDIAN(" + column + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "SUM(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      /**
       * Get the aggregate columns that could be used to calculate this
       * formula.
       * @return an empty collection if this formula can not be composed from
       * other formulas. Otherwise returns a collection of the aggregate columns
       * that can be used to calculate this function.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         col = new AliasDataRef(MEDIAN.getUID(getUIDName(col), null), col);
         col = new ColumnRef(col);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "Sum" : "Median";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "Median";
      }

      @Override
      public String getDataType() {
         // median may return average of two values so it should be a double (50268).
         return XSchema.DOUBLE;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * Mode formula.
    */
   private static class Mode extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "Mode";
      }

      /**
       * Check if this formula can be calculated from the result of sub-groups,
       * or the sub aggregate of the sub-groups.
       */
      @Override
      public boolean isCombinable() {
         return false;
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2, AggregateHelper helper) {
         return "Mode(" + column + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "SUM(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      /**
       * Get the aggregate columns that could be used to calculate this
       * formula.
       * @return an empty collection if this formula can not be composed from
       * other formulas. Otherwise returns a collection of the aggregate columns
       * that can be used to calculate this function.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         col = new AliasDataRef(MODE.getUID(getUIDName(col), null), col);
         col = new ColumnRef(col);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "Sum" : "Mode";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return null;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * Correlation formula.
    */
   private static class Correlation extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "CORRELATION";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         if(helper != null) {
            String func = helper.getAggregateExpression("correl", column, col2);

            if(func != null) {
               return func;
            }
         }

         return "(" + COVARIANCE.getExpression(column, col2, helper) +
            ") / ("
            + POPULATION_STANDARD_DEVIATION.getExpression(column, null, helper) +
            " * "
            + POPULATION_STANDARD_DEVIATION.getExpression(col2, null, helper) + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "(" + COVARIANCE.getExpressionSub(column, col2, helper) +
            ") / (" + POPULATION_STANDARD_DEVIATION
            .getExpressionSub(column, null, helper) +
            " * " + POPULATION_STANDARD_DEVIATION
            .getExpressionSub(col2, null, helper) +
            ")";
      }

      /**
       * Get the sub aggregate columns if any. Otherwise, return the aggregate
       * for this formula.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();

         set.addAll(COVARIANCE.getSubAggregates(col, col2));
         set.addAll(POPULATION_STANDARD_DEVIATION.getSubAggregates(col, null));
         set.addAll(POPULATION_STANDARD_DEVIATION.getSubAggregates(col2, null));

         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "CompositeCorrelationFormula" : "Correlation";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }

      /**
       * Check if the formula requires two columns.
       */
      @Override
      public boolean isTwoColumns() {
         return true;
      }
   }

   /**
    * Covariance formula.
    */
   private static class Covariance extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "COVARIANCE";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         if(helper != null) {
            String func = helper.getAggregateExpression("covar", column, col2);

            if(func != null) {
               return func;
            }
         }

         return "(0.0+" + SUMWT.getExpression(column, col2, helper) + ") / "
            + COUNT_ALL.getExpression(column, null, helper)+ " - (0.0+ " +
            SUM.getExpression(column, null, helper) + ") * ((0.0 + " +
            SUM.getExpression(col2, null, helper)
            + ") / ((0.0 + " +
            COUNT_ALL.getExpression(column, null, helper) + ") * " +
            COUNT_ALL.getExpression(column, null, helper) + "))";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         // (SUMWT / N) - (SUMx * (SUMy / (N^2)))
         // (SUMWT / N) - (SUMx * (SUMy / (N^2)))
         return "((0.0+" + SUMWT.getExpressionSub(column, col2, helper) +
         ") /" + COUNT_ALL.getExpressionSub(column, col2, helper) +
         ") - ((0.0 + " + SUM.getExpressionSub(column, col2, helper) +
         ") * ((0.0 + " + SUM.getExpressionSub(col2, null, helper) +
         ") / ((0.0 +" + COUNT_ALL.getExpressionSub(column, col2, helper) +
         ") * " + COUNT_ALL.getExpressionSub(column, col2, helper) +
         ")))";
      }

      /**
       * Get the sub aggregate columns if any. Otherwise, return the aggregate
       * for this formula.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         set.addAll(SUMWT.getSubAggregates(col, col2));
         set.addAll(SUM.getSubAggregates(col, null));
         set.addAll(SUM.getSubAggregates(col2, null));
         set.addAll(COUNT_ALL.getSubAggregates(col, null));
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "CompositeCovarianceFormula" : "Covariance";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }

      /**
       * Check if the formula requires two columns.
       */
      @Override
      public boolean isTwoColumns() {
         return true;
      }
   }

   /**
    * Variance formula.
    */
   private static class Variance extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "VARIANCE";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         if(helper != null) {
            String func = helper.getAggregateExpression("var", column, col2);

            if(func != null) {
               return func;
            }
         }

         return "((0.0+" + SUMSQ.getExpression(column, null, helper) + ") * " +
            COUNT_ALL.getExpression(column, null, helper) + " - ((0.0 + " +
            SUM.getExpression(column, null, helper) + ") * " +
            SUM.getExpression(column, null, helper) + " ))/NULLIF(((0.0 + " +
            COUNT_ALL.getExpression(column, null, helper) + ") * (" +
            COUNT_ALL.getExpression(column, null, helper) + " - 1)), 0)";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper)
      {
         if(helper != null && "db2".equals(helper.getDBType())) {
            return "((0.0+" + SUMSQ.getExpressionSub(column, null, helper) +
               ") * " +
               COUNT_ALL.getExpressionSub(column, null, helper) + " - ((0.0 + "
               + SUM.getExpressionSub(column, col2, helper) + ") * " +
               SUM.getExpressionSub(column, col2, helper) + " ))/((0.0 + " +
               COUNT_ALL.getExpressionSub(column, null, helper) + ") * (" +
               COUNT_ALL.getExpressionSub(column, null, helper) + "))";
         }

         return "((0.0+" + SUMSQ.getExpressionSub(column, null, helper) +
            ") * " +
            COUNT_ALL.getExpressionSub(column, null, helper) + " - ((0.0 + "
            + SUM.getExpressionSub(column, col2, helper) + ") * " +
            SUM.getExpressionSub(column, col2, helper) + " ))/NULLIF(((0.0 + " +
            COUNT_ALL.getExpressionSub(column, null, helper) + ") * (" +
            COUNT_ALL.getExpressionSub(column, null, helper) + " - 1)), 0)";
      }

      /**
       * Get the sub aggregate columns if any. Otherwise, return the aggregate
       * for this formula.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         set.addAll(COUNT_ALL.getSubAggregates(col, col2));
         set.addAll(SUMSQ.getSubAggregates(col, col2));
         set.addAll(SUM.getSubAggregates(col, col2));
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "CompositeVarianceFormula" : "Variance";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "Var";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }
   }

   /**
    * Standard deviation formula.
    */
   private static class StandardDeviation extends Variance {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "STANDARD DEVIATION";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         if(helper != null) {
            String func = helper.getAggregateExpression("stddev", column, col2);

            if(func != null) {
               return func;
            }
         }

         return "SQRT(" + VARIANCE.getExpression(column, col2, helper) + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "SQRT(" +
            VARIANCE.getExpressionSub(column, col2, helper) + ")";
      }

      /**
       * Get the sub aggregate columns if any. Otherwise, return the aggregate
       * for this formula.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         set.addAll(VARIANCE.getSubAggregates(col, col2));
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "CompositeStandardDeviationFormula" :
                                "StandardDeviation";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "Stdev";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }
   }

   /**
    * Population variance formula.
    */
   private static class PopulationVariance extends Variance {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "POPULATION VARIANCE";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         if(helper != null) {
            String func = helper.getAggregateExpression("varp", column, col2);

            if(func != null) {
               return func;
            }
         }

         return "((0.0+" + SUMSQ.getExpression(column, null, helper) + ") * " +
            COUNT_ALL.getExpression(column, null, helper) + " - ((0.0 + " +
            SUM.getExpression(column, null, helper) + ") * " +
            SUM.getExpression(column, null, helper) + " ))/((0.0 + " +
            COUNT_ALL.getExpression(column, null, helper) + ") * " +
            COUNT_ALL.getExpression(column, null, helper) + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "((0.0+" + SUMSQ.getExpressionSub(column, null, helper) +
            ") * " +
            COUNT_ALL.getExpressionSub(column, null, helper) + " - ((0.0 + " +
            SUM.getExpressionSub(column, col2, helper) + ") * " +
            SUM.getExpressionSub(column, col2, helper) + " ))/((0.0 + " +
            COUNT_ALL.getExpressionSub(column, null, helper) + ") * " +
            COUNT_ALL.getExpressionSub(column, null, helper) + ")";
      }

      /**
       * Get the sub aggregate columns if any. Otherwise, return the aggregate
       * for this formula.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         set.addAll(COUNT_ALL.getSubAggregates(col, col2));
         set.addAll(SUMSQ.getSubAggregates(col, col2));
         set.addAll(SUM.getSubAggregates(col, col2));
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "CompositePopulationVarianceFormula" :
                                "PopulationVariance";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "VarP";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }
   }

   /**
    * Population standard deviation formula.
    */
   private static class PopulationStandardDeviation extends PopulationVariance {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "POPULATION STANDARD DEVIATION";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         if(helper != null) {
            String func = helper.getAggregateExpression("stddevp", column, col2);

            if(func != null) {
               return func;
            }
         }

         return "SQRT(" +
            POPULATION_VARIANCE.getExpression(column, col2, helper) +")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "SQRT(" +
            POPULATION_VARIANCE.getExpressionSub(column, col2, helper)
            + ")";
      }

      /**
       * Get the sub aggregate columns if any. Otherwise, return the aggregate
       * for this formula.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         set.addAll(POPULATION_VARIANCE.getSubAggregates(col, col2));
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "CompositePopulationStandardDeviationFormula" :
                                "PopulationStandardDeviation";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "StdevP";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }
   }

   /**
    * Weighted average formula.
    */
   private static class WeightedAvg extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "WEIGHTED AVG";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2, AggregateHelper helper) {
         if(helper != null) {
            String func = helper.getAggregateExpression("wavt", column, col2);

            if(func != null) {
               return func;
            }
         }

         String sumwt = "(0.0+" + SUMWT.getExpression(column, col2, helper) + ")";
         String sum2 = "(0.0 + " + SUM2.getExpression(col2, column, helper) + ")";

         return "CASE WHEN " + sum2 + " = 0 THEN 0.0 ELSE " + sumwt + "/" + sum2 + " END";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2, AggregateHelper helper) {
         return "(0.0+" + SUMWT.getExpressionSub(column, col2, helper) +
            ") / (0.0+" + SUM.getExpressionSub(col2, column, helper) + ")";
      }

      /**
       * Get the sub aggregate columns if any. Otherwise, return the aggregate
       * for this formula.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         set.addAll(SUMWT.getSubAggregates(col, col2));
         set.addAll(SUM2.getSubAggregates(col2, col));
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ?
            "CompositeAverageFormula" : "WeightedAverage";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }

      /**
       * Check if the formula requires two columns.
       */
      @Override
      public boolean isTwoColumns() {
         return true;
      }
   }

   /**
    * Sum of square.
    */
   private static class SumSQ extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "SUMSQ";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return "SUM((0.0+" + addParens(column) + ") * (0.0+" + addParens(column) + "))";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "SUM(" + XUtil.quoteAlias(getUID(column, col2), null) + ")";
      }

      /**
       * Get the aggregate columns that could be used to calculate this
       * formula.
       * @return an empty collection if this formula can not be composed from
       * other formulas. Otherwise returns a collection of the aggregate columns
       * that can be used to calculate this function.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         String name = SUMSQ.getUID(getUIDName(col), getUIDName(col2));
         col = new AliasDataRef(name, col);
         col = new ColumnRef(col);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "Sum" : "SumSQFormula";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }

      @Override
      public AggregateFormula getParentFormula() {
         return SUM;
      }
   }

   /**
    * Weighted sum.
    */
   private static class SumWT extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "SUMWT";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return "SUM(" + addParens(column) + " * " + addParens(col2) + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "SUM(" + XUtil.quoteAlias(getUID(column, col2), null) + ")";
      }

      /**
       * Get the aggregate columns that could be used to calculate this
       * formula.
       * @return an empty collection if this formula can not be composed from
       * other formulas. Otherwise returns a collection of the aggregate columns
       * that can be used to calculate this function.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         String name = SUMWT.getUID(getUIDName(col), getUIDName(col2));
         col = new AliasDataRef(name, col);
         col = new ColumnRef(col);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "Sum" : "SumWTFormula";
      }

      /**
       * Check if the formula requires two columns.
       */
      @Override
      public boolean isTwoColumns() {
         return true;
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }

      @Override
      public AggregateFormula getParentFormula() {
         return SUM;
      }
   }

   /**
    * Sum that ignores a value if the second value is null. Used for weighted average.
    */
   private static class Sum2 extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "SUM2";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return "SUM(" + addParens(column) + ")";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "SUM(" + XUtil.quoteAlias(getUID(column, col2), null) + ")";
      }

      /**
       * Get the aggregate columns that could be used to calculate this
       * formula.
       * @return an empty collection if this formula can not be composed from
       * other formulas. Otherwise returns a collection of the aggregate columns
       * that can be used to calculate this function.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();
         String name = SUM2.getUID(getUIDName(col), getUIDName(col2));
         col = new AliasDataRef(name, col);
         col = new ColumnRef(col);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return isComposite() ? "Sum" : "Sum2Formula";
      }

      /**
       * Check if the formula requires two columns.
       */
      @Override
      public boolean isTwoColumns() {
         return true;
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }

      @Override
      public AggregateFormula getParentFormula() {
         return SUM;
      }
   }

   /**
    * Aggregate formula.
    */
   private static class Aggregate extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "Aggregate";
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return "Aggregate";
      }

      /**
       * Get formula name used for mdx.
       */
      @Override
      public String getCubeFormulaName() {
         return "Aggregate";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return "";
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return "";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }
   }

   /**
    * First formula.
    */
   private static class First extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "FIRST";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2, AggregateHelper helper) {
         return column;
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2, AggregateHelper helper) {
         return column;
      }

      /**
       * Get the sub aggregate columns if any.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();

         String name = FIRST.getUID(getUIDName(col), getUIDName(col2));
         col = new AliasDataRef(name, col);
         col = new ColumnRef(col);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);

         set.addAll(MIN.getSubAggregates(col2, null));
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return "First";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return null;
      }

      /**
       * Check if the formula requires two columns.
       */
      @Override
      public boolean isTwoColumns() {
         return true;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * Last formula.
    */
   private static class Last extends AggregateFormula {
      /**
       * Get the name.
       * @return the name of the aggregate formula.
       */
      @Override
      public String getName() {
         return "LAST";
      }

      /**
       * Get the expression of the formula.
       * @param column the specified column used in the expression.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpression(String column, String col2,
                                  AggregateHelper helper) {
         return column;
      }

      /**
       * Get the SQL expression using sub-aggregates. The sub-aggregates should
       * be columns that's on the same table. The sub-aggregate column can be
       * looked up using the submap from the UID to the aggregate ref.
       * This method aggregate the sub-aggregate.
       * @param column the specified column used in the expression.
       * @param col2 the secondary column for formulas that perform calculation
       * on two columns, such as correlation and weighted average.
       * This parameter is ignored for formulas that don't require a
       * secondary column.
       * @param helper the helper for the target database.
       * @return the expression of the formula.
       */
      @Override
      public String getExpressionSub(String column, String col2,
                                     AggregateHelper helper) {
         return column;
      }

      /**
       * Get the sub aggregate columns if any.
       */
      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         OrderedSet<AggregateRef> set = new OrderedSet<>();

         String name = LAST.getUID(getUIDName(col), getUIDName(col2));
         col = new AliasDataRef(name, col);
         col = new ColumnRef(col);
         AggregateRef ref = new AggregateRef(col, col2, this);
         set.add(ref);

         set.addAll(MAX.getSubAggregates(col2, null));
         return set;
      }

      /**
       * Get a formula that can be used to create a formula calculation object.
       * This is an internal method used during runtime for post processing.
       */
      @Override
      public String getFormulaName() {
         return "Last";
      }

      /**
       * Get the type of the formula result. Returns null if the result is
       * the same as the input data.
       * @return a type defined in XSchema.
       */
      @Override
      public String getDataType() {
         return null;
      }

      /**
       * Check if the formula requires two columns.
       */
      @Override
      public boolean isTwoColumns() {
         return true;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * Product formula.
    */
   private static class Product extends AggregateFormula {
      @Override
      public String getName() {
         return "Product";
      }

      @Override
      public boolean isCombinable() {
         return false;
      }

      @Override
      public String getExpression(String column, String col2, AggregateHelper helper) {
         return "Product(" + column + ")";
      }

      @Override
      public String getExpressionSub(String column, String col2, AggregateHelper helper) {
         // shouldn't be used
         return "SUM(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         return new HashSet<>();
      }

      @Override
      public String getFormulaName() {
         return isComposite() ? "Sum" : "Product";
      }

      @Override
      public String getDataType() {
         return XSchema.DOUBLE;
      }
   }

   /**
    * Concat formula.
    */
   private static class Concat extends AggregateFormula {
      @Override
      public String getName() {
         return "Concat";
      }

      @Override
      public boolean isCombinable() {
         return false;
      }

      @Override
      public String getExpression(String column, String col2, AggregateHelper helper) {
         return "Concat(" + column + ")";
      }

      @Override
      public String getExpressionSub(String column, String col2, AggregateHelper helper) {
         // shouldn't be used
         return "Concat(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         return new HashSet<>();
      }

      @Override
      public String getFormulaName() {
         return "Concat";
      }

      @Override
      public String getDataType() {
         return XSchema.STRING;
      }

      /**
       * Get the parent formula.
       */
      @Override
      public AggregateFormula getParentFormula() {
         return CONCAT;
      }
   }

   /**
    * NthLargest formula.
    */
   private static class NthLargest extends AggregateFormula {
      @Override
      public String getName() {
         return "NthLargest";
      }

      @Override
      public boolean isCombinable() {
         return false;
      }

      @Override
      public String getExpression(String column, String col2, AggregateHelper helper) {
         return "NthLargest(" + column + ")";
      }

      @Override
      public String getExpressionSub(String column, String col2, AggregateHelper helper) {
         // shouldn't be used
         return "SUM(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         return new HashSet<>();
      }

      @Override
      public String getFormulaName() {
         return "NthLargest";
      }

      @Override
      public String getDataType() {
         return null;
      }

      @Override
      public boolean hasN() {
         return true;
      }

      @Override
      public AggregateFormula getParentFormula() {
         return null;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * NthSmallest formula.
    */
   private static class NthSmallest extends AggregateFormula {
      @Override
      public String getName() {
         return "NthSmallest";
      }

      @Override
      public boolean isCombinable() {
         return false;
      }

      @Override
      public String getExpression(String column, String col2, AggregateHelper helper) {
         return "NthSmallest(" + column + ")";
      }

      @Override
      public String getExpressionSub(String column, String col2, AggregateHelper helper) {
         // shouldn't be used
         return "SUM(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         return new HashSet<>();
      }

      @Override
      public String getFormulaName() {
         return "NthSmallest";
      }

      @Override
      public String getDataType() {
         return null;
      }

      @Override
      public boolean hasN() {
         return true;
      }

      @Override
      public AggregateFormula getParentFormula() {
         return null;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * NthMostFrequent formula.
    */
   private static class NthMostFrequent extends AggregateFormula {
      @Override
      public String getName() {
         return "NthMostFrequent";
      }

      @Override
      public boolean isCombinable() {
         return false;
      }

      @Override
      public String getExpression(String column, String col2, AggregateHelper helper) {
         return "NthMostFrequent(" + column + ")";
      }

      @Override
      public String getExpressionSub(String column, String col2, AggregateHelper helper) {
         // shouldn't be used
         return "SUM(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         return new HashSet<>();
      }

      @Override
      public String getFormulaName() {
         return "NthMostFrequent";
      }

      @Override
      public String getDataType() {
         return null;
      }

      @Override
      public boolean hasN() {
         return true;
      }

      @Override
      public AggregateFormula getParentFormula() {
         return null;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   /**
    * PthPercentile formula.
    */
   private static class PthPercentile extends AggregateFormula {
      @Override
      public String getName() {
         return "PthPercentile";
      }

      @Override
      public boolean isCombinable() {
         return false;
      }

      @Override
      public String getExpression(String column, String col2, AggregateHelper helper) {
         return "PthPercentile(" + column + ")";
      }

      @Override
      public String getExpressionSub(String column, String col2, AggregateHelper helper) {
         // shouldn't be used
         return "SUM(" + XUtil.quoteAlias(getUID(column, null), null) + ")";
      }

      @Override
      Collection<AggregateRef> getSubAggregates(DataRef col, DataRef col2) {
         return new HashSet<>();
      }

      @Override
      public String getFormulaName() {
         return "PthPercentile";
      }

      @Override
      public String getDataType() {
         return null;
      }

      @Override
      public boolean hasN() {
         return true;
      }

      @Override
      public boolean isNumeric() {
         return false;
      }
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(CloneNotSupportedException ex) {
         return null;
      }
   }

   // surround with parens for expression
   private static String addParens(String col2) {
      if(col2 != null && !(col2.startsWith("(") && col2.endsWith(")")) &&
         !XUtil.isQualifiedName(col2) && !isField(col2))
      {
         col2 = "(" + col2 + ")";
      }

      return col2;
   }

   // check if it's field['name']
   private static boolean isField(String exp) {
      return exp.startsWith("field['") && exp.endsWith("']") &&
         !exp.substring(7, exp.length() - 2).contains("'");
   }

   private static AggregateFormula[] formulas;
   private static List<String> ids = null;
   private static Map<String, AggregateFormula> fmap = new Object2ObjectOpenHashMap<>();
   private transient boolean composite = false;
}

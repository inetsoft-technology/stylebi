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
package inetsoft.uql.viewsheet;

import inetsoft.graph.data.CombinedDataSetComparator;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.ManualOrderComparer;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.execution.VSCubeTableLens;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.ValueOrderComparer;
import inetsoft.report.filter.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.graph.HyperlinkRef;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;

import java.io.PrintWriter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

/**
 * A VSDimensionRef object represents a dimension reference.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSDimensionRef extends AbstractDataRef implements ContentObject, XDimensionRef {
   /**
    * Constructor.
    */
   public VSDimensionRef() {
      super();

      groupValue = new DynamicValue();
      rankingOptValue = new DynamicValue(
         null, XSchema.INTEGER,
         new int[] {XCondition.NONE, XCondition.TOP_N, XCondition.BOTTOM_N},
         new String[] {"", "top", "bottom"});
      rankingNValue = new DynamicValue();
      rankingNValue.setDataType(XSchema.INTEGER);
      rankingColValue = new DynamicValue();
      sortByColValue = new DynamicValue();
      dlevelValue = new DynamicValue(
         null, XSchema.INTEGER,
         new int[] {DateRangeRef.YEAR_INTERVAL, DateRangeRef.QUARTER_INTERVAL,
                    DateRangeRef.MONTH_INTERVAL, DateRangeRef.WEEK_INTERVAL,
                    DateRangeRef.DAY_INTERVAL, DateRangeRef.HOUR_INTERVAL,
                    DateRangeRef.MINUTE_INTERVAL, DateRangeRef.SECOND_INTERVAL,
                    DateRangeRef.QUARTER_OF_YEAR_PART,
                    DateRangeRef.MONTH_OF_YEAR_PART,
                    DateRangeRef.WEEK_OF_YEAR_PART,
                    DateRangeRef.DAY_OF_MONTH_PART,
                    DateRangeRef.DAY_OF_WEEK_PART,
                    DateRangeRef.HOUR_OF_DAY_PART,
                    DateRangeRef.MINUTE_OF_HOUR_PART,
                    DateRangeRef.SECOND_OF_MINUTE_PART,
                    DateRangeRef.MONTH_OF_FULL_WEEK,
                    DateRangeRef.QUARTER_OF_FULL_WEEK_PART,
                    DateRangeRef.QUARTER_OF_FULL_WEEK,
                    DateRangeRef.YEAR_OF_FULL_WEEK,
                    DateRangeRef.MONTH_OF_FULL_WEEK_PART,
                    DateRangeRef.NONE_INTERVAL},
         new String[] {"year", "quarter", "month", "week", "day", "hour",
                       "minute", "second", "quarter of year", "month of year",
                       "week of year", "day of month", "day of week",
                       "hour of day", "minute of hour", "second of minute", "month of week",
                       "quarter of week", "quarter of week", "year of week", "month of week",
                       "none"});
      subVisibleValue = new DynamicValue(null, XSchema.BOOLEAN);
      othersValue = new DynamicValue(null, XSchema.BOOLEAN);
   }

   /**
    * Constructor.
    */
   public VSDimensionRef(DataRef group) {
      this();
      this.group = group;
   }

   /**
    * Set the contained data ref.
    */
   @Override
   public void setDataRef(DataRef group) {
      this.group = group;
      cname = null;
      chash = Integer.MIN_VALUE;
   }

   /**
    * Get the contained data ref.
    * @return the contained data ref.
    */
   @Override
   public DataRef getDataRef() {
      return group;
   }

   /**
    * Check if is a date type.
    * @return <tt>true</tt> if date, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isDateTime() {
      String type = getDataType();
      return XSchema.DATE.equals(type) || XSchema.TIME_INSTANT.equals(type) ||
         XSchema.TIME.equals(type);
   }

   private boolean isDateRef() {
     if(isDateTime()) {
        return true;
     }

     if(getDataRef() == null) {
        return false;
     }

     String type = getDataRef().getDataType();

     if(getDateLevel() <= 0) {
        return false;
     }

     return XSchema.DATE.equals(type) || XSchema.TIME_INSTANT.equals(type) ||
         XSchema.TIME.equals(type);
   }

   /**
    * Check if is a time series dimension.
    * @return <tt>true</tt> if is a time series dimension, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean isTimeSeries() {
      return timeseries;
   }

   /**
    * Set the time series option.
    * @param ts <tt>true</tt> if is a time series dimension, <tt>false</tt>
    * otherwise.
    */
   @Override
   public void setTimeSeries(boolean ts) {
      this.timeseries = ts;
   }

   /**
    * Check if is a date type.
    * @return <tt>true</tt> if date, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isDate() {
      switch(getDataType()) {
      case XSchema.DATE:
      case XSchema.TIME_INSTANT:
      case XSchema.TIME:
         return true;
      }

      return false;
   }

   /**
    * Get the full name.
    */
   @Override
   public String getFullName() {
      String name = getVSName();

      if((!isDateRef() || getNamedGroupInfo() instanceof DCNamedGroupInfo) ||
         (getRefType() & DataRef.CUBE) != 0 || name == null ||
         name.length() == 0)
      {
         if(groupType != null && name != null && name.length() != 0 && isNameGroup()) {
            return NamedRangeRef.getName(name, Integer.parseInt(groupType));
         }

         return name;
      }

      boolean period = dates != null && dates.length >= 2;
      name = period ? PERIOD_PREFIX + name : name;
      return DateRangeRef.getName(name, getDateLevel());
   }

   public String getFullNameByVariable() {
      String fullName = getFullName();

      return fullName != null ? fullName.replace("" + groupValue.getRValue(),
         groupValue.getDValue()) : null;
   }

   /**
    * Check if is a name group or not.
    */
   public boolean isNameGroup() {
      return groupInfo != null && !groupInfo.isEmpty();
   }

   /**
    * Get the named group definition.
    */
   @Override
   public XNamedGroupInfo getNamedGroupInfo() {
      return groupInfo;
   }

   /**
    * Set the named group definition.
    */
   @Override
   public void setNamedGroupInfo(XNamedGroupInfo info) {
      if(info == null || info instanceof SNamedGroupInfo) {
         this.groupInfo = (SNamedGroupInfo) info;
      }
      else {
         throw new IllegalArgumentException("Please set SNamedGroupInfo for VSDimensionRef.");
      }
   }

   /**
    * Check if is date range.
    */
   public boolean isDateRange() {
      return isDateTime() && (getRefType() & DataRef.CUBE) == 0;
   }

   /**
    * Create group ref.
    */
   public GroupRef createGroupRef(ColumnSelection cols) {
      DataRef ref = cols == null ? group : AssetUtil.getColumnRefFromAttribute(cols, group);

      if(ref == null) {
         return null;
      }

      if(isDateRange() && !(getNamedGroupInfo() instanceof DCNamedGroupInfo)) {
         // create a date range ref for date option
         String odtype = ref.getDataType();
         ref = ((ColumnRef) ref).getDataRef();
         boolean period = dates != null && dates.length >= 2;

         // AbstractCrosstabVSAQuery.createPeriodDimensionRef() creates a dimension with a
         // DateRangeRef as base. using DateRangeRef here causes the date range to be applied
         // twice. Since the dim_Period column doesn't exist, the final expression (getDataJS)
         // will return null. we either change the createPeriodDimensionRef to not use
         // DateRangeRef or use the base here. since date grouping the column twice is the
         // same as just applying the grouping from the top level, this change should be safe.
         // (45106)
         if(ref instanceof DateRangeRef) {
            ref = ((DateRangeRef) ref).getDataRef();
         }

         ref = new DateRangeRef(getFullName(), ref, getDateLevel(), forceDcToDateWeekOfMonth);
         ((DateRangeRef) ref).setOriginalType(odtype);
         ColumnRef column = new ColumnRef(ref);
         column.setDataType(ref.getDataType());
         GroupRef gref = new GroupRef(column);
         // make sure the date level in group in correct, because in
         // ChartVSAQuery, the base DateRangeRef will be replaced(?), then
         // the date level will be lost, fix bug1260160005881
         gref.setDateGroup(getDateLevel());

         if(period && cols != null) {
            ((DateRangeRef) ref).setApplyAutoDrill(false);
            cols.addAttribute(column);
         }

         gref.setDcMergeGroup(getDcMergeGroup());
         gref.setDcRangeCol(isDcRange());
         return gref;
      }

      if(groupInfo == null || groupInfo.isEmpty()) {
         return new GroupRef(ref);
      }

      // create named group range ref
      String dtype = ref.getDataType();
      ref = ((ColumnRef) ref).getDataRef();
      NamedRangeRef ref0 = new NamedRangeRef(getFullName(), ref);

      if(ref instanceof AttributeRef && caption != null) {
         caption = NamedRangeRef.getName(
            ((AttributeRef) ref).getCaption(), Integer.parseInt(groupType));
      }

      // @by robertwang, bug #6733. Allways set the NamedRangeRef data type to string,
      // because the group name can be any style.
      ref0.setBaseDataType(dtype);
      ref0.setDataType(XSchema.STRING);
      ref0.setRefType(getRefType());

      // backward compatibility
      if((getRefType() & DataRef.CUBE) == DataRef.CUBE) {
         processGroupInfo(getRefType(), groupInfo);
      }

      ref0.setNamedGroupInfo(groupInfo);
      ColumnRef column = new ColumnRef(ref0);
      column.setDataType(XSchema.STRING);
      DataRef nref = groupInfo.getDataRef();

      if(nref instanceof AttributeRef) {
         ((AttributeRef) nref).setDataType(dtype);
      }

      GroupRef groupRef = new GroupRef(column);
      groupRef.setDcRangeCol(isDcRange());

      return groupRef;
   }

   /**
    * Get the type of the field.
    * @return the type of the field.
    */
   @Override
   public int getRefType() {
      DataRef group = this.group;

      if(!(group instanceof ColumnRef)) {
         return this.refType;
      }

      return group.getRefType();
   }

   /**
    * set the type of the field.
    * @param refType the type of the field.
    */
   public void setRefType(int refType) {
      this.refType = (byte) refType;
   }

   /**
    * Get caption.
    * @param caption the caption of the dimension.
    */
   public void setCaption(String caption) {
      this.caption = caption;
   }

   /**
    * Set caption.
    * @return the caption of the dimension.
    */
   public String getCaption() {
      return caption;
   }

   /**
    * Check if the attribute is an expression.
    * @return <tt>true</tt> if is an expression, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpression() {
      return group != null && group.isExpression();
   }

   /**
    * Get the attribute's parent entity.
    * @return the name of the entity.
    */
   @Override
   public String getEntity() {
      return group == null ? null : group.getEntity();
   }

   /**
    * Get the attribute's parent entity.
    * @return an Enumeration with the name of the entity.
    */
   @Override
   public Enumeration getEntities() {
      return group == null ? new Vector().elements() : group.getEntities();
   }

   /**
    * Get the referenced attribute.
    * @return the name of the attribute.
    */
   @Override
   public String getAttribute() {
      return group == null ? "" : group.getAttribute();
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    * @return an Enumeration containing AttributeRef objects.
    */
   @Override
   public Enumeration getAttributes() {
      return group == null ? new Vector().elements() : group.getAttributes();
   }

   /**
    * Determine if the entity is blank.
    * @return <code>true</code> if entity is <code>null</code> or blank.
    */
   @Override
   public boolean isEntityBlank() {
      return group == null || group.isEntityBlank();
   }

   /**
    * Get the name of the field.
    * @return the name of the field.
    */
   @Override
   public String getName() {
      if(group == null) {
         Object rval = groupValue.getRValue();

         if(rval instanceof String) {
            return (String) rval;
         }

         return groupValue.getDValue();
      }

      return group.getName();
   }

   /**
    * Get the name for viewsheet only. Entity will be excluded.
    */
   private String getVSName() {
      DataRef group = this.group;

      if(group == null) {
         return "";
      }

      if(group instanceof ColumnRef) {
         String caption = ((ColumnRef) group).getCaption();

         if(caption != null && caption.length() > 0) {
            return caption;
         }

         String alias = ((ColumnRef) group).getAlias();

         if(alias != null && alias.length() > 0) {
            return alias;
         }
      }

      return group.getAttribute();
   }

   /**
    * Get the data type.
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType() {
      if(!(group instanceof ColumnRef)) {
         return dtype;
      }

      return group.getDataType();
   }

   /**
    * Set the data type.
    */
   public void setDataType(String dtype) {
      this.dtype = dtype;
   }

   /**
    * Check if is dynamic.
    * @return <tt>true</tt> if dynamic, <tt>false</tt> otherwise.
    */
   public boolean isDynamic() {
      String text = groupValue.getDValue();
      return VSUtil.isVariableValue(text) || VSUtil.isScriptValue(text);
   }

   /**
    * Check if contains dynamic value.
    */
   public boolean containsDynamic() {
      return VSUtil.isDynamic(groupValue) || VSUtil.isDynamic(rankingOptValue) ||
         VSUtil.isDynamic(rankingNValue) || VSUtil.isDynamic(rankingColValue) ||
         VSUtil.isDynamic(sortByColValue) || VSUtil.isDynamic(subVisibleValue) ||
         VSUtil.isDynamic(dlevelValue) || VSUtil.isDynamic(othersValue);
   }

   /**
    * Get the group column value.
    * @return the group column value.
    */
   public String getGroupColumnValue() {
      return groupValue.getDValue();
   }

   /**
    * Set the group column value.
    * @param value the column value.
    */
   public void setGroupColumnValue(String value) {
      groupValue.setDValue(value);
   }

   /**
    * Get the date level of this dimension reference. The date level options
    * are defined in DataRangeRef (e.g. YEAR_INTERVAL).
    * @return the date level of this dimension reference.
    */
   @Override
   public int getDateLevel() {
      Integer value = (Integer) dlevelValue.getRuntimeValue(true);

      return dlevel == null ? value == null ? 0 : value : dlevel;
   }

   /**
    * Set the date level of this dimension reference. The date level options
    * are defined in DataRangeRef (e.g. YEAR_INTERVAL).
    * @param dlevel the date level of this dimension reference.
    */
   @Override
   public void setDateLevel(int dlevel) {
      this.dlevel = dlevel == -1 ? null : dlevel;
      this.runtimeDValueChange = false;
   }

   /**
    * Get the date level value of this dimension reference.
    * @return the date level value of this dimension reference.
    */
   public String getDateLevelValue() {
      return dlevelValue.getDValue();
   }

   /**
    * Set the date level value of this dimension reference.
    * @param option the date level value of this dimension reference.
    */
   public void setDateLevelValue(String option) {
      if(!VSUtil.isVariableValue(option) || runtimeDValueChange) {
         this.dlevel = null;
      }

      this.dlevelValue.setDValue(option);
   }

   /**
    * check if the runtime date level value change
    * @return <tt>true</tt> if change, <tt>false</tt> otherwise.
    */
   public boolean runtimeDateLevelChange() {
      return runtimeDValueChange;
   }

   /**
    * Get the real date level of this dimension reference. For cube date,
    * the date level should be none.
    * @return the real date level of this dimension reference.
    */
   public int getRealDateLevel() {
      if(((getRefType() & DataRef.CUBE) == DataRef.CUBE) && isDateTime()) {
         return XConstants.NONE_DATE_GROUP;
      }

      return getDateLevel();
   }

   /**
    * Check if the option is visible.
    * @return the option visibility of the assembly.
    */
   public boolean isSubTotalVisible() {
      Boolean value = (Boolean) subVisibleValue.getRuntimeValue(true);
      return value == null ? false : value.booleanValue();
   }

   /**
    * Get the option visibility value.
    * @return the option visibility value of the assembly.
    */
   public String getSubTotalVisibleValue() {
      return subVisibleValue.getDValue();
   }

   /**
    * Set the option visibility value.
    * @param visible the option visibility value of the assembly.
    */
   public void setSubTotalVisibleValue(String visible) {
      this.subVisibleValue.setDValue(visible);
   }

   /**
    * Get the ranking condition of this dimension reference.
    * @return the ranking condition of this dimension reference.
    */
   public RankingCondition getRankingCondition() {
      return ranking;
   }

   /**
    * Set the ranking condition of this dimension reference.
    * @param ranking the ranking condition of this dimension reference.
    */
   public void setRankingCondition(RankingCondition ranking) {
      this.ranking = ranking;
   }

   /**
    * Get the ranking option value of this dimension reference.
    * @return the ranking option value of this dimension reference.
    */
   public String getRankingOptionValue() {
      return rankingOptValue.getDValue();
   }

   /**
    * Get the runtime ranking option value of this dimension reference.
    */
   @Override
   public int getRankingOption() {
      return (Integer) rankingOptValue.getRuntimeValue(true);
   }

   /**
    * Set the ranking option value of this dimension reference.
    */
   public void setRankingOptionValue(String opt) {
      this.rankingOptValue.setDValue(opt);
   }

   /**
    * Get the ranking option value of the drill root dimension reference.
    */
   public String getRootRankingOption() {
      return this.drillRootRankingOpt;
   }

   /**
    * Set the ranking option value of the drill root dimension reference.
    */
   public void setRootRankingOption(String opt) {
      this.drillRootRankingOpt = opt;
   }

   /**
    * Get the ranking n value of this dimension reference.
    */
   public String getRankingNValue() {
      return rankingNValue.getDValue();
   }

   /**
    * Get the runtime ranking n value of this dimension reference.
    */
   @Override
   public int getRankingN() {
      Object rankingN = rankingNValue.getRuntimeValue(true);
      return rankingN == null ? 0 : (Integer) rankingN;
   }

   /**
    * Set the ranking n value of this dimension reference.
    */
   public void setRankingNValue(String n) {
      this.rankingNValue.setDValue(n);
   }

   /**
    * Set the runtime ranking n value.
    */
   public void setRankingN(Integer n) {
      rankingNValue.setRValue(n);
   }

   /**
    * Get the ranking column value of this dimension reference.
    * @return the ranking column value of this dimension reference.
    */
   public String getRankingColValue() {
      return rankingColValue.getDValue();
   }

   /**
    * Get the runtime ranking column value of this dimension reference.
    * @return the runtime ranking column value of this dimension reference.
    */
   @Override
   public String getRankingCol() {
      return (String) rankingColValue.getRuntimeValue(true);
   }

   @Override
   public void setRankingCol(String col) {
      rankingColValue.setRValue(col);
   }

   /**
    * Set the ranking column value of this dimension reference.
    * @param col the ranking column value of this dimension reference.
    */
   public void setRankingColValue(String col) {
      this.rankingColValue.setDValue(col);
   }

   /**
    * Check if others should be grouped or discarded.
    */
   @Override
   public boolean isGroupOthers() {
      Boolean value = (Boolean) othersValue.getRuntimeValue(true);
      return value != null && value.booleanValue();
   }

   /**
    * Get the group others value.
    */
   public String getGroupOthersValue() {
      return othersValue.getDValue();
   }

   /**
    * Set the group others value.
    */
   public void setGroupOthersValue(String others) {
      othersValue.setDValue(others);
   }

   /**
    * Get the column to sort by value.
    */
   public String getSortByColValue() {
      return sortByColValue.getDValue();
   }

   /**
    * Set the column to sort by value.
    */
   public void setSortByColValue(String col) {
      this.sortByColValue.setDValue(col);
   }

   /**
    * Get the column to sort by.
    */
   @Override
   public String getSortByCol() {
      Object val = sortByColValue.getRValue();
      return val == null ? null : val.toString();
   }

   @Override
   public void setSortByCol(String col) {
      sortByColValue.setRValue(col);
   }

   /**
    * Get manual order list of the dimension ref.
    * @return the manual order list.
    */
   public List getManualOrderList() {
      return manualOrder;
   }

   /**
    * Set the manual order list of the dimension ref.
    * @param list the manual order list.
    */
   public void setManualOrderList(List list) {
      this.manualOrder = list;
   }

   /**
    * Create the associated comparer for this dimension ref.
    */
   @Override
   public Comparator createComparator(DataSet data) {
      if(getDcMergeGroup() != null && dcMergeGroupOrder != null) {
         return  new DefaultComparer() {
            @Override
            public int compare(Object v1, Object v2) {
               if(!(v1 instanceof DCMergeDatesCell) && !(v2 instanceof DCMergeDatesCell)) {
                  return 0;
               }

               DCMergeDatesCell cell1 = (DCMergeDatesCell) v1;
               DCMergeDatesCell cell2 = (DCMergeDatesCell) v2;

               if(cell1.getOriginalData() == null || cell2.getOriginalData() == null) {
                  return 0;
               }

               Integer order1 = dcMergeGroupOrder.get(((Date) cell1.getOriginalData()).getTime());
               Integer order2 = dcMergeGroupOrder.get(((Date) cell2.getOriginalData()).getTime());
               order1 = order1 == null ? 0 : order1;
               order2 = order2 == null ? 0 : order2;

               return getSign() *  (order1 - order2);
            }
         };
      }

      // as time series, always sort dimension in ascending order
      if(isDateTime() && isTimeSeries()) {
         return new DefaultComparer();
      }

      int order = getOrder();
      boolean string = XSchema.STRING.equals(getDataType());

      if(data != null) {
         string = data.getType(getFullName()) == String.class;
      }

      DefaultComparer comp = null;

      if((getRefType() & DataRef.CUBE) == 0) {
         comp = new DefaultComparer();

         if(string && !Locale.getDefault().getLanguage().equals("en")) {
            comp = new DefaultComparer() {
               @Override
               public int compare(Object v1, Object v2) {
                  return getSign() * base.compare(v1, v2);
               }

               private TextComparer base = new TextComparer(Collator_CN.getCollator());
            };
         }
      }
      else {
         comp = new DimensionComparer(getRefType());
      }

      Comparator comparator = comp;

      switch(order) {
      case XConstants.SORT_ASC:
         break;
      case XConstants.SORT_DESC:
         comp.setNegate(true);
         break;
      case XConstants.SORT_SPECIFIC:
      case XConstants.SORT_SPECIFIC | XConstants.SORT_DESC:
         if((getRefType() & DataRef.CUBE) != 0 &&
            (getManualOrderList() == null || getManualOrderList().isEmpty()))
         {
            // use comp
         }
         else {
            String dtype = isNameGroup() ? XSchema.STRING : getDataType();

            // for date manual sort get the data type based on the date level selected
            if(!isNameGroup() && isDateRange()) {
               dtype = DateRangeRef.getDataType(getDateLevel(), getDataType());
            }

            comparator = new ManualOrderComparer(dtype, getManualOrderList()) {
               @Override
               protected Object getData(Object obj) {
                  if(!isNameGroup() || obj == null) {
                     return super.getData(obj);
                  }

                  return obj.toString();
               }
            };
         }

         break;
      case XConstants.SORT_VALUE_ASC:
      case XConstants.SORT_VALUE_DESC:
         String sortBy = sortByColValue.getRValue() + "";
         Formula f1 = GraphUtil.getFormula(data, sortBy);
         boolean asc = order == XConstants.SORT_VALUE_ASC;
         comparator = new ValueOrderComparer(getFullName(), sortBy, f1, asc);
         break;
      case XConstants.SORT_ORIGINAL:
      case XConstants.SORT_NONE:
         comparator = null;
         break;
      default:
         throw new RuntimeException("Unsupported order found: " + order);
      }

      if(comparator != null && sortOthersLast && isRankingGroupOthers()) {
         return new CombinedDataSetComparator(getFullName(), comparator, new OthersComparator());
      }

      return comparator;
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> vals = new ArrayList<>();
      vals.add(groupValue);
      vals.add(rankingOptValue);
      vals.add(rankingNValue);
      vals.add(rankingColValue);

      // only sort by value, the sort by col is available
      // fix bug1366038906357
      if(order == XConstants.SORT_VALUE_ASC || order == XConstants.SORT_VALUE_DESC) {
         vals.add(sortByColValue);
      }

      vals.add(dlevelValue);
      vals.add(subVisibleValue);
      vals.add(othersValue);

      return vals;
   }

   /**
    * Get the dynamic values.
    * @return the dynamic values.
    */
   public List<DynamicValue> getHyperlinkDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();

      if(this instanceof HyperlinkRef) {
         Hyperlink hyperlink = ((HyperlinkRef)this).getHyperlink();

         if(hyperlink != null && VSUtil.isScriptValue(hyperlink.getLinkValue())) {
            list.add(hyperlink.getDLink());
         }
      }

      return list;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      VSUtil.renameDynamicValueDepended(oname, nname, groupValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, rankingOptValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, rankingNValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, rankingColValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, sortByColValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, dlevelValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, subVisibleValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, othersValue, vs);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   @Override
   public String toString() {
      return getFullName() + "[" + groupValue + "]";
   }

   /**
    * Get the view representation of this field.
    * @return the view representation of this field.
    */
   @Override
   public String toView() {
      String view = NamedRangeRef.getBaseName(getFullName());
      int idx = view.indexOf('(');

      if(idx > 0) {
         String dateGroup = view.substring(0, idx);
         view = Catalog.getCatalog().getString(dateGroup) + view.substring(idx);
      }

      return view;
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      String val;

      if((val = Tool.getAttribute(elem, "order")) != null) {
         order = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(elem, "timeSeries")) != null) {
         timeseries = "true".equals(val);
      }

      if((val = Tool.getAttribute(elem, "refType")) != null) {
         refType = (byte) Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(elem, "runtimeID")) != null) {
         setRuntimeID((byte) Integer.parseInt(val));
      }

      if((val = Tool.getAttribute(elem, "visible")) != null) {
         setDrillVisible("true".equals(val));
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      groupValue.setDValue(Tool.getChildValueByTagName(elem, "groupValue"));
      rankingOptValue.setDValue(Tool.getChildValueByTagName(elem, "rankingOptValue"));
      rankingNValue.setDValue(Tool.getChildValueByTagName(elem, "rankingNValue"));
      rankingColValue.setDValue(Tool.getChildValueByTagName(elem, "rankingColValue"));
      sortByColValue.setDValue(Tool.getChildValueByTagName(elem, "sortByColValue"));
      dlevelValue.setDValue(Tool.getChildValueByTagName(elem, "dlevelValue"));
      caption = Tool.getChildValueByTagName(elem, "caption");
      groupType = Tool.getChildValueByTagName(elem, "groupType");
      subVisibleValue.setDValue(Tool.getChildValueByTagName(elem, "subVisibleValue"));
      othersValue.setDValue(Tool.getChildValueByTagName(elem, "othersValue"));
      dtype = Tool.getChildValueByTagName(elem, "dtype");

      Element node = Tool.getChildNodeByTagName(elem, "group");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         group = AbstractDataRef.createDataRef(node);
         cname = null;
         chash = Integer.MIN_VALUE;
      }

      node = Tool.getChildNodeByTagName(elem, "manualOrderList");

      if(node != null) {
         ItemList mlist = new ItemList();
         mlist.parseXML(node);
         manualOrder = new ArrayList(Arrays.asList(mlist.toArray()));
      }

      node = Tool.getChildNodeByTagName(elem, "namedgroups");

      if(node != null) {
         String cls = Tool.getAttribute(node, "class");
         groupInfo = cls != null ? (SNamedGroupInfo) Class.forName(cls).newInstance() :
            new SNamedGroupInfo();
         groupInfo.parseXML(node);
      }

      String comboTypeString = Tool.getChildValueByTagName(elem, "comboType");

      if(comboTypeString != null) {
         comboType = ComboMode.values()[Integer.parseInt(comboTypeString)];
      }
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" order=\"" + order + "\"");
      writer.print(" timeSeries=\"" + timeseries + "\"");
      writer.print(" refType=\"" + refType + "\"");
      writer.print(" visible=\"" + isDrillVisible() + "\"");

      if(runtimeID != -1) {
         writer.print(" runtimeID=\"" + runtimeID + "\" ");
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(groupValue.getDValue() != null) {
         writer.print("<groupValue>");
         writer.print("<![CDATA[" + groupValue.getDValue() + "]]>");
         writer.println("</groupValue>");
      }

      if(groupValue.getRuntimeValue(true) != null) {
         writer.print("<groupRValue>");
         writer.print("<![CDATA[" + groupValue.getRuntimeValue(true) + "]]>");
         writer.println("</groupRValue>");
      }

      if(rankingOptValue.getDValue() != null) {
         writer.print("<rankingOptValue>");
         writer.print("<![CDATA[" + rankingOptValue.getDValue() + "]]>");
         writer.println("</rankingOptValue>");
      }

      if(rankingOptValue.getRuntimeValue(true) != null) {
         writer.print("<rankingOptRValue>");
         writer.print("<![CDATA[" + rankingOptValue.getRuntimeValue(true) +
                      "]]>");
         writer.println("</rankingOptRValue>");
      }

      if(rankingNValue.getDValue() != null) {
         writer.print("<rankingNValue>");
         writer.print("<![CDATA[" + rankingNValue.getDValue() + "]]>");
         writer.println("</rankingNValue>");
      }

      if(rankingNValue.getRuntimeValue(true) != null) {
         writer.print("<rankingNRValue>");
         writer.print("<![CDATA[" + rankingNValue.getRuntimeValue(true) +
                      "]]>");
         writer.println("</rankingNRValue>");
      }

      if(rankingColValue.getDValue() != null) {
         writer.print("<rankingColValue>");
         writer.print("<![CDATA[" + rankingColValue.getDValue() + "]]>");
         writer.println("</rankingColValue>");
      }

      if(rankingColValue.getRuntimeValue(true) != null) {
         writer.print("<rankingColRValue>");
         writer.print("<![CDATA[" + rankingColValue.getRuntimeValue(true) +
                      "]]>");
         writer.println("</rankingColRValue>");
      }

      if(sortByColValue.getDValue() != null) {
         writer.print("<sortByColValue>");
         writer.print("<![CDATA[" + sortByColValue.getDValue() + "]]>");
         writer.println("</sortByColValue>");
      }

      if(sortByColValue.getRuntimeValue(true) != null) {
         writer.print("<sortByColRValue>");
         writer.print("<![CDATA[" + sortByColValue.getRuntimeValue(true) +
                      "]]>");
         writer.println("</sortByColRValue>");
      }

      if(dlevelValue.getDValue() != null) {
         writer.print("<dlevelValue>");
         writer.print("<![CDATA[" + dlevelValue.getDValue() + "]]>");
         writer.println("</dlevelValue>");
      }

      if(dlevelValue.getRuntimeValue(true) != null) {
         writer.print("<dlevelRValue>");
         writer.print("<![CDATA[" + dlevelValue.getRuntimeValue(true) + "]]>");
         writer.println("</dlevelRValue>");
      }

      if(groupType != null) {
         writer.print("<groupType>");
         writer.print("<![CDATA[" + groupType + "]]>");
         writer.println("</groupType>");
      }

      if(subVisibleValue.getDValue() != null) {
         writer.print("<subVisibleValue>");
         writer.print("<![CDATA[" + subVisibleValue.getDValue() + "]]>");
         writer.println("</subVisibleValue>");
      }

      if(subVisibleValue.getRuntimeValue(true) != null) {
         writer.print("<subVisibleRValue>");
         writer.print("<![CDATA[" + subVisibleValue.getRuntimeValue(true) +
                      "]]>");
         writer.println("</subVisibleRValue>");
      }

      if(othersValue.getDValue() != null) {
         writer.print("<othersValue>");
         writer.print("<![CDATA[" + othersValue.getDValue() + "]]>");
         writer.println("</othersValue>");
      }

      if(othersValue.getRuntimeValue(true) != null) {
         writer.print("<othersRValue>");
         writer.print("<![CDATA[" + othersValue.getRuntimeValue(true) +
                      "]]>");
         writer.println("</othersRValue>");
      }

      if(caption != null) {
         writer.print("<caption>");
         writer.print("<![CDATA[" + caption + "]]>");
         writer.println("</caption>");
      }

      if(dtype != null) {
         writer.print("<dtype>");
         writer.print("<![CDATA[" + dtype + "]]>");
         writer.println("</dtype>");
      }

      if(group != null) {
         writer.print("<group>");
         group.writeXML(writer);
         writer.println("</group>");
      }

      if(manualOrder != null) {
         ItemList mlist = new ItemList("manualOrderList");
         mlist.addAllItems(manualOrder);
         mlist.writeXML(writer);
      }

      String fullName = getFullName();

      if(fullName != null && !fullName.equals("")) {
         writer.print("<fullName>");
         writer.print("<![CDATA[" + fullName + "]]>");
         writer.println("</fullName>");
      }

      if(groupInfo != null) {
         groupInfo.writeXML(writer);
      }

      if(comboType != null) {
         writer.print("<comboType>");
         writer.print("<![CDATA[" + comboType.ordinal() + "]]>");
         writer.println("</comboType>");
      }
   }

   /**
    * Get the sort order of this dimension ref.
    * @return the sort order of this dimension ref.
    */
   @Override
   public int getOrder() {
      return order;
   }

   /**
    * Set the sort order to this dimension ref.
    * @param order the specified order.
    */
   @Override
   public void setOrder(int order) {
      this.order = order;
   }

   /**
    * Set the previous sort order of the drill root dimension ref.
    * @param order the specified order.
    */
   public int setDrillRootOrder(int order) {
      return this.drillRootOrder = order;
   }

   /**
    * Get the previous sort order of the drill root dimension ref.
    * @return the previous sort order of the drill root dimension ref.
    */
   public int getDrillRootOrder() {
      return drillRootOrder;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      throw new Exception("Unsupported method called!");
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof VSDimensionRef)) {
         return false;
      }

      VSDimensionRef dref = (VSDimensionRef) obj;

      return Tool.equals(groupValue, dref.groupValue) &&
         Tool.equals(rankingOptValue, dref.rankingOptValue) &&
         Tool.equals(rankingNValue, dref.rankingNValue) &&
         Tool.equals(rankingColValue, dref.rankingColValue) &&
         Tool.equals(sortByColValue, dref.sortByColValue) &&
         (!isDateTime() || Tool.equals(dlevelValue, dref.dlevelValue)) &&
         Tool.equals(subVisibleValue, dref.subVisibleValue) &&
         Tool.equals(othersValue, dref.othersValue) &&
         order == dref.order && timeseries == dref.timeseries &&
         refType == dref.refType && Tool.equals(manualOrder, dref.manualOrder) &&
         Tool.equals(groupInfo, dref.groupInfo) &&
         Tool.equals(timeseries, dref.timeseries);
   }

   /**
    * Check if equals another object if ignore dynamic information.
    */
   public boolean equalsContentIgnoreSorting(Object obj) {
      if(!(obj instanceof VSDimensionRef)) {
         return false;
      }

      VSDimensionRef dref = (VSDimensionRef) obj;

      return Tool.equals(groupValue, dref.groupValue) &&
         Tool.equals(othersValue, dref.othersValue) &&
         Tool.equals(subVisibleValue, dref.subVisibleValue) &&
         Tool.equals(dlevelValue, dref.dlevelValue) &&
         timeseries == dref.timeseries &&
         Tool.equals(groupInfo, dref.groupInfo);
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   public List<DataRef> update(Viewsheet vs, ColumnSelection columns) {
      return update0(vs, columns, true);
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   protected List<DataRef> update0(Viewsheet vs, ColumnSelection columns,
                                   boolean calcAggSupportSort)
   {
      ArrayList<DataRef> refs = new ArrayList<>();

      // find the group ref
      Object[] arr = VSAggregateRef.toArray(groupValue.getRuntimeValue(false));
      Object[] darr = VSAggregateRef.toArray(dlevelValue.getRuntimeValue(false));
      Object[] varr = VSAggregateRef.toArray(subVisibleValue.getRuntimeValue(false));
      Object[] roarr = VSAggregateRef.toArray(rankingOptValue.getRuntimeValue(false));
      Object[] rnarr = VSAggregateRef.toArray(rankingNValue.getRuntimeValue(false));
      Object[] rcarr = VSAggregateRef.toArray(rankingColValue.getRuntimeValue(false));
      Object[] sbarr = VSAggregateRef.toArray(sortByColValue.getRuntimeValue(false));
      Object[] oarr = VSAggregateRef.toArray(othersValue.getRuntimeValue(false));

      this.group = null;

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] == null) {
            continue;
         }

         String gtext = Tool.toString(arr[i]);
         DataRef group = columns.getAttribute(gtext);

         if(group == null && !gtext.equals("null") && !gtext.equals("")) {
            // don't throw an exception so the rendering can complete without
            // generating a broken chart image
            LOG.warn("Column not found: " + groupValue + " (" + columns + ")");

            if(groupValue.getDValue().startsWith("=")) {
               CoreTool.addUserMessage(Catalog.getCatalog().getString(
                  "common.viewsheet.expressionColumn", groupValue));
            }

            continue;
         }

         VSDimensionRef ref = (VSDimensionRef) this.clone();

         if(group instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) group;

            if(col.getDataRef() instanceof AttributeRef) {
               AttributeRef aref = (AttributeRef) col.getDataRef();
               setCaption(aref.getCaption());
            }
         }

         ref.group = this.group = group;

         if((isVariable() || isScript()) && group != null) {
            boolean isTimeType = XSchema.TIME.equals(group.getDataType());
            boolean isDateType = XSchema.DATE.equals(group.getDataType());

            if(isTimeType && !isTimeLevel()) {
               setDateLevelValue(String.valueOf(XConstants.HOUR_DATE_GROUP));
            }

            if(isDateType && !isDateLevel()) {
               setDateLevelValue(String.valueOf(XConstants.YEAR_DATE_GROUP));
            }
         }

         ref.caption = caption;
         ref.cname = null;
         ref.chash = Integer.MIN_VALUE;
         refs.add(ref);

         if(darr.length > 0) {
            ref.dlevelValue.setRValue(darr[i % darr.length]);

            if(oldRuntimeDateLevel != null &&
               oldRuntimeDateLevel.intValue() != (int) darr[i % darr.length])
            {
               dlevel = null;
               runtimeDValueChange = true;
            }

            oldRuntimeDateLevel = (Integer) darr[i % darr.length];
         }

         if(varr.length > 0) {
            ref.subVisibleValue.setRValue(varr[i % varr.length]);
         }

         if(roarr.length > 0) {
            ref.rankingOptValue.setRValue(roarr[i % roarr.length]);
         }

         if(rnarr.length > 0) {
            ref.rankingNValue.setRValue(rnarr[i % rnarr.length]);
         }

         if(rcarr.length > 0) {
            ref.rankingColValue.setRValue(rcarr[i % rcarr.length]);
         }

         if(sbarr.length > 0) {
            ref.sortByColValue.setRValue(sbarr[i % sbarr.length]);
         }

         if(oarr.length > 0) {
            ref.othersValue.setRValue(oarr[i % oarr.length]);
         }

         if(!calcAggSupportSort) {
            ref.updateRanking(columns);
         }
      }

      return refs;
   }

   public boolean isTimeLevel() {
      int dateLevel = getDateLevel();

      return dateLevel == XConstants.HOUR_DATE_GROUP || dateLevel == XConstants.MINUTE_DATE_GROUP ||
         dateLevel == XConstants.SECOND_DATE_GROUP || dateLevel == XConstants.HOUR_OF_DAY_DATE_GROUP
         || dateLevel == XConstants.MINUTE_OF_HOUR_DATE_GROUP ||
         dateLevel == XConstants.SECOND_OF_MINUTE_DATE_GROUP ||
         dateLevel == XConstants.NONE_DATE_GROUP;
   }

   public boolean isDateLevel() {
      int dateLevel = getDateLevel();

      return dateLevel == XConstants.YEAR_DATE_GROUP ||
         dateLevel == XConstants.QUARTER_DATE_GROUP ||
         dateLevel == XConstants.MONTH_DATE_GROUP ||
         dateLevel == XConstants.WEEK_DATE_GROUP ||
         dateLevel == XConstants.DAY_DATE_GROUP ||
         dateLevel == XConstants.QUARTER_OF_YEAR_DATE_GROUP ||
         dateLevel == XConstants.MONTH_OF_YEAR_DATE_GROUP ||
         dateLevel == XConstants.WEEK_OF_YEAR_DATE_GROUP ||
         dateLevel == XConstants.DAY_OF_MONTH_DATE_GROUP ||
         dateLevel == XConstants.DAY_OF_WEEK_DATE_GROUP ||
         dateLevel == XConstants.NONE_DATE_GROUP;
   }

   /**
    * Whether is full week date level.
    *
    * @return
    */
   public boolean isFullWeekDateLevel() {
      int dateLevel = getDateLevel();

      return dateLevel == DateRangeRef.MONTH_OF_FULL_WEEK ||
         dateLevel == DateRangeRef.QUARTER_OF_FULL_WEEK ||
         dateLevel == DateRangeRef.YEAR_OF_FULL_WEEK;
   }

   /**
    * Set the ranking condition.
    */
   public void updateRanking(ColumnSelection columns) {
      ranking = null;
      int option = (Integer) rankingOptValue.getRuntimeValue(true);

      if(option != XCondition.NONE) {
         Integer nobj = (Integer) rankingNValue.getRuntimeValue(true);

         if(nobj != null && nobj > 0) {
            // find the aggregate ref
            Object obj = rankingColValue.getRuntimeValue(true);
            String ctext = obj == null ? null : obj.toString();
            DataRef aref = columns.getAttribute(ctext);

            // fix bug bug1301304453692, try getting fuzzy column
            if(aref == null && ctext != null && ctext.length() > 0) {
               int from = ctext.lastIndexOf('(');
               int to = ctext.lastIndexOf(')');

               if(from > 0 && to > from) {
                  String col = ctext.substring(from + 1, to);
                  DataRef back = null;
                  DataRef back2 = null;

                  for(int i = 0; i < columns.getAttributeCount(); i++) {
                     DataRef aref2 = columns.getAttribute(i);
                     String ctext2 = aref2.toString();
                     int from2 = ctext2.lastIndexOf('(');
                     int to2 = ctext2.lastIndexOf(')');

                     if(from2 > 0 && to2 > from2) {
                        String col2 = ctext2.substring(from2 + 1, to2);

                        if(col2.equals(col)) {
                           aref = aref2;
                           break;
                        }

                        if(back != null) {
                           continue;
                        }

                        String fcol = col;
                        int idx = col.indexOf(',');

                        if(idx > 0) {
                           col2 = col.substring(0, idx);
                        }

                        String fcol2 = col2;
                        idx = col2.indexOf(',');

                        if(idx > 0) {
                           fcol2 = col2.substring(0, idx);
                        }

                        if(fcol2.equals(fcol)) {
                           back = aref2;
                        }
                     }
                     else if(ctext2.equals(col)) {
                        back2 = aref2;
                     }
                  }

                  if(aref == null) {
                     aref = (back != null) ? back : back2;
                  }
               }
               // agg calc formula is none, so not have "()" encode
               // such as "% of subtotal: CalcField"
               else if(from == -1 && to == -1) {
                  for(int i = 0; i < columns.getAttributeCount(); i++) {
                     DataRef aref2 = columns.getAttribute(i);
                     String ctext2 = aref2.toString();

                     if(ctext.endsWith(ctext2)) {
                        aref = aref2;
                        break;
                     }
                  }
               }
            }

            if(ctext != null) {
               ranking = new RankingCondition();
               ranking.setN(nobj);
               ranking.setDataRef(aref);
               ranking.setOperation(option);
               ranking.setGroupOthers(isGroupOthers());
            }
         }
      }
   }

   /**
    * Set the ranking condition.
    */
   public void updateRanking(VSAggregateRef[] aggregates) {
      ranking = null;
      int option = (Integer) rankingOptValue.getRuntimeValue(true);

      if(option != XCondition.NONE) {
         Integer nobj = (Integer) rankingNValue.getRuntimeValue(true);

         if(nobj != null && nobj > 0) {
            // find the aggregate ref
            Object obj = rankingColValue.getRuntimeValue(true);
            String ctext = obj == null ? null : obj.toString();
            VSAggregateRef aggregate = null;

            for(VSAggregateRef agg : aggregates) {
               if(Tool.equals(ctext, agg.getFullName()) ||
                  agg instanceof VSAggregateRef && Tool.equals(ctext,
                     CrossTabFilterUtil.getCrosstabRTAggregateName((VSAggregateRef) agg, true)))
               {
                  aggregate = agg;
                  break;
               }
            }

            if(aggregate != null) {
               ranking = new RankingCondition();
               ranking.setN(nobj);
               ranking.setDataRef(aggregate);
               ranking.setOperation(option);
               ranking.setGroupOthers(isGroupOthers());
            }
         }
      }
   }

   /**
    * Check if the column is a variable.
    */
   public boolean isVariable() {
      return VSUtil.isVariableValue(groupValue.getDValue());
   }

   /**
    * Check if the column is a variable.
    */
   public boolean isScript() {
      return VSUtil.isScriptValue(groupValue.getDValue());
   }

   /**
    * Get the dates for period comparison.
    * @return the dates.
    */
   @Override
   public String[] getDates() {
      return dates;
   }

   /**
    * Set the dates for period comparison.
    * @param dates the dates.
    */
   public void setDates(String[] dates) {
      this.dates = dates;
   }

   /**
    * Get the name group type.
    */
   public String getGroupType() {
      return groupType;
   }

   /**
    * Set the name group type.
    */
   public void setGroupType(String groupType) {
      this.groupType = groupType;
   }

   @Override
   public boolean isSortOthersLast() {
      return sortOthersLast;
   }

   @Override
   public void setSortOthersLast(boolean sortOthersLast) {
      this.sortOthersLast = sortOthersLast;
   }

   @Override
   public VSDimensionRef clone() {
      VSDimensionRef dim = (VSDimensionRef) super.clone();
      dim.groupValue = (DynamicValue) groupValue.clone();
      dim.rankingOptValue = (DynamicValue) rankingOptValue.clone();
      dim.rankingNValue = (DynamicValue) rankingNValue.clone();
      dim.rankingColValue = (DynamicValue) rankingColValue.clone();
      dim.sortByColValue = (DynamicValue) sortByColValue.clone();
      dim.dlevelValue = (DynamicValue) dlevelValue.clone();
      dim.subVisibleValue = (DynamicValue) subVisibleValue.clone();
      dim.othersValue = (DynamicValue) othersValue.clone();

      if(manualOrder != null) {
         dim.manualOrder = Tool.deepCloneCollection(manualOrder);
      }

      if(groupInfo != null) {
         dim.groupInfo = (SNamedGroupInfo) groupInfo.clone();
      }

      return dim;
   }

   /**
    * Get the period value.
    */
   private String getPeriodValue(ConditionList clist, boolean more) {
      for(int i = 0; i < clist.getConditionSize(); i++) {
         ConditionItem item = clist.getConditionItem(i);

         if(item == null) {
            continue;
         }

         Condition condition = item.getCondition();

         if(condition.getOperation() == Condition.GREATER_THAN) {
            int count = condition.getValueCount();

            if(count > 0) {
               Object value = condition.getValue(0);
               // fix bug1306293520679, should apply format in
               // PeriodDateTableLens, because the DB may not support the
               // charset after format here
               return value instanceof Date ? ((Date) value).getTime() + "" :
                                              value.toString();
            }
         }
      }

      return null;
   }

   /**
    * Convert full name to caption for group condition.
    */
   private void processGroupInfo(int dimType, SNamedGroupInfo groupInfo0) {
      String[] names = groupInfo0.getGroups();

      if(names == null || names.length == 0) {
         return;
      }

      for(int i = 0; i < names.length; i++) {
         List gvalues = groupInfo0.getGroupValue(names[i]);

         for(int j = 0; j < gvalues.size(); j++) {
            Object val = gvalues.get(j);

            if(val != null) {
               val = VSCubeTableLens.getDisplayValue(val, dimType);
               gvalues.set(j, val);
            }
         }
      }
   }

   /**
    * Copy the dimension options such as sorting and topN.
    */
   @Override
   public void copyOptions(XDimensionRef ref) {
      if(!(ref instanceof VSDimensionRef)) {
         return;
      }

      VSDimensionRef ref0 = (VSDimensionRef) ref;

      this.order = ref0.order;
      this.rankingOptValue = ref0.rankingOptValue.clone();
      this.rankingNValue = ref0.rankingNValue.clone();
      this.rankingColValue = ref0.rankingColValue.clone();
      this.sortByColValue = ref0.sortByColValue.clone();
      this.subVisibleValue = ref0.subVisibleValue.clone();
      this.othersValue = ref0.othersValue.clone();
   }

   /**
    * When drill down on a dimension which has a variable datelevel, should copy the
    * oldRuntimeDateLevel from the original dimension to the next dimension then we can distinguish
    * whether it's dynamic date level changed or drill down happened.
    * @param from
    */
   public void copyOldVariableDateLevel(VSDimensionRef from) {
      if(from != null) {
         oldRuntimeDateLevel = from.oldRuntimeDateLevel;
      }
   }

   /**
    * Set runtime id.
    */
   public void setRuntimeID(int rid) {
      if(rid < Byte.MIN_VALUE || rid > Byte.MAX_VALUE) {
         throw new IllegalArgumentException("Value out of byte range: " + rid);
      }

      this.runtimeID = (byte) rid;
   }

   /**
    * Get runtime id.
    */
   public int getRuntimeID() {
      return runtimeID;
   }


   public ComboMode getComboType() {
      return comboType;
   }

   public void setComboType(ComboMode comboType) {
      this.comboType = comboType;
   }

   public boolean isDcRange() {
      return dcRange;
   }

   public void setDcRange(boolean dcRange) {
      this.dcRange = dcRange;
   }

   public void setDcMergeGroup(Map<GroupTuple, DCMergeDatesCell> dcMergeGroup) {
      this.dcMergeGroup = dcMergeGroup;
   }

   public Map<GroupTuple, DCMergeDatesCell> getDcMergeGroup() {
      return dcMergeGroup;
   }

   public void setDcMergeGroupOrder(Map<Long, Integer> dcMergeGroupOrder) {
      this.dcMergeGroupOrder = dcMergeGroupOrder;
   }

   public boolean isIgnoreDcTemp() {
      return ignoreDcTemp;
   }

   public void setIgnoreDcTemp(boolean ignoreDcTemp) {
      this.ignoreDcTemp = ignoreDcTemp;
   }

   public Integer getForceDcToDateWeekOfMonth() {
      return forceDcToDateWeekOfMonth;
   }

   public void setForceDcToDateWeekOfMonth(Integer dcToDateWeekOfMonth) {
      this.forceDcToDateWeekOfMonth = dcToDateWeekOfMonth;
   }

   private boolean ignoreDcTemp;
   private static final String PERIOD_PREFIX = "P_";
   private DynamicValue groupValue;
   private DynamicValue rankingOptValue;
   private DynamicValue rankingNValue;
   private DynamicValue rankingColValue;
   private DynamicValue sortByColValue;
   private DynamicValue subVisibleValue;
   private DynamicValue dlevelValue; // date level value
   private DynamicValue othersValue; // group others or discard
   private String caption;
   private String groupType;
   private String dtype = XSchema.STRING; // data type
   private byte refType = NONE;
   private int order = XConstants.SORT_ASC; // sorting order
   private int drillRootOrder = -1;
   private String drillRootRankingOpt;
   private boolean timeseries = false; // whether is a time series dimension
   private List manualOrder; // manually sorting order

   private boolean sortOthersLast = true;
   // runtime
   private Integer dlevel;
   private DataRef group;
   private RankingCondition ranking;
   private String[] dates;
   private SNamedGroupInfo groupInfo;
   private int runtimeID = (byte) -1;
   private ComboMode comboType = ComboMode.VALUE;
   private Integer oldRuntimeDateLevel; // old runtime date level value
   //runtime date level value change
   private boolean runtimeDValueChange;
   private boolean dcRange;
   private Map<GroupTuple, DCMergeDatesCell> dcMergeGroup;
   private Map<Long, Integer> dcMergeGroupOrder; // key is original date value is order index;
   private Integer forceDcToDateWeekOfMonth;

   private static final Logger LOG = LoggerFactory.getLogger(VSDimensionRef.class);
}

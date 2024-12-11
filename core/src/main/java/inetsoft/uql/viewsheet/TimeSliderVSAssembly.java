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

import inetsoft.graph.internal.GTool;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.math.*;
import java.text.DateFormat;
import java.text.Format;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TimeSliderVSAssembly represents one time slider assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TimeSliderVSAssembly extends AbstractSelectionVSAssembly
   implements TitledVSAssembly, StateSelectionListVSAssembly, MaxModeSupportAssembly
{
   /**
    * Label year format.
    */
   public static final String LABEL_YEAR_PATTERN = "yyyy";
   /**
    * Label month format.
    */
   public static final String LABEL_MONTH_PATTERN = "yyyy MMM";
   /**
    * Label day format.
    */
   public static final String LABEL_DAY_PATTERN = "yyyy-MM-dd";
   /**
    * Label hour format.
    */
   public static final String LABEL_HOUR_PATTERN = "yyyy-MM-dd HH";
   /**
    * Label minute format.
    */
   public static final String LABEL_MINUTE_PATTERN = "yyyy-MM-dd HH:mm";
   /**
    * Label hour time format.
    */
   public static final String LABEL_HOUR_OF_DAY_PATTERN = "HH";
   /**
    * Label minute time format.
    */
   public static final String LABEL_MINUTE_OF_DAY_PATTERN = "HH:mm";
   /**
    * Value year format.
    */
   public static final ThreadLocal<DateFormat> VALUE_YEAR_FORMAT = Tool.yearFmt;
   /**
    * Value month format.
    */
   public static final ThreadLocal<DateFormat> VALUE_MONTH_FORMAT = Tool.monthFmt;
   /**
    * Value day format.
    */
   public static final ThreadLocal<DateFormat> VALUE_DAY_FORMAT = Tool.dayFmt;
   /**
    * Value hour format.
    */
   public static final ThreadLocal<DateFormat> VALUE_HOUR_FORMAT = Tool.timeInstantFmt;
   /**
    * Value minute format.
    */
   public static final ThreadLocal<DateFormat> VALUE_MINUTE_FORMAT = Tool.timeInstantFmt;
   /**
    * Value hour time format.
    */
   public static final ThreadLocal<DateFormat> VALUE_HOUR_OF_DAY_FORMAT = Tool.timeFmt;
   /**
    * Value minute time format.
    */
   public static final ThreadLocal<DateFormat> VALUE_MINUTE_OF_DAY_FORMAT = Tool.timeFmt;

   /**
    * Get the preferred ticks number.
    * @return double array contains ticks value.
    */
   public static double[] getPreferredTicks(double mind, double maxd,
                                            int tickNo, boolean excludeRight,
                                            boolean islog, double inc) {
      ArrayList<Double> ticks = new ArrayList<>();

      if(islog) {
         double omind = mind;
         double omaxd = maxd;
         int base = TimeSliderVSAssembly.getLogIncrment(mind, maxd);
         int more = (int) (10 - TimeSliderVSAssembly.log(maxd - mind, base));
         double start = (more > 1) ? 1 / Math.pow(2, more) : 1;
         mind = roundToPow(mind, base, false);
         double nmaxd = roundToPow(maxd, base, true);

         // enlarge the max to fit exclude the right value usage
         if(excludeRight && nmaxd <= maxd) {
            nmaxd = nmaxd < 0 ? nmaxd / base : nmaxd * base;
         }

         maxd = nmaxd;

         // favor zero as min
         if(mind > 0 && mind < start) {
            mind = 0;
         }

         if(mind == Double.NEGATIVE_INFINITY ||
            maxd == Double.POSITIVE_INFINITY)
         {
            throw new RuntimeException("The range exceed the maximum for min: "
               + omind + ", max:" + omaxd);
         }

         for(double n = mind; n <= maxd; ) {
            ticks.add(n);

            if(n < 0 && maxd > 0 && n > -0.1) {
               n = 0;
            }

            if(n == 0) {
               n = start;
            }
            else {
               n = (n < 0) ? n / base : n * base;
            }
         }
      }
      else {
         double len0 = inc == 0 ? 0 : (maxd - mind) / inc;

         // ignore 6.0000000000001
         if(!GTool.isInteger(len0)) {
            len0 = Math.ceil(len0);
         }
         else {
            len0 = excludeRight ? Math.round(len0) + 1 : Math.round(len0);
         }

         len0 = len0 + 1;
         int len = (int) len0;
         String str = Tool.toString(inc);
         int dot = str.indexOf('.');
         int dp = str.length() - dot - 1;
         double factor = Math.pow(10, dp);
         BigInteger binc0 = createBigInteger(inc * factor);
         double mind0 = mind * factor;
         double inc0 = binc0.doubleValue();

         for(int i = 0; i < len; i++) {
            BigDecimal val = createBigDecimal(mind0 + inc0 * i);
            ticks.add(val.doubleValue() / factor);
         }
      }

      double[] result = new double[ticks.size()];

      for(int i = 0; i < result.length; i++) {
         result[i] = ticks.get(i);
      }

      if(result.length <= 2) {
         result = new double[] {mind, maxd};
      }

      return result;
   }

   /**
    * Find an increment to produce nice numbers on a number scale.
    */
   public static double[] getNiceNumbers(double mind, double maxd, int tickNo) {
      double[] nums = GTool.getNiceNumbers(mind, maxd, Double.NaN,
                                           Double.NaN, tickNo, false);

      if(nums[0] == nums[1] && mind == maxd) {
         nums[0] = mind;
         nums[1] = maxd;
      }

      // modified the inc to make the tick number close to expectation
      double multiple = (((nums[1] - nums[0]) / nums[2]) / tickNo);

      if(multiple <= 0 || Double.isNaN(multiple)) {
         return nums;
      }

      int pow = (int) TimeSliderVSAssembly.log(multiple, 10);

      if(multiple > 8) {
         nums[2] = nums[2] * 10 * Math.pow(10, pow);
      }
      else if(multiple > 2) {
         nums[2] = nums[2] * 2 * Math.pow(10, pow);
      }
      else if(multiple < 0.125) {
         nums[2] = nums[2] / 10 * Math.pow(10, pow);
      }
      else {
         String str = Tool.toString(nums[2]);
         int dot = str.indexOf('.');
         int dp = str.length() - dot - 1;
         double factor = Math.pow(10, dp);
         BigInteger size = createBigInteger(nums[2] * factor);
         int mod5 = size.mod(BigInteger.valueOf(5)).intValue();
         int mod3 = size.mod(BigInteger.valueOf(3)).intValue();
         BigDecimal power = createBigDecimal(Math.pow(10, pow));
         BigDecimal dsize = createBigDecimal(size);

         if(mod5 == 0 && multiple <= 0.2) {
            nums[2] = dsize.divide(createBigDecimal(5 * factor)).
               multiply(power).doubleValue();
         }
         else if(mod3 == 0 && multiple * 3 < 1) {
            nums[2] = dsize.divide(createBigDecimal(3 * factor)).
               multiply(power).doubleValue();
         }
         else {
            // make sure the ticks contains max value
            int couple = (int) Math.pow(2,
               Math.ceil(TimeSliderVSAssembly.log(1 / multiple, 2)));
            nums[2] = dsize.divide(createBigDecimal(couple * factor)).
               multiply(power).doubleValue();
         }
      }

      return nums;
   }

   /**
    * Create one big integer from the given double value.
    */
   private static BigInteger createBigInteger(double val) {
      return BigDecimal.valueOf(val).toBigInteger();
   }

   /**
    * Create one big decimal from the given double value.
    */
   private static BigDecimal createBigDecimal(double val) {
      return BigDecimal.valueOf(val);
   }

   /**
    * Create one big integer from the given big integer.
    * Precision will be set to 10.
    */
   private static BigDecimal createBigDecimal(BigInteger bi) {
      MathContext context = new MathContext(10);
      return new BigDecimal(bi, context);
   }

   /**
    * Round value to the power of the base.
    */
   public static double roundToPow(double v, double base, boolean ceil) {
      if(v == 0) {
         return 0;
      }

      int sign = 1;

      if(v < 0) {
         v = -v;
         sign = -1;
         ceil = !ceil;
      }

      v = TimeSliderVSAssembly.log(v, base);
      v = ceil ? Math.ceil(v) : Math.floor(v);
      return sign * Math.pow(base, v);
   }

   /**
    * Get the best log increment base value.
    */
   public static int getLogIncrment(double mind, double maxd) {
      int[] incs = {10, 5, 2};
      int inc = 10;

      // find a multiple to get at least 10 ticks
      for(int value : incs) {
         inc = value;

         if(log(maxd - mind, inc) >= 10) {
            break;
         }
      }

      return inc;
   }

   /**
    * Log value of specified base.
    */
   public static double log(double v, double base) {
      return Math.log(v) / Math.log(base);
   }

   /**
    * Constructor.
    */
   public TimeSliderVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public TimeSliderVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new TimeSliderVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.TIME_SLIDER_ASSET;
   }

   /**
    * Get the name of the target table.
    * @return the name of the target table.
    */
   @Override
   public String getTableName() {
      return getTimeSliderInfo().getTableName();
   }

   /**
    * Set the name of the target table.
    * @param table the specified name of the target table.
    */
   @Override
   public void setTableName(String table) {
      getTimeSliderInfo().setTableName(table);
   }

   /**
    * Get the time information. This is either a SingleTimeInfo or a
    * CompositeTimeInfo, depending on whether slider is bound to one or
    * multiple columns respectively.
    * @return the time information.
    */
   public TimeInfo getTimeInfo() {
      return getTimeSliderInfo().getTimeInfo();
   }

   /**
    * Set the time information.
    * @param info time information.
    */
   public void setTimeInfo(TimeInfo info) {
      getTimeSliderInfo().setTimeInfo(info);
   }

   /**
    * Get the current position.
    * @return the current position.
    */
   public int getCurrentPos() {
      return getTimeSliderInfo().getCurrentPos();
   }

   /**
    * Get the total length.
    * @return the total length.
    */
   public int getTotalLength() {
      return getTimeSliderInfo().getTotalLength();
   }

   /**
    * Check if the runtime numbers are in log scale.
    */
   public boolean isLogScale() {
      return getTimeSliderInfo().isLogScale();
   }

   /**
    * Check if the design time numbers are in log scale.
    */
   public boolean getLogScaleValue() {
      return getTimeSliderInfo().getLogScaleValue();
   }

   /**
    * Set if the design time numbers are in log scale. Log scale is only applied
    * to numeric values.
    */
   public void setLogScaleValue(boolean log) {
      getTimeSliderInfo().setLogScaleValue(log);
   }

   /**
    * If it shows current value label at runtime.
    * @return it shows current.
    */
   public boolean isCurrentVisible() {
      return getTimeSliderInfo().isCurrentVisible();
   }

   /**
    * If it shows current value label at desing time.
    * @return it shows current.
    */
   public boolean getCurrentVisibleValue() {
      return getTimeSliderInfo().getCurrentVisibleValue();
   }

   /**
    * Set if it shows current value label at design time.
    * @param current if it shows current.
    */
   public void setCurrentVisibleValue(boolean current) {
      getTimeSliderInfo().setCurrentVisibleValue(current);
   }

   /**
    * If it shows maximum value label at runtime.
    * @return it shows max.
    */
   public boolean isMaxVisible() {
      return getTimeSliderInfo().isMaxVisible();
   }

   /**
    * If it shows maximum value label at design time.
    * @return it shows max.
    */
   public boolean getMaxVisibleValue() {
      return getTimeSliderInfo().getMaxVisibleValue();
   }

   /**
    * Set if it shows maximum value label at design time.
    * @param max if it shows max.
    */
   public void setMaxVisibleValue(boolean max) {
      getTimeSliderInfo().setMaxVisibleValue(max);
   }

   /**
    * If it shows minimum value label at runtime.
    * @return it shows min.
    */
   public boolean isMinVisible() {
      return getTimeSliderInfo().isMinVisible();
   }

   /**
    * If it shows minimum value label at design time.
    * @return it shows min.
    */
   public boolean getMinVisibleValue() {
      return getTimeSliderInfo().getMinVisibleValue();
   }

   /**
    * Set if it shows minimum value label at design time.
    * @param min if it shows min.
    */
   public void setMinVisibleValue(boolean min) {
      getTimeSliderInfo().setMinVisibleValue(min);
   }

   /**
    * If the tick is visible at runtime.
    * @return visibility of ticks.
    */
   public boolean isTickVisible() {
      return getTimeSliderInfo().isTickVisible();
   }

   /**
    * If the tick is visible at design time.
    * @return visibility of ticks.
    */
   public boolean getTickVisibleValue() {
      return getTimeSliderInfo().getTickVisibleValue();
   }

   /**
    * Set the visibility of ticks at design time.
    * @param visible the visibility of ticks.
    */
   public void setTickVisibleValue(boolean visible) {
      getTimeSliderInfo().setTickVisibleValue(visible);
   }

   /**
    * If the tick label is visible at runtime.
    * @return visibility of min value.
    */
   public boolean isLabelVisible() {
      return getTimeSliderInfo().isLabelVisible();
   }

   /**
    * If the tick label is visible at design time.
    * @return visibility of min value.
    */
   public boolean getLabelVisibleValue() {
      return getTimeSliderInfo().getLabelVisibleValue();
   }

   /**
    * Set the visibility of tick labels at .
    * @param visible the visibility of tick labels.
    */
   public void setLabelVisibleValue(boolean visible) {
      getTimeSliderInfo().setLabelVisibleValue(visible);
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return getTimeSliderInfo().getTitle();
   }

   /**
    * Get the group title value.
    * @return the title value of the checkbox assembly.
    */
   @Override
   public String getTitleValue() {
      return getTimeSliderInfo().getTitleValue();
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      getTimeSliderInfo().setTitleValue(value);
   }

   /**
    * Get time slider assembly info.
    * @return the time slider assembly info.
    */
   public TimeSliderVSAssemblyInfo getTimeSliderInfo() {
      return (TimeSliderVSAssemblyInfo) getInfo();
   }

   @Override
   public MaxModeSupportAssemblyInfo getMaxModeInfo() {
      return getTimeSliderInfo();
   }

   /**
    * Get the selection.
    * @param map the container contains the selection of this selection
    * viewsheet assembly.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   @Override
   public boolean getSelection(Map<String, Map<String, Collection<Object>>> map, boolean applied) {
      if(!isEnabled() || !hasActiveSelection()) {
         return false;
      }

      final Vector<Object> rangeConditions = new Vector<>();
      rangeConditions.add(RangeCondition.from(this));
      final String rangeSelectionKey = RANGE + VSUtil.getSelectionKey(getDataRefs());

      for(String tableName : getTableNames()) {
         final Map<String, Collection<Object>> tableSelections =
            map.computeIfAbsent(tableName, k -> new HashMap<>());

         tableSelections.computeIfAbsent(rangeSelectionKey, k -> new Vector<>())
            .addAll(rangeConditions);
      }

      return false;
   }

   private boolean hasActiveSelection() {
      final SelectionList slist = getStateSelectionList();
      final SelectionList vlist = getSelectionList();
      final int sCount = slist == null ? -1 : slist.getSelectionValueCount();
      final int vCount = vlist == null ? -1 : vlist.getSelectionValueCount();
      boolean active = sCount > 0 && vCount > 0 && sCount != vCount;

      if(active && !isUpperInclusive()) {
         final SelectionValue vLastValue = vlist.getSelectionValue(vCount - 1);
         final SelectionValue sLastValue = slist.getSelectionValue(sCount - 1);

         if(vLastValue instanceof SelectionValue.UpperExclusiveEndValue &&
            !(sLastValue instanceof SelectionValue.UpperExclusiveEndValue))
         {
            active = sCount != vCount - 1;
         }
      }

      return active;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ConditionList getConditionList() {
      TimeInfo info = getTimeInfo();

      if(info instanceof SingleTimeInfo) {
         return getConditionList(
            new DataRef[] { ((SingleTimeInfo) info).getDataRef() });
      }
      else {
         return getConditionList(((CompositeTimeInfo) info).getDataRefs());
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ConditionList getConditionList(DataRef[] dataRefs) {
      return getConditionList(dataRefs, false);
   }

   public ConditionList getConditionList(DataRef[] dataRefs, boolean shareFilter) {
      if(!isEnabled() || !hasActiveSelection() && !shareFilter) {
         return null;
      }

      // If refs has a null, defer to older condition list strategy.
      if(Arrays.stream(dataRefs).anyMatch(Objects::isNull)) {
         return getCompositeConditionList(dataRefs);
      }
      else {
         return RangeCondition.from(this, dataRefs).createConditionList();
      }
   }

   /**
    * Parse the date string.
    * @param unit the specified unit.
    * @return the parsed date value.
    */
   private static Date parseDate(String val, int unit) {
      try {
         if(unit == TimeInfo.YEAR) {
            return VALUE_YEAR_FORMAT.get().parse(val);
         }
         else if(unit == TimeInfo.MONTH) {
            return VALUE_MONTH_FORMAT.get().parse(val);
         }
         else if(unit == TimeInfo.DAY) {
            return VALUE_DAY_FORMAT.get().parse(val);
         }
         else if(unit == TimeInfo.HOUR) {
            return VALUE_HOUR_FORMAT.get().parse(val);
         }
         else if(unit == TimeInfo.MINUTE) {
            return VALUE_MINUTE_FORMAT.get().parse(val);
         }
         else if(unit == TimeInfo.HOUR_OF_DAY) {
            return VALUE_HOUR_OF_DAY_FORMAT.get().parse(val);
         }
         else if(unit == TimeInfo.MINUTE_OF_DAY) {
            return VALUE_MINUTE_OF_DAY_FORMAT.get().parse(val);
         }
      }
      catch(Exception ex) {
         LOG.debug("Failed to parse date: " + val, ex);
      }

      return null;
   }

   /**
    * Parse a string into number.
    */
   private Double parseNumber(String val) {
      try {
         return Double.valueOf(val);
      }
      catch(Exception ex) {
         LOG.debug("Failed to parse number: " + val, ex);
      }

      return null;
   }

   /**
    * Get the condition list for composite time info.
    * @return the condition list.
    */
   private ConditionList getCompositeConditionList(DataRef[] refs) {
      final SelectionList slist = getStateSelectionList();
      final List<ConditionList> list = new ArrayList<>();

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         SelectionValue sval = slist.getSelectionValue(i);

         if(!sval.isSelected()) {
            continue;
         }

         String[] arr = Tool.split(sval.getValue(), "::", false);

         if(arr.length != refs.length) {
            if(refs.length > 1) {
               continue;
            }

            // in case that yyyy-mm-dd string is used directly
            arr = new String[] {sval.getValue()};
         }

         final ConditionList conds = new ConditionList();

         for(int j = 0; j < arr.length; j++) {
            if(j < arr.length - 1) {
               // @by billh, a hack, if ':' is the first char, it's most likely
               // to be last char of the previous value, not the current value
               if(arr[j + 1] != null && arr[j + 1].startsWith(":")) {
                  arr[j] += ":";
                  arr[j + 1] = arr[j + 1].substring(1);
               }
            }

            if(refs[j] != null) {
               if(conds.getSize() > 0) {
                  conds.append(new JunctionOperator(JunctionOperator.AND, 0));
               }

               Condition cond = new Condition(refs[j].getDataType());

               if(arr[j] == null || Tool.NULL.equals(arr[j])) {
                  cond.setOperation(Condition.NULL);
               }
               else {
                  cond.setOperation(Condition.EQUAL_TO);
                  cond.addValue(arr[j]);
               }

               conds.append(new ConditionItem(refs[j], cond, 0));
            }
         }

         list.add(conds);
      }

      return VSUtil.mergeConditionList(list, JunctionOperator.OR);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DataRef[] getDataRefs() {
      return getTimeSliderInfo().getDataRefs();
   }

   /**
    * Reset (clear) the selection.
    * @return true if changed.
    */
   @Override
   public boolean resetSelection() {
      SelectionList vlist = getSelectionList();

      if(vlist == null) {
         return false;
      }

      if(getTableName() == null) {
         setSelectionList(null);
         setStateSelectionList(null);
         return true;
      }

      boolean changed = slist != null &&
         slist.getSelectionValueCount() < vlist.getSelectionValueCount();

      for(int i = 0; i < vlist.getSelectionValueCount(); i++) {
         vlist.getSelectionValue(i).setState(SelectionValue.STATE_SELECTED);
      }

      slist = (SelectionList) vlist.clone();
      setStateSelectionList(slist);
      setLengthValue(slist.getSelectionValueCount() - 1);
      updateSharedBounds();

      return changed;
   }

   /**
    * Get the selection list.
    * @return the selection list.
    */
   @Override
   public SelectionList getSelectionList() {
      return getTimeSliderInfo().getSelectionList();
   }

   /**
    * Set the selection list.
    * @param list the selection list.
    */
   @Override
   public void setSelectionList(SelectionList list) {
      getTimeSliderInfo().setSelectionList(list);
      runtimeMin = runtimeMax = null;
   }

   /**
    * Get the state selection list.
    * @return the selection list.
    */
   @Override
   public SelectionList getStateSelectionList() {
      return slist;
   }

   /**
    * Set the state selection list.
    * @param list the selection list.
    * @return the change hint.
    */
   @Override
   public int setStateSelectionList(SelectionList list) {
      if(Tool.equals(slist, list)) {
         return NONE_CHANGED;
      }

      this.slist = list;
      SelectionList vlist = getSelectionList();

      // keep the state of selection list in sync
      if(vlist != null) {
         for(int i = 0; i < vlist.getSelectionValueCount(); i++) {
            SelectionValue val = vlist.getSelectionValue(i);
            val.setState(0);

            for(int j = 0; j < slist.getSelectionValueCount(); j++) {
               if(val.equalsValue(slist.getSelectionValue(j))) {
                  if(!(val instanceof SelectionValue.UpperExclusiveEndValue)) {
                     val.setState(SelectionValue.STATE_SELECTED);
                  }

                  break;
               }
            }
         }
      }

      return OUTPUT_DATA_CHANGED;
   }

   /**
    * Get the runtime length of current button.
    */
   public int getLength() {
      return getTimeInfo().getLength();
   }

   /**
    * Get the design time length of current button.
    */
   public int getLengthValue() {
      return getTimeInfo().getLengthValue();
   }

   /**
    * Set the design time length of current button.
    */
   public void setLengthValue(int length) {
      getTimeInfo().setLengthValue(length);
   }

   /**
    * Check whether the upper bound is inclusive (less than or equal to)
    * at runtime.
    */
   public boolean isUpperInclusive() {
      return getTimeSliderInfo().isUpperInclusive();
   }

   /**
    * Check whether the upper bound is inclusive (less than or equal to)
    * at design time.
    */
   public boolean getUpperInclusiveValue() {
      return getTimeSliderInfo().getUpperInclusiveValue();
   }

   /**
    * Set whether the upper bound is inclusive (less than or equal to)
    * at design time.
    */
   public void setUpperInclusiveValue(boolean upperInclusive) {
      getTimeSliderInfo().setUpperInclusiveValue(upperInclusive);
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);
      SelectionList slist = getStateSelectionList();

      if(slist != null) {
         writer.println("<state_selectionList>");
         slist.writeXML(writer);
         writer.println("</state_selectionList>");
      }

      int length = getLengthValue();
      SelectionList vlist = getSelectionList();
      boolean all = vlist == null || length >= vlist.getSelectionValueCount() - 1;

      writer.println("<state_length length=\"" + length +
                     "\" full=\"" + all + "\"/>");
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseStateContent(Element elem, boolean runtime)
      throws Exception
   {
      super.parseStateContent(elem, runtime);

      Element snode = Tool.getChildNodeByTagName(elem, "state_selectionList");

      if(snode != null) {
         snode = Tool.getFirstChildNode(snode);
         SelectionList slist = new SelectionList();
         slist.parseXML(snode);
         setStateSelectionList(slist);
      }
      else {
         setStateSelectionList(null);
      }

      Element lnode = Tool.getChildNodeByTagName(elem, "state_length");

      if(lnode != null) {
          String lstr = Tool.getAttribute(lnode, "length");
          String astr = Tool.getAttribute(lnode, "full");
          int length = Integer.parseInt(lstr);
          boolean all = "true".equals(astr);

          // if the state is saved with the slider selecting the full range,
          // it's equivalent to no selection, so we restore to the same full
          // range state
          if(all && getSelectionList() != null) {
             length = getSelectionList().getSelectionValueCount() - 1;
             setStateSelectionList(getSelectionList());
          }

          setLengthValue(length);
      }
   }

   /**
    * Get the selected minimum date.
    * @return the selected minimum date.
    */
   public Object getSelectedMin() {
      SelectionValue sval = getMinSelectionValue();

      if(sval == null) {
         return null;
      }

      TimeInfo info = getTimeInfo();

      if(info instanceof SingleTimeInfo) {
         return getSingleSelectedValue(sval);
      }
      else {
         return getCompositeSelectedValue(sval);
      }
   }

   /**
    * Get minimum selection value.
    */
   public SelectionValue getMinSelectionValue() {
      if(slist == null) {
         return null;
      }

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         SelectionValue value = slist.getSelectionValue(i);

         if(value.isSelected()) {
            return value;
         }
      }

      return null;
   }

   /**
    * Get the single selected minimum value.
    * @param val the minimum selection value.
    * @return the single selected minimum value.
    */
   private Object getSingleSelectedValue(SelectionValue val) {
      int unit = ((SingleTimeInfo) getTimeInfo()).getRangeType();

      if(unit == TimeInfo.MEMBER) {
         return getCompositeSelectedValue(val);
      }

      return unit == TimeInfo.NUMBER ? parseNumber(val.getValue()) :
         parseDate(val.getValue(), unit);
   }

   /**
    * Get the composite selected minimum value.
    * @param val the minimum selection value.
    * @return the composite selected minimum value.
    */
   private Object getCompositeSelectedValue(SelectionValue val) {
      return val.getValue();
   }

   /**
    * Get the selected maximum date.
    * @return the selected maximum date.
    */
   public Object getSelectedMax() {
      SelectionValue sval = getMaxSelectionValue();

      if(sval == null) {
         return null;
      }

      TimeInfo info = getTimeInfo();

      if(info instanceof SingleTimeInfo) {
         return getSingleSelectedMax(sval, false);
      }
      else {
         return getCompositeSelectedValue(sval);
      }
   }

   /**
    * Get maximum selection value.
    */
   public SelectionValue getMaxSelectionValue() {
      if(slist == null) {
         return null;
      }

      for(int i = slist.getSelectionValueCount() - 1; i >= 0; i--) {
         SelectionValue value = slist.getSelectionValue(i);

         if(value.isSelected() && !(value instanceof SelectionValue.UpperExclusiveEndValue)) {
            return value;
         }
      }

      return null;
   }

   /**
    * Get the single selected maximum value.
    * @param val the maximum selection value.
    * @return the single selected maximum value.
    */
   private Object getSingleSelectedMax(SelectionValue val, boolean lessThan) {
      SingleTimeInfo tinfo = (SingleTimeInfo) getTimeInfo();
      int unit = tinfo.getRangeType();

      if(unit == TimeInfo.MEMBER) {
         return getCompositeSelectedValue(val);
      }

      boolean isdate = unit == TimeInfo.YEAR || unit == TimeInfo.MONTH ||
         unit == TimeInfo.DAY || unit == TimeInfo.HOUR ||
         unit == TimeInfo.MINUTE || unit == TimeInfo.HOUR_OF_DAY ||
         unit == TimeInfo.MINUTE_OF_DAY;
      Object maxd = val.getValue();

      if(isdate) {
         maxd = parseDate(val.getValue(), unit);
      }
      else if(unit == TimeInfo.NUMBER) {
         maxd = parseNumber(val.getValue());
      }

      if(maxd == null) {
         return null;
      }

      if(isdate && isUpperInclusive()) {
         Calendar calendar = CoreTool.calendar.get();
         calendar.setTime((Date) maxd);

         if(tinfo.getRangeType() == TimeInfo.MONTH) {
            calendar.add(Calendar.MONTH, 1);
         }
         else if(tinfo.getRangeType() == TimeInfo.YEAR) {
            calendar.add(Calendar.YEAR, 1);
         }
         else if(tinfo.getRangeType() == TimeInfo.DAY) {
            calendar.add(Calendar.DATE, 1);
         }
         else if(tinfo.getRangeType() == TimeInfo.HOUR) {
            calendar.add(Calendar.HOUR_OF_DAY, 1);
         }
         else if(tinfo.getRangeType() == TimeInfo.MINUTE) {
            calendar.add(Calendar.MINUTE, 1);
         }
         else if(tinfo.getRangeType() == TimeInfo.HOUR_OF_DAY) {
            calendar.add(Calendar.HOUR_OF_DAY, 1);
         }
         else if(tinfo.getRangeType() == TimeInfo.MINUTE_OF_DAY) {
            calendar.add(Calendar.MINUTE, 1);
         }

         if(!lessThan) {
            calendar.add(Calendar.MILLISECOND, -1);
         }

         maxd = calendar.getTime();
      }

      return maxd;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public TimeSliderVSAssembly clone() {
      try {
         TimeSliderVSAssembly assembly2 = (TimeSliderVSAssembly) super.clone();

         if(slist != null) {
            assembly2.slist = (SelectionList) slist.clone();
         }

         return assembly2;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone timeSliderVSAssembly", ex);
      }

      return null;
   }

   /**
    * Copy the state selection from a selection viewsheet assembly.
    * @param assembly the specified selection viewsheet assembly.
    * @return the changed hint.
    */
   @Override
   public int copyStateSelection(SelectionVSAssembly assembly) {
      // both length and selection might be changed at runtime
      TimeSliderVSAssembly sassembly = (TimeSliderVSAssembly) assembly;
      TimeInfo info2 = sassembly.getTimeInfo();
      setLengthValue(info2.getLengthValue());
      return setStateSelectionList(sassembly.getStateSelectionList());
   }

   /**
    * Get display value.
    * @param onlyList only get the selected values, not include title,
    * and not restrict by visible properties.
    * @return the string to represent the selected value.
    */
   @Override
   public String getDisplayValue(boolean onlyList) {
      if(!isEnabled() && onlyList) {
         return null;
      }

      StringBuilder sb = new StringBuilder();

      if((isCurrentVisible() || onlyList) && getSelectionList() != null) {
         int start = getCurrentPos() < 0 ? 0 : getCurrentPos();
         int max = getTotalLength();

         if(max < 1 || (onlyList && start == 0 && getTimeInfo().getLength() >= max - 1)) {
            return null;
         }

         SelectionValue[] values = getSelectionList().getSelectionValues();

         if(max == 1) {
            return values[0].getLabel();
         }

         sb.append(values[start].getLabel());

         int end = start + getTimeInfo().getLength() + 1;
         end = end > max ? max : end;
         String conn = isUpperInclusive() ? ".." : "->";

         sb.append(conn).append(values[end - 1].getLabel());
         return sb.toString();
      }

      return null;
   }

   /**
    * Check if contains selection in this selection viewsheet assembly.
    * @return <tt>true</tt> if contains selection, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean containsSelection() {
      if(!isEnabled()) {
         return false;
      }

      SelectionList slist = getStateSelectionList();
      SelectionList vlist = getSelectionList();

      return slist != null && vlist != null &&
         slist.getSelectionValueCount() != vlist.getSelectionValueCount();
   }

   /**
    * Set default format.
    */
   public void setDefaultFormat(Format dfmt) {
      this.dfmt = dfmt;
   }

   /**
    * Get default format.
    */
   public Format getDefaultFormat() {
      return dfmt;
   }

   @Override
   public void removeBindingCol(String ref) {
      TimeInfo tinfo = getTimeInfo();

      if(tinfo instanceof CompositeTimeInfo) {
         CompositeTimeInfo cinfo = (CompositeTimeInfo) tinfo;
         DataRef[] refs = cinfo.getDataRefs();

         for(int i = refs.length - 1; i >= 0; i--) {
            if(Tool.equals(refs[i].getName(), ref)) {
               refs = VSUtil.removeRow(refs, i);
               cinfo.setDataRefs(refs);
            }
         }
      }
      else if(tinfo instanceof SingleTimeInfo) {
         SingleTimeInfo sinInfo = (SingleTimeInfo) tinfo;
         sinInfo.getDataRef().getName();

         if(ref.equals(sinInfo.getDataRef().getName())) {
            setTableName(null);
         }
      }
      else {
         // it only binding to to one col data, clear the col means clear table?
         setTableName(null);
      }
   }

   @Override
   public void renameBindingCol(String oname, String nname) {
      TimeInfo tinfo = getTimeInfo();

      if(tinfo instanceof CompositeTimeInfo) {
         CompositeTimeInfo cinfo = (CompositeTimeInfo) tinfo;
         DataRef[] refs = cinfo.getDataRefs();

         if(refs != null) {
            for(int i = refs.length - 1; i >= 0; i--) {
               if(Tool.equals(refs[i].getName(), oname)) {
                  VSUtil.renameDataRef(refs[i], nname);
               }
            }
         }
      }
      else if(tinfo instanceof SingleTimeInfo) {
         DataRef ref = ((SingleTimeInfo) tinfo).getDataRef();

         if(ref != null && Tool.equals(ref.getName(), oname)) {
            VSUtil.renameDataRef(ref, nname);
         }
      }
   }

   /**
    * @return the split selected min values.
    */
   public Object[] getSplitSelectedMinValues() {
      final Object[] splitSelectedMinValues;

      if(getTimeInfo() instanceof CompositeTimeInfo) {
         splitSelectedMinValues = getSplitCompositeValues(getMinSelectionValue());
      }
      else {
         splitSelectedMinValues = new Object[] {getSelectedMin()};
      }

      return splitSelectedMinValues;
   }

   public Object[] getSplitSelectedValues(SelectionValue value) {
      final Object[] splitSelectedValues;

      if(getTimeInfo() instanceof CompositeTimeInfo) {
         splitSelectedValues = getSplitCompositeValues(value);
      }
      else {
         splitSelectedValues = new Object[] {getSingleSelectedValue(value)};
      }

      return splitSelectedValues;
   }

   /**
    * @return the split selected max values.
    */
   public Object[] getSplitSelectedMaxValues() {
      final Object[] splitSelectedMaxValues;

      if(getTimeInfo() instanceof CompositeTimeInfo) {
         splitSelectedMaxValues = getSplitCompositeValues(getMaxSelectionValue());
      }
      else {
         splitSelectedMaxValues = new Object[] {getSelectedMax()};
      }

      return splitSelectedMaxValues;
   }

   /**
    * @param value the value to split.
    *
    * @return the split values array.
    */
   private String[] getSplitValues(SelectionValue value) {
      return value == null ? new String[0] : Tool.split(value.getValue(), "::", false);
   }

   /**
    * @param value the selection value to get the split the composite values of
    *
    * @return the split parsed composite values.
    */
   private Object[] getSplitCompositeValues(SelectionValue value) {
      final String[] splitValues = getSplitValues(value);
      final Object[] parsedValues = new Object[splitValues.length];
      final DataRef[] refs = getDataRefs();

      for(int i = 0; i < splitValues.length && i < refs.length; i++) {
         final String val = splitValues[i];
         final DataRef ref = refs[i];
         final String dtype = ref.getDataType();

         if((ref.getRefType() & DataRef.CUBE_DIMENSION) == DataRef.CUBE_DIMENSION) {
            parsedValues[i] = val;
         }
         else if(val == null || Tool.FAKE_NULL.equals(val)) {
            parsedValues[i] = null;
         }
         else {
            parsedValues[i] = AbstractCondition.getObject(dtype, val);
         }
      }

      return parsedValues;
   }

   /**
    * @param other the time slider to get the selection intersection of.
    *
    * @return a selection list that contains the intersection with the selection of the other time
    * slider, or null if none exists.
    */
   public SelectionList getSelectionIntersection(TimeSliderVSAssembly other) {
      updateSharedBounds(other);

      if(sharedBounds == null) {
         return null;
      }

      final SelectionList selectionList = getSelectionList();

      if(!sharedBounds.isActive()) {
         return selectionList;
      }

      final int leftIdx = getLeftIntersectionIndex();
      final int rightIdx = getRightIntersectionIndex();
      final int start = Math.max(0, leftIdx);
      final int end = Math.min(selectionList.getSelectionValueCount() - 1, rightIdx);

      if(start == end && isUpperInclusive() ||
         leftIdx < 0 && rightIdx >= selectionList.getSelectionValueCount())
      {
         return selectionList;
      }

      final SelectionList intersectionList = new SelectionList();

      for(int i = start; i <= end; i++) {
         final SelectionValue selectionValue = selectionList.getSelectionValue(i);

         if(!(selectionValue instanceof SelectionValue.UpperExclusiveEndValue)) {
            intersectionList.addSelectionValue(selectionValue);
         }
      }

      return intersectionList;
   }

   private int getLeftIntersectionIndex() {
      final SelectionList selectionList = getSelectionList();
      final int length = selectionList.getSelectionValueCount();
      int leftIntersection = -1;

      int left = 0;
      int right = length - 1;
      int mid;

      while(left <= right) {
         mid = left + (right - left) / 2;

         final SelectionValue selectionValue = selectionList.getSelectionValue(mid);
         Object[] values = getSplitSelectedValues(selectionValue);
         final int comparison = compare(values, sharedBounds.getRawMin());

         if(comparison == 0) {
            leftIntersection = mid;
            right = mid - 1;
         }
         else if(comparison < 0) {
            left = mid + 1;

            if(left < length) {
               final SelectionValue selectionValue2 = selectionList.getSelectionValue(mid + 1);
               Object[] values2 = getSplitSelectedValues(selectionValue2);

               if(compare(values2, sharedBounds.getRawMin()) > 0) {
                  leftIntersection = mid + 1;
               }
            }
         }
         else {
            right = mid - 1;
         }
      }

      return leftIntersection;
   }

   private int getRightIntersectionIndex() {
      final SelectionList selectionList = getSelectionList();
      final int length = selectionList.getSelectionValueCount();
      int rightIntersection = length;

      int left = 0;
      int right = length - 1;
      int mid;

      while(left <= right) {
         mid = left + (right - left) / 2;

         final SelectionValue selectionValue = selectionList.getSelectionValue(mid);
         Object[] values = getSplitSelectedValues(selectionValue);
         final int comparison = compare(values, sharedBounds.getRawMax());

         if(comparison == 0) {
            rightIntersection = mid;
            right = mid - 1;
         }
         else if(comparison < 0) {
            left = mid + 1;

            if(left < length) {
               final SelectionValue selectionValue2 = selectionList.getSelectionValue(mid + 1);
               Object[] values2 = getSplitSelectedValues(selectionValue2);

               if(compare(values2, sharedBounds.getRawMax()) > 0) {
                  rightIntersection = mid;
               }
            }
         }
         else {
            right = mid - 1;
         }
      }

      return rightIntersection;
   }

   private int compare(Object[] values, List<Object> rawValues) {
      for(int i = 0; i < values.length && i < rawValues.size(); i++) {
         final Object value = values[i];
         final Object rawValue = rawValues.get(i);

         final int comparison = Objects.compare(value, rawValue, Tool::compare);

         if(comparison != 0) {
            return comparison;
         }
      }

      return 0;
   }

   public void updateSharedBounds() {
      sharedBounds = SharedFilterBounds.from(this);
   }

   private void updateSharedBounds(TimeSliderVSAssembly source) {
      if(source.sharedBounds != null) {
         sharedBounds = source.sharedBounds.createSharedBounds(this);
      }
      else {
         sharedBounds = null;
      }
   }

   public Object getRuntimeMin() {
      return runtimeMin;
   }

   public void setRuntimeMin(Object runtimeMin) {
      this.runtimeMin = runtimeMin;
   }

   public Object getRuntimeMax() {
      return runtimeMax;
   }

   public void setRuntimeMax(Object runtimeMax) {
      this.runtimeMax = runtimeMax;
   }

   /**
    * Represents the underlying "raw value" of a shared filter. It is a result of a user interaction
    * with a time slider.
    */
   private static class SharedFilterBounds implements Cloneable {
      public SharedFilterBounds(List<Object> rawMin,
                                List<Object> rawMax,
                                List<String> dataTypes,
                                boolean upperInclusive,
                                boolean active)
      {
         this.rawMin = rawMin;
         this.rawMax = rawMax;
         this.dataTypes = dataTypes;
         this.matchingRefCount = dataTypes.size();
         this.upperInclusive = upperInclusive;
         this.active = active;
      }

      public static SharedFilterBounds from(TimeSliderVSAssembly assembly) {
         final boolean upperInclusive = assembly.isUpperInclusive();
         final boolean active = assembly.hasActiveSelection();
         final List<String> dataTypes = Arrays.stream(assembly.getDataRefs())
            .map(DataRef::getDataType)
            .collect(Collectors.toList());

         return new SharedFilterBounds(Arrays.asList(assembly.getSplitSelectedMinValues()),
                                       Arrays.asList(assembly.getSplitSelectedMaxValues()),
                                       dataTypes, upperInclusive, active);
      }

      public SharedFilterBounds createSharedBounds(TimeSliderVSAssembly assembly) {
         final SharedFilterBounds bounds =
            new SharedFilterBounds(rawMin, rawMax, dataTypes, upperInclusive, active);
         final DataRef[] refs = assembly.getDataRefs();
         bounds.matchingRefCount = 0;

         for(int i = 0; i < dataTypes.size() && i < refs.length; i++) {
            if(!AssetUtil.isMergeable(dataTypes.get(i), refs[i].getDataType())) {
               break;
            }

            bounds.matchingRefCount++;
         }

         return bounds;
      }

      public List<Object> getRawMin() {
         return rawMin.subList(0, matchingRefCount);
      }

      public List<Object> getRawMax() {
         return rawMax.subList(0, matchingRefCount);
      }

      public boolean isUpperInclusive() {
         return upperInclusive;
      }

      public boolean isActive() {
         return active;
      }

      private int matchingRefCount;
      private final List<Object> rawMin;
      private final List<Object> rawMax;
      private final List<String> dataTypes;
      private final boolean upperInclusive;
      private final boolean active;
   }

   // output data
   private SelectionList slist;
   private SharedFilterBounds sharedBounds;
   private transient Format dfmt = null;

   // script set values
   private transient Object runtimeMin, runtimeMax;

   private static final Logger LOG = LoggerFactory.getLogger(TimeSliderVSAssembly.class);
}

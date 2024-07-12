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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.data.BoxDataSet;
import inetsoft.graph.internal.DimensionD;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.report.internal.graph.ChangeChartTypeProcessor;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.*;
import inetsoft.util.css.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ChartVSAssemblyInfo, the assembly info of a chart assembly.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ChartVSAssemblyInfo extends DataVSAssemblyInfo
   implements TipVSAssemblyInfo, ContentObject, TitledVSAssemblyInfo, DateCompareAbleAssemblyInfo
{
   /**
    * Constructor.
    */
   public ChartVSAssemblyInfo() {
      super();

      tipOptionValue = new DynamicValue2(TOOLTIP_OPTION + "", XSchema.INTEGER);
      flyClickValue = new DynamicValue("false", XSchema.BOOLEAN);

      summarySortCol = new DynamicValue2("-1", XSchema.INTEGER);
      summarySortVal = new DynamicValue2("0", XSchema.INTEGER);

      setPixelSize(new Dimension(400, 240));
      cinfo = new DefaultVSChartInfo();
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      setDefaultFormat(true, true, true);
   }

   @Override
   protected void setDefaultFormat(boolean border, boolean setFormat, boolean fill) {
      setPadding(new Insets(10, 10, 10, 10));
      super.setDefaultFormat(border, setFormat, fill);
      getFormat().getDefaultFormat().setBackgroundValue("#ffffff");

      VSCompositeFormat tFormat = new VSCompositeFormat();
      tFormat.getCSSFormat().setCSSType(getObjCSSType() + CSSConstants.TITLE);
      tFormat.getDefaultFormat().setFontValue(getDefaultFont(Font.BOLD, 11));
      tFormat.getDefaultFormat().setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_CENTER);
      getFormatInfo().setFormat(TITLEPATH, tFormat);
   }

   @Override
   public void clearBinding() {
      super.clearBinding();
      cinfo = new DefaultVSChartInfo();
   }

   /**
    * Set brushing selections.
    * @param bselection the specified brushing selections.
    */
   public int setBrushSelection(VSSelection bselection) {
      if(!Tool.equals(this.bselection, bselection)) {
         this.bselection = bselection;
         return VSAssembly.INPUT_DATA_CHANGED | VSAssembly.OUTPUT_DATA_CHANGED;
      }

      return 0;
   }

   /**
    * Get brushing selections.
    * @return the brushing selections.
    */
   public VSSelection getBrushSelection() {
      return bselection;
   }

   /**
    * Get the brush condition list.
    * @param expothers true to expand 'Others' into individual items.
    */
   public ConditionList getBrushConditionList(ColumnSelection cols, boolean expothers) {
      VSSelection selection = getBrushSelection();

      if(!expothers && selection != null) {
         VSSelection parent = selection;
         selection = selection.getOrigSelection();
         // bc
         selection = selection == null ? parent : selection;
      }

      ConditionList conds = getConditionList(selection, cols);

      // for backward compatibility
      if(conds == null && selection != null) {
         if(!selection.isEmpty()) {
            LOG.info("Discard brush for backward compatibility");
         }

         setBrushSelection(null);
      }

      return conds;
   }

   /**
    * Set zoom selections.
    */
   public int setZoomSelection(VSSelection zselection) {
      if(!Tool.equals(this.zselection, zselection)) {
         this.zselection = zselection;
         return VSAssembly.INPUT_DATA_CHANGED;
      }

      return 0;
   }

   /**
    * Get zoom selections.
    */
   public VSSelection getZoomSelection() {
      return zselection;
   }

   /**
    * Set exclude selections.
    */
   public int setExcludeSelection(VSSelection xselection) {
      if(!Tool.equals(this.xselection, xselection)) {
         this.xselection = xselection;
         return VSAssembly.INPUT_DATA_CHANGED;
      }

      return 0;
   }

   /**
    * Get exclude selections.
    */
   public VSSelection getExcludeSelection() {
      return xselection;
   }

   /**
    * Get the zoom condition list.
    */
   public ConditionList getZoomConditionList(ColumnSelection cols) {
      VSSelection zselection = getZoomSelection();
      VSSelection xselection = getExcludeSelection();
      ConditionList zconds = getConditionList(zselection, cols);
      ConditionList xconds = getConditionList(xselection, cols);

      if(zconds == null) {
         zconds = new ConditionList();
      }

      if(xconds != null && !xconds.isEmpty()) {
         xconds = ConditionUtil.negate(xconds);
      }

      if(!zconds.isEmpty() && xconds != null && !xconds.isEmpty()) {
         // need ident?
         if(zconds.getSize() > 1) {
            zconds.indent(1);
         }

         // need indent?
         if(xconds.getSize() > 1) {
            xconds.indent(1);
         }

         zconds.append(new JunctionOperator(JunctionOperator.AND, 0));

         for(int i = 0; i < xconds.getSize(); i++) {
            zconds.append(xconds.getItem(i));
         }
      }
      else if(zconds.isEmpty()) {
         zconds = xconds;
      }

      if(zconds != null) {
         zconds.validate();
      }

      return zconds == null || zconds.isEmpty() ? null : zconds;
   }

   /**
    * Get the condition list from a selection.
    * @param selection the specified selection.
    * @param cols the specified column selection.
    */
   public ConditionList getConditionList(VSSelection selection, ColumnSelection cols) {
      if(selection == null || selection.isEmpty() || cinfo == null) {
         return null;
      }

      // multi aesthetic, ignore performance enhance, otherwise will
      // cause different result with same selection but different selection
      // AssetUtil.filter is not enhanced well.
      // also ignore for treemap since the relationship between different layers (e.g. sunburst)
      // need to be reflected in condition.
      if(cinfo.isMultiStyles() || GraphTypes.isTreemap(cinfo.getChartType())) {
         return createNoneSelectionCondition(selection, cols);
      }

      int range = selection.getRange();

      synchronized(cinfo) {
         switch(range) {
         case VSSelection.NONE_RANGE:
            return createNoneSelectionCondition(selection, cols);
         case VSSelection.PHYSICAL_RANGE:
            return createPhysicalRangeCondition(selection, cols);
         default:
            return createLogicalRangeCondition(selection, cols);
         }
      }
   }

   /**
    * Check if selection across multi measures.
    */
   private boolean selectionCrossMeasures(VSSelection selection) {
      Set<String> ofields = null;

      for(int i = 0; i < selection.getPointCount(); i++) {
         VSPoint point = selection.getPoint(i);

         if(point == null) {
            continue;
         }

         Set<String> fields = new HashSet<>();

         for(int j = 0; j < point.getValueCount(); j++) {
            VSFieldValue pair = point.getValue(j);

            if(pair == null) {
               continue;
            }

            String field = pair.getFieldName();
            fields.add(field);
         }

         if(fields.size() <= 0) {
            continue;
         }

         if(ofields == null) {
            ofields = fields;
            continue;
         }

         if(!ofields.equals(fields)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Create condition list for logical range selection.
    * @param selection the specified selection.
    * @param cols the specified column selection.
    */
   private ConditionList createNoneSelectionCondition(VSSelection selection, ColumnSelection cols) {
      if(selection == null || selection.isEmpty()) {
         return null;
      }

      List<ConditionList> list = new ArrayList<>();

      for(int i = 0; i < selection.getPointCount(); i++) {
         VSPoint point = selection.getPoint(i);
         ConditionList conds = new ConditionList();

         for(int j = 0; j < point.getValueCount(); j++) {
            VSFieldValue pair = point.getValue(j);
            ConditionItem citem = createCondition(pair, 0, cols);

            if(citem == null) {
               continue;
            }

            if(conds.getSize() > 0) {
               JunctionOperator op = new JunctionOperator(JunctionOperator.AND, 0);
               conds.append(op);
            }

            conds.append(citem);
         }

         if(conds.getSize() > 0) {
            list.add(conds);
         }
      }

      if(list.size() == 0) {
         return null;
      }

      return ConditionUtil.mergeConditionList(list, JunctionOperator.OR);
   }

   /**
    * Create condition list for physical range, here use tree structure to
    * build efficient condition list, here is a sample illustrates the
    * transformation:
    * (a && b && c) || (a && b && d) || (a && e && f)
    * = a && ((b && [c..d]) || (e && f))
    * @param selection the specified selection.
    * @param cols the specified column selection.
    */
   private ConditionList createPhysicalRangeCondition0(VSSelection selection,
                                                       ColumnSelection cols)
   {
      if(selection == null || selection.isEmpty()) {
         return null;
      }

      List<String> dims = getDimensionName();
      XNode root = new XNode("root");
      XNode parent;

      for(int i = 0; i < selection.getPointCount(); i++) {
         parent = root;
         VSPoint point = selection.getPoint(i);

         if(point == null) {
            continue;
         }

         for(int j = 0; j < point.getValueCount(); j++) {
            VSFieldValue pair = point.getValue(j);

            if(pair == null) {
               continue;
            }

            XNode node = new XNode(i + "-" + j);
            node.setValue(pair);
            // here depend on the selection structure, it must be
            // composed from outer dimension to inner dimension
            parent = insertNode(parent, node, dims);
         }
      }

      ConditionList conds = new ConditionList();
      buildCondition(root, conds, cols, 1);
      conds = ConditionUtil.shrinkCondition(conds);
      return conds;
   }

   /**
    * Convert tree to condition.
    */
   private void buildCondition(XNode node, ConditionList conds,
                               ColumnSelection cols, int level) {
      if(level > 1 && node.getChildCount() > 0) {
         JunctionOperator op = new JunctionOperator(
            JunctionOperator.AND, level - 1);
         conds.append(op);
      }

      // when all child without child, we can use one of
      boolean useOneOf = true;
      Map<String, Set<Object>> f2vals = new HashMap<>();
      Map<String, ConditionItem> f2conds = new HashMap<>();
      Set<String> failed = new HashSet<>();

      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);

         if(child.getChildCount() > 0) {
            useOneOf = false;
            break;
         }

         VSFieldValue pair = (VSFieldValue) child.getValue();
         String field = pair.getFieldName();
         ConditionItem citem = createCondition(pair, level, cols);
         Condition cond = citem.getCondition();

         // for null, we could not convert it to in
         if(cond.getOperation() == XCondition.NULL) {
            failed.add(field);
         }

         f2conds.putIfAbsent(field, citem);
         Set<Object> vals = f2vals.computeIfAbsent(field, k -> new HashSet<>());
         Object val = cond.getValue(0);
         vals.add(val);
      }

      Set<String> processed = new HashSet<>();

      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);
         VSFieldValue pair = (VSFieldValue) child.getValue();
         String field = pair.getFieldName();
         Set<Object> vals = f2vals.get(field);
         ConditionItem oneOfItem = f2conds.get(field);
         boolean oneOf = useOneOf && !failed.contains(field) && vals != null &&
            vals.size() > 1 && oneOfItem != null;

         if(oneOf && processed.contains(field)) {
            continue;
         }

         if(i > 0) {
            JunctionOperator op = new JunctionOperator(
               JunctionOperator.OR, level - 1);
            conds.append(op);
         }

         if(oneOf) {
            processed.add(field);
            Condition ncond = oneOfItem.getCondition();
            ncond.setOperation(XCondition.ONE_OF);
            ncond.removeAllValues();

            for(Object val : vals) {
               ncond.addValue(val);
            }

            conds.append(oneOfItem);
            continue;
         }

         ConditionItem citem = createCondition(pair, level, cols);
         conds.append(citem);
         buildCondition(child, conds, cols, level + 1);
      }
   }

   /**
    * Append a node the parent.
    */
   private XNode insertNode(XNode parent, XNode node, List<String> dims) {
      VSFieldValue pair = (VSFieldValue) node.getValue();
      String field = pair.getFieldName();
      boolean isDim = dims.contains(field);

      for(int i = 0; i < parent.getChildCount(); i++) {
         XNode child = parent.getChild(i);
         VSFieldValue cpair = (VSFieldValue) child.getValue();

         if(Tool.equals(pair, cpair)) {
            return isDim ? child : parent;
         }
      }

      parent.addChild(node);
      return node;
   }

   /**
    * Create dimension name.
    */
   private List<String> getDimensionName() {
      List<String> names = new ArrayList<>();
      VSDataRef[] refs = cinfo.getRTFields();

      for(VSDataRef ref : refs) {
         if(ref instanceof XDimensionRef) {
            String name = ref.getFullName();

            if(!names.contains(name)) {
               names.add(name);
            }
         }
      }

      return names;
   }

   /**
    * Create condition list for physical range.
    * @param selection the specified selection.
    * @param cols the specified column selection.
    */
   private ConditionList createPhysicalRangeCondition(VSSelection selection, ColumnSelection cols) {
      if(selection == null || selection.isEmpty()) {
         return null;
      }

      PRComparator comp = new PRComparator(selection);
      ConditionList conds = new ConditionList();
      selection.sort(comp);
      VSPoint lpoint = null;
      String[] arr = comp.getFields();

      // here is a sample illustrates the transformation:
      // (a && b && c) || (a && b && d) || (a && e && f)
      // = a && ((b && [c..d]) || (e && f))
      for(int i = 0; i < selection.getPointCount(); i++) {
         VSPoint point = selection.getPoint(i);
         int lvl = comp.getGroupLevel(lpoint, point);

         // first row
         if(lpoint == null) {
            for(int j = 0; j < arr.length; j++) {
               VSFieldValue pair = point.getValue(arr[j]);
               ConditionItem citem = createCondition(pair, 0, cols);

               if(citem == null) {
                  continue;
               }

               if(conds.getSize() > 0) {
                  JunctionOperator op = new JunctionOperator(JunctionOperator.AND, (j - 1) * 2 + 1);
                  conds.append(op);
               }

               conds.append(citem);
            }
         }
         // same row
         else if(lvl == arr.length) {
            continue;
         }
         // normal row
         else {
            for(int j = lvl; j < arr.length; j++) {
               VSFieldValue pair = point.getValue(arr[j]);
               ConditionItem citem = createCondition(pair, 0, cols);
               JunctionOperator op;

               if(citem == null) {
                  continue;
               }

               if(j == lvl) {
                  op = new JunctionOperator(JunctionOperator.OR, lvl * 2);
                  conds.append(op);
               }
               else {
                  op = new JunctionOperator(JunctionOperator.AND, (j - 1) * 2 + 1);
                  conds.append(op);
               }

               conds.append(citem);
            }
         }

         lpoint = point;
      }

      // convert or to in if possible
      convertOr2In(conds, (arr.length - 1) * 2);
      // validate condition items
      conds.validate(false);
      return conds.isEmpty() ? null : conds;
   }

   /**
    * Convert or to in.
    */
   private void convertOr2In(ConditionList conds, int level) {
      int end = -1;

      // iterate junction from end to start
      for(int i = conds.getSize() - 2; i >= 0; i -= 2) {
         JunctionOperator op = conds.getJunctionOperator(i);
         int start;

         // same level(last)/op(or)
         if(op.getJunction() == JunctionOperator.OR && op.getLevel() == level) {
            // no end? initialize end
            if(end == -1) {
               end = i;
            }

            // first one? perform replacement
            if(i == 1) {
               start = i;
            }
            // middle one? continue
            else {
               continue;
            }
         }
         // different level/op
         else {
            start = i + 2;
         }

         // perform replacement
         if(start >= 0 && end >= start) {
            ConditionItem first = conds.getConditionItem(start - 1);
            ConditionItem nitem = (ConditionItem) first.clone();
            Condition ncond = nitem.getCondition();
            ncond.setOperation(XCondition.ONE_OF);
            ncond.removeAllValues();
            boolean success = true;

            for(int j = start - 1; j <= end + 1; j += 2) {
               ConditionItem citem = conds.getConditionItem(j);
               Condition cond = citem.getCondition();

               // for null, we could not convert it to in
               if(cond.getOperation() == XCondition.NULL ||
                  // @by yanie: bug1421999533635
                  // Since bug1420530104336, between is used insteadof equal
                  // to compare float values, between cannot be merged to in
                  cond.getOperation() == XCondition.BETWEEN)
               {
                  success = false;
                  break;
               }

               Object val = cond.getValue(0);
               ncond.addValue(val);
            }

            // replace the or-ed items with one in-ed item
            if(success) {
               for(int j = end + 1; j >= start; j--) {
                  conds.remove(j);
               }

               conds.setItem(start - 1, nitem);
            }
         }

         end = -1;
      }
   }

   /**
    * Check if selection is valid in the given column selection.
    */
   public boolean isSelectionValid(VSSelection selection, ColumnSelection cols) {
      if(selection == null) {
         return true;
      }

      for(int i = 0; i < selection.getPointCount(); i++) {
         VSPoint point = selection.getPoint(i);

         for(int j = 0; j < point.getValueCount(); j++) {
            VSFieldValue pair = point.getValue(j);
            DataRef ref = getDataRef(pair);

            if(ref == null) {
               continue;
            }

            // measure?
            if(ref instanceof VSAggregateRef) {
               AggregateRef aggregate =
                  ((VSAggregateRef) ref).createAggregateRef(cols);

               if(aggregate == null || aggregate.getDataRef() == null) {
                  return false;
               }
            }
            // dimension?
            else {
               GroupRef group = ((VSDimensionRef) ref).createGroupRef(cols);

               if(group == null || group.getDataRef() == null) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   /**
    * Create a condition by the specified field value and level.
    */
   private ConditionItem createCondition(VSFieldValue pair, int level, ColumnSelection cols) {
      DataRef ref = getDataRef(pair);

      if(ref == null || (ref instanceof VSChartAggregateRef &&
         ((VSChartAggregateRef) ref).isDiscrete()) )
      {
         return null;
      }

      // measure?
      if(ref instanceof VSAggregateRef) {
         AggregateRef aggregate = ((VSAggregateRef) ref).createAggregateRef(cols);

         if(aggregate == null) {
            return null;
         }

         ref = aggregate.getDataRef();
      }
      // dimension?
      else {
         GroupRef group = ((VSDimensionRef) ref).createGroupRef(cols);

         if(group == null) {
            return null;
         }

         ref = group.getDataRef();
      }

      String dtype = ref.getDataType();

      // handle time column type (49878) (50021).
      if(XSchema.isDateType(dtype) && ref instanceof ColumnRef &&
         ((ColumnRef) ref).getDataRef() instanceof DateRangeRef)
      {
         dtype = ((DateRangeRef) ((ColumnRef) ref).getDataRef()).getOriginalType();
      }

      Object val = pair.getFieldValue().getObject(dtype);

      if(val == null && !pair.isNullValueIsObjectNull()) {
         val = pair.getFieldValue().getValue();
      }

      Object val2 = null;

      // val could be 'Others'
      if(val == null) {
         val = pair.getFieldValue().getValue();

         if(!"Others".equals(val)) {
            val = null;
         }
      }

      if(pair.getFieldValue2() != null) {
         val2 = pair.getFieldValue2().getObject(dtype);
      }

      boolean convertType = true;

      if(XSchema.BOOLEAN.equals(dtype) && pair.isStringData()) {
         val = pair.getFieldValue().getValue();
         convertType = false;
      }

      boolean isModel = (ref.getRefType() & DataRef.CUBE_MODEL_DIMENSION) ==
         DataRef.CUBE_MODEL_DIMENSION;

      if(!isModel && (ref.getRefType() & DataRef.CUBE_DIMENSION) == DataRef.CUBE_DIMENSION) {
         // dimension member with no value is meaningless
         if(val == null) {
            return null;
         }
      }

      Condition cond = new Condition(dtype);
      int operation = val2 != null ? XCondition.BETWEEN : XCondition.EQUAL_TO;
      boolean isfloat = val instanceof Number &&
         ((Number) val).doubleValue() != ((Number) val).longValue();

      if(StringUtils.isEmpty(val)) {
         operation = XCondition.NULL;
      }
      else if(isfloat) {
         operation = XCondition.BETWEEN;

         // use between for float number to account for rounding error
         float moe = (float) 0.0001;

         if(XSchema.FLOAT.equals(dtype)) {
            float fval1 = ((Number) val).floatValue();
            float fval2 = val2 != null ? ((Number) val2).floatValue() : fval1;
            cond.addValue(fval1 - moe);
            cond.addValue(fval2 + moe);
         }
         else {
            double fval1 = ((Number) val).doubleValue();
            double fval2 = val2 != null ? ((Number) val2).doubleValue() : fval1;
            cond.addValue(fval1 - moe);
            cond.addValue(fval2 + moe);
         }
      }
      else {
         cond.addValue(val);

         if(!StringUtils.isEmpty(val2)) {
            cond.addValue(val2);
         }
      }

      cond.setOperation(operation);
      cond.setConvertingType(convertType);

      if(GraphTypes.isBoxplot(cinfo.getChartType()) && pair.getFieldValue2() == null &&
         operation == XCondition.BETWEEN)
      {
         final String attr = ref.getAttribute().trim();
         ref = new ColumnRef(new AttributeRef(BoxDataSet.OUTLIER_PREFIX + attr));
      }

      return new ConditionItem(ref, cond, level);
   }

   /**
    * Create condition list for logical range selection.
    * @param selection the specified selection.
    * @param cols the specified column selection.
    */
   private ConditionList createLogicalRangeCondition(VSSelection selection,
                                                     ColumnSelection cols)
   {
      if(selection == null || selection.isEmpty()) {
         return null;
      }

      Map<DataRef, RangeCondition> rmap = new HashMap<>(); // range condition map

      for(int i = 0; i < selection.getPointCount(); i++) {
         VSPoint point = selection.getPoint(i);

         for(int j = 0; j < point.getValueCount(); j++) {
            VSFieldValue pair = point.getValue(j);
            DataRef ref = getDataRef(pair);

            if(ref == null) {
               continue;
            }

            // measure?
            if(ref instanceof VSAggregateRef) {
               AggregateRef aggregate = ((VSAggregateRef) ref).createAggregateRef(cols);
               ref = aggregate.getDataRef();
            }
            // dimension?
            else {
               GroupRef group = ((VSDimensionRef) ref).createGroupRef(cols);
               ref = group.getDataRef();
            }

            String dtype = ref.getDataType();
            Object val = pair.getFieldValue().getObject(dtype);
            RangeCondition cc = rmap.get(ref);

            if(cc == null) {
               cc = new RangeCondition();
               rmap.put(ref, cc);
            }

            cc.addObject(val);
         }
      }

      List<ConditionList> list = rmap.entrySet().stream()
         .map(e -> e.getValue().createConditionList(e.getKey()))
         .collect(Collectors.toList());

      if(list.size() == 0) {
         return null;
      }

      return ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
   }

   /**
    * Get the index of an axis data ref.
    */
   private int indexOfAxisDataRef(String name, VSDataRef[] refs) {
      for(int i = 0; i < refs.length; i++) {
         if(refs[i].getFullName().equals(name)) {
            return i;
         }
      }

      return -1;
   }


   /**
    * Get the data ref from a field value.
    */
   public DataRef getDataRef(VSFieldValue val) {
      if(val == null) {
         return null;
      }

      VSChartInfo cinfo = getVSChartInfo();
      VSDataRef[] refs = cinfo.getRTFields();
      DataRef ref = findDataRef(refs, val);

      if(ref != null) {
         return ref;
      }

      VSDataRef[] tempRefs = cinfo.getDcTempGroups();
      return findDataRef(tempRefs, val);
   }

   private DataRef findDataRef(VSDataRef[] refs, VSFieldValue val) {
      if(refs == null || refs.length == 0 || val == null) {
         return null;
      }

      for(VSDataRef ref : refs) {
         if(ref.getFullName().equals(val.getFieldName())) {
            return ref;
         }

         if(isRightField(ref, val.getFieldName())) {
            return ref;
         }

         if(ref instanceof XAggregateRef) {
            XAggregateRef aref = (XAggregateRef) ref;

            if(aref.getFullName(false).equals(val.getFieldName())) {
               return aref;
            }

            if(isRightField(aref, val.getFieldName())) {
               return aref;
            }
         }
      }

      return null;
   }

   /**
    * Check attribute with filed name for olap backward compatibility.
    */
   private boolean isRightField(DataRef ref, String fieldName) {
      if((ref.getRefType() & DataRef.CUBE) == DataRef.CUBE) {
         return ref.getAttribute().equals(fieldName);
      }

      return false;
   }

   /**
    * Set the xcube.
    * @param cube the specified xcube.
    */
   public void setXCube(XCube cube) {
      this.cube = cube;
   }

   /**
    * Get the xcube.
    * @return the xcube.
    */
   public XCube getXCube() {
      return cube;
   }

   /**
    * Set the chart data info.
    * @param info the chart data info.
    */
   public void setVSChartInfo(VSChartInfo info) {
      this.cinfo = info;
   }

   /**
    * Get the chart data info.
    * @return the chart data info.
    */
   public VSChartInfo getVSChartInfo() {
      return cinfo;
   }

   /**
    * Set the chart descriptor.
    * @param desc the specified chart descriptor.
    */
   public void setChartDescriptor(ChartDescriptor desc) {
      this.desc = desc;
   }

   /**
    * Get the chart descriptor.
    * @return the chart desciptor.
    */
   public ChartDescriptor getChartDescriptor() {
      return desc;
   }

   /**
    * Set the runtime chart descriptor.
    * @param rdesc the specified chart descriptor.
    */
   public void setRTChartDescriptor(ChartDescriptor rdesc) {
      this.rdesc = rdesc;
   }

   /**
    * Get the runtime chart descriptor.
    * @return the chart desciptor.
    */
   public ChartDescriptor getRTChartDescriptor() {
      return rdesc;
   }

   /**
    * Get the run time tip option.
    */
   @Override
   public int getTipOption() {
      return tipOptionValue.getIntValue(false, TOOLTIP_OPTION);
   }

   /**
    * Set the run time tip option.
    */
   @Override
   public void setTipOption(int tipOption) {
      tipOptionValue.setRValue(tipOption);
   }

   /**
    * Get the design time tip option.
    */
   @Override
   public int getTipOptionValue() {
      return tipOptionValue.getIntValue(true, TOOLTIP_OPTION);
   }

   /**
    * Set the design time tip option.
    */
   @Override
   public void setTipOptionValue(int tipOption) {
      tipOptionValue.setDValue(tipOption + "");
   }

   /**
    * Get the runtime tip view.
    * @return the runtime tip view.
    */
   public String getRuntimeTipView() {
      return getTipOption() == VIEWTIP_OPTION ? getTipView() : null;
   }

   /**
    * Get the run time tip view.
    */
   @Override
   public String getTipView() {
      Object tipView = tipViewValue.getRValue();
      return tipView == null ? null : tipView + "";
   }

   /**
    * Set the run time tip view.
    */
   @Override
   public void setTipView(String tipView) {
      tipViewValue.setRValue(tipView);
   }

   /**
    * Get the design time tip view.
    */
   @Override
   public String getTipViewValue() {
      return tipViewValue.getDValue();
   }

   /**
    * Set the design time tip view.
    */
   @Override
   public void setTipViewValue(String tipView) {
      tipViewValue.setDValue(tipView);
   }

   /**
    * Get the run time alpha.
    */
   @Override
   public String getAlpha() {
      Object alpha = alphaValue.getRValue();
      return alpha == null ? null : alpha + "";
   }

   /**
    * Set the run time alpha.
    */
   @Override
   public void setAlpha(String alpha) {
      alphaValue.setRValue(alpha);
   }

   /**
    * Get the design time alpha.
    */
   @Override
   public String getAlphaValue() {
      return alphaValue.getDValue();
   }

   /**
    * Set the design time alpha.
    */
   @Override
   public void setAlphaValue(String alpha) {
      alphaValue.setDValue(alpha);
   }

   /**
    * Get the views to apply filtering on mouse flyover over this assembly.
    */
   @Override
   public String[] getFlyoverViews() {
      return flyoverValue.getRValue();
   }

   /**
    * Set the views to apply filtering on mouse flyover over this assembly.
    */
   @Override
   public void setFlyoverViews(String[] views) {
      flyoverValue.setRValue(views);
   }

   /**
    * Get the views to apply filtering on mouse flyover over this assembly.
    */
   @Override
   public String[] getFlyoverViewsValue() {
      return flyoverValue.getDValue();
   }

   /**
    * Set the views to apply filtering on mouse flyover over this assembly.
    */
   @Override
   public void setFlyoverViewsValue(String[] views) {
      flyoverValue.setDValue(views);
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public ChartVSAssemblyInfo clone(boolean shallow) {
      try {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(desc != null) {
               info.desc = (ChartDescriptor) desc.clone();
            }

            if(rdesc != null) {
               info.rdesc = (ChartDescriptor) rdesc.clone();
            }

            if(cube instanceof VSCube) {
               info.cube = (XCube) ((VSCube) cube).clone();
            }

            if(cinfo != null) {
               info.cinfo = cinfo.clone();
            }

            if(zselection != null) {
               info.zselection = zselection.clone();
            }

            if(xselection != null) {
               info.xselection = xselection.clone();
            }

            if(bselection != null) {
               info.bselection = bselection.clone();
            }

            if(titleInfo != null) {
               info.titleInfo = (TitleInfo) titleInfo.clone();
            }

            if(tipOptionValue != null) {
               info.tipOptionValue = (DynamicValue2) tipOptionValue.clone();
            }

            if(tipViewValue != null) {
               info.tipViewValue = tipViewValue.clone();
            }

            if(drillFilter != null) {
               info.drillFilter = drillFilter.clone();
            }

            if(dateComparison != null) {
               info.dateComparison = dateComparison.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ChartVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Write attributes.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" tipOption=\"" + getTipOption() + "\"");
      writer.print(" tipOptionValue=\"" + getTipOptionValue() + "\"");
      writer.print(" flyClick=\"" + isFlyOnClick() + "\"");
      writer.print(" flyClickValue=\"" + getFlyOnClickValue() + "\"");
      writer.print(" summarySortCol=\"" + getSummarySortCol() + "\"");
      writer.print(" summarySortVal=\"" + getSummarySortValValue() + "\"");

      if(cubeType != null) {
         writer.print(" cubeType=\"" + cubeType + "\"");
      }
   }

   /**
    * Parse attributes.
    */
   @Override
   protected void parseAttributes(Element element) {
      super.parseAttributes(element);
      String prop = getAttributeStr(element, "tipOption", "" + TOOLTIP_OPTION);
      setTipOptionValue(Integer.parseInt(prop));

      setFlyOnClickValue(Tool.getAttribute(element, "flyClickValue"));

      prop = getAttributeStr(element, "summarySortCol", "-1");
      setSummarySortColValue(Integer.parseInt(prop));

      prop = getAttributeStr(element, "summarySortVal", "0");
      setSummarySortValValue(Integer.parseInt(prop));

      cubeType = Tool.getAttribute(element, "cubeType");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      VSUtil.setIgnoreCSSFormat(false);

      if(titleInfo != null) {
         titleInfo.writeXML(writer, getFormatInfo().getFormat(TITLEPATH),
                            getViewsheet(), getName());
      }

      if(tipViewValue != null && tipViewValue.getDValue() != null) {
         writer.print("<tipViewValue>");
         writer.print("<![CDATA[" + tipViewValue.getDValue() + "]]>");
         writer.println("</tipViewValue>");
      }

      if(tipViewValue != null && getTipView() != null) {
         writer.print("<tipView>");
         writer.print("<![CDATA[" + getTipView() + "]]>");
         writer.println("</tipView>");
      }

      if(alphaValue != null && alphaValue.getDValue() != null) {
         writer.print("<alphaValue>");
         writer.print("<![CDATA[" + alphaValue.getDValue() + "]]>");
         writer.println("</alphaValue>");
      }

      if(alphaValue != null && getAlpha() != null) {
         writer.print("<alpha>");
         writer.print("<![CDATA[" + getAlpha() + "]]>");
         writer.println("</alpha>");
      }

      if(flyoverValue.getDValue() != null) {
         writer.println("<flyoverViewValues>");

         for(String view : flyoverValue.getDValue()) {
            writer.println("<flyoverViewValue><![CDATA[" + view +
                           "]]></flyoverViewValue>");
         }

         writer.println("</flyoverViewValues>");
      }

      if(flyoverValue.getRValue() != null) {
         writer.println("<flyoverViews>");

         for(String view : flyoverValue.getRValue()) {
            writer.println("<flyoverView><![CDATA[" + view +
                           "]]></flyoverView>");
         }

         writer.println("</flyoverViews>");
      }

      if(desc != null) {
         desc.writeXML(writer);
      }

      if(cube instanceof VSCube) {
         ((VSCube) cube).writeXML(writer);
      }

      if(cinfo != null) {
         cinfo.writeXML(writer);
      }

      if(zselection != null) {
         writer.print("<zoomSelection>");
         zselection.writeXML(writer);
         writer.println("</zoomSelection>");
      }

      if(xselection != null) {
         writer.print("<excludeSelection>");
         xselection.writeXML(writer);
         writer.println("</excludeSelection>");
      }

      if(bselection != null) {
         writer.print("<brushSelection>");
         bselection.writeXML(writer);
         writer.println("</brushSelection>");

         if(bselection.getOrigSelection() != null) {
            writer.print("<origSelection>");
            bselection.getOrigSelection().writeXML(writer);
            writer.println("</origSelection>");
         }
      }

      drillFilter.writeXML(writer);
      writer.print("<dateComparison comparisonShareFrom=\""+
                      (comparisonShareFrom == null ? "" : comparisonShareFrom) + "\">");
      DateComparisonInfo dComparison = getDateComparisonInfo();

      if(dComparison != null) {
         dComparison.writeXML(writer);
      }

      writer.print("</dateComparison>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      titleInfo.parseXML(elem);

      Element anode = Tool.getChildNodeByTagName(elem, "tipViewValue");
      anode = anode == null ? Tool.getChildNodeByTagName(elem, "tipView") : anode;

      if(Tool.getValue(anode) != null) {
         tipViewValue.setDValue(Tool.getValue(anode));
      }

      anode = Tool.getChildNodeByTagName(elem, "alphaValue");
      anode = anode == null ? Tool.getChildNodeByTagName(elem, "alpha") : anode;

      if(Tool.getValue(anode) != null) {
         alphaValue.setDValue(Tool.getValue(anode));
      }

      anode = Tool.getChildNodeByTagName(elem, "flyoverViewValues");

      if(anode != null) {
         NodeList views = Tool.getChildNodesByTagName(anode, "flyoverViewValue");
         String[] arr = new String[views.getLength()];

         for(int i = 0; i < arr.length; i++) {
            arr[i] = Tool.getValue(views.item(i));
         }

         flyoverValue.setDValue(arr);
      }

      Element desNode = Tool.getChildNodeByTagName(elem, "chartDescriptor");

      if(desNode != null) {
         desc = new ChartDescriptor();
         desc.parseXML(desNode);
      }

      Element dnode = Tool.getChildNodeByTagName(elem, "VSCube");

      if(dnode != null) {
         cube = new VSCube();
         ((VSCube) cube).parseXML(dnode);
      }

      Element enode = Tool.getChildNodeByTagName(elem, "VSChartInfo");

      if(enode != null) {
         cinfo = VSChartInfo.createVSChartInfo(enode);
      }

      Element zoomNode = Tool.getChildNodeByTagName(elem, "zoomSelection");

      if(zoomNode != null) {
         Element node2 = Tool.getChildNodeByTagName(zoomNode, "VSSelection");

         if(node2 != null) {
            zselection = new VSSelection();
            zselection.parseXML(node2);
         }
      }

      Element excludeNode = Tool.getChildNodeByTagName(elem, "excludeSelection");

      if(excludeNode != null) {
         Element node2 = Tool.getChildNodeByTagName(excludeNode, "VSSelection");

         if(node2 != null) {
            xselection = new VSSelection();
            xselection.parseXML(node2);
         }
      }

      Element brushNode = Tool.getChildNodeByTagName(elem, "brushSelection");

      if(brushNode != null) {
         Element node2 = Tool.getChildNodeByTagName(brushNode, "VSSelection");

         if(node2 != null) {
            bselection = new VSSelection();
            bselection.parseXML(node2);
         }

         brushNode = Tool.getChildNodeByTagName(elem, "origSelection");

         if(brushNode != null) {
            node2 = Tool.getChildNodeByTagName(brushNode, "VSSelection");

            if(node2 != null) {
               VSSelection sel2 = new VSSelection();
               sel2.parseXML(node2);
               bselection.setOrigSelection(sel2);
            }
         }
         // for bc problem
         else if(bselection != null) {
            bselection.setOrigSelection(bselection.clone());
         }
      }

      drillFilter.parseXML(elem);

      Element dateComparisonNode = Tool.getChildNodeByTagName(elem, "dateComparison");

      if(dateComparisonNode != null) {
         comparisonShareFrom = Tool.getAttribute(dateComparisonNode, "comparisonShareFrom");
         DateComparisonInfo dcomparison = new DateComparisonInfo();
         dcomparison.parseXML(dateComparisonNode);

         if(dcomparison.getPeriods() != null && dcomparison.getInterval() != null) {
            dateComparison = dcomparison;
         }
      }
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) info;

      if(!Tool.equalsContent(desc, ninfo.desc)) {
         desc = ninfo.desc;
         result = true;
      }

      if(!Tool.equalsContent(rdesc, ninfo.rdesc)) {
         rdesc = ninfo.rdesc;
         result = true;
      }

      if(!Tool.equals(tipOptionValue, ninfo.tipOptionValue) ||
         !Tool.equals(getTipOption(), ninfo.getTipOption()))
      {
         tipOptionValue = ninfo.tipOptionValue;
         result = true;
      }

      if(!Tool.equals(tipViewValue, ninfo.tipViewValue) ||
         !Tool.equals(getTipView(), ninfo.getTipView()))
      {
         tipViewValue = ninfo.tipViewValue;
         result = true;
      }

      if(!Tool.equals(alphaValue, ninfo.alphaValue) ||
         !Tool.equals(getAlpha(), ninfo.getAlpha()))
      {
         alphaValue = ninfo.alphaValue;
         result = true;
      }

      if(!Tool.equals(getFlyoverViewsValue(), ninfo.getFlyoverViewsValue()) ||
         !Tool.equals(getFlyoverViews(), ninfo.getFlyoverViews()))
      {
         flyoverValue = ninfo.flyoverValue;
         result = true;
      }

      if(!Tool.equals(getFlyOnClickValue(), ninfo.getFlyOnClickValue()) ||
         !Tool.equals(isFlyOnClick(), ninfo.isFlyOnClick()))
      {
         flyClickValue = ninfo.flyClickValue;
         result = true;
      }

      if(cinfo.getUnitWidthRatio() !=
         ninfo.getVSChartInfo().getUnitWidthRatio())
      {
         cinfo.setUnitWidthRatio(ninfo.getVSChartInfo().getUnitWidthRatio());
         result = true;
      }

      if(cinfo.getUnitHeightRatio() !=
         ninfo.getVSChartInfo().getUnitHeightRatio())
      {
         cinfo.setUnitHeightRatio(ninfo.getVSChartInfo().getUnitHeightRatio());
         result = true;
      }

      if(cinfo.isWidthResized() != ninfo.getVSChartInfo().isWidthResized()) {
         cinfo.setWidthResized(ninfo.getVSChartInfo().isWidthResized());
         result = true;
      }

      if(cinfo.isHeightResized() != ninfo.getVSChartInfo().isHeightResized()) {
         cinfo.setHeightResized(ninfo.getVSChartInfo().isHeightResized());
         result = true;
      }

      if(!Tool.equals(cinfo.getToolTip(), ninfo.getVSChartInfo().getToolTip())) {
         cinfo.setToolTip(ninfo.getVSChartInfo().getToolTip());
         cinfo.setToolTipValue(ninfo.getVSChartInfo().getToolTipValue());
         result = true;
      }

      if(cinfo.isCombinedToolTip() != ninfo.getVSChartInfo().isCombinedToolTip()) {
         cinfo.setCombinedToolTip(ninfo.getVSChartInfo().isCombinedToolTip());
         cinfo.setCombinedToolTipValue(ninfo.getVSChartInfo().getCombinedToolTipValue());
         result = true;
      }

      ChartRef preiod = cinfo.getPeriodField();
      ChartRef npreiod = ninfo.getVSChartInfo().getPeriodField();

      if(!Tool.equals(preiod, npreiod) || !Tool.equalsContent(preiod, npreiod)) {
         cinfo.setPeriodField(npreiod);
         result = true;
      }

      if(!Tool.equals(maxSize, ninfo.maxSize)) {
         maxSize = ninfo.maxSize;
         result = true;
      }

      if(!Tool.equals(titleInfo, ninfo.titleInfo)) {
         titleInfo = ninfo.titleInfo;
         result = true;
      }

      return result;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) info;
      SourceInfo source = getSourceInfo();
      boolean srcChanged = !Tool.equals(source, ninfo.getSourceInfo());

      hint = super.copyInputDataInfo(info, hint);
      source = getSourceInfo();

      if(source != null && source.getType() == SourceInfo.ASSET &&
         !Tool.equals(cube, ninfo.cube))
      {
         cube = ninfo.cube;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equalsContent(cinfo, ninfo.cinfo)) {
         cinfo = ninfo.cinfo;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      // reset unit size if binding changed
      if(srcChanged) {
         cinfo.setUnitWidthRatio(1);
         cinfo.setUnitHeightRatio(1);
         cinfo.setWidthResized(false);
         cinfo.setHeightResized(false);
      }

      if(!Tool.equals(drillFilter, ninfo.getDrillFilterInfo())) {
         drillFilter = ninfo.getDrillFilterInfo().clone();
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(dateComparison, ninfo.getDateComparisonInfo())) {
         dateComparison = ninfo.getDateComparisonInfo() != null ?
            ninfo.getDateComparisonInfo().clone() : null;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(comparisonShareFrom, ninfo.getComparisonShareFrom())) {
         comparisonShareFrom = ninfo.getComparisonShareFrom();
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof ChartVSAssemblyInfo)) {
         return false;
      }

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) obj;
      VSChartInfo cinfo2 = info.cinfo;
      XCube cube2 = info.cube;
      ChartDescriptor desc2 = info.desc;
      ChartDescriptor rdesc2 = info.rdesc;

      return Tool.equals(tipOptionValue, info.tipOptionValue) &&
         Tool.equals(getTipOption(), info.getTipOption()) &&
         Tool.equals(tipViewValue, info.tipViewValue) &&
         Tool.equals(alphaValue, info.alphaValue) &&
         Tool.equals(flyClickValue, info.flyClickValue) &&
         Tool.equals(getTipView(), info.getTipView()) &&
         Tool.equals(getAlpha(), info.getAlpha()) &&
         Tool.equals(getFlyoverViewsValue(), info.getFlyoverViewsValue()) &&
         Tool.equals(getFlyoverViews(), info.getFlyoverViews()) &&
         Tool.equals(bselection, info.bselection) &&
         Tool.equals(zselection, info.zselection) &&
         Tool.equals(xselection, info.xselection) &&
         Tool.equalsContent(cinfo, cinfo2) &&
         Tool.equals(cube, cube2) &&
         Tool.equalsContent(desc, desc2) &&
         Tool.equalsContent(rdesc, rdesc2);
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();
      // @by ankitmathur, Fix Bug #6253, Need to first add the ChartDescriptor
      // DynamicValues set so that when they are executed, the correct Runtime
      // ChartDescriptor is set.
      list.addAll(desc.getDynamicValues());
      list.addAll(titleInfo.getViewDynamicValues());

      // if rt chart descriptor exists, it's used for graph generation. needs to make sure
      // it's dynamic value is executed. (42014)
      if(rdesc != null) {
         list.addAll(rdesc.getDynamicValues());
      }

      if(cinfo != null) {
         List<DynamicValue> list2 = cinfo.getDynamicValues();
         list.addAll(list2);
      }

      DateComparisonInfo rtComparison = getDateComparisonInfo();

      if(rtComparison != null) {
         list.addAll(rtComparison.getDynamicValues());
      }

      return list;
   }

   /**
    * Get the hyperlink dynamic property values.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getHyperlinkDynamicValues() {
      List<DynamicValue> list = super.getHyperlinkDynamicValues();

      if(cinfo != null) {
         List<DynamicValue> list2 = cinfo.getHyperlinkDynamicValues();
         list.addAll(list2);
      }

      return list;
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   @Override
   public void update(Viewsheet vs, ColumnSelection columns) throws Exception {
      super.update(vs, columns);

      VSChartDimensionRef dim = null;
      String table = getTableName();
      CalendarVSAssembly calendar = VSUtil.getPeriodCalendar(vs, table);

      if(calendar != null && !GraphTypes.isMekko(cinfo.getChartType())) {
         int dtype = calendar.getDateType();
         DataRef ref = calendar.getDataRef();
         dim = new VSChartDimensionRef(ref);
         dim.setTimeSeries(false);
         dim.setDateLevel(VSUtil.getPeriodDateLevel(dtype));
         dim.setDates(calendar.getDates());

         ChartRef periodRef = cinfo.getPeriodField();

         if(Tool.equals(dim, periodRef)) {
            dim.setAxisDescriptor(periodRef.getAxisDescriptor());

            if(periodRef instanceof VSChartDimensionRef) {
               dim.setHyperlink(((VSChartDimensionRef) periodRef).getHyperlink());
            }
         }
      }

      if(cinfo != null) {
         SourceInfo source = getSourceInfo();

         if(source == null) {
            return;
         }

         if(cube == null) {
            cube = AssetUtil.getCube(source.getPrefix(), source.getSource());
         }

         if(table != null && table.startsWith(Assembly.CUBE_VS)) {
            cubeType = VSUtil.getCubeType(source.getPrefix(), table);
         }

         DateComparisonInfo dcInfo = null;
         dcInfo = DateComparisonUtil.getDateComparison(this, vs);
         cinfo.setChartDescriptor(getChartDescriptor());

         if(dcInfo == null || dcInfo.invalid()) {
            dcInfo = null;
            resetRuntimeDateComparisonInfo();
         }

         VSChartInfo clone = dcInfo == null ? cinfo : cinfo.clone();
         clone.update(vs, columns, !cinfo.isMultiStyles(), dim, source.getSource(), dcInfo);
         cinfo = clone;
         removeUselessChildRefs();
         chartTree.updateHierarcy(cinfo, cube, cubeType);
         updateSelections();
         updateChartTypeCssAttribute();
      }
   }

   public void updateSelections() {
      if(bselection != null && !bselection.isValid(cinfo) &&
         !DateComparisonUtil.appliedDateComparison(this))
      {
         bselection = null;
      }

      if(zselection != null && !zselection.isValid(cinfo) &&
         !DateComparisonUtil.appliedDateComparison(this))
      {
         zselection = null;
      }

      if(xselection != null && !xselection.isValid(cinfo) &&
         !DateComparisonUtil.appliedDateComparison(this))
      {
         xselection = null;
      }
   }

   /**
    * If date range field using variable dlevel, should remove the useless childrefs
    * when runtime dlevel changed to avoid keeping the wrong childref.
    */
   private void removeUselessChildRefs() {
      ChartRef[] refs = cinfo.getRTXFields();

      if(refs != null && refs.length != 0) {
         for(DataRef ref : refs) {
            if(ref instanceof VSDimensionRef && ((VSDimensionRef) ref).runtimeDateLevelChange()) {
               chartTree.removeXChildRef(((VSDimensionRef) ref).getFullName());
            }
         }
      }

      refs = cinfo.getRTYFields();

      if(refs != null && refs.length != 0) {
         for(DataRef ref : refs) {
            if(ref instanceof VSDimensionRef && ((VSDimensionRef) ref).runtimeDateLevelChange()) {
               chartTree.removeYChildRef(((VSDimensionRef) ref).getFullName());
            }
         }
      }

      refs = cinfo.getRTGroupFields();

      if(refs != null && refs.length != 0) {
         for(DataRef ref : refs) {
            if(ref instanceof VSDimensionRef && ((VSDimensionRef) ref).runtimeDateLevelChange()) {
               chartTree.removeGChildRef(((VSDimensionRef) ref).getFullName());
            }
         }
      }

      if(!GraphTypes.isTreemap(cinfo.getRTChartType())) {
         return;
      }

      if(cinfo instanceof RelationChartInfo) {
         ChartRef sourceField = ((RelationChartInfo) cinfo).getRTSourceField();

         if(sourceField != null && ((VSDimensionRef) sourceField).runtimeDateLevelChange()) {
            chartTree.removeTChildRef(sourceField.getFullName());
         }

         ChartRef targetField = ((RelationChartInfo) cinfo).getRTTargetField();

         if(targetField != null && ((VSDimensionRef) sourceField).runtimeDateLevelChange()) {
            chartTree.removeTChildRef(targetField.getFullName());
         }
      }
   }

   /**
    * Physical range comparator.
    */
   private class PRComparator implements Comparator<VSPoint>, Serializable {
      PRComparator(VSSelection selection) {
         final Map<String, Integer> map = new HashMap<>();
         List<String> axisList = new ArrayList<>();
         List<String> aestheticList = new ArrayList<>();
         VSDataRef[] arefs = cinfo.getRTAxisFields();
         Set<String> processed = new HashSet<>();

         for(int k = 0; k < selection.getPointCount(); k++) {
            VSPoint point = selection.getPoint(k);

            for(int i = 0; i < point.getValueCount(); i++) {
               VSFieldValue val = point.getValue(i);
               DataRef ref = getDataRef(val);

               if(ref == null) {
                  continue;
               }

               String fname = val.getFieldName();

               if(processed.contains(fname)) {
                  continue;
               }

               processed.add(fname);
               int index = indexOfAxisDataRef(fname, arefs);

               if(index >= 0) {
                  axisList.add(fname);
                  map.put(fname, index);
               }
               else {
                  aestheticList.add(fname);
               }
            }
         }

         // outer dimension first, then inner dimension or measure,
         // so that we might produce condition list of high performance
         axisList.sort((a, b) -> {
            int va = map.get(a);
            int vb = map.get(b);
            return va - vb;
         });

         xycnt = axisList.size();
         arr = new String[axisList.size() + aestheticList.size()];

         for(int i = 0; i < axisList.size(); i++) {
            arr[i] = axisList.get(i);
         }

         for(int i = 0; i < aestheticList.size(); i++) {
            arr[i + xycnt] = aestheticList.get(i);
         }
      }

      /**
       * Get the field size.
       */
      public int size() {
         return arr.length;
      }

      /**
       * Get all the fields.
       */
      public String[] getFields() {
         return arr;
      }

      /**
       * Get the group level at which index the values are not equal.
       */
      public int getGroupLevel(VSPoint a, VSPoint b) {
         if(a == null || b == null) {
            return 0;
         }

         for(int i = 0; i < arr.length; i++) {
            VSFieldValue vala = a.getValue(arr[i]);
            VSFieldValue valb = b.getValue(arr[i]);

            if(vala == valb) {
               continue;
            }

            // if two points contains different comparison value, don't group the values, e.g.
            // (state == NJ && price == 1) || (state == NJ && total == 100)
            // where fields are [state, total, price], will be grouped into
            // (state == NJ && price == 1) || (total == 100)
            // if we want to allow grouping in these cases, we need to handle the case where
            // comparisons are performed at different levels (41707).
            if(vala == null || valb == null) {
               return 0;
            }

            int val = vala.compareTo(valb);

            if(val != 0) {
               return i;
            }
         }

         return size();
      }

      /**
       * Compare two points.
       */
      @Override
      public int compare(VSPoint pa, VSPoint pb) {
         for(String name : arr) {
            VSFieldValue vala = pa.getValue(name);
            VSFieldValue valb = pb.getValue(name);

            if(vala == null) {
               vala = new VSFieldValue();
            }

            if(valb == null) {
               valb = new VSFieldValue();
            }

            int val = vala.compareTo(valb);

            if(val != 0) {
               return val;
            }
         }

         return 0;
      }

      private final int xycnt;
      private final String[] arr;
   }

   /**
    * Range condition, min <= val <= max
    */
   private static class RangeCondition {
      RangeCondition() {
         super();
      }

      // add to range, update min or max if necessary
      public void addObject(Object obj) {
         if(obj == null) {
            nflag = true;
            return;
         }

         if(min == null) {
            min = obj;
            max = obj;
            return;
         }

         int val = CoreTool.compare(obj, min, false, true);
         min = val < 0 ? obj : min;
         val = CoreTool.compare(obj, max, false, true);
         max = val > 0 ? obj : max;

         // handle rounding error for double comparison.
         if(min instanceof Double && max instanceof Double) {
            min = (Double) min - 0.0001;
            max = (Double) max + 0.0001;
         }
      }

      /**
       * Create condition list.
       */
      ConditionList createConditionList(DataRef ref) {
         ConditionList conds = new ConditionList();
         String dtype = ref.getDataType();

         // null
         if(nflag) {
            Condition cond = new Condition(dtype);
            int operation = XCondition.NULL;
            cond.setOperation(operation);
            ConditionItem citem = new ConditionItem(ref, cond, 0);
            conds.append(citem);
         }

         // only null
         if(min == null) {
            return conds;
         }
         // one value
         else if(min == max) {
            if(conds.getSize() > 0) {
               JunctionOperator op =
                  new JunctionOperator(JunctionOperator.OR, 0);
               conds.append(op);
            }

            Condition cond = new Condition(dtype);
            int operation = XCondition.EQUAL_TO;
            cond.setOperation(operation);
            cond.addValue(min);
            ConditionItem citem = new ConditionItem(ref, cond, 0);
            conds.append(citem);
            return conds;
         }
         // more than one different values
         else {
            if(conds.getSize() > 0) {
               JunctionOperator op =
                  new JunctionOperator(JunctionOperator.OR, 0);
               conds.append(op);
            }

            int level = conds.getSize() > 0 ? 1 : 0;

            // boolean? (col == false || col == true)
            if(Tool.equals(XSchema.BOOLEAN, dtype)) {
               Condition cond = new Condition(dtype);
               cond.addValue(min);
               ConditionItem citem = new ConditionItem(ref, cond, level);
               conds.append(citem);
               int jop = JunctionOperator.OR;
               JunctionOperator op = new JunctionOperator(jop, level);
               conds.append(op);

               cond = new Condition(dtype);
               cond.addValue(max);
               citem = new ConditionItem(ref, cond, level);
               conds.append(citem);
            }
            // normal? (min <= col <= max)
            else {
               Condition cond = new Condition(dtype);
               int operation = XCondition.GREATER_THAN;
               cond.setOperation(operation);
               cond.setEqual(true);
               cond.addValue(min);
               ConditionItem citem = new ConditionItem(ref, cond, level);
               conds.append(citem);
               int jop = JunctionOperator.AND;
               JunctionOperator op = new JunctionOperator(jop, level);
               conds.append(op);

               cond = new Condition(dtype);
               operation = XCondition.LESS_THAN;
               cond.setOperation(operation);
               cond.setEqual(true);
               cond.addValue(max);
               citem = new ConditionItem(ref, cond, level);
               conds.append(citem);
            }

            return conds;
         }
      }

      private Object min; // min object
      private Object max; // max object
      private boolean nflag; // null flag
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.CHART;
   }

   /**
    * Get binding cube type if any.
    * @return binding cube type.
    */
   public String getCubeType() {
      return cubeType;
   }

   /**
    * Set binding cube type.
    * @param cubeType binding cube type.
    */
   public void setCubeType(String cubeType) {
      this.cubeType = cubeType;
   }

   /**
    * Get the chart style.
    */
   public int getChartStyle() {
      return getVSChartInfo().getChartStyle();
   }

   /**
    * Set the chart style.
    * @param value represent the specific chart style.
    */
   public void setChartStyle(int value) {
      if(getChartStyle() != value && !cinfo.isMultiStyles()) {
         this.cinfo = (VSChartInfo) new ChangeChartTypeProcessor(
            getChartStyle(), value, false, false, null, getVSChartInfo(),
       true, getChartDescriptor()).process();
      }

      if(!cinfo.isMultiStyles()) {
         cinfo.setChartType(value);
      }
      else {
         ChartRef[] xrefs = cinfo.getRTXFields();
         ChartRef[] yrefs = cinfo.getRTYFields();
         ChangeChartProcessor processor = new ChangeChartTypeProcessor(
            getChartStyle(), value, true, true, null, getVSChartInfo(),
            true, getChartDescriptor());

         for(ChartRef[] refs : new ChartRef[][] {yrefs, xrefs}) {
            for(ChartRef ref : refs) {
               if(!GraphUtil.isMeasure(ref)) {
                  continue;
               }

               ChartAggregateRef mref = (ChartAggregateRef) ref;
               processor.fixShapeField(mref, getVSChartInfo(), value);
               mref.setChartType(value);
            }
         }
      }

      cinfo.updateChartType(!cinfo.isMultiStyles());
   }

   /**
    * Set map type.
    */
   public void setMapType(String type) {
      if(getVSChartInfo() instanceof VSMapInfo) {
         VSMapInfo minfo = (VSMapInfo) getVSChartInfo();
         minfo.setMeasureMapType(type);
      }
   }

   /**
    * Get map type.
    */
   public String getMapType() {
      return getVSChartInfo().getMapType();
   }

   /**
    * Check if only apply flyover when clicked.
    */
   public String getFlyOnClickValue() {
      return flyClickValue.getDValue();
   }

   /**
    * Set if only apply flyover when clicked.
    */
   public void setFlyOnClickValue(String val) {
      flyClickValue.setDValue(val);
   }

   /**
    * Check if only apply flyover when clicked.
    */
   public boolean isFlyOnClick() {
      return (Boolean) flyClickValue.getRuntimeValue(true);
   }

   /**
    * Set if only apply flyover when clicked.
    */
   public void setFlyOnClick(boolean val) {
      flyClickValue.setRValue(val);
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      tipOptionValue.setRValue(null);
      tipViewValue.setRValue(null);
      flyoverValue.setRValue(null);
      flyClickValue.setRValue(null);
      summarySortCol.setRValue(null);
      summarySortVal.setRValue(null);

      setRTChartDescriptor(null);

      if(cinfo == null) {
         return;
      }

      cinfo.setToolTip(null);
      cinfo.clearCombinedTooltipValue();
      cinfo.setRTAxisDescriptor(null);
      cinfo.setRTAxisDescriptor2(null);

      ChartRef[][] nrefs = {cinfo.getRTXFields(), cinfo.getRTYFields(),
                            cinfo.getRTGroupFields()};

      for(ChartRef[] refs : nrefs) {
         for(ChartRef ref : refs) {
            VSChartRef nref = (VSChartRef) ref;
            nref.setRTAxisDescriptor(null);

            if(ref instanceof VSChartAggregateRef) {
               ChartRef origRef =
                  cinfo.getFieldByName(ref.getFullName(), false);

               if(origRef instanceof VSChartAggregateRef) {
                  ((VSChartAggregateRef) ref).setChartType(
                     ((VSChartAggregateRef) origRef).getChartType());
               }
            }
         }
      }

      titleInfo.resetRuntimeValues();
      setDateComparisonRef(null);
   }

   /**
    * Rename the flyover views.
    */
   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      super.renameDepended(oname, nname, vs);

      String[] arr = getFlyoverViewsValue();

      if(arr != null) {
         for(int i = 0; i < arr.length; i++) {
            if(Tool.equals(arr[i], oname)) {
               arr[i] = nname;
            }
         }

         setFlyoverViewsValue(arr);
      }

      titleInfo.renameDepended(oname, nname, vs);
   }

   /**
    * Get scaling ratio.
    */
   public DimensionD getScalingRatio() {
      return scalingRatio;
   }

   /**
    * Set scaling ratio.
    */
   public void setScalingRatio(DimensionD ratio) {
      this.scalingRatio = ratio;
   }

   /**
    * Return the max mode size of the chart.
    */
   @Override
   public Dimension getMaxSize() {
      return maxSize;
   }

   /**
    * If set, should pass this value to
    * {@link inetsoft.report.composition.execution.ViewsheetSandbox#getVGraphPair}
    * to generate a graph with this max size.
    */
   public void setMaxSize(Dimension maxSize) {
      this.maxSize = maxSize;
   }

   /**
    * @return the z-index value when in max mode
    */
   public int getMaxModeZIndex() {
      return maxModeZIndex > 0 ? maxModeZIndex : getZIndex();
   }

   /**
    * Set the z-index value when in max mode
    */
   public void setMaxModeZIndex(int maxModeZIndex) {
      this.maxModeZIndex = maxModeZIndex;
   }

   /**
    * Get the run time title.
    * @return the title of assembly.
    */
   public String getTitle() {
      return titleInfo.getTitle(getFormatInfo().getFormat(TITLEPATH),
                                getViewsheet(), getName());
   }

   /**
    * Set the run time title.
    * @param value the specified title.
    */
   public void setTitle(String value) {
      titleInfo.setTitle(value);
   }

   /**
    * Get the title value in design time.
    * @return the title value of assembly.
    */
   public String getTitleValue() {
      return titleInfo.getTitleValue();
   }

   /**
    * Set the design time title value.
    * @param value the specified title value.
    */
   public void setTitleValue(String value) {
      titleInfo.setTitleValue(value);
   }

   /**
    * Check whether current assembly title is visible in run time.
    * @return true if title is visible, otherwise false.
    */
   public boolean isTitleVisible() {
      return titleInfo.isTitleVisible();
   }

   /**
    * Set the runtime title visible.
    * @param visible true if title is visible, otherwise false.
    */
   public void setTitleVisible(boolean visible) {
      titleInfo.setTitleVisible(visible);
   }

   /**
    * Check whether current assembly title is visible in design time.
    * @return true if title is visible, otherwise false.
    */
   public boolean getTitleVisibleValue() {
      return titleInfo.getTitleVisibleValue();
   }

   /**
    * Set the design time title visible value.
    * @param visible true if title is visible, otherwise false.
    */
   public void setTitleVisibleValue(boolean visible) {
      titleInfo.setTitleVisibleValue(visible + "");
   }

   /**
    * Get the run time title height.
    * @return the title height of assembly.
    */
   public int getTitleHeight() {
      return titleInfo.getTitleHeight();
   }

   /**
    * Set the run time title height.
    * @param value the specified title height.
    */
   public void setTitleHeight(int value) {
      titleInfo.setTitleHeight(value);
   }

   /**
    * Get the title height value in design time.
    * @return the title height value of assembly.
    */
   public int getTitleHeightValue() {
      return titleInfo.getTitleHeightValue();
   }

   /**
    * Set the design time title height value.
    * @param value the specified title height value.
    */
   public void setTitleHeightValue(int value) {
      titleInfo.setTitleHeightValue(value);
   }

   @Override
   public Insets getTitlePadding() {
      return titleInfo.getPadding();
   }

   @Override
   public void setTitlePadding(Insets padding, CompositeValue.Type type) {
      titleInfo.setPadding(padding, type);
   }

   public ConditionList getDrillFilterConditionList(String field) {
      return drillFilter.getDrillFilterConditionList(field);
   }

   public void setDrillFilterConditionList(String field, ConditionList drillCondition) {
      drillFilter.setDrillFilterConditionList(field, drillCondition);
   }

   public DrillFilterInfo getDrillFilterInfo() {
      return drillFilter;
   }

   public void setDrillFilterInfo(DrillFilterInfo info) {
      drillFilter = info;
   }

   public ChartTree getChartTree() {
      return chartTree;
   }

   @Override
   public DateComparisonInfo getDateComparisonInfo() {
      return this.dateComparison;
   }

   @Override
   public void setDateComparisonInfo(DateComparisonInfo info) {
      dateComparison = info;
   }

   public void resetRuntimeDateComparisonInfo() {
      comparisonShareFrom = null;
      setDateComparisonRef(null);
      setDateComparisonInfo(null);

      if(cinfo != null) {
         cinfo.setRuntimeDateComparisonRefs(null);
      }
   }

   @Override
   public String getComparisonShareFrom() {
      return comparisonShareFrom;
   }

   @Override
   public void setComparisonShareFrom(String assemblyFullName) {
      comparisonShareFrom = assemblyFullName;
   }

   @Override
   public void setDateComparisonRef(VSDataRef ref) {
      if(cinfo != null) {
         cinfo.setDateComparisonRef(ref);
      }
   }

   @Override
   public VSDataRef getDateComparisonRef() {
      return cinfo != null ? cinfo.getDateComparisonRef() : null;
   }

   public boolean isNoData() {
      return noData;
   }

   public void setNoData(boolean noData) {
      this.noData = noData;
   }

   public Integer getSummarySortCol() {
      return summarySortCol.getIntValue(false, -1);
   }

   public void setSummarySortCol(int summarySortCol) {
      this.summarySortCol.setRValue(summarySortCol + "");
   }

   public Integer getSummarySortColValue() {
      return summarySortCol.getIntValue(true, -1);
   }

   public void setSummarySortColValue(int summarySortCol) {
      this.summarySortCol.setDValue(summarySortCol + "");
   }

   public Integer getSummarySortVal() {
      return summarySortVal.getIntValue(false, -1);
   }

   public void setSummarySortVal(int summarySortVal) {
      this.summarySortVal.setRValue(summarySortVal + "");
   }

   public Integer getSummarySortValValue() {
      return summarySortVal.getIntValue(true, 0);
   }

   public void setSummarySortValValue(int summarySortVal) {
      this.summarySortVal.setDValue(summarySortVal + "");
   }

   /**
    * Return fields which are temporarily generated for expand the data as dc required,
    * and this part of temp fields also used to date compare(other temp fields are not used
    * in date compare, just used to expand the data).
    */
   @Override
   public XDimensionRef[] getTempDateGroupRef() {
      DateComparisonInfo dinfo =
         DateComparisonUtil.getDateComparison(this, getViewsheet());
      return dinfo == null ? null : dinfo.getTempDateGroupRef(this);
   }

   @Override
   public boolean supportDateComparison() {
      VSChartInfo vsChartInfo = getVSChartInfo();

      if(vsChartInfo == null) {
         return false;
      }

      return DateComparisonUtil.supportDateComparison(vsChartInfo, true);
   }

   @Override
   public DataRef getDCBIndingRef(String refName) {
      if(DateComparisonUtil.getDateComparison(this, vs) == null || !supportDateComparison()) {
         return null;
      }

      VSChartInfo chartInfo = getVSChartInfo();

      if(chartInfo == null) {
         return null;
      }

      ChartRef[] refs = chartInfo.getRuntimeDateComparisonRefs();

      for(ChartRef ref : refs) {
         if(ref != null && Tool.equals(ref.getFullName(), refName)) {
            return ref;
         }
      }

      return null;
   }

   public void updateChartTypeCssAttribute() {
      FormatInfo formatInfo = getFormatInfo();
      VSCompositeFormat fmt = formatInfo.getFormat(VSAssemblyInfo.OBJECTPATH);

      if(fmt != null) {
         CSSChartStyles.ChartType chartType = CSSChartStyles.getChartType(getVSChartInfo());
         CSSAttr attr = new CSSAttr("type", chartType.getCssName());
         fmt.getCSSFormat().getCSSParam().setCSSAttributes(attr);
      }
   }

   public ArrayList<CSSParameter> getCssParentParameters() {
      FormatInfo formatInfo = getFormatInfo();
      VSCompositeFormat objFmt = formatInfo.getFormat(VSAssemblyInfo.OBJECTPATH);

      if(objFmt == null) {
         return null;
      }

      ArrayList<CSSParameter> parentParams = new ArrayList<>();
      VSCompositeFormat sheetFormat = formatInfo.getFormat(VSAssemblyInfo.SHEETPATH);

      if(sheetFormat != null) {
         parentParams.add(sheetFormat.getCSSFormat().getCSSParam());
      }

      VSCSSFormat cssFormat = objFmt.getCSSFormat();
      parentParams.add(cssFormat.getCSSParam());
      parentParams.trimToSize();
      return parentParams;
   }

   private Dimension maxSize = null;
   private int maxModeZIndex = -1;
   private VSSelection bselection = null;
   private VSSelection zselection = null;
   private VSSelection xselection = null;
   private VSChartInfo cinfo;
   private XCube cube = null;
   private ChartDescriptor desc = new ChartDescriptor();
   private ChartDescriptor rdesc; // runtime chart descriptor
   private DynamicValue2 tipOptionValue;
   private DynamicValue flyClickValue;
   private DynamicValue tipViewValue = new DynamicValue();
   private DynamicValue alphaValue = new DynamicValue();
   private ClazzHolder<String[]> flyoverValue = new ClazzHolder<>();
   private String cubeType = null;
   private DimensionD scalingRatio = new DimensionD(1.0, 1.0);
   private TitleInfo titleInfo = new TitleInfo("Chart");
   private final ChartTree chartTree = new ChartTree();
   private DrillFilterInfo drillFilter = new DrillFilterInfo();
   private String comparisonShareFrom;
   private DateComparisonInfo dateComparison;
   private boolean noData = false;
   private DynamicValue2 summarySortCol;
   private DynamicValue2 summarySortVal;

   private static final Logger LOG = LoggerFactory.getLogger(ChartVSAssemblyInfo.class);
}

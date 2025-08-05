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
package inetsoft.uql.viewsheet.internal;

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.*;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.internal.GDefaults;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.graph.calc.*;
import inetsoft.report.filter.CrossTabFilterUtil;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.gui.viewsheet.VSLine;
import inetsoft.report.internal.*;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.internal.png.PNGEncoder;
import inetsoft.report.internal.table.*;
import inetsoft.report.io.viewsheet.CoordinateHelper;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.report.script.ReportJavaScriptEngine;
import inetsoft.report.style.TableStyle;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.asset.internal.ScriptIterator.ScriptListener;
import inetsoft.uql.asset.internal.ScriptIterator.Token;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.xmla.DimMember;
import inetsoft.uql.xmla.XMLAUtil;
import inetsoft.util.*;
import inetsoft.util.audit.AuditRecordUtils;
import inetsoft.util.audit.BookmarkRecord;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.web.binding.dnd.BindingDropTarget;
import inetsoft.web.binding.handler.CrosstabConstants;
import inetsoft.web.viewsheet.model.table.DrillLevel;
import org.apache.commons.imaging.ImageFormat;
import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Array;
import java.net.URL;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Viewsheet utilities.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public final class VSUtil {
   /**
    * Create presenter.
    */
   public static PresenterPainter createPainter(TextVSAssembly text) {
      VSAssemblyInfo info = text.getVSAssemblyInfo();

      if(info == null) {
         return null;
      }

      VSCompositeFormat fmt = info.getFormat();

      if(fmt == null || fmt.getPresenter() == null) {
         return null;
      }

      PresenterRef p = fmt.getPresenter();
      PresenterPainter painter = null;

      try {
         PresenterRef.CONVERTER.set(VSUtil.getPreConverter(text.getViewsheet()));
         Presenter presenter = p.createPresenter();
         presenter.setFont(fmt.getFont());
         presenter.setBackground(fmt.getBackground());
         painter = new PresenterPainter(presenter);
         Object obj = presenter.isRawDataRequired() ? text.getValue() :
            text.getText();

         if(obj == null || !presenter.isPresenterOf(obj.getClass())) {
            return null;
         }

         painter.setObject(obj);
      }
      catch(Exception ex) {
         LOG.info("create presenter error: " + p.getName(), ex);
      }

      return painter;
   }

   public static PresenterRef.PreConverter getPreConverter(Viewsheet vs) {
      final Viewsheet fvs = vs;

      return (Object obj, Class type) -> {
         if(type == Image.class && obj instanceof MetaImage) {
            String path = ((MetaImage) obj).getImageLocation().getPath();
            Image img = getVSImage(null, path, fvs, -1, -1, null, new VSPortalHelper());

            if(img != null) {
               return img;
            }
            else {
               LOG.warn("Cannot load image for presenter: {}", path);
            }
         }

         return obj;
      };
   }

   /**
    * Get the default font.
    */
   public static Font getDefaultFont() {
      Font tf = GDefaults.DEFAULT_TEXT_FONT;
      return new StyleFont(tf);
   }

   /**
    * Get the date level for period comparison.
    *
    * @param dtype the specified calendar data type.
    */
   public static int getPeriodDateLevel(int dtype) {
      int group;

      switch(dtype) {
      case Calendar.YEAR:
         group = DateRangeRef.YEAR_INTERVAL;
         break;
      case Calendar.MONTH:
         group = DateRangeRef.MONTH_INTERVAL;
         break;
      case Calendar.WEEK_OF_MONTH:
         group = DateRangeRef.WEEK_INTERVAL;
         break;
      case Calendar.DAY_OF_MONTH:
         group = DateRangeRef.DAY_INTERVAL;
         break;
      default:
         throw new RuntimeException("unsupported date type found: " + dtype);
      }

      return group;
   }

   /**
    * Get the period calendar.
    */
   public static CalendarVSAssembly getPeriodCalendar(Viewsheet vs,
                                                      String table)
   {
      if(table == null || table.length() == 0) {
         return null;
      }

      Assembly[] arr = vs.getAssemblies();

      for(int i = 0; i < arr.length; i++) {
         if(!(arr[i] instanceof CalendarVSAssembly)) {
            continue;
         }

         CalendarVSAssembly calendar = (CalendarVSAssembly) arr[i];
         String table2 = calendar.getTableName();
         DataRef ref = calendar.getDataRef();

         if(!table.equals(table2) || ref == null || !calendar.isPeriod()) {
            continue;
         }

         String[] dates = calendar.getDates();

         if(dates == null || dates.length == 0) {
            continue;
         }

         return calendar;
      }

      return null;
   }

   /**
    * Rename the depended in a dynamic value.
    *
    * @param oname the specified old assembly name.
    * @param nname the specified new assembly name.
    * @param dval  the specified dynamic value.
    * @param vs    the specified base viewsheet.
    *
    * @return the renamed dynamic value.
    */
   public static void renameDynamicValueDepended(String oname, String nname,
                                                 DynamicValue dval,
                                                 Viewsheet vs)
   {
      String val = dval.getDValue();

      if(isVariableValue(val)) {
         String name = val.substring(2, val.length() - 1);

         if(name.contains(".drillMember")) {
            name = name.replaceAll("\\.drillMember$", "");
            nname = nname + ".drillMember";
         }

         if(oname.equals(name)) {
            dval.setDValue("$(" + nname + ")");
         }
      }
      else if(isScriptValue(val)) {
         String script = val.substring(1);
         dval.setDValue("=" + Util.renameScriptDepended(oname, nname, script));
      }
   }

   /**
    * Check if is dynamic.
    *
    * @return <tt>true</tt> if dynamic, <tt>false</tt> otherwise.
    */
   public static boolean isDynamic(DynamicValue value) {
      if(value == null) {
         return false;
      }

      String text = value.getDValue();
      return isVariableValue(text) || isScriptValue(text);
   }

   /**
    * Check if is a variable value.
    *
    * @param val the specified dynamic value.
    *
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public static boolean isVariableValue(String val) {
      return val != null && val.startsWith("$(") && val.endsWith(")");
   }

   /**
    * Match the value that contains variable.
    */
   public static Matcher matchVariableFormula(String val) {
      Matcher matcher = null;

      if(val != null) {
         matcher = CONTAINS_VARIABLE_PATTERN.matcher(val);
      }

      return matcher;
   }

   /**
    * Check if the string may contain embedded variable, $(var)
    */
   public static boolean containsVariableFormula(String val) {
      return val != null && val.contains("$(");
   }

   /**
    * Check if is a script value.
    * @param val the specified dynamic value.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public static boolean isScriptValue(String val) {
      return val != null && val.startsWith("=");
   }

   /**
    * Check if is a dynamic value.
    * @param val the specified dynamic value.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public static boolean isDynamicValue(String val) {
      return isVariableValue(val) || isScriptValue(val);
   }

   /**
    * Get the value assembly depended on.
    * @param dvalues specified list of dynamic value.
    * @param set the specified set.
    * @param vs the specified base viewsheet.
    * @param excludedAssembly the specified assembly which won't be include.
    */
   public static void getDynamicValueDependeds(List dvalues, Set<AssemblyRef> set,
                                               AbstractSheet vs,
                                               Assembly excludedAssembly)
   {
      for(int i = 0; i < dvalues.size(); i++) {
         DynamicValue val = (DynamicValue) dvalues.get(i);
         VSUtil.getDynamicValueDependeds(val, set, vs, excludedAssembly);
      }
   }

   /**
    * Get the highlight dependeds.
    */
   public static void getHighlightDependeds(HighlightGroup group, Set<AssemblyRef> set, AbstractSheet vs) {
      if(group == null) {
         return;
      }

      String[] names = group.getNames();

      for(int i = 0; names != null && i < names.length; i++) {
         Highlight highlight = group.getHighlight(names[i]);
         ConditionList list = highlight.getConditionGroup().getConditionList();
         getConditionDependeds(list, set, vs);
      }
   }

   /**
    * Rename the highlight dependeds.
    */
   public static void renameHighlightDependeds(String oname, String nname, HighlightGroup group) {
      if(group == null) {
         return;
      }

      String[] names = group.getNames();

      for(int i = 0; names != null && i < names.length; i++) {
         Highlight highlight = group.getHighlight(names[i]);
         ConditionList list = highlight.getConditionGroup().getConditionList();

         if(list == null) {
            continue;
         }

         for(int j = 0; j < list.getSize(); j += 2) {
            XCondition condition = list.getXCondition(j);

            if(condition instanceof Condition) {
               renameConditionDependeds(oname, nname, (Condition) condition);
            }
         }
      }
   }

   /**
    * Get the condition dependeds.
    */
   public static void getConditionDependeds(ConditionList list, Set<AssemblyRef> set, AbstractSheet vs) {
      if(list == null) {
         return;
      }

      int len = list.getSize();

      for(int i = 0; i < len; i += 2) {
         XCondition condition = list.getXCondition(i);

         if(condition instanceof Condition) {
            getConditionDependeds((Condition) condition, set, vs);
         }
      }
   }

   /**
    * Get the condition dependeds.
    */
   public static void getConditionDependeds(Condition cond, Set<AssemblyRef> set, AbstractSheet vs) {
      for(int i = 0; i < cond.getValueCount(); i++) {
         Object val = cond.getValue(i);
         String name = null;

         if(val instanceof UserVariable) {
            UserVariable var = (UserVariable) val;
            name = var.getName();
            name = name.replaceAll("\\.drillMember$", "");
         }
         else if(val instanceof String && Condition.isVariable(val)) {
            name = (String) val;
            name = name.substring(2, name.length() - 1);
            name = name.replaceAll("\\.drillMember$", "");
         }
         else if(val instanceof ExpressionValue) {
            String type = ((ExpressionValue) val).getType();

            if(ExpressionValue.JAVASCRIPT.equals(type)) {
               String script = ((ExpressionValue) val).getExpression();
               getReferencedAssets(script, set, vs, null);
            }
         }

         Assembly assembly = vs == null || name == null ? null : vs.getAssembly(name);

         if(assembly != null) {
            set.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA, assembly.getAssemblyEntry()));
         }
      }
   }

   /**
    * Get the condition dependeds.
    */
   public static void renameConditionDependeds(String oname, String nname, Condition cond) {
      for(int i = 0; i < cond.getValueCount(); i++) {
         Object val = cond.getValue(i);
         String name = null;

         if(val instanceof UserVariable) {
            UserVariable var = (UserVariable) val;
            name = var.getName();

            if(Tool.equals(oname, name)) {
               var.setName(nname);
            }
         }
         else if(val instanceof String && Condition.isVariable(val)) {
            name = (String) val;
            name = name.substring(2, name.length() - 1);

            if(Tool.equals(oname, name)) {
               name = "$(" + nname + ")";
            }

            cond.setValue(i, name);
         }
         else if(val instanceof ExpressionValue) {
            ExpressionValue expVal = (ExpressionValue) val;
            String expression = expVal.getExpression();
            expVal.setExpression(Util.renameScriptDepended(oname, nname, expression));
         }
      }
   }

   /**
    * Get the value assembly depended on.
    * @param dval the specified dynamic value.
    * @param set the specified set.
    * @param vs the specified base viewsheet.
    * @param excludedAssembly the specified assembly which won't be include.
    */
   public static void getDynamicValueDependeds(DynamicValue dval, Set<AssemblyRef> set,
                                               AbstractSheet vs, Assembly excludedAssembly)
   {
      if(dval == null) {
         return;
      }

      String val = dval.getDValue();

      if(isVariableValue(val)) {
         String name = val.substring(2, val.length() - 1);
         name = name.replaceAll("\\.drillMember$", "");
         Assembly assembly = vs == null ? null : vs.getAssembly(name);

         if(assembly != null) {
            set.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA, assembly.getAssemblyEntry()));
         }
      }
      else if(isScriptValue(val)) {
         String script = val.substring(1);
         getReferencedAssets(script, set, vs, excludedAssembly);
      }
   }

   /**
    * Get assets referenced in script.
    * @param script the specified script.
    * @param set the specified set.
    * @param vs the specified base viewsheet.
    * @param excludedAssembly the specified assembly which won't be include.
    */
   public static void getReferencedAssets(String script, final Set<AssemblyRef> set,
                                          final AbstractSheet vs,
                                          Assembly excludedAssembly)
   {
      if(script == null || script.length() == 0 || vs == null) {
         return;
      }

      String key = vs.addr() + "::" + script;
      Set<AssemblyRef> dset = scriptDeps.get(key);

      // @by larryl, cache the result. This method may be called many many
      // times for the same script
      if(dset == null) {
         dset = new HashSet<>();

         ScriptListener listener = new ScriptListener2(vs, dset, excludedAssembly);
         ScriptIterator iterator = new ScriptIterator(script);
         iterator.addScriptListener(listener);
         iterator.iterate();

         scriptDeps.put(key, dset);
      }

      set.addAll(dset);
   }

   /**
    * Rename the tip/flyover assembly depended on.
    * @param tip the specified tip.
    * @param oname the old name.
    * @param nname the new name.
    */
   public static void renameTipDependeds(TipVSAssemblyInfo tip,
                                         String oname, String nname)
   {
      if(tip.getTipOption() == TipVSAssemblyInfo.VIEWTIP_OPTION) {
         String val = tip.getTipViewValue();

         if(Tool.equals(oname, val)) {
            tip.setTipViewValue(nname);
         }
      }

      String[] flyovers = tip.getFlyoverViewsValue();

      if(flyovers != null) {
         for(int i = 0; i < flyovers.length; i++) {
            if(Tool.equals(flyovers[i], oname)) {
               flyovers[i] = nname;
            }
         }

         tip.setFlyoverViewsValue(flyovers);
      }
   }

   /**
    * Get content size.
    */
   public static Dimension getContentSize(FloatableVSAssembly chart, Dimension maxsize) {
      VSAssemblyInfo cinfo = chart.getVSAssemblyInfo();
      Viewsheet vs = chart.getViewsheet();
      int width, height;

      if(maxsize != null) {
         width = maxsize.width;
         height = maxsize.height;
      }
      else {
         Dimension size = cinfo.getLayoutSize();

         if(size == null || size.width == 0 || size.height == 0) {
            size = vs.getPixelSize(cinfo);
         }

         width = size.width;
         height = size.height;
      }

      VSCompositeFormat format = cinfo.getFormat();

      if(format != null && format.getBorders() != null) {
         Insets borders = format.getBorders();
         width -= Common.getLineWidth(borders.left);
         width -= Common.getLineWidth(borders.right);
         height -= Common.getLineWidth(borders.top);
         height -= Common.getLineWidth(borders.bottom);
      }

      if(((ChartVSAssemblyInfo) cinfo).isTitleVisible()) {
         height -= ((ChartVSAssemblyInfo) cinfo).getTitleHeight();
      }

      return new Dimension(width, height);
   }

   /**
    * Create a condition list.
    * @param ref the comparison column.
    * @param objs the comparison values.
    * @return the created condition list if any, <tt>null</tt> otherwise.
    */
   public static ConditionList createConditionList(DataRef ref, List<Object> objs) {
      ConditionItem icond = null; // in condition
      ConditionItem ncond = null; // null condition
      int icounter = 0;

      for(Object val : objs) {
         if(val == null && ncond == null) {
            Condition cond = new Condition();
            cond.setOperation(Condition.NULL);
            ncond = new ConditionItem(ref, cond, 0);
         }
         else if(val != null) {
            icounter++;

            if(icond == null) {
               Condition cond = new Condition(false, false);
               cond.setOperation(Condition.EQUAL_TO);
               cond.setType(ref.getDataType());
               icond = new ConditionItem(ref, cond, 0);
            }

            Condition cond = icond.getCondition();

            if(icounter > 1) {
               cond.setOperation(Condition.ONE_OF);
            }

            // value needs to be the correct type for MV condition. (60208)
            if(val instanceof String) {
               val = Tool.getData(ref.getDataType(), (String) val);
            }

            cond.addValue(val);
         }
      }

      if(icond == null && ncond == null) {
         return null;
      }

      ConditionList list = new ConditionList();

      if(icond == null) {
         list.append(ncond);
      }
      else if(ncond == null) {
         list.append(icond);
      }
      else {
         list.append(icond);
         list.append(new JunctionOperator(JunctionOperator.OR, 0));
         list.append(ncond);
      }

      return list;
   }

   /**
    * Merge the condition list.
    * @param conds the list stores condition lists.
    * @param op a junction operation (constants in JunctionOperator).
    * @return the merged condition list.
    */
   public static ConditionList mergeConditionList(List conds, int op) {
      return ConditionUtil.mergeConditionList(conds, op);
   }

   /**
    * Normalize a condition list. The condition list is generated from viewsheet
    * and used in worksheet. As in viewsheet, most data refs have attribute only
    * so we have to perform this normalization.
    * @param columns the specified column selection.
    * @param oconds the specified condition list.
    * @return the normalized condition list.
    */
   public static ConditionList normalizeConditionList(ColumnSelection columns,
                                                      ConditionList oconds)
   {
      return normalizeConditionList(columns, oconds, false);
   }

   /**
    * Normalize a condition list. The condition list is generated from viewsheet
    * and used in worksheet. As in viewsheet, most data refs have attribute only
    * so we have to perform this normalization.
    * @param columns the specified column selection.
    * @param oconds the specified condition list.
    * @param append append column to column selection.
    * @return the normalized condition list.
    */
   public static ConditionList normalizeConditionList(ColumnSelection columns,
                                                      ConditionList oconds,
                                                      boolean append)
   {
      return normalizeConditionList(columns, oconds, append, false);
   }

   /**
    * Normalize a condition list. The condition list is generated from viewsheet
    * and used in worksheet. As in viewsheet, most data refs have attribute only
    * so we have to perform this normalization.
    * @param columns the specified column selection.
    * @param oconds the specified condition list.
    * @param append append column to column selection.
    * @param detail condition list is from detail.
    * @return the normalized condition list.
    */
   public static ConditionList normalizeConditionList(ColumnSelection columns,
                                                      ConditionList oconds,
                                                      boolean append,
                                                      boolean detail)
   {
      if(oconds == null || columns == null) {
         return oconds;
      }

      ConditionList conds = new ConditionList();

      for(int i = 0; i < oconds.getSize(); i++) {
         if((i % 2) == 0) {
            ConditionItem item = oconds.getConditionItem(i);
            item = (ConditionItem) item.clone();
            DataRef ref = item.getAttribute();
            ref = columns.getAttribute(ref.getName());

            if(detail && ref instanceof ColumnRef && item.getAttribute() instanceof ColumnRef) {
               if(!(((ColumnRef) ref).getDataRef() instanceof DateRangeRef) &&
                  ((ColumnRef) item.getAttribute()).getDataRef() instanceof DateRangeRef)
               {
                  ref = null;
               }
            }

            if(ref == null) {
               if(append) {
                  ref = item.getAttribute();
                  ColumnRef column = (ref instanceof ColumnRef) ?
                     (ColumnRef) ref : null;

                  if(column != null) {
                     column = (ColumnRef) column.clone();
                     column.setVisible(false);
                     ref = column;
                  }

                  if(ref != null) {
                     columns.addAttribute(ref);
                  }
               }
            }
            else {
               item.setAttribute(ref);
            }

            conds.append(item);
         }
         else {
            JunctionOperator operator = oconds.getJunctionOperator(i);
            operator = (JunctionOperator) operator.clone();
            conds.append(operator);
         }
      }

      return conds;
   }

   /**
    * Get the attribute of a data ref.
    * @param ref the specified data ref.
    * @return the attribute of the data ref, which might be used to find column
    * in table.
    */
   public static String getAttribute(DataRef ref) {
      if(!(ref instanceof ColumnRef)) {
         return ref.getAttribute();
      }

      ColumnRef column = (ColumnRef) ref;
      String alias = column.getAlias();
      String attr = column.getAttribute();
      return alias == null || alias.length() == 0 ? attr : alias;
   }

   /**
    * Get the viewsheet column ref.
    * @param column the specified worksheet column ref.
    * @return the viewsheet column ref.
    */
   public static ColumnRef getVSColumnRef(ColumnRef column) {
      return VSUtil.getVSColumnRef(column, false);
   }

   /**
    * Get the viewsheet column ref.
    * @param column the specified worksheet column ref.
    * @return the viewsheet column ref.
    */
   private static ColumnRef getVSColumnRef(ColumnRef column, boolean replaceCalc) {
      if(column == null) {
         return null;
      }

      // calculate ref not have entity, attribute name problem
      if(!replaceCalc && column instanceof CalculateRef) {
         return column;
      }

      String name = column.getAlias();

      if(name == null || name.length() == 0) {
         name = column.getAttribute();
      }

      AttributeRef aref = new AttributeRef(null, name);
      DataRef ref = column.getDataRef();
      aref.setRefType(column.getRefType());
      aref.setDefaultFormula(column.getDefaultFormula());

      if(column.isSqlTypeSet()) {
         aref.setSqlType(column.getSqlType());
      }

      if(ref instanceof AttributeRef) {
         String caption = ((AttributeRef) ref).getCaption();

         if(caption != null && caption.length() > 0) {
            aref.setCaption(caption);
         }
      }

      ColumnRef ncolumn = new ColumnRef(aref);
      ncolumn.setDataType(column.getDataType());
      ncolumn.setWidth(column.getWidth());
      ncolumn.setDescription(column.getDescription());

      return ncolumn;
   }

   /**
    * Get the viewsheet column selection.
    * @param columns the specified worksheet column selection.
    * @return the viewsheet column selection.
    */
   public static ColumnSelection getVSColumnSelection(ColumnSelection columns) {
      if(columns == null) {
         return null;
      }

      ColumnSelection ncolumns = new ColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         ncolumns.addAttribute(getVSColumnRef(column), false);
      }

      return ncolumns;
   }

   /**
    * Get the target shared selection viewsheet assembly.
    * @param tvs the specified target viewsheet container.
    * @param fassembly the specified selection assembly.
    * @return the target selection assembly.
    */
   public static List<VSAssembly> getSharedVSAssemblies(Viewsheet tvs, VSAssembly fassembly) {
      Viewsheet fvs = fassembly.getViewsheet();
      ViewsheetInfo fvinfo = fvs.getViewsheetInfo();
      String fname = fassembly.getName();
      String fid = fvinfo.getFilterID(fname);
      List<VSAssembly> list = new ArrayList<>();

      if(fid == null) {
         return list;
      }

      ViewsheetInfo tvinfo = tvs.getViewsheetInfo();
      List<String> tnames = tvinfo.getFilterColumns(fid);

      for(String tname : tnames) {
         Assembly tassembly = tvs.getAssembly(tname);

         // ignore self

         if(tvs == fvs && tname.equals(fname)) {
            continue;
         }

         if(tassembly == null ||
            (tassembly.getAssemblyType() != fassembly.getAssemblyType()) &&
             !((tassembly instanceof NumericRangeVSAssembly &&
             fassembly instanceof NumericRangeVSAssembly)))
         {
            continue;
         }

         list.add((VSAssembly) tassembly);
      }

      return list;
   }

   /**
    * Get an unique key for a group of columns.
    */
   public static String getSelectionKey(DataRef[] refs) {
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < refs.length; i++) {
         if(buf.length() > 0) {
            buf.append("^/^");
         }

         buf.append(refs[i].getName());
      }

      return buf.toString();
   }

   /**
    * Parse the selection key into column names.
    */
   public static String[] parseSelectionKey(String key) {
      return Tool.split(key, "^/^", false);
   }

   /**
    * Check if contains excluded selection in this selection list.
    * @param list the specified selection list.
    * @return <tt>true</tt> if contains excluded selection, <tt>false</tt>
    * otherwise.
    */
   public static boolean containsExcludedSelection(SelectionList list) {
      if(list == null) {
         return false;
      }

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         SelectionValue sval = list.getSelectionValue(i);

         if(sval.isSelected() && sval.isExcluded()) {
            return true;
         }

         if(sval instanceof CompositeSelectionValue) {
            SelectionList slist = ((CompositeSelectionValue) sval).getSelectionList();
            boolean contained = containsExcludedSelection(slist);

            if(contained) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if contains selection in this selection list.
    * @param list the specified selection list.
    * @return <tt>true</tt> if contains selection, <tt>false</tt>
    * otherwise.
    */
   public static boolean containsSelection(SelectionList list) {
      if(list == null) {
         return false;
      }

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         SelectionValue sval = list.getSelectionValue(i);

         if(sval.isSelected()) {
            return true;
         }

         if(sval instanceof CompositeSelectionValue) {
            SelectionList slist = ((CompositeSelectionValue) sval).getSelectionList();
            boolean contained = containsSelection(slist);

            if(contained) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Get an image from either updated (in viewsheet) or from server.
    * @param path image path.
    * @param width the width of dynamically generated images.
    * @param height the height of dynamically generated images.
    * @param fmt default format settings.
    */
   public static Image getVSImage(Image rawImage, String path, Viewsheet vs,
                                  int width, int height,
                                  VSCompositeFormat fmt, XPortalHelper helper)
   {
      return getVSImage(rawImage, path, vs, width, height, -1, -1, fmt, helper);
   }

   /**
    * Get an image from either updated (in viewsheet) or from server.
    * @param path image path.
    * @param width the width of dynamically generated images.
    * @param height the height of dynamically generated images.
    * @param maxWidth the max width of dynamically generated images.
    * @param maxHeight the max height of dynamically generated images.
    * @param fmt default format settings.
    */
   public static Image getVSImage(Image rawImage, String path, Viewsheet vs,
                                  int width, int height, int maxWidth, int maxHeight,
                                  VSCompositeFormat fmt, XPortalHelper helper)
   {
      Image rimg = rawImage;

      if(rawImage != null) {
         rawImage = (rawImage instanceof MetaImage) ?
            ((MetaImage) rawImage).getImage() : rawImage;
         return Tool.getBufferedImage(rawImage);
      }

      // @by Armandwang bug1426830144926.
      if(path == null) {
         return null;
      }

      boolean isSVG = path.toLowerCase().endsWith(".svg");
      boolean isTIF = path.toLowerCase().endsWith(".tif");

      if(path.startsWith(ImageVSAssemblyInfo.UPLOADED_IMAGE) ||
         path.startsWith(ImageVSAssemblyInfo.SKIN_IMAGE) ||
         path.startsWith(ImageVSAssemblyInfo.SERVER_IMAGE))
      {
         try {
            byte[] buf = getVSImageBytes(null, path, vs, width, height, fmt, helper);

            if(buf == null) {
               LOG.error("Failed to get image: " + path);
            }
            else {
               buf = isTIF ? transcodeTiffToJpg(buf) : buf;
               ByteArrayInputStream baos = new ByteArrayInputStream(buf);
               rimg = isSVG ?
                  SVGSupport.getInstance().getSVGImage(
                     baos, width, height, maxWidth, maxHeight) : ImageIO.read(baos);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get image: " + path, ex);
         }
      }
      else if(path.startsWith("java:presenter:")) {
         try {
            rimg = getPresenterImage(path, width, height, fmt);
         }
         catch(Exception ex) {
            LOG.error(
                        "Failed to get presenter image: " + path, ex);
         }
      }
      else {
         byte[] buf = vs.getUploadedImageBytes(path);

         if(buf != null) {
            try {
               buf = isTIF ? transcodeTiffToJpg(buf) : buf;
               ByteArrayInputStream baos = new ByteArrayInputStream(buf);
               rimg = isSVG ? SVGSupport.getInstance().getSVGImage(
                  baos, width, height, maxWidth, maxHeight) : ImageIO.read(baos);
            }
            catch(Exception ex) {
               LOG.error("Failed to get uploaded image: " + path, ex);
            }
         }
         else {
            rimg = Tool.getImage(vs, path);

            if(rimg == null) {
               rimg = ReportJavaScriptEngine.getImage(path);
            }
         }
      }

      return rimg;
   }

   /**
    * Get an image from either updated (in viewsheet) or from server.
    * @param path image path.
    * @param width the width of dynamically generated images.
    * @param height the height of dynamically generated images.
    * @param fmt default format settings.
    */
   public static byte[] getVSImageBytes(Image rawImage, String path,
                                        Viewsheet vs, int width,
                                        int height, VSCompositeFormat fmt,
                                        XPortalHelper helper)
      throws Exception
   {
      String name;
      byte[] buf;

      if(rawImage != null) {
         buf = getImageBytes(rawImage, 72);
      }
      else if(path.startsWith(ImageVSAssemblyInfo.UPLOADED_IMAGE)) {
         name = path.substring(ImageVSAssemblyInfo.UPLOADED_IMAGE.length());
         buf = vs.getUploadedImageBytes(name);
      }
      else if(path.startsWith(ImageVSAssemblyInfo.SKIN_IMAGE)) {
         name = path.substring(ImageVSAssemblyInfo.SKIN_IMAGE.length());
         InputStream in;

         if(ImageVSAssemblyInfo.SKIN_TITLE.equals(name)) {
            in = helper.getPortalResource("image_title.png", false, true);
         }
         else if(ImageVSAssemblyInfo.SKIN_BACKGROUND.equals(name)) {
            in = helper.getPortalResource(
               "theme_background.png", false, true);
         }
         else if(ImageVSAssemblyInfo.SKIN_NEUTER1.equals(name)) {
            in = helper.getPortalResource("background1.png", false, false);
         }
         else if(ImageVSAssemblyInfo.SKIN_NEUTER2.equals(name)) {
            in = helper.getPortalResource("background2.png", false, false);
         }
         else if(ImageVSAssemblyInfo.SKIN_NEUTER3.equals(name)) {
            in = helper.getPortalResource("background3.png", false, false);
         }
         else {
            in = helper.getPortalResource(name, false, false);
         }

         buf = new byte[in.available()];
         in.read(buf);
         in.close();
      }
      else if(path.startsWith(ImageVSAssemblyInfo.SERVER_IMAGE)) {
         name = path.substring(ImageVSAssemblyInfo.SERVER_IMAGE.length());
         final String dir = SreeEnv.getProperty("html.image.directory");

         if(!Tool.isEmptyString(dir)) {
            final String imagePath = FileSystemService.getInstance().getPath(dir, name).toString();
            new ByteArrayOutputStream();

            try(final InputStream stream = DataSpace.getDataSpace().getInputStream(null, imagePath)) {
               buf = new byte[stream.available()];
               stream.read(buf);
            }
         }
         else {
            buf = null;
         }
      }
      else if(path.startsWith("java:presenter:")) {
         buf = getImageBytes(getPresenterImage(path, width, height, fmt), 72);
      }
      else if(path.endsWith(".svg") && path.indexOf("://") > 0) {
         buf = getSVGImageBytes(path);
      }
      else {
         buf = vs.getUploadedImageBytes(path);

         if(buf == null) {
            Image image = Tool.getImage(vs, path);

            if(image == null) {
               image = ReportJavaScriptEngine.getImage(path);
            }

            Tool.waitForImage(image);
            buf = getImageBytes(image, 72);
         }
      }

      return buf;
   }

   private static byte[] getSVGImageBytes(String path) {
      if(path == null || path.indexOf("://") <= 0) {
         return null;
      }

      try(InputStream input = new URL(path).openStream()) {
         SVGSupport svg = SVGSupport.getInstance();
         return svg.transcodeSVGImage(svg.createSVGDocument(input));
      }
      catch(IOException ex) {
         LOG.debug("Failed to read the SVG image", ex);
      }
      catch(Exception ex) {
         LOG.debug("An unexpected error occurred", ex);
      }

      return null;
   }

   /**
    * Convert a TIFF image byte to JPEG image byte by using
    * twelvemonkeys package for transcoding.
    * @param tif tiff image byte
    * @return jpeg image byte
    */
   public static byte[] transcodeTiffToJpg(byte[] tif) throws IOException {
      return transcodeTiff(tif, ImageFormats.JPEG);
   }

   /**
    * Convert a TIFF image byte to other format image.
    */
   private static byte[] transcodeTiff(byte[] tif, ImageFormat format) throws IOException {
      if(tif == null) {
         return null;
      }

      try {
         BufferedImage image = ImageIO.read(new ByteArrayInputStream(tif));
         ByteArrayOutputStream output = new ByteArrayOutputStream();
         ImageIO.write(image, format.getName(), output);
         return output.toByteArray();
      }
      catch(Exception e) {
         throw new IOException("Failed to transcode image", e);
      }
   }

   /**
    * Get an image from a presenter drawing. The presenter path is encoded as:
    * java:presenter:inetsoft.report.painter.BarPresenter;value=5;max=100
    * @param width the width of dynamically generated images.
    * @param height the height of dynamically generated images.
    * @param fmt default format settings.
    */
   private static Image getPresenterImage(String path, int width, int height,
                                          VSCompositeFormat fmt)
      throws Exception
   {
      path = path.substring("java:presenter:".length());

      int semi = path.indexOf(';');
      String cls = null;

      if(semi < 0) {
         cls = path;
         path = "";
      }
      else {
         cls = path.substring(0, semi);
         path = path.substring(semi + 1);
      }

      PresenterRef ref = new PresenterRef(cls);

      // set format before parsing so it can be overridden
      if(fmt != null && fmt.getFormat() != null) {
         Object fval = TableFormat.getFormat(
            fmt.getFormat(), fmt.getFormatExtent(), Locale.getDefault());
         ref.setParameter("format", fval);
      }

      Object val = null;
      String[] arr = Tool.split(path, ';');

      for(int i = 0; i < arr.length; i++) {
         String[] pair = Tool.split(arr[i], '=');

         if(pair.length == 2) {
            if(pair[0].equals("value")) {
               val = pair[1];
            }
            else {
               ref.setParameter(pair[0], pair[1]);
            }
         }
      }

      Presenter painter = ref.createPresenter();

      if(width <= 0 || height <= 0) {
         Dimension psize = painter.getPreferredSize(val);
         width = psize.width;
         height = psize.height;
      }

      if(!painter.isPresenterOf(val)) {
         try {
            val = Double.valueOf(val.toString());
         }
         catch(Throwable ex) {
         }
      }

      Image img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics g = img.getGraphics();
      g.setColor(Color.BLACK);

      if(fmt != null) {
         if(fmt.getBackground() != null) {
            g.setColor(fmt.getBackground());
            g.fillRect(0, 0, width, height);
         }

         if(fmt.getFont() != null) {
            g.setFont(fmt.getFont());
         }

         if(fmt.getForeground() != null) {
            g.setColor(fmt.getForeground());
         }
      }

      painter.paint(g, val, 0, 0, width, height);
      g.dispose();

      return img;
   }

   /**
    * Get image bytes.
    */
   public static byte[] getImageBytes(Image image, int dpi) throws Exception {
      if(image == null) {
         return new byte[0];
      }

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      // @by larryl, png encoding is 5x faster than jpeg
      PNGEncoder png = new PNGEncoder(image, true);

      png.setResolution(dpi);
      png.encode(output);
      output.close();

      return output.toByteArray();
   }

   /**
    * update calculate column for crosstab.
    */
   public static void updateCalculateInfo(boolean hasRow, boolean hasCol, boolean findColumn,
                                          Calculator calc, String dropType)
   {
      if(calc instanceof ValueOfCalc) {
         ValueOfCalc vcalc = (ValueOfCalc) calc;
         String columnName = vcalc.getColumnName();

         //add row
         if(CrosstabConstants.ROW_HEADERS.equals(dropType) && !findColumn) {
            if(AbstractCalc.COLUMN_INNER.equals(columnName) && hasCol) {
               return;
            }

            vcalc.setColumnName(AbstractCalc.ROW_INNER);
            updateValueOfCalcForm(vcalc);
         }
         //add col
         else if(CrosstabConstants.COLUMN_HEADERS.equals(dropType) && !findColumn && !hasRow) {
            vcalc.setColumnName(AbstractCalc.COLUMN_INNER);
            updateValueOfCalcForm(vcalc);
         }
         // add aggregate
         else if(!findColumn) {
            if(AbstractCalc.ROW_INNER.equals(columnName) && hasRow ||
               AbstractCalc.COLUMN_INNER.equals(columnName) && hasCol)
            {
               return;
            }

            vcalc.setColumnName(hasRow ? AbstractCalc.ROW_INNER : hasCol ?
               AbstractCalc.COLUMN_INNER : columnName);
         }
      }
      else if(calc instanceof RunningTotalCalc) {
         RunningTotalCalc rcalc = (RunningTotalCalc) calc;
         String breakBy = rcalc.getBreakBy();

         //add row
         if(CrosstabConstants.ROW_HEADERS.equals(dropType) && !findColumn) {
            if(AbstractCalc.COLUMN_INNER.equals(breakBy) && hasCol) {
               return;
            }

            rcalc.setBreakBy(AbstractCalc.ROW_INNER);
         }
         //add col
         else if(CrosstabConstants.COLUMN_HEADERS.equals(dropType) && !findColumn && !hasRow) {
            rcalc.setBreakBy(AbstractCalc.COLUMN_INNER);
         }
         // add aggregate
         else if(!findColumn) {
            if(AbstractCalc.ROW_INNER.equals(breakBy) && hasRow ||
               AbstractCalc.COLUMN_INNER.equals(breakBy) && hasCol)
            {
               return;
            }

            rcalc.setBreakBy(hasRow ? AbstractCalc.ROW_INNER
               : hasCol ? AbstractCalc.COLUMN_INNER : breakBy);
         }

         // if break by changed or
         // rows changed while break by is set to ROW_INNER or
         // columns changed while break by is set to COLUMN_INNER
         // then update the reset level to NONE
         if(!Tool.equals(breakBy, rcalc.getBreakBy()) ||
            (AbstractCalc.ROW_INNER.equals(breakBy) &&
               CrosstabConstants.ROW_HEADERS.equals(dropType)) ||
            (AbstractCalc.COLUMN_INNER.equals(breakBy) &&
               CrosstabConstants.COLUMN_HEADERS.equals(dropType)))
         {
            rcalc.setResetLevel(RunningTotalColumn.NONE);
         }
      }
      else if(calc instanceof PercentCalc) {
         PercentCalc percentCalc = (PercentCalc) calc;
         String percentage = percentCalc.getPercentageByValue();

         if(Tool.isEmptyString(percentage)) {
            return;
         }

         if(CrosstabConstants.ROW_HEADERS.equals(dropType) &&
            Integer.parseInt(percentage) == XConstants.PERCENTAGE_BY_COL && !hasCol)
         {
            percentCalc.setPercentageByValue(XConstants.PERCENTAGE_BY_ROW + "");
         }
         else if(CrosstabConstants.COLUMN_HEADERS.equals(dropType) &&
            Integer.parseInt(percentage) == XConstants.PERCENTAGE_BY_ROW && !hasRow)
         {
            percentCalc.setPercentageByValue(XConstants.PERCENTAGE_BY_COL + "");
         }
      }
   }

   private static void updateValueOfCalcForm(ValueOfCalc calc) {
      final int from = calc.getFrom();

      if(from == ValueOfCalc.PREVIOUS_YEAR || from == ValueOfCalc.PREVIOUS_QUARTER ||
         from == ValueOfCalc.PREVIOUS_WEEK)
      {
         calc.setFrom(ValueOfCalc.PREVIOUS);
      }
   }

   // callback for script iterator
   private static class ScriptListener2 implements ScriptListener {
      public ScriptListener2(AbstractSheet vs, Set dset, Assembly assembly) {
         this.vs = vs;
         this.dset = dset;
         this.excludedAssembly = assembly;
      }

      @Override
      public void nextElement(Token token, Token ptoken, Token ctoken) {
         if(!token.isRef()) {
            parseText(token.val);
            return;
         }

         Assembly assembly = vs.getAssembly(token.val);

         if(assembly == null && vs instanceof Viewsheet) {
            Worksheet ws = ((Viewsheet) vs).getBaseWorksheet();
            assembly = ws == null ? null : ws.getAssembly(token.val);
         }

         if(assembly == null || Tool.equals(assembly, excludedAssembly)) {
            return;
         }

         AssemblyEntry entry = assembly.getAssemblyEntry();

         if(assembly instanceof TableAssembly) {
            dset.add(new AssemblyRef(AssemblyRef.INPUT_DATA,
               assembly.getAssemblyEntry()));
         }
         // param.assembly?
         else if(ptoken != null && ptoken.val.equals("param")) {
            dset.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA, entry));
         }
         // assembly.selectedObjects or assembly.selectedObject?
         else if(ctoken != null &&
                 (ctoken.val.equals("selectedObject") ||
                  ctoken.val.equals("selectedObjects") ||
                  ctoken.val.equals("selectedLabel") ||
                  ctoken.val.equals("selectedLabels") ||
                  ctoken.val.equals("drillMember") ||
                  ctoken.val.equals("selectedIndex")))
         {
            // for tab, should use view changed
            if(assembly instanceof TabVSAssembly) {
               dset.add(new AssemblyRef(AssemblyRef.VIEW, entry));
            }
            // for non-tab, should use output changed
            else {
               dset.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA, entry));
            }
         }
         // assembly.value?
         else if(!token.val.equals(Assembly.FIELD)) {
            dset.add(new AssemblyRef(AssemblyRef.INPUT_DATA, entry));
         }
      }

      /**
       * Check if we can find identifiers in the text. Since the name of an
       * input assembly can be used alone to reference the value, we can't
       * assume that the dot notation is always used.
       */
      private void parseText(String str) {
         // parse the string into identifiers
         for(int i = 0; i < str.length(); i++) {
            for(; i < str.length() && !Character.isJavaIdentifierStart(str.charAt(i)); i++) {
            }

            int start = i;
            int end = str.length();

            for(int k = i; k < str.length(); k++) {
               if(!Character.isJavaIdentifierPart(str.charAt(k))) {
                  end = k;
                  break;
               }
            }

            boolean quoted = false;

            if(end > start) {
               String name = str.substring(start, end);

               // check if is a quoted text. For a quoted text, we need
               // not take it as an identifier but just a string value
               if(start > 0 && end < str.length()) {
                  char schar = str.charAt(start - 1);
                  char echar = str.charAt(end);

                  if((schar == '\'' || schar == '"') && schar == echar) {
                     quoted = true;
                  }
               }

               Assembly assembly = quoted ? null : vs.getAssembly(name);

               if(assembly != null && !Tool.equals(assembly, excludedAssembly)) {
                  AssemblyEntry entry = assembly.getAssemblyEntry();
                  dset.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA, entry));
               }

               i = end - 1;
            }
         }
      }

      private AbstractSheet vs;
      private Set dset;
      private Assembly excludedAssembly; // excluded assembly
   }

   /**
    * Sort the columns by name.
    */
   public static ColumnSelection sortColumns(ColumnSelection cols) {
      List<DataRef> list = cols.stream()
         .sorted(new DataRefComparator())
         .collect(Collectors.toList());

      return new ColumnSelection(list);
   }

   /**
    * Case insensitive comparison of column names.
    */
   public static class DataRefComparator implements Comparator<DataRef> {
      @Override
      public int compare(DataRef col1, DataRef col2) {
         return col1.getAttribute().compareToIgnoreCase(col2.getAttribute());
      }
   }

   /**
    * Calc the components z index.
    */
   public static void calcChildZIndex(Assembly[] arr, int parentZIndex) {
      Arrays.sort(arr, new Comparator() {
         @Override
         public int compare(Object obj0, Object obj1) {
            if(obj0 instanceof VSAssembly && obj1 instanceof VSAssembly) {
               VSAssembly assembly0 = (VSAssembly) obj0;
               VSAssembly assembly1 = (VSAssembly) obj1;
               return assembly0.getVSAssemblyInfo().getZIndex() -
                  assembly1.getVSAssemblyInfo().getZIndex();
            }

            return 0;
         }
      });

      int zindex = parentZIndex + 1;

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof VSAssembly) {
            ((VSAssembly) arr[i]).getVSAssemblyInfo().setZIndex(zindex);
            zindex += getZIndexGap((VSAssembly) arr[i]);
         }
      }
   }

   /**
    * Get zindex gap.
    * @param pre the previous assembly.
    */
   public static int getZIndexGap(VSAssembly pre) {
      if(pre instanceof ContainerVSAssembly) {
         return Viewsheet.CONTAINER_ZINDEX_GAP;
      }
      else if(pre instanceof Viewsheet) {
         return Viewsheet.VIEWSHEET_ZINDEX_GAP;
      }

      return Viewsheet.NORMAL_ZINDEX_GAP;
   }

   /**
    * Get cube source.
    */
   public static SourceInfo getCubeSource(VSAssembly assembly) {
      if(assembly == null) {
         return null;
      }

      Worksheet ws = assembly.getWorksheet();
      AssemblyRef[] refs = assembly.getDependedWSAssemblies();

      if(refs.length == 0) {
         return null;
      }

      Assembly assembly0 = ws.getAssembly(refs[0].getEntry());
      CubeTableAssembly cubeTable = null;

      if(assembly0 instanceof CubeTableAssembly) {
         cubeTable = (CubeTableAssembly) assembly0;
      }

      if(assembly0 instanceof MirrorTableAssembly) {
         TableAssembly tassembly =
            ((MirrorTableAssembly) assembly0).getTableAssembly();

         if(tassembly instanceof CubeTableAssembly) {
            cubeTable = (CubeTableAssembly) tassembly;
         }
      }

      if(cubeTable != null) {
         return cubeTable.getSourceInfo();
      }

      return null;
   }

   /**
    * Get cube assembly.
    */
   private static CubeTableAssembly getCubeAssembly(VSAssembly assembly) {
      if(assembly == null) {
         return null;
      }

      Worksheet ws = assembly.getWorksheet();
      AssemblyRef[] refs = assembly.getDependedWSAssemblies();

      if(refs.length == 0) {
         return null;
      }

      Assembly assembly0 = ws.getAssembly(refs[0].getEntry());

      if(assembly0 instanceof TableAssembly) {
         CubeTableAssembly cube =
            AssetUtil.getBaseCubeTable((TableAssembly) assembly0);
         return cube;
      }

      return null;
   }

   /**
    * Check if is worksheet cube.
    */
   public static boolean isWorksheetCube(VSAssembly table) {
      CubeTableAssembly cube = getCubeAssembly(table);

      if(cube == null) {
         return false;
      }

      return cube.getName().indexOf(Assembly.CUBE_VS) < 0;
   }

   public static String getCubeType(SourceInfo source) {
      String table = source == null ? null : source.getSource();

      if(table != null && table.startsWith(Assembly.CUBE_VS)) {
         return VSUtil.getCubeType(source.getPrefix(), table);
      }

      return null;
   }

   /**
    * Get binding cube type of a data assembly.
    * @return binding cube type.
    */
   public static String getCubeType(VSAssembly assembly) {
      SourceInfo sinfo = VSUtil.getCubeSource(assembly);

      if(sinfo != null) {
         return VSUtil.getCubeType(sinfo.getPrefix(), sinfo.getSource());
      }

      return null;
   }

   /**
    * Get binding cube type of a data assembly.
    * @param prefix the specified datasource name.
    * @param source the specified cube name.
    * @return binding cube type.
    */
   public static String getCubeType(String prefix, String source) {
      return AssetUtil.getCubeType(prefix, source);
   }

   /**
    * Refresh embedded viewsheet size.
    * @param assembly the assembly which cause the size of the
    *  embedded viewsheet to be change.
    */
   public static void refreshEmbeddedViewsheet(VSAssembly assembly) {
      // recalculate embedded vs bounds
      Viewsheet sheet = assembly.getViewsheet();

      if(sheet != null && sheet.isEmbedded()) {
         while(sheet != null && sheet.isEmbedded()) {
            sheet.getInfo().setPixelSize(null);
            sheet = sheet.getViewsheet();
         }

         sheet.layout();
      }
   }

   /**
    * If thread is from designer, should not parse css format.
    */
   public static void setIgnoreCSSFormat(Boolean ignore) {
      IGNORE_CSS.set(ignore);
   }

   /**
    * Check if ignore should css format.
    */
   public static boolean isIgnoreCSSFormat() {
      return IGNORE_CSS.get() != null && IGNORE_CSS.get();
   }

   /**
    * Fix selected assemblies and labels.
    * Note that the vs must be the top level viewsheet.
    * @param info the tab assembly info.
    * @param vs the top level viewsheet.
    */
   public static void fixSelected(TabVSAssemblyInfo info, Viewsheet vs) {
      fixSelected(info, vs, false);
   }

   /**
    * Fix selected assemblies and labels.
    * Note that the vs must be the top level viewsheet.
    * @param info the tab assembly info.
    * @param vs the top level viewsheet.
    * @param hideOnPrint return true if its visibility is hide-on-print.
    */
   public static void fixSelected(TabVSAssemblyInfo info, Viewsheet vs, boolean hideOnPrint) {
      String[] aAssemblies = info.getAbsoluteAssemblies();

      while(vs.isEmbedded()) {
         vs = vs.getViewsheet();
      }

      fixSelected(info, vs, hideOnPrint, aAssemblies);
   }

   public static void fixSelected(TabVSAssemblyInfo info, Viewsheet vs,
      boolean hideOnPrint, String[] aAssemblies)
   {
      int idx = -1;
      ArrayList<String> vAssemblies = new ArrayList<>();
      ArrayList<String> vlabels = new ArrayList<>();
      String[] assemblies = info.getAssemblies();
      String[] labels = info.getLabelsValue();

      for(int i = 0; i < aAssemblies.length; i++) {
         Assembly aAssembly = vs.getAssembly(aAssemblies[i]);

         if(aAssembly == null) {
            continue;
         }

         VSAssemblyInfo vsInfo = (VSAssemblyInfo) aAssembly.getInfo();

         if(vsInfo.isVisible(hideOnPrint)) {
            vAssemblies.add(assemblies[i]);
            vlabels.add(i < labels.length ? labels[i] : assemblies[i]);
         }
         else if(assemblies[i].equals(info.getSelected())) {
            idx = vAssemblies.size();
         }
      }

      String[] visibleAssemblies = new String[vAssemblies.size()];
      vAssemblies.toArray(visibleAssemblies);
      info.setAssemblies(visibleAssemblies);
      String[] visibleLabels = new String[vlabels.size()];
      vlabels.toArray(visibleLabels);
      info.setLabelsValue(visibleLabels);

      if(idx != -1 && visibleAssemblies.length > 0) {
         idx = idx < visibleAssemblies.length ? idx :
                     visibleAssemblies.length - 1;
         info.setSelected(visibleAssemblies[idx]);
      }
   }

   /**
    * Get the refs in the conditionlist in the table and its sub tables if it
    * is a mirror table.
    */
   private static DataRef[] getConditionListRefs(Worksheet ws) {
      List<DataRef> refs = new ArrayList<>();
      Assembly[] assemblies = ws.getAssemblies();

      for(int i = 0; i < assemblies.length; i++) {
         if(!(assemblies[i] instanceof TableAssembly)) {
            continue;
         }

         ConditionListWrapper wrapper =
            ((TableAssembly) assemblies[i]).getPreRuntimeConditionList();

         if(wrapper == null || wrapper.getConditionList() == null) {
            continue;
         }

         ConditionList condition = wrapper.getConditionList();

         for(int j = 0; j < condition.getConditionSize(); j += 2) {
            ConditionItem item = condition.getConditionItem(j);

            if(!refs.contains(item.getAttribute())) {
               refs.add(item.getAttribute());
            }
         }
      }

      return refs.toArray(new DataRef[] {});
   }

   /**
    * Check if the table might be changed as an embedded table.
    */
   public static boolean isDynamicTable(Viewsheet vs, TableAssembly table) {
      String tname = table.getName();
      boolean embedded = false;

      if(table instanceof EmbeddedTableAssembly) {
         embedded = true;
      }
      else if(table instanceof MirrorTableAssembly) {
         MirrorTableAssembly mtable = (MirrorTableAssembly) table;
         String bname = mtable.getAssemblyName();
         embedded = bname.equals(tname + "_O") &&
            mtable.getTableAssembly() instanceof EmbeddedTableAssembly;
      }

      if(!embedded || "true".equals(table.getProperty("auto.generate"))) {
         return false;
      }

      // this method is used to determine if an embedded table is used for input
      // or as a regular table. they are processed different regarding selection
      // (applied on pre or post aggregation). this would impact how mv works too.
      // a side-effect of this is that once an embedded table is used as a regular
      // table, it would not be available as an embedded table as target of input.
      Assembly[] arr = vs.getAssemblies();

      for(int i = 0; i < arr.length; i++) {
         VSAssembly assembly = (VSAssembly) arr[i];

         if(!tname.equals(assembly.getTableName())) {
            continue;
         }

         if(assembly instanceof InputVSAssembly) {
            return true;
         }

         if(assembly instanceof TableVSAssembly &&
            ((TableVSAssemblyInfo) assembly.getInfo()).isEmbeddedTable())
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Shrink the bound table in viewsheet, if the viewsheet is bound to direct
    * source. This only keep the column used in viewsheet to avoid pulling
    * in all column from tables.
    */
   public static void shrinkTable(Viewsheet vs, Worksheet ws) {
      if(vs == null || !vs.isLMSource() || !(ws instanceof WorksheetWrapper)) {
         return;
      }

      WorksheetWrapper wrapper = (WorksheetWrapper) ws;
      Worksheet base = wrapper.getWorksheet();
      Assembly[] arr = base.getAssemblies();

      // prepare bound tables in worksheet wrapper
      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof BoundTableAssembly) {
            String name = arr[i].getName();

            if(!wrapper.containsAssembly(name, false)) {
               wrapper.getAssembly(name, true);
            }
         }
      }

      arr = ws.getAssemblies();
      BoundTableAssembly btable = null;

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof BoundTableAssembly) {
            btable = (BoundTableAssembly) arr[i];
            break;
         }
      }

      if(btable == null) {
         return;
      }

      // prepare vs columns and hidden columns
      arr = vs.getAssemblies();
      Set bset = new HashSet(); // bound columns
      Set hset = new HashSet(); // hidden columns
      ColumnSelection cols = btable.getColumnSelection(false);

      for(int i = 0; i < arr.length; i++) {
         VSAssembly vassembly = (VSAssembly) arr[i];
         DataRef[] vsrefs = vassembly.getBindingRefs(cols);

         for(int j = 0; vsrefs != null && j < vsrefs.length; j++) {
            bset.add(vsrefs[j]);
         }

         // need to include all geo columns otherwise the geo setting is lost on refresh. (44453)
         if(vassembly instanceof ChartVSAssembly) {
            VSChartInfo cinfo = ((ChartVSAssembly) vassembly).getVSChartInfo();
            ColumnSelection geoCols = cinfo.getGeoColumns();

            if(geoCols != null && geoCols.getAttributeCount() > 0) {
               geoCols.stream().forEach(a -> bset.add(a));
            }
         }

         // @by stephenwebster, For Bug #3704
         // Add dimensions from hierarchy so they are included in column
         // selection (particularly for MV)
         if(vassembly instanceof CubeVSAssembly) {
            XCube cube = ((CubeVSAssembly) vassembly).getXCube();

            if(cube != null) {
               Enumeration<XDimension> dims = cube.getDimensions();

               while(dims.hasMoreElements()) {
                  XDimension dim = dims.nextElement();

                  if(dim instanceof VSDimension) {
                     XCubeMember[] members = ((VSDimension) dim).getLevels();

                     for(XCubeMember member : members) {
                        if(member instanceof VSDimensionMember) {
                           DataRef cubeRef = member.getDataRef();

                           if(!bset.contains(cubeRef)) {
                              bset.add(cubeRef);
                           }
                        }
                     }
                  }
               }
            }
         }

         List hidden = VSUtil.getHiddenParameterColumns(cols, vassembly);

         for(int j = 0; hidden != null && j < hidden.size(); j++) {
            hset.add(hidden.get(j));
         }

         if(vassembly instanceof DynamicBindableVSAssembly) {
            ConditionList conds =
               ((DynamicBindableVSAssembly) vassembly).getPreConditionList();

            for(int j = 0; conds != null && j < conds.getSize(); j++) {
               if(conds.isConditionItem(j)) {
                  bset.add(conds.getAttribute(j));
                  XCondition xcond = conds.getConditionItem(j).getXCondition();

                  if(xcond instanceof AssetCondition) {
                     DataRef[] dvalues =
                        ((AssetCondition) xcond).getDataRefValues();

                     for(DataRef ref : dvalues) {
                        bset.add(ref);
                     }
                  }
               }
            }
         }
         else if(vassembly instanceof ListInputVSAssembly) {
            ListBindingInfo info = ((ListInputVSAssembly) vassembly).getListBindingInfo();

            if(info == null) {
               continue;
            }

            if(info.getLabelColumn() != null) {
               bset.add(info.getLabelColumn());
            }

            if(info.getValueColumn() != null) {
               bset.add(info.getValueColumn());
            }
         }

         if(vassembly instanceof TableVSAssembly) {
            TableVSAssembly tv = (TableVSAssembly) vassembly;
            ColumnSelection hiddenCols = tv.getHiddenColumns();
            ColumnSelection pcols = tv.getColumnSelection();

            pcols.stream().filter(p -> p instanceof FormRef)
               .map(p -> (FormRef) p)
               .filter(p -> p.getOption() instanceof ComboBoxColumnOption)
               .map(p -> (ComboBoxColumnOption) p.getOption())
               .forEach(combo -> {
                  ListBindingInfo list = combo.getListBindingInfo();

                  if(list != null) {
                     if(list.getLabelColumn() != null) {
                        bset.add(list.getLabelColumn());
                     }

                     if(list.getValueColumn() != null) {
                        bset.add(list.getValueColumn());
                     }
                  }
               });

            if(hiddenCols != null) {
               for(int j = 0; j < hiddenCols.getAttributeCount(); j++) {
                  DataRef hideCol = hiddenCols.getAttribute(j);
                  ColumnRef ncolumn = findColumn(hideCol.getAttribute(), cols);

                  if(ncolumn == null) {
                     ncolumn = findColumn(hideCol.getName(), cols);
                  }

                  if(ncolumn != null) {
                     hset.add(ncolumn.clone());
                  }
               }
            }
         }

         if(vassembly instanceof ChartVSAssembly) {
            VSChartInfo cinfo = ((ChartVSAssembly) vassembly).getVSChartInfo();
            ColumnSelection geoCols = cinfo.getRTGeoColumns();

            for(int r = 0; r < geoCols.getAttributeCount(); r++) {
               Object obj = geoCols.getAttribute(r);

               if(obj instanceof VSChartGeoRef) {
                  bset.add(((VSChartGeoRef) obj).getDataRef());
               }
               else if(obj instanceof ColumnRef) {
                  bset.add(obj);
               }
            }
         }
      }

      DataRef[] vsrefs = new DataRef[bset.size()];
      ColumnRef[] hrefs = new ColumnRef[hset.size()];
      bset.toArray(vsrefs);
      hset.toArray(hrefs);
      arr = ws.getAssemblies();

      if(vsrefs.length == 0) {
         return;
      }

      String modelName = vs.getBaseEntry().getName();
      CalculateRef[] calcs = vs.getCalcFields(modelName);
      boolean keepAllCols = Drivers.getInstance().isDataCached();

      // shrink bound table one by one
      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof BoundTableAssembly) {
            TableAssembly table = (TableAssembly) arr[i];
            String name = table.getName();
            filterBoundTableColumns((BoundTableAssembly) table, ws,
                                    vsrefs, hrefs, calcs, keepAllCols);
            ColumnSelection tcolumns = table.getColumnSelection(true);
            ColumnSelection columns = new ColumnSelection();

            for(int j = 0; j < tcolumns.getAttributeCount(); j++) {
               ColumnRef tcolumn = (ColumnRef) tcolumns.getAttribute(j);
               DataRef attr = AssetUtil.getOuterAttribute(name, tcolumn);
               String dtype = tcolumn.getDataType();
               ColumnRef column = new ColumnRef(attr);
               column.setDataType(dtype);
               columns.addAttribute(column);
            }

            if(name.endsWith("_O")) {
               name = stripOuter(name);
               table = (TableAssembly) ws.getAssembly(name);

               if(table != null) {
                  ColumnSelection ncolumns = (ColumnSelection) columns.clone();
                  ColumnSelection old = table.getColumnSelection();
                  keepCalcField(old, ncolumns, calcs);
                  table.setColumnSelection(ncolumns, false);
                  table.resetColumnSelection();
               }

               name = Assembly.SELECTION + name;
               table = (TableAssembly) ws.getAssembly(name);

               if(table != null) {
                  ColumnSelection old = table.getColumnSelection();
                  ColumnSelection ncolumns = (ColumnSelection) columns.clone();
                  keepCalcField(old, ncolumns, calcs);
                  table.setColumnSelection(ncolumns, false);
               }
            }
         }
      }
   }

   /**
    * Sort the bookmark.
    */
   public static List<VSBookmarkInfo> sortBookmark(
      List<VSBookmarkInfo> bookmarks, Principal user)
   {
      List<VSBookmarkInfo> currentUserBookmark = new ArrayList<>();
      Map<IdentityID, List<VSBookmarkInfo>> ownerToBookmark = new HashMap<>();
      Set<IdentityID> ownerList = new HashSet<>();

      for(int i = 0; i < bookmarks.size(); i++) {
         VSBookmarkInfo info  = bookmarks.get(i);
         IdentityID owner = info.getOwner();

         // @by ChrisS 2014-5-22: bug1400577032157 fix#2a
         // removed storing of one (Home) bookmark, instead of sorting it

         if(user.getName().equals(owner.convertToKey()) ||
            VSBookmark.HOME_BOOKMARK.equals(info.getName()))
         {
            currentUserBookmark.add(info);
         }
         else {
            ownerList.add(owner);

            if(ownerToBookmark.get(owner) == null) {
               List<VSBookmarkInfo> list = new ArrayList<>();
               list.add(info);
               ownerToBookmark.put(owner, list);
            }
            else {
               ownerToBookmark.get(owner).add(info);
            }
         }
      }

      Comparator<VSBookmarkInfo> compare =
         (VSBookmarkInfo obj1, VSBookmarkInfo obj2) -> obj1.getName().compareToIgnoreCase(obj2.getName());

      List<VSBookmarkInfo> sortedList = new ArrayList<>();
      Collections.sort(currentUserBookmark, compare);

      // @by ChrisS 2014-5-22: bug1400577032157 fix#2a
      // removed restoring one (Home) bookmark, instead of sorting it

      sortedList.addAll(currentUserBookmark);
      List<IdentityID> owners = new ArrayList<>(ownerList);
      Collections.sort(owners);

      for(int i = 0; i < owners.size(); i++) {
         List<VSBookmarkInfo> list = ownerToBookmark.get(owners.get(i));
         Collections.sort(list, compare);
         sortedList.addAll(list);
      }

      return sortedList;
   }

   /**
    * Append all calc refs' base data ref.
    */
   public static boolean addCalcBaseRefs(ColumnSelection selection,
                                         ColumnSelection bselection,
                                         List<CalculateRef> calcs)
   {
      List<DataRef> all = getScriptBaseRef(null, new ArrayList<>(),
         calcs.toArray(new CalculateRef[0]));
      boolean changed = false;

      for(DataRef ref : all) {
         ColumnRef column = ref instanceof ColumnRef ? (ColumnRef) ref : new ColumnRef(ref);
         String name = column.getName();

         if(selection.getAttribute(name) == null) {
            ColumnRef bcolumn = (ColumnRef) (bselection == null ?
               column : bselection.getAttribute(name));

            if(bcolumn != null) {
               bcolumn = bcolumn.clone();
               bcolumn.setVisible(false);
               selection.addAttribute(bcolumn);
               changed = true;
            }
         }
      }

      return changed;
   }

   /**
    * filter the bound table assembly columns.
    */
   private static void keepCalcField(ColumnSelection old,
      ColumnSelection ncolumns, CalculateRef[] calcs)
   {
      if(calcs == null) {
         return;
      }

      List<CalculateRef> used = new ArrayList<>();

      for(int i = 0; i < old.getAttributeCount(); i++) {
         DataRef ref = old.getAttribute(i);

         if(ref instanceof CalculateRef) {
            for(int j = 0; j < calcs.length; j++) {
               if(ref.equals(calcs[j])) {
                  used.add(calcs[j]);
                  ncolumns.addAttribute((CalculateRef) calcs[j].clone());
               }
            }
         }
         else {
            for(int j = 0; j < calcs.length; j++) {
               if(ref.getAttribute().equals(calcs[j].getName())) {
                  used.add(calcs[j]);
                  ncolumns.addAttribute(old.getAttribute(i));
               }
            }
         }
      }

      addCalcBaseRefs(ncolumns, old, used);
   }

   /**
    * filter the bound table assembly columns.
    */
   private static void filterBoundTableColumns(BoundTableAssembly btable,
                                               Worksheet ws, DataRef[] vsrefs,
                                               ColumnRef[] hrefs,
                                               CalculateRef[] calcs,
                                               boolean keepAllCols)
   {
      vsrefs = decomposeAggregates(vsrefs);
      // get the data refs in the preruntimeconditions in the mirror table
      DataRef[] condrefs = getConditionListRefs(ws);
      List<DataRef> retainedRefs = new ArrayList<>();
      retainedRefs.addAll(Arrays.asList(vsrefs));
      retainedRefs.addAll(Arrays.asList(condrefs));
      retainedRefs = getScriptBaseRef(btable, retainedRefs, calcs);
      DataRef[] refArr = retainedRefs.toArray(new DataRef[]{});
      ColumnSelection cols = new ColumnSelection();
      ColumnSelection wcols = btable.getColumnSelection();

      // when jdbc result is cached, include all columns from same entity so cache can be reused
      if(keepAllCols) {
         Set<String> entities = retainedRefs.stream()
            .map(r -> r.getName())
            .filter(n -> n.contains(":"))
            .map(name -> name.substring(0, name.indexOf(':')))
            .collect(Collectors.toSet());
         wcols.stream()
            .filter(r -> r.getName().contains(":"))
            .filter(r -> {
               String name = r.getName();
               String entity = name.substring(0, name.indexOf(':'));
               return entities.contains(entity);
            })
            .forEach(col -> cols.addAttribute((DataRef) col.clone()));
      }

      for(int i = 0; i < refArr.length; i++) {
         if(refArr[i] != null) {
            // for 10.1 bc, first find from alias
            ColumnRef ncolumn = findColumn(refArr[i].getAttribute(), wcols);

            if(ncolumn == null) {
               ncolumn = findColumn(refArr[i].getName(), wcols);
            }

            if(ncolumn != null && !cols.containsAttribute(ncolumn)) {
               cols.addAttribute((DataRef) (ncolumn.clone()));
            }
         }
      }

      if(hrefs != null) {
         for(int i = 0; i < hrefs.length; i++) {
            // has been cloned
            if(!cols.containsAttribute(hrefs[i])) {
               // the ref should be visible on worksheet base table,
               // hidden ref on viewsheet table column selection is invisible
               cols.addAttribute(hrefs[i]);
            }
         }
      }

      btable.setColumnSelection(cols, false);
      btable.resetColumnSelection();
   }

   /**
    * Get all base ref used in calculate fields.
    */
   public static List<DataRef> getCalcBaseRef(List<CalculateRef> calcs) {
      List<DataRef> base = new ArrayList<>();

      for(CalculateRef calc : calcs) {
         Enumeration e = calc.getExpAttributes();

         while(e.hasMoreElements()) {
            DataRef ref = (DataRef) e.nextElement();

            if(!isOnCalcField(ref, calcs)) {
               continue;
            }

            if(!base.contains(ref)) {
               base.add(ref);
            }
         }
      }

      return base;
   }

   /**
    * Check if a field is on calc field.
    */
   public static boolean isOnCalcField(DataRef ref, List<CalculateRef> calcs) {
      if(ref == null) {
         return false;
      }

      if(ref instanceof CalculateRef) {
         return true;
      }

      for(CalculateRef calc : calcs) {
         DataRef temp = ref;

         while(temp != null) {
            if(calc.getAttribute().equals(temp.getAttribute())) {
               return true;
            }

            temp = temp instanceof DataRefWrapper ?
               ((DataRefWrapper) temp).getDataRef() : null;
         }
      }

      return false;
   }

   /**
    * Add expression ref used base ref to list.
    */
   private static List<DataRef> getScriptBaseRef(BoundTableAssembly btable,
                                                 List<DataRef> list,
                                                 CalculateRef[] calcs) {
      if(calcs == null) {
         return list;
      }

      boolean otable = btable == null ? true : btable.getName().endsWith("_O");
      List<DataRef> all = new ArrayList<>();
      all.addAll(list);

      for(int i = 0; i < calcs.length; i++) {
         DataRef ref = calcs[i];
         boolean contains = list.contains(ref);

         // if table with _O, because all calcs will be added to its mirror
         // table, here we should add all calcs base refs
         if(!otable && !contains) {
            continue;
         }

         if(contains) {
            all.add(ref);
         }

         ExpressionRef eref = null;

         while(ref instanceof DataRefWrapper) {
            ref = ((DataRefWrapper) ref).getDataRef();
            eref = ref instanceof ExpressionRef ? (ExpressionRef) ref : null;
         }

         if(eref != null) {
            addExpression(eref, all, calcs);
         }
      }

      return all;
   }

   /**
    * Add expression ref used base ref to list.
    */
   private static void addExpression(ExpressionRef eref, List<DataRef> all,
                                     CalculateRef[] calcs)
   {
      Enumeration<?> attrs = eref.getAttributes();

      while(attrs.hasMoreElements()) {
         DataRef nref = (DataRef) attrs.nextElement();

         if(!all.contains(nref)) {
            all.add(nref);

            for(CalculateRef calc : calcs) {
               if(calc.equals(nref)) {
                  ExpressionRef eeref = (ExpressionRef) calc.getDataRef();
                  addExpression(eeref, all, calcs);
                  break;
               }
            }
         }
      }
   }

   /**
    * Get hidden parameter columns.
    */
   public static List<ColumnRef> getHiddenParameterColumns(ColumnSelection cols,
                                                           VSAssembly assembly)
   {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      List<ColumnRef> hidden = new ArrayList<>();

      if(cols == null || cols.getAttributeCount() == 0) {
         return hidden;
      }

      if(!(info instanceof TableVSAssemblyInfo)) {
         return hidden;
      }

      TableHyperlinkAttr lattr = ((TableVSAssemblyInfo) info).getHyperlinkAttr();
      List<String> hiddenNames = new ArrayList();

      if(lattr != null) {
         Enumeration links = lattr.getAllHyperlinks();

         while(links.hasMoreElements()) {
            Hyperlink link = (Hyperlink) links.nextElement();

            if(link.getLink() != null && link.getLink().startsWith("hyperlink:")) {
               String field = link.getLink().substring(10);

               if(!hiddenNames.contains(field)) {
                  hiddenNames.add(field);
               }
            }

            for(String pname : link.getParameterNames()) {
               String field = link.getParameterField(pname);

               if(!hiddenNames.contains(field)) {
                  hiddenNames.add(field);
               }
            }
         }
      }

      Hyperlink rowHyperlink = ((TableVSAssemblyInfo) info).getRowHyperlink();

      if(rowHyperlink != null) {
         if(rowHyperlink.getLink() != null && rowHyperlink.getLink().startsWith("hyperlink:")) {
            String field = rowHyperlink.getLink().substring(10);

            if(!hiddenNames.contains(field)) {
               hiddenNames.add(field);
            }
         }

         for(String pname : rowHyperlink.getParameterNames()) {
            String field = rowHyperlink.getParameterField(pname);

            if(!hiddenNames.contains(field)) {
               hiddenNames.add(field);
            }
         }
      }

      TableHighlightAttr hattr =
         ((TableVSAssemblyInfo) info).getHighlightAttr();

      if(hattr != null) {
         Enumeration hlights = hattr.getAllHighlights();

         while(hlights.hasMoreElements()) {
            HighlightGroup hgroup = (HighlightGroup) hlights.nextElement();

            if(hgroup == null) {
               continue;
            }

            String[] levels = hgroup.getLevels();

            for(int i = 0; i < levels.length; i++) {
               String[] names = hgroup.getNames(levels[i]);

               for(int j = 0; j < names.length; j++) {
                  Highlight light = hgroup.getHighlight(levels[i], names[j]);
                  ConditionList list = light.getConditionGroup();

                  for(int k = 0; k < list.getSize(); k++) {
                     DataRef ref = list.getAttribute(k);

                     if(ref != null && !hiddenNames.contains(ref.getName())) {
                        hiddenNames.add(ref.getName());
                     }
                  }
               }
            }
         }
      }

      for(int i = 0; i < hiddenNames.size(); i++) {
         ColumnRef ref = findColumn(hiddenNames.get(i), cols);

         if(ref != null) {
            ref = (ColumnRef) ref.clone();
            ref.setHiddenParameter(true);
            hidden.add(ref);
         }
      }

      return hidden;
   }

   /**
    * Find column from column selection by alias.
    */
   public static ColumnRef findColumn(String name, ColumnSelection columns) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(column != null && name.equals(column.getAlias())) {
            return column;
         }
      }

      return (ColumnRef) columns.getAttribute(name);
   }

   /**
    * Decompose the aggregate refs.
    */
   private static DataRef[] decomposeAggregates(DataRef[] vsRefs) {
      List<DataRef> refs = new ArrayList<>();

      for(int i = 0; i < vsRefs.length; i++) {
         if(vsRefs[i] instanceof VSAggregateRef) {
            VSAggregateRef aref = (VSAggregateRef) vsRefs[i];
            DataRef ref = aref.getDataRef();

            if(ref instanceof ColumnRef) {
               ColumnRef col = (ColumnRef) ref;
               DataRef bref = col.getDataRef();

               if(bref instanceof AliasDataRef) {
                  ref = ((AliasDataRef) bref).getDataRef();
               }
            }

            refs.add(ref);

            if(aref.getSecondaryColumn() != null) {
               refs.add(aref.getSecondaryColumn());
            }

            continue;
         }

         refs.add(vsRefs[i]);
      }

      return refs.toArray(new DataRef[] {});
   }

   /**
    * Remove entity from a fully qualified attribute name (entity:attr).
    * @param entity null to remove any string before a colon.
    */
   public static String trimEntity(String attr, String entity) {
      return AssetUtil.trimEntity(attr, entity);
   }

   /**
    * Get the column selection for the specified viewsheet assembly.
    * @param forCondition true if the columns are used for conditions.
    */
   public static ColumnSelection getBaseColumns(VSAssembly assembly, boolean forCondition) {
      Viewsheet vs = assembly == null ? null : assembly.getViewsheet();
      Worksheet ws = vs == null ? null : vs.getBaseWorksheet();

      if(ws == null || !(assembly instanceof DynamicBindableVSAssembly)) {
         return new ColumnSelection();
      }

      return getColumnsForVSAssembly(vs, (BindableVSAssembly) assembly, forCondition);
   }

   /**
    * Get the column selection for the specified calc table assembly.
    */
   public static ColumnSelection getColumnsForCalc(VSAssembly assembly) {
      Viewsheet vs = assembly == null ? null : assembly.getViewsheet();
      Worksheet ws = vs == null ? null : vs.getBaseWorksheet();

      if(ws == null || !(assembly instanceof DynamicBindableVSAssembly)) {
         return new ColumnSelection();
      }

      return getColumnsForVSAssembly(vs, (BindableVSAssembly) assembly, false);
   }

   /**
    * Get the column selection for the specified calc table assembly.
    */
   public static ColumnSelection getColumnsForVSAssembly(Viewsheet vs,
      BindableVSAssembly bindableVSAssembly, boolean isCondition)
   {
      String tableName = bindableVSAssembly.getTableName();
      Worksheet ws = vs == null ? null : vs.getBaseWorksheet();
      Assembly tassembly = ws == null ? null : ws.getAssembly(tableName);

      if(!(tassembly instanceof TableAssembly)) {
         return new ColumnSelection();
      }

      TableAssembly assembly = (TableAssembly) tassembly;
      ColumnSelection cols = assembly.getColumnSelection(true);
      cols = (ColumnSelection) cols.clone();
      addDescription(assembly);

      if(isCondition) {
         for(int i = cols.getAttributeCount() - 1; i >= 0; i--) {
            if(VSUtil.isAggregateCalc(cols.getAttribute(i))) {
               cols.removeAttribute(i);
            }
         }
      }

      DataRef[] bindingRefs = bindableVSAssembly.getBindingRefs();
      List<CalculateRef> bindingRuntimeCalcs = new ArrayList<>();

      if(bindingRefs != null) {
         Arrays.stream(bindingRefs)
            .filter(VSDimensionRef.class::isInstance)
            .map(VSDimensionRef.class::cast)
            .filter(dim -> dim.getDataRef() instanceof CalculateRef)
            .filter(dim -> !((CalculateRef) dim.getDataRef()).isDcRuntime())
            .map(dim -> ((CalculateRef) dim.getDataRef()))
            .forEach(calc -> bindingRuntimeCalcs.add(calc));
      }

      Function<CalculateRef, Boolean> filter = calc -> calc != null && !calc.isDcRuntime() ||
         bindingRuntimeCalcs.contains(calc);
      VSUtil.appendCalcFields(cols, tableName, vs, isCondition, filter);
      cols = VSUtil.sortColumns(cols);

      return getVSColumnSelection(cols);
   }

   /**
    * Get the column selection for the specified vs assembly binding
    */
   public static ColumnSelection getColumnsForVSAssemblyBinding(RuntimeViewsheet rvs,
                                                                String tableName)
   {
      ColumnSelection cols = new ColumnSelection();
      Worksheet ws = rvs.getViewsheet().getBaseWorksheet();
      TableAssembly tableAssembly = ws != null ? (TableAssembly) ws.getAssembly(tableName) : null;

      if(tableAssembly != null) {
         cols = (ColumnSelection) tableAssembly.getColumnSelection(true).clone();
      }
      else {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         TableLens lens = null;

         try {
            lens = box.getTableData(tableName);
         }
         catch(Exception e) {
         }

         if(lens == null) {
            return cols;
         }

         for(int c = 0; c < lens.getColCount(); c++) {
            String header = Util.getHeader(lens, c).toString();
            BaseField field = new BaseField(null, header);
            field.setDataType(Util.getDataType(lens, c));

            ColumnRef columnRef = new ColumnRef(field);
            columnRef.setDataType(field.getDataType());

            if(!cols.containsAttribute(columnRef)) {
               cols.addAttribute(columnRef);
            }
         }
      }

      cols = VSUtil.sortColumns(cols);
      cols = getVSColumnSelection(cols);
      return cols;
   }

   /**
    * merge alias from bindableCols to cols
    */
   public static void mergeColumnAlias(ColumnSelection cols, ColumnSelection fromCols) {
      if(cols == null || fromCols == null) {
         return;
      }

      for(int i = 0; i < fromCols.getAttributeCount(); i++) {
         Enumeration attrs = cols.getAttributes();
         ColumnRef bref = (ColumnRef) fromCols.getAttribute(i);
         DataRef dataRef = bref.getDataRef();

         if(bref == null || !bref.isApplyingAlias() ||
            bref.getAlias() == null || dataRef == null)
         {
            continue;
         }

         while(attrs.hasMoreElements()) {
            ColumnRef ref = (ColumnRef) attrs.nextElement();
            String alias = bref.getAlias();
            DataRef dref = ref.getDataRef();

            if(dref != null && Tool.equals(dref, dataRef))
            {
               ref.setAlias(alias);

               break;
            }
         }
      }
   }

   private static void addDescription(TableAssembly assembly) {
      TableAssembly base = assembly;

      while(base instanceof MirrorTableAssembly) {
         base = ((MirrorTableAssembly) base).getTableAssembly();
      }

      if(base != null) {
         ColumnSelection cols = base.getColumnSelection(false);
         cols = (ColumnSelection) cols.clone();
         XUtil.addDescriptionsFromSource(base, cols);
      }
   }

   /**
    * Replace "." with "_" for source table name.
    */
   public static String getTableName(String table) {
      /* we allow '.' in name now so comment it out. leave it here for now in case
         should remove it in future if no need (13.1)
      if(table != null && table.startsWith(Assembly.CUBE_VS)) {
         return table;
      }

      return Tool.replaceAll(table, ".", "_");
      */
      return table;
   }

   /**
    * Fix the entry to valid entry.
    */
   public static AssetEntry fixEntry(AssetEntry entry) {
      //@by hunkliu,replace "." for "_" for directory table name
      if(entry != null && (entry.isQuery() || entry.isPhysicalTable()
                           || entry.isLogicModel()))
      {
         AssetEntry oentry = entry;
         String path = "";

         if(entry.getName().equals(entry.getPath())) {
            path = entry.getPath();
         }
         else {
            path = entry.getPath().substring(0,
                   entry.getParentPath().length() + 1)
                   + Tool.replaceAll(entry.getName(), ".", "_");
         }

         entry = new AssetEntry(entry.getScope(), entry.getType(),
                                path, entry.getUser());

         for(String key : oentry.getPropertyKeys()) {
            entry.setProperty(key, oentry.getProperty(key));
         }
      }

      return entry;
   }

   /**
    * Parse attribute properly.
    * @param elem the specified xml element.
    * @param prop the old property name.
    * @param def the default value.
    */
   public static String getAttributeStr(Element elem, String prop, String def) {
      String attr = Tool.getAttribute(elem, prop + "Value");
      attr = attr == null ? Tool.getAttribute(elem, prop) : attr;

      return attr == null ? def : attr;
   }

   /**
    * Get the drill op for the dimension.
    */
   public static String getChartDrillOp(ChartInfo cinfo, XCube cube, String field, DataRef[] refs) {
      VSDataRef ref = cinfo.getRTFieldByFullName(field);
      String op = null;

      if(ref == null || !(ref instanceof XDimensionRef)) {
         return "";
      }

      XDimensionRef dim = (XDimensionRef) ref;

      if(cube != null) {
         op = getCubeDrillOp(dim, cube, refs);
      }

      // don't use default date hierarchy if explicit hierarchy is defined. they don't mix well
      if(op == null && (cube == null || !isCubeContainRef((VSDimensionRef) dim, cube)) &&
         dim.isDateTime())
      {
         return getDateDrillOp(dim, refs, true, cube);
      }

      return op != null ? op : "";
   }

   /**
    * Get the drill op for the dimension.
    */
   public static String getCrosstabDrillOp(XCube cube, DataRef ref, DataRef[] refs) {
      if(ref == null || !(ref instanceof XDimensionRef)) {
         return "";
      }

      String op = null;
      XDimensionRef dim = (XDimensionRef) ref;

      if(cube != null) {
         op = getCubeDrillOp(dim, cube, refs);
      }

      // don't use default date hierarchy if explicit hierarchy is defined. they don't mix well
      if(op == null && (cube == null || cube.getDimension(dim.getName()) == null) &&
         dim.isDateTime())
      {
         boolean dateHierarchy = true;

         if(cube != null) {
            Enumeration iter = cube.getDimensions();

            while(iter.hasMoreElements()) {
               XDimension hier = (XDimension) iter.nextElement();

               for(int i = 0; i < hier.getLevelCount(); i++) {
                  XCubeMember member = hier.getLevelAt(i);

                  if(Objects.equals(member.getName(), dim.getName())) {
                     dateHierarchy = false;
                     break;
                  }
               }
            }
         }

         if(dateHierarchy) {
            return getDateDrillOp(dim, refs, false, cube);
         }
      }

      return op != null ? op : "";
   }

   /**
    * Get the op for the date dimension.
    */
   private static String getDateDrillOp(XDimensionRef dim, DataRef[] refs, boolean chart, XCube cube) {
      int rank0 = -1; // the ranking of dim in the hierarchy
      int rank2 = -1; // the max ranking of other dims in refs
      int[] levels = new int[refs.length];
      int level0 = dim.getDateLevel();
      boolean last = false;

      for(int i = 0; i < refs.length; i++) {
         if(!(refs[i] instanceof XDimensionRef) || !refs[i].equals(dim)) {
            continue;
         }

         XDimensionRef ref2 = (XDimensionRef) refs[i];
         levels[i] = ref2.getDateLevel();
         int rank = GraphUtil.getDateLevelRanking(levels[i]);

         if(ref2.getFullName().equals(dim.getFullName())) {
            int nlevel = GraphUtil.getNextDateLevelValue(dim, null, 0);
            rank0 = rank;
            last = nlevel < 0 || nlevel == levels[i];
         }

         rank2 = Math.max(rank2, rank);
      }

      if(rank0 <= 0) {
         return "";
      }

      int nlevel = GraphUtil.getDrillDownDateLevel(level0);
      int plevel = GraphUtil.getDrillUpDateLevel(level0);

      // date should not drill into time.
      if(XSchema.DATE.equals(dim.getDataType()) && nlevel == DateRangeRef.HOUR_INTERVAL) {
         nlevel = -1;
      }

      // if drill in continuous level. allow date level to be replaced in-place.
      // this should match the VSChartDrillHandler.drill()'s handleLevel condition. (55629)
      if(chart && cube == null && (level0 & XConstants.PART_DATE_GROUP) == 0 &&
         ((nlevel & XConstants.PART_DATE_GROUP) == 0 || nlevel == -1) &&
         ((plevel & XConstants.PART_DATE_GROUP) == 0 || plevel == -1))
      {
         String ops = "";
         boolean time = !dim.isDate();

         if(plevel != -1 && (!time || DateRangeRef.isTimeOption(plevel)) &&
            !(XSchema.TIME.equals(dim.getDataType()) && level0 == DateRangeRef.HOUR_INTERVAL))
         {
            ops += "-";
         }

         if(nlevel != -1 && (!time || DateRangeRef.isTimeOption(nlevel))) {
            ops += "+";
         }

         if(ops.length() > 0) {
            return ops;
         }
      }

      for(int level : levels) {
         if(nlevel == level && !last) {
            return "-";
         }
      }

      return rank0 >= rank2 ? (last ? "" : "+") : "-";
   }

   /**
    * Get the op for the cube dimension.
    */
   private static String getCubeDrillOp(XDimensionRef dim, XCube cube, DataRef[] refs) {
      final String fullname = dim.getAttribute();
      int dot = fullname.lastIndexOf('.');
      String dname = (dot > 0) ? fullname.substring(0, dot) : null;
      XDimension xdim = null;

      if(dname != null) {
         xdim = cube.getDimension(dname);
      }

      if(xdim == null) {
         // if dimension is not found, check if the member is in any dimension.
         // this is for user created hierarchy in vs.
         xdim = findDimension(cube, fullname);
         dname = null;
      }

      if(xdim == null) {
         return null;
      }

      int dimRank = -1; // dim rank
      int maxRank = -1; // max rank in binding (refs)
      int[] ranks = new int[refs.length];
      // true if there are date dim after the current dim that is not on the hierarchy
      boolean mixedDates = false;

      for(int i = 0; i < refs.length; i++) {
         if(!(refs[i] instanceof XDimensionRef)) {
            continue;
         }

         String name2 = refs[i].getAttribute();
         XDimensionRef ref2 = (XDimensionRef) refs[i];

         if(dname != null) {
            dot = name2.lastIndexOf('.');
            String dname2 = name2.substring(0, dot);

            if(!dname2.equals(dname)) {
               continue;
            }
         }

         ranks[i] = getScope(getDimMemberName(name2, xdim), xdim,
                             ref2.getDateLevel());

         if(ref2.getFullName().equals(dim.getFullName())) {
            dimRank = ranks[i];
         }
         // date dim of the same date col, but not on the hierarchy
         else if(dimRank > -1 && ranks[i] < 0 && ref2.isDateTime()) {
            mixedDates = true;
         }

         maxRank = Math.max(maxRank, ranks[i]);
      }

      // dim not on hierarchy, no drilling
      if(dimRank < 0) {
         return null;
      }

      // if there are date dim following this dim that is on the same date column, but
      // is not in the hierarchy, drilling will not play well.
      if(mixedDates && dim.isDateTime()) {
         return null;
      }

      for(int rank : ranks) {
         // a level at a lower level exist, can drill up
         if(dimRank == rank - 1) {
            return "-";
         }
      }

      // true if dim at the lowest level on the hierarchy
      boolean last = dimRank == xdim.getLevelCount() - 1;
      return (dimRank >= maxRank) ? (last ? "" : "+") : "-";
   }

   public static String getCaption(XDimension dm, XCubeMember member) {
      String label = member instanceof DimMember ?
         ((DimMember) member).getCaption() : member.getName();

      return AssetUtil.getFullCaption(dm) + "." + label;
   }

   /**
    * Get the dimension at the last detail level.
    */
   public static VSDimensionRef getLastDrillLevelRef(VSDimensionRef ref, XCube cube) {
      return getLastDrillLevelRef(ref, cube, false);
   }

   /**
    * Get the dimension at the last detail level.
    */
   public static VSDimensionRef getLastDrillLevelRef(VSDimensionRef ref, XCube cube,
                                                     Boolean keepOptions)
   {
      if(ref == null) {
         return null;
      }

      VSDimensionRef nref = null;

      if(cube != null) {
         nref = getCubeLastLevelRef(ref, cube);
      }

      if(nref == null && ref.isDateTime() && !isCubeContainRef(ref, cube)) {
         nref = (VSDimensionRef) ref.clone();
         int level = nref.getDateLevel();
         int nlevel = GraphUtil.getDrillUpDateLevel(level);

         // hour is top level for time
         if(XSchema.TIME.equals(ref.getDataType()) && level == DateRangeRef.HOUR_INTERVAL) {
            return null;
         }

         if(nlevel < 0 || level == nlevel) {
            return null;
         }

         if(nref.getRootRankingOption() == null && nref.getDrillRootOrder() == -1) {
            nref.setRootRankingOption(ref.getRankingOptionValue());
            nref.setDrillRootOrder(ref.getOrder());
         }

         nref.setOrder(XConstants.SORT_ASC);
         nref.setDateLevelValue(nlevel + "");

         if(nref instanceof ChartDimensionRef) {
            // date lavel changed, format shouldn't be same as before. (57107)
            ((ChartDimensionRef) nref).getTextFormat().setFormat(null);
            ((ChartDimensionRef) nref).getTextFormat().getDefaultFormat().setFormat(null);
         }

         if(!keepOptions) {
            nref.setRankingOptionValue(XCondition.NONE + "");
         }

         // don't set as time series if not interval or soring won't work
         if((nlevel & XConstants.PART_DATE_GROUP) != 0) {
            nref.setTimeSeries(false);
         }

         if(nref instanceof HighlightRef) {
            ((HighlightRef) nref).setHighlightGroup(null);
            ((HighlightRef) nref).setTextHighlightGroup(null);
         }
      }

      if(nref != null && XSchema.DATE.equals(ref.getDataType())
         && nref.getDateLevel() == DateRangeRef.HOUR_OF_DAY_PART)
      {
         return null;
      }

      if(nref instanceof VSChartDimensionRef) {
         VSChartDimensionRef cref = (VSChartDimensionRef) nref;
         AxisDescriptor axis = cref.getAxisDescriptor();

         if(axis != null) {
            axis.setAxisLabelTextFormat(new CompositeTextFormat());
         }
      }

      return nref;
   }

   /**
    * Get the dimension at the next detail level.
    * @param drillLevel true to find the next drill evel. false to find the next binding level.
    */
   public static VSDimensionRef getNextLevelRef(VSDimensionRef ref, XCube cube, boolean drillLevel) {
      if(ref == null) {
         return null;
      }

      VSDimensionRef nref = null;

      if(cube != null) {
         nref = getCubeNextLevelRef(ref, cube);
      }

      if(nref == null && ref.isDateTime() && !isCubeContainRef(ref, cube)) {
         nref = (VSDimensionRef) ref.clone();

         if(ref.isDynamic()) {
            nref.setGroupColumnValue(ref.getName());
            nref.setComboType(ComboMode.VALUE);
         }

         // should use the current level to calculate (the runtime level may not be same as
         // design level if the chart has drill up/down.
         //nref.setDateLevel(-1);
         int level = nref.getDateLevel();

         int nlevel = drillLevel ? GraphUtil.getDrillDownDateLevel(level)
            : GraphUtil.getNextDateLevel(level);

         if(nlevel < 0 || level == nlevel) {
            return null;
         }

         if(nref.getRootRankingOption() == null && nref.getDrillRootOrder() == -1) {
            nref.setRootRankingOption(ref.getRankingOptionValue());
            nref.setDrillRootOrder(ref.getOrder());
         }

         nref.setOrder(XConstants.SORT_ASC);
         nref.setDateLevelValue(nlevel + "");
         nref.setRankingOptionValue(XCondition.NONE + "");

         // don't set as time series if not interval or soring won't work
         if((nlevel & XConstants.PART_DATE_GROUP) != 0) {
            nref.setTimeSeries(false);
         }

         if(nref instanceof HighlightRef) {
            ((HighlightRef) nref).setHighlightGroup(null);
            ((HighlightRef) nref).setTextHighlightGroup(null);
         }
      }

      if(nref != null && XSchema.DATE.equals(ref.getDataType())
         && nref.getDateLevel() == DateRangeRef.HOUR_OF_DAY_PART)
      {
         return null;
      }

      if(nref instanceof VSChartDimensionRef) {
         VSChartDimensionRef cref = (VSChartDimensionRef) nref;
         AxisDescriptor axis = cref.getAxisDescriptor();

         if(axis != null) {
            axis.setAxisLabelTextFormat(new CompositeTextFormat());
         }
      }

      return nref;
   }

   public static boolean isCubeContainRef(VSDimensionRef ref, XCube cube) {
      if(cube == null) {
         return false;
      }

      Enumeration dims = cube.getDimensions();

      while(dims.hasMoreElements()) {
         XDimension xdim = (XDimension) dims.nextElement();

         if(xdim instanceof VSDimension) {
            XCubeMember[] members = ((VSDimension) xdim).getLevels();

            for(XCubeMember member : members) {
               if(member.getDataRef().getName().equals(ref.getName())) {
                  return true;
               }
            }
         }
         else if(xdim instanceof inetsoft.uql.xmla.Dimension) {
            inetsoft.uql.xmla.Dimension dim = (inetsoft.uql.xmla.Dimension) xdim;

            for(int i = 0; i < dim.getLevelCount(); i++) {
               XCubeMember member = dim.getLevelAt(i);

               if(member.getDataRef() == null) {
                  continue;
               }

               if(member.getDataRef().getName().equals(ref.getName())) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static VSDimensionRef getCubeLastLevelRef(VSDimensionRef ref, XCube cube) {
      VSDimensionRef nref = null;
      String name = ref.getAttribute();
      int dot = name.lastIndexOf('.');
      String dimname = (dot > 0) ? name.substring(0, dot) : "";
      String mbrname= name.substring(dot + 1);
      XDimension xdim = cube.getDimension(dimname);

      // dot in member name?
      if(xdim == null) {
         xdim = findDimension(cube, name);

         if(xdim != null) {
            dimname = "";
            mbrname = name;
         }
      }

      // if dimension is not found, check if the member is in any dimension.
      // this is for user created hierarchy in vs.
      if(xdim == null) {
         xdim = findDimension(cube, mbrname);
      }

      if(xdim == null) {
         return null;
      }

      int level = getScope(getDimMemberName(name, xdim), xdim, ref.getDateLevel());

      if(level > 0 && level <= xdim.getLevelCount() - 1) {
         nref = (VSDimensionRef) ref.clone();
         nref.setNamedGroupInfo(new SNamedGroupInfo());
         XCubeMember member = xdim.getLevelAt(level - 1);
         String group = (dimname.length() > 0) ? xdim.getName() + "." + member.getName()
            : member.getName();

         AttributeRef aref = new AttributeRef(group);

         if(ref.getCaption() != null) {
            aref.setCaption(getCaption(xdim, member));
         }

         ColumnRef col = new ColumnRef(aref);

         if(member.getDataRef() != null) {
            col.setDataType(member.getDataRef().getDataType());
         }
         else {
            col.setDataType(member.getType());
         }

         if(nref instanceof HyperlinkRef) {
            ((HyperlinkRef) nref).setHyperlink(null);
         }

         if(nref instanceof HighlightRef) {
            ((HighlightRef) nref).setHighlightGroup(null);
            ((HighlightRef) nref).setTextHighlightGroup(null);
         }

         nref.setOrder(XConstants.SORT_ASC);
         nref.setRankingOptionValue(XCondition.NONE + "");
         nref.setGroupColumnValue(group);
         nref.setDataRef(col);

         if(member instanceof VSDimensionMember) {
            nref.setDateLevelValue(((VSDimensionMember) member).getDateOption() + "");
         }

         col.setAlias(XMLAUtil.getDisplayName(group));
      }

      return nref;
   }

   /**
    * Get the dimension at the next detail level for cube(include user defined hierarchy).
    */
   public static VSDimensionRef getCubeNextLevelRef(VSDimensionRef ref, XCube cube) {
      VSDimensionRef nref = null;
      String name = ref.getAttribute();
      int dot = name.lastIndexOf('.');
      String dimname = (dot > 0) ? name.substring(0, dot) : "";
      String mbrname= name.substring(dot + 1);
      XDimension xdim = cube.getDimension(dimname);

      // dot in member name?
      if(xdim == null) {
         xdim = findDimension(cube, name);

         if(xdim != null) {
            dimname = "";
            mbrname = name;
         }
      }

      // if dimension is not found, check if the member is in any dimension.
      // this is for user created hierarchy in vs.
      if(xdim == null) {
         xdim = findDimension(cube, mbrname);
      }

      if(xdim == null) {
         return null;
      }

      int level = getScope(getDimMemberName(name, xdim), xdim, ref.getDateLevel());

      if(level >= 0 && level < xdim.getLevelCount() - 1) {
         nref = (VSDimensionRef) ref.clone();
         nref.setNamedGroupInfo(new SNamedGroupInfo());
         XCubeMember member = xdim.getLevelAt(level + 1);
         String group = (dimname.length() > 0)
            ? xdim.getName() + "." + member.getName()
            : member.getName();

         AttributeRef aref = new AttributeRef(group);

         if(ref.getCaption() != null) {
            aref.setCaption(getCaption(xdim, member));
         }

         ColumnRef col = new ColumnRef(aref);

         if(member.getDataRef() != null) {
            col.setDataType(member.getDataRef().getDataType());
         }
         else {
            col.setDataType(member.getType());
         }

         nref.setOrder(XConstants.SORT_ASC);
         nref.setRankingOptionValue(XCondition.NONE + "");
         nref.setGroupColumnValue(group);
         nref.setDataRef(col);

         if(member instanceof VSDimensionMember) {
            nref.setDateLevelValue(((VSDimensionMember) member).getDateOption() + "");
         }

         col.setAlias(XMLAUtil.getDisplayName(group));
      }

      return nref;
   }

   /**
    * Find the member level.
    */
   public static int getScope(String mname, XDimension dim, int dlevel) {
      for(int i = 0; i < dim.getLevelCount(); i++) {
         XCubeMember mbr = dim.getLevelAt(i);

         if(mbr.getName().equals(mname)) {
            if(mbr instanceof VSDimensionMember) {
               int level2 = ((VSDimensionMember) mbr).getDateOption();

               if(dlevel != level2) {
                  continue;
               }
            }

            return i;
         }
      }

      return -1;
   }

   /**
    * Get dimension member name.
    */
   public static String getDimMemberName(String name, XDimension dim) {
      if(name.indexOf('.') < 0) {
         return name;
      }

      String dname = dim.getName();

      if(name.startsWith(dname + ".")) {
         return name.substring(dname.length() + 1);
      }

      // dot in member name
      if(dim.getScope(name) >= 0) {
         return name;
      }

      return name.substring(name.lastIndexOf('.') + 1);
   }

   /**
    * Find the dimension.
    */
   public static XDimension findDimension(XCube cube, VSDimensionRef ref) {
      if(cube == null) {
         return null;
      }

      String name = ref.getAttribute();
      int dot = name.lastIndexOf('.');
      String dimname = (dot > 0) ? name.substring(0, dot) : "";
      String mbrname = name.substring(dot + 1);
      XDimension xdim = cube.getDimension(dimname);

      if(xdim == null) {
         xdim = VSUtil.findDimension(cube, mbrname);
      }

      if(xdim == null) {
         xdim = VSUtil.findDimension(cube, name);
      }

      return xdim;
   }

   /**
    * Find a dimension that contains the member.
    */
   public static XDimension findDimension(XCube cube, String mbrname) {
      Enumeration iter = cube.getDimensions();

      while(iter.hasMoreElements()) {
         XDimension dim0 = (XDimension) iter.nextElement();

         if(dim0.getScope(mbrname) >= 0) {
            return dim0;
         }
      }

      return null;
   }

   /**
    * Get the ranking of the dimension member in the hierarchy.
    *
    * @param pref parent ref in the dimension, ignore if null.
    */
   public static int getDimRanking(VSDimensionRef ref, XCube cube, VSDimensionRef pref) {
      if(cube != null) {
         String name = ref.getAttribute();
         XDimension xdim = findDimension(cube, ref);

         if(xdim != null) {
            if(pref != null) {
               XDimension pdim = findDimension(cube, pref);

               // not in the same dimension, meaningless to rank
               if(pdim != xdim) {
                  return 0;
               }
            }

            int dlevel = ref.getDateLevel();
            String mbrname = VSUtil.getDimMemberName(name, xdim);

            return VSUtil.getScope(mbrname, xdim, dlevel);
         }
      }

      if(ref.isDateTime()) {
         if(pref == null || pref.equals(ref)) {
            int level = ref.getDateLevel();
            return GraphUtil.getDateLevelRanking(level);
         }
      }

      return 0;
   }

   /**
    * Find the position to insert the next level.
    */
   public static int findNextIndex(DataRef[] refs, VSDataRef ref) {
      int idx = findIndex(refs, ref);
      return (idx < 0) ? refs.length : idx + 1;
   }

   /**
    * Find the position of the ref index.
    */
   public static int findIndex(DataRef[] refs, VSDataRef ref) {
      if(refs == null || refs.length <= 0) {
         return -1;
      }

      String name = ref.getFullName();

      for(int i = 0; i < refs.length; i++) {
         String attribute = null;

         // if the refs[i] is namedgroup, should check the attribute
         if(refs[i] instanceof VSDimensionRef &&
            ((VSDimensionRef) refs[i]).isNameGroup())
         {
            attribute = refs[i].getAttribute();
         }

         if((refs[i] instanceof VSDataRef &&
            name.equals(((VSDataRef) refs[i]).getFullName())) ||
            (attribute != null && attribute.equals(ref.getAttribute())))
         {
            return i;
         }
      }

      return -1;
   }

   /**
    * Check the ref match the given name or not.
    */
   public static boolean matchRef(DataRef ref, String name) {
      if(name != null) {
         if(matchRefName(ref.getName(), name)) {
            return true;
         }

         while(ref instanceof DataRefWrapper) {
            ref = ((DataRefWrapper) ref).getDataRef();

            if(ref != null && matchRefName(ref.getName(), name)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check the ref match the given ref name or not.
    */
   public static boolean matchRefName(String refName, String name) {
      if(refName != null && refName.indexOf("(") > 0 &&
         refName.indexOf(")") > 0)
      {
         refName = refName.substring(refName.indexOf("(") + 1,
            refName.indexOf(")"));
      }

      return Tool.equals(refName, name);
   }

   /**
    * Get the name match the given ref name or not.
    */
   public static String getMatchRefName(String refName, String name) {
      if(refName != null && refName.indexOf("(") > 0 &&
         refName.indexOf(")") > 0)
      {
         String aname = refName.substring(refName.indexOf("(") + 1,
            refName.indexOf(")"));
         name = refName.replace(aname, name);
      }

      return name;
   }

   /**
    * Remove a row from the two dimensional array.
    */
   public static DataRef[] removeRow(DataRef[] arr, int row) {
      DataRef[] narr = (DataRef[]) Array.newInstance(
         getItemComponentType(arr), arr.length - 1);
      System.arraycopy(arr, 0, narr, 0, row);
      System.arraycopy(arr, row + 1, narr, row, arr.length - row - 1);
      return narr;
   }

   /**
    * Check the data ref is on aggregate value calculate ref or not.
    */
   public static boolean isAggregateCalc(DataRef ref) {
      return ref instanceof CalculateRef &&
         !((CalculateRef) ref).isBaseOnDetail() &&
         (ref.getRefType() & DataRef.CUBE_MEASURE) != DataRef.CUBE_MEASURE;
   }

   /**
    * Return if target ref is a calculated field added for donut chart which created in wizard.
    */
   public static boolean isPreparedCalcField(DataRef ref) {
      if(ref == null) {
         return false;
      }

      if(ref instanceof DataRefWrapper) {
         if(isPreparedCalcField(((DataRefWrapper) ref).getDataRef())) {
            return true;
         }
      }

      // the inner ref may not be CalculateRef
      if(ref instanceof VSChartDimensionRef) {
         String col = ((VSChartDimensionRef) ref).getGroupColumnValue();

         if(col != null && col.startsWith("Range@")) {
            return true;
         }
      }
      else if(ref instanceof VSChartAggregateRef) {
         String col = ((VSChartAggregateRef) ref).getColumnValue();

         if(col != null && col.startsWith("Total@")) {
            return true;
         }
      }

      String name = ref.getName();

      // added for donut/histogram chart which created in vs wizard.
      return (ref instanceof CalculateRef || ref instanceof AttributeRef) && name != null &&
         (name.startsWith("Total@") || name.startsWith("Range@"));
   }

   /**
    * Check the ref is fake.
    */
   public static boolean isFake(DataRef ref) {
      while(ref != null) {
         if(ref instanceof CalculateRef && ((CalculateRef) ref).isFake()) {
            return true;
         }

         ref = ref instanceof DataRefWrapper ?
            ((DataRefWrapper) ref).getDataRef() : null;
      }

      return false;
   }

   /**
    * Check if supports aggregate on aggregate.
    */
   public static boolean supportsAOA(IAggregateRef aref, AggregateRef[] aggrs) {
      if(aref == null) {
         return true;
      }

      // add all aggregates
      if(VSUtil.isAggregateCalc(aref.getDataRef())) {
         CalculateRef cref = (CalculateRef) aref.getDataRef();
         ExpressionRef eref = (ExpressionRef) cref.getDataRef();
         List<String> names = new ArrayList<>();
         List<AggregateRef> subs = VSUtil.findAggregate(aggrs, names, eref.getExpression());

         for(int i = 0; i < subs.size(); i++) {
            if(!VSUtil.supportsAOA(subs.get(i), aggrs)) {
               return false;
            }
         }
      }
      else {
         AggregateFormula formula = aref.getFormula();

         if(formula != null) {
            AggregateFormula parent = formula.getParentFormula();

            if(parent == null) {
               return false;
            }
         }
      }

      return true;
   }

   public static boolean requiresTwoColumns(AggregateFormula formula) {
      return formula.isTwoColumns() && formula != AggregateFormula.FIRST &&
         formula != AggregateFormula.LAST;
   }

   /**
    * Find attribute refs which used in expression.
    * @param expression the specified expression.
    * @return an List which contains used Aggregate refs.
    */
   public static List<AggregateRef> findAggregate(AggregateRef[] allagg,
                                                  List<String> matchNames,
                                                  String expression)
   {
      return VSUtil.findAggregate(allagg, matchNames, expression, null);
   }

   /**
    * Find attribute refs which used in expression.
    * @param expression the specified expression.
    * @return an List which contains used Aggregate refs.
    */
   private static List<AggregateRef> findAggregate(AggregateRef[] allagg,
                                                   List<String> matchNames,
                                                   String expression,
                                                   Viewsheet vs)
   {
      List<AggregateRef> attrs = new ArrayList<>();

      if(allagg != null) {
         // clear the dataref's entity
         List<AggregateRef> all = new ArrayList<>();

         for(int i = 0; i < allagg.length; i++) {
            AggregateRef aref = (AggregateRef) allagg[i].clone();
            DataRef ref = aref.getDataRef();
            String name = ref.getAttribute();
            boolean changed = false;

            // auto create aggregate ref, not contains entity
            if(vs == null || !vs.isAOARef(aref)) {
               if(ref instanceof ColumnRef) {
                  if(((ColumnRef) ref).getDataRef() instanceof AliasDataRef) {
                     AliasDataRef alref = (AliasDataRef) ((ColumnRef) ref).getDataRef();
                     name = alref.getDataRef().getAttribute();
                  }
                  else {
                     ref = VSUtil.getVSColumnRef((ColumnRef) ref);
                     aref.setDataRef(ref);
                     changed = true;
                  }
               }

               if(!changed) {
                  if(name == null || name.length() == 0) {
                     name = ref.getName();
                  }

                  AttributeRef attr = new AttributeRef(null, name);

                  // data type in ref (AttributeRef) may not be accurate since the can
                  // be parsed from xml (where data type is not saved) or created fresh
                  // without setting type (e.g. ExpressionRef.AttributeEnumeration),
                  // so we keep the type unless it's the default string. (49095)
                  if(XSchema.STRING.equals(ref.getDataType()) && aref.getFormula() != null &&
                     aref.getFormula().getDataType() != null)
                  {
                     attr.setDataType(aref.getFormula().getDataType());
                  }
                  else {
                     attr.setDataType(ref.getDataType());
                  }

                  aref.setDataRef(attr);
               }
            }

            all.add(aref);
         }

         addUsedAggregateRef(all, attrs, matchNames, expression);
      }

      return attrs;
   }

   /**
    * Get all aggregate refs which used for this table.
    */
   public static AggregateRef[] getAggregates(Viewsheet vs, String tname, boolean aggtreeOnly) {
      Worksheet ws = vs.getBaseWorksheet();
      List<AggregateRef> aggres = new ArrayList<>();
      Set<String> exist = new HashSet<>();
      // add user create agg calc first
      AggregateRef[] vsaggres = vs.getAggrFields(tname);

      if(vsaggres != null) {
         TableAssembly table = ws != null ? (TableAssembly) ws.getAssembly(tname) : null;
         ColumnSelection cols = table != null ? table.getColumnSelection(false) : null;

         for(int i = 0; i < vsaggres.length; i++) {
            AggregateRef aggRef = (AggregateRef) Tool.clone(vsaggres[i]);

            // don't change the base when only using aggRef for gui column tree. (60537)
            if(!aggtreeOnly) {
               DataRef base = vsaggres[i].getDataRef();
               DataRef base0 = cols != null ? cols.getAttribute(base.getAttribute(), true) : null;

               if(base0 != null) {
                  aggRef.setDataRef(base0);
               }
            }

            aggres.add(aggRef);
            exist.add(aggRef.toString());
         }
      }

      // add aoa aggregate ref
      if(!aggtreeOnly) {
         vsaggres = vs.getAOAAggrFields();

         if(vsaggres != null) {
            for(int i = 0; i < vsaggres.length; i++) {
               String str = vsaggres[i].toString();

               if(!exist.contains(str)) {
                  exist.add(str);
                  aggres.add(vsaggres[i]);
               }
            }
         }
      }

      Assembly[] assembies = vs.getAssemblies();

      for(int i = 0; i < assembies.length; i++) {
         if(assembies[i] instanceof BindableVSAssembly &&
            !(assembies[i] instanceof InputVSAssembly))
         {
            BindableVSAssembly bind = ((BindableVSAssembly) assembies[i]);
            String vtable = bind.getTableName();

            if(Tool.equals(tname, vtable)) {
               if(bind instanceof CubeVSAssembly) {
                  DataRef[] refs = bind.getBindingRefs();

                  for(int j = 0; j < refs.length; j++) {
                     if(refs[j] instanceof AggregateRef) {
                        AggregateRef xref = (AggregateRef) refs[j];
                        String str = xref.toString();

                        if(!exist.contains(str)) {
                           exist.add(str);
                           aggres.add(xref);
                        }
                     }
                     else if(refs[j] instanceof VSAggregateRef) {
                        VSAggregateRef xref = (VSAggregateRef) refs[j];
                        DataRef dref = xref.getDataRef();

                        if(dref == null ||
                           aggtreeOnly && dref instanceof CalculateRef &&
                           !((CalculateRef) dref).isBaseOnDetail() ||
                           xref.getFormula() == AggregateFormula.NONE)
                        {
                           continue;
                        }

                        DataRef rootRef = VSUtil.getRootRef(dref);
                        AttributeRef ref = new AttributeRef(null, rootRef.getAttribute());
                        ref.setDataType(rootRef.getDataType());
                        DataRef xref2 = xref.getSecondaryColumn();
                        DataRef ref2 = xref2 == null ? null :
                           new AttributeRef(null, xref2.getAttribute());

                        AggregateRef aref = new AggregateRef(ref, ref2, xref.getFormula());
                        aref.setN(xref.getN());
                        String str = aref.toString();

                        if(!exist.contains(str)) {
                           exist.add(str);
                           aggres.add(aref);
                        }
                     }
                  }
               }
               else if(bind instanceof OutputVSAssembly) {
                  OutputVSAssembly out = (OutputVSAssembly) bind;
                  ScalarBindingInfo sbinfo = out.getScalarBindingInfo();

                  if(sbinfo != null && sbinfo.getColumn() != null) {
                     DataRef rootRef = VSUtil.getRootRef(sbinfo.getColumn());
                     AttributeRef ref = new AttributeRef(null, rootRef.getAttribute());
                     ref.setDataType(rootRef.getDataType());
                     DataRef ref2 = VSUtil.getRootRef(sbinfo.getSecondaryColumn());
                     ref2 = ref2 == null ? null :
                        new AttributeRef(null, ref2.getAttribute());
                     AggregateFormula formula = sbinfo.getAggregateFormula();

                     AggregateRef aref = new AggregateRef(ref, ref2, formula);
                     aref.setN(sbinfo.getN());
                     String str = aref.toString();

                     if(!aggtreeOnly && !exist.contains(str)) {
                        exist.add(str);
                        aggres.add(aref);
                     }
                  }
               }
            }
         }
      }

      AggregateRef[] aggs = aggres.toArray(new AggregateRef[0]);
      return aggs;
   }

   /**
    * Find attribute refs which used in expression.
    * @param expression the specified expression.
    * @return an List which contains used Aggregate refs.
    */
   public static List<AggregateRef> findAggregate(Viewsheet vs,
      String tname, List<String> matchNames, String expression)
   {
      AggregateRef[] aggs = VSUtil.getAggregates(vs, tname, false);
      return VSUtil.findAggregate(aggs, matchNames, expression, vs);
   }

   /**
    * Add script used aggregate ref to target list.
    */
   private static void addUsedAggregateRef(List<AggregateRef> available,
      List<AggregateRef> target, List<String> matchNames, String expression)
   {
      // to ignore case Sum and sum
      expression = expression.toUpperCase();

      if(available != null) {
         for(int i = 0; i < available.size(); i++) {
            AggregateRef aref = available.get(i);

            if(aref.getFormula() == null) {
               continue;
            }

            String tag = VSUtil.getAggregateString(aref, false);
            int idx = expression.indexOf(tag.toUpperCase());

            if(idx >= 0) {
               if(!matchNames.contains(tag)) {
                  target.add(aref);
                  matchNames.add(tag);
               }

               continue;
            }

            tag = VSUtil.getAggregateString(aref, true);
            idx = expression.indexOf(tag.toUpperCase());

            if(idx >= 0) {
               if(!matchNames.contains(tag)) {
                  target.add(aref);
                  matchNames.add(tag);
               }

               continue;
            }

            tag = aref.toString();
            idx = expression.indexOf(tag.toUpperCase());

            if(idx >= 0) {
               if(!matchNames.contains(tag)) {
                  target.add(aref);
                  matchNames.add(tag);
               }

               continue;
            }

            DataRef ref = aref.getDataRef();

            if(VSUtil.isAggregateAlias(ref.getName())) {
               // e.g. field['Sum(TotalPurchased)']
               tag = "field['" + ref.getName() + "']";
               idx = expression.indexOf(tag.toUpperCase());

               if(idx >= 0) {
                  if(!matchNames.contains(tag)) {
                     target.add(aref);
                     matchNames.add(tag);
                  }

                  continue;
               }

               // e.g. field['Sum([TotalPurchased])'] or field['Max([Month(Date)])']
               tag = replaceOne(tag, "(", "([", true);
               tag = replaceOne(tag, ")", "])", false);
               idx = expression.indexOf(tag.toUpperCase());

               if(idx >= 0) {
                  if(!matchNames.contains(tag)) {
                     target.add(aref);
                     matchNames.add(tag);
                  }

                  continue;
               }

               // formula with 2nd column
               tag = tag.replace(", ", "], [");
               idx = expression.indexOf(tag.toUpperCase());

               if(idx >= 0) {
                  if(!matchNames.contains(tag)) {
                     target.add(aref);
                     matchNames.add(tag);
                  }

                  continue;
               }

               // formula with N
               tag = tag.replace(", [", ", ");
               tag = tag.replace("])", ")");
               idx = expression.indexOf(tag.toUpperCase());

               if(idx >= 0) {
                  if(!matchNames.contains(tag)) {
                     target.add(aref);
                     matchNames.add(tag);
                  }

                  continue;
               }
            }
         }
      }
   }

   private static String replaceOne(String str, String from, String to, boolean first) {
      int idx = first ? str.indexOf(from) : str.lastIndexOf(from);

      if(idx >= 0) {
         return str.substring(0, idx) + to + str.substring(idx + from.length());
      }

      return str;
   }

   /**
    * Check the string represent the aggregate ref's alias or not.
    */
   private static boolean isAggregateAlias(String name) {
      if(name.indexOf(')') > name.indexOf('(') && name.indexOf('(') > 0) {
         AggregateFormula[] aggs = AggregateFormula.getFormulas();

         for(int i = 0; i < aggs.length; i++) {
            int idx = name.indexOf(aggs[i].getFormulaName());

            if(idx == 0) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Create the aggregate ref with formula function name.
    */
   public static AggregateRef createAliasAgg(AggregateRef ref, boolean aggrCalc) {
      AggregateFormula formula = ref.getFormula();

      if(formula == null) {
         return ref;
      }

      DataRef col = ref.getDataRef();
      DataRef root = VSUtil.getRootRef(col);
      StringBuilder sb = new StringBuilder();
      String fname = formula.getFormulaName();
      DataRef second = ref.getSecondaryColumn();
      boolean hasN = ref.getFormula() != null && ref.getFormula().hasN();

      String str = hasN ? (", " + ref.getN()) : (second == null ? "" : (", " + second.getName()));
      sb.append(fname);
      sb.append("(");
      sb.append(ref.getName());
      sb.append(str);
      sb.append(")");
      AliasDataRef aref = new AliasDataRef(sb.toString(), root);
      aref.setRefType(col.getRefType());
      aref.setAggrCalc(aggrCalc);
      ColumnRef acol = new ColumnRef(aref);
      // need to maintain the same type. (50819, 50836)
      // since alias for aggregate in calcfield is really an alias to the DETAIL/BASE column,
      // we should set the column type of the base.
      // Bug #53753 need to use the ref dataType in the case it's different from the underlying ref.
//      acol.setDataType(ref.getDataRef().getDataType());
      acol.setDataType(ref.getDataType());
      AggregateRef aggRef = new AggregateRef(acol, ref.getSecondaryColumn(), formula);
      aggRef.setN(ref.getN());

      return aggRef;
   }

   /**
    * Get the aggregate ref string present.
    */
   public static String getAggregateString(AggregateRef aref, boolean addQuote) {
      AggregateFormula formula = aref.getFormula();
      DataRef ref = aref.getDataRef();
      DataRef ref2 = aref.getSecondaryColumn();
      String result = null;

      if(formula != null && formula.isTwoColumns() && ref2 != null) {
         result = addQuote ? formula.getFormulaName() + "([" +
            ref.getAttribute() + "], [" + ref2.getAttribute() + "])" :
            formula.getFormulaName() + "(" + ref.getAttribute() + ", " +
            ref2.getAttribute() + ")";
         result = "field['" + result + "']";
         return result;
      }

      result = formula == null ? "null" : formula.getFormulaName();

      if(aref.getN() > 0 && formula.hasN()) {
         result = addQuote ? result + "([" + ref.getAttribute() + "], " + aref.getN() + ")" :
            result + "(" + ref.getAttribute() + ", " + aref.getN() + ")";
      }
      else {
         result = addQuote ? result + "([" + ref.getAttribute() + "])" :
            result + "(" + ref.getAttribute() + ")";
      }

      result = "field['" + result + "']";
      return result;
   }

   /**
    * Append the user create calc field to column selection.
    */
   public static void appendCalcFields(ColumnSelection sel, String tname,
                                       Viewsheet vs)
   {
      appendCalcFields(sel, tname, vs, false);
   }

   /**
    * Append the user create calc field to column selection.
    */
   public static void appendCalcFields(ColumnSelection sel, String tname,
                                       Viewsheet vs, boolean detailOnly)
   {
      appendCalcFields(sel, tname, vs, detailOnly, false);
   }

   /**
    * Append the user create calc field to column selection.
    */
   public static void appendCalcFields(ColumnSelection sel, String tname,
                                       Viewsheet vs, boolean detailOnly, boolean ignoreRT)
   {
      appendCalcFields(sel, tname, vs, detailOnly, calc -> !ignoreRT || !calc.isDcRuntime());
   }

   /**
    * Append the user create calc field to column selection.
    */
   public static void appendCalcFields(ColumnSelection sel, String tname,
                                       Viewsheet vs, boolean detailOnly,
                                       Function<CalculateRef, Boolean> filter)
   {
      CalculateRef[] calcs = vs == null ? null : vs.getCalcFields(tname);

      if(calcs != null) {
         for(int i = 0; i < calcs.length; i++) {
            if((!detailOnly || calcs[i].isBaseOnDetail()) &&
               (filter == null || filter.apply(calcs[i])))
            {
               calcs[i].setVisible(true);
               CalculateRef nref = (CalculateRef) calcs[i].clone();
               sel.removeAttribute(nref);
               sel.addAttribute(nref);
            }
         }
      }
   }

   public static void appendCalcFieldsForTree(ColumnSelection selection, String tname,
                                              Viewsheet vs)
   {
      CalculateRef[] calcs = vs == null ? null : vs.getCalcFields(tname);

      if(calcs == null) {
         return;
      }

      for(int i = 0; i < calcs.length; i++) {
         if(calcs[i].isDcRuntime() || VSUtil.isPreparedCalcField(calcs[i])) {
            continue;
         }

         calcs[i].setVisible(true);
         CalculateRef nref = (CalculateRef) calcs[i].clone();
         selection.removeAttribute(nref);
         selection.addAttribute(nref);
      }
   }

   public static ColumnSelection removePreparedCalcFields(ColumnSelection columns) {
      ColumnSelection ncolumns = new ColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef column = columns.getAttribute(i);

         if(!VSUtil.isPreparedCalcField(column)) {
            ncolumns.addAttribute(column);
         }
      }

      return ncolumns;
   }

   /**
    * Append the user create calc field to list.
    */
   public static void appendCalcFields(Collection<ColumnRef> list, TableAssembly assembly,
                                       Viewsheet vs, boolean excludeAggCalc,
                                       boolean excludePreparedCalc)
   {
      String tname = assembly.getName();
      CalculateRef[] calcs = vs == null ? null : vs.getCalcFields(tname);

      if(calcs != null) {
         for(int i = 0; i < calcs.length; i++) {
            if(!calcs[i].isDcRuntime() && !list.contains(calcs[i]) &&
               (!excludeAggCalc || calcs[i].isBaseOnDetail())
               && (!excludePreparedCalc || !VSUtil.isPreparedCalcField(calcs[i])))
            {
               list.add(calcs[i]);
            }
         }
      }
   }

   /**
    * Get the item type of an array or multi-dimensional array.
    */
   private static Class getItemComponentType(Object arr) {
      Class cls = arr.getClass();

      while(cls.isArray()) {
         cls = cls.getComponentType();
      }

      return cls;
   }

   /**
    * Rename dataRef of assembly columns after calculate field name changed.
    */
   public static void renameDataRef(DataRef tref, String nname) {
      if(tref instanceof ColumnRef) {
         DataRef ref = ((ColumnRef) tref).getDataRef();
         nname = getMatchRefName(ref.getName(), nname);

         if(ref instanceof AttributeRef) {
            ref = new AttributeRef(null, nname);
            ((ColumnRef) tref).setDataRef(ref);
            ((ColumnRef) tref).setView(nname);
         }
         else if(ref instanceof ExpressionRef) {
            ((ExpressionRef) ref).setName(nname);
         }
      }
      else if(tref instanceof VSDimensionRef) {
         VSDimensionRef dref = (VSDimensionRef) tref;
         setVSDimensionRefName(dref, nname);
         VSUtil.renameDataRef(dref.getDataRef(), nname);
      }
   }

   /**
    * Set VSAggregateRef name.
    */
   public static void setVSAggregateRefName(VSAggregateRef aref,
                                            String nname)
   {
      if(aref.getCaption() != null) {
         aref.setCaption(nname);
      }

      aref.setColumnValue(nname);
   }

   /**
    * Set VSDimensionRef name.
    */
   public static void setVSDimensionRefName(VSDimensionRef dref,
                                            String nname)
   {
      if(dref.getCaption() != null) {
         dref.setCaption(nname);
      }

      dref.setGroupColumnValue(nname);
   }

   /**
    * Normalize aggregate name, Sum(Sum(col)) -> Sum(col)
    */
   public static String normalizeAggregateName(String aggr) {
      if(aggr != null) {
         aggr = ChartAggregateRef.getBaseName(aggr);

         int idx1 = aggr.indexOf('(');

         if(idx1 > 0) {
            int idx2 = aggr.indexOf('(', idx1 + 1);
            int idx3 = aggr.lastIndexOf(')');

            if(idx2 > 0 && idx3 > idx2) {
               String formula1 = aggr.substring(0, idx1);
               String formula2 = aggr.substring(idx1 + 1, idx2);

               // this method is for stripping the pushdown aggregates so we should
               // check for AoA cases. (49926)
               if(!isParentFormula(formula2, formula1)) {
                  return aggr;
               }

               aggr = aggr.substring(idx1 + 1, idx3);
               int idx4 = aggr.lastIndexOf(')');

               if(idx4 >= 0 && idx4 < aggr.length() - 1) {
                  // outer function had additional parameters, drop them
                  aggr = aggr.substring(0, idx4 + 1);
               }
            }
         }
      }

      return aggr;
   }

   private static boolean isParentFormula(String formula, String parentFormula) {
      AggregateFormula formula1 = AggregateFormula.getFormula(formula);
      AggregateFormula formula2 = AggregateFormula.getFormula(parentFormula);

      return formula1 != null && formula2 != null && formula2 == formula1.getParentFormula();
   }

   /**
    * Create column ref represent the calc ref for aggregate push down usage.
    */
   public static String createAOAExpression(List<AggregateRef> subaggs,
                                            String expression,
                                            List<String> matchNames,
                                            Viewsheet vs)
   {
      String newExp = expression;

      for(int i = 0; i < subaggs.size(); i++) {
         AggregateRef aref = VSUtil.createAliasAgg(subaggs.get(i), false);
         AggregateFormula formula = aref.getFormula();
         AggregateFormula pfromula = formula.getParentFormula();
         pfromula = pfromula == null ? AggregateFormula.NONE : pfromula;

         if(!Tool.equals(formula, pfromula)) {
            aref.setFormula(pfromula);
            vs.addAOARef(aref);
            String str = VSUtil.getAggregateString(aref, false);
            newExp = newExp.replace(matchNames.get(i), str);
         }
      }

      return newExp;
   }

   /**
    * Get the inner most data ref.
    */
   public static DataRef getRootRef(DataRef ref, boolean checkAlias) {
      while(ref instanceof DataRefWrapper) {
         ref = ((DataRefWrapper) ref).getDataRef();
      }

      if(checkAlias && ref instanceof AliasDataRef) {
         ref = ((AliasDataRef) ref).getDataRef();
      }

      return ref;
   }

   /**
    * Get the inner most data ref.
    */
   private static DataRef getRootRef(DataRef ref) {
      return getRootRef(ref, true);
   }

   /**
    * Add data ref from condition list.
    */
   public static void addConditionListRef(ConditionListWrapper conlist,
                                          List<DataRef> datarefs)
   {
      if(conlist != null) {
         for(int i = 0; i < conlist.getConditionSize(); i += 2) {
            ConditionItem item = conlist.getConditionItem(i);
            DataRef ref = item.getAttribute();

            if(ref != null) {
               datarefs.add(ref);
            }

            Condition cond = item.getCondition();

            if(cond != null) {
                DataRef[] drefs = cond.getDataRefValues();

                if(drefs != null) {
                   for(int j = 0; j < drefs.length; j++) {
                      if(drefs[j] != null) {
                         datarefs.add(drefs[j]);
                      }
                   }
                }
            }
         }
      }
   }

   /**
    * Rename data ref from condition list.
    */
   public static void renameConditionListRef(ConditionList conlist,
                                             String oname, String nname)
   {
      if(conlist != null) {
         for(int i = 0; i < conlist.getConditionSize(); i += 2) {
            ConditionItem item = conlist.getConditionItem(i);
            DataRef cref = item.getAttribute();

            if(VSUtil.matchRef(cref, oname)) {
               VSUtil.renameDataRef(cref, nname);
               item.setAttribute(cref);
            }

            Condition cond = item.getCondition();

            if(cond != null) {
               DataRef[] drefs = cond.getDataRefValues();

               if(drefs != null) {
                  for(int j = 0; j < drefs.length; j++) {
                     if(VSUtil.matchRef(drefs[j], oname)) {
                        VSUtil.renameDataRef(drefs[j], nname);
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Remove data ref from condition list.
    */
   public static void removeConditionListRef(ConditionList conlist,
                                             String oname)
   {
      if(conlist != null) {
         for(int i = conlist.getConditionSize() - 1; i >= 0; i -= 2) {
            ConditionItem item = conlist.getConditionItem(i);
            DataRef cref = item.getAttribute();

            if(VSUtil.matchRef(cref, oname)) {
               conlist.remove(i);

               if(i > 0) {
                 conlist.remove(i - 1);
               }
               else if(i == 0 && (conlist.getConditionSize() > 0)) {
                 conlist.remove(0);
               }

               continue;
            }

            Condition cond = item.getCondition();

            if(cond != null) {
               DataRef[] drefs = cond.getDataRefValues();

               if(drefs != null) {
                  for(int j = 0; j < drefs.length; j++) {
                     if(VSUtil.matchRef(drefs[j], oname)) {
                        conlist.remove(i);

                        if(i > 0) {
                           conlist.remove(i - 1);
                        }
                        else if(i == 0 && (conlist.getConditionSize() > 0)) {
                           conlist.remove(0);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Add data ref from condition in highlights.
    */
   public static void addHLConditionListRef(HighlightGroup hgroup,
                                            List<DataRef> datarefs)
   {
      if(hgroup == null) {
         return;
      }

      String[] levels = hgroup.getLevels();

      for(int i = 0; i < levels.length; i++) {
         String level = levels[i];
         String[] names = hgroup.getNames(level);

         for(int j = 0; j < names.length; j++) {
            Highlight hl = hgroup.getHighlight(level, names[j]);
            ConditionList list = hl.getConditionGroup();
            addConditionListRef(list, datarefs);
         }
      }
   }

   /**
    * Rename data ref from condition in highlights.
    */
   public static void renameHLConditionListRef(HighlightGroup hgroup,
                                               String oname, String nname)
   {
      if(hgroup == null) {
         return;
      }

      String[] levels = hgroup.getLevels();

      for(int i = 0; i < levels.length; i++) {
         String level = levels[i];
         String[] names = hgroup.getNames(level);

         for(int j = 0; j < names.length; j++) {
            Highlight hl = hgroup.getHighlight(level, names[j]);
            ConditionList list = hl.getConditionGroup();
            renameConditionListRef(list, oname, nname);
         }
      }
   }

   /**
    * Remove data ref from condition in highlights.
    */
   public static void removeHLConditionListRef(HighlightGroup hgroup,
                                               String oname)
   {
      if(hgroup == null) {
         return;
      }

      String[] levels = hgroup.getLevels();

      for(int i = 0; i < levels.length; i++) {
         String level = levels[i];
         String[] names = hgroup.getNames(level);

         for(int j = 0; j < names.length; j++) {
            Highlight hl = hgroup.getHighlight(level, names[j]);
            ConditionList list = hl.getConditionGroup();
            removeConditionListRef(list, oname);
         }
      }
   }

   /**
    * Add data ref from Hyperlink.
    */
   public static void addHyperlinkRef(Hyperlink link, List<DataRef> datarefs,
                                      CalculateRef[] crefs)
   {
      if(crefs == null || link == null) {
         return;
      }

      List<String> refNames = new ArrayList<>();

      for(int i = 0; i < crefs.length; i++) {
         refNames.add(crefs[i].getName());
      }

      for(int i = 0 ; link != null && i < link.getParameterCount(); i++) {
         for(String pname : link.getParameterNames()) {
            String field = link.getParameterField(pname);

            if(refNames.contains(field)) {
               datarefs.add(crefs[refNames.indexOf(field)]);
            }
         }
      }
   }

   /**
    * Rename data ref from Hyperlink.
    */
   public static void renameHyperlinkRef(Hyperlink link,
                                         String oname, String nname)
   {
      if(link == null) {
         return;
      }

      for(String pname : link.getParameterNames()) {
         String field = link.getParameterField(pname);

         if(matchRefName(field, oname)) {
            link.setParameterField(pname, getMatchRefName(field, nname));
            link.setParameterLabel(pname, getMatchRefName(field, nname));
         }
      }
   }

   /**
    * Remove data ref from Hyperlink.
    */
   public static void removeHyperlinkRef(Hyperlink link, String oname) {
      if(link == null) {
         return;
      }

      for(String pname : link.getParameterNames()) {
         String field = link.getParameterField(pname);

         if(matchRefName(field, oname)) {
            link.removeParameterField(field);
         }
      }
   }

   /**
    * Rename VSDimension.
    */
   public static void renameVSDimension(VSDimension dim, String oname,
                                        String nname)
   {
      if(dim != null) {
         XCubeMember[] merbers = dim.getLevels();

         for(int i = 0; i < merbers.length; i++) {
            DataRef ref = merbers[i].getDataRef();

            if(VSUtil.matchRef(ref, oname)) {
               VSUtil.renameDataRef(ref, nname);
            }
         }
      }
   }

   /**
    * Add data ref from VSDimension.
    */
   public static void addVSDimension(VSDimension dim, List<DataRef> datarefs) {
      if(dim != null) {
         XCubeMember[] merbers = dim.getLevels();

         for(int i = 0; i < merbers.length; i++) {
            DataRef ref = merbers[i].getDataRef();

            if(ref != null) {
               datarefs.add(ref);
            }
         }
      }
   }

   /**
    * Check contains data ref from VSDimension.
    */
   public static void removeVSDimension(VSDimension dim, String ref) {
      if(dim != null) {
         XCubeMember[] merbers = dim.getLevels();

         for(int i = 0; i < merbers.length; i++) {
            DataRef dref = merbers[i].getDataRef();

            if(VSUtil.matchRef(dref, ref)) {
               dim.removeLevel(ref);
               break;
            }
         }
      }
   }

   /**
    * Copy the state input from a input viewsheet assembly.
    * @param tassembly the specified input viewsheet assembly.
    * @return the changed hint.
    */
   public static int copySelectedValues(InputVSAssembly tassembly,
      InputVSAssembly fssembly)
   {
      if(fssembly instanceof CompositeInputVSAssembly) {
         return copyCompositeInput(tassembly, fssembly);
      }
      else if(fssembly instanceof SingleInputVSAssembly) {
         return copySingleInput(tassembly, fssembly);
      }

      return VSAssembly.NONE_CHANGED;
   }

   /**
    * Copy the state input from a CompositeInput viewsheet assembly.
    * @param tassembly the target assembly to copy to
    * @param fassembly  the source assembly to copy from
    * @return the changed state
    */
   private static int copyCompositeInput(InputVSAssembly tassembly,
      InputVSAssembly fassembly)
   {
      if(tassembly == null || fassembly == null || !tassembly.isEnabled()) {
         return VSAssembly.NONE_CHANGED;
      }

      // @by ChrisSpagnoli bug1397460287828 #1 2014-8-19
      // Cannot simply set the target selections equal to the source selections,
      // have to add any selected target elements which do not appear in the list of source values.
      final List sourceValuesList =
         Arrays.asList(((ListInputVSAssembly) fassembly).getValues());
      final List targetValuesList =
         Arrays.asList(((ListInputVSAssembly) tassembly).getValues());
      final List sourceSelectedList =
         Arrays.asList(((CompositeInputVSAssembly) fassembly).getSelectedObjects());
      final List targetSelectedList =
         Arrays.asList(((CompositeInputVSAssembly) tassembly).getSelectedObjects());
      List newTargetSelectedList = new ArrayList(targetSelectedList);

      boolean changeFlag = false;

      // traverse list of all elements from source,
      for(Object sv : sourceValuesList) {

         // and if that element is also in the list of all target elements,
         if(targetValuesList.contains(sv)) {

            // and is selected in the source but not in the target, then add it.
            if(sourceSelectedList.contains(sv) && !targetSelectedList.contains(sv)) {
               newTargetSelectedList.add(sv);
               changeFlag = true;
            } else
            // or is selected in the target but not in the source, then add it.
            if(!sourceSelectedList.contains(sv) && targetSelectedList.contains(sv)) {
               newTargetSelectedList.remove(sv);
               changeFlag = true;
            }
         }
      }

      if(!changeFlag) {
         return VSAssembly.NONE_CHANGED;
      }

      // And finally, set the target selecteds to the combined source selected
      // plus the additional target elements which were already selected.
      ((CompositeInputVSAssembly) tassembly).setSelectedObjects(newTargetSelectedList.toArray());

      // The previous approach only worked if the source and destination had
      // matching lists of check boxes.  It would clear any non-matching check
      // box on update to a matching check box, as the non-matching check box
      // was not selected in the source - because it did not exist in the
      // source to be selected.

      return VSAssembly.OUTPUT_DATA_CHANGED;
   }


   /**
    * Copy the state input from a SingleInput viewsheet assembly.
    * @param tassembly the specified input tssembly.
    * @param fassembly the specified source fssembly.
    * @return the changed.
    */
   private static int copySingleInput(InputVSAssembly tassembly,
      InputVSAssembly fassembly)
   {
      if(tassembly == null || fassembly == null) {
         return VSAssembly.NONE_CHANGED;
      }

      if(!tassembly.isEnabled()) {
         return VSAssembly.NONE_CHANGED;
      }

      Object sourceObj =
         ((SingleInputVSAssembly) tassembly).getSelectedObject();
      Object selectedObj =
         ((SingleInputVSAssembly) fassembly).getSelectedObject();

      // @by: ChrisSpagnoli bug1397460287828 #3 2014-8-19
      if(fassembly instanceof NumericRangeVSAssembly) {
         selectedObj = ((NumericRangeVSAssembly) fassembly).getUnboundedSelectedObject();
      }

      if(tassembly instanceof RadioButtonVSAssembly ||
         tassembly instanceof ComboBoxVSAssembly &&
         !((ComboBoxVSAssemblyInfo) tassembly.getVSAssemblyInfo()).isCalendar())
      {
         Object[] values = ((ListInputVSAssembly) tassembly).getValues();
         boolean contained = false;

         for(int i = 0; i < values.length; i++) {
            Object value = Tool.getData(tassembly.getDataType(), values[i]);

            if(Tool.equals(selectedObj, value)) {
               contained = true;
               break;
            }
         }

         if(!contained) {
            return VSAssembly.NONE_CHANGED;
         }
      }
      else if(tassembly instanceof SpinnerVSAssembly) {
         SpinnerVSAssembly tssembly = (SpinnerVSAssembly) tassembly;
         double max = tssembly.getMax();
         double min = tssembly.getMin();
         double select = ((Number) selectedObj).doubleValue();

         if(select < min || select > max) {
            selectedObj = select < min ? min : max;
         }
      }
      else if(Tool.equals(sourceObj, selectedObj)) {
         return VSAssembly.NONE_CHANGED;
      }

      ((SingleInputVSAssembly) tassembly).setSelectedObject(selectedObj);

      return VSAssembly.OUTPUT_DATA_CHANGED;
   }

   /**
    * Get the table style object by a table style name.
    * @param name the specified table style name.
    * @return the table style object.
    */
   public static TableStyle getTableStyle(String name) {
      return getTableStyle(name, null);
   }

   public static TableStyle getTableStyle(String name, String orgID) {
      if(name == null || name.length() == 0) {
         return null;
      }

      TableStyle style = null;

      if(style == null) {
         style = StyleTreeModel.get(name, orgID);
      }

      if(style == null) {
         try {
            style = (TableStyle) Class.forName(name).newInstance();
         }
         catch(Exception ex) {
            LOG.debug(
                        "Failed to get table style: " + name);
         }
      }

      if(style != null) {
         style.applyDefaultStyle();
      }

      return style;
   }

   /**
    * check if the name points to the DataRef.
    */
   public static boolean isSameCol(String name, DataRef ref) {
      if(ref == null) {
         return false;
      }

      if(ref.getName().equals(name)) {
         return true;
      }

      if(ref instanceof VSDataRef) {
         if(((VSDataRef) ref).getFullName().equals(name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the assembly is used as flyover, and only hide the assembly when
    * it is not used ad fly over.
    * @param assemblyName the specified assembly absolute name.
    * @param vs the specified viewsheet.
    * @return whether is fly over view.
    */
   public static boolean isFlyOver(String assemblyName, Viewsheet vs) {
      return isFlyOverOrTipView(assemblyName, vs, false);
   }

   /**
    * Get the top level viewsheet.
    * @param vs the specified viewsheet.
    * @return the top level viewsheet.
    */
   public static Viewsheet getTopViewsheet(Viewsheet vs) {
      while(vs.isEmbedded()) {
         vs = vs.getViewsheet();
      }

      return vs;
   }

   /**
    * Check if the assembly is used as flyover, and only hide the assembly when
    * it is not used ad fly over.
    * @param assemblyName the specified assembly absolute name.
    * @param vs the specified viewsheet.
    * @return whether is tip view.
    */
   public static boolean isTipView(String assemblyName, Viewsheet vs) {
      return isFlyOverOrTipView(assemblyName, vs, true);
   }

   /**
    * Check if the assembly is used as flyover, and only hide the assembly when
    * it is not used ad fly over.
    * @param assemblyName the specified assembly absolute name.
    * @param vs the specified viewsheet.
    * @param tipView whether need check tip view or fly over view.
    * @return whether is fly over view or tip view.
    */
   private static boolean isFlyOverOrTipView(String assemblyName, Viewsheet vs,
      boolean tipView)
   {
      Assembly[] arr = vs.getAssemblies();
      Assembly aobj = vs.getAssembly(assemblyName);

      if(aobj instanceof VSAssembly) {
         VSAssembly container = ((VSAssembly) aobj).getContainer();

         if(container != null) {
            if(isFlyOverOrTipView(container.getAbsoluteName(), vs, tipView)) {
               return true;
            }
         }
      }

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof Viewsheet) {
            int index = assemblyName.indexOf('.');

            if(index < 0 || index == assemblyName.length() - 1) {
               continue;
            }

            boolean result = isFlyOverOrTipView(assemblyName, (Viewsheet) arr[i], tipView);

            if(result) {
               return true;
            }

            continue;
         }

         VSAssemblyInfo info = (VSAssemblyInfo) arr[i].getInfo();

         if(!(info instanceof TipVSAssemblyInfo)) {
            continue;
         }

         TipVSAssemblyInfo tipInfo = (TipVSAssemblyInfo) info;

         if(tipView) {
            int tipOption = tipInfo.getTipOption();
            String tipview = tipInfo.getTipView();
            VSAssembly obj = (VSAssembly) vs.getAssembly(tipview);

            if(tipOption != TipVSAssemblyInfo.VIEWTIP_OPTION || obj == null ||
               obj.getContainer() != null)
            {
               continue;
            }

            if(Tool.equals(obj.getAbsoluteName(), assemblyName)) {
               return true;
            }
         }
         else {
            String[] views = tipInfo.getFlyoverViews();

            if(views == null || views.length == 0) {
               continue;
            }

            for(String view : views) {
               if(view.equals(assemblyName)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Check if the assembly is pop component.
    * @param assemblyName the specified assembly absolute name.
    * @param vs the specified viewsheet.
    * @return whether is pop component.
    */
   public static boolean isPopComponent(String assemblyName, Viewsheet vs) {
      Assembly[] arr = vs.getAssemblies();
      Assembly aobj = vs.getAssembly(assemblyName);

      if(aobj instanceof VSAssembly) {
         VSAssembly container = ((VSAssembly) aobj).getContainer();

         if(container != null) {
            if(isPopComponent(container.getAbsoluteName(), vs)) {
               return true;
            }
         }
      }

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof Viewsheet) {
            int index = assemblyName.indexOf('.');

            if(index < 0 || index == assemblyName.length() - 1) {
               continue;
            }

            if(!assemblyName.substring(0, index).equals(arr[i].getAbsoluteName())) {
               continue;
            }

            boolean result = isPopComponent(assemblyName.substring(index + 1),
               (Viewsheet) arr[i]);

            if(result) {
               return true;
            }

            continue;
         }

         VSAssemblyInfo info = (VSAssemblyInfo) arr[i].getInfo();

         if(!(info instanceof PopVSAssemblyInfo)) {
            continue;
         }

         PopVSAssemblyInfo popInfo = (PopVSAssemblyInfo) info;

         int popOption = popInfo.getPopOption();
         String popcomponent = popInfo.getPopComponent();
         VSAssembly obj = (VSAssembly) vs.getAssembly(popcomponent);

         if(popOption != PopVSAssemblyInfo.POP_OPTION || obj == null ||
            obj.getContainer() != null)
         {
            continue;
         }

         if(Tool.equals(obj.getAbsoluteName(), assemblyName)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Return table assemblies list.
    * @param print true if for print, else not.
    */
   public static List<TableDataVSAssembly> getTableDataAssemblies(Viewsheet vs, boolean print) {
      List<TableDataVSAssembly> list = new ArrayList<>();

      if(vs == null) {
         return list;
      }

      // use original viewsheet which not applied vslayout to collect table data assemblies.
      if(vs.getOriginalVs() != null) {
         vs = vs.getOriginalVs();
      }

      for(Assembly assembly : vs.getAssemblies(true)) {
         if(assembly instanceof TableDataVSAssembly &&
            ((TableDataVSAssembly) assembly).getVSAssemblyInfo().isVisible(print) &&
            !VSUtil.isTipView(assembly.getAbsoluteName(), vs) &&
            !VSUtil.isPopComponent(assembly.getAbsoluteName(), vs))
         {
            list.add((TableDataVSAssembly) assembly);
         }
      }

      return list;
   }

   /**
    * If flyover component is not binding same source with current component,
    * ignore to process it.
    */
   public static boolean sameSource(VSAssembly tip, VSAssembly comp) {
      String tiptable = getSourceTable(tip);
      String ctable = getSourceTable(comp);
      return tiptable != null && !tiptable.trim().equals("") &&
         ctable != null && !ctable.trim().equals("") && tiptable.equals(ctable);
   }

   /**
    * Get the source binding table.
    */
   private static String getSourceTable(VSAssembly comp) {
      if(comp instanceof DataVSAssembly) {
         SourceInfo src = ((DataVSAssembly) comp).getSourceInfo();
         return src == null || src.isEmpty() ? null : src.getSource();
      }
      else if(comp instanceof InputVSAssembly) {
         return comp.getTableName();
      }
      else if(comp instanceof OutputVSAssembly) {
         ScalarBindingInfo src =
            ((OutputVSAssembly) comp).getScalarBindingInfo();
         return src == null ? null : src.getTableName();
      }
      else if(comp instanceof SelectionVSAssembly) {
         return comp.getTableName();
      }

      return null;
   }

   /**
    * Check whether current condition is same as tip condition.
    */
   public static boolean sameCondition(RuntimeViewsheet rvs, String aname,
      String value) throws Exception
   {
      Viewsheet viewsheet = rvs.getViewsheet();

      if(viewsheet == null) {
         return true;
      }

      VSAssembly comp = (VSAssembly) viewsheet.getAssembly(aname);
      VSAssemblyInfo vinfo = comp.getVSAssemblyInfo();

      if(!(vinfo instanceof TipVSAssemblyInfo)) {
         return true;
      }

      TipVSAssemblyInfo minfo = (TipVSAssemblyInfo) vinfo;
      String[] views = minfo.getFlyoverViews();

      if(views == null || views.length == 0) {
         return true;
      }

      ConditionList conds = VSUtil.getConditionList(rvs, comp, value);

      for(String view : views) {
         VSAssembly tip = (VSAssembly) viewsheet.getAssembly(view);

         if(tip == null || view.equals(aname) || !VSUtil.sameSource(tip, comp))
         {
            continue;
         }

         if(!Tool.equals(VSUtil.fixCondition(rvs, tip, conds, aname, value),
                         VSAssembly.NONE_CHANGED))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Check current condition whether is same as tip contidion.
    */
   public static Object fixCondition(RuntimeViewsheet rvs,
      VSAssembly tip, ConditionList conds, String objName, String value)
      throws Exception
   {
      if(!(tip instanceof BindableVSAssembly)) {
         return VSAssembly.NONE_CHANGED;
      }

      if(tip instanceof DataVSAssembly) {
         SourceInfo src = ((DataVSAssembly) tip).getSourceInfo();

         if(src != null && src.getType() == SourceInfo.VS_ASSEMBLY) {
            return VSAssembly.NONE_CHANGED;
         }
      }

      String tbl = tip.getTableName();

      if(tbl == null) {
         return VSAssembly.NONE_CHANGED;
      }

      Worksheet ws = tip.getWorksheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return VSAssembly.NONE_CHANGED;
      }

      TableAssembly tobj = ws.getVSTableAssembly(tbl);

      if(tobj == null) {
         throw new BoundTableNotFoundException(Catalog.getCatalog().getString
            ("common.notTable", tbl));
      }

      tobj = box.getBoundTable(tobj, tip.getName(), false);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly comp = vs.getAssembly(objName);

      if(VSUtil.createWsWrapper(rvs.getViewsheet(), comp)) {
         String containsCalcTable = getContainsCalcBaseTableName(tobj, vs);

         if(containsCalcTable != null) {
            tobj = (TableAssembly) tobj.clone();
            VSAQuery.appendCalcField(tobj, containsCalcTable, true, vs);
         }
      }

      ColumnSelection columns = tobj.getColumnSelection(false);

      if(vs == null) {
         return VSAssembly.NONE_CHANGED;
      }

      if(conds == null && comp instanceof ChartVSAssembly) {
         ChartVSAssembly chart = (ChartVSAssembly) comp;
         String aname = chart.getAbsoluteName();
         VSDataSet alens = (VSDataSet) box.getData(aname, true, DataMap.ZOOM);
         VSChartInfo cinfo = chart.getVSChartInfo();
         VGraphPair pair = box.getVGraphPair(aname);
         VGraph vgraph = (pair == null) ? null : pair.getRealSizeVGraph();

         if(vgraph == null) {
            return VSAssembly.NONE_CHANGED;
         }

         DataSet vdset = vgraph.getCoordinate().getDataSet();
         VSDataSet lens = vdset instanceof VSDataSet
            ? (VSDataSet) vdset : (VSDataSet) box.getData(aname);

         if(lens == null) {
            return VSAssembly.NONE_CHANGED;
         }

         VSSelection selection = ChartVSSelectionUtil.getVSSelection(
            value, lens, alens, vdset, false, cinfo, false, null, true, false, false, true);
         DateComparisonUtil.fixDatePartSelection((ChartVSAssembly) comp, lens, selection);
         DateComparisonUtil.fixDatePartSelection((ChartVSAssembly) comp, lens, selection);
         conds = chart.getConditionList(selection, columns);
         conds = VSAQuery.replaceGroupValues(conds, chart);
      }
      else if(comp instanceof CrosstabVSAssembly) {
         conds = VSAQuery.replaceGroupValues(conds, (CrosstabVSAssembly) comp, false);
      }

      conds = VSUtil.normalizeConditionList(columns, conds);
      VSAQuery.fixConditionList(conds, columns);

      return Tool.equals(conds, tip.getTipConditionList()) ? VSAssembly.NONE_CHANGED : conds;
   }

   private static String getContainsCalcBaseTableName(TableAssembly tobj, Viewsheet vs) {
      if(vs.getCalcFields(tobj.getName()) != null) {
         return tobj.getName();
      }

      if(tobj instanceof MirrorTableAssembly) {
         return getContainsCalcBaseTableName(((MirrorTableAssembly) tobj).getTableAssembly(), vs);
      }

      return null;
   }

   /**
    * Get a condition list for the region.
    */
   public static ConditionList getConditionList(RuntimeViewsheet rvs,
                                                VSAssembly comp, String value, boolean afterGroup)
      throws Exception
   {
      ConditionList conds = null;
      // for chart, get condition list when apply condition
      if(comp instanceof ChartVSAssembly || value == null) {
         return null;
      }
      else if(comp instanceof CrosstabDataVSAssembly) {
         conds = getConditionList(rvs, (CrosstabDataVSAssembly) comp, value);
      }
      else if(comp instanceof CalcTableVSAssembly) {
         conds = getConditionList(rvs, (CalcTableVSAssembly) comp, value);
      }
      else if(comp instanceof TableVSAssembly) {
         conds = getConditionList(rvs, (TableVSAssembly) comp, value, afterGroup);
      }

      return conds == null ? new ConditionList() : conds;
   }

   /**
    * Get a condition list for the region.
    */
   public static ConditionList getConditionList(RuntimeViewsheet rvs,
      VSAssembly comp, String value) throws Exception
   {
      return getConditionList(rvs, comp, value, false);
   }

   /**
    * Get a condition list for the region.
    */
   private static ConditionList getConditionList(RuntimeViewsheet rvs,
      CrosstabDataVSAssembly crosstab, String value) throws Exception
   {
      CrosstabDataVSAssemblyInfo cinfo = (CrosstabDataVSAssemblyInfo) crosstab.getInfo();
      VSCrosstabInfo vinfo = cinfo.getVSCrosstabInfo();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      DataVSAssembly data = (DataVSAssembly) vs.getAssembly(crosstab.getAbsoluteName());

      if(box == null) {
         return new ConditionList();
      }

      TableLens lens = (TableLens) box.getData(crosstab.getAbsoluteName());
      DataRef[] rheaders = vinfo.getRuntimeRowHeaders();
      DataRef[] cheaders = vinfo.getRuntimeColHeaders();
      boolean period = vinfo.getPeriodRuntimeRowHeaders().length >
         vinfo.getRuntimeRowHeaders().length;
      int offset = period ? 1 : 0;
      SourceInfo sinfo = data.getSourceInfo();
      String cubeType = VSUtil.getCubeType(sinfo.getPrefix(), sinfo.getSource());
      boolean xmla = XCube.SQLSERVER.equals(cubeType) ||
                     XCube.MONDRIAN.equals(cubeType);
      ConditionList rowConds = new ConditionList();
      ConditionList colConds = new ConditionList();
      String[] pairs = Tool.split(value, ';');
      ConditionList conds = new ConditionList();

      for(String str : pairs) {
         String[] pair = str.split("X");
         int row = Integer.parseInt(pair[0]);
         int col = Integer.parseInt(pair[1]);
         TableDataPath path = lens.getDescriptor().getCellDataPath(row, col);

         if(path == null || path.getType() != TableDataPath.SUMMARY &&
            path.getType() != TableDataPath.GRAND_TOTAL &&
            path.getType() != TableDataPath.GROUP_HEADER)
         {
            continue;
         }

         int hrcount = lens.getHeaderRowCount();
         int hccount = lens.getHeaderColCount();
         List<ConditionList> condList = new ArrayList<>();

         // if target cell is row header cell,
         // don't need to merge the condition of the same column in column headers.
         if(!(row >= hrcount && col < hccount)) {
            TableConditionUtil.createCrosstabConditions(
               crosstab, colConds, cheaders, col, lens, true, cubeType, 0, path, xmla);
            colConds.trim();
            condList.add(colConds);
         }

         // if target cell is col header cell,
         // don't need to merge the condition of the same column in row headers.
         if(!(row < hrcount && col >= hccount)) {
            TableConditionUtil.createCrosstabConditions(
               crosstab, rowConds, rheaders, row, lens, false, cubeType, offset, path, xmla);
            rowConds.trim();
            condList.add(rowConds);
         }

         conds = ConditionUtil.mergeConditionList(condList, JunctionOperator.AND);
      }

      conds = VSAQuery.replaceGroupValues(conds, (CrosstabVSAssembly)crosstab, true);

      return conds;
   }

   /**
    * Get a condition list for the region.
    */
   private static ConditionList getConditionList(RuntimeViewsheet rvs,
      CalcTableVSAssembly calc, String value) throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      int[][] rowcols = getRowColumns(value);
      return TableConditionUtil.createCalcTableConditions(calc, rowcols,
         calc.getAbsoluteName(), box);
   }

   /**
    * Get a condition list for the region.
    */
   private static ConditionList getConditionList(RuntimeViewsheet rvs,
      TableVSAssembly table, String value, boolean afterGroup) throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return new ConditionList();
      }

      TableLens lens = (TableLens) box.getData(table.getAbsoluteName());
      ConditionList conds = new ConditionList();

      String[] pairs = Tool.split(value, ';');

      for(String str : pairs) {
         String[] pair = str.split("X");
         int row = Integer.parseInt(pair[0]);
         int col = Integer.parseInt(pair[1]);
         conds = TableConditionUtil.createTableConditions(table, conds, row, col, lens, afterGroup);
      }

      conds.trim();
      return conds;
   }

   /**
    * Get the selected rows and columns.
    * @return [row, col] pairs.
    */
   private static int[][] getRowColumns(String value) {
      if(value == null) {
         return new int[0][0];
      }

      String[] arr = Tool.split(value, ';');
      int[][] pairs = new int[arr.length][2];

      for(int i = 0; i < arr.length; i++) {
         String[] pair = Tool.split(arr[i], 'X');

         for(int j = 0; j < pair.length; j++) {
            pairs[i][pair.length - 1 - j] = Integer.parseInt(pair[j]);
         }
      }

      return pairs;
   }

   /**
    * Reset all runtime values in assemblies.
    */
   public static void resetRuntimeValues(Viewsheet vs, boolean actionVisible) {
      resetRuntimeValues(vs, actionVisible, false);
   }

   /**
    * Reset all runtime values in assemblies.
    */
   public static void resetRuntimeValues(Viewsheet vs, boolean actionVisible,
                                         boolean exceptEmbeddedVs)
   {
      Assembly[] arr = vs.getAssemblies();

      if(!exceptEmbeddedVs) {
         ((VSAssemblyInfo) vs.getInfo()).resetRuntimeValues();

         if(actionVisible) {
            ((VSAssemblyInfo) vs.getInfo()).resetActionVisible();
         }
      }

      for(Assembly obj : arr) {
         if(obj instanceof Viewsheet) {
            resetRuntimeValues((Viewsheet) obj, actionVisible, true);
            continue;
         }

         if(!(obj instanceof AbstractVSAssembly)) {
            continue;
         }

         AbstractVSAssembly assembly = (AbstractVSAssembly) obj;
         assembly.getVSAssemblyInfo().resetRuntimeValues();

         if(actionVisible) {
            assembly.getVSAssemblyInfo().resetActionVisible();
         }
      }
   }

   /**
    * Synchronism the table data path in the crosstab when the crosstab changed.
    * @param cross the crosstab which to sync.
    * @param oinfo the old crosstab info before change crosstab.
    * @param isPropertyChange is change property or modify columns.
    * @param synMap the map to sync.
    */
   public static <V> void syncCrosstabPath(CrosstabVSAssembly cross,
                                           CrosstabVSAssemblyInfo oinfo,
                                           boolean isPropertyChange,
                                           Map<TableDataPath, V> synMap)
   {
      syncCrosstabPath(cross, oinfo, isPropertyChange, synMap, null);
   }

   public static <V> void syncCrosstabPath(CrosstabVSAssembly cross,
                                           CrosstabVSAssemblyInfo oinfo,
                                           boolean isPropertyChange,
                                           Map<TableDataPath, V> synMap,
                                           BiConsumer<Map<TableDataPath, V>, TableDataPath> clearAliasFormatProcessor)
   {
      syncCrosstabPath(cross, oinfo, isPropertyChange, synMap, false, false,
                       clearAliasFormatProcessor);
   }

   public static <V> void syncCrosstabPath(CrosstabVSAssembly cross,
                                           CrosstabVSAssemblyInfo oinfo,
                                           boolean isPropertyChange,
                                           Map<TableDataPath, V> synMap,
                                           boolean fromSetInput)
   {
      syncCrosstabPath(cross, oinfo, isPropertyChange, synMap, fromSetInput, false, null);
   }

   /**
    * Synchronize the table data path in the crosstab when the crosstab changed.
    * @param cross the crosstab which to sync.
    * @param oinfo the old crosstab info before change crosstab.
    * @param isPropertyChange is change property or modify columns.
    * @param synMap the map to sync.
    * @param fromSetInput is call coming from SetInputObjectEvent.
    */
   public static <V> void syncCrosstabPath(CrosstabVSAssembly cross,
                                           CrosstabVSAssemblyInfo oinfo,
                                           boolean isPropertyChange,
                                           Map<TableDataPath, V> synMap,
                                           boolean fromSetInput,
                                           boolean fromDrill,
                                           BiConsumer<Map<TableDataPath, V>, TableDataPath> clearAliasFormatProcessor)
   {
      VSCrosstabInfo cinfo = cross.getVSCrosstabInfo();
      VSCrosstabInfo ocinfo = oinfo == null ? null : oinfo.getVSCrosstabInfo();

      if(oinfo == null || cinfo == null || ocinfo == null) {
         return;
      }

      // aggregate name map, old name-->new name
      Map<String, String> aggNameMap = new HashMap<>();
      // dimension name map, old name-->new name
      Map<String, String> dimNameMap = new HashMap<>();
      // dimension index map, old index-->new index
      Map<Integer, Integer> dimIdxMap = new HashMap<>();
      Set<String> clearFormats = new HashSet<>();
      buildClearFormatSet(cinfo, clearFormats);
      buildMap(ocinfo, cinfo, aggNameMap, null, true);
      buildMap(ocinfo, cinfo, dimNameMap, dimIdxMap, false);

      // Add all the old path to be replaced to the remove map, add the new path
      // to the add map, remove old path and add new path in the end, so to
      // avoid format lost when new path the same as the removed path.
      Map<TableDataPath, V> added = new HashMap<>() {
         @Override
         public V put(TableDataPath key, V value) {
            return super.put(key, value);
         }
      };
      Set<TableDataPath> removed = new HashSet<>();

      if(isPropertyChange) {
         if(cinfo.isSummarySideBySide() == ocinfo.isSummarySideBySide()) {
            return;
         }

         syncPropertyPath(cinfo, aggNameMap, removed, added, synMap);
      }

      // @by ankitmathur, Fix Bug #2761, We need to ensure Summary,
      // Summary Header, and Total cells are synced even for a Property change event.
      syncCornerPath(cinfo, dimIdxMap, removed, added, synMap);
      syncGroupHeader(ocinfo, cinfo, dimNameMap, removed, added, synMap);

      if(hasRuntimeRowHeader(ocinfo)) {
         syncSummaryHeader(ocinfo, cinfo, aggNameMap, dimNameMap, removed, added, synMap, fromSetInput);
         syncSummaryCell(ocinfo, cinfo, aggNameMap, dimNameMap, removed, added, synMap,
            clearFormats, fromSetInput);
         syncGrandTotal(ocinfo, cinfo, aggNameMap, dimNameMap, removed, added, synMap, fromSetInput);
      }

      // @by ankitmathur, For Bug #4211, Maintain list of old TableDataPaths.
      // If the user is dynamically changing their Row/Col headers, we should
      // maintain User defined formatting.
      if(fromSetInput) {
         pruneTableDataPaths(cross.getAllBindingRefs(), synMap, removed);
      }
      else {
         // only remove unreferenced path if the number of entries is very large,
         // to prevent uncontrolled growth. otherwise keep the user setting so if a
         // column is removed and add back, the original format is not lost. (43331)
         for(TableDataPath tableDataPath : removed) {
            if(synMap.size() > 200 && tableDataPath.getType() != TableDataPath.HIDDEN_SUMMARY &&
               tableDataPath.getType() != TableDataPath.SUMMARY)
            {
               // Bug #64353, don't remove the path when drilling up/down
               if(!fromDrill) {
                  synMap.remove(tableDataPath);
               }
            }
            // Remove temp paths used to hide summary formats
            else if(tableDataPath.getType() == TableDataPath.SUMMARY) {
               TableDataPath path = (TableDataPath) tableDataPath.clone();
               path.setType(TableDataPath.HIDDEN_SUMMARY);

               if(added.containsKey(path)) {
                  synMap.remove(tableDataPath);
               }
            }
            else if(tableDataPath.getType() == TableDataPath.HIDDEN_SUMMARY) {
               TableDataPath path = (TableDataPath) tableDataPath.clone();
               path.setType(TableDataPath.SUMMARY);

               if(added.containsKey(path)) {
                  synMap.remove(tableDataPath);
               }
            }

            if(clearAliasFormatProcessor != null) {
               clearAliasFormatProcessor.accept(synMap, tableDataPath);
            }
         }
      }

      for(TableDataPath ntp : added.keySet()) {
         synMap.put(ntp, added.get(ntp));
      }
   }

   /**
    * Synchronism the summary header cells and group header cell when side by
    * side.
    */
   private static <V> void syncPropertyPath(VSCrosstabInfo cinfo,
                                            Map<String, String> aggName,
                                            Collection<TableDataPath> remove,
                                            Map<TableDataPath, V> add,
                                            Map<TableDataPath, V> synMap)
   {
      String[] dims = buildNames(cinfo, false);
      String[] aggs = buildNames(cinfo, true);

      if(dims.length == 0 || aggs.length <= 1) {
         return;
      }

      boolean isSide = cinfo.isSummarySideBySide();
      int rowLen = cinfo.getRuntimeRowHeaders().length;
      int colLen = cinfo.getRuntimeColHeaders().length;
      String[] oheaders = getFullSumHeader(!isSide, rowLen, dims);
      String[] nheaders = getFullSumHeader(isSide, rowLen, dims);
      Iterator<TableDataPath> fpaths = synMap.keySet().iterator();
      String[] arr2;
      TableDataPath ntp = null;

      while(fpaths.hasNext()) {
         TableDataPath tp = fpaths.next();
         String[] arr = tp.getPath();

         if(arr == null || arr.length <= 0 || arr[0].contains("Cell [")) {
            continue;
         }

         String agg = arr[arr.length - 1];

         if(aggName.get(agg) == null) {
            continue;
         }

         if(tp.getType() == TableDataPath.GROUP_HEADER) {
            if(arr.length != 1) {
               continue;
            }

            if((isSide && rowLen == 0) || (!isSide && colLen == 0)) {
               String nagg = aggName.get(agg);
               arr2 = new String[dims.length + 1];
               System.arraycopy(dims, 0, arr2, 0, dims.length);
               arr2[dims.length] = nagg;
               ntp = new TableDataPath(-1, TableDataPath.HEADER,
                  tp.getDataType(), arr2);
               remove.add(tp);
               V obj = synMap.get(tp);
               add.put(ntp, obj);
            }

            continue;
         }
         else if(tp.getType() == TableDataPath.HEADER) {
            String total0 = isSide ? "ROW_GRAND_TOTAL" : "COL_GRAND_TOTAL";
            String total1 = isSide ? "COL_GRAND_TOTAL" : "ROW_GRAND_TOTAL";

            if(total0.equals(arr[0])) {
               arr2 = arr.clone();
               arr2[0] = total1;
               ntp = (TableDataPath) tp.clone(arr2);
               remove.add(tp);
               V obj = synMap.get(tp);
               add.put(ntp, obj);
               continue;
            }

            if(!arrMatchHeader(oheaders, arr)) {
               continue;
            }

            if((isSide && colLen == 0) || (!isSide && rowLen == 0)) {
               String nagg = aggName.get(agg);
               ntp = new TableDataPath(-1,
                  TableDataPath.GROUP_HEADER, tp.getDataType(),
                  new String[]{nagg});
            }
            else if(rowLen != 0 && colLen != 0) {
               String nagg = aggName.get(agg);
               ntp = getFullPath(tp, nheaders, nagg);
            }

            if(ntp != null) {
               remove.add(tp);
               V obj = synMap.get(tp);
               add.put(ntp, obj);
            }
         }
      }
   }

   /**
    * Synchronize the corner path in the crosstab.
    */
   private static <V> void syncCornerPath(VSCrosstabInfo info,
                                          Map<Integer, Integer> dimMap,
                                          Collection<TableDataPath> remove,
                                          Map<TableDataPath, V> add,
                                          Map<TableDataPath, V> synMap)
   {
      int rowlen = info.getRuntimeRowHeaders().length;

      for(TableDataPath tp : synMap.keySet()) {
         String[] arr = tp.getPath();

         if(arr == null || arr.length <= 0 || !arr[0].contains("Cell [") ||
            tp.getType() != TableDataPath.HEADER)
         {
            continue;
         }

         int row = Integer.parseInt(arr[0].substring(8, arr[0].length() - 1));

         if(!dimMap.containsKey(row)) {
            continue;
         }

         int newRow = dimMap.get(row);

         if(newRow >= 0 && newRow < rowlen) {
            remove.add(tp);
            TableDataPath ntp = new TableDataPath(-1, TableDataPath.HEADER, tp.getDataType(),
               new String[] {"Cell [" + 0 + "," + newRow + "]"});
            V fmt = synMap.get(tp);
            add.put(ntp, fmt);
         }
         else if(newRow == -1) {
            remove.add(tp);
         }
      }
   }

   /**
    * Synchronize the group header cells in the crosstab.
    */
   private static <V> void syncGroupHeader(VSCrosstabInfo ocinfo,
                                           VSCrosstabInfo cinfo,
                                           Map<String, String> dimName,
                                           Collection<TableDataPath> remove,
                                           Map<TableDataPath, V> add,
                                           Map<TableDataPath, V> synMap)
   {
      Map<String, TableDataPath> rowGHeaders= new HashMap<>();
      Map<String, TableDataPath> colGHeaders = new HashMap<>();
      populateGHeaders(rowGHeaders, true, cinfo);
      populateGHeaders(colGHeaders, false, cinfo);

      if(!hasRuntimeRowHeader(ocinfo)) {
         return;
      }

      int orows = ocinfo.getRuntimeRowHeaders().length;
      String[] odims = buildNames(ocinfo, false);

      for(TableDataPath tp : synMap.keySet()) {
         String[] arr = tp.getPath();

         if(arr == null || arr.length <= 0 ||
            tp.getType() != TableDataPath.GROUP_HEADER)
         {
            continue;
         }

         String gfld = arr[arr.length - 1];
         int idx = findField(odims, gfld);

         if(idx < 0) {
            continue;
         }

         String nname = dimName.get(gfld);
         TableDataPath ntp = idx < orows ? rowGHeaders.get(nname) : colGHeaders.get(nname);

         if(ntp != null) {
            remove.add(tp);
            V fmt = synMap.get(tp);
            add.put(ntp, fmt);
         }
      }
   }

   /**
    * Synchronism the summary header cells in the crosstab.
    */
   private static <V> void syncSummaryHeader(VSCrosstabInfo ocinfo,
                                             VSCrosstabInfo cinfo,
                                             Map<String, String> aggName,
                                             Map<String, String> dimName,
                                             Collection<TableDataPath> remove,
                                             Map<TableDataPath, V> add,
                                             Map<TableDataPath, V> synMap,
                                             boolean fromSetInput)
   {
      boolean isSide = cinfo.isSummarySideBySide();
      String[] odims = buildNames(ocinfo, false);
      String[] dims = buildNames(cinfo, false);
      Set<String> dimSet = new HashSet<>(Arrays.asList(dims));
      int orlen = ocinfo.getRuntimeRowHeaders().length;
      int rlen = cinfo.getRuntimeRowHeaders().length;
      String[] oheader = getFullSumHeader(isSide, orlen, odims);
      String[] nheader = getFullSumHeader(isSide, rlen, dims);
      Iterator<TableDataPath> fpaths = synMap.keySet().iterator();
      TableDataPath ntp = null;

      while(fpaths.hasNext()) {
         TableDataPath tp = fpaths.next();
         String[] arr = tp.getPath();

         if(arr == null || arr.length <= 0 || arr[0].contains("Cell [")) {
            continue;
         }

         if(tp.getType() != TableDataPath.HEADER) {
            continue;
         }

         String agg = arr[arr.length - 1];

         if(dimSet.contains(agg)) {
            continue;
         }

         String nagg = aggName.get(agg);

         if(nagg == null) {
            remove.add(tp);
            continue;
         }

         if(arrMatchHeader(oheader, arr)) {
            ntp = getFullPath(tp, nheader, nagg);
         }
         else if(arr.length > 1) {
            String total = arr[arr.length - 2];

            if("ROW_GRAND_TOTAL".equals(total)) {
               ntp = (TableDataPath) tp.clone(new String[]{total, nagg});
            }
            else {
               String ntotal = dimName.get(total);

               if(!isTotalMatch(oheader, arr) || findField(nheader, ntotal) == -1) {
                  continue;
               }

               ntp = getSumHeaderTotalPath(tp, nheader, ntotal, nagg);
            }
         }

         if(ntp != null && !ntp.equals(tp)) {
            remove.add(tp);
            V obj = synMap.get(tp);
            // @by ankitmathur, For Bug #4211, If a User Defined format already
            // exist, use that instead of "synced" format. This logic is similar
            // to "DrillEvent".
            if(!fromSetInput || synMap.get(ntp) == null) {
               add.put(ntp, obj);
            }
         }
      }
   }

   /**
    * Synchronize the summary cells in the crosstab.
    */
   private static <V> void syncSummaryCell(VSCrosstabInfo ocinfo,
                                           VSCrosstabInfo cinfo,
                                           Map<String, String> aggName,
                                           Map<String, String> dimName,
                                           Collection<TableDataPath> remove,
                                           Map<TableDataPath, V> add,
                                           Map<TableDataPath, V> synMap,
                                           Set<String> clearFormats,
                                           boolean fromSetInput)
   {
      String[] odims = buildNames(ocinfo, false);
      String[] oaggs = buildNames(ocinfo, true);
      String[] dims = buildNames(cinfo, false);
      int olen = ocinfo.getRuntimeRowHeaders().length;
      String[] orows = getHeaderArr(false, olen, odims);
      String[] ocols = getHeaderArr(true, olen, odims);
      int len = cinfo.getRuntimeRowHeaders().length;
      String[] rows = getHeaderArr(false, len, dims);
      String[] cols = getHeaderArr(true, len, dims);

      for(TableDataPath tp : synMap.keySet()) {
         TableDataPath ntp = null;
         boolean clearUserFormat = false;

         String[] arr = tp.getPath();

         if(arr == null || arr.length == 0) {
            continue;
         }

         String agg = arr[arr.length - 1];

         // @by ankitmathur, For Bug #4211, Although Aggregates which do not
         // have any row/col binding are considered Grand Total cells
         // (TableDataPath.TRAILER), we need to handle the case where
         // dimensions are dynamically added and the cell gets converted to a
         // Summary Cell (TableDataPath.SUMMARY).
         if(tp.getType() != TableDataPath.SUMMARY &&
            !isNoneAggregate(tp, agg, TableDataPath.TRAILER, true))
         {
            continue;
         }

         // no aggregate summary cell
         if(findField(oaggs, agg) == -1) {
            // is row summary cell
            if(matchRowCol(orows, arr)) {
               String ofld = arr[arr.length - 1];
               String nfld = dimName.get(ofld);
               int idx = findField(dims, nfld);

               if(idx >= 0 && idx < len - 1) {
                  String[] arr0 = new String[idx + 1];
                  System.arraycopy(rows, 0, arr0, 0, idx + 1);
                  ntp = (TableDataPath) tp.clone(arr0);
               }
            }
            // is col summary cell
            else if(matchRowCol(ocols, arr)) {
               String ofld = arr[arr.length - 1];
               String nfld = dimName.get(ofld);
               int idx = findField(dims, nfld);

               if(idx > len - 1) {
                  String[] arr0 = new String[idx - len + 1];
                  System.arraycopy(cols, 0, arr0, 0, idx - len + 1);
                  ntp = (TableDataPath) tp.clone(arr0);
               }
            }
         }
         else if(tp.getType() == TableDataPath.HIDDEN_SUMMARY) {
            // Restore format hidden by current full path summary cell
            if(arrMatchHeader(odims, arr)  && !Arrays.equals(odims, dims)) {
               ntp = (TableDataPath) tp.clone();
               ntp.setType(TableDataPath.SUMMARY);
            }
         }
         // has aggregate summary cell
         else {
            if(aggName.get(agg) == null) {
               remove.add(tp);
               continue;
            }

            String nagg = aggName.get(agg);

            // full path summary cell
            if(arrMatchHeader(odims, arr) &&
               (tp.getType() != TableDataPath.SUMMARY || isValidSummaryCell(odims, arr)))
            {
               ntp = getFullPath(tp, dims, nagg);

               if(clearFormats.contains(nagg) && isValidSummaryCell(odims, arr)) {
                  clearUserFormat = true;
               }

               if(isNoneAggregate(ntp, nagg, TableDataPath.SUMMARY, false)) {
                  int newPath = agg.endsWith(")") && agg.contains("(") ?
                     TableDataPath.TRAILER : TableDataPath.DETAIL;
                  ntp = new TableDataPath(
                     ntp.getLevel(), newPath, ntp.getDataType(),
                     ntp.getPath(), ntp.isRow(), ntp.isCol());
               }
            }
            // Temporarily hide summary format that will be replaced by the full path summary cell's format
            else if(arrMatchHeader(dims, arr) && !Arrays.equals(odims, dims)
               && tp.getType() == TableDataPath.SUMMARY)
            {
               if(!fromSetInput && synMap.get(tp) != null) {
                  ntp = (TableDataPath) tp.clone();
                  ntp.setType(TableDataPath.HIDDEN_SUMMARY);
               }
            }
            // total path summary cell
            else {
               String[] arr0 = new String[arr.length - 1];
               System.arraycopy(arr, 0, arr0, 0, arr.length - 1);

               if(ocols.length == 0 && orows.length == 0 && arr.length == 1) {
                  if(isNoneAggregate(tp, nagg, TableDataPath.TRAILER, true)) {
                     ntp = handleNoneAggregate(tp, dims, nagg);
                  }
                  else {
                     continue;
                  }
               }
               else if(orows.length == 0) {
                  if(matchHeader(arr0, ocols)) {
                     if(arr0.length == ocols.length) {
                        ntp = getFullPath(tp, dims, nagg);
                     }
                     else {
                        String ctotal = dimName.get(arr0[arr0.length - 1]);
                        String[] tcols = getTotalArr(cols, ctotal);
                        ntp = getRowColTotalPath(tp, rows, tcols, nagg);
                     }
                  }
               }
               else if(ocols.length == 0) {
                  if(matchHeader(arr0, orows)) {
                     if(arr0.length == orows.length) {
                        ntp = getFullPath(tp, dims, nagg);
                     }
                     else {
                        String rtotal = dimName.get(arr0[arr0.length - 1]);
                        String[] trows = getTotalArr(rows, rtotal);
                        ntp = getRowColTotalPath(tp, trows, cols, nagg);
                     }
                  }
               }
               else {
                  int split = findField(arr0, ocols[0]);

                  if(split < 0) {
                     continue;
                  }

                  String[] rarr = new String[split];
                  System.arraycopy(arr0, 0, rarr, 0, split);
                  String[] carr = new String[arr0.length - split];
                  System.arraycopy(arr0, split, carr, 0, arr0.length - split);

                  if(matchHeader(rarr, orows) && matchHeader(carr, ocols)) {
                     String rowTotal = dimName.get(rarr[rarr.length - 1]);
                     String colTotal = dimName.get(carr[carr.length - 1]);
                     String[] trows = null;
                     String[] tcols = null;

                     if(rarr.length == orows.length) {
                        trows = rows;
                        tcols = getTotalArr(cols, colTotal);
                     }
                     else if(carr.length == ocols.length) {
                        trows = getTotalArr(rows, rowTotal);
                        tcols = cols;
                     }
                     else {
                        trows = getTotalArr(rows, rowTotal);
                        tcols = getTotalArr(cols, colTotal);
                     }

                     ntp = getRowColTotalPath(tp, trows, tcols, nagg);
                  }
               }
            }
         }

         if(ntp != null && !ntp.equals(tp)) {
            remove.add(tp);
            V obj = synMap.get(tp);
            // @by ankitmathur, For Bug #4211, If a User Defined format already
            // exist, use that instead of "synced" format. This logic is similar
            // to "DrillEvent".
            if(!fromSetInput || synMap.get(ntp) == null) {
               add.put(ntp, obj);

               if(clearUserFormat && obj instanceof VSCompositeFormat) {
                  VSFormat userDefinedFormat = ((VSCompositeFormat) obj).getUserDefinedFormat();
                  userDefinedFormat.setFormatValue(null, false);
               }
            }
         }
      }
   }

   /**
    * Synchronize the grand total cells in the crosstab.
    */
   private static <V> void syncGrandTotal(VSCrosstabInfo ocinfo,
                                          VSCrosstabInfo cinfo,
                                          Map<String, String> aggName,
                                          Map<String, String> dimName,
                                          Collection<TableDataPath> remove,
                                          Map<TableDataPath, V> add,
                                          Map<TableDataPath, V> synMap,
                                          boolean fromSetInput)
   {
      String[] odims = buildNames(ocinfo, false);
      String[] oaggs = buildNames(ocinfo, true);
      String[] dims = buildNames(cinfo, false);
      int olen = ocinfo.getRuntimeRowHeaders().length;
      String[] orows = getHeaderArr(false, olen, odims);
      String[] ocols = getHeaderArr(true, olen, odims);
      int len = cinfo.getRuntimeRowHeaders().length;
      String[] rows = getHeaderArr(false, len, dims);
      String[] cols = getHeaderArr(true, len, dims);
      Iterator<TableDataPath> fpaths = synMap.keySet().iterator();
      TableDataPath ntp;

      while(fpaths.hasNext()) {
         TableDataPath tp = fpaths.next();
         ntp = null;
         String[] arr = tp.getPath();

         if(arr == null || arr.length <= 1) {
            continue;
         }

         if(tp.getType() != TableDataPath.GRAND_TOTAL) {
            continue;
         }

         String agg = arr[arr.length - 1];

         if(aggName.get(agg) == null) {
            continue;
         }

         String nagg = aggName.get(agg);

         // Is row grand total and col grand total cell.
         if(arr.length == 3 && "ROW_GRAND_TOTAL".equals(arr[0]) &&
            "COL_GRAND_TOTAL".equals(arr[1]))
         {
            continue;
         }
         // Is row grand total.
         else if("ROW_GRAND_TOTAL".equals(arr[0])) {
            if(ocols.length == 0 && arr.length == 2) {
               ntp = getGrandTotalPath(tp, true, cols, nagg);
            }
            else if(arr.length > 2) {
               String[] arr0 = new String[arr.length - 1];
               System.arraycopy(arr, 1, arr0, 0, arr.length - 1);
               String ctotal = dimName.get(arr[arr.length - 2]);

               // Full col headers.
               if(arrMatchHeader(ocols, arr0) &&
                  arr0.length == ocols.length + 1)
               {
                  ntp = getGrandTotalPath(tp, true, cols, nagg);
               }
               // Total col headers.
               else if(isTotalMatch(ocols, arr0) && ctotal!= null) {
                  String[] col = getTotalArr(cols, ctotal);
                  ntp = getGrandTotalPath(tp, true, col, nagg);
               }
            }
         }
         // Is col grand total.
         else if("COL_GRAND_TOTAL".equals(arr[arr.length - 2])) {
            if(orows.length == 0 && arr.length == 2) {
               getGrandTotalPath(tp, false, rows, nagg);
            }
            else if(arr.length > 2 ) {
               String[] arr0 = new String[arr.length - 1];
               System.arraycopy(arr, 0, arr0, 0, arr.length - 1);
               String rtotal = dimName.get(arr[arr.length - 3]);

               // Full row headers.
               if(arrMatchHeader(orows, arr0) &&
                  arr0.length == orows.length + 1)
               {
                  ntp = getGrandTotalPath(tp, false, rows, nagg);
               }
               // Total row headers.
               if(isTotalMatch(orows, arr0) && rtotal != null) {
                  String[] row = getTotalArr(rows, rtotal);
                  ntp = getGrandTotalPath(tp, false, row, nagg);
               }
            }
         }

         if(ntp != null && !ntp.equals(tp)) {
            remove.add(tp);
            V obj = synMap.get(tp);
            // @by ankitmathur, For Bug #4211, If a User Defined format already
            // exist, use that instead of "synced" format. This logic is similar
            // to "DrillEvent".
            if(!fromSetInput || synMap.get(ntp) == null) {
               add.put(ntp, obj);
            }
         }
      }
   }

   /**
    * Clean any unreachable table data paths if it does not exist
    * in working binding.
    *
    * @param refs DataRefs representing the binding.
    * @param map Map of existing TableDataPaths which have assigned objects
    * @param removeList Marked list of TableDataPaths which can be evaluated
    *                   for removal.
    */
   private static void pruneTableDataPaths(DataRef[] refs, Map<TableDataPath, ?> map,
                                           Collection<TableDataPath> removeList)
   {
      Collection<TableDataPath> list = removeList == null ? new ArrayList<>(map.keySet()) : removeList;

      // In order for a TableDataPath to still be relevant, each path must
      // be contained in the binding.
      for(TableDataPath tp : list) {
         if(tp.getType() == TableDataPath.HEADER ||
            tp.getType() == TableDataPath.SUMMARY ||
            tp.getType() == TableDataPath.GROUP_HEADER)
         {
            boolean foundAll = true;
            String [] paths = tp.getPath();

            for(String path : paths) {
               for(DataRef ref : refs) {
                  if(path.equals(ref.getName()) ||
                     (ref instanceof VSDataRef && path.equals(((VSDataRef) ref).getFullName())) )
                  {
                     foundAll = true;
                     break;
                  }

                  foundAll = false;
               }

               if(!foundAll) {
                  map.remove(tp);
                  break;
               }
            }
         }
      }
   }

   private static void buildClearFormatSet(VSCrosstabInfo ninfo, Set<String> clearFormats) {
      DataRef[] refs = getRefs(ninfo, true);
      Map<String, Integer> dup = new HashMap<>();

      for(int i = 0; i < refs.length; i++) {
         DataRef ref = refs[i];
         String name =  ref.getName();
         int cnt = dup.get(name) == null ? 0 : dup.get(name);
         dup.put(name, cnt + 1);

         if(cnt > 0) {
            name = name + "." + cnt;
         }

         Calculator calculator = ((VSAggregateRef) ref).getCalculator();

         if(ref instanceof VSAggregateRef && calculator != null && calculator.isPercent()) {
            VSAggregateRef vsAggregateRef = (VSAggregateRef) ref;
            String fullName = CrossTabFilterUtil.getCrosstabRTAggregateName(vsAggregateRef, true);

            if(cnt > 0) {
               name = fullName.replace(CrossTabFilterUtil.getCrosstabRTAggregateName(vsAggregateRef, false),
                                       name);
            }
            else {
               name = fullName;
            }

            clearFormats.add(name);
         }
      }
   }

   /**
    * Build name map.
    */
   private static void buildMap(VSCrosstabInfo oinfo, VSCrosstabInfo ninfo,
      Map<String, String> nmap, Map<Integer, Integer> imap, boolean isAgg)
   {
      if(oinfo == null || ninfo == null) {
         return;
      }

      DataRef[] orefs = getRefs(oinfo, isAgg);
      DataRef[] nrefs = getRefs(ninfo, isAgg);
      String[] onames = buildName(orefs, isAgg);
      String[] nnames = buildName(nrefs, isAgg);
      DataRef[] oldRowRefs = null;

      if(!isAgg) {
         oldRowRefs = oinfo.getRuntimeRowHeaders();
      }

      Set<Integer> foundNewRuntimeIdSet = new HashSet<>();
      Set<Integer> notFoundIndexSet = new HashSet<>();

      for(int i = 0; i < orefs.length; i++) {
         int oidx = getRuntimeId(orefs[i], isAgg);

         if(oidx < 0) {
            continue;
         }

         boolean find = false;

         // find by name first
         for(int j = 0; j < nrefs.length; j++) {
            int nidx = getRuntimeId(nrefs[j], isAgg);

            if(nidx < 0) {
               continue;
            }

            if(crosstabRefEquals(orefs[i], nrefs[j], isAgg)) {
               if(nmap.containsValue(nnames[j])) {
                  continue;
               }

               nmap.put(onames[i], nnames[j]);

               // just row dim index will at the table thead path such as Cell[0, dimIndex]
               if(imap != null &&
                  (oldRowRefs == null || containsCrosstabRefs(oldRowRefs, orefs[i], isAgg)))
               {
                  imap.put(i, j);
               }

               find = true;
               foundNewRuntimeIdSet.add(nidx);
               break;
            }
         }

         if(!find) {
            notFoundIndexSet.add(i);
            nmap.put(onames[i], null);

            if(imap != null) {
               imap.put(i, -1);
            }
         }
      }

      // then find by runtime index (old logic)
      for(Integer i : notFoundIndexSet) {
         int oidx = getRuntimeId(orefs[i], isAgg);

         if(oidx < 0) {
            continue;
         }

         for(int j = 0; j < nrefs.length; j++) {
            int nidx = getRuntimeId(nrefs[j], isAgg);

            if(nidx < 0 || foundNewRuntimeIdSet.contains(nidx)) {
               continue;
            }

            if(oidx == nidx) {
               nmap.put(onames[i], nnames[j]);

               if(imap != null) {
                  imap.put(i, j);
               }

               foundNewRuntimeIdSet.add(nidx);
               break;
            }
         }
      }
   }

   private static boolean containsCrosstabRefs(DataRef[] refs, DataRef ref, boolean isAgg) {
      if(refs == null || ref == null) {
         return false;
      }

      for(DataRef dataRef : refs) {
         if(crosstabRefEquals(dataRef, ref, isAgg)) {
            return true;
         }
      }

      return false;
   }

   private static boolean crosstabRefEquals(DataRef ref0, DataRef ref1, boolean isAgg) {
      boolean aggEquals = ref0 instanceof VSAggregateRef &&
         ref1 instanceof VSAggregateRef ?
         Tool.equals(((VSAggregateRef) ref0).getFullName(),
            ((VSAggregateRef) ref1).getFullName()) :
         Tool.equals(ref0.getName(), ref1.getName());

      return isAgg && aggEquals ||
         !isAgg && Tool.equals(((VSDimensionRef) ref0).getFullName(),
            ((VSDimensionRef) ref1).getFullName());
   }

   /**
    * Get refs.
    */
   private static DataRef[] getRefs(VSCrosstabInfo info, boolean isAgg) {
      if(info == null) {
         return new DataRef[0];
      }

      DataRef[] refs = null;

      if(isAgg) {
         refs = info.getRuntimeAggregates();
      }
      else {
         DataRef[] row = info.getRuntimeRowHeaders();
         DataRef[] col = info.getRuntimeColHeaders();
         int rowLength = row == null ? 0 : row.length;
         int colLength = col == null ? 0 : col.length;

         refs = new DataRef[rowLength + colLength];

         if(row != null) {
            System.arraycopy(row, 0, refs, 0, rowLength);
         }

         if(col != null) {
            System.arraycopy(col, 0, refs, rowLength, colLength);
         }
      }

      return refs;
   }

   /**
    * remove use variable of condition list from variable table
    * @param vart variable table
    * @param oconlist old condition list
    * @param nconlist new condition list
    */
   public static void removeVariable(VariableTable vart, ConditionList oconlist, ConditionList nconlist) {
      List<String> names = new ArrayList<>();

      for(int i = 0; i < oconlist.getSize(); i = i + 2) {
         ConditionItem oconitem = oconlist.getConditionItem(i);
         getUsedVariableNames(oconitem, nconlist, names);
      }

      for(int i = 0; i < names.size(); i++) {
         vart.remove(names.get(i));
      }
   }

   /**
    * get all Condition values needed remove
    */
   private static void getUsedVariableNames(ConditionItem oconditem, ConditionList ncondlist,
                                           List<String> values)
   {
      if(ncondlist == null) {
         return;
      }

      List<String> names;

      for(int i = 0; i < ncondlist.getSize(); i = i + 2) {
         ConditionItem nconditem = ncondlist.getConditionItem(i);
         names = getUsedVariableName(oconditem, nconditem);

         if(names == null) {
            continue;
         }

         values.addAll(names);
      }
   }

   /**
    * get Condition values needed remove
    */
   private static List<String> getUsedVariableName(ConditionItem oconditem, ConditionItem nconditem) {
      List<String> names = new ArrayList();

      if(oconditem.equals(nconditem)){
         return null;
      }

      List ovalues = oconditem.getCondition().getValues();
      List nvalues = nconditem.getCondition().getValues();

      ovalues.stream()
         .filter((v) -> v instanceof UserVariable)
         .forEach((v) -> names.add(((UserVariable) v).getName()));

      boolean match = nvalues.stream()
         .filter((v) -> v instanceof UserVariable)
         .anyMatch((v) -> names.contains(((UserVariable) v).getName()));

      return match ? names : null;
   }

   /**
    * Build nameS.
    */
   private static String[] buildNames(VSCrosstabInfo info, boolean isAgg) {
      DataRef[] refs = getRefs(info, isAgg);
      return buildName(refs, isAgg);
   }

   /**
    * Build name.
    */
   public static String[] buildName(DataRef[] refs, boolean isAgg) {
      String[] names = new String[refs.length];
      Map<String, Integer> dup = new HashMap<>();

      for(int i = 0; i < refs.length; i++) {
         DataRef ref = refs[i];
         String name = isAgg ? ref.getName() : ((VSDimensionRef) ref).getFullName();
         int cnt = dup.get(name) == null ? 0 : dup.get(name);
         dup.put(name, cnt + 1);

         if(cnt > 0) {
            name = name + "." + cnt;
         }

         if(ref instanceof VSAggregateRef && ((VSAggregateRef) ref).getCalculator() != null) {
            VSAggregateRef vsAggregateRef = (VSAggregateRef) ref;
            String fullName = CrossTabFilterUtil.getCrosstabRTAggregateName(vsAggregateRef, true);

            if(cnt > 0) {
               name = fullName.replace(CrossTabFilterUtil.getCrosstabRTAggregateName(vsAggregateRef, false),
                  name);
            }
            else {
               name = fullName;
            }
         }

         names[i] = name;
      }

      return names;
   }

   /**
    * Get viewsheet bookmark owner alias.
    */
   public static String getUserAlias(IdentityID owner) {
      SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();

      if(securityProvider == null || securityProvider.isVirtual()) {
         return owner.name;
      }

      String alias = securityProvider.getUser(owner) != null ? securityProvider.getUser(owner).getAlias() : null;

      return Tool.isEmptyString(alias) ? owner.name : alias;
   }

   /**
    * Get viewsheet bookmark info.
    * @param identifier the viewsheet identifier.
    * @param currUser the current user.
    * @return bookmark infos.
    */
   public static VSBookmarkInfo[] getBookmarks(String identifier,
                                               IdentityID currUser)
   {
      if(identifier == null) {
         return new VSBookmarkInfo[0];
      }

      return getBookmarks(AssetEntry.createAssetEntry(identifier), currUser);
   }

   /**
    * Get viewsheet bookmark info.
    * @param aEntry the viewsheet entry.
    * @param currUser the current user.
    * @return bookmark infos.
    */
   public static VSBookmarkInfo[] getBookmarks(AssetEntry aEntry, IdentityID currUser) {
      if(aEntry == null) {
         return new VSBookmarkInfo[0];
      }

      if(currUser == null || currUser.name.length() == 0) {
         return new VSBookmarkInfo[0];
      }

      AssetRepository rep = AssetUtil.getAssetRepository(false);
      List<VSBookmarkInfo> bookmarks = new ArrayList<>();

      try {
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
         IdentityID[] users;

         if(provider == null) {
            users = new IdentityID[] { new IdentityID(XPrincipal.ANONYMOUS, OrganizationManager.getInstance().getCurrentOrgID()) };
         }
         else if(aEntry.getOrgID().equals(Organization.getSelfOrganizationID())) {
            users = new IdentityID[] {currUser};
         }
         else if(provider.isVirtual()) {
            users = provider.getUsers();
         }
         else {
            // users can come from SSO and not exist in the security provider
            users = rep.getBookmarkUsers(aEntry).toArray(new IdentityID[0]);
         }

         for(IdentityID user : users) {
            getUserBookmarks(rep, aEntry, user, currUser, bookmarks);
         }

         boolean contain = Arrays.stream(users).anyMatch(user -> Tool.equals(user, currUser));

         if(users.length == 0 || !contain) {
            getUserBookmarks(rep, aEntry, currUser, currUser, bookmarks);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to load bookmarks", ex);
      }

      return bookmarks.toArray(new VSBookmarkInfo[0]);
   }

   private static void getUserBookmarks(AssetRepository rep, AssetEntry aEntry, IdentityID user,
                                        IdentityID currUser, List<VSBookmarkInfo> bookmarks)
      throws Exception
   {
      VSBookmark bookmark = rep.getVSBookmark(aEntry, new XPrincipal(user));

      if(bookmark != null) {
         for(String name : bookmark.getBookmarks()) {
            VSBookmarkInfo bminfo = bookmark.getBookmarkInfo(name);
            int type = bminfo.getType();

            if(!user.equals(currUser)) {
               if(VSBookmark.HOME_BOOKMARK.equals(name)) {
                  continue;
               }
               else if(type == VSBookmarkInfo.PRIVATE) {
                  continue;
               }
               else if(type == VSBookmarkInfo.GROUPSHARE && !isSameGroup(user, currUser)) {
                  continue;
               }
            }

            if(bminfo.getOwner() == null) {
               bminfo.setOwner(user);
            }

            if(VSBookmark.HOME_BOOKMARK.equals(name)) {
               bookmarks.add(0, bminfo);
            }
            else {
               bookmarks.add(bminfo);
            }
         }

         if(user.equals(currUser) && !bookmark.containsBookmark(VSBookmark.HOME_BOOKMARK)) {
            VSBookmarkInfo info = new VSBookmarkInfo(VSBookmark.HOME_BOOKMARK,
                                                     VSBookmarkInfo.ALLSHARE,
                                                     currUser, false,
                                                     new java.util.Date().getTime());
            bookmarks.add(0, info);
         }
      }
   }

   public static String createBookmarkIdentifier(AssetEntry sheetEntry) {
      if(sheetEntry == null) {
         return null;
      }

      String identifier = sheetEntry.toIdentifier();
      StringBuffer buf = new StringBuffer();

      for(int i = 0; i < identifier.length(); i++) {
         char c = identifier.charAt(i);

         switch(c) {
            case '^':
            case '/':
               buf.append("__");
               break;
            default:
               buf.append(c);
         }
      }

      return buf.toString();
   }

   /**
    * Refresh bookmark id after viewsheet was renamed.
    * @param osheetEntry the old asset entry of the viewsheet.
    * @param nsheetEntry the new asset entry of the viewsheet.
    */
   public static void refreshBookmarkId(AssetEntry osheetEntry, AssetEntry nsheetEntry) {
      if(osheetEntry == null || nsheetEntry == null) {
         return;
      }

      String oldBookmarkID = osheetEntry.getProperty("__bookmark_id__");

      // 1. refresh bookmark id.
      // 2. if old bookmark id is not match the old entry then don't change the bookmark id
      // for bc issue, because we don't rename bookmark before we enhenced rename transform.
      if(oldBookmarkID != null &&
         Tool.equals(oldBookmarkID, createBookmarkIdentifier(osheetEntry)))
      {
         nsheetEntry.setProperty("__bookmark_id__", VSUtil.createBookmarkIdentifier(nsheetEntry));
      }
   }

   /**
    * Viewsheet goto the specified bookmark.
    *
    * @param vs the specified viewsheet.
    * @param bookmark viewsheet bookmark will goto.
    * @param bookmarkName bookmark name the viewsheet will goto.
    * @param rep AssetRepository to update the bookmark.
    */
   public static Viewsheet vsGotoBookmark(Viewsheet vs, VSBookmark bookmark, String bookmarkName, AssetRepository rep)
   {
      vs = bookmark.getBookmark(bookmarkName, vs);
      AuditRecordUtils.executeBookmarkRecord(
         vs, bookmark.getBookmarkInfo(bookmarkName), BookmarkRecord.ACTION_TYPE_ACCESS);
      VSBookmarkInfo bookmarkInfo = bookmark.getBookmarkInfo(bookmarkName);

      if(!VSBookmark.HOME_BOOKMARK.equals(bookmarkName) && !VSBookmark.INITIAL_STATE.equals(bookmarkName) &&
         bookmarkInfo != null && rep != null)
      {
         String debounceKey = VSBookmark.getLockKey(bookmark.getIdentifier(), bookmarkInfo.getOwner().convertToKey())
            + "_" + bookmarkName;
         getDebouncer().debounce(debounceKey, 2L, TimeUnit.SECONDS, () -> {
            updateBookmarkLastAccessedTime(bookmark.getIdentifier(), bookmarkName,
                                           bookmarkInfo.getOwner(), rep);
         });
      }

      return vs;
   }

   private static void updateBookmarkLastAccessedTime(String identifier, String bookmarkName,
                                                      IdentityID bookmarkOwner, AssetRepository rep)
   {
      String key = VSBookmark.getLockKey(identifier, bookmarkOwner.convertToKey());
      Cluster.getInstance().lockKey(key);

      try {
         AssetEntry entry = AssetEntry.createAssetEntry(identifier);
         XPrincipal principal = new XPrincipal(bookmarkOwner);
         VSBookmark bookmark = rep.getVSBookmark(entry, principal, true);
         VSBookmarkInfo bookmarkInfo = bookmark.getBookmarkInfo(bookmarkName);

         if(bookmarkInfo != null) {
            bookmarkInfo.setLastAccessed(System.currentTimeMillis());
         }

         rep.setVSBookmark(entry, bookmark, principal);
      }
      catch(Exception ex) {
         LOG.error("Failed to update the " + bookmarkOwner + " user bookmark last accessed time for "
                      + identifier, ex);
      }
      finally {
         Cluster.getInstance().unlockKey(key);
      }
   }

   public static Debouncer<String> getDebouncer() {
      return ConfigurationContext.getContext()
         .computeIfAbsent(DEBOUNCER_KEY, k -> new DefaultDebouncer<>(false));
   }

   /**
    * Get the bookmark tooltip.
    *
    * @param info bookmark info.
    * @return
    */
   public static String getBookmarkTooltip(VSBookmarkInfo info, String timeZoneId) {
      Catalog catalog = Catalog.getCatalog();
      StringBuilder stringBuilder = new StringBuilder();

      if(info.getCreateTime() > 0) {
         stringBuilder.append(catalog.getString("Created Time"));
         stringBuilder.append(": ");
         stringBuilder.append(getLocalDate(new Date(info.getCreateTime()), timeZoneId));
      }

      if(info.getLastAccessed() > 0) {
         stringBuilder.append("\n");
         stringBuilder.append(catalog.getString("Last Accessed Time"));
         stringBuilder.append(": ");
         stringBuilder.append(getLocalDate(new Date(info.getLastAccessed()), timeZoneId));
      }

      return stringBuilder.toString();
   }

   private static String getLocalDate(Date date, String timeZoneId) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);
      sdf.setTimeZone(timeZone);

      return sdf.format(date);
   }

   /**
     * Check the the two specified users are in same group.
     */
   private static boolean isSameGroup(IdentityID user0, IdentityID user1) {
      if(user0.equals(user1)) {
         return true;
      }

      String[] groups0 = SUtil.getGroups(user0);
      java.util.List<String> list = Arrays.asList(groups0);
      String[] group1 = SUtil.getGroups(user1);

      for(String group : group1) {
         if(list.contains(group)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get runtime id.
    */
   private static int getRuntimeId(DataRef ref, boolean isAgg) {
      if(isAgg && ref instanceof VSAggregateRef) {
         return ((VSAggregateRef) ref).getRuntimeID();
      }
      else if(!isAgg && ref instanceof VSDimensionRef) {
         return ((VSDimensionRef) ref).getRuntimeID();
      }

      return -1;
   }

   /**
    * Populate group header path.
    */
   private static void populateGHeaders(Map ghpath, boolean isRow, VSCrosstabInfo cinfo) {
      DataRef[] refs = getRefs(cinfo, false);
      String[] ndims = buildNames(cinfo, false);
      int rowLen = cinfo.getRuntimeRowHeaders().length;
      int start = isRow ? 0 : rowLen;
      int end = isRow ? rowLen : ndims.length;

      for(int i = start; i < end; i++) {
         String[] arr = new String[i - start + 1];
         System.arraycopy(ndims, start, arr, 0, i - start + 1);
         DataRef ref = refs[i];

         if(ref instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) ref;
            TableDataPath path = new TableDataPath(-1,
               TableDataPath.GROUP_HEADER, dim.getDataType(), arr);
            ghpath.put(ndims[i], path);
         }
      }
   }

   /**
    * Check the group header is row group or column group.
    */
   private static int findField(String[] names, String name) {
      for(int i = 0; i < names.length; i++) {
         if(Objects.equals(name, names[i])) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get the full header of summary.
    */
   private static String[] getFullSumHeader(boolean isCol, int len, String[] dims) {
      String[] arr = isCol ? new String[dims.length - len] : new String[len];
      int start = isCol ? len : 0;
      int end = isCol ? dims.length : len;
      int cnt = 0;

      for(int i = start; i < end; i++) {
         arr[cnt] = dims[i];
         cnt++;
      }

      return arr;
   }

   /**
    * Check whether there is runtime row header.
    */
   private static boolean hasRuntimeRowHeader(VSCrosstabInfo info) {
      return info != null && info.getRuntimeRowHeaders() != null;
   }

   /**
    * Check if the array is a valid summary cell.
    */
   private static boolean isValidSummaryCell(String[] headers, String[] arr) {
      if(arr == null || headers == null) {
         return true;
      }

      return arr.length - 1 <= headers.length;
   }

   /**
    * Check if the array is match with the header is match.
    */
   private static boolean arrMatchHeader(String[] headers, String[] arr) {
      boolean matched = false;

      if(arr.length <= headers.length) {
         return false;
      }

      for(int i = 0; i < headers.length; i++) {
         if(!Tool.equals(arr[i], headers[i])) {
            return false;
         }

         matched = true;
      }

      return matched;
   }

   /**
    * Get the full path of summary header.
    */
   private static TableDataPath getFullPath(TableDataPath tp, String[] headers,
      String agg)
   {
      if(headers == null) {
         return (TableDataPath) tp.clone(new String[]{agg});
      }

      String[] path = new String[headers.length + 1];
      System.arraycopy(headers, 0, path, 0, headers.length);
      path[headers.length] = agg;

      return (TableDataPath) tp.clone(path);
   }

   /**
    * Convert a TableDataPath which had "None" (no) col/row headers to the new
    * Path which does.
    *
    * @param tp  The Old/New TableDataPath.
    * @param headers The new set of Headers for the TableDataPath.
    * @param agg The Aggregate to check.
    */
   private static TableDataPath handleNoneAggregate(
      TableDataPath tp, String[] headers, String agg)
   {
      if(headers == null) {
         return (TableDataPath) tp.clone(new String[]{agg});
      }

      String[] path = new String[headers.length + 1];
      System.arraycopy(headers, 0, path, 0, headers.length);
      path[headers.length] = agg;

      if(headers.length >= 1) {
         TableDataPath ntp = new TableDataPath(
            tp.getLevel(), TableDataPath.SUMMARY, tp.getDataType(), path,
            tp.isRow(), tp.isCol());

         return  ntp;
      }

      return (TableDataPath) tp.clone(path);
   }

   /**
    * Utility method to verify whether the newly created TableDataPath has
    * "None" (no) row/col binding.
    *
    * @param tp The Old/New TableDataPath.
    * @param agg The Aggregate to check.
    * @param pathType The Path type to compare.
    * @param oldPath Indicates if the path is already synced or not.
    */
   private static boolean isNoneAggregate(TableDataPath tp, String agg,
                                          int pathType, boolean oldPath)
   {
      if(tp != null && tp.getPath() != null) {
         String[] tpPath = tp.getPath();

         if(tpPath.length == 1 && tpPath[0].equals(agg))
         {
            if((oldPath &&
               (tp.getType() == TableDataPath.TRAILER ||
                  tp.getType() == TableDataPath.DETAIL)) ||
               (!oldPath && tp.getType() == TableDataPath.SUMMARY))
            {
               return true;
            }
         }
         else if(tpPath.length > 1 && tpPath[tpPath.length - 1].equals(agg) &&
            pathType == TableDataPath.TRAILER)
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the total array of summary header.
    */
   private static TableDataPath getSumHeaderTotalPath(TableDataPath tp,
      String[] headers, String total, String agg)
   {
      String[] path = getTotalArr(headers, total);

      if(path != null) {
         String[] arr = new String[path.length + 1];
         System.arraycopy(path, 0, arr, 0, path.length);
         arr[arr.length - 1] = agg;
         return (TableDataPath) tp.clone(arr);
      }

      return null;
   }

   /**
    * Get the total path of summary header.
    */
   private static String[] getTotalArr(String[] headers, String total) {
      String[] path = null;

      for(int i = 0; i < headers.length; i++) {
         if(i == headers.length - 1) {
            return null;
         }

         if(total != null && total.equals(headers[i])) {
            path = new String[i + 1];
            System.arraycopy(headers, 0, path, 0, i + 1);
            return path;
         }
      }

      return null;
   }

   /**
    * Check if the total field match with old headers.
    */
   private static boolean isTotalMatch(String[] headers, String[] arr) {
     boolean matched = false;

      if(arr.length - 1 >= headers.length) {
         return false;
      }

      for(int i = 0; i < arr.length - 1; i++) {
         if(!Tool.equals(arr[i], headers[i])) {
            return false;
         }

         matched = true;
      }

      return matched;
   }

   /**
    * Get the full headers.
    */
   private static String[] getHeaderArr(boolean isCol, int len, String[] dims) {
      String[] arr = isCol ? new String[dims.length - len] :
         new String[len];
      int start = isCol ? len : 0;
      int end = isCol ? dims.length : len;
      int cnt = 0;

      for(int i = start; i < end; i++) {
         arr[cnt] = dims[i];
         cnt++;
      }

      return arr;
   }

   /**
    * Check if the arr is match with the headers.
    */
   private static boolean matchHeader(String[] arr, String[] headers) {
      if(arr.length > headers.length) {
         return false;
      }

      boolean matched = false;

      for(int i = 0; i < arr.length; i++) {
         if(!Tool.equals(arr[i], headers[i])) {
            return false;
         }

         matched = true;
      }

      return matched;
   }

   /**
    * Check it is a row summary cell or column summary cell.
    */
   private static boolean matchRowCol(String[] headers, String[] arr) {
      boolean matched = false;

      if(arr.length >= headers.length) {
         return false;
      }

      for(int i = 0; i < arr.length; i++) {
         if(!Tool.equals(arr[i], headers[i])) {
            return false;
         }

         matched = true;
      }

      return matched;
   }

   /**
    * Get the total path of summary cell.
    */
   private static TableDataPath getRowColTotalPath(TableDataPath tp,
      String[] rows, String[] cols, String agg)
   {
      int rowLength = rows == null ? 0 : rows.length;
      int colLength = cols == null ? 0 : cols.length;

      String[] path = new String[rowLength + colLength + 1];

      if(rows != null) {
         System.arraycopy(rows, 0, path, 0, rowLength);
      }

      if(cols != null) {
         System.arraycopy(cols, 0, path, rowLength, colLength);
      }

      path[path.length - 1] = agg;

      return (TableDataPath) tp.clone(path);
   }

   /**
    * Get the full path of grand total.
    */
   private static TableDataPath getGrandTotalPath(TableDataPath tp,
      boolean isRow, String[] headers, String agg)
   {
      String[] row = isRow ? new String[]{"ROW_GRAND_TOTAL"} : headers;
      String[] col = isRow ? headers : new String[]{"COL_GRAND_TOTAL"};

      int rowLength = row == null ? 0 : row.length;
      int colLength = col == null ? 0 : col.length;

      String[] path = new String[rowLength + colLength + 1];

      if(row != null) {
         System.arraycopy(row, 0, path, 0, rowLength);
      }

      if(col != null) {
         System.arraycopy(col, 0, path, rowLength, colLength);
      }

      path[rowLength + colLength] = agg;

      return (TableDataPath) tp.clone(path);
   }

   /**
    * Get all hyperlinks.
    */
   public static Hyperlink[] getAllLinks(VSAssemblyInfo info, boolean runtime) {
      List<Hyperlink> list = new ArrayList();

      if(info instanceof TableDataVSAssemblyInfo) {
         TableDataVSAssemblyInfo tinfo = (TableDataVSAssemblyInfo) info;
         TableHyperlinkAttr attr = tinfo.getHyperlinkAttr();

         if(attr != null) {
            Enumeration links = attr.getAllHyperlinks();

            while(links.hasMoreElements()) {
               Hyperlink link = (Hyperlink) links.nextElement();

               if(link != null) {
                  list.add(link);
               }
            }
         }
      }
      else if(info instanceof ChartVSAssemblyInfo) {
         ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) info;
         VSChartInfo cinfo = chartInfo.getVSChartInfo();

         if(cinfo != null) {
            ChartRef[] refs = cinfo.getBindingRefs(runtime);

            for(ChartRef ref : refs) {
               if(ref instanceof HyperlinkRef) {
                  Hyperlink link = ((HyperlinkRef) ref).getHyperlink();

                  if(link != null) {
                     list.add(link);
                  }
               }
            }
         }
      }
      else {
         Hyperlink link = info.getHyperlink();

         if(link != null) {
            list.add(link);
         }
      }

      return list.toArray(new Hyperlink[0]);
   }

   /**
    * Add selection parameter.
    * @param link the hyperlink to be set parameters.
    * @param sel the Hashtable from sand box.
    */
   public static void addSelectionParameter(Hyperlink.Ref link, Hashtable<String, SelectionVSAssembly> sel) {
      if(sel == null) {
         return;
      }

      List<String> exists = new ArrayList<>();
      Enumeration<String> pnames = link.getParameterNames();
      Enumeration<String> vnames = sel.keys();

      while(pnames.hasMoreElements()) {
         exists.add(pnames.nextElement());
      }

      while(vnames.hasMoreElements()) {
         String name = vnames.nextElement();

         if(exists.contains(name)) {
            continue;
         }

         Object val = sel.get(name);

         if(val instanceof SelectionListVSAssembly || val instanceof SelectionTreeVSAssembly) {
            SelectionList list = null;
            boolean idMode = false;

            if(val instanceof SelectionTreeVSAssembly) {
               final SelectionTreeVSAssembly tree = (SelectionTreeVSAssembly) val;
               CompositeSelectionValue cval = tree.getCompositeSelectionValue();
               idMode = tree.isIDMode();

               if(cval != null) {
                  list = cval.getSelectionList();
               }
            }
            else {
               list = ((SelectionListVSAssembly) val).getStateSelectionList();
            }

            addSelectionListParams(name, link, list, "", idMode);
         }

         if(val instanceof TimeSliderVSAssembly) {
            TimeSliderVSAssembly tslider = (TimeSliderVSAssembly) val;
            SelectionValue minVal = tslider.getMinSelectionValue();
            String min = minVal == null ? null : minVal.getValue();
            SelectionValue maxVal = tslider.getMaxSelectionValue();
            String max = maxVal == null ? null : maxVal.getValue();

            if(min != null && max != null) {
               link.setParameter(name + AssetUtil.RANGE_SLIDER_START, min);
               link.setParameter(name + AssetUtil.RANGE_SLIDER_END, max);
            }
         }
         if(val instanceof CalendarVSAssembly) {
            CalendarVSAssembly calendar = (CalendarVSAssembly) val;
            calendar.updateSelectedRanges();
            Object[] startDates = calendar.getStartDates().toArray();
            Object[] endDates = calendar.getEndDates().toArray();
            link.setParameter(name + AssetUtil.CALENDAR_START, startDates);
            link.setParameter(name + AssetUtil.CALENDAR_END, endDates);
         }
      }
   }

   /**
    * Re-calculate the line size/position and start/end point.
    */
   public static Object[] refreshLineInfo(Viewsheet vs, LineVSAssemblyInfo info) {
      LineVSAssembly lineAssembly = (LineVSAssembly) vs.getAssembly(info.getName());
      Point startPixelPt = lineAssembly.getAnchorPos(
         vs, info, info.getStartAnchorID(), info.getStartAnchorPos(), false);

      Point linePt = info.getLayoutPosition() != null ?
         info.getLayoutPosition() : vs.getPixelPosition(info);
      Point startPt = info.getStartPos();
      Point endPt = info.getEndPos();
      Dimension pixelSize = info.getLayoutSize() != null ?
         info.getLayoutSize() : vs.getPixelSize(info);

      if(startPixelPt == null) {
         startPixelPt = new Point(linePt.x + startPt.x, linePt.y + startPt.y);
      }

      Point endPixelPt = new Point(linePt.x + endPt.x, linePt.y + endPt.y);
      Point newPixelPt = new Point(Math.min(startPixelPt.x, endPixelPt.x),
                                   Math.min(startPixelPt.y, endPixelPt.y));
      int width = (info instanceof AnnotationLineVSAssemblyInfo) ?
         Math.abs(startPixelPt.x - endPixelPt.x) : pixelSize.width;
      int height = Math.abs(startPixelPt.y - endPixelPt.y);
      Point start = new Point(startPixelPt.x - newPixelPt.x, startPixelPt.y - newPixelPt.y);
      Point end = new Point(endPixelPt.x - newPixelPt.x, endPixelPt.y - newPixelPt.y);
      int GAP = VSLine.ARROW_GAP;

      // if point starts at 0,0, half of the arrow will be out of bounds (of image). we move
      // it by GAP, and shift image position (newPixelPt) so it fits and is show at the
      // correct position
      newPixelPt.x -= GAP;
      newPixelPt.y -= GAP;
      start.x += GAP;
      start.y += GAP;
      end.x += GAP;
      end.y += GAP;
      width += GAP * 2;
      height += GAP * 2;

      return new Object[] {newPixelPt, new Dimension(width, height), start, end};
   }

   /**
    * Get the scaled size.
    */
   public static Dimension getScaledSize(Dimension osize, DimensionD ratio) {
      int x = (int) Math.floor(osize.width * ratio.getWidth());
      int y = (int) Math.floor(osize.height * ratio.getHeight());
      return new Dimension(x, y);
   }

   /**
    * Add selection list parameter.
    * @param name the assembly name.
    * @param hlink the hyperlink to be set parameters.
    * @param list assembly's selectionlist.
    * @param val the parent selection value of current list.
    * @param idMode whether or not the selectionList is id mode.
    * @return true if list has selected values, otherwise false.
    */
   private static boolean addSelectionListParams(String name, Hyperlink.Ref hlink,
                                                 SelectionList list, String val, boolean idMode)
   {
      if(list == null) {
         return false;
      }

      boolean selected = false;
      SelectionValue[] values = list.getSelectionValues();

      for(int j = 0; j < values.length; j++) {
         SelectionValue value = values[j];
         selected |= value.isSelected();
         final String str;

         if(value.isSelected() && !idMode) {
            str = val.isEmpty() ? value.getValue() : val + "^" + value.getValue();
         }
         else {
            str = value.getValue();
         }

         boolean hasSelectedChildren = false;

         if(value instanceof CompositeSelectionValue) {
            SelectionList slist = ((CompositeSelectionValue) value).getSelectionList();
            SelectionValue[] svalues = slist.getSelectionValues();

            if(svalues != null && svalues.length > 0) {
               hasSelectedChildren = addSelectionListParams(name, hlink, slist, str, idMode);
            }
         }

         if(!value.isSelected()) {
            continue;
         }

         if(!(value instanceof CompositeSelectionValue) || !hasSelectedChildren || idMode) {
            Object obj = hlink.getParameter(name);
            String[] narr;

            if(obj != null) {
               String[] oarr = (String[]) obj;
               narr = new String[oarr.length + 1];
               System.arraycopy(oarr, 0, narr, 0, oarr.length);

               narr[oarr.length] = str;
            }
            else {
               narr = new String[1];
               narr[0] = str;
            }

            hlink.setParameter(name, narr);
         }
      }

      return selected;
   }

   /**
    * Get the sorting type for the selection list.
    */
   public static int getSortType(SelectionBaseVSAssemblyInfo info) {
      int sort = info.getSortType();

      if(info.getMeasure() != null && (info.isShowText() || info.isShowBar())) {
         switch(sort) {
         case XConstants.SORT_ASC:
            sort = XConstants.SORT_VALUE_ASC;
            break;
         case XConstants.SORT_DESC:
            sort = XConstants.SORT_VALUE_DESC;
            break;
         }
      }

      return sort;
   }

   /**
    * XThemeHelper color gets theme from environment.
    */
   public static interface XThemeHelper {
      /**
       * Get the theme.
       */
      public String getTheme();
   }

   // default theme helper
   public static XThemeHelper themeHelper = new XThemeHelper() {
      @Override
      public String getTheme() {
         return "unknown";
      }
   };

   /**
    * Get the text id for the VSAssembly.
    * @param vs the viewsheet.
    * @param name the assembly name.
    * @return the text id.
    */
   public static String getTextID(Viewsheet vs, String name) {
      if(vs == null) {
         return null;
      }

      String textId = null;
      Assembly assembly = vs.getAssembly(name);

      if(assembly instanceof TextVSAssembly ||
         assembly instanceof SubmitVSAssembly)
      {
         textId = getLocalMap(vs, name).get(name);
      }
      else {
         textId = getLocalMap(vs, name).get("Title");
      }

      return textId;
   }

   /**
    * Get the local map for the VSAssembly.
    * @param vs the viewsheet.
    * @param name the assembly name.
    * @return the localization map.
    */
   public static Map<String, String> getLocalMap(Viewsheet vs, String name) {
      ViewsheetInfo vinfo = vs.getViewsheetInfo();
      String[] components = vinfo.getLocalComponents();
      Map<String, String> localMap = new HashMap<>();

      for(int i = 0; i < components.length; i++) {
         String[] names = Tool.split(components[i], "^_^", false);

         if(!Tool.equals(name, names[0])) {
            continue;
         }

         if(names.length == 1) {
            localMap.put(names[0], vinfo.getLocalID(components[i]));
         }
         else if(names.length == 2) {
            localMap.put(names[1], vinfo.getLocalID(components[i]));
         }
      }

      return localMap;
   }

   /**
    * Get shrink table title width.
    */
   public static Rectangle2D getShrinkTitleWidth(VSAssembly assembly,
      CoordinateHelper helper, Rectangle2D pixelbounds, Dimension bounds,
      Point pos, int[] columnPixelW)
   {
      if(!(assembly instanceof TableDataVSAssembly) || pixelbounds != null) {
         return pixelbounds;
      }

      TableDataVSAssemblyInfo info =
         (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info.isShrink()) {
         pixelbounds = helper.getBounds(pos, bounds, true);
         int totalWidth = 0;

         for(int i = 0; i < columnPixelW.length; i++) {
            totalWidth += columnPixelW[i];
         }

         pixelbounds.setRect(pixelbounds.getX(), pixelbounds.getY(),
                             totalWidth, pixelbounds.getHeight());
         pixelbounds = helper.scaleBounds(pixelbounds);
      }

      return pixelbounds;
   }

   /**
    * Check if the assembly is a shrink table assembly.
    */
   public static boolean isAssemblyShrink(VSAssembly assembly) {
      if(!(assembly instanceof TableDataVSAssembly)) {
         return false;
      }

      return ((TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo()).isShrink();
   }

   /**
    * Copy object formt to cell default format.
    */
   public static void copyFormat(VSFormat tfmt, VSCompositeFormat sfmt) {
      VSFormat userfmt = sfmt.getUserDefinedFormat();

      if(userfmt.isAlignmentDefined() && !tfmt.isAlignmentValueDefined()) {
         tfmt.setAlignment(sfmt.getAlignment());
      }

      if(userfmt.isWrappingDefined() && !tfmt.isWrappingValueDefined()) {
         tfmt.setWrapping(sfmt.isWrapping());
      }

      if(userfmt.isForegroundDefined() && !tfmt.isForegroundValueDefined()) {
         tfmt.setForeground(sfmt.getForeground());
      }

      if(userfmt.isFontDefined() && !tfmt.isFontValueDefined()) {
         Font sfont = sfmt.getFont();
         tfmt.setFont(sfont);
      }

      if(userfmt.isBackgroundDefined() && !tfmt.isBackgroundValueDefined()) {
         tfmt.setBackground(sfmt.getBackground());
      }

      if(userfmt.isBorderColorsDefined() && !tfmt.isBorderColorsValueDefined()) {
         tfmt.setBorderColors(sfmt.getBorderColors());
      }

      if(userfmt.isAlphaDefined() && !tfmt.isAlphaValueDefined()) {
         tfmt.setAlpha(sfmt.getAlpha());
      }

      if(userfmt.isFormatDefined() && !tfmt.isFormatValueDefined()) {
         tfmt.setFormat(sfmt.getFormat());
         tfmt.setFormatExtent(sfmt.getFormatExtent());
      }
   }

   /**
    * Get the embedded table with same source of the selection assembly.
    */
   public static String getEmbeddedTableWithSameSource(Viewsheet vs,
                                                       SelectionVSAssembly selection)
   {
      if(selection == null) {
         return null;
      }

      Assembly[] assemblies = vs.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof EmbeddedTableVSAssembly)) {
            continue;
         }

         final String embeddedSource = VSUtil.getSourceTable((EmbeddedTableVSAssembly) assembly);

         if(selection.getTableNames().contains(embeddedSource)) {
            return assembly.getName();
         }
      }

      return null;
   }

   /**
    * Get the runtime worksheet based on viewsheet.
    */
   public static RuntimeWorksheet getRuntimeWorksheet(Viewsheet vs, ViewsheetSandbox box) {
      Worksheet ws = vs.getBaseWorksheet();

      if(ws == null) {
         return null;
      }

      // @by billh, for vs binds to logical model, we need to shrink worksheet,
      // so that table joins are properly maintained. This place might be the
      // best position to perform such a task for WorksheetEvent
      if(vs.isLMSource()) {
         ws = new WorksheetWrapper(ws);
         VSUtil.shrinkTable(vs, ws);
      }

      return new RuntimeWorksheet(vs.getBaseEntry(), ws, box.getUser(), false);
   }

   /**
    * Get the export options.
    */
   public static String[] getExportOptions(boolean globalProperty) {
      String[] types = null;
      String property = SreeEnv.getProperty("vsexport.menu.options", false, !globalProperty);

      if(property != null) {
         types = property.isEmpty() ? new String[0] : property.split(",");
      }

      if(types == null) {
         types = new String[] {
            FileFormatInfo.EXPORT_NAME_EXCEL, FileFormatInfo.EXPORT_NAME_POWERPOINT,
            FileFormatInfo.EXPORT_NAME_PDF, FileFormatInfo.EXPORT_NAME_HTML,
            FileFormatInfo.EXPORT_NAME_PNG, FileFormatInfo.EXPORT_NAME_SNAPSHOT,
            FileFormatInfo.EXPORT_NAME_CSV
         };
      }

      return types;
   }

   /**
    * Get the export options.
    */
   public static String[] getExportOptions() {
      return getExportOptions(false);
   }

   public static boolean isInTab(VSAssembly obj) {
      if(obj == null) {
         return false;
      }

      VSAssembly container = obj.getContainer();

      if(container == null) {
         return false;
      }
      else if(container instanceof TabVSAssembly) {
         return true;
      }

      return VSUtil.isInTab(container);
   }

   public static TabVSAssembly getTabContainer(VSAssembly obj) {
      if(obj == null) {
         return null;
      }

      VSAssembly container = obj.getContainer();

      if(container == null) {
         return null;
      }
      else if(container instanceof TabVSAssembly) {
         return (TabVSAssembly) container;
      }

      return VSUtil.getTabContainer(container);
   }

   /**
    * Check whether the assembly show in tab.
    * @param obj vs assembly
    * @param vs viewsheet.
    * @param fixSelected
    * @return
    */
   public static boolean isVisibleInTab(VSAssembly obj, final Viewsheet vs, boolean fixSelected) {
      VSAssembly container = obj.getContainer();

      if(container == null) {
         return true;
      }

      if(container instanceof TabVSAssembly) {
         TabVSAssembly tab = (TabVSAssembly) container;

         if(obj.isVisible() && fixSelected) {
            long viscnt = Arrays.stream(tab.getAssemblies())
               .map(name -> tab.getViewsheet().getAssembly(name))
               .filter(aobj -> aobj != null && aobj.isVisible())
               .count();

            // if only one child is visible, the tab is not displayed
            // but the child is always displayed
            if(viscnt == 1) {
               return true;
            }
         }

         TabVSAssemblyInfo tinfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();

         if(fixSelected) {
            tinfo = (TabVSAssemblyInfo) tinfo.clone();
            VSUtil.fixSelected(tinfo, vs);
         }

         return tinfo.getSelected().equals(obj.getName());
      }

      if(!vs.isVisible(container, Viewsheet.SHEET_RUNTIME_MODE)) {
         return false;
      }

      return isVisibleInTab(container, vs, fixSelected);
   }

   /**
    * Checks whether the table name starts with "__vs_assembly__"
    */
   public static boolean isVSAssemblyBinding(String tableName) {
      return tableName != null && tableName.startsWith(Assembly.TABLE_VS_BOUND);
   }

   public static boolean isVSAssemblyBinding(VSAssembly assembly) {
      return assembly instanceof DataVSAssembly &&
         VSUtil.isVSAssemblyBinding((assembly).getTableName());
   }

   /**
    * Returns the actual vs assembly name
    */
   public static String getVSAssemblyBinding(String tableName) {
      if(VSUtil.isVSAssemblyBinding(tableName)) {
         tableName = tableName.substring(Assembly.TABLE_VS_BOUND.length());
      }
      else if(VSUtil.isCubeSource(tableName)) {
         tableName = VSUtil.getCubeSource(tableName);
      }

      return tableName;
   }

   /**
    * Checks whether the table name starts with CUBE_VS.
    */
   public static boolean isCubeSource(String tableName) {
      if(tableName == null || !tableName.startsWith(Assembly.CUBE_VS)) {
         return false;
      }

      tableName = tableName.substring(Assembly.CUBE_VS.length());
      int idx = tableName.lastIndexOf("/");

      return idx >= 0;
   }

   public static DrillLevel getDrillLevel(DataRef ref, XCube cube) {
      if(!(ref instanceof VSDimensionRef) || ((VSDimensionRef) ref).isDynamic()) {
         return DrillLevel.None;
      }

      VSDimensionRef dim = (VSDimensionRef) ref;
      VSDimensionRef child = VSUtil.getNextLevelRef(dim, cube, true);
      VSDimensionRef parent = VSUtil.getLastDrillLevelRef(dim, cube);

      if(child != null && parent != null) {
         return DrillLevel.Middle;
      }
      else if(child != null && parent == null) {
         return DrillLevel.Root;
      }
      else if(child == null && parent != null) {
         return DrillLevel.Leaf;
      }
      else {
         return DrillLevel.Normal;
      }
   }

   /**
    * Returns the actual cube source.
    */
   public static String getCubeSource(String tableName) {
      if(VSUtil.isCubeSource(tableName)) {
         tableName = tableName.substring(Assembly.CUBE_VS.length());
      }

      return tableName;
   }

   /**
    * Checks for a possible circular dependency
    *
    * @param srcAssembly the assembly as potential source for binding.
    * @param bindAssembly assembly in binding pane
    * @param vs current viewsheet
    * @return false if no circular dependency
    */
   public static boolean isBoundTo(Assembly srcAssembly, Assembly bindAssembly, Viewsheet vs) {
      if(!(srcAssembly instanceof DataVSAssembly) && !(srcAssembly instanceof TimeSliderVSAssembly) ||
         srcAssembly instanceof DataVSAssembly && ((DataVSAssembly) srcAssembly).isEmbedded())
      {
         return false;
      }

      if(srcAssembly instanceof TimeSliderVSAssembly) {
         TimeSliderVSAssembly tassembly = (TimeSliderVSAssembly) srcAssembly;

         if(tassembly.getSourceType() != XSourceInfo.VS_ASSEMBLY) {
            return false;
         }

         return Tool.equals(tassembly.getTableName(), bindAssembly.getName());
      }

      SourceInfo sinfo = ((DataVSAssembly) srcAssembly).getSourceInfo();

      if(sinfo != null && sinfo.getType() == XSourceInfo.VS_ASSEMBLY) {
         String source = VSUtil.getVSAssemblyBinding(sinfo.getSource());

         if(source.equals(bindAssembly.getName())) {
            return true;
         }
         else {
            return isBoundTo(vs.getAssembly(source), bindAssembly, vs);
         }
      }
      else {
         return false;
      }
   }

   public static void fixVariableDimInAgg(Map<String, String> find, List<XDimensionRef> bindingDims,
                                          DataRef agg)
   {
      if(bindingDims == null || bindingDims.size() == 0 || !(agg instanceof VSAggregateRef)) {
         return;
      }

      VSAggregateRef aggregateRef = (VSAggregateRef) agg;
      Calculator calculator = aggregateRef.getCalculator();

      String dimName = null;

      if(calculator instanceof PercentCalc) {
         dimName = ((PercentCalc) calculator).getColumnNameValue();
      }
      else if(calculator instanceof ValueOfCalc) {
         dimName = ((ValueOfCalc) calculator).getColumnNameValue();
      }
      else if(calculator instanceof RunningTotalCalc) {
         dimName = ((RunningTotalCalc) calculator).getBreakByValue();
      }

      String variableDimName = find.get(dimName) == null ?
         getVariableDimensionName(bindingDims, dimName) : find.get(dimName);

      if(variableDimName == null) {
         return;
      }
      else {
         find.put(dimName, variableDimName);
      }

      if(calculator instanceof PercentCalc) {
         ((PercentCalc) calculator).setColumnName(variableDimName);
      }
      else if(calculator instanceof ValueOfCalc) {
         ((ValueOfCalc) calculator).setColumnName(variableDimName);
      }
      else if(calculator instanceof RunningTotalCalc) {
         ((RunningTotalCalc) calculator).setBreakBy(variableDimName);
      }
   }

   public static void updateCalculate(VSCrosstabInfo cinfo, BindingDropTarget dropTarget) {
      Arrays.stream(cinfo.getDesignAggregates()).forEach((ref) -> {
         VSAggregateRef agg = (VSAggregateRef) ref;
         Calculator calculator = agg.getCalculator();
         DataRef[] rows = cinfo.getRuntimeRowHeaders();
         DataRef[] cols = cinfo.getRuntimeColHeaders();

         if(calculator instanceof ValueOfCalc || calculator instanceof PercentCalc ||
            calculator instanceof RunningTotalCalc)
         {
            String columnName = ((AbstractCalc) calculator).getColumnName();
            boolean findRef = findDataRef(rows, cols, columnName);

            if(!findRef) {
               if(calculator instanceof ValueOfCalc) {
                  columnName = ((ValueOfCalc) calculator).getColumnNameValue();
               }
               else if(calculator instanceof RunningTotalCalc) {
                  columnName = ((RunningTotalCalc) calculator).getBreakByValue();
               }

               if(VSUtil.isVariableValue(columnName) || VSUtil.isScriptValue(columnName)) {
                  findRef = findDataRef(rows, cols, columnName);
               }
            }

            String dropType = dropTarget == null ? null : dropTarget.getDropType();
            updateCalculateInfo(rows.length != 0, cols.length != 0,
               findRef, calculator, dropType);

            if(calculator instanceof PercentCalc) {
               cinfo.setPercentageByValue(((PercentCalc) calculator).getPercentageByValue());
            }
         }
      });
   }

   private static boolean findDataRef(DataRef[] rows, DataRef[] cols, String columnName) {
      return Arrays.stream(ArrayUtils.addAll(rows, cols))
         .anyMatch((field) -> {
            String group = (field instanceof VSDimensionRef)
               ? ((VSDimensionRef) field).getGroupColumnValue() : "";

            if(VSUtil.isDynamicValue(group) && Tool.equals(columnName, group)) {
               return true;
            }

            return Tool.equals(columnName, ((VSDataRef) field).getFullName());
         });
   }

   /**
    * Get specific dimensions on binding.
    *
    * @param name specific dimension name.
    */
   private static String getVariableDimensionName(List<XDimensionRef> bindingDims, String name) {
      VSDimensionRef dim = getVariableDimension0(bindingDims, name);

      return dim == null ? name : dim.getFullNameByVariable();
   }

   private static VSDimensionRef getVariableDimension0(List<XDimensionRef> refs, String name) {
      if(StringUtils.isEmpty(name)) {
         return null;
      }

      for(DataRef ref : refs) {
         if(ref instanceof VSDimensionRef) {
            VSDimensionRef dimensionRef = (VSDimensionRef) ref;

            if(dimensionRef.isVariable() && StringUtils.equals(dimensionRef.getFullName(), name))
            {
               return dimensionRef;
            }
         }
         else if(ref instanceof AestheticRef) {
            DataRef ref0 = ((AestheticRef) ref).getDataRef();

            if(!(ref0 instanceof VSDimensionRef)) {
               continue;
            }

            VSDimensionRef dimensionRef0 = (VSDimensionRef) ref0;

            if(dimensionRef0.isVariable() &&
               StringUtils.equals(dimensionRef0.getFullName(), name))
            {
               return dimensionRef0;
            }
         }
      }

      return null;
   }

   /**
    * fix the VariableAggregate in dimension to dynamic.
    * @param find
    * @param bindingAggs
    * @param dim
    */
   public static void fixVariableAggInDim(Map<String, VSAggregateRef> find, DataRef[] bindingAggs,
                                          DataRef dim)
   {
      if(bindingAggs == null || bindingAggs.length == 0 || !(dim instanceof VSDimensionRef)) {
         return;
      }

      VSDimensionRef dimensionRef = (VSDimensionRef) dim;
      String sortByCol = dimensionRef.getSortByColValue();
      String rankingCol = dimensionRef.getRankingColValue();
      VSAggregateRef sortByRef = findVariableAggregate(bindingAggs, sortByCol, find);

      if(sortByRef != null) {
         dimensionRef.setSortByColValue(sortByRef.getFullNameByDVariable());
         dimensionRef.setSortByCol(sortByCol);
      }

      VSAggregateRef rankingRef = findVariableAggregate(bindingAggs, rankingCol, find);

      if(rankingRef != null) {
         dimensionRef.setRankingColValue(rankingRef.getFullNameByDVariable());
         dimensionRef.setRankingCol(rankingCol);
      }
   }

   private static VSAggregateRef findVariableAggregate(DataRef[] aggs, String name,
                                                Map<String, VSAggregateRef> find)
   {
      if(aggs == null || aggs.length == 0 || StringUtils.isEmpty(name)) {
         return null;
      }

      if(find != null && find.get(name) != null) {
         return find.get(name);
      }

      for(DataRef agg : aggs) {
         if(!(agg instanceof VSAggregateRef)) {
            continue;
         }

         VSAggregateRef vsAggregateRef = (VSAggregateRef) agg;

         if(vsAggregateRef.isVariable() && name.equals(vsAggregateRef.getFullName())) {
            find.put(name, vsAggregateRef);

            return vsAggregateRef;
         }
      }

      return null;
   }

   /**
    * Gets calc table column name given the cell name
    */
   public static String getCalcTableColumnNameFromCellName(String cellName,
                                                           CalcTableVSAssembly assembly)
   {
      TableCellBinding binding = getCalcCellBindingFromCellName(cellName, assembly);

      if(binding != null) {
         return binding.getValue();
      }

      return cellName;
   }

   /**
    * Gets calc table column name given the cell name
    */
   public static TableCellBinding getCalcCellBindingFromCellName(String cellName,
                                                       CalcTableVSAssembly assembly)
   {
      if(cellName == null) {
         return null;
      }

      TableLayout layout = assembly.getTableLayout();

      for(BaseLayout.Region region : layout.getRegions()) {
         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               TableCellBinding bind = (TableCellBinding) region.getCellBinding(r, c);

               if(bind != null && bind.getType() == CellBinding.BIND_COLUMN) {
                  if(cellName.equals(layout.getRuntimeCellName(bind)) && bind.getFormula() == null) {
                     return bind;
                  }
               }
            }
         }
      }

      return null;
   }

   /**
    * Determines whether two rectangles intersect
    * @param rec rectangle1.
    * @param rec2 rectangle2.
    * @return
    */
   public static boolean isIntersecting(Rectangle rec, Rectangle rec2) {
      double xCenterDistance = Math.abs(rec.getCenterX() - rec2.getCenterX());
      double yCenterDistance = Math.abs(rec.getCenterY() - rec2.getCenterY());

      return xCenterDistance < (rec.width + rec2.width) / 2 &&
            yCenterDistance < (rec.height + rec2.height) / 2;
   }

   public static String getAggregateField(String field, DataRef ref) {
      VSAggregateRef vref = (VSAggregateRef) ref;
      DataRef dref = vref.getDataRef();
      ColumnRef colRef =
              dref instanceof ColumnRef ? (ColumnRef) dref : null;

      if(colRef != null && colRef.getAlias() == null &&
              colRef.getDataRef() instanceof AliasDataRef)
      {
         field = colRef.getAttribute();

         if(vref.getCalculator() != null) {
            field = vref.getFullName(field, null, vref.getCalculator());
         }
      }

      return field;
   }

   /**
    * String the _O suffix from table name.
    */
   public static String stripOuter(String name) {
      if(name != null && name.endsWith("_O")) {
         return name.substring(0, name.length() - 2);
      }

      return name;
   }

   public static VSDimensionRef createPeriodDimensionRef(Viewsheet vs, DataVSAssembly assembly,
      ColumnSelection cols, VSCrosstabInfo cinfo)
   {
      if(assembly == null) {
         return null;
      }

      if(assembly instanceof CrosstabVSAssembly &&
         !((CrosstabVSAssembly) assembly).supportPeriod())
      {
         return null;
      }

      String tname = assembly.getTableName();

      if(tname == null || tname.length() == 0) {
         return null;
      }

      CalendarVSAssembly calendar = VSUtil.getPeriodCalendar(vs, tname);

      if(calendar == null) {
         return null;
      }

      int dtype = calendar.getDateType();
      DataRef ref = calendar.getDataRef();
      ref = AssetUtil.getColumnRefFromAttribute(cols, ref);

      if(ref == null) {
         return null;
      }

      DataRef[] rheaders = cinfo.getRuntimeRowHeaders();
      DataRef[] cheaders = cinfo.getRuntimeColHeaders();

      if(ref instanceof ColumnRef) {
         ColumnRef nref = VSUtil.getVSColumnRef((ColumnRef) ref);

         if(containPeriodDimensionRef(rheaders, nref) ||
            containPeriodDimensionRef(cheaders, nref))
         {
            return null;
         }
      }

      int dlevel = VSUtil.getPeriodDateLevel(dtype);
      String name = VSUtil.getAttribute(ref) + "_Period";
      String odtype = ref.getDataType();
      ref = ((ColumnRef) ref).getDataRef();
      DateRangeRef range = new DateRangeRef(name, ref, dlevel);
      range.setOriginalType(odtype);
      ColumnRef column = new ColumnRef(range);
      column.setDataType(odtype);
      VSDimensionRef dim = new VSDimensionRef(column);
      dim.setDateLevel(dlevel);
      dim.setDates(calendar.getDates());
      cols.addAttribute(column);
      return dim;
   }

   /**
    * Get data ref list by the specified OutputVSAssemblyInfo.
    */
   public static String[] getDataRefList(OutputVSAssemblyInfo info, RuntimeViewsheet rvs) {
      ArrayList<String> refs = new ArrayList<>();

      if(info == null) {
         return null;
      }

      ScalarBindingInfo bindingInfo = info.getScalarBindingInfo();

      if(bindingInfo != null) {
         String bindingDesc = VSUtil.getBindingDescription(info, rvs);

         if(!Tool.isEmptyString(bindingDesc)) {
            refs.add(bindingDesc);
         }
      }

      return refs.toArray(new String[0]);
   }

   public static String[] getAvailableTipValues(String[] dataRefList) {
      String[] tips = new String[dataRefList.length];

      for(int i = 0; i < tips.length; i++) {
         tips[i] = "{" + i + "}" + " " + dataRefList[i];
      }

      return tips;
   }

   /**
    * Get binding description by the specified OutputVSAssemblyInfo.
    */
   public static String getBindingDescription(OutputVSAssemblyInfo info, RuntimeViewsheet rvs) {
      if(info == null || info.getBindingInfo() == null) {
         return null;
      }

      ScalarBindingInfo binfo = info.getScalarBindingInfo();
      String aggValue = binfo.getAggregateValue();
      aggValue = Tool.isEmptyString(aggValue) || "none".equals(aggValue) ? null : aggValue;
      String colValue = binfo.getColumnValue();
      colValue = Tool.isEmptyString(colValue) || "null".equals(colValue) ? null : colValue;
      String col2Value = binfo.getColumn2Value();
      col2Value = Tool.isEmptyString(col2Value) || "null".equals(col2Value) ? null : col2Value;
      String nValue = binfo.getNValue();
      nValue = Tool.isEmptyString(nValue) || "null".equals(nValue) ? null : nValue;

      if(VSUtil.isDynamicValue(aggValue)) {
         aggValue = binfo.getRuntimeAggregateValue();
      }

      if(VSUtil.isDynamicValue(colValue)) {
         colValue = binfo.getRuntimeColumnValue();
      }

      if(VSUtil.isDynamicValue(col2Value)) {
         col2Value = binfo.getRuntimeColumn2Value();
      }

      if(VSUtil.isDynamicValue(nValue)) {
         nValue = binfo.getRuntimeNValue();
      }

      VSAggregateRef ref = new VSAggregateRef();
      ref.setColumnValue(colValue);
      ref.setSecondaryColumnValue(col2Value);
      ref.setFormulaValue(aggValue);
      ref.setNValue(nValue);

      return ref.getFullName();
   }

   /**
    * If the tip format string contains text references, those need to be
    * converted to numeric.  Do this by parsing/rebuilding the format string,
    * referring to the array of allowed headers passed in.
    */
   public static String convertTipFormatToNumeric(String tipfmt, List<String> headers) {
      int fmtptr = 0;

      while(fmtptr < tipfmt.length()) {
         // Determine where the start of the format element is, by "{"
         int start = tipfmt.indexOf("{", fmtptr);

         if(start == -1) {
            break;
         }

         // Determine where the end of the format element is, by "}"
         int end = tipfmt.indexOf("}", start);

         if(end == -1) {
            break;
         }

         String str = tipfmt.substring(start + 1, end);

         if(headers.indexOf(str) > -1) {
            StringBuilder sb = new StringBuilder();
            sb.append(tipfmt.substring(0, start + 1));
            sb.append(headers.indexOf(str));
            sb.append(tipfmt.substring(end));
            tipfmt = sb.toString();
            fmtptr = start + 1;
            continue;
         }

         // Determine where the end of the reference is, by ","
         int endnumber = tipfmt.indexOf(",", start);

         if(endnumber == -1 || endnumber > end) {
            endnumber = end;
         }

         // Pull the reference out of the format element
         String fragnum = tipfmt.substring(start + 1, endnumber);

         try {
            int num = Integer.parseInt(fragnum);
         }
         catch (NumberFormatException nfe) {
            // If a non-numeric reference, then locate it in the headers array
            for(int i = 0; i < headers.size(); i++) {
               if(fragnum.trim().equals(headers.get(i))) {

                  // Once reference is located, rebuild the format string
                  StringBuilder sb = new StringBuilder();
                  sb.append(tipfmt.substring(0,start+1));
                  sb.append(i);
                  sb.append(tipfmt.substring(endnumber,tipfmt.length()));
                  tipfmt = sb.toString();
                  end = start;
                  break;
               }
            }
         }

         fmtptr = end + 1;
      }

      return tipfmt;
   }

   /**
    * Check headers contains create period dimension ref or not.
    */
   private static boolean containPeriodDimensionRef(DataRef[] ref, DataRef dim) {
      for(int i = 0; i < ref.length; i++) {
         if(dim.equals(ref[i])) {
            return true;
         }
      }

      return false;
   }

   /**
    * Fix the text height when text is auto size.
    * @param info text info.
    * @param vs viewsheet
    */
   public static void setAutoSizeTextHeight(AssemblyInfo info, Viewsheet vs) {
      if(!(info instanceof TextVSAssemblyInfo) || vs == null) {
         return;
      }

      Dimension size;
      Point pos;

      size = ((VSAssemblyInfo) info).getLayoutSize();
      pos = ((VSAssemblyInfo) info).getLayoutPosition();

      if(size == null) {
         size = vs.getPixelSize(info);
      }

      if(pos == null) {
         pos = vs.getPixelPosition(info);
      }

      CoordinateHelper.fixTextSize(size, pos, (TextVSAssemblyInfo) info);
   }

   /**
    * Marks columns matching latitude/longitude as geographic.
    */
   public static void setDefaultGeoColumns(VSChartInfo cinfo, RuntimeViewsheet rvs,
                                           String tableName)
   {
      final ColumnSelection columns = VSUtil.getColumnsForVSAssemblyBinding(rvs, tableName);
      final ColumnSelection geoColumns = cinfo.getGeoColumns();
      final List<String> geoNames = Arrays.asList("latitude", "lat",
                                                  "longitude", "long", "lon");
      final List<String> partialGeoNames = Arrays.asList(" latitude", " longitude");

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         final ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(VSEventUtil.isMeasure(column)) {
            String colname = column.getName().toLowerCase();

            if(geoNames.contains(colname) ||
               partialGeoNames.stream().anyMatch(p -> colname.endsWith(p)))
            {
               geoColumns.addAttribute(new BaseField(column.getName()));
            }
         }
      }
   }

   /**
    * if contains condition list defined in viewsheet, wrap worksheet to
    * avoid it being polluted, for the table assembly in the worksheet will
    * be changed by merging condition lists
    * @param vs
    * @param vassembly
    * @return
    */
   public static boolean createWsWrapper(Viewsheet vs,  VSAssembly vassembly) {
      boolean sqlCube = XCube.SQLSERVER.equals(getCubeType(vassembly));
      boolean dynamic = false;

      if(vassembly instanceof DynamicBindableVSAssembly) {
         DynamicBindableVSAssembly dassembly =
            (DynamicBindableVSAssembly) vassembly;
         ConditionList conds = dassembly.getPreConditionList();

         if(conds != null && !conds.isEmpty()) {
            dynamic = true;
         }
      }

      return dynamic || vs.isDirectSource() || sqlCube ||
         DateComparisonUtil.containsDateComparison(vs);
   }

   /**
    * Returns only the flyovers that exist in the viewsheet
    */
   public static String[] getValidFlyovers(String[] flyovers, Viewsheet vs) {
      if(flyovers != null && vs != null) {
         return Arrays.stream(flyovers)
            .filter(vs::containsAssembly)
            .toArray(String[]::new);
      }

      return flyovers;
   }

   /**
    * Update an annotation z-index if the annotation is for assembly of embedded vs.
    *
    * @param parentAssembly      the parent assembly of the annotation.
    * @param annotation          the annotation assembly
    */
   public static void updateEmbeddedVSAnnotationZIndex(final AnnotationVSAssembly annotation,
                                                       final VSAssembly parentAssembly)
   {
      if(annotation == null || parentAssembly == null) {
         return;
      }

      if(annotation.getViewsheet() == parentAssembly.getViewsheet()) {
         return;
      }

      if(annotation.getZIndex() < parentAssembly.getZIndex()) {
         annotation.setZIndex(parentAssembly.getZIndex() + VSUtil.getZIndexGap(parentAssembly));
      }
   }

   public static boolean hideActionsForHostAssets(AssetEntry entry, Principal principal) {
      if(entry == null || principal == null) {
         return false;
      }

      String orgId = ((XPrincipal) principal).getOrgId();

      return SUtil.isDefaultVSGloballyVisible(principal) &&
         !orgId.equals(Organization.getDefaultOrganizationID()) &&
         Tool.equals(entry.getOrgID(), Organization.getDefaultOrganizationID());
   }

   public static boolean isDefaultVSGloballyViewsheet(AssetEntry entry, Principal user) {
      String orgID = entry.getOrgID();
      String currentOrgID = user instanceof XPrincipal ?
         ((XPrincipal) user).getOrgId() : OrganizationManager.getInstance().getCurrentOrgID();

      return SUtil.isDefaultVSGloballyVisible() && !Tool.equals(orgID, currentOrgID)
         && Tool.equals(orgID, Organization.getDefaultOrganizationID());
   }

   /**
    * Template switch current org to host-org for the share global assets.
    *
    * @param sheetRuntimeId runtime sheet id.
    * @param principal current org.
    */
   public static <T> T globalShareVsRunInHostScope(String sheetRuntimeId, Principal principal,
                                                   Callable<T> call) throws Exception
   {
      return OrganizationManager.runInOrgScope(
         VSUtil.switchToHostOrgForGlobalShareAsset(sheetRuntimeId, principal) ?
            Organization.getDefaultOrganizationID() : null,
         call);
   }

   /**
    * Template switch current org to host-org for the share global assets.
    *
    * @param sheetRuntimeId runtime sheet id.
    * @param principal current org.
    */
   public static boolean switchToHostOrgForGlobalShareAsset(String sheetRuntimeId,
                                                         Principal principal)
   {

      if(sheetRuntimeId == null) {
         return false;
      }

      ViewsheetService service = SingletonManager.getInstance(ViewsheetService.class);

      try {
         RuntimeSheet runtimeSheet = service.getSheet(sheetRuntimeId, principal);

         if(runtimeSheet == null || runtimeSheet.getEntry() == null ||
            !(runtimeSheet instanceof RuntimeViewsheet))
         {
            return false;
         }

         AssetEntry entry = runtimeSheet.getEntry();

         if(SUtil.isDefaultVSGloballyVisible(principal) &&
            !Tool.equals(((XPrincipal) principal).getOrgId(), entry.getOrgID()) &&
            Tool.equals(entry.getOrgID(), Organization.getDefaultOrganizationID()))
         {
            return true;
         }
      }
      catch(ExpiredSheetException ignored) {
         // no-op
      }
      catch(Exception ignored) {
         LOG.warn("Can't get runtime viewsheet by id: " + sheetRuntimeId);
      }

      return false;
   }

   private static InheritableThreadLocal<Boolean> IGNORE_CSS = new InheritableThreadLocal<>();
   // cached dependency
   private static DataCache<String, Set<AssemblyRef>> scriptDeps = new DataCache<>(1000, 5000);
   private static final Pattern CONTAINS_VARIABLE_PATTERN = Pattern.compile("([\\s\\S]+)(\\$\\([\\s\\S]*?\\))([\\s\\S]*)");
   public static final ThreadLocal<Boolean> OPEN_VIEWSHEET = ThreadLocal.withInitial(() -> Boolean.FALSE);
   private static final String DEBOUNCER_KEY = "VSUtil.debouncer";

   private static final Logger LOG = LoggerFactory.getLogger(VSUtil.class);
}

/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.script;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.internal.graph.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import inetsoft.util.script.*;

import java.util.*;
import java.util.function.BiConsumer;

import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents an ChartInfo in the Javascript environment.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public abstract class AbstractChartBindingScriptable extends PropertyScriptable {
   /**
    * Create a chart aggregate ref.
    */
   protected abstract ChartAggregateRef createChartAggregateRef();

   /**
    * Create a chart dimension ref.
    */
   protected abstract ChartDimensionRef createChartDimensionRef();

   /**
    * Create an aesthetic ref for the named field.
    */
   protected abstract AestheticRef createAestheticRef(String field, String dtype);

   /**
    * Get chart info.
    */
   protected abstract ChartInfo getInfo();

   /**
    * Set top n for a dimension column.
    * @param dim the dimension ref
    * @param n the top n
    */
   protected abstract void setTopN(XDimensionRef dim, int n);

   /**
    * Get top n for a dimension column.
    * @param dim the dimension ref
    * @return the top n of dimension
    */
   protected abstract int getTopN(XDimensionRef dim);

   /**
    * Set topn summary column for a dimension column.
    * @param dim the dimension ref
    * @param sumfield the summary column
    */
   protected abstract void setTopNSummaryCol(XDimensionRef dim, String sumfield);

   /**
    * Get topn summary column for a dimension column.
    * @param dim the dimension column ref
    * @return the summary column
    */
   protected abstract String getTopNSummaryCol(XDimensionRef dim);

   /**
    * Set topn reverse for a dimension column.
    * @param dim the dimension column ref
    * @param reserve <code>true</code> if reverse
    */
   protected abstract void setTopNReverse(XDimensionRef dim, boolean reserve);

   /**
    * Get topn reverse for a dimension column.
    * @param dim the dimension column ref
    * @return <code>true</code> if reverse, <code>false</code> means not found
    * or not reverse
    */
   protected abstract boolean isTopNReverse(XDimensionRef dim);

   /**
    * Initialize the object.
    */
   protected void init() {
      if(inited) {
         return;
      }

      inited = true;
      Class[] sparams = {String.class};
      Class[] oparams = {Object.class};
      Class[] iparams = {int.class};
      Class[] ssparams = {String.class, String.class};
      Class[] sssparams = {String.class, String.class, String.class};
      Class[] oosparams = {Object.class, Object.class, String.class};
      Class[] ssiparams = {String.class, String.class, int.class};
      Class[] siiparams = {String.class, int.class, int.class};
      Class[] siooparams = {String.class, int.class, Object.class, Object.class};
      Class[] siparams = {String.class, int.class};
      Class[] sbparams = {String.class, boolean.class};
      Class[] sbiparams = {String.class, boolean.class, int.class};

      try {
         addProperty("breakdownFields", "getBreakdownFields",
            "setBreakdownFields", Object[].class, getClass(), this);
         addProperty("pathField", "getPathField", "setPathField",
            Object[].class, getClass(), this);

         // candle bindingrefs
         addFunctionProperty(getClass(), "setCandleBindingField", Object.class);
         addFunctionProperty(getClass(), "getCandleBindingField", iparams);

         // stock bindingrefs
         addFunctionProperty(getClass(), "setStockBindingField", Object.class);
         addFunctionProperty(getClass(), "getStockBindingField", iparams);

         // color aesthetic
         addProperty("colorFrame", "getColorFrame", "setColorFrame",
            ColorFrame.class, getClass(), this);

        // size aesthetic
         addProperty("sizeFrame", "getSizeFrame", "setSizeFrame",
            SizeFrame.class, getClass(), this);

         // shape aesthetic
         addProperty("shapeFrame", "getShapeFrame", "setShapeFrame",
            ShapeFrame.class, getClass(), this);
         addProperty("lineFrame", "getLineFrame", "setLineFrame",
            LineFrame.class, getClass(), this);
         addProperty("textureFrame", "getTextureFrame", "setTextureFrame",
            TextureFrame.class, getClass(), this);

         // aggregate
         addFunctionProperty(getClass(), "setFormula", ssiparams);
         addFunctionProperty(getClass(), "getFormula", siparams);
         addFunctionProperty(getClass(), "setSecondaryField", ssiparams);
         addFunctionProperty(getClass(), "getSecondaryField", siparams);
         addFunctionProperty(getClass(), "setPercentageType", siiparams);
         addFunctionProperty(getClass(), "getPercentageType", siparams);
         addFunctionProperty(getClass(), "setDiscrete", sbiparams);
         addFunctionProperty(getClass(), "isDiscrete", siparams);

         // group
         addFunctionProperty(getClass(), "setTopN", siiparams);
         addFunctionProperty(getClass(), "getTopN", siparams);
         addFunctionProperty(getClass(), "setTopNSummaryCol", ssiparams);
         addFunctionProperty(getClass(), "getTopNSummaryCol", siparams);
         addFunctionProperty(getClass(), "setTopNReverse", sbiparams);
         addFunctionProperty(getClass(), "isTopNReverse", siparams);
         addFunctionProperty(getClass(), "setColumnOrder", siooparams);
         addFunctionProperty(getClass(), "getColumnOrder", siparams);
         addFunctionProperty(getClass(), "setGroupOrder", siiparams);
         addFunctionProperty(getClass(), "getGroupOrder", siparams);
         addFunctionProperty(getClass(), "setTimeSeries", sbparams);
         addFunctionProperty(getClass(), "isTimeSeries", sparams);

         // geographic field
         addFunctionProperty(getClass(), "setMapLayer", ssparams);
         addFunctionProperty(getClass(), "getMapLayer", sparams);
         addFunctionProperty(getClass(), "getMappings", sparams);
         addFunctionProperty(getClass(), "addMapping", sssparams);
         addFunctionProperty(getClass(), "removeMapping", ssparams);
         addFunctionProperty(getClass(), "getColorField", oparams);
         addFunctionProperty(getClass(), "setColorField", oosparams);
         addFunctionProperty(getClass(), "getShapeField", oparams);
         addFunctionProperty(getClass(), "setShapeField",  oosparams);
         addFunctionProperty(getClass(), "getSizeField", oparams);
         addFunctionProperty(getClass(), "setSizeField", oosparams);
         addFunctionProperty(getClass(), "getTextField", oparams);
         addFunctionProperty(getClass(), "setTextField", oosparams);
      }
      catch(Exception e) {
         LOG.error("Failed to register chart binding properties and functions", e);
      }
   }

   /**
    * Get formula.
    */
   protected AggregateFormula getFormula(String formula) {
      if(formula == null || "".equals(formula)) {
         if(getMapInfo() != null) {
            return AggregateFormula.NONE;
         }
         else {
            return AggregateFormula.SUM;
         }
      }
      else {
         return AggregateFormula.getFormula(formula);
      }
   }

   /**
    * Get a list of breakdown fields in chart info.
    * @return null if value is not set.
    */
   public String[] getBreakdownFields() {
      if(!(getInfo() instanceof MergedChartInfo)) {
         ChartRef[] refs = getInfo().getGroupFields();
         String[] names = new String[refs.length];

         for(int i = 0; i < refs.length; i++) {
            names[i] = refs[i].getName();
         }

         return names;
      }

      return new String[0];
   }

   /**
    * Set a list of breakdown fields in chart info.
    */
   public void setBreakdownFields(Object[] fields) {
      if(getInfo() instanceof MergedChartInfo) {
         return;
      }

      for(int i = 0; i < fields.length; i++) {
         if(JSObject.isArray(fields[i])) {
            Object[] arr = JSObject.split(fields[i]);

            // array contains column name and column type
            if(arr.length >= 2) {
               String cname = arr[0].toString();
               String ctype = arr[1].toString();
               DataRef ref = createDataRef(cname);

               if(ChartConstants.STRING.equals(ctype) ||
                  ChartConstants.DATE.equals(ctype) ||
                  ChartConstants.NUMBER.equals(ctype))
               {
                  ChartRef newRef = ChartConstants.NUMBER.equals(ctype) ?
                     getChartRef(false, ref, cname, ctype, arr) :
                     getChartRef(true, ref, cname, ctype, arr);

                  if(checkFieldByName(getInfo().getGroupFields(),
                     newRef.getName()))
                  {
                     return;
                  }

                  getInfo().addGroupField(newRef);
                  getInfo().updateChartType(!getInfo().isMultiStyles());
               }
            }
         }
      }
   }

   /**
    * Get path field in chart info.
    * @return null if value is not set.
    */
   public String getPathField() {
      if(getInfo().supportsPathField()) {
         ChartRef ref = getInfo().getPathField();

         return ref == null ? null : ref.getName();
      }

      return null;
   }

   /**
    * Set a path field in chart info.
    */
   public void setPathField(Object[] field) {
      if(!getInfo().supportsPathField()) {
         LOG.warn("The chart type does not support paths");
         return;
      }

      // array contains column name and column type
      if(field.length >= 2) {
         String cname = field[0].toString();
         String ctype = field[1].toString();
         DataRef ref = createDataRef(cname);

         if(ChartConstants.STRING.equals(ctype) ||
            ChartConstants.DATE.equals(ctype) ||
            ChartConstants.NUMBER.equals(ctype))
         {
            ChartRef newRef = ChartConstants.NUMBER.equals(ctype) ?
               getChartRef(false, ref, cname, ctype, field) :
               getChartRef(true, ref, cname, ctype, field);

            if(getInfo().getPathField() != null &&
               getInfo().getPathField().getName().equals(newRef.getName()))
            {
               return;
            }

            getInfo().setPathField(newRef);
            getInfo().updateChartType(!getInfo().isMultiStyles());
         }
      }
   }

   /**
    * Create a chart ref.
    */
   private ChartRef getChartRef(boolean isDim, DataRef ref, String cname,
      String ctype, Object[] arr)
   {
      if(isDim) {
         ChartDimensionRef dim = createChartDimensionRef();
         dim.setDataRef(ref);

         if(dim instanceof VSDimensionRef) {
            ((VSDimensionRef) dim).setGroupColumnValue(cname);
         }

         if(ChartConstants.DATE.equals(ctype)) {
            int level = arr.length == 3 ?
               Integer.parseInt(arr[2].toString()) :
               DateRangeRef.YEAR_INTERVAL;
            ((BaseField) ref).setDataType(ctype);
            setDateLevelValue(dim, level);
         }

         return dim;
      }
      else {
         ChartAggregateRef agg = createChartAggregateRef();
         agg.setDataRef(ref);
         agg.setFormula(AggregateFormula.SUM);

         if(agg instanceof VSAggregateRef) {
            ((VSAggregateRef) agg).setColumnValue(cname);
         }

         return agg;
      }
   }

   /**
    * Set binding fields of candle.
    */
   public void setCandleBindingField(Object obj) {
      if(JSObject.isArray(obj)) {
         Object[] arr = JSObject.split(obj);

         // array contains column name and field type, high, close, low, open,
         // and formula
         if(arr.length >= 2) {
            String cname = arr[0].toString();
            int btype = Integer.parseInt(arr[1].toString());
            DataRef ref = createDataRef(cname);
            String formula = arr.length >= 3 ? arr[2].toString() : null;
            int poption = arr.length >= 4 ?
               Double.valueOf(arr[3].toString()).intValue() :
               XConstants.PERCENTAGE_NONE;
            String field2 = arr.length == 5 ? arr[4].toString() : null;
            DataRef secCol = field2 == null || "".equals(field2) ? null :
               createDataRef(field2);

            ChartAggregateRef aref = createChartAggregateRef();
            aref.setDataRef(ref);
            AggregateFormula aformula = formula == null || "".equals(formula) ?
               AggregateFormula.SUM : AggregateFormula.getFormula(formula);
            aref.setFormula(aformula);
            aref.setCalculator(GraphUtil.getCalculator(poption));

            if(secCol != null) {
               setSecondaryColumnValue(aref, secCol);
            }

            creatCandleField(btype, aref);
         }
      }
   }

   /**
    * Create the candle field.
    */
   private void creatCandleField(int type, ChartAggregateRef aref) {
      if(getInfo() instanceof CandleChartInfo) {
         switch(type) {
         case ChartConstants.HIGH:
            ((CandleChartInfo) getInfo()).setHighField(aref);
            break;
         case ChartConstants.LOW:
            ((CandleChartInfo) getInfo()).setLowField(aref);
            break;
         case ChartConstants.CLOSE:
            ((CandleChartInfo) getInfo()).setCloseField(aref);
            break;
         case ChartConstants.OPEN:
            ((CandleChartInfo) getInfo()).setOpenField(aref);
            break;
         default:
            break;
         }
      }
   }

   /**
    * Get binding field in candle.
    */
   public ChartRef getCandleBindingField(int type) {
      if(getInfo() instanceof CandleChartInfo) {
         switch(type) {
         case ChartConstants.HIGH:
            return ((CandleChartInfo) getInfo()).getHighField();
         case ChartConstants.LOW:
            return ((CandleChartInfo) getInfo()).getLowField();
         case ChartConstants.CLOSE:
            return ((CandleChartInfo) getInfo()).getCloseField();
         case ChartConstants.OPEN:
            return ((CandleChartInfo) getInfo()).getOpenField();
         default:
            return null;
         }
      }

      return null;
   }

   /**
    * Set binding fields of stock.
    */
   public void setStockBindingField(Object obj) {
      if(JSObject.isArray(obj)) {
         Object[] arr = JSObject.split(obj);

         // array contains column name and field type, high, close, low, open,
         // and formula
         if(arr.length >= 2) {
            String cname = arr[0].toString();
            int btype = Integer.parseInt(arr[1].toString());
            DataRef ref = createDataRef(cname);
            String formula = arr.length >= 3 ? arr[2].toString() : null;
            int poption = arr.length >= 4 ?
               Integer.parseInt(arr[3].toString()) :
               XConstants.PERCENTAGE_NONE;
            String field2 = arr.length == 5 ? arr[4].toString() : null;
            DataRef secCol = field2 == null || "".equals(field2) ? null :
               createDataRef(field2);

            ChartAggregateRef aref = createChartAggregateRef();
            aref.setDataRef(ref);
            AggregateFormula aformula = formula == null || "".equals(formula) ?
               AggregateFormula.SUM : AggregateFormula.getFormula(formula);
            aref.setFormula(aformula);
            aref.setCalculator(GraphUtil.getCalculator(poption));

            if(secCol != null) {
               setSecondaryColumnValue(aref, secCol);
            }

            creatStockField(btype, aref);
         }
      }
   }

   /**
    * Create the stock field.
    */
   private void creatStockField(int type, ChartAggregateRef aref) {
      if(getInfo() instanceof StockChartInfo) {
         switch(type) {
         case ChartConstants.HIGH:
            ((StockChartInfo) getInfo()).setHighField(aref);
            break;
         case ChartConstants.LOW:
            ((StockChartInfo) getInfo()).setLowField(aref);
            break;
         case ChartConstants.CLOSE:
            ((StockChartInfo) getInfo()).setCloseField(aref);
            break;
         case ChartConstants.OPEN:
            ((StockChartInfo) getInfo()).setOpenField(aref);
            break;
         default:
            break;
         }
      }
   }

   /**
    * Get binding field in stock.
    */
   public ChartRef getStockBindingField(int type) {
      if(getInfo() instanceof StockChartInfo) {
         switch(type) {
         case ChartConstants.HIGH:
            return ((StockChartInfo) getInfo()).getHighField();
         case ChartConstants.LOW:
            return ((StockChartInfo) getInfo()).getLowField();
         case ChartConstants.CLOSE:
            return ((StockChartInfo) getInfo()).getCloseField();
         case ChartConstants.OPEN:
            return ((StockChartInfo) getInfo()).getOpenField();
         default:
            return null;
         }
      }

      return null;
   }

   /**
    * Get the color frame.
    * @return the color frame.
    */
   public ColorFrame getColorFrame() {
      AestheticRef ref = getInfo().getColorField();

      if(ref != null) {
         return (ColorFrame) ref.getVisualFrame();
      }

      VSDataRef[] aggs = getInfo().getAggregateRefs();

      return (aggs.length > 0) ?
         ((ChartAggregateRef) aggs[0]).getColorFrame() : null;
   }

   /**
    * Set the color frame.
    * @param frame the color frame.
    */
   public void setColorFrame(ColorFrame frame) {
      if(frame != null) {
         AestheticRef ref = getInfo().getColorField();

         // if a colorframe is assigned in script and color mappings are explicitly defined,
         // must turn off useGlobal for the color to be applied. this may be a problem if
         // an existing color frame (instead of new) is assigned to another color binding,
         // although that use cases seems remote. (45391)
         if(frame instanceof CategoricalColorFrame) {
            CategoricalColorFrame cframe = (CategoricalColorFrame) frame;
            cframe.setUseGlobal(false);
         }

         if(ref != null) {
            ref.setVisualFrame(frame);
         }
         else {
            VSDataRef[] aggs = getInfo().getAggregateRefs();

            for(int i = 0; i < aggs.length; i++) {
               ChartAggregateRef agg = (ChartAggregateRef) aggs[i];
               agg.setColorFrame(frame);
            }
         }
      }
   }

   /**
    * Get the size frame.
    * @return the size frame.
    */
   public SizeFrame getSizeFrame() {
      AestheticRef ref = getInfo().getSizeField();

      if(ref != null) {
         // mark size as changed so the new size set in script is used
         ref.getVisualFrameWrapper().setChanged(true);
         return (SizeFrame) ref.getVisualFrame();
      }
      else {
         // same as above
         getInfo().getSizeFrameWrapper().setChanged(true);
         return getInfo().getSizeFrame();
      }
   }

   /**
    * Set the size frame.
    * @param frame the size frame.
    */
   public void setSizeFrame(SizeFrame frame) {
      if(frame != null) {
         AestheticRef ref = getInfo().getSizeField();

         if(ref != null) {
            ref.setVisualFrame(frame);
            // see getSizeFrame()
            ref.getVisualFrameWrapper().setChanged(true);
         }
         else {
            getInfo().setSizeFrame(frame);
            // see getSizeFrame()
            getInfo().getSizeFrameWrapper().setChanged(true);
         }
      }
   }

   /**
    * Get the shape frame.
    * @return the shape frame.
    */
   public ShapeFrame getShapeFrame() {
      AestheticRef ref = getInfo().getShapeField();

      if(ref != null) {
         VisualFrame visualFrame = ref.getVisualFrame();
         return visualFrame instanceof ShapeFrame ? (ShapeFrame) visualFrame : null;
      }

      VSDataRef[] aggs = getInfo().getAggregateRefs();

      return (aggs.length > 0) ?
         ((ChartAggregateRef) aggs[0]).getShapeFrame() : null;
   }

   /**
    * Set the shape frame.
    * @param frame the shape frame.
    */
   public void setShapeFrame(ShapeFrame frame) {
      if(frame != null) {
         AestheticRef ref = getInfo().getShapeField();
         boolean isAllPoint = true;
         boolean hasRef = ref != null;
         boolean multi = getInfo().isMultiStyles();

         if(hasRef) {
            ref.setVisualFrame(frame);
         }

         if(!multi) {
            isAllPoint = GraphTypes.isPoint(getInfo().getRTChartType());
         }

         if(!hasRef || multi) {
            VSDataRef[] aggs = getInfo().getAggregateRefs();

            for(int i = 0; i < aggs.length; i++) {
               ChartAggregateRef agg = (ChartAggregateRef) aggs[i];

               if(multi) {
                  isAllPoint =
                     isAllPoint && GraphTypes.isPoint(agg.getRTChartType());
               }

               if(!hasRef) {
                  agg.setShapeFrame(frame);
               }
            }
         }

         if(!isAllPoint && frame instanceof StaticShapeFrame) {
            LOG.info("Chart does not support specified frame: {}", frame);
         }
      }
   }

   /**
    * Get the line frame.
    * @return the line frame.
    */
   public LineFrame getLineFrame() {
      AestheticRef ref = getInfo().getShapeField();

      if(ref != null) {
         VisualFrame visualFrame = ref.getVisualFrame();
         return visualFrame instanceof LineFrame ? (LineFrame) visualFrame : null;
      }

      VSDataRef[] aggs = getInfo().getAggregateRefs();

      return (aggs.length > 0) ?
         ((ChartAggregateRef) aggs[0]).getLineFrame() : null;
   }

   /**
    * Set the line frame.
    * @param frame the line frame.
    */
   public void setLineFrame(LineFrame frame) {
      if(frame != null) {
         AestheticRef ref = getInfo().getShapeField();

         if(ref != null) {
            if(ref.getVisualFrame() instanceof LineFrame) {
               ref.setVisualFrame(frame);
            }
         }
         else {
            VSDataRef[] aggs = getInfo().getAggregateRefs();

            for(int i = 0; i < aggs.length; i++) {
               ChartAggregateRef agg = (ChartAggregateRef) aggs[i];
               agg.setLineFrame(frame);
            }
         }
      }
   }

   /**
    * Get the texture frame.
    * @return the texture frame.
    */
   public TextureFrame getTextureFrame() {
      AestheticRef ref = getInfo().getShapeField();

      if(ref != null) {
         VisualFrame visualFrame = ref.getVisualFrame();
         return visualFrame instanceof TextureFrame ? (TextureFrame) visualFrame : null;
      }

      VSDataRef[] aggs = getInfo().getAggregateRefs();

      return (aggs.length > 0) ?
         ((ChartAggregateRef) aggs[0]).getTextureFrame() : null;
   }

   /**
    * Set the texture frame.
    * @param frame the texture frame.
    */
   public void setTextureFrame(TextureFrame frame) {
      if(frame != null) {
         AestheticRef ref = getInfo().getShapeField();

         if(ref != null) {
            if(ref.getVisualFrame() instanceof TextureFrame) {
               ref.setVisualFrame(frame);
            }
         }
         else {
            VSDataRef[] aggs = getInfo().getAggregateRefs();

            for(int i = 0; i < aggs.length; i++) {
               ChartAggregateRef agg = (ChartAggregateRef) aggs[i];
               agg.setTextureFrame(frame);
            }
         }
      }
   }

   /**
    * Set the summarization formula for an aggregate column. The formula strings
    * are defined as constants in StyleReport.
    * @param field the specified column name
    * @param formula the formula string
    * @param type the specifed type, aesthetic or binding
    */
   public void setFormula(String field, String formula, int type) {
      ChartInfo oinfo = (ChartInfo) getInfo().clone();

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         List<AestheticRef> oflds = getAestheticRefs(oinfo, field, type);
         List<AestheticRef> flds = getAestheticRefs(field, type);

         for(AestheticRef tfield : getAestheticRefs(field, type)) {
            setAestheticFormula(tfield, formula);
         }

         XAggregateRef[] oaggs = oflds.stream()
            .filter(ref -> ref.getDataRef() instanceof XAggregateRef)
            .map(ref -> ref.getDataRef())
            .toArray(XAggregateRef[]::new);
         XAggregateRef[] aggs = flds.stream()
            .filter(ref -> ref.getDataRef() instanceof XAggregateRef)
            .map(ref -> ref.getDataRef())
            .toArray(XAggregateRef[]::new);
         updateSortByColAndRankingCol(oaggs, aggs);
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         DataRef ref = createDataRef(field);
         XAggregateRef[] oflds = oinfo.getAllAggregates(ref, false);
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, false);

         for(int i = 0; flds != null && i < flds.length; i++) {
            setBindingFormula(flds[i], formula);
         }

         updateSortByColAndRankingCol(oflds, flds);
      }
      else {
         DataRef ref = createDataRef(field);
         XAggregateRef[] oflds = oinfo.getAllAggregates(ref, true);
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, true);

         for(int i = 0; flds != null && i < flds.length; i++) {
            setBindingFormula(flds[i], formula);
         }

         updateSortByColAndRankingCol(oflds, flds);
      }
   }

   /**
    * Set the formula of the aesthetic ref.
    */
   private void setAestheticFormula(AestheticRef aref, String formula) {
      if(aref.getDataRef() instanceof XAggregateRef) {
         ((XAggregateRef) aref.getDataRef()).setFormula(
            AggregateFormula.getFormula(formula));
      }
      else {
         LOG.warn("Aggregate column is required: {}", aref);
      }
   }

   /**
    * Set the formula of the binding ref.
    */
   private void setBindingFormula(XAggregateRef fld, String formula) {
      if(fld != null) {
         fld.setFormula(AggregateFormula.getFormula(formula));
      }
      else {
         LOG.warn("Aggregate column not found: {}", fld);
      }
   }

   /**
    * Get the formula specified for an aggregate column.
    * @param field the specified column name
    * @param type the specifed type, aesthetic or binding
    * @return formula.
    */
   public String getFormula(String field, int type) {
      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         List<AestheticRef> arefs = getAestheticRefs(field, type);
         return (arefs.size() > 0)
            ? getAestheticFormula(arefs.get(0)) : null;
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         DataRef ref = createDataRef(field);
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, false);

         return flds == null || flds.length == 0 ? null :
            AggregateFormula.getIdentifier(flds[0].getFormula());
      }
      else {
         DataRef ref = createDataRef(field);
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, true);

         return flds == null || flds.length == 0 ? null :
            AggregateFormula.getIdentifier(flds[0].getFormula());
      }
   }

   /**
    * Get the formula of the aesthetic ref.
    */
   private String getAestheticFormula(AestheticRef aref) {
      if(aref.getDataRef() instanceof XAggregateRef) {
         return AggregateFormula.getIdentifier(
            ((XAggregateRef) aref.getDataRef()).getFormula());
      }

      return null;
   }

   private void updateSortByColAndRankingCol(XAggregateRef[] oflds, XAggregateRef[] nflds) {
      List<ChartDimensionRef> allDimensions = getAllDimensions(true);

      for(ChartDimensionRef dimensionRef : allDimensions) {
         for(int i = 0; i < oflds.length; i++) {
            if(CoreTool.equals(oflds[i].getFullName(false), dimensionRef.getRankingCol())) {
               dimensionRef.setRankingCol(nflds[i].getFullName(false));
            }

            if(CoreTool.equals(oflds[i].getFullName(false), dimensionRef.getSortByCol())) {
               dimensionRef.setSortByCol(oflds[i].getFullName(false));
            }
         }
      }
   }

   private List<ChartDimensionRef> getAllDimensions(boolean aesthetic) {
      ChartRef[] bindingRefs = getInfo().getBindingRefs(false);
      List<ChartDimensionRef> refs = new ArrayList<>();

      for(ChartRef ref : bindingRefs) {
         if(ref instanceof ChartDimensionRef) {
            refs.add((ChartDimensionRef) ref);
         }
      }

      if(!aesthetic) {
         return refs;
      }

      List<ChartDimensionRef> aestheticDimensionRefs = getInfo().getAestheticDimensionRefs(false);

      if(aestheticDimensionRefs.size() > 0) {
         refs.addAll(aestheticDimensionRefs);
      }

      return refs;
   }

   /**
    * Set the secondary field for an aggregate column.
    * @param field the specified column name
    * @param field2 the secondary column name
    * @param type the specifed type, aesthetic or binding
    */
   public void setSecondaryField(String field, String field2, int type) {
      ChartInfo oinfo = (ChartInfo) getInfo().clone();
      DataRef ref = createDataRef(field);
      DataRef secCol = createDataRef(field2);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         List<AestheticRef> oflds = getAestheticRefs(oinfo, field, type);
         List<AestheticRef> flds = getAestheticRefs(field, type);

         for(AestheticRef cfield : getAestheticRefs(field, type)) {
            setAestheticSecondField(cfield, secCol);
         }

         XAggregateRef[] oaggs = oflds.stream()
            .filter(aestheticRef -> aestheticRef.getDataRef() instanceof XAggregateRef)
            .map(aestheticRef -> aestheticRef.getDataRef())
            .toArray(XAggregateRef[]::new);
         XAggregateRef[] aggs = flds.stream()
            .filter(aestheticRef -> aestheticRef.getDataRef() instanceof XAggregateRef)
            .map(aestheticRef -> aestheticRef.getDataRef())
            .toArray(XAggregateRef[]::new);
         updateSortByColAndRankingCol(oaggs, aggs);
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XAggregateRef[] oflds = oinfo.getAllAggregates(ref, false);
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, false);

         for(int i = 0; flds != null && i < flds.length; i++) {
            setBindingSecondField(flds[i], secCol);
         }

         updateSortByColAndRankingCol(oflds, flds);
      }
      else {
         XAggregateRef[] oflds = oinfo.getAllAggregates(ref, true);
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, true);

         for(int i = 0; flds != null && i < flds.length; i++) {
            setBindingSecondField(flds[i], secCol);
         }

         updateSortByColAndRankingCol(oflds, flds);
      }
   }

   /**
    * Set the second column of formula for the aesthetic ref.
    */
   private void setAestheticSecondField(AestheticRef aref, DataRef ref2) {
      if(aref.getDataRef() instanceof XAggregateRef) {
         setSecondaryColumnValue((XAggregateRef) aref.getDataRef(), ref2);
      }
      else {
         LOG.warn("Aggregate column is required: {}", aref);
      }
   }

   /**
    * Set the second column of formula for the binding ref.
    */
   private void setBindingSecondField(XAggregateRef fld, DataRef ref2) {
      if(fld != null) {
         setSecondaryColumnValue(fld, ref2);
      }
      else {
         LOG.warn("Aggregate column not found: {}", fld);
      }
   }

   /**
    * Get the secondary field if exists.
    * @param field the specified column name
    * @param type the specifed type, aesthetic or binding
    * @return the secondary field, null means not existing
    */
   public String getSecondaryField(String field, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         List<AestheticRef> arefs = getAestheticRefs(field, type);
         return (arefs.size() > 0)
            ? getAestheticSecondField(arefs.get(0)) : null;
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, false);

         return flds == null || flds.length == 0 ? null :
            getSecondaryColumnValue(flds[0]);
      }
      else {
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, true);

         return flds == null || flds.length == 0 ? null :
            getSecondaryColumnValue(flds[0]);
      }
   }

   /**
    * Get the second column of formula for the aesthetic ref.
    */
   private String getAestheticSecondField(AestheticRef aref) {
      if(aref.getDataRef() instanceof XAggregateRef) {
         return getSecondaryColumnValue((XAggregateRef) aref.getDataRef());
      }

      return null;
   }

   /**
    * Set the percentage type for an aggregate column.
    * @param field the specified column name
    * @param ptype the specified percentage type
    * @param type the specifed type, aesthetic or binding
    */
   public void setPercentageType(String field, int ptype, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         for(AestheticRef tfield : getAestheticRefs(field, type)) {
            setAestheticPercentageOp(tfield, ptype);
         }
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, false);

         for(int i = 0; flds != null && i < flds.length; i++) {
            setBindingPercentageOp(flds[i], ptype);
         }
      }
      else {
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, true);

         for(int i = 0; flds != null && i < flds.length; i++) {
            setBindingPercentageOp(flds[i], ptype);
         }
      }
   }

   /**
    * Set the percentage option of formula for the aesthetic ref.
    */
   private void setAestheticPercentageOp(AestheticRef aref, int type) {
      if(aref.getDataRef() instanceof XAggregateRef) {
         ((XAggregateRef) aref.getDataRef()).setCalculator(
            GraphUtil.getCalculator(type));
      }
      else {
         LOG.warn("Aggregate column is required: {}", aref);
      }
   }

   /**
    * Set the percentage option of formula for the binding ref.
    */
   private void setBindingPercentageOp(XAggregateRef fld, int type) {
      if(fld != null) {
         fld.setCalculator(GraphUtil.getCalculator(type));
      }
      else {
         LOG.warn("Aggregate column not found: {}", fld);
      }
   }

   /**
    * Get the percentage type for an aggregate column.
    * @param field the specified column name
    * @param type the specifed type, aesthetic or binding
    * @return the percentage type
    */
   public int getPercentageType(String field, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         List<AestheticRef> arefs = getAestheticRefs(field, type);
         return (arefs.size() > 0)
            ? getAestheticPercentageOp(arefs.get(0)) : XConstants.PERCENTAGE_NONE;
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, false);

         return flds == null || flds.length == 0 ? XConstants.PERCENTAGE_NONE :
            GraphUtil.getPercentageOption(flds[0].getCalculator());
      }
      else {
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, true);

         return flds == null || flds.length == 0 ? XConstants.PERCENTAGE_NONE :
            GraphUtil.getPercentageOption(flds[0].getCalculator());
      }
   }

   /**
    * Set measure discrete.
    */
   public void setDiscrete(String measure, boolean discrete, int type) {
      DataRef ref = createDataRef(measure);
      boolean changed = false;

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         for(AestheticRef tfield : getAestheticRefs(measure, type)) {
            boolean fixVisual = setAestheticDiscrete(tfield, discrete);

            if(fixVisual) {
               ChartInfo info = getInfo();
               int ctype = -1;
               changed = true;

               if(info != null) {
                  ctype = info.getChartType() == GraphTypes.CHART_AUTO
                     ? info.getRTChartType() : info.getChartType();
               }

               GraphUtil.fixVisualFrame(tfield, type, ctype, getInfo());
            }
         }
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, false);

         for(int i = 0; flds != null && i < flds.length; i++) {
            changed = setBindingDiscrete(flds[i], discrete) || changed;
         }
      }
      else {
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, true);

         for(int i = 0; flds != null && i < flds.length; i++) {
            changed = setBindingDiscrete(flds[i], discrete) || changed;
         }
      }

      // if script is executed during export, the runtime may not be initialized
      // again, so avoid if not necessary. (54088)
      if(changed) {
         new ChangeChartDataProcessor(getInfo(), false).process();
      }
   }

   public boolean isDiscrete(String measure, int type) {
      DataRef ref = createDataRef(measure);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         List<AestheticRef> arefs = getAestheticRefs(measure, type);
         return (arefs.size() > 0) && isAestheticDiscrete(arefs.get(0));
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, false);

         return flds != null && flds.length != 0 && ((ChartAggregateRef) flds[0]).isDiscrete();
      }
      else {
         XAggregateRef[] flds = getInfo().getAllAggregates(ref, true);

         return flds != null && flds.length != 0 && ((ChartAggregateRef) flds[0]).isDiscrete();
      }
   }

   private boolean setAestheticDiscrete(AestheticRef aref, boolean discrete) {
      boolean changed = false;

      if(aref.getDataRef() instanceof ChartAggregateRef) {
         changed = ((ChartAggregateRef) aref.getDataRef()).isDiscrete() != discrete;
         ((ChartAggregateRef) aref.getDataRef()).setDiscrete(discrete);
      }
      else {
         LOG.warn("Aggregate column not found: {}", aref);
      }

      return changed;
   }

   private boolean setBindingDiscrete(XAggregateRef fld, boolean discrete) {
      boolean changed = false;

      if(fld instanceof ChartAggregateRef) {
         changed = ((ChartAggregateRef) fld).isDiscrete() != discrete;
         ((ChartAggregateRef) fld).setDiscrete(discrete);
      }
      else {
         LOG.warn("Aggregate column not found: {}", fld);
      }

      return changed;
   }

   private boolean isAestheticDiscrete(AestheticRef aref) {
      if(aref.getDataRef() instanceof ChartAggregateRef) {
         return ((ChartAggregateRef) aref.getDataRef()).isDiscrete();
      }

      return false;
   }

   /**
    * Get the percentage option of formula for the aesthetic ref.
    */
   private int getAestheticPercentageOp(AestheticRef aref) {
      if(aref.getDataRef() instanceof XAggregateRef) {
         return GraphUtil.getPercentageOption(
            ((XAggregateRef) aref.getDataRef()).getCalculator());
      }

      return XConstants.PERCENTAGE_NONE;
   }

   /**
    * Set top n for a dimension column.
    * @param field the specified dimension column
    * @param n the top n
    * @param type the field column type
    */
   public void setTopN(String field, int n, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         for(AestheticRef tfield : getAestheticRefs(field, type)) {
            setAestheticTopN(tfield, n);
         }
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, false);
         setBindingTopN(field, flds, n);
      }
      else {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, true);
         setBindingTopN(field, flds, n);
      }
   }

   /**
    * Set aesthetic top n for a dimension column.
    * @param aref the aesthetic ref
    * @param n the top n
    */
   private void setAestheticTopN(AestheticRef aref, int n) {
      if(aref.getDataRef() instanceof XDimensionRef) {
         setTopN((XDimensionRef) aref.getDataRef(), n);
      }
      else {
         LOG.warn("Dimension column not found: {}", aref);
      }
   }

   /**
    * Set binding top n for a dimension column.
    * @param flds the dimension refs
    * @param n the top n
    */
   private void setBindingTopN(String field, XDimensionRef[] flds, int n) {
      if(flds.length > 0 && flds[0] != null) {
         setTopN(flds[0], n);
      }
      else {
         LOG.warn("DimensionRef column not found: {}", field);
      }
   }

   /**
    * Get top n for a dimension column.
    * @param field the specified dimension column
    * @param type the field column type
    * @return the top n if exists, <code>0</code> otherwise
    */
   public int getTopN(String field, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         List<AestheticRef> arefs = getAestheticRefs(field, type);
         return (arefs.size() > 0)
            ? getAestheticTopN(arefs.get(0)) : 0;
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, false);
         return getBindingTopN(flds);
      }
      else {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, true);
         return getBindingTopN(flds);
      }
   }

   /**
    * Get aesthetic top n for a dimension column.
    * @param aref the aesthetic ref
    * @return the top n if exists, <code>0</code> otherwise
    */
   private int getAestheticTopN(AestheticRef aref) {
      if(aref.getDataRef() instanceof XDimensionRef) {
         return getTopN((XDimensionRef) aref.getDataRef());
      }

      return 0;
   }

   /**
    * Get binding top n for a dimension column.
    * @param flds the dimension refs
    * @return the top n if exists, <code>0</code> otherwise
    */
   private int getBindingTopN(XDimensionRef[] flds) {
      if(flds != null && flds.length > 0) {
         return getTopN(flds[0]);
      }

      return 0;
   }

   /**
    * Set topn summary column for a dimension column.
    * @param field the specified dimension column
    * @param sumfield the summary column
    * @param type the field column type
    */
   public void setTopNSummaryCol(String field, String sumfield, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         for(AestheticRef tfield : getAestheticRefs(field, type)) {
            setAestheticTopNSummaryCol(tfield, sumfield);
         }
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, false);
         setBindingTopNSummaryCol(field, flds, sumfield);
      }
      else {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, true);
         setBindingTopNSummaryCol(field, flds, sumfield);
      }
   }

   /**
    * Set aesthetic topn summary column for a dimension column.
    * @param aref the aesthetic ref
    * @param sumfield the summary column
    */
   private void setAestheticTopNSummaryCol(AestheticRef aref, String sumfield) {
      if(aref.getDataRef() instanceof XDimensionRef) {
         setTopNSummaryCol((XDimensionRef) aref.getDataRef(), sumfield);
      }
      else {
         LOG.warn("Dimension column not found: {}", aref);
      }
   }

   /**
    * Set binding topn summary column for a dimension column.
    * @param field the specified dimension column
    * @param flds the dimension refs
    * @param sumfield the summary column
    */
   private void setBindingTopNSummaryCol(String field, XDimensionRef[] flds,
      String sumfield)
   {
      if(flds != null && flds.length > 0) {
         setTopNSummaryCol(flds[0], sumfield);
      }
      else {
         LOG.warn("Dimension column not found: {}", field);
      }
   }

   /**
    * Get topn summary column for a dimension column.
    * @param field the specified dimension column
    * @param type the field column type
    * @return the summary column if exists, <code>null</code> otherwise
    */
   public String getTopNSummaryCol(String field, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         List<AestheticRef> arefs = getAestheticRefs(field, type);
         return (arefs.size() > 0)
            ? getAestheticTopNSummaryCol(arefs.get(0)) : null;
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, false);
         return getBindingTopNSummaryCol(flds);
      }
      else {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, true);
         return getBindingTopNSummaryCol(flds);
      }
   }

   /**
    * Get aesthetic topn summary column for a dimension column.
    * @param aref the aesthetic ref
    * @return the summary column if exists, <code>null</code> otherwise
    */
   private String getAestheticTopNSummaryCol(AestheticRef aref) {
      if(aref.getDataRef() instanceof XDimensionRef) {
         return getTopNSummaryCol((XDimensionRef) aref.getDataRef());
      }

      return null;
   }

   /**
    * Get binding topn summary column for a dimension column.
    * @param flds the dimension refs
    * @return the summary column if exists, <code>null</code> otherwise
    */
   private String getBindingTopNSummaryCol(XDimensionRef[] flds) {
      if(flds != null && flds.length > 0) {
         return getTopNSummaryCol(flds[0]);
      }

      return null;
   }

   /**
    * Set topn reverse for a dimension column.
    * @param field the specified dimension column
    * @param reserve <code>true</code> if reverse
    * @param type the field column type
    */
   public void setTopNReverse(String field, boolean reserve, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         for(AestheticRef tfield : getAestheticRefs(field, type)) {
            setAestheticTopNReverse(tfield, reserve);
         }
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, false);
         setBindingTopNReverse(field, flds, reserve);
      }
      else {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, true);
         setBindingTopNReverse(field, flds, reserve);
      }
   }

   /**
    * Set aesthetic topn reverse for a dimension column.
    * @param aref the aesthetic ref
    * @param reserve <code>true</code> if reverse
    */
   private void setAestheticTopNReverse(AestheticRef aref, boolean reserve) {
      if(aref.getDataRef() instanceof XDimensionRef) {
         setTopNReverse((XDimensionRef) aref.getDataRef(), reserve);
      }
      else {
         LOG.warn("Dimension column not found: {}", aref);
      }
   }

   /**
    * Set binding topn reverse for a dimension column.
    * @param field the specified dimension column
    * @param flds the dimension refs
    * @param reserve <code>true</code> if reverse
    */
   private void setBindingTopNReverse(String field, XDimensionRef[] flds,
      boolean reserve)
   {
      if(flds != null && flds.length > 0) {
         setTopNReverse(flds[0], reserve);
      }
      else {
         LOG.warn("Dimension column not found: {}", field);
      }
   }

   /**
    * Get topn reverse for a dimension column.
    * @param field the specified dimension column
    * @param type the field column type
    * @return <code>true</code> if reverse, <code>false</code> means not found
    * or not reverse
    */
   public boolean isTopNReverse(String field, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         List<AestheticRef> arefs = getAestheticRefs(field, type);
         return (arefs.size() > 0) && isAestheticTopNReverse(arefs.get(0));
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, false);
         return isBindingTopNReverse(flds);
      }

      return false;
   }

   /**
    * Get aesthetic topn reverse for a dimension column.
    * @param aref the aesthetic ref
    * @return <code>true</code> if reverse, <code>false</code> means not found
    * or not reverse
    */
   private boolean isAestheticTopNReverse(AestheticRef aref) {
      if(aref.getDataRef() instanceof XDimensionRef) {
         return isTopNReverse((XDimensionRef) aref.getDataRef());
      }

      return false;
   }

   /**
    * Get binding topn reverse for a dimension column.
    * @param flds the dimension refs
    * @return <code>true</code> if reverse, <code>false</code> means not found
    * or not reverse
    */
   private boolean isBindingTopNReverse(XDimensionRef[] flds) {
      if(flds != null && flds.length > 0) {
         return isTopNReverse(flds[0]);
      }

      return false;
   }

   /**
    * Set the sort order for a dimension column.
    * @param field the specified column name
    * @param order the specified sort order
    * @param type0 the field column type
    * @param sortBy0 the sort by column for sorting by value.
    */
   public void setColumnOrder(String field, int order,
			      Object type0, Object sortBy0)
   {
      int type = -1;
      String sortBy = null;
      DataRef ref = createDataRef(field);

      type0 = JavaScriptEngine.unwrap(type0);
      sortBy0 = JavaScriptEngine.unwrap(sortBy0);

      // the function can be called by:
      //  setColumnOrder(field, order, type, sortBy) // or
      //  setColumnOrder(field, order, type) // or
      //  setColumnOrder(field, order, sortBy)
      if(type0 instanceof Number) {
         type = ((Number) type0).intValue();
         sortBy = (sortBy0 == null) ? null : sortBy0.toString();
      }
      else if(type0 != null) {
         sortBy = type0.toString();
      }

      //@temp yanie bug1408384830451
      //If aggregate function is not specified, find the first aggregate who
      //used the column
      if(sortBy != null && getInfo() != null) {
         boolean isFound = false;
         String guessName = null;
         VSDataRef[] refs = getInfo().getAggregateRefs();

         for(VSDataRef ref0 : refs) {
            String refName = ref0 instanceof XAggregateRef ?
               ((XAggregateRef) ref0).getFullName(false) : null;

            if(sortBy.equals(refName)) {
               isFound = true;
               break;
            }
            else if(guessName == null && refName != null &&
               refName.indexOf('(') > 0 &&
               refName.indexOf(')') > refName.indexOf('(') + 1)
            {
               String refName2 = refName.substring(refName.indexOf('(') + 1,
                                                   refName.indexOf(')'));

               if(sortBy.equals(refName2)) {
                  guessName = refName;
               }
            }
         }

         sortBy = !isFound && guessName != null ? guessName : sortBy;
      }

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         for(AestheticRef tfield : getAestheticRefs(field, type)) {
            setAestheticColumnOrder(tfield, order, sortBy);
         }
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         ChartInfo info = getInfo();
         XDimensionRef[] flds = info != null ? info.getAllDimensions(ref, false) : null;
         setBindingColumnOrder(field, flds, order, sortBy);
      }
      else {
         ChartInfo info = getInfo();
         XDimensionRef[] flds = info != null ? info.getAllDimensions(ref, true) : null;
         setBindingColumnOrder(field, flds, order, sortBy);
      }
   }

   /**
    * Set the aesthetic sort order for a dimension column.
    * @param aref the aesthetic ref
    * @param order the specified sort order
    */
   private void setAestheticColumnOrder(AestheticRef aref, int order, String sortBy) {
      if(aref.getDataRef() instanceof XDimensionRef) {
         setColumnOrder((XDimensionRef) aref.getDataRef(), order, sortBy);
      }
      else {
         LOG.warn("Dimension column not found: {}", aref);
      }
   }

   /**
    * Set aesthetic topn summary column for a dimension column.
    * @param field the specified dimension column
    * @param flds the dimension refs
    * @param order the specified sort order
    */
   private void setBindingColumnOrder(String field, XDimensionRef[] flds,
				     int order, String sortBy)
   {
      if(flds != null && flds.length > 0) {
         setColumnOrder(flds[0], order, sortBy);
      }
      else {
         LOG.warn("Dimension column not found: {}", field);
      }
   }

   /**
    * Set the sort order for a dimension column.
    * @param dim the dimension ref
    * @param order the specified sort order
    */
   private void setColumnOrder(XDimensionRef dim, int order, String sortBy) {
      dim.setOrder(order);
      dim.setSortByCol(sortBy);
   }

   /**
    * Get the sort order for a dimension column.
    * @param field the specified column name
    * @param type the field column type
    * @return the sort order
    */
   public int getColumnOrder(String field, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         List<AestheticRef> arefs = getAestheticRefs(field, type);
         return (arefs.size() > 0)
            ? getAestheticColumnOrder(arefs.get(0)) : XConstants.SORT_NONE;
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, false);
         return getBindingColumnOrder(flds);
      }
      else {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, true);
         return getBindingColumnOrder(flds);
      }
   }

   /**
    * Get the aesthetic sort order for a dimension column.
    * @param aref the aesthetic ref
    * @return the sort order
    */
   private int getAestheticColumnOrder(AestheticRef aref) {
      if(aref.getDataRef() instanceof XDimensionRef) {
         return getColumnOrder((XDimensionRef) aref.getDataRef());
      }

      return XConstants.SORT_NONE;
   }

   /**
    * Get the binding sort order for a dimension column.
    * @param flds the dimension refs
    * @return the sort order
    */
   private int getBindingColumnOrder(XDimensionRef[] flds) {
      if(flds != null && flds.length > 0) {
         return getColumnOrder(flds[0]);
      }

      return XConstants.SORT_NONE;
   }

   /**
    * Get the sort order for a dimension column.
    * @param dim the dimension column ref
    * @return the sort order
    */
   private int getColumnOrder(XDimensionRef dim) {
      return dim.getOrder();
   }

   /**
    * Set the group order of a specified dimension column.
    * @param field the specified dimension column name
    * @param order the group order
    */
   public void setGroupOrder(String field, int order, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         for(AestheticRef tfield : getAestheticRefs(field, type)) {
            setAestheticGrpOrder(tfield, order);
         }
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, false);
         setBindingGrpOrder(flds, order);
      }
      else {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, true);
         setBindingGrpOrder(flds, order);
      }

      //bug1347519674702, change field need process.
      new ChangeChartDataProcessor(getInfo(), false).process();
   }

   /**
    * Set the date level of the aesthetic ref.
    */
   private void setAestheticGrpOrder(AestheticRef aref, int order) {
      if(aref.getDataRef() instanceof XDimensionRef) {
         if(Arrays.binarySearch(this.attributes, order) > -1) {
            setDateLevelValue((XDimensionRef) aref.getDataRef(), order);
         }
      }
      else {
         LOG.warn("Dimension column not found: {}", aref);
      }
   }

   /**
    * Set the date level of the binding refs.
    */
   private void setBindingGrpOrder(XDimensionRef[] flds, int order) {
      for(int i = 0; flds != null && i < flds.length; i++) {
         if(Arrays.binarySearch(this.attributes, order) > -1) {
            setDateLevelValue(flds[i], order);
         }
      }
   }

   /**
    * Get the group order of a specified dimension column, including date
    * interval and parts
    * @param field the specified dimension column name
    * @return the group order
    */
   public int getGroupOrder(String field, int type) {
      DataRef ref = createDataRef(field);

      if(type == ChartConstants.AESTHETIC_COLOR ||
         type == ChartConstants.AESTHETIC_SHAPE ||
         type == ChartConstants.AESTHETIC_SIZE ||
         type == ChartConstants.AESTHETIC_TEXT)
      {
         List<AestheticRef> arefs = getAestheticRefs(field, type);
         return (arefs.size() > 0)
            ? getAestheticGrpOrder(arefs.get(0)) : DateRangeRef.YEAR_INTERVAL;
      }
      else if(type == ChartConstants.BINDING_FIELD) {
         XDimensionRef[] flds = getInfo().getAllDimensions(ref, false);

         return flds == null || flds.length == 0
            ? DateRangeRef.YEAR_INTERVAL : getDateLevelValue(flds[0]);
      }

      return DateRangeRef.YEAR_INTERVAL;
   }

   /**
    * Get the group order of the aesthetic ref.
    */
   private int getAestheticGrpOrder(AestheticRef aref) {
      if(aref.getDataRef() instanceof XDimensionRef) {
         return getDateLevelValue((XDimensionRef) aref.getDataRef());
      }

      return DateRangeRef.YEAR_INTERVAL;
   }

   /**
    * Set whether to treat a date column as a time series.
    * @param field the specified dimension column name
    * @param timeSeries <code>true</code> if is a time series dimension
    */
   public void setTimeSeries(String field, boolean timeSeries) {
      DataRef ref = createDataRef(field);
      XDimensionRef[] flds = getInfo().getAllDimensions(ref, false);

      if(flds != null && flds.length > 0) {
         flds[0].setTimeSeries(timeSeries);
         getInfo().updateChartType(!getInfo().isMultiStyles());
         new ChangeChartDataProcessor(getInfo(), false).process();
      }
      else {
         LOG.warn("Dimension column not found: {}", field);
      }
   }

   /**
    * Get whether to treat a date column as a time series.
    * @param field the specified dimension column name
    * @return <code>true</code> if is a time series dimension
    */
   public boolean isTimeSeries(String field) {
      DataRef ref = createDataRef(field);
      XDimensionRef[] flds = getInfo().getAllDimensions(ref, false);

      if(flds != null && flds.length > 0) {
         return flds[0].isTimeSeries();
      }

      return true;
   }

   /**
    * Get the map info of the chart element.
    */
   private MapInfo getMapInfo() {
      return getInfo() instanceof MapInfo ? (MapInfo) getInfo() : null;
   }

   /**
    * Set the map layer for a geographic column.
    * @param field the specified column name.
    * @param layer the specified map layer.
    */
   public void setMapLayer(String field, String layer) {
      MapInfo info = getMapInfo();

      if(info == null) {
         LOG.warn("Current element is not a map");
         return;
      }

      GeoRef geo = GraphUtil.getGeoRefByName(info, field);

      if(geo == null) {
         geo = GraphUtil.getGeoRefByName(info,
            GeoRef.wrapGeoName(field));
      }

      if(geo != null) {
         int l = MapData.getLayer(layer);
         GeographicOption option = geo.getGeographicOption();
         option.setLayerValue(l + "");
         option.getMapping().setLayer(option.getLayer());
      }
      else {
         LOG.warn("Geographic column not found: {}", field);
      }
   }

   /**
    * Get the map layer for a geographic column.
    * @param field the specified column name.
    */
   public String getMapLayer(String field) {
      MapInfo info = getMapInfo();

      if(info == null) {
         LOG.warn("Current element is not a map");
         return null;
      }

      GeoRef geo = GraphUtil.getRTGeoRefByName(info, field);

      if(geo == null) {
         geo = GraphUtil.getRTGeoRefByName(info,
            GeoRef.wrapGeoName(field));
      }

      if(geo != null) {
         int layer = geo.getGeographicOption().getLayer();
         return MapData.getLayerName(layer);
      }
      else {
         LOG.warn("Geographic column not found: {}", field);
         return null;
      }
   }

   /**
    * Get manual mappings.
    * @param field the specified column name.
    */
   public String[][] getMappings(String field) {
      MapInfo info = getMapInfo();

      if(info == null) {
         LOG.warn("Current element is not a map");
         return new String[0][0];
      }

      GeoRef geo = GraphUtil.getRTGeoRefByName(info, field);

      if(geo == null) {
         geo = GraphUtil.getRTGeoRefByName(info,
            GeoRef.wrapGeoName(field));
      }

      if(geo != null) {
         Map<String, String> map = geo.getGeographicOption().getMapping().getMappings();
         String[][] mappings = new String[map.size()][2];
         List<String> values = new ArrayList<>(map.keySet());

         for(int i = 0; i < mappings.length; i++) {
            String value = values.get(i);
            mappings[i][0] = value;
            mappings[i][1] = map.get(value);
         }

         return mappings;
      }
      else {
         LOG.warn("Geographic column not found: {}", field);
         return new String[0][0];
      }
   }

   /**
    * Set manual mappings.
    * @param field the specified column name.
    * @param value the value that feature mapping mapped to.
    * @param geoLabel geographic label.
    */
   public void addMapping(String field, String value, String geoLabel) {
      MapInfo info = getMapInfo();

      if(info == null) {
         LOG.warn("Current element is not a map");
         return;
      }

      GeoRef geo = GraphUtil.getGeoRefByName(info, field);

      if(geo == null) {
         geo = GraphUtil.getGeoRefByName(info,
            GeoRef.wrapGeoName(field));
      }

      if(geo != null) {
         FeatureMapping mapping = geo.getGeographicOption().getMapping();

         if(Tool.isEmptyString(mapping.getType()) && Tool.isEmptyString(info.getMapType())) {
            LOG.warn("Map type is not set");
            return;
         }
         else if(mapping.getLayer() == 0 &&
            geo.getGeographicOption().getLayer() == -1)
         {
            LOG.warn("Geographic layer is not set");
            return;
         }

         //mapping create from script, need init default.
         if(Tool.isEmptyString(mapping.getID())) {
            mapping.setID(MapHelper.BUILD_IN_MAPPING_ID);
         }

         //mapping type only use on addmapping, so init here.
         if(Tool.isEmptyString(mapping.getType())) {
            mapping.setType(info.getMapType());
         }

         String geoCode = MapHelper.getGeoCodeByLabel(mapping.getType(),
            mapping.getLayer(), geoLabel);

         if(Tool.isEmptyString(geoCode)) {
            LOG.warn("Geographic code not found: {}", geoLabel);
            return;
         }

         geo.getGeographicOption().getMapping().addMapping(value, geoCode);
      }
      else {
         LOG.warn("Geographic column not found: {}", field);
      }
   }

   /**
    * Set manual mappings.
    * @param field the specified column name.
    * @param value the value that feature mapping mapped to.
    */
   public void removeMapping(String field, String value) {
      MapInfo info = getMapInfo();

      if(info == null) {
         LOG.warn("Current element is not a map");
         return;
      }

      GeoRef geo = GraphUtil.getGeoRefByName(info, field);

      if(geo == null) {
         geo = GraphUtil.getGeoRefByName(info,
            GeoRef.wrapGeoName(field));
      }

      if(geo != null) {
         geo.getGeographicOption().getMapping().removeMapping(value);
      }
      else {
         LOG.warn("Geographic column not found: {}", field);
      }
   }

   /**
    * Create a data ref from a string field name.
    * The string can contains field with "entity.attribute" format.
    */
   protected DataRef createDataRef(String name) {
      int dot = name.lastIndexOf('.');

      return dot < 0 ? new BaseField(null, name) :
         new BaseField(name.substring(0, dot), name.substring(dot + 1));
   }

   /**
    * Removes a indexed property from this object.
    */
   @Override
   public void delete(int i) {
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
      init();
      return super.getIds();
   }

   /**
    * Object is passed to PropertyDescriptor in this class.
    */
   @Override
   protected Object getObject() {
      return null;
   }

   /**
    * Sets a named property in this object.
    */
   @Override
   public void put(String name, Scriptable start, Object value) {
      init();

      super.put(name, start, value);
   }

   /**
    * Indicates whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      init();
      return super.has(name, start);
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      init();
      return super.get(name, start);
   }

   /**
    * Get the type of a named property from the object.
    */
   @Override
   public Class getType(String name, Scriptable start) {
      init();
      return super.getType(name, start);
   }

   /**
    * Check the field is in the field array.
    */
   protected boolean checkFieldByName(ChartRef[] refs, String fullName) {
      if(getInfo() == null) {
         return true;
      }

      for(ChartRef ref : refs) {
         if(ref.getName().equals(fullName)) {
            return true;
         }
      }
      return false;
   }

   /**
    * Set date level value.
    */
   protected void setDateLevelValue(XDimensionRef ref, int level) {
      if(ref instanceof VSDimensionRef) {
         ((VSDimensionRef) ref).setDateLevelValue(level + "");
      }
      else {
         ref.setDateLevel(level);
      }
   }

   /**
    * Get date level value.
    */
   protected int getDateLevelValue(XDimensionRef ref) {
      return ref instanceof VSDimensionRef ?
         Integer.parseInt(((VSDimensionRef) ref).getDateLevelValue()) :
         ref.getDateLevel();
   }

   /**
    * Set secondary column value.
    */
   protected void setSecondaryColumnValue(XAggregateRef ref, DataRef ref2) {
      if(ref instanceof VSAggregateRef) {
         ((VSAggregateRef) ref).setSecondaryColumnValue(ref2.getName());
      }
      else {
         ref.setSecondaryColumn(ref2);
      }
   }

   /**
    * Get secondary column value.
    */
   protected String getSecondaryColumnValue(XAggregateRef ref) {
      return ref instanceof VSAggregateRef ?
         ((VSAggregateRef) ref).getSecondaryColumnValue() :
         ref.getSecondaryColumn().getName();
   }

   /**
    * Get all aesthetic fields matching the named field.
    */
   private List<AestheticRef> getAestheticRefs(ChartInfo info, String field, int type) {
      List<AestheticRef> refs = new ArrayList<>();
      refs.add(getAestheticRef(info, field, type));

      for(Object ref : getInfo().getBindingRefs(true)) {
         if(ref instanceof ChartAggregateRef) {
            refs.add(getAestheticRef((ChartAggregateRef) ref, field, type));
         }
      }

      while(refs.remove(null)) {
         // remove all empty values
      }

      if(refs.size() == 0) {
         LOG.warn("Aesthetic column not found: {}", field);
      }

      return refs;
   }

   /**
    * Get all aesthetic fields matching the named field.
    */
   private List<AestheticRef> getAestheticRefs(String field, int type) {
      return getAestheticRefs(getInfo(), field, type);
   }

   /**
    * Get the aesthetic field matching the named field.
    */
   private AestheticRef getAestheticRef(ChartBindable bindable, String field,
                                        int type)
   {
      AestheticRef aref = null;

      if(type == ChartConstants.AESTHETIC_COLOR) {
         aref = bindable.getColorField();
      }
      else if(type == ChartConstants.AESTHETIC_SHAPE) {
         aref = bindable.getShapeField();
      }
      else if(type == ChartConstants.AESTHETIC_SIZE) {
         aref = bindable.getSizeField();
      }
      else if(type == ChartConstants.AESTHETIC_TEXT) {
         aref = bindable.getTextField();
      }

      if(aref != null && !field.equals(aref.getDataRef().getName())) {
         aref = null;
      }

      return aref;
   }

   /**
    * Find field by name (not full name).
    */
   protected final VSDataRef findField(String field) {
      ChartInfo info = getInfo();

      for(VSDataRef ref : info.getRTFields()) {
         if(ref.getName().equals(field)) {
            return ref;
         }
      }

      return null;
   }

   /**
    * Set the color field for aesthetic color attribute.
    */
   public void setColorField(Object arg1, Object arg2, String arg3) {
      setAestheticField(arg1, arg2, arg3, ChartConstants.AESTHETIC_COLOR,
                        (bindable, aref) -> bindable.setColorField(aref));
   }

   /**
    * Set the shape field for aesthetic shape attribute.
    */
   public void setShapeField(Object argObj1, Object argObj2, String arg3) {
      setAestheticField(argObj1, argObj2, arg3, ChartConstants.AESTHETIC_SHAPE,
                        (bindable, aref) -> bindable.setShapeField(aref));
   }

   /**
    * Set the size field for aesthetic size attribute.
    */
   public void setSizeField(Object arg1, Object arg2, String arg3) {
      setAestheticField(arg1, arg2, arg3, ChartConstants.AESTHETIC_SIZE,
                        (bindable, aref) -> bindable.setSizeField(aref));
   }

   /**
    * Set the text field for aesthetic text attribute.
    */
   public void setTextField(Object arg1, Object arg2, String arg3) {
      setAestheticField(arg1, arg2, arg3, ChartConstants.AESTHETIC_TEXT,
                        (bindable, aref) -> bindable.setTextField(aref));
   }

   private void setAestheticField(Object argObj1, Object argObj2, String arg3, int aesType,
                                  BiConsumer<ChartBindable, AestheticRef> setter)
   {
      ChartInfo info = getInfo();
      argObj1 = ScriptUtil.unwrap(argObj1);
      argObj2 = ScriptUtil.unwrap(argObj2);

      if(argObj1 instanceof AestheticRef) {
         setter.accept(info, getAestheticRef((AestheticRef) argObj1, info, aesType));
         return;
      }

      boolean aggr = !"undefined".equals(arg3);
      String arg1 = argObj1 == null ? null : argObj1.toString();
      ChartBindable bindable = ChartProcessor.getChartBindable(info, aggr ? arg1 : null);

      if(argObj2 instanceof AestheticRef) {
         setter.accept(bindable, getAestheticRef((AestheticRef) argObj2, info, aesType));
         return;
      }

      String arg2 = argObj2 == null ? null : argObj2.toString();
      String field = aggr ? arg2 : arg1;
      String type = aggr ? arg3 : arg2;

      if(field == null) {
         setter.accept(bindable, null);
         return;
      }

      AestheticRef aestheticRef = createAestheticRef(field, type);

      if(aestheticRef != null) {
         if(info instanceof VSChartInfo && aesType == ChartConstants.AESTHETIC_SHAPE) {
            ((VSChartInfo) info).setNeedResetShape(true);
         }

         GraphUtil.fixVisualFrame(aestheticRef, aesType, info.getRTChartType(), info);
         setter.accept(bindable, aestheticRef);

         // design time ref changed, clear runtime so aesthetic takes effect
         if(info.isMultiStyles() && info instanceof VSChartInfo) {
            info.clearRuntime();
         }

         info.updateChartType(!info.isMultiStyles());
      }
      else {
         LOG.warn("Aesthetic binding field not found: {}", field);
      }
   }

   private static AestheticRef getAestheticRef(AestheticRef argObj1, ChartInfo info, int aesType) {
      AestheticRef nfield = (AestheticRef) argObj1.clone();
      GraphUtil.fixVisualFrame(nfield, aesType, info.getRTChartType(), info);
      return nfield;
   }

   /**
    * Get the color field.
    */
   public AestheticRef getColorField(Object aggr) {
      aggr = JavaScriptEngine.unwrap(aggr);
      ChartBindable bindable = ChartProcessor.getChartBindable(
         getInfo(), aggr != null ? aggr + "" : null);

      return bindable.getColorField();
   }

   /**
    * Get the shape field.
    */
   public AestheticRef getShapeField(Object aggr) {
      aggr = JavaScriptEngine.unwrap(aggr);
      ChartBindable bindable = ChartProcessor.getChartBindable(
         getInfo(), aggr != null ? aggr + "" : null);

      return bindable.getShapeField();
   }

   /**
    * Get the size field.
    */
   public AestheticRef getSizeField(Object aggr) {
      aggr = JavaScriptEngine.unwrap(aggr);
      ChartBindable bindable = ChartProcessor.getChartBindable(
         getInfo(), aggr != null ? aggr + "" : null);

      return bindable.getSizeField();
   }

   /**
    * Get the text field.
    */
   public AestheticRef getTextField(Object aggr) {
      aggr = JavaScriptEngine.unwrap(aggr);
      ChartBindable bindable = ChartProcessor.getChartBindable(
         getInfo(), aggr != null ? aggr + "" : null);

      return bindable.getTextField();
   }

   private boolean inited = false;
   private int[] attributes = {DateRangeRef.DAY_INTERVAL,
      DateRangeRef.WEEK_INTERVAL, DateRangeRef.MONTH_INTERVAL,
      DateRangeRef.QUARTER_INTERVAL, DateRangeRef.YEAR_INTERVAL,
      DateRangeRef.SECOND_INTERVAL, DateRangeRef.MINUTE_INTERVAL,
      DateRangeRef.HOUR_INTERVAL, DateRangeRef.QUARTER_OF_YEAR_PART,
      DateRangeRef.MONTH_OF_YEAR_PART, DateRangeRef.WEEK_OF_YEAR_PART,
      DateRangeRef.DAY_OF_MONTH_PART, DateRangeRef.DAY_OF_WEEK_PART,
      DateRangeRef.HOUR_OF_DAY_PART, DateRangeRef.MINUTE_OF_HOUR_PART,
      DateRangeRef.SECOND_OF_MINUTE_PART, DateRangeRef.NONE_INTERVAL};
   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractChartBindingScriptable.class);
}

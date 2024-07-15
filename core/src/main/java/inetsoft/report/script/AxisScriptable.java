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

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.viewsheet.graph.*;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * This class represents an AxisDescriptor in the Javascript environment.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class AxisScriptable extends PropertyScriptable {
   /**
    * Create an axis descriptor javascript object.
    * @param field the field name of a chart aggregate ref
    */
   public AxisScriptable(ChartInfo cinfo, String field) {
      this.info = cinfo;
      this.field = field;
      isFieldAxis = true;
   }

   /**
    * Create a scriptable for a specific axis descriptor.
    */
   public AxisScriptable(ChartInfo cinfo, AxisDescriptor axis) {
      this.info = cinfo;
      this.axis = axis;
   }

   /**
    * Create a scriptable for a specific axis descriptor.
    * @param isXAxis the axis is xAxis or x2Axis if true.
    */
   public AxisScriptable(ChartInfo cinfo, AxisDescriptor axis, boolean isXAxis) {
      this.info = cinfo;
      this.axis = axis;
      this.isXAxis = isXAxis;
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "AxisDescriptor";
   }

   /**
    * Set the axis if is field axis.
    */
   public void setFieldAxis(boolean isFieldAxis) {
      this.isFieldAxis = isFieldAxis;
   }

   /**
    * Initialize the object.
    */
   private void init() {
      if(inited) {
         return;
      }

      inited = true;
      ChartRef ref = ChartProcessor.getRuntimeField(info, field);
      boolean radarParallel = "Parallel_Label".equals(field) &&
         (info instanceof RadarChartInfo);

      if(ref != null || radarParallel) {
         if(radarParallel) {
            axis = ((RadarChartInfo) info).getLabelAxisDescriptor();
         }
         else if(info.isSeparatedGraph() &&
            !(info instanceof StockChartInfo) &&
            !(info instanceof CandleChartInfo))
         {
            axis = getAxisDescriptor(ref);
         }
         else if(ref instanceof ChartDimensionRef) {
            axis = getAxisDescriptor(ref);
         }
         else if(ref instanceof VSChartAggregateRef) {
            if(((VSChartAggregateRef) ref).isSecondaryY()) {
               axis = ((VSChartInfo) info).getRTAxisDescriptor2();
            }
            else {
               axis = ((VSChartInfo) info).getRTAxisDescriptor();
            }
         }
         else {
            // if inseparate graph or candle, stock chart, get dimension
            // descriptor from ref, get shared measure descriptor from info
            axis = info.getAxisDescriptor();
         }
      }

      if(axis != null) {
         try {
            if(ref == null) {
               addMeasureProperties();

               //bug1365675721026, the script "axis" need add dim.
               if(isFieldAxis) {
                  addDimensionProperties();
               }
            }
            else if(ref.isMeasure()) {
               addMeasureProperties();
            }
            else {
               addDimensionProperties();
            }

            addProperty("ticksVisible", "isTicksVisible", "setTicksVisible",
                        boolean.class, AxisDescriptor.class);
            addProperty("lineVisible", "isLineVisible", "setLineVisible",
                        boolean.class, AxisDescriptor.class);
            addProperty("labelVisible", "isLabelVisible",
                        "setLabelVisible", boolean.class,
                        AxisDescriptor.class);
            addProperty("lineColor", "getLineColor", "setLineColor",
                        Color.class, AxisDescriptor.class);
            addProperty("labelColor", "getColor", "setColor", Color.class,
                        CompositeTextFormat.class);
            addProperty("font", "getFont", "setFont", Font.class,
                        CompositeTextFormat.class);
            addProperty("rotation", "getRotation", "setRotation", Number.class,
                        CompositeTextFormat.class);
            addProperty("format", "getFormat", "setFormat", XFormatInfo.class,
                        CompositeTextFormat.class);
         }
         catch(Exception ex) {
            LOG.error("Failed to register axis properties", ex);
         }
      }
   }

   private void addMeasureProperties() {
      addProperty("minimum", "getMinimum", "setMinimum", Number.class,
                  AxisDescriptor.class);
      addProperty("maximum", "getMaximum", "setMaximum", Number.class,
                  AxisDescriptor.class);
      addProperty("increment", "getIncrement", "setIncrement",
                  Number.class, AxisDescriptor.class);
      addProperty("minorIncrement", "getMinorIncrement",
                  "setMinorIncrement", Number.class,
                  AxisDescriptor.class);
      addProperty("logarithmic", "isLogarithmicScale",
                  "setLogarithmicScale", boolean.class,
                  AxisDescriptor.class);
      addProperty("reversed", "isReversed",
                  "setReversed", boolean.class,
                  AxisDescriptor.class);
      addProperty("sharedRange", "isSharedRange",
                  "setSharedRange", boolean.class,
                  AxisDescriptor.class);
   }

   private void addDimensionProperties() throws Exception {
      Class[] ssparams = {String.class, String.class};
      addFunctionProperty(getClass(), "setLabelAlias", ssparams);
      addProperty("noNull", "isNoNull",
                  "setNoNull", boolean.class,
                  AxisDescriptor.class, axis);
      addProperty("truncate", "isTruncate",
                  "setTruncate", boolean.class,
                  AxisDescriptor.class, axis);
   }

   /**
    * Set the label alias for the dimension axis descriptor.
    */
   public void setLabelAlias(String label, String alias) {
      if(axis != null && label != null) {
         axis.setLabelAlias(label, alias);
      }
   }

   /**
    * Get the object for getting and setting properties.
    */
   @Override
   protected Object getObject() {
      init();

      return axis;
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      init();

      // @by gregm format properties require a CompositeTextFormat object,
      // not an AxisDescriptor.
      if(isFormatProperty(name)) {
         try {
            Object val = propmap.get(name);

            if(val instanceof PropertyDescriptor) {
               PropertyDescriptor desc = (PropertyDescriptor) val;
               return desc.get(axis.getAxisLabelTextFormat());
            }
         }
         catch(Exception e) {
            LOG.error("Failed to get axis property: " + name, e);
         }
      }

      return super.get(name, start);
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
    * Sets a named property in this object.
    */
   @Override
   public void put(String name, Scriptable start, Object value) {
      init();

      try {
         Object val = propmap.get(name);

         if(val instanceof PropertyDescriptor) {
            PropertyDescriptor desc = (PropertyDescriptor) val;

            // @by gregm 2012-04-24
            // fix bug1335152670042, allows certain global axis properties
            // to work even if the chart is a separateGraph and has an axis
            // associated with each field.
            if(isGlobalAxisScriptable()) {
               ChartRef[] refs = isXAxis ? info.getRTXFields() : info.getRTYFields();

               for(int i = 0; i < refs.length; i++) {
                  put(name, value, desc,
                     !GraphUtil.isDimension(refs[i]) && !info.isSeparatedGraph() ?
                     axis : getAxisDescriptor(refs[i]));
               }
            }
            else {
               put(name, value, desc, axis);
            }
         }
         else if(val != null) {
            propmap.put(name, val);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to set axis property: " + name + "=" + value, e);
      }
   }

   /**
    * Sets a named property on an AxisDescriptor object
    */
   private void put(String name, Object value, PropertyDescriptor desc,
                    AxisDescriptor aDesc) throws Exception {
      // @by gregm format properties require a CompositeTextFormat object,
      // not an AxisDescriptor
      if(isFormatProperty(name)) {
         if(field != null) {
            CompositeTextFormat fmt = aDesc.getColumnLabelTextFormat(field);

            if(fmt == null) {
               fmt = aDesc.getAxisLabelTextFormat();
            }

            desc.set(fmt, value);
         }
         else {
            desc.set(aDesc.getAxisLabelTextFormat(), value);
         }
      }
      else {
         desc.set(aDesc, value);
      }
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
    * Get the type of a named property from the object.
    */
   @Override
   public Class getType(String name, Scriptable start) {
      init();
      return super.getType(name, start);
   }

   /**
    * Determines whether this scriptable is for a global axis property
    * e.g: (yAxis, yAxis2)
    */
   private boolean isGlobalAxisScriptable() {
      return field == null;
   }

   /**
    * Determines if the specified property is linked to the CompositeTextFormat
    * class. When setting properties with put, one should use
    * AxisDescriptor.getAxisLabelTextFormat() as the target object.
    * @param name Property name
    */
   private boolean isFormatProperty(String name) {
      return name.equals("font") || name.equals("labelColor") ||
             name.equals("format") || name.equals("rotation");
   }

   /**
    * Get the axis descriptor for descriptable.
    */
   private AxisDescriptor getAxisDescriptor(ChartRef ref) {
      AxisDescriptor axis = null;

      if(ref instanceof VSChartRef) {
         axis = ((VSChartRef) ref).getRTAxisDescriptor();
      }

      if(axis == null) {
         axis = ref.getAxisDescriptor();
      }

      return axis;
   }

   private AxisDescriptor axis;
   private String field;
   private ChartInfo info;
   private boolean inited = false;
   private boolean isXAxis = false;
   private boolean isFieldAxis = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(AxisScriptable.class);
}

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
package inetsoft.report.script;

import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.util.script.ArrayObject;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * This represents an array of chart styles, axises in a chart info.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public abstract class AbstractChartArray extends ScriptableObject
   implements ArrayObject {
   /**
    * Constructor.
    * @param property property name, e.g. Object.
    * @param property type, e.g. Object.class.
    */
   public AbstractChartArray(String property, Class pType) {
      this.property = property;
      this.pType = pType;
   }

   /**
    * Get array element type.
    */
   @Override
   public Class getType() {
      return pType;
   }

   /**
    * Initialize method. This needs to be delayed otherwise the chart may
    * be null in the constructor.
    */
   protected void init() {
      if(inited) {
         return;
      }

      inited = true;

      if("ChartType".equals(property)) {
         try {
            setMethod = ChartAggregateRef.class.getMethod(
               "set" + property, new Class[] {pType});
         }
         catch(Throwable e) {
         }

         try {
            getMethod = ChartAggregateRef.class.getMethod(
               "get" + property, new Class[] {});
         }
         catch(Throwable e) {
         }
      }
      else if(property.indexOf("Frame") != -1) {
         ChartInfo info = getInfo();

         try {
            if(info instanceof MergedChartInfo) {
               getMethod = AbstractChartInfo.class.getMethod(
                  "get" + property, new Class[] {});
               setMethod = AbstractChartInfo.class.getMethod(
                  "set" + property, new Class[] {getType()});
               getWrapper = AbstractChartInfo.class.getMethod(
               "get" + property + "Wrapper", new Class[] {});
            }
            else {
               getMethod = ChartAggregateRef.class.getMethod(
                  "get" + property, new Class[] {});
               setMethod = ChartAggregateRef.class.getMethod(
                  "set" + property, new Class[] {getType()});
               getWrapper = ChartAggregateRef.class.getMethod(
               "get" + property + "Wrapper", new Class[] {});
            }
         }
         catch(Throwable e) {
         }
      }
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "AbstractChartArray";
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String id, Scriptable start) {
      return get(id, start) != null;
   }

   /**
    * Indicate whether or not a indexed property is defined in an object.
    */
   @Override
   public boolean has(int index, Scriptable start) {
      return false;
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String id, Scriptable start) {
      init();

      ChartInfo info = getInfo();

      if("Axis".equals(property)) {
         return new AxisScriptable(info, id);
      }
      else if(getMethod != null) {
         try {
            if(info instanceof MergedChartInfo) {
               try {
                  VisualFrameWrapper wrapper = (VisualFrameWrapper)
                     getWrapper.invoke(info, new Object[0]);
                  wrapper.setChanged(true);
               }
               catch(Exception ex) {
                  // ignore it
               }

               return getMethod.invoke(info, new Object[0]);
            }
            else {
               // @by davidd feature1336679288695, support non-precise field
               // matching. For example: ['Order Date'] vs ['Year(Order Date)']
               ChartRef ref = ChartProcessor.getRuntimeField(info, id);

               if(ref != null) {
                  try {
                     VisualFrameWrapper wrapper = (VisualFrameWrapper)
                        getWrapper.invoke(ref, new Object[0]);
                     wrapper.setChanged(true);
                  }
                  catch(Exception ex) {
                     // ignore it
                  }
               }

               return ref == null ? null : getMethod.invoke(ref, new Object[0]);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to get chart array property: " + id, e);
         }
      }

      return super.get(id, start);
   }

   /**
    * Get a indexed property from the object.
    */
   @Override
   public Object get(int index, Scriptable start) {
      return Undefined.instance;
   }

   /**
    * Sets a named property in this object.
    */
   @Override
   public void put(String id, Scriptable start, Object value) {
      init();

      if(setMethod != null) {
         ChartInfo info = getInfo();

         try {
            Object[] p = new Object[] {PropertyDescriptor.convert(value, pType)};
            // @by davyc, set both to runtime and design time field
            // so viewsheet can working correct, cause viewsheet script
            // scope not same as designer scope
            // fix bug1325221033252
            if(info instanceof MergedChartInfo) {
               setMethod.invoke(info, p);
            }
            else {
               setMethod.invoke(info.getFieldByName(id, true), p);
               setMethod.invoke(info.getFieldByName(id, false), p);
            }

            if("setChartType".equals(setMethod.getName())) {
               info.updateChartType(!info.isMultiStyles());
            }
            //fix bug1334654165836, size wrapper need setchanged.
            else if("setSizeFrame".equals(setMethod.getName())) {
               ChartRef ref = info.getFieldByName(id, true);
               ChartRef ref2 = info.getFieldByName(id, false);

               if(ref instanceof ChartAggregateRef) {
                  ((ChartAggregateRef) ref).getSizeFrameWrapper().setChanged(true);
               }

               if(ref2 instanceof ChartAggregateRef) {
                  ((ChartAggregateRef) ref2).getSizeFrameWrapper().setChanged(true);
               }
            }
         }
         catch(Exception e) {
            LOG.error(
               "Failed to set property in chart array: " + id + "=" + value, e);
         }
      }
      else {
         LOG.error("Property cannot be modified: " + property);
      }
   }

   /**
    * Sets a indexed property in this object.
    */
   @Override
   public void put(int index, Scriptable start, Object value) {
      LOG.error("Indexed property cannot be modified: " + property);
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
      init();
      return ids;
   }

   /**
    * Check if a specific object has an instance.
    */
   @Override
   public boolean hasInstance(Scriptable value) {
      return false;
   }

   /**
    * Get chart info. Don't remember the info, get it dynamically so
    * if the info is changed in a script (e.g. change the chart type
    * to map), the new info is used in subsequent scripts.
    */
   public abstract ChartInfo getInfo();

   /**
    * Get display suffix.
    */
   @Override
   public String getDisplaySuffix() {
      return null;
   }

   /**
    * Get suffix.
    */
   @Override
   public String getSuffix() {
      return "[]";
   }

   protected boolean inited = false;
   protected Method setMethod = null;
   protected Method getMethod = null;
   protected Method getWrapper = null;
   protected String property = "Object";
   protected String[] ids;
   private Class pType = Object.class;

   private static final Logger LOG =
      LoggerFactory.getLogger(ChartArray.class);
}

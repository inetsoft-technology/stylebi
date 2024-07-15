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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.util.script.NativeJavaArray2;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import java.util.ArrayList;
import java.util.List;

/**
 * The range slider viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class RangeSliderVSAScriptable extends SelectionVSAScriptable {
   /**
    * Create a range slider viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public RangeSliderVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "RangeSliderVSA";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null :
         (VSAssembly) vs.getAssembly(assembly);

      if(!(vassembly instanceof TimeSliderVSAssembly)) {
         return Undefined.instance;
      }

      if(name.equals("selectedObjects")) {
         Object[] arr = new Object[2];
         arr[0] = ((TimeSliderVSAssembly) vassembly).getSelectedMin();
         arr[1] = ((TimeSliderVSAssembly) vassembly).getSelectedMax();

         return new NativeJavaArray2(arr, getParentScope());
      }

      initTimeInfo(getInfo().getTimeInfo());
      return super.get(name, start);
   }

   @Override
   public void put(String name, Scriptable start, Object value) {
      initTimeInfo(getInfo().getTimeInfo());
      super.put(name, start, value);
   }

   /**
    * Add the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      TimeSliderVSAssemblyInfo info = getInfo();

      if(info == null) {
         return;
      }

      TimeInfo tinfo = getInfo().getTimeInfo();

      addProperty("title", "getTitle", "setTitle",
         String.class, TimeSliderVSAssemblyInfo.class, info);
      addProperty("minVisible", "isMinVisible", "setMinVisible",
         boolean.class, TimeSliderVSAssemblyInfo.class, info);
      addProperty("maxVisible", "isMaxVisible", "setMaxVisible",
         boolean.class, TimeSliderVSAssemblyInfo.class, info);
      addProperty("currentVisible", "isCurrentVisible", "setCurrentVisible",
         boolean.class, TimeSliderVSAssemblyInfo.class, info);
      addProperty("tickVisible", "isTickVisible", "setTickVisible",
         boolean.class, TimeSliderVSAssemblyInfo.class, info);
      addProperty("logScale", "isLogScale", "setLogScale",
         boolean.class, TimeSliderVSAssemblyInfo.class, info);
      addProperty("upperInclusive", "isUpperInclusive", "setUpperInclusive",
         boolean.class, TimeSliderVSAssemblyInfo.class, info);
      addProperty("composite", "isComposite", "setComposite",
                  boolean.class, TimeSliderVSAssemblyInfo.class, info);

      initTimeInfo(tinfo);
   }


   public Object getRangeMin() {
      return rangeMin;
   }

   public void setRangeMin(Object rangeMin) {
      this.rangeMin = rangeMin;
      initRange(rangeMin, rangeMax);
   }

   public Object getRangeMax() {
      return rangeMax;
   }

   public void setRangeMax(Object rangeMax) {
      this.rangeMax = rangeMax;
      initRange(rangeMin, rangeMax);
   }

   /**
    * Make sure TimeInfo scriptable is up-to-date.
    */
   private void initTimeInfo(TimeInfo tinfo) {
      if(tinfo == o_tinfo) {
         return;
      }

      o_tinfo = tinfo;
      SingleTimeInfo single = null;

      if(tinfo instanceof SingleTimeInfo) {
         single = (SingleTimeInfo) tinfo;
      }

      addProperty("length", "getLength", "setLength",
                  int.class, tinfo.getClass(), tinfo);
      addProperty("min", "getMin", "setMin",
                  Object.class, tinfo.getClass(), tinfo);
      addProperty("max", "getMax", "setMax",
                  Object.class, tinfo.getClass(), tinfo);
      addProperty("rangeMin", "getRangeMin", "setRangeMin", Object.class, getClass(), this);
      addProperty("rangeMax", "getRangeMax", "setRangeMax", Object.class, getClass(), this);
      addProperty("rangeType", "getRangeType", "setRangeType",
                  int.class, getClass(), this);

      if(single != null) {
         addProperty("rangeSize", "getRangeSize", "setRangeSize",
                     double.class, getClass(), this);
         addProperty("maxRangeSize", "getMaxRangeSize", "setMaxRangeSize",
                     double.class, getClass(), this);
      }

      addProperty("selectedObjects", null);
   }

   /**
    * Set the initial ranges for the range slider if set through script
    */
   private void initRange(Object min, Object max) {
      final boolean hasMin = min != null;
      final boolean hasMax = max != null;

      if(!hasMin && !hasMax) {
         return;
      }

      final Viewsheet vs = box.getViewsheet();
      final Assembly assembly = vs.getAssembly(this.assembly);

      if(assembly instanceof TimeSliderVSAssembly) {
         final TimeSliderVSAssembly timeSlider = (TimeSliderVSAssembly) assembly;

         // all values
         final SelectionList slist = timeSlider.getSelectionList();

         // clear current selection
         final SelectionList stateList = timeSlider.getStateSelectionList();
         stateList.clear();

         // if there's a min then deselect all prior values
         boolean state = !hasMin;
         int count = 0;

         for(int i = 0; i < slist.getSelectionValueCount(); i++) {
            final SelectionValue sval = slist.getSelectionValue(i);
            final Object[] splitSelectedValues = timeSlider.getSplitSelectedValues(sval);

            // selected false until min, true until max
            if(hasMax && isGreaterThan(splitSelectedValues, max)) {
               state = false;
            }
            else if(hasMin && isGreaterThan(splitSelectedValues, min)) {
               state = true;
            }

            count += state ? 1 : 0;
            sval.setSelected(state);
            stateList.addSelectionValue(sval);
         }

         timeSlider.setLengthValue(count);
      }
   }

   public int getRangeType() {
      TimeInfo tinfo = getInfo().getTimeInfo();

      if(tinfo instanceof SingleTimeInfo) {
         return ((SingleTimeInfo) tinfo).getRangeType();
      }

      return TimeInfo.MONTH;
   }

   public void setRangeType(int rtype) {
      TimeInfo tinfo = getInfo().getTimeInfo();

      if(tinfo instanceof SingleTimeInfo) {
         ((SingleTimeInfo) tinfo).setRangeType(rtype);
      }
   }

   public void setRangeTypeValue(int rtype) {
      TimeInfo tinfo = getInfo().getTimeInfo();

      if(tinfo instanceof SingleTimeInfo) {
         ((SingleTimeInfo) tinfo).setRangeTypeValue(rtype);
      }
   }

   public double getRangeSize() {
      TimeInfo tinfo = getInfo().getTimeInfo();

      if(tinfo instanceof SingleTimeInfo) {
         return ((SingleTimeInfo) tinfo).getRangeSize();
      }

      return 0;
   }

   public void setRangeSize(double rsize) {
      TimeInfo tinfo = getInfo().getTimeInfo();

      if(tinfo instanceof SingleTimeInfo) {
         ((SingleTimeInfo) tinfo).setRangeSize(rsize);
      }
   }

   public void setRangeSizeValue(double rsize) {
      TimeInfo tinfo = getInfo().getTimeInfo();

      if(tinfo instanceof SingleTimeInfo) {
         ((SingleTimeInfo) tinfo).setRangeSizeValue(rsize);
      }
   }

   public double getMaxRangeSize() {
      TimeInfo tinfo = getInfo().getTimeInfo();

      if(tinfo instanceof SingleTimeInfo) {
         return ((SingleTimeInfo) tinfo).getMaxRangeSize();
      }

      return 0;
   }

   public void setMaxRangeSize(double rsize) {
      TimeInfo tinfo = getInfo().getTimeInfo();

      if(tinfo instanceof SingleTimeInfo) {
         ((SingleTimeInfo) tinfo).setMaxRangeSize(rsize);
      }
   }

   public void setMaxRangeSizeValue(double rsize) {
      TimeInfo tinfo = getInfo().getTimeInfo();

      if(tinfo instanceof SingleTimeInfo) {
         ((SingleTimeInfo) tinfo).setMaxRangeSizeValue(rsize);
      }
   }

   /**
    * Get Fields.
    */
   @Override
   public Object[] getFields() {
      if(getInfo() instanceof TimeSliderVSAssemblyInfo) {
         DataRef[] datarefs = getInfo().getDataRefs();
         List<String> refName = new ArrayList<>();

         for(DataRef dataref : datarefs) {
            if(dataref instanceof ColumnRef) {
               refName.add(dataref.getAttribute());
            }
         }

         return refName.toArray(new String[0]);
      }

      return null;
   }

   /**
    * Set Fields.
    */
   @Override
   public void setFields(Object[] fields) {
      if(fields.length > 0) {
         TimeSliderVSAssemblyInfo timeSliderInfo = getInfo();
         TimeInfo timeinfo = timeSliderInfo.getTimeInfo();

         // composite time info?
         if(timeSliderInfo.isComposite())
         {
            CompositeTimeInfo ctimeinfo =
               timeinfo instanceof CompositeTimeInfo ?
               (CompositeTimeInfo) timeinfo : new CompositeTimeInfo();
            DataRef[] datarefs = ctimeinfo.getDataRefs();
            boolean refreshFlag = false;

            if(datarefs.length == fields.length) {
               for(int i = 0; i < datarefs.length; i++) {
                  ColumnRef colref = (ColumnRef) datarefs[i];

                  if(colref.getAttribute() == null ||
                     !(colref.getAttribute()).equals((String) fields[i]))
                  {
                     refreshFlag = true;
                     break;
                  }
               }
            }
            else {
               refreshFlag = true;
            }

            if(refreshFlag) {
               ArrayList<DataRef> secList = new ArrayList<>();

               for(Object field:fields) {
                  ColumnRef colref = new ColumnRef();
                  colref.setDataRef(new AttributeRef((String) field));
                  secList.add(colref);
               }

               DataRef[] colrefs = new DataRef[secList.size()];
               colrefs = secList.toArray(colrefs);
               ctimeinfo.setDataRefs(colrefs);
               timeSliderInfo.setComposite(true);
               timeSliderInfo.setTimeInfo(ctimeinfo);
            }
         }
         // normal time info?
         else if(!timeSliderInfo.isComposite())
         {
            SingleTimeInfo stimeinfo =
               timeinfo instanceof SingleTimeInfo ?
               (SingleTimeInfo) timeinfo : new SingleTimeInfo();
            DataRef dataref = stimeinfo.getDataRef();
            ColumnRef colref =
            (dataref == null) ? new ColumnRef() : (ColumnRef) dataref;

            colref.setDataRef(new AttributeRef((String) fields[0]));
            stimeinfo.setDataRef(colref);
            stimeinfo.setRangeTypeValue(getRangeType());
            timeSliderInfo.setComposite(false);
            timeSliderInfo.setTimeInfo(stimeinfo);
         }
      }
   }

   /**
    * Get the assembly info of current range slider.
    */
   private TimeSliderVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof TimeSliderVSAssemblyInfo) {
         return (TimeSliderVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new TimeSliderVSAssemblyInfo();
   }

   /**
    * Check if the selection value is greater than the given rangeMin/rangeMax value
    *
    * @param v1 the selection value from the slider
    * @param v2 the rangeMin/rangeMax value from the script
    *
    * @return true if the range value is greater than the selection value
    */
   private boolean isGreaterThan(Object[] v1, Object v2) {
      if(v2 instanceof Object[] && v1.length != ((Object[]) v2).length) {
         return false;
      }

      for(int i = 0; i < v1.length; i++) {
         final Object val = v2 instanceof Object[] ? ((Object[])v2)[i] : v2;

         if(Tool.compare(v1[i], val) >= 0) {
            return true;
         }
      }

      return false;
   }


   private Object o_tinfo;
   private Object rangeMin;
   private Object rangeMax;
}

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
package inetsoft.web.binding.handler;

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Catalog;

import java.util.ArrayList;
import java.util.List;

public class ChartHandler {
   public void getAllDimensions(DataRef[] refs, List<XDimensionRef> list) {
      for(DataRef ref : refs) {
         if(ref instanceof XDimensionRef) {
            list.add((XDimensionRef) ref);
         }
         else if(ref instanceof AestheticRef) {
            DataRef ref0 = ((AestheticRef) ref).getDataRef();

            if(ref0 instanceof XDimensionRef) {
               list.add((XDimensionRef) ref0);
            }
         }
      }
   }

   public String getDateTimeView(XDimensionRef ref) {
      int dlevel = ref.getDateLevel();

      if(dlevel != -1) {
         String drange = DateRangeRef.getRangeValue(dlevel);

         return Catalog.getCatalog().getString(drange) +
            "(" + ref.getName() + ")";
      }

      return ref.getName();
   }


   public List<String[]> getResetOptions(ChartInfo cinfo, String aggreName) {
      XDimensionRef dim = getInnerDim(cinfo, aggreName);
      String dtype = dim == null ? null : dim.getDataType();
      boolean isYRef = isYRef(cinfo, aggreName);

      if(dtype == XSchema.TIME) {
         return getResetOptions0(CalculatorHandler.resetAtTimes, cinfo, isYRef);
       }
       else if(dtype == XSchema.DATE) {
         return getResetOptions0(CalculatorHandler.resetAtDates, cinfo, isYRef);
       }
       else {
          return getResetOptions0(CalculatorHandler.resetAtDateTimes, cinfo, isYRef);
       }
   }

   private List<String[]> getResetOptions0(String[][] opts, ChartInfo cinfo, boolean isYRef) {
      int dimensionDateLevel = getDimensionInnerDateLevel(cinfo, isYRef);
      XDimensionRef ref = GraphUtil.getInnerDimRef(cinfo, true);

      return calculatorHandler.getResetOptions(opts, ref, dimensionDateLevel);
   }

   private int getDimensionInnerDateLevel(ChartInfo cinfo, boolean fromX) {
      ChartRef[] refs = fromX ? cinfo.getRTXFields() : cinfo.getRTYFields();

      if(refs == null || refs.length == 0) {
         return XConstants.NONE_DATE_GROUP;
      }

      for(int i = refs.length - 1; i >= 0; i--) {
         if(refs[i] instanceof XDimensionRef) {
            XDimensionRef dref = (XDimensionRef) refs[i];

            if(XSchema.isDateType(dref.getDataType())) {
               int level = dref.getDateLevel();
               level = DateRangeRef.isDateTime(level) ?
                  level : XConstants.NONE_DATE_GROUP;

               return calculatorHandler.transferLevel(level);
            }

            break;
         }
      }

      return XConstants.NONE_DATE_GROUP;
   }

   private XDimensionRef getInnerDim(ChartInfo cinfo, String aggreName) {
      ChartRef[] refs = isYRef(cinfo, aggreName) ?
         cinfo.getRTXFields() : cinfo.getRTYFields();
      List<XDimensionRef> list = new ArrayList<>();
      getAllDimensions(refs, list);

      return list.size() > 0 ? list.get(list.size() - 1) : null;
   }

   private boolean isYRef(ChartInfo cinfo, String aggreName) {
      ChartRef[] yrefs = cinfo.getRTYFields();

      for(ChartRef ref : yrefs) {
         if(aggreName.equals(ref.getName())) {
            return true;
         }
      }

      return false;
   }

   public boolean supportReset(ChartInfo cinfo, String aggreName) {
      XDimensionRef dim = getInnerDim(cinfo, aggreName);
      return calculatorHandler.supportReset(dim);
   }

   // @by ChrisSpagnoli bug1412008160666 #1 2014-10-28
   // Determine if tip format contains numeric references
   public boolean tipFormatContainsNumericReferences(String tipfmt) {
      if(tipfmt == null) {
         return false;
      }

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

         // Determine where the end of the reference is, by ","
         int endnumber = tipfmt.indexOf(",", start);
         if(endnumber == -1 || endnumber > end) {
            endnumber = end;
         }

         // Pull the reference out of the format element
         String fragnum = tipfmt.substring(start + 1, endnumber);

         // Test if the reference is numeric
         try {
            Integer.parseInt(fragnum);
            return true;
         }
         catch (NumberFormatException nfe) {
         }

         fmtptr = end + 1;
      }

      return false;
   }

   protected CalculatorHandler calculatorHandler;
}
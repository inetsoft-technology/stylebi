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
package inetsoft.report.internal;

import inetsoft.report.TableLens;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.filter.MetaTableFilter;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.graph.*;
import inetsoft.report.internal.table.ConcatTableLens;
import inetsoft.report.internal.table.FilledTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import org.springframework.util.ObjectUtils;

import java.util.*;

/**
 * This class generate meta table lens for chart element.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ChartMetaTableGenerator {
   /**
    * Create a chart meta data model from a chart element.
    * @param lens the normal generated  XNode meta table lens which only
    * contains one row.
    */
   public ChartMetaTableGenerator(XNodeMetaTable lens) {
      this.lens = lens;
   }

   /**
    * Generate the meta table lens.
    */
   public TableLens generate(ChartVSAssembly elem) {
      List<Field> flds = new ArrayList<>();
      ChartInfo info = elem.getChartInfo().getVSChartInfo();
      VSDataRef[] refs = info.getRTFields();

      for(VSDataRef ref : refs) {
         if(ref instanceof VSAggregateRef) {
            flds.add(new BaseField(((VSAggregateRef) ref).getFullName(false)));
         }
         else {
            flds.add(new BaseField(ref.getFullName()));
         }
      }

      return generate(info, flds);
   }

   /**
    * Generate the meta table lens and handle multi-query table.
    */
   private TableLens generate(ChartInfo info, List<Field> flds) {
      Map<Set, Set> groups = info.getRTFieldGroups();
      Set[] includes = new Set[groups.size()];
      SubColumns[] subcols = new SubColumns[groups.size()];
      String[] cols = new String[flds.size()];
      TableLens[] tbls = new TableLens[groups.size()];
      int idx = 0;

      for(Set dims : groups.keySet()) {
         Set aggrs = groups.get(dims);
         List all = new ArrayList(dims);
         all.addAll(aggrs);
         List<Field> flds0 = new ArrayList<>();
         includes[idx] = new HashSet<>();
         subcols[idx] = new SubColumns(dims, aggrs);

         for(Object ref : all) {
            String fullname = ((VSDataRef) ref).getFullName();

            if(ref instanceof VSAggregateRef) {
               fullname = ((VSAggregateRef) ref).getFullName(false);
            }

            includes[idx].add(fullname);
         }

         for(Object ref : all) {
            if(ref instanceof ChartAggregateRef) {
               GraphUtil.addSubCol((ChartAggregateRef) ref, includes[idx]);
            }
         }

         for(Field field : flds) {
            if(includes[idx].contains(field.getName()) && !flds0.contains(field)) {
               flds0.add(field);
            }
         }

         tbls[idx] = generate0(info, flds0);
         idx++;
      }

      for(int i = 0; i < flds.size(); i++) {
         cols[i] = flds.get(i).getName();
      }

      for(int i = 0; i < tbls.length; i++) {
         tbls[i] = new FilledTableLens(tbls[i], cols, includes[i], subcols[i]);
      }

      return new MetaTableFilter(new ConcatTableLens(tbls));
   }

   private void addCandleField(ChartRef ref) {
      if(ref instanceof ChartAggregateRef) {
         String name = ((ChartAggregateRef) ref).getFullName(false);

         if(!candleFields.contains(name)) {
            candleFields.add(name);
         }
      }
   }

   /**
    * Generate the meta table lens for a single query.
    */
   private TableLens generate0(ChartInfo info, List<Field> flds) {
      if(info instanceof CandleChartInfo) {
         CandleChartInfo cinfo = (CandleChartInfo) info;
         addCandleField(cinfo.getRTHighField());
         addCandleField(cinfo.getRTCloseField());
         addCandleField(cinfo.getRTOpenField());
         addCandleField(cinfo.getRTLowField());
      }

      List<Object[]> dlist = new ArrayList<>(); // dimension values
      List<Integer> dcols = new ArrayList<>();
      List<Object[]> glist = new ArrayList<>(); // geographic values
      List<Integer> gcols = new ArrayList<>();
      List<Object> mlist = new ArrayList<>(); // measure values
      List<Integer> mcols = new ArrayList<>();
      List<Object[]> exlist = new ArrayList<>(); // explicit x measure values
      List<Integer> excols = new ArrayList<>();
      List<Object[]> eylist = new ArrayList<>(); // explicit y measure values
      List<Integer> eycols = new ArrayList<>();
      int cityCount = 0;
      String type = info instanceof MapInfo ? info.getMapType() : null;
      Set<Field> used = new HashSet<>();
      flds.sort(new FieldComparator(info));
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(lens, true);

      for(int i = 0; i < flds.size(); i++) {
         Field fld = flds.get(i);
         Object[] objs = new Object[2];
         int idx = Util.findColumn(columnIndexMap, fld);

         // ignore duplicate fields
         if(used.contains(fld)) {
            continue;
         }

         used.add(fld);

         if(idx < 0) {
            // @by jasonshobe, 2015-07-24, Bug #347
            // sanity check, caused by query timeout/cancelled or other problem
            // upstream
            continue;
         }

         if(isDimension(info, fld)) {
            String dtype = Tool.getDataType(lens.getColType(idx));
            boolean isDimensionDate = false; // if the dimension is date type
            int dlevel = 0; // date level

            VSDataRef ref = info.getRTFieldByFullName(fld.getName());

            if(ref instanceof XDimensionRef && ((XDimensionRef) ref).isDateTime()) {
               isDimensionDate = true;
               dlevel = ref instanceof VSDimensionRef ?
                      ((VSDimensionRef) ref).getRealDateLevel() :
                      ((XDimensionRef) ref).getDateLevel();
            }

            GeoRef gfld = GraphUtil.getGeoFieldByName(info, fld.getName());
            boolean isGeo = gfld != null;

            if(isGeo) {
               int layer = gfld.getGeographicOption().getLayer();
               objs = MapHelper.getMetaData(type, layer, cityCount);

               if(MapData.isPointLayer(layer)) {
                  cityCount++;
               }
            }
            else if(isDimensionDate) {
               Date date1 = new java.sql.Date(System.currentTimeMillis());
               Calendar calendar = Calendar.getInstance();
               calendar.setTime(date1);

               if(dlevel == DateRangeRef.YEAR_INTERVAL) {
                  calendar.add(Calendar.YEAR, 1);
               }
               else if(dlevel == DateRangeRef.QUARTER_INTERVAL) {
                  calendar.add(Calendar.MONTH, 3);
               }
               else if(dlevel == DateRangeRef.MONTH_INTERVAL) {
                  calendar.add(Calendar.MONTH, 1);
               }
               else if(dlevel == DateRangeRef.WEEK_INTERVAL) {
                  calendar.add(Calendar.WEEK_OF_YEAR, 1);
               }
               else if(dlevel == DateRangeRef.DAY_INTERVAL) {
                  calendar.add(Calendar.DAY_OF_MONTH, 1);
               }
               else if(dlevel == DateRangeRef.HOUR_INTERVAL) {
                  calendar.add(Calendar.HOUR_OF_DAY, 1);
               }
               else if(dlevel == DateRangeRef.MINUTE_INTERVAL) {
                  calendar.add(Calendar.MINUTE, 1);
               }
               else if(dlevel == DateRangeRef.SECOND_INTERVAL) {
                  calendar.add(Calendar.SECOND, 1);
               }
               else if(dlevel == DateRangeRef.QUARTER_OF_YEAR_PART) {
                  calendar.add(Calendar.MONTH, 3);
               }
               else if(dlevel == DateRangeRef.MONTH_OF_YEAR_PART) {
                  calendar.add(Calendar.MONTH, 1);
               }
               else if(dlevel == DateRangeRef.WEEK_OF_YEAR_PART) {
                  calendar.add(Calendar.WEEK_OF_YEAR, 1);
               }
               else if(dlevel == DateRangeRef.DAY_OF_MONTH_PART) {
                  calendar.add(Calendar.DAY_OF_MONTH, 1);
               }
               else if(dlevel == DateRangeRef.DAY_OF_WEEK_PART) {
                  calendar.add(Calendar.DAY_OF_WEEK, 1);
               }
               else if(dlevel == DateRangeRef.HOUR_OF_DAY_PART) {
                  calendar.add(Calendar.HOUR_OF_DAY, 1);
               }

               Date date2 = calendar.getTime();
               objs[0] = DateRangeRef.getData(dlevel, date1);
               objs[1] = DateRangeRef.getData(dlevel, date2);
            }
            else if(dtype.equals(XSchema.STRING)) {
               objs[0] = lens.getObject(0, idx) + "1";
               objs[1] = lens.getObject(0, idx) + "2";
            }
            else if(dtype.equals(XSchema.BOOLEAN)) {
               objs[0] = Boolean.TRUE;
               objs[1] = Boolean.FALSE;
            }
            else if(dtype.equals(XSchema.FLOAT)) {
               objs[0] = 900f;
               objs[1] = 1000f;
            }
            else if(dtype.equals(XSchema.DOUBLE)) {
               objs[0] = 900d;
               objs[1] = 1000d;
            }
            else if(dtype.equals(XSchema.CHAR)) {
               objs[0] = "A";
               objs[1] = "B";
            }
            else if(dtype.equals(XSchema.BYTE)) {
               objs[0] = (byte) 90;
               objs[1] = (byte) 100;
            }
            else if(dtype.equals(XSchema.SHORT)) {
               objs[0] = (short) 900;
               objs[1] = (short) 1000;
            }
            else if(dtype.equals(XSchema.INTEGER)) {
               objs[0] = 900;
               objs[1] = 1000;
            }
            else if(dtype.equals(XSchema.LONG)) {
               objs[0] = 900L;
               objs[1] = 1000L;
            }
            else if(dtype.equals(XSchema.TIME)) {
               Date date1 = new java.sql.Date(System.currentTimeMillis());
               Calendar calendar = Calendar.getInstance();
               calendar.setTime(date1);
               calendar.add(Calendar.HOUR , 1);
               Date date2 = calendar.getTime();

               objs[0] = new java.sql.Time(date1.getTime());
               objs[1] = new java.sql.Time(date2.getTime());
            }

            if(isGeo) {
               if(!ObjectUtils.isEmpty(objs)) {
                  glist.add(objs);
                  gcols.add(idx);
               }
            }
            else {
               dlist.add(objs);
               dcols.add(idx);
            }
         }
         // keep one row data for each measure
         else {
            boolean exm = isExpXMeasure(info, fld.getName());
            boolean eym = isExpYMeasure(info, fld.getName());

            if(exm) {
               int idx2 = exlist.size() + cityCount;
               objs = MapHelper.getLonMetaData(type, idx2);
               exlist.add(objs);
               excols.add(idx);
            }
            else if(eym) {
               int idx2 = eylist.size() + cityCount;
               objs = MapHelper.getLatMetaData(type, idx2);
               eylist.add(objs);
               eycols.add(idx);
            }
            else {
               mlist.add(getNextObj(fld.getName(), lens.getObject(1, idx)));
               mcols.add(idx);
            }
         }
      }

      // clear map, so value will not go down again
      valmap.clear();
      // permute and combine all dimensions
      int ms = mlist.size();
      int ds = dlist.size();
      int gs = glist.size();
      int exs = exlist.size();
      int eys = eylist.size();
      int exp = (gs + exs + eys) == 0 ? ds : ds + 1;
      int rcount = (int) Math.pow(2, exp) + 1;
      int ccount = ms + gs + ds + exs + eys;
      List<Integer> cols = new ArrayList<>();
      cols.addAll(dcols);
      cols.addAll(gcols);
      cols.addAll(eycols);
      cols.addAll(excols);
      cols.addAll(mcols);

      DefaultTableLens resLens = new DefaultTableLens(rcount, ccount);

      resLens.setHeaderRowCount(1);
      resLens.setHeaderColCount(0);

      for(int i = 0; i < rcount; i++) {
         for(int j = 0; j < ccount; j++) {
            // header
            if(i == 0) {
               resLens.setObject(0, j, lens.getObject(0, cols.get(j)));
            }
            // dimension
            else if(j < ds) {
               Object[] temp = dlist.get(j);
               int idx = ((i - 1) % ((int) Math.pow(2, ds - j))) /
                  ((int) Math.pow(2, ds - j - 1));
               resLens.setObject(i, j, temp[idx]);
            }
            // geographic dim
            else if(j < ds + gs) {
               Object[] temp = glist.get(j - ds);
               int idx = i - 1 < (rcount - 1) / 2 ? 0 : 1;
               resLens.setObject(i, j, temp[idx]);
            }
            // y explict measure
            else if(j < ds + gs + eys) {
               Object[] temp = eylist.get(j - ds - gs);
               int idx = i - 1 < (rcount - 1) / 2 ? 0 : 1;

               if(idx < temp.length) {
                  resLens.setObject(i, j, temp[idx]);
               }
            }
            // x explict measure
            else if(j < ds + gs + exs + eys) {
               Object[] temp = exlist.get(j - ds - gs - eys);
               int idx = i - 1 < (rcount - 1) / 2 ? 0 : 1;

               if(idx < temp.length) {
                  resLens.setObject(i, j, temp[idx]);
               }
            }
            // measure
            else {
               Object val = mlist.get(j - ds - gs - exs - eys);
               resLens.setObject(i, j, getNextObj(flds.get(j).getName(), val));
            }
         }
      }

      return resLens;
   }

   /**
    * Check if the specified field is dimension.
    */
   private boolean isDimension(ChartInfo info, Field field) {
      return info.getRTFieldByFullName(field.getName()) instanceof XDimensionRef
         || info.getFieldByName(field.getName(), true) instanceof XDimensionRef;
   }

   /**
    * Get the next meta data value for measure column. If the value is used by
    * other measure column, try to give a new one to avoid the chart area maybe
    * overlap.
    * @param value the original value.
    * @return the next meta data value.
    */
   private Object getNextObj(String fld, Object value) {
      if(value == null) {
         return value;
      }

      // for gantt
      if(value instanceof Date) {
         return value;
      }
      // a measure should be a number, e.g. count(reseller)
      else if(!(value instanceof Number)) {
         return 900;
      }

      String key = fld;

      if(candleFields.contains(fld)) {
         key = "_candle_fields_";
      }

      Set<Integer> objList = valmap.computeIfAbsent(key, k -> new HashSet<>());
      Number val = (Number) value;

      if(objList.contains(val.intValue())) {
         Object next;

         if(val instanceof Float) {
            next = val.floatValue() - 100;
         }
         else if(val instanceof Double) {
            next = val.doubleValue() - 100;
         }
         else if(val instanceof Byte) {
            next = (byte) (val.byteValue() - 10);
         }
         else if(val instanceof Short) {
            next = (short) (val.shortValue() - 10);
         }
         else if(val instanceof Long) {
            next = val.longValue() - 100;
         }
         else {
            next = val.intValue() - 100;
         }

         return getNextObj(fld, next);
      }

      objList.add(val.intValue());

      return val;
   }

   /**
    * Check if the specified field is explicit x measure.
    */
   private static boolean isExpXMeasure(ChartInfo info, String refName) {
      if(!(info instanceof MapInfo)) {
         return false;
      }

      ChartRef[] flds = info.getRTXFields();

      for(int i = 0; i < flds.length; i++) {
         ChartRef fld = flds[i];

         if(refName.equals(fld.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the specified field is explicit y measure.
    */
   private static boolean isExpYMeasure(ChartInfo info, String refName) {
      if(!(info instanceof MapInfo)) {
         return false;
      }

      ChartRef[] flds = info.getRTYFields();

      for(int i = 0; i < flds.length; i++) {
         ChartRef fld = flds[i];

         if(refName.equals(fld.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * FieldComparator sorts dimensions and aggregates.
    */
   private class FieldComparator implements Comparator<Field> {
      public FieldComparator(ChartInfo info) {
         this.info = info;
      }

      /**
       * Compare two data refs.
       */
      @Override
      public int compare(Field f1, Field f2) {
         int l1 = isDimension(info, f1) ? 0 : 1;
         int l2 = isDimension(info, f2) ? 0 : 1;

         if(l1 != l2) {
            return l1 - l2;
         }

         String name1 = f1.getName();
         int idx1 = candleFields.indexOf(name1);
         String name2 = f2.getName();
         int idx2 = candleFields.indexOf(name2);
         return idx1 - idx2;
      }

      private final ChartInfo info;
   }

   private final XNodeMetaTable lens;
   private final List<String> candleFields = new ArrayList<>();
   private final Map<String, Set<Integer>> valmap = new HashMap<>();
}

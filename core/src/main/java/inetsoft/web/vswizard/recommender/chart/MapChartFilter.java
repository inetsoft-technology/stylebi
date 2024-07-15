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
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.aesthetic.StaticShapeFrame;
import inetsoft.report.internal.graph.MapData;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

public class MapChartFilter extends ChartTypeFilter {
   public MapChartFilter(AssetEntry[] entries, VSChartInfo temp, ColumnSelection geos,
                         List<List<ChartRef>> hgroup, boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
      this.geoCols = geos;
   }

   /**
    * Map rule:
    * geo > 0.
    * inside <= 2 (color/size).
    * x only has dimensions
    * y only has geo dimensions
    * inside only has measure on color and size.
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      IntList x = comb.getX();
      IntList y = comb.getY();
      IntList inside = comb.getInside();

      if(comb.getInsideCount() > 2) {
         return false;
      }

      if(hasXMeasure(comb) || hasInsideDimension(comb)) {
         return false;
      }

      long geoDimCnt = 0;
      long geoMeaCnt = 0;
      long otherGeoMeaCnt = 0;

      // @dim true if dimension only, false if aggregate only
      // private long geoCount(IntList list, boolean dim) {
      final List<ChartRef> allRefs = getAllRefs(false);

      for(int i = 0; i < y.size(); i++) {
         if(isGeo(allRefs.get(y.getInt(i)), true)) {
            geoDimCnt++;
         }
         else if(isGeo(allRefs.get(y.getInt(i)), false)) {
            geoMeaCnt++;
         }
      }

      for(int i = 0; i < inside.size(); i++) {
         if(isGeo(allRefs.get(inside.getInt(i)), false)) {
            otherGeoMeaCnt++;
         }
      }

      // only dimension OR lat/lon
      // lat/lon should never be used as aesthetic binding, which is meaningless
      if(geoDimCnt != 0 && geoMeaCnt != 0 || otherGeoMeaCnt > 0) {
         return false;
      }

      // y contains geo cols
      if(y.size() == 0 || geoDimCnt + geoMeaCnt != y.size()) {
         return false;
      }

      // If geo have different map type, return false.
      if(hasGeoDim(y) && getMapType(y) == null) {
         return false;
      }

      return geoDimCnt >= 1 || geoMeaCnt == 2;
   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      List<ChartRef> refs = getAllRefs(true);
      VSMapInfo info = new VSMapInfo();
      info.setChartType(GraphTypes.CHART_MAP);
      info.setGeoColumns(geoCols);
      info.setMeasureMapType(getMapType(comb.getY()));
      addYFields(info, comb.getX(), refs);
      addGeoFields(info, comb.getY());
      addInsideField(info, comb, refs);

      if(!hasPointField(info) && info.getSizeField() == null) {
         info.setShapeFrame(new StaticShapeFrame(GShape.NIL));
      }

      return getClassyInfo(info);
   }

   private static boolean hasPointField(VSMapInfo info) {
      return Arrays.stream(info.getGeoFields())
         .filter(f -> f instanceof GeoRef)
         .anyMatch(f -> MapData.isPointLayer(((GeoRef) f).getGeographicOption().getLayer()));
   }

   @Override
   protected void putInside(VSChartInfo info, ChartRef ref) {
      VSAestheticRef aes = createAestheticRef(ref);

      if(hasPointField((VSMapInfo) info)) {
         if(info.getSizeField() == null) {
            info.setSizeField(aes);
         }
         else if(info.getColorField() == null) {
            info.setColorField(aes);
         }
      }
      else {
         if(info.getColorField() == null) {
            info.setColorField(aes);
         }
         else if(info.getSizeField() == null) {
            info.setSizeField(aes);
         }
      }
   }

   private void addGeoFields(VSMapInfo info, IntList list) {
      List<ChartRef> refs = getAllRefs(true);
      List<ChartRef> geoRefs = new ArrayList<>();
      List<ChartRef> aggrRefs = new ArrayList<>();
      AtomicBoolean foundLatitude = new AtomicBoolean(false);

      list.forEach((IntConsumer) index -> {
         ChartRef ref = refs.get(index);

         if(geoCols.getAttribute(ref.getName()) instanceof GeoRef) {
            GeoRef gref = (GeoRef) geoCols.getAttribute(ref.getName());

            if(gref != null) {
               geoRefs.add(gref);
            }
         }
         else {
            VSChartAggregateRef agg = new VSChartAggregateRef();
            agg.setRefType(ref.getRefType());
            agg.setColumnValue(ref.getName());
            agg.setOriginalDataType(ref.getDataType());

            if(ref instanceof VSChartAggregateRef) {
               agg.setFormulaValue(((VSChartAggregateRef) ref).getFormulaValue());
            }

            if(ref.getName().toLowerCase().contains("lat")) {
               aggrRefs.add(agg);
               foundLatitude.set(true);
            }
            // latitude should be on y (first in addXYField), and longitude on x.
            else if(foundLatitude.get() || ref.getName().toLowerCase().contains("lon")) {
               aggrRefs.add(0, agg);
            }
            else {
               aggrRefs.add(agg);
            }
         }
      });

      addGeoFields(info, geoRefs);
      addXYFields(info, aggrRefs);
   }

   private void addGeoFields(VSMapInfo info, List<ChartRef> geoRefs) {
      geoRefs.stream().sorted(new Comparator<ChartRef>() {
         @Override
         public int compare(ChartRef ref1, ChartRef ref2) {
            return getLayer(ref1) - getLayer(ref2);
         }
      }).forEach(gref -> { info.addGeoField(gref); });
   }

   private void addXYFields(VSMapInfo info, List<ChartRef> aggrRefs) {
      aggrRefs.forEach(aggrRef -> {
         if(info.getXFieldCount() == 0) {
            info.addXField(aggrRef);
         }
         else {
            info.addYField(aggrRef);
         }
      });
   }

   // @dim true if dimension only, false if aggregate only
   private boolean isGeo(ChartRef ref, boolean dim) {
      if(ref instanceof XDimensionRef) {
         if(!dim) {
            return false;
         }
      }
      else if(dim) {
         return false;
      }

      // measure set as geo but changed to dimension should not be treated as geo
      if(ref instanceof XDimensionRef && XSchema.isNumericType(ref.getDataType())) {
         return false;
      }

      return ChartRecommenderUtil.isGeoRef(entries, ref);
   }

   private String getMapType(IntList list) {
      List<ChartRef> refs = getAllRefs(false);
      String mapType = null;

      for(int i = 0; i < list.size(); i++) {
         ChartRef ref = refs.get(list.get(i));

         if(!(geoCols.getAttribute(ref.getName()) instanceof GeoRef)) {
            continue;
         }

         GeoRef gref = (GeoRef) geoCols.getAttribute(ref.getName());

         if(gref == null || gref.getGeographicOption() == null ||
            gref.getGeographicOption().getMapping() == null)
         {
            continue;
         }

         String type = gref.getGeographicOption().getMapping().getType();

         if(mapType == null) {
            mapType = type;
         }
         else if(!mapType.equals(type)) {
            return null;
         }
      }

      return mapType;
   }

   private boolean hasGeoDim(IntList list) {
      List<ChartRef> refs = getAllRefs(false);

      for(int i = 0; i < list.size(); i++) {
         ChartRef ref = refs.get(list.get(i));

         if(geoCols.getAttribute(ref.getName()) instanceof GeoRef) {
            return true;
         }
      }

      return false;
   }

   @Override
   protected int getScore(ChartInfo chart) {
      return 1000;
   }

   private int getLayer(ChartRef ref) {
      if(ref == null || !(ref instanceof VSChartGeoRef)) {
         return -1;
      }

      VSChartGeoRef gref = (VSChartGeoRef) ref;
      GeographicOption option = gref.getGeographicOption();
      return option != null ? option.getLayer() : -1;
   }

   private ColumnSelection geoCols;
}

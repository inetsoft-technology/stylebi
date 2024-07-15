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
package inetsoft.uql.viewsheet.graph;

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.filter.HighlightGroup;

import java.util.Arrays;

/**
 * Interface for a map info class.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public interface MapInfo extends ChartInfo {
   /**
    * Google maps for webmap.service.
    */
   static final String GOOGLE = "googlemaps";
   /**
    * Mapbox maps for webmap.service.
    */
   static final String MAPBOX = "mapbox";

   /**
    * Set the highlight group.
    * @param group the HighlightGroup.
    */
   void setHighlightGroup(HighlightGroup group);

   /**
    * Set hyperlink.
    * @param link the hyperlink.
    */
   void setHyperlink(Hyperlink link);

   /**
    * Get the geo field at the specified position.
    * @param index the index of geo fields.
    * @return the geo field.
    */
   ChartRef getGeoFieldByName(int index);

   /**
    * Set the field at the specified index in geo fields.
    * @param idx the index of the geo fields.
    * @param field the specified field to be added to geo fields.
    */
   void setGeoField(int idx, ChartRef field);

   /**
    * Get all the geo field.
    * @return all the geo fields.
    */
   ChartRef[] getGeoFields();

   /**
    * Get the runtime geo fields.
    * @return the runtime geo fields.
    */
   ChartRef[] getRTGeoFields();

   /**
    * Get the geo fields count.
    * @return the geo fields count.
    */
   int getGeoFieldCount();

   /**
    * Remove the geo field at the specified position.
    */
   void removeGeoField(int index);

   /**
    * Remove all the geo fields.
    */
   void removeGeoFields();

   /**
    * Add a field to be used as geo field.
    * @param field the specified field to be added to geo field.
    */
   void addGeoField(ChartRef field);

   /**
    * Add a field to be used as geo field.
    * @param idx the index of the geo field.
    * @param field the specified field to be added to geo field.
    */
   void addGeoField(int idx, ChartRef field);

   /**
    * Check if the specifed data ref is geographic.
    */
   boolean isGeoRef(String name);

   default ChartRef getGeoFieldByName(String name, boolean rt, boolean ignoreDataGroup) {
      ChartRef[] refs = rt ? getRTGeoFields() : getGeoFields();
      return Arrays.stream(refs)
         .filter(a -> AbstractChartInfo.isSameField(a, GraphUtil.getGeoField(name), ignoreDataGroup))
         .findFirst().orElse(null);
   }

   default boolean isLon(ChartRef ref) {
      return getXFieldCount() > 0 &&
         ref.getName().equals(getXField(getXFieldCount() - 1).getName());
   }

   default boolean isLat(ChartRef ref) {
      return getYFieldCount() > 0 &&
         ref.getName().equals(getYField(getYFieldCount() - 1).getName());
   }

   default boolean shouldAggregate(ChartRef ref) {
      return ChartInfo.super.shouldAggregate(ref) && !isLat(ref) && !isLon(ref);
   }

   default boolean hasXYDimension() {
      ChartRef[][] xyfields = { getRTXFields(), getRTYFields() };

      for(ChartRef[] refs : xyfields) {
         for(ChartRef ref : refs) {
            if(!ref.isMeasure()) {
               return true;
            }
         }
      }

      return false;
   }
}

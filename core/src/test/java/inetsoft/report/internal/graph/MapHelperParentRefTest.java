/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.report.internal.graph;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82160: the Geographic Mapping dialog (and the mapping status flag) must pick
 * the parent geo column the same way the map renderer does - only from the geo fields
 * bound to the map - not from a column that is only marked Geographic in the data tree.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MapHelperParentRefTest {
   private static final int COUNTRY = 1;
   private static final int STATE = 2;
   private static final int CITY = 1001;

   /**
    * City is bound, Country is only marked Geographic (unbound) but is in the data set,
    * as VSChartHandler.getChartData puts it there. Its values do not pair with the
    * cities table, so using it as the parent made every city "unmatched" although the
    * renderer (name-only, no bound parent) draws them.
    */
   @Test
   void unboundGeographicColumnIsNotUsedAsParent() {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "PLACE_NAME", "COUNTRY" },
         { "Atlanta", "United States of America" },
         { "Chicago", "United States of America" },
         { "San Francisco", "United States of America" },
         { "Xyzzyville", "United States of America" },
      });
      VSMapInfo info = createMapInfo(
         new String[] { "PLACE_NAME", "COUNTRY" }, new int[] { CITY, COUNTRY },
         new boolean[] { true, false });

      assertEquals(Set.of("Xyzzyville"), unmatched(data, "PLACE_NAME", info));
   }

   /**
    * A bound parent (State) is still used for city+state pair matching: London is a
    * U.S. city name but not in Georgia, so it stays unmatched.
    */
   @Test
   void boundParentIsStillUsed() {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "PLACE_NAME", "STATE_NAME", "COUNTRY" },
         { "Atlanta", "Georgia", "United States of America" },
         { "London", "Georgia", "United States of America" },
         { "Xyzzyville", "Georgia", "United States of America" },
      });
      VSMapInfo info = createMapInfo(
         new String[] { "PLACE_NAME", "STATE_NAME", "COUNTRY" },
         new int[] { CITY, STATE, COUNTRY }, new boolean[] { true, true, false });

      assertEquals(Set.of("London", "Xyzzyville"), unmatched(data, "PLACE_NAME", info));
   }

   private static Set<String> unmatched(DataSet data, String col, VSMapInfo info) {
      FeatureMapping mapping = new FeatureMapping();
      mapping.setType("U.S.");
      mapping.setLayer(CITY);
      int index = GraphUtil.indexOfHeader(data, col);
      return new TreeSet<>(MapHelper.getUnMatchedValues(data, index, mapping, info).keySet());
   }

   /**
    * Every column is marked Geographic in the data tree (geoCols); only the ones
    * flagged bound are also runtime geo fields of the map.
    */
   private static VSMapInfo createMapInfo(String[] names, int[] layers, boolean[] bound) {
      Viewsheet vs = new Viewsheet();
      ColumnSelection columns = new ColumnSelection();

      for(String name : names) {
         columns.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      VSMapInfo info = new VSMapInfo();
      ColumnSelection geoCols = new ColumnSelection();

      for(int i = 0; i < names.length; i++) {
         geoCols.addAttribute(createGeoRef(names[i], layers[i]));
      }

      info.setGeoColumns(geoCols);
      info.updateGeoColumns(vs, columns);
      assertEquals(names.length, info.getRTGeoColumns().getAttributeCount());

      for(int i = 0; i < names.length; i++) {
         if(bound[i]) {
            List<DataRef> rrefs = createGeoRef(names[i], layers[i]).update(vs, columns);
            assertEquals(1, rrefs.size());
            info.addRTGeoField((ChartRef) rrefs.get(0));
         }
      }

      return info;
   }

   private static VSChartGeoRef createGeoRef(String name, int layer) {
      VSChartGeoRef ref = new VSChartGeoRef();
      ref.setGroupColumnValue(name);
      GeographicOption opt = new GeographicOption();
      opt.setLayerValue(String.valueOf(layer));
      FeatureMapping mapping = new FeatureMapping();
      mapping.setType("U.S.");
      mapping.setLayer(layer);
      opt.setMapping(mapping);
      ref.setGeographicOption(opt);
      return ref;
   }
}

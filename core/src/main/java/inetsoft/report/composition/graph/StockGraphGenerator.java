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
package inetsoft.report.composition.graph;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.schema.SchemaPainter;
import inetsoft.graph.schema.StockPainter;
import inetsoft.uql.VariableTable;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;

import java.awt.*;

/**
 * CandleGraphGenerator generates candle element graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class StockGraphGenerator extends CandleGraphGenerator {
   /**
    * Constructor.
    * @param chart the specified chart.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    */
   public StockGraphGenerator(ChartVSAssemblyInfo chart, DataSet adata, DataSet data,
                              VariableTable vars, DataSet vdata, int sourceType, Dimension size)
   {
      super(chart, adata, data, vars, vdata, sourceType, size);
   }

   /**
    * Constructor.
    * @param info the specified chart info.
    * @param desc the specified chart descriptor.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    * @param vsrc the source (worksheet/tbl, query) of the chart.
    */
   public StockGraphGenerator(ChartInfo info, ChartDescriptor desc, DataSet adata, DataSet data,
                              VariableTable vars, String vsrc, int sourceType, Dimension size)
   {
      super(info, desc, adata, data, vars, vsrc, sourceType, size);
   }

   /**
    * Check if the open field is required.
    */
   @Override
   protected boolean isOpenRequired() {
      return false;
   }

   /**
    * Get the actual schema chart type.
    */
   @Override
   protected int getSchemaType() {
      return GraphTypes.CHART_STOCK;
   }

   /**
    * Create the painter for the schema.
    */
   @Override
   protected SchemaPainter createSchemaPainter() {
      return new StockPainter();
   }
}

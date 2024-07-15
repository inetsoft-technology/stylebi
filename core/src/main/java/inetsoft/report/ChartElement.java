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
package inetsoft.report;

import inetsoft.graph.EGraph;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.report.internal.BindableElement;
import inetsoft.report.internal.BorderedElement;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.ChartInfo;

/**
 * This class represents a chart element. Chart elements are painters, so
 * a chart element also shares all properties in a painter element.
 */
public interface ChartElement extends PainterElement, BindableElement, BorderedElement {
   /*
    * The X-axis title used to set TextID.
    */
   public static final String X_AXIS_TITLE = "X Axis Title";
   /*
    * The Y-axis title used to set TextID.
    */
   public static final String Y_AXIS_TITLE = "Y Axis Title";
   /*
    * The secondary X-axis title used to set TextID.
    */
   public static final String X_2ND_AXIS_TITLE = "Secondary X Axis Title";
   /*
    * The secondary Y-axis title used to set TextID.
    */
   public static final String Y_2ND_AXIS_TITLE = "Secondary Y Axis Title";

   /*
    * Get chart data.
    */
   public DataSet getDataSet();

   /**
    * Set chart data. It contains chart datasets, as well as attributes for
    * controlling the chart rendering.
    */
   public void setDataSet(DataSet chart);

   /**
    * Get the chart descriptor.
    */
   public ChartDescriptor getChartDescriptor();

   /**
    * Set the chart descriptor. A chart descriptor stores secondary
    * attributes for controlling chart rendering.
    */
   public void setChartDescriptor(ChartDescriptor desc);

   /**
    * Get the report chart info.
    */
   public ChartInfo getChartInfo();

}


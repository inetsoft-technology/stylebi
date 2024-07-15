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
package inetsoft.web.binding.model.graph;

/**
 * This class is used as a descriptor for ChartRefModel.
 *
 * @version 12.2
 * @author  InetSoft Technology Corp
 */
public class OriginalDescriptor {
   /**
    * X axis.
    */
   public static final String X_AXIS = "X";
   /**
    * Y axis.
    */
   public static final String Y_AXIS = "Y";
   /**
    * Group.
    */
   public static final String GROUP = "Group";
   /**
    * Path.
    */
   public static final String PATH = "Path";
   /**
    * Geo.
    */
   public static final String GEO = "Geo";
   /**
    * Geo column.
    */
   public static final String GEO_COL = "GeoCol";
   /**
    * Close.
    */
   public static final String CLOSE = "Close";
   /**
    * Open.
    */
   public static final String OPEN = "Open";
   /**
    * High.
    */
   public static final String HIGH = "High";
   /**
    * Low.
    */
   public static final String LOW = "Low";
   public static final String SOURCE = "Source";
   public static final String TARGET = "Target";
   public static final String START = "Start";
   public static final String END = "End";
   public static final String MILESTONE = "Milestone";
   /**
    * Shape legend.
    */
   public static final String SHAPE = "Shape";
   /**
    * Color legend.
    */
   public static final String COLOR = "Color";
   /**
    * Size legend.
    */
   public static final String SIZE = "Size";
   /**
    * Text legend.
    */
   public static final String TEXT = "Text";
   /**
    * All aggregate.
    */
   public static final String ALL = "All";

   public static final String NODE_COLOR = "NodeColor";
   public static final String NODE_SIZE = "NodeSize";

   /**
    * Constructor.
    */
   public OriginalDescriptor() {
   }

   /**
    * Constructor.
    */
   public OriginalDescriptor(String source) {
      setSource(source);
      setIndex(-1);
   }

   /**
    * Constructor.
    */
   public OriginalDescriptor(String source, OriginalDescriptor aggregateDesc) {
      this(source);
      setAggregateDesc(aggregateDesc);
   }

   /**
    * Constructor.
    */
   public OriginalDescriptor(String source, int index) {
      setSource(source);
      setIndex(index);
   }

   /**
    * Get the original source.
    */
   public String getSource() {
      return source;
   }

   /**
    * Set the original source.
    */
   public void setSource(String source) {
      this.source = source;
   }

   /**
    * Get the original index.
    */
   public int getIndex() {
      return idx;
   }

   /**
    * Set the original index.
    */
   public void setIndex(int idx) {
      this.idx = idx;
   }

   /**
    * Get the aggregate descriptor.
    */
   public OriginalDescriptor getAggregateDesc() {
      return aggregateDesc;
   }

   /**
    * Set the aggregate descriptor.
    */
   public void setAggregateDesc(OriginalDescriptor aggregateDesc) {
      this.aggregateDesc = aggregateDesc;
   }

   private String source;
   private int idx;
   private OriginalDescriptor aggregateDesc;
}
/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.region;

import inetsoft.graph.EGraph;
import inetsoft.graph.coord.MekkoCoord;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.scale.TimeScale;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;
import inetsoft.uql.XConstants;
import inetsoft.uql.XCube;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VSUtil;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.*;

/**
 * AxisArea represents an axis area.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class AxisArea extends SortedContainerArea implements GraphComponentArea {
   /**
    * Constructor.
    */
   public AxisArea(AbstractArea[] areas, String direction, boolean isRotated,
                   int dropType, Rectangle2D bounds, Rectangle2D layoutbounds,
                   String axisType, AffineTransform trans,
                   Rectangle2D ebounds, boolean secondary, ChartInfo cinfo,
                   XCube cube, DefaultAxis[] axes, boolean drillEnabled,
                   EGraph egraph)
   {
      super(areas, direction, isRotated, trans);

      this.dropType = dropType;
      this.axisType = axisType;
      this.bounds = bounds;
      this.layoutbounds = layoutbounds;
      this.secondary = secondary;
      this.cube = cube;
      this.drillEnabled = drillEnabled;

      // the related point should use the evgraph axis bounds
      Point2D p = new Point2D.Double(ebounds.getX(), ebounds.getY());

      for(int i = 0; i < areas.length; i++) {
         areas[i].setRelPos(p);
      }

      container = new SplitContainer(getRegions()[0].getBounds());
      List<DefaultAxis> alist = new ArrayList<>();
      Comparator comp = direction.equals(ChartArea.VERTICAL_DIRECTION)
         ? new AxisComparator(true) : new AxisComparator(false);

      Arrays.sort(axes, comp);

      for(int i = 0; i < axes.length; i++) {
         if(!axes[i].isLabelVisible()) {
            continue;
         }

         if(alist.size() > 0 && comp.compare(axes[i], alist.get(alist.size() - 1)) == 0) {
            continue;
         }

         alist.add(axes[i]);
      }

      axisSizes = new int[alist.size()];
      axisFields = new String[alist.size()];
      axisOps = new String[alist.size()];
      DataRef[] refs = cinfo == null ? null :
         ("x".equals(axisType) ? cinfo.getRTXFields() : cinfo.getRTYFields());
      DataRef[] drefs = cinfo == null ? null :
         ("x".equals(axisType) ? cinfo.getXFields() : cinfo.getYFields());
      Set allfields = new HashSet();
      boolean mekko = egraph.getCoordinate() instanceof MekkoCoord;

      for(int i = 0; i < alist.size(); i++) {
         Rectangle2D asize = alist.get(i).getBounds();
         String[] fields = alist.get(i).getScale().getFields();

         allfields.addAll(Arrays.asList(fields));
         axisSizes[i] = direction.equals(ChartArea.VERTICAL_DIRECTION)
            ? (int) asize.getHeight() : (int) asize.getWidth();
         axisFields[i] = (fields.length > 0) ? fields[0] : "";
         // fix bug1303981735253, should not drill period dimension
         boolean period = cinfo != null && refs != null && drefs != null &&
            isPeriod(axisFields[i], cinfo, refs, drefs);
         axisOps[i] = alist.get(i).isLabelVisible() && drillEnabled &&
            cinfo != null && refs != null && !period && !mekko
            ? VSUtil.getChartDrillOp(cinfo, cube, axisFields[i], refs) : "";
      }

      // last field for this axis
      ChartRef ref = null;

      if(refs != null) {
         for(int i = refs.length - 1; i >= 0; i--) {
            if(allfields.contains(((ChartRef) refs[i]).getFullName())) {
               ref = (ChartRef) refs[i];
               break;
            }
         }
      }

      XDimensionRef innerdim = cinfo == null ?
         null : GraphUtil.getInnerDimRef(cinfo, false);
      boolean sortable = cinfo != null && !GraphTypeUtil.isXYChart(cinfo) &&
         GraphUtil.isMeasure(ref) && innerdim != null &&
         !(egraph.getScale(innerdim.getFullName()) instanceof TimeScale);
      sortField = (ref != null) ? ref.getFullName() : "";
      sortOp = "none";

      if(ref instanceof VSChartAggregateRef) {
         setSortFieldIsCalc(((VSChartAggregateRef) ref).getCalculator() != null);
      }

      if(sortable) {
         int order = innerdim.getOrder();
         String sortBy = innerdim.getSortByCol();

         if(sortField.equals(sortBy)) {
            switch(order) {
            case XConstants.SORT_VALUE_ASC:
               sortOp = "Asc";
               break;
            case XConstants.SORT_VALUE_DESC:
               sortOp = "Desc";
               break;
            default:
               sortOp = "";
            }
         }
         else {
            sortOp = "";
         }
      }
   }

   /**
    * Check the field is period dimension.
    */
   private boolean isPeriod(String field, ChartInfo cinfo, DataRef[] rtrefs,
      DataRef[] refs)
   {
      VSDataRef ref = cinfo.getRTFieldByFullName(field);

      if(ref == null || !(ref instanceof VSDimensionRef)) {
         return false;
      }

      String[] dates = ((VSDimensionRef) ref).getDates();
      return dates != null && dates.length >= 2;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeInt(dropType);
      output.writeUTF(axisType);
      output.writeBoolean(secondary);
      container.writeData(output);

      output.writeInt(axisSizes.length);

      for(int i = 0; i < axisSizes.length; i++) {
         output.writeInt(axisSizes[i]);
         output.writeUTF(axisFields[i]);
         output.writeUTF(axisOps[i]);
      }

      output.writeUTF(sortOp);
      output.writeUTF(sortField);
      output.writeDouble(layoutbounds.getX());
      output.writeDouble(layoutbounds.getY());
      output.writeDouble(layoutbounds.getWidth());
      output.writeDouble(layoutbounds.getHeight());
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      // the bounds has been transformed in ChartArea
      return new Region[] {new RectangleRegion(bounds)};
   }

   /**
    * Get the axis type.
    */
   public String getAxisType() {
      return axisType;
   }

   /**
    * Get the number of axes in the area.
    */
   public int getAxisCount() {
      return axisSizes.length;
   }

   /**
    * Get the axis width/height.
    */
   public int[] getAxisSize() {
      return axisSizes;
   }

   /**
    * Get the field plotted on the axis.
    */
   public String[] getAxisField() {
      return axisFields;
   }

   /**
    * Get the axis width/height.
    */
   public int getAxisSize(int idx) {
      return axisSizes[idx];
   }

   /**
    * Get the field plotted on the axis.
    */
   public String getAxisField(int idx) {
      return axisFields[idx];
   }

   public boolean isSecondary() {
      return secondary;
   }

   public String getSortOp() {
      return sortOp;
   }

   public Rectangle2D getLayoutBounds() {
      return layoutbounds;
   }

   public String[] getAxisOps() {
      return axisOps;
   }

   public String getSortField() {
      return sortField;
   }

   public boolean isSortFieldIsCalc() {
      return sortFieldIsCalc;
   }

   public void setSortFieldIsCalc(boolean sortFieldIsCalc) {
      this.sortFieldIsCalc = sortFieldIsCalc;
   }

   /**
    * Compare axis position.
    */
   private static class AxisComparator implements Comparator<DefaultAxis> {
      public AxisComparator(boolean vertical) {
         this.vertical = vertical;
      }

      @Override
      public int compare(DefaultAxis a1, DefaultAxis a2) {
         Rectangle2D box1 = a1.getBounds();
         Rectangle2D box2 = a2.getBounds();

         double diff = vertical ? box1.getY() - box2.getY()
            : box1.getX() - box2.getX();

         // allow rounding error
         if(Math.abs(diff) < 0.1) {
            return 0;
         }

         return diff > 0 ? 1 : -1;
      }

      private boolean vertical;
   }

   private int dropType;
   private String axisType;
   private boolean secondary;
   private Rectangle2D bounds;
   private Rectangle2D layoutbounds;
   private SplitContainer container;
   private int[] axisSizes;
   private String[] axisFields;
   private String[] axisOps; // + -
   private String sortOp; // null, "", Asc, Desc
   private String sortField;
   private XCube cube;
   private boolean drillEnabled;
   private boolean sortFieldIsCalc;
}

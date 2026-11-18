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
package inetsoft.graph.guide.axis;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.TextFrame;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.AbstractDataSet;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.*;
import inetsoft.graph.scale.*;
import inetsoft.graph.visual.ElementVO;
import inetsoft.graph.visual.FormVO;
import inetsoft.util.ExtendedDecimalFormat;
import inetsoft.util.Tuple;
import org.jnumbers.NumberParser;

import java.awt.*;
import java.awt.geom.*;
import java.text.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This is an axis with a straight line.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class DefaultAxis extends Axis {
   /**
    * Show every label.
    */
   public static final int LABELS_ALL = 0;
   /**
    * Show no label.
    */
   public static final int LABELS_NONE = 1;
   /**
    * Show only the last label, at the far end of the axis.
    */
   public static final int LABELS_OUTERMOST = 2;

   /**
    * Default constructor.
    */
   public DefaultAxis() {
   }

   /**
    * Create an axis for the specified scale.
    * @param scale the scale for this visual axis.
    */
   public DefaultAxis(Scale scale, VGraph vgraph) {
      super(scale, vgraph);
   }

   /**
    * Create the visual elements for the axis.
    */
   @Override
   protected void createVisuals() {
      removeAllVisuals();
      compactTexts = new IdentityHashMap<>();
      vlabels = new VDimensionLabel[0];
      line = new AxisLine(this);
      visibleLabelCnt = 0;
      addVisual(line);

      if(isLabelVisible()) {
         // use getValues() for format
         Object[] values = getScale().getValues();
         Object[] labels = getValueLabels();
         Scale scale = getScale();
         AxisSpec spec = scale.getAxisSpec();
         TextFrame aliasframe = spec.getTextFrame();
         Set aliased = new HashSet((aliasframe != null) ? aliasframe.getKeys() : new Vector());
         vlabels = new VDimensionLabel[labels.length];
         boolean hor = isHorizontal();
         CompactDates compact = spec.getCompactDates();
         Format full = compact == null ? null : compact.full(scale);
         Format brief = compact == null ? null : compact.brief(scale);
         boolean fromLast = isReadFromLast();

         for(int i = 0; i < vlabels.length; i++) {
            boolean isalias = aliased.size() > 0 && aliased.contains(GTool.toString(values[i]));
            // the label read before this one
            int prev = fromLast ? i + 1 : i - 1;
            boolean hasPrev = prev >= 0 && prev < labels.length;
            Object prevLabel = hasPrev ? labels[prev] : null;
            Object label = labels[i];
            String[] texts = null;

            // the alias is a different value from the date in the label
            if(full != null && brief != null && !isalias && values[i] instanceof Date &&
               values[i].equals(label))
            {
               texts = new String[] { full.format(values[i]), brief.format(values[i]) };
               boolean sameYear = hasPrev && values[prev] instanceof Date &&
                  year((Date) values[prev]) == year((Date) values[i]);
               label = sameYear ? texts[1] : texts[0];
            }

            vlabels[i] = createLabel(label, prevLabel, isalias || texts != null, values[i], hor);

            if(texts != null) {
               compactTexts.put(vlabels[i], texts);
            }

            vlabels[i].setTruncate(spec.isTruncate());
            vlabels[i].setWrapping(!spec.isTruncate());
            vlabels[i].setEllipsis(spec.getLabelLimit() > 0);
            vlabels[i].setLastOrAll(spec.isLastOrAll());

            if(spec.isLastOrAll()) {
               vlabels[i].setTruncateLines(true);
            }

            addVisual(vlabels[i]);
            Object lbl = vlabels[i].getLabel();

            if(lbl != null && !lbl.equals("")) {
               visibleLabelCnt++;
            }
            else {
               vlabels[i].setZIndex(-1);
            }
         }
      }
   }

   /**
    * Create a label object.
    * @param label label object.
    * @param prevLabel previous label.
    * @param isAlias if the label is explicitly set as an alias value.
    * @param value label original value.
    */
   private VDimensionLabel createLabel(Object label, Object prevLabel,
                                       boolean isAlias, Object value, boolean hor)
   {
      Scale scale = getScale();
      String[] fields = scale.getFields();
      String field = (fields.length > 0) ? fields[0] : null;
      AxisSpec spec = scale.getAxisSpec();
      TextSpec spec0 = createLabelSpec(spec, value);
      double weight = 0;
      double[] ticks = scale.getTicks();

      // prevent axis showing same values when format is set to K/M/B and the value
      // range is changed (by selection).
      if(spec0.getFormat() instanceof ExtendedDecimalFormat) {
         ExtendedDecimalFormat fmt = (ExtendedDecimalFormat) spec0.getFormat();
         boolean unevenTicks = scale instanceof LinearScale &&
            ((LinearScale) scale).getUserMin() != null;

         // LinearScale may cause the last interval to be not at exact increment in
         // getTicks() if the smin is explicitly set, so use the second interval to
         // get the correct increment if possible.
         if(ticks.length > 2 && unevenTicks) {
            spec0.setFormat(fmt.setIncrement(ticks[2] - ticks[1]));
         }
         else if(ticks.length > 1) {
            spec0.setFormat(fmt.setIncrement(ticks[1] - ticks[0]));
         }
      }

      Abbreviation labelVal = new Abbreviation(label, null);

      if(spec.isAbbreviate() && !isAlias && ticks.length > 1) {
         labelVal = abbreviate(label, prevLabel, spec0);
      }

      // avoid the abbreviated string being formatted again (Message)
      // keep date format to format the value text for editing alias
      // abbreviate already format the value, don't format again.
      // if label is from explicitly defined alias, don't format
      if(spec.isAbbreviate() && ticks.length > 1 || isAlias) {
         spec0.setFormat(null);
      }

      // used to align vertical axis of facet
      if(!hor && isCenterLabel()) {
         if(scale instanceof CategoricalScale) {
            weight = ((CategoricalScale) scale).getWeight(value);
         }
      }

      return new VDimensionLabel(labelVal.getLabel(), spec0, field, axisType, value, weight,
                                 labelVal.getAnchor());
   }

   static TextSpec createLabelSpec(AxisSpec axisSpec, Object value) {
      TextSpec textSpec = axisSpec.getTextSpec();
      TextSpec spec0 = textSpec.clone();

      spec0.setColor(axisSpec.getColor(value));
      spec0.setFont(axisSpec.getFont(value));
      return spec0;
   }

   /**
    * Paint the axis.
    */
   @Override
   protected void paintVisualizables(Graphics2D g, List<Visualizable> visuals,
                                     GraphPaintContext ctx)
   {
      prepareLabels();

      Color bg0 = getBackground0();
      List<VLabel> changedLabels = new ArrayList();

      // paint bg on entire axis if labels have background and not rotated
      if(getScale().getAxisSpec().getTextSpec().getRotation() % 90 == 0 &&
         Math.toDegrees(GTool.getRotation(getScreenTransform())) % 90 == 0 &&
         bg0 != null && vgraph != null)
      {
         Rectangle2D box = getBounds();
         Rectangle2D plot = vgraph.getPlotBounds();

         //--bug #10925, avoid between background region and horizontal axis
         // have a vertical spacing when chart type is radar or filled radar.
         if(isLabelAbove() && bounds != null) {
            box = new Rectangle2D.Double(box.getX(), box.getY() + 2,
                                         box.getWidth(), box.getHeight());
         }

         // clip the axis bg within the plot area
         if(plot != null) {
            double xmax = box.getMaxX();
            double ymax = box.getMaxY();
            double xmin = Math.max(plot.getX(), box.getMinX());
            double ymin = Math.max(plot.getY(), box.getMinY());

            if(isHorizontal()) {
               box = new Rectangle2D.Double(xmin, box.getY(),
                                            xmax - xmin, box.getHeight());
            }
            else {
               box = new Rectangle2D.Double(box.getX(), ymin,
                                            box.getWidth(), ymax - ymin);
            }
         }

         g.setColor(bg0);
         g.fill(box);

         // don't draw label bg in this case (if the color is semi-transparent,
         // the color would be darker in label area
         for(VDimensionLabel label : vlabels) {
            if(bg0 == label.getTextSpec().getBackground()) {
               label.getTextSpec().setBackground(null);
               changedLabels.add(label);
            }
         }
      }

      // @by ChrisSpagnoli feature1379102629417 2015-1-15
      // Expands labels to the end of the Y axis.
      visuals = expandLabels(visuals, g);

      super.paintVisualizables(g, visuals, ctx);

      // restore color in spec
      for(VLabel label : changedLabels) {
         label.getTextSpec().setBackground(bg0);
      }
   }

   /**
    * Expand the labels to the end of the calculated axis (can be X or Y).
    */
   private List<Visualizable> expandLabels(List<Visualizable> visuals, Graphics2D g) {
      if(!(visuals.size() > 2 &&
           visuals instanceof ArrayList &&
           visuals.get(0) instanceof AxisLine &&
           visuals.get(1) instanceof VLabel))
      {
         return visuals;
      }

      if(bounds == null || !(getScale() instanceof LinearScale)) {
         return visuals;
      }

      for(int i = 1; i < visuals.size(); i++) {
         Visualizable v = visuals.get(i);

         if(!(v instanceof VLabel)) {
            return visuals;
         }
      }

      double firstX = 0;
      double firstY = 0;
      double lastX = 0;
      double lastY = 0;
      double deltaX = 0;
      double deltaY = 0;
      double deltaFirstX = 0;
      double deltaFirstY = 0;
      double lastLabel = 0;
      double firstLabel = 0;
      double deltaLabel = 0;
      Rectangle2D vbounds = null;
      int alignX = 0;
      int alignY = 0;
      Dimension2D size = null;
      double x = 0;
      double y = 0;
      FontMetrics fm = g.getFontMetrics();

      for(int i = 1; i < visuals.size(); i++) {
         Visualizable v = visuals.get(i);
         VLabel vl = (VLabel)v;
         vbounds = v.getBounds();
         double thisLabel = 0;

         try {
            Object val = vl.getValue();

            if(val instanceof Number) {
               thisLabel = ((Number) val).doubleValue();
            }
            else if(val != null) {
               thisLabel = NumberParser.getDouble(val.toString());

               // parsing failed
               if(Double.isInfinite(thisLabel)) {
                  return visuals;
               }
            }
         }
         catch(NumberFormatException nfe) {
            return visuals;
         }

         if(i == 1) {
            firstX = vbounds.getX();
            firstY = vbounds.getY();
            firstLabel = thisLabel;
         }
         else if(i == 2) {
            alignX = vl.getAlignmentX();
            alignY = vl.getAlignmentY();
            size = vl.getSize();
            x = v.getBounds().getX();
            y = v.getBounds().getY();
            deltaLabel = thisLabel - lastLabel;
         }
         else if(i > 2) {
            if(thisLabel - lastLabel != deltaLabel) {
               return visuals;
            }
         }

         if(i == 2) {
            deltaFirstX = Math.round((vbounds.getX() - lastX) * 1000);
            deltaFirstY = Math.round((vbounds.getY() - lastY) * 1000);
         }
         else if(i == 3) {
            if(isHorizontal()) {
               deltaX = Math.round((vbounds.getX() - lastX) * 1000);
            }
            else {
               deltaY = Math.round((vbounds.getY() - lastY) * 1000);
            }
         }
         else if((i > 3) && (i < (visuals.size()-1))) {
            if(deltaX > 0 && Math.round((vbounds.getX() - lastX) * 1000) != deltaX) {
               return visuals;
            }

            if(deltaY > 0 && Math.round((vbounds.getY() - lastY) * 1000) != deltaY) {
               return visuals;
            }
         }

         lastX = vbounds.getX();
         lastY = vbounds.getY();
         lastLabel = thisLabel;
      }

      boolean labelLeft = isLabelLeft();
      boolean labelAbove = isLabelAbove();
      int forward = line.getProjectForward();
      int backward = line.getProjectBackward();

      // Project labels "forward"
      final Point2D maxPos = getAxisScreenMaxPos();
      Object prevLabel = vlabels.length == 0 ? null : vlabels[vlabels.length - 1].getValue();
      boolean hor = isHorizontal();

      while(deltaX > 0 && lastX + deltaX / 1000 < maxPos.getX() - vbounds.getWidth() ||
            deltaY > 0 && lastY + deltaY / 1000 < maxPos.getY() - vbounds.getHeight())
      {
         // keep expanded label in-sync with expanded ticks. otherwise we
         // will get labels without tick/grid and it looks out of place
         if(forward-- <= 0) {
            break;
         }

         final double thisLabel = lastLabel + deltaLabel;
         final double thisX = lastX + deltaX / 1000;
         final double thisY = lastY + deltaY / 1000;
         VDimensionLabel vl = createLabel(thisLabel, prevLabel, false, thisLabel, hor);
         prevLabel = thisLabel;

         final double xBump = Math.max(
            0, (fm.stringWidth(vl.getText()) - vbounds.getWidth()));
         Point2D anchor = null;
         int placement = labelLeft ? GraphConstants.LEFT : GraphConstants.RIGHT;

         if(deltaX > 0) {
            vl.setBounds(thisX - xBump, y, vbounds.getWidth() + xBump, vbounds.getHeight());

            if(labelAbove) {
               anchor = new Point2D.Double(vl.getBounds().getCenterX(), vl.getBounds().getMinY());
               placement = GraphConstants.TOP;
            }
            else {
               anchor = new Point2D.Double(vl.getBounds().getCenterX(), vl.getBounds().getMaxY());
               placement = GraphConstants.BOTTOM;
            }
         }
         else if(deltaY > 0) {
            double labelX;

            if(labelLeft) {
               labelX = x - xBump;
            }
            else {
               labelX = x;
            }

            vl.setBounds(labelX, thisY, vbounds.getWidth() + xBump, vbounds.getHeight());

            if(labelLeft) {
               anchor = new Point2D.Double(vl.getBounds().getMaxX(), vl.getBounds().getCenterY());
            }
            else {
               anchor = new Point2D.Double(vl.getBounds().getX(), vl.getBounds().getCenterY());
            }
         }
         else {
            break;
         }

         vl.setAlignmentX(alignX);
         vl.setAlignmentY(alignY);
         setRotationOffset(vl, anchor, placement);

         if(xBump == 0) {
            vl.setSize(size);
         }
         else {
            vl.setSize(new Dimension((int) (size.getWidth() + xBump), (int) size.getHeight()));
         }

         visuals.add(vl);
         lastLabel = thisLabel;
         lastX = thisX;
         lastY = thisY;
      }

      // Project labels "backward"
      prevLabel = vlabels.length == 0 ? null : vlabels[0].getValue();

      while(deltaX > 0 && firstX - deltaX / 1000 > bounds.getX() ||
            deltaY > 0 && firstY - deltaY / 1000 > bounds.getY())
      {
         // @see forward
         if(backward-- <= 0) {
            break;
         }

         final double thisLabel = firstLabel - deltaLabel;
         final double thisX = firstX - deltaX / 1000;
         final double thisY = firstY - deltaY / 1000;
         VDimensionLabel vl = createLabel(thisLabel, prevLabel, false, thisLabel, hor);

         prevLabel = thisLabel;

         final int xBump = Math.max(0, (int) (fm.stringWidth(vl.getText()) - vbounds.getWidth()));
         Point2D anchor;
         int placement = labelLeft ? GraphConstants.LEFT : GraphConstants.RIGHT;

         if(deltaX > 0) {
            vl.setBounds(thisX - xBump, y, vbounds.getWidth() + xBump, vbounds.getHeight());
            placement = GraphConstants.BOTTOM;
            anchor = new Point2D.Double(vl.getBounds().getCenterX(), vl.getBounds().getMaxY());
         }
         else if(deltaY > 0) {
            double labelX;

            if(labelLeft) {
               labelX = x - xBump;
            }
            else {
               labelX = x;
            }

            vl.setBounds(labelX, thisY, vbounds.getWidth() + xBump, vbounds.getHeight());

	    if(labelLeft) {
               anchor = new Point2D.Double(vl.getBounds().getMaxX(), vl.getBounds().getCenterY());
            }
            else {
               anchor = new Point2D.Double(vl.getBounds().getX(), vl.getBounds().getCenterY());
            }
         }
         else {
            break;
         }

         vl.setAlignmentX(alignX);
         vl.setAlignmentY(alignY);
         setRotationOffset(vl, anchor, placement);

         if(xBump == 0) {
            vl.setSize(size);
         }
         else {
            vl.setSize(new Dimension((int) (size.getWidth() + xBump), (int) size.getHeight()));
         }

         visuals.add(1,vl);  // position 1 is the first axis label
         firstLabel = thisLabel;
         firstX = thisX;
         firstY = thisY;
      }

      return visuals;
   }

   /**
    * Get the min position including trend projection.
    */
   Point2D getAxisScreenMinPos() {
      Coordinate coord = vgraph.getCoordinate();
      Point2D pos1z = new Point2D.Double(Math.min(0, coord.getTrendLineMin()), 0);
      pos1z = getScreenTransform().transform(pos1z, null);
      return pos1z;
   }

   /**
    * Get the max position including trend projection.
    */
   Point2D getAxisScreenMaxPos() {
      Coordinate coord = vgraph.getCoordinate();
      double maxlength = getLength();

      // trendlineMax is only meaningful for Y position
      if("y".equals(getAxisType())) {
         maxlength = Math.max(maxlength, coord.getTrendLineMax());
      }

      Point2D pos2z = new Point2D.Double(maxlength, 0);
      pos2z = getScreenTransform().transform(pos2z, null);
      return pos2z;
   }

   /**
    * Get the axis fill color.
    */
   private Color getBackground0() {
      Color bg = super.getBackground();

      if(bg != null) {
         return bg;
      }

      // fill the axis if label has background
      return getScale().getAxisSpec().getTextSpec().getBackground();
   }

   /**
    * Set the length of the axis in logic coordinate, if this axis is horizontal
    * the length is width, otherwise it is height.
    * @param length new length of this axis in logic coordinate.
    */
   public void setLength(double length) {
      this.length = length;
   }

   /**
    * Get the length of the axis in logic coordinate.
    * @return the width of the axis in logic coordinate.
    */
   public double getLength() {
      return length;
   }

   /**
    * Set the size of the axis. The size is the height of horizontal axis, and
    * width of vertical axis.
    */
   public void setAxisSize(double size) {
      this.size = size;
   }

   /**
    * Get the size of the axis.
    */
   public double getAxisSize() {
      return size;
   }

   /**
    * Create grid lines from this axis to the other axis.
    * @param axis the other axis to draw grid lines to.
    * @param asc true to create grid line in same order, otherwise create line
    * from the min to the max of the other axis, and so on.
    * @return true if grid lines are created.
    */
   public boolean createGridLines(DefaultAxis axis, boolean asc) {
      AxisSpec spec = getScale().getAxisSpec();
      int style = spec.getGridStyle();
      int zindex = spec.isGridOnTop() ? GDefaults.GRIDLINE_TOP_Z_INDEX : GDefaults.GRIDLINE_Z_INDEX;

      removeAllGridLines();

      if(style == GraphConstants.NONE) {
         return false;
      }

      Object[] svals = getScale().getValues();
      double[] ticks = getTickLocations(null);

      // @by: ChrisSpagnoli feature1379102629417 2015-1-10
      // Project ticks to end of Y axis for grid lines.
      // @by: ChrisSpagnoli bug1421743488123 2015-1-20
      // @by: ChrisSpagnoli bug1422846693610 2015-2-6
      // Only do this for the Y axis, not X
      if("y".equals(axis.getAxisType())) {
         ticks = line.projectTicksTrendLine(ticks);
      }

      // @by larryl, for pareto chart, we have different scale on y and y2,
      // using the same ticks on both for grid makes more sense. This may
      // cause problem for other cases (if so, consider adding an option to
      // control this).
      double[] ticks2 = ticks;
      int cnt = Math.min(ticks.length, ticks2.length);
      boolean between = spec.isGridBetween();

      if(!asc) {
         ticks2 = reverse(ticks);
      }

      Point2D minPos = getAxisScreenMinPos();
      Point2D maxPos = getAxisScreenMaxPos();
      double minX = Math.min(minPos.getX(), maxPos.getX());
      double minY = Math.min(minPos.getY(), maxPos.getY());
      double maxX = Math.max(minPos.getX(), maxPos.getX());
      double maxY = Math.max(minPos.getY(), maxPos.getY());
      Double offset = 0.1;

      for(int i = between ? 1 : 0; i < cnt; i++) {
         if(zeroGridLineHidden && svals.length == ticks.length && svals[i] instanceof Number &&
            ((Number) svals[i]).doubleValue() == 0 && getScale() instanceof LinearScale)
         {
            continue;
         }

         double pos1 = between ? (ticks[i - 1] + ticks[i]) / 2 : ticks[i];
         double pos2 = between ? (ticks2[i - 1] + ticks2[i]) / 2 : ticks2[i];
         Point2D loc = new Point2D.Double(pos1, 0);
         Point2D loc2 = new Point2D.Double(pos2, 0);

         loc = getScreenTransform().transform(loc, null);
         loc2 = axis.getScreenTransform().transform(loc2, null);

         // Never draw grid lines beyond end of the axis line
         // vertical line
         if(loc.getX() == loc2.getX()) {
            if(loc.getY() < minY - offset || loc.getY() > maxY + offset) {
               continue;
            }
         }
         // horizontal line
         else if(loc.getY() == loc2.getY()) {
            if(loc.getX() < minX - offset || loc.getX() > maxX + offset) {
               continue;
            }
         }

         Line2D shape = new Line2D.Double(loc, loc2);
         GridLine line = new GridLine(shape, this, zindex, true);

         // change the zero line to a darker color
         // only if the grid is the only grid line (3 - mintick - maxtick) > 1
         if(svals.length == ticks.length && svals[i] instanceof Number &&
            ((Number) svals[i]).doubleValue() == 0 && i > 0 && cnt > 3 &&
            getScale() instanceof LinearScale)
         {
            Color color = (spec.getGridColor() != null) ? spec.getGridColor()
               : GDefaults.DEFAULT_GRIDLINE_COLOR;

            line.setColor(GTool.darken(color, 0.85));
         }

         addGridLine(line);

         if(vgraph != null && vgraph.isCancelled()) {
            break;
         }
      }

      return true;
   }

   /**
    * Create grid lines from this axis to the other axis at fractions of the axis length.
    * @param axis the other axis to draw grid lines to.
    * @param fractions the positions, each a fraction of the axis length.
    * @return true if grid lines are created.
    */
   public boolean createGridLinesAt(DefaultAxis axis, double[] fractions) {
      AxisSpec spec = getScale().getAxisSpec();
      int zindex = spec.isGridOnTop() ? GDefaults.GRIDLINE_TOP_Z_INDEX : GDefaults.GRIDLINE_Z_INDEX;

      removeAllGridLines();

      if(spec.getGridStyle() == GraphConstants.NONE) {
         return false;
      }

      for(double fraction : fractions) {
         double pos = fraction * getLength();
         Point2D loc = getScreenTransform().transform(new Point2D.Double(pos, 0), null);
         Point2D loc2 = axis.getScreenTransform().transform(new Point2D.Double(pos, 0), null);

         addGridLine(new GridLine(new Line2D.Double(loc, loc2), this, zindex, true));

         if(vgraph != null && vgraph.isCancelled()) {
            break;
         }
      }

      return true;
   }

   /**
    * Create the zero line from this axis to the other axis, replacing any earlier one.
    * @param axis the other axis to draw the line to.
    * @param cross the scale of the cross axis, checked when the spec asks for crossing and
    * the cross axis is a value axis.
    * @return true if a zero line is created.
    */
   public boolean createZeroLine(DefaultAxis axis, Scale cross) {
      if(zeroLine != null && vgraph != null) {
         vgraph.removeVisual(zeroLine);
      }

      zeroLine = null;
      AxisSpec spec = getScale().getAxisSpec();

      if(spec.getZeroLineColor() == null || !crossesZero(getScale()) ||
         spec.isZeroLineCrossing() && cross instanceof LinearScale && !crossesZero(cross))
      {
         return false;
      }

      double pos = getTickLocations(new double[] { getScale().map(0) })[0];
      Point2D loc = getScreenTransform().transform(new Point2D.Double(pos, 0), null);
      Point2D loc2 = axis.getScreenTransform().transform(new Point2D.Double(pos, 0), null);

      zeroLine = new GridLine(new Line2D.Double(loc, loc2), this, GDefaults.AXIS_Z_INDEX, true);
      zeroLine.setColor(spec.getZeroLineColor());
      zeroLine.setStyle(GraphConstants.THIN_LINE);

      if(vgraph != null) {
         vgraph.addVisual(zeroLine);
      }

      return true;
   }

   /**
    * Check whether a linear scale has zero strictly inside its range.
    */
   private static boolean crossesZero(Scale scale) {
      if(!(scale instanceof LinearScale) || scale instanceof LogScale) {
         return false;
      }

      double min = scale.getMin();
      double max = scale.getMax();
      double zero = scale.map(0);

      return !Double.isNaN(zero) && zero > Math.min(min, max) && zero < Math.max(min, max);
   }

   /**
    * Get the zero line of this axis.
    * @hidden
    */
   public GridLine getZeroLine() {
      return zeroLine;
   }

   /**
    * Set whether the grid line at value 0 is left out, because a zero line replaces it.
    * @hidden
    */
   public void setZeroGridLineHidden(boolean zeroGridLineHidden) {
      this.zeroGridLineHidden = zeroGridLineHidden;
   }

   /**
    * Reverse the values in the array.
    */
   static double[] reverse(double[] arr) {
      double[] narr = new double[arr.length];

      for(int i = 0; i < arr.length; i++) {
         narr[i] = arr[arr.length - 1 - i];
      }

      return narr;
   }

   /**
    * Get the X location (chart coordinate) of the ticks.
    */
   double[] getTickLocations(double[] ticks) {
      Scale scale = getScale();

      if(ticks == null) {
         ticks = scale.getTicks();
      }

      double min = scale.getMin();
      double max = scale.getMax();

      for(int i = 0; i < ticks.length; i++) {
         // center label in cell (facet)
         if(scale instanceof CategoricalScale && centerLabel) {
            CategoricalScale scale0 = (CategoricalScale) scale;
            ticks[i] += scale0.getWeightAt(i) / 2;
         }

         if(max == min) {
            ticks[i] = 0;
         }
         else {
            ticks[i] = (ticks[i] - min) * length / (max - min);
         }
      }

      return ticks;
   }

   /**
    * Get the minimum width for displaying this object.
    */
   @Override
   protected double getMinWidth0() {
      return getWidth0(true);
   }

   /**
    * Get the preferred width for displaying this object.
    */
   @Override
   protected double getPreferredWidth0() {
      return getWidth0(false);
   }

   /**
    * Get the min/preferred width.
    * @param min true for minimum width, false for preferred width.
    */
   private double getWidth0(boolean min) {
      double width = getAxisWidth(min);
      AxisSpec spec = getScale().getAxisSpec();

      if(isHorizontal()) {
         width = width + getPreferredSpacing(min) * (vlabels.length - 1);
      }
      else if(spec.getAxisSize() > 0 && isLabelVisible()) {
         return spec.getAxisSize();
      }

      return width;
   }

   /**
    * Get the minimum height for displaying this object.
    */
   @Override
   protected double getMinHeight0() {
      return getHeight0(true);
   }

   /**
    * Get the preferred height for displaying this object.
    */
   @Override
   protected double getPreferredHeight0() {
      return getHeight0(false);
   }

   /**
    * Get the min/preferred height of the axis.
    */
   private double getHeight0(boolean min) {
      double height = getAxisHeight(min);
      AxisSpec spec = getScale().getAxisSpec();

      if(!isHorizontal()) {
         height = height + getPreferredSpacing(min) * (vlabels.length - 1);
      }
      else if(spec.getAxisSize() > 0 && isLabelVisible()) {
         return spec.getAxisSize();
      }

      return height;
   }

   /**
    * Get axis min or prefered width.
    * @param min true if get min width.
    */
   private double getAxisWidth(boolean min) {
      double width = 0;

      if(isHorizontal()) {
         if(containsLabel()) {
            double n = getMinLabelCount(min);
            width = n * getMaxLabelSize(true);
         }
         else {
            width = isLineVisible() ? line.getMinWidth() : 0;
         }
      }
      else {
         if(containsLabel()) {
            // we used preferred size for this regardless of min, keep it same for BC (47927).
            width = getMaxLabelSize(true);
            width += !isTickDown() ? Math.max(AxisLine.MAJOR_TICK_LENGTH, getLabelGap())
               : getLabelGap();

            // if the labels will be expanded, the expanded labels may
            // be wider than the widest label in the existing labels.
            // there is no way (in the current flow) to know the precise
            // labels so we just add one character to reserve space
            // if the expanded labels are one char more.
            if(labelExpanded()) {
               Font font = vlabels[0].getFont();
               FontMetrics fm = GTool.getFontMetrics(font);

               width += fm.charWidth('8') * 1.2;
            }
         }

         if(isLineVisible()) {
            width += line.getMinHeight();
         }
      }

      return Math.ceil(width);
   }

   /**
    * Check if the axis may (not completely deterministic) expand the labels
    */
   private boolean labelExpanded() {
      if(vlabels.length < 2 || vgraph == null || !"y".equals(axisType) ||
         getCoordinate() == null)
      {
         return false;
      }

      DataSet dataset = getCoordinate().getDataSet();

      if(dataset instanceof AbstractDataSet &&
         ((AbstractDataSet) dataset).getRowsProjectedForward() > 0)
      {
         return true;
      }

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof FormVO) {
            Rectangle2D bounds = vgraph.getVisual(i).getBounds();

            // 1000 is the max logical bounds
            if(bounds != null && (bounds.getY() < 0 || bounds.getY() > 1000)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Get axis min or preferred height.
    * @param min true if get min height.
    */
   private double getAxisHeight(boolean min) {
      double height = 0;

      if(isHorizontal()) {
         if(containsLabel()) {
            // @see getAxisWidth()
            height = getMaxLabelSize(false);
            height += !isTickDown() ? Math.max(AxisLine.MAJOR_TICK_LENGTH, getLabelGap())
               : getLabelGap();
         }

         if(isLineVisible()) {
            height += line.getMinHeight();
         }
      }
      else {
         if(containsLabel()) {
            double n = getMinLabelCount(min);
            height = n * getMaxLabelSize(false);
         }
         else {
            height = isLineVisible() ? line.getMinWidth() : 0;
         }
      }

      return Math.ceil(height);
   }

   // for continuous scale (time/linear), the labels can be skipped so for min
   // size, we reduce the number of labels that needs to be displayed.
   private double getMinLabelCount(boolean min) {
      return !min || getScale() instanceof CategoricalScale
         ? vlabels.length : Math.ceil(vlabels.length / 2.0);
   }

   /**
    * Get the bounds the labels are limited to.
    */
   private Rectangle2D getLabelCoordBounds() {
      Coordinate coord = vgraph.getCoordinate();
      Rectangle2D cbounds = coord.getCoordBounds();

      for(Coordinate pcoord = coord.getParentCoordinate();
          pcoord != null && cbounds == null;
          pcoord = pcoord.getParentCoordinate())
      {
         Dimension2D csize = (pcoord.getVGraph() != null) ? pcoord.getLayoutSize() : null;

         if(csize == null) {
            cbounds = pcoord.getCoordBounds();
         }
         else {
            cbounds = new Rectangle2D.Double(0, 0, csize.getWidth(), csize.getHeight());
         }
      }

      return cbounds;
   }

   /**
    * Get the max label gap. The labels and the gap together (the axis size) can't take
    * more than most of the chart, so the plot is kept. The label size is capped
    * independently of the gap and is not recalculated from the axis size, so this is safe
    * to use in the size calculation. The ceiling stays above the shipped default gaps for
    * ordinary charts, even with crowded labels (labels are capped at 2/3 of the chart).
    */
   private double getGapCeiling() {
      if(vgraph == null || vlabels == null || vlabels.length == 0) {
         return Double.MAX_VALUE;
      }

      Rectangle2D cbounds = getLabelCoordBounds();

      if(cbounds == null) {
         return Double.MAX_VALUE;
      }

      boolean hor = isHorizontal();
      double avail = (hor ? cbounds.getHeight() : cbounds.getWidth()) * MAX_AXIS_RATIO;

      // keep the legacy 2px gap at least
      return Math.max(2, avail - getMaxLabelSize(!hor));
   }

   /**
    * Get max label width.
    */
   private double getMaxLabelSize(boolean width) {
      // optimization
      if(vlabels.length > 0) {
         if(width) {
            if(maxLabelW >= 0) {
               return maxLabelW;
            }
         }
         else if(maxLabelH >= 0) {
            return maxLabelH;
         }
      }

      double maxSize = 0;
      Rectangle2D cbounds = getLabelCoordBounds();
      boolean hor = isHorizontal();

      for(int i = 0; i < vlabels.length; i++) {
         VLabel vlabel = vlabels[i];
         double lsize;

         if(cbounds != null) {
            Dimension2D maxsize = vlabel.getMaxSize();
            boolean truncate = getScale().getAxisSpec().isTruncate();
            double ratio = truncate ? 1 / 2.0 : 2 / 3.0;
            double[] box = getLabelLimitBox(vlabel);

            if(box != null) {
               maxsize.setSize(Math.min(maxsize.getWidth(), box[0]),
                               Math.min(maxsize.getHeight(), box[1]));
            }
            else if(hor) {
               double v = Math.max(50, cbounds.getHeight() * ratio);
               maxsize.setSize(maxsize.getWidth(), v);
            }
            else {
               double v = Math.max(50, cbounds.getWidth() * ratio);
               maxsize.setSize(v, maxsize.getHeight());
            }

            vlabel.setMaxSize(maxsize);
         }

         lsize = width ? vlabel.getPreferredWidth() : vlabel.getPreferredHeight();

         if(lsize > maxSize) {
            maxSize = lsize;
         }
      }

      if(vlabels.length > 0) {
         if(width) {
            maxLabelW = maxSize;
         }
         else {
            maxLabelH = maxSize;
         }
      }

      return maxSize;
   }

   /**
    * Get the width and height a label may take under the label length limit, or null if
    * there is no limit. A dimension the limit doesn't constrain is unbounded.
    */
   private double[] getLabelLimitBox(VLabel vlabel) {
      double limit = getScale().getAxisSpec().getLabelLimit();

      if(limit <= 0) {
         return null;
      }

      double rotation = vlabel.getTextSpec().getRotation();

      if(rotation % 180 == 0) {
         return new double[] { limit, Double.MAX_VALUE };
      }

      if((rotation + 90) % 180 == 0) {
         return new double[] { Double.MAX_VALUE, limit };
      }

      double angle = Math.toRadians(rotation);
      double lh = GTool.getFontMetrics(vlabel.getFont()).getHeight();
      double sin = Math.abs(Math.sin(angle));
      double cos = Math.abs(Math.cos(angle));

      // whole pixels so the label size matches the (integer) max size
      return new double[] { Math.ceil(limit * cos + lh * sin), Math.ceil(limit * sin + lh * cos) };
   }

   /**
    * Check if this axis contains label.
    */
   private boolean containsLabel() {
      return isLabelVisible() && vlabels != null && vlabels.length > 0;
   }

   /**
    * Layout the bounds, axisline, and axis labels.
    * @param bounds the bounding area for the axis.
    */
   @Override
   public void layout(Shape bounds) {
      //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      // Axis may be rotated to any angle, not horizontal nor vertical!!!
      // Don't assume it's at 0 or 90 degrees!!!
      //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

      Point2D pos1 = new Point2D.Double(0, 0);
      Point2D pos2 = new Point2D.Double(length, 0);
      pos1 = getScreenTransform().transform(pos1, null);
      pos2 = getScreenTransform().transform(pos2, null);

      boolean hor = pos1.getY() == pos2.getY();
      boolean ver = pos1.getX() == pos2.getX();

      line.setHeight(Math.min(getAxisSize(), line.getMinHeight0()));

      if(vlabels == null || vlabels.length == 0) {
         // Secondary axes have no labels but hold the grid lines. Extend them here
         // since the primary axis (which has labels) has no grid lines to extend.
         if(getScale().getAxisSpec().isFacetGrid() && getGridLineCount() > 0 &&
            getPrimaryAxis() instanceof DefaultAxis)
         {
            extendGridLines(((DefaultAxis) getPrimaryAxis()).getAxisSize(), hor);
         }

         return;
      }

      final double L_GAP = getLayoutLabelGap(hor);
      double width;
      double height;

      if(hor) {
         width = Math.abs(pos2.getX() - pos1.getX());
         height = getAxisSize();

         if(height == 0) {
            height = getPreferredHeight();
         }

         if(containsLabel()) {
            height -= L_GAP - 1;
         }

         if(isLineVisible()) {
            height = height - line.getHeight();
         }
      }
      else {
         width = getAxisSize();
         height = Math.abs(pos2.getY() - pos1.getY());

         if(width == 0) {
            width = getPreferredWidth();
         }

         if(containsLabel()) {
            width -= L_GAP;
         }

         if(isLineVisible()) {
            width -= line.getHeight();
         }
      }

      if(getScale().getAxisSpec().isFacetGrid()) {
         extendGridLines(hor ? height : width, hor);
      }

      Scale scale = getScale();
      double weightSum = 0;
      double wwidth = 0;
      double wheight = 0;
      CategoricalScale cscale = (scale instanceof CategoricalScale)
         ? (CategoricalScale) scale : null;

      double[] tlocs = getTickLocations(null);

      if(cscale != null) {
         for(int i = 0; i < vlabels.length; i++) {
            weightSum += cscale.getWeight(vlabels[i].getValue());
         }

         boolean weighted = weightSum != vlabels.length;
         // if only 1 label, fill the entire axis to avoid clipping.
         // adding 1 (padding on the sides) causes each label to be slightly narrow than the
         // available area, and leaves a small gap between them. (59991)
         weightSum += (cscale.isFill() || vlabels.length <= 1 || !weighted) ? 0 : 1;

         wheight = hor ? height : height / weightSum;
         wwidth = hor ? width / weightSum : width;
      }
      else {
         // when labelBetween and counts match, each label spans one interval between ticks
         int tickCount = scale.getAxisSpec().isLabelBetween()
            && vlabels.length > 0 && vlabels.length == tlocs.length
            ? vlabels.length : tlocs.length;
         wheight = hor ? height : height / tickCount;
         wwidth = hor ? width / tickCount : width;
      }

      double lx = 0;
      double ly = 0;

      // when labelBetween, position each label at the midpoint between adjacent ticks;
      // for the last label, the axis end (length) acts as the phantom boundary tick.
      if(scale.getAxisSpec().isLabelBetween() && tlocs.length >= 1
         && vlabels.length == tlocs.length)
      {
         double[] midTlocs = new double[vlabels.length];
         for(int i = 0; i < midTlocs.length - 1; i++) {
            midTlocs[i] = (tlocs[i] + tlocs[i + 1]) / 2.0;
         }
         midTlocs[midTlocs.length - 1] = (tlocs[tlocs.length - 1] + length) / 2.0;
         tlocs = midTlocs;
      }
      int skip = 0;

      // optimization, avoid layout large number of labels
      // if some labels are hidden by user, don't skip since the overlapping is
      // a little unpredictable in this case
      // a limited axis picks its own step from every label
      if(visibleLabelCnt > vlabels.length * 0.6 && pos1.distance(pos2) / visibleLabelCnt < 10 &&
         !isThinnedInOneStep())
      {
         skip = (int) (visibleLabelCnt * 10 / pos1.distance(pos2));
      }

      for(int i = 0; i < vlabels.length; i++) {
         if(!isMinTickVisible() && i == 0 ||
            !isMaxTickVisible() && i == vlabels.length - 1 ||
            labelMode == LABELS_NONE ||
            labelMode == LABELS_OUTERMOST && i < vlabels.length - 1 ||
            skip > 0 && i % skip != 0 && i < vlabels.length - 1)
         {
            vlabels[i].setZIndex(-1);
            updateAnchor(vlabels, i);
         }

         if(vlabels[i].getZIndex() < 0) {
            continue;
         }

         Object value = (vlabels[i] != null) ? vlabels[i].getValue() : vlabels[i].getLabel();
         double weight = (cscale != null) ? cscale.getWeight(value) : 1;
         double lwidth = hor ? wwidth * weight : wwidth;
         double lheight = hor ? wheight : wheight * weight;
         double[] limitBox = getLabelLimitBox(vlabels[i]);

         if(limitBox != null) {
            double lrot = vlabels[i].getTextSpec().getRotation();
            boolean angled = lrot % 90 != 0;

            // the text, not the slot, sets the width on a horizontal axis so labels can be
            // thinned instead of cut to the slot
            if(lrot % 180 == 0 || angled) {
               lwidth = hor ? limitBox[0] : Math.min(lwidth, limitBox[0]);
            }

            if((lrot + 90) % 180 == 0 || angled) {
               lheight = hor ? Math.min(lheight, limitBox[1]) : limitBox[1];
            }
         }

         Point2D lpos = line.getTickPosition(tlocs[i], 0);
         Point2D anchor;
         int placement;

         // since we don't clip string in label, make sure the bounds matches
         // the string drawing otherwise overlapping resolution won't work
         if(scale instanceof LinearScale) {
            lwidth = Math.max(lwidth, vlabels[i].getPreferredWidth());
         }

         if(hor) {
            lx = lpos.getX() - lwidth / 2;

            if(isLabelAbove()) {
               placement = GraphConstants.TOP;
               ly = lpos.getY();

               if(isLineVisible()) {
                  ly += line.getHeight();
               }

               if(isLineVisible() && isTickVisible() && isTickDown()) {
                  ly += AxisLine.MAJOR_TICK_LENGTH;
                  anchor = new Point2D.Double(
                     lpos.getX(), lpos.getY() + AxisLine.MAJOR_TICK_LENGTH);
               }
               else {
                  ly += L_GAP;
                  anchor = new Point2D.Double(lpos.getX(), lpos.getY() + L_GAP);
               }

               vlabels[i].setAlignmentY(GraphConstants.BOTTOM_ALIGNMENT);
            }
            else {
               placement = GraphConstants.BOTTOM;
               ly = lpos.getY() - lheight;

               if(isLineVisible()) {
                  ly -= line.getHeight();
               }

               vlabels[i].setAlignmentY(GraphConstants.TOP_ALIGNMENT);
               anchor = new Point2D.Double(lpos.getX(), lpos.getY() - L_GAP);
               ly -= L_GAP;
            }

            vlabels[i].setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         }
         else { // non-horizontal axis
            lx = lpos.getX();
            ly = lpos.getY() - lheight / 2;

            if(isLabelLeft()) {
               lx -= lwidth;

               if(isLineVisible() && isTickVisible() && isTickDown()) {
                  lx -= AxisLine.MAJOR_TICK_LENGTH;
                  anchor = new Point2D.Double(
                     lpos.getX() - AxisLine.MAJOR_TICK_LENGTH, lpos.getY());
               }
               else {
                  lx -= L_GAP;
                  anchor = new Point2D.Double(lpos.getX() - L_GAP, lpos.getY());
               }

               vlabels[i].setAlignmentX(GraphConstants.RIGHT_ALIGNMENT);
               placement = GraphConstants.LEFT;
            }
            else {
               if(isLineVisible() && isTickVisible() && isTickDown()) {
                  lx += AxisLine.MAJOR_TICK_LENGTH;
                  anchor = new Point2D.Double(
                     lpos.getX() + AxisLine.MAJOR_TICK_LENGTH, lpos.getY());
               }
               else {
                  lx += L_GAP;
                  anchor = new Point2D.Double(lpos.getX() + L_GAP, lpos.getY());
               }

               vlabels[i].setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
               placement = GraphConstants.RIGHT;
            }

            vlabels[i].setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
         }

         vlabels[i].setPosition(new Point2D.Double(lx, ly));
         vlabels[i].setSize(new DimensionD(lwidth, lheight));

         if(lwidth == 0) {
            vlabels[i].setZIndex(-1);
         }

         // make sure label is lined up at lpos after rotation
         setRotationOffset(vlabels[i], anchor, placement);

         // for outer axis of facet, clip the labels so it doesn't extend
         // into the other axis
         if(hor) {
            vlabels[i].setClipHeight(true);
         }
         else if(ver) {
            vlabels[i].setClipWidth(true);
         }
      }

      layoutBounds = bounds;
   }

   // extends the grid line across the axis labels.
   private void extendGridLines(double labelSize, boolean hor) {
      for(int k = 0; k < getGridLineCount(); k++) {
         GridLine gridLine = getGridLine(k);

         // createGridLines() always recreates GridLine instances (via removeAllGridLines) before
         // layout() is called, so facetGrid is false on the first pass. This guard is a safety net
         // against a hypothetical double-layout() call without an intervening createGridLines().
         if(gridLine.isFacetGrid()) {
            continue;
         }

         Shape lineShape = gridLine.getShape();

         if(lineShape instanceof Line2D) {
            Line2D line = (Line2D) lineShape;
            Line2D line2;

            if(hor) {
               // In VGraph Y-up coordinates the label area is always at smaller Y than the plot,
               // regardless of which side the labels appear on in screen space.
               line2 = new Line2D.Double(
                  line.getX1(), Math.min(line.getY1(), line.getY2()) - labelSize,
                  line.getX2(), Math.max(line.getY1(), line.getY2()));
            }
            else if(isLabelLeft()) {
               line2 = new Line2D.Double(
                  Math.min(line.getX1(), line.getX2()) - labelSize, line.getY1(),
                  Math.max(line.getX1(), line.getX2()), line.getY2());
            }
            else {
               line2 = new Line2D.Double(
                  Math.max(line.getX1(), line.getX2()) + labelSize, line.getY1(),
                  Math.min(line.getX1(), line.getX2()), line.getY2());
            }

            gridLine.setShape(line2);
            gridLine.setFacetGrid(true);
         }
      }
   }

   // layout on-demand phase
   public synchronized void prepareLabels() {
      if(prepared || layoutBounds == null) {
         return;
      }

      Shape bounds = layoutBounds;
      prepared = true;

      Scale scale = getScale();
      double[] ticks = scale.getTicks();
      boolean hor = isHorizontal();
      // outer is used to check for out of bounds conditions.
      Shape outer = bounds;

      // ignore top/bottom if moving horizontally
      if(isHorizontal()) {
         Rectangle2D b = outer.getBounds2D();
         // avoid move/remove an only label
         float gridW = (ticks.length < 2) ? 0 : getGridLineWidth(Coordinate.TOP_AXIS);

         outer = new Rectangle2D.Double(b.getX() + gridW, -10000000.0,
                                        b.getWidth() - gridW, 20000000.0);
      }
      else if(isVertical()) {
         Rectangle2D b = outer.getBounds2D();
         // avoid move/remove an only label
         float gridW = (ticks.length < 2) ? 0 : getGridLineWidth(Coordinate.LEFT_AXIS);

         outer = new Rectangle2D.Double(-10000000.0, b.getY() + gridW,
                                        20000000.0, b.getHeight() - gridW);
      }

      boolean moved = moveEndLabelsInside(outer); // if min/max moved
      dupTicks.clear();
      // remove duplicate timescale labels
      // remove duplicates before checking for overlapping otherwise non-duplicates
      // can be removed by removeOverlappedLabels (46989).
      removeDuplicateLabels();
      // move min/max to apply minPadding/maxPadding before removing overlapping labels.
      moveMinMaxInside(outer);

      if(isThinnedInOneStep()) {
         thinInOneStep(bounds, outer);
         return;
      }

      if(removeOverlappedLabels(bounds, hor, moved)) {
         redistributeSpace();
      }

      showCompactYears();
   }

   /**
    * Move the end labels inside the bounds.
    * @return true if the first or last label moved.
    */
   private boolean moveEndLabelsInside(Shape outer) {
      Scale scale = getScale();
      double[] ticks = scale.getTicks();
      double[] tlocs = getTickLocations(null);
      boolean moved = false; // if min/max moved
      CategoricalScale cscale = (scale instanceof CategoricalScale)
         ? (CategoricalScale) scale : null;

      // move labels inside bounds
      for(int i = 0; i < vlabels.length; i++) {
         // make sure the first is within the bounds
         if((i == 0 || i == ticks.length - 1) && ticks.length > 1) {
            double rotation = vlabels[i].getTextSpec().getRotation();
            Point2D lpos = line.getTickPosition(tlocs[i], 0);
            Point2D opos2;

            // if categorical, we allow the label to be moved toward the
            // next adjacent label location. For non-categorical, since we
            // can remove overlapping labels, we allow the end labels to be
            // moved all the way to the middle of the axis
            double v = (cscale != null || tlocs.length <= 2)
               ? (i == 0 ? tlocs[i + 1] : tlocs[i - 1])
               : tlocs[tlocs.length / 2];
            // distance to move
            double d = (rotation % 90 == 0) ? 1 : 2;
            opos2 = line.getTickPosition(tlocs[i] + (v - tlocs[i]) / d, 0);
            moved = moveInside(vlabels[i], vlabels, outer, lpos, opos2) || moved;
         }
      }

      // only one (not handled in the loop above)
      if(ticks.length == 1 && vlabels.length > 0) {
         Point2D lpos = line.getTickPosition(tlocs[0], 0);
         Point2D opos2 = line.getTickPosition(tlocs[0] + 1, 0);
         moveInside(vlabels[0], vlabels, outer, lpos, opos2);
      }

      AxisSpec spec = scale.getAxisSpec();

      if(spec.getAxisStyle() == AxisSpec.AXIS_CROSS) {
         moveOverlappedLabels();
      }

      return moved;
   }

   private static int year(Date date) {
      Calendar cal = Calendar.getInstance();
      cal.setTime(date);
      return cal.get(Calendar.YEAR);
   }

   /**
    * Put the year on the first shown label of each year. A label with the longer text that
    * overlaps a shown neighbour hides the neighbour (the year then moves on if the neighbour
    * carried it), until no shown labels overlap.
    */
   private void showCompactYears() {
      if(compactTexts == null || compactTexts.isEmpty() || layoutBounds == null) {
         return;
      }

      Set<VDimensionLabel> grown = Collections.newSetFromMap(new IdentityHashMap<>());
      boolean fromLast = isReadFromLast();

      while(true) {
         List<VDimensionLabel> shown = new ArrayList<>();
         boolean moved = false;
         Integer prevYear = null;

         for(int r = 0; r < vlabels.length; r++) {
            VDimensionLabel vl = vlabels[fromLast ? vlabels.length - 1 - r : r];

            if(vl.getZIndex() < 0) {
               continue;
            }

            shown.add(vl);
            String[] texts = compactTexts.get(vl);

            if(texts == null) {
               prevYear = vl.getValue() instanceof Date ? year((Date) vl.getValue()) : null;
               continue;
            }

            int year = year((Date) vl.getValue());

            if((prevYear == null || prevYear != year) && !texts[0].equals(vl.getLabel())) {
               vl.setLabel(texts[0]);
               grown.add(vl);
               moved = true;
            }

            prevYear = year;
         }

         if(moved) {
            layout(layoutBounds);
            // layout may put the end labels outside again
            moveMinMaxInside(layoutBounds);
         }

         VDimensionLabel victim = null;

         for(int i = 0; i < shown.size() - 1 && victim == null; i++) {
            VDimensionLabel a = shown.get(i);
            VDimensionLabel b = shown.get(i + 1);

            if((grown.contains(a) || grown.contains(b)) && overlaps(a, b)) {
               victim = grown.contains(b) && !grown.contains(a) ? a : b;
            }
         }

         if(victim == null) {
            break;
         }

         victim.setZIndex(-1);
      }
   }

   // overlap as the strict label thinning sees it, with the gap it keeps around a label
   private boolean overlaps(VDimensionLabel l1, VDimensionLabel l2) {
      boolean cat = !isContinuousLabels(getScale());
      Area a = getThinningArea(l1, l1.getDisplayLabel(), cat, thinningSpacing);
      a.intersect(getThinningArea(l2, l2.getDisplayLabel(), cat, thinningSpacing));
      return !a.isEmpty() && a.getBounds2D().getHeight() >= 1;
   }

   /**
    * Get the area a label takes when the thinning checks for overlap: its text box with the
    * gap kept around it, rotated with the text.
    */
   private static Area getThinningArea(VDimensionLabel vl, String[] dlabels, boolean cat,
                                       double spacing)
   {
      double angle = Math.toRadians(vl.getTextSpec().getRotation());
      Font font = vl.getFont();
      FontMetrics fm = GTool.getFontMetrics(font);
      double lwidth = GTool.stringWidth(dlabels, font);
      int lheight = fm.getHeight() * dlabels.length;
      Point2D p2d = vl.getTextPosition();
      float x = (float) p2d.getX();
      float y = (float) p2d.getY();
      double xadj = (!cat && angle == 0) ? 1 : 0;
      double yadj = (angle == 0) ? 1 : 2;

      // Bug #58430, determine how much of the spacing value should go towards xAdj and yAdj
      // based on the angle of the text. Compute the vertical percentage of the text and
      // multiply it by the spacing
      double ySpacing = spacing *
         (Math.abs(Math.sin(angle)) / ((Math.abs(Math.cos(angle)) + Math.abs(Math.sin(angle)))));
      xadj += spacing - ySpacing;
      yadj -= ySpacing;

      Rectangle2D box = new Rectangle2D.Double(x - xadj, y + yadj, lwidth + xadj * 2,
                                               lheight - yadj * 2);
      AffineTransform trans = AffineTransform.getRotateInstance(angle, x, y);
      return new Area(trans.createTransformedShape(box));
   }

   /**
    * Check if the labels are thinned in one step for the whole axis: an axis with a label
    * length limit that runs across or down the chart.
    */
   private boolean isThinnedInOneStep() {
      return getScale().getAxisSpec().getLabelLimit() > 0 && (isHorizontal() || isVertical());
   }

   /**
    * Check if the labels read from the last index, the top of a transposed axis: a reversed
    * category scale with a modern label property.
    */
   private boolean isReadFromLast() {
      Scale scale = getScale();
      AxisSpec spec = scale.getAxisSpec();
      return scale instanceof CategoricalScale && ((CategoricalScale) scale).isReversed() &&
         (spec.getLabelLimit() > 0 || spec.getCompactDates() != null);
   }

   /**
    * Show every k-th label from the first, with the smallest k that keeps every shown label
    * apart from the next. A shown label that starts a year (or a date anchor) after a hidden
    * one carries its full text, so the step is chosen with that text. The step comes from the
    * labels on their ticks; then the first and last shown labels slide inside the axis, and an
    * end label that would come too close to its neighbour is hidden, so end labels never change
    * the step. The labels are taken in reading order (see isReadFromLast()).
    */
   private void thinInOneStep(Shape bounds, Shape outer) {
      boolean hor = isHorizontal();
      List<Integer> cands = new ArrayList<>();

      for(int i = 0; i < vlabels.length; i++) {
         if(vlabels[i].getZIndex() >= 0) {
            cands.add(i);
         }
      }

      if(isReadFromLast()) {
         Collections.reverse(cands);
      }

      int n = cands.size();

      if(n == 0) {
         return;
      }

      Object[] entry = new Object[n];
      Object[] brief = new Object[n];
      Object[] full = new Object[n];
      Integer[] years = new Integer[n];
      boolean[] anchored = new boolean[vlabels.length];
      boolean varies = false;

      for(int j = 0; j < vlabels.length; j++) {
         String anchor = vlabels[j].getAnchor();
         anchored[j] = anchor != null && Abbreviation.isAnchor(vlabels[j].getLabel(), anchor);
      }

      for(int p = 0; p < n; p++) {
         VDimensionLabel vl = vlabels[cands.get(p)];
         String[] texts = compactTexts == null ? null : compactTexts.get(vl);
         entry[p] = vl.getLabel();
         years[p] = vl.getValue() instanceof Date ? year((Date) vl.getValue()) : null;

         if(texts != null) {
            full[p] = texts[0];
            brief[p] = texts[1];
         }
         else if(vl.getAnchor() != null) {
            full[p] = vl.getAnchor();
            brief[p] = anchored[cands.get(p)] ? vl.getAnchor() : entry[p];
         }
         else {
            full[p] = brief[p] = entry[p];
         }

         varies = varies || !Objects.equals(full[p], brief[p]);
      }

      double rotation = vlabels[cands.get(0)].getTextSpec().getRotation();
      boolean flat = rotation % 180 == 0;
      // stacked lines of flat text may share a little of their line boxes
      double inset = flat && !hor ? STACKED_LINE_INSET : 0;
      LabelBox[] briefBoxes;
      LabelBox[] fullBoxes;

      // the step comes from the labels on their ticks, as if no end label ran out of the axis
      if(varies) {
         setLabels(cands, brief);
         layoutOnTicks();
         briefBoxes = getLabelBoxes(cands, inset);
         setLabels(cands, full);
         layoutOnTicks();
         fullBoxes = getLabelBoxes(cands, inset);
      }
      else {
         layoutOnTicks();
         briefBoxes = fullBoxes = getLabelBoxes(cands, inset);
      }

      double[] dir = getLabelDirection(cands, hor);
      double[] sweep = new double[4];

      // flat labels side by side keep a gap along the axis
      if(flat && hor) {
         sweep[2] = ONE_STEP_GAP * dir[0];
         sweep[3] = ONE_STEP_GAP * dir[1];
      }
      // rotated labels keep a gap across their text, (line height + gap) / sin between slots
      else if(!flat) {
         double angle = Math.toRadians(rotation);
         sweep[0] = ROTATED_GAP * Math.sin(angle);
         sweep[1] = -ROTATED_GAP * Math.cos(angle);
         sweep[2] = -sweep[0];
         sweep[3] = -sweep[1];
      }

      int k = 1;

      while(k < n && !isApart(cands, k, years, anchored, briefBoxes, fullBoxes, dir, sweep)) {
         if(vgraph.isCancelled()) {
            return;
         }

         k++;
      }

      // the unit vector along the axis, and the start and end of the axis along it
      Rectangle2D area = getAdjustedBounds(outer).getBounds2D();
      double[] along = hor ? new double[] { dir[0] < 0 ? -1 : 1, 0 }
         : new double[] { 0, dir[1] < 0 ? -1 : 1 };
      double[] extent = hor ?
         (along[0] > 0 ? new double[] { area.getMinX(), area.getMaxX() }
            : new double[] { -area.getMaxX(), -area.getMinX() }) :
         (along[1] > 0 ? new double[] { area.getMinY(), area.getMaxY() }
            : new double[] { -area.getMaxY(), -area.getMinY() });
      List<Integer> shown = new ArrayList<>();

      for(int p = 0; p < n; p += k) {
         shown.add(p);
      }

      double[] slides = placeEnds(cands, shown, years, anchored, briefBoxes, fullBoxes, sweep,
                                  along, extent);
      boolean[] fulls = carriesFullText(cands, shown, years, anchored);
      boolean[] fullAt = new boolean[n];
      boolean[] showAt = new boolean[n];

      for(int s = 0; s < shown.size(); s++) {
         showAt[shown.get(s)] = true;
         fullAt[shown.get(s)] = fulls[s];
      }

      for(int p = 0; p < n; p++) {
         VDimensionLabel vl = vlabels[cands.get(p)];

         if(!showAt[p]) {
            vl.setZIndex(-1);
         }

         if(varies) {
            vl.setLabel(!showAt[p] ? entry[p] : fullAt[p] ? full[p] : brief[p]);
         }
      }

      if(varies) {
         layoutOnTicks();
      }

      slide(vlabels[cands.get(shown.get(0))], along, slides[0], area);

      if(shown.size() > 1) {
         slide(vlabels[cands.get(shown.get(shown.size() - 1))], along, slides[1], area);
      }
   }

   /**
    * Slide the first and last shown labels inside the axis. An end label that would then come
    * within the gap of its neighbour, or reach past it along the axis, is hidden, and the next
    * shown label becomes the end (the new first label takes the year).
    * @param shown the shown positions in the candidates, updated for hidden end labels.
    * @return how far the first and the last shown label slide along the axis.
    */
   private double[] placeEnds(List<Integer> cands, List<Integer> shown, Integer[] years,
                              boolean[] anchored, LabelBox[] briefBoxes, LabelBox[] fullBoxes,
                              double[] sweep, double[] along, double[] extent)
   {
      double first;
      LabelBox firstBox;

      while(true) {
         boolean[] fulls = carriesFullText(cands, shown, years, anchored);
         LabelBox box = (fulls[0] ? fullBoxes : briefBoxes)[shown.get(0)];
         first = Math.max(0, extent[0] - box.min(along[0], along[1]));

         // a lone label that runs past the end slides back
         if(shown.size() == 1 && first == 0) {
            first = Math.min(0, extent[1] - box.max(along[0], along[1]));
         }

         firstBox = box.shifted(along[0] * first, along[1] * first);

         if(shown.size() == 1) {
            break;
         }

         LabelBox next = (fulls[1] ? fullBoxes : briefBoxes)[shown.get(1)];

         // parallel rotated labels can be apart across the text in either order
         if(!firstBox.isNear(next, sweep) &&
            firstBox.center(along[0], along[1]) < next.center(along[0], along[1]))
         {
            break;
         }

         shown.remove(0);
      }

      double last = 0;

      while(shown.size() > 1) {
         boolean[] fulls = carriesFullText(cands, shown, years, anchored);
         int end = shown.size() - 1;
         LabelBox box = (fulls[end] ? fullBoxes : briefBoxes)[shown.get(end)];
         last = Math.min(0, extent[1] - box.max(along[0], along[1]));
         LabelBox prev = end == 1 ? firstBox :
            (fulls[end - 1] ? fullBoxes : briefBoxes)[shown.get(end - 1)];
         LabelBox slid = box.shifted(along[0] * last, along[1] * last);

         if(!prev.isNear(slid, sweep) &&
            prev.center(along[0], along[1]) < slid.center(along[0], along[1]))
         {
            break;
         }

         shown.remove(end);
         last = 0;
      }

      return new double[] { first, last };
   }

   /**
    * Move a label along the axis. A flat label's box is then kept inside the axis, as
    * moveInside() does.
    */
   private void slide(VDimensionLabel vl, double[] along, double by, Rectangle2D area) {
      if(by == 0) {
         return;
      }

      Point2D pos = vl.getPosition();
      vl.setPosition(new Point2D.Double(pos.getX() + along[0] * by, pos.getY() + along[1] * by));

      if(vl.getTextSpec().getRotation() != 0) {
         return;
      }

      Area vbox = new Area(vl.getBounds());
      vbox.subtract(new Area(area));
      Rectangle2D box = vbox.getBounds2D();

      if(box.getWidth() > 2 && box.getHeight() > 2) {
         String[] str = vl.getDisplayLabel();
         Point2D tpos = vl.getTextPosition();
         double y = tpos.getY();
         double h = vl.getPreferredHeight(str);

         if(GTool.isHorizontal(getScreenTransform())) {
            y = vl.getPosition().getY();
            h = vl.getSize().getHeight();
         }

         vl.setBounds(tpos.getX(), y, vl.getPreferredWidth(str), h);
      }
   }

   private void setLabels(List<Integer> cands, Object[] texts) {
      for(int p = 0; p < cands.size(); p++) {
         vlabels[cands.get(p)].setLabel(texts[p]);
      }
   }

   // lay the labels out on their ticks again, as the axis layout places them
   private void layoutOnTicks() {
      layout(layoutBounds);

      if(getScale().getAxisSpec().getAxisStyle() == AxisSpec.AXIS_CROSS) {
         moveOverlappedLabels();
      }
   }

   // the unit vector from the first label to the last
   private double[] getLabelDirection(List<Integer> cands, boolean hor) {
      Rectangle2D first = vlabels[cands.get(0)].getBounds();
      Rectangle2D last = vlabels[cands.get(cands.size() - 1)].getBounds();
      double dx = last.getCenterX() - first.getCenterX();
      double dy = last.getCenterY() - first.getCenterY();
      double len = Math.sqrt(dx * dx + dy * dy);

      if(len < 1e-9) {
         return hor ? new double[] { 1, 0 } : new double[] { 0, 1 };
      }

      return new double[] { dx / len, dy / len };
   }

   // whether each shown label (its position in the candidates) carries its full text
   private boolean[] carriesFullText(List<Integer> cands, List<Integer> shown, Integer[] years,
                                     boolean[] anchored)
   {
      boolean[] fulls = new boolean[shown.size()];
      boolean fromLast = isReadFromLast();
      int prev = -1;

      for(int s = 0; s < fulls.length; s++) {
         int p = shown.get(s);
         int idx = cands.get(p);
         VDimensionLabel vl = vlabels[idx];

         // the year shows on the first shown label and wherever it changes
         if(compactTexts != null && compactTexts.containsKey(vl)) {
            Integer prevYear = s == 0 ? null : years[shown.get(s - 1)];
            fulls[s] = prevYear == null || !prevYear.equals(years[p]);
         }
         // a hidden anchor moves to the next shown label
         else if(vl.getAnchor() != null) {
            fulls[s] = s == 0;
            // the labels after the previous shown one, up to this one
            int from = fromLast ? idx : prev + 1;
            int to = fromLast ? prev - 1 : idx;

            for(int j = from; j <= to && !fulls[s]; j++) {
               fulls[s] = anchored[j];
            }
         }

         prev = idx;
      }

      return fulls;
   }

   /**
    * Check if every k-th candidate, on its tick, keeps its gap to the labels after it.
    * @param sweep the two ends (x0, y0, x1, y1) of the gap a label is grown by.
    */
   private boolean isApart(List<Integer> cands, int k, Integer[] years, boolean[] anchored,
                           LabelBox[] briefBoxes, LabelBox[] fullBoxes, double[] dir,
                           double[] sweep)
   {
      List<Integer> shown = new ArrayList<>();

      for(int p = 0; p < cands.size(); p += k) {
         shown.add(p);
      }

      boolean[] fulls = carriesFullText(cands, shown, years, anchored);
      int m = fulls.length;
      LabelBox[] boxes = new LabelBox[m];
      double[] lo = new double[m];
      double[] hi = new double[m];
      // the lowest start along the axis of a label from here on
      double[] minLo = new double[m];

      for(int s = 0; s < m; s++) {
         boxes[s] = fulls[s] ? fullBoxes[shown.get(s)] : briefBoxes[shown.get(s)];
         lo[s] = boxes[s].min(dir[0], dir[1]);
         hi[s] = boxes[s].max(dir[0], dir[1]);
      }

      for(int s = m - 1; s >= 0; s--) {
         minLo[s] = s == m - 1 ? lo[s] : Math.min(lo[s], minLo[s + 1]);
      }

      // how far the gap reaches ahead along the axis
      double reach = Math.max(0, Math.max(sweep[0] * dir[0] + sweep[1] * dir[1],
                                          sweep[2] * dir[0] + sweep[3] * dir[1]));

      for(int a = 0; a < m - 1; a++) {
         for(int b = a + 1; b < m && minLo[b] <= hi[a] + reach; b++) {
            if(boxes[a].isNear(boxes[b], sweep)) {
               return false;
            }
         }
      }

      return true;
   }

   private LabelBox[] getLabelBoxes(List<Integer> cands, double inset) {
      LabelBox[] boxes = new LabelBox[cands.size()];

      for(int p = 0; p < boxes.length; p++) {
         boxes[p] = new LabelBox(vlabels[cands.get(p)], inset);
      }

      return boxes;
   }

   /**
    * The text box of a label, rotated with the text.
    */
   private static final class LabelBox {
      LabelBox(VDimensionLabel vl, double inset) {
         String[] dlabels = vl.getDisplayLabel();
         Font font = vl.getFont();
         double w = GTool.stringWidth(dlabels, font);

         // the text draws at its fractional advance, wider than the whole-pixel width
         for(String line : dlabels) {
            w = Math.max(w, font.getStringBounds(line, DRAWN_TEXT).getWidth());
         }
         double h = GTool.getFontMetrics(font).getHeight() * dlabels.length;
         double in = Math.min(inset, h / 4);
         Point2D pos = vl.getTextPosition();
         double angle = Math.toRadians(vl.getTextSpec().getRotation());
         double cos = Math.cos(angle);
         double sin = Math.sin(angle);
         double[][] corners = { { 0, in }, { w, in }, { w, h - in }, { 0, h - in } };

         for(int i = 0; i < 4; i++) {
            xs[i] = pos.getX() + corners[i][0] * cos - corners[i][1] * sin;
            ys[i] = pos.getY() + corners[i][0] * sin + corners[i][1] * cos;
         }
      }

      private LabelBox() {
      }

      // this box moved by (dx, dy)
      LabelBox shifted(double dx, double dy) {
         LabelBox box = new LabelBox();

         for(int i = 0; i < 4; i++) {
            box.xs[i] = xs[i] + dx;
            box.ys[i] = ys[i] + dy;
         }

         return box;
      }

      double min(double nx, double ny) {
         double v = Double.MAX_VALUE;

         for(int i = 0; i < 4; i++) {
            v = Math.min(v, xs[i] * nx + ys[i] * ny);
         }

         return v;
      }

      // the middle of this box along (nx, ny)
      double center(double nx, double ny) {
         return (min(nx, ny) + max(nx, ny)) / 2;
      }

      double max(double nx, double ny) {
         double v = -Double.MAX_VALUE;

         for(int i = 0; i < 4; i++) {
            v = Math.max(v, xs[i] * nx + ys[i] * ny);
         }

         return v;
      }

      /**
       * Check if the other box comes within the gap of this one: this box swept from one end
       * of the gap to the other meets the other box (separating axis test).
       */
      boolean isNear(LabelBox other, double[] sweep) {
         List<double[]> normals = new ArrayList<>();
         addNormals(normals);
         other.addNormals(normals);
         double sx = sweep[2] - sweep[0];
         double sy = sweep[3] - sweep[1];
         double slen = Math.sqrt(sx * sx + sy * sy);

         if(slen > 1e-9) {
            normals.add(new double[] { -sy / slen, sx / slen });
         }

         for(double[] n : normals) {
            double s0 = sweep[0] * n[0] + sweep[1] * n[1];
            double s1 = sweep[2] * n[0] + sweep[3] * n[1];
            double amin = min(n[0], n[1]) + Math.min(s0, s1);
            double amax = max(n[0], n[1]) + Math.max(s0, s1);
            double bmin = other.min(n[0], n[1]);
            double bmax = other.max(n[0], n[1]);

            // touching is apart
            if(amax <= bmin + 1e-6 || bmax <= amin + 1e-6) {
               return false;
            }
         }

         return true;
      }

      private void addNormals(List<double[]> normals) {
         for(int i = 0; i < 2; i++) {
            double ex = xs[i + 1] - xs[i];
            double ey = ys[i + 1] - ys[i];
            double len = Math.sqrt(ex * ex + ey * ey);

            if(len > 1e-9) {
               normals.add(new double[] { -ey / len, ex / len });
            }
         }
      }

      private final double[] xs = new double[4];
      private final double[] ys = new double[4];
   }

   /**
    * Set the rotation offset for rotated labels.
    */
   private void setRotationOffset(VLabel vlabel, Point2D anchor, int placement) {
      if(vlabel.getTextSpec().getRotation() != 0) {
         // ignore alignment in rotation
         vlabel.setAlignmentY(GraphConstants.BOTTOM_ALIGNMENT);
         vlabel.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
         Point2D offset = vlabel.getRotationOffset(anchor, placement);
         vlabel.setOffset(offset);
      }
   }

   /**
    * If axis in middle, flip the position of labels overlapping vos.
    */
   private void moveOverlappedLabels() {
      for(int i = 0; i < vlabels.length; i++) {
         if(isOverlapVO(vlabels[i])) {
            Point2D pos = vlabels[i].getPosition();

            flipLabel(vlabels[i]);

            // if still overlaps after flipped, restore
            if(isOverlapVO(vlabels[i])) {
               vlabels[i].setPosition(pos);
            }
         }
      }
   }

   /**
    * Check if label overlaps vos.
    */
   private boolean isOverlapVO(VLabel label) {
      Rectangle2D bounds = label.getTransformedBounds().getBounds2D();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable vo = vgraph.getVisual(i);
         Rectangle2D vbox = vo.getBounds();

         if(!(vo instanceof ElementVO) || vbox == null) {
            continue;
         }

         Rectangle2D ibox = vbox.createIntersection(bounds);

         if(ibox.getWidth() > 2 && ibox.getHeight() > 2) {
            return true;
         }
      }

      return false;
   }

   /**
    * Move the label to the other size of the axis.
    */
   private void flipLabel(VLabel label) {
      boolean hor = isHorizontal();
      Point2D pos = label.getPosition();
      Rectangle2D bounds = label.getTransformedBounds().getBounds2D();
      final double L_GAP = getLabelGap();

      if(hor) {
         double h = bounds.getHeight();

         if(isLabelAbove()) {
            pos = new Point2D.Double(pos.getX(), pos.getY() - h - L_GAP * 2);
         }
         else {
            pos = new Point2D.Double(pos.getX(), pos.getY() + h + L_GAP * 2);
         }
      }
      else {
         double w = bounds.getWidth();

         if(isLabelLeft()) {
            pos = new Point2D.Double(pos.getX() + w + L_GAP * 2, pos.getY());
         }
         else {
            pos = new Point2D.Double(pos.getX() - w - L_GAP * 2, pos.getY());
         }
      }

      label.setPosition(pos);
   }

   /**
    * Remove overlapped labels.
    */
   private boolean removeOverlappedLabels(Shape bounds, boolean hor, boolean moved) {
      if(bounds == null) {
         return false;
      }

      anchorChanged = false;
      List<VDimensionLabel> vlabelList = new ArrayList<>();
      List<Area> areaList = new ArrayList<>();
      // bounds for removing out of bounds labels
      Rectangle2D bounds2 = bounds.getBounds2D();
      double rotation = 0;

      if(hor) {
         // ignore vertical bounds for horizontal axis
         bounds2 = new Rectangle2D.Double(bounds2.getX(), bounds2.getY() - 1000,
                                          bounds2.getWidth(),
                                          bounds2.getHeight() + 2000);
      }
      else {
         // ignore horizontal bounds for vertical axis
         bounds2 = new Rectangle2D.Double(bounds2.getX() - 1000, bounds2.getY(),
                                          bounds2.getWidth() + 2000,
                                          bounds2.getHeight());
      }

      Scale scale = getScale();
      // max overlapping height/width. Categorical labels are impossible to
      // guess so we allow more overlapping to avoid removing the label
      // altogether if not absolutely necessary
      boolean cat = !isContinuousLabels(scale);

      int first = -1; // the index of first non-empty label
      int outsideIdx = -1; // the last outside label
      boolean foundInside = false; // found label that's not outside
      double spacing = getSpacing(scale, bounds2);
      thinningSpacing = spacing;

      for(int i = 0; i < vlabels.length; i++) {
         VDimensionLabel vl = vlabels[i];

         if(vl.getZIndex() < 0) {
            continue;
         }

         if(vgraph.isCancelled()) {
            return false;
         }

         rotation = vl.getTextSpec().getRotation();
         boolean outside = !vl.isContained(bounds2, 1, 1);

         // don't remove all labels even if the label may not all fit.
         // for using axis to show discrete measures in hybrid chart/table
         if(outside && !foundInside && i == vlabels.length - 1) {
            outside = false;
         }

         // remove out of bound labels
         if(outside) {
            // leave rotated label since it's better to let it clip than
            // missing a label entirely
            if(rotation % 90 == 0) {
               vl.setZIndex(-1);
               continue;
            }

            if(!foundInside) {
               outsideIdx = vlabelList.size();
            }
         }
         else {
            foundInside = true;
         }

         String[] dlabels = vl.getDisplayLabel();
         Area area = getThinningArea(vl, dlabels, cat, spacing);

         if(first < 0 && dlabels.length > 0) {
            first = i;
         }

         vlabelList.add(vl);
         areaList.add(area);
      }

      first = Math.max(0, first);

      boolean strict = scale.getAxisSpec().getLabelLimit() > 0;
      int oH = cat && !strict ? 3 : 1;
      int oW = cat && !strict ? 1 : 0;
      int pass = 0; // number of pass
      boolean adjusted = false;

      if(rotation != 0 && !strict) {
         int tmp = oW;
         // avoid removing unecessarily. (57512)
         oW = Math.max(oH, 2);
         oH = tmp;
      }

      final double maxWeight = (scale instanceof CategoricalScale)
         ? ((CategoricalScale) scale).getMaxWeight() : 1;
      double currWeight = 1; // don't remove a label that's greater than this weight
      // weight can be very large, increase by 1 will result in large number of iterations
      double weightIncr = Math.max(1, maxWeight / areaList.size());
      boolean removed = false;
      // don't remove alternate labels if weighted or ticks are not for same intervals (TimeScale),
      // remove individual labels.
      boolean uniformTicks = scale.isUniformInterval();

      while(true) {
         boolean changed = false;

         for(int i = 0; i < areaList.size() - 1; i++) {
            if(vgraph.isCancelled()) {
               return false;
            }

            VDimensionLabel vlabel = vlabelList.get(i);
            double weight = (scale instanceof CategoricalScale)
               ? ((CategoricalScale) scale).getWeight(vlabel.getLabel()) : 1;

            // if weighted, start from the lesser weight labels and leave the labels with
            // higher weight in place
            if(weight > currWeight) {
               continue;
            }

            Area a1 = areaList.get(i);
            a1 = (Area) a1.clone();
            a1.intersect(areaList.get(i + 1));
            Rectangle2D a1box = a1.getBounds2D();

            // don't need to keep last for linear or categorical scale, which doesn't convey
            // special meaning.
            boolean keepLast = scale instanceof TimeScale;
            boolean isKeepingLast = keepLast && i == vlabelList.size() - 2 ||
               // if only min and max labels are left on a linear scale, allow some more
               // overlapping to keep the max label since the value can't be inferred.
               pass == 0 && i == 0 && areaList.size() == 2 && !cat;

            // if we didn't find overlapping at the first position, we are not
            // going to remove alternating labels. So only individual labels
            // will be dropped. In this case missing one label in the middle
            // looks like an error to user. So we allow more overlapping to
            // reduce the chance of missing individual labels.
            // for continuous scale, the values can be inferred so it's preferrable to
            // keep the values separate and readable than allowing any overlapping.
            if(pass == 0 && !changed && i > first + 1 && !adjusted && cat && !strict) {
               oW += 2;
               oH++;
               adjusted = true;
            }
            // when the current label is (later) removed to preserve the last label, a hole will
            // be left in the spot, which looks pretty bad. here we increase the allowed
            // overlapping so the label is kept instead of creating the hole even if
            // there is a very slight overlapping. (57431, 57443)
            else if(!adjusted && !strict && isKeepingLast &&
                    vlabel.getTextSpec().getRotation() != 0)
            {
               oW += 2;
               oH += 2;
               adjusted = true;
            }

            // text normally doesn't fill the text height so allow very small
            // overlapping of the bounds
            if(a1.isEmpty() || a1box.getWidth() < oW || a1box.getHeight() < oH) {
               continue;
            }

            // if we find overlapping, and there are labels that are outside
            // of the axis area, we remove those first. Keeping outside labels
            // while removing overlapping labels inside the axis isn't what
            // most people expect.
            if(outsideIdx > 0 && !changed) {
               // always keep the first label
               for(int k = 1; k <= outsideIdx; k++) {
                  removeLabel(1, vlabelList, areaList);
                  removed = true;
               }

               outsideIdx = -1;
               i = 1;
               continue;
            }

            // this if() block removes alternate labels from the 2nd label onward
            if(!changed && i <= first + 1 && first + 1 < areaList.size() && uniformTicks) {
               // remove even spaced labels results in better spacing in
               // remaining labels. for linear scale, if the min/max is moved
               // we remove the min instead starting at the second label
               // since the spacing between the min and the second label is
               // not even in this case
               /* not showing min is a little disorienting, reconsider later
               if(first == 0 && moved && !cat) {
                  first = -1;
               }
               */

               // preserve the last label. (53091)
               for(int k = first + 1; k < areaList.size() - (keepLast ? 1 : 0); k++) {
                  removeLabel(k, vlabelList, areaList);
               }
            }
            // in the old logic (else part), the next label is removed instead of the current.
            // that preserves the label in the front, which is nicer.
            // for weighted axis, we remove the smallest label so the current one is removed
            // instead.
            // try to preserve last label so the end of the range is visible (otherwise
            // it may be hard to tell). (53091)
            else if(maxWeight > 1 || isKeepingLast) {
               removeLabel(i, vlabelList, areaList);
            }
            // remove overlapped label
            else {
               removeLabel(i + 1, vlabelList, areaList);
            }

            removed = true;
            changed = true;
            i--;
         }

         pass++;

         // no change?
         if(!changed) {
            // no label removed, increase the allowed weight
            if(currWeight < maxWeight) {
               currWeight += weightIncr;
            }
            // no label removed and reached max weight, stop
            else {
               break;
            }
         }
      }

      // if vlabel text is changed, need to relayout to make sure the labels are properly placed.
      if(anchorChanged) {
         layout(layoutBounds);
         // layout may cause label to be outside, need to move again.
         moveMinMaxInside(bounds);
      }

      return removed;
   }

   // get spacing between labels
   private double getSpacing(Scale scale, Rectangle2D bounds) {
      // only calculate for time scale since numeric scale has a few ticks, and categorical
      // labels shouldn't be skipped unless absolutely necessary.
      if(scale instanceof TimeScale && vlabels.length > 10) {
         boolean hor = scale.getAxisSpec().getTextSpec().getRotation() == 0;
         double largestSize;
         FontMetrics fm = GTool.getFontMetrics(vlabels[0].getFont());

         if(hor) {
            // use longest string to calculate max size for performance since this calculation
            // is approximation and accurate value is not necessary.
            String longestLine = Arrays.stream(vlabels)
               .flatMap(label -> Stream.of(label.getDisplayLabel()))
               .reduce((s1, s2) -> s1.length() > s2.length() ? s1 : s2).orElse("");
            largestSize = fm.stringWidth(longestLine);
         }
         else {
            largestSize = fm.getHeight();
         }

         double visibleCnt = (hor ? bounds.getWidth() : bounds.getHeight()) / largestSize;

         // only increase spacing if can large number of labels.
         if(visibleCnt > 20) {
            return Math.min(scale.getAxisSpec().getMaxLabelSpacing(), visibleCnt / 20);
         }
      }

      return 0;
   }

   private void moveMinMaxInside(Shape bounds) {
      if(vlabels.length > 1) {
         bounds = getAdjustedBounds(bounds);

         double[] tlocs = getTickLocations(null);
         Point2D lpos = line.getTickPosition(tlocs[0], 0);
         Point2D opos2 = line.getTickPosition(tlocs[1], 0);
         moveInside(vlabels[0], vlabels, bounds, lpos, opos2);

         lpos = line.getTickPosition(tlocs[tlocs.length - 1], 0);
         opos2 = line.getTickPosition(tlocs[tlocs.length - 2], 0);
         moveInside(vlabels[vlabels.length - 1], vlabels, bounds, lpos, opos2);
      }
   }

   // apply min/max padding.
   private Shape getAdjustedBounds(Shape bounds) {
      int minPadding = getScale().getAxisSpec().getMinPadding();
      int maxPadding = getScale().getAxisSpec().getMaxPadding();

      if(minPadding != 0 || maxPadding != 0) {
         Rectangle box = bounds.getBounds();

         if(isHorizontal()) {
            box = new Rectangle(box.x + minPadding, box.y,
                                box.width - minPadding - maxPadding, box.height);
         }
         else if(isVertical()) {
            box = new Rectangle(box.x, box.y + minPadding,
                                box.width, box.height - minPadding - maxPadding);
         }

         bounds = box;
      }

      return bounds;
   }

   // check if the labels on the axis is continuous where a missing label can be inferred
   // by the labels next to it
   private static boolean isContinuousLabels(Scale scale) {
      if(!(scale instanceof CategoricalScale)) {
         return true;
      }

      Object[] values = scale.getValues();
      Object first = Arrays.stream(values).filter(a -> a != null).findFirst().orElse(null);
      // if not a number or date, categorical scale is not continuous.
      return first instanceof Number || first instanceof Date;
   }

   /**
    * Remove a label from list.
    */
   private void removeLabel(int idx, List<VDimensionLabel> labels, List<Area> areas) {
      AxisSpec spec = getScale().getAxisSpec();
      VDimensionLabel vlabel = labels.get(idx);

      // if the removed label is a duplicate timescale label, we mark it
      // so the tick is not displayed
      if(!spec.isAllTicks() && spec.getTextSpec().getFormat() != null &&
         getScale() instanceof TimeScale)
      {
         Object lbl = vlabel.getText();
         Object prev = (idx > 0) ? labels.get(idx - 1).getText() : null;
         Object next = (idx < labels.size() - 1) ? labels.get(idx + 1).getText() : null;

         if(lbl != null && (lbl.equals(prev) || lbl.equals(next))) {
            dupTicks.set(idx);
         }
      }

      updateAnchor(labels.toArray(new VDimensionLabel[0]), idx);

      labels.get(idx).setZIndex(-1);
      labels.remove(idx);
      areas.remove(idx);
   }

   // if the label at idx is hidden, and it's an anchor label (e.g. 2021), make sure the
   // next label is changed to anchor (e.g. Feb -> 2021) so year is not lost on axis.
   private void updateAnchor(VDimensionLabel[] labels, int idx) {
      VDimensionLabel vlabel = labels[idx];

      // if an anchor (e.g. 2021) is removed, should make the next label to show the anchor
      // (e.g. Feb -> 2021) so year will not be lost. (53040)
      if(Abbreviation.isAnchor(vlabel.getLabel(), vlabel.getAnchor())) {
         // the next label in reading order
         int step = isReadFromLast() ? -1 : 1;

         for(int i = idx + step; i >= 0 && i < labels.length; i += step) {
            if(labels[i].getZIndex() >= 0) {
               Abbreviation.setAnchor(labels[i], labels[i].getAnchor());
               anchorChanged = true;
               break;
            }
         }
      }
   }

   /**
    * Remove duplicate date labels.
    */
   private void removeDuplicateLabels() {
      // in the case format causes two ticks to format to the same string,
      // there is no point including both ticks. A typical use case is
      // display day interval and set the format to 'yyyy'. It's better
      // to just show one tick for each year vs creating a lot of same
      // year label for each day.

      Scale scale = getScale();
      AxisSpec spec = scale.getAxisSpec();

      if(!(scale instanceof TimeScale) || spec.getTextSpec().getFormat() == null) {
         return;
      }

      List<VDimensionLabel> labels = Arrays.stream(vlabels)
         .filter(label -> label.getZIndex() >= 0)
         .collect(Collectors.toList());

      // Map each label to its index in vlabels (== its index in scale.getTicks()),
      // so that dupTicks bits correspond to the original tick array positions.
      Map<VDimensionLabel, Integer> tickIndex = new IdentityHashMap<>();

      for(int k = 0; k < vlabels.length; k++) {
         tickIndex.put(vlabels[k], k);
      }

      for(int i = 1; i < labels.size(); i++) {
         if(labels.get(i).getText().equals(labels.get(i - 1).getText())) {
            if(!spec.isAllTicks()) {
               dupTicks.set(tickIndex.get(labels.get(i)));
            }

            labels.get(i).setZIndex(-1);
            labels.remove(i--);
         }
      }
   }

   // if labels are removed, redistribute space to fill in the gaps so labels won't be
   // truncated if there is empty space between the labels.
   private void redistributeSpace() {
      if(!isHorizontal() || getScale().getAxisSpec().getTextSpec().getRotation() != 0) {
         return;
      }

      double gap = getLabelGap();
      List<VDimensionLabel> visLabels = Arrays.stream(vlabels)
         .filter(v -> v.getZIndex() >= 0).collect(Collectors.toList());
      int cnt = visLabels.size();

      if(cnt > 1) {
         for(int i = 0; i < visLabels.size(); i++) {
            VDimensionLabel vlabel = visLabels.get(i);
            Rectangle2D bounds = vlabel.getBounds();
            double prefW = vlabel.getPreferredWidth();

            // labels may not be evenly spaced so we need to check for previous and next
            // label to calculate the space to expand.

            if(!vlabel.isTruncated()) {
               // not truncated, no need to increase size.
               continue;
            }
            else if(i == 0) {
               Rectangle2D next = visLabels.get(1).getBounds();
               double availableSpace = next.getX() - bounds.getMaxX() - gap;
               double adj = getMinMaxRedistribution(availableSpace, bounds.getWidth(), false);

               if(adj > 0) {
                  // expanding too much will cause label to shift away from its tick.
                  double w = Math.min(prefW, bounds.getWidth() + adj);
                  vlabel.setBounds(bounds.getX(), bounds.getY(), w, bounds.getHeight());
               }
            }
            else if(i == visLabels.size() - 1) {
               Rectangle2D prev = visLabels.get(i - 1).getBounds();
               double availableSpace = bounds.getX() - prev.getMaxX() - gap;
               double adj = getMinMaxRedistribution(availableSpace, bounds.getWidth(), true);

               if(adj > 0) {
                  adj = Math.min(bounds.getWidth() + adj, prefW) - bounds.getWidth();
                  vlabel.setBounds(bounds.getX() - adj, bounds.getY(),
                                   bounds.getWidth() + adj, bounds.getHeight());
               }
            }
            else {
               Rectangle2D prev = visLabels.get(i - 1).getBounds();
               Rectangle2D next = visLabels.get(i + 1).getBounds();
               double xadj = bounds.getX() - prev.getMaxX() - gap;
               double wadj = (next.getX() - bounds.getMaxX() - gap) / 2;
               // keep it centered
               double adj = Math.min(xadj, wadj);

               if(adj > 0) {
                  // limit to prefW
                  adj = (Math.min(bounds.getWidth() + adj * 2, prefW) - bounds.getWidth()) / 2;
                  vlabel.setBounds(bounds.getX() - adj, bounds.getY(),
                                   bounds.getWidth() + adj * 2, bounds.getHeight());
               }
            }
         }
      }
   }

   private double getMinMaxRedistribution(double availableSpace, double currentW, boolean last) {
      // first label of time scale is more important than others.
      if(getScale() instanceof TimeScale) {
         return availableSpace;
      }
      // moving categorical label too much may plae it under wrong tick.
      else if(getScale() instanceof CategoricalScale) {
         // shifting last label left cause it to be confused with the previous tick.
         // expanding right for first label suffers less of this problem.
         return last ? 0 : Math.min(availableSpace / 2, currentW / 2);
      }

      return availableSpace / 2;
   }

   /**
    * Get the ticks for displaying on axis.
    */
   @Override
   public double[] getTicks() {
      double[] ticks = super.getTicks();
      double[] nticks = new double[ticks.length - dupTicks.cardinality()];

      for(int i = 0, k = 0; i < ticks.length; i++) {
         if(!dupTicks.get(i)) {
            nticks[k++] = ticks[i];
         }
      }

      return nticks;
   }

   /**
    * Check if this axis is a horizontal line with the specified transformation.
    */
   public boolean isHorizontal() {
      return GTool.isHorizontal(getScreenTransform());
   }

   /**
    * Check if this axis is a vertical line with the specified transformation.
    */
   public boolean isVertical() {
      return GTool.isVertical(getScreenTransform());
   }

   /**
    * Check if the label is toward the left of the line.
    */
   private boolean isLabelLeft() {
      Point2D pos1 = new Point2D.Double(0, 0);
      Point2D pos2 = new Point2D.Double(0, -100);
      pos1 = getScreenTransform().transform(pos1, null);
      pos2 = getScreenTransform().transform(pos2, null);

      return pos2.getX() < pos1.getX();
   }

   /**
    * Check if the label is above the line.
    */
   private boolean isLabelAbove() {
      Point2D pos1 = new Point2D.Double(0, 0);
      Point2D pos2 = new Point2D.Double(0, -100);
      pos1 = getScreenTransform().transform(pos1, null);
      pos2 = getScreenTransform().transform(pos2, null);

      return pos2.getY() > pos1.getY();
   }

   /**
    * Get line.
    */
   @Override
   public AxisLine getAxisLine() {
      return line;
   }

   /**
    * Get max height among label's min height.
    */
   public double getLabelMinHeight() {
      return getMaxLabelSize(false) + getLabelGapForSize(true, false) / 2;
   }

   /**
    * Get max width among label's min width.
    */
   public double getLabelMinWidth() {
      return getMaxLabelSize(true) + getLabelGapForSize(true, true) / 2;
   }

   /**
    * Get max width among label's preferred height.
    */
   public double getLabelPreferredHeight() {
      return getMaxLabelSize(false) + getLabelGapForSize(false, false) / 2;
   }

   /**
    * Get max width among label's preferred width.
    */
   public double getLabelPreferredWidth() {
      return getMaxLabelSize(true) + getLabelGapForSize(false, true) / 2;
   }

   /**
    * Get the label gap.
    * @param min to get the minimum gap, false to get preferred gap.
    */
   private double getLabelGapForSize(boolean min, boolean width) {
      if(width && !isHorizontal() || !width && isHorizontal()) {
         int gap = getScale().getAxisSpec().getLabelGap();

         if(gap != 0) {
            // multiply by 2 to counter the / 2 in sizing calculation
            return Math.min(gap, getGapCeiling()) * 2.0;
         }
      }

      // this is to keep backward compatibility before labelGap is added
      return getPreferredSpacing(min);
   }

   // Get spacing between labels
   private double getPreferredSpacing(boolean min) {
      double rotation = getScale().getAxisSpec().getTextSpec().getRotation();

      // when the labels are rotated, we don't need to add a gap since chars
      // rarely reaches the top/bottom of the char space.
      if(rotation != 0) {
         return 0;
      }

      return min ? 1 : 2;
   }

   /**
    * Get the spacing between label and axis line.
    */
   private double getLabelGap() {
      int gap = getScale().getAxisSpec().getLabelGap();
      // use 2 since it was the hardcoded value before we added label-gap to axisSpec
      return gap == 0 ? 2 : Math.min(gap, getGapCeiling());
   }

   /**
    * Get the label gap used in layout. If a fixed or squeezed axis size can't hold the
    * gap and the labels, the gap is reduced (down to the legacy 2px) so the labels are
    * not pushed out of the axis.
    */
   private double getLayoutLabelGap(boolean hor) {
      double gap = getLabelGap();

      if(!containsLabel()) {
         return gap;
      }

      double asize = getAxisSize();

      // for auto size the gap is already limited by getLabelGap, and the preferred
      // size can't be used here since it is calculated from the gap
      if(asize == 0) {
         return gap;
      }

      double lineSize = isLineVisible() ? line.getHeight() : 0;
      // layout() takes L_GAP - 1 off a horizontal axis
      double room = asize - lineSize - getMaxLabelSize(!hor) + (hor ? 1 : 0);
      return Math.min(gap, Math.max(Math.min(gap, 2), room));
   }

   /**
    * Get axis type, 'x' or 'y'.
    */
   public String getAxisType() {
      return axisType;
   }

   /**
    * Set axis type.
    * @param axisType 'x' or 'y' for x and y axis respectively.
    */
   public void setAxisType(String axisType) {
      this.axisType = axisType;

      for(int i = 0; vlabels != null && i < vlabels.length; i++) {
         vlabels[i].setAxisType(axisType);
      }
   }

   /**
    * Check whether to center label between ticks.
    * @hidden
    */
   public boolean isCenterLabel() {
      return centerLabel;
   }

   /**
    * Set whether to center label between ticks.
    * @hidden
    */
   public void setCenterLabel(boolean center) {
      this.centerLabel = center;
   }

   /**
    * Set which labels are shown: all, none, or only the last (outermost) one.
    */
   public void setLabelMode(int labelMode) {
      this.labelMode = labelMode;
   }

   /**
    * Get which labels are shown.
    */
   public int getLabelMode() {
      return labelMode;
   }

   /**
    * Get axis labels.
    */
   @Override
   public VLabel[] getLabels() {
      return vlabels;
   }

   /**
    * Gets the bounds of this visual in the form of a Rectangle2D object.
    * The bounds specify this visual's width, height, and location relative to
    * the whole graph.
    */
   @Override
   public Rectangle2D getBounds() {
      if(bounds != null) {
         return bounds;
      }

      double yfactor = GTool.getScaleFactor(getScreenTransform(), 90);
      double size = this.size * yfactor;
      Rectangle2D bounds = new Rectangle2D.Double(0, -size, length, size);

      return getScreenTransform().createTransformedShape(bounds).getBounds2D();
   }

   /**
    * Set the outer bounds of the axis. This can be used to override the
    * calculated bounds. It doesn't affect the position and painting of the
    * axis.
    */
   public void setBounds(Rectangle2D bounds) {
      this.bounds = bounds;
   }

   /**
    * Abbreviate the value label.
    * @return the string formatted from the value, and abbreviated when
    * appropriate.
    */
   private Abbreviation abbreviate(Object val, Object prev, TextSpec spec) {
      Format fmt = spec.getFormat();

      if(fmt == null || val == null) {
         return new Abbreviation(val, null);
      }

      if(fmt instanceof DateFormat) {
         return abbreviateDate(val, prev, spec);
      }
      else if(fmt instanceof NumberFormat) {
         return abbreviateNumber(val, prev, spec);
      }

      return new Abbreviation(spec.getFormat().format(val), null);
   }

   /**
    * Abbreviate string if the current value shares the same prefix as the
    * previous value.
    */
   private Abbreviation abbreviateDate(Object val, Object prev, TextSpec spec) {
      Format fmt = spec.getFormat();
      String str = null;
      String pattern = null;

      if(fmt instanceof SimpleDateFormat) {
         pattern = ((SimpleDateFormat) fmt).toPattern();
      }

      str = formatWithCache(fmt, pattern, val);

      if(pattern == null) {
         return new Abbreviation(str, null);
      }

      // only abbreviate if the date starts with year
      if(!pattern.startsWith("yy")) {
         return new Abbreviation(str, null);
      }

      Scale scale = getScale();
      // a category axis of dates only drops the repeated prefix
      boolean mmm = scale instanceof TimeScale &&
         ((TimeScale) scale).getType() == TimeScale.MONTH && pattern.endsWith("MMM");
      String pstr = formatWithCache(fmt, pattern, prev);

      // if two date labels are identical, ignore the subsequent labels
      if(pstr.equals(str)) {
         return new Abbreviation(null, null);
      }

      // return part after the common prefix
      if(prev instanceof Date) {
         int len = Math.min(str.length(), pstr.length());
         int idx = 0;

         // find the first different char
         for(; idx < len; idx++) {
            if(str.charAt(idx) != pstr.charAt(idx)) {
               idx--;
               break;
            }
            // we only skip the first part so we don't end up with:
            // 2012-02-01    02     03    04  ...  03-01
            else if(!Character.isUnicodeIdentifierPart(str.charAt(idx))) {
               break;
            }
         }

         if(idx >= str.length()) {
            idx = str.length() - 1;
         }

         // going backward to find the first non-alphanumeric separator
         for(; idx > 0; idx--) {
            if(!Character.isUnicodeIdentifierPart(str.charAt(idx))) {
               idx++;
               break;
            }
         }

         if(idx > 0) {
            return new Abbreviation(str.substring(idx), mmm ? str.substring(0, idx - 1) : str);
         }
      }

      // doing this only makes sense for case like: 2009  Feb  Mar. But it
      // could be confusing for case like 2009-01  02  03, which actually
      // means 2009-01-01  01-02  01-03.
      // first value, drop the last part, e.g. 2009 Jan -> 2009, and the end
      // result is: 2008  Feb ...  Dec   2009  Feb   Mar
      if(scale instanceof TimeScale && mmm && (prev == null || !isSameYear(prev, val))) {
         for(int i = str.length() - 1; i > 0; i--) {
            if(!Character.isUnicodeIdentifierPart(str.charAt(i))) {
               return new Abbreviation(str.substring(0, i), str.substring(0, i));
            }
         }
      }

      return new Abbreviation(str, str);
   }

   private String formatWithCache(Format fmt, String pattern, Object val) {
      if(pattern == null) {
         return GTool.format(fmt, val);
      }

      Tuple key = new Tuple(pattern, val);
      Map cache = GTool.getTopCoordinate(vgraph.getCoordinate()).getCoordCache();
      Object str = cache.get(key);

      if(str == null) {
         cache.put(key, str = GTool.format(fmt, val));
      }

      return (String) str;
   }

   /**
    * Check if the dates are in the same year.
    */
   private boolean isSameYear(Object v1, Object v2) {
      if(!(v1 instanceof Date) || !(v2 instanceof Date)) {
         return false;
      }

      Calendar c1 = new GregorianCalendar();
      Calendar c2 = new GregorianCalendar();

      c1.setTime((Date) v1);
      c2.setTime((Date) v2);

      return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR);
   }

   /**
    * Abbreviate string if the current value shares the same currency prefix
    * or percent suffix.
    */
   private Abbreviation abbreviateNumber(Object val, Object prev, TextSpec spec) {
      Format fmt = spec.getFormat();
      String str = GTool.format(fmt, val);

      if(prev == null) {
         return new Abbreviation(str, null);
      }

      String pstr = GTool.format(fmt, prev);

      if(str.length() < 2 || pstr.length() < 2) {
         return new Abbreviation(str, null);
      }

      char c0 = str.charAt(0);
      char c1 = str.charAt(1);

      // drop $ from $###
      if(c0 == pstr.charAt(0) && !Character.isDigit(c0) &&
         c0 != '-' && c0 != '(' && Character.isDigit(c1))
      {
         return new Abbreviation(str.substring(1), str);
      }

      if(str.endsWith("%") && pstr.endsWith("%")) {
         return new Abbreviation(str.substring(0, str.length() - 1), str);
      }

      return new Abbreviation(str, str);
   }

   /**
    * Get the grid line width of the facet.
    * @param axis the top or left axis.
    */
   private float getGridLineWidth(int axis) {
      Coordinate top = GTool.getTopCoordinate(vgraph.getCoordinate());

      if(top instanceof FacetCoord) {
         RectCoord outer = (RectCoord) ((FacetCoord) top).getOuterCoordinate();
         Scale scale = outer.getScaleAt(axis);

         return GTool.getLineWidth(scale.getAxisSpec().getGridStyle());
      }

      return 0;
   }

   @Override
   public void removeAllLabels() {
      for(VDimensionLabel label : vlabels) {
         removeVisual(label);
      }

      vlabels = new VDimensionLabel[0];
   }

   private double length;
   private GridLine zeroLine;
   private boolean zeroGridLineHidden = false;
   private double size;
   private AxisLine line;
   private VDimensionLabel[] vlabels;
   // full and brief text of the compact date labels
   private Map<VDimensionLabel, String[]> compactTexts; // set by createVisuals
   private int visibleLabelCnt;
   private String axisType;
   private boolean centerLabel;
   private Rectangle2D bounds;
   // removed duplicate timescale labels
   private BitSet dupTicks = new BitSet();
   private boolean prepared;
   private transient Shape layoutBounds = null;
   // the spacing the label thinning kept, for the year move
   private transient double thinningSpacing;
   private boolean anchorChanged = false;
   private int labelMode = LABELS_ALL;
   private static final double MAX_AXIS_RATIO = 0.9;
   // the room kept between flat labels side by side on an axis with a label limit
   private static final double ONE_STEP_GAP = 4;
   // measures text as it draws, at fractional advances
   private static final java.awt.font.FontRenderContext DRAWN_TEXT =
      new java.awt.font.FontRenderContext(null, true, true);
   // the room kept across the text between rotated labels on an axis with a label limit
   private static final double ROTATED_GAP = 2;
   // how much stacked lines of text may share of their line boxes, on each side
   private static final double STACKED_LINE_INSET = 1.5;
   private transient double maxLabelW = -1, maxLabelH = -1;
}

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
package inetsoft.graph.guide.form;

import com.inetsoft.build.tern.*;
import inetsoft.graph.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.LabelFormVO;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * This is a text label form guide.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=LabelForm")
public class LabelForm extends GraphForm {
   /**
    * Default constructor.
    */
   public LabelForm() {
      setLine(GraphConstants.NONE); // default to no border
   }

   /**
    * Create a label at position specified in logic space (scaled tuple values).
    * @param label label content.
    * @param tuple label point specified as a tuple of values.
    */
   public LabelForm(Object label, double[] tuple) {
      this.label = GTool.unwrap(label);
      this.tuple = tuple;
   }

   /**
    * Create a label at position specified as data values (to be scaled).
    * @param label label content.
    * @param values label point specified as an unscaled tuple of values.
    */
   @TernConstructor
   public LabelForm(Object label, Object[] values) {
      this.label = GTool.unwrap(label);
      this.values = values;
   }

   /**
    * Create a label at a fixed position.
    * @param pos the position in the graph. The position is in points using the
    * math coordinate (Y grows upwards).
    */
   public LabelForm(Object label, Point2D pos) {
      this.label = GTool.unwrap(label);
      this.pos = pos;
   }

   {
      setLine(GraphConstants.THIN_THIN_LINE);
   }

   /**
    * Set the text label of the form.
    */
   @TernMethod
   public void setLabel(Object label) {
      this.label = GTool.unwrap(label);
   }

   /**
    * Get the text label of the form.
    */
   @TernMethod
   public Object getLabel() {
      return label;
   }

   /**
    * Set the tuple for obtaining the position for the label in the coordinate.
    * The tuple contains scaled values.
    */
   @TernMethod
   public void setTuple(double[] tuple) {
      this.tuple = tuple;
   }

   /**
    * Get the position tuple value.
    */
   @TernMethod
   public double[] getTuple() {
      return this.tuple;
   }

   /**
    * Set the values for obtaining the position for the label in the coordinate.
    * The values are scaled to get the logic space.
    */
   @TernMethod
   public void setValues(Object[] values) {
      this.values = GTool.unwrapArray(values);
   }

   /**
    * Get the position values value.
    */
   @TernMethod
   public Object[] getValues() {
      return this.values;
   }

   /**
    * Set the fixed position for the label.
    * @param pos the point location in graph. If the value is between 0 and
    * 1 (non-inclusive), it's treated as a proportion of the width/height. If the
    * value is negative, it's the distance from the right/top of the graph.
    */
   @TernMethod
   public void setPoint(Point2D pos) {
      this.pos = pos;
   }

   /**
    * Get the fixed position for the label.
    */
   @TernMethod
   public Point2D getPoint() {
      return pos;
   }

   /**
    * Set the label insets.
    */
   @TernMethod
   public void setInsets(Insets insets) {
      this.insets = insets;
   }

   /**
    * Get the label insets.
    */
   @TernMethod
   public Insets getInsets() {
      return insets;
   }

   /**
    * Set the text attributes.
    */
   @TernMethod
   public void setTextSpec(TextSpec spec) {
      if(spec == null) {
         spec = new TextSpec();
      }

      this.spec = spec;
   }

   /**
    * Get the text attributes.
    */
   @TernMethod
   public TextSpec getTextSpec() {
      return spec;
   }

   /**
    * Get the text collision resolution option.
    */
   @TernMethod
   public int getCollisionModifier() {
      return modifier;
   }

   /**
    * Set the text collision resolution option.
    */
   @TernMethod
   public void setCollisionModifier(int modifier) {
      this.modifier = modifier;
   }

   /**
    * Set the the horizontal alignment.
    */
   @TernMethod
   public void setAlignmentX(int alignx) {
      this.alignx = alignx;
   }

   /**
    * Set the the vertical alignment.
    */
   @TernMethod
   public void setAlignmentY(int aligny) {
      this.aligny = aligny;
   }

   /**
    * Gets the the horizontal alignment.
    */
   @TernMethod
   public int getAlignmentX() {
      return alignx;
   }

   /**
    * Gets the the vertical alignment.
    */
   @TernMethod
   public int getAlignmentY() {
      return aligny;
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public Visualizable createVisual(Coordinate coord) {
      Point2D labelpos = null;

      if(pos != null) {
         labelpos = pos;
      }
      else {
         if(values != null) {
            tuple = scale(values, coord);
         }

         if(tuple != null) {
            labelpos = getPosition(coord, tuple);
         }
      }

      // sanity check of invalid labels. (52067)
      if(labelpos == null || Double.isInfinite(labelpos.getX()) ||
         Double.isInfinite(labelpos.getY()))
      {
         return null;
      }

      LabelFormVO lform = createFormVO(label, labelpos);
      lform.setZIndex(getZIndex());
      lform.setFixedPosition(isFixedPosition());

      return lform;
   }

   /**
    * Create the visual object for this form.
    */
   protected LabelFormVO createFormVO(Object label, Point2D labelpos) {
      return new LabelFormVO(this, label, labelpos);
   }

   /**
    * Check if form is at fixed position.
    * @hidden
    */
   @Override
   public boolean isFixedPosition() {
      return getPoint() != null;
   }


   /**
    * Check if equals another objects in structure.
    */
   public boolean equalsContent(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      LabelForm form = (LabelForm) obj;
      return Objects.equals(label, form.label) &&
         Objects.deepEquals(tuple, form.tuple) &&
         Objects.deepEquals(values, form.values) &&
         Objects.equals(pos, form.pos) &&
         Objects.equals(spec, form.spec) &&
         modifier == form.modifier &&
         Objects.equals(insets, form.insets) &&
         alignx == form.alignx &&
         aligny == form.aligny;
   }

   private Object label;
   private double[] tuple;
   private Object[] values;
   private Point2D pos;
   private TextSpec spec = new TextSpec();
   private int modifier = VLabel.MOVE_NONE;
   private Insets insets = new Insets(0, 0, 0, 0);
   private int alignx = GraphConstants.LEFT_ALIGNMENT;
   private int aligny = GraphConstants.BOTTOM_ALIGNMENT;
   private static final long serialVersionUID = 1L;
}

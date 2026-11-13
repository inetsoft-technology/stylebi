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
package inetsoft.graph.guide.form;

import inetsoft.graph.Visualizable;
import inetsoft.graph.visual.BridgeLineFormVO;

import java.awt.geom.Point2D;

/**
 * A waterfall bridge line between two bars. An end next to a bar whose width is clamped on
 * screen moves with that bar's edge, so it keeps its gap from the bar.
 */
public class BridgeLineForm extends LineForm {
   /**
    * Create a bridge line.
    * @param from the start point, in scaled values, with x second to last.
    * @param to the end point, in scaled values, with x second to last.
    * @param fromCenterX the x of the bar the bridge starts from.
    * @param toCenterX the x of the bar the bridge ends at.
    * @param fromHalf the start bar's half-width, as a fraction of one x unit.
    * @param toHalf the end bar's half-width, as a fraction of one x unit.
    * @param fromClamp true if the start bar's width is clamped.
    * @param toClamp true if the end bar's width is clamped.
    */
   public BridgeLineForm(double[] from, double[] to, double fromCenterX, double toCenterX,
                         double fromHalf, double toHalf, boolean fromClamp, boolean toClamp)
   {
      super(from, to);
      this.fromX = from[from.length - 2];
      this.toX = to[to.length - 2];
      this.fromCenterX = fromCenterX;
      this.toCenterX = toCenterX;
      this.fromHalf = fromHalf;
      this.toHalf = toHalf;
      this.fromClamp = fromClamp;
      this.toClamp = toClamp;
   }

   /**
    * Get the x of the start point.
    */
   public double getFromX() {
      return fromX;
   }

   /**
    * Get the x of the end point.
    */
   public double getToX() {
      return toX;
   }

   /**
    * Get the x of the bar the bridge starts from.
    */
   public double getFromCenterX() {
      return fromCenterX;
   }

   /**
    * Get the x of the bar the bridge ends at.
    */
   public double getToCenterX() {
      return toCenterX;
   }

   /**
    * Get the start bar's half-width, as a fraction of one x unit.
    */
   public double getFromHalf() {
      return fromHalf;
   }

   /**
    * Get the end bar's half-width, as a fraction of one x unit.
    */
   public double getToHalf() {
      return toHalf;
   }

   /**
    * Check if the start bar's width is clamped.
    */
   public boolean isFromClamp() {
      return fromClamp;
   }

   /**
    * Check if the end bar's width is clamped.
    */
   public boolean isToClamp() {
      return toClamp;
   }

   @Override
   protected Visualizable createVisual(Point2D[] points) {
      BridgeLineFormVO line = new BridgeLineFormVO(this, points);

      line.setZIndex(getZIndex());
      line.setFixedPosition(isFixedPosition());
      line.setColor(getColor());
      return line;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      BridgeLineForm form = (BridgeLineForm) obj;
      return fromX == form.fromX && toX == form.toX && fromCenterX == form.fromCenterX &&
         toCenterX == form.toCenterX && fromHalf == form.fromHalf && toHalf == form.toHalf &&
         fromClamp == form.fromClamp && toClamp == form.toClamp;
   }

   private final double fromX;
   private final double toX;
   private final double fromCenterX;
   private final double toCenterX;
   private final double fromHalf;
   private final double toHalf;
   private final boolean fromClamp;
   private final boolean toClamp;

   private static final long serialVersionUID = 1L;
}

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
package inetsoft.graph.visual;

import inetsoft.graph.guide.form.BridgeLineForm;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Visual object for a waterfall bridge line. Each end next to a width-clamped bar moves by
 * the pixels the clamp took from that bar's half-width, so it keeps its gap from the bar.
 */
public class BridgeLineFormVO extends LineFormVO {
   /**
    * Constructor.
    * @param form the bridge line form.
    * @param points the two ends, in the coordinate's space.
    */
   public BridgeLineFormVO(BridgeLineForm form, Point2D[] points) {
      super(form, points);
   }

   /**
    * Get the ends the bridge is drawn between, or none when the moved ends leave no gap to
    * bridge.
    */
   @Override
   protected Point2D[] getScreenPoints() {
      Point2D[] pts = super.getScreenPoints();
      BridgeLineForm form = (BridgeLineForm) getForm();
      double span = form.getToX() - form.getFromX();

      if(pts.length != 2 || span == 0) {
         return pts;
      }

      // screen offset of one x unit along the bridge
      double ux = (pts[1].getX() - pts[0].getX()) / span;
      double uy = (pts[1].getY() - pts[0].getY()) / span;
      double slotPx = Math.hypot(ux, uy);

      if(slotPx == 0) {
         return pts;
      }

      double from = form.isFromClamp() ? clampedPx(form.getFromHalf(), slotPx) : 0;
      double to = form.isToClamp() ? clampedPx(form.getToHalf(), slotPx) : 0;
      // x units each end moves toward its bar's centre
      double fromMove = Math.signum(form.getFromCenterX() - form.getFromX()) * from / slotPx;
      double toMove = Math.signum(form.getToCenterX() - form.getToX()) * to / slotPx;

      // widened bars left no gap, so the ends met or crossed
      if(Math.signum(span - fromMove + toMove) != Math.signum(span)) {
         return new Point2D[0];
      }

      return new Point2D[] {
         new Point2D.Double(pts[0].getX() + ux * fromMove, pts[0].getY() + uy * fromMove),
         new Point2D.Double(pts[1].getX() + ux * toMove, pts[1].getY() + uy * toMove)
      };
   }

   /**
    * Make sure the bounds hold the moved ends.
    */
   @Override
   public Rectangle2D getBounds() {
      Rectangle2D box = super.getBounds();

      if(box != null) {
         for(Point2D pt : getScreenPoints()) {
            box.add(pt.getX() + getForm().getXOffset(), pt.getY() + getForm().getYOffset());
         }
      }

      return box;
   }

   // the pixels the width clamp takes from each side of a bar with this half-width, in slots
   private static double clampedPx(double half, double slotPx) {
      double thickness = 2 * half * slotPx;

      if(thickness <= 0) {
         return 0;
      }

      return (thickness - Math.min(BarVO.MAX_BAR_PX, Math.max(BarVO.MIN_BAR_PX, thickness))) / 2;
   }
}

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
package inetsoft.graph.internal;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;

/**
 * This class is used to support performing transformation in chart coordinate
 * space, and then convert to graphics space before printing. This allows
 * layout to be done without concern with the actual output size.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GTransform extends AffineTransform {
   /**
    * Scales all previous translation to the specified ratio. The scale is
    * not applied to the matrix itself.
    */
   public void scaleTo(double sx, double sy) {
      if(trans == null) {
         return;
      }

      ArrayList vec = trans;
      AffineTransform scale = AffineTransform.getScaleInstance(sx, sy);

      trans = null;
      setToIdentity();

      for(int i = 0; i < vec.size(); i++) {
         AffineTransform aobj = (AffineTransform) vec.get(i);

         if((aobj.getType() & AffineTransform.TYPE_GENERAL_ROTATION) == 0) {
            aobj.preConcatenate(scale);
            aobj.scale(1 / sx, 1 / sy);
         }

         concatenate0(aobj);
      }
   }

   /**
    * Proxy method, @see AffineTransform's translate.
    */
   @Override
   public void translate(double tx, double ty) {
      super.translate(tx, ty);

      if(trans != null) {
         trans.add(AffineTransform.getTranslateInstance(tx, ty));
      }
   }

   /**
    * Proxy method, @see AffineTransform's rotate.
    */
   @Override
   public void rotate(double theta) {
      super.rotate(theta);

      if(trans != null) {
         trans.add(AffineTransform.getRotateInstance(theta));
      }
   }

   /**
    * Proxy method, @see AffineTransform's scale.
    */
   @Override
   public void scale(double sx, double sy) {
      super.scale(sx, sy);

      if(trans != null) {
         trans.add(AffineTransform.getScaleInstance(sx, sy));
      }
   }

   /**
    * Proxy method, @see AffineTransform's shear.
    */
   @Override
   public void shear(double shx, double shy) {
      super.shear(shx, shy);

      if(trans != null) {
         trans.add(AffineTransform.getShearInstance(shx, shy));
      }
   }

   /**
    * Proxy method, @see AffineTransform's concatenate.
    */
   @Override
   public void concatenate(AffineTransform tx) {
      super.concatenate(tx);

      if(trans != null) {
         ArrayList tlist = getTransforms(tx);

         for(int i = 0; i < tlist.size(); i++) {
            trans.add(tlist.get(i));
         }
      }
   }

   /**
    * Proxy method, @see AffineTransform's preConcatenate.
    */
   @Override
   public void preConcatenate(AffineTransform tx) {
      super.preConcatenate(tx);

      if(trans != null) {
         ArrayList tlist = getTransforms(tx);

         for(int i = 0; i < tlist.size(); i++) {
            trans.add(i, tlist.get(i));
         }
      }
   }

   /**
    * Get the transform or the transforms contained in the GTransform.
    */
   private ArrayList getTransforms(AffineTransform tx) {
      ArrayList tlist = null;

      if(tx instanceof GTransform && tx != this) {
         tlist = ((GTransform) tx).trans;
      }

      if(tlist == null) {
         tlist = new ArrayList();
         tlist.add(tx);
      }

      return tlist;
   }

   /**
    * Proxy method, call AffineTransform's concatenate0.
    */
   private void concatenate0(AffineTransform tx) {
      super.concatenate(tx);
   }

   /**
    * Proxy method, call AffineTransform's preConcatenate.
    */
   private void preConcatenate0(AffineTransform tx) {
      super.preConcatenate(tx);
   }

   /**
    * Make a copy of the transform.
    */
   @Override
   public Object clone() {
      GTransform obj = (GTransform) super.clone();

      if(trans != null) {
         obj.trans = new ArrayList();

         for(int i = 0; i < trans.size(); i++) {
            obj.trans.add(((AffineTransform) trans.get(i)).clone());
         }
      }

      return obj;
   }

   /**
    * This deals with rounding error in transform, e.g. 5.9999999 vs. 6.
    */
   public static AffineTransform normalize(AffineTransform trans) {
      double[] mx = new double[6];

      trans.getMatrix(mx);
      mx[4] = roundFraction(mx[4]);
      mx[5] = roundFraction(mx[5]);
      trans.setTransform(mx[0], mx[1], mx[2], mx[3], mx[4], mx[5]);

      return trans;
   }

   /**
    * Round the rounding error, e.g. 5.999999 -> 6
    */
   private static double roundFraction(double v) {
      double v2 = Math.round(v);
      
      return (Math.abs(v2 - v) < 0.00001) ? v2 : v;
   }

   private ArrayList trans = new ArrayList(3);
}

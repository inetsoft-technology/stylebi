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
package inetsoft.graph.internal;

import java.util.HashMap;
import java.util.Map;

/**
 * Size manager assigns size to layout objects.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class SizeManager {
   /**
    * Get the size manager for width.
    * @param asize the specified available size.
    * @param objs the layout objects to share the size, ordered by size
    * priority.
    * @param cobj the layout object to consume the remainder size if any, false
    * otherwise.
    */
   public static SizeManager width(double asize, ILayout[] objs, ILayout cobj) {
      return new Width(asize, objs, cobj);
   }

   /**
    * Get the size manager for height.
    * @param asize the specified available size.
    * @param objs the layout objects to share the size, ordered by size
    * priority.
    * @param cobj the layout object to consume the remainder size if any, false
    * otherwise.
    */
   public static SizeManager height(double asize, ILayout[] objs, ILayout cobj)
   {
      return new Height(asize, objs, cobj);
   }

   /**
    * Get the size manager for size.
    * @param asize the specified available size.
    * @param objs the size objects to share the size, ordered by size
    * priority.
    * @param cobj the size object to consume the remainder size if any, false
    * otherwise.
    */
   public static SizeManager size(double asize, ISize[] objs, ISize cobj) {
      return new Size(asize, objs, cobj);
   }

   /**
    * Create an instance of size manager.
    * @param asize the specified available size.
    * @param objs the layout objects to share the size, ordered by size
    * priority.
    * @param cobj the layout object to consume the remainder size if any, false
    * otherwise.
    */
   protected SizeManager(double asize, ILayout[] objs, ILayout cobj) {
      super();

      this.asize = asize;
      this.objs = objs;
      this.cobj = cobj;

      init();
   }

   /**
    * Create an instance of size manager.
    * @param asize the specified available size.
    * @param objs the size objects to share the size, ordered by size
    * priority.
    * @param cobj the size object to consume the remainder size if any, false
    * otherwise.
    */
   protected SizeManager(double asize, ISize[] objs, ISize cobj) {
      super();

      this.asize = asize;
      this.objs = objs;
      this.cobj = cobj;

      init();
   }

   /**
    * Get the size assigned to the specified layout object.
    */
   public double getSize(ILayout obj) {
      return map.get(obj);
   }

   /**
    * Get the size assigned to the specified layout object.
    */
   public double getSize(ISize obj) {
      return map.get(obj);
   }

   /**
    * Get the available size.
    */
   public double getAvailableSize() {
      return asize;
   }

   /**
    * Get the total size being occupied.
    */
   public double getTotalSize() {
      return total;
   }

   /**
    * Initialize the size map.
    */
   private void init() {
      double msize = 0; // min size of all objects
      double psize = 0; // preferred size of all objects
      map = new HashMap<>();

      for(int i = 0; i < objs.length; i++) {
         msize += getMinSize(objs[i]);
         psize += getPreferredSize(objs[i]);
      }

      // available size less than min, use min size
      if(asize <= msize) {
         for(int i = 0; i < objs.length; i++) {
            double msize0 = getMinSize(objs[i]);
            double size0 = Math.min(msize0, asize);
            map.put(objs[i], size0);
            total += size0;
            asize -= size0;
         }
      }
      // use perferred size
      else if(asize >= psize) {
         double remainder = asize - psize;

         for(int i = 0; i < objs.length; i++) {
            double size0 = getPreferredSize(objs[i]);

            if(objs[i] == cobj) {
               size0 += remainder;
            }

            map.put(objs[i], size0);
            total += size0;
         }
      }
      // use both preferred size and min size
      else {
         double remainder = asize - msize;

         for(int i = 0; i < objs.length; i++) {
            double size0 = getMinSize(objs[i]);
            double psize0 = getPreferredSize(objs[i]);

            if(remainder > 0) {
               double inc = Math.min(remainder, psize0 - size0);
               size0 += inc;
               remainder -= inc;
            }

            map.put(objs[i], size0);
            total += size0;
         }

         if(remainder > 0) {
            double val = map.get(cobj);
            val += remainder;
            map.put(cobj, val);
            total += remainder;
         }
      }
   }

   /**
    * Get the min size of the specified layout object.
    */
   protected abstract double getMinSize(Object obj);

   /**
    * Get the preferred size of the specified layout object.
    */
   protected abstract double getPreferredSize(Object obj);

   /**
    * The size manager for width.
    */
   private static class Width extends SizeManager {
      /**
       * Constructor.
       */
      private Width(double asize, ILayout[] objs, ILayout cobj) {
         super(asize, objs, cobj);
      }

      /**
       * Get the min size of the specified layout object.
       */
      @Override
      protected double getMinSize(Object obj) {
         return obj == null ? 0 : ((ILayout) obj).getMinWidth();
      }

      /**
       * Get the preferred size of the specified layout object.
       */
      @Override
      protected double getPreferredSize(Object obj) {
         return obj == null ? 0 : ((ILayout) obj).getPreferredWidth();
      }
   }

   /**
    * The size manager for height.
    */
   private static class Height extends SizeManager {
      /**
       * Constructor.
       */
      private Height(double asize, ILayout[] objs, ILayout cobj) {
         super(asize, objs, cobj);
      }

      /**
       * Get the min size of the specified layout object.
       */
      @Override
      protected double getMinSize(Object obj) {
         return obj == null ? 0 : ((ILayout) obj).getMinHeight();
      }

      /**
       * Get the preferred size of the specified layout object.
       */
      @Override
      protected double getPreferredSize(Object obj) {
         return obj == null ? 0 : ((ILayout) obj).getPreferredHeight();
      }
   }

   /**
    * The size manager for size.
    */
   private static class Size extends SizeManager {
      /**
       * Constructor.
       */
      private Size(double asize, ISize[] objs, ISize cobj) {
         super(asize, objs, cobj);
      }

      /**
       * Get the min size of the specified layout object.
       */
      @Override
      protected double getMinSize(Object obj) {
         return obj == null ? 0 : ((ISize) obj).getMinSize();
      }

      /**
       * Get the preferred size of the specified layout object.
       */
      @Override
      protected double getPreferredSize(Object obj) {
         return obj == null ? 0 : ((ISize) obj).getPreferredSize();
      }
   }

   private double asize;
   private double total;
   private Object[] objs;
   private Object cobj;
   private Map<Object,Double> map;
}

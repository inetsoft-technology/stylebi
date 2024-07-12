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
package inetsoft.mv.fs;

/**
 * XNodeRuler, measures the distance of two nodes. This is not thread-safe.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class XNodeRuler {
   /**
    * Create an instance of XNodeRuler.
    */
   public XNodeRuler() {
      super();
      val = -1;
   }

   /**
    * Create an instance of XNodeRuler.
    */
   public XNodeRuler(String from, String to) {
      this();
      this.from = from;
      this.to = to;
   }

   /**
    * Set the source node.
    */
   public void setSource(String src) {
      val = -1;
      this.from = src;
   }

   /**
    * Get the source node.
    */
   public String getSource() {
      return from;
   }

   /**
    * Set the target node.
    */
   public void setTarget(String target) {
      val = -1;
      this.to = target;
   }

   /**
    * Get the target node.
    */
   public String getTarget() {
      return to;
   }

   /**
    * Get the distance of the two nodes.
    */
   public int distance() {
      if(val >= 0) {
         return val;
      }

      String[] farr = from.split("\\.");
      String[] tarr = to.split("\\.");
      int flen = farr.length;
      int tlen = tarr.length;
      boolean eq = true;
      val = 0;

      for(int i = 0; i < flen || i < tlen; i++) {
         if(eq && i < flen && i < tlen && farr[i].equals(tarr[i])) {
            continue;
         }

         eq = false;

         if(i < flen) {
            val++;
         }

         if(i < tlen) {
            val++;
         }
      }

      return val;
   }

   private int val;
   private String from;
   private String to;
}

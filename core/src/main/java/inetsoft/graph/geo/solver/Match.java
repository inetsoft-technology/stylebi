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
package inetsoft.graph.geo.solver;

/**
 * Match represent a match performed by distance algorithm.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public class Match implements Comparable<Match> {
   /**
    * Constructure.
    * @param id feature id.
    * @param distance the distance.
    */
   public Match(String id, int distance) {
      this.id = id;
      this.distance = distance;
   }

   /**
    * Compares two Match objects.
    * @param o the match to be compared.
    */
   @Override
   public int compareTo(Match o) {
      if(distance < o.distance) {
         return -1;
      }
      else if(distance > o.distance) {
         return 1;
      }

      return 0;
   }

   /**
    * Get feature id.
    */
   public String getID() {
      return id;
   }

   /**
    * Get distance.
    */
   public int getDistance() {
      return distance;
   }

   private final String id;
   private int distance = 0;
}
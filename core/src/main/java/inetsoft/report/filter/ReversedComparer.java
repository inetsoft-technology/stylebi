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
package inetsoft.report.filter;

import inetsoft.report.Comparer;

/**
 * This class returns a reversed comparison result of the nested comparer.
 *
 * @version 9.5, 12/20/2008
 * @author InetSoft Technology Corp
 */
public class ReversedComparer implements Comparer {
   /**
    * Constructor.
    */
   public ReversedComparer(Comparer comp) {
      this.comp = comp;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(Object v1, Object v2) {
      return -1 * comp.compare(v1, v2);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(double v1, double v2){
      return -1 * comp.compare(v1, v2);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(float v1, float v2){
      return -1 * comp.compare(v1, v2);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(long v1, long v2){
      return -1 * comp.compare(v1, v2);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(int v1, int v2){
      return -1 * comp.compare(v1, v2);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(short v1, short v2){
      return -1 * comp.compare(v1, v2);
   }
   
   private Comparer comp;
}

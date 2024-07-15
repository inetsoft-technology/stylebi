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
package inetsoft.uql.viewsheet.graph;

/**
 * Interface for a candle chart info class which maintains binding info of
 * a candle chart.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface CandleChartInfo extends MergedChartInfo {
   /**
    * Get the close field.
    * @return the close field.
    */
   public ChartRef getCloseField();

   /**
    * Get the high field.
    * @return the high field.
    */
   public ChartRef getHighField();

   /**
    * Get the low field.
    * @return the low field.
    */
   public ChartRef getLowField();

   /**
    * Get the open field.
    * @return the open field.
    */
   public ChartRef getOpenField();

   /**
    * Get the runtime close field.
    * @return the runtime close field.
    */
   public ChartRef getRTCloseField();

   /**
    * Get the runtime open field.
    * @return the runtime open field.
    */
   public ChartRef getRTOpenField();

   /**
    * Get the runtime high field.
    * @return the runtime high field.
    */
   public ChartRef getRTHighField();

   /**
    * Get the runtime low field.
    * @return the runtime low field.
    */
   public ChartRef getRTLowField();

   /**
    * Set the high field.
    */
   public void setHighField(ChartRef ref);

   /**
    * Set the close field.
    */
   public void setCloseField(ChartRef ref);

   /**
    * Set the low field.
    */
   public void setLowField(ChartRef ref);

   /**
    * Set the open field.
    */
   public void setOpenField(ChartRef ref);
}
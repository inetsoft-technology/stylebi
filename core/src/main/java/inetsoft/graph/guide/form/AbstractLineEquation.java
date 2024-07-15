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
package inetsoft.graph.guide.form;

import com.inetsoft.build.tern.TernMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @by: ChrisSpagnoli feature1379102629417 2015-1-10
/**
 * A line equation defines how a line (or curve) is drawn from a given set of
 * points. This is used to fit a trend line to a set of data points.
 *
 * @hidden
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractLineEquation implements LineEquation {

   @Override
   @TernMethod
   public void setXmax(double xmax){
      this.xmax = xmax;
   }

   protected double xmax = Double.NEGATIVE_INFINITY;

   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractLineEquation.class);
}

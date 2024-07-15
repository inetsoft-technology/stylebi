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
package inetsoft.report.gui.viewsheet.gauge;

/**
 * The default quarter VS Gauge.
 *
 * @version 8.5, 2006-6-23
 * @author InetSoft Technology Corp
 */
public class DefaultQuarterVSGauge extends DefaultVSGauge {
   /**
    * Caculate the begin drawing radian.
    */
   @Override
   protected double getValueDrawingBeginRadian() {
      double begin = (Math.PI / 2 - angle) + angle - getRotation();
      return begin;
   }
}

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
package inetsoft.graph.mxgraph.util.svg;

import java.awt.*;

/**
 * This interface represents objects which creates Shape objects.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 */
public interface ShapeProducer {
   /**
    * Returns the Shape object initialized during the last parsing.
    *
    * @return the shape or null if this handler has not been used to
    * parse a path.
    */
   Shape getShape();

   /**
    * Returns the current winding rule.
    */
   int getWindingRule();

   /**
    * Sets the winding rule used to construct the path.
    */
   void setWindingRule(int i);
}

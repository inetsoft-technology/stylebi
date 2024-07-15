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
package inetsoft.graph.mxgraph.util.svg;

import java.awt.*;

/**
 * This class produces a polygon shape from a reader.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 */
public class AWTPolygonProducer extends AWTPolylineProducer {
   /**
    * Utility method for creating an ExtendedGeneralPath.
    *
    * @param text The text representation of the path specification.
    * @param wr   The winding rule to use for creating the path.
    */
   public static Shape createShape(String text, int wr) throws ParseException
   {
      AWTPolygonProducer ph = new AWTPolygonProducer();

      ph.setWindingRule(wr);
      PointsParser p = new PointsParser(ph);
      p.parse(text);

      return ph.getShape();
   }

   /**
    * Implements {@link PointsHandler#endPoints()}.
    */
   public void endPoints() throws ParseException
   {
      path.closePath();
   }
}

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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * This class defines a color frame for numeric values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class LinearColorFrameWrapper extends ColorFrameWrapper {
   /**
    * Check whether the author picked this ramp, as opposed to the chart seeding it.
    */
   public boolean isAuthorRamp() {
      return authorRamp;
   }

   /**
    * Set whether the author picked this ramp.
    */
   public void setAuthorRamp(boolean authorRamp) {
      this.authorRamp = authorRamp;
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(authorRamp) {
         writer.print(" authorRamp=\"true\"");
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      authorRamp = "true".equals(Tool.getAttribute(tag, "authorRamp"));
   }

   @Override
   public boolean equalsContent(Object obj) {
      return super.equalsContent(obj) && sameRamp(obj);
   }

   @Override
   public boolean equals(Object obj) {
      return super.equals(obj) && sameRamp(obj);
   }

   private boolean sameRamp(Object obj) {
      return obj instanceof LinearColorFrameWrapper &&
         authorRamp == ((LinearColorFrameWrapper) obj).authorRamp;
   }

   private boolean authorRamp;
}

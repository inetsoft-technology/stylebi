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
package inetsoft.report.internal;

import inetsoft.report.ReportElement;

import java.io.*;

/**
 * Report element context (all attributes affecting the elements).
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DefaultTextContext extends BaseElement
   implements TextContext, Cacheable 
{
   /**
    * Get the heading level. If the value is less than 0, this is not
    * a heading element.
    */
   @Override
   public int getLevel() {
      return heading;
   }

   /**
    * Set the element heading level.
    */
   public void setLevel(int level) {
      this.heading = level;
   }

   /**
    * Write an element out for swapping.
    */
   public static void write(ObjectOutputStream out, ReportElement cn)
      throws IOException 
   {
      ((BaseElement) cn).writeObjectMin(out);

      if(cn instanceof TextContext) {
         out.writeInt(((TextContext) cn).getLevel());
      }
      else {
         out.writeInt(-1);
      }
   }

   /**
    * Read back a swapped element.
    */
   public void read(ObjectInputStream inp)
      throws IOException, ClassNotFoundException 
   {
      super.readObjectMin(inp);
      heading = inp.readInt();
   }

   public boolean equals(Object obj) {
      if(super.equals(obj) && (obj instanceof DefaultTextContext)) {
         return heading == ((DefaultTextContext) obj).heading;
      }

      return false;
   }

   private int heading = -1;
}


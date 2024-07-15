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
package inetsoft.report.composition.graph.data;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.scale.Scale;

import java.util.Comparator;

/**
 * A map for data compare with scale, it is used for data calculation,
 * like Change, RunningTotal or Moving.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ScaleRouter extends AbstractRouter {
   /**
    * Default constructor.
    */
   public ScaleRouter() {
      super();
   }

   /**
    * Constructor.
    */
   public ScaleRouter(Scale scale, Comparator comp) {
      super(comp);
      this.scale = scale;
      values = scale.getValues();
   }

   @Override
   public Object[] getValues() {
      if(values == null) {
         values = scale == null ? null : scale.getValues();
         values = values == null ? new Object[0] : values;
      }

      return values;
   }

   @Override
   public boolean isValidFor(DataSet dataSet) {
      return true;
   }

   private Scale scale;
   private transient Object[] values;
}

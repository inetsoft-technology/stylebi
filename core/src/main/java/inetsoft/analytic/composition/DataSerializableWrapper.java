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
package inetsoft.analytic.composition;

import inetsoft.util.*;

import java.io.*;

/**
 * DataSerializableWrapper, a wrapper to wrap an object which both implements
 * XMLSerializable and DataSerializable to DataSerializable only.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public class DataSerializableWrapper implements CustomDataSerializable {
   public DataSerializableWrapper(DataSerializable source) {
      this.source = source;
   }

   @Override
   public void writeData(DataOutputStream output) throws IOException {
      source.writeData(output);
   }

   @Override
   public boolean parseData(DataInputStream input) {
      return source.parseData(input);
   }

   @Override
   public Class getSerializedClass() {
      return source instanceof CustomSerializable ?
         ((CustomSerializable) source).getSerializedClass() : source.getClass();
   }

   private DataSerializable source;
}
/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import java.io.*;

/**
 * DataSerializable defines the common method of write data to an OutputStream
 * and parse it from an InputStream.
 *
 * @version 9.6
 * @author InetSoft Technology Corp
 */
public interface DataSerializable {
   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   public void writeData(DataOutputStream output) throws IOException;

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   public boolean parseData(DataInputStream input);
}


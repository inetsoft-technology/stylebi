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
package inetsoft.sree.internal;

import inetsoft.util.XMLSerializable;

/**
 * HttpXMLSerializable defines the common method of encoding & decoding.
 *
 * @version 8.5 6/26/2006
 * @author InetSoft Technology Corp
 */
public interface HttpXMLSerializable extends XMLSerializable {
   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source source string.
    * @return encoded string.
    */
   String byteEncode(String source);

   /**
    * Convert the encoded string to the original unencoded string.
    * @param encString a string encoded using the byteEncode method.
    * @return original string.
    */
   String byteDecode(String encString);

   /**
    * Check if this object should encoded when writing.
    * @return <code>true</code> if should encoded, <code>false</code> otherwise.
    */
   boolean isEncoding();

   /**
    * Set encoding flag.
    * @param encoding true to encode.
    */
   void setEncoding(boolean encoding);
}


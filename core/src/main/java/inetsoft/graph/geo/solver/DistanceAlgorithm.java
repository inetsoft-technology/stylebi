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
package inetsoft.graph.geo.solver;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.StringEncoder;

/**
 * <tt>MatchingAlgorithm</tt> that ranks matches based on the Levenshtein
 * distance between the strings.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public class DistanceAlgorithm extends AbstractDistanceAlgorithm {
   /**
    * Creates a new instance of <tt>DistanceAlgorithm</tt>.
    */
   public DistanceAlgorithm() {
      super(new IdentityEncoder());
   }

   private static final class IdentityEncoder implements StringEncoder {
      @Override
      public String encode(String pString) throws EncoderException {
         return pString;
      }

      @Override
      public Object encode(Object pObject) throws EncoderException {
         return pObject;
      }
   }
}

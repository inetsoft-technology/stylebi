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
package inetsoft.graph.geo.solver;

import org.apache.commons.codec.language.Soundex;

/**
 * <tt>MatchingAlgorithm</tt> that ranks matches based on the Levenshtein
 * distance between the Soundex values of the strings.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public class SoundexAlgorithm extends AbstractDistanceAlgorithm {
   /**
    * Creates a new instance of <tt>SoundexAlgorithm</tt>.
    */
   public SoundexAlgorithm() {
      super(new Soundex());
   }
}

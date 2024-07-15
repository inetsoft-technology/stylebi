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

import inetsoft.graph.data.DataSet;
import org.apache.commons.codec.StringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Base class for matching algorithms that rank matches based on their
 * Levenshtein distance.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public abstract class AbstractDistanceAlgorithm
   extends AbstractMatchingAlgorithm
{
   /**
    * Creates a new instance of <tt>AbstractDistanceAlgorithm</tt>.
    *
    * @param encoder the encoder used to encode the strings.
    */
   public AbstractDistanceAlgorithm(StringEncoder encoder) {
      this.encoder = encoder;
   }

   /**
    * Find the map feature that best matches the source data.
    *
    * @param source        the source data.
    * @param sourceColumns the columns in the source data to match.
    * @param names         the map feature names.
    * @param nameColumns   the columns in the name data to match.
    * @param row           the row of the source data to match.
    *
    * @return the Match of the map feature that best matches the source data.
    */
   public List<Match> findMatches(DataSet source, int[] sourceColumns,
                                  NameTable names, int[] nameColumns, int row)
   {
      Object[] data = new Object[sourceColumns.length];

      for(int i = 0; i < data.length; i++) {
         data[i] = source.getData(sourceColumns[i], row);
      }

      Visitor visitor = new Visitor(data, nameColumns);
      names.accept(visitor);
      Collections.sort(visitor.matches);

      return visitor.matches;
   }

   /**
    * Find the map feature that best matches the source data.
    *
    * @param source        the source data.
    * @param sourceColumns the columns in the source data to match.
    * @param names         the map feature names.
    * @param nameColumns   the columns in the name data to match.
    * @param row           the row of the source data to match.
    *
    * @return the Match of the map feature that best matches the source data.
    */
   public Match findBestMatch(DataSet source, int[] sourceColumns, NameTable names,
                              int[] nameColumns, int row)
   {
      List<Match> matches = findMatches(source, sourceColumns, names,
         nameColumns, row);
      return matches.isEmpty() ? null : matches.get(0);
   }

   /**
    * Finds the map feature ids that best match the source data. The returned
    * map features will be sorted in order of the likelihood of the match, from
    * the most likely to the least likely.
    *
    * @param source        the source data.
    * @param sourceColumns the columns in the source data to match.
    * @param names         the map feature names.
    * @param nameColumns   the columns in the name data to match.
    * @param row           the row of the source data to match.
    *
    * @return the IDs of the map features that match the source data.
    */
   @Override
   public List<String> findMatchIDs(DataSet source, int[] sourceColumns,
                                    NameTable names, int[] nameColumns, int row)
   {
      List<Match> matches = findMatches(source, sourceColumns, names,
         nameColumns, row);
      List<String> ids = new ArrayList();

      for(Match match : matches) {
         ids.add(match.getID());
      }

      return ids;
   }

   private final StringEncoder encoder;
   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractDistanceAlgorithm.class);

   private final class Visitor implements NameTable.NameVisitor {
      public Visitor(Object[] data, int[] idxs) {
         this.data = data;
         this.idxs = idxs;
      }

      @Override
      public void visit(String id, String[] columns) {
         int distance = 0;

         for(int i = 0; i < data.length; i++) {
            String s1 = data[i] == null ? null : String.valueOf(data[i]);
            String s2 = (idxs[i] < columns.length) ? columns[idxs[i]] : null;

            try {
               s1 = s1 == null ? null : encoder.encode(s1);
               s1 = s1 == null ? null : s1.toLowerCase();
               s2 = s2 == null ? null : encoder.encode(s2);
               s2 = s2 == null ? null : s2.toLowerCase();
            }
            catch(Throwable exc) {
               LOG.debug("Failed to encode string: {}", exc.getMessage());
            }

            distance += distance(s1, s2);
         }

         matches.add(new Match(id, distance));
      }

      private int distance(String s1, String s2) {
         if(s1 == null) {
            return s2 == null ? 0 : s2.length();
         }

         if(s2 == null) {
            return s1.length();
         }

         int n = s1.length();
         int m = s2.length();

         if(n == 0) {
            return m;
         }

         if(m == 0) {
            return n;
         }

         if(n > m) {
            String swap = s1;
            s1 = s2;
            s2 = swap;
            n = m;
            m = s2.length();
         }

         int[] p = new int[n + 1];
         int[] d = new int[n + 1];
         int[] _d;

         int i, j;
         char c;
         int cost;

         for(i = 0; i <= n; i++) {
            p[i] = i;
         }

         for(j = 1; j <= m; j++) {
            c = s2.charAt(j - 1);
            d[0] = j;

            for(i = 1; i <= n; i++) {
               cost = s1.charAt(i - 1) == c ? 0 : 1;
               d[i] = Math.min(
                  Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1 + cost]);
            }

            _d = p;
            p = d;
            d = _d;
         }

         return p[n];
      }

      @Override
      public void addMatch(String id) {
         matches.add(new Match(id, 0));
      }

      private final Object[] data;
      private final int[] idxs;
      private final List<Match> matches = new ArrayList();
   }
}

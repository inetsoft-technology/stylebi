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
package inetsoft.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TournamentTreeTest {
   @Test
   public void findsWinner() {
      final TournamentTree<Integer> tournamentTree =
         new TournamentTree<>(Arrays.asList(5, 4, 4, 1, 2, 3), Comparator.naturalOrder());

      Assertions.assertEquals(1, tournamentTree.getWinner());
   }

   @Test
   public void emptyCase() {
      final ArrayList<Integer> list = new ArrayList<>();
      final TournamentTree<Integer> tournamentTree =
         new TournamentTree<>(list, Comparator.naturalOrder());

      Assertions.assertNull(tournamentTree.getWinner());
   }

   @Test
   public void removeAll() {
      final TournamentTree<Integer> tournamentTree =
         new TournamentTree<>(Collections.singletonList(1), Comparator.naturalOrder());
      tournamentTree.replaceValueAtIndex(0, null);

      Assertions.assertNull(tournamentTree.getWinner());
      Assertions.assertEquals(-1, tournamentTree.getWinnerIndex());
   }

   @Test
   public void singleCase() {
      final TournamentTree<Integer> tournamentTree =
         new TournamentTree<>(Collections.singletonList(1), Comparator.naturalOrder());

      Assertions.assertEquals(1, tournamentTree.getWinner());
   }

   @Test
   public void elementNumberPowerOf2Case() {
      final TournamentTree<Integer> tournamentTree =
         new TournamentTree<>(Arrays.asList(3, 4, 2, 1), Comparator.naturalOrder());

      Assertions.assertEquals(1, tournamentTree.getWinner());
   }

   @Test
   public void elementReplacement() {
      final TournamentTree<Integer> tournamentTree =
         new TournamentTree<>(Arrays.asList(3, 4, 2, 1), Comparator.naturalOrder());

      final int winnerIndex = tournamentTree.getWinnerIndex();
      assertEquals(3, winnerIndex);

      tournamentTree.replaceValueAtIndex(3, 5);
      assertEquals(2, tournamentTree.getWinner());
      tournamentTree.replaceValueAtIndex(2, null);
      assertEquals(3, tournamentTree.getWinner());
      tournamentTree.replaceValueAtIndex(0, null);
      assertEquals(4, tournamentTree.getWinner());
      tournamentTree.replaceValueAtIndex(1, null);
      assertEquals(5, tournamentTree.getWinner());
      assertEquals(3, tournamentTree.getWinnerIndex());
      tournamentTree.replaceValueAtIndex(3, null);
      assertNull(tournamentTree.getWinner());
   }
}

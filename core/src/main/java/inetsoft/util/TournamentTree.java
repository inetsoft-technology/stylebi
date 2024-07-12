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

import java.util.*;

/**
 * This implementation is effectively a min-heap with element indexing and fast element
 * replacement. Null values indicate empty values.
 */
public class TournamentTree<T> {
   public TournamentTree(List<T> initialElements, Comparator<? super T> comparator) {
      this.comparator = comparator;
      initTree(initialElements);
   }

   private void initTree(List<T> elements) {
      if(elements.isEmpty()) {
         return;
      }

      final int highestOneBit = Integer.highestOneBit(elements.size());
      int size = highestOneBit * 2;

      if(highestOneBit != elements.size()) {
         size += size;
      }

      nodes = new ArrayList<>(Collections.nCopies(size, null));
      final int levelStartIdx = getParentIndex(size);

      for(int i = 0; i < elements.size(); i++) {
         nodes.set(i + levelStartIdx, new Node<>(elements.get(i), i));
      }

      buildTree(levelStartIdx / 2);
   }

   private void buildTree(int levelStartIdx) {
      for(int i = levelStartIdx; i <= levelStartIdx * 2 && nodes.get(i * 2 + 1) != null; i++) {
         nodes.set(i, findWinnerAtIndex(i));
      }

      if(levelStartIdx > 0) {
         buildTree(levelStartIdx / 2);
      }
   }

   /**
    * Replace the value at index i and recalculate this tournament tree.
    *
    * @param i     the index to replace the value at.
    * @param value the replacement value.
    */
   public void replaceValueAtIndex(int i, T value) {
      final int startIdx = Integer.highestOneBit(nodes.size()) / 2 - 1;
      final int insertionIndex = startIdx + i;
      final Node<T> node = value == null ? null : new Node<>(value, i);
      nodes.set(insertionIndex, node);

      replayGame(getParentIndex(insertionIndex));
   }

   private int getParentIndex(int i) {
      return (i - 1) / 2;
   }

   private void replayGame(int i) {
      final Node<T> winner = findWinnerAtIndex(i);

      if(!Objects.equals(winner, nodes.get(i))) {
         nodes.set(i, winner);

         final int nextRoundIndex = getParentIndex(i);

         if(nextRoundIndex != i) {
            replayGame(nextRoundIndex);
         }
      }
   }

   private Node<T> findWinnerAtIndex(int i) {
      final int idxA = i * 2 + 1;
      final int idxB = idxA + 1;

      final T a = Optional.ofNullable(nodes.get(idxA)).map(Node::getValue).orElse(null);
      final T b = idxB < nodes.size() ? Optional.ofNullable(nodes.get(idxB))
         .map(Node::getValue).orElse(null) : null;
      final int winnerIdx;

      if(a != null && b != null) {
         final int compare = comparator.compare(a, b);

         if(compare <= 0) {
            winnerIdx = idxA;
         }
         else {
            winnerIdx = idxB;
         }
      }
      else if(b == null) {
         winnerIdx = idxA;
      }
      else {
         winnerIdx = idxB;
      }

      return nodes.get(winnerIdx);
   }

   /**
    * Gets the winner of this tournament tree, which is the minimum according to the comparator.
    *
    * @return the winner of the tournament tree, or null if none exists.
    */
   public T getWinner() {
      if(nodes == null ) {
         return null;
      }

      final Node<T> rootNode = nodes.get(0);

      if(rootNode == null) {
         return null;
      }

      return rootNode.value;
   }

   /**
    * @return the index of the winner of this tournament tree.
    */
   public int getWinnerIndex() {
      if(nodes == null) {
         return -1;
      }

      final Node<T> rootNode = nodes.get(0);

      if(rootNode == null) {
         return -1;
      }

      return nodes.get(0).getIndex();
   }

   private static final class Node<T> {
      private Node(T value, int index) {
         if(value == null) {
            throw new IllegalArgumentException("Value cannot be null.");
         }

         this.value = value;
         this.index = index;
      }

      public T getValue() {
         return value;
      }

      public int getIndex() {
         return index;
      }

      final T value;
      final int index;
   }

   private List<Node<T>> nodes;

   private final Comparator<? super T> comparator;
}

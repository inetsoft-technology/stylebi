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
package inetsoft.report.lib;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static inetsoft.report.lib.TransactionType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionSimplifierTest {
   @Test
   void simplifyTransactions() {
      // same types
      testTransactionSimplifyingOnSingleAsset(Arrays.asList(CREATE, CREATE), CREATE);
      testTransactionSimplifyingOnSingleAsset(Arrays.asList(MODIFY, MODIFY), MODIFY);
      testTransactionSimplifyingOnSingleAsset(Arrays.asList(DELETE, DELETE), DELETE);

      // different types
      testTransactionSimplifyingOnSingleAsset(Arrays.asList(CREATE, DELETE), DELETE);
      testTransactionSimplifyingOnSingleAsset(Arrays.asList(DELETE, CREATE), CREATE);
      testTransactionSimplifyingOnSingleAsset(Arrays.asList(MODIFY, DELETE), DELETE);
      testTransactionSimplifyingOnSingleAsset(Arrays.asList(DELETE, MODIFY), MODIFY);
      testTransactionSimplifyingOnSingleAsset(Arrays.asList(CREATE, MODIFY), MODIFY);
      testTransactionSimplifyingOnSingleAsset(Arrays.asList(MODIFY, CREATE), CREATE);
   }

   @Test
   void distinctIdentifiersDoNotSimplify() {
      final String a = "a";
      final String b = "b";

      final List<Transaction<Integer>> transactions =
         Arrays.asList(Transaction.<Integer>builder().identifier(a).type(CREATE).build(),
                       Transaction.<Integer>builder().identifier(b).type(CREATE).build());

      final List<Transaction<Integer>> coalesced = coalescer.simplifyTransactions(transactions);
      assertEquals(2, coalesced.size());
   }

   private void testTransactionSimplifyingOnSingleAsset(List<TransactionType> types, TransactionType coalescedType) {
      final String id = "asset";

      final List<Transaction<Integer>> transactions =
         types.stream()
              .map(type -> Transaction.<Integer>builder().identifier(id).type(type).build())
              .collect(Collectors.toList());

      final List<Transaction<Integer>> coalesced = coalescer.simplifyTransactions(transactions);
      assertEquals(1, coalesced.size());
      assertEquals(coalescedType, coalesced.get(0).type());
   }

   private TransactionSimplifier<Integer> coalescer = new TransactionSimplifier<>();
}
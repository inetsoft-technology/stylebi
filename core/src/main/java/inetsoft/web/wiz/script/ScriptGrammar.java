/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.script;

import java.util.Arrays;
import java.util.List;

/**
 * The script target grammar this server speaks.
 *
 * <p>One place, because the controller advertises it and {@link ScriptReadService} emits targets
 * shaped by it: if those two disagreed, a client would negotiate a grammar the responses do not
 * follow, which is worse than no negotiation.
 */
public final class ScriptGrammar {
   /** v1 = the delimited target string; v2 = kinds, ids and structured params. */
   public static final int VERSION = 2;

   /**
    * The kinds this server can serve — those with an internal location behind them. Reserved
    * kinds are deliberately absent: advertising one and then refusing it is the silent capability
    * lie this contract exists to prevent.
    */
   public static List<String> supportedKinds() {
      return Arrays.stream(ScriptTarget.Kind.values())
         .filter(k -> k.location() != null)
         .map(ScriptTarget.Kind::wireName)
         .toList();
   }

   private ScriptGrammar() {
   }
}

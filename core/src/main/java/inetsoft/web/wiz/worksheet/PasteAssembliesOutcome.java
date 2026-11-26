/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.wiz.worksheet;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of the {@code paste_assemblies} edit op (see {@link EditResponse#paste()}): what was
 * pasted under which names, and what the paste left out or could not finish. Nothing is dropped
 * silently -- every requested name is in exactly one of {@code pasted}, {@code moved} or
 * {@code skipped}.
 *
 * @param pasted           the requested assemblies that were copied, with the name each got
 * @param alsoCopied       assemblies pasted that were not requested by name: the hidden copies
 *                         that back a pasted mirror of another worksheet
 * @param moved            same-worksheet cut only: the assemblies that stayed where they were
 *                         (a cut inside one worksheet moves, it does not rename or copy)
 * @param skipped          requested assemblies the paste rules left out, with the reason
 * @param sourceNotRemoved cross-worksheet cut only: pasted assemblies that could not be removed
 *                         from the source worksheet afterwards, so they now exist in both
 */
public record PasteAssembliesOutcome(
   List<NamePair> pasted,
   @JsonInclude(JsonInclude.Include.NON_EMPTY) List<NamePair> alsoCopied,
   @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> moved,
   List<Skipped> skipped,
   @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> sourceNotRemoved)
{
   /** An assembly copied from {@code from} (source worksheet) and named {@code to}. */
   public record NamePair(String from, String to) {}

   /** A requested assembly the paste left out. */
   public record Skipped(String name, String reason) {}
}

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
package inetsoft.web.wiz.dispatch;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bug #82316: the Composer's Console is a list the browser keeps, so a plugin-only session had
 * nothing to read. This is the server-side copy of that list for one runtime viewsheet: a bounded
 * ring of the messages the wiz calls produced, with the per-level counters the Console dialog
 * shows. Levels (the dialog's display filter) are not stored here; they live in
 * {@code ViewsheetInfo.messageLevels}.
 *
 * <p>Counters describe the retained entries, so an evicted entry stops counting;
 * {@link Snapshot#dropped()} says how many were evicted since the last clear.
 */
public final class ConsoleMessageBuffer {
   /** Entries kept per runtime viewsheet; the oldest is evicted first. */
   public static final int CAPACITY = 200;

   /**
    * Longest message kept; a longer one (a failing script is quoted whole) is cut and marked, so
    * 256 runtimes of 200 entries stay bounded.
    */
   public static final int MAX_MESSAGE_LENGTH = 2000;

   public static final String TRUNCATION_MARK = "... [truncated]";

   public static final String ERROR = "ERROR";
   public static final String WARNING = "WARNING";
   public static final String INFO = "INFO";

   /** One Console row; {@code type} is a {@code MessageCommand.Type} name, {@code assembly} may be null. */
   public record Entry(long seq, String type, String message, String assembly, Instant time) {
   }

   /**
    * @param entries  the matching entries after {@code limit} was applied, oldest first
    * @param matching how many entries matched before {@code limit}
    * @param counts   retained entries per type, over everything retained
    * @param dropped  entries evicted by the bound since the last clear
    */
   public record Snapshot(List<Entry> entries, int matching, int error, int warning, int info,
                          long dropped)
   {
      public int total() {
         return error + warning + info;
      }
   }

   /** Appends an entry and returns its sequence number. */
   public synchronized long add(String type, String message, String assembly) {
      if(entries.size() >= CAPACITY) {
         entries.removeFirst();
         dropped++;
      }

      if(message != null && message.length() > MAX_MESSAGE_LENGTH) {
         message = message.substring(0, MAX_MESSAGE_LENGTH) + TRUNCATION_MARK;
      }

      long seq = SEQ.incrementAndGet();
      entries.addLast(new Entry(seq, type, message, assembly, Instant.now()));
      return seq;
   }

   /**
    * @param types the types to keep, or null for all
    * @param limit the most entries to return (the latest ones)
    * @param sinceSeq only entries with a greater sequence number
    */
   public synchronized Snapshot read(Set<String> types, int limit, long sinceSeq) {
      List<Entry> matched = new ArrayList<>();
      int error = 0;
      int warning = 0;
      int info = 0;

      for(Entry entry : entries) {
         switch(entry.type()) {
         case ERROR -> error++;
         case WARNING -> warning++;
         default -> info++;
         }

         if(entry.seq() > sinceSeq && (types == null || types.contains(entry.type()))) {
            matched.add(entry);
         }
      }

      int matching = matched.size();
      List<Entry> kept = matching > limit ? matched.subList(matching - limit, matching) : matched;
      return new Snapshot(List.copyOf(kept), matching, error, warning, info, dropped);
   }

   /** Removes every entry and returns how many there were. Sequence numbers keep increasing. */
   public synchronized int clear() {
      int count = entries.size();
      entries.clear();
      dropped = 0;
      return count;
   }

   /**
    * One counter for every buffer, so a buffer recreated after its runtime fell out of the
    * runtime LRU (see {@link ConsoleMessageCapture}) never reuses a number a poller already saw.
    */
   private static final AtomicLong SEQ = new AtomicLong();
   private final ArrayDeque<Entry> entries = new ArrayDeque<>();
   private long dropped;
}

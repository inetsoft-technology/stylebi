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
package inetsoft.web.wiz.viewsheet;

import inetsoft.util.Tool;
import inetsoft.web.composer.ConsoleDialogMessageServiceProxy;
import inetsoft.web.composer.ws.event.SaveConsoleMessageLevelsEvent;
import inetsoft.web.wiz.dispatch.ConsoleMessageBuffer;
import inetsoft.web.wiz.dispatch.ConsoleMessageCapture;
import inetsoft.web.wiz.pairing.PairingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bug #82316: reads, clears and filters the Console of a paired viewsheet, the server-side
 * counterpart of the Composer's Console dialog. The messages are the runtime's
 * {@link ConsoleMessageBuffer}; the level filter is {@code ViewsheetInfo.messageLevels}, saved
 * through the same {@code ConsoleDialogMessageService} the dialog uses.
 */
@Service
public class ViewsheetConsoleService {
   public static final int DEFAULT_LIMIT = 50;
   private static final List<String> LEVELS = List.of("Error", "Warning", "Info");

   @Autowired
   public ViewsheetConsoleService(ViewsheetSessionService sessions,
                                  ConsoleDialogMessageServiceProxy levelService)
   {
      this.sessions = sessions;
      this.levelService = levelService;
   }

   /**
    * @param levels   the levels to show instead of the saved ones (comma lists allowed), or null
    * @param limit    most messages returned, the latest ones; null for {@value #DEFAULT_LIMIT}
    * @param sinceSeq only messages after this sequence number; null for all
    */
   public Map<String, Object> read(String sessionToken, Principal agent, List<String> levels,
                                   Integer limit, Long sinceSeq)
      throws PairingException
   {
      int max = limit == null ? DEFAULT_LIMIT : limit;

      if(max < 1 || max > ConsoleMessageBuffer.CAPACITY) {
         throw new PairingException(
            "limit must be between 1 and " + ConsoleMessageBuffer.CAPACITY + ", got " + max);
      }

      if(sinceSeq != null && sinceSeq < 0) {
         throw new PairingException("sinceSeq must be 0 or more, got " + sinceSeq);
      }

      // parsed before the session is touched so a bad word fails the same way for any session
      // a blank level parameter is no parameter, not "show nothing"
      List<String> parsed = levels == null ? List.of() : parseLevels(levels);
      List<String> requested = parsed.isEmpty() ? null : parsed;
      String runtimeId = sessions.runtimeId(sessionToken, agent);
      String[] stored = levelService.getConsoleMessageLevels(Tool.byteEncode(runtimeId), agent);
      List<String> saved = savedLevels(stored);
      List<String> unrecognized = unrecognizedLevels(stored);
      Set<String> types = new LinkedHashSet<>();

      for(String level : requested != null ? requested : saved) {
         types.add(level.toUpperCase(Locale.ROOT));
      }

      ConsoleMessageBuffer.Snapshot snapshot = ConsoleMessageCapture.buffer(runtimeId)
         .read(types, max, sinceSeq == null ? 0 : sinceSeq);
      List<Map<String, Object>> messages = new ArrayList<>();

      for(ConsoleMessageBuffer.Entry entry : snapshot.entries()) {
         Map<String, Object> row = new LinkedHashMap<>();
         row.put("seq", entry.seq());
         row.put("type", entry.type());
         row.put("message", entry.message());
         row.put("assembly", entry.assembly());
         row.put("time", DateTimeFormatter.ISO_INSTANT.format(entry.time()));
         messages.add(row);
      }

      Map<String, Object> counts = new LinkedHashMap<>();
      counts.put("error", snapshot.error());
      counts.put("warning", snapshot.warning());
      counts.put("info", snapshot.info());
      counts.put("total", snapshot.total());

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("levels", saved);

      if(!unrecognized.isEmpty()) {
         result.put("unrecognizedLevels", unrecognized);
      }

      result.put("counts", counts);
      result.put("messages", messages);
      result.put("returned", messages.size());
      result.put("matching", snapshot.matching());
      result.put("dropped", snapshot.dropped());
      result.put("bufferSize", ConsoleMessageBuffer.CAPACITY);
      return result;
   }

   public Map<String, Object> clear(String sessionToken, Principal agent) throws PairingException {
      String runtimeId = sessions.runtimeId(sessionToken, agent);
      return Map.of("cleared", ConsoleMessageCapture.buffer(runtimeId).clear());
   }

   /** @param levels the request's {@code levels} value, validated here */
   public Map<String, Object> setLevels(String sessionToken, Principal agent, Object levels)
      throws PairingException
   {
      if(!(levels instanceof List<?> list)) {
         throw new PairingException(
            "levels must be an array of error, warning, info (an empty array shows nothing)");
      }

      List<String> words = new ArrayList<>();

      for(Object item : list) {
         if(!(item instanceof String word)) {
            throw new PairingException("levels must contain only strings, got " + item);
         }

         words.add(word);
      }

      List<String> canonical = parseLevels(words);
      String runtimeId = sessions.runtimeId(sessionToken, agent);
      SaveConsoleMessageLevelsEvent event = new SaveConsoleMessageLevelsEvent();
      event.setMessageLevels(canonical.toArray(new String[0]));
      Boolean saved = levelService.saveConsoleMessageLevels(Tool.byteEncode(runtimeId), event, agent);

      if(!Boolean.TRUE.equals(saved)) {
         throw new PairingException(PairingException.Kind.INTERNAL,
                                    "The viewsheet has no info to hold the message levels");
      }

      return Map.of("levels", canonical);
   }

   /**
    * The saved levels in canonical form. A sheet saved with no level reads back as a single empty
    * string, which is the same "show nothing" state. A sheet whose dialog was saved in another
    * locale holds localized labels this cannot match; such a sheet is read as showing everything
    * (the words are reported as {@code unrecognizedLevels}) rather than as showing nothing.
    */
   private static List<String> savedLevels(String[] saved) {
      if(saved == null) {
         return LEVELS;
      }

      Set<String> found = new LinkedHashSet<>();

      for(String level : saved) {
         if(level != null) {
            found.add(level.trim().toLowerCase(Locale.ROOT));
         }
      }

      List<String> result = canonical(found);
      return result.isEmpty() && !unrecognizedLevels(saved).isEmpty() ? LEVELS : result;
   }

   private static List<String> unrecognizedLevels(String[] saved) {
      List<String> result = new ArrayList<>();

      if(saved != null) {
         for(String level : saved) {
            if(level != null && !level.isBlank() && LEVELS.stream()
               .noneMatch(known -> known.equalsIgnoreCase(level.trim())))
            {
               result.add(level.trim());
            }
         }
      }

      return result;
   }

   /** Canonical Error, Warning, Info order; each word matched case-insensitively. */
   static List<String> parseLevels(List<String> words) throws PairingException {
      Set<String> found = new LinkedHashSet<>();

      for(String entry : words) {
         for(String part : entry.split(",", -1)) {
            String word = part.trim().toLowerCase(Locale.ROOT);

            if(word.isEmpty()) {
               continue;
            }

            if(LEVELS.stream().noneMatch(level -> level.toLowerCase(Locale.ROOT).equals(word))) {
               throw new PairingException(
                  "Unknown message level \"" + part.trim() + "\"; use error, warning or info");
            }

            found.add(word);
         }
      }

      return canonical(found);
   }

   private static List<String> canonical(Set<String> lowerCaseLevels) {
      List<String> result = new ArrayList<>();

      for(String level : LEVELS) {
         if(lowerCaseLevels.contains(level.toLowerCase(Locale.ROOT))) {
            result.add(level);
         }
      }

      return result;
   }

   private final ViewsheetSessionService sessions;
   private final ConsoleDialogMessageServiceProxy levelService;
}

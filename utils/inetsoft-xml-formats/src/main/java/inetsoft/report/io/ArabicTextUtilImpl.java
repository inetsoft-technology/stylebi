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
package inetsoft.report.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.Map;

public class ArabicTextUtilImpl extends ArabicTextUtil {
   public ArabicTextUtilImpl() {
      parseBidiMirrorFile();
   }

   @Override
   public boolean containsArabic(String text) {
      return InetsoftArabicTextHandler.containsArabic(new AttributedString(text));
   }

   @Override
   public String createSubstituteString(String text) {
      AttributedString astr = new AttributedString(text);
      astr = InetsoftArabicTextHandler.assignArabicForms(astr);
      return InetsoftArabicTextHandler.createSubstituteString(astr.getIterator());
   }

   @Override
   public char getMirrorCharacter(char c) {
      if(charMirrorMap.containsKey(c)) {
         return charMirrorMap.get(c);
      }

      return c;
   }

   private void parseBidiMirrorFile() {
      try(BufferedReader reader = new BufferedReader(
         new InputStreamReader(ArabicTextUtilImpl.class.getResourceAsStream("BidiMirroring.txt"),
                               StandardCharsets.UTF_8)))
      {
         reader.lines()
            .filter(line -> {
               String trimmed = line.trim();
               return !trimmed.startsWith("#") && trimmed.contains(";");
            })
            .map(line -> line.contains("#") ? line.substring(0, line.indexOf("#")) : line)
            .forEach(line -> {
               String[] tokens = line.split(";");

               if(tokens.length == 2) {
                  charMirrorMap.put((char) Integer.parseInt(tokens[0].trim(), 16),
                                    (char) Integer.parseInt(tokens[1].trim(), 16));
               }
            });
      }
      catch(IOException e) {
         LOG.warn("Failed to create a character mirror map", e);
      }
   }

   private final Map<Character, Character> charMirrorMap = new HashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(ArabicTextUtilImpl.class);
}

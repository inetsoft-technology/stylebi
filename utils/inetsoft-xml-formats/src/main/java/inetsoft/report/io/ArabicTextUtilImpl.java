/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
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

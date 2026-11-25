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
package inetsoft.graph.aesthetic;

import inetsoft.graph.rgb.AbstractSplineColorFrame;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the 27 ColorBrewer palette colour frames. The tables are compared with the official
 * ColorBrewer 2.0 tables of Cynthia Brewer (axismaps/colorbrewer, export/colorbrewer.js), written
 * below as concatenated RRGGBB strings per ramp size, first colour first.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class BrewerPaletteFramesTest {
   /** One line per class: class name followed by "size:hex" ramps. */
   private static final String OFFICIAL = """
            BluesColorFrame 3:deebf79ecae13182bd 4:eff3ffbdd7e76baed62171b5 5:eff3ffbdd7e76baed63182bd08519c 6:eff3ffc6dbef9ecae16baed63182bd08519c 7:eff3ffc6dbef9ecae16baed64292c62171b5084594 8:f7fbffdeebf7c6dbef9ecae16baed64292c62171b5084594 9:f7fbffdeebf7c6dbef9ecae16baed64292c62171b508519c08306b
            BrBGColorFrame 3:d8b365f5f5f55ab4ac 4:a6611adfc27d80cdc1018571 5:a6611adfc27df5f5f580cdc1018571 6:8c510ad8b365f6e8c3c7eae55ab4ac01665e 7:8c510ad8b365f6e8c3f5f5f5c7eae55ab4ac01665e 8:8c510abf812ddfc27df6e8c3c7eae580cdc135978f01665e 9:8c510abf812ddfc27df6e8c3f5f5f5c7eae580cdc135978f01665e 10:5430058c510abf812ddfc27df6e8c3c7eae580cdc135978f01665e003c30 11:5430058c510abf812ddfc27df6e8c3f5f5f5c7eae580cdc135978f01665e003c30
            BuGnColorFrame 3:e5f5f999d8c92ca25f 4:edf8fbb2e2e266c2a4238b45 5:edf8fbb2e2e266c2a42ca25f006d2c 6:edf8fbccece699d8c966c2a42ca25f006d2c 7:edf8fbccece699d8c966c2a441ae76238b45005824 8:f7fcfde5f5f9ccece699d8c966c2a441ae76238b45005824 9:f7fcfde5f5f9ccece699d8c966c2a441ae76238b45006d2c00441b
            BuPuColorFrame 3:e0ecf49ebcda8856a7 4:edf8fbb3cde38c96c688419d 5:edf8fbb3cde38c96c68856a7810f7c 6:edf8fbbfd3e69ebcda8c96c68856a7810f7c 7:edf8fbbfd3e69ebcda8c96c68c6bb188419d6e016b 8:f7fcfde0ecf4bfd3e69ebcda8c96c68c6bb188419d6e016b 9:f7fcfde0ecf4bfd3e69ebcda8c96c68c6bb188419d810f7c4d004b
            GnBuColorFrame 3:e0f3dba8ddb543a2ca 4:f0f9e8bae4bc7bccc42b8cbe 5:f0f9e8bae4bc7bccc443a2ca0868ac 6:f0f9e8ccebc5a8ddb57bccc443a2ca0868ac 7:f0f9e8ccebc5a8ddb57bccc44eb3d32b8cbe08589e 8:f7fcf0e0f3dbccebc5a8ddb57bccc44eb3d32b8cbe08589e 9:f7fcf0e0f3dbccebc5a8ddb57bccc44eb3d32b8cbe0868ac084081
            GreensColorFrame 3:e5f5e0a1d99b31a354 4:edf8e9bae4b374c476238b45 5:edf8e9bae4b374c47631a354006d2c 6:edf8e9c7e9c0a1d99b74c47631a354006d2c 7:edf8e9c7e9c0a1d99b74c47641ab5d238b45005a32 8:f7fcf5e5f5e0c7e9c0a1d99b74c47641ab5d238b45005a32 9:f7fcf5e5f5e0c7e9c0a1d99b74c47641ab5d238b45006d2c00441b
            GreysColorFrame 3:f0f0f0bdbdbd636363 4:f7f7f7cccccc969696525252 5:f7f7f7cccccc969696636363252525 6:f7f7f7d9d9d9bdbdbd969696636363252525 7:f7f7f7d9d9d9bdbdbd969696737373525252252525 8:fffffff0f0f0d9d9d9bdbdbd969696737373525252252525 9:fffffff0f0f0d9d9d9bdbdbd969696737373525252252525000000
            OrRdColorFrame 3:fee8c8fdbb84e34a33 4:fef0d9fdcc8afc8d59d7301f 5:fef0d9fdcc8afc8d59e34a33b30000 6:fef0d9fdd49efdbb84fc8d59e34a33b30000 7:fef0d9fdd49efdbb84fc8d59ef6548d7301f990000 8:fff7ecfee8c8fdd49efdbb84fc8d59ef6548d7301f990000 9:fff7ecfee8c8fdd49efdbb84fc8d59ef6548d7301fb300007f0000
            OrangesColorFrame 3:fee6cefdae6be6550d 4:feeddefdbe85fd8d3cd94701 5:feeddefdbe85fd8d3ce6550da63603 6:feeddefdd0a2fdae6bfd8d3ce6550da63603 7:feeddefdd0a2fdae6bfd8d3cf16913d948018c2d04 8:fff5ebfee6cefdd0a2fdae6bfd8d3cf16913d948018c2d04 9:fff5ebfee6cefdd0a2fdae6bfd8d3cf16913d94801a636037f2704
            PRGnColorFrame 3:af8dc3f7f7f77fbf7b 4:7b3294c2a5cfa6dba0008837 5:7b3294c2a5cff7f7f7a6dba0008837 6:762a83af8dc3e7d4e8d9f0d37fbf7b1b7837 7:762a83af8dc3e7d4e8f7f7f7d9f0d37fbf7b1b7837 8:762a839970abc2a5cfe7d4e8d9f0d3a6dba05aae611b7837 9:762a839970abc2a5cfe7d4e8f7f7f7d9f0d3a6dba05aae611b7837 10:40004b762a839970abc2a5cfe7d4e8d9f0d3a6dba05aae611b783700441b 11:40004b762a839970abc2a5cfe7d4e8f7f7f7d9f0d3a6dba05aae611b783700441b
            PiYGColorFrame 3:e9a3c9f7f7f7a1d76a 4:d01c8bf1b6dab8e1864dac26 5:d01c8bf1b6daf7f7f7b8e1864dac26 6:c51b7de9a3c9fde0efe6f5d0a1d76a4d9221 7:c51b7de9a3c9fde0eff7f7f7e6f5d0a1d76a4d9221 8:c51b7dde77aef1b6dafde0efe6f5d0b8e1867fbc414d9221 9:c51b7dde77aef1b6dafde0eff7f7f7e6f5d0b8e1867fbc414d9221 10:8e0152c51b7dde77aef1b6dafde0efe6f5d0b8e1867fbc414d9221276419 11:8e0152c51b7dde77aef1b6dafde0eff7f7f7e6f5d0b8e1867fbc414d9221276419
            PuBuColorFrame 3:ece7f2a6bddb2b8cbe 4:f1eef6bdc9e174a9cf0570b0 5:f1eef6bdc9e174a9cf2b8cbe045a8d 6:f1eef6d0d1e6a6bddb74a9cf2b8cbe045a8d 7:f1eef6d0d1e6a6bddb74a9cf3690c00570b0034e7b 8:fff7fbece7f2d0d1e6a6bddb74a9cf3690c00570b0034e7b 9:fff7fbece7f2d0d1e6a6bddb74a9cf3690c00570b0045a8d023858
            PuBuGnColorFrame 3:ece2f0a6bddb1c9099 4:f6eff7bdc9e167a9cf02818a 5:f6eff7bdc9e167a9cf1c9099016c59 6:f6eff7d0d1e6a6bddb67a9cf1c9099016c59 7:f6eff7d0d1e6a6bddb67a9cf3690c002818a016450 8:fff7fbece2f0d0d1e6a6bddb67a9cf3690c002818a016450 9:fff7fbece2f0d0d1e6a6bddb67a9cf3690c002818a016c59014636
            PuOrColorFrame 3:f1a340f7f7f7998ec3 4:e66101fdb863b2abd25e3c99 5:e66101fdb863f7f7f7b2abd25e3c99 6:b35806f1a340fee0b6d8daeb998ec3542788 7:b35806f1a340fee0b6f7f7f7d8daeb998ec3542788 8:b35806e08214fdb863fee0b6d8daebb2abd28073ac542788 9:b35806e08214fdb863fee0b6f7f7f7d8daebb2abd28073ac542788 10:7f3b08b35806e08214fdb863fee0b6d8daebb2abd28073ac5427882d004b 11:7f3b08b35806e08214fdb863fee0b6f7f7f7d8daebb2abd28073ac5427882d004b
            PuRdColorFrame 3:e7e1efc994c7dd1c77 4:f1eef6d7b5d8df65b0ce1256 5:f1eef6d7b5d8df65b0dd1c77980043 6:f1eef6d4b9dac994c7df65b0dd1c77980043 7:f1eef6d4b9dac994c7df65b0e7298ace125691003f 8:f7f4f9e7e1efd4b9dac994c7df65b0e7298ace125691003f 9:f7f4f9e7e1efd4b9dac994c7df65b0e7298ace125698004367001f
            PurplesColorFrame 3:efedf5bcbddc756bb1 4:f2f0f7cbc9e29e9ac86a51a3 5:f2f0f7cbc9e29e9ac8756bb154278f 6:f2f0f7dadaebbcbddc9e9ac8756bb154278f 7:f2f0f7dadaebbcbddc9e9ac8807dba6a51a34a1486 8:fcfbfdefedf5dadaebbcbddc9e9ac8807dba6a51a34a1486 9:fcfbfdefedf5dadaebbcbddc9e9ac8807dba6a51a354278f3f007d
            RdBuColorFrame 3:ef8a62f7f7f767a9cf 4:ca0020f4a58292c5de0571b0 5:ca0020f4a582f7f7f792c5de0571b0 6:b2182bef8a62fddbc7d1e5f067a9cf2166ac 7:b2182bef8a62fddbc7f7f7f7d1e5f067a9cf2166ac 8:b2182bd6604df4a582fddbc7d1e5f092c5de4393c32166ac 9:b2182bd6604df4a582fddbc7f7f7f7d1e5f092c5de4393c32166ac 10:67001fb2182bd6604df4a582fddbc7d1e5f092c5de4393c32166ac053061 11:67001fb2182bd6604df4a582fddbc7f7f7f7d1e5f092c5de4393c32166ac053061
            RdGyColorFrame 3:ef8a62ffffff999999 4:ca0020f4a582bababa404040 5:ca0020f4a582ffffffbababa404040 6:b2182bef8a62fddbc7e0e0e09999994d4d4d 7:b2182bef8a62fddbc7ffffffe0e0e09999994d4d4d 8:b2182bd6604df4a582fddbc7e0e0e0bababa8787874d4d4d 9:b2182bd6604df4a582fddbc7ffffffe0e0e0bababa8787874d4d4d 10:67001fb2182bd6604df4a582fddbc7e0e0e0bababa8787874d4d4d1a1a1a 11:67001fb2182bd6604df4a582fddbc7ffffffe0e0e0bababa8787874d4d4d1a1a1a
            RdPuColorFrame 3:fde0ddfa9fb5c51b8a 4:feebe2fbb4b9f768a1ae017e 5:feebe2fbb4b9f768a1c51b8a7a0177 6:feebe2fcc5c0fa9fb5f768a1c51b8a7a0177 7:feebe2fcc5c0fa9fb5f768a1dd3497ae017e7a0177 8:fff7f3fde0ddfcc5c0fa9fb5f768a1dd3497ae017e7a0177 9:fff7f3fde0ddfcc5c0fa9fb5f768a1dd3497ae017e7a017749006a
            RdYlBuColorFrame 3:fc8d59ffffbf91bfdb 4:d7191cfdae61abd9e92c7bb6 5:d7191cfdae61ffffbfabd9e92c7bb6 6:d73027fc8d59fee090e0f3f891bfdb4575b4 7:d73027fc8d59fee090ffffbfe0f3f891bfdb4575b4 8:d73027f46d43fdae61fee090e0f3f8abd9e974add14575b4 9:d73027f46d43fdae61fee090ffffbfe0f3f8abd9e974add14575b4 10:a50026d73027f46d43fdae61fee090e0f3f8abd9e974add14575b4313695 11:a50026d73027f46d43fdae61fee090ffffbfe0f3f8abd9e974add14575b4313695
            RdYlGnColorFrame 3:fc8d59ffffbf91cf60 4:d7191cfdae61a6d96a1a9641 5:d7191cfdae61ffffbfa6d96a1a9641 6:d73027fc8d59fee08bd9ef8b91cf601a9850 7:d73027fc8d59fee08bffffbfd9ef8b91cf601a9850 8:d73027f46d43fdae61fee08bd9ef8ba6d96a66bd631a9850 9:d73027f46d43fdae61fee08bffffbfd9ef8ba6d96a66bd631a9850 10:a50026d73027f46d43fdae61fee08bd9ef8ba6d96a66bd631a9850006837 11:a50026d73027f46d43fdae61fee08bffffbfd9ef8ba6d96a66bd631a9850006837
            RedsColorFrame 3:fee0d2fc9272de2d26 4:fee5d9fcae91fb6a4acb181d 5:fee5d9fcae91fb6a4ade2d26a50f15 6:fee5d9fcbba1fc9272fb6a4ade2d26a50f15 7:fee5d9fcbba1fc9272fb6a4aef3b2ccb181d99000d 8:fff5f0fee0d2fcbba1fc9272fb6a4aef3b2ccb181d99000d 9:fff5f0fee0d2fcbba1fc9272fb6a4aef3b2ccb181da50f1567000d
            SpectralColorFrame 3:fc8d59ffffbf99d594 4:d7191cfdae61abdda42b83ba 5:d7191cfdae61ffffbfabdda42b83ba 6:d53e4ffc8d59fee08be6f59899d5943288bd 7:d53e4ffc8d59fee08bffffbfe6f59899d5943288bd 8:d53e4ff46d43fdae61fee08be6f598abdda466c2a53288bd 9:d53e4ff46d43fdae61fee08bffffbfe6f598abdda466c2a53288bd 10:9e0142d53e4ff46d43fdae61fee08be6f598abdda466c2a53288bd5e4fa2 11:9e0142d53e4ff46d43fdae61fee08bffffbfe6f598abdda466c2a53288bd5e4fa2
            YlGnBuColorFrame 3:edf8b17fcdbb2c7fb8 4:ffffcca1dab441b6c4225ea8 5:ffffcca1dab441b6c42c7fb8253494 6:ffffccc7e9b47fcdbb41b6c42c7fb8253494 7:ffffccc7e9b47fcdbb41b6c41d91c0225ea80c2c84 8:ffffd9edf8b1c7e9b47fcdbb41b6c41d91c0225ea80c2c84 9:ffffd9edf8b1c7e9b47fcdbb41b6c41d91c0225ea8253494081d58
            YlGnColorFrame 3:f7fcb9addd8e31a354 4:ffffccc2e69978c679238443 5:ffffccc2e69978c67931a354006837 6:ffffccd9f0a3addd8e78c67931a354006837 7:ffffccd9f0a3addd8e78c67941ab5d238443005a32 8:ffffe5f7fcb9d9f0a3addd8e78c67941ab5d238443005a32 9:ffffe5f7fcb9d9f0a3addd8e78c67941ab5d238443006837004529
            YlOrBrColorFrame 3:fff7bcfec44fd95f0e 4:ffffd4fed98efe9929cc4c02 5:ffffd4fed98efe9929d95f0e993404 6:ffffd4fee391fec44ffe9929d95f0e993404 7:ffffd4fee391fec44ffe9929ec7014cc4c028c2d04 8:ffffe5fff7bcfee391fec44ffe9929ec7014cc4c028c2d04 9:ffffe5fff7bcfee391fec44ffe9929ec7014cc4c02993404662506
            YlOrRdColorFrame 3:ffeda0feb24cf03b20 4:ffffb2fecc5cfd8d3ce31a1c 5:ffffb2fecc5cfd8d3cf03b20bd0026 6:ffffb2fed976feb24cfd8d3cf03b20bd0026 7:ffffb2fed976feb24cfd8d3cfc4e2ae31a1cb10026 8:ffffccffeda0fed976feb24cfd8d3cfc4e2ae31a1cb10026 9:ffffccffeda0fed976feb24cfd8d3cfc4e2ae31a1cbd0026800026
      """;

   private static final Set<String> DIVERGING = Set.of(
      "BrBGColorFrame", "PiYGColorFrame", "PRGnColorFrame", "PuOrColorFrame", "RdBuColorFrame",
      "RdGyColorFrame", "RdYlBuColorFrame", "RdYlGnColorFrame", "SpectralColorFrame");

   private static final Map<String, Map<Integer, String>> TABLE = new TreeMap<>();

   static {
      for(String line : OFFICIAL.split("\n")) {
         String[] parts = line.trim().split(" ");
         Map<Integer, String> ramps = new TreeMap<>();

         for(int i = 1; i < parts.length; i++) {
            String[] kv = parts[i].split(":");
            ramps.put(Integer.parseInt(kv[0]), kv[1]);
         }

         if(parts[0].equals("PuOrColorFrame")) {
            // PuOr is stored reversed (purple first), as shown by its icon
            ramps.replaceAll((k, v) -> reverse(v));
         }

         TABLE.put(parts[0], ramps);
      }
   }

   private static String reverse(String ramp) {
      StringBuilder sb = new StringBuilder();

      for(int i = ramp.length() / 6 - 1; i >= 0; i--) {
         sb.append(ramp, i * 6, i * 6 + 6);
      }

      return sb.toString();
   }

   static List<String> allClasses() {
      return new ArrayList<>(TABLE.keySet());
   }

   /** All classes except PuOr, whose ramps are the official ones reversed. */
   static List<String> classesWithOfficialOrder() {
      return allClasses().stream().filter(n -> !n.equals("PuOrColorFrame"))
         .collect(Collectors.toList());
   }

   static List<String> sequentialClasses() {
      return allClasses().stream().filter(n -> !DIVERGING.contains(n))
         .collect(Collectors.toList());
   }

   static List<String> divergingClasses() {
      return allClasses().stream().filter(DIVERGING::contains).collect(Collectors.toList());
   }

   private static AbstractSplineColorFrame create(String name) throws Exception {
      return (AbstractSplineColorFrame) Class.forName("inetsoft.graph.aesthetic." + name)
         .getDeclaredConstructor().newInstance();
   }

   private static String[] ramps(String name) throws Exception {
      Method m = Class.forName("inetsoft.graph.aesthetic." + name).getDeclaredMethod("getColorRamps");
      m.setAccessible(true);
      return (String[]) m.invoke(create(name));
   }

   private static Color color(String hex, int index) {
      return new Color(Integer.parseInt(hex.substring(index * 6, index * 6 + 6), 16));
   }

   private static Color first(String name) {
      String largest = TABLE.get(name).values().stream().reduce((a, b) -> b).get();
      return color(largest, 0);
   }

   private static Color last(String name) {
      String largest = TABLE.get(name).values().stream().reduce((a, b) -> b).get();
      return color(largest, largest.length() / 6 - 1);
   }

   private static void assertWithinOne(Color expected, Color actual, String message) {
      assertNotNull(actual, message);
      assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= 1 &&
                    Math.abs(expected.getGreen() - actual.getGreen()) <= 1 &&
                    Math.abs(expected.getBlue() - actual.getBlue()) <= 1,
                 message + ": expected " + expected + " within 1 of " + actual);
   }

   private static double luma(Color c) {
      return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
   }

   private static double[] lumas(String ramp) {
      double[] y = new double[ramp.length() / 6];

      for(int i = 0; i < y.length; i++) {
         y[i] = luma(color(ramp, i));
      }

      return y;
   }

   @Test
   void tableHas27Classes() {
      assertEquals(27, TABLE.size());
      assertEquals(18, sequentialClasses().size());
      assertEquals(9, divergingClasses().size());
   }

   @ParameterizedTest
   @MethodSource("classesWithOfficialOrder")
   @DisplayName("ramp tables equal the official ColorBrewer ramps")
   void rampsEqualOfficialTable(String name) throws Exception {
      String[] actual = ramps(name);
      Map<Integer, String> official = TABLE.get(name);
      assertEquals(official.size(), actual.length, name + " ramp count");

      for(int i = 0; i < actual.length; i++) {
         assertEquals(official.get(i + 3), actual[i], name + " ramp of " + (i + 3) + " colours");
      }
   }

   @Test
   @DisplayName("PuOr ramps are the official ramps reversed (purple first, as its icon shows)")
   void puOrRampsAreReversedOfficialRamps() throws Exception {
      String[] actual = ramps("PuOrColorFrame");
      assertEquals(9, actual.length);
      assertTrue(actual[8].startsWith("2d004b"));
      assertTrue(actual[8].endsWith("7f3b08"));

      for(int i = 0; i < actual.length; i++) {
         assertEquals(TABLE.get("PuOrColorFrame").get(i + 3), actual[i],
                      "PuOr ramp of " + (i + 3) + " colours");
      }
   }

   @ParameterizedTest
   @MethodSource("allClasses")
   @DisplayName("sequential classes have 7 ramps of 3..9 colours, diverging 9 ramps of 3..11")
   void rampSizes(String name) throws Exception {
      String[] actual = ramps(name);
      int expectedCount = DIVERGING.contains(name) ? 9 : 7;
      assertEquals(expectedCount, actual.length, name);

      for(int i = 0; i < actual.length; i++) {
         assertEquals(i + 3, actual[i].length() / 6, name + " ramp " + i);
         assertEquals(0, actual[i].length() % 6, name + " ramp " + i);
      }
   }

   @ParameterizedTest
   @MethodSource("allClasses")
   @DisplayName("default frame starts at the first colour of the largest ramp (within 1)")
   void defaultFrameStartsAtLargestRamp(String name) throws Exception {
      assertWithinOne(first(name), create(name).getColor(0.0), name);
   }

   @ParameterizedTest
   @MethodSource("allClasses")
   @DisplayName("first colour is exact")
   void firstColourIsExact(String name) throws Exception {
      assertEquals(first(name), create(name).getColor(0.0), name);
   }

   @ParameterizedTest
   @MethodSource("allClasses")
   @DisplayName("last colour is exact")
   void lastColourIsExact(String name) throws Exception {
      assertEquals(last(name), create(name).getColor(1.0), name);
   }

   @ParameterizedTest
   @MethodSource("allClasses")
   @DisplayName("object path maps the scale ends to the first and last colour (within 1)")
   void objectPathEnds(String name) throws Exception {
      AbstractSplineColorFrame f = create(name);
      f.setScale(new LinearScale(0, 100));
      assertWithinOne(first(name), f.getColor((Object) Double.valueOf(0)), name + " at 0");
      assertWithinOne(last(name), f.getColor((Object) Double.valueOf(100)), name + " at 100");
   }

   @Test
   void allLargestAndSmallestTablesAreDistinct() throws Exception {
      Set<String> largest = new HashSet<>();
      Set<String> smallest = new HashSet<>();
      List<AbstractSplineColorFrame> frames = new ArrayList<>();

      for(String name : allClasses()) {
         String[] r = ramps(name);
         assertTrue(largest.add(r[r.length - 1]), name + " largest ramp is shared");
         assertTrue(smallest.add(r[0]), name + " smallest ramp is shared");
         frames.add(create(name));
      }

      for(int i = 0; i < frames.size(); i++) {
         for(int j = i + 1; j < frames.size(); j++) {
            assertNotEquals(frames.get(i), frames.get(j));
         }
      }
   }

   @ParameterizedTest
   @MethodSource("sequentialClasses")
   @DisplayName("sequential ramps run from light to dark")
   void sequentialRampsGetDarker(String name) throws Exception {
      for(String ramp : ramps(name)) {
         double[] y = lumas(ramp);

         for(int i = 1; i < y.length; i++) {
            assertTrue(y[i] <= y[i - 1] + 1e-9, name + " " + ramp + " rises at colour " + i);
         }
      }
   }

   @ParameterizedTest
   @MethodSource("divergingClasses")
   @DisplayName("diverging ramps with an odd size have their lightest colour in the middle")
   void divergingOddRampsHaveLightCentre(String name) throws Exception {
      for(String ramp : ramps(name)) {
         double[] y = lumas(ramp);

         if(y.length % 2 == 0) {
            continue;
         }

         int mid = y.length / 2;

         for(int i = 1; i <= mid; i++) {
            assertTrue(y[i] >= y[i - 1] - 1e-9, name + " " + ramp + " falls before the middle");
         }

         for(int i = mid + 1; i < y.length; i++) {
            assertTrue(y[i] <= y[i - 1] + 1e-9, name + " " + ramp + " rises after the middle");
         }

         for(double v : y) {
            assertTrue(v <= y[mid] + 1e-9, name + " " + ramp + " middle is not the lightest");
         }
      }
   }

   @ParameterizedTest
   @MethodSource("divergingClasses")
   @DisplayName("diverging ramps with an even size rise to one peak and then fall")
   void divergingEvenRampsAreUnimodal(String name) throws Exception {
      for(String ramp : ramps(name)) {
         double[] y = lumas(ramp);

         if(y.length % 2 != 0) {
            continue;
         }

         int peak = 0;

         for(int i = 1; i < y.length; i++) {
            if(y[i] > y[peak]) {
               peak = i;
            }
         }

         for(int i = 1; i <= peak; i++) {
            assertTrue(y[i] >= y[i - 1] - 1e-9, name + " " + ramp + " falls before the peak");
         }

         for(int i = peak + 1; i < y.length; i++) {
            assertTrue(y[i] <= y[i - 1] + 1e-9, name + " " + ramp + " has a second peak");
         }
      }
   }

   @ParameterizedTest
   @MethodSource("allClasses")
   @DisplayName("each wrapper creates exactly its own frame class")
   void wrapperCreatesItsOwnFrame(String name) throws Exception {
      Class<?> wrapperClass = Class.forName(
         "inetsoft.uql.viewsheet.graph.aesthetic." + name + "Wrapper");
      VisualFrameWrapper wrapper =
         (VisualFrameWrapper) wrapperClass.getDeclaredConstructor().newInstance();
      assertSame(Class.forName("inetsoft.graph.aesthetic." + name),
                 wrapper.getVisualFrame().getClass(), name);
   }

   @ParameterizedTest
   @MethodSource("allClasses")
   @DisplayName("palette frames are linear frames, opaque, and not bound to a field")
   void paletteFramesAreLinearFrames(String name) throws Exception {
      AbstractSplineColorFrame f = create(name);
      assertTrue(f instanceof LinearColorFrame);
      assertEquals(255, f.getColor(0.5).getAlpha());
      assertNull(f.getField());
   }
}

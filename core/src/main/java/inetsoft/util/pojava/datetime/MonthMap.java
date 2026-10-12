package inetsoft.util.pojava.datetime;

/*
 From org.pojava.datetime 3.0.0 by John Pile, moved to inetsoft.util.pojava.datetime by
 InetSoft Technology in 2026.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

import java.text.DateFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * This is a language-agnostic mapping of month abbreviations to month numbers
 */
public class MonthMap {

    private Map<String, Integer> monthMap = new HashMap<String, Integer>();

    public MonthMap() {
    }

    public static MonthMap fromAllLocales() {
        MonthMap newMonthMap = new MonthMap();
        Locale[] locales = DateFormatSymbols.getAvailableLocales();
        for (Locale locale : locales) {
            DateFormatSymbols dfs = DateFormatSymbols.getInstance(locale);
            String[] longMonths = dfs.getMonths();
            String[] shortMonths = dfs.getShortMonths();
            for (int i = 0; i < 12; i++) {
                String shortMonth = shortMonths[i].toUpperCase();
                String shortMonth4 = shortMonths[i].toUpperCase().substring(0, Math.min(4, shortMonths[i].length()));
                String longMonth = longMonths[i].toUpperCase();
                if (longMonth.startsWith(shortMonth4) || shortMonth4.endsWith(".")) {
                    // If a truncated month name matches its 4-char abbrev, we'll use it.
                    // The goal is to support both standard and non-standard abbreviations like "Octob".
                    newMonthMap.addMonth(shortMonth4, i);
                    // Finland's 11th month starts with "MAR" which collides with EN on 3-char abbrevs.
                    // Except for that, we'll recognize 3-char abbreviations
                    if (shortMonth4.length() == 4 && !locale.toString().startsWith("fi")) {
                        newMonthMap.addMonth(shortMonth.substring(0, 3), i);
                    }
                } else {
                    // Otherwise, we'll strictly match the abbreviation and full name
                    newMonthMap.addMonth(shortMonth, i);
                    newMonthMap.addMonth(longMonth, i);
                }
            }
        }
        return newMonthMap;
    }

    /**
     * Returns a multi-language lookup based on the first 3 or 4 characters of a month
     *
     * @param candidateMonth A word whose first 3 or 4 letters match any known month abbreviation
     * @return null if no match found, or an Integer between 1 and 12
     */
    public Integer monthIndex(String candidateMonth) {
        if (candidateMonth == null) {
            return null;
        }
        candidateMonth = candidateMonth.toUpperCase();
        // First, try for an exact match
        Integer monthIndex = monthMap.get(candidateMonth);
        // If no match, try an abbrev of four characters if possible
        if (monthIndex == null && candidateMonth.length() > 4) {
            monthIndex = monthMap.get(candidateMonth.substring(0, 4));
        }
        // If still no match, truncate to 3 characters and retry
        // Consider where candidateMonth="DEC." and abbrev="DEC".
        if (monthIndex == null && candidateMonth.length() > 3) {
            monthIndex = monthMap.get(candidateMonth.substring(0, 3));
        }
        return monthIndex;
    }

    /**
     * Registers a new month abbreviation.  Will ignore duplicates.
     *
     * @param abbrev      Month abbreviation (must be either 3 or 4 characters)
     * @param monthNumber In range 1..12
     */
    public void addMonth(String abbrev, Integer monthNumber) {
        abbrev = abbrev.toUpperCase();
        if (!monthMap.containsKey(abbrev)) {
            monthMap.put(abbrev, monthNumber);
        }
    }

    public boolean isEmpty() {
        return monthMap == null || monthMap.isEmpty();
    }
}

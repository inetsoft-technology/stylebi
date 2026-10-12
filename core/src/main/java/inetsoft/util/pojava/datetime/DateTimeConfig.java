package inetsoft.util.pojava.datetime;

/*
 Copyright 2008-14 John Pile

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

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Establish global defaults for shaping DateTime behavior. This version supports English,
 * German, French and Spanish month names in the date parser, and can be customized by your
 * applications to interpret other languages.
 *
 * @author John Pile
 */
public class DateTimeConfig implements IDateTimeConfig, Serializable {

    /**
     * Compulsory serial ID.
     */
    private static final long serialVersionUID = 3L;

    /**
     * Singleton pattern. The globalDefault variable is referenced by DateTime, so changes you
     * make here affect new calls to DateTime.
     */
    private static IDateTimeConfig globalDefault = null;

    /**
     * This determines the default interpretation of a ##/##/#### date, whether Day precedes
     * Month or vice versa.
     */
    private boolean isDmyOrder = false;

    private boolean isUnspecifiedCenturyAlwaysInPast = false;

    /**
     * The 1970-01-01 epoch started on a Thursday. If Sunday is the start of a week, then this
     * number is 4. If Monday is the start, then set to 3.
     */
    private int epochDOW = 4;

    /**
     * The default date format used for DateTime.toString();
     */
    private String format = "yyyy-MM-dd HH:mm:ss";

    private String defaultJdbcFormat = "yyyy-MM-dd HH:mm:ss.SSS";

    private TimeZone inputTimeZone = TimeZone.getDefault();

    private TimeZone outputTimeZone = TimeZone.getDefault();

    private Locale locale = Locale.getDefault();

    private String bcPrefix = "-";

    private MonthMap monthMap;

    /**
     * <p>
     * Support parsing of zones unlisted in TimeZone by translating to known zones. Got a zone
     * that's not supported or should be overridden? Fix it locally by updating your custom
     * tzMap!
     * </p>
     * <p/>
     * <pre>
     * // Example change CST from U.S. Central to Chinese.
     * class CustomTzMap {
     * 	private static Map&lt;String, String&amp;gt tzMap = DateTimeConfig.getTzMap();
     * 	static {
     * 		tzMap.put(&quot;CST&quot;, &quot;Asia/Hong_Kong&quot;);
     *    }
     * }
     * </pre>
     */
    private final Map<String, String> tzMap = new HashMap<String, String>();

    private final Map<String, TimeZone> tzCache = new HashMap<String, TimeZone>();


    /**
     * Reset the global default to a different DateTimeConfig object.
     *
     * @param globalDefault Set this DateTimeConfig instance as the global default.
     */
    public static void setGlobalDefault(IDateTimeConfig globalDefault) {
        if (globalDefault != null) {
            globalDefault.validate();
        }
        synchronized (DateTimeConfig.class) {
            DateTimeConfig.globalDefault = globalDefault;
        }
    }

    /**
     * @return The singleton used as the default DateTimeConfig.
     */
    public static IDateTimeConfig getGlobalDefault() {
        if (globalDefault == null) {
            synchronized (DateTimeConfig.class) {
                if (globalDefault == null) {
                    globalDefault = defaultDateTimeConfig();
                }
            }
        }
        return globalDefault;
    }

    private static DateTimeConfig defaultDateTimeConfig() {
        TimeZone tz = TimeZone.getDefault();
        DateTimeConfig dtc = new DateTimeConfig();
        dtc.monthMap = MonthMap.fromAllLocales();
        dtc.tzMap.put("Z", "UTC");
        dtc.tzCache.put(tz.getID(), tz);
        dtc.validate();
        DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());
        if (df instanceof SimpleDateFormat) {
            String pattern = ((SimpleDateFormat) df).toPattern();
            dtc.isDmyOrder = !pattern.startsWith("M");
        }
        return dtc;
    }

    /**
     * Returns true if 01/02/1970 is interpreted as 1970-02-01, returns false if 01/02/1970 is
     * interpreted as 1970-01-02.
     *
     * @return True if DD/MM/YYYY is recognized by parser over MM/DD/YYYY.
     */
    public boolean isDmyOrder() {
        return this.isDmyOrder;
    }

    /**
     * @return a Map of time zones recognized by DateTime.
     */
    public Map<String, String> getTzMap() {
        return this.tzMap;
    }

    /**
     * Merge a Map of time zones recognized by DateTime
     *
     * @param tzMap
     */
    public void addTzMap(Map<String, String> tzMap) {
        this.tzMap.putAll(tzMap);
    }

    /**
     * Ensure that object is complete and consistent
     */
    public void validate() {
        if (this.monthMap == null || this.monthMap.isEmpty()) {
            throw new IllegalStateException("Month Map is required.");
        }
        if (this.inputTimeZone == null) {
            throw new IllegalStateException("Input TimeZone must be non-null.");
        }
        if (this.outputTimeZone == null) {
            throw new IllegalStateException("Output TimeZone must be non-null.");
        }
    }

    /**
     * Get the day of week offset on the epoch date. This is used to calculate the day of week
     * for all other dates.
     *
     * @return Day of week offset of the epoch date.
     */
    public int getEpochDOW() {
        return epochDOW;
    }

    /**
     * Get the default date format.
     *
     * @return A format string for dates.
     */
    public String getFormat() {
        return format;
    }

    /**
     * Get the default JDBC date format.
     *
     * @return the default format desired for JDBC.
     */
    public String getDefaultJdbcFormat() {
        return defaultJdbcFormat;
    }

    /**
     * Lookup the TimeZone, including custom time zones.
     */
    public TimeZone lookupTimeZone(String id) {
        TimeZone tz;
        if (id == null) {
            tz = this.inputTimeZone;
        } else if (!tzCache.containsKey(id)) {
            tz = TimeZone.getTimeZone(id);
            // TimeZone defaults to GMT if it can't match a parsed timezone.
            if ("GMT".equals(tz.getID())) {
                if (!("GMT".equals(id) || "UTC".equals(id) || "CUT".equals(id) || "Z".equals(id) || "WET".equals(id))) {
                    // If it's GMT due to parse error, we'll default to input time.
                    return inputTimeZone;
                }
            }
            tzCache.put(id, tz);
        } else {
            tz = tzCache.get(id);
        }
        return tz;
    }

    @Override
    public Integer lookupMonthIndex(String monthNameOrAbbreviation) {
        return monthMap.monthIndex(monthNameOrAbbreviation);
    }

    /**
     * @return Input TimeZone default for parser.
     */
    public TimeZone getInputTimeZone() {
        return inputTimeZone;
    }

    /**
     * @return Default TimeZone for DateTime.toString formatter.
     */
    public TimeZone getOutputTimeZone() {
        return outputTimeZone;
    }

    /**
     * Locale under which toString words are translated
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * @return When true, a date missing century is always assumed to be a past date
     */
    public boolean isUnspecifiedCenturyAlwaysInPast() {
        return isUnspecifiedCenturyAlwaysInPast;
    }

    public String getBcPrefix() {
        return bcPrefix;
    }

    public long systemTime() {
        return System.currentTimeMillis();
    }

    public static void setGlobalDefaultFromBuilder(DateTimeConfigBuilder builder) {
        globalDefault = fromBuilder(builder);
    }

    public static DateTimeConfig fromBuilder(DateTimeConfigBuilder builder) {
        DateTimeConfig dtc = new DateTimeConfig();
        dtc.monthMap = builder.getMonthMap();
        dtc.bcPrefix = builder.getBcPrefix();
        dtc.defaultJdbcFormat = builder.getDefaultJdbcFormat();
        dtc.epochDOW = builder.getEpochDOW();
        dtc.format = builder.getFormat();
        dtc.inputTimeZone = builder.getInputTimeZone();
        dtc.isDmyOrder = builder.isDmyOrder();
        dtc.isUnspecifiedCenturyAlwaysInPast = builder.isUnspecifiedCenturyAlwaysInPast();
        dtc.locale = builder.getLocale();
        dtc.outputTimeZone = builder.getOutputTimeZone();
        dtc.tzMap.putAll(builder.getTzMap());
        dtc.tzCache.putAll(builder.getTzCache());
        dtc.validate();
        return dtc;
    }

}

package inetsoft.util.pojava.datetime;

/*
 Copyright 2008-09 John Pile

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

/**
 * A CalendarUnit represents a time interval whose duration is allowed to vary in order to
 * adjust to Daylight Saving Time where needed.
 * <p/>
 * The fundamental difference between CalendarUnit and Duration is that calculations performed
 * with CalendarUnit will seek to match an equivalent time of day should a DST to non-DST
 * boundary be crossed, whereas calculations with Duration will simply add a fixed unit of time.
 *
 * @author John Pile
 */
public enum CalendarUnit {
    NANOSECOND, /* A NANOSECOND = a billionth of a second */
    MICROSECOND, /* A MICROSECOND = a millionth of a second */
    MILLISECOND, /* A MILLISECOND = a thousandth of a second */
    SECOND, /* A SECOND = Well, you probably know what a second is... */
    MINUTE, /* A MINUTE = sixty seconds (there are no leap seconds in system time) */
    HOUR, /* An HOUR = a shift of one hour on the clock, regardless of elapsed time. */
    DAY, /* A DAY = a shift to the same time on the next calendar day */
    WEEK, /* A WEEK = a shift to the same time offset by 7 calendar days */
    MONTH, /* A MONTH = a shift to the same day of the month for a different month */
    QUARTER, /* A QUARTER = a shift of three calendar months */
    YEAR, /* A YEAR = a shift of one calendar year */
    CENTURY
    /* A CENTURY = a shift of one hundred calendar years */
}

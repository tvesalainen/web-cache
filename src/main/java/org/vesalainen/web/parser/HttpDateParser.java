/*
 * Copyright (C) 2016 tkv
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.vesalainen.web.parser;

import org.vesalainen.parser.GenClassFactory;
import org.vesalainen.parser.annotation.GenClassname;
import org.vesalainen.parser.annotation.GrammarDef;
import org.vesalainen.parser.annotation.ParseMethod;
import org.vesalainen.parser.annotation.Rule;
import org.vesalainen.parser.annotation.Rules;
import org.vesalainen.parser.annotation.Terminal;
import org.vesalainen.parser.annotation.Terminals;
import org.vesalainen.time.SimpleMutableDate;
import org.vesalainen.web.cache.Method;

/**
 *
 * @author tkv
 */
@GenClassname("org.vesalainen.web.parser.HttpDateParserImpl")
@GrammarDef()
@Terminals({
@Terminal(left="string", expression="[A-Za-z]+"),
@Terminal(left="SP", expression="[ \t]+")
})
public abstract class HttpDateParser
{
    @Rules({
    @Rule("fixdate"),
    @Rule("rfc850"),
    @Rule("asctime")
    })
    protected SimpleMutableDate date(SimpleMutableDate date)
    {
        return date;
    }
    @Rule("string '\\,' SP integer SP month SP integer SP integer ':' integer ':' integer SP 'GMT'")
    protected SimpleMutableDate fixdate(int day, int month, int year, int hour, int minute, int second)
    {
        SimpleMutableDate smt = new SimpleMutableDate();
        smt.setDate(year, month, day);
        smt.setHour(hour);
        smt.setMinute(minute);
        smt.setSecond(second);
        return smt;
    }
    @Rule("string '\\,' SP integer '\\-' month '\\-' integer SP integer ':' integer ':' integer SP 'GMT'")
    protected SimpleMutableDate rfc850(int day, int month, int year, int hour, int minute, int second)
    {
        SimpleMutableDate smt = new SimpleMutableDate();
        smt.setDate(year, month, day);
        smt.setHour(hour);
        smt.setMinute(minute);
        smt.setSecond(second);
        return smt;
    }
    @Rule("string SP month SP integer SP integer ':' integer ':' integer SP integer")
    protected SimpleMutableDate asctime(int month, int day, int hour, int minute, int second, int year)
    {
        SimpleMutableDate smt = new SimpleMutableDate();
        smt.setDate(year, month, day);
        smt.setHour(hour);
        smt.setMinute(minute);
        smt.setSecond(second);
        return smt;
    }
    @Terminal(expression="[0-9]+")
    protected int integer(int value)
    {
        return value;
    }
    @Rules({
    @Rule("jan"),
    @Rule("feb"),
    @Rule("mar"),
    @Rule("apr"),
    @Rule("may"),
    @Rule("jun"),
    @Rule("jul"),
    @Rule("aug"),
    @Rule("sep"),
    @Rule("oct"),
    @Rule("nov"),
    @Rule("dec")
    })
    protected int month(int month)
    {
        return month;
    }
    
    @Terminal(expression="Jan")
    protected int jan()
    {
        return 1;
    }
    @Terminal(expression="Feb")
    protected int feb()
    {
        return 2;
    }
    @Terminal(expression="Mar")
    protected int mar()
    {
        return 3;
    }
    @Terminal(expression="Apr")
    protected int apr()
    {
        return 4;
    }
    @Terminal(expression="May")
    protected int may()
    {
        return 5;
    }
    @Terminal(expression="Jun")
    protected int jun()
    {
        return 6;
    }
    @Terminal(expression="Jul")
    protected int jul()
    {
        return 7;
    }
    @Terminal(expression="Aug")
    protected int aug()
    {
        return 8;
    }
    @Terminal(expression="Sep")
    protected int sep()
    {
        return 9;
    }
    @Terminal(expression="Oct")
    protected int oct()
    {
        return 10;
    }
    @Terminal(expression="Nov")
    protected int nov()
    {
        return 11;
    }
    @Terminal(expression="Dec")
    protected int dec()
    {
        return 12;
    }
    @ParseMethod(start="date")
    protected abstract SimpleMutableDate parse(CharSequence text);
    
    public static HttpDateParser getInstance()
    {
        return (HttpDateParser) GenClassFactory.getGenInstance(HttpDateParser.class);
    }

}

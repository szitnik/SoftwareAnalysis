/*
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.util;

import java.util.stream.Stream;

/**
 * StringJoiner is used to construct a sequence of characters separated
 * by an infix delimiter and optionally starting with a supplied prefix
 * and ending with a supplied suffix.
 *
 * For example, the String {@code "[George:Sally:Fred]"} may
 * be constructed as follows:
<code><pre>

  StringJoiner sj = new StringJoiner( ":", "[", "]" );
  sj.add( "George" ).add( "Sally" ).add( "Fred" );
  String desiredString = sj.toString();

</pre></code>
 *
 * Prior to adding something to the StringJoiner, {@code sj.toString()}
 * will, by default, return {@code prefix+suffix}.  However, if the
 * {@code emptyOutput} parameter is supplied via the setEmptyOutput method,
 * the value supplied will be returned instead. This can be used, for example,
 * when creating a string using set notation to indicate an empty set, i.e. "{}",
 * where the prefix is "{", the suffix is "}" and nothing has been added to the
 * StringJoiner.
 *
 * A StringJoiner may be employed to create formatted output from a
 * collection using lambda expressions.  For example,
 *
 *
 <code><pre>
      List<Person> people = ...
      String commaSeparatedNames =
          people.map( p -> p.getName() )
              .into( new StringJoiner(", ") )
              .toString();
 </pre></code>
 *
 * @author Jim Gish
 * @since  1.8
*/
public class StringJoiner {
    private final String prefix;
    private final String infix;
    private final String suffix;

    /*
     * StringBuilder value -- at any time, the characters constructed from the
     * prefix, the added element separated by the infix, but without the suffix,
     * so that we can more easily add elements without having to jigger the
     * suffix each time.
     */
    private StringBuilder value;

    /*
     * By default, the string consisting of prefix+suffix, returned by toString(),
     * or properties of value, when no elements have yet been added, i.e. when it
     * is empty.  This may be overridden by the user to be some other value
     * including the empty String.
     */
    private String emptyOutput;

    /**
     * Constructs a string joiner with no characters in it with no prefix or
     * suffix and using the supplied infix delimiter.  Also, if no characters
     * are added to the StringJoiner and methods accessing the value of it are
     * invoked, it will not return a prefix or suffix (or properties thereof)
     * in the result, unless {@code setEmptyOutput} has first been called.
     *
     * @param infix the sequence of characters to be used between each element
     * added to the StringJoiner value
     * @throws NullPointerException if infix is null
     */
    public StringJoiner(CharSequence infix) {
        this(infix, "", "");
    }

    /**
     * Constructs a string joiner with no characters in it and using the
     * supplied prefix, infix and suffix. Also, if no characters are added to
     * the StringJoiner and methods accessing the string value of it are
     * invoked, it will return the prefix+suffix (or properties thereof) in the
     * result, unless {@code setEmptyOutput} has first been called.
     *
     * @param infix the sequence of characters to be used between each element
     *              added to the StringJoiner
     * @param prefix the sequence of characters to be used at the beginning
     * @param suffix the sequence of characters to be used at the end
     * @throws NullPointerException if prefix, infix, or suffix is null
     */
    public StringJoiner(CharSequence infix, CharSequence prefix, CharSequence suffix) {
        Objects.requireNonNull(prefix, "The prefix must not be null");
        Objects.requireNonNull(infix, "The infix delimiter must not be null");
        Objects.requireNonNull(suffix, "The suffix must not be null");
        // make defensive copies of arguments
        this.prefix = prefix.toString();
        this.infix = infix.toString();
        this.suffix = suffix.toString();
        this.emptyOutput = this.prefix + this.suffix;
    }

    /**
     * Sets the sequence of characters to be used when dertermine the string
     * representation of this StringJoiner and no elements have been added yet,
     * i.e. when it is empty.  Note that once an add method has been called,
     * the StringJoiner is no longer considered empty, even if the element(s)
     * added correspond to the empty String.
     *
     * @param emptyOutput the characters to return as the value of an empty
     *                    StringJoiner
     * @return this StringJoiner itself so the calls may be chained
     * @throws NullPointerException when the emptyOutput parameter is null
     */
    public StringJoiner setEmptyOutput(CharSequence emptyOutput) {
        this.emptyOutput = Objects.requireNonNull(emptyOutput, "The empty output value must not be null").toString();
        return this;
    }

    /**
     * Returns the current value, consisting of the prefix, the values added so
     * far separated by the infix delimiter, and the suffix, unless no elements
     * have been added in which case, the prefix+suffix or the emptyOutput
     * characters are returned
     *
     * @return the string representation of this StringJoiner
     */
    @Override
    public String toString() {
        return (value != null ? value.toString() + suffix : emptyOutput);
    }

    /**
     * add the supplied CharSequence value as the next element of the StringJoiner value.
     * If newElement is null, then {@code "null"} is added.
     *
     * @param newElement The element to add
     * @return a reference to this StringJoiner
     */
    public StringJoiner add(CharSequence newElement) {
        prepareBuilder().append(newElement);
        return this;
    }

    private StringBuilder prepareBuilder() {
        if (value != null) {
            value.append(infix);
        } else {
            value = new StringBuilder().append(prefix);
        }
        return value;
    }

   /**
    * The length of the StringJoiner value.  i.e. the length of String
    * representation of the StringJoiner. Note that if no add methods have been
    * called, then the length of the String representation (either
    * prefix+suffix or emptyOutput) will be returned. The value should be
    * equivalent to toString().length().
    *
    * @return the length of the current value of StringJoiner
    */
    public int length() {
        // Remember that we never actually append the suffix unless we return
        // the full (present) value or some sub-string or length of it, so that
        // we can add on more if we need to.
        return (value != null ? value.length() + suffix.length() : emptyOutput.length());
    }
}

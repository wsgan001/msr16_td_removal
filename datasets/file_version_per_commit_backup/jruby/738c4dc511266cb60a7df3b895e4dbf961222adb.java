/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2006 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 David Corbin <dcorbin@users.sourceforge.net>
 * Copyright (C) 2006 Antti Karanta <antti.karanta@napa.fi>
 * Copyright (C) 2007 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.runtime.Block;
import org.jruby.runtime.BlockBody;
import org.jruby.runtime.ClassIndex;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.UnmarshalStream;
import org.jruby.util.ConvertBytes;
import org.jruby.util.Numeric;
import org.jruby.util.TypeCoercer;
import static org.jruby.CompatVersion.*;

/** 
 * Implementation of the Fixnum class.
 */
@JRubyClass(name="Fixnum", parent="Integer", include="Precision")
public class RubyFixnum extends RubyInteger {
    
    public static RubyClass createFixnumClass(Ruby runtime) {
        RubyClass fixnum = runtime.defineClass("Fixnum", runtime.getInteger(),
                ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        runtime.setFixnum(fixnum);

        fixnum.index = ClassIndex.FIXNUM;
        fixnum.setReifiedClass(RubyFixnum.class);
        
        fixnum.kindOf = new RubyModule.KindOf() {
            @Override
            public boolean isKindOf(IRubyObject obj, RubyModule type) {
                return obj instanceof RubyFixnum;
            }
        };

        if (!runtime.is1_9()) {
            fixnum.includeModule(runtime.getPrecision());
        }

        fixnum.defineAnnotatedMethods(RubyFixnum.class);
        
        for (int i = 0; i < runtime.fixnumCache.length; i++) {
            runtime.fixnumCache[i] = new RubyFixnum(fixnum, i - CACHE_OFFSET);
        }

        return fixnum;
    }
    
    private final long value;
    private static final int BIT_SIZE = 64;
    public static final long SIGN_BIT = (1L << (BIT_SIZE - 1));
    public static final long MAX = (1L<<(BIT_SIZE - 1)) - 1;
    public static final long MIN = -1 * MAX - 1;
    public static final long MAX_MARSHAL_FIXNUM = (1L << 30) - 1; // 0x3fff_ffff
    public static final long MIN_MARSHAL_FIXNUM = - (1L << 30);   // -0x4000_0000
    public static final int CACHE_OFFSET = 256;

    private static IRubyObject fixCoerce(IRubyObject x) {
        do {
            x = x.convertToInteger();
        } while (!(x instanceof RubyFixnum) && !(x instanceof RubyBignum));
        return x;
    }
    
    private static IRubyObject bitCoerce(IRubyObject x) {
        do {
            if (x instanceof RubyFloat)
                throw x.getRuntime().newTypeError("can't convert Float to Integer");
            x = x.convertToInteger("to_int");
        } while (!(x instanceof RubyFixnum) && !(x instanceof RubyBignum));
        return x;
    }
    
    public RubyFixnum(Ruby runtime) {
        this(runtime, 0);
    }

    public RubyFixnum(Ruby runtime, long value) {
        super(runtime.getFixnum());
        this.value = value;
    }
    
    private RubyFixnum(RubyClass klazz, long value) {
        super(klazz);
        this.value = value;
    }
    
    @Override
    public int getNativeTypeIndex() {
        return ClassIndex.FIXNUM;
    }
    
    /** 
     * short circuit for Fixnum key comparison
     */
    @Override
    public final boolean eql(IRubyObject other) {
        return other instanceof RubyFixnum && value == ((RubyFixnum)other).value;
    }

    @Override
    public IRubyObject equal_p(ThreadContext context, IRubyObject obj) {
        return context.getRuntime().newBoolean(this == obj || eql(obj));
    }
    
    @Override
    public boolean isImmediate() {
    	return true;
    }
    
    @Override
    public RubyClass getSingletonClass() {
        throw getRuntime().newTypeError("can't define singleton");
    }

    @Override
    public Class<?> getJavaClass() {
        return long.class;
    }

    @Override
    public double getDoubleValue() {
        return value;
    }

    @Override
    public long getLongValue() {
        return value;
    }

    @Override
    public BigInteger getBigIntegerValue() {
        return BigInteger.valueOf(value);
    }

    public static RubyFixnum newFixnum(Ruby runtime, long value) {
        if (isInCacheRange(value)) {
            return runtime.fixnumCache[(int) value + CACHE_OFFSET];
        }
        return new RubyFixnum(runtime, value);
    }
    
    private static boolean isInCacheRange(long value) {
        return value <= CACHE_OFFSET - 1 && value >= -CACHE_OFFSET;
    }

    public RubyFixnum newFixnum(long newValue) {
        return newFixnum(getRuntime(), newValue);
    }

    public static RubyFixnum zero(Ruby runtime) {
        return runtime.fixnumCache[CACHE_OFFSET];
    }

    public static RubyFixnum one(Ruby runtime) {
        return runtime.fixnumCache[CACHE_OFFSET + 1];
    }
    
    public static RubyFixnum two(Ruby runtime) {
        return runtime.fixnumCache[CACHE_OFFSET + 2];
    }
    
    public static RubyFixnum three(Ruby runtime) {
        return runtime.fixnumCache[CACHE_OFFSET + 3];
    }
    
    public static RubyFixnum four(Ruby runtime) {
        return runtime.fixnumCache[CACHE_OFFSET + 4];
    }
    
    public static RubyFixnum five(Ruby runtime) {
        return runtime.fixnumCache[CACHE_OFFSET + 5];
    }

    public static RubyFixnum minus_one(Ruby runtime) {
        return runtime.fixnumCache[CACHE_OFFSET - 1];
    }

    @Override
    public RubyFixnum hash() {
        return newFixnum(hashCode());
    }

    @Override
    public final int hashCode() {
        return (int)(value ^ value >>> 32);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        
        if (other instanceof RubyFixnum) { 
            RubyFixnum num = (RubyFixnum)other;
            
            if (num.value == value) {
                return true;
            }
        }
        
        return false;
    }

    /*  ================
     *  Instance Methods
     *  ================ 
     */
    @Override
    @JRubyMethod
    public IRubyObject times(ThreadContext context, Block block) {
        if (block.isGiven()) {
            Ruby runtime = context.getRuntime();
            long lvalue = this.value;
            if (block.getBody().getArgumentType() == BlockBody.ZERO_ARGS) {
                IRubyObject nil = runtime.getNil();
                for (long i = 0; i < lvalue; i++) {
                    block.yield(context, nil);
                }
            } else {
                for (long i = 0; i < lvalue; i++) {
                    block.yield(context, RubyFixnum.newFixnum(runtime, i));
                }
            }
            return this;
        } else {
            return RubyEnumerator.enumeratorize(context.getRuntime(), this, "times");
        }
    }

    /** fix_to_s
     * 
     */
    public RubyString to_s(IRubyObject[] args) {
        switch (args.length) {
        case 0: return to_s();
        case 1: return to_s(args[0]);
        default: throw getRuntime().newArgumentError(args.length, 1);
        }
    }
    
    @JRubyMethod
    @Override
    public RubyString to_s() {
        int base = 10;
        return getRuntime().newString(ConvertBytes.longToByteList(value, base));
    }
    
    @JRubyMethod
    public RubyString to_s(IRubyObject arg0) {
        int base = num2int(arg0);
        if (base < 2 || base > 36) {
            throw getRuntime().newArgumentError("illegal radix " + base);
        }
        return getRuntime().newString(ConvertBytes.longToByteList(value, base));
    }

    /** fix_id2name
     * 
     */
    @JRubyMethod
    public IRubyObject id2name() {
        RubySymbol symbol = RubySymbol.getSymbolLong(getRuntime(), value);
        
        if (symbol != null) return getRuntime().newString(symbol.asJavaString());

        return getRuntime().getNil();
    }

    /** fix_to_sym
     * 
     */
    @JRubyMethod
    public IRubyObject to_sym() {
        RubySymbol symbol = RubySymbol.getSymbolLong(getRuntime(), value);
        
        return symbol != null ? symbol : getRuntime().getNil(); 
    }

    /** fix_uminus
     * 
     */
    @JRubyMethod(name = "-@")
    public IRubyObject op_uminus() {
        if (value == MIN) { // a gotcha
            return RubyBignum.newBignum(getRuntime(), BigInteger.valueOf(value).negate());
        }
        return RubyFixnum.newFixnum(getRuntime(), -value);
    }

    /** fix_plus
     * 
     */
    @JRubyMethod(name = "+")
    public IRubyObject op_plus(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) {
            return addFixnum(context, (RubyFixnum)other);
        }
        return addOther(context, other);
    }
    
    public IRubyObject op_plus(ThreadContext context, long otherValue) {
        long result = value + otherValue;
        if (additionOverflowed(value, otherValue, result)) {
            return addAsBignum(context, otherValue);
        }
        return newFixnum(context.getRuntime(), result);
    }
    
    public IRubyObject op_plus_one(ThreadContext context) {
        long result = value + 1;
        if (result == Long.MIN_VALUE) {
            return addAsBignum(context, 1);
        }
        return newFixnum(context.getRuntime(), result);
    }
    
    public IRubyObject op_plus_two(ThreadContext context) {
        long result = value + 2;
        if (result == Long.MIN_VALUE + 1) {
            return addAsBignum(context, 2);
        }
        return newFixnum(context.getRuntime(), result);
    }
    
    private IRubyObject addFixnum(ThreadContext context, RubyFixnum other) {
        long otherValue = other.value;
        long result = value + otherValue;
        if (additionOverflowed(value, otherValue, result)) {
            return addAsBignum(context, other);
        }
        return newFixnum(context.getRuntime(), result);
    }
    
    private static boolean additionOverflowed(long original, long other, long result) {
        return (~(original ^ other) & (original ^ result) & SIGN_BIT) != 0;
    }
    
    private static boolean subtractionOverflowed(long original, long other, long result) {
        return (~(original ^ ~other) & (original ^ result) & SIGN_BIT) != 0;
    }
    
    private IRubyObject addAsBignum(ThreadContext context, RubyFixnum other) {
        return RubyBignum.newBignum(context.getRuntime(), value).op_plus(context, other);
    }

    private IRubyObject addAsBignum(ThreadContext context, long other) {
        return RubyBignum.newBignum(context.getRuntime(), value).op_plus(context, other);
    }
    
    private IRubyObject addOther(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyBignum) {
            return ((RubyBignum) other).op_plus(context, this);
        }
        if (other instanceof RubyFloat) {
            return context.getRuntime().newFloat((double) value + ((RubyFloat) other).getDoubleValue());
        }
        return coerceBin(context, "+", other);
    }

    /** fix_minus
     * 
     */
    @JRubyMethod(name = "-")
    public IRubyObject op_minus(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) {
            return subtractFixnum(context, (RubyFixnum)other);
        }
        return subtractOther(context, other);
    }

    public IRubyObject op_minus(ThreadContext context, long otherValue) {
        long result = value - otherValue;
        if (subtractionOverflowed(value, otherValue, result)) {
            return subtractAsBignum(context, otherValue);
        }
        return newFixnum(context.getRuntime(), result);
    }

    public IRubyObject op_minus_one(ThreadContext context) {
        long result = value - 1;
        if (result == Long.MAX_VALUE) {
            return subtractAsBignum(context, 1);
        }
        return newFixnum(context.getRuntime(), result);
    }

    public IRubyObject op_minus_two(ThreadContext context) {
        long result = value - 2;
        if (result == Long.MAX_VALUE - 1) {
            return subtractAsBignum(context, 2);
        }
        return newFixnum(context.getRuntime(), result);
    }

    private IRubyObject subtractFixnum(ThreadContext context, RubyFixnum other) {
        long otherValue = other.value;
        long result = value - otherValue;
        if (subtractionOverflowed(value, otherValue, result)) {
            return subtractAsBignum(context, other);
        }
        return newFixnum(context.getRuntime(), result);
    }
    
    private IRubyObject subtractAsBignum(ThreadContext context, RubyFixnum other) {
        return RubyBignum.newBignum(context.getRuntime(), value).op_minus(context, other);
    }

    private IRubyObject subtractAsBignum(ThreadContext context, long other) {
        return RubyBignum.newBignum(context.getRuntime(), value).op_minus(context, other);
    }
    
    private IRubyObject subtractOther(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyBignum) {
            return RubyBignum.newBignum(context.getRuntime(), value).op_minus(context, other);
        } else if (other instanceof RubyFloat) {
            return context.getRuntime().newFloat((double) value - ((RubyFloat) other).getDoubleValue());
        }
        return coerceBin(context, "-", other);
    }

    /** fix_mul
     * 
     */
    @JRubyMethod(name = "*")
    public IRubyObject op_mul(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) {
            return multiplyFixnum(context, other);
        } else {
            return multiplyOther(context, other);
        }
    }

    private IRubyObject multiplyFixnum(ThreadContext context, IRubyObject other) {
        return op_mul(context, ((RubyFixnum) other).value);
    }

    private IRubyObject multiplyOther(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        if (other instanceof RubyBignum) {
            return ((RubyBignum) other).op_mul(context, this);
        } else if (other instanceof RubyFloat) {
            return runtime.newFloat((double) value * ((RubyFloat) other).getDoubleValue());
        }
        return coerceBin(context, "*", other);
    }

    public IRubyObject op_mul(ThreadContext context, long otherValue) {
        Ruby runtime = context.getRuntime();
        long result = value * otherValue;
        if (value != 0 && result / value != otherValue) {
            return RubyBignum.newBignum(runtime, value).op_mul(context, otherValue);
        }
        return newFixnum(runtime, result);
    }

    /** fix_div
     * here is terrible MRI gotcha:
     * 1.div 3.0 -> 0
     * 1 / 3.0   -> 0.3333333333333333
     * 
     * MRI is also able to do it in one place by looking at current frame in rb_num_coerce_bin:
     * rb_funcall(x, ruby_frame->orig_func, 1, y);
     * 
     * also note that RubyFloat doesn't override Numeric.div
     */
    @JRubyMethod(name = "div", compat = RUBY1_8)
    public IRubyObject div_div(ThreadContext context, IRubyObject other) {
        return idiv(context, other, "div");
    }

    @JRubyMethod(name = "div", compat = RUBY1_9)
    public IRubyObject div_div19(ThreadContext context, IRubyObject other) {
        checkZeroDivisionError(context, other);
        return div_div(context, other);
    }
    	
    @JRubyMethod(name = "/")
    public IRubyObject op_div(ThreadContext context, IRubyObject other) {
        return idiv(context, other, "/");
    }

    public IRubyObject op_div(ThreadContext context, long other) {
        return idiv(context, other, "/");
    }

    @JRubyMethod(name = {"odd?"}, compat = RUBY1_9)
    public RubyBoolean odd_p() {
        if(value%2 != 0) {
            return getRuntime().getTrue();
        }
        return getRuntime().getFalse();
    }

    @JRubyMethod(name = {"even?"}, compat = RUBY1_9)
    public RubyBoolean even_p() {
        if(value%2 == 0) {
            return getRuntime().getTrue();
        }
        return getRuntime().getFalse();
    }

    @JRubyMethod(compat = RUBY1_9)
    public IRubyObject pred() {
        return getRuntime().newFixnum(value-1);
    }

    public IRubyObject idiv(ThreadContext context, IRubyObject other, String method) {
        if (other instanceof RubyFixnum) {
            return idivLong(context, value, ((RubyFixnum) other).value);
        } 
        return coerceBin(context, method, other);
    }

    public IRubyObject idiv(ThreadContext context, long y, String method) {
        long x = value;

        return idivLong(context, x, y);
    }

    private IRubyObject idivLong(ThreadContext context, long x, long y) {
        if (y == 0) {
            throw context.getRuntime().newZeroDivisionError();
        }
        long div = x / y;
        long mod = x % y;
        if (mod < 0 && y > 0 || mod > 0 && y < 0) {
            div -= 1;
        }
        return context.getRuntime().newFixnum(div);
    }
        
    /** fix_mod
     * 
     */
    @JRubyMethod(name = {"%", "modulo"}, compat = RUBY1_8)
    public IRubyObject op_mod(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) {
            return moduloFixnum(context, (RubyFixnum)other);
        }
        return coerceBin(context, "%", other);
    }
    
    public IRubyObject op_mod(ThreadContext context, long other) {
        return moduloFixnum(context, other);
    }

    @JRubyMethod(name = {"%", "modulo"}, compat = RUBY1_9)
    public IRubyObject op_mod19(ThreadContext context, IRubyObject other) {
        if (context.runtime.is1_9()) checkZeroDivisionError(context, other);
        return op_mod(context, other);
    }

    private IRubyObject moduloFixnum(ThreadContext context, RubyFixnum other) {
        return moduloFixnum(context, other.value);
    }

    private IRubyObject moduloFixnum(ThreadContext context, long other) {
        // Java / and % are not the same as ruby
        long x = value;
        long y = other;
        if (y == 0) {
            throw context.getRuntime().newZeroDivisionError();
        }
        long mod = x % y;
        if (mod < 0 && y > 0 || mod > 0 && y < 0) {
            mod += y;
        }
        return context.getRuntime().newFixnum(mod);
    }
                
    /** fix_divmod
     * 
     */
    @JRubyMethod(name = "divmod", compat = RUBY1_8)
    @Override
    public IRubyObject divmod(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) {
            return divmodFixnum(context, other);
        }
        return coerceBin(context, "divmod", other);
    }

    @JRubyMethod(name = "divmod", compat = RUBY1_9)
    public IRubyObject divmod19(ThreadContext context, IRubyObject other) {
        checkZeroDivisionError(context, other);
        return divmod(context, other);
    }

    private IRubyObject divmodFixnum(ThreadContext context, IRubyObject other) {
        long x = value;
        long y = ((RubyFixnum) other).value;
        final Ruby runtime = context.getRuntime();
        if (y == 0) {
            throw runtime.newZeroDivisionError();
        }
        long div = x / y;
        long mod = x % y;
        if (mod < 0 && y > 0 || mod > 0 && y < 0) {
            div -= 1;
            mod += y;
        }
        IRubyObject fixDiv = RubyFixnum.newFixnum(runtime, div);
        IRubyObject fixMod = RubyFixnum.newFixnum(runtime, mod);
        return RubyArray.newArray(runtime, fixDiv, fixMod);
    }
    	
    /** fix_quo
     * 
     */
    @JRubyMethod(name = "quo", compat = RUBY1_8)
    @Override
    public IRubyObject quo(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) {
            return RubyFloat.newFloat(context.getRuntime(), (double) value / (double) ((RubyFixnum) other).value);
        }
        return coerceBin(context, "quo", other);
    }

    /** fix_pow 
     * 
     */
    @JRubyMethod(name = "**")
    public IRubyObject op_pow(ThreadContext context, IRubyObject other) {
        if(other instanceof RubyFixnum) {
            return powerFixnum(context, ((RubyFixnum)other).value);
        } else {
            return powerOther(context, other);
        }
    }

    public IRubyObject op_pow(ThreadContext context, long other) {
        return powerFixnum(context, other);
    }

    private IRubyObject powerFixnum(ThreadContext context, long other) {
        Ruby runtime = context.getRuntime();
        if (other == 0) {
            return RubyFixnum.one(runtime);
        }
        if (other == 1) {
            return this;
        }
        if (other > 0) {
            return RubyBignum.newBignum(runtime, value).op_pow(context, other);
        }
        return RubyFloat.newFloat(runtime, Math.pow(value, other));
    }

    private IRubyObject powerOther(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        if (other instanceof RubyFloat) {
            return RubyFloat.newFloat(runtime, Math.pow(value, ((RubyFloat) other)
                    .getDoubleValue()));
        }
        return coerceBin(context, "**", other);
    }

    @JRubyMethod(name = "**", compat = RUBY1_9)
    public IRubyObject op_pow_19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyNumeric) {
            double d_other = ((RubyNumeric) other).getDoubleValue();
            if (value < 0 && (d_other != Math.round(d_other))) {
                return RubyComplex.newComplexRaw(getRuntime(), this).callMethod(context, "**", other);
            }
            if (other instanceof RubyFixnum) {
                return powerFixnum19(context, other);
            }
        }
        return powerOther19(context, other);
    }

    private IRubyObject powerOther19(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        long a = value;
        if (other instanceof RubyBignum) {
            if (other.callMethod(context, "<", RubyFixnum.zero(runtime)).isTrue()) {
                return RubyRational.newRationalRaw(runtime, this).callMethod(context, "**", other);
            }
            if (a == 0) return RubyFixnum.zero(runtime);
            if (a == 1) return RubyFixnum.one(runtime);
            if (a == -1) {
                return ((RubyBignum)other).even_p(context).isTrue() ? RubyFixnum.one(runtime) : RubyFixnum.minus_one(runtime);
            }
            RubyBignum.newBignum(runtime, RubyBignum.fix2big(this)).op_pow(context, other);
        } else if (other instanceof RubyFloat) {
            double b = ((RubyFloat)other).getValue();
            if (b == 0.0 || a == 1) return runtime.newFloat(1.0);
            if (a == 0) return runtime.newFloat(b < 0 ? 1.0 / 0.0 : 0.0);
            return RubyFloat.newFloat(runtime, Math.pow(a, b));
        }
        return coerceBin(context, "**", other);
    }

    private IRubyObject powerFixnum19(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        long a = value;
        long b = ((RubyFixnum) other).value;
        if (b < 0) {
            return RubyRational.newRationalRaw(runtime, this).callMethod(context, "**", other);
        }
        if (b == 0) {
            return RubyFixnum.one(runtime);
        }
        if (b == 1) {
            return this;
        }
        if (a == 0) {
            return b > 0 ? RubyFixnum.zero(runtime) : RubyNumeric.dbl2num(runtime, 1.0 / 0.0);
        }
        if (a == 1) {
            return RubyFixnum.one(runtime);
        }
        if (a == -1) {
            return b % 2 == 0 ? RubyFixnum.one(runtime) : RubyFixnum.minus_one(runtime);
        }
        return Numeric.int_pow(context, a, b);
    }

    /** fix_abs
     * 
     */
    @JRubyMethod
    @Override
    public IRubyObject abs(ThreadContext context) {
        if (value < 0) {
            // A gotcha for Long.MIN_VALUE: value = -value
            if (value == Long.MIN_VALUE) {
                return RubyBignum.newBignum(
                        context.getRuntime(), BigInteger.valueOf(value).negate());
            }
            return RubyFixnum.newFixnum(context.getRuntime(), -value);
        }
        return this;
    }

    /** fix_abs/1.9
     * 
     */
    @JRubyMethod(name = "magnitude", compat = RUBY1_9)
    @Override
    public IRubyObject magnitude(ThreadContext context) {
        return abs(context);
    }

    /** fix_equal
     * 
     */
    @JRubyMethod(name = "==", compat = RUBY1_8)
    @Override
    public IRubyObject op_equal(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return op_equal(context, ((RubyFixnum) other).value);
        return super.op_num_equal(context, other);
    }

    public IRubyObject op_equal(ThreadContext context, long other) {
        return RubyBoolean.newBoolean(context.getRuntime(), value == other);
    }

    public boolean fastEqual(RubyFixnum other) {
        return value == other.value;
    }

    @JRubyMethod(name = "==", compat = RUBY1_9)
    public IRubyObject op_equal19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return op_equal(context, ((RubyFixnum) other).value);
        return op_equalOther(context, other);
    }

    private IRubyObject op_equalOther(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyBignum) return RubyBoolean.newBoolean(context.getRuntime(),
                BigInteger.valueOf(value).compareTo(((RubyBignum)other).getValue()) == 0);
        if (other instanceof RubyFloat) return RubyBoolean.newBoolean(context.getRuntime(), (double)value == ((RubyFloat)other).getDoubleValue());
        return super.op_num_equal(context, other);
    }

    @Override
    public final int compareTo(IRubyObject other) {
        if (other instanceof RubyFixnum) {
            long otherValue = ((RubyFixnum)other).value;
            return value == otherValue ? 0 : value > otherValue ? 1 : -1; 
        }
        return compareToOther(other);
    }

    private int compareToOther(IRubyObject other) {
        if (other instanceof RubyBignum) return BigInteger.valueOf(value).compareTo(((RubyBignum)other).getValue());
        if (other instanceof RubyFloat) return Double.compare((double)value, ((RubyFloat)other).getDoubleValue());
        return (int)coerceCmp(getRuntime().getCurrentContext(), "<=>", other).convertToInteger().getLongValue();
    }

    /** fix_cmp
     * 
     */
    @JRubyMethod(name = "<=>", compat = RUBY1_8)
    public IRubyObject op_cmp(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return op_cmp(context, ((RubyFixnum)other).value);
        return coerceCmp(context, "<=>", other);
    }

    public IRubyObject op_cmp(ThreadContext context, long other) {
        Ruby runtime = context.runtime;
        return value == other ? RubyFixnum.zero(runtime) : value > other ?
                RubyFixnum.one(runtime) : RubyFixnum.minus_one(runtime);
    }

    @JRubyMethod(name = "<=>", compat = RUBY1_9)
    public IRubyObject op_cmp19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return op_cmp(context, ((RubyFixnum)other).value);
        return compareOther(context, other);
    }

    private IRubyObject compareOther(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyBignum) return newFixnum(context.getRuntime(), BigInteger.valueOf(value).compareTo(((RubyBignum)other).getValue()));
        if (other instanceof RubyFloat) return dbl_cmp(context.getRuntime(), (double)value, ((RubyFloat)other).getDoubleValue());
        return coerceCmp(context, "<=>", other);
    }

    /** fix_gt
     * 
     */
    @JRubyMethod(name = ">", compat = RUBY1_8)
    public IRubyObject op_gt(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return RubyBoolean.newBoolean(context.getRuntime(), value > ((RubyFixnum) other).value);
        return coerceRelOp(context, ">", other);
    }

    public IRubyObject op_gt(ThreadContext context, long other) {
        return RubyBoolean.newBoolean(context.getRuntime(), value > other);
    }

    @JRubyMethod(name = ">", compat = RUBY1_9)
    public IRubyObject op_gt19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return RubyBoolean.newBoolean(context.getRuntime(), value > ((RubyFixnum) other).value);
        return op_gtOther(context, other);
    }

    private IRubyObject op_gtOther(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyBignum) return RubyBoolean.newBoolean(context.getRuntime(),
                BigInteger.valueOf(value).compareTo(((RubyBignum)other).getValue()) > 0);
        if (other instanceof RubyFloat) return RubyBoolean.newBoolean(context.getRuntime(), (double)value > ((RubyFloat) other).getDoubleValue());
        return coerceRelOp(context, ">", other);
    }

    /** fix_ge
     * 
     */
    @JRubyMethod(name = ">=", compat = RUBY1_8)
    public IRubyObject op_ge(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return RubyBoolean.newBoolean(context.getRuntime(), value >= ((RubyFixnum) other).value);
        return coerceRelOp(context, ">=", other);
    }

    public IRubyObject op_ge(ThreadContext context, long other) {
        return RubyBoolean.newBoolean(context.getRuntime(), value >= other);
    }

    @JRubyMethod(name = ">=", compat = RUBY1_9)
    public IRubyObject op_ge19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return RubyBoolean.newBoolean(context.getRuntime(), value >= ((RubyFixnum) other).value);
        return op_geOther(context, other);
    }

    private IRubyObject op_geOther(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyBignum) return RubyBoolean.newBoolean(context.getRuntime(),
                BigInteger.valueOf(value).compareTo(((RubyBignum)other).getValue()) >= 0);
        if (other instanceof RubyFloat) return RubyBoolean.newBoolean(context.getRuntime(), (double)value >= ((RubyFloat) other).getDoubleValue());
        return coerceRelOp(context, ">=", other);
    }

    /** fix_lt
     * 
     */
    @JRubyMethod(name = "<", compat = RUBY1_8)
    public IRubyObject op_lt(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return RubyBoolean.newBoolean(context.getRuntime(), value < ((RubyFixnum) other).value);
        return coerceRelOp(context, "<", other);
    }

    public IRubyObject op_lt(ThreadContext context, long other) {
        return RubyBoolean.newBoolean(context.getRuntime(), value < other);
    }

    @JRubyMethod(name = "<", compat = RUBY1_9)
    public IRubyObject op_lt19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return RubyBoolean.newBoolean(context.getRuntime(), value < ((RubyFixnum) other).value);
        return op_ltOther(context, other);
    }

    private IRubyObject op_ltOther(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyBignum) return RubyBoolean.newBoolean(context.getRuntime(),
                BigInteger.valueOf(value).compareTo(((RubyBignum)other).getValue()) < 0);
        if (other instanceof RubyFloat) return RubyBoolean.newBoolean(context.getRuntime(), (double)value < ((RubyFloat) other).getDoubleValue());
        return coerceRelOp(context, "<", other);
    }

    /** fix_le
     * 
     */
    @JRubyMethod(name = "<=", compat = RUBY1_8)
    public IRubyObject op_le(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return RubyBoolean.newBoolean(context.getRuntime(), value <= ((RubyFixnum) other).value);
        return coerceRelOp(context, "<=", other);
    }

    public IRubyObject op_le(ThreadContext context, long other) {
        return RubyBoolean.newBoolean(context.getRuntime(), value <= other);
    }

    @JRubyMethod(name = "<=", compat = RUBY1_9)
    public IRubyObject op_le19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) return RubyBoolean.newBoolean(context.getRuntime(), value <= ((RubyFixnum) other).value);
        return op_leOther(context, other);
    }

    private IRubyObject op_leOther(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyBignum) return RubyBoolean.newBoolean(context.getRuntime(),
                BigInteger.valueOf(value).compareTo(((RubyBignum)other).getValue()) <= 0);
        if (other instanceof RubyFloat) return RubyBoolean.newBoolean(context.getRuntime(), (double)value <= ((RubyFloat) other).getDoubleValue());
        return coerceRelOp(context, "<=", other);
    }

    /** fix_rev
     * 
     */
    @JRubyMethod(name = "~")
    public IRubyObject op_neg() {
        return newFixnum(~value);
    }
    	
    /** fix_and
     * 
     */
    @JRubyMethod(name = "&", compat = CompatVersion.RUBY1_8)
    public IRubyObject op_and(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum || (other = fixCoerce(other)) instanceof RubyFixnum) {
            return newFixnum(context.getRuntime(), value & ((RubyFixnum) other).value);
        }
        return ((RubyBignum) other).op_and(context, this);
    }
    
    public IRubyObject op_and(ThreadContext context, long other) {
        return newFixnum(context.getRuntime(), value & other);
    }

    @JRubyMethod(name = "&", compat = CompatVersion.RUBY1_9)
    public IRubyObject op_and19(ThreadContext context, IRubyObject other) {
        if (!((other = bitCoerce(other)) instanceof RubyFixnum)) {
            return ((RubyBignum) other).op_and(context, this);
        }
        return op_and(context, other);
    }
    
    /** fix_or 
     * 
     */
    @JRubyMethod(name = "|", compat = CompatVersion.RUBY1_8)
    public IRubyObject op_or(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum || (other = fixCoerce(other)) instanceof RubyFixnum) {
            return newFixnum(context.getRuntime(), value | ((RubyFixnum) other).value);
        }
        return ((RubyBignum) other).op_or(context, this);
    }
    
    public IRubyObject op_or(ThreadContext context, long other) {
        return newFixnum(context.getRuntime(), value | other);
    }
    
    @JRubyMethod(name = "|", compat = CompatVersion.RUBY1_9)
    public IRubyObject op_or19(ThreadContext context, IRubyObject other) {
        if (!((other = bitCoerce(other)) instanceof RubyFixnum)) {
            return ((RubyBignum) other).op_or(context, this);
        }
        return op_or(context, other);
    }
    
    /** fix_xor 
     * 
     */
    @JRubyMethod(name = "^", compat = CompatVersion.RUBY1_8)
    public IRubyObject op_xor(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum || (other = fixCoerce(other)) instanceof RubyFixnum) {
            return newFixnum(context.getRuntime(), value ^ ((RubyFixnum) other).value);
        }
        return ((RubyBignum) other).op_xor(context, this); 
    }
    
    public IRubyObject op_xor(ThreadContext context, long other) {
        return newFixnum(context.getRuntime(), value ^ other);
    }

    @JRubyMethod(name = "^", compat = CompatVersion.RUBY1_9)
    public IRubyObject op_xor19(ThreadContext context, IRubyObject other) {
        if (!((other = bitCoerce(other)) instanceof RubyFixnum)) {
            return ((RubyBignum) other).op_xor(context, this);
        }
        return op_xor(context, other);
    }
    
    /** fix_aref 
     * 
     */
    @JRubyMethod(name = "[]")
    public IRubyObject op_aref(IRubyObject other) {
        if(!(other instanceof RubyFixnum) && !((other = fixCoerce(other)) instanceof RubyFixnum)) {
            RubyBignum big = (RubyBignum) other;
            other = RubyBignum.bignorm(getRuntime(), big.getValue());
            if (!(other instanceof RubyFixnum)) {
                return big.getValue().signum() == 0 || value >= 0 ? RubyFixnum.zero(getRuntime()) : RubyFixnum.one(getRuntime());
            }
        }

        long otherValue = fix2long(other);

        if (otherValue < 0) return RubyFixnum.zero(getRuntime());

        if (BIT_SIZE - 1 < otherValue) {
            return value < 0 ? RubyFixnum.one(getRuntime()) : RubyFixnum.zero(getRuntime());
        }

        return (value & (1L << otherValue)) == 0 ? RubyFixnum.zero(getRuntime()) : RubyFixnum.one(getRuntime());
    }

    /** fix_lshift 
     * 
     */
    @JRubyMethod(name = "<<")
    public IRubyObject op_lshift(IRubyObject other) {
        if (!(other instanceof RubyFixnum)) return RubyBignum.newBignum(getRuntime(), value).op_lshift(other);

        return op_lshift(((RubyFixnum)other).getLongValue());
    }
    
    public IRubyObject op_lshift(long width) {
        return width < 0 ? rshift(-width) : lshift(width); 
    }
    
    private IRubyObject lshift(long width) {
        if (width > BIT_SIZE - 1 || ((~0L << BIT_SIZE - width - 1) & value) != 0) {
            return RubyBignum.newBignum(getRuntime(), value).op_lshift(RubyFixnum.newFixnum(getRuntime(), width));
        }
        return RubyFixnum.newFixnum(getRuntime(), value << width);
    }

    /** fix_rshift 
     * 
     */
    @JRubyMethod(name = ">>")
    public IRubyObject op_rshift(IRubyObject other) {
        if (!(other instanceof RubyFixnum)) return RubyBignum.newBignum(getRuntime(), value).op_rshift(other);

        return op_rshift(((RubyFixnum)other).getLongValue());
    }
    
    public IRubyObject op_rshift(long width) {
        if (width == 0) return this;

        return width < 0 ? lshift(-width) : rshift(width);  
    }
    
    private IRubyObject rshift(long width) { 
        if (width >= BIT_SIZE - 1) {
            return value < 0 ? RubyFixnum.minus_one(getRuntime()) : RubyFixnum.zero(getRuntime()); 
        }
        return RubyFixnum.newFixnum(getRuntime(), value >> width);
    }

    /** fix_to_f 
     * 
     */
    @JRubyMethod
    public IRubyObject to_f() {
        return RubyFloat.newFloat(getRuntime(), (double) value);
    }

    /** fix_size 
     * 
     */
    @JRubyMethod
    public IRubyObject size() {
        return newFixnum((long) ((BIT_SIZE + 7) / 8));
    }

    /** fix_zero_p 
     * 
     */
    @JRubyMethod(name = "zero?")
    public IRubyObject zero_p() {
        return RubyBoolean.newBoolean(getRuntime(), value == 0);
    }

    @JRubyMethod
    @Override
    public IRubyObject id() {
        if (value <= Long.MAX_VALUE / 2 && value >= Long.MIN_VALUE / 2) {
            return newFixnum(2 * value + 1);
        }
        
        return super.id();
    }

    @Override
    public IRubyObject taint(ThreadContext context) {
        return this;
    }

    // Piece of mri rb_to_id
    @Override
    public String asJavaString() {
        Ruby runtime = getRuntime();
        if (runtime.is1_9()) throw runtime.newTypeError(inspect().toString() + " is not a symbol");
        runtime.getWarnings().warn(ID.FIXNUMS_NOT_SYMBOLS, "do not use Fixnums as Symbols");

        // FIXME: I think this chunk is equivalent to MRI id2name (and not our public method 
        // id2name).  Make into method if used more than once.  
        RubySymbol symbol = RubySymbol.getSymbolLong(runtime, value);

        if (symbol == null) {
            throw runtime.newArgumentError("" + value + " is not a symbol");
        }

        return symbol.asJavaString();
    }

    public static RubyFixnum unmarshalFrom(UnmarshalStream input) throws java.io.IOException {
        return input.getRuntime().newFixnum(input.unmarshalInt());
    }

    /*  ================
     *  Singleton Methods
     *  ================ 
     */

    /** rb_fix_induced_from
     * 
     */
    @JRubyMethod(name = "induced_from", meta = true, compat = RUBY1_8)
    public static IRubyObject induced_from(IRubyObject recv, IRubyObject other) {
        return RubyNumeric.num2fix(other);
    }
    
    private static Object coerceToJavaType(Ruby ruby, RubyFixnum self, Class javaClass) {
        if (!Number.class.isAssignableFrom(javaClass)) {
            throw ruby.newTypeError(javaClass.getCanonicalName() + " is not a numeric type");
        }
        
        TypeCoercer coercer = JAVA_COERCERS.get(javaClass);
        
        if (coercer == null) {
            throw ruby.newTypeError("Cannot coerce Fixnum to " + javaClass.getCanonicalName());
        }
        
        return coercer.coerce(self);
    }
    
    private static final Map<Class, TypeCoercer> JAVA_COERCERS = new HashMap<Class, TypeCoercer>();
    
    static {
        TypeCoercer intCoercer = new TypeCoercer() {
            public Object coerce(IRubyObject self) {
                RubyFixnum fixnum = (RubyFixnum)self;
                
                if (fixnum.value > Integer.MAX_VALUE) {
                    throw self.getRuntime().newRangeError("Fixnum " + fixnum.value + " is too large for Java int");
                }
                
                return Integer.valueOf((int)fixnum.value);
            }
        };
        JAVA_COERCERS.put(int.class, intCoercer);
        JAVA_COERCERS.put(Integer.class, intCoercer);
    }

    private void checkZeroDivisionError(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFloat && ((RubyFloat)other).getDoubleValue() == 0.0d) {
            throw context.getRuntime().newZeroDivisionError();
        }
    }
}

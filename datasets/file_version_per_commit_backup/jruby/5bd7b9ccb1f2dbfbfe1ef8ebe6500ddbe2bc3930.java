/***** BEGIN LICENSE BLOCK *****
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
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
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

import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.ClassIndex;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.builtin.IRubyObject;

/**
 *
 * @author  jpetersen
 */
public class RubyNil extends RubyObject {
    private final Ruby runtime;
    
    public RubyNil(Ruby runtime) {
        super(runtime, runtime.getNilClass(), false);
        this.runtime = runtime;
        flags |= NIL_F | FALSE_F;
    }
    
    public Ruby getRuntime() {
        if (Ruby.RUNTIME_THREADLOCAL) {
            return Ruby.getCurrentInstance();
        } else {
            return runtime;
        }
    }
    
    public static ObjectAllocator NIL_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return runtime.getNil();
        }
    };
    
    public static RubyClass createNilClass(Ruby runtime) {
        RubyClass nilClass = runtime.defineClass("NilClass", runtime.getObject(), NIL_ALLOCATOR);
        runtime.setNilClass(nilClass);
        nilClass.index = ClassIndex.NIL;
        
        CallbackFactory callbackFactory = runtime.callbackFactory(RubyNil.class);
        
        nilClass.defineAnnotatedMethods(RubyNil.class);
        
        nilClass.getMetaClass().undefineMethod("new");
        
        // FIXME: This is causing a verification error for some reason
        //nilClass.dispatcher = callbackFactory.createDispatcher(nilClass);
        
        return nilClass;
    }
    
    public int getNativeTypeIndex() {
        return ClassIndex.NIL;
    }
    
//    public RubyClass getMetaClass() {
//        return runtime.getNilClass();
//    }

    public boolean isImmediate() {
        return true;
    }

    public RubyClass getSingletonClass() {
        return metaClass;
    }
    
    // Methods of the Nil Class (nil_*):
    
    /** nil_to_i
     *
     */
    @JRubyMethod(name = "to_i")
    public static RubyFixnum to_i(IRubyObject recv) {
        return RubyFixnum.zero(recv.getRuntime());
    }
    
    /**
     * nil_to_f
     *
     */
    @JRubyMethod(name = "to_f")
    public static RubyFloat to_f(IRubyObject recv) {
        return RubyFloat.newFloat(recv.getRuntime(), 0.0D);
    }
    
    /** nil_to_s
     *
     */
    @JRubyMethod(name = "to_s")
    public static RubyString to_s(IRubyObject recv) {
        return recv.getRuntime().newString("");
    }
    
    /** nil_to_a
     *
     */
    @JRubyMethod(name = "to_a")
    public static RubyArray to_a(IRubyObject recv) {
        return recv.getRuntime().newArray(0);
    }
    
    /** nil_inspect
     *
     */
    @JRubyMethod(name = "inspect")
    public static RubyString inspect(IRubyObject recv) {
        return recv.getRuntime().newString("nil");
    }
    
    /** nil_type
     *
     */
    @JRubyMethod(name = "type")
    public static RubyClass type(IRubyObject recv) {
        return recv.getRuntime().getNilClass();
    }
    
    /** nil_and
     *
     */
    @JRubyMethod(name = "&", required = 1)
    public static RubyBoolean op_and(IRubyObject recv, IRubyObject obj) {
        return recv.getRuntime().getFalse();
    }
    
    /** nil_or
     *
     */
    @JRubyMethod(name = "|", required = 1)
    public static RubyBoolean op_or(IRubyObject recv, IRubyObject obj) {
        return recv.getRuntime().newBoolean(obj.isTrue());
    }
    
    /** nil_xor
     *
     */
    @JRubyMethod(name = "^", required = 1)
    public static RubyBoolean op_xor(IRubyObject recv, IRubyObject obj) {
        return recv.getRuntime().newBoolean(obj.isTrue());
    }

    @JRubyMethod(name = "nil?")
    public IRubyObject nil_p() {
        return getRuntime().getTrue();
    }
    
    public RubyFixnum id() {
        return getRuntime().newFixnum(4);
    }
    
    public IRubyObject taint() {
        return this;
    }

    public IRubyObject freeze() {
        return this;
    }
}

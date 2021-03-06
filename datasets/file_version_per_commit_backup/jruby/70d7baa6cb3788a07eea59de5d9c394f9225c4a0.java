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
 * Copyright (C) 2006 The JRuby Community <www.jruby.org>
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
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
package org.jruby.internal.runtime.methods;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import org.jruby.Ruby;
import org.jruby.parser.StaticScope;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.compiler.ASTInspector;
import org.jruby.compiler.impl.SkinnyMethodAdapter;
import org.jruby.exceptions.JumpException;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.MethodFactory;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import static org.jruby.util.CodegenUtils.*;
import org.jruby.util.JRubyClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * In order to avoid the overhead with reflection-based method handles, this
 * MethodFactory uses ASM to generate tiny invoker classes. This allows for
 * better performance and more specialization per-handle than can be supported
 * via reflection. It also allows optimizing away many conditionals that can
 * be determined once ahead of time.
 * 
 * When running in secured environments, this factory may not function. When
 * this can be detected, MethodFactory will fall back on the reflection-based
 * factory instead.
 * 
 * @see org.jruby.internal.runtime.methods.MethodFactory
 */
public class InvocationMethodFactory extends MethodFactory implements Opcodes {
    /** The pathname of the super class for compiled Ruby method handles. */ 
    private final static String COMPILED_SUPER_CLASS = p(CompiledMethod.class);
    
    /** The outward call signature for compiled Ruby method handles. */
    private final static String COMPILED_CALL_SIG = sig(IRubyObject.class,
            params(ThreadContext.class, IRubyObject.class, RubyModule.class, String.class, IRubyObject[].class));
    
    /** The outward call signature for compiled Ruby method handles. */
    private final static String COMPILED_CALL_SIG_BLOCK = sig(IRubyObject.class,
            params(ThreadContext.class, IRubyObject.class, RubyModule.class, String.class, IRubyObject[].class, Block.class));
    
    /** The outward arity-zero call-with-block signature for compiled Ruby method handles. */
    private final static String COMPILED_CALL_SIG_ZERO_BLOCK = sig(IRubyObject.class,
            params(ThreadContext.class, IRubyObject.class, RubyModule.class, String.class, Block.class));
    
    /** The outward arity-zero call-with-block signature for compiled Ruby method handles. */
    private final static String COMPILED_CALL_SIG_ZERO = sig(IRubyObject.class,
            params(ThreadContext.class, IRubyObject.class, RubyModule.class, String.class));
    
    /** The outward arity-zero call-with-block signature for compiled Ruby method handles. */
    private final static String COMPILED_CALL_SIG_ONE_BLOCK = sig(IRubyObject.class,
            params(ThreadContext.class, IRubyObject.class, RubyModule.class, String.class, IRubyObject.class, Block.class));
    
    /** The outward arity-zero call-with-block signature for compiled Ruby method handles. */
    private final static String COMPILED_CALL_SIG_ONE = sig(IRubyObject.class,
            params(ThreadContext.class, IRubyObject.class, RubyModule.class, String.class, IRubyObject.class));
    
    /** The outward arity-zero call-with-block signature for compiled Ruby method handles. */
    private final static String COMPILED_CALL_SIG_TWO_BLOCK = sig(IRubyObject.class,
            params(ThreadContext.class, IRubyObject.class, RubyModule.class, String.class, IRubyObject.class, IRubyObject.class, Block.class));
    
    /** The outward arity-zero call-with-block signature for compiled Ruby method handles. */
    private final static String COMPILED_CALL_SIG_TWO = sig(IRubyObject.class,
            params(ThreadContext.class, IRubyObject.class, RubyModule.class, String.class, IRubyObject.class, IRubyObject.class));
    
    /** The outward arity-zero call-with-block signature for compiled Ruby method handles. */
    private final static String COMPILED_CALL_SIG_THREE_BLOCK = sig(IRubyObject.class,
            params(ThreadContext.class, IRubyObject.class, RubyModule.class, String.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, Block.class));
    
    /** The outward arity-zero call-with-block signature for compiled Ruby method handles. */
    private final static String COMPILED_CALL_SIG_THREE = sig(IRubyObject.class,
            params(ThreadContext.class, IRubyObject.class, RubyModule.class, String.class, IRubyObject.class, IRubyObject.class, IRubyObject.class));
    
    /** The super constructor signature for compile Ruby method handles. */
    private final static String COMPILED_SUPER_SIG = 
            sig(Void.TYPE, RubyModule.class, Arity.class, Visibility.class, StaticScope.class, Object.class, CallConfiguration.class);
    
    /** The super constructor signature for Java-based method handles. */
    private final static String JAVA_SUPER_SIG = sig(Void.TYPE, params(RubyModule.class, Visibility.class));
    
    /** The super constructor signature for indexed Java-based method handles. */
    private final static String JAVA_INDEXED_SUPER_SIG = sig(Void.TYPE, params(RubyModule.class, Visibility.class, int.class));
    
    /** The lvar index of "this" */
    public static final int THIS_INDEX = 0;
    
    /** The lvar index of the passed-in ThreadContext */
    public static final int THREADCONTEXT_INDEX = 1;
    
    /** The lvar index of the method-receiving object */
    public static final int RECEIVER_INDEX = 2;
    
    /** The lvar index of the RubyClass being invoked against */
    public static final int CLASS_INDEX = 3;
    
    /** The lvar index method name being invoked */
    public static final int NAME_INDEX = 4;
    
    /** The lvar index of the method args on the call */
    public static final int ARGS_INDEX = 5;
    
    /** The lvar index of the passed-in Block on the call */
    public static final int BLOCK_INDEX = 6;

    /** The classloader to use for code loading */
    private JRubyClassLoader classLoader;
    
    /**
     * Construct a new InvocationMethodFactory using the specified classloader
     * to load code. If the target classloader is not an instance of
     * JRubyClassLoader, it will be wrapped with one.
     * 
     * @param classLoader The classloader to use, or to wrap if it is not a
     * JRubyClassLoader instance.
     */
    public InvocationMethodFactory(ClassLoader classLoader) {
        if (classLoader instanceof JRubyClassLoader) {
            this.classLoader = (JRubyClassLoader)classLoader;
        } else {
           this.classLoader = new JRubyClassLoader(classLoader);
        }
    }

    /**
     * Use code generation to provide a method handle for a compiled Ruby method.
     * 
     * @see org.jruby.internal.runtime.methods.MethodFactory#getCompiledMethod
     */
    public DynamicMethod getCompiledMethod(
            RubyModule implementationClass, String method, Arity arity, 
            Visibility visibility, StaticScope scope, Object scriptObject, CallConfiguration callConfig) {
        String sup = COMPILED_SUPER_CLASS;
        Class scriptClass = scriptObject.getClass();
        String mname = scriptClass.getName() + "Invoker" + method + arity;
        synchronized (classLoader) {
            Class generatedClass = tryClass(implementationClass.getRuntime(), mname);

            try {
                if (generatedClass == null) {
                    String typePath = p(scriptClass);
                    String mnamePath = typePath + "Invoker" + method + arity;
                    ClassWriter cw = createCompiledCtor(mnamePath,sup);
                    SkinnyMethodAdapter mv = new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, "call", COMPILED_CALL_SIG_BLOCK, null, null));

                    mv.visitCode();
                    Label line = new Label();
                    mv.visitLineNumber(0, line);

                    // invoke pre method stuff
                    if (!callConfig.isNoop()) {
                        invokeCallConfigPre(mv, COMPILED_SUPER_CLASS, -1, true);
                    }

                    // store null for result var
                    mv.aconst_null();
                    mv.astore(8);

                    Label tryBegin = new Label();
                    Label tryEnd = new Label();
                    Label doFinally = new Label();
                    Label catchReturnJump = new Label();
                    Label catchRedoJump = new Label();
                    Label normalExit = new Label();

                    mv.trycatch(tryBegin, tryEnd, catchReturnJump, p(JumpException.ReturnJump.class));
                    mv.trycatch(tryBegin, tryEnd, catchRedoJump, p(JumpException.RedoJump.class));
                    mv.trycatch(tryBegin, tryEnd, doFinally, null);
                    mv.trycatch(catchReturnJump, doFinally, doFinally, null);
                    mv.label(tryBegin);

                    mv.aload(0);
                    // FIXME we want to eliminate these type casts when possible
                    mv.getfield(mnamePath, "$scriptObject", ci(Object.class));
                    mv.checkcast(typePath);
                    mv.aload(THREADCONTEXT_INDEX);
                    mv.aload(RECEIVER_INDEX);
                    mv.aload(ARGS_INDEX);
                    mv.aload(BLOCK_INDEX);
                    mv.invokevirtual(typePath, method, sig(IRubyObject.class, params(ThreadContext.class, IRubyObject.class, IRubyObject[].class, Block.class)));

                    // store result in temporary variable 8
                    mv.astore(8);

                    mv.label(tryEnd);

                    //call post method stuff (non-finally)
                    mv.label(normalExit);
                    if (!callConfig.isNoop()) {
                        invokeCallConfigPost(mv, COMPILED_SUPER_CLASS);
                    }
                    // reload and return result
                    mv.aload(8);
                    mv.visitInsn(ARETURN);

                    handleReturn(catchReturnJump,mv, doFinally, normalExit, COMPILED_SUPER_CLASS);

                    handleRedo(catchRedoJump, mv, doFinally);

                    // finally handling for abnormal exit
                    {
                        mv.label(doFinally);

                        //call post method stuff (exception raised)
                        if (!callConfig.isNoop()) {
                            invokeCallConfigPost(mv, COMPILED_SUPER_CLASS);
                        }

                        // rethrow exception
                        mv.athrow(); // rethrow it
                    }

                    generatedClass = endCall(implementationClass.getRuntime(), cw,mv,mname);
                }

                return (DynamicMethod)generatedClass
                        .getConstructor(RubyModule.class, Arity.class, Visibility.class, StaticScope.class, Object.class, CallConfiguration.class)
                        .newInstance(implementationClass, arity, visibility, scope, scriptObject, callConfig);
            } catch(Exception e) {
                e.printStackTrace();
                throw implementationClass.getRuntime().newLoadError(e.getMessage());
            }
        }
    }

    /**
     * Use code generation to provide a method handle based on an annotated Java
     * method.
     * 
     * @see org.jruby.internal.runtime.methods.MethodFactory#getAnnotatedMethod
     */
    public DynamicMethod getAnnotatedMethod(RubyModule implementationClass, Method method) {
        Class type = method.getDeclaringClass();
        JRubyMethod jrubyMethod = method.getAnnotation(JRubyMethod.class);
        String typePath = p(type);
        String javaMethodName = method.getName();
        
        String generatedClassName = type.getName() + "Invoker$" + javaMethodName + "_method_" + jrubyMethod.required() + "_" + jrubyMethod.optional();
        String generatedClassPath = typePath + "Invoker$" + javaMethodName + "_method_" + jrubyMethod.required() + "_" + jrubyMethod.optional();
        
        synchronized (classLoader) {
            Class c = tryClass(implementationClass.getRuntime(), generatedClassName);

            try {
                if (c == null) {
                    int specificArity = -1;
                    if (jrubyMethod.optional() == 0 && jrubyMethod.rest() == false) {
                        if (jrubyMethod.required() >= 0 && jrubyMethod.required() <= 3) {
                            specificArity = jrubyMethod.required();
                        }
                    }

                    Class[] parameterTypes = method.getParameterTypes();
                    boolean block;
                    if (parameterTypes.length == 0) {
                        block = false;
                    } else {
                        if (parameterTypes[parameterTypes.length - 1] == Block.class) {
                            block = true;
                        } else {
                            block = false;
                        }
                    }

                    String superClass = p(selectSuperClass(specificArity, block));

                    ClassWriter cw = createJavaMethodCtor(generatedClassPath, superClass);
                    SkinnyMethodAdapter mv = null;

                    mv = beginMethod(cw, "call", specificArity, block);
                    mv.visitCode();
                    Label line = new Label();
                    mv.visitLineNumber(0, line);

                    createAnnotatedMethodInvocation(method, mv, superClass, specificArity, block);

                    endMethod(mv);

                    c = endClass(implementationClass.getRuntime(), cw, generatedClassName);
                }

                JavaMethod ic = (JavaMethod)c.getConstructor(new Class[]{RubyModule.class, Visibility.class}).newInstance(new Object[]{implementationClass, jrubyMethod.visibility()});

                ic.setArity(Arity.fromAnnotation(jrubyMethod));
                ic.setJavaName(javaMethodName);
                ic.setArgumentTypes(method.getParameterTypes());
                ic.setSingleton(Modifier.isStatic(method.getModifiers()));
                ic.setCallConfig(CallConfiguration.getCallConfigByAnno(jrubyMethod));
                return ic;
            } catch(Exception e) {
                e.printStackTrace();
                throw implementationClass.getRuntime().newLoadError(e.getMessage());
            }
        }
    }

    /**
     * Use code generation to generate a set of method handles based on all
     * annotated methods in the target class.
     * 
     * @see org.jruby.internal.runtime.methods.MethodFactory#defineIndexedAnnotatedMethods
     */
    public void defineIndexedAnnotatedMethods(RubyModule implementationClass, Class type, MethodDefiningCallback callback) {
        String typePath = p(type);
        String superClass = p(JavaMethod.class);
        
        String generatedClassName = type.getName() + "Invoker";
        String generatedClassPath = typePath + "Invoker";
        
        synchronized (classLoader) {
            Class c = tryClass(implementationClass.getRuntime(), generatedClassName);

            try {
                ArrayList<Method> annotatedMethods = new ArrayList();
                Method[] methods = type.getDeclaredMethods();
                for (Method method : methods) {
                    JRubyMethod jrubyMethod = method.getAnnotation(JRubyMethod.class);

                    if (jrubyMethod == null) continue;

                    annotatedMethods.add(method);
                }
                // To ensure the method cases are generated the same way every time, we make a second sorted list
                ArrayList<Method> sortedMethods = new ArrayList(annotatedMethods);
                Collections.sort(sortedMethods, new Comparator<Method>() {
                    public int compare(Method a, Method b) {
                        return a.getName().compareTo(b.getName());
                    }
                });
                // But when binding the methods, we want to use the order from the original class, so we save the indices
                HashMap<Method,Integer> indexMap = new HashMap();
                for (int index = 0; index < sortedMethods.size(); index++) {
                    indexMap.put(sortedMethods.get(index), index);
                }

                if (c == null) {
                    ClassWriter cw = createIndexedJavaMethodCtor(generatedClassPath, superClass);
                    SkinnyMethodAdapter mv = null;

                    mv = new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, "call", COMPILED_CALL_SIG_BLOCK, null, null));
                    mv.visitCode();
                    Label line = new Label();
                    mv.visitLineNumber(0, line);

                    Label defaultCase = new Label();
                    Label[] cases = new Label[sortedMethods.size()];
                    for (int i = 0; i < cases.length; i++) cases[i] = new Label();

                    // load method index
                    mv.aload(THIS_INDEX);
                    mv.getfield(generatedClassPath, "methodIndex", ci(int.class));

                    mv.tableswitch(0, cases.length - 1, defaultCase, cases);

                    for (int i = 0; i < sortedMethods.size(); i++) {
                        mv.label(cases[i]);
                        String callName = getAnnotatedMethodForIndex(cw, sortedMethods.get(i), i, superClass);

                        // invoke call#_method for method
                        mv.aload(THIS_INDEX);
                        mv.aload(THREADCONTEXT_INDEX);
                        mv.aload(RECEIVER_INDEX);
                        mv.aload(CLASS_INDEX);
                        mv.aload(NAME_INDEX);
                        mv.aload(ARGS_INDEX);
                        mv.aload(BLOCK_INDEX);

                        mv.invokevirtual(generatedClassPath, callName, COMPILED_CALL_SIG_BLOCK);
                        mv.areturn();
                    }

                    // if we fall off the switch, error.
                    mv.label(defaultCase);
                    mv.aload(THREADCONTEXT_INDEX);
                    mv.invokevirtual(p(ThreadContext.class), "getRuntime", sig(Ruby.class));
                    mv.ldc("Error: fell off switched invoker for class: " + implementationClass.getBaseName());
                    mv.invokevirtual(p(Ruby.class), "newRuntimeError", sig(RaiseException.class, String.class));
                    mv.athrow();

                    c = endCall(implementationClass.getRuntime(), cw, mv, generatedClassName);
                }

                for (int i = 0; i < annotatedMethods.size(); i++) {
                    Method method = annotatedMethods.get(i);
                    JRubyMethod jrubyMethod = method.getAnnotation(JRubyMethod.class);

                    if (jrubyMethod.frame()) {
                        for (String name : jrubyMethod.name()) {
                            ASTInspector.FRAME_AWARE_METHODS.add(name);
                        }
                    }

                    int index = indexMap.get(method);
                    JavaMethod ic = (JavaMethod)c.getConstructor(new Class[]{RubyModule.class, Visibility.class, int.class}).newInstance(new Object[]{implementationClass, jrubyMethod.visibility(), index});

                    ic.setArity(Arity.fromAnnotation(jrubyMethod));
                    ic.setJavaName(method.getName());
                    ic.setArgumentTypes(method.getParameterTypes());
                    ic.setSingleton(Modifier.isStatic(method.getModifiers()));
                    ic.setCallConfig(CallConfiguration.getCallConfigByAnno(jrubyMethod));

                    callback.define(implementationClass, method, ic);
                }
            } catch(Exception e) {
                e.printStackTrace();
                throw implementationClass.getRuntime().newLoadError(e.getMessage());
            }
        }
    }

    /**
     * Emit code to check the arity of a call to a Java-based method.
     * 
     * @param jrubyMethod The annotation of the called method
     * @param method The code generator for the handle being created
     */
    private void checkArity(JRubyMethod jrubyMethod, SkinnyMethodAdapter method, int specificArity) {
        Label arityError = new Label();
        Label noArityError = new Label();
        
        switch (specificArity) {
        case 0:
        case 1:
        case 2:
        case 3:
            // for zero, one, two, three arities, JavaMethod.JavaMethod*.call(...IRubyObject[] args...) will check
            return;
        default:
            if (jrubyMethod.rest()) {
                if (jrubyMethod.required() > 0) {
                    // just confirm minimum args provided
                    method.aload(ARGS_INDEX);
                    method.arraylength();
                    method.ldc(jrubyMethod.required());
                    method.if_icmplt(arityError);
                }
            } else if (jrubyMethod.optional() > 0) {
                if (jrubyMethod.required() > 0) {
                    // confirm minimum args provided
                    method.aload(ARGS_INDEX);
                    method.arraylength();
                    method.ldc(jrubyMethod.required());
                    method.if_icmplt(arityError);
                }

                // confirm maximum not greater than optional
                method.aload(ARGS_INDEX);
                method.arraylength();
                method.ldc(jrubyMethod.required() + jrubyMethod.optional());
                method.if_icmpgt(arityError);
            } else {
                // just confirm args length == required
                method.aload(ARGS_INDEX);
                method.arraylength();
                method.ldc(jrubyMethod.required());
                method.if_icmpne(arityError);
            }

            method.go_to(noArityError);

            // Raise an error if arity does not match requirements
            method.label(arityError);
            method.aload(THREADCONTEXT_INDEX);
            method.invokevirtual(p(ThreadContext.class), "getRuntime", sig(Ruby.class));
            method.aload(ARGS_INDEX);
            method.ldc(jrubyMethod.required());
            method.ldc(jrubyMethod.required() + jrubyMethod.optional());
            method.invokestatic(p(Arity.class), "checkArgumentCount", sig(int.class, Ruby.class, IRubyObject[].class, int.class, int.class));
            method.pop();

            method.label(noArityError);
        }
    }

    private ClassWriter createCompiledCtor(String namePath, String sup) throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_4, ACC_PUBLIC + ACC_SUPER, namePath, null, sup, null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", COMPILED_SUPER_SIG, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitVarInsn(ALOAD, 4);
        mv.visitVarInsn(ALOAD, 5);
        mv.visitVarInsn(ALOAD, 6);
        mv.visitMethodInsn(INVOKESPECIAL, sup, "<init>", COMPILED_SUPER_SIG);
        Label line = new Label();
        mv.visitLineNumber(0, line);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0,0);
        mv.visitEnd();
        return cw;
    }

    private ClassWriter createJavaMethodCtor(String namePath, String sup) throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_4, ACC_PUBLIC + ACC_SUPER, namePath, null, sup, null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", JAVA_SUPER_SIG, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitMethodInsn(INVOKESPECIAL, sup, "<init>", JAVA_SUPER_SIG);
        Label line = new Label();
        mv.visitLineNumber(0, line);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0,0);
        mv.visitEnd();
        return cw;
    }

    private ClassWriter createIndexedJavaMethodCtor(String namePath, String sup) throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_4, ACC_PUBLIC + ACC_SUPER, namePath, null, sup, null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", JAVA_INDEXED_SUPER_SIG, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitVarInsn(ILOAD, 3);
        mv.visitMethodInsn(INVOKESPECIAL, sup, "<init>", JAVA_INDEXED_SUPER_SIG);
        Label line = new Label();
        mv.visitLineNumber(0, line);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0,0);
        mv.visitEnd();
        return cw;
    }

    private void handleRedo(Label tryRedoJump, SkinnyMethodAdapter mv, Label tryFinally) {
        mv.label(tryRedoJump);

        // clear the redo
        mv.pop();

        // get runtime, dup it
        mv.aload(1);
        mv.invokevirtual(p(ThreadContext.class), "getRuntime", sig(Ruby.class));
        mv.invokevirtual(p(Ruby.class), "newRedoLocalJumpError", sig(RaiseException.class));
        mv.go_to(tryFinally);
    }

    private void handleReturn(Label catchReturnJump, SkinnyMethodAdapter mv, Label doFinally, Label normalExit, String typePath) {
        mv.label(catchReturnJump);

        mv.aload(0);
        mv.swap();
        mv.invokevirtual(typePath, "handleReturnJump", sig(IRubyObject.class, JumpException.ReturnJump.class));

        mv.astore(8);
        mv.go_to(normalExit);
    }

    private void invokeCallConfigPost(SkinnyMethodAdapter mv, String superClass) {
        //call post method stuff (non-finally)
        mv.aload(0);
        mv.aload(1);
        mv.invokevirtual(superClass, "post", sig(void.class, params(ThreadContext.class)));
    }

    private void invokeCallConfigPre(SkinnyMethodAdapter mv, String superClass, int specificArity, boolean block) {
        // invoke pre method stuff
        mv.aload(0); 
        mv.aload(THREADCONTEXT_INDEX); // tc
        mv.aload(RECEIVER_INDEX); // self
        mv.aload(NAME_INDEX); // name
        
        loadArgumentsForPre(mv, specificArity);
        
        loadBlockForPre(mv, specificArity, block);
        
        mv.invokevirtual(superClass, "pre", sig(void.class, params(ThreadContext.class, IRubyObject.class, String.class, IRubyObject[].class, Block.class)));
    }
    
    private void loadArgumentsForPre(SkinnyMethodAdapter mv, int specificArity) {
        switch (specificArity) {
        default:
        case -1:
            mv.aload(ARGS_INDEX); // args
            break;
        case 0:
            // zero arity but we have pre/post; use NULL_ARRAY
            mv.getstatic(p(IRubyObject.class), "NULL_ARRAY", ci(IRubyObject[].class));
            break;
        case 1:
            mv.aload(ARGS_INDEX);
            mv.invokestatic(p(RuntimeHelpers.class), "constructObjectArray", sig(IRubyObject[].class, IRubyObject.class));
            break;
        case 2:
            mv.aload(ARGS_INDEX);
            mv.aload(ARGS_INDEX + 1);
            mv.invokestatic(p(RuntimeHelpers.class), "constructObjectArray", sig(IRubyObject[].class, IRubyObject.class, IRubyObject.class));
            break;
        case 3:
            mv.aload(ARGS_INDEX);
            mv.aload(ARGS_INDEX + 1);
            mv.aload(ARGS_INDEX + 2);
            mv.invokestatic(p(RuntimeHelpers.class), "constructObjectArray", sig(IRubyObject[].class, IRubyObject.class, IRubyObject.class, IRubyObject.class));
            break;
        }
    }

    private void loadArguments(SkinnyMethodAdapter mv, JRubyMethod jrubyMethod, int specificArity) {
        switch (specificArity) {
        default:
        case -1:
            mv.aload(ARGS_INDEX);
            break;
        case 0:
            // no args
            break;
        case 1:
            mv.aload(ARGS_INDEX);
            break;
        case 2:
            mv.aload(ARGS_INDEX);
            mv.aload(ARGS_INDEX + 1);
            break;
        case 3:
            mv.aload(ARGS_INDEX);
            mv.aload(ARGS_INDEX + 1);
            mv.aload(ARGS_INDEX + 2);
            break;
        }
    }

    private void loadBlockForPre(SkinnyMethodAdapter mv, int specificArity, boolean getsBlock) {
        switch (specificArity) {
        default:
        case -1:
            if (getsBlock) {
                // variable args with block
                mv.visitVarInsn(ALOAD, BLOCK_INDEX);
            } else {
                // variable args no block, load null block
                mv.getstatic(p(Block.class), "NULL_BLOCK", ci(Block.class));
            }
            break;
        case 0:
            if (getsBlock) {
                // zero args with block
                // FIXME: omit args index; subtract one from normal block index
                mv.visitVarInsn(ALOAD, BLOCK_INDEX - 1);
            } else {
                // zero args, no block; load NULL_BLOCK
                mv.getstatic(p(Block.class), "NULL_BLOCK", ci(Block.class));
            }
            break;
        case 1:
            if (getsBlock) {
                // one arg with block
                mv.visitVarInsn(ALOAD, BLOCK_INDEX);
            } else {
                // one arg, no block; load NULL_BLOCK
                mv.getstatic(p(Block.class), "NULL_BLOCK", ci(Block.class));
            }
            break;
        case 2:
            if (getsBlock) {
                // two args with block
                mv.visitVarInsn(ALOAD, BLOCK_INDEX + 1);
            } else {
                // two args, no block; load NULL_BLOCK
                mv.getstatic(p(Block.class), "NULL_BLOCK", ci(Block.class));
            }
            break;
        case 3:
            if (getsBlock) {
                // three args with block
                mv.visitVarInsn(ALOAD, BLOCK_INDEX + 2);
            } else {
                // three args, no block; load NULL_BLOCK
                mv.getstatic(p(Block.class), "NULL_BLOCK", ci(Block.class));
            }
            break;
        }
    }

    private void loadBlock(SkinnyMethodAdapter mv, int specificArity, boolean getsBlock) {
        // load block if it accepts block
        switch (specificArity) {
        default:
        case -1:
            if (getsBlock) {
                // all other arg cases with block
                mv.visitVarInsn(ALOAD, BLOCK_INDEX);
            } else {
                // all other arg cases without block
            }
            break;
        case 0:
            if (getsBlock) {
                mv.visitVarInsn(ALOAD, BLOCK_INDEX - 1);
            } else {
                // zero args, no block; do nothing
            }
            break;
        case 1:
            if (getsBlock) {
                mv.visitVarInsn(ALOAD, BLOCK_INDEX);
            } else {
                // one arg, no block; do nothing
            }
            break;
        case 2:
            if (getsBlock) {
                mv.visitVarInsn(ALOAD, BLOCK_INDEX + 1);
            } else {
                // two args, no block; do nothing
            }
            break;
        case 3:
            if (getsBlock) {
                mv.visitVarInsn(ALOAD, BLOCK_INDEX + 2);
            } else {
                // three args, no block; do nothing
            }
            break;
        }
    }

    private void loadReceiver(String typePath, Method method, SkinnyMethodAdapter mv) {
        // load target for invocations
        if (Modifier.isStatic(method.getModifiers())) {
            // load self object as IRubyObject, for recv param
            mv.aload(RECEIVER_INDEX);
        } else {
            // load receiver as original type for virtual invocation
            mv.aload(RECEIVER_INDEX);
            mv.checkcast(typePath);
        }
    }

    private Class tryClass(Ruby runtime, String name) {
        try {
            if (classLoader == null) {
                return Class.forName(name, true, runtime.getJRubyClassLoader());
            }
             
            return classLoader.loadClass(name);
        } catch(Exception e) {
            return null;
        }
    }

    protected Class endCall(Ruby runtime, ClassWriter cw, MethodVisitor mv, String name) {
        endMethod(mv);
        return endClass(runtime, cw, name);
    }

    protected void endMethod(MethodVisitor mv) {
        mv.visitMaxs(0,0);
        mv.visitEnd();
    }

    protected Class endClass(Ruby runtime, ClassWriter cw, String name) {
        cw.visitEnd();
        byte[] code = cw.toByteArray();
        CheckClassAdapter.verify(new ClassReader(code), false, new PrintWriter(System.err));
        if (classLoader == null) classLoader = runtime.getJRubyClassLoader();
         
        return classLoader.defineClass(name, code);
    }
    
    private void loadArguments(MethodVisitor mv, int argsIndex, int count) {
        for (int i = 0; i < count; i++) {
            loadArgument(mv, argsIndex, i);
        }
    }
    
    private void loadArgument(MethodVisitor mv, int argsIndex, int argIndex) {
        mv.visitVarInsn(ALOAD, argsIndex);
        mv.visitLdcInsn(new Integer(argIndex));
        mv.visitInsn(AALOAD);
    }
    
    private SkinnyMethodAdapter beginMethod(ClassWriter cw, String methodName, int specificArity, boolean block) {
        switch (specificArity) {
        default:
        case -1:
            if (block) {
                return new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, methodName, COMPILED_CALL_SIG_BLOCK, null, null));
            } else {
                return new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, methodName, COMPILED_CALL_SIG, null, null));
            }
        case 0:
            if (block) {
                return new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, methodName, COMPILED_CALL_SIG_ZERO_BLOCK, null, null));
            } else {
                return new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, methodName, COMPILED_CALL_SIG_ZERO, null, null));
            }
        case 1:
            if (block) {
                return new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, methodName, COMPILED_CALL_SIG_ONE_BLOCK, null, null));
            } else {
                return new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, methodName, COMPILED_CALL_SIG_ONE, null, null));
            }
        case 2:
            if (block) {
                return new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, methodName, COMPILED_CALL_SIG_TWO_BLOCK, null, null));
            } else {
                return new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, methodName, COMPILED_CALL_SIG_TWO, null, null));
            }
        case 3:
            if (block) {
                return new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, methodName, COMPILED_CALL_SIG_THREE_BLOCK, null, null));
            } else {
                return new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, methodName, COMPILED_CALL_SIG_THREE, null, null));
            }
        }
    }
    
    private Class selectSuperClass(int specificArity, boolean block) {
        switch (specificArity) {
        default:
        case -1:
            if (block) {
                return JavaMethod.class;
            } else {
                return JavaMethod.JavaMethodNoBlock.class;
            }
        case 0:
            if (block) {
                return JavaMethod.JavaMethodZeroBlock.class;
            } else {
                return JavaMethod.JavaMethodZero.class;
            }
        case 1:
            if (block) {
                return JavaMethod.JavaMethodOneBlock.class;
            } else {
                return JavaMethod.JavaMethodOne.class;
            }
        case 2:
            if (block) {
                return JavaMethod.JavaMethodTwoBlock.class;
            } else {
                return JavaMethod.JavaMethodTwo.class;
            }
        case 3:
            if (block) {
                return JavaMethod.JavaMethodThreeBlock.class;
            } else {
                return JavaMethod.JavaMethodThree.class;
            }
        }
    }

    private String getAnnotatedMethodForIndex(ClassWriter cw, Method method, int index, String superClass) {
        String methodName = "call" + index + "_" + method.getName();
        SkinnyMethodAdapter mv = new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, methodName, COMPILED_CALL_SIG_BLOCK, null, null));
        mv.visitCode();
        Label line = new Label();
        mv.visitLineNumber(0, line);
        // TODO: indexed methods do not use specific arity yet
        createAnnotatedMethodInvocation(method, mv, superClass, -1, true);
        endMethod(mv);
        
        return methodName;
    }

    private void createAnnotatedMethodInvocation(Method javaMethod, SkinnyMethodAdapter method, String superClass, int specificArity, boolean block) {
        JRubyMethod jrubyMethod = javaMethod.getAnnotation(JRubyMethod.class);
        String typePath = p(javaMethod.getDeclaringClass());
        String javaMethodName = javaMethod.getName();
        Class[] signature = javaMethod.getParameterTypes();
        Class ret = javaMethod.getReturnType();

        checkArity(jrubyMethod, method, specificArity);
        
        CallConfiguration callConfig = CallConfiguration.getCallConfigByAnno(jrubyMethod);
        if (!callConfig.isNoop()) {
            invokeCallConfigPre(method, superClass, specificArity, block);
        }

        Label tryBegin = new Label();
        Label tryEnd = new Label();
        Label doFinally = new Label();
        Label catchReturnJump = new Label();
        Label catchRedoJump = new Label();
        Label normalExit = new Label();

        method.trycatch(tryBegin, tryEnd, catchReturnJump, p(JumpException.ReturnJump.class));
        method.trycatch(tryBegin, tryEnd, catchRedoJump, p(JumpException.RedoJump.class));
        method.trycatch(tryBegin, tryEnd, doFinally, null);
        method.trycatch(catchReturnJump, doFinally, doFinally, null);
        
        method.label(tryBegin);
        {
            loadReceiver(typePath, javaMethod, method);
            
            loadArguments(method, jrubyMethod, specificArity);
            
            loadBlock(method, specificArity, block);

            if (Modifier.isStatic(javaMethod.getModifiers())) {
                // static invocation
                method.invokestatic(typePath, javaMethodName, sig(ret, signature));
            } else {
                // virtual invocation
                method.invokevirtual(typePath, javaMethodName, sig(ret, signature));
            }
        }
                
        // store result in temporary variable 8
        method.astore(8);

        method.label(tryEnd);
        
        method.label(normalExit);
        
        if (!callConfig.isNoop()) {
            invokeCallConfigPost(method, superClass);
        }
        
        // reload and return result
        method.aload(8);
        method.visitInsn(ARETURN);

        handleReturn(catchReturnJump,method, doFinally, normalExit, superClass);
        
        handleRedo(catchRedoJump, method, doFinally);

        // finally handling for abnormal exit
        method.label(doFinally);
        {
            if (!callConfig.isNoop()) {
                invokeCallConfigPost(method, superClass);
            }

            // rethrow exception
            method.athrow(); // rethrow it
        }
    }
}

/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.invoke;

import java.lang.reflect.InvocationTargetException;

/**
 * MethodHandleProxyLambdaMetafactory
 *
 * @author Brian Goetz
 */
/*non-public*/ final class MethodHandleProxyLambdaMetafactory extends AbstractValidatingLambdaMetafactory {

    private final MethodHandle implMethod;

    /**
     * Meta-factory constructor.
     *
     * @param caller Stacked automatically by VM; represents a lookup context with the accessibility privileges
     *               of the caller.
     * @param invokedType Stacked automatically by VM; the signature of the invoked method, which includes the
     *                    expected static type of the returned lambda object, and the static types of the captured
     *                    arguments for the lambda.  In the event that the implementation method is an instance method,
     *                    the first argument in the invocation signature will correspond to the receiver.
     * @param samMethod The primary method in the functional interface to which the lambda or method reference is
     *                  being converted, represented as a method handle.
     * @param implMethod The implementation method which should be called (with suitable adaptation of argument
     *                   types, return types, and adjustment for captured arguments) when methods of the resulting
     *                   functional interface instance are invoked.
     * @param instantiatedMethodType The signature of the SAM method from the functional interface's perspective
     * @throws ReflectiveOperationException
     */
    MethodHandleProxyLambdaMetafactory(MethodHandles.Lookup caller,
                                       MethodType invokedType,
                                       MethodHandle samMethod,
                                       MethodHandle implMethod,
                                       MethodType instantiatedMethodType)
            throws ReflectiveOperationException {
        super(caller, invokedType, samMethod, implMethod, instantiatedMethodType);
        this.implMethod = implMethod;
    }

    /**
     * Build the CallSite.
     *
     * @return a CallSite, which, when invoked, will return an instance of the
     * functional interface
     * @throws ReflectiveOperationException
     */
    @Override
    CallSite buildCallSite() throws LambdaConversionException {
        // @@@ Special bindTo case for count=1 && isInstance

        if (invokedType.parameterCount() == 0) {
            return new ConstantCallSite(MethodHandles.constant(samBase,
                MethodHandleProxies.asInterfaceInstance(samBase, implMethod)));
        }
        else {
            try {
                return new MhMetafactoryCallSite(implMethod, invokedType, samBase);
            }
            catch (Throwable e) {
                // log("Exception constructing Lambda factory callsite", e);
                throw new LambdaConversionException("Error constructing CallSite", e);
            }
        }
    }

    static class MhMetafactoryCallSite extends ConstantCallSite {
        static final MethodType MT_ADAPTER = MethodType.fromMethodDescriptorString("(Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/CallSite;)Ljava/lang/invoke/MethodHandle;", null);
        static final MethodType MT_FACTORY = MethodType.fromMethodDescriptorString("([Ljava/lang/Object;)Ljava/lang/Object;", null);
        static final MethodHandle MH_FACTORY;
        static final MethodHandle MH_ADAPTER;

        static {
            try {
                MH_ADAPTER = MethodHandles.Lookup.IMPL_LOOKUP.findStatic(MhMetafactoryCallSite.class, "adapter", MT_ADAPTER);
                MH_FACTORY = MethodHandles.Lookup.IMPL_LOOKUP.findVirtual(MhMetafactoryCallSite.class, "factory", MT_FACTORY);
            }
            catch (NoSuchMethodException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final MethodHandle implHandle;
        private final Class<?> samClass;

        public MhMetafactoryCallSite(MethodHandle implHandle,
                                     MethodType methodType,
                                     Class<?> samClass) throws Throwable {
            // This bit of method handle magic means to permanently bind the lambda factory site
            // to the factory method, converting any arguments passed to the factory site into an Object[]
            // box.  This seemingly circuitous path is needed so that the call site target (factory) can
            // know about the CallSite, so it can access the instance fields of the CallSite
            super(methodType,
                  MH_ADAPTER.bindTo(MH_FACTORY.asCollector(Object[].class, methodType.parameterCount())));
            this.implHandle = implHandle;
            this.samClass = samClass;
        }

        public Object factory(Object[] args) throws InvocationTargetException, IllegalAccessException, InstantiationException {
            return MethodHandleProxies.asInterfaceInstance(samClass, MethodHandles.insertArguments(implHandle, 0, args));
        }

        public static MethodHandle adapter(MethodHandle target, CallSite cs) {
            return target.bindTo(cs).asType(cs.type());
        }
    }
}

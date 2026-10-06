/*
 * Copyright (c) 2026, Alessandro Autiero. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @summary C2 must not narrow the result of a call it inlines late to the class the type profile
 *          of a checkcast recorded: the profile is shared by every caller of the method holding
 *          the cast, and once the call is inlined its result can be a constant of another class,
 *          which the narrowed check then rejects on every execution.
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=inline,compiler.inlining.TestProfiledCastOfLateInlineResult::run
 *                   -XX:CompileCommand=inline,compiler.inlining.TestProfiledCastOfLateInlineResult::lookup
 *                   -XX:CompileCommand=delayinline,compiler.inlining.TestProfiledCastOfLateInlineResult::lookup
 *                   -XX:CompileCommand=exclude,compiler.inlining.TestProfiledCastOfLateInlineResult::warm
 *                   compiler.inlining.TestProfiledCastOfLateInlineResult
 */

package compiler.inlining;

import java.lang.reflect.Method;
import jdk.test.whitebox.WhiteBox;

public class TestProfiledCastOfLateInlineResult {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    interface Operation {
    }

    static final class Increment implements Operation {
    }

    static final class Decrement implements Operation {
    }

    static final Operation INCREMENT = new Increment();
    static final Operation DECREMENT = new Decrement();

    // Returns a constant once inlined with a constant argument, like a lookup in a @Stable cache.
    static Object lookup(int which) {
        return which == 0 ? INCREMENT : DECREMENT;
    }

    static volatile Operation sink;

    // The cast's profile is shared by every caller of this method.
    static int run(int which, int x) {
        sink = (Operation) lookup(which);
        return x + which;
    }

    // Records only Increment in the profile of the cast in run(), from the interpreter.
    static int warm(int x) {
        return run(0, x);
    }

    // Uses the other operation; compiled before it ever runs, so it adds nothing to the profile.
    static int kernel(int x) {
        return run(1, x);
    }

    public static void main(String[] args) throws Exception {
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += warm(i);
        }
        Method kernel = TestProfiledCastOfLateInlineResult.class.getDeclaredMethod("kernel", int.class);
        if (!WB.enqueueMethodForCompilation(kernel, 4) || WB.getMethodCompilationLevel(kernel) != 4) {
            throw new RuntimeException("kernel() was not compiled by C2");
        }
        for (int i = 0; i < 10; i++) {
            sum += kernel(i);
        }
        if (!WB.isMethodCompiled(kernel)) {
            throw new RuntimeException("The C2 code of kernel() was deoptimized: its inlined cast followed"
                                       + " the profile of run() instead of the inlined lookup's result");
        }
        System.out.println("kernel() kept its C2 code (" + sum + ")");
    }
}

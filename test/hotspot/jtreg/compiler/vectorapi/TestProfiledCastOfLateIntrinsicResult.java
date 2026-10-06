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
 * @summary C2 must narrow the result of a Vector API intrinsic it inlines late to the vector class
 *          the call names, not to the class the type profile of the cast after it recorded: the
 *          profile is shared by every species that runs the template holding the cast.
 * @requires vm.compiler2.enabled
 * @requires vm.cpu.features ~= ".*avx2.*"
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   --add-modules jdk.incubator.vector
 *                   -XX:-TieredCompilation -XX:-BackgroundCompilation
 *                   -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=exclude,compiler.vectorapi.TestProfiledCastOfLateIntrinsicResult::warm
 *                   compiler.vectorapi.TestProfiledCastOfLateIntrinsicResult
 */

package compiler.vectorapi;

import java.lang.reflect.Method;
import jdk.incubator.vector.IntVector;
import jdk.test.whitebox.WhiteBox;

public class TestProfiledCastOfLateIntrinsicResult {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    // Records only the 128-bit vector class in the profile of the cast in IntSpecies.broadcastBits().
    static int warm(int x) {
        return IntVector.broadcast(IntVector.SPECIES_128, x).lane(0);
    }

    // Uses the 256-bit species; compiled before it ever runs, so it adds nothing to the profile.
    static int kernel(int x) {
        return IntVector.broadcast(IntVector.SPECIES_256, x).lane(0);
    }

    public static void main(String[] args) throws Exception {
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += warm(i);
        }
        Method kernel = TestProfiledCastOfLateIntrinsicResult.class.getDeclaredMethod("kernel", int.class);
        if (!WB.enqueueMethodForCompilation(kernel, 4) || WB.getMethodCompilationLevel(kernel) != 4) {
            throw new RuntimeException("kernel() was not compiled by C2");
        }
        for (int i = 0; i < 10; i++) {
            sum += kernel(i);
        }
        if (!WB.isMethodCompiled(kernel)) {
            throw new RuntimeException("The C2 code of kernel() was deoptimized: the broadcast result was narrowed"
                                       + " to the profiled 128-bit class instead of the 256-bit class it names");
        }
        System.out.println("kernel() kept its C2 code (" + sum + ")");
    }
}

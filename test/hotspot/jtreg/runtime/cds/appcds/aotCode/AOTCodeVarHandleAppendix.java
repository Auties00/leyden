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
 *
 */

/*
 * @test
 * @summary AOT code that embeds the appendix of an AOT-resolved VarHandle call site is stored.
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @library /test/lib
 * @build AOTCodeVarHandleAppendix
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTCodeVarHandleAppendixApp
 * @run driver AOTCodeVarHandleAppendix
 */

import java.util.concurrent.atomic.AtomicIntegerArray;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCodeVarHandleAppendix {
    public static void main(String... args) throws Exception {
        new Tester().runAOTWorkflow("AOT", "--two-step-training");
    }

    static class Tester extends CDSAppTester {
        Tester() {
            super("AOTCodeVarHandleAppendix");
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (runMode == RunMode.ASSEMBLY) {
                return new String[] {"-Xlog:aot+codecache+nmethod=info", "-Xlog:aot+codecache+oops=debug"};
            }
            return new String[0];
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {"AOTCodeVarHandleAppendixApp"};
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.ASSEMBLY) {
                // AtomicIntegerArray.set() calls VarHandle.setVolatile(), whose call site has an
                // AccessDescriptor appendix.
                out.shouldMatch("Wrote nmethod 'java.util.concurrent.atomic.AtomicIntegerArray::set\\(");
                out.shouldNotMatch("Not archived Java object: .* java.lang.invoke.VarHandle\\$AccessDescriptor");
            } else if (runMode == RunMode.PRODUCTION) {
                out.shouldContain("sum=");
            }
        }
    }
}

class AOTCodeVarHandleAppendixApp {
    public static void main(String[] args) {
        AtomicIntegerArray a = new AtomicIntegerArray(16);
        long sum = 0;
        for (int i = 0; i < 200_000; i++) {
            a.set(i & 15, i);
            sum += a.get((i + 1) & 15);
        }
        System.out.println("sum=" + sum);
    }
}

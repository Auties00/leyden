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
 * @summary AOT code that calls the Math.sinh, Math.tanh and Math.cbrt intrinsic stubs is stored.
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @library /test/lib
 * @build AOTCodeMathStubs
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTCodeMathStubsApp
 * @run driver AOTCodeMathStubs
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCodeMathStubs {
    public static void main(String... args) throws Exception {
        new Tester().runAOTWorkflow("AOT", "--two-step-training");
    }

    static class Tester extends CDSAppTester {
        Tester() {
            super("AOTCodeMathStubs");
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (runMode == RunMode.ASSEMBLY) {
                return new String[] {"-Xlog:aot+codecache+nmethod=info"};
            }
            return new String[0];
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {"AOTCodeMathStubsApp"};
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.ASSEMBLY) {
                for (String kernel : new String[] {"sinh", "tanh", "cbrt"}) {
                    out.shouldMatch("Wrote nmethod 'AOTCodeMathStubsApp::" + kernel + "\\(");
                }
            } else if (runMode == RunMode.PRODUCTION) {
                out.shouldContain("sum=");
            }
        }
    }
}

class AOTCodeMathStubsApp {
    static double sinh(double[] values) {
        double s = 0;
        for (double v : values) {
            s += Math.sinh(v);
        }
        return s;
    }

    static double tanh(double[] values) {
        double s = 0;
        for (double v : values) {
            s += Math.tanh(v);
        }
        return s;
    }

    static double cbrt(double[] values) {
        double s = 0;
        for (double v : values) {
            s += Math.cbrt(v);
        }
        return s;
    }

    public static void main(String[] args) {
        double[] values = new double[256];
        for (int i = 0; i < values.length; i++) {
            values[i] = (i - 128) / 32.0;
        }
        double sum = 0;
        for (int r = 0; r < 20_000; r++) {
            sum += sinh(values) + tanh(values) + cbrt(values);
        }
        System.out.println("sum=" + sum);
    }
}

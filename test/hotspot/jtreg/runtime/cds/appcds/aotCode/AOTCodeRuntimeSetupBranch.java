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
 * @summary AOT code does not speculate on the training run's value of a field that a runtimeSetup()
 *          method sets again in the production run, such as the iteration direction of Set.of().
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @library /test/lib
 * @build AOTCodeRuntimeSetupBranch
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTCodeRuntimeSetupBranchApp
 * @run driver AOTCodeRuntimeSetupBranch
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCodeRuntimeSetupBranch {
    static final String NEXT = "java.util.ImmutableCollections$SetN$SetNIterator::next";
    // ImmutableCollections.REVERSE is random in each process; enough runs make one differ from training.
    static final int MAX_RUNS = 24;

    static String trainingReverse;

    public static void main(String... args) throws Exception {
        Tester t = new Tester();
        t.runAOTWorkflow("AOT", "--two-step-training");

        boolean differed = false;
        for (int i = 0; i < MAX_RUNS && !differed; i++) {
            OutputAnalyzer out = t.productionRun();
            out.shouldContain("Read nmethod '" + NEXT + "()");
            out.shouldNotMatch(Pattern.quote(NEXT) + ".*trap_bci");
            differed = !reverse(out).equals(trainingReverse);
        }
        if (!differed) {
            throw new jtreg.SkippedException("REVERSE was " + trainingReverse + " in every run");
        }
    }

    static String reverse(OutputAnalyzer out) {
        Matcher m = Pattern.compile("REVERSE=(true|false)").matcher(out.getStdout());
        if (!m.find()) {
            throw new RuntimeException("no REVERSE in the output");
        }
        return m.group(1);
    }

    static class Tester extends CDSAppTester {
        Tester() {
            super("AOTCodeRuntimeSetupBranch");
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            // A call to next() keeps its own code, and the regular AOT code is loaded without
            // the preload code before it.
            String[] common = {"--add-opens", "java.base/java.util=ALL-UNNAMED",
                               "-XX:CompileCommand=quiet",
                               "-XX:CompileCommand=dontinline," + NEXT};
            if (runMode == RunMode.PRODUCTION) {
                return concat(common, "-XX:+UnlockDiagnosticVMOptions", "-XX:AOTCodePreloadStop=0",
                              "-Xlog:deoptimization=debug", "-Xlog:aot+codecache+nmethod=info");
            }
            return common;
        }

        static String[] concat(String[] a, String... b) {
            String[] r = java.util.Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, r, a.length, b.length);
            return r;
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {"AOTCodeRuntimeSetupBranchApp", "300000"};
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.TRAINING) {
                trainingReverse = reverse(out);
            }
        }
    }
}

class AOTCodeRuntimeSetupBranchApp {
    static final java.util.Set<String> SET = java.util.Set.of("a", "b", "c", "d", "e", "f", "g");

    public static void main(String[] args) throws Exception {
        var reverse = Class.forName("java.util.ImmutableCollections").getDeclaredField("REVERSE");
        reverse.setAccessible(true);
        int rounds = Integer.parseInt(args[0]);
        long sum = 0;
        for (int r = 0; r < rounds; r++) {
            for (String s : SET) {
                sum += s.hashCode() * (r & 7);
            }
        }
        System.out.println("REVERSE=" + reverse.getBoolean(null) + " sum=" + sum);
    }
}

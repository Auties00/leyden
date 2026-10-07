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
 * @test id=guards
 * @summary An AOT compilation makes the inlining decisions of the training's JIT compilation: the
 *          speculation guards of AOT code do not count against NodeCountInliningCutoff.
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @build AOTCodeInliningCutoff
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTCodeInliningCutoffApp AOTCodeInliningCutoffProfileApp
 * @run driver AOTCodeInliningCutoff AOTCodeInliningCutoffApp
 */

/*
 * @test id=profile
 * @summary An AOT compilation inlines past NodeCountInliningCutoff a callee that the training compiled
 *          with C2 only as part of other methods, so the AOT code does not call its tier 2 code.
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @build AOTCodeInliningCutoff
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTCodeInliningCutoffApp AOTCodeInliningCutoffProfileApp
 * @run driver AOTCodeInliningCutoff AOTCodeInliningCutoffProfileApp
 */

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCodeInliningCutoff {
    public static void main(String... args) throws Exception {
        new Tester(args[0]).runAOTWorkflow("AOT", "--two-step-training");
    }

    static class Tester extends CDSAppTester {
        final String app;
        final Pattern target;

        Tester(String app) {
            super(app);
            this.app = app;
            this.target = Pattern.compile(Pattern.quote(app + "::target") + " \\(\\d+ bytes\\)\\s+(.*)");
        }

        // Returns the inlining decisions printed for calls to target() in the compilations of root().
        List<String> targetDecisions(OutputAnalyzer out) {
            List<String> decisions = new ArrayList<>();
            var m = target.matcher(out.getStdout());
            while (m.find()) {
                decisions.add(m.group(1).trim());
            }
            return decisions;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            String[] common = {"--add-modules", "jdk.incubator.vector",
                               "-XX:+UnlockDiagnosticVMOptions",
                               "-XX:CompileCommand=quiet",
                               "-XX:CompileCommand=PrintInlining," + app + "::root"};
            if (runMode == RunMode.TRAINING) {
                return concat(common, "-Xbatch");
            }
            if (runMode == RunMode.ASSEMBLY && app.equals("AOTCodeInliningCutoffApp")) {
                // Only regular AOT compilations: target() has C2 code of its own, so a preload
                // compilation, which also compiles the paths of uncommon traps, may leave it out of line.
                return concat(common, "-XX:ClassInitBarrierMode=0");
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
            return new String[] {app, "200000"};
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            List<String> decisions = targetDecisions(out);
            if (runMode == RunMode.TRAINING) {
                // The precondition: the JIT compilation of root() inlines target().
                if (decisions.stream().noneMatch(d -> d.startsWith("inline"))) {
                    throw new RuntimeException("The training's JIT compilation did not inline target(): " + decisions);
                }
            } else if (runMode == RunMode.ASSEMBLY) {
                if (decisions.isEmpty() || !decisions.stream().allMatch(d -> d.startsWith("inline"))) {
                    throw new RuntimeException("The AOT compilation of root() did not inline target(): " + decisions);
                }
            }
        }
    }
}

class AOTCodeInliningCutoffApp {
    // The Vector API value makes the training record the static finals of this class: an AOT
    // compilation reads each of them with a guard, a JIT compilation folds it.
    static final jdk.incubator.vector.VectorSpecies<Integer> SPECIES = jdk.incubator.vector.IntVector.SPECIES_64;
    static final int K0 = value(1);
    static final int K1 = value(2);
    static final int K2 = value(3);
    static final int K3 = value(4);
    static final int K4 = value(5);
    static final int K5 = value(6);
    static final int K6 = value(7);
    static final int K7 = value(8);

    static int value(int i) {
        return Integer.getInteger("AOTCodeInliningCutoffApp.seed", 17) * i;
    }

    public static void main(String[] args) {
        int rounds = Integer.parseInt(args[0]);
        int[] a = new int[64];
        int sum = 0;
        for (int r = 0; r < rounds; r++) {
            sum += root(a, r);
        }
        // target() also gets C2 code of its own, so only the guards decide the AOT compilation.
        for (int r = 0; r < rounds; r++) {
            sum += target(a, r);
        }
        System.out.println("sum=" + sum);
    }

    // Guards and fillers bring the AOT compilation's node count past NodeCountInliningCutoff at the
    // call to target() unless the guards are not counted; the JIT compilation stays below it.
    static int root(int[] a, int s) {
        s += SPECIES.length();
        s = guarded(s);
        s = guarded(s);
        s = guarded(s);
        s = guarded(s);
        s = guarded(s);
        s = guarded(s);
        s = guarded(s);
        s = guarded(s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        return target(a, s);
    }

    static int guarded(int s) {
        s = s * 31 + K0; s = s * 31 + K1; s = s * 31 + K2; s = s * 31 + K3;
        s = s * 31 + K4; s = s * 31 + K5; s = s * 31 + K6; s = s * 31 + K7;
        s = s * 31 + K0; s = s * 31 + K1; s = s * 31 + K2; s = s * 31 + K3;
        s = s * 31 + K4; s = s * 31 + K5; s = s * 31 + K6; s = s * 31 + K7;
        s = s * 31 + K0; s = s * 31 + K1; s = s * 31 + K2; s = s * 31 + K3;
        s = s * 31 + K4; s = s * 31 + K5; s = s * 31 + K6; s = s * 31 + K7;
        s = s * 31 + K0; s = s * 31 + K1; s = s * 31 + K2; s = s * 31 + K3;
        s = s * 31 + K4; s = s * 31 + K5; s = s * 31 + K6; s = s * 31 + K7;
        return s;
    }

    static int filler(int[] a, int s) {
        int i = s & 31;
        s += a[i] * 3;       a[i + 1] = s;
        s ^= a[i + 2] << 1;  a[i + 3] = s >>> 2;
        s += a[i + 4] * 5;   a[i + 5] = s ^ i;
        s ^= a[i + 6] >> 3;  a[i + 7] = s + i;
        s += a[i + 8] * 7;   a[i + 9] = s;
        s ^= a[i + 10] << 2; a[i + 11] = s >>> 1;
        s += a[i + 12] * 9;  a[i + 13] = s ^ 5;
        s ^= a[i + 14] >> 1; a[i + 15] = s + 3;
        return s;
    }

    static int target(int[] a, int s) {
        int i = s & 31;
        s += a[i] * 11;
        a[i + 1] = s ^ (s >>> 7);
        s ^= a[i + 2] * 13;
        a[i + 3] = s + (s << 3);
        return s;
    }
}


class AOTCodeInliningCutoffProfileApp {
    static boolean wide;

    public static void main(String[] args) {
        int rounds = Integer.parseInt(args[0]);
        int[] a = new int[64];
        int sum = 0;
        // root() is compiled while the wide branch of filler() has never been taken ...
        for (int r = 0; r < rounds; r++) {
            sum += root(a, r);
        }
        // ... and then taken from another caller, so the AOT compilation of root() parses it and
        // reaches NodeCountInliningCutoff before the call to target().
        wide = true;
        for (int r = 0; r < rounds; r++) {
            sum += filler(a, r);
        }
        System.out.println("sum=" + sum);
    }

    static int root(int[] a, int s) {
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        s = filler(a, s);
        // target() runs too rarely to be compiled on its own before root() is.
        return (s & 7) == 0 ? target(a, s) : s;
    }

    static int filler(int[] a, int s) {
        int i = s & 31;
        s += a[i] * 3;
        a[i + 1] = s;
        if (wide) {
            int[] x;
            x = new int[8];
            x = new int[9];
            x = new int[10];
            x = new int[11];
            x = new int[12];
            x = new int[8];
            x = new int[9];
            x = new int[10];
            x = new int[11];
            x = new int[12];
            x = new int[8];
            x = new int[9];
            x = new int[10];
            x = new int[11];
            x = new int[12];
            x = new int[8];
            x = new int[9];
            x = new int[10];
            x = new int[11];
            x = new int[12];
            x = new int[8];
            x = new int[9];
            x = new int[10];
            x = new int[11];
            x = new int[12];
            x = new int[8];
            x = new int[9];
            x = new int[10];
            x = new int[11];
            x = new int[12];
            x = new int[8];
            x = new int[9];
            x = new int[10];
            x = new int[11];
            x = new int[12];
            x = new int[8];
            x = new int[9];
            x = new int[10];
            x = new int[11];
            x = new int[12];
            s += x.length;
        }
        return s;
    }

    static int target(int[] a, int s) {
        return a[s & 31] * 11 + (s ^ (s >>> 7));
    }
}

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
 * @test id=holder
 * @summary A Vector API kernel whose intrinsics are not inlined in the AOT compilation is not stored.
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @build AOTCodeVectorAPI
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             VectorKernels VectorKernels$Holder
 * @run driver AOTCodeVectorAPI holder
 */

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AOTCodeVectorAPI {
    static final String NOT_INLINED = "Vector API intrinsic not inlined in AOT compilation";
    static final String TRAINING_ROUNDS = "20000";
    // Fewer calls than in training, so that the AOT invocation counters do not request a JIT
    // compilation while the test runs.
    static final String PRODUCTION_ROUNDS = "1000";

    public static void main(String... args) throws Exception {
        String scenario = args[0];
        switch (scenario) {
            case "holder" -> holder();
            default -> throw new RuntimeException("unknown scenario " + scenario);
        }
    }

    static String kernelMethod(String kernel) {
        return Pattern.quote("VectorKernels$" + Character.toUpperCase(kernel.charAt(0)) + kernel.substring(1) + "::dot");
    }

    // The result of the kernel computed without an AOT cache.
    static String expectedSum(String kernel, String... vmArgs) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("--add-modules");
        cmd.add("jdk.incubator.vector");
        cmd.addAll(List.of(vmArgs));
        cmd.addAll(List.of("-cp", "app.jar", "VectorKernels", kernel, PRODUCTION_ROUNDS));
        OutputAnalyzer out = ProcessTools.executeTestJava(cmd.toArray(new String[0]));
        out.shouldHaveExitValue(0);
        Matcher m = Pattern.compile("sum=-?[0-9]+ ").matcher(out.getStdout());
        if (!m.find()) {
            throw new RuntimeException("no sum in " + out.getStdout());
        }
        return m.group();
    }

    static class Tester extends CDSAppTester {
        final String kernel;
        final String[] extraVmArgs;

        Tester(String kernel, String... extraVmArgs) {
            super("AOTCodeVectorAPI-" + kernel);
            this.kernel = kernel;
            this.extraVmArgs = extraVmArgs;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            List<String> args = new ArrayList<>(List.of("--add-modules", "jdk.incubator.vector"));
            args.addAll(List.of(extraVmArgs));
            if (runMode == RunMode.ASSEMBLY) {
                args.addAll(List.of("-XX:+PrintCompilation", "-Xlog:aot+codecache=info",
                                    "-Xlog:aot+codecache+nmethod=info"));
            } else if (runMode == RunMode.PRODUCTION) {
                args.add("-Xlog:aot+codecache+nmethod=info");
            }
            return args.toArray(new String[0]);
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {"VectorKernels", kernel,
                                 runMode == RunMode.TRAINING ? TRAINING_ROUNDS : PRODUCTION_ROUNDS};
        }
    }

    // A kernel whose species is held in an instance field cannot have its intrinsics inlined in
    // the AOT compilation: it is not stored, and the JIT compiles it at run time.
    static void holder() throws Exception {
        String method = kernelMethod("holder");
        String expected = expectedSum("holder");
        Tester t = new Tester("holder") {
            @Override
            public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
                if (runMode == RunMode.ASSEMBLY) {
                    out.shouldMatch(method + ".*COMPILE SKIPPED: " + NOT_INLINED);
                    out.shouldNotMatch("Wrote nmethod '" + method + "\\(");
                } else if (runMode == RunMode.PRODUCTION) {
                    out.shouldContain(expected);
                }
            }
        };
        t.runAOTWorkflow("AOT", "--two-step-training");
    }
}

class VectorKernels {
    static class Holder {
        static final Holder H = new Holder(IntVector.SPECIES_PREFERRED);

        final VectorSpecies<Integer> species;

        Holder(VectorSpecies<Integer> species) {
            this.species = species;
        }

        static int dot(int[] a, int[] b) {
            var sp = H.species;
            var acc = IntVector.zero(sp);
            var i = 0;
            for (; i <= a.length - sp.length(); i += sp.length()) {
                acc = IntVector.fromArray(sp, a, i).mul(IntVector.fromArray(sp, b, i)).add(acc);
            }
            var s = acc.reduceLanes(VectorOperators.ADD);
            for (; i < a.length; i++) {
                s += a[i] * b[i];
            }
            return s;
        }
    }

    static int dot(String kernel, int[] a, int[] b) {
        return switch (kernel) {
            case "holder" -> Holder.dot(a, b);
            default -> throw new IllegalArgumentException(kernel);
        };
    }

    public static void main(String[] args) {
        var a = new int[1024];
        var b = new int[1024];
        for (var i = 0; i < a.length; i++) {
            a[i] = i % 7;
            b[i] = i % 5;
        }
        String kernel = args[0];
        int rounds = Integer.parseInt(args[1]);
        long s = 0;
        for (var r = 0; r < rounds; r++) {
            s += dot(kernel, a, b);
        }
        System.out.println("sum=" + s + " ");
    }
}

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
 *             VectorKernels VectorKernels$Holder VectorKernels$Jdk VectorKernels$Ops
 * @run driver AOTCodeVectorAPI holder
 */

/*
 * @test id=jdk
 * @summary A Vector API kernel using the JDK's species is stored as preload code with its intrinsics.
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox AOTCodeVectorAPI
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             VectorKernels VectorKernels$Holder VectorKernels$Jdk VectorKernels$Ops
 * @run driver AOTCodeVectorAPI jdk
 */

/*
 * @test id=jdk-serial-nocoops
 * @summary The same kernel with the Serial GC, uncompressed oops and a GC between calls.
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @requires vm.gc.Serial
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox AOTCodeVectorAPI
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             VectorKernels VectorKernels$Holder VectorKernels$Jdk VectorKernels$Ops
 * @run driver AOTCodeVectorAPI jdk -XX:+UseSerialGC -XX:-UseCompressedOops
 */

/*
 * @test id=onestep
 * @summary The kernel is stored with the one-step workflow (-XX:AOTCacheOutput).
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox AOTCodeVectorAPI
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             VectorKernels VectorKernels$Holder VectorKernels$Jdk VectorKernels$Ops
 * @run driver AOTCodeVectorAPI onestep
 */

/*
 * @test id=ops
 * @summary Kernels using masks, conversions, shuffles, compress and bit operations are stored.
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox AOTCodeVectorAPI
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             VectorKernels VectorKernels$Holder VectorKernels$Jdk VectorKernels$Ops
 * @run driver AOTCodeVectorAPI ops
 */

/*
 * @test id=validation
 * @summary A cache with Vector API state is not used with other vector sizes, OOB checks or vector support.
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @build AOTCodeVectorAPI
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             VectorKernels VectorKernels$Holder VectorKernels$Jdk VectorKernels$Ops
 * @run driver AOTCodeVectorAPI validation
 */

/*
 * @test id=allclasses
 * @summary A cache can be created when training initialized every class of jdk.incubator.vector.
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.flagless
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @build AOTCodeVectorAPI
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             VectorKernels VectorKernels$Holder VectorKernels$Jdk VectorKernels$Ops
 * @run driver AOTCodeVectorAPI allclasses
 */

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorSpecies;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AOTCodeVectorAPI {
    static final String NOT_INLINED = "Vector API intrinsic not inlined in AOT compilation";
    static final String TRAINING_ROUNDS = "20000";
    // Fewer calls than in training, so that the AOT invocation counters do not request a JIT
    // compilation while the test runs.
    static final String PRODUCTION_ROUNDS = "1000";

    static final String VECTOR_MODULE = "jdk.incubator.vector";

    public static void main(String... args) throws Exception {
        String scenario = args[0];
        String[] vmArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (scenario) {
            case "holder" -> holder();
            case "jdk" -> stored("jdk", false, vmArgs);
            case "onestep" -> stored("jdk", true, vmArgs);
            case "ops" -> stored("ops", false, vmArgs);
            case "validation" -> validation();
            case "allclasses" -> allClasses();
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
        cmd.addAll(List.of("-cp", "app.jar", "VectorKernels", kernel, PRODUCTION_ROUNDS, "nogc"));
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
                                 runMode == RunMode.TRAINING ? TRAINING_ROUNDS : PRODUCTION_ROUNDS, "gc"};
        }
    }

    // A kernel whose species are JDK constants is stored as preload code with its intrinsics:
    // it is loaded before main() and allocates no vector.
    static void stored(String kernel, boolean oneStep, String... vmArgs) throws Exception {
        String method = kernelMethod(kernel);
        String expected = expectedSum(kernel, vmArgs);
        Tester t = new Tester(kernel, vmArgs) {
            @Override
            public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
                if (runMode == RunMode.ASSEMBLY) {
                    out.shouldMatch("Wrote nmethod '" + method + "\\(.*' \\(for preload\\)");
                    out.shouldNotMatch(method + ".*COMPILE SKIPPED");
                } else if (runMode == RunMode.PRODUCTION) {
                    out.shouldContain(expected);
                }
            }
        };
        t.useWhiteBox(ClassFileInstaller.getJarPath("WhiteBox.jar"));
        t.runAOTWorkflow("AOT", oneStep ? "--one-step-training" : "--two-step-training");

        // The preload code is installed before main(). A box kept in the stored code would allocate
        // in every call. The app checks the calls that ran the same C2 code from start to end
        // without a deoptimization; the first execution of the preload code can still take a
        // predicate trap and run at C1 until the regular AOT code is loaded.
        String[] appArgs = kernel.equals("jdk") ? new String[] {"exact"} : new String[0];
        OutputAnalyzer out = t.productionRun(new String[] {"-XX:+UnlockDiagnosticVMOptions", "-XX:+PreloadBlocking"},
                                             appArgs);
        out.shouldMatch("Preloading nmethod '" + method + "\\(");
        out.shouldContain(expected);
        if (kernel.equals("jdk")) {
            Matcher m = Pattern.compile("compiled calls=([0-9]+) allocating=([0-9]+)").matcher(out.getStdout());
            if (!m.find()) {
                throw new RuntimeException("no allocation check in the output");
            }
            int calls = Integer.parseInt(m.group(1));
            int allocating = Integer.parseInt(m.group(2));
            if (allocating != 0 || calls < 100) {
                throw new RuntimeException(allocating + " of " + calls + " compiled calls of the stored kernel allocated");
            }
        }
    }

    // A cache whose Vector API state was computed for other vector sizes, another
    // VECTOR_ACCESS_OOB_CHECK or with vector support enabled is refused with -XX:AOTMode=on and
    // not used by default, which then gives the result of a run without the cache.
    static void validation() throws Exception {
        String kernel = "jdk";
        Tester t = new Tester(kernel);
        t.runAOTWorkflow("AOT", "--two-step-training");

        List<String[]> rejected = new ArrayList<>();
        int preferredBytes = IntVector.SPECIES_PREFERRED.vectorByteSize();
        if (preferredBytes >= 32) {
            rejected.add(new String[] {"-XX:MaxVectorSize=" + (preferredBytes / 2)});
        }
        rejected.add(new String[] {"-Djdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK=0"});
        rejected.add(new String[] {"-Djdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK=0x2"});
        rejected.add(new String[] {"-XX:+UnlockExperimentalVMOptions", "-XX:-EnableVectorSupport"});
        for (String[] vmArgs : rejected) {
            setCheckExitValue(t, false);
            OutputAnalyzer on = t.productionRun(vmArgs);
            on.shouldNotHaveExitValue(0);
            setCheckExitValue(t, true);
            List<String> auto = new ArrayList<>(List.of("--add-modules", VECTOR_MODULE, "-XX:AOTCache=" + t.name() + ".aot",
                                                        "-Xlog:aot"));
            auto.addAll(List.of(vmArgs));
            auto.addAll(List.of("-cp", "app.jar", "VectorKernels", kernel, PRODUCTION_ROUNDS, "nogc"));
            OutputAnalyzer out = ProcessTools.executeTestJava(auto.toArray(new String[0]));
            out.shouldHaveExitValue(0);
            out.shouldMatch("max vector lanes|VECTOR_ACCESS_OOB_CHECK setting|EnableVectorSupport disabled");
            out.shouldContain(expectedSum(kernel, vmArgs));
        }

        // An equivalent value of the property is accepted.
        OutputAnalyzer out = t.productionRun(new String[] {"-Djdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK=2"});
        out.shouldMatch("Read nmethod '" + kernelMethod(kernel) + "\\(");
    }

    static void setCheckExitValue(CDSAppTester t, boolean check) {
        t.setCheckExitValue(check);
    }

    // Training initializes every class of jdk.incubator.vector; creating the cache checks the
    // annotations of all of them and of their super types.
    static void allClasses() throws Exception {
        Tester t = new Tester("allclasses");
        t.runAOTWorkflow("AOT", "--two-step-training");
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
    static class Jdk {
        static int dot(int[] a, int[] b) {
            var acc = IntVector.zero(IntVector.SPECIES_PREFERRED);
            var i = 0;
            for (; i <= a.length - IntVector.SPECIES_PREFERRED.length(); i += IntVector.SPECIES_PREFERRED.length()) {
                acc = IntVector.fromArray(IntVector.SPECIES_PREFERRED, a, i)
                        .mul(IntVector.fromArray(IntVector.SPECIES_PREFERRED, b, i)).add(acc);
            }
            var s = acc.reduceLanes(VectorOperators.ADD);
            for (; i < a.length; i++) {
                s += a[i] * b[i];
            }
            return s;
        }
    }

    // Masks, conversions, shuffles, compress, iota and bit operations.
    static class Ops {
        static int dot(int[] a, int[] b) {
            var sp = IntVector.SPECIES_PREFERRED;
            var lsp = LongVector.SPECIES_PREFERRED;
            var bsp = ByteVector.SPECIES_PREFERRED;
            var acc = IntVector.zero(sp);
            var iota = VectorShuffle.iota(sp, 0, 1, true);
            var rev = VectorShuffle.fromOp(sp, j -> sp.length() - 1 - j);
            var i = 0;
            for (; i <= a.length - sp.length(); i += sp.length()) {
                var va = IntVector.fromArray(sp, a, i);
                var vb = IntVector.fromArray(sp, b, i);
                VectorMask<Integer> m = va.compare(VectorOperators.GT, vb);
                var v = va.blend(vb, m)
                          .add(va.lanewise(VectorOperators.LEADING_ZEROS_COUNT))
                          .add(vb.lanewise(VectorOperators.REVERSE).lanewise(VectorOperators.AND, 0xF))
                          .add(va.compress(m))
                          .add(vb.rearrange(rev))
                          .add(va.rearrange(iota));
                long widened = ((LongVector) va.convertShape(VectorOperators.I2L, lsp, 0)).reduceLanes(VectorOperators.ADD);
                byte narrowed = ((ByteVector) vb.convertShape(VectorOperators.I2B, bsp, 0)).reduceLanes(VectorOperators.XOR);
                acc = acc.add(v).add((int) widened + narrowed + m.trueCount());
            }
            var s = acc.reduceLanes(VectorOperators.ADD);
            for (; i < a.length; i++) {
                s += a[i] * b[i];
            }
            return s;
        }
    }

    // Initializes every class of jdk.incubator.vector.
    static int initializeAll() {
        int n = 0;
        FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
        try (Stream<Path> files = Files.walk(jrt.getPath("/modules/jdk.incubator.vector"))) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String name = p.toString();
                if (!name.endsWith(".class") || name.endsWith("module-info.class")) {
                    continue;
                }
                name = name.substring("/modules/jdk.incubator.vector/".length(), name.length() - ".class".length());
                try {
                    Class.forName(name.replace('/', '.'), true, null);
                    n++;
                } catch (Throwable t) {
                    System.out.println("cannot initialize " + name + ": " + t);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return n;
    }

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
            case "jdk", "allclasses" -> Jdk.dot(a, b);
            case "ops" -> Ops.dot(a, b);
            case "holder" -> Holder.dot(a, b);
            default -> throw new IllegalArgumentException(kernel);
        };
    }

    // Calls Jdk.dot() and counts the calls that ran the same compiled code from start to end
    // without a deoptimization, and those of them that allocated.
    static long exactAllocationCheck(int[] a, int[] b, int rounds, boolean gc) throws Exception {
        var wb = jdk.test.whitebox.WhiteBox.getWhiteBox();
        var tm = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        var m = Jdk.class.getDeclaredMethod("dot", int[].class, int[].class);
        long s = 0;
        int calls = 0;
        int allocating = 0;
        for (var r = 1; r < rounds; r++) {
            if (gc && r % 100 == 0) {
                System.gc();
            }
            Object[] before = wb.getNMethod(m, false);
            int deopts = wb.getDeoptCount();
            long a0 = tm.getCurrentThreadAllocatedBytes();
            s += Jdk.dot(a, b);
            long a1 = tm.getCurrentThreadAllocatedBytes();
            Object[] after = wb.getNMethod(m, false);
            // [1] is the compilation level, [4] the entry point.
            if (before != null && after != null && before[4].equals(after[4]) &&
                (Integer) before[1] == 4 && wb.getDeoptCount() == deopts) {
                calls++;
                if (a1 != a0) {
                    allocating++;
                }
            }
        }
        System.out.println("compiled calls=" + calls + " allocating=" + allocating);
        return s;
    }

    public static void main(String[] args) throws Exception {
        var a = new int[1024];
        var b = new int[1024];
        for (var i = 0; i < a.length; i++) {
            a[i] = i % 7;
            b[i] = i % 5;
        }
        String kernel = args[0];
        int rounds = Integer.parseInt(args[1]);
        boolean gc = args[2].equals("gc");
        if (kernel.equals("allclasses")) {
            System.out.println("initialized " + initializeAll() + " classes");
        }
        long s = dot(kernel, a, b);
        if (args.length > 3 && args[3].equals("exact")) {
            s += exactAllocationCheck(a, b, rounds, gc);
        } else {
            for (var r = 1; r < rounds; r++) {
                if (gc && r % 100 == 0) {
                    System.gc();
                }
                s += dot(kernel, a, b);
            }
        }
        System.out.println("sum=" + s + " ");
    }
}

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
 * @summary AOT code that has not been used is not removed as cold code, unless the code cache
 *          is nearly full.
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox AOTCodeUnusedNotCold
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTCodeUnusedNotColdApp
 * @run driver AOTCodeUnusedNotCold
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCodeUnusedNotCold {
    public static void main(String... args) throws Exception {
        Tester t = new Tester();
        t.useWhiteBox(ClassFileInstaller.getJarPath("WhiteBox.jar"));
        t.runAOTWorkflow("AOT", "--two-step-training");

        // Unused AOT code is kept while code is allocated between GCs that would make other
        // unused code cold.
        OutputAnalyzer out = t.productionRun();
        out.shouldContain("compiled before=true after=true");

        // With free space below StartAggressiveSweepingAt (which 100 makes always true), it is
        // removed like other cold code, possibly already by the GCs of the startup.
        out = t.productionRun(new String[] {"-XX:StartAggressiveSweepingAt=100"});
        out.shouldContain(" after=false");
    }

    static class Tester extends CDSAppTester {
        Tester() {
            super("AOTCodeUnusedNotCold");
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (runMode == RunMode.PRODUCTION) {
                return new String[] {"-XX:+UnlockDiagnosticVMOptions", "-XX:+PreloadBlocking",
                                     "-XX:NmethodSweepActivity=2000", "-Xlog:codecache=info",
                                     "-Xlog:aot+codecache+nmethod=info"};
            }
            return new String[0];
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {"AOTCodeUnusedNotColdApp", runMode == RunMode.TRAINING ? "train" : "check"};
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.PRODUCTION) {
                out.shouldMatch("Preloading nmethod 'AOTCodeUnusedNotColdApp::kernel\\(");
                out.shouldContain("kernel=");
            }
        }
    }
}

class AOTCodeUnusedNotColdApp {
    static int kernel(int x) {
        int s = 0;
        for (int i = 0; i < 64; i++) {
            s += (x ^ i) * 31;
        }
        return s;
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("train")) {
            int s = 0;
            for (int i = 0; i < 200_000; i++) {
                s += kernel(i);
            }
            System.out.println("kernel=" + s);
            return;
        }
        var wb = jdk.test.whitebox.WhiteBox.getWhiteBox();
        var m = AOTCodeUnusedNotColdApp.class.getDeclaredMethod("kernel", int.class);
        boolean before = wb.isMethodCompiled(m);
        // CodeBlobType::MethodNonProfiled, or All without code cache segmentation.
        int blobType = wb.getBooleanVMFlag("SegmentedCodeCache") ? 0 : 3;
        // The first GCs do not age code; after a few more the unused JIT code would be cold.
        for (int i = 0; i < 10; i++) {
            wb.allocateCodeBlob(64 * 1024, blobType);
            wb.fullGC();
        }
        boolean after = wb.isMethodCompiled(m);
        System.out.println("compiled before=" + before + " after=" + after);
        System.out.println("kernel=" + kernel(1));
    }
}

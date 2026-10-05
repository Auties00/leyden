/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8319343
 * @summary Test handling of the --add-modules option.
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI AddmodsOption
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.code.Compiler;

public class AddmodsOption {

    public static void main(String[] args) throws Exception {
        final String moduleOption = "jdk.httpserver/sun.net.httpserver.simpleserver.Main";
        final String incubatorModule = "jdk.incubator.vector";
        final String jconsoleModule = "jdk.jconsole";
        final String multiModules = ",,jdk.jconsole,jdk.compiler,,";
        final String allSystem = "ALL-SYSTEM";
        final String allModulePath = "ALL-MODULE-PATH";
        final String loggingOption = "-Xlog:aot=debug,aot+module=debug,aot+heap=info,cds=debug,module=trace";
        final String versionPattern = "java.[0-9][0-9].*";
        final String subgraphCannotBeUsed = "subgraph jdk.internal.module.ArchivedBootLayer cannot be used because full module graph is disabled";
        final String warningIncubator = "WARNING: Using incubator modules: jdk.incubator.vector";
        String archiveName = TestCommon.getNewArchiveName("addmods-option");
        TestCommon.setCurrentArchiveName(archiveName);

        // dump a base archive with --add-modules jdk.jconsole -m jdk.httpserver
        OutputAnalyzer oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "--add-modules", jconsoleModule,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0);

        // same modules specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "--add-modules", jconsoleModule,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          // version of the jdk.httpserver module, e.g. java 22-ea
          .shouldMatch(versionPattern)
          .shouldMatch("aot,module.*Restored from archive: entry.0x.*name jdk.jconsole")
          .shouldMatch("aot,module.*Restored from archive: entry.0x.*name jdk.httpserver");

        // different --add-modules specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "--add-modules", incubatorModule,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("Mismatched values for property jdk.module.addmods")
          .shouldContain("runtime jdk.incubator.vector dump time jdk.jconsole")
          .shouldContain(subgraphCannotBeUsed);

        // no module specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("jdk.httpserver specified during dump time but not during runtime")
          .shouldContain(subgraphCannotBeUsed);

        // dump an archive without the --add-modules option
        archiveName = TestCommon.getNewArchiveName("no-addmods-option");
        TestCommon.setCurrentArchiveName(archiveName);
        oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0);

        // run with --add-modules option
        oa = TestCommon.execCommon(
            loggingOption,
            "--add-modules", jconsoleModule,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("jdk.jconsole specified during runtime but not during dump time")
          // version of the jdk.httpserver module, e.g. java 22-ea
          .shouldMatch(versionPattern)
          .shouldContain(subgraphCannotBeUsed);

        // dump an archive with an incubator module, -add-modules jdk.incubator.vector
        archiveName = TestCommon.getNewArchiveName("incubator-module");
        TestCommon.setCurrentArchiveName(archiveName);
        oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "--add-modules", incubatorModule,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          // the module graph is archived with an incubator module
          .shouldContain("Full module graph = enabled");

        // run with the same incubator module
        oa = TestCommon.execCommon(
            loggingOption + ",compilation=info",
            "--add-modules", incubatorModule,
            "-m", moduleOption,
            "-version");
        oa.shouldContain("full module graph: enabled")
          // module is restored from archive
          .shouldMatch("aot,module.*Restored from archive: entry.0x.*name jdk.incubator.vector")
          .shouldNotContain("define_module(): creation of module: jdk.incubator.vector")
          // the incubating warning is printed from the archived boot layer too
          .shouldContain(warningIncubator)
          .shouldHaveExitValue(0);
        if (Compiler.isC2OrJVMCIIncluded()) {
            // the restored module sets the Vector API flags as define_module() does
            oa.shouldContain("EnableVectorSupport=true");

            // an explicit setting is kept
            oa = TestCommon.execCommon(
                "-Xlog:aot=info,compilation=info",
                "-XX:+UnlockExperimentalVMOptions", "-XX:-EnableVectorSupport",
                "--add-modules", incubatorModule,
                "-m", moduleOption,
                "-version");
            oa.shouldContain("full module graph: enabled")
              .shouldContain("EnableVectorSupport=false")
              .shouldHaveExitValue(0);
        }

        // the archive is not used without the incubator module, and no warning is printed
        oa = TestCommon.execCommon(
            loggingOption,
            "-m", moduleOption,
            "-version");
        oa.shouldContain(subgraphCannotBeUsed)
          .shouldNotContain(warningIncubator)
          .shouldHaveExitValue(0);

        if (Compiler.isJVMCIEnabled()) {
            // dump an archive with JVMCI option which indirectly adds the
            // jdk.internal.vm.ci module using the --add-modules option
            archiveName = TestCommon.getNewArchiveName("jvmci-module");
            TestCommon.setCurrentArchiveName(archiveName);
            oa = TestCommon.dumpBaseArchive(
                archiveName,
                loggingOption,
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+EagerJVMCI", "-XX:+UseJVMCICompiler",
                "-version");
            oa.shouldHaveExitValue(0);

            // run with the JVMCI option
            oa = TestCommon.execCommon(
                loggingOption,
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+EagerJVMCI", "-XX:+UseJVMCICompiler",
                "-version");
            try {
                oa.shouldHaveExitValue(0)
                  .shouldMatch("aot,module.*Restored from archive: entry.0x.*name jdk.internal.vm.ci");
            } catch (RuntimeException re) {
                // JVMCI compile may not be available
                oa.shouldHaveExitValue(1)
                  .shouldContain("Cannot use JVMCI compiler: No JVMCI compiler found");
            }
        }

        // dump an archive with multiple modules in -add-modules
        archiveName = TestCommon.getNewArchiveName("muti-modules");
        TestCommon.setCurrentArchiveName(archiveName);
        oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "--add-modules", multiModules,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0);

        // run with the same multiple modules with a duplicate module in --add-modules
        oa = TestCommon.execCommon(
            loggingOption,
            "--add-modules", multiModules,
            "--add-modules", jconsoleModule,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldMatch("aot,module.*Restored from archive: entry.0x.*name jdk.compiler")
          .shouldMatch("aot,module.*Restored from archive: entry.0x.*name jdk.jconsole");

        // dump an archive with ALL-SYSTEM in -add-modules
        archiveName = TestCommon.getNewArchiveName("muti-modules");
        TestCommon.setCurrentArchiveName(archiveName);
        oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "--add-modules", allSystem,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain(warningIncubator);

        // run with the same ALL-SYSTEM in --add-modules
        oa = TestCommon.execCommon(
            loggingOption,
            "--add-modules", allSystem,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          // the jdk.incubator.vector was specified indirectly via ALL-SYSTEM
          .shouldContain(warningIncubator)
          .shouldContain("full module graph: enabled");

        // dump an archive with ALL-MODULE-PATH in -add-modules
        archiveName = TestCommon.getNewArchiveName("muti-modules");
        TestCommon.setCurrentArchiveName(archiveName);
        oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "--add-modules", allModulePath,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0);

        // run with the same ALL-MODULE-PATH in --add-modules
        oa = TestCommon.execCommon(
            loggingOption,
            "--add-modules", allModulePath,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldMatch("aot,module.*Restored from archive: entry.0x.*name jdk.httpserver");
    }
}

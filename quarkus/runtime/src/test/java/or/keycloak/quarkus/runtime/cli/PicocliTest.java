/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package or.keycloak.quarkus.runtime.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.Test;
import org.keycloak.quarkus.runtime.Environment;
import org.keycloak.quarkus.runtime.KeycloakMain;
import org.keycloak.quarkus.runtime.cli.Picocli;
import org.keycloak.quarkus.runtime.configuration.ConfigArgsConfigSource;
import org.keycloak.quarkus.runtime.configuration.test.AbstractConfigurationTest;

import io.smallrye.config.SmallRyeConfig;
import picocli.CommandLine;
import picocli.CommandLine.Help;

public class PicocliTest extends AbstractConfigurationTest {

    // TODO: could utilize CLIResult
    private class NonRunningPicocli extends Picocli {

        final StringWriter err = new StringWriter();
        final StringWriter out = new StringWriter();
        SmallRyeConfig config;
        int exitCode = Integer.MAX_VALUE;
        boolean reaug;
        private Properties buildProps;

        String getErrString() {
            return normalize(err);
        }

        // normalize line endings - TODO: could also normalize non-printable chars
        // but for now those are part of the expected output
        String normalize(StringWriter writer) {
            return System.lineSeparator().equals("\n") ? writer.toString()
                    : writer.toString().replace(System.lineSeparator(), "\n");
        }

        String getOutString() {
            return normalize(out);
        }

        @Override
        public PrintWriter getErrWriter() {
            return new PrintWriter(err, true);
        }

        @Override
        public PrintWriter getOutWriter() {
            return new PrintWriter(out, true);
        }

        @Override
        public void exit(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public void start() {
            // skip
        }

        @Override
        public void build() throws Throwable {
            reaug = true;
            this.buildProps = getNonPersistedBuildTimeOptions();
        }

    };

    NonRunningPicocli pseudoLaunch(String... args) {
        NonRunningPicocli nonRunningPicocli = new NonRunningPicocli();
        ConfigArgsConfigSource.setCliArgs(args);
        // TODO: this needs refined, otherwise profile handling will not be correct
        nonRunningPicocli.config = createConfig();
        KeycloakMain.main(args, nonRunningPicocli);
        return nonRunningPicocli;
    }

    @Test
    public void testNegativeArgument() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start-dev");
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        assertEquals("1h",
                nonRunningPicocli.config.getConfigValue("quarkus.http.ssl.certificate.reload-period").getValue());

        nonRunningPicocli = pseudoLaunch("start-dev", "--https-certificates-reload-period=-1");
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        assertNull(nonRunningPicocli.config.getConfigValue("quarkus.http.ssl.certificate.reload-period").getValue());
    }

    @Test
    public void testInvalidArgumentType() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start-dev", "--http-port=a");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(),
                containsString("Invalid value for option '--http-port': 'a' is not an int"));
    }

    @Test
    public void failWrongEnumValue() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start-dev", "--log-console-level=wrong");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString(
                "Invalid value for option '--log-console-level': wrong. Expected values are: off, fatal, error, warn, info, debug, trace, all"));
    }

    @Test
    public void failMissingOptionValue() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start-dev", "--db");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString(
                "Option '--db' (vendor) expects a single value. Expected values are: dev-file, dev-mem, mariadb, mssql, mysql, oracle, postgres"));
    }

    @Test
    public void failMultipleOptionValue() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("build", "--db", "mysql", "postgres");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString("Unknown option: 'postgres'"));
    }

    @Test
    public void failMultipleMultiOptionValue() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("build", "--features", "linkedin-oauth", "account3");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString("Unknown option: 'account3'"));
    }

    @Test
    public void failMissingMultiOptionValue() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("build", "--features");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString(
                "Option '--features' (feature) expects one or more comma separated values without whitespace. Expected values are:"));
    }

    @Test
    public void failInvalidMultiOptionValue() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("build", "--features", "xyz,account3");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(),
                containsString("xyz is an unrecognized feature, it should be one of"));
    }

    @Test
    public void failUnknownOption() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("build", "--nosuch");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString("Unknown option: '--nosuch'"));
    }

    @Test
    public void failUnknownOptionWhitespaceSeparatorNotShowingValue() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start", "--db-pasword", "mytestpw");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString(Help.defaultColorScheme(Help.Ansi.AUTO)
                .errorText("Unknown option: '--db-pasword'")
                + "\nPossible solutions: --db-url, --db-url-host, --db-url-database, --db-url-port, --db-url-properties, --db-username, --db-password, --db-schema, --db-pool-initial-size, --db-pool-min-size, --db-pool-max-size, --db-driver, --db"));
    }

    @Test
    public void failUnknownOptionEqualsSeparatorNotShowingValue() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start", "--db-pasword=mytestpw");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString(Help.defaultColorScheme(Help.Ansi.AUTO)
                .errorText("Unknown option: '--db-pasword'")
                + "\nPossible solutions: --db-url, --db-url-host, --db-url-database, --db-url-port, --db-url-properties, --db-username, --db-password, --db-schema, --db-pool-initial-size, --db-pool-min-size, --db-pool-max-size, --db-driver, --db"));
    }

    @Test
    public void failWithFirstOptionOnMultipleUnknownOptions() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start", "--db-username=foobar", "--db-pasword=mytestpw",
                "--foobar=barfoo");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString(Help.defaultColorScheme(Help.Ansi.AUTO)
                .errorText("Unknown option: '--db-pasword'")
                + "\nPossible solutions: --db-url, --db-url-host, --db-url-database, --db-url-port, --db-url-properties, --db-username, --db-password, --db-schema, --db-pool-initial-size, --db-pool-min-size, --db-pool-max-size, --db-driver, --db"));
    }

    @Test
    public void httpStoreTypeValidation() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start", "--https-key-store-file=not-there.ks", "--hostname-strict=false");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString("Unable to determine 'https-key-store-type' automatically. Adjust the file extension or specify the property"));

        nonRunningPicocli = pseudoLaunch("start", "--https-key-store-file=not-there.ks", "--hostname-strict=false", "--https-key-store-type=jdk");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString("Failed to load 'https-key-' material: NoSuchFileException not-there.ks"));

        nonRunningPicocli = pseudoLaunch("start", "--https-trust-store-file=not-there.jks", "--https-key-store-file=not-there.ks", "--hostname-strict=false", "--https-key-store-type=jdk");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString("No trust store password provided"));
    }

    @Test
    public void testShowConfigHidesSystemProperties() {
        setSystemProperty("kc.something", "password", () -> {
            NonRunningPicocli nonRunningPicocli = pseudoLaunch("show-config");
            // the command line should now show up within the output
            assertThat(nonRunningPicocli.getOutString(), not(containsString("show-config")));
            // arbitrary kc system properties should not show up either
            assertThat(nonRunningPicocli.getOutString(), not(containsString("kc.something")));
        });
    }

    @Test
    public void failSingleParamWithSpace() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start", "--db postgres");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString(
                "Option: '--db postgres' is not expected to contain whitespace, please remove any unnecessary quoting/escaping"));
    }

    @Test
    public void spiRuntimeAllowedWithStart() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start", "--http-enabled=true", "--spi-something-pass=changeme");
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getOutString(), not(containsString("kc.spi-something-pass")));
    }

    @Test
    public void spiRuntimeWarnWithBuild() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("build", "--spi-something-pass=changeme");
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getOutString(), containsString("The following run time options were found, but will be ignored during build time: kc.spi-something-pass"));
    }

    @Test
    public void failBuildDev() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("--profile=dev", "build");
        assertThat(nonRunningPicocli.getErrString(), containsString("You can not 'build' the server in development mode."));
        assertEquals(CommandLine.ExitCode.SOFTWARE, nonRunningPicocli.exitCode);
    }

    @Test
    public void failStartBuildDev() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("--profile=dev", "start");
        assertThat(nonRunningPicocli.getErrString(), containsString("You can not 'start' the server in development mode."));
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
    }

    @Test
    public void failIfOptimizedUsedForFirstStartupExport() {
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("export", "--optimized", "--dir=data");
        assertEquals(CommandLine.ExitCode.USAGE, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getErrString(), containsString("The '--optimized' flag was used for first ever server start."));
    }

    @Test
    public void testReaugFromProdToDev() {
        build("build");

        Environment.setRebuildCheck(); // will be reset by the system properties logic
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start-dev", "--hostname=name", "--http-enabled=true");
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        assertTrue(nonRunningPicocli.reaug);
        assertEquals("dev", nonRunningPicocli.buildProps.getProperty(org.keycloak.common.util.Environment.PROFILE));;
    }

    /**
     * Runs a fake build to setup the state of the persisted build properties
     */
    private void build(String... args) {
        if (Stream.of(args).anyMatch("start-dev"::equals)) {
            Environment.setRebuildCheck(); // auto-build
        }
        NonRunningPicocli nonRunningPicocli = pseudoLaunch(args);
        assertTrue(nonRunningPicocli.reaug);
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        onAfter();
        addPersistedConfigValues((Map)nonRunningPicocli.buildProps);
    }

    @Test
    public void testReaugFromProdToDevExport() {
        build("build");

        Environment.setRebuildCheck(); // will be reset by the system properties logic
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("--profile=dev", "export", "--file=file");
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        assertTrue(nonRunningPicocli.reaug);
    }

    @Test
    public void testNoReaugFromProdToExport() {
        build("build");

        Environment.setRebuildCheck(); // will be reset by the system properties logic
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("export", "--file=file");
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        assertFalse(nonRunningPicocli.reaug);
    }

    @Test
    public void testReaugFromDevToProd() {
        build("start-dev");

        Environment.setRebuildCheck(); // will be reset by the system properties logic
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start", "--hostname=name", "--http-enabled=true");
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        assertTrue(nonRunningPicocli.reaug);
    }

    @Test
    public void testNoReaugFromDevToDevExport() {
        build("start-dev");

        Environment.setRebuildCheck(); // will be reset by the system properties logic
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("--profile=dev", "export", "--file=file");
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        assertFalse(nonRunningPicocli.reaug);
    }

    @Test
    public void testReaugFromDevToProdExport() {
        build("start-dev");

        Environment.setRebuildCheck(); // will be reset by the system properties logic
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("export", "--file=file");
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        assertTrue(nonRunningPicocli.reaug);
        assertEquals("prod", nonRunningPicocli.buildProps.getProperty(org.keycloak.common.util.Environment.PROFILE));;
    }

    @Test
    public void testOptimizedReaugmentationMessage() {
        build("build");

        Environment.setRebuildCheck(); // will be reset by the system properties logic
        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start", "--features=docker", "--hostname=name", "--http-enabled=true");
        assertEquals(CommandLine.ExitCode.OK, nonRunningPicocli.exitCode);
        assertThat(nonRunningPicocli.getOutString(), containsString("features=<unset> > features=docker"));
        assertTrue(nonRunningPicocli.reaug);
    }

    @Test
    public void fastStartOptimizedSucceeds() {
        build("build");

        System.setProperty("kc.http-enabled", "true");
        System.setProperty("kc.hostname-strict", "false");

        NonRunningPicocli nonRunningPicocli = pseudoLaunch("start", "--optimized");
        assertEquals(Integer.MAX_VALUE, nonRunningPicocli.exitCode); // "running" state
    }

}

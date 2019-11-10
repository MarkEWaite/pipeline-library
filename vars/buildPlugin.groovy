#!/usr/bin/env groovy
/**
 * Simple wrapper step for building a plugin
 */
def call(Map params = [:]) {
    def buildNumber = BUILD_NUMBER as int; if (buildNumber > 1) milestone(buildNumber - 1); milestone(buildNumber) // JENKINS-43353 / JENKINS-58625

    // Faster build and reduces IO needs
    properties([
        durabilityHint('PERFORMANCE_OPTIMIZED'),
        buildDiscarder(logRotator(numToKeepStr: '5')),
    ])

    def repo = params.containsKey('repo') ? params.repo : null
    def failFast = params.containsKey('failFast') ? params.failFast : true
    def timeoutValue = params.containsKey('timeout') ? params.timeout : 60
    def useAci = params.containsKey('useAci') ? params.useAci : false
    if(timeoutValue > 180) {
      echo "Timeout value requested was $timeoutValue, lowering to 180 to avoid Jenkins project's resource abusive consumption"
      timeoutValue = 180
    }

    boolean publishingIncrementals = false
    boolean archivedArtifacts = false
    Map tasks = [failFast: failFast]
    getConfigurations(params).each { config ->
        String label = config.platform
        String jdk = config.jdk
        String jenkinsVersion = config.jenkins
        String javaLevel = config.javaLevel

        String stageIdentifier = "${label}-${jdk}${jenkinsVersion ? '-' + jenkinsVersion : ''}"
        boolean first = tasks.size() == 1
        boolean skipTests = params?.tests?.skip
        boolean addToolEnv = !useAci

        if(useAci && (label == 'linux' || label == 'windows')) {
            String aciLabel = jdk == '8' ? 'maven' : 'maven-11'
            if(label == 'windows') {
                aciLabel += "-windows"
            }
            label = aciLabel
        }

        tasks[stageIdentifier] = {
            node(label) {
                try {
                    timeout(timeoutValue) {
                        boolean isMaven
                        // Archive artifacts once with pom declared baseline
                        boolean doArchiveArtifacts = !jenkinsVersion && !archivedArtifacts
                        if (doArchiveArtifacts) {
                            archivedArtifacts = true
                        }
                        boolean incrementals // cf. JEP-305

                        stage("Checkout (${stageIdentifier})") {
                            infra.checkoutSCM(repo)
                            isMaven = fileExists('pom.xml')
                            incrementals = fileExists('.mvn/extensions.xml') &&
                                    readFile('.mvn/extensions.xml').contains('git-changelist-maven-extension')
                            skipTests = skipTestsIfNoRelevantChanges(skipTests)
                            if (incrementals) { // Incrementals needs 'git status -s' to be empty at start of job
                                if (isUnix()) {
                                    sh(script: 'git clean -xffd > /dev/null 2>&1',
                                       label:'Clean for incrementals',
                                       returnStatus: true) // Ignore failure if CLI git is not available
                                } else {
                                    bat(script: 'git clean -xffd 1> nul 2>&1',
                                        label:'Clean for incrementals',
                                        returnStatus: true) // Ignore failure if CLI git is not available
                                }
                            }
                        }

                        String changelistF
                        String m2repo

                        stage("Build (${stageIdentifier})") {
                            String command
                            if (isMaven) {
                                m2repo = "${pwd tmp: true}/m2repo"
                                List<String> mavenOptions = [
                                        '--update-snapshots',
                                        "-Dmaven.repo.local=$m2repo",
                                        '-Dmaven.test.failure.ignore',
                                        '-Dspotbugs.failOnError=false',
                                        '-Dcheckstyle.failOnViolation=false',
                                        '-Dcheckstyle.failsOnError=false',
                                ]
                                // jacoco had file locking issues on Windows, so only running on linux
                                if (isUnix()) {
                                        mavenOptions += '-Penable-jacoco'
                                }
                                if (incrementals) { // set changelist and activate produce-incrementals profile
                                    mavenOptions += '-Dset.changelist'
                                    if (doArchiveArtifacts) { // ask Maven for the value of -rc999.abc123def456
                                        changelistF = "${pwd tmp: true}/changelist"
                                        mavenOptions += "help:evaluate -Dexpression=changelist -Doutput=$changelistF"
                                    }
                                }
                                if (jenkinsVersion) {
                                    mavenOptions += "-Djenkins.version=${jenkinsVersion} -Daccess-modifier-checker.failOnError=false"
                                }
                                if (javaLevel) {
                                    mavenOptions += "-Djava.level=${javaLevel}"
                                }
                                if (skipTests) {
                                    mavenOptions += "-DskipTests"
                                }
                                mavenOptions += "clean install"
                                try {
                                    infra.runMaven(mavenOptions, jdk, null, null, addToolEnv)
                                } finally {
                                    if (!skipTests) {
                                        junit('**/target/surefire-reports/**/*.xml,**/target/failsafe-reports/**/*.xml,**/target/invoker-reports/**/*.xml')
                                        if (first) {
                                            publishCoverage calculateDiffForChangeRequests: true, adapters: [jacocoAdapter('**/target/site/jacoco/jacoco.xml')]
                                        }
                                    }
                                }
                            } else {
                                echo "WARNING: Gradle mode for buildPlugin() is deprecated, please use buildPluginWithGradle()"
                                List<String> gradleOptions = [
                                        '--no-daemon',
                                        'cleanTest',
                                        'build',
                                ]
                                if (skipTests) {
                                    gradleOptions += '--exclude-task test'
                                }
                                command = "gradlew ${gradleOptions.join(' ')}"
                                if (isUnix()) {
                                    command = "./" + command
                                }

                                try {
                                    infra.runWithJava(command, jdk, null, addToolEnv)
                                } finally {
                                    if (!skipTests) {
                                        junit('**/build/test-results/**/*.xml')
                                    }
                                }
                            }
                        }

                        stage("Archive (${stageIdentifier})") {
                            if (failFast && currentBuild.result == 'UNSTABLE') {
                                error 'There were test failures; halting early'
                            }

                            if (first) {
                                folders = env.JOB_NAME.split("/")
                                if (folders.length > 1) {
                                    discoverGitReferenceBuild(scm: folders[1])
                                }

                                echo "Recording static analysis results on '${stageIdentifier}'"

                                recordIssues enabledForFailure: true,
                                        tool: mavenConsole(),
                                        skipBlames: true,
                                        trendChartType: 'TOOLS_ONLY'
                                recordIssues enabledForFailure: true,
                                        tools: [java(), javaDoc()],
                                        filters: [excludeFile('.*Assert.java')],
                                        sourceCodeEncoding: 'UTF-8',
                                        skipBlames: true,
                                        trendChartType: 'TOOLS_ONLY'

                                // Default configuration for SpotBugs can be overwritten using a `spotbugs`, `checkstyle', etc. parameter (map).
                                // Configuration see: https://github.com/jenkinsci/warnings-ng-plugin/blob/master/doc/Documentation.md#configuration
                                Map spotbugsArguments = [tool: spotBugs(pattern: '**/target/spotbugsXml.xml,**/target/findbugsXml.xml'),
                                                         sourceCodeEncoding: 'UTF-8',
                                                         skipBlames: true,
                                                         trendChartType: 'TOOLS_ONLY',
                                                         qualityGates: [[threshold: 1, type: 'NEW', unstable: true]]]
                                if (params?.spotbugs) {
                                    spotbugsArguments.putAll(params.spotbugs as Map)
                                }
                                recordIssues spotbugsArguments

                                Map checkstyleArguments = [tool: checkStyle(pattern: '**/target/checkstyle-result.xml'),
                                                           sourceCodeEncoding: 'UTF-8',
                                                           skipBlames: true,
                                                           trendChartType: 'TOOLS_ONLY',
                                                           qualityGates: [[threshold: 1, type: 'TOTAL', unstable: true]]]
                                if (params?.checkstyle) {
                                    checkstyleArguments.putAll(params.checkstyle as Map)
                                }
                                recordIssues checkstyleArguments

                                Map pmdArguments = [tool: pmdParser(pattern: '**/target/pmd.xml'),
                                                    sourceCodeEncoding: 'UTF-8',
                                                    skipBlames: true,
                                                    trendChartType: 'NONE']
                                if (params?.pmd) {
                                    pmdArguments.putAll(params.pmd as Map)
                                }
                                recordIssues pmdArguments

                                Map cpdArguments = [tool: cpd(pattern: '**/target/cpd.xml'),
                                                    sourceCodeEncoding: 'UTF-8',
                                                    skipBlames: true,
                                                    trendChartType: 'NONE']
                                if (params?.cpd) {
                                    cpdArguments.putAll(params.cpd as Map)
                                }
                                recordIssues cpdArguments

                                recordIssues enabledForFailure: true, tool: taskScanner(
                                        includePattern:'**/*.java',
                                        excludePattern:'**/target/**',
                                        highTags:'FIXME',
                                        normalTags:'TODO'),
                                        sourceCodeEncoding: 'UTF-8',
                                        skipBlames: true,
                                        trendChartType: 'NONE'
                                if (failFast && currentBuild.result == 'UNSTABLE') {
                                    error 'Static analysis quality gates not passed; halting early'
                                }
                            }
                            else {
                                echo "Skipping static analysis results for ${stageIdentifier}"
                            }
                            if (doArchiveArtifacts) {
                                if (incrementals) {
                                    String changelist = readFile(changelistF)
                                    dir(m2repo) {
                                        fingerprint '**/*-rc*.*/*-rc*.*' // includes any incrementals consumed
                                        archiveArtifacts artifacts: "**/*$changelist/*$changelist*",
                                                excludes: '**/*.lastUpdated',
                                                allowEmptyArchive: true // in case we forgot to reincrementalify
                                    }
                                    publishingIncrementals = true
                                } else {
                                    String artifacts
                                    if (isMaven) {
                                        artifacts = '**/target/*.hpi,**/target/*.jpi,**/target/*.jar'
                                    } else {
                                        artifacts = '**/build/libs/*.hpi,**/build/libs/*.jpi'
                                    }
                                    archiveArtifacts artifacts: artifacts, fingerprint: true
                                }
                            }
                        }
                    }
                } finally {
                    if (hasDockerLabel()) {
                        if(isUnix()) {
                            sh 'docker system prune --force --all || echo "Failed to cleanup docker images"'
                        } else {
                            bat 'docker system prune --force --all || echo "Failed to cleanup docker images"'
                        }
                    }
                }
            }
        }
    }

    parallel(tasks)
    if (publishingIncrementals) {
        infra.maybePublishIncrementals()
    }
}

boolean hasDockerLabel() {
    env.NODE_LABELS?.contains("docker")
}

List<Map<String, String>> getConfigurations(Map params) {
    boolean explicit = params.containsKey("configurations")
    boolean implicit = params.containsKey('platforms') || params.containsKey('jdkVersions') || params.containsKey('jenkinsVersions')

    if (explicit && implicit) {
        error '"configurations" option can not be used with either "platforms", "jdkVersions" or "jenkinsVersions"'
    }


    def configs = params.configurations
    configs.each { c ->
        if (!c.platform) {
            error("Configuration field \"platform\" must be specified: $c")
        }
        if (!c.jdk) {
            error("Configuration field \"jdk\" must be specified: $c")
        }
    }

    if (explicit) return params.configurations

    def platforms = params.containsKey('platforms') ? params.platforms : ['linux', 'windows']
    def jdkVersions = params.containsKey('jdkVersions') ? params.jdkVersions : ['8']
    def jenkinsVersions = params.containsKey('jenkinsVersions') ? params.jenkinsVersions : [null]

    def ret = []
    for (p in platforms) {
        for (jdk in jdkVersions) {
            for (jenkins in jenkinsVersions) {
                ret << [
                        "platform": p,
                        "jdk": jdk,
                        "jenkins": jenkins,
                        "javaLevel": null   // not supported in the old format
                ]
            }
        }
    }
    return ret
}

/**
 * Get recommended configurations for testing.
 * Includes testing Java 8 and 11 on the newest LTS.
 */
static List<Map<String, String>> recommendedConfigurations() {
    def recentLTS = "2.164.1"
    def configurations = [
        // Intentionally test configurations which have detected the most problems
        // Linux - Java 8 with plugin specified minimum Jenkins version
        // Windows - Java 8 with recent LTS
        // Linux - Java 11 with recent LTS
        [ platform: "linux", jdk: "8", jenkins: null ],
        // [ platform: "windows", jdk: "8", jenkins: null ],
        // [ platform: "linux", jdk: "8", jenkins: recentLTS, javaLevel: "8" ],
        [ platform: "windows", jdk: "8", jenkins: recentLTS, javaLevel: "8" ],
        [ platform: "linux", jdk: "11", jenkins: recentLTS, javaLevel: "8" ],
        // [ platform: "windows", jdk: "11", jenkins: recentLTS, javaLevel: "8" ]
    ]
    return configurations
}

/**
 * Return true if tests should be skipped because the changeset
 * contains no changes that will reasonably affect a test.
 */
@NonCPS
boolean skipTestsIfNoRelevantChanges(skipTestsInitialValue) {
    if (skipTestsInitialValue) { // Explicit skip wins
        return true
    }
    if (currentBuild.number == 1) { // Don't skip tests on first build
        return false
    }
    buildCauses = currentBuild.getBuildCauses()
    for (int i = 0; i < buildCauses.size(); i++) {
        buildCauseClass = buildCauses[i]._class // String name of class
        if (buildCauseClass == 'hudson.model.Cause$UserIdCause') {
            return false
        }
    }
    if (currentBuild.changeSets == null || currentBuild.changeSets.size() == 0) { // Don't skip tests if no changeSet detected
        // No changeset indicates user launched build without SCM change
        // Run tests on user launched build
        return false
    }
    def changeLogSets = currentBuild.changeSets
    for (int i = 0; i < changeLogSets.size(); i++) { // for each changelog
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {   // for each commit in the changelog
            def files = new ArrayList(entries[j].affectedFiles)
            for (int k = 0; k < files.size(); k++) { // for each file in the commit
                if (files[k].path.endsWith(".java") || files[k].path.endsWith("pom.xml")) {
                    return false
                }
            }
        }
    }
    echo "Skipping tests because changeset does not contain java or pom.xml changes"
    return true
}

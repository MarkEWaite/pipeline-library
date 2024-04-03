**infra.isRunningOnJenkinsInfra()**

Return true if this Pipeline is executing on Jenkins infrastructure.
It may be `ci.jenkins.io`, a trusted CI environment, or an infrastructure CI environment.

**infra.isTrusted()**

Return true if this Pipeline is executing on a trusted CI environment.

**infra.isRelease()**

Return true if this Pipeline is executing on the Jenkins release CI environment.

**infra.isInfra()**

Return true if this Pipeline is executing on an infrastructure CI environment.

**infra.withDockerCredentials(Closure body)**

Run the specified Closure with Docker credentials.
Note that the credentials are available only on a trusted CI environment; execution will fail on other CI instances.

**infra.checkoutSCM(String repo = null)**

Check out a repository.
Either this must be used as part of a Multibranch Pipeline or a repository argument must be provided.

**infra.withArtifactCachingProxy(boolean useArtifactCachingProxy = true, Closure body)**

Execute the body passed as closure with a Maven settings file using the
Artifact Caching Proxy provider corresponding to the requested one defined
via the agent's env.ARTIFACT_CACHING_PROXY variable, or 'azure' if not defined.
This allows decreasing JFrog Artifactory bandwidth consumption, and increase reliability.
There are currently three providers, one for each cloud used in Jenkins Infrastructure:
"aws", "azure" and "do" (DigitalOcean).
The available providers can be restricted by setting a global ARTIFACT_CACHING_PROXY_AVAILABLE_PROVIDERS
variable on the Jenkins controller, with providers separated by a comma. Ex: 'aws,do' if the Azure provider is unavailable.
A 'skip-artifact-caching-proxy' label can be added to pull request in order to punctually disable it.
When calling this function with its boolean parameter to false, it does nothing.

See the method Javadoc in the repository for more information.

**infra.runMaven(List&lt;String&gt; options, String jdk = '8', List&lt;String&gt; extraEnv = null, Boolean addToolEnv = true, Boolean useArtifactCachingProxy = true)**

Run Maven with the specified options in the current workspace.
If `useArtifactCachingProxy` is `true`, Maven settings will be added using `infra.withArtifactCachingProxy()` if possible (i.e. the provider is available, reachable and not skipped).

See the method Javadoc in the repository for more information.

**infra.runMaven(List&lt;String&gt; options, Integer jdk, List&lt;String&gt; extraEnv = null, Boolean addToolEnv = true, Boolean useArtifactCachingProxy = true)**

Run Maven with the specified options in the current workspace.
If `useArtifactCachingProxy` is `true`, Maven settings will be added using `infra.withArtifactCachingProxy()` if possible (i.e. the provider is available, reachable and not skipped).

See the method Javadoc in the repository for more information.

**runWithMaven(String command, String jdk = '8', List&lt;String&gt; extraEnv = null, Boolean addToolEnv = true)**

Run the specified command with custom Java and Maven environments.
The command may be either Batch or Shell depending on the OS.

See the method Javadoc in the repository for more information.

**runWithJava(String command, String jdk = '8', List&lt;String&gt; extraEnv = null, Boolean addToolEnv = true)**

Run the specified command with the Java tool.
`PATH` and `JAVA_HOME` will be set.
The command may be either Batch or Shell depending on the OS.

See the method Javadoc in the repository for more information.

**infra.ensureInNode(nodeLabels, body)**

Run a code block in a node with the all the specified nodeLabels as labels.
If already running in that node, simply execute the code block.
Otherwise, allocate the desired node and run the code inside it.
Node labels must be specified as a `String` consisting of a comma-separated list of labels.
**Note:** This step is not able to manage complex labels and checks for them literally, so do not try to use labels like `docker,(lowmemory&amp;&amp;linux)`.
This will result in the step launching a new node, as it will be unable to find the label `(lowmemory&amp;&amp;linux)` in the list of labels for the current node.

**infra.prepareToPublishIncrementals()**

Record artifacts created by this build which could be published via Incrementals (JEP-305).
Call at most once per build, on a Linux node, after running `mvn -Dset.changelist install`.
Follow up with `infra.maybePublishIncrementals()`.

**infra.maybePublishIncrementals()**

When appropriate, publish artifacts from the current build to the Incrementals repository.
Call at the end of the build, outside any node, when `infra.prepareToPublishIncrementals()` may have been called previously.
See [INFRA-1571](https://issues.jenkins.io/browse/INFRA-1571) and [JEP-305](https://www.jenkins.io/jep/305).

**infra.publishDeprecationCheck(String deprecationSummary, String deprecationMessage)**

Log a warning to the build console and publish a deprecation check via the Checks API.

import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest
import mock.CurrentBuild
import mock.Infra
import org.junit.Before
import org.junit.Test

import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString

class BaseTest extends DeclarativePipelineTest {
  Map env = [:]

  @Override
  void setUp() throws Exception {
    super.setUp()

    binding.setVariable('env', env)
    binding.setProperty('scm', new String())
    binding.setProperty('buildPlugin', loadScript('vars/buildPlugin.groovy'))
    binding.setProperty('infra', new Infra())
    binding.setProperty('mvnSettingsFile', 'settings.xml')

    helper.registerAllowedMethod('archiveArtifacts', [Map.class], { true })
    helper.registerAllowedMethod('attachments', [])
    helper.registerAllowedMethod('checkout', [String.class], { 'OK' })
    helper.registerAllowedMethod('configFile', [Map.class], { 'OK' })
    helper.registerAllowedMethod('configFileProvider', [List.class, Closure.class], { l, body -> body() })
    helper.registerAllowedMethod('deleteDir', [], { true })
    helper.registerAllowedMethod('dir', [String.class], { s -> s })
    helper.registerAllowedMethod('disableConcurrentBuilds', [Map.class], { 'OK' })
    helper.registerAllowedMethod('durabilityHint', [String.class], { s -> s })
    helper.registerAllowedMethod('echo', [String.class], { s -> s })
    helper.registerAllowedMethod('error', [String.class], { s ->
      updateBuildStatus('FAILURE')
      throw new Exception(s)
    })
    helper.registerAllowedMethod('fingerprint', [String.class], { s -> s })
    helper.registerAllowedMethod('git', [String.class], { 'OK' })
    helper.registerAllowedMethod('hasDockerLabel', [], { true })
    helper.registerAllowedMethod('isUnix', [], { true })
    helper.registerAllowedMethod('junit', [Map.class])
    helper.registerAllowedMethod('lock', [String.class, Closure.class], { s, body -> body() })
    helper.registerAllowedMethod('node', [String.class, Closure.class], { s, body -> body() })
    helper.registerAllowedMethod('retry', [Map.class, Closure.class], { m, body ->body() })
    helper.registerAllowedMethod('agent', [], { 'agent' })
    helper.registerAllowedMethod('kubernetesAgent', [], { 'kubernetesAgent' })
    helper.registerAllowedMethod('kubernetesAgent', [Map.class], { 'kubernetesAgent' })
    helper.registerAllowedMethod('nonresumable', [], { 'nonresumable' })

    helper.registerAllowedMethod('parallel', [Map.class, Closure.class], { l, body -> body() })
    helper.registerAllowedMethod('pwd', [], { '/foo' })
    helper.registerAllowedMethod('pwd', [Map.class], { '/bar' })
    helper.registerAllowedMethod('discoverGitReferenceBuild', [Map.class], { true })
    helper.registerAllowedMethod('recordIssues', [Map.class], { true })
    helper.registerAllowedMethod('esLint', [Map.class], { 'esLint' })
    helper.registerAllowedMethod('mavenConsole', [], { 'maven' })
    helper.registerAllowedMethod('java', [], { 'java' })
    helper.registerAllowedMethod('javaDoc', [], { 'javadoc' })
    helper.registerAllowedMethod('spotBugs', [Map.class], { 'spotbugs' })
    helper.registerAllowedMethod('checkStyle', [Map.class], { 'checkstyle' })
    helper.registerAllowedMethod('pmdParser', [Map.class], { 'pmd' })
    helper.registerAllowedMethod('cpd', [Map.class], { 'cpd' })
    helper.registerAllowedMethod('taskScanner', [Map.class], { 'tasks' })
    helper.registerAllowedMethod('excludeFile', [String.class], { true })

    helper.registerAllowedMethod('recordCoverage', [Map.class], { true })
    helper.registerAllowedMethod('jacoco', [Map.class], { 'jacoco' })
    helper.registerAllowedMethod('pit', [Map.class], { 'pit' })

    helper.registerAllowedMethod('sh', [String.class], { s -> s })
    helper.registerAllowedMethod('powershell', [String.class], { s -> s })
    helper.registerAllowedMethod('pwsh', [String.class], { s -> s })
    helper.registerAllowedMethod('stage', [String.class], { s -> s })
    helper.registerAllowedMethod('timeout', [String.class], { s -> s })
    helper.registerAllowedMethod('timeout', [Integer.class, Closure.class], { list, body -> body() })
    helper.registerAllowedMethod('withCredentials', [List.class, Closure.class], { list, body -> body() })
    helper.registerAllowedMethod('withEnv', [List.class, Closure.class], { list, body -> body() })
    helper.registerAllowedMethod('publishChecks', [Map.class], { m -> m })
    helper.registerAllowedMethod('input', [Map.class], { m -> m })

    // Kubernetes Agents in scripted syntax
    helper.registerAllowedMethod('podTemplate', [Map.class, Closure.class], { m, body ->body() })
    helper.registerAllowedMethod('containerTemplate', [Map.class], { m -> m })
    helper.registerAllowedMethod('podAnnotation', [Map.class], { m -> m })
    helper.registerAllowedMethod('container', [String.class, Closure.class], { s, body ->body() })
    helper.registerAllowedMethod('merge', [], { })
    binding.setVariable('POD_LABEL', 'builder')
  }

  def assertMethodCallContainsPattern(String methodName, String pattern) {
    return helper.callStack.findAll { call ->
      call.methodName == methodName
    }.any { call ->
      callArgsToString(call).contains(pattern)
    }
  }

  def assertMethodCall(String methodName) {
    return helper.callStack.find { call ->
      call.methodName == methodName
    } != null
  }

  def assertMethodCallOccurrences(String methodName, int compare) {
    return helper.callStack.findAll { call ->
      call.methodName == methodName
    }.size() == compare
  }
}

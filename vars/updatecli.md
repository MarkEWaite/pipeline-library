Execute [updatecli](https://www.updatecli.io/updatecli) on the repository.

The following arguments are available for this function:

* String action: (Optional - Default: "diff") Updatecli action (e.g. subcommand) to execute.
* String config: (Optional - Default: "./updatecli/updatecli.d") path to the file or directory with the updatecli configuration (flag "--config").
* String values: (Optional - Default: "./updatecli/values.yaml") path to the file with the updatecli values (flag "--values").
* String updatecliAgentLabel: (Optional - Default: "jnlp-linux-arm64") agent to be used in the process.
* String cronTriggerExpression: (Optional - Default: "") Enable periodic execution by providing a cron-like expression.
* String credentialsId: (Optional - Default: "github-app-updatecli-on-jenkins-infra") specify the githubApp or usernamePassword credentials id to use to get an Access Token. The corresponding populated env vars are USERNAME_VALUE and UPDATECLI_GITHUB_TOKEN

Examples:

```
// Run the "updatecli diff" command
updatecli()
```

```
// Here is an example to use another credentials than the default one:
withCredentials([string(credentialsId: 'another-credential-id', variable: 'ANOTHER_CRED')]) {
updatecli()
}
```

Runs JMH benchmarks and archives benchmark reports on `highmem` nodes.

Supported Parameters:

<dl>
    <dt><code>artifacts</code></dt>
    <dd>
        (Optional) If <code>artifacts</code> is not null, invokes <code>archiveArtifacts</code>
        with the given string value.
    </dd>
</dl>

**Example:**

```
runBenchmarks('jmh-report.json')
```

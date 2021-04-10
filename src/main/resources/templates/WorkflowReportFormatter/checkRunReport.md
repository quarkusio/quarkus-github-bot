## Test Failures

{#for job in report.jobsWithTestFailures}
### :gear: {job.name} {#if job.testFailuresAnchor}<a href="#user-content-{job.testFailuresAnchor}" id="{job.testFailuresAnchor}">#</a>{/if}
{#for module in job.modules}
{#if module.testFailures}
#### :package: {module.name}

```diff
# Tests:    {module.testCount}
+ Success:  {module.successCount}
- Failures: {module.failureCount}
- Errors:   {module.errorCount}
! Skipped:  {module.skippedCount}
```

{#for failure : module.failures}
##### ✖ `{failure.fullClassName}`{#if failure.failureErrorLine} line `{failure.failureErrorLine}`{/if} <a id="test-failure-{failure.fullClassName.toLowerCase}-{count}"></a> - [Source on GitHub]({failure.shortenedFailureUrl}) - [🠅](#user-content-build-summary-top)

{#if failure.failureDetail || (report.sameRepository && failure.failureErrorLine)}
<details>

{#if failure.failureDetail}
```
{failure.failureDetail.trim}
```
{/if}

{#if report.sameRepository && failure.failureErrorLine}
{failure.shortenedFailureUrl}
{/if}
</details>
{/if}

{/for}
{/if}
{/for}
{#if hasNext}
---
{/if}
{/for}

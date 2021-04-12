## Test Failures

{#if !includeStackTraces}:warning: Unable to include the stracktraces as they were too long. See annotations below for the details.{/if}

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
<p>✖ <code>{failure.fullName}</code>{#if failure.failureErrorLine} line <code>{failure.failureErrorLine}</code>{/if} <a id="test-failure-{failure.fullClassName.toLowerCase}-{count}"></a> - <a href="{failure.shortenedFailureUrl}">Source on GitHub</a> - <a href="#user-content-build-summary-top">🠅</a></p>

{#if (failure.abbreviatedFailureDetail && includeStackTraces) || (report.sameRepository && failure.failureErrorLine)}
<details>

{#if failure.abbreviatedFailureDetail && includeStackTraces}
```
{failure.abbreviatedFailureDetail.trim}
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

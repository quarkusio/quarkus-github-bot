## Test Failures

{#if !includeStackTraces}:warning: Unable to include the stracktraces as they were too long. See annotations below for the details.{/if}

{#for job in report.jobsWithReportedFailures}
### :gear: {job.name} {#if job.reportedFailures}<a href="#user-content-{job.failuresAnchor}" id="{job.failuresAnchor}">#</a>{/if}

{#if job.failingModules || job.skippedModules}
```diff
{#if job.failingModules}- Failing: {#for failingModule : job.firstFailingModules}{failingModule} {/for}{/if}{#if job.moreFailingModulesCount}and {job.moreFailingModulesCount} more{/if}
{#if job.skippedModules}! Skipped: {#for skippedModule : job.firstSkippedModules}{skippedModule} {/for}{/if}{#if job.moreSkippedModulesCount}and {job.moreSkippedModulesCount} more{/if}
```
{/if}

{#for module in job.modules}
#### :package: {module.name ? module.name : "Root project"}

{#if module.testFailures}
```diff
# Tests:    {module.testCount}
+ Success:  {module.successCount}
- Failures: {module.failureCount}
- Errors:   {module.errorCount}
! Skipped:  {module.skippedCount}
```

{#for failure : module.testFailures}
<p>✖ <code>{failure.fullName}</code>{#if failure.failureErrorLine} line <code>{failure.failureErrorLine}</code>{/if} <a id="test-failure-{failure.fullClassName.toLowerCase}-{failure_count}"></a> - <a href="{failure.shortenedFailureUrl}">Source on GitHub</a> - <a href="#user-content-build-summary-top">🠅</a></p>

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
{#else if module.projectReportFailure}
<p>✖ <code>{module.projectReportFailure}</code></p>
{#else}
<p>We were unable to extract a useful error message.</p>
{/if}
{/for}
{#if job_hasNext}

---

{/if}
{/for}

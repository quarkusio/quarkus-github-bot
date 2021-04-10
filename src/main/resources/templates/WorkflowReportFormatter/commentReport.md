## Failing Jobs - Building {report.sha}

| Status | Name | Step | Test failures | Logs | Raw logs |
| :-:  | --  | --  | :-:  | :-:  | :-:  |
{#for job in report.jobs}
| {job.conclusionEmoji} | {job.name} | {#if job.failingStep}`{job.failingStep}`{/if} | {#if job.testFailuresAnchor && job.testFailures}[Test failures](#user-content-{job.testFailuresAnchor}){#else}:warning: Check →{/if} | [Logs]({job.url}) | [Raw logs]({job.rawLogsUrl})
{/for}

{#if checkRun}
Full information is available in the [Build summary check run]({checkRun.htmlUrl}).
{/if}

{#if report.errorDownloadingSurefireReports}
:warning: Errors occurred while downloading the Surefire reports. This report is incomplete.
{/if}

{#if report.testFailures}
## Test Failures

{#for job in report.jobsWithTestFailures}
### :gear: {job.name} {#if job.testFailuresAnchor}<a href="#user-content-{job.testFailuresAnchor}" id="{job.testFailuresAnchor}">#</a>{/if}
{#for module in job.modules}
{#if module.testFailures}
#### :package: {module.name}

{#for failure : module.failures}
##### ✖ `{failure.fullName}`{#if failure.failureErrorLine} line `{failure.failureErrorLine}`{/if} - {#if checkRun && failure.failureDetail}[More details]({checkRun.htmlUrl}#user-content-test-failure-{failure.fullClassName.toLowerCase}-{count}) - {/if}[Source on GitHub]({failure.shortenedFailureUrl})
{/for}
{/if}
{/for}
{#if hasNext}
---
{/if}
{/for}
{/if}

{messageIdActive}
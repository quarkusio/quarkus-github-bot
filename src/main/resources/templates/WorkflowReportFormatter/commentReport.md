## Failing Jobs - Building {report.sha}

{#if !artifactsAvailable}:warning: Artifacts of the workflow run were not available thus the report misses some details.{/if}

| Status | Name | Step | Test failures | Logs | Raw logs |
| :-:  | --  | --  | :-:  | :-:  | :-:  |
{#for job in report.jobs}
{#if job.failing || (report.jvmJobsFailing && job.jvm)}
| {job.conclusionEmoji} | {job.name} | {#if job.failingStep}`{job.failingStep}`{/if} | {#if job.testFailuresAnchor && job.testFailures}[Test failures](#user-content-{job.testFailuresAnchor}){#else if job.failing}{#if !job.skipped}:warning: Check →{/if}{/if} | {#if job.url}[Logs]({job.url}){/if} | {#if job.rawLogsUrl}[Raw logs]({job.rawLogsUrl}){/if}
{/if}
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
<p>✖ <code>{failure.fullName}</code>{#if failure.failureErrorLine} line <code>{failure.failureErrorLine}</code>{/if} - {#if checkRun && failure.failureDetail}<a href="{checkRun.htmlUrl}#user-content-test-failure-{failure.fullClassName.toLowerCase}-{count}">More details</a> - {/if}<a href="{failure.shortenedFailureUrl}">Source on GitHub</a></p>

{/for}
{/if}
{/for}
{#if hasNext}
---
{/if}
{/for}
{/if}

{messageIdActive}
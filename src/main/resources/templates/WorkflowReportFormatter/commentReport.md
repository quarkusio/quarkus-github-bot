{#if report.cancelled}
:no_entry_sign: This workflow run has been cancelled.

{/if}
{#if report.failure && !report.jobsFailing}
✖ This workflow run has failed but no jobs reported an error. Something weird happened, please check [the workflow run page]({report.workflowRunUrl}) carefully: it might be an issue with the workflow configuration itself.

{/if}
{#if report.jobsFailing}
## Failing Jobs - Building {report.sha}

{#if !artifactsAvailable && !report.cancelled}:warning: Artifacts of the workflow run were not available thus the report misses some details.{/if}

| Status | Name | Step | Failures | Logs | Raw logs |
| :-:  | --  | --  | :-:  | :-:  | :-:  |
{#for job in report.jobs}
{#if job.failing || (report.jvmJobsFailing && job.jvmLinux)}
| {job.conclusionEmoji} | {job.name} | {#if job.failingStep}`{job.failingStep}`{/if} | {#if job.reportedFailures}[Failures](#user-content-{job.failuresAnchor}){#else if job.failing}:warning: Check →{/if} | {#if job.url}[Logs]({job.url}){/if} | {#if job.rawLogsUrl}[Raw logs]({job.rawLogsUrl}){/if}
{/if}
{/for}

{#if checkRun}
Full information is available in the [Build summary check run]({checkRun.htmlUrl}).
{/if}
{/if}

{#if report.errorDownloadingBuildReports}
:warning: Errors occurred while downloading the build reports. This report is incomplete.
{/if}

{#if report.reportedFailures}
## Failures

{#for job in report.jobsWithReportedFailures}
### :gear: {job.name} {#if job.failuresAnchor}<a href="#user-content-{job.failuresAnchor}" id="{job.failuresAnchor}">#</a>{/if}

{#if job.failingModules || job.skippedModules}
```diff
{#if job.failingModules}- Failing: {#for failingModule : job.firstFailingModules}{failingModule} {/for}{/if}{#if job.moreFailingModulesCount}and {job.moreFailingModulesCount} more{/if}
{#if job.skippedModules}! Skipped: {#for skippedModule : job.firstSkippedModules}{skippedModule} {/for}{/if}{#if job.moreSkippedModulesCount}and {job.moreSkippedModulesCount} more{/if}
```
{/if}

{#for module in job.modules}
#### :package: {module.name}

{#if module.testFailures}
{#for failure : module.testFailures}
<p>✖ <code>{failure.fullName}</code>{#if failure.failureErrorLine} line <code>{failure.failureErrorLine}</code>{/if} - {#if checkRun && failure.failureDetail}<a href="{checkRun.htmlUrl}#user-content-test-failure-{failure.fullClassName.toLowerCase}-{count}">More details</a> - {/if}<a href="{failure.shortenedFailureUrl}">Source on GitHub</a></p>

{#if failure.abbreviatedFailureDetail && includeStackTraces}
<details>

```
{failure.abbreviatedFailureDetail.trim}
```

</details>
{/if}

{/for}
{#else if module.buildReportFailure}
<p>✖ <code>{module.buildReportFailure}</code></p>

{#else}
<p>We were unable to extract a useful error message.</p>

{/if}
{/for}
{#if hasNext}

---

{/if}
{/for}
{/if}

{messageIdActive}
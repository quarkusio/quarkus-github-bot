{#if report.cancelled}
:no_entry_sign: This build has been cancelled.

{/if}
{#if report.failure && !report.jobsFailing}
✖ This build has failed but no jobs reported an error. Something weird happened, please check [the workflow run page]({report.workflowRunUrl}) carefully.

{/if}
{#if report.jobsFailing}
## <a id="build-summary-top"></a>Failing Jobs - Building {report.sha} - [Back to {workflowContext.type}]({workflowContext.htmlUrl})

{#if !artifactsAvailable && !report.cancelled}:warning: Artifacts of the workflow run were not available thus the report misses some details.{/if}

| Status | Name | Step | Failures | Logs | Raw logs |
| :-:  | --  | --  | :-:  | :-:  | :-:  |
{#for job in report.jobs}
{#if workflowReportJobIncludeStrategy.include(report, job)}
| {job.conclusionEmoji} | {job.name} | {#if job.failingStep}`{job.failingStep}`{/if} | {#if job.reportedFailures}[Failures](#user-content-{job.failuresAnchor}){#else if job.failing}:warning: Check →{/if} | {#if job.url}[Logs]({job.url}){/if} | {#if job.rawLogsUrl}[Raw logs]({job.rawLogsUrl}){/if}
{/if}
{/for}
{/if}
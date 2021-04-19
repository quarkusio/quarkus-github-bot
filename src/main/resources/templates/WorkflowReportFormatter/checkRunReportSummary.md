{#if report.cancelled}
:no_entry_sign: This build has been cancelled.

{/if}
{#if report.failure && !report.jobsFailing}
✖ This build has failed but no jobs reported an error. Something weird happened, please check [the workflow run page]({report.workflowRunUrl}) carefully.

{/if}
{#if report.jobsFailing}
## <a id="build-summary-top"></a>Failing Jobs - Building {report.sha} - [Back to Pull Request]({pullRequest.htmlUrl})

| Status | Name | Step | Test failures | Logs | Raw logs |
| :-:  | --  | --  | :-:  | :-:  | :-:  |
{#for job in report.jobs}
{#if job.failing || (report.jvmJobsFailing && job.jvmLinux)}
| {job.conclusionEmoji} | {job.name} | {#if job.failingStep}`{job.failingStep}`{/if} | {#if job.testFailuresAnchor}[Test failures](#user-content-{job.testFailuresAnchor}){#else if job.failing}{#if !job.skipped}:warning: Check →{/if}{/if} | {#if job.url}[Logs]({job.url}){/if} | {#if job.rawLogsUrl}[Raw logs]({job.rawLogsUrl}){/if}
{/if}
{/for}
{/if}
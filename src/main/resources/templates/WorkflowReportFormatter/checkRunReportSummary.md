## <a id="build-summary-top"></a>Failing Jobs - Building {report.sha} - [Back to Pull Request]({pullRequest.htmlUrl})

| Status | Name | Step | Test failures | Logs | Raw logs |
| :-:  | --  | --  | :-:  | :-:  | :-:  |
{#for job in report.jobs}
| {job.conclusionEmoji} | {job.name} | {#if job.failingStep}`{job.failingStep}`{/if} | {#if job.testFailuresAnchor}[Test failures](#user-content-{job.testFailuresAnchor}){#else}:warning: Check →{/if} | [Logs]({job.url}) | [Raw logs]({job.rawLogsUrl})
{/for}
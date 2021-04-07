## Failing Jobs

| Status  | Name | Failing step | Test failures | Logs | Raw logs |
| :----:  | ------  | ------  | :----:  | :----:  | :----:  |
{#for job in report.jobs}
| {job.conclusionEmoji} | {job.name} | `{job.failingStep}` | {#if job.testFailuresAnchor}[Test failures](#user-content-{job.testFailuresAnchor}){#else}:warning: Check →{/if} | [Logs]({job.url}) | [Raw logs]({job.rawLogsUrl})
{/for}
## Test Failures

{#for job in report.jobs}
{#if job.errors}
### :gear: {job.name} {#if job.testFailuresAnchor}<a href="#user-content-{job.testFailuresAnchor}" id="{job.testFailuresAnchor}">#</a>{/if}
{#for module in job.modules}
{#if module.errors}
#### :package: {module.name}

```diff
# Tests:    {module.testCount}
+ Success:  {module.successCount}
- Failures: {module.failureCount}
- Errors:   {module.errorCount}
! Skipped:  {module.skippedCount}
```

{#for failure : module.failures}

##### :x: `{failure.fullClassName}`{#if failure.failureErrorLine} line `{failure.failureErrorLine}`{/if} - [**See on GitHub**](https://github.com/{report.repository}/blob/{report.sha}/{module.name}/{failure.classPath}{#if failure.failureErrorLine}#L{failure.failureErrorLine}{/if})

<details>

{#if failure.failureDetail}
```
{failure.failureDetail.trim}
```
{/if}

{#if report.sameRepository && failure.failureErrorLine}
https://github.com/{report.repository}/blob/{report.sha}/{module.name}/{failure.classPath}#L{failure.failureErrorLine}
{/if}
</details>

{/for}

{/if}
{/for}
{#if hasNext}
---
{/if}
{/if}
{/for}
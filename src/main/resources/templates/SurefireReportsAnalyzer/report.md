## Test Failures

{#for job in report.jobs}
{#if job.errors}
### :gear: {job.name}
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

##### :x: `{failure.fullClassName}`

<details>

{#if failure.failureDetail}
```
{failure.failureDetail.trim}
```
{/if}

{#if report.sameRepository}
https://github.com/{report.repository}/blob/{report.sha}/{module.name}/{failure.classPath}#L{failure.failureErrorLine}
{#else}
› `{failure.fullClassName} line {failure.failureErrorLine}` - [**see in GitHub**](https://github.com/{report.repository}/blob/{report.sha}/{module.name}/{failure.classPath}#L{failure.failureErrorLine})
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
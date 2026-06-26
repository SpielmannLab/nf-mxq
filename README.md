# nf-mxq plugin

## Getting a copy

First download this repo with `git clone`.

## Building

To build the plugin:

```bash
cd nf-mxq
make assemble
```

## Testing before installing

The plugin can be tested as follows. This should not require a nextflow installation.

```bash
# Wait for completion. This should work in one of the public servers
make test
# This will install the plugin at ~/.nextflow/plugins making it usable for pipelines
make install
```

## Testing by running a real workflow with Nextflow in one of the Public Severs (eg. godxxxxqueen)

1. Run a pipeline with the plugin: `nextflow run hello -plugins nf-mxq@1.0.0 -process.executor mxq`
2. Monitor your job status either using `mysql --defaults-file=/etc/mxq/mysql_ro.cnf`:

   ```SQL
   SELECT job_id, job_command, date_submit, job_status, job_memory, job_time, job_tmpdir_size, mxq_job.group_id
   FROM mxq_job INNER JOIN mxq_group ON mxq_job.group_id = mxq_group.group_ID
   WHERE group_name = 'nf_mxq_executor' AND DATEDIFF(NOW(), date_submit) <= 2 AND user_name = '<username>'
   ORDER BY date_submit DESC LIMIT 6
   ```

   or

   Using the web interface

## Supported task/processor directives that are executor-specific (i.e., the ones that are passed onto mxqsub)

- process.cpus passed onto --processors=
- process.memory passed onto --memory= (unit conversion to MB done automatically)
- task.workDir (the automatically generated one by nextflow) passed onto --workdir=
- the task specific .command.out and .command.err paths are passed onto the --stdout= and --stderr=, respectively
- process.time passed onto --runtime= (unit conversion to minutes done automatically)
- process.disk passed onto --tmpdir= (unit conversion to MB done automatically)

Here are the ones not yet supported:

- clusterOptions #TODO
- gpu #TODO
- **dynamic resources:** The mxqsub is not capable of keeping track of processes consuming more than requested memory. Therefore, error codes cannot indicate the failure due to OUT_OF_MEMORY, like the way SLURM can do. Therefore, we cannot do a dynamic errorStrategy directive where nextflow increases the memory for every retry of a task.

For example, this all of the values in this entry in nextflow.config are respected:

```groovy
process {
    executor = 'mxq'
    // scratch = '$MXQ_JOB_TMPDIR'
    scratch = true // Nextflow uses $TMPDIR (=$MXQ_JOB_TMPDIR) for scratch by default if true
    errorStrategy = 'terminate' | 'finish'
    cpus = 1
    memory = 128.MB
    time = 5.min
    disk = 100.MB // This is important. This is the space in the SCRATCH. Else the default in mxq is 10GB.
}
```

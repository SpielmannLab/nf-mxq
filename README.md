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

1. Build and install the plugin to your local Nextflow installation: `make install`
2. Run a pipeline with the plugin: `nextflow run hello -plugins nf-mxq@1.0.0 -process.executor mxq`
3. Monitor your job status either using `mysql --defaults-file=/etc/mxq/mysql_ro.cnf`:

   ```
   SELECT job_id, job_status
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


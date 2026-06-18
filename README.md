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
3. Monitor your job status either using:

   ```mysql --defaults-file=/etc/mxq/mysql_ro.cnf
   SELECT job_id, job_status
   FROM mxq_job INNER JOIN mxq_group ON mxq_job.group_id = mxq_group.group_ID
   WHERE group_name = 'nf_mxq_executor' AND DATEDIFF(NOW(), date_submit) <= 2 AND user_name = 'sreeniva'
   ORDER BY date_submit DESC LIMIT 6
   ```

   or

   Using the web interface

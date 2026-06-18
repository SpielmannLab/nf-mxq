/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package mpi.plugin

import java.nio.file.Path
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.executor.AbstractGridExecutor
import nextflow.processor.TaskConfig
import nextflow.processor.TaskRun

/**
 * Executor for Mariux64 Job Scheduling System
 *
 * See https://github.molgen.mpg.de/mariux64/mxq
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Vanessa Sochat <sochat1@llnl.gov>
 * @author Varun Sreenivasan <varun.sreenivasan@molgen.mpg.de> (Edited from Flux Executor with help from Thomas Kreitler and Donald Buczek)
 */
@Slf4j
@CompileStatic
class MxqExecutor extends AbstractGridExecutor {

    // mxqsub returns this text, which we need to parse: mxq_group_id=584721 \n mxq_group_name=default \n mxq_job_id=58132227

    static final private Pattern SUBMIT_REGEX = ~/mxq_job_id=(\d+)/
    static final private String GROUP_NAME = 'nf_mxq_executor'
    static final private String MXQ_SQL_CONF_PATH = '/etc/mxq/mysql_ro.cnf'
    static final private Integer MSQ_SQL_DAY_LIMIT = 2

    /**
     * Gets the directives to submit the specified task to the cluster for execution
     *
     * @param task A {@link TaskRun} to be submitted
     * @param result The {@link List} instance to which add the job directives
     * @return A {@link List} containing all directive tokens and values.
     */
    protected List<String> getDirectives(TaskRun task, List<String> result) {
        return result
    }

    // Mxq does not require a special token or header
    String getHeaderToken() { null }

    /**
     * The command line to submit this job
     *
     * @param task The {@link TaskRun} instance to submit for execution to the cluster
     * @param scriptFile The file containing the job launcher script
     * @return A list representing the submit command line
     */
    @Override
    List<String> getSubmitCommandLine(TaskRun task, Path scriptFile) {
        List<String> result = ['mxqsub']
        result << '--workdir=' + quote(task.workDir)
        result << '--group-name=' + GROUP_NAME
        result << '--stdout=' + quote(task.workDir.resolve(TaskRun.CMD_OUTFILE))  // .command.out
        result << '--stderr=' + quote(task.workDir.resolve(TaskRun.CMD_ERRFILE))  // .command.err
        result << '--processors=' + task.config.getCpus().toString()
        result << '--command-alias="' + getJobNameFor(task) + '"'

        // Time limit in minutes when no units provided
        if (task.config.getTime()) {
            result << '--runtime=' + task.config.getTime().format('mm') + 'm' // Time in minutes
        }

        // Set memory limits
        if (task.config.getDisk()) {
            result << '--tmpdir=' + task.config.getDisk().toMega() + 'M'
        }

        // Set memory limits
        if (task.config.getMemory()) {
            result << '--memory=' + task.config.getMemory().toMega() + 'M'
        }

        result << '/bin/bash' << scriptFile.getName()

        return result
    }

    /**
     * Parse the string returned by the {@code mxqsub} command and extract the job ID string
     *
     * @param text The string returned when submitting the job
     * @return The actual job ID string
     */
    @Override
    def parseJobId(String text) {
        // Parse the special "f" first
        for (String line : text.readLines()) {
            def m = SUBMIT_REGEX.matcher(line)
            if (m.find()) {
                return m.group(1).toString()
            }
        }

        // Fall back to just a jobid
        def id = text.trim()
        if (id.isLong()) {
            return id
        }

        throw new IllegalStateException("Invalid mxqsub submit response:\n$text\n\n")
    }

    @Override
    protected List<String> getKillCommand() { ['mxqkill', '-J'] } //-J or --job-id=

    @Override
    protected List<String> queueStatusCommand(Object queue) { // there is no concept of queue in mxqsub

        List<String> result = []

        // Now write an SQL command to get the table
        final List<String> sql_prefix = [ 'mysql',
            "--defaults-file=${MXQ_SQL_CONF_PATH}".toString(),
            '--skip-column-names',
            '--batch',
            '-e']

        // construct the SQL query
        final user = System.getProperty('user.name')
        List<String> sql_query = ['SELECT job_id, job_status',
            'FROM mxq_job INNER JOIN mxq_group ON mxq_job.group_id = mxq_group.group_ID',
            "WHERE group_name = \'${GROUP_NAME}\'".toString(),
            "AND DATEDIFF(NOW(), date_submit) <= ${MSQ_SQL_DAY_LIMIT}".toString()] // restrict to 2 days, as jobs in Mariux can only run for 24 hours anyway
        if (user) {
            sql_query.add("AND user_name = \'${user}\'".toString())
        }
        else {
            log.debug 'Cannot retrieve current user'
        }
        result = sql_prefix + [ sql_query.join(' ') ] // the query has to be wrapeed in double inverted commas

        return result
    }

    /*
     *  Maps Mxq job status to nextflow status
     *  see https://github.molgen.mpg.de/mariux64/mxq/blob/master/mxq_job.h
     */
    static private Map<String,QueueStatus> STATUS_MAP = [
            '0': QueueStatus.PENDING,      // (MXQ_JOB_STATUS_INQ)
            '100': QueueStatus.PENDING,   // (MXQ_JOB_STATUS_ASSIGNED)
            '150': QueueStatus.PENDING,   // (MXQ_JOB_STATUS_LOADED)
            '200': QueueStatus.RUNNING,   // (MXQ_JOB_STATUS_RUNNING)
            '400': QueueStatus.ERROR,    // (MXQ_JOB_STATUS_KILLED)
            '750': QueueStatus.ERROR,    // (MXQ_JOB_STATUS_FAILED)
            '990': QueueStatus.ERROR,      // (MXQ_JOB_STATUS_CANCELLED)
            '999': QueueStatus.ERROR,      // (MXQ_JOB_STATUS_UNKNOWN)
            '1000': QueueStatus.DONE,      // (MXQ_JOB_STATUS_FINISHED)
    ]

    @Override
    protected Map<String, QueueStatus> parseQueueStatus(String text) {
        final result = new LinkedHashMap<String, QueueStatus>()

        text.eachLine { String line ->
            def cols = line.split(/\s+/)
            if (cols.size() >= 2) {
                result.put(cols[0], STATUS_MAP.get(cols[1]))
            }
            else {
                log.debug "[Mxq] invalid status line: `$line`"
            }
        }

        return result
    }

}

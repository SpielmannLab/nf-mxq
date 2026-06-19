package mpi.plugin

import java.nio.file.Paths

import nextflow.executor.ExecutorConfig
import nextflow.Session
import nextflow.processor.TaskConfig
import nextflow.executor.AbstractGridExecutor
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import spock.lang.Specification

/**
 * Test for the Executor of Mariux64 Job Scheduling System
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Vanessa Sochat <sochat1@llnl.gov>
 * @author Varun Sreenivasan <varun.sreenivasan@molgen.mpg.de> (Edited from Flux Executor with help from Thomas Kreitler and Donald Buczek)
 */
class MxqExecutorTest extends Specification {

    def createExecutor0(config) {
        Spy(MxqExecutor) {
            getConfig() >> config
        }
    }

    def createExecutor() {
        createExecutor0(new ExecutorConfig([:]))
    }

    def testParseJob() {
        given:
        def exec = [:] as MxqExecutor

        expect:
        exec.parseJobId('''mxq_group_id=584027
            mxq_group_name=FACTOR_TEST
            mxq_job_id=58575566''') == '58575566'
    }

    def testParseJobID() {
        given:
        def exec = [:] as MxqExecutor

        expect:
        exec.parseJobId('''mxq_group_id=584027
            mxq_group_name=FACTOR_TEST
            mxq_job_id=58575566''') == '58575566'
    }

    def testKill() {
        given:
        def executor = [:] as MxqExecutor
        expect:
        executor.killTaskCommand(123) == ['mxqkill', '-J', '123']
    }

    def testGetCommandLine() {
        setup:
        // SLURM executor
        def executor = createExecutor()

        // mock process
        def proc = Mock(TaskProcessor)

        // task object
        def task = new TaskRun()
        task.processor = proc
        task.workDir = Paths.get('/work/path')
        task.name = 'my task'
        task.config = new TaskConfig()

        when:
        task.config = new TaskConfig()
        task.config.time = '1m'
        task.config.disk = '2 G'
        task.config.memory = '50 M'
        task.config.cpus = '1'

        then:
        executor.getSubmitCommandLine(task, Paths.get('/some/path/job.sh')) == ['mxqsub', '--workdir=/work/path', '--group-name=nf_mxq_executor', '--stdout=/work/path/.command.out', '--stderr=/work/path/.command.err', '--processors=1', '--command-alias="nf-my_task"', '--runtime=01m', '--tmpdir=2048M', '--memory=50M', '/bin/bash', 'job.sh']
    }

    def testWorkDirWithBlanks() {
        setup:
        // SLURM executor
        def executor = createExecutor()

        def proc = Mock(TaskProcessor)
        def task = new TaskRun()
        task.processor = proc
        task.workDir = Paths.get('/home/sreeniva/test work/path')
        task.name = 'my task'

        when:
        task.config = new TaskConfig()
        task.config.time = '1m'
        task.config.disk = '2 G'
        task.config.memory = '50 M'
        task.config.cpus = '1'
        then:
        executor.getSubmitCommandLine(task, Paths.get('/some/path/job.sh')) == ['mxqsub', '--workdir="/home/sreeniva/test\\ work/path"', '--group-name=nf_mxq_executor', '--stdout="/home/sreeniva/test\\ work/path/.command.out"', '--stderr="/home/sreeniva/test\\ work/path/.command.err"', '--processors=1', '--command-alias="nf-my_task"', '--runtime=01m', '--tmpdir=2048M', '--memory=50M', '/bin/bash', 'job.sh']
    }

    def testQstatCommand() {
        setup:
        def executor = [:] as MxqExecutor
        def text =
            '''
            58130027    0
            58130028    100
            58130029    150
            58130030    200
            58130031    400
            58130032    750
            58130033    990
            58130034    999
            58130035    1000
            '''.stripIndent().trim()

        when:
        def result = executor.parseQueueStatus(text)
        then:
        result.size() == 9
        result['58130027'] == AbstractGridExecutor.QueueStatus.PENDING
        result['58130028'] == AbstractGridExecutor.QueueStatus.PENDING
        result['58130029'] == AbstractGridExecutor.QueueStatus.PENDING
        result['58130030'] == AbstractGridExecutor.QueueStatus.RUNNING
        result['58130031'] == AbstractGridExecutor.QueueStatus.ERROR
        result['58130032'] == AbstractGridExecutor.QueueStatus.ERROR
        result['58130033'] == AbstractGridExecutor.QueueStatus.ERROR
        result['58130034'] == AbstractGridExecutor.QueueStatus.ERROR
        result['58130035'] == AbstractGridExecutor.QueueStatus.DONE
    }

    def testQueueStatusCommand() {
        when:
        def usr = System.getProperty('user.name')
        def executor = [:] as MxqExecutor

        then:
        usr

        executor.queueStatusCommand(null) == [ 'mysql', '--defaults-file=/etc/mxq/mysql_ro.cnf', '--skip-column-names', '--batch' , '-e', "SELECT job_id, job_status FROM mxq_job INNER JOIN mxq_group ON mxq_job.group_id = mxq_group.group_ID WHERE group_name = \'nf_mxq_executor\' AND DATEDIFF(NOW(), date_submit) <= 2 AND user_name = \'${usr}\'".toString()]
        executor.queueStatusCommand('xxx') == [ 'mysql', '--defaults-file=/etc/mxq/mysql_ro.cnf', '--skip-column-names', '--batch' , '-e', "SELECT job_id, job_status FROM mxq_job INNER JOIN mxq_group ON mxq_job.group_id = mxq_group.group_ID WHERE group_name = \'nf_mxq_executor\' AND DATEDIFF(NOW(), date_submit) <= 2 AND user_name = \'${usr}\'".toString()]
    }

}


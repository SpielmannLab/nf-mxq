// This nextflow pipeline is to check if the process.accelerator configuration is being passed onto mxqsub as --gpu
process checkGpu {

    output:
    stdout

    script:
    """
    nvidia-smi
    """
}

workflow {
    checkGpu()
}

Introduction
============

This project contains the code behind the EC2 I/O benchmarks first described in <http://blog.scalyr.com/2012/10/16/a-systematic-look-at-ec2-io/>. You should read the "Methodology" section near the end of that post before proceeding.

The benchmark code is divided into two main parts: a Java program that does the actual benchmarking, and a collection of scripts that are used to manage EC2 and EBS instances, kick off benchmark runs, and collect the results. The management scripts run on a central "control computer", which can be a cloud instance or a local workstation. The benchmark program is generally run on one or more cloud instances (and not on the control computer).

Note that this code was not developed with publication in mind; it was intended as a quiet one-off benchmarking project. The internal documentation is spotty, and the code bears scars from repeated rounds of hacking and tweaking.

In this document, we'll give an overview of the benchmark program and control scripts, and then provide step-by-step instructions for running a simple benchmark.

For questions, go to the discussion group: <https://groups.google.com/forum/#!forum/scalyr-cloud-benchmarks>


Java benchmark program
======================

The benchmark application has two functions: creating a data file, and benchmarking operations on a data file. Recall that our test methodology is based around a single large file, executing a stream of randomly positioned read or write operations on the file.

Once you've built the application (see the Walkthrough section), here is the command to create a data file:

    java -classpath iobench.jar Main create /tmp/file1 1M

The last two parameters specify the pathname and size of the data file. The size can be specified in megabytes (M) or gigabytes (G). The program will create the file, write the specified number of random bytes, and exit. This is considered a setup operation; we do not normally measure performance for this step.

The command for executing benchmarks is more complex. Here is an example:

    java -classpath iobench.jar Main run /tmp/file1 5 read,1,1K,1K

This runs a test against the existing data file /tmp/file1. The test runs for 5 seconds. It performs read operations, using a single thread, reading 1K blocks of data aligned at 1K boundaries.

The general form of a run command is as follows:

    java -classpath iobench.jar Main run /tmp/file1 RUNTIME THREADSPEC1 THREADSPEC2 ...

RUNTIME is the execution time, in seconds, for each test.

There can be any number of threadspecs. Each specifies a separate test. It consists of four comma-separated values: an I/O operation, a thread count, a size, and an alignment. The operation can be "read", "write", "writeFlush", or "writeNN" (where NN is a decimal number). Read and write are self-explanatory; writes are not explicitly flushed. writeFlush is similar to write, except that the file is opened in auto-flush mode, instructing the operating system to synchronously flush each write to disk. writeNN is the same as write, except that after each write, we perform a flush of the file with probability 1/NN.

Each thread executes these operations in parallel. So for instance if threadCount is 4, then 4 threads will be executing the following pseudocode:

    while (benchmark time limit not exceeded) {
      read_or_write(random position, specified length);
      if (mode == "writeNN" && random(NN) == 0)
        flush();
    }

Threadspecs can be combined using periods, e.g. "read,3,1K,1K.write,1,4K,4K". This calls for three threads to perform 1K reads, and simultaneously one thread to perform 4K writes.

***reference blog post describing output format

Additional options:

* -threadCounts=2,4,6,8

    Causes each benchmark to be executed four times, with threadcount set to 2, 4, 6, and 8 respectively. Overrides the threadcount specified in the threadspec. A ".." can be used to indicate ranges, e.g. "-threadCounts=1..4,8,16" would run each benchmark with thread counts of 1, 2, 3, 4, 8, and 16.

* -shuffle

    Causes the benchmarks to be executed in random order. For instance:

    java -classpath iobench.jar Main run /tmp/file1 30 read,1,1K,1K write,1,1K,1K -threadCounts=1..4 -shuffle > /tmp/iobench.out

    This will perform a total of 8 benchmark runs, each executing for 30 seconds, yielding a total runtime of (approximately) four minutes. There will be read benchmarks using 1, 2, 3, or 4 threads, and write benchmarks using 1, 2, 3, or 4 threads. The eight runs are performed in a random order.

* -h=NNN

    This causes the program to emit summary statistics on NNN evenly-spaced occasions during the run. Each time, it prints mean, median, and various percentiles, for each type of I/O operation. If this parameter is NOT specified, then the program will print a line of output for each individual operation (a lot of output!).

* -raw

    This causes the program to print the result of each individual operation, even if -h is also specified.

* -json

    If this option is specified, then when the program is finished, it prints a JSON object containing a complete description of the run -- its parameters and summarized results.

When individual operations are being logged, the following format is used:

    operation name, file position, operation length, microsecond duration, microsecond end time, error if any


Control scripts
===============

The control scripts are used to create EC2 instances (with associated EBS volumes), run benchmarks, collect results, and tear down instances. Instances can be left running between benchmark runs; the script records the EC2 instance IDs in a text file, to track them between runs.

The entry point for the control scripts is setup/remote-run.scm. Here is a sample command line:

    ./remote-run.scm trial 2 -i 1 t "/iobenchdata/file1 1M" "/iobenchdata/file1 10 -h=5 -json read,1,1K,1K"

This example launches 2 new spot instances, runs a read benchmark, and then terminates the instances. Run remote-run.scm with the -h option to see a complete list of arguments. Here are the most common scenarios:

    ./remote-run.scm DIRNAME INSTANCECOUNT -i INSTANCETYPE t "CREATE_ARGS" "RUN_ARGS"

This is the form used in the example just above. It launches INSTANCECOUNT instances, runs iobench.jar in "create" mode with the given arguments on each instance, and then runs iobench.jar again in "run" mode on each instance. Output files are created under a directory named DIRNAME. See the "remote test" section of the Walkthrough (below) for details regarding output file location.

INSTANCETYPE specifies which EC2 instance type and disk storage is used; see -h output for details. DIRNAME specifies the name of a directory on the control computer where intermediate state and final output is written.

    ./remote-run.scm DIRNAME INSTANCECOUNT -i INSTANCETYPE f "CREATE_ARGS" "RUN_ARGS"

Identical to the previous command, but "f" is passed for the teardown parameter. This leaves the benchmark instances running, so that you can perform additional tests. WARNING: you can run up a big AWS bill if you leave the instances running for a long time!

    ./remote-run.scm DIRNAME INSTANCECOUNT -i INSTANCETYPE f "/dev/null 0" "RUN_ARGS"

Runs a new set of benchmarks on existing instances, leaving them still running afterwards. We pass "/dev/null 0" for CREATE_ARGS, so that no work is done during the file creation phase of the new benchmark. (The existing data file can be re-used -- be sure to specify the same filename in your RUN_ARGS as you did previously.)

    ./remote-run.scm DIRNAME INSTANCECOUNT -i INSTANCETYPE t "/dev/null 0" "RUN_ARGS"

Passes "t" for the teardown parameter, indicating that the instances should be torn down at the end of the test. To tear down the instances quickly, pass a short time period (e.g. 1 second) in RUN_ARGS.

When running multiple tests on the same benchmark instances, make sure to keep the INSTANCECOUNT and INSTANCETYPE parameters consistent. It's also a good idea to clear the output directory first, by deleting *.out and *.err from all subdirectories of the specified DIRNAME.

remote-run.scm creates, in the output directory, a set of numbered subdirectories, one per benchmark instance. Each subdirectory contains a number of files containing stdout and/or stderr from various phases of the benchmark setup and execution. Use the collect.sh script to gather the output into a single directory for easier analysis -- see the walkthrough for details.

collect.sh also creates a file json.NN for each instance, containing the JSON records summarizing the data gathered by that instance. This is the primary output of the entire benchmark process.


Walkthrough
===========

The first thing you need to do is set up your control computer. This can be your local workstation, or a cloud instance, but we've only tried it from Linux. The setup instructions here assume a 32-bit Linux server running Amazon's standard Linux AMI. (For a 64-bit server, getting Scheme Shell to compile can be more difficult. There's a build for Debian Linux at <http://www.wispym.com/download/scsh-linux-64.tar.gz> that you can try.)

1. Create an EC2 instance to serve as the control computer:
   * region: us-east-1 (or else you'll need to adjust the AMI ID in the next step)
   * ami: Amazon Linux AMI 2012.09 (ami-1a249873)
   * 32-bit
   * keypair named "iobench"
   * security settings: specify a security group that allows incoming ssh.

2.  Launch the instance and ssh in with something like:

    `ssh -i iobench.pem ec2-user@<public-ip>`

3.  Install prerequisites (JDK6 and Scheme shell)

        sudo su  
        yum update  
        yum install java-1.6.0-openjdk-devel.i686 gcc make screen  
        wget http://ftp.scsh.net/pub/scsh/0.6/scsh-0.6.7.tar.gz  
        tar -xzf scsh-0.6.7.tar.gz  
        cd scsh-0.6.7  
        ./configure && make && make install  
        echo 'PATH=/usr/local/bin:$PATH' >>/home/ec2-user/.bash_profile  
        exit  
        . /home/ec2-user/.bash_profile  

4.  Set up Amazon's EC2 command-line tools:

    <http://docs.amazonwebservices.com/AWSEC2/latest/UserGuide/SettingUp_CommandLine.html>

    The ec2-api-tools are preinstalled on this instance. You only need to follow the sections _Tell the Tools Who You Are_ and _Set the Region_ .  Let's use the region us-east-1 .

5.  Download and compile the code:

        wget https://github.com/scalyr/iobench/zipball/master # download latest version
        unzip master # unpack archive
        mv scalyr-iobench-* iobench # rename unpacked archive to "iobench"
        cd iobench/setup
        ./build.sh

Local test
----------

Now you're ready to run benchmarks. We'll start with a simple test running directly on the control computer.

1. Create a 1 MB data file with random data.

    `java -classpath iobench.jar Main create /tmp/file1 1M`

2. Run a 5 second read test, with 1 thread, reading 1KB blocks aligned at 1KB boundaries.

    `java -classpath iobench.jar Main run /tmp/file1 5 read,1,1K,1K >/tmp/run1.out`

3. Clean up the data and output files:

    `rm /tmp/{file1,run1.out}`

Remote test
-----------

Now we'll use remote-run.scm to benchmark an array of EC2 instances.

1. If you want to benchmark a region other than us-east-1, edit the "instance-type-costs" table in remote-run.scm to reflect the prices of on-demand instances in your region. (This table is used to determine our bid price for the spot instances used to run the benchmark -- we set the bid price to 1.2 times the on-demand instance price, ensuring that we will have winning bids under most normal circumstances.)

2.  Edit config.scm as follows:

    *region*. Specifies where instances will be launched.

    *keyname*. The ssh key used to launch the instance ("iobench" in our example).

    *keyfile*. The path to the (private) key file (on the control computer) that corresponds to the ssh key named above.

    *securitygroup*. The name of the EC2 security group to use for your instances. Must allow incoming SSH connections. In our example this was "iobench".

    *ip-type*. Specifies which IP address the control computer should use to talk to the benchmark instances. Specify "private" if the control computer is running in the same EC2 region as the instances, otherwise "public".

3.  Invoke the main script. This example launches 2 new spot instances, runs a read benchmark, and then terminates the instances:

    `./remote-run.scm trial 2 -i 1 t "/iobenchdata/file1 1M" "/iobenchdata/file1 10 -h=5 -json read,1,1K,1K"`

    This will create directories trial/1 and trial/2, representing the two benchmarked instances. Each directory contains the following files:
    
    create.out -- stdout from the iobench.jar invocation to create the data file

    run.out -- stdout from the iobench.jar invocation to run the benchmark

    remote-run.{out,err} -- log output relating to the setup and launch of the benchmark instance

    detached.{out,err} -- any other miscellaneous console output from the benchmark instance (includes some I/O stats)

    instances -- records the IDs of any benchmark instances that have not been torn down

4.  Gather the output from all benchmark instances into a single directory:

    `./collect.sh trial 2 trialOut`

    This copies all non-empty output files into a single directory ("trialOut", in this example), for further analysis. It also extracts the JSON output records (from the various run.out files) into files json.1, json.2, etc.

Notes
-----

If you specify the "f" argument so that instances are not terminated, then their IDs are saved on disk so that they can be reused for the next test.  The next such test can be told to skip the data file creation because the old one is still around.  When you've finished, just run a short test specifying "t" for instances to be terminated.


Code tour
=========

Here is a quick tour through the source code for the benchmark program that runs on each instance:

src/Histogram.java is used to aggregate I/O timings into histograms.

src/IO.java contains simple utilities for reading or writing blocks of data. This is the innermost step of the benchmark.

src/IOThread.java implements the inner loop of the benchmark, repeatedly invoking a specified I/O operation at random positions.

src/Json{Array,Object,Writer}.java are a simple JSON library, used to generate the benchmark output.

src/Log.java is used when emitting a log message for each individual I/O operation.

src/Main.java parses the command line, determines which benchmarks to run in which order, and invokes the benchmarks.

src/Operation.java implements the specific I/O operations to be benchmarked (read, write, writeFlush, and writeNN).

src/OptionParser.java contains utilities for parsing command-line arguments.

src/ResultEmitter.java collects operation timings and emits summaries (including histograms).

src/Run.java kicks off a fleet of IOThreads to perform a benchmark.

src/Size.java contains utilities for describing file sizes.

src/ThreadOptionsParser.java parses thread specifications from the command line.

src/ThreadSpec.java represents a parsed thread specification.

test/* contains JUnit tests for some of the code.

And the setup tool:

setup/batch.scm contains some private functions used by remote-run.scm to perform operations on all benchmark instances in parallel.

setup/build.sh compiles the Java code

setup/collect.sh copies the output files from separate EC2 instances from scattered locations on the control server into a single directory

setup/config.scm contains configuration options for remote-run.scm

setup/my-util-def.scm and setup/my-util.scm contain utilities used by remote-run.scm.

setup/remote-run.scm is the heart of the control scripts -- a complex Scheme Shell program that creates, sets up, and tears down EC2 instances and EBS volumes.

setup/stats.sh invokes a specified command line and reports I/O statistics -- similar to the shell command "time", but for I/O stats instead of simple time measurement. Used by remote-run.scm.

**Endit-HPSS software**

Endit-HPSS is an intermediate software that works in conjunction with the dCache ENDIT-Provider plugin and the HPSS API (see details here: [HPSS Collaboration](https://hpss-collaboration.org/product-documentation/)) which must be pre-installed and properly configured. It was used to efficiently stage/retrieve files from the IBM HPSS tape storage system.

Works fine with OpenJDK version 13 and above. 

To compile the plugin, run:
> mvn package

To compile the _libEnditHpss.so_, run:
> `cd src/main/java/c_hpss/`

> `gcc `pkg-config --cflags --libs hpss.pc` -c -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux  Application_HPSS.c`

> `gcc `pkg-config --cflags --libs hpss.pc` -c -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux  C_HPSS.c`

> `gcc `pkg-config --cflags --libs hpss.pc` -shared -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -o libEnditHpss.so  C_HPSS.o  Application_HPSS.o -lc`

To install the software, simple install it as an RPM package available in the _/endit-hpss-aggregates/rpms_ directory. It will be installed in _/usr/share/endit-hpss-aggregates/_.
> yum install _endit-hpss-aggregates-*.*-*x86_64.rpm_

To run/start the software as service, use systemctl commands as follows: 
To start the service:
> systemctl start endit-hpss-aggregates@read.service

To stop the service:
> systemctl stop endit-hpss-aggregates@read.service

To check the status of the service:
> systemctl status endit-hpss-aggregates@read.service

Configuration
The default configuration file (_/endit-hpss-aggregates/rpms/endit-hpss-aggregates.properties_) needs to be adapted to your use case.

The file contains the following parameters:

- _hpss.user=scc-atlas-0001_  //HPSS user
- _hpss.keytab.file=/var/hpss/etc/scc-atlas-0001.unix.keytab_  //HPSS user keytab file
- _read.request.dir.path=/export/atlas01/f01-124-106-e_sT_atlas/data/request;/export/atlas01/f01-124-106-e_sT_atlas/data/cancel_  //Path to the directory(s) where the dCache Endit provider plugin stores metadata files about files to be staged from tapes(s) and about canceled files
- _filelist.dir.active=/var/log/endit-hpss-aggregates/aggr_lists/active_  //Path to the directory where Endit-HPSS stores the created aggregated file list(s)
- _filelist.dir.finished=/var/log/endit-hpss-aggregates/aggr_lists/finished_  //Path to the directory where Endit-HPSS moves the already iterated aggregated file list(s)
- _make.list.time=5_  //How often Endit-HPSS creates aggregated file lists in minutes
- _dir.cleanup.days=5_  //Given directory cleanup in days
- _nr.tape.drives=14_  //Number of tape drives to use
- _nr.retries=3_  //Number of retries if file staging fails
- _retries.interval=20000_  //Retry interval in milliseconds if file staging fails
- _on.off.purging=false_  //Ability to enable (true) and disable (false) the purging of the successfully staged files from HPSS disk cache
- _on.off.p2pmove=false_  //Ability to enable (true) and disable (false) the movement of already staged files from one dCache stage pool and another
- _buffer.size=33554432_  //Buffer size in bytes that is used to copy a file from the HPSS disk cache to the IBM SS file system


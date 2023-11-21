#!/bin/bash

src=$1
dst=$2
pnfsid=$3
cosid=$4
fam=$5
checksum_type=$6
checksum_value=$7


log="/var/log/endit-hpss-aggregates/writeLog.log"
exec &>> $log

file="/etc/endit-hpss-aggregates.properties"

hpss_user=""
hpss_keytab_file=""


if [ -f "$file" ]
then
    hpss_user=`sed '/^\#/d' $file | grep 'hpss.user'  | tail -n 1 | cut -d "=" -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'`
	hpss_keytab_file=`sed '/^\#/d' $file | grep 'hpss.keytab.file'  | tail -n 1 | cut -d "=" -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'`

else
    	echo "$file not found."
fi

#echo $(date '+%Y-%m-%d %H:%M:%S') "INFO bash:- Calling C code function: " $src " " $dst " " $cosid " " $pnfsid " " $fam " " $checksum_type " " $checksum_value

#calling a C code

cd /home/scc-dcache-0001/endit-hpss-aggregates/src/main/java/c_hpss

#./C_HPSS_Write $src $dst $pnfsid $cosid $fam $checksum_type $checksum_value $hpss_user $hpss_keytab_file

./C_HPSS_Write $src $dst $pnfsid $cosid $fam $checksum_type $checksum_value
rc_cCode=$?

if [ $rc_cCode -eq 0 ]; then
	
	echo $(date '+%Y-%m-%d %H:%M:%S') "INFO bash: File writing finished successfully: " $src $dst            

	#running hpsssum
	cmd="/opt/hpss/bin/hpsssum -k -t $hpss_keytab_file -p $hpss_user -f $src -u verify -b 5242880";
	eval $cmd
    rc_hpsssum=$?         
    request_file=$(echo $dst | sed "s/out/request/")
    
    	if [ $rc_hpsssum -eq 0 ] && [ -f "$request_file" ]; then
    		       
			echo $(date '+%Y-%m-%d %H:%M:%S') "Verification succeeded: " $src
			
			#wObj.addMeta(dObj.getFileName(), "exitValue", Integer.toString(dObj.getExitValue()), dObj.getReqDir());
			# karig@ chka
			jq_exitValue='."exitValue"='$rc_hpsssum
  			jq $jq_exitValue $request_file>$request_file.tmp
  			mv -u $request_file.tmp $request_file
		
			#remove a file from "out" directory
  			if [ -f "$dst" ]; then
      			rm $dst
			    echo $(date '+%Y-%m-%d %H:%M:%S') "INFO bash:- File: " $dst " removed from 'out' directory by bash script."
 
  		    fi
  		   
		else 
			if [ -f "$request_file" ]; then
				echo $(date '+%Y-%m-%d %H:%M:%S') "Verification failed, file: " $src
			
				#needs to be implemented file removal from the hpss disk cash
			
				# karig@ chka
				jq_exitValue='."exitValue"='$rc_hpsssum
  				jq $jq_exitValue $request_file>$request_file.tmp
  				mv -u $request_file.tmp $request_file
		fi
	fi

	
fi






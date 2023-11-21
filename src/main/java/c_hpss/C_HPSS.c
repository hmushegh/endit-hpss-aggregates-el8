#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <hpss_errno.h>
#include <hpss_api.h>
#include <hpss_Getenv.h>
#include <hpss_limits.h>
#include <hpss_String.h>
#include <unistd.h>
#include <inttypes.h>
#include <time.h>
#include "C_HPSS.h"
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <pthread.h>
#include "api_internal.h"
#include "u_signed64.h"
#include <ctype.h>
#include <syslog.h>


//#define BUFFER_SIZE (32*1024*1024)

FileInfo tapeInfo;
int syslog_is_open = -1;


int login(char user[], char keytab_path[]){

    int rc;
    char hpss_user[64];
	char keytab_file[64]; 
	hpss_authn_mech_t mech;
	char msg[1000];

    mech=hpss_authn_mech_unix;	
	strcpy(hpss_user, user);
	strcpy(keytab_file, keytab_path);
	
		 	
 	if((rc = hpss_SetLoginCred(hpss_user, mech, hpss_rpc_cred_client, hpss_rpc_auth_type_keytab, keytab_file)) < 0)
 	{

	//printf("Could not authenticate, error RC: %d\n", rc);
	sprintf(msg, "Could not authenticate %s, RC: %d\n", rc);
	syslog(LOG_INFO, "%s", msg);          
        
	return -1;      
    }
       	
        return 0;  
            
}

FileInfo getFileAttr(const char * fileName){

	char msg[1000];
	
	int rc, i, j, k;
	hpss_xfileattr_t attrs;	 	 	 
	
	if (syslog_is_open == -1) {
		openlog("HPSS", 0, LOG_LOCAL2);
		syslog_is_open = 0;
	}	

	memset(&attrs, 0x0, sizeof(attrs));	
	
	rc = hpss_FileGetXAttributes(fileName, API_GET_STATS_FOR_ALL_LEVELS, 0, &attrs);
   	
	if (rc < 0)
	{
        
	//printf("Could not get attributes:%s, error RC: %d\n", fileName, rc);
        
 	sprintf(msg, "Could not get attributes:%s, error RC: %d\n", fileName, rc);
	syslog(LOG_INFO, "%s", msg);   	

	return  tapeInfo;    
	}
		    
    tapeInfo.cosId = attrs.Attrs.COSId;
    tapeInfo.fileSize = attrs.Attrs.DataLength;

 	for(i=0;i<HPSS_MAX_STORAGE_LEVELS;i++)
	{
        for(j=0;j<attrs.SCAttrib[i].NumberOfVVs;j++)
        {		        		
                //printf("RelativePosition: %d\n", j, attrs.SCAttrib[i].VVAttrib[j].RelPosition);        		
        		tapeInfo.relPosition = attrs.SCAttrib[i].VVAttrib[j].RelPosition; 
        		tapeInfo.relPositionOffset = attrs.SCAttrib[i].VVAttrib[j].RelPositionOffset;        		
        		     

                if (attrs.SCAttrib[i].VVAttrib[j].PVList != NULL)
                        {
                                for(k=0; k < attrs.SCAttrib[i].VVAttrib[j].PVList->List.List_len; k++)
                                {
                                        //printf("TapeName %d: %s \n ", k, attrs.SCAttrib[i].VVAttrib[j].PVList->List.List_val[k].Name);
                                        tapeInfo.tapeName = attrs.SCAttrib[i].VVAttrib[j].PVList->List.List_val[k].Name;
                                }
                        free(attrs.SCAttrib[i].VVAttrib[j].PVList);
                        }
        }
	}
		 
    //printf(logger, "File attributes successfully retrieved from HPSS: %s\n", fileName);  
    
    sprintf(msg, "Attributes: File: %s Tape: %s RelPosition: %d Offset: %" PRIu64 " Filesize: %" PRIu64 "", fileName , tapeInfo.tapeName, tapeInfo.relPosition, tapeInfo.relPositionOffset, tapeInfo.fileSize);
    syslog(LOG_INFO, "%s", msg);
	 	
 	return tapeInfo; 
}

int stageAFile(const char * fileName){
   
	int rc;
	char buffer[255];
	int hpss_rc = 0;
	u_signed64 bytes = cast64m(0);

  
	hpss_rc = hpss_Open(fileName, O_RDWR | O_NONBLOCK, 000, NULL, NULL, NULL);
	if(hpss_rc < 0)
	{
		
		//printf("Failed to open file:%s, error RC: %d\n", fileName, hpss_rc); 		
		return -1; 
	}
		
	rc = hpss_Stage(hpss_rc, 0, cast64m(0), 0, BFS_STAGE_ALL);
	if(rc != 0)
	{
		
		//printf("Failed to stage:%s, error RC: %d\n", fileName, rc); 	
		hpss_Close(hpss_rc);
		return -1; 
	}

	
	//printf("File staged successfully:%s , bytes %d\n", fileName, rc); 
	hpss_Close(hpss_rc);
    return 0;
}

int purgeAFile(const char * fileName){	
	
	int rc;
	char buffer[255];
	int hpss_rc;
	u_signed64 bytes = cast64m(0);
	
		
	hpss_rc = hpss_Open(fileName, O_RDWR, 000, NULL, NULL, NULL);
	if(hpss_rc < 0)
	{		
		//printf("Failed to open file:%s, error RC: %d\n", fileName, hpss_rc); 		
		return -1; 	
	}
	
	rc = hpss_Purge(hpss_rc, cast64m(0), cast64m(0), 0, BFS_PURGE_ALL, &bytes);
	if(rc != 0)
	{	
		//printf("Failed to Purge:%s, error RC: %d\n", fileName, rc); 
		hpss_Close(hpss_rc);
		return -1; 	
	}
	

	//printf("File is purged successfully:%s, bytes: %d\n", fileName, u64tostr_r(bytes, buffer)); 
	hpss_Close(hpss_rc);
	return 0;
}


int copyAFile (const char * src, const char * dst, const char * info, const int bSize, const long fsize){

	char msg[1000];
	int hpss_src;
	int posix_dst;
	time_t start, stop;
    char *buf;
    buf = (char *) valloc(bSize);
    size_t rc;
    long long fs;
    int hpss_close_rc;
    int posix_close_rc;
		
   if (syslog_is_open == -1) {
		openlog("HPSS", 0, LOG_LOCAL2);
		syslog_is_open = 0;
	}
		
	if (buf == NULL)
    {
        sprintf(msg, "Could not allocate memory: %d");
        syslog(LOG_INFO, "%s", msg);
        return -1;
    }

	
	sprintf(msg, "Start: Start Action: Read HPSS_file: %s %s", src, info);
	syslog(LOG_INFO, "%s", msg);
		
	start = time(NULL);
	
	hpss_src = hpss_Open((char *) src, O_RDONLY, 0, NULL, NULL, NULL);
	
	if (hpss_src < 0)
	{   		 
	   //sprintf(msg, "End: End Action: Read HPSS_file: %s Destination_file: %s RC (hpss_open): %d Msg (hpss_open): %s %s", src, dst, hpss_src, hpss_ErrnoString(hpss_src), info);
	   
	   sprintf(msg, "End: End Action: Read HPSS_file: %s Destination_file: %s Bytes: %d TransferRate: %dMB/s RC (hpss_open): %d ErrMsg (hpss_open): %s RC (GPFS open): %d ErrMsg (GPFS open): %s RC (hpss_read): %d ErrMsg (hpss_read): %s %s", src, dst, -9999, -9999, hpss_src, hpss_ErrnoString(hpss_src), -9999, "NONE", -9999, "NONE", info);

	   syslog(LOG_INFO, "%s", msg); 
	   return -1;	
	}
	
	//Opening a file on the local file system	
	posix_dst = open(dst, O_CREAT | O_WRONLY, 0644);	
	
	if(posix_dst == -1)
	{	
	   //sprintf(msg, "End: End Action: Read HPSS_file: %s Destination_file: %s RC (GPFS open): %d Msg (GPFS open): %s %s", src, dst, errno, strerror(errno), info);
	   sprintf(msg, "End: End Action: Read HPSS_file: %s Destination_file: %s Bytes: %d TransferRate: %dMB/s RC (hpss_open): %d ErrMsg (hpss_open): %s RC (GPFS open): %d ErrMsg (GPFS open): %s RC (hpss_read): %d ErrMsg (hpss_read): %s %s", src, dst, -9999 , -9999, hpss_src, "NONE", errno, strerror(errno), -9999, "NONE", info);
	   
	   syslog(LOG_INFO, "%s", msg);
	   
	   //hpss_Close(hpss_src);
                 
       hpss_close_rc = hpss_Close(hpss_src);
	    if (hpss_close_rc != 0)
	    {
            sprintf(msg, "RC (hpss_close): Msg (hpss_close) %d %s", hpss_close_rc, hpss_ErrnoString(hpss_close_rc));
	        syslog(LOG_INFO, "%s", msg);   
	    }
	    
	   return -1; 			
	}

    //Copying a file from HPSS    
    while ((rc = hpss_Read(hpss_src, buf, bSize)) > 0) {
       long long w_rc = write(posix_dst, buf, rc);
    
       if (w_rc > 0) {
         fs += w_rc;
    
     }else {
     
        //sprintf(msg, "End: End Action: Read HPSS_file: %s Destination_file: %s Bytes: %" PRIu64 " TransferRate: %" PRIu64 "MB/s RC (hpss_open): %d RC (GPFS open): %d RC (hpss_read): %d %s", src, dst, fsize, fsize/(1024*1024), hpss_src, posix_dst, rc, info);
 		sprintf(msg, "End: End Action: Read HPSS_file: %s Destination_file: %s Bytes: %" PRIu64 " TransferRate: %" PRIu64 "MB/s RC (hpss_open): %d ErrMsg (hpss_open): %s RC (GPFS open): %d ErrMsg (GPFS open): %s RC (hpss_read): %d ErrMsg (hpss_read): %s %s", src, dst, fsize, fsize/(1024*1024), hpss_src, "NONE", posix_dst, "NONE", rc, hpss_ErrnoString(rc), info);
 		
 		syslog(LOG_INFO, "%s", msg);	   
       
       free(buf);
       //hpss_Close(hpss_src);
       //close(posix_dst);
           
    	hpss_close_rc = hpss_Close(hpss_src);
	    
	    if (hpss_close_rc != 0)
	    {
            sprintf(msg, "hpss_Close(): %d", hpss_close_rc);
	        syslog(LOG_INFO, "%s", msg);   
	    }
	        

        posix_close_rc = close(posix_dst);
	    if (posix_close_rc != 0)
	    {
	       sprintf(msg, "RC (posix_close): Msg (posix_close) %d %s", posix_close_rc, strerror(posix_close_rc));
	       syslog(LOG_INFO, "%s", msg);	       
	    }
        
       return -1;
    }
  }
 	
 	stop = time(NULL);
 	

 if ((stop-start) > 0){ 
       //sprintf(msg, "End: End Action: Read HPSS_file: %s Destination_file: %s Bytes: %" PRIu64 " TransferRate: %" PRIu64 "MB/s RC (hpss_open): %d RC (GPFS open): %d RC (hpss_read): %d %s", src, dst, fsize, (fsize/(1024*1024))/(stop-start), hpss_src, posix_dst, rc, info);
       sprintf(msg, "End: End Action: Read HPSS_file: %s Destination_file: %s Bytes: %" PRIu64 " TransferRate: %" PRIu64 "MB/s RC (hpss_open): %d ErrMsg (hpss_open): %s RC (GPFS open): %d ErrMsg (GPFS open): %s RC (hpss_read): %d ErrMsg (hpss_read): %s %s", src, dst, fsize, (fsize/(1024*1024))/(stop-start), hpss_src, "NONE", posix_dst, "NONE", rc, "NONE", info);
 		
  }
  else {
  	//sprintf(msg, "End: End Action: Read HPSS_file: %s Destination_file: %s Bytes: %" PRIu64 " TransferRate: %" PRIu64 "MB/s RC (hpss_open): %d RC (GPFS open): %d RC (hpss_read): %d %s", src, dst, fsize, fsize/(1024*1024), hpss_src, posix_dst, rc, info);
    sprintf(msg, "End: End Action: Read HPSS_file: %s Destination_file: %s Bytes: %" PRIu64 " TransferRate: %" PRIu64 "MB/s RC (hpss_open): %d ErrMsg (hpss_open): %s RC (GPFS open): %d ErrMsg (GPFS open): %s RC (hpss_read): %d ErrMsg (hpss_read): %s %s", src, dst, fsize, fsize/(1024*1024), hpss_src, "NONE", posix_dst, "NONE", rc, "NONE", info);
 		
  }
  

    syslog(LOG_INFO, "%s", msg);
		   
    free(buf);	
	//hpss_Close(hpss_src);
    //close(posix_dst);  
    
    hpss_close_rc = hpss_Close(hpss_src);
	    if (hpss_close_rc != 0)
	    {
            sprintf(msg, "RC (hpss_close): Msg (hpss_close) %d %s", hpss_close_rc, hpss_ErrnoString(hpss_close_rc));
	        syslog(LOG_INFO, "%s", msg);   
	    }
	    
    posix_close_rc = close(posix_dst);
	    if (posix_close_rc != 0)
	    {
	       sprintf(msg, "RC (posix_close): Msg (posix_close) %d %s", posix_close_rc, strerror(posix_close_rc));
	       syslog(LOG_INFO, "%s", msg);	       
	    }

   	
	return 0;
}


int createADirectory(char *path)
{   
    char *remainder;
    char *subdirectory = (char *) malloc(sizeof(char) * strlen(path));
    char slash = '/';
    int rc = 0;
        
    remainder = strchr(path + 1, slash);
    while (remainder != NULL)
    {   
        int subLen = (strlen(path) - strlen(remainder));
        strncpy(subdirectory, path, (size_t) subLen);
        if (subLen >= 0)
            subdirectory[subLen] = '\0';
        if (subdirectory != NULL && *subdirectory != '\0')
        {   
            //printf("subdirectory:%s\n", subdirectory);
            rc = hpss_Mkdir(subdirectory, 0770);
        }
         remainder = strchr(remainder + 1, slash);
    }

    free(subdirectory);
    return rc;
}


int hash_type_from_string(char *str, hpss_hash_type_t *type)
{  
	if(!strcmp(str,"adler32"))
      *type = hpss_hash_type_adler32;
   else
      return(-1);

   return(0);
}


int get_value_of_letter (const char ch)
{
  if(ch >= 'a' && ch <= 'z')
    return (ch - 'a' + 10);
  else if(ch >= '0' && ch <= '9')
    return (ch - '0');
  return(-1);
}

char *get_hash_digest_from_string (const char *checksumV, int *len)
{
 static char digest[64];
 int i = 0;
 *len = 0;

  for(i=0; i < strlen(checksumV); i++) {
    int value = get_value_of_letter(checksumV[i]);
    if((i%2) == 0) {
        digest[*len] = (value & 0xF);
     }
    else {
      digest[*len] <<= 4;
      digest[*len] |= (value & 0xF);
      (*len)++;
     }
  }

  return digest;
}


int writeAFile (char * src, char * dst, char * pnfsid, int cosid, int fam, char* checksum_type, char* checksum_value, int bSize){

char msg[1000];
   
if (syslog_is_open == -1) {
	openlog("HPSS", 0, LOG_LOCAL2);
	syslog_is_open = 0;
}
	
int rc;
rc = createADirectory (src);

if (rc != 0 && rc != -17 && rc != HPSS_EEXIST)
{
  sprintf(msg, "Could not create a directory: %d ", rc);
  syslog(LOG_INFO, "%s", msg);
}

int hfd;
hpss_cos_hints_t hints_in, hints_out;
hpss_cos_priorities_t hints_pri;
char *buf2;
int rc2;

memset(&hints_in, 0x0, sizeof(hints_in));
memset(&hints_out, 0x0, sizeof(hints_out));
memset(&hints_pri, 0x0, sizeof(hints_pri));


hints_in.COSId = cosid;
hints_in.FamilyId = fam;

hints_pri.COSIdPriority = REQUIRED_PRIORITY;
hints_pri.FamilyIdPriority = REQUIRED_PRIORITY;


int hash_len = 0;
char *hash_val;
unsigned char *hash_buf;
hpss_hash_type_t  hash_type = hpss_hash_type_none;

hash_type_from_string(checksum_type, &hash_type);

hash_val = checksum_value;

hash_buf = get_hash_digest_from_string(hash_val, &hash_len);

hfd = hpss_Open(src, O_CREAT | O_WRONLY, 0644, &hints_in, &hints_pri, &hints_out);

if (hfd < 0)
{
sprintf(msg, "Could not open: %s, error %d", src, hfd);
syslog(LOG_INFO, "%s", msg);
hpss_Close(hfd);
return hfd;

}

buf2 = (char *) valloc(bSize);
long long fsize;

int src_rc = open(dst, O_RDONLY, 0);


while ((rc2 = read(src_rc, buf2, bSize)) > 0) {
       fsize += hpss_Write(hfd, buf2, rc2);

}

// Setting pnfsid for a file as a file attribute
int rc_set;
hpss_fileattr_t attrs_in2, attrs_out2;
	
memset((char*)&attrs_in2, 0x00, sizeof(attrs_in2));

strcpy(attrs_in2.Attrs.Comment, pnfsid);
    
rc_set = hpss_FileSetAttributes(src, orbit64m(cast64m(0), CORE_ATTR_COMMENT),  &attrs_in2, &attrs_out2);
	if (rc_set < 0)
	{
        sprintf(msg, "Setting PNFSID as comment failed: %d\n", rc_set);
   		syslog(LOG_INFO, "%s", msg);
	    return -1;
	}

// Setting hash for a file 

hpss_file_hash_digest_t  hash_digest;

memset(&hash_digest,'\0',sizeof(hash_digest));

hash_digest.Type = hash_type;
hash_digest.Flags |= HPSS_FILE_HASH_DIGEST_VALID;

memcpy(hash_digest.Buffer,hash_buf,hash_len);

int rc3 = hpss_FsetFileDigest(hfd, &hash_digest);
if(rc3 != 0) {   
   sprintf(msg, "Could not set hash: %s", hpss_ErrnoName(rc));
   syslog(LOG_INFO, "%s", msg);
   free(hash_buf);
   return -1;
}

int aa = hpss_Close(hfd);
int bb = close(src_rc);


sprintf(msg, "hpss_Close(): %d, src_close(): %d, src: %s", aa, bb, src);
syslog(LOG_INFO, "%s", msg);

return 0;
	
}


int unlinkAFile(const char * fileName){	
	
	int rc;	
   
	rc = hpss_Unlink(fileName);
	if(rc != 0)
	{			
		return -1; 	
	}	
	
	return 0;
}


void closeSysLog(){
	closelog(); 	
	syslog_is_open = -1;
}
	

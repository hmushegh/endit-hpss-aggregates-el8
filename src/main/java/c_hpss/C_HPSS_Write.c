#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <hpss_errno.h>
#include <hpss_api.h>
#include <hpss_Getenv.h>
#include <hpss_limits.h>
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


#define BUFFER_SIZE (16*1024*1024)


int syslog_is_open = -1;

char hpss_user[HPSS_MAX_PATH_NAME] = "scc-cms-0001";
char hpss_keytab_file[HPSS_MAX_PATH_NAME] = "/home/scc-dcache-0001/keytab_hpss_test/scc-cms-0001.unix.keytab";


int login(char user[], char keytab_path[]){

    int rc;
    char hpss_user[64];
	char keytab_file[64]; 
	hpss_authn_mech_t mech;

    mech=hpss_authn_mech_unix;	
	strcpy(hpss_user, user);
	strcpy(keytab_file, keytab_path);
	
		 	
 	if((rc = hpss_SetLoginCred(hpss_user, mech, hpss_rpc_cred_client, hpss_rpc_auth_type_keytab, keytab_file)) < 0)
 	{
 		//printf("Could not authenticate, error RC: %d\n", rc);          
        return -1;      
    }
 
      //printf("Logged in to HPSS, RC: %d\n", rc);   	
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


int writeAFile (char * src, char * dst, char * pnfsid, int cosid, int fam, char* checksum_type, char* checksum_value){

char msg[1000];
   
if (syslog_is_open == -1) {
	openlog("HPSS", 0, LOG_LOCAL2);
	syslog_is_open = 0;
}

int rc;
rc = createADirectory (src);

if (rc != 0 && rc != HPSS_EEXIST)
{
  sprintf(msg, "Could not create a directory: %d %s", rc, src);
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

buf2 = (char *) valloc(BUFFER_SIZE);
long long fsize;

int src_rc = open(dst, O_RDONLY, 0);


while ((rc2 = read(src_rc, buf2, BUFFER_SIZE)) > 0) {
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


int

main(int argc, char** argv, char** env3)
{

	char *src, *dst, *pnfsid, *checksum_type, *checksum_value;
	int cosid, fam;
	
	src=argv[1];
	dst=argv[2];
	pnfsid=argv[3];
	cosid=atoi(argv[4]);
	fam=atoi(argv[5]);
	checksum_type=argv[6];
	checksum_value=argv[7];
		
		
	login(hpss_user, hpss_keytab_file);
	
	char msg[1000];
   
	/*if (syslog_is_open == -1) {
		openlog("HPSS", 0, LOG_LOCAL2);
		syslog_is_open = 0;
	}
	
	sprintf(msg, "%s "  " %s "  " %s "  "%d "  " %d "  " %s "  " %s", src, dst, pnfsid, cosid, fam, checksum_type, checksum_value);
	syslog(LOG_INFO, "%s", msg);
	*/
		
	int rc = writeAFile (src, dst, pnfsid, cosid, fam, checksum_type, checksum_value);
	
	if(rc != 0)
	{			
		return -1; 	
	}	
	
	return 0;
	
}
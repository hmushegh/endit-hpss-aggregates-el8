#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <hpss_errno.h>
#include <hpss_api.h>
#include <hpss_Getenv.h>
#include <hpss_limits.h>
#include "Application_HPSS.h"
#include <jni.h>
#include <unistd.h>
#include <inttypes.h>
#include "C_HPSS.h"


char user[64] = "" ;
char keytabFile[64] = "" ;
 
JNIEXPORT jint JNICALL Java_Application_HPSS_login(JNIEnv * env, jclass this, jstring jhpssUser, jstring jhpssKeytab) {
	
	const char *usr = (*env)->GetStringUTFChars(env, jhpssUser, NULL);
	const char *keytabPath = (*env)->GetStringUTFChars(env, jhpssKeytab, NULL); 
		
	strcpy(user, usr);
	strcpy(keytabFile, keytabPath);
		
	int rc = login(user, keytabFile);
	
	(*env)->ReleaseStringUTFChars(env, jhpssUser, usr); 
	(*env)->ReleaseStringUTFChars(env, jhpssKeytab, keytabPath); 
	
	return rc;

}


JNIEXPORT jobject JNICALL Java_Application_HPSS_getFileAttr(JNIEnv * env, jclass this, jstring jfileName) {
       
	const char *fileName = (*env)->GetStringUTFChars(env, jfileName, NULL); //C			

 	FileInfo fInfo = getFileAttr(fileName);
 	 	
 	jclass fAttr = (*env)->FindClass(env, "LApplication/HPSSFileInfo;");
    jobject obj = (*env)->AllocObject(env, fAttr);
		 
    jfieldID jtapeName = (*env)->GetFieldID(env, fAttr , "tapeName", "Ljava/lang/String;");
    jstring jtapeNameToString = (*env)->NewStringUTF(env, fInfo.tapeName);
    
    jfieldID jrelPosition = (*env)->GetFieldID(env, fAttr , "relPosition", "I");
    jfieldID jcosId = (*env)->GetFieldID(env, fAttr , "cosId", "I");
    jfieldID jfileSize = (*env)->GetFieldID(env, fAttr , "fileSize", "J");
    jfieldID joffset = (*env)->GetFieldID(env, fAttr , "offset", "J");
    
	
    (*env)->SetObjectField(env, obj, jtapeName, jtapeNameToString);
    (*env)->SetIntField(env, obj, jrelPosition, fInfo.relPosition);
    (*env)->SetIntField(env, obj, jcosId, fInfo.cosId);
    (*env)->SetLongField(env, obj, jfileSize, fInfo.fileSize);
    (*env)->SetLongField(env, obj, joffset, fInfo.relPositionOffset);
   
  	
	(*env)->ReleaseStringUTFChars(env, jfileName, fileName); //C
	(*env)->DeleteLocalRef(env, jtapeNameToString);
		
	return obj;

}

JNIEXPORT jint JNICALL Java_Application_HPSS_purgeAFile(JNIEnv * env, jclass this, jstring jfileName) {
       
	const char *fileName = (*env)->GetStringUTFChars(env, jfileName, NULL); 

	int rc = purgeAFile(fileName);
	
	(*env)->ReleaseStringUTFChars(env, jfileName, fileName); 
	
	return rc;
}


JNIEXPORT jint JNICALL Java_Application_HPSS_stageAFile(JNIEnv * env, jclass this, jstring jfileName) {
       
	const char *fileName = (*env)->GetStringUTFChars(env, jfileName, NULL); 

	int rc = stageAFile(fileName);
	
	(*env)->ReleaseStringUTFChars(env, jfileName, fileName); 
	
	return rc;
}


JNIEXPORT jint JNICALL Java_Application_HPSS_copyAFile(JNIEnv * env, jclass this, jstring src, jstring dst, jstring info, jint buffSize, jlong fSize) {
       
    char hpss_src[600] = "" ;
    char posix_dst[600] = "" ;
    char attr[500] = "" ;
    

	const char *src_ = (*env)->GetStringUTFChars(env, src, NULL); 
	const char *dst_ = (*env)->GetStringUTFChars(env, dst, NULL); 	
	const char *attr_ = (*env)->GetStringUTFChars(env, info, NULL);	
	
	strcpy(hpss_src, src_);
	strcpy(posix_dst, dst_);	
	strcpy(attr, attr_);

	int rc = copyAFile(hpss_src, posix_dst, attr, buffSize, fSize);
	
	(*env)->ReleaseStringUTFChars(env, src, src_); 
	(*env)->ReleaseStringUTFChars(env, dst, dst_);	
	(*env)->ReleaseStringUTFChars(env, info, attr_);
	
	return rc;
}



JNIEXPORT jint JNICALL Java_Application_HPSS_writeAFile(JNIEnv * env, jclass this, jstring src, jstring dst, jstring pnfsid, jint cid, jint fam, jstring checksum_type, jstring checksum_value, jint buffSize) {
       
    char hpss_src[600] = "" ;
    char posix_dst[600] = "" ;
    
    char posix_pnfsid[100] = "" ;
    
    char checksum_t[100] = "" ;
    char checksum_v[100] = "" ;    
   

	const char *src_ = (*env)->GetStringUTFChars(env, src, NULL); 
	const char *dst_ = (*env)->GetStringUTFChars(env, dst, NULL); 
	
	const char *pnfsid_ = (*env)->GetStringUTFChars(env, pnfsid, NULL); 
		
	const char *c_type_ = (*env)->GetStringUTFChars(env, checksum_type, NULL); 
	const char *c_value_ = (*env)->GetStringUTFChars(env, checksum_value, NULL); 	
	
	
	strcpy(hpss_src, src_);
	strcpy(posix_dst, dst_);
	
	strcpy(posix_pnfsid, pnfsid_);
	
	strcpy(checksum_t, c_type_);
	strcpy(checksum_v, c_value_);

	int rc = writeAFile(hpss_src, posix_dst, posix_pnfsid, cid, fam, checksum_t, checksum_v, buffSize);
	
	(*env)->ReleaseStringUTFChars(env, src, src_); 
	(*env)->ReleaseStringUTFChars(env, dst, dst_);	
	
	(*env)->ReleaseStringUTFChars(env, pnfsid, pnfsid_);	
	
	(*env)->ReleaseStringUTFChars(env, checksum_type, c_type_); 
	(*env)->ReleaseStringUTFChars(env, checksum_value, c_value_);
	
	return rc;
}

JNIEXPORT jint JNICALL Java_Application_HPSS_unlinkAFile(JNIEnv * env, jclass this, jstring jfileName) {
       
	const char *fileName = (*env)->GetStringUTFChars(env, jfileName, NULL); 

	int rc = unlinkAFile(fileName);
	
	(*env)->ReleaseStringUTFChars(env, jfileName, fileName); 
	
	return rc;
}


JNIEXPORT void Java_Application_HPSS_closeSysLog(JNIEnv * env, jclass this) {
       
	closeSysLog();
}

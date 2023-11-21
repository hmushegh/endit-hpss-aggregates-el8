#ifndef C_HPSS_h
#define C_HPSS_h


// HPSS headers
#include "hpss_errno.h"
#include "hpss_limits.h"
#include "hpss_api.h"

struct fileAttr {
	char *tapeName;
	unsigned long long fileSize;
	int relPosition;
	int cosId;
	unsigned long long relPositionOffset;
};

typedef struct fileAttr FileInfo;

int login(char[], char[]);
FileInfo getFileAttr(const char *);
int stageAFile(const char *fileName);
int purgeAFile(const char *fileName);
int copyAFile(const char[], const char[], const char[], const int, const long);
int writeAFile(char[], char[], char[], int, int, char[], char[], int);
int unlinkAFile(const char[]);
void closeSysLog();

#endif

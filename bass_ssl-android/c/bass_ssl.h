#ifndef BASS_SSL_H
#define BASS_SSL_H

#include "bass.h"

#ifdef __cplusplus
extern "C" {
#endif

// BASS SSL plugin version
#define BASS_SSL_VERSION 0x02040000

// BASS SSL plugin functions
BOOL BASSDEF(BASS_SSL_Init)();
void BASSDEF(BASS_SSL_Free)();
DWORD BASSDEF(BASS_SSL_GetVersion)();

// SSL stream creation
HSTREAM BASSDEF(BASS_SSL_StreamCreateURL)(const char *url, DWORD offset, DWORD flags, DOWNLOADPROC *proc, void *user, DWORD freq);
HSTREAM BASSDEF(BASS_SSL_StreamCreateFile)(BOOL mem, const void *file, QWORD offset, QWORD length, DWORD flags);

// SSL configuration
BOOL BASSDEF(BASS_SSL_SetConfig)(DWORD option, DWORD value);
DWORD BASSDEF(BASS_SSL_GetConfig)(DWORD option);

// SSL certificate handling
BOOL BASSDEF(BASS_SSL_SetCertificate)(const char *cert, const char *key);
BOOL BASSDEF(BASS_SSL_SetCertificateFile)(const char *certfile, const char *keyfile);

// SSL verification
BOOL BASSDEF(BASS_SSL_SetVerification)(BOOL verify);
BOOL BASSDEF(BASS_SSL_SetCA)(const char *ca);
BOOL BASSDEF(BASS_SSL_SetCAFile)(const char *cafile);

#ifdef __cplusplus
}
#endif

#endif // BASS_SSL_H

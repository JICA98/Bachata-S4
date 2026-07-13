/* #define to the attribute for default visibility. */
#define DEFAULT_VISIBILITY __attribute__((visibility("default")))

/* #define to 1 to start with debug message logging enabled. */
/* #undef ENABLE_DEBUG_LOGGING */

/* #define to 1 to enable message logging. */
#define ENABLE_LOGGING 1

/* #define to 1 if you have the <asm/types.h> header file. */
#define HAVE_ASM_TYPES_H

/* #define to 1 if you have the `clock_gettime' function. */
#define HAVE_CLOCK_GETTIME 1

/* #define to 1 if the system has eventfd functionality. */
#define HAVE_EVENTFD 1

/* #define to 1 if the system has the type `nfds_t'. */
/* #undef HAVE_NFDS_T */

/* #define to 1 if you have the `pipe2' function. */
#define HAVE_PIPE2 1

/* #define to 1 if you have the `pthread_condattr_setclock' function. */
#define HAVE_PTHREAD_CONDATTR_SETCLOCK 1

/* #define to 1 if you have the `pthread_setname_np' function. */
#define HAVE_PTHREAD_SETNAME_NP 1

/* #define to 1 if you have the `pthread_threadid_np' function. */
/* #undef HAVE_PTHREAD_THREADID_NP */

/* #define to 1 if you have the <string.h> header file. */
#define HAVE_STRING_H 1

/* #define to 1 if the system has the type `struct timespec'. */
#define HAVE_STRUCT_TIMESPEC 1

/* #define to 1 if you have the `syslog' function. */
#define HAVE_SYSLOG 1

/* #define to 1 if you have the <sys/time.h> header file. */
#define HAVE_SYS_TIME_H 1

/* #define to 1 if the system has timerfd functionality. */
#define HAVE_TIMERFD 1

/* #define to 1 if compiling for a POSIX platform. */
#define PLATFORM_POSIX 1

/* #define to 1 if compiling for a Windows platform. */
/* #undef PLATFORM_WINDOWS */


#if defined(__GNUC__)
 #define PRINTF_FORMAT(a, b) __attribute__ ((format (__printf__, a, b)))
#else
 #define PRINTF_FORMAT(a, b)
#endif

/* #define to 1 if you have the ANSI C header files. */
/* #undef STDC_HEADERS */

/* #define to 1 to output logging messages to the systemwide log. */
/* #undef USE_SYSTEM_LOGGING_FACILITY */

/* Enable GNU extensions. */
#define _GNU_SOURCE 1

/* Define to `__inline__' or `__inline' if that's what the C compiler
   calls it, or to nothing if 'inline' is not supported under any name.  */
#ifndef __cplusplus
/* #undef inline */
#endif

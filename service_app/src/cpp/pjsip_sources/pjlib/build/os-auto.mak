# pjlib/build/os-auto.mak.  Generated from os-auto.mak.in by configure.

# Determine OS specific files
AC_OS_OBJS=ioqueue_select.o file_access_unistd.o file_io_ansi.o os_core_unix.o os_error_unix.o os_time_unix.o os_timestamp_posix.o sock_qos_bsd.o guid_android.o

#
# PJLIB_OBJS specified here are object files to be included in PJLIB
# (the library) for this specific operating system. Object files common 
# to all operating systems should go in Makefile instead.
#
export PJLIB_OBJS +=    $(AC_OS_OBJS) \
                        addr_resolv_sock.o \
                        log_writer_stdout.o \
                        os_timestamp_common.o \
                        pool_policy_malloc.o sock_bsd.o sock_select.o

#
# TEST_OBJS are operating system specific object files to be included in
# the test application.
#
export TEST_OBJS +=     main.o

#
# Additional LDFLAGS for pjlib-test
#
# Disabled, as this causes duplicated LDFLAGS, which may raise linking errors
#export TEST_LDFLAGS +=  /Users/imjunhyeong/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/lib64/clang/14.0.6/lib/linux/aarch64/libunwind.a -latomic -target aarch64-none-linux-android33 -no-canonical-prefixes -Wl,--build-id=sha1 -nostdlib++ -Wl,--no-undefined -Wl,--fatal-warnings -lc -lm   -lmediandk -lm  /Users/imjunhyeong/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so -lOpenSLES -llog -lGLESv2 -lEGL -landroid

#
# TARGETS are make targets in the Makefile, to be executed for this given
# operating system.
#
export TARGETS      =   $(PJLIB_LIB) $(PJLIB_SONAME)
export TARGETS_EXE  =   $(TEST_EXE)

ANDROID_NDK ?= $(HOME)/Android/Sdk/ndk/30.0.16248370
ANDROID_API ?= 23
ANDROID_LDFLAGS := -Wl,-z,max-page-size=16384

JNI_DIR := ../app/src/main/jniLibs
LEGACY_BIN_DIR := ../app/src/main/assets/raw

ARCH_LIST := arm arm64 x86 x86_64

NDK_ARCH_arm := armeabi-v7a
TRIPLET_arm := armv7a-linux-androideabi

NDK_ARCH_arm64 := arm64-v8a
TRIPLET_arm64 := aarch64-linux-android

NDK_ARCH_x86 := x86
TRIPLET_x86 := i686-linux-android

NDK_ARCH_x86_64 := x86_64
TRIPLET_x86_64 := x86_64-linux-android

CURL_VER := 8.21.0
CURL_SHA256 := aa1b66a70eace83dc624508745646c08ae561de512ab403adffb93ac87fc72e6
CURL_TAR := curl-$(CURL_VER).tar.xz
CURL_SRC := curl-$(CURL_VER)

TOPDIR := $(shell pwd)
TOOLCHAIN := $(ANDROID_NDK)/toolchains/llvm/prebuilt/linux-x86_64
OPENCONNECT_REV := $(shell git -C openconnect rev-parse --short=12 HEAD)
OPENCONNECT_GIT_DIR := $(shell git -C openconnect rev-parse --absolute-git-dir)
STOKEN_REV := $(shell git -C stoken rev-parse --short=12 HEAD)
OPENCONNECT_BOOTSTRAP_STAMP := .build-stamps/openconnect-bootstrap-$(OPENCONNECT_REV)
STOKEN_BOOTSTRAP_STAMP := .build-stamps/stoken-bootstrap-$(STOKEN_REV)
OPENCONNECT_STAMP := .build-stamps/openconnect-$(OPENCONNECT_REV)-api$(ANDROID_API)-ndk$(notdir $(ANDROID_NDK))-page16k
STOKEN_STAMP := .build-stamps/stoken-$(STOKEN_REV)

ARCH_TARGETS := $(addprefix arch-,$(ARCH_LIST))
INSTALL_ARCH_TARGETS := $(addprefix install-arch-,$(ARCH_LIST))
ARCH_OC_TARGETS := $(addsuffix /libopenconnect.so,$(ARCH_LIST))
ARCH_STOKEN_TARGETS := $(addsuffix /libstoken.so,$(ARCH_LIST))
ARCH_CURL_TARGETS := $(addsuffix /curl,$(ARCH_LIST))
ARCH_CSD_TARGETS := $(addsuffix /libcsd-wrapper.so,$(ARCH_LIST))

get_oc_sysroot = $(TOPDIR)/openconnect/android/$(TRIPLET_$(1))/sysroot/usr
get_cc = $(TOOLCHAIN)/bin/$(TRIPLET_$(1))$(ANDROID_API)-clang
get_cxx = $(TOOLCHAIN)/bin/$(TRIPLET_$(1))$(ANDROID_API)-clang++
get_gnutls_private_libs = $(filter-out -lgnutls,$(shell PKG_CONFIG_LIBDIR=$(call get_oc_sysroot,$(1))/lib/pkgconfig pkg-config --static --libs-only-l gnutls))

set_build_env = PATH=$(TOOLCHAIN)/bin:$$PATH \
	PKG_CONFIG_LIBDIR=$(call get_oc_sysroot,$(1))/lib/pkgconfig \
	AR=$(TOOLCHAIN)/bin/llvm-ar \
	AS=$(TOOLCHAIN)/bin/llvm-as \
	CC="$(call get_cc,$(1)) --sysroot=$(call get_oc_sysroot,$(1))/.." \
	CXX="$(call get_cxx,$(1)) --sysroot=$(call get_oc_sysroot,$(1))/.." \
	RANLIB=$(TOOLCHAIN)/bin/llvm-ranlib \
	STRIP=$(TOOLCHAIN)/bin/llvm-strip

.PHONY: all install clean distclean versions
all: $(ARCH_TARGETS)

install: $(INSTALL_ARCH_TARGETS)

clean:
	rm -rf $(ARCH_LIST)
	rm -rf $(foreach arch,$(ARCH_LIST),openconnect/android/$(TRIPLET_$(arch)))
	rm -rf .build-stamps

distclean: clean
	rm -rf $(CURL_SRC) $(CURL_TAR)

versions:
	@printf 'OpenConnect: %s\n' "$$(git -C openconnect describe --tags --always)"
	@printf 'stoken: %s\n' "$$(git -C stoken describe --tags --always)"
	@printf 'curl: %s\n' "$(CURL_VER)"
	@printf 'NDK: %s\n' "$(ANDROID_NDK)"

$(OPENCONNECT_STAMP):
	mkdir -p $(@D)
	touch $@

$(STOKEN_STAMP):
	mkdir -p $(@D)
	touch $@

$(OPENCONNECT_BOOTSTRAP_STAMP):
	cd openconnect && ./autogen.sh
	mkdir -p $(@D)
	touch $@

$(STOKEN_BOOTSTRAP_STAMP):
	cd stoken && ./autogen.sh
	mkdir -p $(@D)
	touch $@

.PHONY: $(INSTALL_ARCH_TARGETS)
$(INSTALL_ARCH_TARGETS): install-arch-%: arch-%
	mkdir -p $(JNI_DIR)/$(NDK_ARCH_$*)
	rm -rf $(LEGACY_BIN_DIR)/$(NDK_ARCH_$*)
	cp $*/libopenconnect.so $(JNI_DIR)/$(NDK_ARCH_$*)/
	cp $*/libstoken.so $(JNI_DIR)/$(NDK_ARCH_$*)/
	cp $*/curl $(JNI_DIR)/$(NDK_ARCH_$*)/libcurl-bin.so
	cp $*/libcsd-wrapper.so $(JNI_DIR)/$(NDK_ARCH_$*)/

.PHONY: $(ARCH_TARGETS)
$(ARCH_TARGETS): arch-%: %/libopenconnect.so %/libstoken.so %/curl %/libcsd-wrapper.so

$(ARCH_OC_TARGETS): %/libopenconnect.so: $(OPENCONNECT_BOOTSTRAP_STAMP) $(OPENCONNECT_STAMP) Makefile
	@test -x "$(call get_cc,$*)" || { \
		echo "Android NDK compiler not found under $(ANDROID_NDK)"; \
		exit 1; \
	}
	GIT_DIR=$(OPENCONNECT_GIT_DIR) \
		$(MAKE) -C openconnect/android ARCH=$* NDK=$(ANDROID_NDK) \
			API_LEVEL=$(ANDROID_API) EXTRA_LDFLAGS="$(ANDROID_LDFLAGS)"
	mkdir -p $*
	cp -Lf openconnect/android/$(TRIPLET_$*)/out/lib/libopenconnect.so $@

$(ARCH_STOKEN_TARGETS): %/libstoken.so: %/libopenconnect.so $(STOKEN_BOOTSTRAP_STAMP) $(STOKEN_STAMP) Makefile
	rm -rf $*/build-stoken $*/out-stoken
	mkdir -p $*/build-stoken $*/out-stoken
	cd $*/build-stoken && \
		$(call set_build_env,$*) $(TOPDIR)/stoken/configure \
			--prefix=/ --host=$(TRIPLET_$*) \
			--disable-static --enable-shared --without-gtk \
			--with-java=$(call get_oc_sysroot,$*)/include \
			--enable-jni-standalone --disable-symvers \
			CFLAGS="-Os -fPIC" \
			LDFLAGS="-Wl,--strip-debug $(ANDROID_LDFLAGS)"
	$(call set_build_env,$*) $(MAKE) -C $*/build-stoken
	$(call set_build_env,$*) $(MAKE) -C $*/build-stoken install-strip \
		DESTDIR=$(TOPDIR)/$*/out-stoken
	cp -Lf $*/out-stoken/lib/libstoken.so $@

$(CURL_TAR):
	curl --fail --location --retry 3 \
		https://curl.se/download/$(CURL_TAR) -o $@.tmp
	printf '%s  %s\n' "$(CURL_SHA256)" "$@.tmp" | sha256sum --check -
	mv $@.tmp $@

$(CURL_SRC)/configure: $(CURL_TAR)
	rm -rf $(CURL_SRC)
	tar -xJf $<
	touch $@

$(ARCH_CURL_TARGETS): %/curl: %/libopenconnect.so $(CURL_SRC)/configure Makefile
	rm -rf $*/build-curl $*/out-curl
	mkdir -p $*/build-curl $*/out-curl
	cd $*/build-curl && \
		$(call set_build_env,$*) $(TOPDIR)/$(CURL_SRC)/configure \
			--prefix=/ --host=$(TRIPLET_$*) \
			--disable-shared --enable-static \
			--enable-ipv6 --disable-threaded-resolver \
			--with-gnutls --with-zlib \
			--without-libpsl --without-libidn2 \
			--without-brotli --without-zstd \
			--without-nghttp2 --without-ngtcp2 --without-nghttp3 \
			--disable-ftp --disable-file --disable-ipfs --disable-smb \
			--disable-ldap --disable-ldaps --disable-rtsp \
			--disable-dict --disable-telnet --disable-tftp \
			--disable-pop3 --disable-imap --disable-smtp \
			--disable-gopher --disable-mqtt \
			--disable-manual --disable-docs \
			CFLAGS="-Os -fPIE" \
			LDFLAGS="-fPIE -pie $(ANDROID_LDFLAGS)" \
			LIBS="$(call get_gnutls_private_libs,$*)"
	$(call set_build_env,$*) $(MAKE) -C $*/build-curl
	$(call set_build_env,$*) $(MAKE) -C $*/build-curl install-strip \
		DESTDIR=$(TOPDIR)/$*/out-curl
	cp -Lf $*/out-curl/bin/curl $@

$(ARCH_CSD_TARGETS): %/libcsd-wrapper.so: csd-wrapper.c %/libopenconnect.so Makefile
	$(call get_cc,$*) -Os -fPIE -pie $(ANDROID_LDFLAGS) $< -o $@
	$(TOOLCHAIN)/bin/llvm-strip --strip-unneeded $@

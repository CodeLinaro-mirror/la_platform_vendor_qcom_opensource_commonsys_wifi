ifeq ($(findstring true,$(TARGET_FWK_SUPPORTS_FULL_VALUEADDS) $(TARGET_BOARD_AUTO)),true)
ifneq ($(TARGET_HAS_LOW_RAM),true)
PRODUCT_PACKAGES += QtiWifiService
endif #TARGET_HAS_LOW_RAM
endif # TARGET_FWK_SUPPORTS_FULL_VALUEADDS | TARGET_BOARD_AUTO

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.wifi.rtt.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/android.hardware.wifi.rtt.xml
